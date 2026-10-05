package com.bayzyl;

import org.bukkit.Axis;
import org.bukkit.Rotation;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.FaceAttachable;
import org.bukkit.block.data.MultipleFacing;
import org.bukkit.block.data.Orientable;
import org.bukkit.block.data.Rail;
import org.bukkit.block.data.Rotatable;
import org.bukkit.block.data.type.Chest;
import org.bukkit.block.data.type.Door;
import org.bukkit.block.data.type.RedstoneWire;
import org.bukkit.block.data.type.Slab;
import org.bukkit.block.data.type.Stairs;
import org.bukkit.block.data.type.TrapDoor;
import org.bukkit.block.data.type.Wall;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

public final class ClipboardTransforms {
    private ClipboardTransforms() {
    }

    public static Clipboard flip(Clipboard clipboard, String axis) {
        if (clipboard == null) {
            return null;
        }

        String normalizedAxis = normalizeAxis(axis);
        if (normalizedAxis == null) {
            throw new IllegalArgumentException("Unknown flip axis.");
        }

        int startX = clipboard.getMinOffsetX();
        int endX = clipboard.getMinOffsetX() + clipboard.getSizeX() - 1;
        int startY = clipboard.getMinOffsetY();
        int endY = clipboard.getMinOffsetY() + clipboard.getSizeY() - 1;
        int startZ = clipboard.getMinOffsetZ();
        int endZ = clipboard.getMinOffsetZ() + clipboard.getSizeZ() - 1;

        int newMinOffsetX = normalizedAxis.equals("x") ? -endX : startX;
        int newMinOffsetY = normalizedAxis.equals("y") ? -endY : startY;
        int newMinOffsetZ = normalizedAxis.equals("z") ? -endZ : startZ;

        BlockData[] flippedData = new BlockData[clipboard.getSizeX() * clipboard.getSizeY() * clipboard.getSizeZ()];
        BlockState[] flippedStates = new BlockState[clipboard.getSizeX() * clipboard.getSizeY() * clipboard.getSizeZ()];

        for (int y = 0; y < clipboard.getSizeY(); y++) {
            for (int z = 0; z < clipboard.getSizeZ(); z++) {
                for (int x = 0; x < clipboard.getSizeX(); x++) {
                    BlockData data = clipboard.get(x, y, z);
                    int vectorX = clipboard.getMinOffsetX() + x;
                    int vectorY = clipboard.getMinOffsetY() + y;
                    int vectorZ = clipboard.getMinOffsetZ() + z;

                    if (normalizedAxis.equals("x")) {
                        vectorX = -vectorX;
                    } else if (normalizedAxis.equals("y")) {
                        vectorY = -vectorY;
                    } else {
                        vectorZ = -vectorZ;
                    }

                    int newX = vectorX - newMinOffsetX;
                    int newY = vectorY - newMinOffsetY;
                    int newZ = vectorZ - newMinOffsetZ;
                    int idx = (newY * clipboard.getSizeZ() + newZ) * clipboard.getSizeX() + newX;
                    flippedData[idx] = data == null ? null : transformFlip(data.clone(), normalizedAxis);
                    BlockState state = clipboard.getState(x, y, z);
                    flippedStates[idx] = state == null ? null : state.copy();
                }
            }
        }

        return new Clipboard(
                clipboard.getSizeX(),
                clipboard.getSizeY(),
                clipboard.getSizeZ(),
                flippedData,
                flippedStates,
                clipboard.getEntities().stream().map(entity -> entity.flip(normalizedAxis)).collect(Collectors.toList()),
                clipboard.getOrigin(),
                newMinOffsetX,
                newMinOffsetY,
                newMinOffsetZ
        );
    }

