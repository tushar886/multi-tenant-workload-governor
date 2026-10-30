# ADR 0001: Keep the Scheduling Core Framework-Independent

- **Status:** Accepted
- **Context:** The same governor should be reusable by Spark, Kubernetes, and internal worker-pool adapters. Framework coupling makes scheduling policy harder to test and migrate.
- **Decision:** Implement the core using Java 21 domain types and explicit adapter boundaries. HTTP, persistence, telemetry, and executor integrations remain outside the core.
- **Consequences:** The reference project is easy to compile and test, but does not provide a deployable HTTP service in this iteration. Production adapters can be added without changing scheduling invariants.
