package io.github.quicklybly.configmapwatcher.testapp

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class MessageController(private val configMapProperties: ConfigMapProperties) {

    @GetMapping("/message")
    fun message(): String = configMapProperties.message
}
