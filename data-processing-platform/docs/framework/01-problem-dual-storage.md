# Dual storage ES & CH

This is a complex challenge that requires treating these two distinct engines not just as data stores, but as a **Converged Lambda Architecture**.

- Elasticsearch (ES) is a search engine (row-oriented, inverted index, eventually consistent).
- ClickHouse (CH) is an OLAP engine (column-oriented, sparse index, asynchronously consistent).

The biggest mistake developers make is trying to force one to behave like the other, or assuming their "eventual consistency" models are identical. They are not.

Below is the framework definition, the DSL concept, and the critical consistency/pitfall analysis.

---

### Part 1: The Abstraction Framework & DSL

**Name:** _Dual-Store Convergence Layer (DSCL)_

The goal of this layer is to decouple the _logical entity_ from the _physical storage_. You should not write directly to ES or CH from your application code.

#### 1. The Architecture
- **Ingestion:** Asynchronous, Log-Based (Kafka/Pulsar).
- **Transformation:** Stateless Workers (Flink/Spark/Go Consumers) that split the stream.
- **Write Strategy:** "Fork-Join Write" with Idempotency Keys.
- **Read Strategy:** Intent-Based Routing (Search vs. Compute).

#### 2. The DSL (Domain Specific Language)

Use a declarative configuration (e.g., HCL or YAML) to define data entities. The abstraction layer compiles this into ES Mappings and CH Table Definitions.

```yaml
# entity_definition.yaml
entity: "user_activity"
consistency_level: "eventual" # Options: strict (expensive), eventual
partition_key: "event_date"
primary_key: "user_id"

fields:
  - name: "event_id"
    type: "uuid"
    role: "idempotency_key" 
  - name: "message"
    type: "text"
    storage: ["es"]        # Only ES needs full-text search capability
    analyzer: "standard"
  - name: "duration_ms"
    type: "uint32"
    storage: ["ch", "es"]  # Both need this: CH for avg/sum, ES for filtering
  - name: "tags"
    type: "array<string>"
    storage: ["ch", "es"] 

# The Framework compiles this to:
# ES: Index Template with keyword/text mappings.
# CH: CREATE TABLE ... ENGINE = ReplacingMergeTree() ORDER BY (user_id) ...
```
---
### Part 2: Inherent Consistency Issues (The "Danger Zone")

