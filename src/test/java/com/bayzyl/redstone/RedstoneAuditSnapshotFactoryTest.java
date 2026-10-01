package com.bayzyl.redstone;

import com.bayzyl.redstone.AuditCell.WireLink;
import com.bayzyl.redstone.RedstoneAuditSnapshotFactory.Capture;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Comparator;
import org.bukkit.block.data.type.Observer;
import org.bukkit.block.data.type.RedstoneWallTorch;
import org.bukkit.block.data.type.RedstoneWire;
import org.bukkit.block.data.type.Repeater;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RedstoneAuditSnapshotFactoryTest {

    @Test
    void oversizedSelectionIsRefusedBeforeAnyBlockIsRead() {
        AtomicInteger reads = new AtomicInteger();
        RedstoneAuditSnapshotFactory factory = new RedstoneAuditSnapshotFactory((x, y, z) -> {
            reads.incrementAndGet();
            return null;
        }, List.of(), -64, 319);

        Capture capture = factory.capture(new AuditPosition(0, 0, 0), new AuditPosition(63, 31, 64));

        assertNull(capture.snapshot());
        assertNotNull(capture.refusal());
        assertTrue(capture.refusal().contains("133120"), capture.refusal());
        assertEquals(0, reads.get(), "an oversized selection must not touch the world");
    }

    @Test
    void selectionAtTheCapIsCaptured() {
        Capture capture = new RedstoneAuditSnapshotFactory((x, y, z) -> null, List.of(), -64, 319)
                .capture(new AuditPosition(0, 0, 0), new AuditPosition(63, 31, 63));
        assertNotNull(capture.snapshot());
        assertNull(capture.refusal());
    }

    @Test
    void readsTheSelectionPlusItsHaloButNothingAboveTheWorld() {
        AtomicInteger reads = new AtomicInteger();
        BlockData repeater = repeater(BlockFace.EAST);
        RedstoneAuditSnapshotFactory factory = new RedstoneAuditSnapshotFactory((x, y, z) -> {
            reads.incrementAndGet();
            return x == 0 && y == 319 && z == 0 ? repeater : null;
        }, List.of(), -64, 319);

        Capture capture = factory.capture(new AuditPosition(0, 319, 0), new AuditPosition(1, 319, 0));

        assertEquals(6 * 3 * 5, reads.get(), "2-block halo on x and z, only downward on y at the build limit");
        assertEquals(AuditCell.repeater(Side.EAST), capture.snapshot().at(new AuditPosition(0, 319, 0)));
        assertSame(AuditCell.EMPTY, capture.snapshot().at(new AuditPosition(0, 320, 0)));
        assertNotNull(capture.snapshot().at(new AuditPosition(-2, 317, 0)));
    }

    @Test
    void itemFramesMakeTheirBlockReadable() {
        AuditPosition frame = new AuditPosition(1, 64, 0);
        Capture capture = new RedstoneAuditSnapshotFactory((x, y, z) -> null, List.of(frame), -64, 319)
                .capture(new AuditPosition(0, 64, 0), new AuditPosition(2, 64, 0));
        assertTrue(capture.snapshot().at(frame).readable());
    }

    @Test
    void classifiesRedstoneComponentsFromBlockData() {
        RedstoneWire wire = mock(RedstoneWire.class);
        when(wire.getMaterial()).thenReturn(Material.REDSTONE_WIRE);
        when(wire.getFace(BlockFace.EAST)).thenReturn(RedstoneWire.Connection.SIDE);
        when(wire.getFace(BlockFace.WEST)).thenReturn(RedstoneWire.Connection.UP);
        when(wire.getFace(BlockFace.NORTH)).thenReturn(RedstoneWire.Connection.NONE);
        AuditCell dust = RedstoneAuditSnapshotFactory.classify(wire);
        assertTrue(dust.isDust());
        assertEquals(WireLink.SIDE, dust.wire(Side.EAST));
        assertEquals(WireLink.UP, dust.wire(Side.WEST));
        assertEquals(WireLink.NONE, dust.wire(Side.SOUTH));

        Comparator comparator = mock(Comparator.class);
        when(comparator.getMaterial()).thenReturn(Material.COMPARATOR);
        when(comparator.getFacing()).thenReturn(BlockFace.SOUTH);
        assertEquals(AuditCell.comparator(Side.SOUTH), RedstoneAuditSnapshotFactory.classify(comparator));

        Observer observer = mock(Observer.class);
        when(observer.getMaterial()).thenReturn(Material.OBSERVER);
        when(observer.getFacing()).thenReturn(BlockFace.UP);
        assertEquals(AuditCell.observer(Side.UP), RedstoneAuditSnapshotFactory.classify(observer));

        RedstoneWallTorch wallTorch = mock(RedstoneWallTorch.class);
        when(wallTorch.getMaterial()).thenReturn(Material.REDSTONE_WALL_TORCH);
        when(wallTorch.getFacing()).thenReturn(BlockFace.NORTH);
        assertEquals(AuditCell.wallTorch(Side.NORTH), RedstoneAuditSnapshotFactory.classify(wallTorch));

        assertEquals(AuditCell.torch(), RedstoneAuditSnapshotFactory.classify(block(Material.REDSTONE_TORCH, false)));
        assertSame(AuditCell.EMPTY, RedstoneAuditSnapshotFactory.classify(block(Material.CAVE_AIR, false)));
        assertSame(AuditCell.EMPTY, RedstoneAuditSnapshotFactory.classify(null));
    }

    @Test
    void classifiesBlocksByWhatTheyDoForRedstone() {
        assertEquals(AuditCell.conductorBlock(), RedstoneAuditSnapshotFactory.classify(block(Material.STONE, true)));
        assertEquals(AuditCell.nonConductorBlock(), RedstoneAuditSnapshotFactory.classify(block(Material.GLASS, false)));
        assertTrue(RedstoneAuditSnapshotFactory.classify(block(Material.LEVER, false)).source());
        assertTrue(RedstoneAuditSnapshotFactory.classify(block(Material.STONE_BUTTON, false)).source());
        assertTrue(RedstoneAuditSnapshotFactory.classify(block(Material.REDSTONE_BLOCK, true)).source());
        assertFalse(RedstoneAuditSnapshotFactory.classify(block(Material.REDSTONE_BLOCK, true)).conductor());
        assertTrue(RedstoneAuditSnapshotFactory.classify(block(Material.DAYLIGHT_DETECTOR, false)).analogSource());
        assertTrue(RedstoneAuditSnapshotFactory.classify(block(Material.HEAVY_WEIGHTED_PRESSURE_PLATE, false)).analogSource());

        AuditCell lamp = RedstoneAuditSnapshotFactory.classify(block(Material.REDSTONE_LAMP, true));
        assertTrue(lamp.receiver());
        assertTrue(lamp.conductor());
        assertTrue(RedstoneAuditSnapshotFactory.classify(block(Material.OAK_DOOR, false)).receiver());

        AuditCell piston = RedstoneAuditSnapshotFactory.classify(block(Material.STICKY_PISTON, false));
        assertTrue(piston.pistonLike());
        assertTrue(piston.receiver());
        AuditCell dropper = RedstoneAuditSnapshotFactory.classify(block(Material.DROPPER, true));
        assertTrue(dropper.pistonLike());
        assertTrue(dropper.readable());

        assertTrue(RedstoneAuditSnapshotFactory.classify(block(Material.CHEST, false)).readable());
        assertTrue(RedstoneAuditSnapshotFactory.classify(block(Material.COMPOSTER, false)).readable());
        AuditCell hopper = RedstoneAuditSnapshotFactory.classify(block(Material.HOPPER, false));
        assertTrue(hopper.readable());
        assertTrue(hopper.receiver());
        AuditCell trapped = RedstoneAuditSnapshotFactory.classify(block(Material.TRAPPED_CHEST, false));
        assertTrue(trapped.readable());
        assertTrue(trapped.analogSource());
    }

    private static BlockData repeater(BlockFace facing) {
        Repeater repeater = mock(Repeater.class);
        when(repeater.getMaterial()).thenReturn(Material.REPEATER);
        when(repeater.getFacing()).thenReturn(facing);
        return repeater;
    }

    private static BlockData block(Material material, boolean occluding) {
        BlockData data = mock(BlockData.class);
        when(data.getMaterial()).thenReturn(material);
        when(data.isOccluding()).thenReturn(occluding);
        return data;
    }
}
