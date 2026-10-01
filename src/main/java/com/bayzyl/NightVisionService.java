package com.bayzyl;

import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class NightVisionService {
    private static final int PERSISTENT_DURATION = Integer.MAX_VALUE;

    private final Set<UUID> enabled = ConcurrentHashMap.newKeySet();

    public boolean isEnabled(Player player) {
        return enabled.contains(player.getUniqueId());
    }

    public void setEnabled(Player player, boolean value) {
        if (value) {
            enabled.add(player.getUniqueId());
            apply(player);
        } else {
            enabled.remove(player.getUniqueId());
            player.removePotionEffect(PotionEffectType.NIGHT_VISION);
        }
    }

    public void applyIfEnabled(Player player) {
        if (isEnabled(player)) {
            apply(player);
        }
    }

    private void apply(Player player) {
        player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, PERSISTENT_DURATION, 0, false, false, true));
    }
}
