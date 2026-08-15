package com.quicklybly.configmapwatcher.testapp

import org.springframework.beans.factory.annotation.Value
import org.springframework.cloud.context.config.annotation.RefreshScope
import org.springframework.stereotype.Component

@Component
@RefreshScope
class WatchedMessage(@Value($$"${config-map.message:none}") val message: String)
