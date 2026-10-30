package io.github.tushar886.governor.core;

import io.github.tushar886.governor.accounting.UsageLedger;
import io.github.tushar886.governor.api.*;
import io.github.tushar886.governor.domain.*;
import io.github.tushar886.governor.policy.TenantPolicyRegistry;
import io.github.tushar886.governor.scheduling.FairShareScheduler;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Thread-safe, single-process reference implementation.
 *
 * All queue transitions and reservations are serialized by stateLock. Do not hold this
 * lock while calling an external executor. Production adapters should use an outbox and
 * asynchronous reconciliation rather than dispatching network calls in this critical section.
 */
public final class WorkloadGovernor {
    private final Object stateLock = new Object();
    private final TenantPolicyRegistry policies;
    private final FairShareScheduler scheduler;
    private final Clock clock;
    private final int globalQueueLimit;
    private ClusterCapacity capacity;
    private final UsageLedger ledger = new UsageLedger();
    private final Map<String, Deque<QueuedWorkload>> queues = new HashMap<>();
    private final Map<String, RunningWorkload> running = new HashMap<>();
    private final Map<String, String> workloadStates = new HashMap<>();
    private long sequence;
    private long reservationVersion;
    private int queuedCount;

    public WorkloadGovernor(TenantPolicyRegistry policies, ClusterCapacity capacity,
                            int globalQueueLimit, Duration agingInterval, int maxAgingBoost, Clock clock) {
        this.policies = Objects.requireNonNull(policies);
        this.capacity = Objects.requireNonNull(capacity);
        if (globalQueueLimit < 1) throw new IllegalArgumentException("globalQueueLimit must be >= 1");
        this.globalQueueLimit = globalQueueLimit;
        this.scheduler = new FairShareScheduler(agingInterval, maxAgingBoost);
        this.clock = Objects.requireNonNull(clock);
    }

    public SubmissionDecision submit(WorkloadRequest request) {
        Objects.requireNonNull(request);
        synchronized (stateLock) {
            if (workloadStates.containsKey(key(request))) {
                return SubmissionDecision.rejected(DecisionReason.DUPLICATE_WORKLOAD_ID, "Workload ID has already been seen");
            }
            Optional<TenantPolicy> policyOptional = policies.find(request.tenantId());
            if (policyOptional.isEmpty()) return SubmissionDecision.rejected(DecisionReason.UNKNOWN_TENANT, "No policy registered for tenant");
            TenantPolicy policy = policyOptional.get();
            if (request.priority().rank() > policy.maxPriorityRank()) {
                return SubmissionDecision.rejected(DecisionReason.PRIORITY_NOT_AUTHORIZED, "Requested priority exceeds tenant authorization");
            }
            if (!request.resources().fitsWithin(policy.maxResources())) {
                return SubmissionDecision.rejected(DecisionReason.REQUEST_EXCEEDS_TENANT_BUDGET, "Request exceeds tenant resource ceiling");
            }
            if (!request.resources().fitsWithin(capacity.resources()) ||
                    request.estimatedRuntimeSeconds() <= 0) {
                return SubmissionDecision.rejected(DecisionReason.REQUEST_EXCEEDS_CLUSTER_CAPACITY, "Request cannot fit within cluster capacity");
            }
            Deque<QueuedWorkload> tenantQueue = queues.computeIfAbsent(request.tenantId(), ignored -> new ArrayDeque<>());
            if (tenantQueue.size() >= policy.maxQueuedWorkloads()) {
                return SubmissionDecision.rejected(DecisionReason.TENANT_QUEUE_LIMIT_REACHED, "Tenant pending queue is full");
            }
            if (queuedCount >= globalQueueLimit) {
                return SubmissionDecision.rejected(DecisionReason.GLOBAL_QUEUE_LIMIT_REACHED, "Global pending queue is full");
            }
            tenantQueue.addLast(new QueuedWorkload(request, policy, ++sequence));
            queuedCount++;
            workloadStates.put(key(request), "QUEUED");
            return SubmissionDecision.queued("Accepted into bounded tenant queue");
        }
    }

