# Operational Runbook

## Symptom: queue depth rising

1. Break down queue depth and oldest age by tenant and priority.
2. Check cluster allocatable capacity and running reservations.
3. Check whether a tenant quota is binding or global capacity is binding.
4. Inspect executor startup latency, throttling, and failure rate.
5. Apply an explicit capacity or policy change; do not bypass admission globally.

## Symptom: one tenant dominates service

1. Compare actual service share with configured weight over a meaningful window.
2. Check whether higher priority classes are consuming the pool.
3. Inspect request sizes and estimated runtime accuracy.
4. Confirm the tenant is not operating multiple identities to evade quotas.
5. Adjust weights or budgets through audited policy changes.

## Symptom: resources appear leaked

1. Find reservations with no recent backend state update.
2. Query the execution backend by deterministic external ID.
3. If still running, retain reservation and restore reconciliation.
4. If terminal, release through the idempotent completion path.
5. If backend state is unknown, quarantine the reservation; do not free capacity optimistically.

## Safety controls

- Never reset the ledger while external workloads may still be running.
- Never lower global capacity below current usage without accepting dispatch pause.
- Never treat queue rejection as successful admission.
- Never dispatch based only on stale metrics; reservation must be transactional.