    public static Clipboard rotateY(Clipboard clipboard, int degrees) {
        if (clipboard == null) {
            return null;
        }

        int rotation = normalizeRotation(degrees);
        if (rotation == 0) {
            return clipboard;
        }
        if (rotation != 90 && rotation != 180 && rotation != 270) {
            throw new IllegalArgumentException("Rotation must be a multiple of 90.");
        }

        int startX = clipboard.getMinOffsetX();
        int endX = clipboard.getMinOffsetX() + clipboard.getSizeX() - 1;
        int startZ = clipboard.getMinOffsetZ();
        int endZ = clipboard.getMinOffsetZ() + clipboard.getSizeZ() - 1;

        int minOffsetX = Integer.MAX_VALUE;
        int minOffsetZ = Integer.MAX_VALUE;
        int maxOffsetX = Integer.MIN_VALUE;
        int maxOffsetZ = Integer.MIN_VALUE;

        int[][] corners = new int[][]{
                {startX, startZ},
                {startX, endZ},
                {endX, startZ},
                {endX, endZ}
        };

        for (int[] corner : corners) {
            int[] rotated = rotateY(corner[0], corner[1], rotation);
            minOffsetX = Math.min(minOffsetX, rotated[0]);
            maxOffsetX = Math.max(maxOffsetX, rotated[0]);
            minOffsetZ = Math.min(minOffsetZ, rotated[1]);
            maxOffsetZ = Math.max(maxOffsetZ, rotated[1]);
        }

        int newSizeX = maxOffsetX - minOffsetX + 1;
        int newSizeY = clipboard.getSizeY();
        int newSizeZ = maxOffsetZ - minOffsetZ + 1;
        BlockData[] rotatedData = new BlockData[newSizeX * newSizeY * newSizeZ];
        BlockState[] rotatedStates = new BlockState[newSizeX * newSizeY * newSizeZ];

        for (int y = 0; y < clipboard.getSizeY(); y++) {
            for (int z = 0; z < clipboard.getSizeZ(); z++) {
                for (int x = 0; x < clipboard.getSizeX(); x++) {
                    BlockData data = clipboard.get(x, y, z);
                    int vectorX = clipboard.getMinOffsetX() + x;
                    int vectorZ = clipboard.getMinOffsetZ() + z;
                    int[] rotated = rotateY(vectorX, vectorZ, rotation);

                    int newX = rotated[0] - minOffsetX;
                    int newZ = rotated[1] - minOffsetZ;
                    int idx = (y * newSizeZ + newZ) * newSizeX + newX;
                    rotatedData[idx] = data == null ? null : transformRotate(data.clone(), rotation);
                    BlockState state = clipboard.getState(x, y, z);
                    rotatedStates[idx] = state == null ? null : state.copy();
                }
            }
        }

        return new Clipboard(
                newSizeX,
                newSizeY,
                newSizeZ,
                rotatedData,
                rotatedStates,
                clipboard.getEntities().stream().map(entity -> entity.rotateY(rotation)).collect(Collectors.toList()),
                clipboard.getOrigin(),
                minOffsetX,
                clipboard.getMinOffsetY(),
                minOffsetZ
        );
    }

    public static double[] rotateVectorY(double x, double z, int rotation) {
        int normalized = normalizeRotation(rotation);
        if (normalized == 90) {
            return new double[]{-z, x};
        }
        if (normalized == 180) {
            return new double[]{-x, -z};
        }
        if (normalized == 270) {
            return new double[]{z, -x};
        }
        return new double[]{x, z};
    }

    public static int[] rotateVectorY(int x, int z, int rotation) {
        int normalized = normalizeRotation(rotation);
        if (normalized == 90) {
            return new int[]{-z, x};
        }
        if (normalized == 180) {
            return new int[]{-x, -z};
        }
        if (normalized == 270) {
            return new int[]{z, -x};
        }
        return new int[]{x, z};
    }

    public static BlockData applyRotation(BlockData data, int rotation) {
        if (data == null) {
            return null;
        }
        int normalized = normalizeRotation(rotation);
        if (normalized == 0) {
            return data;
        }
        return transformRotate(data, normalized);
    }

    public static BlockData applyFlip(BlockData data, String axis) {
        if (data == null) {
            return null;
        }
        String normalizedAxis = normalizeAxis(axis);
        if (normalizedAxis == null) {
            throw new IllegalArgumentException("Unknown flip axis.");
        }
        return transformFlip(data, normalizedAxis);
    }

    private static int normalizeRotation(int degrees) {
        return ((degrees % 360) + 360) % 360;
    }

    private static String normalizeAxis(String axis) {
        if (axis == null) {
            return null;
        }
        switch (axis.toLowerCase()) {
            case "x":
            case "east-west":
            case "ew":
            case "left-right":
            case "lr":
                return "x";
            case "y":
            case "up-down":
            case "ud":
            case "vertical":
                return "y";
            case "z":
            case "north-south":
            case "ns":
            case "front-back":
            case "fb":
                return "z";
            default:
                return null;
        }
    }

