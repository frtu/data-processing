## Dual-Store Convergence Layer (DSCL)

### Summary

This framework aims to resolve inherent & dynamic consistency conflict between **Elasticsearch** (Search/Row-Store) and **ClickHouse** (Analytics/Column-Store), preventing data corruption and "phantom reads."

- **The Architecture:** A **Fork-Join Flink Topology** that decouples logical entities from physical storage. It ingests once, leverage a deterministic **Idempotency Key**, and splits the stream based on intent (Search vs. Compute).
- **Consistency Enforcement:**
  - **Atomicity:** Uses Flink Checkpointing to guarantee atomic dual-writes; if one store fails, the entire batch rolls back.
  - **Mutability:** Bridges the gap between ES's "Real-Time Updates" and CH's "Immutable Logs" using `ReplacingMergeTree` strategies automatically managed by the framework.
  - **Barrier:** Marker allowing to measure drift & eventually used for query 
- **The Fluent DSL:** A strongly-typed **Kotlin API** acts as a guardrail. It hides physical clients, enforcing schema strictness and preventing anti-patterns like synchronous ClickHouse inserts or unversioned updates.
- **Key Outcome:** Eliminates **Schema Drift**, **Partial Failures**, and **Write Asymmetry**, ensuring that what users search for (ES) matches what they analyze (CH).

Value of this Approach:
- Single Ingestion Point: No data divergence from the start
- Intent-Based Splitting: handle the search vs. analytics dichotomy
- Automatic Replacing/MergeTree Management: abstracted away CH's complexity while preserving its power
- Compile-Time Guardrails: Preventing the most dangerous anti-patterns before they happen

Recovery & Edge Cases:
- Schema Evolution During Splits: How does the Fork-Join handle live schema changes that affect either ES or CH differently?
- Partial Recovery Scenarios: When Flink checkpoints recover, how do you detect and resolve "limbo states" where one store committed and the other rolled back?

Performance Under Pressure:
- Backpressure Handling: When CH can't keep up with high-frequency FinTech data, how does your topology prevent ES from getting blocked?

Reconciliation
- Real-Time vs. Batch Reconciliation: In high-frequency trading, how do you balance the need for immediate ES updates with CH's batch optimization?
- Cross-Stream Dependencies: What happens when enrichment data needed for the split decision itself comes from either ES or CH?

### Details

Here is the summary of the critical consistency constraints and the design for a **Fluent API (DSL)** that acts as a guardrail, preventing developers from coding "bad patterns" that violate these constraints.

### Part 1: The "Consistency Gap" Summary

You are dealing with two systems that sit on opposite ends of the consistency spectrum. The danger lies in the **Time-to-Consistency (TTC)** gap.

|**Constraint**|**Elasticsearch (Search Engine)**|**ClickHouse (OLAP Engine)**|**The Danger Zone (Inconsistency)**|
|---|---|---|---|
|**Visibility**|**Near Real-Time (~1s)**. `Refresh` makes data searchable almost instantly.|**Immediate Insert, Eventual Result**. Data is on disk, but results (sums/avgs) are incorrect until async deduplication occurs.|**The "Phantom Read"**: User updates a record, searches for it (sees new version in ES), then looks at dashboard (sees old version in CH).|
|**Mutability**|**Mutable**. Supports `_update` (read-modify-write) efficiently for single docs.|**Immutable (mostly)**. `ALTER UPDATE` is heavy/async. Designed for append-only logs.|**The "Zombie Record"**: Deleting a record in ES is instant. In CH, it requires a `TTL` or `Sign` column, leaving "dead" rows visible to raw queries for hours.|
|**Ordering**|**Version-Based**. Last write wins based on `_version` or external version numbers.|**Arbitrary**. Ingestion order is not guaranteed. Deduplication relies on `ORDER BY` keys during merge.|**The "Race Condition"**: Two events arrive out of order. ES handles it via versioning. CH might simply append both, skewing metrics.|
|**Atomicity**|**Document Level**. Single doc writes are atomic.|**Block Level**. Writes are atomic per batch (block).|**The "Partial Failure"**: The network fails after writing to ES but before CH. Now your search index has data that your analytics DB completely lacks.|

**The Core Challenge: The Consistency Gap** Elasticsearch and ClickHouse are fundamentally different engines. Attempting to synchronize them manually leads to unavoidable failures:

