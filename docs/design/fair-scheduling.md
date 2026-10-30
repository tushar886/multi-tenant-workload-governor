# Fair Scheduling

## Policy

The reference scheduler applies three stages:

1. Determine effective priority using bounded aging.
2. Consider only each tenant's first currently eligible queued workload.
3. Within the highest effective priority, choose the lowest weighted virtual finish time.

The finish-time increment is:

`cost = max(cpu_request / cluster_cpu, memory_request / cluster_memory) * estimated_runtime`

`finish = max(global_virtual_time, tenant_last_finish) + cost / tenant_weight`

The dominant normalized resource share accounts for CPU-heavy and memory-heavy jobs. Runtime is an estimate and therefore affects ordering quality, not correctness.

## Why per-tenant queues?

A single global FIFO lets one tenant flood the queue and place unrelated tenants behind its backlog. Per-tenant queues provide isolation and enable weighted service allocation.

The implementation preserves FIFO within each tenant. If the first item is temporarily ineligible due to tenant budget, later items from that tenant are not allowed to bypass it. This avoids uncontrolled intra-tenant reordering; a production system may support explicit queue lanes by workload class.

## Priority aging

Aging raises effective priority by one rank per configured interval, up to a bounded boost. This reduces indefinite starvation of low-priority work, but it does not guarantee a deadline. Aging cannot bypass hard quotas or cluster capacity.

## Trade-offs and alternatives

| Algorithm | Strength | Limitation |
|---|---|---|
| FIFO | Simple, predictable | No tenant isolation |
| Strict priority | Protects critical work | Starvation risk |
| Round robin | Simple tenant fairness | Ignores workload size |
| Weighted round robin | Relative tenant shares | Job size heterogeneity |
| WFQ / virtual finish | Accounts for normalized job cost | Approximation depends on runtime estimates |
| Dominant Resource Fairness | Multi-resource fairness model | More complex with discrete jobs and per-tenant caps |

The reference implementation is a weighted virtual-finish approximation. It should not be described as formal DRF or a hard real-time scheduler.
