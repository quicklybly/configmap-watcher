package io.github.quicklybly.configmapwatcher.testapp

import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import kotlin.io.path.writeText

@SpringBootTest
class ConfigMapRefreshE2ETest {

    @Autowired
    private lateinit var configMapProperties: ConfigMapProperties

    @Test
    fun `refreshes the context when the watched config map file changes`() {
        assertThat(configMapProperties.message).isEqualTo("initial")

        configFile.writeText("config-map:\n  message: updated\n")

        await.atMost(Duration.ofSeconds(30))
            .pollInterval(Duration.ofMillis(250))
            .untilAsserted { assertThat(configMapProperties.message).isEqualTo("updated") }
    }

    companion object {
        private const val CONFIG_LOCATION_PROPERTY = "spring.config.additional-location"
        private lateinit var configDir: Path
        private lateinit var configFile: Path

        // A system property, not @SpringBootTest(properties = ...): Spring Cloud rebuilds the
        // environment from the system property and environment variable sources only, so a location
        // declared as an inlined test property is gone by the time refresh() re-reads the config -
        // the file changes, and nothing happens. This also matches how a pod passes the location in.
        //
        // @BeforeAll runs before the test instance exists, and therefore before the application
        // context is loaded, so the property and the file are in place when Spring Boot starts.
        @JvmStatic
        @BeforeAll
        fun seedConfigMap() {
            configDir = Files.createTempDirectory("configmap-watcher-e2e")
            configFile = configDir.resolve("application.yaml")
            configFile.writeText("config-map:\n  message: initial\n")
            System.setProperty(CONFIG_LOCATION_PROPERTY, configFile.toString())
        }

        @JvmStatic
        @AfterAll
        fun removeConfigMap() {
            System.clearProperty(CONFIG_LOCATION_PROPERTY)
            configDir.toFile().deleteRecursively()
        }
    }
}
