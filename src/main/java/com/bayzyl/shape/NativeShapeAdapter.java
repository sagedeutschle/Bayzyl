package com.bayzyl.shape;

import com.bayzyl.BlockDistribution;
import com.bayzyl.CylinderRequest;
import com.bayzyl.PyramidRequest;
import com.bayzyl.SphereRequest;
import com.bayzyl.safety.OperationLimits;
import com.bayzyl.safety.WorkEstimate;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class NativeShapeAdapter implements ShapeAdapter {
    @Override
    public String getName() {
        return "bayzyl-native";
    }

    @Override
    public WorkEstimate estimateSphere(SphereRequest request) {
        return OperationLimits.estimateSphere(request.radiusX(), request.radiusY(), request.radiusZ());
    }

    @Override
    public WorkEstimate estimateCylinder(CylinderRequest request) {
        return OperationLimits.estimateCylinder(request.radiusX(), request.radiusZ(), request.height());
    }

    @Override
    public WorkEstimate estimatePyramid(PyramidRequest request) {
        return OperationLimits.estimatePyramid(request.size());
    }

    @Override
    public ShapeBounds sphereBounds(Location anchor, SphereRequest request) {
        requireWithinHardLimit(estimateSphere(request));
        return new ShapeBounds(
                anchor.getBlockX() - request.radiusX(),
                anchor.getBlockY() - request.radiusY(),
                anchor.getBlockZ() - request.radiusZ(),
                anchor.getBlockX() + request.radiusX(),
                anchor.getBlockY() + request.radiusY(),
                anchor.getBlockZ() + request.radiusZ()
        );
    }

    @Override
    public ShapeBounds cylinderBounds(Location anchor, CylinderRequest request) {
        requireWithinHardLimit(estimateCylinder(request));
        return new ShapeBounds(
                anchor.getBlockX() - request.radiusX(),
                anchor.getBlockY(),
                anchor.getBlockZ() - request.radiusZ(),
                anchor.getBlockX() + request.radiusX(),
                anchor.getBlockY() + Math.max(0, request.height() - 1),
                anchor.getBlockZ() + request.radiusZ()
        );
    }

    @Override
    public ShapeBounds pyramidBounds(Location anchor, PyramidRequest request) {
        requireWithinHardLimit(estimatePyramid(request));
        return new ShapeBounds(
                anchor.getBlockX() - request.size(),
                anchor.getBlockY(),
                anchor.getBlockZ() - request.size(),
                anchor.getBlockX() + request.size(),
                anchor.getBlockY() + request.size(),
                anchor.getBlockZ() + request.size()
        );
    }

    @Override
    public int createSphere(Player player, Location anchor, SphereRequest request) {
        requireWithinHardLimit(estimateSphere(request));
        return placeShape(anchor, request.distribution(), generateSphereOffsets(
                request.radiusX(), request.radiusY(), request.radiusZ(), request.hollow()));
    }

    @Override
    public int createCylinder(Player player, Location anchor, CylinderRequest request) {
        requireWithinHardLimit(estimateCylinder(request));
        return placeShape(anchor, request.distribution(), generateCylinderOffsets(
                request.radiusX(), request.radiusZ(), request.height(), request.hollow()));
    }

    @Override
    public int createPyramid(Player player, Location anchor, PyramidRequest request) {
        requireWithinHardLimit(estimatePyramid(request));
        return placeShape(anchor, request.distribution(), generatePyramidOffsets(request.size(), request.hollow()));
    }

    private void requireWithinHardLimit(WorkEstimate estimate) {
        if (estimate.hardRejected()) {
            throw new IllegalArgumentException(estimate.reason());
        }
    }

    private int placeShape(Location anchor, BlockDistribution distribution, List<int[]> offsets) {
        World world = anchor.getWorld();
        if (world == null) {
            return 0;
        }

        int changed = 0;
        int baseX = anchor.getBlockX();
        int baseY = anchor.getBlockY();
        int baseZ = anchor.getBlockZ();

        for (int[] offset : offsets) {
            Material material = distribution.pickRandom();
            BlockData placedData = material.createBlockData();
            Block block = world.getBlockAt(baseX + offset[0], baseY + offset[1], baseZ + offset[2]);
            BlockData before = block.getBlockData();
            if (before.matches(placedData)) {
                continue;
            }
            block.setBlockData(placedData, false);
            changed++;
        }

        return changed;
    }

    // Package-private so tests can check the generated geometry without a live world.
    List<int[]> generateSphereOffsets(int radiusX, int radiusY, int radiusZ, boolean hollow) {
        Set<Long> offsets = new LinkedHashSet<>();
        double rx = radiusX + 0.5;
        double ry = radiusY + 0.5;
        double rz = radiusZ + 0.5;
        double invX = 1.0 / rx;
        double invY = 1.0 / ry;
        double invZ = 1.0 / rz;
        int ceilX = (int) Math.ceil(rx);
        int ceilY = (int) Math.ceil(ry);
        int ceilZ = (int) Math.ceil(rz);

        double nextX = 0.0;
        xLoop:
        for (int x = 0; x <= ceilX; x++) {
            double currX = nextX;
            nextX = (x + 1) * invX;
            double nextY = 0.0;
            yLoop:
            for (int y = 0; y <= ceilY; y++) {
                double currY = nextY;
                nextY = (y + 1) * invY;
                double nextZ = 0.0;
                for (int z = 0; z <= ceilZ; z++) {
                    double currZ = nextZ;
                    nextZ = (z + 1) * invZ;
                    double distance = lengthSq3(currX, currY, currZ);
                    if (distance > 1.0) {
                        if (z != 0) {
                            break;
                        }
                        if (y != 0) {
                            break yLoop;
                        }
                        break xLoop;
                    }

                    if (hollow
                            && lengthSq3(nextX, currY, currZ) <= 1.0
                            && lengthSq3(currX, nextY, currZ) <= 1.0
                            && lengthSq3(currX, currY, nextZ) <= 1.0) {
                        continue;
                    }

                    addSymmetric3d(offsets, x, y, z);
                }
            }
        }

        return decodeOffsets(offsets);
    }

    // Package-private so tests can check the generated geometry without a live world.
    List<int[]> generateCylinderOffsets(int radiusX, int radiusZ, int height, boolean hollow) {
        Set<Long> offsets = new LinkedHashSet<>();
        double rx = radiusX + 0.5;
        double rz = radiusZ + 0.5;
        double invX = 1.0 / rx;
        double invZ = 1.0 / rz;
        int ceilX = (int) Math.ceil(rx);
        int ceilZ = (int) Math.ceil(rz);

        double nextX = 0.0;
        xLoop:
        for (int x = 0; x <= ceilX; x++) {
            double currX = nextX;
            nextX = (x + 1) * invX;
            double nextZ = 0.0;
            for (int z = 0; z <= ceilZ; z++) {
                double currZ = nextZ;
                nextZ = (z + 1) * invZ;
                double distance = lengthSq2(currX, currZ);
                if (distance > 1.0) {
                    if (z != 0) {
                        break;
                    }
                    break xLoop;
                }

                if (hollow
                        && lengthSq2(nextX, currZ) <= 1.0
                        && lengthSq2(currX, nextZ) <= 1.0) {
                    continue;
                }

                for (int y = 0; y < height; y++) {
                    addSymmetricHorizontal(offsets, x, y, z);
                }
            }
        }

        return decodeOffsets(offsets);
    }

    // Package-private so tests can check the generated geometry without a live world.
    List<int[]> generatePyramidOffsets(int size, boolean hollow) {
        Set<Long> offsets = new LinkedHashSet<>();
        int originalSize = size;
        for (int y = 0; y <= originalSize; y++) {
            size--;
            for (int x = 0; x <= size; x++) {
                for (int z = 0; z <= size; z++) {
                    if (hollow && z != size && x != size) {
                        continue;
                    }
                    addSymmetricHorizontal(offsets, x, y, z);
                }
            }
        }
        return decodeOffsets(offsets);
    }

    private void addSymmetric3d(Set<Long> offsets, int x, int y, int z) {
        offsets.add(encodeOffset(x, y, z));
        offsets.add(encodeOffset(-x, y, z));
        offsets.add(encodeOffset(x, y, -z));
        offsets.add(encodeOffset(-x, y, -z));
        offsets.add(encodeOffset(x, -y, z));
        offsets.add(encodeOffset(-x, -y, z));
        offsets.add(encodeOffset(x, -y, -z));
        offsets.add(encodeOffset(-x, -y, -z));
    }

    private void addSymmetricHorizontal(Set<Long> offsets, int x, int y, int z) {
        offsets.add(encodeOffset(x, y, z));
        offsets.add(encodeOffset(-x, y, z));
        offsets.add(encodeOffset(x, y, -z));
        offsets.add(encodeOffset(-x, y, -z));
    }

    private List<int[]> decodeOffsets(Set<Long> encoded) {
        List<int[]> offsets = new ArrayList<>(encoded.size());
        for (long value : encoded) {
            offsets.add(new int[]{
                    (int) ((value >> 42) - 1_048_576L),
                    (int) (((value >> 21) & 0x1F_FFFFL) - 1_048_576L),
                    (int) ((value & 0x1F_FFFFL) - 1_048_576L)
            });
        }
        return offsets;
    }

    private long encodeOffset(int x, int y, int z) {
        long ox = x + 1_048_576L;
        long oy = y + 1_048_576L;
        long oz = z + 1_048_576L;
        return (ox << 42) | (oy << 21) | oz;
    }

    private double lengthSq2(double x, double z) {
        return x * x + z * z;
    }

    private double lengthSq3(double x, double y, double z) {
        return x * x + y * y + z * z;
    }
}
