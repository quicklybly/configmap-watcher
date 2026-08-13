package com.quicklybly.configmapwatcher.config

import com.quicklybly.configmapwatcher.service.ConfigMapWatcher
import com.quicklybly.configmapwatcher.service.FileSystemConfigMapWatcher
import io.mockk.called
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.cloud.context.refresh.ConfigDataContextRefresher
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.nio.file.Path
import java.time.Duration
import kotlin.io.path.writeText

class ConfigMapWatcherConfigurationTest {

    private val contextRefresher = mockk<ConfigDataContextRefresher>(relaxed = true)

    private val contextRunner = ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(ConfigMapWatcherConfiguration::class.java))
        .withBean(ConfigDataContextRefresher::class.java, { contextRefresher })

    @Test
    fun `is registered as an auto configuration`() {
        val imports = javaClass.classLoader
            .getResource("META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports")
            ?.readText()

        assertThat(imports)
            .withFailMessage("ConfigMapWatcherConfiguration is not listed in the auto configuration imports")
            .contains(ConfigMapWatcherConfiguration::class.java.name)
    }

    @Test
    fun `does not register a watcher when the property is missing`() {
        contextRunner.run { context ->
            assertThat(context).doesNotHaveBean(ConfigMapWatcher::class.java)
        }
    }

    @Test
    fun `does not register a watcher when the property is disabled`() {
        contextRunner.withPropertyValues("config-map-watcher.enabled=false").run { context ->
            assertThat(context).doesNotHaveBean(ConfigMapWatcher::class.java)
        }
    }

    @Test
    fun `registers the file system watcher when the property is enabled`() {
        contextRunner.withPropertyValues("config-map-watcher.enabled=true").run { context ->
            assertThat(context).hasSingleBean(ConfigMapWatcher::class.java)
            assertThat(context).getBean(ConfigMapWatcher::class.java)
                .isInstanceOf(FileSystemConfigMapWatcher::class.java)
        }
    }

    // The config path is optional: with no `spring.config.additional-location` the bean is still
    // created, it just has nothing to watch - so the context must start rather than fail to bind.
    @Test
    fun `defaults the config path to empty`() {
        contextRunner.withPropertyValues("config-map-watcher.enabled=true").run { context ->
            assertThat(context).hasNotFailed()
            verify { contextRefresher wasNot called }
        }
    }

    @Test
    fun `keeps the watcher declared by the application`() {
        contextRunner
            .withPropertyValues("config-map-watcher.enabled=true")
            .withUserConfiguration(CustomWatcherConfiguration::class.java)
            .run { context ->
                assertThat(context).hasSingleBean(ConfigMapWatcher::class.java)
                assertThat(context).getBean(ConfigMapWatcher::class.java)
                    .isInstanceOf(CustomConfigMapWatcher::class.java)
            }
    }

    // End to end through the container: proves `spring.config.additional-location` really reaches
    // the watcher, which the bean-presence tests above cannot show.
    @Test
    fun `watches the path bound from spring config additional-location`(
        @TempDir tempDir: Path,
    ) {
        val configFile = tempDir.resolve("application.yaml")
        configFile.writeText("greeting: hello")

        contextRunner
            .withPropertyValues(
                "config-map-watcher.enabled=true",
                "spring.config.additional-location=$configFile",
            )
            .run { context ->
                assertThat(context).hasSingleBean(ConfigMapWatcher::class.java)

                configFile.writeText("greeting: goodbye")

                // Generous by design: macOS has no native file watcher, so the JDK falls back to
                // PollingWatchService and a change surfaces only on the next poll tick.
                await.atMost(Duration.ofSeconds(30))
                    .pollInterval(Duration.ofMillis(250))
                    .untilAsserted { verify(atLeast = 1) { contextRefresher.refresh() } }
            }
    }

    @Configuration(proxyBeanMethods = false)
    class CustomWatcherConfiguration {
        @Bean
        fun customConfigMapWatcher(): ConfigMapWatcher = CustomConfigMapWatcher()
    }

    class CustomConfigMapWatcher : ConfigMapWatcher {
        override fun startWatchingConfigMaps() = Unit
    }
}
