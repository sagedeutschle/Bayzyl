package com.bayzyl;

public record ClipboardMoveRequest(
        int distance,
        String direction,
        boolean ignoreAir,
        boolean confirm
) {
}
