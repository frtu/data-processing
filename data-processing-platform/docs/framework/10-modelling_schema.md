## Modelling

### Summary

Modelling aims to resolve static consistency conflict between **Elasticsearch** (Search/Row-Store) and **ClickHouse** (Analytics/Column-Store), preventing data corruption.

**The Architecture:** decouples logical entities from physical storage. 

- It ingests once, assigns a deterministic **Idempotency Key**, and splits the stream based on intent (Search vs. Compute).

FinTech-Specific Complexity:
- Compliance Versioning: How does your framework handle requirements like GDPR deletion or SOX immutability that have different implications for search vs. analytics?
- Regulatory Atomicity: How do you prove to auditors that financial transactions are truly atomic across both stores, especially during failure scenarios?

### Principles

```
Schema Evolution as First-Class Citizen: Making schema tracking and PII tagging core to the architecture rather than an afterthought. That's how you build compliance-by-design!
Pragmatic Consistency Model: Choosing eventual consistency with robust detection/repair over strict consistency that breaks under real-world pressure. Smart trade-off!
Operational Reality: Acknowledging that systems need human intervention capabilities, not just automatic recovery. That's battle-tested thinking!
```

Schema change means rebuild data store from scratch, so changes is predictable. Enablement happen using blue/green deployment method allowing to gracefully ramp up data, validate, deploy but also rollback.
Runtime data validation happen using total count & bucket data count. Bucketing happen by fixed date time using event/creation time when possible.
