package com.bayzyl.shape;

import com.bayzyl.CylinderRequest;
import com.bayzyl.PyramidRequest;
import com.bayzyl.SphereRequest;
import com.bayzyl.safety.WorkEstimate;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.function.pattern.BlockPattern;
import com.sk89q.worldedit.math.BlockVector3;
import org.bukkit.Location;
import org.bukkit.entity.Player;

public final class WorldEditShapeAdapter implements ShapeAdapter {
    private final NativeShapeAdapter fallback;

    public WorldEditShapeAdapter(NativeShapeAdapter fallback) {
        this.fallback = fallback;
    }

    @Override
    public String getName() {
        return "worldedit";
    }

    @Override
    public WorkEstimate estimateSphere(SphereRequest request) {
        return fallback.estimateSphere(request);
    }

    @Override
    public WorkEstimate estimateCylinder(CylinderRequest request) {
        return fallback.estimateCylinder(request);
    }

    @Override
    public WorkEstimate estimatePyramid(PyramidRequest request) {
        return fallback.estimatePyramid(request);
    }

    @Override
    public ShapeBounds sphereBounds(Location anchor, SphereRequest request) {
        requireWithinHardLimit(estimateSphere(request));
        return fallback.sphereBounds(anchor, request);
    }

    @Override
    public ShapeBounds cylinderBounds(Location anchor, CylinderRequest request) {
        requireWithinHardLimit(estimateCylinder(request));
        return fallback.cylinderBounds(anchor, request);
    }

    @Override
    public ShapeBounds pyramidBounds(Location anchor, PyramidRequest request) {
        requireWithinHardLimit(estimatePyramid(request));
        return fallback.pyramidBounds(anchor, request);
    }

    @Override
    public int createSphere(Player player, Location anchor, SphereRequest request) throws Exception {
        requireWithinHardLimit(estimateSphere(request));
        if (!request.distribution().isSingleMaterial()) {
            return fallback.createSphere(player, anchor, request);
        }
        try (EditSession editSession = WorldEdit.getInstance()
                .newEditSessionBuilder()
                .world(BukkitAdapter.adapt(player.getWorld()))
                .actor(BukkitAdapter.adapt(player))
                .maxBlocks(-1)
                .build()) {
            int changed = editSession.makeSphere(
                    toVector(anchor),
                    new BlockPattern(BukkitAdapter.adapt(request.distribution().getSoleMaterial().createBlockData())),
                    request.radiusX(),
                    request.radiusY(),
                    request.radiusZ(),
                    !request.hollow()
            );
            editSession.flushSession();
            return changed;
        }
    }

    @Override
    public int createCylinder(Player player, Location anchor, CylinderRequest request) throws Exception {
        requireWithinHardLimit(estimateCylinder(request));
        if (!request.distribution().isSingleMaterial()) {
            return fallback.createCylinder(player, anchor, request);
        }
        try (EditSession editSession = WorldEdit.getInstance()
                .newEditSessionBuilder()
                .world(BukkitAdapter.adapt(player.getWorld()))
                .actor(BukkitAdapter.adapt(player))
                .maxBlocks(-1)
                .build()) {
            int changed = editSession.makeCylinder(
                    toVector(anchor),
                    new BlockPattern(BukkitAdapter.adapt(request.distribution().getSoleMaterial().createBlockData())),
                    request.radiusX(),
                    request.radiusZ(),
                    request.height(),
                    !request.hollow()
            );
            editSession.flushSession();
            return changed;
        }
    }

    @Override
    public int createPyramid(Player player, Location anchor, PyramidRequest request) throws Exception {
        requireWithinHardLimit(estimatePyramid(request));
        if (!request.distribution().isSingleMaterial()) {
            return fallback.createPyramid(player, anchor, request);
        }
        try (EditSession editSession = WorldEdit.getInstance()
                .newEditSessionBuilder()
                .world(BukkitAdapter.adapt(player.getWorld()))
                .actor(BukkitAdapter.adapt(player))
                .maxBlocks(-1)
                .build()) {
            int changed = editSession.makePyramid(
                    toVector(anchor),
                    new BlockPattern(BukkitAdapter.adapt(request.distribution().getSoleMaterial().createBlockData())),
                    request.size(),
                    !request.hollow()
            );
            editSession.flushSession();
            return changed;
        }
    }

    private BlockVector3 toVector(Location location) {
        return BlockVector3.at(location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    private void requireWithinHardLimit(WorkEstimate estimate) {
        if (estimate.hardRejected()) {
            throw new IllegalArgumentException(estimate.reason());
        }
    }
}
