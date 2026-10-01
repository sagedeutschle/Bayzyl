package com.bayzyl.shape;

import com.bayzyl.CylinderRequest;
import com.bayzyl.PyramidRequest;
import com.bayzyl.ShapeAnchorMode;
import com.bayzyl.SphereRequest;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ShapeAdapterSafetyTest {
    private final NativeShapeAdapter nativeAdapter = new NativeShapeAdapter();

    @Test
    void estimatesUseCheckedBoundingVolumesWithoutMaterializingGeometry() {
        assertEquals(125L, nativeAdapter.estimateSphere(sphere(2)).workUnits());
        assertEquals(75L, nativeAdapter.estimateCylinder(cylinder(2, 1, 5)).workUnits());
        assertEquals(75L, nativeAdapter.estimatePyramid(pyramid(2)).workUnits());

        assertTrue(nativeAdapter.estimateSphere(sphere(Integer.MAX_VALUE)).hardRejected());
        assertTrue(nativeAdapter.estimateCylinder(
                cylinder(Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE)).hardRejected());
        assertTrue(nativeAdapter.estimatePyramid(pyramid(Integer.MAX_VALUE)).hardRejected());
    }

    @Test
    void nativeBoundsAndCreateRejectHardRequestsBeforeAnchorOrWorldAccess() {
        SphereRequest sphere = sphere(51);
        CylinderRequest cylinder = cylinder(51, 51, 100);
        PyramidRequest pyramid = pyramid(63);

        assertThrows(IllegalArgumentException.class, () -> nativeAdapter.sphereBounds(null, sphere));
        assertThrows(IllegalArgumentException.class, () -> nativeAdapter.cylinderBounds(null, cylinder));
        assertThrows(IllegalArgumentException.class, () -> nativeAdapter.pyramidBounds(null, pyramid));
        assertThrows(IllegalArgumentException.class, () -> nativeAdapter.createSphere(null, null, sphere));
        assertThrows(IllegalArgumentException.class, () -> nativeAdapter.createCylinder(null, null, cylinder));
        assertThrows(IllegalArgumentException.class, () -> nativeAdapter.createPyramid(null, null, pyramid));
    }

    @Test
    void worldEditEntrypointsPreflightBeforeFallbackOrAdaptation() throws IOException {
        String source = Files.readString(Path.of("src", "main", "java", "com", "bayzyl", "shape",
                "WorldEditShapeAdapter.java"));

        assertPreflightFirst(source, "public ShapeBounds sphereBounds", "return fallback.sphereBounds");
        assertPreflightFirst(source, "public ShapeBounds cylinderBounds", "return fallback.cylinderBounds");
        assertPreflightFirst(source, "public ShapeBounds pyramidBounds", "return fallback.pyramidBounds");
        assertPreflightFirst(source, "public int createSphere", "request.distribution().isSingleMaterial");
        assertPreflightFirst(source, "public int createCylinder", "request.distribution().isSingleMaterial");
        assertPreflightFirst(source, "public int createPyramid", "request.distribution().isSingleMaterial");
    }

    private static SphereRequest sphere(int radius) {
        return new SphereRequest(null, radius, radius, radius, false,
                ShapeAnchorMode.TARGET, 1, false, false, true, null);
    }

    private static CylinderRequest cylinder(int radiusX, int radiusZ, int height) {
        return new CylinderRequest(null, radiusX, radiusZ, height, false,
                ShapeAnchorMode.TARGET, 1, false, false, true, null);
    }

    private static PyramidRequest pyramid(int size) {
        return new PyramidRequest(null, size, false,
                ShapeAnchorMode.TARGET, false, true, null);
    }

    private static void assertPreflightFirst(String source, String method, String firstUnsafeAccess) {
        String body = methodBody(source, method);
        int preflight = body.indexOf("requireWithinHardLimit");
        int unsafeAccess = body.indexOf(firstUnsafeAccess);
        assertTrue(preflight >= 0 && unsafeAccess > preflight,
                method + " must check the hard limit before " + firstUnsafeAccess);
    }

    private static String methodBody(String source, String signature) {
        int methodStart = source.indexOf(signature);
        int bodyStart = source.indexOf('{', methodStart);
        assertTrue(methodStart >= 0 && bodyStart > methodStart, "missing method " + signature);
        int depth = 0;
        for (int i = bodyStart; i < source.length(); i++) {
            char current = source.charAt(i);
            if (current == '{') {
                depth++;
            } else if (current == '}' && --depth == 0) {
                return source.substring(bodyStart + 1, i);
            }
        }
        throw new AssertionError("unterminated method " + signature);
    }
}
