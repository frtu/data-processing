package com.github.frtu.dataprocessing.framework

import com.github.frtu.kotlin.utils.data.ValueObject
import java.io.Serializable
import java.util.Properties

/**
 * Configuration class for Flink pipeline settings. * Loads configuration from application.properties or system properties. */@ValueObject
data class FlinkConfig(
    val jobManagerHost: String,
    val jobManagerPort: Int,
    val parallelism: Int,
    val kafkaBootstrapServers: String,
    val kafkaInputTopic: String,
    val kafkaConsumerGroupId: String,
    val elasticsearchHost: String,
    val elasticsearchPort: Int,
    val elasticsearchScheme: String,
    val elasticsearchIndex: String,
    val checkpointingInterval: Long,
    val batchSizeElasticsearch: Int,
    val maxInFlightRequests: Int,
    val maxBufferedRequests: Int
) : Serializable {
    companion object {
        private const val serialVersionUID = 1L

        /**
         * Load configuration from application.properties file or system properties.         * Command line arguments take precedence over properties file.         */        fun load(args: Array<String> = emptyArray()): FlinkConfig {
            val properties = Properties()

            // Load from application.properties file
            try {
                val inputStream = FlinkConfig::class.java.classLoader
                    .getResourceAsStream("application.properties")
                if (inputStream != null) {
                    properties.load(inputStream)
                    inputStream.close()
                }
            } catch (e: Exception) {
                println("Warning: Could not load application.properties: ${e.message}")
            }

            // Override with system properties if available
            System.getProperties().forEach { key, value ->
                if (key.toString().startsWith("flink.") ||
                    key.toString().startsWith("kafka.") ||
                    key.toString().startsWith("elasticsearch.") ||
                    key.toString().startsWith("checkpointing.") ||
                    key.toString().startsWith("batch.") ||
                    key.toString().startsWith("max.")
                ) {
                    properties[key] = value
                }
            }

            return FlinkConfig(
                jobManagerHost = args.getOrNull(0) ?: properties.getProperty("flink.jobmanager.host", "localhost"),
                jobManagerPort = args.getOrNull(1)?.toIntOrNull() ?: properties.getProperty(
                    "flink.jobmanager.port",
                    "28081"
                ).toInt(),
                parallelism = args.getOrNull(2)?.toIntOrNull() ?: properties.getProperty("flink.parallelism", "1")
                    .toInt(),
                kafkaBootstrapServers = properties.getProperty("kafka.bootstrap.servers", "localhost:9092"),
                kafkaInputTopic = properties.getProperty("kafka.topic.input", "raw_events"),
                kafkaConsumerGroupId = properties.getProperty("kafka.consumer.group.id", "dscl_processor_group"),
                elasticsearchHost = properties.getProperty("elasticsearch.host", "localhost"),
                elasticsearchPort = properties.getProperty("elasticsearch.port", "9200").toInt(),
                elasticsearchScheme = properties.getProperty("elasticsearch.scheme", "http"),
                elasticsearchIndex = properties.getProperty("elasticsearch.index", "events_idx"),
                checkpointingInterval = properties.getProperty("checkpointing.interval", "5000").toLong(),
                batchSizeElasticsearch = properties.getProperty("batch.size.elasticsearch", "500").toInt(),
                maxInFlightRequests = properties.getProperty("max.inflight.requests", "50").toInt(),
                maxBufferedRequests = properties.getProperty("max.buffered.requests", "10000").toInt()
            )
        }
    }
}