# ADR 0003: Weighted Virtual-Finish Scheduling with Bounded Aging

- **Status:** Accepted
- **Context:** FIFO lacks tenant isolation; strict priority can starve lower classes; round robin ignores heterogeneous job size.
- **Decision:** Use effective priority with bounded aging, then weighted virtual finish time based on dominant normalized resource share and estimated runtime.
- **Consequences:** The policy is explainable and practical for a reference implementation. Runtime estimates can be inaccurate; this is not formal DRF, GPS, or a deadline scheduler. Production fairness must be measured and tuned from observed service share.
