# Admission Control and Tenant Quotas

## Two-phase decision

Admission and dispatch answer different questions:

1. **Admission:** Is this request valid, authorized, and feasible in principle? Is there queue capacity?
2. **Dispatch:** Is there capacity now, and is this tenant the fair next candidate?

A workload that is valid but temporarily blocked is queued. A workload that can never fit is rejected immediately.

## Static rejection conditions

- Unknown tenant.
- Duplicate tenant/workload identifier.
- Requested priority above tenant entitlement.
- CPU or memory request above tenant maximum.
- CPU or memory request above current cluster capacity.
- Tenant or global pending queue limit reached.

## Dynamic dispatch gate

For each candidate, compute projected usage:

`projectedGlobal = currentGlobal + request`

`projectedTenant = currentTenant + request`

Dispatch only when all conditions hold:

- projected global CPU <= cluster CPU capacity
- projected global memory <= cluster memory capacity
- projected tenant CPU <= tenant CPU budget
- projected tenant memory <= tenant memory budget
- global running count < cluster max concurrency
- tenant running count < tenant max concurrency

The gate is atomic with reservation in this in-memory implementation.

## Resource units

- CPU: millicores (`1000` = one vCPU).
- Memory: MiB.
- Requests are envelopes, not observed consumption. The executor adapter should enforce limits and feed observed usage back into capacity planning.

## Quota changes

Production policy changes need explicit semantics:
- Lowering a quota below current usage should not kill workloads by default.
- New dispatches remain blocked until usage falls below the new quota.
- Policy versions should be recorded with each reservation.
- Emergency revocation/preemption is a separate policy with explicit workload safety semantics.
