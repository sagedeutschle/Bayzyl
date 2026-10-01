package com.bayzyl;

import org.bukkit.*;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.BlockState;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.entity.EntitySnapshot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.Rotation;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.function.Consumer;

/**
 * Crash recovery system for Bayzyl.
 * 
 * Tracks active commands and sessions so they can be restored if the server crashes.
 * Persists clipboard, nudge sessions, and active operation state to survive restarts.
 * 
 * Similar to WorldEdit's crash recovery where operations persist across restarts.
 */
public final class CrashRecoveryService {
    private static final long SAVE_INTERVAL_TICKS = 20 * 30; // Every 30 seconds
    private static final long GRACE_PERIOD_MS = 1000 * 60 * 5; // 5 minutes for crash detection
    
    private final JavaPlugin plugin;
    private final File file;
    private final YamlConfiguration yaml;
    private final Map<UUID, ActiveCommandSession> activeSessions = new ConcurrentHashMap<>();
    private final Set<UUID> dirtyPlayers = new ConcurrentSkipListSet<>();
    private BukkitTask saveTask;
    private long lastSaveTime = System.currentTimeMillis();
    
    public CrashRecoveryService(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "crash-recovery.yml");
        this.yaml = YamlConfiguration.loadConfiguration(file);
        loadSessions();
        startSaveTask();
    }
    
    /**
     * Start tracking an active command session.
     * @param playerId Player performing the command
     * @param command The command being executed (e.g., "/copy", "/paste", "/stack")
     * @param stage Current stage of execution (parsing, executing, waiting, etc.)
     * @param data Command-specific data to restore from
     * @param onResume Callback to execute when session is resumed after crash
     */
    public void startSession(UUID playerId, String command, String stage, Map<String, Object> data, 
                           Consumer<ActiveCommandSession> onResume) {
        ActiveCommandSession session = new ActiveCommandSession(
            playerId, command, stage, data, System.currentTimeMillis(),
            onResume, true
        );
        activeSessions.put(playerId, session);
        dirtyPlayers.add(playerId);
    }
    
    /**
     * Update an existing session with new stage/data.
     */
    public void updateSession(UUID playerId, String stage, Map<String, Object> data) {
        ActiveCommandSession session = activeSessions.get(playerId);
        if (session != null) {
            ActiveCommandSession updated = new ActiveCommandSession(
                session.playerId, session.command, stage, data,
                System.currentTimeMillis(), session.onResume, true
            );
            activeSessions.put(playerId, updated);
            dirtyPlayers.add(playerId);
        }
    }
    
    /**
     * Complete a session (command finished successfully).
     */
    public void completeSession(UUID playerId) {
        ActiveCommandSession removed = activeSessions.remove(playerId);
        if (removed != null) {
            dirtyPlayers.add(playerId);
            yaml.set("sessions." + playerId, null);
            saveFileIfDirty();
        }
    }
    
    /**
     * Get active session for player, if any.
     */
    @Nullable
    public ActiveCommandSession getSession(UUID playerId) {
        return activeSessions.get(playerId);
    }
    
    /**
     * Check if player has an active session that was interrupted.
     */
    public boolean hasInterruptedSession(UUID playerId) {
        ActiveCommandSession session = activeSessions.get(playerId);
        if (session == null) return false;
        // Session is considered interrupted if it's still active but player hasn't touched it
        // in a while (indicating server crashed during it)
        return session.active && 
               (System.currentTimeMillis() - session.lastUpdateTime) > GRACE_PERIOD_MS;
    }
    
    /**
     * Resume an interrupted session.
     */
    public void resumeSession(Player player) {
        ActiveCommandSession session = activeSessions.get(player.getUniqueId());
        if (session != null && session.onResume != null) {
            try {
                session.onResume.accept(session);
                // Mark as resumed but keep for completion tracking
                ActiveCommandSession resumed = new ActiveCommandSession(
                    session.playerId, session.command, "resumed", session.data,
                    System.currentTimeMillis(), null, false
                );
                activeSessions.put(player.getUniqueId(), resumed);
                dirtyPlayers.add(player.getUniqueId());
                
                player.sendMessage("§6[Bayzyl] §7Resuming interrupted command: §f" + session.command);
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to resume session for " + player.getName() + ": " + e.getMessage());
                completeSession(player.getUniqueId());
            }
        }
    }
    
    /**
     * Save clipboard to recovery storage.
     */
    public void saveClipboard(UUID playerId, Clipboard clipboard) {
        if (clipboard == null) {
            yaml.set("clipboards." + playerId, null);
        } else {
            writeClipboard("clipboards." + playerId, clipboard);
        }
        dirtyPlayers.add(playerId);
    }
    
    /**
     * Load clipboard from recovery storage.
     */
    @Nullable
    public Clipboard loadClipboard(UUID playerId) {
        return readClipboard("clipboards." + playerId);
    }
    
