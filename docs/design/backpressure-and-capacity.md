# Backpressure and Capacity Management

## Queue bounds

Both per-tenant and global queue limits are required:
- Per-tenant limits prevent one tenant from consuming all pending-state memory.
- Global limits protect the control plane from unbounded backlog.
- Rejections should include a machine-readable reason and retry guidance.

A production API should distinguish `429` (tenant/global admission pressure) from `400/403` (invalid or unauthorized request), and may return a retry-after hint for transient pressure.

## Capacity signals

Capacity should be modeled as allocatable capacity, not raw cluster size. Reserve headroom for:
- system and daemon workloads
- scheduler / executor overhead
- burst tolerance
- non-governed workloads
- failure-domain loss

For Kubernetes, allocatable CPU/memory and pod placement constraints matter. For Spark, executor sizing, dynamic allocation, shuffle overhead, and concurrent application limits matter.

## Dynamic capacity updates

A capacity decrease may put current usage above the new ceiling. The governor should:
- preserve existing reservations;
- stop dispatching new work;
- expose overcommit state and age;
- resume only when usage is back under the new ceiling.

It should not silently revoke reservations. Preemption needs a separate cooperative cancellation protocol.

## Adaptive concurrency

A production extension can tune concurrency using queue latency, executor startup latency, throttling, and failure rate. Adaptive limits must be bounded and slower to increase than to decrease to avoid oscillation. Never let an adaptive controller override hard safety quotas.
