package com.bayzyl.persistence;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Detached, Bukkit-free crash-recovery state. It never holds live server objects, so it can be encoded off the
 * main thread. {@link #toDocument()} always writes the versioned layout; {@link #fromDocument(Map)} reads the
 * versioned layout or the legacy 0.1 layout and rejects malformed payloads one at a time.
 */
public record RecoverySnapshot(Lifecycle lifecycle,
                               boolean legacy,
                               Map<UUID, SessionRecord> sessions,
                               Map<UUID, ClipboardRecord> clipboards,
                               Map<UUID, NudgeRecord> nudges) {

    public enum Lifecycle { RUNNING, CLEAN, UNKNOWN }

    public RecoverySnapshot {
        Objects.requireNonNull(lifecycle, "lifecycle");
        sessions = Map.copyOf(sessions);
        clipboards = Map.copyOf(clipboards);
        nudges = Map.copyOf(nudges);
    }

    public static RecoverySnapshot empty() {
        return new RecoverySnapshot(Lifecycle.UNKNOWN, false, Map.of(), Map.of(), Map.of());
    }

    /** A decoded snapshot plus one human-readable line per rejected payload. */
    public record Decoded(RecoverySnapshot snapshot, List<String> rejections) {
        public Decoded {
            rejections = List.copyOf(rejections);
        }
    }

    public record DetachedLocation(String world, double x, double y, double z, float yaw, float pitch) {
        public DetachedLocation {
            if (world == null || world.isBlank()) {
                throw new IllegalArgumentException("location world is required");
            }
        }
    }

    /** Either position may be absent: an incomplete selection is still a valid selection. */
    public record DetachedSelection(String type, DetachedLocation pos1, DetachedLocation pos2) {
        public DetachedSelection {
            Objects.requireNonNull(type, "type");
        }
    }

    public sealed interface SessionValue {
        record Scalar(Object value) implements SessionValue {
            public Scalar {
                if (!(value instanceof String || value instanceof Integer || value instanceof Double
                        || value instanceof Boolean)) {
                    throw new IllegalArgumentException("unsupported scalar session value: "
                            + (value == null ? "null" : value.getClass().getName()));
                }
            }
        }

        record LongValue(long value) implements SessionValue {
        }

        record UuidValue(UUID value) implements SessionValue {
            public UuidValue {
                Objects.requireNonNull(value, "value");
            }
        }

        record LocationValue(DetachedLocation value) implements SessionValue {
            public LocationValue {
                Objects.requireNonNull(value, "value");
            }
        }

        record SelectionValue(DetachedSelection value) implements SessionValue {
            public SelectionValue {
                Objects.requireNonNull(value, "value");
            }
        }

        record MaskValue(String raw) implements SessionValue {
            public MaskValue {
                Objects.requireNonNull(raw, "raw");
            }
        }
    }

    public record SessionRecord(String command, String stage, long lastUpdateTime, boolean active,
                                Map<String, SessionValue> data) {
        public SessionRecord {
            Objects.requireNonNull(command, "command");
            Objects.requireNonNull(stage, "stage");
            data = Map.copyOf(data);
        }
    }

    /** An item payload kept in its original serialized form; it is only turned into an item on the server. */
    public record ItemPayload(String format, Object value) {
        public static final String PAPER_BYTES = "paper-bytes";
        public static final String BUKKIT_MAP = "bukkit-map";

        public ItemPayload {
            if (PAPER_BYTES.equals(format)) {
                if (!(value instanceof String)) {
                    throw new IllegalArgumentException("paper-bytes item payload must be a base64 string");
                }
            } else if (BUKKIT_MAP.equals(format)) {
                if (!(value instanceof Map<?, ?>)) {
                    throw new IllegalArgumentException("bukkit-map item payload must be a mapping");
                }
            } else {
                throw new IllegalArgumentException("unknown item payload format: " + format);
            }
        }
    }

    public record EntityRecord(boolean glow, double supportOffsetX, double supportOffsetY, double supportOffsetZ,
                               String facing, String rotation, ItemPayload item, boolean visible, boolean fixed,
                               float itemDropChance, String paintingArt, String snapshotData, float yaw,
                               float pitch) {
    }

    /**
     * Block data is palette-compressed: {@code blocks} is base64 of one unsigned LEB128 palette index per block, in
     * the clipboard's y-z-x order. Tile-entity states are not persisted; their count is kept so the loss is reported.
     * The palette entry {@link #SKIP} marks a cell the clipboard leaves untouched on paste (it is not air).
     */
    public record ClipboardRecord(int sizeX, int sizeY, int sizeZ, DetachedLocation origin,
                                  int minOffsetX, int minOffsetY, int minOffsetZ,
                                  List<String> palette, String blocks, int omittedTileStates,
                                  List<EntityRecord> entities) {
        public static final String SKIP = "bayzyl:skip";

        public ClipboardRecord {
            Objects.requireNonNull(origin, "origin");
            Objects.requireNonNull(blocks, "blocks");
            palette = List.copyOf(palette);
            entities = List.copyOf(entities);
        }

        public long volume() {
            return (long) sizeX * sizeY * sizeZ;
        }

        public int[] decodeIndices() {
            return RecoverySnapshot.decodeIndices(blocks, volume(), palette.size());
        }

        public static String encodeIndices(int[] indices) {
            ByteArrayOutputStream out = new ByteArrayOutputStream(indices.length + 16);
            for (int index : indices) {
                if (index < 0) {
                    throw new IllegalArgumentException("palette index must not be negative");
                }
                int value = index;
                while ((value & ~0x7F) != 0) {
                    out.write((value & 0x7F) | 0x80);
                    value >>>= 7;
                }
                out.write(value);
            }
            return Base64.getEncoder().encodeToString(out.toByteArray());
        }
    }

    public record NudgeRecord(ClipboardRecord clipboard, DetachedSelection selection) {
        public NudgeRecord {
            Objects.requireNonNull(clipboard, "clipboard");
            Objects.requireNonNull(selection, "selection");
        }
    }

    // ---------------------------------------------------------------------------------------------------------
    // Document encoding (always the versioned layout)
    // ---------------------------------------------------------------------------------------------------------

    public Map<String, Object> toDocument() {
        Map<String, Object> document = new LinkedHashMap<>();
        document.put(CrashRecoverySnapshotCodec.FORMAT_KEY, CrashRecoverySnapshotCodec.FORMAT_VERSION);
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("lifecycleState", lifecycle.name());
        document.put("meta", meta);
        Map<String, Object> sessionSection = new TreeMap<>();
        sessions.forEach((id, session) -> sessionSection.put(id.toString(), sessionDocument(session)));
        document.put("sessions", sessionSection);
        Map<String, Object> clipboardSection = new TreeMap<>();
        clipboards.forEach((id, clipboard) -> clipboardSection.put(id.toString(), clipboardDocument(clipboard)));
        document.put("clipboards", clipboardSection);
        Map<String, Object> nudgeSection = new TreeMap<>();
        nudges.forEach((id, nudge) -> {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("clipboard", clipboardDocument(nudge.clipboard()));
            entry.put("selection", selectionDocument(nudge.selection()));
            nudgeSection.put(id.toString(), entry);
        });
        document.put("nudge", nudgeSection);
        return document;
    }

    public static Map<String, Object> sessionDocument(SessionRecord session) {
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("command", session.command());
        document.put("stage", session.stage());
        document.put("lastUpdateTime", session.lastUpdateTime());
        document.put("active", session.active());
        Map<String, Object> data = new TreeMap<>();
        session.data().forEach((key, value) -> data.put(key, valueDocument(value)));
        document.put("data", data);
        return document;
    }

    private static Object valueDocument(SessionValue value) {
        if (value instanceof SessionValue.Scalar scalar) {
            return scalar.value();
        }
        Map<String, Object> typed = new LinkedHashMap<>();
        if (value instanceof SessionValue.LongValue longValue) {
            typed.put("kind", "long");
            typed.put("value", Long.toString(longValue.value()));
        } else if (value instanceof SessionValue.UuidValue uuid) {
            typed.put("kind", "uuid");
            typed.put("value", uuid.value().toString());
        } else if (value instanceof SessionValue.LocationValue location) {
            typed.put("kind", "location");
            typed.putAll(locationDocument(location.value()));
        } else if (value instanceof SessionValue.SelectionValue selection) {
            typed.put("kind", "selection");
            typed.putAll(selectionDocument(selection.value()));
        } else if (value instanceof SessionValue.MaskValue mask) {
            typed.put("kind", "mask");
            typed.put("raw", mask.raw());
        }
        return typed;
    }

    private static Map<String, Object> locationDocument(DetachedLocation location) {
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("world", location.world());
        document.put("x", location.x());
        document.put("y", location.y());
        document.put("z", location.z());
        document.put("yaw", (double) location.yaw());
        document.put("pitch", (double) location.pitch());
        return document;
    }

    private static Map<String, Object> selectionDocument(DetachedSelection selection) {
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("type", selection.type());
        if (selection.pos1() != null) {
            document.put("pos1", locationDocument(selection.pos1()));
        }
        if (selection.pos2() != null) {
            document.put("pos2", locationDocument(selection.pos2()));
        }
        return document;
    }

    private static Map<String, Object> clipboardDocument(ClipboardRecord clipboard) {
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("sizeX", clipboard.sizeX());
        document.put("sizeY", clipboard.sizeY());
        document.put("sizeZ", clipboard.sizeZ());
        document.put("origin", locationDocument(clipboard.origin()));
        document.put("minOffsetX", clipboard.minOffsetX());
        document.put("minOffsetY", clipboard.minOffsetY());
        document.put("minOffsetZ", clipboard.minOffsetZ());
        document.put("palette", clipboard.palette());
        document.put("blocks", clipboard.blocks());
        document.put("omittedTileStates", clipboard.omittedTileStates());
        List<Object> entities = new ArrayList<>();
        for (EntityRecord entity : clipboard.entities()) {
            entities.add(entityDocument(entity));
        }
        document.put("entities", entities);
        return document;
    }

    private static Map<String, Object> entityDocument(EntityRecord entity) {
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("glow", entity.glow());
        document.put("supportOffsetX", entity.supportOffsetX());
        document.put("supportOffsetY", entity.supportOffsetY());
        document.put("supportOffsetZ", entity.supportOffsetZ());
        putIfPresent(document, "facing", entity.facing());
        putIfPresent(document, "rotation", entity.rotation());
        if (entity.item() != null) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("format", entity.item().format());
            item.put("value", entity.item().value());
            document.put("item", item);
        }
        document.put("visible", entity.visible());
        document.put("fixed", entity.fixed());
        document.put("itemDropChance", (double) entity.itemDropChance());
        putIfPresent(document, "paintingArt", entity.paintingArt());
        putIfPresent(document, "snapshotData", entity.snapshotData());
        document.put("yaw", (double) entity.yaw());
        document.put("pitch", (double) entity.pitch());
        return document;
    }

    private static void putIfPresent(Map<String, Object> document, String key, Object value) {
        if (value != null) {
            document.put(key, value);
        }
    }

    // ---------------------------------------------------------------------------------------------------------
    // Document decoding (versioned or legacy); each malformed payload is rejected on its own
    // ---------------------------------------------------------------------------------------------------------

    public static Decoded fromDocument(Map<String, Object> document) {
        boolean legacy = !document.containsKey(CrashRecoverySnapshotCodec.FORMAT_KEY);
        List<String> rejections = new ArrayList<>();
        Lifecycle lifecycle = Lifecycle.UNKNOWN;
        if (document.get("meta") instanceof Map<?, ?> meta && meta.get("lifecycleState") instanceof String state) {
            if (state.equals(Lifecycle.RUNNING.name())) {
                lifecycle = Lifecycle.RUNNING;
            } else if (state.equals(Lifecycle.CLEAN.name())) {
                lifecycle = Lifecycle.CLEAN;
            }
        }

        Map<UUID, SessionRecord> sessions = new LinkedHashMap<>();
        forEachPlayer(document.get("sessions"), "session", rejections, (id, raw) -> {
            sessions.put(id, readSession(id, raw, legacy, rejections));
        });

        Map<UUID, ClipboardRecord> clipboards = new LinkedHashMap<>();
        forEachPlayer(document.get("clipboards"), "clipboard", rejections, (id, raw) -> {
            Map<String, Object> section = mapping(raw, "clipboard");
            if (legacy && !Boolean.TRUE.equals(section.get("hasData"))) {
                return;
            }
            clipboards.put(id, readClipboard(section, legacy, "clipboard " + id, rejections));
        });

        Map<UUID, NudgeRecord> nudges = new LinkedHashMap<>();
        forEachPlayer(document.get("nudge"), "nudge", rejections, (id, raw) -> {
            Map<String, Object> section = mapping(raw, "nudge");
            ClipboardRecord clipboard = readClipboard(mapping(section.get("clipboard"), "nudge clipboard"),
                    legacy, "nudge " + id, rejections);
            DetachedSelection selection = readSelection(mapping(section.get("selection"), "nudge selection"));
            nudges.put(id, new NudgeRecord(clipboard, selection));
        });

        return new Decoded(new RecoverySnapshot(lifecycle, legacy, sessions, clipboards, nudges), rejections);
    }

    private interface PlayerReader {
        void read(UUID id, Object raw) throws InvalidPayload;
    }

    private static void forEachPlayer(Object section, String label, List<String> rejections, PlayerReader reader) {
        if (!(section instanceof Map<?, ?> map)) {
            return;
        }
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            String key = String.valueOf(entry.getKey());
            UUID id;
            try {
                id = UUID.fromString(key);
            } catch (IllegalArgumentException exception) {
                rejections.add(label + " '" + key + "': not a player UUID");
                continue;
            }
            try {
                reader.read(id, entry.getValue());
            } catch (InvalidPayload | RuntimeException exception) {
                rejections.add(label + " " + id + ": " + exception.getMessage());
            }
        }
    }

    private static SessionRecord readSession(UUID id, Object raw, boolean legacy, List<String> rejections)
            throws InvalidPayload {
        Map<String, Object> section = mapping(raw, "session");
        String command = string(section.get("command"), "command");
        String stage = string(section.get("stage"), "stage");
        long lastUpdateTime = section.containsKey("lastUpdateTime") ? number(section.get("lastUpdateTime"), "lastUpdateTime").longValue() : 0L;
        boolean active = !section.containsKey("active") || bool(section.get("active"), "active");
        Map<String, SessionValue> data = new LinkedHashMap<>();
        Object rawData = section.get("data");
        if (rawData != null) {
            for (Map.Entry<String, Object> entry : mapping(rawData, "data").entrySet()) {
                try {
                    data.put(entry.getKey(), readValue(entry.getValue(), legacy));
                } catch (InvalidPayload | RuntimeException exception) {
                    rejections.add("session " + id + " value '" + entry.getKey() + "': " + exception.getMessage());
                }
            }
        }
        return new SessionRecord(command, stage, lastUpdateTime, active, data);
    }

    private static SessionValue readValue(Object raw, boolean legacy) throws InvalidPayload {
        if (raw == CrashRecoverySnapshotCodec.UNSUPPORTED_LEGACY_VALUE) {
            throw new InvalidPayload("unsupported legacy value (Java object)");
        }
        if (raw instanceof String || raw instanceof Integer || raw instanceof Double || raw instanceof Boolean) {
            return new SessionValue.Scalar(raw);
        }
        if (raw instanceof Long value) {
            return new SessionValue.LongValue(value);
        }
        if (!(raw instanceof Map<?, ?>)) {
            throw new InvalidPayload("unsupported value type " + (raw == null ? "null" : raw.getClass().getSimpleName()));
        }
        Map<String, Object> typed = mapping(raw, "value");
        Object kind = typed.get("kind");
        if (kind == null && legacy && typed.containsKey("world")) {
            return new SessionValue.LocationValue(readLocation(typed));
        }
        if (!(kind instanceof String name)) {
            throw new InvalidPayload("mapping value without a kind");
        }
        return switch (name) {
            case "long" -> {
                try {
                    yield new SessionValue.LongValue(Long.parseLong(string(typed.get("value"), "value")));
                } catch (NumberFormatException exception) {
                    throw new InvalidPayload("long value is not a number");
                }
            }
            case "uuid" -> {
                try {
                    yield new SessionValue.UuidValue(UUID.fromString(string(typed.get("value"), "value")));
                } catch (IllegalArgumentException exception) {
                    throw new InvalidPayload("uuid value is malformed");
                }
            }
            case "location" -> new SessionValue.LocationValue(readLocation(typed));
            case "selection" -> new SessionValue.SelectionValue(readSelection(typed));
            case "mask" -> new SessionValue.MaskValue(string(typed.get("raw"), "raw"));
            default -> throw new InvalidPayload("unknown value kind '" + name + "'");
        };
    }

    private static DetachedLocation readLocation(Map<String, Object> section) throws InvalidPayload {
        String world = string(section.get("world"), "world");
        if (world.isBlank()) {
            throw new InvalidPayload("location world is blank");
        }
        double x = number(section.get("x"), "x").doubleValue();
        double y = number(section.get("y"), "y").doubleValue();
        double z = number(section.get("z"), "z").doubleValue();
        float yaw = section.containsKey("yaw") ? number(section.get("yaw"), "yaw").floatValue() : 0f;
        float pitch = section.containsKey("pitch") ? number(section.get("pitch"), "pitch").floatValue() : 0f;
        return new DetachedLocation(world, x, y, z, yaw, pitch);
    }

    private static DetachedSelection readSelection(Map<String, Object> section) throws InvalidPayload {
        String type = string(section.get("type"), "selection type");
        DetachedLocation pos1 = section.get("pos1") == null ? null : readLocation(mapping(section.get("pos1"), "pos1"));
        DetachedLocation pos2 = section.get("pos2") == null ? null : readLocation(mapping(section.get("pos2"), "pos2"));
        return new DetachedSelection(type, pos1, pos2);
    }

    private static ClipboardRecord readClipboard(Map<String, Object> section, boolean legacy, String label,
                                                 List<String> rejections) throws InvalidPayload {
        int sizeX = positive(section.get("sizeX"), "sizeX");
        int sizeY = positive(section.get("sizeY"), "sizeY");
        int sizeZ = positive(section.get("sizeZ"), "sizeZ");
        long volume = (long) sizeX * sizeY * sizeZ;
        if (volume > Integer.MAX_VALUE) {
            throw new InvalidPayload("clipboard volume " + volume + " is too large");
        }
        if (section.get("origin") == null) {
            throw new InvalidPayload("clipboard origin is missing");
        }
        DetachedLocation origin = readLocation(mapping(section.get("origin"), "origin"));
        int minOffsetX = number(section.get("minOffsetX"), "minOffsetX").intValue();
        int minOffsetY = number(section.get("minOffsetY"), "minOffsetY").intValue();
        int minOffsetZ = number(section.get("minOffsetZ"), "minOffsetZ").intValue();

        List<String> palette;
        String blocks;
        int omittedTileStates;
        List<EntityRecord> entities = new ArrayList<>();
        if (legacy) {
            List<String> perBlock = strings(section.get("blockData"), "blockData");
            if (perBlock.size() != volume) {
                throw new InvalidPayload("block count " + perBlock.size() + " does not match volume " + volume);
            }
            Map<String, Integer> paletteIndex = new LinkedHashMap<>();
            int[] indices = new int[perBlock.size()];
            for (int i = 0; i < indices.length; i++) {
                indices[i] = paletteIndex.computeIfAbsent(perBlock.get(i), ignored -> paletteIndex.size());
            }
            palette = new ArrayList<>(paletteIndex.keySet());
            blocks = ClipboardRecord.encodeIndices(indices);
            omittedTileStates = section.get("states") instanceof List<?> states ? states.size() : 0;
            if (section.get("entities") instanceof Map<?, ?> legacyEntities) {
                for (Map.Entry<?, ?> entry : legacyEntities.entrySet()) {
                    try {
                        entities.add(readEntity(mapping(entry.getValue(), "entity"), true));
                    } catch (InvalidPayload | RuntimeException exception) {
                        rejections.add(label + " entity " + entry.getKey() + ": " + exception.getMessage());
                    }
                }
            }
        } else {
            palette = strings(section.get("palette"), "palette");
            if (palette.isEmpty()) {
                throw new InvalidPayload("clipboard palette is empty");
            }
            blocks = string(section.get("blocks"), "blocks");
            decodeIndicesChecked(blocks, volume, palette.size());
            omittedTileStates = number(section.get("omittedTileStates"), "omittedTileStates").intValue();
            Object rawEntities = section.get("entities");
            if (rawEntities != null) {
                if (!(rawEntities instanceof List<?> list)) {
                    throw new InvalidPayload("entities must be a list");
                }
                for (int i = 0; i < list.size(); i++) {
                    try {
                        entities.add(readEntity(mapping(list.get(i), "entity"), false));
                    } catch (InvalidPayload | RuntimeException exception) {
                        rejections.add(label + " entity " + i + ": " + exception.getMessage());
                    }
                }
            }
        }
        return new ClipboardRecord(sizeX, sizeY, sizeZ, origin, minOffsetX, minOffsetY, minOffsetZ,
                palette, blocks, omittedTileStates, entities);
    }

    private static EntityRecord readEntity(Map<String, Object> section, boolean legacy) throws InvalidPayload {
        ItemPayload item = null;
        Object rawItem = section.get("item");
        if (rawItem == CrashRecoverySnapshotCodec.UNSUPPORTED_LEGACY_VALUE) {
            throw new InvalidPayload("unsupported legacy item (Java object)");
        }
        if (rawItem != null) {
            Map<String, Object> itemSection = mapping(rawItem, "item");
            if (legacy) {
                item = new ItemPayload(ItemPayload.BUKKIT_MAP, plainCopy(itemSection));
            } else {
                String format = string(itemSection.get("format"), "item format");
                Object value = ItemPayload.BUKKIT_MAP.equals(format)
                        ? plainCopy(mapping(itemSection.get("value"), "item value"))
                        : string(itemSection.get("value"), "item value");
                try {
                    item = new ItemPayload(format, value);
                } catch (IllegalArgumentException exception) {
                    throw new InvalidPayload(exception.getMessage());
                }
            }
        }
        return new EntityRecord(
                optionalBool(section.get("glow"), false),
                optionalNumber(section.get("supportOffsetX")),
                optionalNumber(section.get("supportOffsetY")),
                optionalNumber(section.get("supportOffsetZ")),
                optionalString(section.get("facing"), "facing"),
                optionalString(section.get("rotation"), "rotation"),
                item,
                optionalBool(section.get("visible"), true),
                optionalBool(section.get("fixed"), false),
                section.containsKey("itemDropChance") ? number(section.get("itemDropChance"), "itemDropChance").floatValue() : 1.0f,
                optionalString(section.get("paintingArt"), "paintingArt"),
                optionalString(section.get("snapshotData"), "snapshotData"),
                (float) optionalNumber(section.get("yaw")),
                (float) optionalNumber(section.get("pitch")));
    }

    // ---------------------------------------------------------------------------------------------------------
    // Strict scalar helpers
    // ---------------------------------------------------------------------------------------------------------

    static int[] decodeIndices(String blocks, long volume, int paletteSize) {
        try {
            return decodeIndicesChecked(blocks, volume, paletteSize);
        } catch (InvalidPayload exception) {
            throw new IllegalStateException(exception.getMessage(), exception);
        }
    }

    private static int[] decodeIndicesChecked(String blocks, long volume, int paletteSize) throws InvalidPayload {
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(blocks);
        } catch (IllegalArgumentException exception) {
            throw new InvalidPayload("block indices are not valid base64");
        }
        if (volume > bytes.length) {
            throw new InvalidPayload("block count does not match volume " + volume);
        }
        int[] indices = new int[(int) volume];
        int count = 0;
        int position = 0;
        while (position < bytes.length) {
            int value = 0;
            int shift = 0;
            while (true) {
                if (position >= bytes.length || shift > 28) {
                    throw new InvalidPayload("block indices are truncated or malformed");
                }
                int b = bytes[position++] & 0xFF;
                value |= (b & 0x7F) << shift;
                if ((b & 0x80) == 0) {
                    break;
                }
                shift += 7;
            }
            if (value < 0 || value >= paletteSize) {
                throw new InvalidPayload("block index " + value + " is outside the palette");
            }
            if (count >= volume) {
                throw new InvalidPayload("block count exceeds volume " + volume);
            }
            indices[count++] = value;
        }
        if (count != volume) {
            throw new InvalidPayload("block count " + count + " does not match volume " + volume);
        }
        return indices;
    }

    private static Map<String, Object> mapping(Object raw, String label) throws InvalidPayload {
        if (!(raw instanceof Map<?, ?> map)) {
            throw new InvalidPayload(label + " must be a mapping");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            result.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return result;
    }

    private static Map<String, Object> plainCopy(Map<String, Object> source) throws InvalidPayload {
        Map<String, Object> copy = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            copy.put(entry.getKey(), plainValue(entry.getValue()));
        }
        return copy;
    }

    private static Object plainValue(Object value) throws InvalidPayload {
        if (value instanceof String || value instanceof Integer || value instanceof Long
                || value instanceof Double || value instanceof Boolean) {
            return value;
        }
        if (value instanceof Map<?, ?> map) {
            return plainCopy(mapping(map, "item value"));
        }
        if (value instanceof List<?> list) {
            List<Object> copy = new ArrayList<>(list.size());
            for (Object element : list) {
                copy.add(plainValue(element));
            }
            return copy;
        }
        throw new InvalidPayload("item payload contains a non-plain value");
    }

    private static String string(Object raw, String label) throws InvalidPayload {
        if (!(raw instanceof String value)) {
            throw new InvalidPayload(label + " must be a string");
        }
        return value;
    }

    private static String optionalString(Object raw, String label) throws InvalidPayload {
        return raw == null ? null : string(raw, label);
    }

    private static List<String> strings(Object raw, String label) throws InvalidPayload {
        if (!(raw instanceof List<?> list)) {
            throw new InvalidPayload(label + " must be a list");
        }
        List<String> values = new ArrayList<>(list.size());
        for (Object element : list) {
            values.add(string(element, label + " entry"));
        }
        return values;
    }

    private static Number number(Object raw, String label) throws InvalidPayload {
        if (!(raw instanceof Integer || raw instanceof Long || raw instanceof Double)) {
            throw new InvalidPayload(label + " must be a number");
        }
        return (Number) raw;
    }

    private static double optionalNumber(Object raw) throws InvalidPayload {
        return raw == null ? 0.0 : number(raw, "number").doubleValue();
    }

    private static int positive(Object raw, String label) throws InvalidPayload {
        if (!(raw instanceof Integer value) || value <= 0) {
            throw new InvalidPayload(label + " must be a positive integer");
        }
        return value;
    }

    private static boolean bool(Object raw, String label) throws InvalidPayload {
        if (!(raw instanceof Boolean value)) {
            throw new InvalidPayload(label + " must be a boolean");
        }
        return value;
    }

    private static boolean optionalBool(Object raw, boolean fallback) throws InvalidPayload {
        return raw == null ? fallback : bool(raw, "flag");
    }

    /** A single payload failed validation; the rest of the document is unaffected. */
    private static final class InvalidPayload extends Exception {
        InvalidPayload(String message) {
            super(message);
        }
    }
}
