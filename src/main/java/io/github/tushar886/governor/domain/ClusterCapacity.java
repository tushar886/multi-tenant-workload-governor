package io.github.tushar886.governor.domain;

import java.util.Objects;

public record ClusterCapacity(ResourceVector resources, int maxConcurrentWorkloads) {
    public ClusterCapacity {
        Objects.requireNonNull(resources, "resources");
        if (resources.isZero()) throw new IllegalArgumentException("Cluster capacity must be non-zero");
        if (maxConcurrentWorkloads < 1) throw new IllegalArgumentException("maxConcurrentWorkloads must be >= 1");
    }
}
