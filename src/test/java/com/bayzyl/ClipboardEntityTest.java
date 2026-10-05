package com.bayzyl;

import org.bukkit.Location;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.EntitySnapshot;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

final class ClipboardEntityTest {
    private static ClipboardEntity freeEntity(double x, double y, double z, float yaw) {
        return new ClipboardEntity(false, x, y, z, null, null, null, true, false, 1.0f, null,
                mock(EntitySnapshot.class), yaw, 0.0f);
    }

    @Test
    void freeEntityLandsInTheCellItsBlockRotatesTo() {
        // The block at offset (1,0) with the entity in it rotates to cell (0,1) at 90 degrees.
        ClipboardEntity rotated = freeEntity(1.5, 0.0, 0.5, 0.0f).rotateY(90);

        assertEquals(0.5, rotated.getSupportOffsetX(), 1e-9);
        assertEquals(1.5, rotated.getSupportOffsetZ(), 1e-9);
    }

    @Test
    void freeEntityRotation180And270FollowTheirBlockCells() {
        ClipboardEntity half = freeEntity(1.5, 0.0, 0.5, 0.0f).rotateY(180);
        assertEquals(-0.5, half.getSupportOffsetX(), 1e-9);
        assertEquals(0.5, half.getSupportOffsetZ(), 1e-9);

        // Cell (1,0) rotates to (0,-1) at 270 degrees.
        ClipboardEntity quarter = freeEntity(1.5, 0.0, 0.5, 0.0f).rotateY(270);
        assertEquals(0.5, quarter.getSupportOffsetX(), 1e-9);
        assertEquals(-0.5, quarter.getSupportOffsetZ(), 1e-9);
    }

    @Test
    void freeEntityMirrorsIntoTheMirroredBlockCell() {
        // Flip maps cell i to -i, so an entity in cell 1 ends up in cell -1.
        ClipboardEntity flipped = freeEntity(1.5, 0.0, 2.25, 0.0f).flip("x");
        assertEquals(-0.5, flipped.getSupportOffsetX(), 1e-9);
        assertEquals(2.25, flipped.getSupportOffsetZ(), 1e-9);

        ClipboardEntity flippedZ = freeEntity(1.5, 0.0, 2.25, 0.0f).flip("z");
        assertEquals(1.5, flippedZ.getSupportOffsetX(), 1e-9);
        assertEquals(-1.25, flippedZ.getSupportOffsetZ(), 1e-9);
    }

    @Test
    void mirroredEntityYawMirrorsItsFacingDirection() {
        // Yaw 90 faces west (-x). Mirroring x makes it face east (yaw 270); mirroring z leaves west alone.
        assertEquals(270.0f, freeEntity(0, 0, 0, 90.0f).flip("x").getYaw(), 1e-4);
        assertEquals(90.0f, freeEntity(0, 0, 0, 90.0f).flip("z").getYaw(), 1e-4);
        // Yaw 0 faces south (+z). Mirroring z makes it face north (yaw 180); mirroring x leaves it.
        assertEquals(180.0f, freeEntity(0, 0, 0, 0.0f).flip("z").getYaw(), 1e-4);
        assertEquals(0.0f, freeEntity(0, 0, 0, 0.0f).flip("x").getYaw(), 1e-4);
    }

    @Test
    void rotationThatIsNotAQuarterTurnLeavesEntitiesAlone() {
        ClipboardEntity rotated = freeEntity(1.5, 0.0, 0.5, 30.0f).rotateY(45);

        assertEquals(1.5, rotated.getSupportOffsetX(), 1e-9);
        assertEquals(0.5, rotated.getSupportOffsetZ(), 1e-9);
        assertEquals(30.0f, rotated.getYaw(), 1e-4);
    }

    @Test
    void mirroredWidePaintingStillCoversTheMirroredBlocks() {
        // 4 wide, facing north: ccw is west, so it covers x = p+1, p, p-1, p-2. Mirroring x covers -p-1 .. -p+2.
        assertEquals(4, org.bukkit.Art.SKELETON.getBlockWidth());
        ClipboardEntity painting = painting(org.bukkit.Art.SKELETON, 0, 0, 0, BlockFace.NORTH);

        ClipboardEntity flipped = painting.flip("x");

        // Facing stays north (ccw west), so the new anchor p' must satisfy p'-2 = -p-1 -> p' = 1 for p = 0.
        assertEquals(1.0, flipped.getSupportOffsetX(), 1e-9);
        assertEquals(BlockFace.NORTH, flipped.getFacing());
    }

    @Test
    void flippingAlongAPaintingsFacingKeepsItsCanvasInPlace() {
        // 4 wide facing north covers x = p+1 .. p-2; after a z flip it faces south (ccw east) covering p'-1 .. p'+2.
        assertEquals(4, org.bukkit.Art.SKELETON.getBlockWidth());
        ClipboardEntity painting = painting(org.bukkit.Art.SKELETON, 0, 0, 0, BlockFace.NORTH);

        ClipboardEntity flipped = painting.flip("z");

        assertEquals(BlockFace.SOUTH, flipped.getFacing());
        assertEquals(-1.0, flipped.getSupportOffsetX(), 1e-9);
    }

    @Test
    void oddSizedPaintingMirrorsLikeAnyOtherBlock() {
        ClipboardEntity painting = painting(org.bukkit.Art.KEBAB, 3, 2, 5, BlockFace.NORTH);

        ClipboardEntity flipped = painting.flip("x");

        assertEquals(-3.0, flipped.getSupportOffsetX(), 1e-9);
        assertEquals(2.0, flipped.getSupportOffsetY(), 1e-9);
    }

    private static ClipboardEntity painting(org.bukkit.Art art, double x, double y, double z, BlockFace facing) {
        return new ClipboardEntity(false, x, y, z, facing, null, null, true, false, 1.0f, art,
                mock(EntitySnapshot.class), 0.0f, 0.0f);
    }

}
