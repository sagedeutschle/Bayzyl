package com.bayzyl;

import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.FaceAttachable;
import org.bukkit.block.data.type.Chest;
import org.bukkit.block.data.type.Door;
import org.bukkit.block.data.type.RedstoneWire;
import org.bukkit.block.data.type.Slab;
import org.bukkit.block.data.type.Stairs;
import org.bukkit.block.data.type.Switch;
import org.bukkit.block.data.type.TrapDoor;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

final class ClipboardTransformsTest {
    @Test
    void rotatingStairsKeepsTheirCornerShapeBecauseItIsRelativeToTheFacing() {
        for (int rotation : new int[]{90, 180, 270}) {
            Stairs stairs = mock(Stairs.class);
            java.util.concurrent.atomic.AtomicReference<Stairs.Shape> shape = new java.util.concurrent.atomic.AtomicReference<>(Stairs.Shape.INNER_LEFT);
            when(stairs.getShape()).thenAnswer(call -> shape.get());
            org.mockito.Mockito.doAnswer(call -> { shape.set(call.getArgument(0)); return null; }).when(stairs).setShape(any());

            ClipboardTransforms.applyRotation(stairs, rotation);

            org.junit.jupiter.api.Assertions.assertEquals(Stairs.Shape.INNER_LEFT, shape.get());
        }
    }

    @Test
    void flippingStairsHorizontallyStillMirrorsTheirCornerShape() {
        Stairs stairs = mock(Stairs.class);
        when(stairs.getShape()).thenReturn(Stairs.Shape.OUTER_LEFT);

        ClipboardTransforms.applyFlip(stairs, "x");

        verify(stairs).setShape(Stairs.Shape.OUTER_RIGHT);
    }

    @Test
    void flippingVerticallyTurnsStairsTrapdoorsAndSlabsUpsideDown() {
        Stairs stairs = mock(Stairs.class);
        when(stairs.getHalf()).thenReturn(Bisected.Half.BOTTOM);
        TrapDoor trapDoor = mock(TrapDoor.class);
        when(trapDoor.getHalf()).thenReturn(Bisected.Half.TOP);
        Slab slab = mock(Slab.class);
        when(slab.getType()).thenReturn(Slab.Type.BOTTOM);
        Slab doubleSlab = mock(Slab.class);
        when(doubleSlab.getType()).thenReturn(Slab.Type.DOUBLE);

        ClipboardTransforms.applyFlip(stairs, "y");
        ClipboardTransforms.applyFlip(trapDoor, "y");
        ClipboardTransforms.applyFlip(slab, "y");
        ClipboardTransforms.applyFlip(doubleSlab, "y");

        verify(stairs).setHalf(Bisected.Half.TOP);
        verify(trapDoor).setHalf(Bisected.Half.BOTTOM);
        verify(slab).setType(Slab.Type.TOP);
        verify(doubleSlab, never()).setType(any());
    }

    @Test
    void horizontalFlipLeavesHalvesAndSlabsAlone() {
        Stairs stairs = mock(Stairs.class);
        when(stairs.getShape()).thenReturn(Stairs.Shape.STRAIGHT);
        when(stairs.getHalf()).thenReturn(Bisected.Half.BOTTOM);
        Slab slab = mock(Slab.class);
        when(slab.getType()).thenReturn(Slab.Type.TOP);

        ClipboardTransforms.applyFlip(stairs, "z");
        ClipboardTransforms.applyFlip(slab, "x");

        verify(stairs, never()).setHalf(any());
        verify(slab, never()).setType(any());
    }

    @Test
    void flippingVerticallySwapsFloorAndCeilingAttachment() {
        Switch lever = mock(Switch.class);
        when(lever.getAttachedFace()).thenReturn(FaceAttachable.AttachedFace.FLOOR);
        Switch wallButton = mock(Switch.class);
        when(wallButton.getAttachedFace()).thenReturn(FaceAttachable.AttachedFace.WALL);

        ClipboardTransforms.applyFlip(lever, "y");
        ClipboardTransforms.applyFlip(wallButton, "y");

        verify(lever).setAttachedFace(FaceAttachable.AttachedFace.CEILING);
        verify(wallButton, never()).setAttachedFace(any());
    }

    @Test
    void mirroringSwapsDoorHingesAndDoubleChestSides() {
        Door door = mock(Door.class);
        when(door.getHinge()).thenReturn(Door.Hinge.LEFT);
        Chest chest = mock(Chest.class);
        when(chest.getType()).thenReturn(Chest.Type.RIGHT);
        Chest single = mock(Chest.class);
        when(single.getType()).thenReturn(Chest.Type.SINGLE);

        ClipboardTransforms.applyFlip(door, "x");
        ClipboardTransforms.applyFlip(chest, "z");
        ClipboardTransforms.applyFlip(single, "z");

        verify(door).setHinge(Door.Hinge.RIGHT);
        verify(chest).setType(Chest.Type.LEFT);
        verify(single, never()).setType(any());
    }

    @Test
    void rotatingRedstoneWireTurnsItsConnections() {
        RedstoneWire wire = wireConnectedNorth();

        ClipboardTransforms.applyRotation(wire, 90);

        verify(wire).setFace(BlockFace.EAST, RedstoneWire.Connection.SIDE);
        verify(wire).setFace(BlockFace.NORTH, RedstoneWire.Connection.NONE);
    }

    @Test
    void flippingRedstoneWireMirrorsItsConnections() {
        RedstoneWire wire = wireConnectedNorth();

        ClipboardTransforms.applyFlip(wire, "z");

        verify(wire).setFace(BlockFace.SOUTH, RedstoneWire.Connection.SIDE);
        verify(wire).setFace(BlockFace.NORTH, RedstoneWire.Connection.NONE);
    }

    private static RedstoneWire wireConnectedNorth() {
        RedstoneWire wire = mock(RedstoneWire.class);
        when(wire.getAllowedFaces()).thenReturn(Set.of(BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST));
        when(wire.getFace(BlockFace.NORTH)).thenReturn(RedstoneWire.Connection.SIDE);
        when(wire.getFace(BlockFace.EAST)).thenReturn(RedstoneWire.Connection.NONE);
        when(wire.getFace(BlockFace.SOUTH)).thenReturn(RedstoneWire.Connection.NONE);
        when(wire.getFace(BlockFace.WEST)).thenReturn(RedstoneWire.Connection.NONE);
        return wire;
    }
}
