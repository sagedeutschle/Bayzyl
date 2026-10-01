package com.bayzyl;

import org.bukkit.Art;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Rotation;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.EntitySnapshot;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Hanging;
import org.bukkit.entity.GlowItemFrame;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Painting;
import org.bukkit.inventory.ItemStack;

public final class ClipboardEntity {
    private final boolean glow;
    private final double supportOffsetX;
    private final double supportOffsetY;
    private final double supportOffsetZ;
    private final BlockFace facing;
    private final Rotation rotation;
    private final ItemStack item;
    private final boolean visible;
    private final boolean fixed;
    private final float itemDropChance;
    private final Art paintingArt;
    private final EntitySnapshot snapshot;
    private final float yaw;
    private final float pitch;

    public ClipboardEntity(boolean glow,
                           double supportOffsetX,
                           double supportOffsetY,
                           double supportOffsetZ,
                           BlockFace facing,
                           Rotation rotation,
                           ItemStack item,
                           boolean visible,
                           boolean fixed,
                           float itemDropChance,
                           Art paintingArt,
                           EntitySnapshot snapshot,
                           float yaw,
                           float pitch) {
        this.glow = glow;
        this.supportOffsetX = supportOffsetX;
        this.supportOffsetY = supportOffsetY;
        this.supportOffsetZ = supportOffsetZ;
        this.facing = facing;
        this.rotation = rotation;
        this.item = item;
        this.visible = visible;
        this.fixed = fixed;
        this.itemDropChance = itemDropChance;
        this.paintingArt = paintingArt;
        this.snapshot = snapshot;
        this.yaw = yaw;
        this.pitch = pitch;
    }

    public static ClipboardEntity from(ItemFrame frame, Location origin) {
        Location location = frame.getLocation();
        int supportX = location.getBlockX() - frame.getFacing().getModX();
        int supportY = location.getBlockY() - frame.getFacing().getModY();
        int supportZ = location.getBlockZ() - frame.getFacing().getModZ();
        return new ClipboardEntity(
                frame instanceof GlowItemFrame,
                supportX - origin.getBlockX(),
                supportY - origin.getBlockY(),
                supportZ - origin.getBlockZ(),
                frame.getFacing(),
                frame.getRotation(),
                frame.getItem() == null ? null : frame.getItem().clone(),
                frame.isVisible(),
                frame.isFixed(),
                frame.getItemDropChance(),
                null,
                frame.createSnapshot(),
                location.getYaw(),
                location.getPitch()
        );
    }

    public static ClipboardEntity from(Hanging hanging, Location origin) {
        if (hanging instanceof ItemFrame frame) {
            return from(frame, origin);
        }
        Location location = hanging.getLocation();
        BlockFace attachedFace = hanging.getAttachedFace();
        BlockFace facing = hanging.getFacing();
        Location support = location.getBlock()
                .getRelative(attachedFace.getModX(), attachedFace.getModY(), attachedFace.getModZ())
                .getLocation();
        return new ClipboardEntity(
                hanging instanceof GlowItemFrame,
                support.getBlockX() - origin.getBlockX(),
                support.getBlockY() - origin.getBlockY(),
                support.getBlockZ() - origin.getBlockZ(),
                facing,
                null,
                null,
                true,
                false,
                1.0f,
                hanging instanceof Painting painting ? painting.getArt() : null,
                hanging.createSnapshot(),
                location.getYaw(),
                location.getPitch()
        );
    }

    public static ClipboardEntity from(Entity entity, Location origin) {
        if (entity instanceof ItemFrame frame) {
            return from(frame, origin);
        }
        if (entity instanceof Hanging hanging) {
            return from(hanging, origin);
        }
        Location location = entity.getLocation();
        return new ClipboardEntity(
                false,
                location.getX() - origin.getX(),
                location.getY() - origin.getY(),
                location.getZ() - origin.getZ(),
                null,
                null,
                null,
                true,
                false,
                1.0f,
                null,
                entity.createSnapshot(),
                location.getYaw(),
                location.getPitch()
        );
    }

    public ClipboardEntity rotateY(int rotationDegrees) {
        double[] rotated = ClipboardTransforms.rotateVectorY(supportOffsetX, supportOffsetZ, rotationDegrees);
        if (snapshot != null && facing == null) {
            return new ClipboardEntity(
                    glow,
                    rotated[0],
                    supportOffsetY,
                    rotated[1],
                    null,
                    null,
                    item == null ? null : item.clone(),
                    visible,
                    fixed,
                    itemDropChance,
                    paintingArt,
                    snapshot,
                    normalizeYaw(yaw + rotationDegrees),
                    pitch
            );
        }
        // Filled maps already inherit the wall rotation visually; applying frame rotation again over-rotates them.
        Rotation rotatedFrameRotation = item != null && item.getType() == Material.FILLED_MAP
                ? rotation
                : ClipboardTransforms.rotateFrameRotation(rotation, rotationDegrees);
        return new ClipboardEntity(
                glow,
                rotated[0],
                supportOffsetY,
                rotated[1],
                ClipboardTransforms.rotateFaceY(facing, rotationDegrees),
                rotatedFrameRotation,
                item == null ? null : item.clone(),
                visible,
                fixed,
                itemDropChance,
                paintingArt,
                snapshot,
                yaw,
                pitch
        );
    }

