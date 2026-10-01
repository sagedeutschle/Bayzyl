package com.bayzyl;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachmentInfo;
import com.bayzyl.safety.BrushSafety;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class EraserService {
    public static final int DEFAULT_MAX_RADIUS = 10;
    public static final int CONFIRM_RADIUS = 12;

    private final SelectionManager selectionManager;

    public EraserService(SelectionManager selectionManager) {
        this.selectionManager = selectionManager;
    }

    public int getMaxRadius(Player player) {
        if (player.hasPermission("bayzyl.eraser.unlimited")) {
            return Integer.MAX_VALUE;
        }

        int max = DEFAULT_MAX_RADIUS;
        for (PermissionAttachmentInfo info : player.getEffectivePermissions()) {
            String perm = info.getPermission().toLowerCase(Locale.ROOT);
            if (!perm.startsWith("bayzyl.eraser.max.")) {
                continue;
            }
            String value = perm.substring("bayzyl.eraser.max.".length());
            try {
                int parsed = Integer.parseInt(value);
                if (parsed > max) {
                    max = parsed;
                }
            } catch (NumberFormatException ignored) {
            }
        }
        return max;
    }

    public EraserResult apply(Player player, Location center, EraserSettings settings) {
        if (!BrushSafety.isValidEraser(settings)) {
            return EraserResult.empty();
        }

        int radius = settings.getRadius();
        if (player == null) {
            return EraserResult.empty();
        }

        int maxRadius = getMaxRadius(player);
        if (radius > maxRadius) {
            ChatOutput.send(player, ChatColor.RED + "Eraser radius exceeds your limit (" + maxRadius + ").");
            return EraserResult.empty();
        }

        Selection selection = selectionManager.get(player.getUniqueId());
        if (settings.isSelectionOnly() && (selection == null || !selection.isComplete())) {
            ChatOutput.send(player, ChatColor.RED + "Selection-only erase requires a complete selection.");
            return EraserResult.empty();
        }
        if (center == null || center.getWorld() == null) {
            return EraserResult.empty();
        }
        World world = center.getWorld();
        int radiusSq = radius * radius;
        List<BlockChange> changes = new ArrayList<>();
        List<EntityChange> entityChanges = new ArrayList<>();

        // Bukkit supplies the nearby-entity candidate collection. The block work cap is
        // enforced before asking for it; the filtered history payload remains bounded by
        // the server's live entity cardinality rather than by the brush cube estimate.
        List<Entity> entities = EditUtil.collectNearbyEntities(center, radius,
                settings.isSelectionOnly() ? selection : null);
        for (Entity entity : entities) {
            Location origin = entity.getLocation().getBlock().getLocation();
            ClipboardEntity clipboardEntity = ClipboardEntity.from(entity, origin);
            entityChanges.add(EntityChange.deleted(clipboardEntity, origin));
            if (entity instanceof ItemFrame frame) {
                frame.setItem(new org.bukkit.inventory.ItemStack(Material.AIR), false);
                frame.setItemDropChance(0.0f);
            }
            entity.remove();
        }

        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    if ((x * x + y * y + z * z) > radiusSq) {
                        continue;
                    }

                    Block block = world.getBlockAt(center.getBlockX() + x, center.getBlockY() + y, center.getBlockZ() + z);
                    Material material = block.getType();
                    if (material.isAir()) {
                        continue;
                    }
                    if (material == Material.BEDROCK && !settings.isEditBedrock()) {
                        continue;
                    }

                    if (settings.isSelectionOnly() && (selection == null || !selection.contains(block.getLocation()))) {
                        continue;
                    }

                    if (settings.getMask() != null && !settings.getMask().matches(material)) {
                        continue;
                    }

                    if (settings.isSurfaceOnly() && !isSurface(block)) {
                        continue;
                    }

                    if (settings.isCarveOnly() && !material.isSolid()) {
                        continue;
                    }

                    BlockData before = block.getBlockData().clone();
                    block.setType(Material.AIR, false);
                    BlockData after = block.getBlockData().clone();
                    if (!before.matches(after)) {
                        changes.add(new BlockChange(block.getLocation(), before, after));
                    }
                }
            }
        }

        ChatOutput.send(player, ChatColor.WHITE + "Eraser removed " + changes.size() + " blocks"
                + (entityChanges.isEmpty() ? "" : " and " + entityChanges.size() + " entities")
                + ".");
        return new EraserResult(changes, entityChanges);
    }

    private boolean isSurface(Block block) {
        return isAir(block, 1, 0, 0)
                || isAir(block, -1, 0, 0)
                || isAir(block, 0, 1, 0)
                || isAir(block, 0, -1, 0)
                || isAir(block, 0, 0, 1)
                || isAir(block, 0, 0, -1);
    }

    private boolean isAir(Block block, int dx, int dy, int dz) {
        return block.getRelative(dx, dy, dz).getType().isAir();
    }

    public record EraserResult(List<BlockChange> blockChanges, List<EntityChange> entityChanges) {
        public static EraserResult empty() {
            return new EraserResult(List.of(), List.of());
        }
    }
}
