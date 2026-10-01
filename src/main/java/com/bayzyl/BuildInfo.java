package com.bayzyl;

import java.io.File;
import java.io.IOException;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

/** The version and revision stamped into the plugin jar's manifest at build time. */
record BuildInfo(String version, String revision) {
    static final String UNKNOWN = "unknown";

    static BuildInfo from(Manifest manifest) {
        if (manifest == null) {
            return new BuildInfo(UNKNOWN, UNKNOWN);
        }
        Attributes attributes = manifest.getMainAttributes();
        return new BuildInfo(valueOr(attributes.getValue(Attributes.Name.IMPLEMENTATION_VERSION)),
                valueOr(attributes.getValue("Bayzyl-Revision")));
    }

    static BuildInfo of(File jar) {
        if (jar == null) {
            return new BuildInfo(UNKNOWN, UNKNOWN);
        }
        try (JarFile file = new JarFile(jar)) {
            return from(file.getManifest());
        } catch (IOException exception) {
            return new BuildInfo(UNKNOWN, UNKNOWN);
        }
    }

    String describe() {
        return version + " (revision " + revision + ")";
    }

    private static String valueOr(String value) {
        return value == null || value.isBlank() ? UNKNOWN : value;
    }
}
