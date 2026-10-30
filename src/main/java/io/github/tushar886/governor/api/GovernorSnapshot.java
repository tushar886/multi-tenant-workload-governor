package io.github.tushar886.governor.api;

import io.github.tushar886.governor.domain.ResourceVector;
import java.util.Map;

public record GovernorSnapshot(
        ResourceVector clusterCapacity,
        ResourceVector clusterInUse,
        int runningWorkloads,
        int queuedWorkloads,
        Map<String, TenantUsage> tenants) {
    public record TenantUsage(ResourceVector inUse, int running, int queued, int weight) {}
}