    private static int[] rotateY(int x, int z, int rotation) {
        if (rotation == 90) {
            return new int[]{-z, x};
        }
        if (rotation == 180) {
            return new int[]{-x, -z};
        }
        if (rotation == 270) {
            return new int[]{z, -x};
        }
        return new int[]{x, z};
    }

    private static BlockData transformRotate(BlockData data, int rotation) {
        if (data instanceof Directional directional) {
            BlockFace rotated = rotateFaceY(directional.getFacing(), rotation);
            if (rotated != null && directional.getFaces().contains(rotated)) {
                directional.setFacing(rotated);
            }
        }
        if (data instanceof Rotatable rotatable) {
            BlockFace rotated = rotateFaceY(rotatable.getRotation(), rotation);
            if (rotated != null) {
                rotatable.setRotation(rotated);
            }
        }
        if (data instanceof Orientable orientable) {
            Axis rotated = rotateAxisY(orientable.getAxis(), rotation);
            if (orientable.getAxes().contains(rotated)) {
                orientable.setAxis(rotated);
            }
        }
        if (data instanceof MultipleFacing multipleFacing) {
            rotateMultipleFacingY(multipleFacing, rotation);
        }
        if (data instanceof Wall wall) {
            rotateWallY(wall, rotation);
        }
        if (data instanceof Rail rail) {
            rail.setShape(rotateRailShape(rail.getShape(), rotation));
        }
        if (data instanceof RedstoneWire wire) {
            remapRedstoneWire(wire, face -> rotateFaceY(face, rotation));
        }
        // Stairs shape (inner/outer, left/right) is relative to the facing, so a rotation keeps it.
        return data;
    }

    private static BlockData transformFlip(BlockData data, String axis) {
        if (data instanceof Directional directional) {
            BlockFace flipped = flipFace(directional.getFacing(), axis);
            if (flipped != null && directional.getFaces().contains(flipped)) {
                directional.setFacing(flipped);
            }
        }
        if (data instanceof Rotatable rotatable) {
            BlockFace flipped = flipFace(rotatable.getRotation(), axis);
            if (flipped != null) {
                rotatable.setRotation(flipped);
            }
        }
        if (data instanceof Orientable orientable) {
            Axis flipped = flipAxis(orientable.getAxis(), axis);
            if (orientable.getAxes().contains(flipped)) {
                orientable.setAxis(flipped);
            }
        }
        if (data instanceof MultipleFacing multipleFacing) {
            flipMultipleFacing(multipleFacing, axis);
        }
        if (data instanceof Wall wall) {
            flipWall(wall, axis);
        }
        if (data instanceof Rail rail) {
            rail.setShape(flipRailShape(rail.getShape(), axis));
        }
        if (data instanceof Stairs stairs) {
            stairs.setShape(flipStairShape(stairs.getShape(), axis));
        }
        if (data instanceof RedstoneWire wire) {
            remapRedstoneWire(wire, face -> flipFace(face, axis));
        }
        if (axis.equals("y")) {
            flipVertically(data);
        } else {
            flipHandedness(data);
        }
        return data;
    }

    /** A vertical mirror turns bottom halves, slabs and floor attachments upside down. */
    private static void flipVertically(BlockData data) {
        if (data instanceof Stairs || data instanceof TrapDoor) {
            Bisected bisected = (Bisected) data;
            bisected.setHalf(bisected.getHalf() == Bisected.Half.TOP ? Bisected.Half.BOTTOM : Bisected.Half.TOP);
        }
        if (data instanceof Slab slab) {
            if (slab.getType() == Slab.Type.TOP) {
                slab.setType(Slab.Type.BOTTOM);
            } else if (slab.getType() == Slab.Type.BOTTOM) {
                slab.setType(Slab.Type.TOP);
            }
        }
        if (data instanceof FaceAttachable attachable) {
            FaceAttachable.AttachedFace face = attachable.getAttachedFace();
            if (face == FaceAttachable.AttachedFace.FLOOR) {
                attachable.setAttachedFace(FaceAttachable.AttachedFace.CEILING);
            } else if (face == FaceAttachable.AttachedFace.CEILING) {
                attachable.setAttachedFace(FaceAttachable.AttachedFace.FLOOR);
            }
        }
    }

