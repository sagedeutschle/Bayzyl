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

public interface EditAdapter {
    String getName();

    boolean requiresConfirm(Selection selection, boolean confirm);

    List<BlockChange> setBlocks(Player player, Selection selection, BlockDistribution distribution, BlockMask mask, String ifMode);

    List<BlockChange> replaceBlocks(Player player, Selection selection, BlockMask from, BlockDistribution toDistribution, BlockMask mask);

    Clipboard copySelection(Player player, Selection selection, BlockMask mask);

    List<BlockChange> cutSelection(Player player, Selection selection, BlockMask mask);

    List<BlockChange> pasteClipboard(Player player, Clipboard clipboard, Location target, int rotation, boolean ignoreAir);

    List<BlockChange> makeWalls(Player player, Selection selection, BlockDistribution distribution, BlockMask mask);

    List<BlockChange> overlaySelection(Player player, Selection selection, Material material, BlockMask mask);

    List<BlockChange> smoothSelection(Player player, Selection selection, int iterations);
}
