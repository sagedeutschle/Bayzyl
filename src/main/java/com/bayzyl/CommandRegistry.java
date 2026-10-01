package com.bayzyl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class CommandRegistry {
    private static final List<CommandSpec> TOP_LEVEL = List.of(
            new CommandSpec("set", "Set blocks in a selection", "/set <block|distribution> [options]"),
            new CommandSpec("replace", "Replace blocks in a selection", "/replace <from...> <block|distribution> [options]"),
            new CommandSpec("copy", "Copy a selection", "/copy [options]"),
            new CommandSpec("cut", "Cut a selection", "/cut [options]"),
            new CommandSpec("paste", "Paste a clipboard", "/paste [-aons] [rotation:<0|90|180|270>] [confirm:true]"),
            new CommandSpec("undo", "Undo the last action", "/undo [steps:<n>]"),
            new CommandSpec("redo", "Redo the last action", "/redo [steps:<n>]"),
            new CommandSpec("clearhistory", "Clear your own Bayzyl undo/redo history", "/clearhistory"),
            new CommandSpec("move", "Move a selection", "/move <distance> [direction] [-a] [confirm:true]"),
            new CommandSpec("stack", "Stack a selection", "/stack <count> [direction] [-a] [confirm:true]"),
            new CommandSpec("rotate", "Rotate a clipboard", "/rotate <0|90|180|270|left|right|back>"),
            new CommandSpec("flip", "Flip a clipboard", "/flip <x|y|z|left-right|front-back|up-down|left|right|forward|back>"),
            new CommandSpec("sphere", "Create a sphere", "/sphere <block|distribution> <radius> [mask:<blocks>] [at:<player|target|selection-center>] [confirm:true]"),
            new CommandSpec("hsphere", "Create a hollow sphere", "/hsphere <block|distribution> <radius> [mask:<blocks>] [at:<player|target|selection-center>] [confirm:true]"),
            new CommandSpec("dome", "Create a dome", "/dome <block|distribution> <radius> [mask:<blocks>] [at:<player|target|selection-center>] [confirm:true]"),
            new CommandSpec("hdome", "Create a hollow dome", "/hdome <block|distribution> <radius> [mask:<blocks>] [at:<player|target|selection-center>] [confirm:true]"),
            new CommandSpec("bowl", "Create a bowl", "/bowl <block|distribution> <radius> [mask:<blocks>] [at:<player|target|selection-center>] [confirm:true]"),
            new CommandSpec("hbowl", "Create a hollow bowl", "/hbowl <block|distribution> <radius> [mask:<blocks>] [at:<player|target|selection-center>] [confirm:true]"),
            new CommandSpec("cyl", "Create a cylinder", "/cyl <block|distribution> <radius> [height] [mask:<blocks>] [at:<player|target|selection-center>] [confirm:true]"),
            new CommandSpec("hcyl", "Create a hollow cylinder", "/hcyl <block|distribution> <radius> [height] [mask:<blocks>] [at:<player|target|selection-center>] [confirm:true]"),
            new CommandSpec("pyramid", "Create a pyramid", "/pyramid <block|distribution> <size> [mask:<blocks>] [at:<player|target|selection-center>] [confirm:true]"),
            new CommandSpec("hpyramid", "Create a hollow pyramid", "/hpyramid <block|distribution> <size> [mask:<blocks>] [at:<player|target|selection-center>] [confirm:true]"),
            new CommandSpec("brush", "Bind, save, and load reusable brushes. Structure brushes default to at:player.", "/brush <save|load|list|delete|sphere|hsphere|cyl|hcyl|pyramid|hpyramid|structure|naturalize|clipboard|paint|spatter|replace|blend|surface|noise|restore|vegetation|decay|gen|erase|smooth|raise|lower|flatten|floatingcleanup|foliagecleanup|liquidcleanup|snowcleanup|lightcleanup|none|info> ..."),
            new CommandSpec("brushgen", "Parametric vanilla-style generation brushes (ridge/plateau/valley/cave/etc.)", "/brushgen <type> [subtype] [size:<n>] [intensity:<0..1>] [height:<n>] [mask:<blocks>] [adapt:on|off] [seed:<n>]"),
            new CommandSpec("mask", "Set or clear the mask of the brush bound to your held item", "/mask <blocks|none>"),
            new CommandSpec("gmask", "Set, toggle, or clear a global mask that filters every edit you perform", "/gmask [blocks|none|status]"),
            new CommandSpec("material", "Change the material or distribution of the brush bound to your held item", "/material <block|distribution>"),
            new CommandSpec("size", "Change the size (and optionally height) of the brush bound to your held item", "/size <radius> [height]"),
            new CommandSpec("density", "Change the scatter density of the paint or pattern brush bound to your held item", "/density <0.0-1.0>"),
            new CommandSpec("none", "Unbind the brush from your held item", "/none"),
            new CommandSpec("generate", "Generate an arbitrary formula-driven shape in a selection", "/generate <block> <expression> [mode:normalized|raw|center|origin] [hollow:true] [confirm:true]"),
            new CommandSpec("generatebiome", "Generate a biome fill, primitive shape, or formula-driven biome edit in a selection", "/generatebiome <biome> [sphere|cyl|pyramid|dome|bowl|expr <expression>] [mode:normalized|raw|center|origin] [hollow:true] [preview:true] [confirm:true]"),
            new CommandSpec("forestgen", "Generate a forest around a placement anchor", "/forestgen [size] [type] [density] [at:<player|target|selection-center>] [confirm:true]"),
            new CommandSpec("genfeature", "Place a vanilla configured feature at an anchor", "/genfeature <feature_id> [at:<player|target|center|selection-center>] [confirm:true]"),
            new CommandSpec("genstructure", "Place a vanilla structure at an anchor. Defaults to at:target.", "/genstructure <structure_id> [at:<player|target|center|selection-center>] [confirm:true]"),
            new CommandSpec("regen", "Reroll the last generated structure in the exact same place", "/regen"),
            new CommandSpec("pumpkins", "Generate pumpkin patches around a placement anchor", "/pumpkins [size] [at:<player|target|selection-center>] [confirm:true]"),
            new CommandSpec("biomeinfo", "Inspect biome composition inside a selection", "/biomeinfo"),
            new CommandSpec("measure", "Show dimensions and totals for the current selection", "/measure"),
            new CommandSpec("ruler", "Measure distance to the block you are looking at", "/ruler"),
            new CommandSpec("whereami", "Show your exact position and context", "/whereami"),
            new CommandSpec("surface", "Teleport to the nearest safe surface above", "/surface"),
            new CommandSpec("naturalize", "Naturalize a selection", "/naturalize [depth:<n>] [bedrock:on|off] [confirm:true]"),
            new CommandSpec("schematic", "Schematic save/load/list/orient", "/schematic <save|load|orient|list> [args]"),
            new CommandSpec("ascend", "Teleport to the next safe floor above", "/ascend"),
            new CommandSpec("descend", "Teleport to the nearest safe floor below", "/descend"),
            new CommandSpec("align", "Snap your facing to a cardinal direction", "/align [north|south|east|west]"),
            new CommandSpec("ceil", "Report the clearance to the ceiling above you", "/ceil"),
            new CommandSpec("centerme", "Center yourself on the current block", "/centerme"),
            new CommandSpec("ghosthand", "Toggle interact-through mode for containers and buttons", "/ghosthand"),
            new CommandSpec("profile", "Profiles and presets shortcut", "/profile <save|update|load|inspect|list|delete|rename|duplicate>"),
            new CommandSpec("kit", "Shared builder kits", "/kit <name|list|menu|load|inspect|delete|rename|duplicate|update|restoredefaults|confirm|note|theme|icon|alias>"),
            new CommandSpec("brushmenu", "Open the in-game brush menu", "/brushmenu"),
            new CommandSpec("kitmake", "Capture a shared builder kit", "/kitmake <hotbar|inventory> <name> [overwrite:true]"),
            new CommandSpec("kitlist", "Paginated shared builder kit browser", "/kitlist [page]"),
            new CommandSpec("kithelp", "Shared builder kit help", "/kithelp [topic]"),
            new CommandSpec("kitupdate", "Update a shared builder kit from your current loadout", "/kitupdate <name>"),
            new CommandSpec("kitconfirm", "Confirm a pending shared builder kit update", "/kitconfirm"),
            new CommandSpec("memreset", "Clear clipboard/history/trail state and reclaim Bayzyl edit memory", "/memreset [global]"),
            new CommandSpec("clearclipboard", "Clear your current Bayzyl clipboard only", "/clearclipboard"),
            new CommandSpec("clipboardinfo", "Show clipboard dimensions and origin", "/clipboardinfo"),
            new CommandSpec("trailclear", "Clear your recent edit trail", "/trailclear"),
            new CommandSpec("selcorners", "Show the current selection corners", "/selcorners"),
            new CommandSpec("selswap", "Swap pos1 and pos2 for the current selection", "/selswap"),
            new CommandSpec("selsave", "Save the current selection server-side", "/selsave <name>"),
            new CommandSpec("selload", "Load a saved selection", "/selload <name>"),
            new CommandSpec("selcenter", "Highlight the center of the current selection", "/selcenter"),
            new CommandSpec("pos1", "Set selection pos1 with an anchor option", "/pos1 [at:player|at:target|at:selection-center]"),
            new CommandSpec("pos2", "Set selection pos2 with an anchor option", "/pos2 [at:player|at:target|at:selection-center]"),
            new CommandSpec("jail", "Jailed admin-mode prank command", "/jail <player>"),
            new CommandSpec("liberate", "Release a jailed player", "/liberate <player>"),
            new CommandSpec("bubu", "Cute pink chat phrase command", "/bubu"),
            new CommandSpec("lol", "Broadcasts haha — deploy smoke test", "/lol"),
            new CommandSpec("susu", "Spawn a calico cat named Susu", "/susu"),
            new CommandSpec("artie", "Spawn a tuxedo cat named Artie", "/artie"),
            new CommandSpec("walls", "Create walls around a selection", "/walls <block|distribution> [options]"),
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
            new CommandSpec("cleanup", "Region cleanup utilities", "/cleanup <floatingcleanup|foliagecleanup|liquidcleanup|snowcleanup|lightcleanup|brush>"),
            new CommandSpec("detailbrush", "Preset detail brushes (flame, cloud, lightning, vine, bark)", "/detailbrush <tool|set|info|presets|save|load|code|getcode|variants|delete|mode|none> ..."),
            new CommandSpec("resume", "Resume interrupted command after server restart", "/resume"),
            new CommandSpec("redstoneaudit", "Find the most likely static wiring faults in the selected redstone machine", "/redstoneaudit [clear|show <n>|page <n>]"),
            new CommandSpec("agitate", "Trigger physics on stuck fluid blocks in a selection", "/agitate"),
            new CommandSpec("accent", "Set, reset, or inspect the Bayzyl menu accent color", "/accent <color|reset|status|#RRGGBB>"),
            new CommandSpec("authority", "Bayzyl command authority controls — claim, give up, or inspect contested labels", "/authority <status|claim|giveup>"),
            new CommandSpec("tool", "Bind a Bayzyl terrain tool (smooth, raise, lower, flatten) to your held slot", "/tool <smooth|raise|lower|flatten> <radius> [power] [bedrock:on|off]"),
            new CommandSpec("floatingcleanup", "Remove orphan floating blocks in the current selection", "/floatingcleanup [confirm:true]"),
            new CommandSpec("foliagecleanup", "Remove leaves and grass in the current selection", "/foliagecleanup [confirm:true]"),
            new CommandSpec("liquidcleanup", "Remove water and lava in the current selection", "/liquidcleanup [confirm:true]"),
            new CommandSpec("snowcleanup", "Remove snow layers in the current selection", "/snowcleanup [confirm:true]"),
            new CommandSpec("lightcleanup", "Remove redundant light-source blocks in the current selection", "/lightcleanup [confirm:true]"),
            new CommandSpec("wetoggle", "Enable or disable the WorldEdit plugin at runtime (testing aid)", "/wetoggle [on|off|toggle|status]")
    );

    private static final List<CommandSpec> BZL_SUBCOMMANDS = List.of(
            new CommandSpec("help", "Show Bayzyl help", "/bzl help [topic]"),
            new CommandSpec("selectionparticles", "Selection particle settings", "/bzl selectionparticles <subcommand>"),
            new CommandSpec("env", "Environmental and runtime settings", "/bzl env <subcommand>"),
            new CommandSpec("nudge", "Selection nudge settings", "/bzl nudge <subcommand>"),
            new CommandSpec("authority", "Give up or claim contested primary command labels", "/bzl authority <status|claim|giveup>"),
            new CommandSpec("stacklook", "Stack direction defaults", "/bzl stacklook <on|off|status>"),
            new CommandSpec("stackautomove", "Stack auto-move to end setting", "/bzl stackautomove <on|off|status|toggle>"),
            new CommandSpec("ramalert", "RAM alert controls", "/bzl ramalert <subcommand>"),
            new CommandSpec("tabmenu", "Tab menu module controls", "/bzl tabmenu <all|module|status> [on|off|status]"),
            new CommandSpec("profile", "Reusable builder profiles and presets", "/bzl profile <save|update|load|inspect|list|delete|rename|duplicate>"),
            new CommandSpec("kit", "Shared builder kits", "/bzl kit <name|list|menu|load|inspect|delete|rename|duplicate|update|restoredefaults|confirm|note|theme|icon|alias>"),
            new CommandSpec("kitmake", "Capture a shared builder kit", "/bzl kitmake <hotbar|inventory> <name> [overwrite:true]"),
            new CommandSpec("kitupdate", "Update a shared builder kit", "/bzl kitupdate <name>"),
            new CommandSpec("kitconfirm", "Confirm a pending shared builder kit update", "/bzl kitconfirm"),
            new CommandSpec("tool", "Bindable helper tools", "/bzl tool <subcommand>"),
            new CommandSpec("brush", "Create a reusable brush", "/bzl brush <shape|clipboard|paint|erase|terrain|structure|cleanup> ..."),
            new CommandSpec("palette", "Palette utilities", "/bzl palette <analyze|swap>"),
            new CommandSpec("naturalize", "Naturalize terrain", "/bzl naturalize [depth:<n>] [bedrock:on|off] [confirm:true]"),
            new CommandSpec("cleanup", "Region cleanup utilities", "/bzl cleanup <floatingcleanup|foliagecleanup|liquidcleanup|snowcleanup|lightcleanup|brush>"),
            new CommandSpec("unstick", "Movement unstuck controls", "/bzl unstick [auto <on|off|status>]"),
            new CommandSpec("audit", "Redstone audit of the selected machine", "/bzl audit [clear|show <n>|page <n>]"),
            new CommandSpec("test", "Pipeline verification ping", "/bzl test")
    );

    private CommandRegistry() {
    }

    public static List<CommandSpec> getAllCommands() {
        // Deduped by command name. Earlier entries win on conflict so the prefix list's
        // metadata is preserved when the same name also appears in TOP_LEVEL.
        Map<String, CommandSpec> byName = new LinkedHashMap<>();
        for (CommandSpec spec : List.of(
                new CommandSpec("bzl", "Bayzyl command root", "/bzl"),
                new CommandSpec("bzlhelp", "Bayzyl help shortcut", "/bzlhelp [topic]"),
                new CommandSpec("kithelp", "Shared builder kit help", "/kithelp [topic]"),
                new CommandSpec("kitlist", "Paginated shared builder kit browser", "/kitlist [page]"),
                new CommandSpec("kitupdate", "Update a shared builder kit", "/kitupdate <name>"),
                new CommandSpec("kitconfirm", "Confirm a pending shared builder kit update", "/kitconfirm"),
                new CommandSpec("bzltoggle", "Bayzyl runtime toggles", "/bzltoggle <admin|ramalert|authority>"),
                new CommandSpec("ramalert", "Bayzyl RAM alert controls", "/ramalert <on|off|status|help> [threshold:<percent>] [interval:<seconds>] [cooldown:<seconds>]"),
                new CommandSpec("step", "Move the player forward immediately", "/step [blocks]"),
                new CommandSpec("nudge", "Bayzyl nudge settings", "/nudge <status|invert|step|vertical|reset>"))) {
            byName.put(spec.name(), spec);
        }
        for (CommandSpec spec : TOP_LEVEL) {
            byName.putIfAbsent(spec.name(), spec);
        }
        return Collections.unmodifiableList(new ArrayList<>(byName.values()));
    }

    public static List<CommandSpec> getTopLevel() {
        return TOP_LEVEL;
    }

    public static List<CommandSpec> getBzlSubcommands() {
        return BZL_SUBCOMMANDS;
    }
}