    /** A horizontal mirror swaps the left/right sense of door hinges and double chests. */
    private static void flipHandedness(BlockData data) {
        if (data instanceof Door door) {
            door.setHinge(door.getHinge() == Door.Hinge.LEFT ? Door.Hinge.RIGHT : Door.Hinge.LEFT);
        }
        if (data instanceof Chest chest) {
            if (chest.getType() == Chest.Type.LEFT) {
                chest.setType(Chest.Type.RIGHT);
            } else if (chest.getType() == Chest.Type.RIGHT) {
                chest.setType(Chest.Type.LEFT);
            }
        }
    }

    /** Moves each side connection of a redstone wire to the face the mapper sends it to. */
    private static void remapRedstoneWire(RedstoneWire wire, UnaryOperator<BlockFace> mapper) {
        Map<BlockFace, RedstoneWire.Connection> before = new EnumMap<>(BlockFace.class);
        for (BlockFace face : wire.getAllowedFaces()) {
            before.put(face, wire.getFace(face));
        }
        for (Map.Entry<BlockFace, RedstoneWire.Connection> entry : before.entrySet()) {
            BlockFace target = mapper.apply(entry.getKey());
            if (target != null && before.containsKey(target)) {
                wire.setFace(target, entry.getValue());
            }
        }
    }

    private static void rotateMultipleFacingY(MultipleFacing multipleFacing, int rotation) {
        Set<BlockFace> currentFaces = new HashSet<>(multipleFacing.getFaces());
        for (BlockFace face : multipleFacing.getAllowedFaces()) {
            multipleFacing.setFace(face, false);
        }
        for (BlockFace face : currentFaces) {
            BlockFace rotated = rotateFaceY(face, rotation);
            if (rotated != null && multipleFacing.getAllowedFaces().contains(rotated)) {
                multipleFacing.setFace(rotated, true);
            }
        }
    }

    private static void flipMultipleFacing(MultipleFacing multipleFacing, String axis) {
        Set<BlockFace> currentFaces = new HashSet<>(multipleFacing.getFaces());
        for (BlockFace face : multipleFacing.getAllowedFaces()) {
            multipleFacing.setFace(face, false);
        }
        for (BlockFace face : currentFaces) {
            BlockFace flipped = flipFace(face, axis);
            if (flipped != null && multipleFacing.getAllowedFaces().contains(flipped)) {
                multipleFacing.setFace(flipped, true);
            }
        }
    }

    private static void rotateWallY(Wall wall, int rotation) {
        Wall.Height north = wall.getHeight(BlockFace.NORTH);
        Wall.Height east = wall.getHeight(BlockFace.EAST);
        Wall.Height south = wall.getHeight(BlockFace.SOUTH);
        Wall.Height west = wall.getHeight(BlockFace.WEST);

        wall.setHeight(BlockFace.NORTH, rotateWallHeight(north, east, south, west, rotation, BlockFace.NORTH));
        wall.setHeight(BlockFace.EAST, rotateWallHeight(north, east, south, west, rotation, BlockFace.EAST));
        wall.setHeight(BlockFace.SOUTH, rotateWallHeight(north, east, south, west, rotation, BlockFace.SOUTH));
        wall.setHeight(BlockFace.WEST, rotateWallHeight(north, east, south, west, rotation, BlockFace.WEST));
    }

    private static void flipWall(Wall wall, String axis) {
        Wall.Height north = wall.getHeight(BlockFace.NORTH);
        Wall.Height east = wall.getHeight(BlockFace.EAST);
        Wall.Height south = wall.getHeight(BlockFace.SOUTH);
        Wall.Height west = wall.getHeight(BlockFace.WEST);

        if (axis.equals("x")) {
            wall.setHeight(BlockFace.EAST, west);
            wall.setHeight(BlockFace.WEST, east);
            wall.setHeight(BlockFace.NORTH, north);
            wall.setHeight(BlockFace.SOUTH, south);
            return;
        }
        if (axis.equals("z")) {
            wall.setHeight(BlockFace.NORTH, south);
            wall.setHeight(BlockFace.SOUTH, north);
            wall.setHeight(BlockFace.EAST, east);
            wall.setHeight(BlockFace.WEST, west);
        }
    }

