package io.github.tushar886.governor.scheduling;

import io.github.tushar886.governor.domain.WorkloadRequest;
import io.github.tushar886.governor.domain.TenantPolicy;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Selects among eligible tenant queue heads using effective priority, then weighted
 * virtual finish time. Lower virtual finish time wins. Cost is dominant normalized
 * resource share multiplied by estimated runtime; tenant weight scales the finish delta.
 *
 * This is a practical approximation of weighted fair queuing for heterogeneous jobs,
 * not a formal GPS scheduler and not a deadline guarantee.
 */
public final class FairShareScheduler {
    private final Duration agingInterval;
    private final int maxAgingBoost;
    private final Map<String, Double> tenantVirtualFinish = new HashMap<>();
    private double virtualTime;

    public FairShareScheduler(Duration agingInterval, int maxAgingBoost) {
        if (agingInterval.isNegative() || agingInterval.isZero()) throw new IllegalArgumentException("agingInterval must be positive");
        if (maxAgingBoost < 0 || maxAgingBoost > 3) throw new IllegalArgumentException("maxAgingBoost must be 0..3");
        this.agingInterval = agingInterval;
        this.maxAgingBoost = maxAgingBoost;
    }

    public int effectivePriority(WorkloadRequest request, Instant now) {
        long waitedSeconds = Math.max(0, Duration.between(request.submittedAt(), now).getSeconds());
        long intervals = waitedSeconds / agingInterval.getSeconds();
        int boost = (int) Math.min(maxAgingBoost, intervals);
        return Math.min(4, request.priority().rank() + boost);
    }

    public Selection select(Collection<QueueEntry> eligible, Instant now,
                            long clusterCpuMillis, long clusterMemoryMiB) {
        if (eligible.isEmpty()) return null;
        List<QueueEntry> ordered = new ArrayList<>(eligible);
        ordered.sort(Comparator
                .comparingInt((QueueEntry e) -> effectivePriority(e.request(), now)).reversed()
                .thenComparingDouble(e -> finishTime(e, clusterCpuMillis, clusterMemoryMiB))
                .thenComparingLong(QueueEntry::sequence));
        QueueEntry selected = ordered.getFirst();
        TenantPolicy policy = selected.policy();
        double finish = finishTime(selected, clusterCpuMillis, clusterMemoryMiB);
        tenantVirtualFinish.put(policy.tenantId(), finish);
        virtualTime = Math.max(virtualTime, finish);
        return new Selection(selected, effectivePriority(selected.request(), now), finish);
    }

    private double finishTime(QueueEntry entry, long clusterCpu, long clusterMemory) {
        double previous = Math.max(virtualTime, tenantVirtualFinish.getOrDefault(entry.policy().tenantId(), 0.0));
        return previous + cost(entry.request(), clusterCpu, clusterMemory) / entry.policy().weight();
    }

    private double cost(WorkloadRequest request, long clusterCpu, long clusterMemory) {
        double cpuShare = (double) request.resources().cpuMillis() / clusterCpu;
        double memoryShare = (double) request.resources().memoryMiB() / clusterMemory;
        double dominantShare = Math.max(cpuShare, memoryShare);
        return Math.max(0.0001, dominantShare * request.estimatedRuntimeSeconds());
    }

    public record QueueEntry(WorkloadRequest request, TenantPolicy policy, long sequence) {}
    public record Selection(QueueEntry entry, int effectivePriorityRank, double virtualFinishTime) {}
}
