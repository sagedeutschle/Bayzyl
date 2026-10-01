package com.bayzyl;

import com.bayzyl.generation.CoordinateMode;
import com.bayzyl.generation.ExpressionEngine;
import com.bayzyl.generation.FeatureGenRequest;
import com.bayzyl.generation.ForestGenRequest;
import com.bayzyl.generation.BiomeGeneratorMode;
import com.bayzyl.generation.GenerateBiomeRequest;
import com.bayzyl.generation.GenerateShapeRequest;
import com.bayzyl.generation.GeneratorResult;
import com.bayzyl.generation.StructureGenRequest;
import com.bayzyl.generation.PumpkinPatchRequest;
import com.bayzyl.generation.VanillaContentRegistry;
import com.bayzyl.safety.OperationLimits;
import com.bayzyl.safety.WorkEstimate;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.TreeType;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.TileState;
import org.bukkit.block.data.BlockData;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.RayTraceResult;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ProceduralGenerationService {
    private static final int TARGET_RANGE = 64;
    private static final int TREE_SNAPSHOT_RADIUS = 8;
    private static final int PUMPKIN_SNAPSHOT_HEIGHT = 2;

    private final HistoryService historyService;
    private final SelectionManager selectionManager;
    private final ClipboardManager clipboardManager;
    private final Plugin plugin;
    private final ExpressionCompiler expressionCompiler;
    private final VanillaPlacementBackend vanillaPlacementBackend;
    private final Random random = new Random();
    private final Map<UUID, LastStructureGeneration> lastStructures;

    public ProceduralGenerationService(HistoryService historyService, SelectionManager selectionManager,
                                       ClipboardManager clipboardManager, Plugin plugin) {
        this(historyService, selectionManager, clipboardManager, plugin,
                ExpressionEngine::compile, serverPlacementBackend(plugin));
    }

    ProceduralGenerationService(HistoryService historyService, SelectionManager selectionManager,
                                ClipboardManager clipboardManager, Plugin plugin,
                                ExpressionCompiler expressionCompiler) {
        this(historyService, selectionManager, clipboardManager, plugin,
                expressionCompiler, serverPlacementBackend(plugin));
    }

    ProceduralGenerationService(HistoryService historyService, SelectionManager selectionManager,
                                ClipboardManager clipboardManager, Plugin plugin,
                                ExpressionCompiler expressionCompiler,
                                VanillaPlacementBackend vanillaPlacementBackend) {
        this(historyService, selectionManager, clipboardManager, plugin, expressionCompiler,
                vanillaPlacementBackend, new ConcurrentHashMap<>());
    }

    ProceduralGenerationService(HistoryService historyService, SelectionManager selectionManager,
                                ClipboardManager clipboardManager, Plugin plugin,
                                ExpressionCompiler expressionCompiler,
                                VanillaPlacementBackend vanillaPlacementBackend,
                                Map<UUID, LastStructureGeneration> lastStructures) {
        this.historyService = historyService;
        this.selectionManager = selectionManager;
        this.clipboardManager = clipboardManager;
        this.plugin = plugin;
        this.expressionCompiler = expressionCompiler;
        this.vanillaPlacementBackend = vanillaPlacementBackend;
        this.lastStructures = lastStructures;
    }

    private static VanillaPlacementBackend serverPlacementBackend(Plugin plugin) {
        return new VanillaPlacementBackend() {
            @Override
            public void loadChunk(World world, int chunkX, int chunkZ) {
                world.getChunkAt(chunkX, chunkZ);
            }

            @Override
            public boolean dispatch(World world, String commandPrefix, int x, int y, int z) {
                String command = String.format(Locale.ROOT, "execute in %s run %s %d %d %d",
                        world.getKey(), commandPrefix, x, y, z);
                ConsoleCommandSender console = plugin.getServer().getConsoleSender();
                return plugin.getServer().dispatchCommand(console, command);
            }
        };
    }

    public boolean requiresConfirm(long workUnits, boolean confirm) {
        return !OperationLimits.checkMaterialized(workUnits).permits(confirm);
    }

    public GeneratorResult generateShape(Player player, GenerateShapeRequest request) {
        Selection selection = requireSelection(player);
        if (selection == null) {
            return GeneratorResult.failed("Selection is incomplete.");
        }

        SelectionBounds bounds = SelectionBounds.from(selection);
        WorkEstimate estimate = estimateSelection(bounds);
        if (estimate.hardRejected()) {
            return GeneratorResult.failed(estimate.reason());
        }

        ExpressionEngine.Program program;
        try {
            program = expressionCompiler.compile(request.expression());
        } catch (IllegalArgumentException ex) {
            return GeneratorResult.failed("Invalid expression: " + ex.getMessage());
        }

        Set<Long> included = evaluateSelection(bounds, request.coordinateMode(), resolvePlacement(player), program);
        List<BlockChange> changes = applyShape(player, bounds.world(), request.material(), included, request.hollow());
        historyService.record(player.getUniqueId(), changes);
        return GeneratorResult.success(changes.size(),
                "Generated " + describeCoordinateMode(request.coordinateMode()) + " shape over " + bounds.volume() + " blocks.");
    }

    public GeneratorResult generateBiome(Player player, GenerateBiomeRequest request) {
        Selection selection = requireSelection(player);
        if (selection == null) {
            return GeneratorResult.failed("Selection is incomplete.");
        }
        SelectionBounds bounds = SelectionBounds.from(selection);
        WorkEstimate selectionEstimate = estimateSelection(bounds);
        if (selectionEstimate.hardRejected()) {
            return GeneratorResult.failed(selectionEstimate.reason());
        }
        WorkEstimate chunkEstimate = OperationLimits.estimateBiomeChunks(
                bounds.minX(), bounds.maxX(), bounds.minZ(), bounds.maxZ());
        if (chunkEstimate.hardRejected()) {
            return GeneratorResult.failed(chunkEstimate.reason());
        }
        Set<Long> included;
        if (request.mode() == BiomeGeneratorMode.EXPRESSION) {
            ExpressionEngine.Program program;
            try {
                program = expressionCompiler.compile(request.expression());
            } catch (IllegalArgumentException ex) {
                return GeneratorResult.failed("Invalid expression: " + ex.getMessage());
            }
            included = evaluateSelection(bounds, request.coordinateMode(), resolvePlacement(player), program);
        } else {
            included = generateBiomeTargets(bounds, request);
        }
        if (included.isEmpty()) {
            plugin.getLogger().info("genbiome matched 0 positions for " + player.getName()
                    + " biome=" + request.biome().name().toLowerCase(Locale.ROOT)
                    + " mode=" + request.mode().name().toLowerCase(Locale.ROOT));
            return GeneratorResult.failed("Biome generator did not match any blocks in the current selection.");
        }
        if (request.preview()) {
            long columnCount = included.stream()
                    .map(encoded -> {
                        int[] pos = decodePos(encoded);
                        return encodeColumn(pos[0], pos[2]);
                    })
                    .distinct()
                    .count();
            return GeneratorResult.preview((int) columnCount,
                    "Preview: " + describeBiomeRequest(request, bounds, included.size(), columnCount));
        }
        BiomeApplyResult result = applyBiome(bounds.world(), request.biome(), included, request.hollow());
        refreshBiomeChunks(bounds.world(), result.blockChanges(), result.columnChanges());
        historyService.record(player.getUniqueId(), List.of(), List.of(), result.blockChanges(), result.columnChanges(), null, null);
        plugin.getLogger().info("genbiome backend=bayzyl player=" + player.getName()
                + " biome=" + request.biome().name().toLowerCase(Locale.ROOT)
                + " mode=" + request.mode()
                + " matched=" + included.size()
                + " changed=" + result.blockChanges().size()
                + " columns=" + result.columnChanges().size());
        if (result.blockChanges().isEmpty() && result.columnChanges().isEmpty()) {
            return GeneratorResult.success(0,
                    "Biome expression matched " + included.size() + " positions, but they were already "
                            + request.biome().name().toLowerCase(Locale.ROOT) + ".");
        }
        return GeneratorResult.success(result.blockChanges().size(),
                "Generated biome shape using " + request.biome().name().toLowerCase(Locale.ROOT)
                        + " across " + result.blockChanges().size() + " changed positions (" + included.size()
                        + " matched, " + result.columnChanges().size() + " columns updated).");
    }

    private Set<Long> generateBiomeTargets(SelectionBounds bounds, GenerateBiomeRequest request) {
        return switch (request.mode()) {
            case FULL -> fullSelection(bounds);
            case SPHERE -> sphereTargets(bounds, request, false, false);
            case DOME -> sphereTargets(bounds, request, true, false);
            case BOWL -> sphereTargets(bounds, request, false, true);
            case CYLINDER -> cylinderTargets(bounds, request);
            case PYRAMID -> pyramidTargets(bounds, request);
            case EXPRESSION -> Set.of();
        };
    }

    public GeneratorResult generateForest(Player player, ForestGenRequest request) {
        WorkEstimate estimate = OperationLimits.estimateForest(request.size(), request.density());
        if (estimate.hardRejected()) {
            return GeneratorResult.failed(estimate.reason());
        }
        Location anchor = resolveAnchor(player, request.anchorMode());
        if (anchor == null || anchor.getWorld() == null) {
            return GeneratorResult.failed("No valid anchor found.");
        }
        World world = anchor.getWorld();
        ChangeCollector collector = new ChangeCollector();
        java.util.Set<Long> placedTrees = new java.util.HashSet<>();
        int attempts = 0;
        int trees = 0;
        double chance = request.density() / 100.0;
        int minDistance = TREE_SNAPSHOT_RADIUS + 2;
        for (int x = anchor.getBlockX() - request.size(); x <= anchor.getBlockX() + request.size(); x++) {
            for (int z = anchor.getBlockZ() - request.size(); z <= anchor.getBlockZ() + request.size(); z++) {
                attempts++;
                if (random.nextDouble() > chance) {
                    continue;
                }
                if (hasNearbyTree(placedTrees, x, z, minDistance)) {
                    continue;
                }
                Location plant = findPlantSurface(world, x, z);
                if (plant == null) {
                    continue;
                }
                captureCube(world, x, plant.getBlockY(), z, TREE_SNAPSHOT_RADIUS, TREE_SNAPSHOT_RADIUS, collector);
                if (world.generateTree(plant, request.treeType())) {
                    trees++;
                    placedTrees.add(encodeColumn(x, z));
                    captureCube(world, x, plant.getBlockY(), z, TREE_SNAPSHOT_RADIUS, TREE_SNAPSHOT_RADIUS, collector);
                }
            }
        }
        List<BlockChange> changes = collector.toBlockChanges(world);
        historyService.record(player.getUniqueId(), changes);
        return GeneratorResult.success(changes.size(), "Generated " + trees + " tree(s) over " + attempts + " positions.");
    }

    private boolean hasNearbyTree(java.util.Set<Long> trees, int x, int z, int distance) {
        for (long encoded : trees) {
            int[] pos = decodeColumn(encoded);
            int dx = Math.abs(pos[0] - x);
            int dz = Math.abs(pos[1] - z);
            if (dx < distance && dz < distance) {
                return true;
            }
        }
        return false;
    }

    public GeneratorResult generatePumpkins(Player player, PumpkinPatchRequest request) {
        WorkEstimate estimate = OperationLimits.estimatePumpkins(request.size());
        if (estimate.hardRejected()) {
            return GeneratorResult.failed(estimate.reason());
        }
        Location anchor = resolveAnchor(player, request.anchorMode());
        if (anchor == null || anchor.getWorld() == null) {
            return GeneratorResult.failed("No valid anchor found.");
        }
        World world = anchor.getWorld();
        ChangeCollector collector = new ChangeCollector();
        int patches = 0;
        for (int x = anchor.getBlockX() - request.size(); x <= anchor.getBlockX() + request.size(); x++) {
            for (int z = anchor.getBlockZ() - request.size(); z <= anchor.getBlockZ() + request.size(); z++) {
                if (random.nextDouble() > 0.4) {
                    continue;
                }
                Location surface = findPlantSurface(world, x, z);
                if (surface == null) {
                    continue;
                }
                Block ground = surface.getBlock().getRelative(BlockFace.DOWN);
                if (!isPumpkinGround(ground.getType())) {
                    continue;
                }
                captureCube(world, x, surface.getBlockY(), z, 1, PUMPKIN_SNAPSHOT_HEIGHT, collector);
                Block base = surface.getBlock();
                if (!base.isEmpty()) {
                    continue;
                }
                base.setType(Material.PUMPKIN, false);
                patches++;
                if (random.nextDouble() < 0.35 && base.getRelative(BlockFace.NORTH).isEmpty()) {
                    base.getRelative(BlockFace.NORTH).setType(Material.CARVED_PUMPKIN, false);
                }
                captureCube(world, x, surface.getBlockY(), z, 1, PUMPKIN_SNAPSHOT_HEIGHT, collector);
            }
        }
        List<BlockChange> changes = collector.toBlockChanges(world);
        historyService.record(player.getUniqueId(), changes);
        return GeneratorResult.success(changes.size(), "Generated " + patches + " pumpkin patch blocks.");
    }

    public GeneratorResult generateFeature(Player player, FeatureGenRequest request) {
        VanillaContentRegistry.Entry meta = VanillaContentRegistry.featureMeta(request.featureId());
        return placeVanilla(player, request.anchorMode(), meta,
                "feature", request.featureId(),
                String.format(Locale.ROOT, "place feature %s", request.featureId()),
                false);
    }

    public GeneratorResult generateStructure(Player player, StructureGenRequest request) {
        VanillaContentRegistry.Entry meta = VanillaContentRegistry.structureMeta(request.structureId());
        return placeVanilla(player, request.anchorMode(), meta,
                "structure", request.structureId(),
                String.format(Locale.ROOT, "place structure %s", request.structureId()),
                true);
    }

    public GeneratorResult regenerateStructure(Player player) {
        LastStructureGeneration previous = lastStructures.get(player.getUniqueId());
        if (previous == null) {
            return GeneratorResult.failed("No generated structure to reroll. Use /genstructure first.");
        }

        VanillaContentRegistry.Entry meta = VanillaContentRegistry.structureMeta(previous.structureId());
        WorkEstimate snapshotEstimate = OperationLimits.estimateStructure(
                meta.footprintRadius(), meta.footprintHeight());
        if (snapshotEstimate.hardRejected()) {
            return GeneratorResult.failed(snapshotEstimate.reason());
        }
        WorkEstimate totalEstimate = OperationLimits.checkedAdd(
                snapshotEstimate.workUnits(), previous.changes().size());
        if (totalEstimate.hardRejected()) {
            return GeneratorResult.failed(totalEstimate.reason());
        }
        WorkEstimate chunkEstimate = OperationLimits.estimateStructureChunks(meta.footprintRadius());
        if (chunkEstimate.hardRejected()) {
            return GeneratorResult.failed(chunkEstimate.reason());
        }

        Location anchor = previous.anchor().clone();
        if (anchor.getWorld() == null) {
            lastStructures.remove(player.getUniqueId());
            return GeneratorResult.failed("Saved structure anchor world is unavailable.");
        }

        World world = anchor.getWorld();
        int x = anchor.getBlockX();
        int y = anchor.getBlockY();
        int z = anchor.getBlockZ();
        int radius = meta.footprintRadius();
        int height = meta.footprintHeight();

        loadFootprintChunks(world, x, z, radius);

        ChangeCollector collector = new ChangeCollector();
        captureCube(world, x, y, z, radius, height, collector);
        captureChangeLocations(world, previous.changes(), collector);
        Map<Long, BlockData> baselineOverrides = baselineOverrides(previous.changes());

        restoreChangesToBefore(previous.changes());
        dispatchVanillaPlace(world, String.format(Locale.ROOT, "place structure %s", previous.structureId()), x, y, z);

        List<BlockChange> historyChanges = collector.toBlockChanges(world);
        List<BlockChange> placementChanges = collector.toBlockChanges(world, baselineOverrides);
        if (placementChanges.isEmpty()) {
            restoreChangesToBefore(historyChanges);
            return GeneratorResult.failed(String.format(Locale.ROOT,
                    "No structure blocks placed while rerolling %s at %d,%d,%d.",
                    previous.structureId(), x, y, z));
        }

        if (!historyChanges.isEmpty()) {
            historyService.record(player.getUniqueId(), historyChanges);
        }

        Clipboard clipboard = buildClipboardFromChanges(player, world, placementChanges);
        if (clipboard != null) {
            clipboardManager.set(player.getUniqueId(), clipboard);
        }
        rememberStructureGeneration(player.getUniqueId(), previous.structureId(), anchor, placementChanges);

        return GeneratorResult.success(historyChanges.size(),
                String.format(Locale.ROOT, "Regenerated structure %s at %d,%d,%d (%d placement blocks, captured to clipboard).",
                        previous.structureId(), x, y, z, placementChanges.size()));
    }

    private GeneratorResult placeVanilla(Player player, ShapeAnchorMode anchorMode, VanillaContentRegistry.Entry meta,
                                         String kind, String id, String placeCommandPrefix, boolean rememberStructure) {
        WorkEstimate snapshotEstimate = OperationLimits.estimateStructure(
                meta.footprintRadius(), meta.footprintHeight());
        if (snapshotEstimate.hardRejected()) {
            return GeneratorResult.failed(snapshotEstimate.reason());
        }
        WorkEstimate chunkEstimate = OperationLimits.estimateStructureChunks(meta.footprintRadius());
        if (chunkEstimate.hardRejected()) {
            return GeneratorResult.failed(chunkEstimate.reason());
        }
        Location anchor = resolveAnchor(player, anchorMode);
        if (anchor == null || anchor.getWorld() == null) {
            return GeneratorResult.failed("No valid anchor found.");
        }
        World world = anchor.getWorld();
        int x = anchor.getBlockX();
        int y = anchor.getBlockY();
        int z = anchor.getBlockZ();

        int radius = meta.footprintRadius();
        int height = meta.footprintHeight();

        loadFootprintChunks(world, x, z, radius);

        // Snapshot before so we can diff the placement and feed the result into history.
        ChangeCollector collector = new ChangeCollector();
        captureCube(world, x, y, z, radius, height, collector);

        dispatchVanillaPlace(world, placeCommandPrefix, x, y, z);

        List<BlockChange> changes = collector.toBlockChanges(world);
        historyService.record(player.getUniqueId(), changes);

        if (changes.isEmpty()) {
            return GeneratorResult.failed(String.format(Locale.ROOT,
                    "No %s blocks placed for %s at %d,%d,%d (invalid id or out-of-bounds?).",
                    kind, id, x, y, z));
        }

        // Capture the placed result into the player's clipboard so /paste can reposition it.
        Clipboard clipboard = buildClipboardFromChanges(player, world, changes);
        if (clipboard != null) {
            clipboardManager.set(player.getUniqueId(), clipboard);
        }
        if (rememberStructure) {
            rememberStructureGeneration(player.getUniqueId(), id, new Location(world, x, y, z), changes);
        }

        return GeneratorResult.success(changes.size(),
                String.format(Locale.ROOT, "Generated %s %s at %d,%d,%d (%d blocks, captured to clipboard).",
                        kind, id, x, y, z, changes.size()));
    }

    private void loadFootprintChunks(World world, int x, int z, int radius) {
        // Force-load every chunk in the vanilla placement footprint so `/place` doesn't
        // refuse with "That position is not loaded".
        int chunkR = (radius >> 4) + 1;
        int anchorCx = x >> 4;
        int anchorCz = z >> 4;
        for (int cdx = -chunkR; cdx <= chunkR; cdx++) {
            for (int cdz = -chunkR; cdz <= chunkR; cdz++) {
                try {
                    vanillaPlacementBackend.loadChunk(world, anchorCx + cdx, anchorCz + cdz);
                } catch (Exception ignored) {
                    // Chunk load failure is logged once below if /place ultimately fails.
                }
            }
        }
    }

    private void dispatchVanillaPlace(World world, String placeCommandPrefix, int x, int y, int z) {
        vanillaPlacementBackend.dispatch(world, placeCommandPrefix, x, y, z);
    }

    private void rememberStructureGeneration(UUID playerId, String structureId, Location anchor, List<BlockChange> changes) {
        lastStructures.put(playerId, new LastStructureGeneration(structureId, anchor.clone(), copyBlockChanges(changes)));
    }

    private Map<Long, BlockData> baselineOverrides(List<BlockChange> changes) {
        Map<Long, BlockData> overrides = new LinkedHashMap<>();
        for (BlockChange change : changes) {
            Location loc = change.getLocation();
            overrides.put(ChangeCollector.encode(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ()), change.getBefore().clone());
        }
        return overrides;
    }

    private int restoreChangesToBefore(List<BlockChange> changes) {
        int restored = 0;
        for (int index = changes.size() - 1; index >= 0; index--) {
            BlockChange change = changes.get(index);
            Block block = change.getLocation().getBlock();
            BlockData before = change.getBefore();
            if (!block.getBlockData().matches(before)) {
                block.setBlockData(before.clone(), false);
                restored++;
            }
        }
        return restored;
    }

    private void captureChangeLocations(World world, List<BlockChange> changes, ChangeCollector collector) {
        for (BlockChange change : changes) {
            Location location = change.getLocation();
            if (location.getWorld() == null || !location.getWorld().equals(world)) {
                continue;
            }
            collector.captureBefore(location.getBlock());
        }
    }

    private List<BlockChange> copyBlockChanges(List<BlockChange> changes) {
        List<BlockChange> copied = new ArrayList<>(changes.size());
        for (BlockChange change : changes) {
            copied.add(new BlockChange(change.getLocation().clone(), change.getBefore().clone(), change.getAfter().clone()));
        }
        return List.copyOf(copied);
    }

    private Clipboard buildClipboardFromChanges(Player player, World world, List<BlockChange> changes) {
        if (changes.isEmpty()) {
            return null;
        }
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        Set<Long> changedKeys = new java.util.HashSet<>();
        for (BlockChange change : changes) {
            Location loc = change.getLocation();
            int cx = loc.getBlockX();
            int cy = loc.getBlockY();
            int cz = loc.getBlockZ();
            if (cx < minX) minX = cx;
            if (cy < minY) minY = cy;
            if (cz < minZ) minZ = cz;
            if (cx > maxX) maxX = cx;
            if (cy > maxY) maxY = cy;
            if (cz > maxZ) maxZ = cz;
            changedKeys.add(encodeClipboardPos(cx, cy, cz));
        }

        int sizeX = maxX - minX + 1;
        int sizeY = maxY - minY + 1;
        int sizeZ = maxZ - minZ + 1;
        BlockData air = Material.AIR.createBlockData();
        BlockData[] data = new BlockData[sizeX * sizeY * sizeZ];
        BlockState[] states = new BlockState[sizeX * sizeY * sizeZ];

        int idx = 0;
        for (int dy = 0; dy < sizeY; dy++) {
            for (int dz = 0; dz < sizeZ; dz++) {
                for (int dx = 0; dx < sizeX; dx++) {
                    long key = encodeClipboardPos(minX + dx, minY + dy, minZ + dz);
                    if (changedKeys.contains(key)) {
                        Block block = world.getBlockAt(minX + dx, minY + dy, minZ + dz);
                        data[idx] = block.getBlockData().clone();
                        BlockState state = block.getState();
                        states[idx] = state instanceof TileState ? state.copy() : null;
                    } else {
                        data[idx] = air;
                        states[idx] = null;
                    }
                    idx++;
                }
            }
        }

        Location origin = player.getLocation().getBlock().getLocation();
        int minOffsetX = minX - origin.getBlockX();
        int minOffsetY = minY - origin.getBlockY();
        int minOffsetZ = minZ - origin.getBlockZ();
        return new Clipboard(sizeX, sizeY, sizeZ, data, states,
                java.util.Collections.emptyList(), origin, minOffsetX, minOffsetY, minOffsetZ);
    }

    private static long encodeClipboardPos(int x, int y, int z) {
        return WorldPosCodec.pack(x, y, z);
    }

    private Selection requireSelection(Player player) {
        Selection selection = selectionManager.get(player.getUniqueId());
        return selection != null && selection.isComplete() ? selection : null;
    }

    private WorkEstimate estimateSelection(SelectionBounds bounds) {
        return OperationLimits.estimateSelection(
                bounds.minX(), bounds.maxX(),
                bounds.minY(), bounds.maxY(),
                bounds.minZ(), bounds.maxZ());
    }

    private Set<Long> evaluateSelection(SelectionBounds bounds, CoordinateMode mode, Location placement, ExpressionEngine.Program program) {
        Set<Long> included = new LinkedHashSet<>();
        int centerX = (bounds.minX + bounds.maxX) / 2;
        int centerY = (bounds.minY + bounds.maxY) / 2;
        int centerZ = (bounds.minZ + bounds.maxZ) / 2;
        double halfX = Math.max(1.0, (bounds.maxX - bounds.minX + 1) / 2.0);
        double halfY = Math.max(1.0, (bounds.maxY - bounds.minY + 1) / 2.0);
        double halfZ = Math.max(1.0, (bounds.maxZ - bounds.minZ + 1) / 2.0);

        for (int x = bounds.minX; x <= bounds.maxX; x++) {
            for (int y = bounds.minY; y <= bounds.maxY; y++) {
                for (int z = bounds.minZ; z <= bounds.maxZ; z++) {
                    Map<String, Double> vars = switch (mode) {
                        case RAW -> Map.of("x", (double) x, "y", (double) y, "z", (double) z);
                        case CENTER -> Map.of("x", (double) (x - centerX), "y", (double) (y - centerY), "z", (double) (z - centerZ));
                        case ORIGIN -> Map.of("x", (double) (x - placement.getBlockX()), "y", (double) (y - placement.getBlockY()), "z", (double) (z - placement.getBlockZ()));
                        case NORMALIZED -> Map.of(
                                "x", ((x + 0.5) - (bounds.minX + halfX)) / halfX,
                                "y", ((y + 0.5) - (bounds.minY + halfY)) / halfY,
                                "z", ((z + 0.5) - (bounds.minZ + halfZ)) / halfZ
                        );
                    };
                    double result = program.evaluate(vars);
                    if (Double.isFinite(result) && result > 0.0) {
                        included.add(encodePos(x, y, z));
                    }
                }
            }
        }
        return included;
    }

    private List<BlockChange> applyShape(Player player, World world, Material material, Set<Long> included, boolean hollow) {
        List<BlockChange> changes = new ArrayList<>();
        BlockData placedData = material.createBlockData();
        for (long encoded : included) {
            if (hollow && !isBoundary(encoded, included)) {
                continue;
            }
            int[] pos = decodePos(encoded);
            Block block = world.getBlockAt(pos[0], pos[1], pos[2]);
            BlockData before = block.getBlockData().clone();
            if (before.matches(placedData)) {
                continue;
            }
            block.setBlockData(placedData, false);
            changes.add(new BlockChange(block.getLocation(), before, block.getBlockData().clone()));
        }
        return changes;
    }

    private BiomeApplyResult applyBiome(World world, Biome biome, Set<Long> included, boolean hollow) {
        Map<Long, Biome> beforeBlocks = snapshotBlockBiomes(world, included, hollow);
        Map<Long, Biome> beforeColumns = snapshotColumnBiomes(world, included, hollow);
        int appliedCommands = BiomeCommandUtil.applySelection(world, included, hollow, biome);
        plugin.getLogger().info("genbiome bayzyl rawCommands=" + appliedCommands
                + " biome=" + biome.name().toLowerCase(Locale.ROOT));
        List<BiomeChange> blockChanges = new ArrayList<>();
        for (Map.Entry<Long, Biome> entry : beforeBlocks.entrySet()) {
            if (entry.getValue() == biome) {
                continue;
            }
            int[] pos = decodePos(entry.getKey());
            blockChanges.add(new BiomeChange(new Location(world, pos[0], pos[1], pos[2]), entry.getValue(), biome));
        }
        List<BiomeColumnChange> columnChanges = new ArrayList<>();
        for (Map.Entry<Long, Biome> entry : beforeColumns.entrySet()) {
            if (entry.getValue() == biome) {
                continue;
            }
            int[] column = decodeColumn(entry.getKey());
            columnChanges.add(new BiomeColumnChange(world, column[0], column[1], entry.getValue(), biome));
        }
        return new BiomeApplyResult(blockChanges, columnChanges);
    }

    private void refreshBiomeChunks(World world, List<BiomeChange> changes, List<BiomeColumnChange> columnChanges) {
        Set<Long> chunks = new LinkedHashSet<>();
        for (BiomeChange change : changes) {
            int chunkX = change.getLocation().getBlockX() >> 4;
            int chunkZ = change.getLocation().getBlockZ() >> 4;
            long key = (((long) chunkX) << 32) ^ (chunkZ & 0xFFFF_FFFFL);
            chunks.add(key);
        }
        for (BiomeColumnChange change : columnChanges) {
            int chunkX = change.getX() >> 4;
            int chunkZ = change.getZ() >> 4;
            long key = (((long) chunkX) << 32) ^ (chunkZ & 0xFFFF_FFFFL);
            chunks.add(key);
        }
        for (long chunkKey : chunks) {
            int chunkX = (int) (chunkKey >> 32);
            int chunkZ = (int) chunkKey;
            world.refreshChunk(chunkX, chunkZ);
        }
    }

    private boolean isBoundary(long encoded, Set<Long> included) {
        WorldPosCodec.Position pos = WorldPosCodec.unpack(encoded);
        return !WorldPosCodec.contains(included, pos.x() + 1, pos.y(), pos.z())
                || !WorldPosCodec.contains(included, pos.x() - 1, pos.y(), pos.z())
                || !WorldPosCodec.contains(included, pos.x(), pos.y() + 1, pos.z())
                || !WorldPosCodec.contains(included, pos.x(), pos.y() - 1, pos.z())
                || !WorldPosCodec.contains(included, pos.x(), pos.y(), pos.z() + 1)
                || !WorldPosCodec.contains(included, pos.x(), pos.y(), pos.z() - 1);
    }

    private Set<Long> fullSelection(SelectionBounds bounds) {
        Set<Long> included = new LinkedHashSet<>();
        for (int x = bounds.minX; x <= bounds.maxX; x++) {
            for (int y = bounds.minY; y <= bounds.maxY; y++) {
                for (int z = bounds.minZ; z <= bounds.maxZ; z++) {
                    included.add(encodePos(x, y, z));
                }
            }
        }
        return included;
    }

    private Set<Long> sphereTargets(SelectionBounds bounds, GenerateBiomeRequest request, boolean upperHalfOnly, boolean lowerHalfOnly) {
        Set<Long> included = new LinkedHashSet<>();
        double centerX = (bounds.minX + bounds.maxX) / 2.0;
        double centerY = (bounds.minY + bounds.maxY) / 2.0;
        double centerZ = (bounds.minZ + bounds.maxZ) / 2.0;
        double radiusX = resolveRadiusX(bounds, request);
        double radiusY = resolveRadiusY(bounds, request);
        double radiusZ = resolveRadiusZ(bounds, request);
        for (int x = bounds.minX; x <= bounds.maxX; x++) {
            for (int y = bounds.minY; y <= bounds.maxY; y++) {
                double dy = (y - centerY) / radiusY;
                if (upperHalfOnly && dy < 0.0) {
                    continue;
                }
                if (lowerHalfOnly && dy > 0.0) {
                    continue;
                }
                for (int z = bounds.minZ; z <= bounds.maxZ; z++) {
                    double dx = (x - centerX) / radiusX;
                    double dz = (z - centerZ) / radiusZ;
                    if (dx * dx + dy * dy + dz * dz <= 1.0) {
                        included.add(encodePos(x, y, z));
                    }
                }
            }
        }
        return included;
    }

    private Set<Long> cylinderTargets(SelectionBounds bounds, GenerateBiomeRequest request) {
        Set<Long> included = new LinkedHashSet<>();
        double centerX = (bounds.minX + bounds.maxX) / 2.0;
        double centerZ = (bounds.minZ + bounds.maxZ) / 2.0;
        double radiusX = resolveRadiusX(bounds, request);
        double radiusZ = resolveRadiusZ(bounds, request);
        int height = request.height() != null ? Math.min(bounds.maxY - bounds.minY + 1, request.height()) : bounds.maxY - bounds.minY + 1;
        int startY = bounds.minY + Math.max(0, ((bounds.maxY - bounds.minY + 1) - height) / 2);
        int endY = Math.min(bounds.maxY, startY + height - 1);
        for (int x = bounds.minX; x <= bounds.maxX; x++) {
            double dx = (x - centerX) / radiusX;
            for (int z = bounds.minZ; z <= bounds.maxZ; z++) {
                double dz = (z - centerZ) / radiusZ;
                if (dx * dx + dz * dz > 1.0) {
                    continue;
                }
                for (int y = startY; y <= endY; y++) {
                    included.add(encodePos(x, y, z));
                }
            }
        }
        return included;
    }

    private Set<Long> pyramidTargets(SelectionBounds bounds, GenerateBiomeRequest request) {
        Set<Long> included = new LinkedHashSet<>();
        int availableHeight = bounds.maxY - bounds.minY + 1;
        int maxHalfSpan = Math.max(1, Math.min((bounds.maxX - bounds.minX + 1) / 2, (bounds.maxZ - bounds.minZ + 1) / 2));
        int size = request.size() != null ? Math.min(request.size(), Math.min(availableHeight, maxHalfSpan)) : Math.min(availableHeight, maxHalfSpan);
        int centerX = (bounds.minX + bounds.maxX) / 2;
        int centerZ = (bounds.minZ + bounds.maxZ) / 2;
        for (int layer = 0; layer < size; layer++) {
            int y = bounds.minY + layer;
            int span = size - 1 - layer;
            for (int x = centerX - span; x <= centerX + span; x++) {
                if (x < bounds.minX || x > bounds.maxX) {
                    continue;
                }
                for (int z = centerZ - span; z <= centerZ + span; z++) {
                    if (z < bounds.minZ || z > bounds.maxZ) {
                        continue;
                    }
                    included.add(encodePos(x, y, z));
                }
            }
        }
        return included;
    }

    private double resolveRadiusX(SelectionBounds bounds, GenerateBiomeRequest request) {
        if (request.radiusX() != null) {
            return request.radiusX();
        }
        if (request.radius() != null) {
            return request.radius();
        }
        return Math.max(1.0, (bounds.maxX - bounds.minX + 1) / 2.0);
    }

    private double resolveRadiusY(SelectionBounds bounds, GenerateBiomeRequest request) {
        if (request.radiusY() != null) {
            return request.radiusY();
        }
        if (request.radius() != null) {
            return request.radius();
        }
        if (request.height() != null) {
            return Math.max(1.0, request.height() / 2.0);
        }
        return Math.max(1.0, (bounds.maxY - bounds.minY + 1) / 2.0);
    }

    private double resolveRadiusZ(SelectionBounds bounds, GenerateBiomeRequest request) {
        if (request.radiusZ() != null) {
            return request.radiusZ();
        }
        if (request.radius() != null) {
            return request.radius();
        }
        return Math.max(1.0, (bounds.maxZ - bounds.minZ + 1) / 2.0);
    }

    private String describeBiomeRequest(GenerateBiomeRequest request, SelectionBounds bounds, int matchedPositions, long matchedColumns) {
        String target = request.biome().name().toLowerCase(Locale.ROOT);
        return switch (request.mode()) {
            case FULL -> "fill selection with " + target + " across " + matchedPositions + " positions (" + matchedColumns + " columns).";
            case EXPRESSION -> "apply expression biome generation using " + target + " in " + describeCoordinateMode(request.coordinateMode())
                    + " mode across " + matchedPositions + " matched positions (" + matchedColumns + " columns).";
            case SPHERE -> "apply sphere biome generation using " + target + " with radii "
                    + trimNumber(resolveRadiusX(bounds, request)) + "/"
                    + trimNumber(resolveRadiusY(bounds, request)) + "/"
                    + trimNumber(resolveRadiusZ(bounds, request)) + " across " + matchedPositions + " positions.";
            case CYLINDER -> "apply cylinder biome generation using " + target + " with radii "
                    + trimNumber(resolveRadiusX(bounds, request)) + "/"
                    + trimNumber(resolveRadiusZ(bounds, request)) + " and height "
                    + (request.height() != null ? request.height() : bounds.maxY - bounds.minY + 1)
                    + " across " + matchedPositions + " positions.";
            case PYRAMID -> "apply pyramid biome generation using " + target + " with size "
                    + (request.size() != null ? request.size() : Math.min(bounds.maxY - bounds.minY + 1,
                    Math.max(1, Math.min((bounds.maxX - bounds.minX + 1) / 2, (bounds.maxZ - bounds.minZ + 1) / 2))))
                    + " across " + matchedPositions + " positions.";
            case DOME -> "apply dome biome generation using " + target + " with radii "
                    + trimNumber(resolveRadiusX(bounds, request)) + "/"
                    + trimNumber(resolveRadiusY(bounds, request)) + "/"
                    + trimNumber(resolveRadiusZ(bounds, request)) + " across " + matchedPositions + " positions.";
            case BOWL -> "apply bowl biome generation using " + target + " with radii "
                    + trimNumber(resolveRadiusX(bounds, request)) + "/"
                    + trimNumber(resolveRadiusY(bounds, request)) + "/"
                    + trimNumber(resolveRadiusZ(bounds, request)) + " across " + matchedPositions + " positions.";
        };
    }

    private String trimNumber(double value) {
        if (Math.rint(value) == value) {
            return Integer.toString((int) value);
        }
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private Location resolvePlacement(Player player) {
        Location target = resolveTarget(player);
        return target != null ? target : player.getLocation().getBlock().getLocation();
    }

    private Location resolveAnchor(Player player, ShapeAnchorMode mode) {
        return switch (mode) {
            case PLAYER -> player.getLocation().getBlock().getLocation();
            case EYES -> player.getEyeLocation().getBlock().getLocation();
            case TARGET -> resolveTarget(player);
            case SELECTION_CENTER -> resolveSelectionCenter(player);
        };
    }

    private Location resolveTarget(Player player) {
        RayTraceResult result = player.rayTraceBlocks(TARGET_RANGE);
        if (result == null || result.getHitBlock() == null) {
            return null;
        }
        BlockFace face = result.getHitBlockFace();
        return face == null
                ? result.getHitBlock().getLocation()
                : result.getHitBlock().getRelative(face).getLocation();
    }

    private Location resolveSelectionCenter(Player player) {
        Selection selection = selectionManager.get(player.getUniqueId());
        if (selection == null || !selection.isComplete()) {
            return null;
        }
        World world = selection.getPos1().getWorld();
        if (world == null) {
            return null;
        }
        return new Location(world,
                (selection.getMinX() + selection.getMaxX()) / 2,
                (selection.getMinY() + selection.getMaxY()) / 2,
                (selection.getMinZ() + selection.getMaxZ()) / 2);
    }

    private Location findPlantSurface(World world, int x, int z) {
        int y = world.getHighestBlockYAt(x, z);
        if (y <= world.getMinHeight()) {
            return null;
        }
        Block surface = world.getBlockAt(x, y, z);
        if (!surface.isEmpty()) {
            y++;
        }
        if (y > world.getMaxHeight()) {
            return null;
        }
        Block base = world.getBlockAt(x, y - 1, z);
        if (!base.getType().isSolid()) {
            return null;
        }
        Block plant = world.getBlockAt(x, y, z);
        if (!plant.isEmpty()) {
            return null;
        }
        return plant.getLocation();
    }

    private boolean isPumpkinGround(Material material) {
        return material == Material.GRASS_BLOCK
                || material == Material.DIRT
                || material == Material.COARSE_DIRT
                || material == Material.PODZOL
                || material == Material.ROOTED_DIRT
                || material == Material.FARMLAND;
    }

    private void captureCube(World world, int x, int y, int z, int horizontalRadius, int verticalRadius, ChangeCollector collector) {
        for (int dx = -horizontalRadius; dx <= horizontalRadius; dx++) {
            for (int dy = -1; dy <= verticalRadius; dy++) {
                for (int dz = -horizontalRadius; dz <= horizontalRadius; dz++) {
                    Block block = world.getBlockAt(x + dx, y + dy, z + dz);
                    collector.captureBefore(block);
                }
            }
        }
    }

    private long encodePos(int x, int y, int z) {
        return WorldPosCodec.pack(x, y, z);
    }

    private int[] decodePos(long value) {
        WorldPosCodec.Position position = WorldPosCodec.unpack(value);
        return new int[]{position.x(), position.y(), position.z()};
    }

    private String describeCoordinateMode(CoordinateMode mode) {
        return switch (mode) {
            case RAW -> "raw-coordinate";
            case CENTER -> "center-origin";
            case ORIGIN -> "placement-origin";
            case NORMALIZED -> "normalized";
        };
    }

    private long encodeColumn(int x, int z) {
        return (((long) x) << 32) ^ (z & 0xFFFF_FFFFL);
    }

    private int[] decodeColumn(long value) {
        return new int[]{(int) (value >> 32), (int) value};
    }

    private Map<Long, Biome> snapshotBlockBiomes(SelectionBounds bounds) {
        Map<Long, Biome> snapshot = new LinkedHashMap<>();
        for (int x = bounds.minX; x <= bounds.maxX; x++) {
            for (int y = bounds.minY; y <= bounds.maxY; y++) {
                for (int z = bounds.minZ; z <= bounds.maxZ; z++) {
                    snapshot.put(encodePos(x, y, z), bounds.world().getBiome(x, y, z));
                }
            }
        }
        return snapshot;
    }

    private Map<Long, Biome> snapshotBlockBiomes(World world, Set<Long> included, boolean hollow) {
        Map<Long, Biome> snapshot = new LinkedHashMap<>();
        for (long encoded : included) {
            if (hollow && !isBoundary(encoded, included)) {
                continue;
            }
            int[] pos = decodePos(encoded);
            if (pos[1] < world.getMinHeight() || pos[1] > world.getMaxHeight() - 1) {
                continue;
            }
            snapshot.put(encoded, world.getBiome(pos[0], pos[1], pos[2]));
        }
        return snapshot;
    }

    private Map<Long, Biome> snapshotColumnBiomes(SelectionBounds bounds) {
        Map<Long, Biome> snapshot = new LinkedHashMap<>();
        for (int x = bounds.minX; x <= bounds.maxX; x++) {
            for (int z = bounds.minZ; z <= bounds.maxZ; z++) {
                snapshot.put(encodeColumn(x, z), bounds.world().getBiome(x, z));
            }
        }
        return snapshot;
    }

    private Map<Long, Biome> snapshotColumnBiomes(World world, Set<Long> included, boolean hollow) {
        Map<Long, Biome> snapshot = new LinkedHashMap<>();
        for (long encoded : included) {
            if (hollow && !isBoundary(encoded, included)) {
                continue;
            }
            int[] pos = decodePos(encoded);
            if (pos[1] < world.getMinHeight() || pos[1] > world.getMaxHeight() - 1) {
                continue;
            }
            long key = encodeColumn(pos[0], pos[2]);
            snapshot.putIfAbsent(key, world.getBiome(pos[0], pos[2]));
        }
        return snapshot;
    }

    private BiomeApplyResult diffBiomeSnapshots(SelectionBounds bounds, Map<Long, Biome> beforeBlocks, Map<Long, Biome> beforeColumns) {
        List<BiomeChange> blockChanges = new ArrayList<>();
        for (int x = bounds.minX; x <= bounds.maxX; x++) {
            for (int y = bounds.minY; y <= bounds.maxY; y++) {
                for (int z = bounds.minZ; z <= bounds.maxZ; z++) {
                    long key = encodePos(x, y, z);
                    Biome before = beforeBlocks.get(key);
                    Biome after = bounds.world().getBiome(x, y, z);
                    if (before != after) {
                        blockChanges.add(new BiomeChange(new Location(bounds.world(), x, y, z), before, after));
                    }
                }
            }
        }
        List<BiomeColumnChange> columnChanges = new ArrayList<>();
        for (int x = bounds.minX; x <= bounds.maxX; x++) {
            for (int z = bounds.minZ; z <= bounds.maxZ; z++) {
                long key = encodeColumn(x, z);
                Biome before = beforeColumns.get(key);
                Biome after = bounds.world().getBiome(x, z);
                if (before != after) {
                    columnChanges.add(new BiomeColumnChange(bounds.world(), x, z, before, after));
                }
            }
        }
        return new BiomeApplyResult(blockChanges, columnChanges);
    }

    private record BiomeApplyResult(List<BiomeChange> blockChanges, List<BiomeColumnChange> columnChanges) {
    }

    record LastStructureGeneration(String structureId, Location anchor, List<BlockChange> changes) {
    }

    private record SelectionBounds(World world, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        private static SelectionBounds from(Selection selection) {
            return new SelectionBounds(selection.getPos1().getWorld(),
                    selection.getMinX(), selection.getMinY(), selection.getMinZ(),
                    selection.getMaxX(), selection.getMaxY(), selection.getMaxZ());
        }

        private long volume() {
            return (long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
        }
    }

    @FunctionalInterface
    interface ExpressionCompiler {
        ExpressionEngine.Program compile(String expression);
    }

    interface VanillaPlacementBackend {
        void loadChunk(World world, int chunkX, int chunkZ);

        boolean dispatch(World world, String commandPrefix, int x, int y, int z);
    }

    private static final class ChangeCollector {
        private final Map<Long, BlockData> before = new LinkedHashMap<>();

        private void captureBefore(Block block) {
            long key = encode(block.getX(), block.getY(), block.getZ());
            before.putIfAbsent(key, block.getBlockData().clone());
        }

        private List<BlockChange> toBlockChanges(World world) {
            return toBlockChanges(world, Map.of());
        }

        private List<BlockChange> toBlockChanges(World world, Map<Long, BlockData> beforeOverrides) {
            List<BlockChange> changes = new ArrayList<>();
            for (Map.Entry<Long, BlockData> entry : before.entrySet()) {
                int[] pos = decode(entry.getKey());
                Block block = world.getBlockAt(pos[0], pos[1], pos[2]);
                BlockData after = block.getBlockData().clone();
                BlockData beforeData = beforeOverrides.getOrDefault(entry.getKey(), entry.getValue());
                if (beforeData.matches(after)) {
                    continue;
                }
                changes.add(new BlockChange(block.getLocation(), beforeData.clone(), after));
            }
            return changes;
        }

        private static long encode(int x, int y, int z) {
            return WorldPosCodec.pack(x, y, z);
        }

        private static int[] decode(long value) {
            WorldPosCodec.Position position = WorldPosCodec.unpack(value);
            return new int[]{position.x(), position.y(), position.z()};
        }
    }
}
