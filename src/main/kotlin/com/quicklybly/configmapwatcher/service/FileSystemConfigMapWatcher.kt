package com.quicklybly.configmapwatcher.service

import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import org.springframework.cloud.context.refresh.ConfigDataContextRefresher
import java.io.File
import java.nio.file.ClosedWatchServiceException
import java.nio.file.FileSystems
import java.nio.file.StandardWatchEventKinds
import java.nio.file.WatchService
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor

private val logger = KotlinLogging.logger { }

/**
 * Default [ConfigMapWatcher], backed by a [WatchService] per configured path.
 */
class FileSystemConfigMapWatcher(
    private val contextRefresher: ConfigDataContextRefresher,
    private val configPath: String,
) : ConfigMapWatcher {

    private val executor = newVirtualThreadPerTaskExecutor()
    private val watchers = CopyOnWriteArrayList<WatchService>()

    @PostConstruct
    override fun startWatchingConfigMaps() {
        val paths = configPath.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        if (paths.isEmpty()) {
            logger.info { "Config path is empty, config map watcher is disabled" }
            return
        }

        logger.info { "Starting watching config maps in :$configPath" }
        paths.forEach { watchConfig(it) }
    }

    /**
     * Closing the watch services unblocks the polling threads, which then terminate on their own.
     * Spring calls this on context shutdown; it is safe to call more than once
     */
    @PreDestroy
    override fun close() {
        watchers.forEach { watcher ->
            runCatching { watcher.close() }
                .onFailure { logger.warn(it) { "Failed to close watch service" } }
        }
        watchers.clear()
        executor.shutdownNow()
    }

    private fun watchConfig(path: String) {
        logger.info { "Start watching config path: $path" }
        val filePath = File(path).toPath().toAbsolutePath()
        val watchDir = filePath.parent
        val watcher = FileSystems.getDefault().newWatchService()
        watchDir.register(
            watcher,
            StandardWatchEventKinds.ENTRY_CREATE,
            StandardWatchEventKinds.ENTRY_MODIFY,
            StandardWatchEventKinds.ENTRY_DELETE,
        )
        watchers += watcher

        executor.submit {
            try {
                watcher.use { pollUntilClosed(it, path) }
            } catch (_: ClosedWatchServiceException) {
                logger.info { "Stopped watching config path: $path" }
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                logger.info { "Stopped watching config path: $path" }
            }
        }
    }

    private fun pollUntilClosed(watcher: WatchService, path: String) {
        while (true) {
            val watchKey = watcher.take()

            watchKey.pollEvents().forEach { _ ->
                logger.info { "Caught file '$path' change event, refreshing context now" }
                contextRefresher.refresh()
                logger.info { "Finished context refresh for file '$path'" }
            }

            val reset = watchKey.reset()
            if (!reset) {
                logger.error { "Cannot reset watch key. Watch key is valid: ${watchKey.isValid}" }
                return
            }
        }
    }
}