    private static Wall.Height rotateWallHeight(Wall.Height north, Wall.Height east, Wall.Height south, Wall.Height west,
                                                int rotation, BlockFace targetFace) {
        return switch (rotation) {
            case 90 -> switch (targetFace) {
                case NORTH -> west;
                case EAST -> north;
                case SOUTH -> east;
                case WEST -> south;
                default -> Wall.Height.NONE;
            };
            case 180 -> switch (targetFace) {
                case NORTH -> south;
                case EAST -> west;
                case SOUTH -> north;
                case WEST -> east;
                default -> Wall.Height.NONE;
            };
            case 270 -> switch (targetFace) {
                case NORTH -> east;
                case EAST -> south;
                case SOUTH -> west;
                case WEST -> north;
                default -> Wall.Height.NONE;
            };
            default -> switch (targetFace) {
                case NORTH -> north;
                case EAST -> east;
                case SOUTH -> south;
                case WEST -> west;
                default -> Wall.Height.NONE;
            };
        };
    }

    public static BlockFace rotateFaceY(BlockFace face, int rotation) {
        if (face == null) {
            return null;
        }
        if (face == BlockFace.UP || face == BlockFace.DOWN || face == BlockFace.SELF) {
            return face;
        }
        int x = face.getModX();
        int z = face.getModZ();
        int[] rotated = rotateY(x, z, rotation);
        return faceFromVector(rotated[0], face.getModY(), rotated[1]);
    }

    public static BlockFace flipFace(BlockFace face, String axis) {
        if (face == null) {
            return null;
        }
        int x = face.getModX();
        int y = face.getModY();
        int z = face.getModZ();
        if (axis.equals("x")) {
            x = -x;
        } else if (axis.equals("y")) {
            y = -y;
        } else {
            z = -z;
        }
        return faceFromVector(x, y, z);
    }

    public static Rotation rotateFrameRotation(Rotation rotation, int degrees) {
        if (rotation == null) {
            return Rotation.NONE;
        }
        Rotation result = rotation;
        int normalized = normalizeRotation(degrees);
        int steps = normalized / 90;
        for (int i = 0; i < steps * 2; i++) {
            result = result.rotateClockwise();
        }
        return result;
    }

    public static Rotation flipFrameRotation(Rotation rotation, String axis) {
        if (rotation == null) {
            return Rotation.NONE;
        }
        if (axis.equals("y")) {
            return rotation;
        }
        return switch (rotation) {
            case NONE -> Rotation.NONE;
            case CLOCKWISE_45 -> axis.equals("x") ? Rotation.COUNTER_CLOCKWISE_45 : Rotation.CLOCKWISE_135;
            case CLOCKWISE -> axis.equals("x") ? Rotation.COUNTER_CLOCKWISE : Rotation.CLOCKWISE;
            case CLOCKWISE_135 -> axis.equals("x") ? Rotation.FLIPPED_45 : Rotation.CLOCKWISE_45;
            case FLIPPED -> Rotation.FLIPPED;
            case FLIPPED_45 -> axis.equals("x") ? Rotation.CLOCKWISE_135 : Rotation.COUNTER_CLOCKWISE_45;
            case COUNTER_CLOCKWISE -> axis.equals("x") ? Rotation.CLOCKWISE : Rotation.COUNTER_CLOCKWISE;
            case COUNTER_CLOCKWISE_45 -> axis.equals("x") ? Rotation.CLOCKWISE_45 : Rotation.FLIPPED_45;
        };
    }

    private static BlockFace faceFromVector(int x, int y, int z) {
        for (BlockFace face : BlockFace.values()) {
            if (face.getModX() == x && face.getModY() == y && face.getModZ() == z) {
                return face;
            }
        }
        return null;
    }

    private static Axis rotateAxisY(Axis axis, int rotation) {
        if (axis == Axis.Y || rotation == 180 || rotation == 0) {
            return axis;
        }
        return axis == Axis.X ? Axis.Z : Axis.X;
    }

    private static Axis flipAxis(Axis axis, String flipAxis) {
        return axis;
    }

