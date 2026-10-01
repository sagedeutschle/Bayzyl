package com.bayzyl;

public record SelectionResizeRequest(
        int amount,
        String direction,
        boolean allDirections
) {
}
