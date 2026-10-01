package com.bayzyl;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

final class ShapeCommandSafetyTest {
    @Test
    void parserPreservesAnisotropicRadiiAnchorMaskAndConfirmation() {
        BlockDistribution distribution = mock(BlockDistribution.class);
        BlockMask mask = mock(BlockMask.class);
        try (MockedStatic<BlockDistribution> distributions = org.mockito.Mockito.mockStatic(BlockDistribution.class);
             MockedStatic<BlockMask> masks = org.mockito.Mockito.mockStatic(BlockMask.class)) {
            distributions.when(() -> BlockDistribution.parse("stone")).thenReturn(distribution);
            masks.when(() -> BlockMask.parse(null)).thenReturn(null);
            masks.when(() -> BlockMask.parse("stone,dirt")).thenReturn(mask);

            SphereRequest request = ShapeCommandParser.parseSphere("sphere", new String[]{
                    "stone", "3,4,5", "at:selection-center", "mask:stone,dirt", "confirm:true"
            }, false);

            assertSame(distribution, request.distribution());
            assertEquals(3, request.radiusX());
            assertEquals(4, request.radiusY());
            assertEquals(5, request.radiusZ());
            assertEquals(ShapeAnchorMode.SELECTION_CENTER, request.anchorMode());
            assertSame(mask, request.mask());
            assertTrue(request.confirm());
        }
    }

    @Test
    void brushMaskShorthandAndCylinderDimensionsRemainCompatible() {
        BlockDistribution distribution = mock(BlockDistribution.class);
        BlockMask mask = mock(BlockMask.class);
        try (MockedStatic<BlockDistribution> distributions = org.mockito.Mockito.mockStatic(BlockDistribution.class);
             MockedStatic<BlockMask> masks = org.mockito.Mockito.mockStatic(BlockMask.class)) {
            distributions.when(() -> BlockDistribution.parse("stone")).thenReturn(distribution);
            masks.when(() -> BlockMask.parse(null)).thenReturn(null);
            masks.when(() -> BlockMask.isResolvable("dirt")).thenReturn(true);
            masks.when(() -> BlockMask.parse("dirt")).thenReturn(mask);

            ShapeBrushSettings settings = ShapeCommandParser.parseBrush(new String[]{
                    "cyl", "stone", "2,5", "7", "dirt"
            });

            assertEquals(ShapeBrushType.CYL, settings.type());
            assertEquals(2, settings.radiusX());
            assertEquals(5, settings.radiusZ());
            assertEquals(7, settings.height());
            assertEquals(ShapeAnchorMode.TARGET, settings.anchorMode());
            assertSame(mask, settings.mask());
        }
    }

    @Test
    void savedShapeBrushLoadValidatesBeforeEquipAndMenuUsesCommandRoute() throws IOException {
        String command = Files.readString(Path.of("src", "main", "java", "com", "bayzyl", "BayzylCommand.java"));
        String loadBody = methodBody(command, "private boolean handleBrushLoad", "private boolean handleBrushList");
        int validation = loadBody.indexOf("toolManager.readShapeBrushSettings(item)");
        int equip = loadBody.indexOf("equipBrushInMainHand(player, item)");
        assertTrue(validation >= 0 && equip > validation,
                "saved shape brushes must validate before they are equipped");
        assertTrue(!loadBody.contains("deleteBrush") && !loadBody.contains("bindShapeBrush"),
                "invalid saved brushes must not be deleted or rewritten during load");

        String menu = Files.readString(Path.of("src", "main", "java", "com", "bayzyl", "BrushMenuService.java"));
        assertTrue(menu.contains("player.performCommand(\"brush load \" + data)"),
                "saved-brush menu selections must use the validated command route");
    }

    @Test
    void commandBindRefusesBeforeInventoryMutation() throws IOException {
        String command = Files.readString(Path.of("src", "main", "java", "com", "bayzyl", "BayzylCommand.java"));
        String bindBody = methodBody(command, "private boolean handleBrush(CommandSender", "private boolean handleBrushSave");
        int bindRefusal = bindBody.indexOf("if (!toolManager.bindShapeBrush");
        int bindInventoryWrite = bindBody.indexOf("player.getInventory().setItemInMainHand(held)", bindRefusal);
        assertTrue(bindRefusal >= 0 && bindInventoryWrite > bindRefusal,
                "unsafe brush binding must return before the held item is written back");
    }

    @Test
    void commandResizeRefusesBeforeInventoryMutation() throws IOException {
        String command = Files.readString(Path.of("src", "main", "java", "com", "bayzyl", "BayzylCommand.java"));
        String resizeBody = methodBody(command, "private boolean handleBrushSizeCommand", "private boolean handleBrushDensityCommand");
        int shapeBranch = resizeBody.indexOf("heldToolType == ToolType.SHAPE_BRUSH");
        int softAssessment = resizeBody.indexOf("resizeEstimate.permits(shouldBypassConfirm", shapeBranch);
        int resizeRefusal = resizeBody.indexOf("if (!updatedAny)", shapeBranch);
        int resizeInventoryWrite = resizeBody.indexOf("player.getInventory().setItemInMainHand(held)", resizeRefusal);
        assertTrue(shapeBranch >= 0 && softAssessment > shapeBranch
                        && resizeRefusal > softAssessment && resizeInventoryWrite > resizeRefusal,
                "unsafe shape resize must refuse before inventory mutation or other brush fallbacks");
    }

    @Test
    void directShapeCommandsCarryEffectiveConfirmationIntoServicePreflight() throws IOException {
        String command = Files.readString(Path.of("src", "main", "java", "com", "bayzyl", "BayzylCommand.java"));
        String directShapes = methodBody(command, "private boolean handleSphere", "private boolean handleBrush(CommandSender");
        assertTrue(!directShapes.contains("shapeService.requiresConfirm"),
                "direct commands must use the service's checked defensive preflight");
        assertTrue(directShapes.contains("shapeService.createSphere(player, request,")
                        && directShapes.contains("shapeService.createDome(player, request, bowl,")
                        && directShapes.contains("shapeService.createCylinder(player, request,")
                        && directShapes.contains("shapeService.createPyramid(player, request,"),
                "each direct shape route must pass effective confirmation to its service entrypoint");
    }

    private static String methodBody(String source, String startToken, String endToken) {
        int start = source.indexOf(startToken);
        int end = source.indexOf(endToken, start + startToken.length());
        assertTrue(start >= 0 && end > start, "could not isolate " + startToken);
        return source.substring(start, end);
    }
}