- **Visibility Mismatch:** Data appears instantly in Elasticsearch (Searchable) but lags in ClickHouse (Analytical), causing "phantom reads" where dashboards contradict search results.
- **Mutation Asymmetry:** Elasticsearch handles updates gracefully; ClickHouse treats updates as heavy, asynchronous operations, leading to "zombie records" (deleted data that remains visible).
- **Type Safety:** Elasticsearch tolerates loose schemas (nulls, dynamic fields); ClickHouse demands strict typing, causing pipeline crashes on minor schema drifts.
---

### Part 2: The Fluent API (The Guardrail)

To prevent developers from manually falling into these traps, the Fluent API must **abstract away the physical write operations**. It should force the developer to declare _intent_ and _identity_, while the framework handles the mechanics.

#### 1. The Design Philosophy

- **Hide the Clients:** Developers never access `RestHighLevelClient` or `JdbcTemplate` directly.
- **Enforce Identity:** You cannot write data without an `Idempotency Key`.
- **Restrict Verbs:** You cannot call `.update()` on a dataset defined as "Append-Only".

#### 2. The Fluent DSL (Kotlin Example)

This API compiles down to the Flink Topology or Worker logic we defined earlier.

Kotlin
```
// BAD PATTERN (Prevented by not exposing these clients)
// esClient.index(doc)
// chClient.insert(row) -> If this fails, data is inconsistent.

// GOOD PATTERN (The Fluent DSL)
DataPipeline.source(kafkaEvents)
    .sanitize<RawInput>() // Forces the strongly typed cleaning we wrote
    .identifyBy { it.eventId } // 1. CONSTRAINT: Must define Idempotency Key
    .defineConsistency(ConsistencyLevel.EVENTUAL) // 2. CONSTRAINT: Acknowledge the gap
    .route { event ->
        // 3. CONSTRAINT: Intent-based Routing (Read vs. Compute)
        
        // A. Search Intent (Targets Elasticsearch)
        // The API automatically handles Versioning and Null-checks
        searchable {
            index("user_activity")
            searchFields(event.userId, event.message)
            storeFields(event.status) // Non-searchable retrieval fields
        }

        // B. Analytical Intent (Targets ClickHouse)
        // The API automatically buffers, handles batching, and coercing types
        analytical {
            table("user_activity_metrics")
            dimensions(event.userId, event.region)
            metrics(event.amount, event.durationMs)
            
            // Critical: The API enforces the logic for handling updates in CH
            // by automatically appending the required 'Sign' or 'Version' column
            strategy(WriteStrategy.COLLAPSING_MERGE_TREE) 
        }
    }
    .execute()
```

### Part 3: How the API Prevents Pitfalls

#### 1. Preventing "Partial Failure" (Atomicity)

- **The Problem:** Developer writes `try { writeES(); writeCH(); } catch ...`
- **The API Fix:** The `.execute()` method wraps the entire dual-write operation in a **Two-Phase Commit** (or relies on Flink Checkpointing). If the CH batch fails, the stream rewinds. The developer _cannot_ write uncoordinated code because they don't control the commit.

#### 2. Preventing "Schema Drift"

- **The Problem:** Sending a String to a CH Long column creates a "poison pill" that halts ingestion.
- **The API Fix:** The `.metrics()` method in the DSL accepts only `Number` types. The compiler prevents passing `event.message` (a String) to an analytical metric field.


#### 3. Preventing "Mutation Asymmetry"

- **The Problem:** Calling "Update" on a ClickHouse stream repeatedly.
- **The API Fix:** If the user selects `strategy(WriteStrategy.APPEND_ONLY)`, the `.update()` method is not available on the interface. They are forced to use `strategy(WriteStrategy.REPLACING_MERGE_TREE)` if they want mutable data, which forces them to provide a `version` column.


#### 4. Enforcing Idempotency

- **The Problem:** Forgetting to set a primary key, leading to duplicates in CH on retry.
- **The API Fix:** The `.identifyBy {}` clause is mandatory. The code won't compile without it. This ensures that even if Flink retries a batch 10 times, the `event_id` remains constant, allowing ClickHouse to deduplicate it later.

## To be explored

Leverage `Barrier` to evaluate divergence & eventually using it during query to have strict or relaxed consistency between dual store.