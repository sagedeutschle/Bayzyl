package com.bayzyl;

import com.bayzyl.persistence.RecoverySnapshot.ClipboardRecord;
import com.bayzyl.persistence.RecoverySnapshot.DetachedLocation;
import com.bayzyl.persistence.RecoverySnapshot.DetachedSelection;
import com.bayzyl.persistence.RecoverySnapshot.EntityRecord;
import com.bayzyl.persistence.RecoverySnapshot.ItemPayload;
import com.bayzyl.persistence.RecoverySnapshot.NudgeRecord;
import com.bayzyl.persistence.RecoverySnapshot.SessionValue;
import org.bukkit.Art;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Rotation;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.configuration.serialization.ConfigurationSerialization;
import org.bukkit.entity.EntitySnapshot;
import org.bukkit.inventory.ItemStack;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * The only crash-recovery code that touches live Bukkit objects. Detaching runs on the thread that owns the
 * object (the main thread); materializing resolves worlds and block data lazily, when a caller asks for state.
 */
final class CrashRecoveryBridge {
    private CrashRecoveryBridge() {
    }

    /** Outcome of turning a detached record back into live objects. */
    record Materialized<T>(T value, String failure, boolean permanent, int rejectedEntities) {
        static <T> Materialized<T> ok(T value, int rejectedEntities) {
            return new Materialized<>(value, null, false, rejectedEntities);
        }

        static <T> Materialized<T> unresolved(String failure) {
            return new Materialized<>(null, failure, false, 0);
        }

        static <T> Materialized<T> invalid(String failure) {
            return new Materialized<>(null, failure, true, 0);
        }
    }

    // ---------------------------------------------------------------------------------------------------------
    // Detaching (live -> record). Unsupported values throw IllegalArgumentException before any state changes.
    // ---------------------------------------------------------------------------------------------------------

