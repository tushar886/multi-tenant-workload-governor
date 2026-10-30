package io.github.tushar886.governor.api;

public record SubmissionDecision(SubmissionStatus status, DecisionReason reason, String detail) {
    public static SubmissionDecision queued(String detail) {
        return new SubmissionDecision(SubmissionStatus.QUEUED, DecisionReason.ACCEPTED_TO_QUEUE, detail);
    }
    public static SubmissionDecision rejected(DecisionReason reason, String detail) {
        return new SubmissionDecision(SubmissionStatus.REJECTED, reason, detail);
    }
}
