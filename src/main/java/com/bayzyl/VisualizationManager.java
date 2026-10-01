package com.bayzyl;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class VisualizationManager {
    private static final long GRID_THRESHOLD = 200_000L;
    private static final int MAX_OUTLINE_EMITS = 240;
    private static final int MAX_GRID_EMITS = 180;
    private static final int MAX_TOTAL_EMITS = 360;

    // Multiplayer remote-selection rendering. We draw OTHER players' selections to a
    // viewer with the same particle TYPE (Particle.DUST) but a lighter outline:
    //   - smaller particle size
    //   - higher minimum step (fewer emits)
    //   - outline only (no grid)
    //   - hard per-peer + per-tick caps so a crowded server can't flood the viewer.
    private static final float REMOTE_OUTLINE_PARTICLE_SIZE = 0.75f;
    private static final int REMOTE_OUTLINE_MIN_STEP = 2;
    private static final int MAX_REMOTE_EMITS_PER_PEER = 90;
    private static final int MAX_REMOTE_EMITS_PER_TICK = 240;

    private final SelectionManager selectionManager;
    private final Map<UUID, VisualizationSettings> settings = new ConcurrentHashMap<>();
    private final Set<UUID> centerMarkers = ConcurrentHashMap.newKeySet();
    private static final Color DEFAULT_SAGE = Color.fromRGB(120, 255, 165);
    private final Map<SelectionType, Color> typeColors = new EnumMap<>(SelectionType.class);
    private int emitBudget = MAX_TOTAL_EMITS;

    public VisualizationManager(SelectionManager selectionManager) {
        this.selectionManager = selectionManager;
        typeColors.put(SelectionType.CUBOID, DEFAULT_SAGE);
        typeColors.put(SelectionType.POLYGONAL, Color.fromRGB(255, 190, 90));
        typeColors.put(SelectionType.CYLINDER, Color.fromRGB(90, 160, 255));
    }

    public VisualizationSettings getSettings(UUID playerId) {
        return settings.computeIfAbsent(playerId, id -> new VisualizationSettings());
    }

    public void clear(UUID playerId) {
        settings.remove(playerId);
        centerMarkers.remove(playerId);
    }

    public void showSelectionCenter(UUID playerId) {
        centerMarkers.add(playerId);
    }

    public void clearSelectionCenter(UUID playerId) {
        centerMarkers.remove(playerId);
    }

    public boolean isSelectionCenterVisible(UUID playerId) {
        return centerMarkers.contains(playerId);
    }

    public void render(Player viewer) {
        VisualizationSettings config = getSettings(viewer.getUniqueId());
        World viewerWorld = viewer.getWorld();
        Selection ownSelection = selectionManager.get(viewer.getUniqueId());

        // Viewer's own selection (full-strength)
        Color viewerColor = null;
        if (ownSelection != null && ownSelection.isComplete()
                && ownSelection.getPos1() != null
                && ownSelection.getPos1().getWorld() != null
                && config.isEnabled()) {
            renderOwn(viewer, ownSelection, config);
            viewerColor = resolveColor(ownSelection, config);
        }

        // Other players' selections in the same world — same particle type, lighter outline,
        // distinct color. If a peer's color collides with the viewer's, the peer is shown
        // inverted to that viewer so the two selections stay visually distinct.
        if (config.isEnabled()) {
            renderRemoteSelections(viewer, viewerWorld, viewerColor);
        }

        if (centerMarkers.contains(viewer.getUniqueId()) && ownSelection != null && ownSelection.isComplete()) {
            emitBudget = MAX_TOTAL_EMITS;
            drawSelectionCenter(viewer, ownSelection, config);
        }
    }

    private void renderOwn(Player player, Selection selection, VisualizationSettings config) {
        long volume = selection.getVolume();
        boolean grid = shouldUseGrid(config, volume);
        int outlineStep = resolveOutlineStep(config, selection, volume, grid);
        int gridStep = resolveGridStep(selection, outlineStep);

        Color color = resolveColor(selection, config);
        float size = resolveParticleSize(config);
        Particle.DustOptions dust = new Particle.DustOptions(color, size);

        emitBudget = MAX_TOTAL_EMITS;
        drawCuboidOutline(player, selection, outlineStep, dust, config);
        if (grid) {
            drawCuboidGrid(player, selection, gridStep, dust, config);
        }
    }

    private void renderRemoteSelections(Player viewer, World viewerWorld, Color viewerColor) {
        int totalBudget = MAX_REMOTE_EMITS_PER_TICK;
        UUID viewerId = viewer.getUniqueId();
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (totalBudget <= 0) break;
            if (other.getUniqueId().equals(viewerId)) continue;
            if (!viewerWorld.equals(other.getWorld())) continue;

            Selection otherSelection = selectionManager.get(other.getUniqueId());
            if (otherSelection == null || !otherSelection.isComplete()) continue;
            Location pos1 = otherSelection.getPos1();
            if (pos1 == null || !viewerWorld.equals(pos1.getWorld())) continue;

            VisualizationSettings otherConfig = getSettings(other.getUniqueId());
            if (!otherConfig.isEnabled()) continue;

            Color peerColor = resolveColor(otherSelection, otherConfig);
            // Color-conflict resolution: if the viewer's own selection color matches
            // this peer's color exactly, draw the peer's selection in the inverted
            // palette so the viewer can still distinguish their own from the peer's.
            if (viewerColor != null && colorsEqual(viewerColor, peerColor)) {
                peerColor = invert(peerColor);
            }

            int spent = drawRemoteOutline(viewer, otherSelection, peerColor, totalBudget);
            totalBudget -= spent;
        }
    }

    /**
     * Draws a peer's selection outline for {@code viewer} with the lighter "remote" style.
     * Returns the number of particle emits actually used so the per-tick budget stays bounded.
     */
    private int drawRemoteOutline(Player viewer, Selection selection, Color color, int sharedBudgetRemaining) {
        long sizeX = (long) selection.getMaxX() - selection.getMinX() + 2;
        long sizeY = (long) selection.getMaxY() - selection.getMinY() + 2;
        long sizeZ = (long) selection.getMaxZ() - selection.getMinZ() + 2;
        long outlineEmitsAtStepOne = 4L * (sizeX + sizeY + sizeZ);
        int allowed = Math.min(MAX_REMOTE_EMITS_PER_PEER, sharedBudgetRemaining);
        int neededStep = (int) Math.max(1L, (long) Math.ceil(outlineEmitsAtStepOne / (double) allowed));
        int step = Math.max(REMOTE_OUTLINE_MIN_STEP, neededStep);

        Particle.DustOptions dust = new Particle.DustOptions(color, REMOTE_OUTLINE_PARTICLE_SIZE);

        // Use a temporary, low-jitter, low-count config so the remote outline reads as
        // "thinner" than the viewer's own selection without being noisy.
        VisualizationSettings remoteConfig = new VisualizationSettings();
        remoteConfig.setEnabled(true);
        remoteConfig.setGridMode(VisualizationSettings.GridMode.OFF);
        remoteConfig.setIntensity(VisualizationSettings.Intensity.LOW);
        remoteConfig.setConsistency(9); // near-zero jitter, count=1

        int budgetBefore = emitBudget;
        emitBudget = allowed;
        drawCuboidOutline(viewer, selection, step, dust, remoteConfig);
        int spent = allowed - emitBudget;
        emitBudget = budgetBefore;
        return spent;
    }

    private boolean colorsEqual(Color a, Color b) {
        return a.getRed() == b.getRed() && a.getGreen() == b.getGreen() && a.getBlue() == b.getBlue();
    }

    private Color invert(Color c) {
        return Color.fromRGB(255 - c.getRed(), 255 - c.getGreen(), 255 - c.getBlue());
    }

    private boolean shouldUseGrid(VisualizationSettings config, long volume) {
        if (config.getGridMode() == VisualizationSettings.GridMode.ON) {
            return true;
        }
        if (config.getGridMode() == VisualizationSettings.GridMode.OFF) {
            return false;
        }
        return volume >= GRID_THRESHOLD;
    }

    private int resolveBaseStep(VisualizationSettings config, long volume, boolean grid) {
        int base;
        switch (config.getIntensity()) {
            case LOW:
                base = 2;
                break;
            case HIGH:
                base = 1;
                break;
            default:
                base = 1;
                break;
        }

        if (grid && volume >= GRID_THRESHOLD) {
            base = Math.max(base, 2);
        }
        return base;
    }

    private int resolveOutlineStep(VisualizationSettings config, Selection selection, long volume, boolean grid) {
        int base = resolveBaseStep(config, volume, grid);
        long sizeX = (long) selection.getMaxX() - selection.getMinX() + 2;
        long sizeY = (long) selection.getMaxY() - selection.getMinY() + 2;
        long sizeZ = (long) selection.getMaxZ() - selection.getMinZ() + 2;
        long outlineEmitsAtStepOne = 4L * (sizeX + sizeY + sizeZ);
        int neededStep = (int) Math.max(1L, (long) Math.ceil(outlineEmitsAtStepOne / (double) MAX_OUTLINE_EMITS));
        return Math.max(base, neededStep);
    }

    private int resolveGridStep(Selection selection, int outlineStep) {
        long sizeX = (long) selection.getMaxX() - selection.getMinX() + 2;
        long sizeY = (long) selection.getMaxY() - selection.getMinY() + 2;
        long sizeZ = (long) selection.getMaxZ() - selection.getMinZ() + 2;
        long faceEmitsAtStepOne = 2L * sizeX * sizeZ + 2L * sizeY * sizeX + 2L * sizeY * sizeZ;
        int neededStep = (int) Math.max(1L, Math.ceil(Math.sqrt(faceEmitsAtStepOne / (double) MAX_GRID_EMITS)));
        return Math.max(outlineStep * 2, neededStep);
    }

    private void drawCuboidOutline(Player player, Selection selection, int step, Particle.DustOptions dust, VisualizationSettings config) {
        double minX = selection.getMinX();
        double maxX = selection.getMaxX() + 1;
        double minY = selection.getMinY();
        double maxY = selection.getMaxY() + 1;
        double minZ = selection.getMinZ();
        double maxZ = selection.getMaxZ() + 1;

        for (double x = minX; x <= maxX; x += step) {
            emit(player, nudge(x, minX, maxX), nudge(minY, minY, maxY), nudge(minZ, minZ, maxZ), dust, config);
            emit(player, nudge(x, minX, maxX), nudge(minY, minY, maxY), nudge(maxZ, minZ, maxZ), dust, config);
            emit(player, nudge(x, minX, maxX), nudge(maxY, minY, maxY), nudge(minZ, minZ, maxZ), dust, config);
            emit(player, nudge(x, minX, maxX), nudge(maxY, minY, maxY), nudge(maxZ, minZ, maxZ), dust, config);
        }
        for (double y = minY; y <= maxY; y += step) {
            emit(player, nudge(minX, minX, maxX), nudge(y, minY, maxY), nudge(minZ, minZ, maxZ), dust, config);
            emit(player, nudge(minX, minX, maxX), nudge(y, minY, maxY), nudge(maxZ, minZ, maxZ), dust, config);
            emit(player, nudge(maxX, minX, maxX), nudge(y, minY, maxY), nudge(minZ, minZ, maxZ), dust, config);
            emit(player, nudge(maxX, minX, maxX), nudge(y, minY, maxY), nudge(maxZ, minZ, maxZ), dust, config);
        }
        for (double z = minZ; z <= maxZ; z += step) {
            emit(player, nudge(minX, minX, maxX), nudge(minY, minY, maxY), nudge(z, minZ, maxZ), dust, config);
            emit(player, nudge(maxX, minX, maxX), nudge(minY, minY, maxY), nudge(z, minZ, maxZ), dust, config);
            emit(player, nudge(minX, minX, maxX), nudge(maxY, minY, maxY), nudge(z, minZ, maxZ), dust, config);
            emit(player, nudge(maxX, minX, maxX), nudge(maxY, minY, maxY), nudge(z, minZ, maxZ), dust, config);
        }
    }

    private void drawCuboidGrid(Player player, Selection selection, int step, Particle.DustOptions dust, VisualizationSettings config) {
        double minX = selection.getMinX();
        double maxX = selection.getMaxX() + 1;
        double minY = selection.getMinY();
        double maxY = selection.getMaxY() + 1;
        double minZ = selection.getMinZ();
        double maxZ = selection.getMaxZ() + 1;

        for (double x = minX; x <= maxX; x += step) {
            for (double z = minZ; z <= maxZ; z += step) {
                emit(player, nudge(x, minX, maxX), nudge(minY, minY, maxY), nudge(z, minZ, maxZ), dust, config);
                emit(player, nudge(x, minX, maxX), nudge(maxY, minY, maxY), nudge(z, minZ, maxZ), dust, config);
            }
        }
        for (double y = minY; y <= maxY; y += step) {
            for (double x = minX; x <= maxX; x += step) {
                emit(player, nudge(x, minX, maxX), nudge(y, minY, maxY), nudge(minZ, minZ, maxZ), dust, config);
                emit(player, nudge(x, minX, maxX), nudge(y, minY, maxY), nudge(maxZ, minZ, maxZ), dust, config);
            }
            for (double z = minZ; z <= maxZ; z += step) {
                emit(player, nudge(minX, minX, maxX), nudge(y, minY, maxY), nudge(z, minZ, maxZ), dust, config);
                emit(player, nudge(maxX, minX, maxX), nudge(y, minY, maxY), nudge(z, minZ, maxZ), dust, config);
            }
        }
    }

    private void emit(Player player, double x, double y, double z, Particle.DustOptions dust, VisualizationSettings config) {
        if (emitBudget <= 0) {
            return;
        }
        emitBudget--;
        Location base = new Location(player.getWorld(), x, y, z);
        double jitter = resolveJitter(config);
        int count = resolveParticleCount(config);
        player.spawnParticle(Particle.DUST, base, count, jitter, jitter, jitter, 0, dust);
    }

    private double nudge(double value, double min, double max) {
        double epsilon = 0.02;
        if (Math.abs(value - min) < 0.0001) {
            return value + epsilon;
        }
        if (Math.abs(value - max) < 0.0001) {
            return value - epsilon;
        }
        return value;
    }

    private double resolveJitter(VisualizationSettings config) {
        int consistency = Math.max(1, Math.min(10, config.getConsistency()));
        if (consistency >= 9) {
            return 0.0;
        }
        double t = (10.0 - consistency) / 9.0;
        return 0.45 * t;
    }

    private int resolveParticleCount(VisualizationSettings config) {
        int consistency = Math.max(1, Math.min(10, config.getConsistency()));
        return 1 + (int) Math.round((consistency - 1) * (9.0 / 9.0));
    }

    private float resolveParticleSize(VisualizationSettings config) {
        switch (config.getIntensity()) {
            case LOW:
                return 0.9f;
            case HIGH:
                return 1.5f;
            default:
                return 1.2f;
        }
    }

    private Color resolveColor(Selection selection, VisualizationSettings config) {
        if (config.getColor() != null) {
            return config.getColor();
        }
        return typeColors.getOrDefault(selection.getType(), DEFAULT_SAGE);
    }

    private void drawSelectionCenter(Player player, Selection selection, VisualizationSettings config) {
        Color base = resolveColor(selection, config);
        Color inverse = Color.fromRGB(255 - base.getRed(), 255 - base.getGreen(), 255 - base.getBlue());
        Particle.DustOptions dust = new Particle.DustOptions(inverse, 1.9f);

        int minX = centerStart(selection.getMinX(), selection.getMaxX());
        int maxX = centerEnd(selection.getMinX(), selection.getMaxX());
        int minY = centerStart(selection.getMinY(), selection.getMaxY());
        int maxY = centerEnd(selection.getMinY(), selection.getMaxY());
        int minZ = centerStart(selection.getMinZ(), selection.getMaxZ());
        int maxZ = centerEnd(selection.getMinZ(), selection.getMaxZ());

        Selection marker = new Selection(
                new Location(player.getWorld(), minX, minY, minZ),
                new Location(player.getWorld(), maxX, maxY, maxZ),
                SelectionType.CUBOID
        );
        VisualizationSettings markerConfig = new VisualizationSettings();
        markerConfig.setEnabled(true);
        markerConfig.setGridMode(VisualizationSettings.GridMode.OFF);
        markerConfig.setIntensity(VisualizationSettings.Intensity.HIGH);
        markerConfig.setConsistency(10);
        drawCuboidOutline(player, marker, 1, dust, markerConfig);
    }

    private int centerStart(int min, int max) {
        int size = max - min + 1;
        return size % 2 == 0 ? min + (size / 2) - 1 : min + (size / 2);
    }

    private int centerEnd(int min, int max) {
        int size = max - min + 1;
        return size % 2 == 0 ? min + (size / 2) : min + (size / 2);
    }
}
