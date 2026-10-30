package io.github.tushar886.governor.core;

import io.github.tushar886.governor.api.*;
import io.github.tushar886.governor.domain.*;
import io.github.tushar886.governor.policy.TenantPolicyRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.*;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class WorkloadGovernorTest {
    private Clock clock;
    private TenantPolicyRegistry registry;
    private WorkloadGovernor governor;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(Instant.parse("2026-10-30T10:00:00Z"), ZoneOffset.UTC);
        registry = new TenantPolicyRegistry();
        registry.register(policy("alpha", 2, 2, 1000, 4, 4_000, 8_000));
        registry.register(policy("beta", 1, 2, 1, 2, 2_000, 4_000));
        governor = new WorkloadGovernor(registry,
                new ClusterCapacity(new ResourceVector(4_000, 8_000), 3),
                10, Duration.ofMinutes(5), 2, clock);
    }

    @Test
    void rejectsUnknownTenantAndUnauthorizedPriority() {
        assertEquals(DecisionReason.UNKNOWN_TENANT, governor.submit(request("x", "missing",
                WorkloadPriority.LOW, 100, 100)).reason());
        assertEquals(DecisionReason.PRIORITY_NOT_AUTHORIZED, governor.submit(request("x", "beta",
                WorkloadPriority.CRITICAL, 100, 100)).reason());
    }

    @Test
    void rejectsRequestsThatCanNeverFit() {
        assertEquals(DecisionReason.REQUEST_EXCEEDS_CLUSTER_CAPACITY,
                governor.submit(request("too-big", "alpha", WorkloadPriority.NORMAL, 5_000, 100)).reason());
    }

    @Test
    void reservesAtDispatchAndReleasesExactlyOnce() {
        governor.submit(request("w1", "alpha", WorkloadPriority.NORMAL, 2_000, 2_000));
        DispatchCandidate candidate = governor.dispatchNext().orElseThrow();
        assertEquals(new ResourceVector(2_000, 2_000), governor.snapshot().clusterInUse());
        assertTrue(governor.complete("alpha", "w1", candidate.reservationVersion()));
        assertFalse(governor.complete("alpha", "w1", candidate.reservationVersion()));
        assertEquals(new ResourceVector(0, 0), governor.snapshot().clusterInUse());
    }

    @Test
    void enforcesGlobalCapacityAndQueuesUntilCompletion() {
        governor.submit(request("w1", "alpha", WorkloadPriority.NORMAL, 3_000, 6_000));
        governor.submit(request("w2", "beta", WorkloadPriority.NORMAL, 2_000, 2_000));
        DispatchCandidate first = governor.dispatchNext().orElseThrow();
        assertTrue(governor.dispatchNext().isEmpty());
        assertEquals(1, governor.snapshot().queuedWorkloads());
        governor.complete(first.request().tenantId(), first.request().workloadId(), first.reservationVersion());
        assertTrue(governor.dispatchNext().isPresent());
    }

    @Test
    void duplicateIdsAreRejectedAndQueuedWorkCanBeCancelled() {
        governor.submit(request("w1", "alpha", WorkloadPriority.NORMAL, 100, 100));
        assertEquals(DecisionReason.DUPLICATE_WORKLOAD_ID,
                governor.submit(request("w1", "alpha", WorkloadPriority.NORMAL, 100, 100)).reason());
        assertTrue(governor.cancel("alpha", "w1"));
        assertEquals(0, governor.snapshot().queuedWorkloads());
    }

    @Test
    void staleReservationVersionCannotReleaseNewWorkloadState() {
        governor.submit(request("w1", "alpha", WorkloadPriority.NORMAL, 100, 100));
        DispatchCandidate candidate = governor.dispatchNext().orElseThrow();
        assertFalse(governor.complete("alpha", "w1", candidate.reservationVersion() + 1));
        assertEquals(1, governor.snapshot().runningWorkloads());
    }

    private TenantPolicy policy(String id, int maxRunning, int maxQueued, int weight, int priority,
                                long cpu, long memory) {
        return new TenantPolicy(id, new ResourceVector(cpu, memory), maxRunning, maxQueued, weight, priority);
    }

    private WorkloadRequest request(String id, String tenant, WorkloadPriority priority, long cpu, long memory) {
        return new WorkloadRequest(id, tenant, WorkloadType.INGESTION, priority,
                new ResourceVector(cpu, memory), clock.instant(), 60);
    }
}
