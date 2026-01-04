package com.github.frtu.dataprocessing.framework

import co.elastic.clients.elasticsearch.core.bulk.IndexOperation
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.github.frtu.kotlin.utils.data.ValueObject
import java.io.Serializable
import org.apache.flink.api.common.eventtime.WatermarkStrategy
import org.apache.flink.api.common.functions.OpenContext
import org.apache.flink.api.common.functions.RichMapFunction
import org.apache.flink.api.common.serialization.SimpleStringSchema
import org.apache.flink.connector.elasticsearch.sink.Elasticsearch8AsyncSinkBuilder
import org.apache.flink.connector.kafka.source.KafkaSource
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import org.apache.http.HttpHost

// This class represents the external, potentially messy API contract
@ValueObject
@JsonIgnoreProperties(ignoreUnknown = true) // Critical: Don't crash on new/unexpected fields
data class RawInput(
    @JsonProperty("event_id")
    val eventId: String?,

    @JsonProperty("user_id")
    val userId: String?,

    @JsonProperty("event_type")
    val eventType: String?,

    @JsonProperty("message")
    val message: String?,

    @JsonProperty("amount")
    val amount: Double? // Nullable allows us to detect missing values vs 0.0
) : Serializable {
    companion object {
        private const val serialVersionUID = 1L
    }
}

// ---------------------------------------------------------
// 1. The Unified Data Model (The Abstraction)
// ---------------------------------------------------------
// This data class acts as the canonical contract between ES and CH.
@ValueObject
data class UnifiedEvent(
    var eventId: String,      // Idempotency Key
    var userId: String,
    var eventType: String,
    var payload: String,      // Full text for ES
    var amount: Double,       // Metric for CH (Cleaned, no nulls)
    var timestamp: Long
) : Serializable {
    companion object {
        private const val serialVersionUID = 1L
    }
}

// ---------------------------------------------------------
// 2. The Transformation Logic (Sanitization & Idempotency)
// ---------------------------------------------------------
class EventSanitizer : RichMapFunction<String, UnifiedEvent>(), Serializable {
    // 1. Declare the mapper as 'transient' (so Flink ignores it during serialization)
    //    OR simply declare it as a nullable var without initializing it here.
    @Transient
    private lateinit var mapper: ObjectMapper

    // 2. Override 'open'. This runs ONCE per parallel task slot on the WORKER.
    override fun open(openContext: OpenContext) {
        // Initialize the non-serializable object here
        mapper = jacksonObjectMapper()
    }

    override fun map(rawJson: String): UnifiedEvent {
        // 1. Deserialization: Strong typing happens here
        // If JSON is malformed, this will throw. In prod, wrap in try-catch
        // and send failures to a Side Output (Dead Letter Queue).
        val raw = mapper.readValue(rawJson, RawInput::class.java)

        // 2. Transformation & Sanitization
        // logic moves from "node.get(...)" to standard Kotlin property access
        return UnifiedEvent(
            // PITFALL FIX: Idempotency
            // If the source doesn't provide an ID, we generate a deterministic one
            // based on the content (hash) rather than a random UUID to allow deduplication.
            eventId = raw.eventId ?: "gen_${raw.hashCode()}",
            userId = raw.userId ?: "anonymous",
            eventType = raw.eventType ?: "unknown",
            payload = raw.message ?: "",
            // PITFALL FIX: Null/Default Mismatch
            // Explicitly coerce nulls to 0.0 for ClickHouse safety

            // PITFALL FIX: Null/Default Mismatch
            // ClickHouse hates nulls in non-nullable columns. We coerce them here.
            // If 'amount' is missing/null, we default to 0.0 to prevent CH rejection.
            amount = raw.amount ?: 0.0,
            timestamp = System.currentTimeMillis()
        ).also {
            System.err.println("UnifiedEvent=$it")
        }
    }

    companion object {
        private const val serialVersionUID = 1L
    }
}

