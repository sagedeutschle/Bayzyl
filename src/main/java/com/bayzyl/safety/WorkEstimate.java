package com.bayzyl.safety;

/** Immutable result of a pure operation preflight. */
public record WorkEstimate(
        long workUnits,
        boolean confirmationRequired,
        boolean hardRejected,
        String reason
) {
    public WorkEstimate {
        if (workUnits < 0L) {
            throw new IllegalArgumentException("Work units cannot be negative.");
        }
        if (hardRejected) {
            confirmationRequired = false;
        }
        if (reason == null || reason.isBlank()) {
            reason = "Operation estimate is unavailable.";
        }
    }

    public boolean permits(boolean confirmed) {
        return !hardRejected && (!confirmationRequired || confirmed);
    }
}