    public ClipboardEntity flip(String axis) {
        double x = supportOffsetX;
        double y = supportOffsetY;
        double z = supportOffsetZ;
        if (axis.equals("x")) {
            x = -x;
        } else if (axis.equals("y")) {
            y = -y;
        } else {
            z = -z;
        }
        if (snapshot != null && facing == null) {
            return new ClipboardEntity(
                    glow,
                    x,
                    y,
                    z,
                    null,
                    null,
                    item == null ? null : item.clone(),
                    visible,
                    fixed,
                    itemDropChance,
                    paintingArt,
                    snapshot,
                    flipYaw(axis, yaw),
                    pitch
            );
        }
        return new ClipboardEntity(
                glow,
                x,
                y,
                z,
                ClipboardTransforms.flipFace(facing, axis),
                ClipboardTransforms.flipFrameRotation(rotation, axis),
                item == null ? null : item.clone(),
                visible,
                fixed,
                itemDropChance,
                paintingArt,
                snapshot,
                yaw,
                pitch
        );
    }

    public ClipboardEntity translate(int dx, int dy, int dz) {
        return new ClipboardEntity(
                glow,
                supportOffsetX + dx,
                supportOffsetY + dy,
                supportOffsetZ + dz,
                facing,
                rotation,
                item == null ? null : item.clone(),
                visible,
                fixed,
                itemDropChance,
                paintingArt,
                snapshot,
                yaw,
                pitch
        );
    }

    public Entity spawn(World world, Location origin) {
        Location spawn = origin.clone().add(supportOffsetX, supportOffsetY, supportOffsetZ);
        if (facing != null) {
            spawn.add(0.5 + facing.getModX(),
                    0.5 + facing.getModY(),
                    0.5 + facing.getModZ());
        }

        if (snapshot != null) {
            Entity placed = snapshot.createEntity(spawn);
            if (placed instanceof ItemFrame frame) {
                apply(frame);
            } else if (placed instanceof Hanging hanging && facing != null) {
                hanging.setFacingDirection(facing, true);
            }
            if (placed instanceof Painting painting && paintingArt != null) {
                painting.setArt(paintingArt, true);
            }
            if (!(placed instanceof Hanging) && (yaw != 0.0f || pitch != 0.0f)) {
                placed.setRotation(yaw, pitch);
            }
            return placed;
        }

        if (facing != null) {
            if (glow) {
                return world.spawn(spawn, GlowItemFrame.class, this::apply);
            }
            return world.spawn(spawn, ItemFrame.class, this::apply);
        }

        throw new IllegalStateException("Clipboard entity snapshot is unavailable.");
    }

    private void apply(ItemFrame frame) {
        frame.setFacingDirection(facing, true);
        frame.setItem(item == null ? new ItemStack(Material.AIR) : item.clone(), false);
        frame.setRotation(rotation);
        frame.setVisible(visible);
        frame.setFixed(fixed);
        frame.setItemDropChance(itemDropChance);
    }

    public boolean isGlow() {
        return glow;
    }

    public double getSupportOffsetX() {
        return supportOffsetX;
    }

    public double getSupportOffsetY() {
        return supportOffsetY;
    }

    public double getSupportOffsetZ() {
        return supportOffsetZ;
    }

    public BlockFace getFacing() {
        return facing;
    }

    public Rotation getRotation() {
        return rotation;
    }

    public ItemStack getItem() {
        return item == null ? null : item.clone();
    }

    public boolean isVisible() {
        return visible;
    }

    public boolean isFixed() {
        return fixed;
    }

    public float getItemDropChance() {
        return itemDropChance;
    }

    public Art getPaintingArt() {
        return paintingArt;
    }

    public float getYaw() {
        return yaw;
    }
    
    public float getPitch() {
        return pitch;
    }
    
    public String getSnapshotData() {
        return snapshot == null ? null : snapshot.getAsString();
    }

    private float normalizeYaw(float value) {
        float yawValue = value % 360.0f;
        return yawValue < 0.0f ? yawValue + 360.0f : yawValue;
    }

    private float flipYaw(String axis, float value) {
        if (axis.equals("y")) {
            return normalizeYaw(value);
        }
        if (axis.equals("x")) {
            return normalizeYaw(180.0f - value);
        }
        return normalizeYaw(-value);
    }
}
