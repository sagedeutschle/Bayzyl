package com.bayzyl;

public record ClipboardPasteRequest(
        int rotation,
        boolean ignoreAir,
        String at,
        boolean selectAfterPaste,
        boolean previewOnly,
        boolean confirm
) {
}
