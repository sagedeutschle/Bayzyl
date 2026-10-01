package com.bayzyl;

public record BuilderKitLoadResult(
        String name,
        BuilderKitScope scope,
        int placed,
        int cleared
) {
}
