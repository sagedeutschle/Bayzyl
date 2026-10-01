package com.bayzyl.edit;

import com.bayzyl.BlockChange;
import com.bayzyl.BlockDistribution;
import com.bayzyl.BlockMask;
import com.bayzyl.Clipboard;
import com.bayzyl.EditUtil;
import com.bayzyl.Selection;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;

public final class NativeEditAdapter implements EditAdapter {
    @Override
    public String getName() {
        return "bayzyl-native";
    }

    @Override
    public boolean requiresConfirm(Selection selection, boolean confirm) {
        return EditUtil.requiresConfirm(selection, confirm);
    }

    @Override
    public List<BlockChange> setBlocks(Player player, Selection selection, BlockDistribution distribution, BlockMask mask, String ifMode) {
        return EditUtil.setBlocks(player, selection, distribution, mask, ifMode);
    }

    @Override
    public List<BlockChange> replaceBlocks(Player player, Selection selection, BlockMask from, BlockDistribution toDistribution, BlockMask mask) {
        return EditUtil.replaceBlocks(player, selection, from, toDistribution, mask);
    }

    @Override
    public Clipboard copySelection(Player player, Selection selection, BlockMask mask) {
        return EditUtil.copySelection(player, selection, mask);
    }

    @Override
    public List<BlockChange> cutSelection(Player player, Selection selection, BlockMask mask) {
        return EditUtil.cutSelection(player, selection, mask);
    }

    @Override
    public List<BlockChange> pasteClipboard(Player player, Clipboard clipboard, Location target, int rotation, boolean ignoreAir) {
        return EditUtil.pasteClipboard(player, clipboard, target, rotation, ignoreAir);
    }

    @Override
    public List<BlockChange> makeWalls(Player player, Selection selection, BlockDistribution distribution, BlockMask mask) {
        return EditUtil.makeWalls(player, selection, distribution, mask);
    }

    @Override
    public List<BlockChange> overlaySelection(Player player, Selection selection, Material material, BlockMask mask) {
        return EditUtil.overlaySelection(player, selection, material, mask);
    }

    @Override
    public List<BlockChange> smoothSelection(Player player, Selection selection, int iterations) {
        return EditUtil.smoothSelection(player, selection, iterations);
    }
}
