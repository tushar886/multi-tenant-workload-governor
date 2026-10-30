package io.github.tushar886.governor.domain;

/** Resource request/accounting vector. CPU is expressed in millicores; memory in MiB. */
public record ResourceVector(long cpuMillis, long memoryMiB) {
    public ResourceVector {
        if (cpuMillis < 0 || memoryMiB < 0) {
            throw new IllegalArgumentException("Resource values cannot be negative");
        }
    }

    public boolean fitsWithin(ResourceVector limit) {
        return cpuMillis <= limit.cpuMillis && memoryMiB <= limit.memoryMiB;
    }

    public ResourceVector plus(ResourceVector other) {
        return new ResourceVector(Math.addExact(cpuMillis, other.cpuMillis),
                Math.addExact(memoryMiB, other.memoryMiB));
    }

    public ResourceVector minus(ResourceVector other) {
        long cpu = cpuMillis - other.cpuMillis;
        long memory = memoryMiB - other.memoryMiB;
        if (cpu < 0 || memory < 0) {
            throw new IllegalStateException("Resource accounting underflow");
        }
        return new ResourceVector(cpu, memory);
    }

    public boolean isZero() {
        return cpuMillis == 0 && memoryMiB == 0;
    }
}
