package com.bayzyl;

import java.util.Locale;

public final class SelectionCommandParser {
    private SelectionCommandParser() {
    }

    public static SelectionResizeRequest parseResize(String verb, String[] args) {
        if (args.length == 0) {
            throw new IllegalArgumentException("Syntax: /" + verb + " <amount|all amount> [<direction>]");
        }

        boolean allDirections = false;
        int amountIndex = 0;
        if (args[0].equalsIgnoreCase("all")) {
            allDirections = true;
            amountIndex = 1;
            if (args.length < 2) {
                throw new IllegalArgumentException("Syntax: /" + verb + " <amount|all amount> [<direction>]");
            }
        }

        int amount;
        try {
            amount = Integer.parseInt(args[amountIndex]);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Amount must be a number.");
        }
        if (amount <= 0) {
            throw new IllegalArgumentException("Amount must be greater than 0.");
        }

        String direction = "forward";
        for (int i = amountIndex + 1; i < args.length; i++) {
            String token = args[i].toLowerCase(Locale.ROOT);
            if (!token.contains(":")) {
                direction = token;
                continue;
            }
            String[] parts = token.split(":", 2);
            String key = parts[0];
            String value = parts.length > 1 ? parts[1] : "";
            if (key.equals("direction") || key.equals("dir")) {
                direction = value;
            }
        }

        return new SelectionResizeRequest(amount, direction, allDirections);
    }
}