When running ES and CH in parallel, you face **Dual-Split Consistency** issues.
#### 1. The "Refresh" vs. "Merge" Gap
- **ElasticSearch:** Data is visible after `refresh_interval` (default 1s).
- **ClickHouse:** Data is physically written immediately but effectively "consistent" for deduplication only after background **merges** occur (which can take minutes).
- **The Issue:** A user writes data, immediately searches (hits ES, sees it), then looks at an analytics dashboard (hits CH, doesn't see it yet, or sees duplicates).

#### 2. Mutation Asymmetry
- **Elasticsearch:** Supports updates (`_update`). It marks the old document as deleted and indexes a new one. Costly, but synchronous-feeling.
- **ClickHouse:** **Hates** updates. `ALTER TABLE UPDATE` is a heavy "Mutation" task that rewrites data parts asynchronously.
- **The Issue:** If you update a record's status from "Pending" to "Paid":
    - ES shows "Paid" almost instantly.
    - CH might show "Pending" for 5-10 minutes until the mutation finishes.

#### 3. The Null/Default Value Mismatch
- **ElasticSearch:** Distinguishes between `null` (missing field) and `0` or `""`.
- **ClickHouse:** Strongly typed. If a field is `Nullable`, it ruins performance. If it's not, it defaults to `0` or `""`.
- **The Issue:** Calculating "Average Order Value" in CH might implicitly include `0`s that were actually `null`s in ES, skewing analytics.

---
### Part 3: Common Pitfalls & Anti-Patterns

#### 1. The "Real-Time" Write Trap
- **Bad Pattern:** Writing single rows to ClickHouse via HTTP every time an event happens (sync with ES writes).
- **Why:** ClickHouse needs **large batches** (min 1000 rows). Small inserts cause "Too many parts" errors and crush the cluster zookeeper/clickhouse-keeper.
- **Fix:** The Abstraction Layer must buffer CH writes (buffer by time or size), while ES writes can be smaller/more frequent.
#### 2. Ignoring Idempotency (The Duplicate Ghost)
- **Bad Pattern:** Relying on the pipeline to never deliver duplicates.
- **Why:** In distributed systems (Kafka), "exactly-once" is a myth; you usually get "at-least-once".
- **Pitfall:** ES handles ID collisions by versioning (overwriting). ClickHouse simply appends the duplicate row.
- **Fix:** Use `ReplacingMergeTree` in ClickHouse with a version column (e.g., `event_timestamp`) to deduplicate on read/background merge.

#### 3. Deep Pagination & High Cardinality
- **Bad Pattern:** Using ES for aggregation on high-cardinality fields (e.g., grouping by `user_id` where you have 10M users).
- **Pitfall:** ES creates massive "Global Ordinals" structures in heap memory, causing OOM (Out Of Memory) or GC pauses.
- **Fix:** The DSL Router must route "Group By" queries to ClickHouse and "Search/Scan" queries to ES.

#### 4. The "Join" Delusion
- **Bad Pattern:** Attempting to join data in Elasticsearch (Nested types/Parent-Child) or doing massive joins in ClickHouse at query time.
- **Pitfall:** ES joins are performance killers. CH joins do not use indexes on the right-side table (by default) and can explode memory.
- **Fix:** **Denormalize** at the ingestion stage. The Framework should "flatten" data before it hits the storage.

#### 5. Schema Drift Disasters
- **Bad Pattern:** Allowing dynamic mapping in ES (`dynamic: true`) while having a rigid schema in CH.
- **Pitfall:** A developer sends a new field `status: "active"`. ES accepts it. CH rejects the batch because the column doesn't exist. The pipeline stalls.
- **Fix:** The DSL must be the **source of truth**. All schema changes are applied via CI/CD pipeline to both stores before code deployment.

### Summary Checklist for your Framework

|**Feature**|**Elasticsearch Strategy**|**ClickHouse Strategy**|**Framework/DSL Responsibility**|
|---|---|---|---|
|**Updates**|`_update` API|`ReplacingMergeTree` (Insert new row with higher version)|Abstract "Update" as "Insert new version"|
|**Deletes**|`_delete` API|`TTL` or `COLLAPSING` MergeTree|Implement "Soft Delete" flag logic|
|**Schema**|Explicit Mapping|Strict Types (Low Cardinality for Enums)|Compiler checks type compatibility|
|**Querying**|`Wait_for` (if needed)|`FINAL` modifier (sparingly)|Route query based on consistency requirement|

### Transformation
```
Generate a transformation code for Flink engine using Kotlin language (on top of Java SDK)
```

This solution implements the **Dual-Store Convergence Layer** in Flink using Kotlin. It focuses on the "Fork-Join" pattern, addressing the specific pitfalls of **batching asymmetry** (ClickHouse needs large batches, ES needs near-real-time) and **type safety**.

### The Architecture: Flink "Split-Stream" Topology

This code performs three critical expert-level tasks:
1. **Ingest & Sanitize:** Reads raw JSON, handles nulls/defaults (fixing the "Null/Default Mismatch").
2. **Tagging:** Generates deterministic Idempotency Keys (fixing the "Duplicate Ghost").
3. **Route & buffer:** Splits the stream into two sinks with distinct buffering strategies (fixing the "Real-Time Write Trap").

### 1. Dependencies (`build.gradle.kts`)

You need the Flink Java SDK (interoperable with Kotlin) and the specific connectors.

org.apache.flink:flink-connector-elasticsearch-parent:3.1.0-1.20
[org.apache.flink:flink-connector-elasticsearch8:3.1.0-1.20](https://search.maven.org/artifact/org.apache.flink/flink-connector-elasticsearch8/3.1.0-1.20/jar)

```gradle
dependencies {
    implementation("org.apache.flink:flink-streaming-java:1.17.0")
    implementation("org.apache.flink:flink-connector-kafka:1.17.0")
    implementation("org.apache.flink:flink-connector-elasticsearch7:1.17.0") 
    implementation("org.apache.flink:flink-connector-jdbc:3.1.0") // For ClickHouse
    implementation("ru.yandex.clickhouse:clickhouse-jdbc:0.4.6")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.14.0")
}
```

### 2. The Transformation & Pipeline Code

```kotlin
import org.apache.flink.api.common.eventtime.WatermarkStrategy
import org.apache.flink.api.common.serialization.SimpleStringSchema
import org.apache.flink.connector.jdbc.JdbcConnectionOptions
import org.apache.flink.connector.jdbc.JdbcExecutionOptions
import org.apache.flink.connector.jdbc.JdbcSink
import org.apache.flink.connector.kafka.source.KafkaSource
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import org.apache.flink.streaming.connectors.elasticsearch.ElasticsearchSinkFunction
import org.apache.flink.streaming.connectors.elasticsearch7.ElasticsearchSink
import org.apache.http.HttpHost
import org.elasticsearch.action.index.IndexRequest
import org.elasticsearch.client.Requests
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.apache.flink.api.common.functions.RichMapFunction

  
// This class represents the external, potentially messy API contract  
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
)  
  
// ---------------------------------------------------------  
// 1. The Unified Data Model (The Abstraction)  
// ---------------------------------------------------------  
// This data class acts as the canonical contract between ES and CH.  
data class UnifiedEvent(  
    val eventId: String,      // Idempotency Key  
    val userId: String,  
    val eventType: String,  
    val payload: String,      // Full text for ES  
    val amount: Double,       // Metric for CH (Cleaned, no nulls)  
    val timestamp: Long  
)  
  
// ---------------------------------------------------------  
// 2. The Transformation Logic (Sanitization & Idempotency)  
// ---------------------------------------------------------  
class EventSanitizer : RichMapFunction<String, UnifiedEvent>() {  
    // 1. Declare the mapper as 'transient' (so Flink ignores it during serialization)  
    //    OR simply declare it as a nullable var without initializing it here.    @Transient  
    private lateinit var mapper: ObjectMapper  
  
    // 2. Override 'open'. This runs ONCE per parallel task slot on the WORKER.  
    override fun open(openContext: OpenContext) {  
        // Initialize the non-serializable object here  
        mapper = jacksonObjectMapper()  
    }  
  
    override fun map(rawJson: String): UnifiedEvent {  
        // 1. Deserialization: Strong typing happens here  
        // If JSON is malformed, this will throw. In prod, wrap in try-catch        // and send failures to a Side Output (Dead Letter Queue).        val raw = mapper.readValue(rawJson, RawInput::class.java)  
  
        // 2. Transformation & Sanitization  
        // logic moves from "node.get(...)" to standard Kotlin property access  
        return UnifiedEvent(  
            // PITFALL FIX: Idempotency  
            // If the source doesn't provide an ID, we generate a deterministic one            // based on the content (hash) rather than a random UUID to allow deduplication.            eventId = raw.eventId ?: "gen_${raw.hashCode()}",  
            userId = raw.userId ?: "anonymous",  
            eventType = raw.eventType ?: "unknown",  
            payload = raw.message ?: "",  
            // PITFALL FIX: Null/Default Mismatch  
            // Explicitly coerce nulls to 0.0 for ClickHouse safety  
            // PITFALL FIX: Null/Default Mismatch            // ClickHouse hates nulls in non-nullable columns. We coerce them here.            // If 'amount' is missing/null, we default to 0.0 to prevent CH rejection.  
            amount = raw.amount ?: 0.0,  
            timestamp = System.currentTimeMillis()  
        )  
    }  
}

// ---------------------------------------------------------
// 3. The Main Pipeline
// ---------------------------------------------------------
fun main() {
    val env = StreamExecutionEnvironment.getExecutionEnvironment()
    
    // CRITICAL: Checkpointing ensures consistency across both stores.
    // If CH write succeeds but ES fails, Flink rolls back Kafka offset.
    env.enableCheckpointing(5000) 

    // A. Source: Kafka
    val kafkaSource = KafkaSource.builder<String>()
        .setBootstrapServers("kafka:9092")
        .setTopics("raw_events")
        .setGroupId("dscl_processor_group")
        .setStartingOffsets(OffsetsInitializer.earliest())
        .setValueOnlyDeserializer(SimpleStringSchema())
        .build()

    val stream = env.fromSource(kafkaSource, WatermarkStrategy.noWatermarks(), "Kafka Source")
        .map(EventSanitizer())
        .name("Sanitization Layer")

    // B. Sink 1: Elasticsearch (Search & Document Store)
    // Strategy: Near Real-time (NRT). Flush often or rely on ES refresh interval.
    val esBuilder = ElasticsearchSink.Builder<UnifiedEvent>(
        listOf(HttpHost("elasticsearch", 9200, "http")),
        ElasticsearchSinkFunction<UnifiedEvent> { element, ctx, indexer ->
            val json = mapOf(
                "user_id" to element.userId,
                "message" to element.payload, // ES gets the text
                "timestamp" to element.timestamp
            )
            
            // PITFALL FIX: Use ID for upsert/dedup in ES
            val request = Requests.indexRequest()
                .index("events_idx")
                .id(element.eventId) 
                .source(json)
            
            indexer.add(request)
        }
    )
    // Batching for ES (optimized for HTTP overhead vs latency)
    esBuilder.setBulkFlushMaxActions(500) 
    stream.addSink(esBuilder.build()).name("Elasticsearch Sink")

    // C. Sink 2: ClickHouse (OLAP Store)
    // Strategy: Heavy Batching. ClickHouse thrives on large inserts.
    // PITFALL FIX: The "Real-Time Write Trap".
    // We force Flink to buffer up to 5000 rows or 5 seconds, whichever comes first.
    val jdbcExecutionOptions = JdbcExecutionOptions.builder()
        .withBatchSize(5000) 
        .withBatchIntervalMs(5000) 
        .withMaxRetries(3)
        .build()

    val jdbcOptions = JdbcConnectionOptions.JdbcConnectionOptionsBuilder()
        .withUrl("jdbc:clickhouse://clickhouse:8123/default")
        .withDriverName("ru.yandex.clickhouse.ClickHouseDriver")
        .withUsername("default")
        .build()

    // PITFALL FIX: ReplacingMergeTree support
    // We insert the 'sign' logic or version column if using CollapsingMergeTree,
    // otherwise standard insertion.
    val chQuery = """
        INSERT INTO user_activity_log 
        (event_id, user_id, amount, event_time) 
        VALUES (?, ?, ?, ?)
    """

    stream.addSink(JdbcSink.sink(
        chQuery,
        { statement, event ->
            statement.setString(1, event.eventId)
            statement.setString(2, event.userId)
            statement.setDouble(3, event.amount)
            statement.setLong(4, event.timestamp)
        },
        jdbcExecutionOptions,
        jdbcOptions
    )).name("ClickHouse Sink")

    env.execute("Dual-Store Convergence Pipeline")
}
```

### 3. Implementation Analysis: Why this works

1. **State Isolation**: By mapping raw JSON to `UnifiedEvent` _before_ the split, we ensure that schema logic exists in exactly one place (the code), preventing the "Schema Drift" pitfall where ES and CH receive slightly different data interpretations.
2. **Explicit Type Coercion (`EventSanitizer`)**: We handle the null-to-zero conversion in Kotlin. This is safer than relying on ClickHouse `Nullable` types which degrade performance.
3. **Divergent Buffering**:
    - **ES Sink**: `setBulkFlushMaxActions(500)` ensures documents appear relatively quickly for search.
    - **CH Sink**: `withBatchSize(5000)` prevents "Too many parts" errors in ClickHouse.
4. **Consistency**: `env.enableCheckpointing` ensures that if the ClickHouse batch fails (e.g., network blip), the Kafka offset is not committed, and the batch is retried.

### Next Step

To complete the abstraction, you need to define the **Database Schema** that matches this pipeline. Would you like me to generate the **DDL scripts** (ClickHouse `CREATE TABLE` with `ReplacingMergeTree` and ES `PUT _template`) to ensure the storage layer matches this code perfectly?