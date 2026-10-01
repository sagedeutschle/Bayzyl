package com.bayzyl.security;

import org.bukkit.command.CommandSender;

import java.util.Objects;
import java.util.function.Predicate;

public final class BayzylAccess {
    public static final String ADMIN_PERMISSION = "bayzyl.admin";

    public boolean allowed(CommandSender sender, CommandCapability capability) {
        return sender != null && allowed(sender::hasPermission, capability);
    }

    public boolean allowed(Predicate<String> hasPermission, CommandCapability capability) {
        Objects.requireNonNull(hasPermission, "hasPermission");
        if (hasPermission.test(ADMIN_PERMISSION)) {
            return true;
        }
        return capability != null && hasPermission.test(capability.permission());
    }
}
