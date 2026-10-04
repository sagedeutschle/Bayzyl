package com.bayzyl;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MovementAssistSafetyTest {
    @Test
    void plainStandingSpotIsNotHazardous() {
        assertFalse(MovementAssistService.isHazardousStand(Material.AIR, Material.AIR, Material.STONE));
        assertFalse(MovementAssistService.isHazardousStand(Material.WATER, Material.AIR, Material.STONE));
    }

    @Test
    void lavaOrFireInsideTheBodyIsHazardous() {
        assertTrue(MovementAssistService.isHazardousStand(Material.LAVA, Material.AIR, Material.STONE));
        assertTrue(MovementAssistService.isHazardousStand(Material.AIR, Material.LAVA, Material.STONE));
        assertTrue(MovementAssistService.isHazardousStand(Material.FIRE, Material.AIR, Material.STONE));
        assertTrue(MovementAssistService.isHazardousStand(Material.AIR, Material.SOUL_FIRE, Material.STONE));
    }

    @Test
    void damagingFloorsAreHazardous() {
        assertTrue(MovementAssistService.isHazardousStand(Material.AIR, Material.AIR, Material.MAGMA_BLOCK));
        assertTrue(MovementAssistService.isHazardousStand(Material.AIR, Material.AIR, Material.CACTUS));
    }
}
