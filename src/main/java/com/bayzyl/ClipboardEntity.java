package com.bayzyl;

import org.bukkit.Art;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Rotation;
import org.bukkit.World;
import org.bukkit.block.Block;
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
        Block anchor = location.getBlock();
        if (hanging instanceof Painting painting && painting.getArt() != null) {
            // A painting's location is the centre of its whole canvas, not the block it is anchored on.
            int[] cell = paintingAnchor(location, facing, painting.getArt().getBlockWidth(), painting.getArt().getBlockHeight());
            anchor = location.getWorld().getBlockAt(cell[0], cell[1], cell[2]);
        }
        Location support = anchor
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

    /**
     * The block a painting is anchored on, recovered from its entity location. Minecraft places an
     * even-sized canvas half a block off its anchor block along the width and the height, so the
     * block containing the location is one block off for such paintings.
     */
    static int[] paintingAnchor(Location location, BlockFace facing, int widthBlocks, int heightBlocks) {
        int ccwX = facing.getModZ();
        int ccwZ = -facing.getModX();
        double widthOffset = widthBlocks % 2 == 0 ? 0.5 : 0.0;
        double heightOffset = heightBlocks % 2 == 0 ? 0.5 : 0.0;
        return new int[]{
                (int) Math.round(location.getX() + facing.getModX() * 0.46875 - widthOffset * ccwX - 0.5),
                (int) Math.round(location.getY() - heightOffset - 0.5),
                (int) Math.round(location.getZ() + facing.getModZ() * 0.46875 - widthOffset * ccwZ - 0.5)
        };
    }

    /** Sum of the first and last canvas cell indexes of a painting side, measured from its anchor block. */
    private static int canvasSpan(int blocks) {
        int first = -((blocks - 1) / 2);
        return first + (first + blocks - 1);
    }

    public ClipboardEntity rotateY(int requestedDegrees) {
        // Only quarter turns move blocks, so anything else must not turn the entities either.
        int rotationDegrees = requestedDegrees % 90 == 0 ? requestedDegrees : 0;
        double[] rotated = ClipboardTransforms.rotateVectorY(supportOffsetX, supportOffsetZ, rotationDegrees);
        if (snapshot != null && facing == null) {
            // Free entities sit at a point inside the block grid, whose cells turn about the centre of the
            // origin block, so turn about that centre rather than the block corner.
            rotated = ClipboardTransforms.rotateVectorY(supportOffsetX - 0.5, supportOffsetZ - 0.5, rotationDegrees);
            rotated[0] += 0.5;
            rotated[1] += 0.5;
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
        if (snapshot != null && facing == null) {
            // Mirror the block cells the way flip() maps them: x and z about the centre of the origin block,
            // y keeping the position inside the cell.
            if (axis.equals("x")) {
                x = 1.0 - x;
            } else if (axis.equals("y")) {
                y = -Math.floor(y) + (y - Math.floor(y));
            } else {
                z = 1.0 - z;
            }
        } else if (axis.equals("x")) {
            x = -x;
        } else if (axis.equals("y")) {
            y = -y;
        } else {
            z = -z;
        }
        if (paintingArt != null && facing != null) {
            // An even-sized canvas is anchored off-centre, so the mirrored canvas needs its anchor shifted to
            // keep covering the mirrored blocks.
            int spanWidth = canvasSpan(paintingArt.getBlockWidth());
            int ccwX = facing.getModZ();
            int ccwZ = -facing.getModX();
            if (axis.equals("y")) {
                y -= canvasSpan(paintingArt.getBlockHeight());
            } else if (axis.equals("x")) {
                z += ccwZ * spanWidth;
                x -= ccwX * spanWidth;
            } else {
                x += ccwX * spanWidth;
                z -= ccwZ * spanWidth;
            }
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
        // Yaw 0 faces +z and 90 faces -x, so mirroring x negates the yaw and mirroring z reflects it about 180.
        if (axis.equals("x")) {
            return normalizeYaw(-value);
        }
        return normalizeYaw(180.0f - value);
    }
}