    static Map<String, SessionValue> detachSessionData(Map<String, Object> data) {
        Map<String, SessionValue> detached = new LinkedHashMap<>();
        if (data == null) {
            return detached;
        }
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            if (entry.getKey() == null) {
                throw new IllegalArgumentException("session data keys must not be null");
            }
            if (entry.getValue() != null) {
                detached.put(entry.getKey(), detachValue(entry.getKey(), entry.getValue()));
            }
        }
        return detached;
    }

    private static SessionValue detachValue(String key, Object value) {
        if (value instanceof String || value instanceof Integer || value instanceof Double || value instanceof Boolean) {
            return new SessionValue.Scalar(value);
        }
        if (value instanceof Long number) {
            return new SessionValue.LongValue(number);
        }
        if (value instanceof UUID uuid) {
            return new SessionValue.UuidValue(uuid);
        }
        if (value instanceof Location location) {
            return new SessionValue.LocationValue(detachLocation(location));
        }
        if (value instanceof Selection selection) {
            return new SessionValue.SelectionValue(detachSelection(selection));
        }
        if (value instanceof BlockMask mask) {
            if (mask.isCombined()) {
                throw new IllegalArgumentException("session value '" + key + "' is a combined mask, which cannot be persisted");
            }
            return new SessionValue.MaskValue(mask.getRaw() == null ? "" : mask.getRaw());
        }
        throw new IllegalArgumentException("session value '" + key + "' has unsupported type " + value.getClass().getName());
    }

    static DetachedLocation detachLocation(Location location) {
        World world = location.getWorld();
        if (world == null) {
            throw new IllegalArgumentException("location has no world");
        }
        return new DetachedLocation(world.getName(), location.getX(), location.getY(), location.getZ(),
                location.getYaw(), location.getPitch());
    }

    static DetachedSelection detachSelection(Selection selection) {
        if (selection.getType() == null) {
            throw new IllegalArgumentException("selection has no type");
        }
        return new DetachedSelection(selection.getType().name(),
                detachPosition(selection.getPos1()), detachPosition(selection.getPos2()));
    }

    private static DetachedLocation detachPosition(Location position) {
        return position == null || position.getWorld() == null ? null : detachLocation(position);
    }

    static ClipboardRecord detachClipboard(Clipboard clipboard) {
        int sizeX = clipboard.getSizeX();
        int sizeY = clipboard.getSizeY();
        int sizeZ = clipboard.getSizeZ();
        int[] indices = new int[Math.multiplyExact(Math.multiplyExact(sizeX, sizeY), sizeZ)];
        Map<BlockData, Integer> paletteIndex = new HashMap<>();
        List<String> palette = new ArrayList<>();
        int skipIndex = -1;
        int omittedTileStates = 0;
        int cursor = 0;
        for (int y = 0; y < sizeY; y++) {
            for (int z = 0; z < sizeZ; z++) {
                for (int x = 0; x < sizeX; x++) {
                    BlockData data = clipboard.get(x, y, z);
                    int index;
                    if (data == null) {
                        if (skipIndex < 0) {
                            skipIndex = palette.size();
                            palette.add(ClipboardRecord.SKIP);
                        }
                        index = skipIndex;
                    } else {
                        Integer known = paletteIndex.get(data);
                        if (known == null) {
                            known = palette.size();
                            palette.add(data.getAsString());
                            paletteIndex.put(data, known);
                        }
                        index = known;
                    }
                    indices[cursor++] = index;
                    if (hasState(clipboard, x, y, z)) {
                        omittedTileStates++;
                    }
                }
            }
        }
        List<EntityRecord> entities = new ArrayList<>();
        List<ClipboardEntity> clipboardEntities = clipboard.getEntities();
        if (clipboardEntities != null) {
            for (ClipboardEntity entity : clipboardEntities) {
                entities.add(detachEntity(entity));
            }
        }
        return new ClipboardRecord(sizeX, sizeY, sizeZ, detachLocation(clipboard.getOrigin()),
                clipboard.getMinOffsetX(), clipboard.getMinOffsetY(), clipboard.getMinOffsetZ(),
                palette, ClipboardRecord.encodeIndices(indices), omittedTileStates, entities);
    }

    private static boolean hasState(Clipboard clipboard, int x, int y, int z) {
        try {
            return clipboard.getState(x, y, z) != null;
        } catch (RuntimeException missingStates) {
            return false;
        }
    }

    private static EntityRecord detachEntity(ClipboardEntity entity) {
        ItemPayload item = null;
        ItemStack stack = entity.getItem();
        if (stack != null && stack.getType() != Material.AIR && stack.getAmount() > 0) {
            item = new ItemPayload(ItemPayload.PAPER_BYTES, Base64.getEncoder().encodeToString(stack.serializeAsBytes()));
        }
        return new EntityRecord(entity.isGlow(), entity.getSupportOffsetX(), entity.getSupportOffsetY(),
                entity.getSupportOffsetZ(),
                entity.getFacing() == null ? null : entity.getFacing().name(),
                entity.getRotation() == null ? null : entity.getRotation().name(),
                item, entity.isVisible(), entity.isFixed(), entity.getItemDropChance(),
                entity.getPaintingArt() == null ? null : entity.getPaintingArt().name(),
                entity.getSnapshotData(), entity.getYaw(), entity.getPitch());
    }

    static NudgeRecord detachNudge(EditService.NudgeSession session) {
        return new NudgeRecord(detachClipboard(session.clipboard()), detachSelection(session.selection()));
    }

    // ---------------------------------------------------------------------------------------------------------
    // Materializing (record -> live). Unresolved worlds are temporary; invalid data is permanent.
    // ---------------------------------------------------------------------------------------------------------

    static Map<String, Object> materializeSessionData(Map<String, SessionValue> data, Consumer<String> warn) {
        Map<String, Object> live = new LinkedHashMap<>();
        for (Map.Entry<String, SessionValue> entry : data.entrySet()) {
            Object value = materializeValue(entry.getValue());
            if (value == null) {
                warn.accept("value '" + entry.getKey() + "' could not be restored (its world is not loaded or its type is unknown)");
            } else {
                live.put(entry.getKey(), value);
            }
        }
        return Collections.unmodifiableMap(live);
    }

    private static Object materializeValue(SessionValue value) {
        if (value instanceof SessionValue.Scalar scalar) {
            return scalar.value();
        }
        if (value instanceof SessionValue.LongValue number) {
            return number.value();
        }
        if (value instanceof SessionValue.UuidValue uuid) {
            return uuid.value();
        }
        if (value instanceof SessionValue.LocationValue location) {
            return materializeLocation(location.value());
        }
        if (value instanceof SessionValue.SelectionValue selection) {
            return materializeSelection(selection.value()).value();
        }
        if (value instanceof SessionValue.MaskValue mask) {
            return BlockMask.deferred(mask.raw());
        }
        return null;
    }

    /**
     * The name of the first world a session value points at that is not loaded right now, or null when every
     * referenced world is loaded. Such a value materializes as missing only until its world loads.
     */
    @Nullable
    static String unloadedWorld(Map<String, SessionValue> data) {
        for (SessionValue value : data.values()) {
            if (value instanceof SessionValue.LocationValue location) {
                if (Bukkit.getWorld(location.value().world()) == null) {
                    return location.value().world();
                }
            } else if (value instanceof SessionValue.SelectionValue selection) {
                for (DetachedLocation position : new DetachedLocation[]{selection.value().pos1(), selection.value().pos2()}) {
                    if (position != null && Bukkit.getWorld(position.world()) == null) {
                        return position.world();
                    }
                }
            }
        }
        return null;
    }

    static Location materializeLocation(DetachedLocation location) {
        World world = Bukkit.getWorld(location.world());
        if (world == null) {
            return null;
        }
        return new Location(world, location.x(), location.y(), location.z(), location.yaw(), location.pitch());
    }

    static Materialized<Selection> materializeSelection(DetachedSelection selection) {
        SelectionType type;
        try {
            type = SelectionType.valueOf(selection.type());
        } catch (IllegalArgumentException exception) {
            return Materialized.invalid("unknown selection type '" + selection.type() + "'");
        }
        Location pos1 = null;
        Location pos2 = null;
        if (selection.pos1() != null) {
            pos1 = materializeLocation(selection.pos1());
            if (pos1 == null) {
                return Materialized.unresolved("world '" + selection.pos1().world() + "' is not loaded");
            }
        }
        if (selection.pos2() != null) {
            pos2 = materializeLocation(selection.pos2());
            if (pos2 == null) {
                return Materialized.unresolved("world '" + selection.pos2().world() + "' is not loaded");
            }
        }
        return Materialized.ok(new Selection(pos1, pos2, type), 0);
    }

    static Materialized<Clipboard> materializeClipboard(ClipboardRecord record) {
        Location origin = materializeLocation(record.origin());
        if (origin == null) {
            return Materialized.unresolved("world '" + record.origin().world() + "' is not loaded");
        }
        BlockData[] palette = new BlockData[record.palette().size()];
        for (int i = 0; i < palette.length; i++) {
            String entry = record.palette().get(i);
            if (ClipboardRecord.SKIP.equals(entry)) {
                continue;
            }
            try {
                palette[i] = Bukkit.createBlockData(entry);
            } catch (IllegalArgumentException exception) {
                return Materialized.invalid("invalid block data '" + entry + "'");
            }
            if (palette[i] == null) {
                return Materialized.invalid("invalid block data '" + entry + "'");
            }
        }
        int[] indices;
        try {
            indices = record.decodeIndices();
        } catch (IllegalStateException exception) {
            return Materialized.invalid(exception.getMessage());
        }
        BlockData[] data = new BlockData[indices.length];
        for (int i = 0; i < indices.length; i++) {
            BlockData shared = palette[indices[i]];
            data[i] = shared == null ? null : shared.clone();
        }
        List<ClipboardEntity> entities = new ArrayList<>();
        int rejectedEntities = 0;
        for (EntityRecord entity : record.entities()) {
            ClipboardEntity live = materializeEntity(entity);
            if (live == null) {
                rejectedEntities++;
            } else {
                entities.add(live);
            }
        }
        Clipboard clipboard = new Clipboard(record.sizeX(), record.sizeY(), record.sizeZ(), data,
                new BlockState[indices.length], entities, origin,
                record.minOffsetX(), record.minOffsetY(), record.minOffsetZ());
        return Materialized.ok(clipboard, rejectedEntities);
    }

    private static ClipboardEntity materializeEntity(EntityRecord entity) {
        try {
            BlockFace facing = entity.facing() == null ? null : BlockFace.valueOf(entity.facing());
            Rotation rotation = entity.rotation() == null ? null : Rotation.valueOf(entity.rotation());
            Art art = entity.paintingArt() == null ? null : Art.valueOf(entity.paintingArt());
            EntitySnapshot snapshot = null;
            if (entity.snapshotData() != null && !entity.snapshotData().isBlank()) {
                snapshot = Bukkit.getEntityFactory().createEntitySnapshot(entity.snapshotData());
            }
            ItemStack item = entity.item() == null ? null : materializeItem(entity.item());
            return new ClipboardEntity(entity.glow(), entity.supportOffsetX(), entity.supportOffsetY(),
                    entity.supportOffsetZ(), facing, rotation, item, entity.visible(), entity.fixed(),
                    entity.itemDropChance(), art, snapshot, entity.yaw(), entity.pitch());
        } catch (RuntimeException rejected) {
            return null;
        }
    }

    private static ItemStack materializeItem(ItemPayload payload) {
        if (ItemPayload.PAPER_BYTES.equals(payload.format())) {
            return ItemStack.deserializeBytes(Base64.getDecoder().decode((String) payload.value()));
        }
        Object legacy = deserializeLegacy(payload.value());
        if (!(legacy instanceof ItemStack stack)) {
            throw new IllegalArgumentException("legacy item payload is not an item");
        }
        return stack;
    }

    /** Rebuilds Bukkit-serialized maps bottom-up, the way Bukkit's own YAML loader does. */
    private static Object deserializeLegacy(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> converted = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                converted.put(String.valueOf(entry.getKey()), deserializeLegacy(entry.getValue()));
            }
            if (converted.containsKey(ConfigurationSerialization.SERIALIZED_TYPE_KEY)) {
                ConfigurationSerializable object = ConfigurationSerialization.deserializeObject(converted);
                if (object == null) {
                    throw new IllegalArgumentException("unknown serialized type " + converted.get(ConfigurationSerialization.SERIALIZED_TYPE_KEY));
                }
                return object;
            }
            return converted;
        }
        if (value instanceof List<?> list) {
            List<Object> converted = new ArrayList<>(list.size());
            for (Object element : list) {
                converted.add(deserializeLegacy(element));
            }
            return converted;
        }
        return value;
    }

    static Materialized<EditService.NudgeSession> materializeNudge(NudgeRecord record) {
        Materialized<Clipboard> clipboard = materializeClipboard(record.clipboard());
        if (clipboard.value() == null) {
            return new Materialized<>(null, clipboard.failure(), clipboard.permanent(), 0);
        }
        Materialized<Selection> selection = materializeSelection(record.selection());
        if (selection.value() == null) {
            return new Materialized<>(null, selection.failure(), selection.permanent(), 0);
        }
        return Materialized.ok(new EditService.NudgeSession(clipboard.value(), selection.value(),
                new java.util.concurrent.ConcurrentHashMap<>()), clipboard.rejectedEntities());
    }
}
