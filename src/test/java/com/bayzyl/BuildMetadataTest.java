package com.bayzyl;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuildMetadataTest {

    @Test
    void builtJarCarriesOneZeroPointTwoVersionInManifestAndDescriptor() throws IOException {
        try (JarFile jar = builtJar()) {
            String manifestVersion = jar.getManifest().getMainAttributes().getValue("Implementation-Version");
            assertNotNull(manifestVersion);
            assertTrue(manifestVersion.startsWith("0.2."), manifestVersion);
            assertEquals("version: " + manifestVersion, descriptorVersionLine(jar));
        }
    }

    @Test
    void manifestNamesItsRevisionAndCarriesNoWallClockTime() throws IOException {
        try (JarFile jar = builtJar()) {
            Attributes attributes = jar.getManifest().getMainAttributes();
            String revision = attributes.getValue("Bayzyl-Revision");
            assertNotNull(revision);
            assertFalse(revision.isBlank());
            for (Object name : attributes.keySet()) {
                String key = name.toString().toLowerCase();
                assertFalse(key.contains("time") || key.contains("date") || key.contains("timestamp"), key);
            }
        }
    }

    @Test
    void buildInfoFallsBackToUnknownWithoutMetadata() {
        assertEquals(new BuildInfo("unknown", "unknown"), BuildInfo.from(new Manifest()));
        assertEquals(new BuildInfo("unknown", "unknown"), BuildInfo.from(null));

        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().put(Attributes.Name.IMPLEMENTATION_VERSION, "0.2.0-alpha.1");
        manifest.getMainAttributes().putValue("Bayzyl-Revision", "abc123def456-dirty");
        BuildInfo info = BuildInfo.from(manifest);
        assertEquals("0.2.0-alpha.1 (revision abc123def456-dirty)", info.describe());
    }

    @Test
    void buildInfoReadsTheBuiltJar() {
        BuildInfo info = BuildInfo.of(Path.of(System.getProperty("bayzyl.jar")).toFile());
        assertTrue(info.version().startsWith("0.2."), info.toString());
    }

    static JarFile builtJar() throws IOException {
        String path = System.getProperty("bayzyl.jar");
        assertNotNull(path, "the test task must pass the built jar as -Dbayzyl.jar");
        assertTrue(Files.isRegularFile(Path.of(path)), path);
        return new JarFile(path);
    }

    private static String descriptorVersionLine(JarFile jar) throws IOException {
        try (InputStream in = jar.getInputStream(jar.getEntry("plugin.yml"))) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).lines()
                    .filter(line -> line.startsWith("version:")).findFirst().orElseThrow();
        }
    }
}