/**
     * Save nudge session state.
     */
    public void saveNudgeSession(UUID playerId, EditService.NudgeSession nudgeSession) {
        if (nudgeSession == null) {
            yaml.set("nudge." + playerId, null);
        } else {
            writeNudgeSession("nudge." + playerId, nudgeSession);
        }
        dirtyPlayers.add(playerId);
    }
    
    /**
     * Load nudge session state.
     */
    @Nullable
    public EditService.NudgeSession loadNudgeSession(UUID playerId) {
        return readNudgeSession("nudge." + playerId);
    }
    
    /**
     * Check if there was a crash (unclean shutdown).
     */
    public boolean wasCrashDetected() {
        long lastCleanShutdown = yaml.getLong("meta.lastCleanShutdown", 0);
        long fileTimestamp = file.lastModified();
        // If file is newer than last clean shutdown, we probably crashed
        return fileTimestamp > lastCleanShutdown;
    }
    
    /**
     * Mark clean shutdown (call on plugin disable).
     */
    public void markCleanShutdown() {
        yaml.set("meta.lastCleanShutdown", System.currentTimeMillis());
        saveFile();
    }
    
    /**
     * Clean up old sessions (called periodically).
     */
    public void cleanupOldSessions() {
        long cutoff = System.currentTimeMillis() - (1000 * 60 * 60 * 24); // 24 hours
        Iterator<Map.Entry<UUID, ActiveCommandSession>> it = activeSessions.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, ActiveCommandSession> entry = it.next();
            if (entry.getValue().lastUpdateTime < cutoff) {
                it.remove();
                yaml.set("sessions." + entry.getKey(), null);
            }
        }
        saveFileIfDirty();
    }
    
    private void loadSessions() {
        ConfigurationSection sessionsSection = yaml.getConfigurationSection("sessions");
        if (sessionsSection != null) {
            for (String playerIdStr : sessionsSection.getKeys(false)) {
                try {
                    UUID playerId = UUID.fromString(playerIdStr);
                    ActiveCommandSession session = readSession("sessions." + playerIdStr);
                    if (session != null) {
                        activeSessions.put(playerId, session);
                    }
                } catch (IllegalArgumentException ignored) {}
            }
        }
    }
    
    private void startSaveTask() {
        saveTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            saveFileIfDirty();
            cleanupOldSessions();
        }, SAVE_INTERVAL_TICKS, SAVE_INTERVAL_TICKS);
    }
    
    private void saveFileIfDirty() {
        if (!dirtyPlayers.isEmpty() || (System.currentTimeMillis() - lastSaveTime) > 10000) {
            saveFile();
        }
    }
    
    private synchronized void saveFile() {
        // Save active sessions
        for (UUID playerId : dirtyPlayers) {
            ActiveCommandSession session = activeSessions.get(playerId);
            if (session != null) {
                writeSession("sessions." + playerId, session);
            } else {
                yaml.set("sessions." + playerId, null);
            }
        }
        dirtyPlayers.clear();
        
        try {
            yaml.save(file);
            lastSaveTime = System.currentTimeMillis();
        } catch (IOException ex) {
            plugin.getLogger().warning("Could not save crash-recovery.yml: " + ex.getMessage());
        }
    }
    
    // Session serialization
    private void writeSession(String path, ActiveCommandSession session) {
        yaml.set(path + ".command", session.command);
        yaml.set(path + ".stage", session.stage);
        yaml.set(path + ".lastUpdateTime", session.lastUpdateTime);
        yaml.set(path + ".active", session.active);
        
        // Write data map
        if (session.data != null && !session.data.isEmpty()) {
            for (Map.Entry<String, Object> entry : session.data.entrySet()) {
                Object value = entry.getValue();
                if (value instanceof Location) {
                    writeLocation(path + ".data." + entry.getKey(), (Location) value);
                } else if (value instanceof UUID) {
                    yaml.set(path + ".data." + entry.getKey(), value.toString());
                } else {
                    yaml.set(path + ".data." + entry.getKey(), value);
                }
            }
        }
    }
    
    private ActiveCommandSession readSession(String path) {
        String command = yaml.getString(path + ".command");
        String stage = yaml.getString(path + ".stage");
        long lastUpdateTime = yaml.getLong(path + ".lastUpdateTime");
        boolean active = yaml.getBoolean(path + ".active", true);
        
        if (command == null || stage == null) {
            return null;
        }
        
        Map<String, Object> data = new HashMap<>();
        ConfigurationSection dataSection = yaml.getConfigurationSection(path + ".data");
        if (dataSection != null) {
            for (String key : dataSection.getKeys(false)) {
                Object value = dataSection.get(key);
                data.put(key, value);
            }
        }
        
        return new ActiveCommandSession(
            null, // playerId will be set by caller
            command, stage, data, lastUpdateTime,
            null, // onResume can't be serialized
            active
        );
    }
    
    // Clipboard serialization
    private void writeClipboard(String path, Clipboard clipboard) {
        if (clipboard == null) {
            yaml.set(path, null);
            return;
        }
        
        yaml.set(path + ".sizeX", clipboard.getSizeX());
        yaml.set(path + ".sizeY", clipboard.getSizeY());
        yaml.set(path + ".sizeZ", clipboard.getSizeZ());
        writeLocation(path + ".origin", clipboard.getOrigin());
        yaml.set(path + ".minOffsetX", clipboard.getMinOffsetX());
        yaml.set(path + ".minOffsetY", clipboard.getMinOffsetY());
        yaml.set(path + ".minOffsetZ", clipboard.getMinOffsetZ());
        
        // Save block data as string array
        int totalSize = clipboard.getSizeX() * clipboard.getSizeY() * clipboard.getSizeZ();
        List<String> blockDataStrings = new ArrayList<>(totalSize);
        for (int y = 0; y < clipboard.getSizeY(); y++) {
            for (int z = 0; z < clipboard.getSizeZ(); z++) {
                for (int x = 0; x < clipboard.getSizeX(); x++) {
                    BlockData data = clipboard.get(x, y, z);
                    blockDataStrings.add(data != null ? data.getAsString() : "minecraft:air");
                }
            }
        }
        yaml.set(path + ".blockData", blockDataStrings);
        
        // Save block states (simplified - just store as strings for now)
        List<String> stateStrings = new ArrayList<>();
        int totalBlocks = clipboard.getSizeX() * clipboard.getSizeY() * clipboard.getSizeZ();
        for (int i = 0; i < totalBlocks; i++) {
            // Calculate 3D coordinates from linear index
            int x = i % clipboard.getSizeX();
            int yz = i / clipboard.getSizeX();
            int z = yz % clipboard.getSizeZ();
            int y = yz / clipboard.getSizeZ();
            
            BlockState state = clipboard.getState(x, y, z);
            if (state != null) {
                // For tile entities, we'd need proper NBT serialization
                // This is a simplified placeholder
                stateStrings.add(state.getClass().getName() + ":" + state.getLocation());
            }
        }
        yaml.set(path + ".states", stateStrings);
        
        // Save entities
        List<ClipboardEntity> entities = clipboard.getEntities();
        if (entities != null && !entities.isEmpty()) {
            yaml.set(path + ".entityCount", entities.size());
            for (int i = 0; i < entities.size(); i++) {
                writeClipboardEntity(path + ".entities." + i, entities.get(i));
            }
        }
        
        yaml.set(path + ".hasData", true);
    }
    
    private Clipboard readClipboard(String path) {
        if (!yaml.getBoolean(path + ".hasData", false)) {
            return null;
        }
        
        int sizeX = yaml.getInt(path + ".sizeX");
        int sizeY = yaml.getInt(path + ".sizeY");
        int sizeZ = yaml.getInt(path + ".sizeZ");
        Location origin = readLocation(path + ".origin");
        int minOffsetX = yaml.getInt(path + ".minOffsetX");
        int minOffsetY = yaml.getInt(path + ".minOffsetY");
        int minOffsetZ = yaml.getInt(path + ".minOffsetZ");
        
        if (origin == null) {
            return null;
        }
        
        List<String> blockDataStrings = yaml.getStringList(path + ".blockData");
        int expectedSize = sizeX * sizeY * sizeZ;
        if (blockDataStrings.size() != expectedSize) {
            plugin.getLogger().warning("Clipboard block data size mismatch for " + path);
            return null;
        }
        
        BlockData[] data = new BlockData[expectedSize];
        for (int i = 0; i < expectedSize; i++) {
            try {
                data[i] = Bukkit.createBlockData(blockDataStrings.get(i));
            } catch (IllegalArgumentException e) {
                data[i] = Bukkit.createBlockData("minecraft:air");
            }
        }
        
        // Block states - simplified restoration (would need proper NBT in real implementation)
        BlockState[] states = null;
        List<String> stateStrings = yaml.getStringList(path + ".states");
        if (!stateStrings.isEmpty()) {
            states = new BlockState[stateStrings.size()];
            // In real implementation, would deserialize NBT data
        }
        
        // Entities
        List<ClipboardEntity> entities = new ArrayList<>();
        int entityCount = yaml.getInt(path + ".entityCount", 0);
        for (int i = 0; i < entityCount; i++) {
            ClipboardEntity entity = readClipboardEntity(path + ".entities." + i);
            if (entity != null) {
                entities.add(entity);
            }
        }
        
        return new Clipboard(sizeX, sizeY, sizeZ, data, states, entities, origin, minOffsetX, minOffsetY, minOffsetZ);
    }
    
    private void writeClipboardEntity(String path, ClipboardEntity entity) {
        yaml.set(path + ".glow", entity.isGlow());
        yaml.set(path + ".supportOffsetX", entity.getSupportOffsetX());
        yaml.set(path + ".supportOffsetY", entity.getSupportOffsetY());
        yaml.set(path + ".supportOffsetZ", entity.getSupportOffsetZ());
        yaml.set(path + ".visible", entity.isVisible());
        yaml.set(path + ".fixed", entity.isFixed());
        yaml.set(path + ".itemDropChance", entity.getItemDropChance());
        yaml.set(path + ".yaw", entity.getYaw());
        yaml.set(path + ".pitch", entity.getPitch());
        
        if (entity.getFacing() != null) {
            yaml.set(path + ".facing", entity.getFacing().name());
        }
        if (entity.getRotation() != null) {
            yaml.set(path + ".rotation", entity.getRotation().name());
        }
        if (entity.getPaintingArt() != null) {
            yaml.set(path + ".paintingArt", entity.getPaintingArt().name());
        }
        
        // Entity snapshot data (simplified)
        if (entity.getSnapshotData() != null) {
            yaml.set(path + ".snapshotData", entity.getSnapshotData());
        }
        if (entity.getItem() != null) {
            yaml.set(path + ".item", entity.getItem());
        }
    }
    
    private ClipboardEntity readClipboardEntity(String path) {
        boolean glow = yaml.getBoolean(path + ".glow");
        double supportOffsetX = yaml.getDouble(path + ".supportOffsetX");
        double supportOffsetY = yaml.getDouble(path + ".supportOffsetY");
        double supportOffsetZ = yaml.getDouble(path + ".supportOffsetZ");
        boolean visible = yaml.getBoolean(path + ".visible", true);
        boolean fixed = yaml.getBoolean(path + ".fixed", false);
        float itemDropChance = (float) yaml.getDouble(path + ".itemDropChance", 1.0);
        float yaw = (float) yaml.getDouble(path + ".yaw", 0.0);
        float pitch = (float) yaml.getDouble(path + ".pitch", 0.0);
        
        BlockFace facing = null;
        String facingStr = yaml.getString(path + ".facing");
        if (facingStr != null) {
            try {
                facing = BlockFace.valueOf(facingStr);
            } catch (IllegalArgumentException ignored) {}
        }
        
        Rotation rotation = null;
        String rotationStr = yaml.getString(path + ".rotation");
        if (rotationStr != null) {
            try {
                rotation = Rotation.valueOf(rotationStr);
            } catch (IllegalArgumentException ignored) {}
        }
        
        Art paintingArt = null;
        String artStr = yaml.getString(path + ".paintingArt");
        if (artStr != null) {
            try {
                paintingArt = Art.valueOf(artStr);
            } catch (IllegalArgumentException ignored) {}
        }
        
        String snapshotData = yaml.getString(path + ".snapshotData");
        ItemStack item = yaml.getItemStack(path + ".item");
        
        // Create entity snapshot from data (simplified)
        EntitySnapshot snapshot = null;
        if (snapshotData != null && !snapshotData.isBlank()) {
            try {
                snapshot = Bukkit.getEntityFactory().createEntitySnapshot(snapshotData);
            } catch (Exception ignored) {}
        }
        
        return new ClipboardEntity(glow, supportOffsetX, supportOffsetY, supportOffsetZ, 
                                  facing, rotation, item, visible, fixed, itemDropChance,
                                  paintingArt, snapshot, yaw, pitch);
    }
    
    // Nudge session serialization
    private void writeNudgeSession(String path, EditService.NudgeSession session) {
        writeClipboard(path + ".clipboard", session.clipboard());
        writeSelection(path + ".selection", session.selection());
    }
    
    private EditService.NudgeSession readNudgeSession(String path) {
        Clipboard clipboard = readClipboard(path + ".clipboard");
        Selection selection = readSelection(path + ".selection");
        if (clipboard == null || selection == null) {
            return null;
        }
        // Create a new NudgeSession with empty background map
        return new EditService.NudgeSession(clipboard, selection, new ConcurrentHashMap<>());
    }
    
    // Helper serialization methods
    private void writeLocation(String path, Location location) {
        if (location == null || location.getWorld() == null) return;
        yaml.set(path + ".world", location.getWorld().getName());
        yaml.set(path + ".x", location.getX());
        yaml.set(path + ".y", location.getY());
        yaml.set(path + ".z", location.getZ());
    }
    
    private Location readLocation(String path) {
        String worldName = yaml.getString(path + ".world");
        if (worldName == null) return null;
        World world = Bukkit.getWorld(worldName);
        if (world == null) return null;
        return new Location(world,
            yaml.getDouble(path + ".x"),
            yaml.getDouble(path + ".y"),
            yaml.getDouble(path + ".z")
        );
    }
    
    private void writeSelection(String path, Selection selection) {
        if (selection == null) return;
        writeLocation(path + ".pos1", selection.getPos1());
        writeLocation(path + ".pos2", selection.getPos2());
        yaml.set(path + ".type", selection.getType().name());
    }
    
    private Selection readSelection(String path) {
        Location pos1 = readLocation(path + ".pos1");
        Location pos2 = readLocation(path + ".pos2");
        String typeName = yaml.getString(path + ".type");
        if (pos1 == null || pos2 == null || typeName == null) return null;
        try {
            SelectionType type = SelectionType.valueOf(typeName);
            return new Selection(pos1, pos2, type);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
    
    public void disable() {
        if (saveTask != null) {
            saveTask.cancel();
        }
        markCleanShutdown();
        saveFile();
    }
    
    /**
     * Represents an active command session that can be resumed after crash.
     */
    public static record ActiveCommandSession(
        UUID playerId,
        String command,
        String stage,
        Map<String, Object> data,
        long lastUpdateTime,
        @Nullable Consumer<ActiveCommandSession> onResume,
        boolean active
    ) {}
}