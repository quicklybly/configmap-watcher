package com.quicklybly.configmapwatcher.testapp

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.bind.DefaultValue
import org.springframework.cloud.context.config.annotation.RefreshScope

@RefreshScope
@ConfigurationProperties("config-map")
class ConfigMapProperties(@DefaultValue("none") val message: String)
