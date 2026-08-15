package com.quicklybly.configmapwatcher.testapp

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

private val logger = KotlinLogging.logger { }

@Component
class MessageReporter(private val configMapProperties: ConfigMapProperties) {

    @Scheduled(fixedDelay = 5_000)
    fun reportCurrentMessage() {
        logger.info { "Current message is '${configMapProperties.message}'" }
    }
}
