package io.github.tushar886.governor.accounting;

import io.github.tushar886.governor.domain.ResourceVector;
import java.util.HashMap;
import java.util.Map;

/** Mutable accounting state; callers must hold the governor's state lock. */
public final class UsageLedger {
    private ResourceVector clusterInUse = new ResourceVector(0, 0);
    private int runningWorkloads;
    private final Map<String, TenantUsage> tenants = new HashMap<>();

    public TenantUsage tenant(String tenantId) { return tenants.computeIfAbsent(tenantId, ignored -> new TenantUsage()); }
    public ResourceVector clusterInUse() { return clusterInUse; }
    public int runningWorkloads() { return runningWorkloads; }

    public void reserve(String tenantId, ResourceVector resources) {
        clusterInUse = clusterInUse.plus(resources);
        runningWorkloads++;
        TenantUsage usage = tenant(tenantId);
        usage.resources = usage.resources.plus(resources);
        usage.running++;
    }

    public void release(String tenantId, ResourceVector resources) {
        clusterInUse = clusterInUse.minus(resources);
        runningWorkloads--;
        TenantUsage usage = tenant(tenantId);
        usage.resources = usage.resources.minus(resources);
        usage.running--;
        if (runningWorkloads < 0 || usage.running < 0) throw new IllegalStateException("Running workload accounting underflow");
    }

    public Map<String, TenantUsage> tenants() { return tenants; }

    public static final class TenantUsage {
        private ResourceVector resources = new ResourceVector(0, 0);
        private int running;
        public ResourceVector resources() { return resources; }
        public int running() { return running; }
    }
}
