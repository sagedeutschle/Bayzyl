package com.bayzyl;

import com.bayzyl.redstone.AuditPosition;
import com.bayzyl.redstone.RedstoneAuditCommand;
import com.bayzyl.redstone.RedstoneAuditSnapshot;
import com.bayzyl.redstone.RedstoneAuditSnapshotFactory;
import com.bayzyl.redstone.RedstoneAuditSnapshotFactory.Capture;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;

import java.util.List;

/** Captures the caller's Bayzyl selection (its bounding box) for the redstone audit. Main thread only. */
final class RedstoneAuditSelectionCapture implements RedstoneAuditCommand.SelectionCapture {
    private final SelectionManager selections;

    RedstoneAuditSelectionCapture(SelectionManager selections) {
        this.selections = selections;
    }

    @Override
    public Capture capture(Player player) {
        Selection selection = selections.get(player.getUniqueId());
        if (selection == null || !selection.isComplete()) {
            return Capture.refused("Select the machine first (wand, or /pos1 and /pos2), then run /redstoneaudit.");
        }
        World world = selection.getPos1().getWorld();
        AuditPosition min = new AuditPosition(selection.getMinX(), selection.getMinY(), selection.getMinZ());
        AuditPosition max = new AuditPosition(selection.getMaxX(), selection.getMaxY(), selection.getMaxZ());
        int halo = RedstoneAuditSnapshot.HALO;
        BoundingBox area = new BoundingBox(min.x() - halo, min.y() - halo, min.z() - halo,
                max.x() + halo + 1, max.y() + halo + 1, max.z() + halo + 1);
        RedstoneAuditSnapshotFactory factory = new RedstoneAuditSnapshotFactory(world::getBlockData,
                () -> itemFrames(world, area), world.getMinHeight(), world.getMaxHeight() - 1);
        return factory.capture(min, max);
    }

    private static List<AuditPosition> itemFrames(World world, BoundingBox area) {
        return world.getNearbyEntities(area).stream()
                .filter(entity -> entity instanceof ItemFrame)
                .map(entity -> {
                    Location location = entity.getLocation();
                    return new AuditPosition(location.getBlockX(), location.getBlockY(), location.getBlockZ());
                })
                .toList();
    }
}
