package com.bayzyl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuildReproducibilityTest {
    @TempDir
    Path receipts;

    /** Entry order determinism is proven by scripts/verify-reproducible.sh (two isolated builds, one hash). */
    @Test
    void jarEntriesCarryNoFileTimestamps() throws IOException {
        try (JarFile jar = BuildMetadataTest.builtJar()) {
            Set<Long> times = new HashSet<>();
            for (JarEntry entry : java.util.Collections.list(jar.entries())) {
                times.add(entry.getTime());
            }
            assertEquals(1, times.size(), "every entry shares one normalized timestamp");
            int year = Instant.ofEpochMilli(times.iterator().next()).atZone(ZoneId.systemDefault()).getYear();
            assertTrue(year <= 1980, "timestamp is Gradle's constant, not the build time: " + year);
        }
    }

    @Test
    void receiptRefusesAMissingJarAndWritesNothing() throws Exception {
        Process process = receipt(receipts.resolve("missing.jar").toString());
        assertNotEquals(0, process.waitFor());
        try (var listing = Files.list(receipts)) {
            assertEquals(0, listing.count(), "no receipt for a jar that does not exist");
        }
    }

    @Test
    void receiptIdentifiesTheExactJar() throws Exception {
        Path jar = Path.of(System.getProperty("bayzyl.jar"));
        Process process = receipt(jar.toString());
        assertEquals(0, process.waitFor(), new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8));

        Path receipt = receipts.resolve(jar.getFileName() + ".receipt");
        List<String> lines = Files.readAllLines(receipt, StandardCharsets.UTF_8);
        String version;
        String revision;
        try (JarFile built = new JarFile(jar.toFile())) {
            version = built.getManifest().getMainAttributes().getValue("Implementation-Version");
            revision = built.getManifest().getMainAttributes().getValue("Bayzyl-Revision");
        }
        assertTrue(lines.contains("sha256: " + sha256(jar)), lines.toString());
        assertTrue(lines.contains("version: " + version), lines.toString());
        assertTrue(lines.contains("revision: " + revision), lines.toString());
        assertTrue(lines.stream().anyMatch(line -> line.matches("created: \\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}Z")), lines.toString());
    }

    private Process receipt(String jarPath) throws IOException {
        ProcessBuilder builder = new ProcessBuilder("bash", Path.of("scripts", "write-build-receipt.sh").toString(), jarPath);
        builder.environment().put("RECEIPT_DIR", receipts.toString());
        builder.redirectOutput(ProcessBuilder.Redirect.DISCARD);
        return builder.start();
    }

    private static String sha256(Path file) throws IOException, NoSuchAlgorithmException {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));
    }
}
