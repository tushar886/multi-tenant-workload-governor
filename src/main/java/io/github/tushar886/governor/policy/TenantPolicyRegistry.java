package io.github.tushar886.governor.policy;

import io.github.tushar886.governor.domain.TenantPolicy;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Replaceable policy source; production implementations should version and audit changes. */
public final class TenantPolicyRegistry {
    private final Map<String, TenantPolicy> policies = new ConcurrentHashMap<>();

    public void register(TenantPolicy policy) { policies.put(policy.tenantId(), policy); }
    public Optional<TenantPolicy> find(String tenantId) { return Optional.ofNullable(policies.get(tenantId)); }
    public Map<String, TenantPolicy> snapshot() { return Map.copyOf(policies); }
}
