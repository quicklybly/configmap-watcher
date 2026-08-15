package io.github.quicklybly.configmapwatcher.service

import io.mockk.called
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.awaitility.kotlin.await
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.cloud.context.refresh.ConfigDataContextRefresher
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Duration
import kotlin.io.path.createDirectory
import kotlin.io.path.writeText

class FileSystemConfigMapWatcherTest {

    private val contextRefresher = mockk<ConfigDataContextRefresher>(relaxed = true)
    private val startedWatchers = mutableListOf<ConfigMapWatcher>()

    @AfterEach
    fun stopWatchers() = startedWatchers.forEach { it.close() }

    private fun startWatching(configPath: String) = FileSystemConfigMapWatcher(contextRefresher, configPath)
        .also { startedWatchers += it }
        .apply { startWatchingConfigMaps() }

    private fun awaitRefresh() = await.atMost(Duration.ofSeconds(30))
        .pollInterval(Duration.ofMillis(250))
        .untilAsserted { verify(atLeast = 1) { contextRefresher.refresh() } }

    @Test
    fun `does nothing when config path is empty`() {
        assertThatCode { startWatching("") }.doesNotThrowAnyException()

        verify { contextRefresher wasNot called }
    }

    @Test
    fun `refreshes context when a watched file is written in place`(@TempDir tempDir: Path) {
        val configFile = tempDir.resolve("application.yaml")
        configFile.writeText("greeting: hello")

        startWatching(configFile.toString())

        configFile.writeText("greeting: goodbye")

        awaitRefresh()
    }

    @Test
    fun `refreshes context on a kubernetes-style atomic configmap swap`(@TempDir tempDir: Path) {
        val key = "application.yaml"

        // Mimic how kubelet mounts a ConfigMap: a timestamped data dir holding the real files,
        // a `..data` symlink pointing at it, and the app-visible file symlinked through `..data`.
        val firstDataDir = tempDir.resolve("..2024_01_01_00_00_00.000000000").createDirectory()
        firstDataDir.resolve(key).writeText("greeting: hello")
        val dataLink = tempDir.resolve("..data")
        Files.createSymbolicLink(dataLink, firstDataDir.fileName)
        val configFile = tempDir.resolve(key)
        Files.createSymbolicLink(configFile, Path.of("..data", key))

        startWatching(configFile.toString())

        // kubelet writes the new content to a fresh timestamped dir, atomically swaps the `..data`
        // symlink onto it, then removes the old dir - the leaf file is never written in place.
        val secondDataDir = tempDir.resolve("..2024_01_01_00_05_00.000000000").createDirectory()
        secondDataDir.resolve(key).writeText("greeting: goodbye")
        val tmpLink = tempDir.resolve("..data_tmp")
        Files.createSymbolicLink(tmpLink, secondDataDir.fileName)
        Files.move(
            tmpLink,
            dataLink,
            StandardCopyOption.ATOMIC_MOVE,
            StandardCopyOption.REPLACE_EXISTING,
        )
        firstDataDir.toFile().deleteRecursively()

        awaitRefresh()
        assertThat(Files.readString(configFile)).isEqualTo("greeting: goodbye")
    }

    @Test
    fun `watches the first of several comma separated paths`(@TempDir tempDir: Path) {
        val (firstFile, secondFile) = twoConfigFiles(tempDir)

        startWatching("$firstFile, $secondFile")

        firstFile.writeText("a: 2")

        awaitRefresh()
    }

    @Test
    fun `watches the last of several comma separated paths`(@TempDir tempDir: Path) {
        val (firstFile, secondFile) = twoConfigFiles(tempDir)

        startWatching("$firstFile, $secondFile")

        secondFile.writeText("b: 2")

        awaitRefresh()
    }

    private fun twoConfigFiles(tempDir: Path): Pair<Path, Path> {
        val firstDir = tempDir.resolve("first").createDirectory()
        val secondDir = tempDir.resolve("second").createDirectory()
        return firstDir.resolve("a.yaml").also { it.writeText("a: 1") } to
            secondDir.resolve("b.yaml").also { it.writeText("b: 1") }
    }
}
