package com.bayzyl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class CommandRegistry {
    private static final List<CommandSpec> TOP_LEVEL = List.of(
            new CommandSpec("set", "Set blocks in a selection", "/set <block> [options]"),
            new CommandSpec("replace", "Replace blocks in a selection", "/replace <from> <to> [options]"),
            new CommandSpec("copy", "Copy a selection", "/copy [options]"),
            new CommandSpec("cut", "Cut a selection", "/cut [options]"),
            new CommandSpec("paste", "Paste a clipboard", "/paste [-aons] [rotation:<0|90|180|270>] [confirm:true]"),
            new CommandSpec("undo", "Undo the last action", "/undo [steps:<n>]"),
            new CommandSpec("redo", "Redo the last action", "/redo [steps:<n>]"),
            new CommandSpec("move", "Move a selection", "/move <distance> [direction] [-a] [confirm:true]"),
            new CommandSpec("stack", "Stack a selection", "/stack <count> [direction] [-a] [confirm:true]"),
            new CommandSpec("rotate", "Rotate a clipboard", "/rotate <0|90|180|270|left|right|back>"),
            new CommandSpec("flip", "Flip a clipboard", "/flip <x|y|z|left-right|front-back|up-down|left|right|forward|back>"),
            new CommandSpec("sphere", "Create a sphere", "/sphere <block> <radius> [mask:<blocks>] [at:<player|target|selection-center>] [confirm:true]"),
            new CommandSpec("hsphere", "Create a hollow sphere", "/hsphere <block> <radius> [mask:<blocks>] [at:<player|target|selection-center>] [confirm:true]"),
            new CommandSpec("dome", "Create a dome", "/dome <block> <radius> [mask:<blocks>] [at:<player|target|selection-center>] [confirm:true]"),
            new CommandSpec("hdome", "Create a hollow dome", "/hdome <block> <radius> [mask:<blocks>] [at:<player|target|selection-center>] [confirm:true]"),
            new CommandSpec("bowl", "Create a bowl", "/bowl <block> <radius> [mask:<blocks>] [at:<player|target|selection-center>] [confirm:true]"),
            new CommandSpec("hbowl", "Create a hollow bowl", "/hbowl <block> <radius> [mask:<blocks>] [at:<player|target|selection-center>] [confirm:true]"),
            new CommandSpec("cyl", "Create a cylinder", "/cyl <block> <radius> <height> [mask:<blocks>] [at:<player|target|selection-center>] [confirm:true]"),
            new CommandSpec("hcyl", "Create a hollow cylinder", "/hcyl <block> <radius> <height> [mask:<blocks>] [at:<player|target|selection-center>] [confirm:true]"),
            new CommandSpec("pyramid", "Create a pyramid", "/pyramid <block> <size> [mask:<blocks>] [at:<player|target|selection-center>] [confirm:true]"),
            new CommandSpec("hpyramid", "Create a hollow pyramid", "/hpyramid <block> <size> [mask:<blocks>] [at:<player|target|selection-center>] [confirm:true]"),
            new CommandSpec("brush", "Bind, save, and load reusable brushes", "/brush <save|load|list|delete|sphere|hsphere|cyl|hcyl|pyramid|hpyramid|naturalize|clipboard|paint|spatter|replace|blend|surface|noise|restore|vegetation|decay|erase|smooth|raise|lower|flatten|floatingcleanup|foliagecleanup|liquidcleanup|snowcleanup|lightcleanup|none|info> ..."),
            new CommandSpec("mask", "Set or clear the mask of the brush bound to your held item", "/mask <blocks|none>"),
            new CommandSpec("material", "Change the material of the brush bound to your held item", "/material <block>"),
            new CommandSpec("size", "Change the size (and optionally height) of the brush bound to your held item", "/size <radius> [height]"),
            new CommandSpec("density", "Change the scatter density of the paint or pattern brush bound to your held item", "/density <0.0-1.0>"),
            new CommandSpec("none", "Unbind the brush from your held item", "/none"),
            new CommandSpec("generate", "Generate an arbitrary formula-driven shape in a selection", "/generate <block> <expression> [mode:normalized|raw|center|origin] [hollow:true] [confirm:true]"),
            new CommandSpec("generatebiome", "Generate a biome fill, primitive shape, or formula-driven biome edit in a selection", "/generatebiome <biome> [sphere|cyl|pyramid|dome|bowl|expr <expression>] [mode:normalized|raw|center|origin] [hollow:true] [preview:true] [confirm:true]"),
            new CommandSpec("forestgen", "Generate a forest around a placement anchor", "/forestgen [size] [type] [density] [at:<player|target|selection-center>] [confirm:true]"),
            new CommandSpec("pumpkins", "Generate pumpkin patches around a placement anchor", "/pumpkins [size] [at:<player|target|selection-center>] [confirm:true]"),
            new CommandSpec("biomeinfo", "Inspect biome composition inside a selection", "/biomeinfo"),
            new CommandSpec("measure", "Show dimensions and totals for the current selection", "/measure"),
            new CommandSpec("ruler", "Measure distance to the block you are looking at", "/ruler"),
            new CommandSpec("whereami", "Show your exact position and context", "/whereami"),
            new CommandSpec("surface", "Teleport to the nearest safe surface above", "/surface"),
            new CommandSpec("naturalize", "Naturalize a selection", "/naturalize [depth:<n>] [bedrock:on|off] [confirm:true]"),
            new CommandSpec("ascend", "Teleport to the next safe floor above", "/ascend"),
            new CommandSpec("descend", "Teleport to the nearest safe floor below", "/descend"),
            new CommandSpec("align", "Snap your facing to a cardinal direction", "/align [north|south|east|west]"),
            new CommandSpec("ceil", "Report the clearance to the ceiling above you", "/ceil"),
            new CommandSpec("centerme", "Center yourself on the current block", "/centerme"),
            new CommandSpec("ghosthand", "Toggle interact-through mode for containers and buttons", "/ghosthand"),
            new CommandSpec("profile", "Profiles and presets shortcut", "/profile <save|update|load|inspect|list|delete|rename|duplicate>"),
            new CommandSpec("kit", "Shared builder kits", "/kit <name|list|menu|load|inspect|delete|rename|duplicate|update|restoredefaults|confirm|note|theme|icon|alias>"),
            new CommandSpec("kitmake", "Capture a shared builder kit", "/kitmake <hotbar|inventory> <name> [overwrite:true]"),
            new CommandSpec("kitlist", "Paginated shared builder kit browser", "/kitlist [page]"),
            new CommandSpec("kithelp", "Shared builder kit help", "/kithelp [topic]"),
            new CommandSpec("kitupdate", "Update a shared builder kit from your current loadout", "/kitupdate <name>"),
            new CommandSpec("kitconfirm", "Confirm a pending shared builder kit update", "/kitconfirm"),
            new CommandSpec("clipboardinfo", "Show clipboard dimensions and origin", "/clipboardinfo"),
            new CommandSpec("trailclear", "Clear your recent edit trail", "/trailclear"),
            new CommandSpec("selcorners", "Show the current selection corners", "/selcorners"),
            new CommandSpec("selswap", "Swap pos1 and pos2 for the current selection", "/selswap"),
            new CommandSpec("selsave", "Save the current selection server-side", "/selsave <name>"),
            new CommandSpec("selload", "Load a saved selection", "/selload <name>"),
            new CommandSpec("selcenter", "Highlight the center of the current selection", "/selcenter"),
            new CommandSpec("walls", "Create walls around a selection", "/walls <block> [options]"),
            new CommandSpec("overlay", "Overlay blocks on top of a selection", "/overlay <block> [options]"),
            new CommandSpec("smooth", "Smooth a terrain selection", "/smooth [options]"),
            new CommandSpec("palette", "Palette utilities", "/palette <analyze|swap>"),
            new CommandSpec("select", "Selection utilities", "/select <subcommand>"),
            new CommandSpec("expand", "Expand a selection", "/expand <amount|all amount> [direction|dir:<direction>]"),
            new CommandSpec("contract", "Contract a selection", "/contract <amount|all amount> [direction|dir:<direction>]"),
            new CommandSpec("unstick", "Move yourself to the nearest safe open space", "/unstick [auto <on|off|status>]"),
            new CommandSpec("thru", "Teleport through the wall you are looking at", "/thru"),
            new CommandSpec("tabmenu", "Tab menu module controls", "/tabmenu <all|module|status> [on|off|status]"),
            new CommandSpec("wand", "Get the selection wand", "/wand"),
            new CommandSpec("eraser", "Get the eraser tool", "/eraser"),
            new CommandSpec("nightvision", "Toggle night vision", "/nightvision [on|off|toggle]"),
            new CommandSpec("particlevisualtoggle", "Toggle selection particle visualization", "/particlevisualtoggle"),
            new CommandSpec("oops", "Undo your latest Bayzyl action with a broadcast", "/oops [steps:<n>]"),
            new CommandSpec("cleanup", "Region cleanup utilities", "/cleanup <floatingcleanup|foliagecleanup|liquidcleanup|snowcleanup|lightcleanup|brush>")
    );

    private static final List<CommandSpec> BZL_SUBCOMMANDS = List.of(
            new CommandSpec("help", "Show Bayzyl help", "/bzl help [topic]"),
            new CommandSpec("selectionparticles", "Selection particle settings", "/bzl selectionparticles <subcommand>"),
            new CommandSpec("env", "Environmental and runtime settings", "/bzl env <subcommand>"),
            new CommandSpec("nudge", "Selection nudge settings", "/bzl nudge <subcommand>"),
            new CommandSpec("ramalert", "RAM alert controls", "/bzl ramalert <subcommand>"),
            new CommandSpec("tabmenu", "Tab menu module controls", "/bzl tabmenu <all|module|status> [on|off|status]"),
            new CommandSpec("profile", "Reusable builder profiles and presets", "/bzl profile <save|update|load|inspect|list|delete|rename|duplicate>"),
            new CommandSpec("kit", "Shared builder kits", "/bzl kit <name|list|menu|load|inspect|delete|rename|duplicate|update|restoredefaults|confirm|note|theme|icon|alias>"),
            new CommandSpec("kitmake", "Capture a shared builder kit", "/bzl kitmake <hotbar|inventory> <name> [overwrite:true]"),
            new CommandSpec("kitupdate", "Update a shared builder kit", "/bzl kitupdate <name>"),
            new CommandSpec("kitconfirm", "Confirm a pending shared builder kit update", "/bzl kitconfirm"),
            new CommandSpec("tool", "Bindable helper tools", "/bzl tool <subcommand>"),
            new CommandSpec("brush", "Create a reusable brush", "/bzl brush <shape|clipboard|paint|erase|terrain|cleanup> ..."),
            new CommandSpec("palette", "Palette utilities", "/bzl palette <analyze|swap>"),
            new CommandSpec("naturalize", "Naturalize terrain", "/bzl naturalize [depth:<n>] [bedrock:on|off] [confirm:true]"),
            new CommandSpec("cleanup", "Region cleanup utilities", "/bzl cleanup <floatingcleanup|foliagecleanup|liquidcleanup|snowcleanup|lightcleanup|brush>"),
            new CommandSpec("unstick", "Movement unstuck controls", "/bzl unstick [auto <on|off|status>]")
    );

    private CommandRegistry() {
    }

    public static List<CommandSpec> getAllCommands() {
        List<CommandSpec> all = new ArrayList<>();
        all.add(new CommandSpec("bzl", "Bayzyl command root", "/bzl"));
        all.add(new CommandSpec("bzlhelp", "Bayzyl help shortcut", "/bzlhelp [topic]"));
        all.add(new CommandSpec("kithelp", "Shared builder kit help", "/kithelp [topic]"));
        all.add(new CommandSpec("kitlist", "Paginated shared builder kit browser", "/kitlist [page]"));
        all.add(new CommandSpec("kitupdate", "Update a shared builder kit", "/kitupdate <name>"));
        all.add(new CommandSpec("kitconfirm", "Confirm a pending shared builder kit update", "/kitconfirm"));
        all.add(new CommandSpec("bzltoggle", "Bayzyl runtime toggles", "/bzltoggle <admin|ramalert>"));
        all.add(new CommandSpec("ramalert", "Bayzyl RAM alert controls", "/ramalert <on|off|status|help> [options]"));
        all.add(new CommandSpec("nudge", "Bayzyl nudge settings", "/nudge <status|invert|step|vertical|reset>"));
        all.addAll(TOP_LEVEL);
        return Collections.unmodifiableList(all);
    }

    public static List<CommandSpec> getTopLevel() {
        return TOP_LEVEL;
    }

    public static List<CommandSpec> getBzlSubcommands() {
        return BZL_SUBCOMMANDS;
    }
}
