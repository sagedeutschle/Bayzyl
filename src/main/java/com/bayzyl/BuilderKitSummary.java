package com.bayzyl;

import java.util.List;

public record BuilderKitSummary(
        String name,
        long updatedAtEpochMillis,
        BuilderKitScope scope,
        boolean builtIn,
        int itemCount,
        String theme,
        String note,
        String iconMaterialKey,
        List<String> aliases
) {
}
