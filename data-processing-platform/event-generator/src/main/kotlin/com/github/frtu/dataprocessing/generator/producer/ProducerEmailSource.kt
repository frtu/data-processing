package com.github.frtu.dataprocessing.generator.producer

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Service

@Service
class ProducerEmailSource {
    @Value("\${application.topic.email-source}")
    lateinit var outputSource: String

    @Autowired
    lateinit var kafkaTemplate: KafkaTemplate<String, String>

    fun send(message: String) {
        logger.info("Sending to topic:$outputSource message:$message")
        kafkaTemplate.send(outputSource, message)
    }

    internal val logger = LoggerFactory.getLogger(this::class.java)
}