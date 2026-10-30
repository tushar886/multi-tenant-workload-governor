package io.github.tushar886.governor.domain;

import java.util.Objects;

/**
 * Immutable tenant budget. Weight affects fair-share ordering, not the hard quota.
 * maxPriorityRank is an authorization boundary: a tenant cannot self-assign a higher class.
 */
public record TenantPolicy(
        String tenantId,
        ResourceVector maxResources,
        int maxConcurrentWorkloads,
        int maxQueuedWorkloads,
        int weight,
        int maxPriorityRank) {

    public TenantPolicy {
        if (tenantId == null || tenantId.isBlank()) throw new IllegalArgumentException("tenantId is required");
        tenantId = tenantId.trim();
        Objects.requireNonNull(maxResources, "maxResources");
        if (maxConcurrentWorkloads < 1) throw new IllegalArgumentException("maxConcurrentWorkloads must be >= 1");
        if (maxQueuedWorkloads < 0) throw new IllegalArgumentException("maxQueuedWorkloads cannot be negative");
        if (weight < 1 || weight > 1000) throw new IllegalArgumentException("weight must be between 1 and 1000");
        if (maxPriorityRank < 1 || maxPriorityRank > 4) throw new IllegalArgumentException("maxPriorityRank must be between 1 and 4");
    }
}
