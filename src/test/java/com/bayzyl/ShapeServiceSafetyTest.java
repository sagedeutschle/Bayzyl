package com.bayzyl;

import com.bayzyl.shape.ShapeAdapter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

final class ShapeServiceSafetyTest {
    @Test
    void hardLargeEffectiveConfirmationRefusesBeforePlayerOrAdapterAccess() {
        Player player = mock(Player.class);
        ShapeAdapter adapter = mock(ShapeAdapter.class);
        ShapeService service = service(adapter);

        ShapeResult result = service.createSphere(player, sphere(51, false), true);

        assertFalse(result.success());
        verifyNoInteractions(player, adapter);
    }

    @Test
    void overflowingShapesRefuseBeforePlayerOrAdapterAccess() {
        Player player = mock(Player.class);
        ShapeAdapter adapter = mock(ShapeAdapter.class);
        ShapeService service = service(adapter);

        assertFalse(service.createSphere(player, sphere(Integer.MAX_VALUE, true)).success());
        assertFalse(service.createCylinder(player, cylinder(Integer.MAX_VALUE, Integer.MAX_VALUE, true)).success());
        assertFalse(service.createPyramid(player, pyramid(Integer.MAX_VALUE, true)).success());

        verifyNoInteractions(player, adapter);
    }

    @Test
    void softLargeUnconfirmedSphereRefusesBeforeAnchorResolution() {
        Player player = mock(Player.class);
        ShapeAdapter adapter = mock(ShapeAdapter.class);
        ShapeService service = service(adapter);

        ShapeResult result = service.createSphere(player, sphere(29, false));

        assertFalse(result.success());
        verifyNoInteractions(player, adapter);
    }

    @Test
    void softLargeEffectiveConfirmationReachesAnchorResolution() {
        Player player = mock(Player.class);
        ShapeAdapter adapter = mock(ShapeAdapter.class);
        ShapeService service = service(adapter);

        ShapeResult result = service.createSphere(player, sphere(29, false), true);

        assertFalse(result.success());
        verify(player).rayTraceBlocks(anyDouble());
        verifyNoInteractions(adapter);
    }

    @Test
    void ordinarySphereAdapterPathDoesNotMaterializePreviewPoints() throws IOException {
        String source = Files.readString(Path.of("src", "main", "java", "com", "bayzyl", "ShapeService.java"));
        int method = source.indexOf("private ShapeResult createSphereLike");
        int previewBranch = source.indexOf("if (request.preview())", method);
        int firstPointMaterialization = source.indexOf("Set<Long> points = previewSpherePoints", method);

        org.junit.jupiter.api.Assertions.assertTrue(method >= 0
                        && previewBranch > method
                        && firstPointMaterialization > previewBranch,
                "sphere points must only materialize inside preview/custom branches");
    }

    private static ShapeService service(ShapeAdapter adapter) {
        return new ShapeService(
                mock(HistoryService.class),
                adapter,
                mock(SelectionManager.class),
                mock(Plugin.class)
        );
    }

    private static SphereRequest sphere(int radius, boolean confirm) {
        return new SphereRequest(null, radius, radius, radius, false,
                ShapeAnchorMode.TARGET, 1, false, false, confirm, null);
    }

    private static CylinderRequest cylinder(int radius, int height, boolean confirm) {
        return new CylinderRequest(null, radius, radius, height, false,
                ShapeAnchorMode.TARGET, 1, false, false, confirm, null);
    }

    private static PyramidRequest pyramid(int size, boolean confirm) {
        return new PyramidRequest(null, size, false,
                ShapeAnchorMode.TARGET, false, confirm, null);
    }
}