// ---------------------------------------------------------
// 3. The Main Pipeline (Remote Flink Configuration)
// ---------------------------------------------------------
fun main(args: Array<String>) {
    // Load configuration from properties file and command line arguments
    val config = FlinkConfig.load(args)

    println("EventSanitizer Pipeline Configuration:")
    println("Connecting to Flink cluster at ${config.jobManagerHost}:${config.jobManagerPort} with parallelism ${config.parallelism}")
    println("Kafka: ${config.kafkaBootstrapServers}")
    println("Elasticsearch: ${config.elasticsearchScheme}://${config.elasticsearchHost}:${config.elasticsearchPort}")
    println("Configuration loaded successfully")

    // Create remote execution environment pointing to Flink cluster
    val env = StreamExecutionEnvironment.createRemoteEnvironment(
        config.jobManagerHost,
        config.jobManagerPort,
        config.parallelism,
        // Add the JAR file path for job submission
        "build/libs/data-processing-platform.jar"
    )

    // CRITICAL: Checkpointing ensures consistency across both stores.
    // If CH write succeeds but ES fails, Flink rolls back Kafka offset.
    env.enableCheckpointing(config.checkpointingInterval)

    // FIX: Configure Kryo serialization for better Kotlin data class support
    // Register custom serializers for Kotlin data classes
    env.config.registerKryoType(UnifiedEvent::class.java)
    env.config.registerKryoType(RawInput::class.java)

    // Enable force Kryo for better performance and compatibility
    env.config.enableForceKryo()

    // Set parallelism for the job
    env.setParallelism(config.parallelism)

    // A. Source: Kafka
    val kafkaSource = KafkaSource.builder<String>()
        .setBootstrapServers(config.kafkaBootstrapServers)
        .setTopics(config.kafkaInputTopic)
        .setGroupId(config.kafkaConsumerGroupId)
        .setStartingOffsets(OffsetsInitializer.earliest())
        .setValueOnlyDeserializer(SimpleStringSchema())
        .build()

    val stream = env.fromSource(kafkaSource, WatermarkStrategy.noWatermarks(), "Kafka Source")
        .map(EventSanitizer())
        .name("Sanitization Layer")

    // B. Sink 1: Elasticsearch (Search & Document Store)
    // Strategy: Near Real-time (NRT). Flush often or rely on ES refresh interval.
    val esSink = Elasticsearch8AsyncSinkBuilder<UnifiedEvent>()
        .setHosts(HttpHost(config.elasticsearchHost, config.elasticsearchPort, config.elasticsearchScheme))
        .setElementConverter { element: UnifiedEvent, context ->
            val json = mapOf(
                "user_id" to element.userId,
                "message" to element.payload, // ES gets the text
                "timestamp" to element.timestamp
            )

            // PITFALL FIX: Use ID for upsert/dedup in ES
            IndexOperation.of<Map<String, Any>> { b ->
                b.index(config.elasticsearchIndex)
                    .id(element.eventId)
                    .document(json)
            }
        }.setMaxBatchSize(config.batchSizeElasticsearch)
        .setMaxInFlightRequests(config.maxInFlightRequests)
        .setMaxBufferedRequests(config.maxBufferedRequests)
        .build()

    stream.sinkTo(esSink as org.apache.flink.api.connector.sink2.Sink<UnifiedEvent>).name("Elasticsearch Sink")

    // C. Sink 2: ClickHouse (OLAP Store)
    // Strategy: Heavy Batching. ClickHouse thrives on large inserts.
    // PITFALL FIX: The "Real-Time Write Trap".
    // We force Flink to buffer up to 5000 rows or 5 seconds, whichever comes first.
//    val jdbcExecutionOptions = JdbcExecutionOptions.builder()
//        .withBatchSize(5000)
//        .withBatchIntervalMs(5000)
//        .withMaxRetries(3)
//        .build()
//
//    val jdbcOptions = JdbcConnectionOptions.JdbcConnectionOptionsBuilder()
//        .withUrl("jdbc:clickhouse://clickhouse:8123/default")
//        .withDriverName("ru.yandex.clickhouse.ClickHouseDriver")
//        .withUsername("default")
//        .build()
//
//    // PITFALL FIX: ReplacingMergeTree support
//    // We insert the 'sign' logic or version column if using CollapsingMergeTree,
//    // otherwise standard insertion.
//    val chQuery = """
//        INSERT INTO user_activity_log
//        (event_id, user_id, amount, event_time)
//        VALUES (?, ?, ?, ?)
//    """
//
//    stream.addSink(JdbcSink.sink(
//        chQuery,
//        { statement, event ->
//            statement.setString(1, event.eventId)
//            statement.setString(2, event.userId)
//            statement.setDouble(3, event.amount)
//            statement.setLong(4, event.timestamp)
//        },
//        jdbcExecutionOptions,
//        jdbcOptions
//    )).name("ClickHouse Sink")

    env.execute("Dual-Store Convergence Pipeline")
}