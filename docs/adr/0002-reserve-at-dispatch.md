# ADR 0002: Reserve Capacity at Dispatch, Not Submission

- **Status:** Accepted
- **Context:** Queued work should not consume running capacity. Reserving at submission can let old queued jobs hoard capacity and make utilization difficult to reason about.
- **Decision:** Perform static feasibility checks at submission. Acquire global and tenant resource reservations atomically only when a queued workload is selected for dispatch.
- **Consequences:** Queue depth is decoupled from running resource use. The scheduler must re-check all dynamic budgets at dispatch time. A durable production implementation must commit reservation and dispatch intent transactionally.
