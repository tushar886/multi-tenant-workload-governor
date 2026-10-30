package io.github.tushar886.governor.domain;

public enum WorkloadPriority {
    CRITICAL(4), HIGH(3), NORMAL(2), LOW(1);

    private final int rank;

    WorkloadPriority(int rank) { this.rank = rank; }

    public int rank() { return rank; }
}
