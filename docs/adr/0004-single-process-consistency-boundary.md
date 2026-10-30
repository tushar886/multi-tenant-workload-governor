# ADR 0004: Explicit Single-Process Consistency Boundary

- **Status:** Accepted
- **Context:** A simple in-memory engine cannot safely coordinate independent scheduler replicas.
- **Decision:** Serialize state transitions under one process-local lock and document the single-writer boundary. Do not imply distributed correctness.
- **Consequences:** This is suitable for algorithm evaluation and local tests. Multi-replica deployment requires partition ownership or transactional state transitions, fencing, durable queues, and reconciliation.
