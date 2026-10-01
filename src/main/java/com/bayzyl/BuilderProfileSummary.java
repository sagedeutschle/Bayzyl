package com.bayzyl;

public record BuilderProfileSummary(
        String name,
        long updatedAtEpochMillis,
        BuilderProfileType type,
        boolean hasConfig,
        boolean hasToolbar,
        int toolbarSlots
) {
}
