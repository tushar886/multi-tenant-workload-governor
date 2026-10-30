package io.github.tushar886.governor.domain;

import java.time.Instant;
import java.util.Objects;

/** Immutable submission contract. workloadId must be unique within a tenant. */
public record WorkloadRequest(
        String workloadId,
        String tenantId,
        WorkloadType type,
        WorkloadPriority priority,
        ResourceVector resources,
        Instant submittedAt,
        long estimatedRuntimeSeconds) {

    public WorkloadRequest {
        workloadId = requireText(workloadId, "workloadId");
        tenantId = requireText(tenantId, "tenantId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(priority, "priority");
        Objects.requireNonNull(resources, "resources");
        Objects.requireNonNull(submittedAt, "submittedAt");
        if (resources.isZero()) throw new IllegalArgumentException("At least one resource must be requested");
        if (estimatedRuntimeSeconds <= 0) throw new IllegalArgumentException("estimatedRuntimeSeconds must be positive");
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        return value.trim();
    }
}
