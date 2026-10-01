package com.bayzyl.edit;

import com.bayzyl.BlockChange;
import com.bayzyl.BlockDistribution;
import com.bayzyl.BlockMask;
import com.bayzyl.Clipboard;
import com.bayzyl.Selection;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;

public final class WorldEditAdapter implements EditAdapter {
    private final NativeEditAdapter fallback;

    public WorldEditAdapter(NativeEditAdapter fallback) {
        this.fallback = fallback;
    }

    @Override
    public String getName() {
        return "worldedit";
    }

    @Override
    public boolean requiresConfirm(Selection selection, boolean confirm) {
        return fallback.requiresConfirm(selection, confirm);
    }

    @Override
    public List<BlockChange> setBlocks(Player player, Selection selection, BlockDistribution distribution, BlockMask mask, String ifMode) {
        return fallback.setBlocks(player, selection, distribution, mask, ifMode);
    }

    @Override
    public List<BlockChange> replaceBlocks(Player player, Selection selection, BlockMask from, BlockDistribution toDistribution, BlockMask mask) {
        return fallback.replaceBlocks(player, selection, from, toDistribution, mask);
    }

    @Override
    public Clipboard copySelection(Player player, Selection selection, BlockMask mask) {
        return fallback.copySelection(player, selection, mask);
    }

    @Override
    public List<BlockChange> cutSelection(Player player, Selection selection, BlockMask mask) {
        return fallback.cutSelection(player, selection, mask);
    }

    @Override
    public List<BlockChange> pasteClipboard(Player player, Clipboard clipboard, Location target, int rotation, boolean ignoreAir) {
        return fallback.pasteClipboard(player, clipboard, target, rotation, ignoreAir);
    }

    @Override
    public List<BlockChange> makeWalls(Player player, Selection selection, com.bayzyl.BlockDistribution distribution, BlockMask mask) {
        return fallback.makeWalls(player, selection, distribution, mask);
    }

    @Override
    public List<BlockChange> overlaySelection(Player player, Selection selection, Material material, BlockMask mask) {
        return fallback.overlaySelection(player, selection, material, mask);
    }

    @Override
    public List<BlockChange> smoothSelection(Player player, Selection selection, int iterations) {
        return fallback.smoothSelection(player, selection, iterations);
    }
}