    private static Rail.Shape rotateRailShape(Rail.Shape shape, int rotation) {
        return switch (shape) {
            case NORTH_SOUTH -> rotation == 90 || rotation == 270 ? Rail.Shape.EAST_WEST : Rail.Shape.NORTH_SOUTH;
            case EAST_WEST -> rotation == 90 || rotation == 270 ? Rail.Shape.NORTH_SOUTH : Rail.Shape.EAST_WEST;
            case ASCENDING_EAST -> rotateAscending(shape, rotation, Rail.Shape.ASCENDING_SOUTH, Rail.Shape.ASCENDING_WEST, Rail.Shape.ASCENDING_NORTH);
            case ASCENDING_SOUTH -> rotateAscending(shape, rotation, Rail.Shape.ASCENDING_WEST, Rail.Shape.ASCENDING_NORTH, Rail.Shape.ASCENDING_EAST);
            case ASCENDING_WEST -> rotateAscending(shape, rotation, Rail.Shape.ASCENDING_NORTH, Rail.Shape.ASCENDING_EAST, Rail.Shape.ASCENDING_SOUTH);
            case ASCENDING_NORTH -> rotateAscending(shape, rotation, Rail.Shape.ASCENDING_EAST, Rail.Shape.ASCENDING_SOUTH, Rail.Shape.ASCENDING_WEST);
            case NORTH_EAST -> rotateCorner(shape, rotation, Rail.Shape.SOUTH_EAST, Rail.Shape.SOUTH_WEST, Rail.Shape.NORTH_WEST);
            case SOUTH_EAST -> rotateCorner(shape, rotation, Rail.Shape.SOUTH_WEST, Rail.Shape.NORTH_WEST, Rail.Shape.NORTH_EAST);
            case SOUTH_WEST -> rotateCorner(shape, rotation, Rail.Shape.NORTH_WEST, Rail.Shape.NORTH_EAST, Rail.Shape.SOUTH_EAST);
            case NORTH_WEST -> rotateCorner(shape, rotation, Rail.Shape.NORTH_EAST, Rail.Shape.SOUTH_EAST, Rail.Shape.SOUTH_WEST);
        };
    }

    private static Rail.Shape flipRailShape(Rail.Shape shape, String axis) {
        if (axis.equals("y")) {
            return shape;
        }
        return switch (shape) {
            case ASCENDING_EAST -> axis.equals("x") ? Rail.Shape.ASCENDING_WEST : Rail.Shape.ASCENDING_EAST;
            case ASCENDING_WEST -> axis.equals("x") ? Rail.Shape.ASCENDING_EAST : Rail.Shape.ASCENDING_WEST;
            case ASCENDING_NORTH -> axis.equals("z") ? Rail.Shape.ASCENDING_SOUTH : Rail.Shape.ASCENDING_NORTH;
            case ASCENDING_SOUTH -> axis.equals("z") ? Rail.Shape.ASCENDING_NORTH : Rail.Shape.ASCENDING_SOUTH;
            case NORTH_EAST -> axis.equals("x") ? Rail.Shape.NORTH_WEST : axis.equals("z") ? Rail.Shape.SOUTH_EAST : shape;
            case NORTH_WEST -> axis.equals("x") ? Rail.Shape.NORTH_EAST : axis.equals("z") ? Rail.Shape.SOUTH_WEST : shape;
            case SOUTH_EAST -> axis.equals("x") ? Rail.Shape.SOUTH_WEST : axis.equals("z") ? Rail.Shape.NORTH_EAST : shape;
            case SOUTH_WEST -> axis.equals("x") ? Rail.Shape.SOUTH_EAST : axis.equals("z") ? Rail.Shape.NORTH_WEST : shape;
            default -> shape;
        };
    }

    private static Stairs.Shape flipStairShape(Stairs.Shape shape, String axis) {
        if (axis.equals("y")) {
            return shape;
        }
        return switch (shape) {
            case INNER_LEFT -> Stairs.Shape.INNER_RIGHT;
            case INNER_RIGHT -> Stairs.Shape.INNER_LEFT;
            case OUTER_LEFT -> Stairs.Shape.OUTER_RIGHT;
            case OUTER_RIGHT -> Stairs.Shape.OUTER_LEFT;
            default -> shape;
        };
    }

    private static Rail.Shape rotateAscending(Rail.Shape base, int rotation, Rail.Shape r90, Rail.Shape r180, Rail.Shape r270) {
        if (rotation == 90) {
            return r90;
        }
        if (rotation == 180) {
            return r180;
        }
        if (rotation == 270) {
            return r270;
        }
        return base;
    }

    private static Rail.Shape rotateCorner(Rail.Shape base, int rotation, Rail.Shape r90, Rail.Shape r180, Rail.Shape r270) {
        if (rotation == 90) {
            return r90;
        }
        if (rotation == 180) {
            return r180;
        }
        if (rotation == 270) {
            return r270;
        }
        return base;
    }
}