    /** Returns the next eligible reservation. Repeated calls can fill available capacity. */
    public Optional<DispatchCandidate> dispatchNext() {
        synchronized (stateLock) {
            List<FairShareScheduler.QueueEntry> eligible = new ArrayList<>();
            for (Deque<QueuedWorkload> queue : queues.values()) {
                QueuedWorkload head = queue.peekFirst();
                if (head != null && canReserve(head)) {
                    eligible.add(new FairShareScheduler.QueueEntry(head.request, head.policy, head.sequence));
                }
            }
            FairShareScheduler.Selection selection = scheduler.select(eligible, clock.instant(),
                    capacity.resources().cpuMillis(), capacity.resources().memoryMiB());
            if (selection == null) return Optional.empty();

            QueuedWorkload chosen = removeBySequence(selection.entry().sequence());
            if (chosen == null) throw new IllegalStateException("Selected queue entry disappeared");
            queuedCount--;
            ledger.reserve(chosen.request.tenantId(), chosen.request.resources());
            long version = ++reservationVersion;
            running.put(key(chosen.request), new RunningWorkload(chosen.request, version));
            workloadStates.put(key(chosen.request), "RUNNING");
            String explanation = "Eligible under global and tenant budgets; selected by effective priority " +
                    selection.effectivePriorityRank() + " and weighted virtual finish ordering";
            return Optional.of(new DispatchCandidate(chosen.request, version,
                    selection.effectivePriorityRank(), selection.virtualFinishTime(), explanation));
        }
    }

    public boolean complete(String tenantId, String workloadId, long reservationVersion) {
        return finish(tenantId, workloadId, reservationVersion, "COMPLETED");
    }

    public boolean cancel(String tenantId, String workloadId) {
        synchronized (stateLock) {
            String key = tenantId + "/" + workloadId;
            RunningWorkload active = running.get(key);
            if (active != null) {
                finishLocked(key, active, "CANCELLED");
                return true;
            }
            Deque<QueuedWorkload> queue = queues.get(tenantId);
            if (queue == null) return false;
            boolean removed = queue.removeIf(item -> item.request.workloadId().equals(workloadId));
            if (removed) {
                queuedCount--;
                workloadStates.put(key, "CANCELLED");
            }
            return removed;
        }
    }

    private boolean finish(String tenantId, String workloadId, long version, String terminalState) {
        synchronized (stateLock) {
            String key = tenantId + "/" + workloadId;
            RunningWorkload active = running.get(key);
            if (active == null || active.reservationVersion != version) return false;
            finishLocked(key, active, terminalState);
            return true;
        }
    }

    private void finishLocked(String key, RunningWorkload active, String terminalState) {
        running.remove(key);
        ledger.release(active.request.tenantId(), active.request.resources());
        workloadStates.put(key, terminalState);
    }

    public void updateClusterCapacity(ClusterCapacity newCapacity) {
        synchronized (stateLock) { capacity = Objects.requireNonNull(newCapacity); }
    }

    public GovernorSnapshot snapshot() {
        synchronized (stateLock) {
            Map<String, GovernorSnapshot.TenantUsage> tenants = new TreeMap<>();
            Map<String, TenantPolicy> policySnapshot = policies.snapshot();
            for (Map.Entry<String, TenantPolicy> entry : policySnapshot.entrySet()) {
                UsageLedger.TenantUsage usage = ledger.tenant(entry.getKey());
                int queued = queues.getOrDefault(entry.getKey(), new ArrayDeque<>()).size();
                tenants.put(entry.getKey(), new GovernorSnapshot.TenantUsage(
                        usage.resources(), usage.running(), queued, entry.getValue().weight()));
            }
            return new GovernorSnapshot(capacity.resources(), ledger.clusterInUse(),
                    ledger.runningWorkloads(), queuedCount, Map.copyOf(tenants));
        }
    }

    private boolean canReserve(QueuedWorkload item) {
        UsageLedger.TenantUsage tenantUsage = ledger.tenant(item.request.tenantId());
        ResourceVector nextCluster = ledger.clusterInUse().plus(item.request.resources());
        ResourceVector nextTenant = tenantUsage.resources().plus(item.request.resources());
        return nextCluster.fitsWithin(capacity.resources())
                && nextTenant.fitsWithin(item.policy.maxResources())
                && ledger.runningWorkloads() < capacity.maxConcurrentWorkloads()
                && tenantUsage.running() < item.policy.maxConcurrentWorkloads();
    }

    private QueuedWorkload removeBySequence(long selectedSequence) {
        for (Deque<QueuedWorkload> queue : queues.values()) {
            Iterator<QueuedWorkload> iterator = queue.iterator();
            while (iterator.hasNext()) {
                QueuedWorkload item = iterator.next();
                if (item.sequence == selectedSequence) {
                    iterator.remove();
                    return item;
                }
            }
        }
        return null;
    }

    private String key(WorkloadRequest request) { return request.tenantId() + "/" + request.workloadId(); }

    private record QueuedWorkload(WorkloadRequest request, TenantPolicy policy, long sequence) {}
    private record RunningWorkload(WorkloadRequest request, long reservationVersion) {}
}
