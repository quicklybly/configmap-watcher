package com.quicklybly.configmapwatcher.testapp

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@EnableScheduling
@SpringBootApplication
@EnableConfigurationProperties(ConfigMapProperties::class)
class TestApplication

fun main(args: Array<String>) {
    runApplication<TestApplication>(*args)
}
