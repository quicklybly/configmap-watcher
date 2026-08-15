package com.quicklybly.configmapwatcher.config

import com.quicklybly.configmapwatcher.service.ConfigMapWatcher
import com.quicklybly.configmapwatcher.service.FileSystemConfigMapWatcher
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.cloud.context.refresh.ConfigDataContextRefresher
import org.springframework.context.annotation.Bean

@AutoConfiguration
@ConditionalOnProperty(
    value = ["config-map-watcher.enabled"],
    havingValue = "true",
    matchIfMissing = false,
)
class ConfigMapWatcherConfiguration {

    @Bean
    @ConditionalOnMissingBean
    fun configMapWatcher(
        contextRefresher: ConfigDataContextRefresher,
        @Value($$"${spring.config.additional-location:}") configPath: String,
    ): ConfigMapWatcher = FileSystemConfigMapWatcher(contextRefresher, configPath)
}
