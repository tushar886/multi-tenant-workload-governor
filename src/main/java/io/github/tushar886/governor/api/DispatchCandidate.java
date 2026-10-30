package io.github.tushar886.governor.api;

import io.github.tushar886.governor.domain.WorkloadRequest;

public record DispatchCandidate(WorkloadRequest request, long reservationVersion, int effectivePriorityRank,
                                double virtualFinishTime, String explanation) {}
