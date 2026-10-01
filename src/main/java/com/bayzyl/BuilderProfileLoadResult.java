package com.bayzyl;

public record BuilderProfileLoadResult(
        String name,
        BuilderProfileType type,
        boolean appliedConfig,
        boolean appliedToolbar,
        int toolbarPlaced,
        int toolbarCleared,
        int toolbarSkipped
) {
}
