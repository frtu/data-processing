package com.github.frtu.dataprocessing.generator.producer

import com.github.frtu.kotlin.utils.io.toJsonString
import com.github.frtu.dataprocessing.generator.RawInput
import java.util.UUID
import org.apache.kafka.clients.admin.NewTopic
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.kafka.config.TopicBuilder

/**
 * Based on :
 * @see <a href="https://docs.spring.io/spring-kafka/reference/html/#introduction">Spring Kafka</a>
 * @author Frédéric TU
 */
@SpringBootApplication
class ProducerEmailSourceApplication {
    @Autowired
    lateinit var producerEmailSource: ProducerEmailSource

    @Bean
    fun topicEmailSource(): NewTopic? {
        return TopicBuilder.name(producerEmailSource.outputSource)
            .partitions(1)
            .replicas(1)
            .build()
    }

    @Bean
    fun runner(): ApplicationRunner? = ApplicationRunner { args: ApplicationArguments? ->
        producerEmailSource.send(RawInput(
            eventId = UUID.randomUUID().toString(),
            userId = UUID.randomUUID().toString(),
            eventType = "Notification",
            message = "Message ${UUID.randomUUID()}",
            amount = 1.6,
        ).toJsonString())
    }
}

fun main(args: Array<String>) {
    System.getProperties().put("server.port", 8084);
    runApplication<ProducerEmailSourceApplication>(*args)
}