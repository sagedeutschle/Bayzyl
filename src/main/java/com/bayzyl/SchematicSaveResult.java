package com.bayzyl;

import java.io.File;

public record SchematicSaveResult(
        boolean success,
        String message,
        File file
) {
    public static SchematicSaveResult success(File file, String message) {
        return new SchematicSaveResult(true, message, file);
    }

    public static SchematicSaveResult failed(String message) {
        return new SchematicSaveResult(false, message, null);
    }
}
