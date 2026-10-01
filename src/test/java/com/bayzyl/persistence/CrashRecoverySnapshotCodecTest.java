package com.bayzyl.persistence;

import com.bayzyl.persistence.RecoverySnapshot.ClipboardRecord;
import com.bayzyl.persistence.RecoverySnapshot.DetachedLocation;
import com.bayzyl.persistence.RecoverySnapshot.DetachedSelection;
import com.bayzyl.persistence.RecoverySnapshot.EntityRecord;
import com.bayzyl.persistence.RecoverySnapshot.ItemPayload;
import com.bayzyl.persistence.RecoverySnapshot.Lifecycle;
import com.bayzyl.persistence.RecoverySnapshot.NudgeRecord;
import com.bayzyl.persistence.RecoverySnapshot.SessionRecord;
import com.bayzyl.persistence.RecoverySnapshot.SessionValue;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CrashRecoverySnapshotCodecTest {
    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID OTHER = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private final CrashRecoverySnapshotCodec codec = new CrashRecoverySnapshotCodec();

    @Test
    void unknownFormatVersionFailsClosed() {
        assertThrows(IOException.class, () -> codec.decodeStrict(bytes("formatVersion: 99\n")));
        assertThrows(IOException.class, () -> codec.decodeStrict(bytes("formatVersion: one\n")));
    }

    @Test
    void malformedOrNonMapDocumentsFailClosed() {
        assertThrows(IOException.class, () -> codec.decodeStrict(bytes("meta: [unterminated\n")));
        assertThrows(IOException.class, () -> codec.decodeStrict(bytes("- a\n- b\n")));
        assertThrows(IOException.class, () -> codec.decodeStrict(bytes("")));
        assertThrows(IOException.class, () -> codec.decodeStrict(bytes("formatVersion: 1\nsessions: [a]\n")));
    }

    @Test
    void versionedDocumentsNeverAcceptJavaTags() {
        String tagged = "formatVersion: 1\nsessions:\n  " + PLAYER + ":\n    command: copy\n    stage: s\n"
                + "    lastUpdateTime: 1\n    active: true\n    data:\n      x: !!java.lang.Object {}\n";
        assertThrows(IOException.class, () -> codec.decodeStrict(bytes(tagged)));
    }

    @Test
    void legacyDocumentWithoutLifecycleIsAcceptedAsAbnormalLegacy() throws IOException {
        Map<String, Object> document = codec.decodeStrict(bytes("sessions: {}\nmeta:\n  lastCleanShutdown: 123\n"));
        RecoverySnapshot.Decoded decoded = RecoverySnapshot.fromDocument(document);
        assertTrue(decoded.snapshot().legacy());
        assertEquals(Lifecycle.UNKNOWN, decoded.snapshot().lifecycle());
        assertTrue(decoded.rejections().isEmpty());
    }

    @Test
    void legacyJavaBeanTagIsARejectedPayloadNotACorruptFile() throws IOException {
        String legacy = "sessions:\n  " + PLAYER + ":\n    command: copy\n    stage: starting\n"
                + "    lastUpdateTime: 5\n    active: true\n    data:\n"
                + "      selection: !!com.bayzyl.Selection {}\n      owner: sage\n";
        RecoverySnapshot.Decoded decoded = RecoverySnapshot.fromDocument(codec.decodeStrict(bytes(legacy)));
        SessionRecord session = decoded.snapshot().sessions().get(PLAYER);
        assertEquals("copy", session.command());
        assertEquals(Map.of("owner", new SessionValue.Scalar("sage")), session.data());
        assertEquals(1, decoded.rejections().size());
        assertTrue(decoded.rejections().get(0).contains("selection"));
    }

    @Test
    void legacyClipboardMigratesToPaletteAndCountsPlaceholderStates() throws IOException {
        String legacy = "clipboards:\n  " + PLAYER + ":\n    hasData: true\n    sizeX: 3\n    sizeY: 1\n    sizeZ: 1\n"
                + "    origin:\n      world: world\n      x: 1.0\n      y: 64.0\n      z: -2.0\n"
                + "    minOffsetX: 0\n    minOffsetY: 0\n    minOffsetZ: 0\n"
                + "    blockData:\n    - minecraft:stone\n    - minecraft:chest[facing=north]\n    - minecraft:chest[facing=north]\n"
                + "    states:\n    - org.bukkit.craftbukkit.block.CraftChest:Location{}\n";
        RecoverySnapshot.Decoded decoded = RecoverySnapshot.fromDocument(codec.decodeStrict(bytes(legacy)));
        ClipboardRecord clipboard = decoded.snapshot().clipboards().get(PLAYER);
        assertEquals(List.of("minecraft:stone", "minecraft:chest[facing=north]"), clipboard.palette());
        assertEquals("AAEB", clipboard.blocks());
        assertArrayEquals(new int[]{0, 1, 1}, clipboard.decodeIndices());
        assertEquals(1, clipboard.omittedTileStates());
        assertEquals(new DetachedLocation("world", 1.0, 64.0, -2.0, 0f, 0f), clipboard.origin());
    }

    @Test
    void clipboardWhoseBlockCountDisagreesWithVolumeIsRejectedAlone() throws IOException {
        String document = "formatVersion: 1\nmeta:\n  lifecycleState: CLEAN\nclipboards:\n"
                + "  " + PLAYER + ":\n" + clipboardYaml(2, "AAA=")
                + "  " + OTHER + ":\n" + clipboardYaml(2, "AAAA");
        RecoverySnapshot.Decoded decoded = RecoverySnapshot.fromDocument(codec.decodeStrict(bytes(document)));
        assertEquals(Lifecycle.CLEAN, decoded.snapshot().lifecycle());
        assertTrue(decoded.snapshot().clipboards().containsKey(PLAYER));
        assertFalse(decoded.snapshot().clipboards().containsKey(OTHER));
        assertEquals(1, decoded.rejections().size());
    }

    @Test
    void versionedRoundTripIsLossless() throws IOException {
        DetachedLocation pos1 = new DetachedLocation("world", 1.25, 2.5, 3.75, 0.1f, -45f);
        DetachedLocation pos2 = new DetachedLocation("world", 8.0, 9.0, 10.0, 0f, 0f);
        Map<String, SessionValue> data = new LinkedHashMap<>();
        data.put("text", new SessionValue.Scalar("7"));
        data.put("count", new SessionValue.Scalar(7));
        data.put("big", new SessionValue.LongValue(5L));
        data.put("ratio", new SessionValue.Scalar(2.0));
        data.put("flag", new SessionValue.Scalar(true));
        data.put("owner", new SessionValue.UuidValue(OTHER));
        data.put("where", new SessionValue.LocationValue(pos1));
        data.put("selection", new SessionValue.SelectionValue(new DetachedSelection("CUBOID", pos1, pos2)));
        data.put("partial", new SessionValue.SelectionValue(new DetachedSelection("CUBOID", pos1, null)));
        data.put("mask", new SessionValue.MaskValue("stone,##logs"));
        SessionRecord session = new SessionRecord("copy", "starting", 1_700_000_000_123L, true, data);
        EntityRecord frame = new EntityRecord(true, 0.5, 0.0, -0.5, "NORTH", "CLOCKWISE",
                new ItemPayload(ItemPayload.PAPER_BYTES, "AQID"), true, false, 0.25f, null, "{id:\"x\"}", 90f, 0f);
        ClipboardRecord clipboard = new ClipboardRecord(3, 1, 1, pos2, 0, -1, 2,
                List.of("minecraft:stone", "minecraft:chest[facing=north]"), "AAEB", 2, List.of(frame));
        NudgeRecord nudge = new NudgeRecord(clipboard, new DetachedSelection("CUBOID", pos1, pos2));
        RecoverySnapshot snapshot = new RecoverySnapshot(Lifecycle.RUNNING, false,
                Map.of(PLAYER, session), Map.of(PLAYER, clipboard), Map.of(OTHER, nudge));

        byte[] encoded = codec.encode(snapshot.toDocument());
        String text = new String(encoded, StandardCharsets.UTF_8);
        assertTrue(text.startsWith("formatVersion: 1\n"), text);
        assertTrue(text.contains("lifecycleState: RUNNING"), text);
        assertTrue(text.contains("text: '7'"), text);

        RecoverySnapshot.Decoded decoded = RecoverySnapshot.fromDocument(codec.decodeStrict(encoded));
        assertEquals(List.of(), decoded.rejections());
        assertEquals(snapshot, decoded.snapshot());
    }

    @Test
    void encodingRejectsValuesThatAreNotPlainYamlData() {
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("formatVersion", 1);
        document.put("meta", Map.of("lifecycleState", new Object()));
        assertThrows(IOException.class, () -> codec.encode(document));
    }

    private static String clipboardYaml(int sizeX, String blocks) {
        return "    sizeX: " + sizeX + "\n    sizeY: 1\n    sizeZ: 1\n"
                + "    origin:\n      world: world\n      x: 0.0\n      y: 0.0\n      z: 0.0\n      yaw: 0.0\n      pitch: 0.0\n"
                + "    minOffsetX: 0\n    minOffsetY: 0\n    minOffsetZ: 0\n"
                + "    palette:\n    - minecraft:stone\n    blocks: " + blocks + "\n    omittedTileStates: 0\n    entities: []\n";
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
