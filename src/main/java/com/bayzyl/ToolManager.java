package com.bayzyl;

import com.bayzyl.generation.VanillaContentRegistry;
import com.bayzyl.detail.DetailBrushSafety;
import com.bayzyl.safety.BrushSafety;
import com.bayzyl.safety.OperationLimits;
import com.bayzyl.safety.WorkEstimate;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ToolManager {
    private final DetailBrushSafety detailBrushSafety;
    private final NamespacedKey toolKey;
    private final NamespacedKey brushTypeKey;
    private final NamespacedKey radiusKey;
    private final NamespacedKey maskKey;
    private final NamespacedKey surfaceKey;
    private final NamespacedKey selectionKey;
    private final NamespacedKey carveKey;
    private final NamespacedKey iterationsKey;
    private final NamespacedKey bedrockKey;
    private final NamespacedKey shapeTypeKey;
    private final NamespacedKey shapeMaterialKey;
    private final NamespacedKey shapeRadiusXKey;
    private final NamespacedKey shapeRadiusYKey;
    private final NamespacedKey shapeRadiusZKey;
    private final NamespacedKey shapeHeightKey;
    private final NamespacedKey shapeSizeKey;
    private final NamespacedKey shapeAnchorKey;
    private final NamespacedKey shapeConfirmKey;
    private final NamespacedKey structureIdKey;
    private final NamespacedKey structureAnchorKey;
    private final NamespacedKey structureConfirmKey;
    private final NamespacedKey paintDensityKey;
    private final NamespacedKey paintSizeKey;
    private final NamespacedKey patternModeKey;
    private final NamespacedKey patternFromKey;
    private final NamespacedKey patternToKey;
    private final NamespacedKey patternPaletteKey;
    private final NamespacedKey detailPresetKey;
    private final NamespacedKey detailParamsKey;
    private final NamespacedKey detailModeKey;
    private final NamespacedKey genTypeKey;
    private final NamespacedKey genCaveSubtypeKey;
    private final NamespacedKey genRadiusKey;
    private final NamespacedKey genParamsKey;
    private final NamespacedKey genAdaptKey;
    private final NamespacedKey genSeedKey;

    public ToolManager(Bayzyl plugin, DetailBrushSafety detailBrushSafety) {
        if (detailBrushSafety == null) {
            throw new IllegalArgumentException("Detail brush safety is required.");
        }
        this.detailBrushSafety = detailBrushSafety;
        this.toolKey = new NamespacedKey(plugin, "tool");
        this.brushTypeKey = new NamespacedKey(plugin, "brush_type");
        this.radiusKey = new NamespacedKey(plugin, "radius");
        this.maskKey = new NamespacedKey(plugin, "mask");
        this.surfaceKey = new NamespacedKey(plugin, "surface_only");
        this.selectionKey = new NamespacedKey(plugin, "selection_only");
        this.carveKey = new NamespacedKey(plugin, "carve_only");
        this.iterationsKey = new NamespacedKey(plugin, "iterations");
        this.bedrockKey = new NamespacedKey(plugin, "edit_bedrock");
        this.shapeTypeKey = new NamespacedKey(plugin, "shape_type");
        this.shapeMaterialKey = new NamespacedKey(plugin, "shape_material");
        this.shapeRadiusXKey = new NamespacedKey(plugin, "shape_radius_x");
        this.shapeRadiusYKey = new NamespacedKey(plugin, "shape_radius_y");
        this.shapeRadiusZKey = new NamespacedKey(plugin, "shape_radius_z");
        this.shapeHeightKey = new NamespacedKey(plugin, "shape_height");
        this.shapeSizeKey = new NamespacedKey(plugin, "shape_size");
        this.shapeAnchorKey = new NamespacedKey(plugin, "shape_anchor");
        this.shapeConfirmKey = new NamespacedKey(plugin, "shape_confirm");
        this.structureIdKey = new NamespacedKey(plugin, "structure_id");
        this.structureAnchorKey = new NamespacedKey(plugin, "structure_anchor");
        this.structureConfirmKey = new NamespacedKey(plugin, "structure_confirm");
        this.paintDensityKey = new NamespacedKey(plugin, "paint_density");
        this.paintSizeKey = new NamespacedKey(plugin, "paint_size");
        this.patternModeKey = new NamespacedKey(plugin, "pattern_mode");
        this.patternFromKey = new NamespacedKey(plugin, "pattern_from");
        this.patternToKey = new NamespacedKey(plugin, "pattern_to");
        this.patternPaletteKey = new NamespacedKey(plugin, "pattern_palette");
        this.detailPresetKey = new NamespacedKey(plugin, "detail_preset");
        this.detailParamsKey = new NamespacedKey(plugin, "detail_params");
        this.detailModeKey = new NamespacedKey(plugin, "detail_mode");
        this.genTypeKey = new NamespacedKey(plugin, "gen_type");
        this.genCaveSubtypeKey = new NamespacedKey(plugin, "gen_cave_subtype");
        this.genRadiusKey = new NamespacedKey(plugin, "gen_radius");
        this.genParamsKey = new NamespacedKey(plugin, "gen_params");
        this.genAdaptKey = new NamespacedKey(plugin, "gen_adapt");
        this.genSeedKey = new NamespacedKey(plugin, "gen_seed");
    }

    public ItemStack createWand() {
        ItemStack item = new ItemStack(Material.GOLDEN_AXE);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + "Bayzyl Wand");
        meta.getPersistentDataContainer().set(toolKey, PersistentDataType.STRING, ToolType.WAND.name());
        meta.setLore(List.of(
                ChatColor.WHITE + "Left click: pos1",
                ChatColor.WHITE + "Right click: pos2"
        ));
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack createEraser(EraserSettings settings) {
        if (!BrushSafety.isValidEraser(settings)) {
            return null;
        }
        ItemStack item = new ItemStack(Material.BRUSH);
        return bindEraser(item, settings, true) ? item : null;
    }

    public boolean bindEraser(ItemStack item, EraserSettings settings, boolean rebrand) {
        if (!BrushSafety.isValidEraser(settings) || item == null) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        if (rebrand) {
            meta.setDisplayName(ChatColor.LIGHT_PURPLE + "Bayzyl Eraser");
        }
        PersistentDataContainer data = meta.getPersistentDataContainer();
        data.set(toolKey, PersistentDataType.STRING, ToolType.ERASER.name());
        data.set(radiusKey, PersistentDataType.INTEGER, settings.getRadius());
        if (settings.getMask() != null && settings.getMask().getRaw() != null
                && !settings.getMask().getRaw().isBlank()) {
            data.set(maskKey, PersistentDataType.STRING, settings.getMask().getRaw());
        } else {
            data.remove(maskKey);
        }
        data.set(surfaceKey, PersistentDataType.BYTE, (byte) (settings.isSurfaceOnly() ? 1 : 0));
        data.set(selectionKey, PersistentDataType.BYTE, (byte) (settings.isSelectionOnly() ? 1 : 0));
        data.set(carveKey, PersistentDataType.BYTE, (byte) (settings.isCarveOnly() ? 1 : 0));
        data.set(bedrockKey, PersistentDataType.BYTE, (byte) (settings.isEditBedrock() ? 1 : 0));

        meta.setLore(buildEraserLore(settings));
        item.setItemMeta(meta);
        return true;
    }

    public ItemStack createSmoothBrush(int radius, int iterations) {
        return createTerrainBrush(TerrainBrushType.SMOOTH, radius, iterations, false);
    }

    public ItemStack createTerrainBrush(TerrainBrushType type, int radius, int power, boolean editBedrock) {
        TerrainBrushSettings settings = new TerrainBrushSettings(type, radius, power, editBedrock);
        if (!BrushSafety.isValidTerrain(settings)) {
            return null;
        }
        ItemStack item = new ItemStack(Material.BRUSH);
        return bindTerrainBrush(item, settings, true) ? item : null;
    }

    public boolean bindTerrainBrush(ItemStack item, TerrainBrushSettings settings, boolean rebrand) {
        if (!BrushSafety.isValidTerrain(settings) || item == null) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        if (rebrand) {
            meta.setDisplayName(ChatColor.AQUA + "Bayzyl " + settings.type().displayName());
        }
        PersistentDataContainer data = meta.getPersistentDataContainer();
        data.set(toolKey, PersistentDataType.STRING, ToolType.TERRAIN_BRUSH.name());
        data.set(brushTypeKey, PersistentDataType.STRING, settings.type().name());
        data.set(radiusKey, PersistentDataType.INTEGER, settings.radius());
        data.set(iterationsKey, PersistentDataType.INTEGER, settings.power());
        data.set(bedrockKey, PersistentDataType.BYTE, (byte) (settings.editBedrock() ? 1 : 0));
        meta.setLore(List.of(
                ChatColor.WHITE + "Radius: " + settings.radius(),
                ChatColor.WHITE + settings.type().powerLabel() + ": " + settings.power(),
                ChatColor.WHITE + "Mode: " + settings.type().commandName(),
                ChatColor.WHITE + "Bedrock: " + (settings.editBedrock() ? "on" : "protected"),
                ChatColor.WHITE + (settings.type().isCleanupMode() ? "Right click to erase matching clutter" : "Right click terrain to apply")
        ));
        item.setItemMeta(meta);
        return true;
    }

    public ItemStack createShapeBrush(ShapeBrushSettings settings) {
        ItemStack item = new ItemStack(Material.BRUSH);
        return bindShapeBrush(item, settings, true) ? item : null;
    }

    public boolean bindShapeBrush(ItemStack item, ShapeBrushSettings settings, boolean rebrand) {
        if (!isValidShapeBrush(settings) || item == null) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        if (rebrand) {
            meta.setDisplayName(ChatColor.GOLD + "Bayzyl " + settings.type().displayName() + " Brush");
        }
        PersistentDataContainer data = meta.getPersistentDataContainer();
        data.set(toolKey, PersistentDataType.STRING, ToolType.SHAPE_BRUSH.name());
        data.set(shapeTypeKey, PersistentDataType.STRING, settings.type().name());
        data.set(shapeMaterialKey, PersistentDataType.STRING, settings.distribution().toString());
        data.set(shapeRadiusXKey, PersistentDataType.INTEGER, settings.radiusX());
        data.set(shapeRadiusYKey, PersistentDataType.INTEGER, settings.radiusY());
        data.set(shapeRadiusZKey, PersistentDataType.INTEGER, settings.radiusZ());
        data.set(shapeHeightKey, PersistentDataType.INTEGER, settings.height());
        data.set(shapeSizeKey, PersistentDataType.INTEGER, settings.size());
        data.set(shapeAnchorKey, PersistentDataType.STRING, settings.anchorMode().name());
        data.set(shapeConfirmKey, PersistentDataType.BYTE, (byte) (settings.confirm() ? 1 : 0));
        if (settings.mask() != null && settings.mask().getRaw() != null && !settings.mask().getRaw().isBlank()) {
            data.set(maskKey, PersistentDataType.STRING, settings.mask().getRaw());
        } else {
            data.remove(maskKey);
        }
        meta.setLore(buildShapeBrushLore(settings));
        item.setItemMeta(meta);
        return true;
    }

    public ItemStack createStructureBrush(StructureBrushSettings settings) {
        ItemStack item = new ItemStack(Material.BRUSH);
        bindStructureBrush(item, settings, true);
        return item;
    }

    public void bindStructureBrush(ItemStack item, StructureBrushSettings settings, boolean rebrand) {
        if (item == null || settings == null) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }
        if (rebrand) {
            meta.setDisplayName(ChatColor.DARK_AQUA + "Bayzyl Structure Brush");
        }
        PersistentDataContainer data = meta.getPersistentDataContainer();
        data.set(toolKey, PersistentDataType.STRING, ToolType.STRUCTURE_BRUSH.name());
        data.set(structureIdKey, PersistentDataType.STRING, normalizeStructureId(settings.structureId()));
        data.set(structureAnchorKey, PersistentDataType.STRING, settings.anchorMode().name());
        data.set(structureConfirmKey, PersistentDataType.BYTE, (byte) (settings.confirm() ? 1 : 0));
        meta.setLore(buildStructureBrushLore(settings));
        item.setItemMeta(meta);
    }

    public boolean unbindShapeBrush(ItemStack item) {
        if (readShapeBrushSettings(item) == null) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer data = meta.getPersistentDataContainer();
        data.remove(toolKey);
        data.remove(shapeTypeKey);
        data.remove(shapeMaterialKey);
        data.remove(shapeRadiusXKey);
        data.remove(shapeRadiusYKey);
        data.remove(shapeRadiusZKey);
        data.remove(shapeHeightKey);
        data.remove(shapeSizeKey);
        data.remove(shapeAnchorKey);
        data.remove(shapeConfirmKey);
        data.remove(maskKey);
        meta.setLore(null);
        meta.setDisplayName(null);
        item.setItemMeta(meta);
        return true;
    }

    public boolean updateShapeBrushMask(ItemStack item, BlockMask mask) {
        ShapeBrushSettings current = readShapeBrushSettings(item);
        if (current == null) {
            return false;
        }
        ShapeBrushSettings updated = new ShapeBrushSettings(
                current.type(), current.distribution(), current.radiusX(), current.radiusY(), current.radiusZ(),
                current.height(), current.size(), current.anchorMode(), mask, current.confirm()
        );
        return bindShapeBrush(item, updated, false);
    }

    public boolean updateShapeBrushDistribution(ItemStack item, BlockDistribution distribution) {
        ShapeBrushSettings current = readShapeBrushSettings(item);
        if (current == null || distribution == null) {
            return false;
        }
        ShapeBrushSettings updated = new ShapeBrushSettings(
                current.type(), distribution, current.radiusX(), current.radiusY(), current.radiusZ(),
                current.height(), current.size(), current.anchorMode(), current.mask(), current.confirm()
        );
        return bindShapeBrush(item, updated, false);
    }

    public boolean updateShapeBrushMaterial(ItemStack item, Material material) {
        ShapeBrushSettings current = readShapeBrushSettings(item);
        if (current == null || material == null) {
            return false;
        }
        return updateShapeBrushDistribution(item, BlockDistribution.parse(material.name().toLowerCase(Locale.ROOT)));
    }

    public boolean updateShapeBrushSize(ItemStack item, int size, Integer height) {
        ShapeBrushSettings current = readShapeBrushSettings(item);
        ShapeBrushSettings updated = resizedShapeBrush(current, size, height);
        if (updated == null) {
            return false;
        }
        return bindShapeBrush(item, updated, false);
    }

    ShapeBrushSettings resizedShapeBrush(ShapeBrushSettings current, int size, Integer height) {
        if (current == null || size < 1) {
            return null;
        }
        int updatedHeight = height != null && height > 0 ? height : current.height();
        return new ShapeBrushSettings(
                current.type(), current.distribution(), size, size, size,
                updatedHeight, size, current.anchorMode(), current.mask(), current.confirm()
        );
    }

    public boolean updateEraserMask(ItemStack item, BlockMask mask) {
        if (getToolType(item) != ToolType.ERASER) {
            return false;
        }
        EraserSettings current = readEraserSettings(item);
        if (current == null) {
            return false;
        }
        return bindEraser(item, new EraserSettings(
                current.getRadius(),
                mask,
                current.isSurfaceOnly(),
                current.isSelectionOnly(),
                current.isCarveOnly(),
                current.isEditBedrock()
        ), false);
    }

    public boolean updateEraserSize(ItemStack item, int size) {
        if (getToolType(item) != ToolType.ERASER || size < 1) {
            return false;
        }
        EraserSettings current = readEraserSettings(item);
        if (current == null) {
            return false;
        }
        return bindEraser(item, new EraserSettings(
                size,
                current.getMask(),
                current.isSurfaceOnly(),
                current.isSelectionOnly(),
                current.isCarveOnly(),
                current.isEditBedrock()
        ), false);
    }

    public boolean updateTerrainBrushSize(ItemStack item, int size, Integer power) {
        TerrainBrushSettings current = readTerrainBrushSettings(item);
        if (current == null || size < 1 || (power != null && power <= 0)) {
            return false;
        }
        int updatedPower = power != null ? power : current.power();
        return bindTerrainBrush(item,
                new TerrainBrushSettings(current.type(), size, updatedPower, current.editBedrock()), false);
    }

    public ToolType getToolType(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        String type = item.getItemMeta().getPersistentDataContainer().get(toolKey, PersistentDataType.STRING);
        if (type == null) {
            return null;
        }
        try {
            return ToolType.valueOf(type.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    public EraserSettings readEraserSettings(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer data = meta.getPersistentDataContainer();
        if (!ToolType.ERASER.name().equals(data.get(toolKey, PersistentDataType.STRING))) {
            return null;
        }
        Integer radius = data.get(radiusKey, PersistentDataType.INTEGER);
        if (radius == null || BrushSafety.assessEraser(radius).hardRejected()) {
            return null;
        }
        String maskRaw = readOptionalStrictString(data, maskKey);
        if (data.getKeys().contains(maskKey)
                && (maskRaw == null || !BrushSafety.isValidMaskRaw(maskRaw))) {
            return null;
        }
        Boolean surface = readStrictFlag(data, surfaceKey);
        Boolean selection = readStrictFlag(data, selectionKey);
        Boolean carve = readStrictFlag(data, carveKey);
        Boolean editBedrock = readStrictFlag(data, bedrockKey);
        if (surface == null || selection == null || carve == null || editBedrock == null) {
            return null;
        }
        EraserSettings settings = new EraserSettings(radius, BlockMask.parse(maskRaw),
                surface, selection, carve, editBedrock);
        return BrushSafety.isValidEraser(settings) ? settings : null;
    }

    public SmoothBrushSettings readSmoothBrushSettings(ItemStack item) {
        TerrainBrushSettings settings = readTerrainBrushSettings(item);
        if (settings == null || settings.type() != TerrainBrushType.SMOOTH) {
            return null;
        }
        return new SmoothBrushSettings(settings.radius(), settings.power(), settings.editBedrock());
    }

    public TerrainBrushSettings readTerrainBrushSettings(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer data = meta.getPersistentDataContainer();
        String rawToolType = data.get(toolKey, PersistentDataType.STRING);
        Integer radius = data.get(radiusKey, PersistentDataType.INTEGER);
        Integer power = data.get(iterationsKey, PersistentDataType.INTEGER);
        Boolean editBedrock = readStrictFlag(data, bedrockKey);
        if (radius == null || power == null) {
            return null;
        }
        if (editBedrock == null) {
            return null;
        }
        TerrainBrushSettings settings;
        if (ToolType.SMOOTH_BRUSH.name().equals(rawToolType)) {
            settings = new TerrainBrushSettings(TerrainBrushType.SMOOTH, radius, power, editBedrock);
            return BrushSafety.isValidTerrain(settings) ? settings : null;
        }
        if (!ToolType.TERRAIN_BRUSH.name().equals(rawToolType)) {
            return null;
        }
        String brushTypeRaw = data.get(brushTypeKey, PersistentDataType.STRING);
        if (brushTypeRaw == null) {
            return null;
        }
        TerrainBrushType brushType = TerrainBrushType.parse(brushTypeRaw);
        if (brushType == null) {
            return null;
        }
        settings = new TerrainBrushSettings(brushType, radius, power, editBedrock);
        return BrushSafety.isValidTerrain(settings) ? settings : null;
    }

    public ShapeBrushSettings readShapeBrushSettings(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }

        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer data = meta.getPersistentDataContainer();
        String rawToolType = data.get(toolKey, PersistentDataType.STRING);
        if (rawToolType == null || !ToolType.SHAPE_BRUSH.name().equals(rawToolType)) {
            return null;
        }

        ShapeBrushType type = ShapeBrushType.parse(data.get(shapeTypeKey, PersistentDataType.STRING));
        Integer radiusX = data.get(shapeRadiusXKey, PersistentDataType.INTEGER);
        Integer radiusY = data.get(shapeRadiusYKey, PersistentDataType.INTEGER);
        Integer radiusZ = data.get(shapeRadiusZKey, PersistentDataType.INTEGER);
        Integer height = data.get(shapeHeightKey, PersistentDataType.INTEGER);
        Integer size = data.get(shapeSizeKey, PersistentDataType.INTEGER);
        String anchorRaw = data.get(shapeAnchorKey, PersistentDataType.STRING);
        Byte confirmFlag = data.get(shapeConfirmKey, PersistentDataType.BYTE);
        BlockMask mask = BlockMask.parse(data.get(maskKey, PersistentDataType.STRING));

        BlockDistribution distribution;
        try {
            distribution = BlockDistribution.parse(data.get(shapeMaterialKey, PersistentDataType.STRING));
        } catch (IllegalArgumentException | NullPointerException ex) {
            return null;
        }

        if (type == null || radiusX == null || radiusY == null || radiusZ == null || height == null || size == null || anchorRaw == null) {
            return null;
        }

        ShapeAnchorMode anchorMode;
        try {
            anchorMode = ShapeAnchorMode.valueOf(anchorRaw);
        } catch (IllegalArgumentException ex) {
            return null;
        }

        if (data.getKeys().contains(shapeConfirmKey)
                && (confirmFlag == null || (confirmFlag != 0 && confirmFlag != 1))) {
            return null;
        }

        ShapeBrushSettings settings = new ShapeBrushSettings(
                type,
                distribution,
                radiusX,
                radiusY,
                radiusZ,
                height,
                size,
                anchorMode,
                mask,
                confirmFlag != null && confirmFlag == 1
        );
        return isValidShapeBrush(settings) ? settings : null;
    }

    private boolean isValidShapeBrush(ShapeBrushSettings settings) {
        if (settings == null || settings.type() == null || settings.distribution() == null
                || settings.anchorMode() == null) {
            return false;
        }
        if (settings.radiusX() < 0 || settings.radiusY() < 0 || settings.radiusZ() < 0
                || settings.height() < 0 || settings.size() < 0) {
            return false;
        }
        return !estimateShapeBrush(settings).hardRejected();
    }

    WorkEstimate estimateShapeBrush(ShapeBrushSettings settings) {
        return switch (settings.type()) {
            case SPHERE, HSPHERE -> OperationLimits.estimateSphere(
                    settings.radiusX(), settings.radiusY(), settings.radiusZ());
            case CYL, HCYL -> OperationLimits.estimateCylinder(
                    settings.radiusX(), settings.radiusZ(), settings.height());
            case PYRAMID, HPYRAMID -> OperationLimits.estimatePyramid(settings.size());
        };
    }

    public StructureBrushSettings readStructureBrushSettings(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer data = meta.getPersistentDataContainer();
        String rawToolType = data.get(toolKey, PersistentDataType.STRING);
        if (rawToolType == null || !ToolType.STRUCTURE_BRUSH.name().equals(rawToolType)) {
            return null;
        }
        String structureId = normalizeStructureId(data.get(structureIdKey, PersistentDataType.STRING));
        String anchorRaw = data.get(structureAnchorKey, PersistentDataType.STRING);
        Byte confirmFlag = data.get(structureConfirmKey, PersistentDataType.BYTE);
        if (structureId == null || anchorRaw == null) {
            return null;
        }
        ShapeAnchorMode anchorMode;
        try {
            anchorMode = ShapeAnchorMode.valueOf(anchorRaw);
        } catch (IllegalArgumentException ex) {
            return null;
        }
        return new StructureBrushSettings(structureId, anchorMode, confirmFlag != null && confirmFlag == 1);
    }

    private Boolean readStrictFlag(PersistentDataContainer data, NamespacedKey key) {
        if (!data.getKeys().contains(key)) {
            return false;
        }
        Byte value = data.get(key, PersistentDataType.BYTE);
        if (value == null || (value != 0 && value != 1)) {
            return null;
        }
        return value == 1;
    }

    private String readOptionalStrictString(PersistentDataContainer data, NamespacedKey key) {
        if (!data.getKeys().contains(key)) {
            return null;
        }
        return data.get(key, PersistentDataType.STRING);
    }

    private List<String> buildEraserLore(EraserSettings settings) {
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.WHITE + "Radius: " + settings.getRadius());
        if (settings.getMask() != null) {
            lore.add(ChatColor.WHITE + "Mask: " + settings.getMask().summary());
        }
        lore.add(ChatColor.WHITE + "Surface: " + (settings.isSurfaceOnly() ? "on" : "off"));
        lore.add(ChatColor.WHITE + "Selection: " + (settings.isSelectionOnly() ? "only" : "any"));
        lore.add(ChatColor.WHITE + "Mode: " + (settings.isCarveOnly() ? "carve" : "full"));
        lore.add(ChatColor.WHITE + "Bedrock: " + (settings.isEditBedrock() ? "on" : "protected"));
        return lore;
    }

    public void bindClipboardBrush(ItemStack item, boolean rebrand) {
        if (item == null) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }
        if (rebrand) {
            meta.setDisplayName(ChatColor.AQUA + "Bayzyl Clipboard Brush");
        }
        PersistentDataContainer data = meta.getPersistentDataContainer();
        data.set(toolKey, PersistentDataType.STRING, ToolType.CLIPBOARD_BRUSH.name());
        meta.setLore(List.of(
                ChatColor.WHITE + "Type: Clipboard",
                ChatColor.WHITE + "Right click to paste your clipboard at the target."
        ));
        item.setItemMeta(meta);
    }

    public boolean isClipboardBrush(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        String raw = item.getItemMeta().getPersistentDataContainer().get(toolKey, PersistentDataType.STRING);
        return ToolType.CLIPBOARD_BRUSH.name().equals(raw);
    }

    public boolean bindPaintBrush(ItemStack item, PaintBrushSettings settings, boolean rebrand) {
        if (!BrushSafety.isValidPaint(settings) || item == null) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        if (rebrand) {
            meta.setDisplayName(ChatColor.GREEN + "Bayzyl Paint Brush");
        }
        PersistentDataContainer data = meta.getPersistentDataContainer();
        data.set(toolKey, PersistentDataType.STRING, ToolType.PAINT_BRUSH.name());
        data.set(shapeMaterialKey, PersistentDataType.STRING, settings.material().name());
        data.set(paintSizeKey, PersistentDataType.INTEGER, settings.size());
        data.set(paintDensityKey, PersistentDataType.STRING, Double.toString(settings.density()));
        if (settings.mask() != null && settings.mask().getRaw() != null && !settings.mask().getRaw().isBlank()) {
            data.set(maskKey, PersistentDataType.STRING, settings.mask().getRaw());
        } else {
            data.remove(maskKey);
        }
        meta.setLore(List.of(
                ChatColor.WHITE + "Type: Paint",
                ChatColor.WHITE + "Block: " + settings.material().name().toLowerCase(Locale.ROOT),
                ChatColor.WHITE + "Size: " + settings.size(),
                ChatColor.WHITE + "Density: " + String.format(Locale.ROOT, "%.2f", settings.density()),
                ChatColor.WHITE + "Mask: " + (settings.mask() == null ? "any" : settings.mask().summary()),
                ChatColor.WHITE + "Right click a surface to scatter."
        ));
        item.setItemMeta(meta);
        return true;
    }

    public PaintBrushSettings readPaintBrushSettings(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer data = meta.getPersistentDataContainer();
        String raw = data.get(toolKey, PersistentDataType.STRING);
        if (!ToolType.PAINT_BRUSH.name().equals(raw)) {
            return null;
        }
        Material material = EditUtil.parseBlock(data.get(shapeMaterialKey, PersistentDataType.STRING));
        Integer size = data.get(paintSizeKey, PersistentDataType.INTEGER);
        String densityRaw = data.get(paintDensityKey, PersistentDataType.STRING);
        if (material == null || size == null || densityRaw == null) {
            return null;
        }
        if (BrushSafety.assessPaint(size).hardRejected()) {
            return null;
        }
        double density;
        try {
            density = Double.parseDouble(densityRaw);
        } catch (NumberFormatException ex) {
            return null;
        }
        String maskRaw = readOptionalStrictString(data, maskKey);
        if (data.getKeys().contains(maskKey)
                && (maskRaw == null || !BrushSafety.isValidMaskRaw(maskRaw))) {
            return null;
        }
        PaintBrushSettings settings = new PaintBrushSettings(material, size, density,
                maskRaw == null ? null : BlockMask.parse(maskRaw));
        return BrushSafety.isValidPaint(settings) ? settings : null;
    }

    public boolean updatePaintBrushMask(ItemStack item, BlockMask mask) {
        PaintBrushSettings current = readPaintBrushSettings(item);
        if (current == null) {
            return false;
        }
        return bindPaintBrush(item,
                new PaintBrushSettings(current.material(), current.size(), current.density(), mask), false);
    }

    public boolean updatePaintBrushMaterial(ItemStack item, Material material) {
        PaintBrushSettings current = readPaintBrushSettings(item);
        if (current == null || material == null || !material.isBlock()) {
            return false;
        }
        return bindPaintBrush(item,
                new PaintBrushSettings(material, current.size(), current.density(), current.mask()), false);
    }

    public boolean updatePaintBrushSize(ItemStack item, int size) {
        PaintBrushSettings current = readPaintBrushSettings(item);
        if (current == null || size < 1) {
            return false;
        }
        return bindPaintBrush(item,
                new PaintBrushSettings(current.material(), size, current.density(), current.mask()), false);
    }

    public boolean updatePaintBrushDensity(ItemStack item, double density) {
        PaintBrushSettings current = readPaintBrushSettings(item);
        if (current == null || !Double.isFinite(density) || density < 0.0 || density > 1.0) {
            return false;
        }
        return bindPaintBrush(item,
                new PaintBrushSettings(current.material(), current.size(), density, current.mask()), false);
    }

    public boolean bindPatternBrush(ItemStack item, PatternBrushSettings settings, boolean rebrand) {
        if (!BrushSafety.isValidPattern(settings) || item == null) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        if (rebrand) {
            meta.setDisplayName(ChatColor.GREEN + "Bayzyl " + settings.mode().displayName() + " Brush");
        }
        PersistentDataContainer data = meta.getPersistentDataContainer();
        data.set(toolKey, PersistentDataType.STRING, ToolType.PATTERN_BRUSH.name());
        data.set(patternModeKey, PersistentDataType.STRING, settings.mode().name());
        data.set(paintSizeKey, PersistentDataType.INTEGER, settings.size());
        data.set(paintDensityKey, PersistentDataType.STRING, Double.toString(settings.density()));
        data.set(patternFromKey, PersistentDataType.STRING, settings.fromMask() == null ? "" : settings.fromMask().getRaw());
        putMaterial(data, patternToKey, settings.to());
        String palette = serializePalette(settings.palette());
        if (palette.isBlank()) {
            data.remove(patternPaletteKey);
        } else {
            data.set(patternPaletteKey, PersistentDataType.STRING, palette);
        }
        if (settings.mask() != null && settings.mask().getRaw() != null && !settings.mask().getRaw().isBlank()) {
            data.set(maskKey, PersistentDataType.STRING, settings.mask().getRaw());
        } else {
            data.remove(maskKey);
        }
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.WHITE + "Type: " + settings.mode().displayName());
        if (settings.fromMask() != null && !settings.fromMask().summary().isBlank()) {
            lore.add(ChatColor.WHITE + "From: " + settings.fromMask().summary());
        }
        if (settings.to() != null) {
            lore.add(ChatColor.WHITE + "To: " + settings.to().name().toLowerCase(Locale.ROOT));
        }
        if (settings.palette() != null && !settings.palette().isEmpty()) {
            lore.add(ChatColor.WHITE + "Palette: " + summarizePalette(settings.palette()));
        }
        lore.add(ChatColor.WHITE + "Size: " + settings.size());
        lore.add(ChatColor.WHITE + "Density: " + String.format(Locale.ROOT, "%.2f", settings.density()));
        lore.add(ChatColor.WHITE + "Mask: " + (settings.mask() == null ? "any" : settings.mask().summary()));
        lore.add(ChatColor.WHITE + "Right click to apply.");
        meta.setLore(lore);
        item.setItemMeta(meta);
        return true;
    }

    public PatternBrushSettings readPatternBrushSettings(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer data = meta.getPersistentDataContainer();
        String raw = data.get(toolKey, PersistentDataType.STRING);
        if (!ToolType.PATTERN_BRUSH.name().equals(raw)) {
            return null;
        }
        PatternBrushMode mode = PatternBrushMode.parse(data.get(patternModeKey, PersistentDataType.STRING));
        Integer size = data.get(paintSizeKey, PersistentDataType.INTEGER);
        String densityRaw = data.get(paintDensityKey, PersistentDataType.STRING);
        if (mode == null || size == null || densityRaw == null) {
            return null;
        }
        if (BrushSafety.assessPattern(mode, size, 0L).hardRejected()) {
            return null;
        }
        double density;
        try {
            density = Double.parseDouble(densityRaw);
        } catch (NumberFormatException ex) {
            return null;
        }
        String fromRaw = readOptionalStrictString(data, patternFromKey);
        String toRaw = readOptionalStrictString(data, patternToKey);
        String paletteRaw = readOptionalStrictString(data, patternPaletteKey);
        String maskRaw = readOptionalStrictString(data, maskKey);
        if ((data.getKeys().contains(patternFromKey) && fromRaw == null)
                || (data.getKeys().contains(patternToKey) && toRaw == null)
                || (data.getKeys().contains(patternPaletteKey) && paletteRaw == null)
                || (data.getKeys().contains(maskKey) && maskRaw == null)) {
            return null;
        }
        if (fromRaw != null && !fromRaw.isBlank() && !BrushSafety.isValidMaskRaw(fromRaw)) {
            return null;
        }
        if (maskRaw != null && !BrushSafety.isValidMaskRaw(maskRaw)) {
            return null;
        }
        Material to = toRaw == null || toRaw.isBlank() ? null : EditUtil.parseBlock(toRaw);
        if (toRaw != null && !toRaw.isBlank() && to == null) {
            return null;
        }
        List<Material> palette = parsePaletteStrict(paletteRaw);
        if (palette == null) {
            return null;
        }
        PatternBrushSettings settings = new PatternBrushSettings(
                mode,
                fromRaw == null || fromRaw.isBlank() ? null : BlockMask.parse(fromRaw),
                to,
                palette,
                size,
                density,
                maskRaw == null ? null : BlockMask.parse(maskRaw)
        );
        return BrushSafety.isValidPattern(settings) ? settings : null;
    }

    public boolean updatePatternBrushMask(ItemStack item, BlockMask mask) {
        PatternBrushSettings current = readPatternBrushSettings(item);
        if (current == null) {
            return false;
        }
        return bindPatternBrush(item,
                new PatternBrushSettings(current.mode(), current.fromMask(), current.to(), current.palette(),
                        current.size(), current.density(), mask), false);
    }

    public boolean updatePatternBrushMaterial(ItemStack item, Material material) {
        PatternBrushSettings current = readPatternBrushSettings(item);
        if (current == null || material == null
                || current.mode() == PatternBrushMode.BLEND
                || current.mode() == PatternBrushMode.NOISE
                || current.mode() == PatternBrushMode.VEGETATION
                || current.mode() == PatternBrushMode.RESTORE) {
            return false;
        }
        return bindPatternBrush(item,
                new PatternBrushSettings(current.mode(), current.fromMask(), material, current.palette(),
                        current.size(), current.density(), current.mask()), false);
    }

    public boolean updatePatternBrushSize(ItemStack item, int size) {
        PatternBrushSettings current = readPatternBrushSettings(item);
        if (current == null || size < 1) {
            return false;
        }
        return bindPatternBrush(item,
                new PatternBrushSettings(current.mode(), current.fromMask(), current.to(), current.palette(),
                        size, current.density(), current.mask()), false);
    }

    public boolean updatePatternBrushDensity(ItemStack item, double density) {
        PatternBrushSettings current = readPatternBrushSettings(item);
        if (current == null || !Double.isFinite(density) || density < 0.0 || density > 1.0) {
            return false;
        }
        return bindPatternBrush(item,
                new PatternBrushSettings(current.mode(), current.fromMask(), current.to(), current.palette(),
                        current.size(), density, current.mask()), false);
    }

    public ItemStack createDetailBrush(com.bayzyl.detail.DetailBrushSettings settings, String displayLabel) {
        try {
            settings = detailBrushSafety.requireValid(settings);
        } catch (IllegalArgumentException ex) {
            return null;
        }
        ItemStack item = new ItemStack(Material.BRUSH);
        return bindDetailBrush(item, settings, displayLabel, true) ? item : null;
    }

    public boolean bindDetailBrush(ItemStack item, com.bayzyl.detail.DetailBrushSettings settings, String displayLabel, boolean rebrand) {
        try {
            settings = detailBrushSafety.requireValid(settings);
        } catch (IllegalArgumentException ex) {
            return false;
        }
        if (item == null) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        if (rebrand) {
            String label = displayLabel == null || displayLabel.isBlank() ? settings.presetId() : displayLabel;
            meta.setDisplayName(ChatColor.LIGHT_PURPLE + "Bayzyl Detail: " + ChatColor.WHITE + label);
        }
        PersistentDataContainer data = meta.getPersistentDataContainer();
        data.set(toolKey, PersistentDataType.STRING, ToolType.DETAIL_BRUSH.name());
        data.set(detailPresetKey, PersistentDataType.STRING, settings.presetId());
        data.set(detailParamsKey, PersistentDataType.STRING, settings.parameters().serialize());
        data.set(detailModeKey, PersistentDataType.STRING, settings.mode().name());
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.WHITE + "Preset: " + settings.presetId());
        lore.add(ChatColor.WHITE + "Mode: " + settings.mode().name().toLowerCase(Locale.ROOT));
        for (var entry : settings.parameters().raw().entrySet()) {
            lore.add(ChatColor.GRAY + "  " + entry.getKey() + ": " + ChatColor.WHITE + entry.getValue());
        }
        lore.add(ChatColor.WHITE + "Hold use to paint on a surface.");
        lore.add(ChatColor.WHITE + "Hold break to extend matching detail.");
        meta.setLore(lore);
        item.setItemMeta(meta);
        return true;
    }

    public com.bayzyl.detail.DetailBrushSettings readDetailBrushSettings(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer data = meta.getPersistentDataContainer();
        String raw = data.get(toolKey, PersistentDataType.STRING);
        if (!ToolType.DETAIL_BRUSH.name().equals(raw)) {
            return null;
        }
        String presetId = data.get(detailPresetKey, PersistentDataType.STRING);
        if (presetId == null || presetId.isBlank()
                || presetId.length() > com.bayzyl.detail.DetailBrushSafety.PRESET_ID_MAX) {
            return null;
        }
        String paramsRaw = readOptionalStrictString(data, detailParamsKey);
        if (data.getKeys().contains(detailParamsKey) && paramsRaw == null) {
            return null;
        }
        com.bayzyl.detail.DetailBrushParameters params;
        try {
            params = detailBrushSafety.parseStoredParameters(paramsRaw);
        } catch (IllegalArgumentException ex) {
            return null;
        }
        com.bayzyl.detail.DetailBrushMode mode = com.bayzyl.detail.DetailBrushMode.STAMP;
        String modeRaw = readOptionalStrictString(data, detailModeKey);
        if (data.getKeys().contains(detailModeKey)) {
            if (modeRaw == null) {
                return null;
            }
            try {
                mode = com.bayzyl.detail.DetailBrushMode.valueOf(modeRaw.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                return null;
            }
        }
        com.bayzyl.detail.DetailBrushSettings settings =
                new com.bayzyl.detail.DetailBrushSettings(presetId, params, mode);
        try {
            return detailBrushSafety.requireValid(settings);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    public ItemStack createGenBrush(com.bayzyl.gen.GenBrushSettings settings) {
        if (com.bayzyl.gen.GenBrushSafety.assess(settings).hardRejected()) {
            return null;
        }
        ItemStack item = new ItemStack(Material.BRUSH);
        return bindGenBrush(item, settings, true) ? item : null;
    }

    public boolean bindGenBrush(ItemStack item, com.bayzyl.gen.GenBrushSettings settings, boolean rebrand) {
        if (settings == null || com.bayzyl.gen.GenBrushSafety.assess(settings).hardRejected()) {
            return false;
        }
        if (item == null) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        if (rebrand) {
            String label = settings.type().displayName();
            if (settings.type() == com.bayzyl.gen.GenBrushType.CAVE
                    && settings.caveSubtype() != com.bayzyl.gen.CaveSubtype.AUTO) {
                label += " (" + settings.caveSubtype().displayName() + ")";
            }
            meta.setDisplayName(ChatColor.DARK_GREEN + "Bayzyl Gen: " + ChatColor.WHITE + label);
        }
        PersistentDataContainer data = meta.getPersistentDataContainer();
        data.set(toolKey, PersistentDataType.STRING, ToolType.GEN_BRUSH.name());
        data.set(genTypeKey, PersistentDataType.STRING, settings.type().name());
        data.set(genCaveSubtypeKey, PersistentDataType.STRING, settings.caveSubtype().name());
        data.set(genRadiusKey, PersistentDataType.INTEGER, settings.radius());
        data.set(genParamsKey, PersistentDataType.STRING, settings.parameters().serialize());
        data.set(genAdaptKey, PersistentDataType.BYTE, (byte) (settings.adaptToEnvironment() ? 1 : 0));
        data.set(genSeedKey, PersistentDataType.LONG, settings.seed());
        if (settings.mask() != null && settings.mask().getRaw() != null
                && !settings.mask().getRaw().isBlank()) {
            data.set(maskKey, PersistentDataType.STRING, settings.mask().getRaw());
        } else {
            data.remove(maskKey);
        }
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.WHITE + "Type: " + settings.type().displayName());
        if (settings.type() == com.bayzyl.gen.GenBrushType.CAVE) {
            lore.add(ChatColor.WHITE + "Subtype: " + settings.caveSubtype().displayName());
        }
        lore.add(ChatColor.WHITE + "Radius: " + settings.radius());
        lore.add(ChatColor.WHITE + "Adapt: " + (settings.adaptToEnvironment() ? "on" : "off"));
        if (settings.mask() != null) {
            lore.add(ChatColor.WHITE + "Mask: " + settings.mask().summary());
        }
        for (var entry : settings.parameters().raw().entrySet()) {
            lore.add(ChatColor.GRAY + "  " + entry.getKey() + ": " + ChatColor.WHITE + entry.getValue());
        }
        lore.add(ChatColor.WHITE + "Right click to stamp.");
        meta.setLore(lore);
        item.setItemMeta(meta);
        return true;
    }

    public com.bayzyl.gen.GenBrushSettings readGenBrushSettings(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer data = meta.getPersistentDataContainer();
        String raw = data.get(toolKey, PersistentDataType.STRING);
        if (!ToolType.GEN_BRUSH.name().equals(raw)) {
            return null;
        }
        String typeRaw = data.get(genTypeKey, PersistentDataType.STRING);
        if (typeRaw == null) {
            return null;
        }
        com.bayzyl.gen.GenBrushType type;
        try {
            type = com.bayzyl.gen.GenBrushType.valueOf(typeRaw);
        } catch (IllegalArgumentException ex) {
            return null;
        }
        com.bayzyl.gen.CaveSubtype subtype = com.bayzyl.gen.CaveSubtype.AUTO;
        String subRaw = data.get(genCaveSubtypeKey, PersistentDataType.STRING);
        if (data.getKeys().contains(genCaveSubtypeKey)) {
            if (subRaw == null) {
                return null;
            }
            try {
                subtype = com.bayzyl.gen.CaveSubtype.valueOf(subRaw);
            } catch (IllegalArgumentException ex) {
                return null;
            }
        }
        Integer radius = data.get(genRadiusKey, PersistentDataType.INTEGER);
        if (data.getKeys().contains(genRadiusKey) && radius == null) {
            return null;
        }
        String paramsRaw = data.get(genParamsKey, PersistentDataType.STRING);
        if (data.getKeys().contains(genParamsKey) && paramsRaw == null) {
            return null;
        }
        com.bayzyl.gen.GenBrushParameters params;
        try {
            params = com.bayzyl.gen.GenBrushSafety.parseStoredParameters(paramsRaw);
        } catch (IllegalArgumentException ex) {
            return null;
        }
        Byte adaptRaw = data.get(genAdaptKey, PersistentDataType.BYTE);
        boolean adapt = true;
        if (data.getKeys().contains(genAdaptKey)) {
            if (adaptRaw == null || (adaptRaw != 0 && adaptRaw != 1)) {
                return null;
            }
            adapt = adaptRaw == 1;
        }
        Long seed = data.get(genSeedKey, PersistentDataType.LONG);
        if (data.getKeys().contains(genSeedKey) && seed == null) {
            return null;
        }
        String maskRaw = data.get(maskKey, PersistentDataType.STRING);
        if (data.getKeys().contains(maskKey)) {
            if (maskRaw == null || !com.bayzyl.gen.GenBrushSafety.isResolvableMask(maskRaw)) {
                return null;
            }
        }
        BlockMask mask = maskRaw == null ? null : BlockMask.parse(maskRaw);
        final com.bayzyl.gen.GenBrushSettings settings;
        try {
            settings = new com.bayzyl.gen.GenBrushSettings(type, subtype,
                    radius == null ? com.bayzyl.gen.GenBrushCommandParser.defaultRadius(type) : radius,
                    params, mask, adapt,
                    seed == null ? System.currentTimeMillis() : seed);
        } catch (IllegalArgumentException ex) {
            return null;
        }
        return com.bayzyl.gen.GenBrushSafety.assess(settings).hardRejected() ? null : settings;
    }

    public boolean updateGenBrushSize(ItemStack item, int radius) {
        com.bayzyl.gen.GenBrushSettings current = readGenBrushSettings(item);
        if (current == null || radius < 1) {
            return false;
        }
        final com.bayzyl.gen.GenBrushSettings resized;
        try {
            resized = new com.bayzyl.gen.GenBrushSettings(
                    current.type(), current.caveSubtype(), radius,
                    current.parameters(), current.mask(),
                    current.adaptToEnvironment(), current.seed());
        } catch (IllegalArgumentException ex) {
            return false;
        }
        return bindGenBrush(item, resized, false);
    }

    public boolean updateGenBrushMask(ItemStack item, BlockMask mask) {
        com.bayzyl.gen.GenBrushSettings current = readGenBrushSettings(item);
        if (current == null) {
            return false;
        }
        return bindGenBrush(item, new com.bayzyl.gen.GenBrushSettings(
                current.type(), current.caveSubtype(), current.radius(),
                current.parameters(), mask,
                current.adaptToEnvironment(), current.seed()), false);
    }

    public boolean unbindAnyBrush(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer data = meta.getPersistentDataContainer();
        String raw = data.get(toolKey, PersistentDataType.STRING);
        if (raw == null) {
            return false;
        }
        if (!ToolType.SHAPE_BRUSH.name().equals(raw)
                && !ToolType.CLIPBOARD_BRUSH.name().equals(raw)
                && !ToolType.PAINT_BRUSH.name().equals(raw)
                && !ToolType.PATTERN_BRUSH.name().equals(raw)
                && !ToolType.ERASER.name().equals(raw)
                && !ToolType.SMOOTH_BRUSH.name().equals(raw)
                && !ToolType.TERRAIN_BRUSH.name().equals(raw)
                && !ToolType.STRUCTURE_BRUSH.name().equals(raw)
                && !ToolType.DETAIL_BRUSH.name().equals(raw)
                && !ToolType.GEN_BRUSH.name().equals(raw)) {
            return false;
        }
        data.remove(toolKey);
        data.remove(radiusKey);
        data.remove(iterationsKey);
        data.remove(brushTypeKey);
        data.remove(surfaceKey);
        data.remove(selectionKey);
        data.remove(carveKey);
        data.remove(bedrockKey);
        data.remove(shapeTypeKey);
        data.remove(shapeMaterialKey);
        data.remove(shapeRadiusXKey);
        data.remove(shapeRadiusYKey);
        data.remove(shapeRadiusZKey);
        data.remove(shapeHeightKey);
        data.remove(shapeSizeKey);
        data.remove(shapeAnchorKey);
        data.remove(shapeConfirmKey);
        data.remove(structureIdKey);
        data.remove(structureAnchorKey);
        data.remove(structureConfirmKey);
        data.remove(paintDensityKey);
        data.remove(paintSizeKey);
        data.remove(patternModeKey);
        data.remove(patternFromKey);
        data.remove(patternToKey);
        data.remove(patternPaletteKey);
        data.remove(detailPresetKey);
        data.remove(detailParamsKey);
        data.remove(detailModeKey);
        data.remove(genTypeKey);
        data.remove(genCaveSubtypeKey);
        data.remove(genRadiusKey);
        data.remove(genParamsKey);
        data.remove(genAdaptKey);
        data.remove(genSeedKey);
        data.remove(maskKey);
        meta.setLore(null);
        meta.setDisplayName(null);
        item.setItemMeta(meta);
        return true;
    }

    private List<String> buildShapeBrushLore(ShapeBrushSettings settings) {
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.WHITE + "Shape: " + settings.type().displayName());
        lore.add(ChatColor.WHITE + "Blocks: " + settings.distribution());
        switch (settings.type()) {
            case SPHERE, HSPHERE -> lore.add(ChatColor.WHITE + "Radius: " + settings.radiusX()
                    + (settings.radiusY() != settings.radiusX() || settings.radiusZ() != settings.radiusX()
                    ? "," + settings.radiusY() + "," + settings.radiusZ() : ""));
            case CYL, HCYL -> {
                lore.add(ChatColor.WHITE + "Radius: " + settings.radiusX()
                        + (settings.radiusZ() != settings.radiusX() ? "," + settings.radiusZ() : ""));
                lore.add(ChatColor.WHITE + "Height: " + settings.height());
            }
            case PYRAMID, HPYRAMID -> lore.add(ChatColor.WHITE + "Size: " + settings.size());
        }
        lore.add(ChatColor.WHITE + "Anchor: " + settings.anchorMode().name().toLowerCase(Locale.ROOT));
        lore.add(ChatColor.WHITE + "Mask: " + (settings.mask() == null ? "any" : settings.mask().summary()));
        lore.add(ChatColor.WHITE + "Confirm: " + (settings.confirm() ? "yes" : "no"));
        lore.add(ChatColor.WHITE + "Right click to build");
        return lore;
    }

    private List<String> buildStructureBrushLore(StructureBrushSettings settings) {
        List<String> lore = new ArrayList<>();
        VanillaContentRegistry.Entry meta = VanillaContentRegistry.structureMeta(settings.structureId());
        lore.add(ChatColor.WHITE + "Structure: " + settings.structureId());
        lore.add(ChatColor.WHITE + "Type: " + meta.description());
        lore.add(ChatColor.WHITE + "Anchor: " + settings.anchorMode().name().toLowerCase(Locale.ROOT));
        lore.add(ChatColor.WHITE + "Confirm: " + (settings.confirm() ? "yes" : "no"));
        lore.add(ChatColor.WHITE + "Right click to place with safety rails");
        return lore;
    }

    private String normalizeStructureId(String raw) {
        if (raw == null) {
            return null;
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        if (normalized.isBlank()) {
            return null;
        }
        return normalized.contains(":") ? normalized : "minecraft:" + normalized;
    }

    private void putMaterial(PersistentDataContainer data, NamespacedKey key, Material material) {
        if (material == null) {
            data.remove(key);
            return;
        }
        data.set(key, PersistentDataType.STRING, material.name());
    }

    private String serializePalette(List<Material> palette) {
        if (palette == null || palette.isEmpty()) {
            return "";
        }
        return palette.stream()
                .filter(material -> material != null)
                .map(Material::name)
                .reduce((left, right) -> left + "," + right)
                .orElse("");
    }

    private List<Material> parsePaletteStrict(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        if (raw.length() > 4_096) {
            return null;
        }
        int entries = 1;
        for (int i = 0; i < raw.length(); i++) {
            if (raw.charAt(i) == ',' && ++entries > 64) {
                return null;
            }
        }
        String[] parts = raw.split(",", -1);
        List<Material> materials = new ArrayList<>();
        for (String part : parts) {
            if (part.isBlank() || part.length() > 128) {
                return null;
            }
            Material material = EditUtil.parseBlock(part);
            if (material == null) {
                return null;
            }
            materials.add(material);
        }
        return materials;
    }

    private String summarizePalette(List<Material> palette) {
        if (palette == null || palette.isEmpty()) {
            return "none";
        }
        List<String> names = palette.stream()
                .limit(4)
                .map(material -> material.name().toLowerCase(Locale.ROOT))
                .toList();
        return String.join(",", names) + (palette.size() > names.size() ? " +" + (palette.size() - names.size()) : "");
    }
}
