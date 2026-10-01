package com.bayzyl;

import com.bayzyl.security.BayzylAccess;
import com.bayzyl.security.CommandAccessPolicy;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

final class RemainingBrushCommandSafetyTest {
    @Test
    void invalidPaintSizeAndTerrainFlagsNeverReachBindersOrReplaceHeldState() throws Exception {
        ToolManager tools = mock(ToolManager.class);
        BayzylCommand command = command(tools);
        Player player = mock(Player.class);
        PlayerInventory inventory = mock(PlayerInventory.class);
        ItemStack held = mock(ItemStack.class);
        when(player.getInventory()).thenReturn(inventory);
        when(inventory.getItemInMainHand()).thenReturn(held);

        invoke(command, "handleBrushPaint", new Class<?>[]{Player.class, String[].class},
                player, new String[]{"paint", "air", "size:0"});
        invoke(command, "handleBrushPaint", new Class<?>[]{Player.class, String[].class},
                player, new String[]{"paint", "air", "size:65"});
        invoke(command, "handleBrushPaint", new Class<?>[]{Player.class, String[].class},
                player, new String[]{"paint", "air", "mask:" + "x".repeat(100_000), "size:65"});
        invoke(command, "handleBrushTerrain", new Class<?>[]{Player.class, TerrainBrushType.class, String[].class},
                player, TerrainBrushType.SMOOTH, new String[]{"smooth", "8", "bedrock:garbage"});
        invoke(command, "handleBrushTerrain", new Class<?>[]{Player.class, TerrainBrushType.class, String[].class},
                player, TerrainBrushType.SMOOTH, new String[]{"smooth", "8", "1", "garbage"});
        invoke(command, "handleToolNamespace", new Class<?>[]{org.bukkit.command.CommandSender.class, String[].class},
                player, new String[]{"tool", "smooth", "8", "bedrock:garbage"});

        verifyNoInteractions(tools);
        verify(inventory, never()).setItemInMainHand(any());
        verify(inventory, never()).addItem(any(ItemStack[].class));
    }

    @Test
    void invalidSavedBrushIsReadOnlyAndNeverReplacesHeldState() throws Exception {
        ToolManager tools = mock(ToolManager.class);
        BrushPresetService presets = mock(BrushPresetService.class);
        BayzylCommand command = command(tools, presets);
        Player player = mock(Player.class);
        PlayerInventory inventory = mock(PlayerInventory.class);
        ItemStack stored = mock(ItemStack.class);
        when(player.getInventory()).thenReturn(inventory);
        when(presets.loadBrush("poisoned")).thenReturn(stored);
        when(tools.getToolType(stored)).thenReturn(ToolType.ERASER);
        when(tools.readEraserSettings(stored)).thenReturn(null);

        invoke(command, "handleBrushLoad", new Class<?>[]{Player.class, String[].class},
                player, new String[]{"load", "poisoned"});

        verify(inventory, never()).setItemInMainHand(any());
        verify(presets, never()).saveBrush(any(), any(), any());
        verify(presets, never()).deleteBrush(any());
        verify(tools, never()).bindEraser(any(), any(), anyBoolean());
    }

    @Test
    void bindAndCreateRefusalsPrecedeInventoryMutation() throws IOException {
        String source = source();
        for (String method : new String[]{
                "private boolean handleBrushPaint",
                "private boolean handleBrushPattern",
                "private boolean handleBrushErase",
                "private boolean handleBrushTerrain"
        }) {
            String body = methodBody(source, method);
            assertTrue(body.contains("if (!toolManager.bind") || body.contains("if (item == null)"),
                    method + " must handle binder/create refusal");
            int refusal = Math.max(body.indexOf("if (!toolManager.bind"), body.indexOf("if (item == null)"));
            int inventoryWrite = body.indexOf("setItemInMainHand", refusal);
            assertTrue(refusal >= 0 && (inventoryWrite < 0 || inventoryWrite > refusal),
                    method + " must refuse before inventory mutation");
        }
    }

    @Test
    void densityAndEraserBooleanParsersRejectNonfiniteOrUnknownText() throws IOException {
        String source = source();
        String density = methodBody(source, "private double parseDensity");
        String densityCommand = methodBody(source, "private boolean handleBrushDensityCommand");
        String eraserFlags = methodBody(source, "private Boolean parseEraserFlag");

        assertTrue(density.contains("Double.isFinite(value)"));
        assertTrue(densityCommand.contains("Double.isFinite(density)"));
        assertTrue(eraserFlags.contains("default -> null"));
    }

    @Test
    void savedRecognizedBrushesUseTheirStrictReaderBeforeEquip() throws IOException {
        String load = methodBody(source(), "private boolean handleBrushLoad");
        for (String strictReader : new String[]{
                "readShapeBrushSettings", "readStructureBrushSettings", "readEraserSettings",
                "readTerrainBrushSettings", "readPaintBrushSettings", "readPatternBrushSettings",
                "readDetailBrushSettings", "readGenBrushSettings", "isClipboardBrush"
        }) {
            assertTrue(load.contains(strictReader + "(item)"), "missing saved-state validation: " + strictReader);
        }
        int validation = load.indexOf("boolean valid =");
        int equip = load.indexOf("equipBrushInMainHand(player, item)");
        assertTrue(validation >= 0 && equip > validation);
        assertTrue(!load.contains("deleteBrush") && !load.contains("bind"),
                "saved-state validation must remain read-only");
    }

    private static String source() throws IOException {
        return Files.readString(Path.of("src", "main", "java", "com", "bayzyl", "BayzylCommand.java"));
    }

    private static BayzylCommand command(ToolManager tools) throws Exception {
        return command(tools, null);
    }

    private static BayzylCommand command(ToolManager tools, BrushPresetService presets) throws Exception {
        Constructor<?> constructor = BayzylCommand.class.getConstructors()[0];
        Class<?>[] types = constructor.getParameterTypes();
        Object[] arguments = new Object[types.length];
        for (int i = 0; i < types.length; i++) {
            if (types[i] == ToolManager.class) {
                arguments[i] = tools;
            } else if (types[i] == BrushPresetService.class) {
                arguments[i] = presets;
            } else if (types[i] == BayzylAccess.class) {
                arguments[i] = new BayzylAccess();
            } else if (types[i] == CommandAccessPolicy.class) {
                arguments[i] = new CommandAccessPolicy();
            } else {
                // These command paths reject before consulting any other service.
                arguments[i] = null;
            }
        }
        return (BayzylCommand) constructor.newInstance(arguments);
    }

    private static Object invoke(Object target, String name, Class<?>[] parameterTypes, Object... args)
            throws Exception {
        Method method = target.getClass().getDeclaredMethod(name, parameterTypes);
        method.setAccessible(true);
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException ex) {
            if (ex.getCause() instanceof Exception cause) {
                throw cause;
            }
            throw ex;
        }
    }

    private static String methodBody(String source, String signature) {
        int start = source.indexOf(signature);
        assertTrue(start >= 0, "could not find " + signature);
        int open = source.indexOf('{', start);
        int depth = 0;
        for (int i = open; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '{') depth++;
            if (c == '}' && --depth == 0) return source.substring(start, i + 1);
        }
        throw new AssertionError("unterminated method " + signature);
    }
}
