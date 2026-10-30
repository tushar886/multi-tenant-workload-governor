import io.github.tushar886.governor.api.DispatchCandidate;
import io.github.tushar886.governor.domain.*;
import io.github.tushar886.governor.policy.TenantPolicyRegistry;
import io.github.tushar886.governor.core.WorkloadGovernor;
import java.time.Clock;
import java.time.Duration;

/** Compile against the project classes; demonstrates the framework-independent API. */
public class QuickStart {
    public static void main(String[] args) {
        var policies = new TenantPolicyRegistry();
        policies.register(new TenantPolicy("finance-app", new ResourceVector(4_000, 8_192),
                2, 50, 5, WorkloadPriority.HIGH.rank()));
        var governor = new WorkloadGovernor(policies,
                new ClusterCapacity(new ResourceVector(16_000, 65_536), 20),
                500, Duration.ofMinutes(10), 2, Clock.systemUTC());

        var request = new WorkloadRequest("daily-ledger-rollup", "finance-app",
                WorkloadType.ANALYTICS, WorkloadPriority.HIGH,
                new ResourceVector(2_000, 4_096), Clock.systemUTC().instant(), 900);

        System.out.println(governor.submit(request));
        DispatchCandidate dispatch = governor.dispatchNext().orElseThrow();
        System.out.println(dispatch.explanation());
        // The executor adapter would submit the external job and later reconcile completion.
        governor.complete("finance-app", "daily-ledger-rollup", dispatch.reservationVersion());
        System.out.println(governor.snapshot());
    }
}
