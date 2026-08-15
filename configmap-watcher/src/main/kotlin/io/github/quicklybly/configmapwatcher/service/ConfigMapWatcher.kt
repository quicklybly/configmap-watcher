package io.github.quicklybly.configmapwatcher.service

/**
 * Watches the configured config map locations and refreshes the Spring context when they change.
 *
 * The autoconfiguration backs off as soon as any [ConfigMapWatcher] bean is present, so declaring
 * your own implementation replaces the default one.
 */
interface ConfigMapWatcher : AutoCloseable {

    /**
     * Starts watching. Implementations are expected to return promptly and do the watching in the
     * background, and to call this themselves during bean initialisation - the default one does so
     * from `@PostConstruct`.
     */
    fun startWatchingConfigMaps()

    /**
     * Releases whatever the implementation holds open. Called by Spring on context shutdown, and
     * expected to be safe to call more than once.
     */
    override fun close() = Unit
}
