package com.bayzyl;

public record ClipboardStackRequest(
        boolean random,
        int count,
        String direction,
        boolean ignoreAir,
        boolean confirm,
        int spreadX,
        int spreadY,
        int spreadZ
) {
}
