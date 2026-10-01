package com.bayzyl.shape;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Regression: decodeOffsets masked y/z with 17 bits while encodeOffset packs 21-bit fields,
// so every native sphere, cylinder, and pyramid wrote ~1,048,576 blocks away from the anchor
// (outside the world) while still reporting "changed N blocks".
final class NativeShapeOffsetsTest {
    private final NativeShapeAdapter adapter = new NativeShapeAdapter();

    @Test
    void sphereOffsetsStayInsideTheRadius() {
        List<int[]> offsets = adapter.generateSphereOffsets(3, 3, 3, false);

        assertTrue(offsets.size() > 100, "a radius-3 sphere has more than 100 blocks");
        for (int[] offset : offsets) {
            assertTrue(Math.abs(offset[0]) <= 4 && Math.abs(offset[1]) <= 4 && Math.abs(offset[2]) <= 4,
                    "offset escaped the sphere: " + Arrays.toString(offset));
        }
    }

    @Test
    void sphereIsSymmetricInEveryAxis() {
        Set<String> keys = new HashSet<>();
        for (int[] offset : adapter.generateSphereOffsets(4, 4, 4, true)) {
            keys.add(offset[0] + "," + offset[1] + "," + offset[2]);
        }
        for (String key : keys) {
            String[] p = key.split(",");
            int x = Integer.parseInt(p[0]);
            int y = Integer.parseInt(p[1]);
            int z = Integer.parseInt(p[2]);
            assertTrue(keys.contains((-x) + "," + (-y) + "," + (-z)), "missing mirror of " + key);
        }
    }

    @Test
    void cylinderOffsetsStartAtTheAnchorAndRiseToHeight() {
        List<int[]> offsets = adapter.generateCylinderOffsets(2, 2, 5, false);

        int minY = offsets.stream().mapToInt(o -> o[1]).min().orElseThrow();
        int maxY = offsets.stream().mapToInt(o -> o[1]).max().orElseThrow();
        assertEquals(0, minY);
        assertEquals(4, maxY);
        for (int[] offset : offsets) {
            assertTrue(Math.abs(offset[0]) <= 3 && Math.abs(offset[2]) <= 3,
                    "offset escaped the cylinder: " + Arrays.toString(offset));
        }
    }

    @Test
    void pyramidOffsetsStayInsideTheBase() {
        List<int[]> offsets = adapter.generatePyramidOffsets(4, false);

        for (int[] offset : offsets) {
            assertTrue(offset[1] >= 0 && offset[1] <= 4, "bad pyramid height: " + Arrays.toString(offset));
            assertTrue(Math.abs(offset[0]) <= 4 && Math.abs(offset[2]) <= 4,
                    "offset escaped the pyramid base: " + Arrays.toString(offset));
        }
    }
}
