# Production Evolution: From Reference Core to Firm-Wide Service

The current implementation is deliberately single-process and in-memory. The following changes are prerequisites before using it as a shared production control plane.

## 1. Durable state and transactional dispatch intent

Persist workload state, tenant queue position, reservation, policy version, and an outbox event in one database transaction. A dispatcher consumes the outbox asynchronously. This closes the crash window between reservation and external submission.

Suggested entities:
- `tenant_policy(tenant_id, version, limits, weight, priority_ceiling, effective_at)`
- `workload(tenant_id, workload_id, state, priority, resource_request, submitted_at, idempotency_key)`
- `reservation(reservation_id, workload_id, version, resources, state, expires_at)`
- `dispatch_outbox(event_id, reservation_id, payload, published_at)`
- `execution_reference(workload_id, backend, external_id, last_observed_state)`

## 2. Partition ownership

Do not let multiple scheduler replicas mutate the same tenant queues and resource ledger independently. Partition by scheduling domain / cluster pool and elect one active owner per partition, or use transactional compare-and-set state transitions. A lease alone is insufficient unless fencing tokens are checked by every state mutation.

## 3. Idempotency

Use `(tenant_id, idempotency_key)` as the submission uniqueness boundary. External executor submission must use a deterministic token derived from the internal workload ID. Retry after timeout by querying the backend before creating another external job.

## 4. Reservation lifecycle

Use explicit states: `PENDING`, `QUEUED`, `RESERVED`, `SUBMITTING`, `RUNNING`, `SUCCEEDED`, `FAILED`, `CANCEL_REQUESTED`, `CANCELLED`, `TIMED_OUT`, `RECONCILIATION_REQUIRED`.

Reservations should have a lease/heartbeat and reconciliation policy. Expiry must not immediately free capacity if the external workload may still be running; first establish external state or apply a safe fencing/cancellation protocol.

## 5. Scheduler scalability

The reference scheduler scans tenant queues. At large scale, maintain an eligible-tenant index or heap, update only affected tenants on capacity changes, and shard by independent resource pools. Measure scheduler decision latency and lock contention before optimizing.

## 6. Fairness observability

Track:
- queue wait p50/p95/p99 by tenant and priority;
- service share vs configured weight;
- dominant resource share by tenant;
- starvation age and aging boosts;
- rejection rates by reason;
- reservation age and orphan count;
- executor submission and reconciliation latency.

Avoid tenant IDs as unbounded metric labels. Use logs/traces for per-tenant diagnosis and bounded cohorts for metrics.

## 7. Security and governance

Authenticate producer identity, derive tenant from trusted claims, authorize workload type and priority, apply per-tenant rate limits, audit policy changes, and redact sensitive workload metadata. A tenant must not be able to self-upgrade its priority or resource budget.
