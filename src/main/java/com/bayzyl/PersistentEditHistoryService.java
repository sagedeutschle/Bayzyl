package com.bayzyl;

import org.bukkit.Bukkit;
import org.bukkit.Art;
import org.bukkit.Location;
import org.bukkit.Rotation;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntitySnapshot;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class PersistentEditHistoryService {
    private static final int PERSISTED_LIMIT = 10;
    /**
     * Per-action block-change cap for persistence. Actions with more block changes than
     * this are persisted as a metadata-only stub (the entry exists, but per-block payload
     * is omitted). The action stays fully in in-memory undo history while the player
     * is online; only after a restart does the oversize action lose its undo data.
     * This is the trade-off that keeps a 1.78M-block paste from locking the main
     * thread for tens of seconds while yaml.set churns through ~10M write calls.
     */
    private static final int MAX_PERSIST_CHANGES_PER_ACTION = 50_000;

    private final JavaPlugin plugin;
    private final File file;
    private final YamlConfiguration yaml;
    private final Object saveLock = new Object();

    public PersistentEditHistoryService(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "edit-history.yml");
        this.yaml = YamlConfiguration.loadConfiguration(file);
    }

    public void save(UUID playerId, List<EditAction> undoActions, List<EditAction> redoActions) {
        // Build the yaml tree synchronously (touches BlockData.getAsString which we
        // assume is thread-unsafe enough to keep on the calling thread), then dispatch
        // the disk write itself to an async task. saveLock serializes concurrent writes
        // to the shared yaml object.
        synchronized (saveLock) {
            String root = "players." + playerId;
            yaml.set(root, null);
            writeActions(root + ".undo", undoActions);
            writeActions(root + ".redo", redoActions);
        }
        scheduleAsyncSave();
    }

    private void scheduleAsyncSave() {
        // Off-main file I/O. The yaml.save() call serializes the in-memory tree
        // (snakeyaml emit + write); doing this on the main thread for a multi-MB
        // history is what was tanking the server after large pastes.
        try {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, this::saveFile);
        } catch (IllegalStateException | org.bukkit.plugin.IllegalPluginAccessException ex) {
            // Plugin disabling (Bukkit throws IllegalPluginAccessException, a plain RuntimeException, for a
            // task registered while disabled) — fall back to synchronous save so we don't lose data.
            saveFile();
        }
    }

    public LoadedHistory load(UUID playerId) {
        String root = "players." + playerId;
        return new LoadedHistory(
                readActions(root + ".undo"),
                readActions(root + ".redo")
        );
    }

    private void writeActions(String path, List<EditAction> actions) {
        yaml.set(path, null);
        if (actions == null || actions.isEmpty()) {
            return;
        }
        for (int i = 0; i < Math.min(PERSISTED_LIMIT, actions.size()); i++) {
            writeAction(path + "." + i, actions.get(i));
        }
    }

    private void writeAction(String path, EditAction action) {
        // Oversize action: persist a stub so the slot exists in undo history (and we
        // don't try to re-load garbage), but skip the heavy per-block payload.
        // In-memory undo still works for this action while the session is alive.
        if (action.getChanges().size() > MAX_PERSIST_CHANGES_PER_ACTION) {
            yaml.set(path + ".tooLargeToPersist", true);
            yaml.set(path + ".blockCount", action.getChanges().size());
            writeSelection(path + ".beforeSelection", action.getBeforeSelection());
            writeSelection(path + ".afterSelection", action.getAfterSelection());
            return;
        }
        writeBlockChanges(path + ".blocks", action.getChanges());
        writeEntityChanges(path + ".entities", action.getEntityChanges());
        writeBiomeChanges(path + ".biomes", action.getBiomeChanges());
        writeBiomeColumnChanges(path + ".biomeColumns", action.getBiomeColumnChanges());
        writeSelection(path + ".beforeSelection", action.getBeforeSelection());
        writeSelection(path + ".afterSelection", action.getAfterSelection());
    }

    private List<EditAction> readActions(String path) {
        ConfigurationSection section = yaml.getConfigurationSection(path);
        if (section == null) {
            return List.of();
        }
        List<String> keys = sortedSectionKeys(section);
        List<EditAction> actions = new ArrayList<>();
        for (String key : keys) {
            if (yaml.getBoolean(path + "." + key + ".tooLargeToPersist", false)) {
                // The stub's undo data was never persisted. Entries after it (older for undo, deeper for redo)
                // only make sense applied on top of it, so applying them would skip an edit; drop the rest.
                break;
            }
            EditAction action = readAction(path + "." + key);
            if (action != null) {
                actions.add(action);
            }
        }
        return actions;
    }

    private EditAction readAction(String path) {
        // Skip stubs left by oversize actions (their undo data was never persisted).
        if (yaml.getBoolean(path + ".tooLargeToPersist", false)) {
            return null;
        }
        List<BlockChange> blockChanges = readBlockChanges(path + ".blocks");
        List<EntityChange> entityChanges = readEntityChanges(path + ".entities");
        List<BiomeChange> biomeChanges = readBiomeChanges(path + ".biomes");
        List<BiomeColumnChange> biomeColumnChanges = readBiomeColumnChanges(path + ".biomeColumns");
        SelectionSnapshot beforeSelection = readSelection(path + ".beforeSelection");
        SelectionSnapshot afterSelection = readSelection(path + ".afterSelection");
        if (blockChanges.isEmpty()
                && entityChanges.isEmpty()
                && biomeChanges.isEmpty()
                && biomeColumnChanges.isEmpty()
                && beforeSelection == null
                && afterSelection == null) {
            return null;
        }
        return new EditAction(blockChanges, entityChanges, biomeChanges, biomeColumnChanges, beforeSelection, afterSelection);
    }

    private void writeBlockChanges(String path, List<BlockChange> changes) {
        yaml.set(path, null);
        for (int i = 0; i < changes.size(); i++) {
            BlockChange change = changes.get(i);
            writeLocation(path + "." + i + ".location", change.getLocation());
            yaml.set(path + "." + i + ".before", change.getBefore().getAsString());
            yaml.set(path + "." + i + ".after", change.getAfter().getAsString());
        }
    }

    private List<BlockChange> readBlockChanges(String path) {
        ConfigurationSection section = yaml.getConfigurationSection(path);
        if (section == null) {
            return List.of();
        }
        List<String> keys = sortedSectionKeys(section);
        List<BlockChange> changes = new ArrayList<>();
        for (String key : keys) {
            Location location = readLocation(path + "." + key + ".location");
            if (location == null) {
                continue;
            }
            String before = yaml.getString(path + "." + key + ".before");
            String after = yaml.getString(path + "." + key + ".after");
            if (before == null || after == null) {
                continue;
            }
            try {
                BlockData beforeData = Bukkit.createBlockData(before);
                BlockData afterData = Bukkit.createBlockData(after);
                changes.add(new BlockChange(location, beforeData, afterData));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return changes;
    }

    private void writeEntityChanges(String path, List<EntityChange> changes) {
        yaml.set(path, null);
        for (int i = 0; i < changes.size(); i++) {
            EntityChange change = changes.get(i);
            ClipboardEntity entity = change.getEntity();
            yaml.set(path + "." + i + ".kind", change.getKind().name());
            writeLocation(path + "." + i + ".location", change.getLocation());
            yaml.set(path + "." + i + ".liveEntityId", change.getLiveEntityId() == null ? null : change.getLiveEntityId().toString());
            yaml.set(path + "." + i + ".entity.glow", entity.isGlow());
            yaml.set(path + "." + i + ".entity.supportOffsetX", entity.getSupportOffsetX());
            yaml.set(path + "." + i + ".entity.supportOffsetY", entity.getSupportOffsetY());
            yaml.set(path + "." + i + ".entity.supportOffsetZ", entity.getSupportOffsetZ());
            yaml.set(path + "." + i + ".entity.snapshot", entity.getSnapshotData());
            yaml.set(path + "." + i + ".entity.facing", entity.getFacing() == null ? null : entity.getFacing().name());
            yaml.set(path + "." + i + ".entity.rotation", entity.getRotation() == null ? null : entity.getRotation().name());
            yaml.set(path + "." + i + ".entity.item", entity.getItem());
            yaml.set(path + "." + i + ".entity.visible", entity.isVisible());
            yaml.set(path + "." + i + ".entity.fixed", entity.isFixed());
            yaml.set(path + "." + i + ".entity.itemDropChance", entity.getItemDropChance());
            yaml.set(path + "." + i + ".entity.paintingArt", entity.getPaintingArt() == null ? null : entity.getPaintingArt().name());
        }
    }

    private List<EntityChange> readEntityChanges(String path) {
        ConfigurationSection section = yaml.getConfigurationSection(path);
        if (section == null) {
            return List.of();
        }
        List<String> keys = sortedSectionKeys(section);
        List<EntityChange> changes = new ArrayList<>();
        for (String key : keys) {
            String base = path + "." + key;
            Location location = readLocation(base + ".location");
            String kindRaw = yaml.getString(base + ".kind");
            if (location == null || kindRaw == null) {
                continue;
            }
            try {
                EntityChange.Kind kind = EntityChange.Kind.valueOf(kindRaw.toUpperCase(Locale.ROOT));
                EntitySnapshot snapshot = null;
                String snapshotRaw = yaml.getString(base + ".entity.snapshot");
                if (snapshotRaw != null && !snapshotRaw.isBlank()) {
                    snapshot = Bukkit.getEntityFactory().createEntitySnapshot(snapshotRaw);
                }
                EntityType snapshotType = snapshot == null ? null : snapshot.getEntityType();
                boolean hangingEntity = snapshot == null
                        || snapshotType == EntityType.ITEM_FRAME
                        || snapshotType == EntityType.GLOW_ITEM_FRAME
                        || snapshotType == EntityType.PAINTING;
                Art paintingArt = readArt(base + ".entity.paintingArt");
                ClipboardEntity entity = new ClipboardEntity(
                        yaml.getBoolean(base + ".entity.glow"),
                        yaml.getDouble(base + ".entity.supportOffsetX"),
                        yaml.getDouble(base + ".entity.supportOffsetY"),
                        yaml.getDouble(base + ".entity.supportOffsetZ"),
                        hangingEntity ? BlockFace.valueOf(yaml.getString(base + ".entity.facing", BlockFace.NORTH.name()).toUpperCase(Locale.ROOT)) : null,
                        hangingEntity && snapshotType != EntityType.PAINTING
                                ? Rotation.valueOf(yaml.getString(base + ".entity.rotation", Rotation.NONE.name()).toUpperCase(Locale.ROOT))
                                : null,
                        hangingEntity && snapshotType != EntityType.PAINTING ? yaml.getItemStack(base + ".entity.item") : null,
                        hangingEntity ? yaml.getBoolean(base + ".entity.visible", true) : true,
                        hangingEntity ? yaml.getBoolean(base + ".entity.fixed", false) : false,
                        (float) yaml.getDouble(base + ".entity.itemDropChance", 1.0D),
                        paintingArt,
                        snapshot,
                        (float) yaml.getDouble(base + ".entity.yaw", 0.0D),
                        (float) yaml.getDouble(base + ".entity.pitch", 0.0D)
                );
                UUID liveEntityId = null;
                String liveRaw = yaml.getString(base + ".liveEntityId");
                if (liveRaw != null && !liveRaw.isBlank()) {
                    liveEntityId = UUID.fromString(liveRaw);
                }
                changes.add(EntityChange.restored(kind, entity, location, liveEntityId));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return changes;
    }

    private Art readArt(String path) {
        String raw = yaml.getString(path);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Art.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private void writeBiomeChanges(String path, List<BiomeChange> changes) {
        yaml.set(path, null);
        for (int i = 0; i < changes.size(); i++) {
            BiomeChange change = changes.get(i);
            writeLocation(path + "." + i + ".location", change.getLocation());
            yaml.set(path + "." + i + ".before", change.getBefore().name());
            yaml.set(path + "." + i + ".after", change.getAfter().name());
        }
    }

    private List<BiomeChange> readBiomeChanges(String path) {
        ConfigurationSection section = yaml.getConfigurationSection(path);
        if (section == null) {
            return List.of();
        }
        List<String> keys = sortedSectionKeys(section);
        List<BiomeChange> changes = new ArrayList<>();
        for (String key : keys) {
            Location location = readLocation(path + "." + key + ".location");
            String before = yaml.getString(path + "." + key + ".before");
            String after = yaml.getString(path + "." + key + ".after");
            if (location == null || before == null || after == null) {
                continue;
            }
            try {
                changes.add(new BiomeChange(location, Biome.valueOf(before), Biome.valueOf(after)));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return changes;
    }

    private void writeBiomeColumnChanges(String path, List<BiomeColumnChange> changes) {
        yaml.set(path, null);
        for (int i = 0; i < changes.size(); i++) {
            BiomeColumnChange change = changes.get(i);
            yaml.set(path + "." + i + ".world", change.getWorld().getName());
            yaml.set(path + "." + i + ".x", change.getX());
            yaml.set(path + "." + i + ".z", change.getZ());
            yaml.set(path + "." + i + ".before", change.getBefore().name());
            yaml.set(path + "." + i + ".after", change.getAfter().name());
        }
    }

    private List<BiomeColumnChange> readBiomeColumnChanges(String path) {
        ConfigurationSection section = yaml.getConfigurationSection(path);
        if (section == null) {
            return List.of();
        }
        List<String> keys = sortedSectionKeys(section);
        List<BiomeColumnChange> changes = new ArrayList<>();
        for (String key : keys) {
            String worldName = yaml.getString(path + "." + key + ".world");
            if (worldName == null) {
                continue;
            }
            World world = Bukkit.getWorld(worldName);
            if (world == null) {
                continue;
            }
            try {
                changes.add(new BiomeColumnChange(
                        world,
                        yaml.getInt(path + "." + key + ".x"),
                        yaml.getInt(path + "." + key + ".z"),
                        Biome.valueOf(yaml.getString(path + "." + key + ".before", Biome.PLAINS.name())),
                        Biome.valueOf(yaml.getString(path + "." + key + ".after", Biome.PLAINS.name()))
                ));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return changes;
    }

    private void writeSelection(String path, SelectionSnapshot snapshot) {
        yaml.set(path, null);
        if (snapshot == null) {
            return;
        }
        writeLocation(path + ".pos1", snapshot.pos1());
        writeLocation(path + ".pos2", snapshot.pos2());
        yaml.set(path + ".type", snapshot.type() == null ? null : snapshot.type().name());
    }

    private SelectionSnapshot readSelection(String path) {
        Location pos1 = readLocation(path + ".pos1");
        Location pos2 = readLocation(path + ".pos2");
        String typeRaw = yaml.getString(path + ".type");
        if (pos1 == null || pos2 == null || typeRaw == null) {
            return null;
        }
        try {
            return new SelectionSnapshot(pos1, pos2, SelectionType.valueOf(typeRaw.toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private void writeLocation(String path, Location location) {
        yaml.set(path, null);
        if (location == null || location.getWorld() == null) {
            return;
        }
        yaml.set(path + ".world", location.getWorld().getName());
        yaml.set(path + ".x", location.getBlockX());
        yaml.set(path + ".y", location.getBlockY());
        yaml.set(path + ".z", location.getBlockZ());
    }

    private Location readLocation(String path) {
        String worldName = yaml.getString(path + ".world");
        if (worldName == null) {
            return null;
        }
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            return null;
        }
        return new Location(
                world,
                yaml.getInt(path + ".x"),
                yaml.getInt(path + ".y"),
                yaml.getInt(path + ".z")
        );
    }

    private void saveFile() {
        // saveLock serializes (a) yaml-tree mutations from save() and (b) yaml.save()
        // emit/write on the async thread. snakeyaml's emit walks the tree, so we can't
        // let it run while another save() is mutating it.
        synchronized (saveLock) {
            try {
                // Write-then-rename so a crash mid-write cannot truncate every player's history.
                java.nio.file.Path target = file.toPath();
                java.nio.file.Path temp = target.resolveSibling(file.getName() + ".tmp");
                java.nio.file.Files.createDirectories(target.toAbsolutePath().getParent());
                java.nio.file.Files.writeString(temp, yaml.saveToString(), java.nio.charset.StandardCharsets.UTF_8);
                try {
                    java.nio.file.Files.move(temp, target, java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                } catch (java.nio.file.AtomicMoveNotSupportedException ex) {
                    java.nio.file.Files.move(temp, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException ex) {
                plugin.getLogger().warning("Could not save edit-history.yml: " + ex.getMessage());
            }
        }
    }

    private List<String> sortedSectionKeys(ConfigurationSection section) {
        List<String> keys = new ArrayList<>(section.getKeys(false));
        keys.sort((left, right) -> Integer.compare(parseIndex(left), parseIndex(right)));
        return keys;
    }

    private int parseIndex(String key) {
        try {
            return Integer.parseInt(key);
        } catch (NumberFormatException ignored) {
            return Integer.MAX_VALUE;
        }
    }

    public record LoadedHistory(List<EditAction> undoActions, List<EditAction> redoActions) {
    }
}
