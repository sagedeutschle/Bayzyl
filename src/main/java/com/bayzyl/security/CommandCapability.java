package com.bayzyl.security;

public enum CommandCapability {
    USE("bayzyl.use"),
    EDIT("bayzyl.edit"),
    GENERATE("bayzyl.generate"),
    GENSTRUCTURE("bayzyl.genstructure"),
    ADMIN_MODE("bayzyl.adminmode"),
    JAIL("bayzyl.jail"),
    WETOGGLE("bayzyl.wetoggle"),
    RUNTIME("bayzyl.runtime");

    private final String permission;

    CommandCapability(String permission) {
        this.permission = permission;
    }

    public String permission() {
        return permission;
    }
}
