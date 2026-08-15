package com.quicklybly.configmapwatcher.testapp

import org.springframework.beans.factory.annotation.Value
import org.springframework.cloud.context.config.annotation.RefreshScope
import org.springframework.stereotype.Component

/**
 * Reads a property that lives in the watched config map file.
 *
 * Being `@RefreshScope`, the bean is thrown away and rebuilt on the next access after
 * `ConfigDataContextRefresher.refresh()` runs, so [message] is what makes a refresh observable.
 */
@Component
@RefreshScope
class WatchedMessage(@Value($$"${config-map.message:none}") val message: String)
