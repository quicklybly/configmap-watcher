package io.github.quicklybly.configmapwatcher.config

import io.github.quicklybly.configmapwatcher.service.ConfigMapWatcher
import io.github.quicklybly.configmapwatcher.service.FileSystemConfigMapWatcher
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

    @Test
    fun `watches the path bound from spring config additional-location`(@TempDir tempDir: Path) {
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
