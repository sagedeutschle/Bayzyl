package com.bayzyl;

import com.bayzyl.shape.ShapeAdapter;
import com.bayzyl.shape.ShapeBounds;
import com.bayzyl.safety.OperationLimits;
import com.bayzyl.safety.WorkEstimate;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ShapeService {
    private static final int TARGET_RANGE = 64;
    private static final int MAX_PREVIEW_POINTS = 600;

    private final HistoryService historyService;
    private final ShapeAdapter adapter;
    private final SelectionManager selectionManager;
    private final Plugin plugin;

    public ShapeService(HistoryService historyService, ShapeAdapter adapter, SelectionManager selectionManager, Plugin plugin) {
        this.historyService = historyService;
        this.adapter = adapter;
        this.selectionManager = selectionManager;
        this.plugin = plugin;
    }

    public String getBackendName() {
        return adapter.getName();
    }

    public WorkEstimate estimateSphere(SphereRequest request) {
        return OperationLimits.estimateSphere(request.radiusX(), request.radiusY(), request.radiusZ());
    }

    public WorkEstimate estimateCylinder(CylinderRequest request) {
        return OperationLimits.estimateCylinder(request.radiusX(), request.radiusZ(), request.height());
    }

    public WorkEstimate estimatePyramid(PyramidRequest request) {
        return OperationLimits.estimatePyramid(request.size());
    }

    public ShapeResult createSphere(Player player, SphereRequest request) {
        return createSphere(player, request, request.confirm());
    }

    public ShapeResult createSphere(Player player, SphereRequest request, boolean confirmed) {
        return createSphereLike(player, request, HemisphereMode.FULL, "sphere", confirmed);
    }

    public ShapeResult createDome(Player player, SphereRequest request, boolean bowl) {
        return createDome(player, request, bowl, request.confirm());
    }

    public ShapeResult createDome(Player player, SphereRequest request, boolean bowl, boolean confirmed) {
        return createSphereLike(player, request, bowl ? HemisphereMode.BOTTOM : HemisphereMode.TOP,
                bowl ? "bowl" : "dome", confirmed);
    }

    public ShapeResult createCylinder(Player player, CylinderRequest request) {
        return createCylinder(player, request, request.confirm());
    }

    public ShapeResult createCylinder(Player player, CylinderRequest request, boolean confirmed) {
        ShapeResult refusal = preflight(estimateCylinder(request), confirmed, "cylinder");
        if (refusal != null) {
            return refusal;
        }
        Location anchor = resolveAnchor(player, request.anchorMode(), request.preview() || request.thickness() > 1);
        if (anchor == null) {
            return ShapeResult.failed("No valid anchor found.");
        }

        String summary = summarizeCylinder(request, anchor);
        if (request.preview()) {
            int shown = preview(player, previewCylinderPoints(request), anchor, request.evenCenter() ? 0.5 : 0.0, 0.0, request.evenCenter() ? 0.5 : 0.0);
            return ShapeResult.preview(summary + " Previewed " + shown + " points.");
        }

        if (request.thickness() > 1 || request.evenCenter() || hasMask(request.mask()) || !request.distribution().isSingleMaterial()) {
            int changed = applyCustomPoints(player, anchor, request.distribution(), previewCylinderPoints(request),
                    request.evenCenter() ? 0.5 : 0.0, 0.0, request.evenCenter() ? 0.5 : 0.0, request.mask());
            return ShapeResult.created(changed, summary);
        }

        ShapeBounds bounds = adapter.cylinderBounds(anchor, request);
        int changed = executeWithHistory(player, bounds, () -> adapter.createCylinder(player, anchor, request), "cylinder");
        return ShapeResult.created(changed, summary);
    }

    public ShapeResult createPyramid(Player player, PyramidRequest request) {
        return createPyramid(player, request, request.confirm());
    }

    public ShapeResult createPyramid(Player player, PyramidRequest request, boolean confirmed) {
        ShapeResult refusal = preflight(estimatePyramid(request), confirmed, "pyramid");
        if (refusal != null) {
            return refusal;
        }
        Location anchor = resolveAnchor(player, request.anchorMode(), request.preview());
        if (anchor == null) {
            return ShapeResult.failed("No valid anchor found.");
        }

        String summary = summarizePyramid(request, anchor);
        if (request.preview()) {
            int shown = preview(player, previewPyramidPoints(request), anchor, 0.0, 0.0, 0.0);
            return ShapeResult.preview(summary + " Previewed " + shown + " points.");
        }

        if (hasMask(request.mask()) || !request.distribution().isSingleMaterial()) {
            int changed = applyCustomPoints(player, anchor, request.distribution(), previewPyramidPoints(request), 0.0, 0.0, 0.0, request.mask());
            return ShapeResult.created(changed, summary);
        }

        ShapeBounds bounds = adapter.pyramidBounds(anchor, request);
        int changed = executeWithHistory(player, bounds, () -> adapter.createPyramid(player, anchor, request), "pyramid");
        return ShapeResult.created(changed, summary);
    }

    private ShapeResult createSphereLike(Player player, SphereRequest request, HemisphereMode hemisphereMode,
                                         String label, boolean confirmed) {
        ShapeResult refusal = preflight(estimateSphere(request), confirmed, label);
        if (refusal != null) {
            return refusal;
        }
        Location anchor = resolveAnchor(player, request.anchorMode(), request.preview() || request.thickness() > 1 || hemisphereMode != HemisphereMode.FULL);
        if (anchor == null) {
            return ShapeResult.failed("No valid anchor found.");
        }

        boolean halfOffset = request.evenCenter();
        double offsetX = halfOffset ? 0.5 : 0.0;
        double offsetZ = halfOffset ? 0.5 : 0.0;
        String summary = summarizeSphere(request, label, hemisphereMode, anchor);

        if (request.preview()) {
            Set<Long> points = previewSpherePoints(request, hemisphereMode);
            int shown = preview(player, points, anchor, offsetX, 0.0, offsetZ);
            return ShapeResult.preview(summary + " Previewed " + shown + " points.");
        }

        if (request.thickness() > 1 || request.evenCenter() || hemisphereMode != HemisphereMode.FULL || hasMask(request.mask()) || !request.distribution().isSingleMaterial()) {
            Set<Long> points = previewSpherePoints(request, hemisphereMode);
            int changed = applyCustomPoints(player, anchor, request.distribution(), points, offsetX, 0.0, offsetZ, request.mask());
            return ShapeResult.created(changed, summary);
        }

        ShapeBounds bounds = adapter.sphereBounds(anchor, request);
        int changed = executeWithHistory(player, bounds, () -> adapter.createSphere(player, anchor, request), label);
        return ShapeResult.created(changed, summary);
    }

    private ShapeResult preflight(WorkEstimate estimate, boolean confirmed, String label) {
        if (estimate.hardRejected()) {
            return ShapeResult.failed(capitalize(label) + " refused: " + estimate.reason());
        }
        if (!estimate.permits(confirmed)) {
            return ShapeResult.failed("Large " + label + " (" + estimate.workUnits()
                    + " work units). Re-run with confirm:true.");
        }
        return null;
    }

    private int executeWithHistory(Player player, ShapeBounds bounds, ShapeOperation operation, String label) {
        World world = player.getWorld();
        Map<Long, BlockData> before = snapshot(world, bounds);
        try {
            int changed = operation.apply();
            pushHistory(player, world, bounds, before);
            return changed;
        } catch (Exception ex) {
            ChatOutput.send(player, ChatColor.RED + capitalize(adapter.getName()) + " " + label + " failed: " + ex.getMessage());
            return 0;
        }
    }

    private int applyCustomPoints(Player player, Location anchor, BlockDistribution distribution, Set<Long> encodedPoints, double offsetX, double offsetY, double offsetZ, BlockMask mask) {
        World world = anchor.getWorld();
        if (world == null) {
            return 0;
        }

        List<BlockChange> changes = new ArrayList<>();
        int skipped = 0;
        int matched = 0;
        int unchanged = 0;
        org.bukkit.Material firstBlockType = null;

        for (long encoded : encodedPoints) {
            int[] point = decodePoint(encoded);
            int x = blockFromOffset(anchor.getBlockX(), point[0], offsetX);
            int y = blockFromOffset(anchor.getBlockY(), point[1], offsetY);
            int z = blockFromOffset(anchor.getBlockZ(), point[2], offsetZ);
            Block block = world.getBlockAt(x, y, z);
            org.bukkit.Material placedMaterial = distribution.pickRandom();
            BlockData placedData = placedMaterial.createBlockData();

            if (firstBlockType == null) {
                firstBlockType = block.getType();
            }

            if (mask != null && !mask.matches(block.getType())) {
                skipped++;
                continue;
            }
            matched++;
            BlockData before = block.getBlockData().clone();
            if (before.matches(placedData)) {
                unchanged++;
                continue;
            }
            block.setBlockData(placedData, false);
            BlockData after = block.getBlockData().clone();
            changes.add(new BlockChange(block.getLocation(), before, after));
        }
        if (hasMask(mask)) {
            ChatOutput.send(player, ChatColor.DARK_GRAY + "Mask: " + mask.summary() + " | Center block: " + (firstBlockType != null ? firstBlockType.name() : "?"));
            ChatOutput.send(player, ChatColor.DARK_GRAY + "Shape: " + encodedPoints.size() + " total | " + matched + " matched | " + unchanged + " already " + distribution + " | " + skipped + " filtered");
        }
        historyService.record(player.getUniqueId(), changes);
        return changes.size();
    }

    private int preview(Player player, Set<Long> encodedPoints, Location anchor, double offsetX, double offsetY, double offsetZ) {
        int shown = renderPreviewOnce(player, encodedPoints, anchor, offsetX, offsetY, offsetZ);
        for (int i = 1; i <= 6; i++) {
            Bukkit.getScheduler().runTaskLater(plugin, () ->
                    renderPreviewOnce(player, encodedPoints, anchor, offsetX, offsetY, offsetZ), i * 5L);
        }
        return shown;
    }

    private int renderPreviewOnce(Player player, Set<Long> encodedPoints, Location anchor, double offsetX, double offsetY, double offsetZ) {
        if (!player.isOnline()) {
            return 0;
        }
        Particle.DustOptions dust = new Particle.DustOptions(Color.fromRGB(80, 200, 255), 1.1f);
        int step = Math.max(1, encodedPoints.size() / MAX_PREVIEW_POINTS);
        int index = 0;
        int shown = 0;
        World world = player.getWorld();
        for (long encoded : encodedPoints) {
            if (index++ % step != 0) {
                continue;
            }
            int[] point = decodePoint(encoded);
            double x = anchor.getBlockX() + point[0] + offsetX + 0.5;
            double y = anchor.getBlockY() + point[1] + offsetY + 0.5;
            double z = anchor.getBlockZ() + point[2] + offsetZ + 0.5;
            player.spawnParticle(Particle.DUST, new Location(world, x, y, z), 2, 0.03, 0.03, 0.03, 0.0, dust);
            shown++;
        }
        return shown;
    }

    private Location resolveAnchor(Player player, ShapeAnchorMode mode, boolean preferTargetForCustom) {
        if (mode == ShapeAnchorMode.PLAYER && preferTargetForCustom) {
            Location target = resolveTarget(player);
            if (target != null) {
                return target;
            }
        }
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
        return result.getHitBlock().getLocation();
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
        int centerX = (selection.getMinX() + selection.getMaxX()) / 2;
        int centerY = (selection.getMinY() + selection.getMaxY()) / 2;
        int centerZ = (selection.getMinZ() + selection.getMaxZ()) / 2;
        return new Location(world, centerX, centerY, centerZ);
    }

    private Set<Long> previewSpherePoints(SphereRequest request, HemisphereMode hemisphereMode) {
        Set<Long> points = new LinkedHashSet<>();
        double centerX = request.evenCenter() ? 0.5 : 0.0;
        double centerZ = request.evenCenter() ? 0.5 : 0.0;
        double outerX = request.radiusX() + 0.5;
        double outerY = request.radiusY() + 0.5;
        double outerZ = request.radiusZ() + 0.5;
        double innerX = Math.max(0.0, outerX - Math.max(1, request.thickness()));
        double innerY = Math.max(0.0, outerY - Math.max(1, request.thickness()));
        double innerZ = Math.max(0.0, outerZ - Math.max(1, request.thickness()));

        int minX = (int) Math.floor(-outerX - centerX);
        int maxX = (int) Math.ceil(outerX + centerX);
        int minY = (int) Math.floor(-outerY);
        int maxY = (int) Math.ceil(outerY);
        int minZ = (int) Math.floor(-outerZ - centerZ);
        int maxZ = (int) Math.ceil(outerZ + centerZ);

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                if (hemisphereMode == HemisphereMode.TOP && y < 0) {
                    continue;
                }
                if (hemisphereMode == HemisphereMode.BOTTOM && y > 0) {
                    continue;
                }
                for (int z = minZ; z <= maxZ; z++) {
                    double dx = x - centerX;
                    double dy = y;
                    double dz = z - centerZ;
                    if (!insideEllipsoid(dx, dy, dz, outerX, outerY, outerZ)) {
                        continue;
                    }
                    if (request.hollow() && request.thickness() <= 1) {
                        if (insideEllipsoid(dx, dy, dz, Math.max(outerX - 1.0, 0.0), Math.max(outerY - 1.0, 0.0), Math.max(outerZ - 1.0, 0.0))) {
                            continue;
                        }
                    } else if (request.thickness() > 1) {
                        if (innerX > 0.0 && innerY > 0.0 && innerZ > 0.0 && insideEllipsoid(dx, dy, dz, innerX, innerY, innerZ)) {
                            continue;
                        }
                    }
                    points.add(encodePoint(x, y, z));
                }
            }
        }
        return points;
    }

    private Set<Long> previewCylinderPoints(CylinderRequest request) {
        Set<Long> points = new LinkedHashSet<>();
        double centerX = request.evenCenter() ? 0.5 : 0.0;
        double centerZ = request.evenCenter() ? 0.5 : 0.0;
        double outerX = request.radiusX() + 0.5;
        double outerZ = request.radiusZ() + 0.5;
        double innerX = Math.max(0.0, outerX - Math.max(1, request.thickness()));
        double innerZ = Math.max(0.0, outerZ - Math.max(1, request.thickness()));
        int minX = (int) Math.floor(-outerX - centerX);
        int maxX = (int) Math.ceil(outerX + centerX);
        int minZ = (int) Math.floor(-outerZ - centerZ);
        int maxZ = (int) Math.ceil(outerZ + centerZ);

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                double dx = x - centerX;
                double dz = z - centerZ;
                if (!insideEllipse(dx, dz, outerX, outerZ)) {
                    continue;
                }
                if (request.hollow() && request.thickness() <= 1) {
                    if (insideEllipse(dx, dz, Math.max(outerX - 1.0, 0.0), Math.max(outerZ - 1.0, 0.0))) {
                        continue;
                    }
                } else if (request.thickness() > 1) {
                    if (innerX > 0.0 && innerZ > 0.0 && insideEllipse(dx, dz, innerX, innerZ)) {
                        continue;
                    }
                }
                for (int y = 0; y < request.height(); y++) {
                    points.add(encodePoint(x, y, z));
                }
            }
        }
        return points;
    }

    private Set<Long> previewPyramidPoints(PyramidRequest request) {
        Set<Long> points = new LinkedHashSet<>();
        int size = request.size();
        for (int y = 0; y <= request.size(); y++) {
            int radius = size - y;
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    if (request.hollow() && Math.abs(x) != radius && Math.abs(z) != radius) {
                        continue;
                    }
                    points.add(encodePoint(x, y, z));
                }
            }
        }
        return points;
    }

    private boolean insideEllipsoid(double x, double y, double z, double rx, double ry, double rz) {
        if (rx <= 0.0 || ry <= 0.0 || rz <= 0.0) {
            return false;
        }
        return (x * x) / (rx * rx) + (y * y) / (ry * ry) + (z * z) / (rz * rz) <= 1.0;
    }

    private boolean insideEllipse(double x, double z, double rx, double rz) {
        if (rx <= 0.0 || rz <= 0.0) {
            return false;
        }
        return (x * x) / (rx * rx) + (z * z) / (rz * rz) <= 1.0;
    }

    private Map<Long, BlockData> snapshot(World world, ShapeBounds bounds) {
        Map<Long, BlockData> snapshot = new HashMap<>();
        for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
            for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
                for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
                    snapshot.put(encodeWorldPos(x, y, z), world.getBlockAt(x, y, z).getBlockData().clone());
                }
            }
        }
        return snapshot;
    }

    private void pushHistory(Player player, World world, ShapeBounds bounds, Map<Long, BlockData> before) {
        List<BlockChange> changes = new ArrayList<>();
        for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
            for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
                for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
                    long key = encodeWorldPos(x, y, z);
                    BlockData previous = before.get(key);
                    Block block = world.getBlockAt(x, y, z);
                    BlockData after = block.getBlockData().clone();
                    if (previous != null && previous.matches(after)) {
                        continue;
                    }
                    changes.add(new BlockChange(block.getLocation(), previous, after));
                }
            }
        }
        historyService.record(player.getUniqueId(), changes);
    }

    private long encodeWorldPos(int x, int y, int z) {
        return WorldPosCodec.pack(x, y, z);
    }

    private long encodePoint(int x, int y, int z) {
        long ox = x + 1_048_576L;
        long oy = y + 1_048_576L;
        long oz = z + 1_048_576L;
        return (ox << 42) | (oy << 21) | oz;
    }

    private int[] decodePoint(long value) {
        return new int[]{
                (int) ((value >> 42) - 1_048_576L),
                (int) (((value >> 21) & 0x1F_FFFFL) - 1_048_576L),
                (int) ((value & 0x1F_FFFFL) - 1_048_576L)
        };
    }

    private int blockFromOffset(int base, int offset, double halfOffset) {
        return (int) Math.floor(base + offset + halfOffset);
    }

    private String summarizeSphere(SphereRequest request, String label, HemisphereMode mode, Location anchor) {
        StringBuilder summary = new StringBuilder("Creating ");
        if (mode == HemisphereMode.TOP) {
            summary.append(request.hollow() ? "hollow dome" : "dome");
        } else if (mode == HemisphereMode.BOTTOM) {
            summary.append(request.hollow() ? "hollow bowl" : "bowl");
        } else {
            summary.append(request.hollow() ? "hollow sphere" : "sphere");
        }
        summary.append(" r=").append(request.radiusX());
        if (request.radiusY() != request.radiusX() || request.radiusZ() != request.radiusX()) {
            summary.append(",").append(request.radiusY()).append(",").append(request.radiusZ());
        }
        appendDistribution(summary, request.distribution());
        if (request.thickness() > 1) {
            summary.append(" thickness=").append(request.thickness());
        }
        if (request.evenCenter()) {
            summary.append(" center=even");
        }
        appendMask(summary, request.mask());
        summary.append(" at ").append(formatAnchor(request.anchorMode(), anchor));
        return summary.toString();
    }

    private String summarizeCylinder(CylinderRequest request, Location anchor) {
        StringBuilder summary = new StringBuilder("Creating ");
        summary.append(request.hollow() ? "hollow cylinder" : "cylinder")
                .append(" r=").append(request.radiusX());
        if (request.radiusZ() != request.radiusX()) {
            summary.append(",").append(request.radiusZ());
        }
        summary.append(" h=").append(request.height());
        appendDistribution(summary, request.distribution());
        if (request.thickness() > 1) {
            summary.append(" thickness=").append(request.thickness());
        }
        if (request.evenCenter()) {
            summary.append(" center=even");
        }
        appendMask(summary, request.mask());
        summary.append(" at ").append(formatAnchor(request.anchorMode(), anchor));
        return summary.toString();
    }

    private String summarizePyramid(PyramidRequest request, Location anchor) {
        StringBuilder summary = new StringBuilder("Creating ")
                .append(request.hollow() ? "hollow pyramid" : "pyramid")
                .append(" size=").append(request.size());
        appendDistribution(summary, request.distribution());
        appendMask(summary, request.mask());
        summary.append(" at ").append(formatAnchor(request.anchorMode(), anchor));
        return summary.toString();
    }

    private void appendDistribution(StringBuilder summary, BlockDistribution distribution) {
        if (distribution == null) {
            return;
        }
        if (distribution.isSingleMaterial()) {
            summary.append(" block=").append(distribution.getSoleMaterial().name().toLowerCase());
        } else {
            summary.append(" blocks=").append(distribution);
        }
    }

    private void appendMask(StringBuilder summary, BlockMask mask) {
        if (hasMask(mask)) {
            summary.append(" mask=").append(mask.summary());
        }
    }

    private boolean hasMask(BlockMask mask) {
        return mask != null && mask.getRaw() != null && !mask.getRaw().isBlank();
    }

    private String formatAnchor(ShapeAnchorMode mode, Location anchor) {
        return mode.name().toLowerCase().replace('_', '-') + " (" + anchor.getBlockX() + "," + anchor.getBlockY() + "," + anchor.getBlockZ() + ")";
    }

    private String capitalize(String value) {
        if (value == null || value.isEmpty()) {
            return "Shape";
        }
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    @FunctionalInterface
    private interface ShapeOperation {
        int apply() throws Exception;
    }

    private enum HemisphereMode {
        FULL,
        TOP,
        BOTTOM
    }
}
