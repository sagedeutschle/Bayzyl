import hashlib
import re
from typing import Dict, Any, Optional, Tuple, List


def _seed_from_name(name: str) -> int:
    h = hashlib.sha256(name.encode("utf-8")).hexdigest()
    return int(h[:8], 16)


def _slug_from_brief(brief: str) -> str:
    tokens = re.findall(r"[a-z0-9]+", brief.lower())
    if not tokens:
        return "terrain_brief"
    stop = {"with", "a", "the", "and", "of", "to", "in", "for"}
    filtered = [t for t in tokens if t not in stop]
    return "_".join((filtered or tokens)[:4])


def _detect_preset(text: str) -> Optional[str]:
    if "volcanic" in text or "hellscape" in text or "lava" in text:
        return "volcanic_hellscape"
    if "basalt delta" in text:
        return "basalt_deltas"
    if "oasis" in text:
        return "oasis"
    if "jungle" in text:
        return "dense_jungle"
    if "mangrove" in text:
        return "mangrove_marsh"
    if "redwood" in text or "giant trees" in text:
        return "redwood_grove"
    if "mushroom" in text:
        return "mushroom_isles"
    if "badlands" in text or "mesa" in text:
        return "badlands"
    if "swamp" in text:
        return "swamp"
    if "savanna" in text:
        return "savanna"
    if "taiga" in text:
        return "taiga"
    if "snowy" in text or "alpine" in text or "frozen" in text:
        return "snowy_taiga"
    if "desert" in text:
        return "desert"
    if "forest" in text:
        return "forest"
    if "mountain" in text or "peaks" in text:
        return "mountains"
    if "crystal" in text or "spires" in text:
        return "crystal_spires"
    if "cherry" in text or "blossom" in text:
        return "cherry_grove"
    if "ashen" in text or "wasteland" in text:
        return "ashen_wastes"
    if "floating island" in text or "sky island" in text:
        return "floating_islands"
    if "lush valley" in text:
        return "lush_valley"
    if "coral" in text or "archipelago" in text:
        return "coral_archipelago"
    if "icy spires" in text:
        return "icy_spires"
    return None


def _preset_config(preset: str, size: Dict[str, int]) -> Dict[str, Any]:
    if preset == "volcanic_hellscape":
        return {
            "amplitude": 24,
            "scale": 0.045,
            "layers": [
                {"name": "bedrock", "block": "minecraft:bedrock", "thickness": 1, "fromTop": False},
                {"name": "basalt", "block": "minecraft:basalt", "thickness": size["y"] - 6, "fromTop": False},
                {"name": "blackstone", "block": "minecraft:blackstone", "thickness": 3, "fromTop": True},
                {"name": "magma", "block": "minecraft:magma_block", "thickness": 1, "fromTop": True},
            ],
            "surface": {
                "grassBlock": "minecraft:blackstone",
                "rockBlock": "minecraft:basalt",
                "snowBlock": "minecraft:blackstone",
                "snowline": size["y"] + 1,
                "rockSlope": 3,
            },
            "paletteRules": [
                {"block": "minecraft:basalt", "weight": 1.0, "conditions": {"minY": int(size["y"] * 0.1)}},
                {"block": "minecraft:blackstone", "weight": 1.0, "conditions": {"maxY": int(size["y"] * 0.2)}},
            ],
            "features": [
                {"type": "cliffs", "angleThreshold": 35},
                {
                    "type": "lava",
                    "surfaceLevel": int(size["y"] * 0.3),
                    "surfaceCount": 6,
                    "surfaceRadius": [3, 7],
                    "caveCount": 8,
                    "caveMinY": 6,
                    "caveMaxY": int(size["y"] * 0.5),
                    "caveRadius": [2, 4],
                    "wallBlock": "minecraft:blackstone",
                },
            ],
        }
    if preset == "dense_jungle":
        return {
            "amplitude": 16,
            "scale": 0.07,
            "layers": None,
            "forestDensity": "dense",
            "treeMix": [
                {
                    "type": "jungle",
                    "weight": 0.7,
                    "variants": [
                        {"minHeight": 16, "maxHeight": 24, "canopy": 6, "root": 3},
                        {"minHeight": 10, "maxHeight": 16, "canopy": 5, "root": 2},
                    ],
                    "canopyShapes": ["oak", "irregular"],
                },
                {"type": "oak", "weight": 0.3},
            ],
            "features": [
                {"type": "water", "seaLevel": int(size["y"] * 0.35), "lakeCount": 2, "lakeRadius": [4, 7], "wallBlock": "minecraft:dirt"},
                {"type": "fallen_logs", "count": 8, "block": "minecraft:jungle_log"},
                {"type": "boulders", "count": 6, "radius": [2, 3], "block": "minecraft:mossy_cobblestone"},
            ],
        }
    if preset == "oasis":
        return {
            "amplitude": 8,
            "scale": 0.1,
            "layers": [
                {"name": "bedrock", "block": "minecraft:bedrock", "thickness": 1, "fromTop": False},
                {"name": "sandstone", "block": "minecraft:sandstone", "thickness": size["y"] - 6, "fromTop": False},
                {"name": "sand", "block": "minecraft:sand", "thickness": 3, "fromTop": True},
                {"name": "grass", "block": "minecraft:grass_block", "thickness": 1, "fromTop": True},
            ],
            "features": [
                {
                    "type": "water",
                    "seaLevel": int(size["y"] * 0.35),
                    "lakeCount": 2,
                    "lakeRadius": [6, 10],
                    "depth": [2, 4],
                    "edgeBlocks": ["minecraft:sand", "minecraft:dirt", "minecraft:grass_block"],
                    "wallBlock": "minecraft:sandstone",
                },
                {"type": "reeds", "count": 24, "block": "minecraft:sugar_cane"},
                {
                    "type": "surface_patches",
                    "count": 10,
                    "radius": [3, 6],
                    "block": "minecraft:grass_block",
                    "baseBlocks": ["minecraft:sand", "minecraft:sandstone"],
                },
            ],
            "forestDensity": "sparse",
            "treeMix": [
                {"type": "palm", "weight": 0.5, "canopyShapes": ["palm"]},
                {"type": "acacia", "weight": 0.3, "canopyShapes": ["oak", "irregular"]},
                {"type": "oak", "weight": 0.2, "canopyShapes": ["oak", "irregular"]},
            ],
        }
    if preset == "crystal_spires":
        return {
            "amplitude": 20,
            "scale": 0.055,
            "layers": [
                {"name": "bedrock", "block": "minecraft:bedrock", "thickness": 1, "fromTop": False},
                {"name": "stone", "block": "minecraft:stone", "thickness": size["y"] - 6, "fromTop": False},
                {"name": "calcite", "block": "minecraft:calcite", "thickness": 2, "fromTop": True},
                {"name": "amethyst", "block": "minecraft:amethyst_block", "thickness": 1, "fromTop": True},
            ],
            "landmarks": [
                {"type": "mountain", "x": size["x"] // 3, "z": size["z"] // 3, "radius": min(size["x"], size["z"]) // 5, "height": size["y"] // 2},
                {"type": "mountain", "x": size["x"] * 2 // 3, "z": size["z"] * 2 // 3, "radius": min(size["x"], size["z"]) // 6, "height": size["y"] // 3},
            ],
            "features": [
                {
                    "type": "spires",
                    "count": 10,
                    "height": [18, 36],
                    "block": "minecraft:calcite",
                    "blocks": ["minecraft:calcite", "minecraft:amethyst_block"],
                    "accentBlocks": ["minecraft:amethyst_block"],
                    "accentChance": 0.35,
                    "baseRadius": [2, 5],
                    "cluster": 2,
                    "shardCount": 3,
                },
                {"type": "boulders", "count": 6, "radius": [2, 4], "block": "minecraft:calcite"},
            ],
        }
    if preset == "basalt_deltas":
        return {
            "amplitude": 14,
            "scale": 0.08,
            "layers": [
                {"name": "bedrock", "block": "minecraft:bedrock", "thickness": 1, "fromTop": False},
                {"name": "basalt", "block": "minecraft:basalt", "thickness": size["y"] - 6, "fromTop": False},
                {"name": "blackstone", "block": "minecraft:blackstone", "thickness": 2, "fromTop": True},
            ],
            "features": [
                {
                    "type": "lava",
                    "surfaceLevel": int(size["y"] * 0.28),
                    "surfaceCount": 4,
                    "surfaceRadius": [3, 6],
                    "caveCount": 6,
                    "caveMinY": 6,
                    "caveMaxY": int(size["y"] * 0.4),
                    "caveRadius": [2, 4],
                    "wallBlock": "minecraft:blackstone",
                }
            ],
        }
    if preset == "mangrove_marsh":
        return {
            "amplitude": 6,
            "scale": 0.12,
            "layers": [
                {"name": "bedrock", "block": "minecraft:bedrock", "thickness": 1, "fromTop": False},
                {"name": "dirt", "block": "minecraft:dirt", "thickness": size["y"] - 6, "fromTop": False},
                {"name": "mud", "block": "minecraft:mud", "thickness": 2, "fromTop": True},
            ],
            "features": [{"type": "water", "seaLevel": int(size["y"] * 0.45), "lakeCount": 4, "lakeRadius": [4, 8], "wallBlock": "minecraft:dirt"}],
            "forestDensity": "medium",
            "treeMix": [{"type": "mangrove", "weight": 1.0}],
        }
    if preset == "redwood_grove":
        return {
            "amplitude": 10,
            "scale": 0.09,
            "forestDensity": "medium",
            "treeMix": [
                {"type": "spruce", "weight": 0.7, "variants": [{"minHeight": 20, "maxHeight": 28, "canopy": 6, "root": 3}]},
                {"type": "dark_oak", "weight": 0.3, "variants": [{"minHeight": 10, "maxHeight": 14, "canopy": 5, "root": 2}]},
            ],
            "features": [{"type": "fallen_logs", "count": 6, "block": "minecraft:spruce_log"}],
        }
    if preset == "mushroom_isles":
        return {
            "amplitude": 8,
            "scale": 0.1,
            "layers": [
                {"name": "bedrock", "block": "minecraft:bedrock", "thickness": 1, "fromTop": False},
                {"name": "dirt", "block": "minecraft:dirt", "thickness": size["y"] - 6, "fromTop": False},
                {"name": "mycelium", "block": "minecraft:mycelium", "thickness": 2, "fromTop": True},
            ],
            "forestDensity": "sparse",
            "treeMix": [],
            "features": [{"type": "mushrooms", "count": 12, "block": "minecraft:red_mushroom_block"}],
        }
    if preset == "cherry_grove":
        return {
            "amplitude": 8,
            "scale": 0.1,
            "layers": [
                {"name": "bedrock", "block": "minecraft:bedrock", "thickness": 1, "fromTop": False},
                {"name": "stone", "block": "minecraft:stone", "thickness": size["y"] - 8, "fromTop": False},
                {"name": "dirt", "block": "minecraft:dirt", "thickness": 3, "fromTop": True},
                {"name": "grass", "block": "minecraft:grass_block", "thickness": 1, "fromTop": True},
            ],
            "features": [
                {
                    "type": "water",
                    "seaLevel": int(size["y"] * 0.32),
                    "lakeCount": 5,
                    "lakeRadius": [4, 7],
                    "depth": [2, 4],
                    "edgeBlocks": ["minecraft:grass_block", "minecraft:dirt"],
                    "wallBlock": "minecraft:dirt",
                },
                {"type": "lilypads", "density": 0.35},
                {"type": "reeds", "count": 18, "block": "minecraft:sugar_cane"},
                {"type": "surface_patches", "count": 16, "radius": [3, 6], "block": "minecraft:pink_petals", "baseBlocks": ["minecraft:grass_block"]},
                {"type": "surface_patches", "count": 6, "radius": [3, 6], "block": "minecraft:moss_block", "baseBlocks": ["minecraft:grass_block"]},
            ],
            "forestDensity": "medium",
            "treeMix": [
                {"type": "cherry", "weight": 0.8, "variants": [{"minHeight": 7, "maxHeight": 11, "canopy": 4, "root": 1}], "canopyShapes": ["oak", "irregular"]},
                {"type": "oak", "weight": 0.2, "variants": [{"minHeight": 6, "maxHeight": 9, "canopy": 3, "root": 1}]},
            ],
        }
    if preset == "floating_islands":
        return {
            "amplitude": 6,
            "scale": 0.1,
            "landmarks": [
                {"type": "plateau", "x": size["x"] // 2, "z": size["z"] // 2, "radius": min(size["x"], size["z"]) // 3, "plateauY": int(size["y"] * 0.7)}
            ],
            "features": [],
        }
    if preset == "lush_valley":
        return {
            "amplitude": 8,
            "scale": 0.1,
            "features": [
                {
                    "type": "water",
                    "seaLevel": int(size["y"] * 0.32),
                    "lakeCount": 2,
                    "lakeRadius": [6, 10],
                    "depth": [2, 4],
                    "edgeBlocks": ["minecraft:grass_block", "minecraft:dirt"],
                    "wallBlock": "minecraft:dirt",
                },
                {
                    "type": "surface_patches",
                    "count": 8,
                    "radius": [4, 7],
                    "block": "minecraft:moss_block",
                    "baseBlocks": ["minecraft:grass_block"],
                },
                {"type": "boulders", "count": 6, "radius": [2, 3], "block": "minecraft:stone"},
                {"type": "fallen_logs", "count": 4, "block": "minecraft:oak_log"},
            ],
            "forestDensity": "medium",
            "treeMix": [{"type": "oak", "weight": 0.6}, {"type": "birch", "weight": 0.4}],
        }
    if preset == "coral_archipelago":
        return {
            "amplitude": 4,
            "scale": 0.12,
            "features": [
                {
                    "type": "sea",
                    "seaLevel": int(size["y"] * 0.32),
                    "shoreBlocks": ["minecraft:sand", "minecraft:sandstone"],
                    "carveAbove": True,
                },
                {
                    "type": "islands",
                    "count": 10,
                    "radius": [6, 12],
                    "height": [3, 7],
                    "topBlock": "minecraft:sand",
                    "fillBlock": "minecraft:sandstone",
                },
                {
                    "type": "coral",
                    "count": 16,
                    "radius": [2, 4],
                    "blocks": ["minecraft:brain_coral_block", "minecraft:fire_coral_block", "minecraft:horn_coral_block"],
                },
            ],
        }
    if preset == "icy_spires":
        return {
            "amplitude": 14,
            "scale": 0.08,
            "layers": [
                {"name": "bedrock", "block": "minecraft:bedrock", "thickness": 1, "fromTop": False},
                {"name": "stone", "block": "minecraft:stone", "thickness": size["y"] - 6, "fromTop": False},
                {"name": "packed_ice", "block": "minecraft:packed_ice", "thickness": 2, "fromTop": True},
                {"name": "snow", "block": "minecraft:snow_block", "thickness": 1, "fromTop": True},
            ],
            "landmarks": [
                {"type": "mountain", "x": size["x"] // 3, "z": size["z"] // 3, "radius": min(size["x"], size["z"]) // 6, "height": size["y"] // 3},
                {"type": "mountain", "x": size["x"] * 2 // 3, "z": size["z"] * 2 // 3, "radius": min(size["x"], size["z"]) // 5, "height": size["y"] // 2},
            ],
            "features": [{"type": "spires", "count": 6, "height": [8, 18], "block": "minecraft:packed_ice"}],
        }
    if preset == "ashen_wastes":
        return {
            "amplitude": 12,
            "scale": 0.08,
            "layers": [
                {"name": "bedrock", "block": "minecraft:bedrock", "thickness": 1, "fromTop": False},
                {"name": "netherrack", "block": "minecraft:netherrack", "thickness": size["y"] - 6, "fromTop": False},
                {"name": "basalt", "block": "minecraft:basalt", "thickness": 2, "fromTop": True},
            ],
        }
    if preset == "mountains":
        return {
            "amplitude": 22,
            "scale": 0.05,
            "landmarks": [
                {"type": "mountain", "x": size["x"] // 2, "z": size["z"] // 2, "radius": min(size["x"], size["z"]) // 3, "height": size["y"] // 2}
            ],
        }
    if preset == "desert":
        return {
            "amplitude": 8,
            "scale": 0.1,
            "layers": [
                {"name": "bedrock", "block": "minecraft:bedrock", "thickness": 1, "fromTop": False},
                {"name": "sandstone", "block": "minecraft:sandstone", "thickness": size["y"] - 6, "fromTop": False},
                {"name": "sand", "block": "minecraft:sand", "thickness": 3, "fromTop": True},
            ],
        }
    if preset == "savanna":
        return {
            "amplitude": 10,
            "scale": 0.09,
            "forestDensity": "sparse",
            "treeMix": [{"type": "acacia", "weight": 1.0}],
        }
    if preset == "taiga":
        return {
            "amplitude": 12,
            "scale": 0.08,
            "forestDensity": "medium",
            "treeMix": [{"type": "spruce", "weight": 1.0}],
        }
    if preset == "snowy_taiga":
        return {
            "amplitude": 14,
            "scale": 0.07,
            "forestDensity": "medium",
            "treeMix": [{"type": "spruce", "weight": 1.0}],
        }
    if preset == "forest":
        return {
            "amplitude": 10,
            "scale": 0.09,
            "forestDensity": "medium",
            "treeMix": [{"type": "oak", "weight": 0.7}, {"type": "birch", "weight": 0.3}],
        }
    if preset == "swamp":
        return {
            "amplitude": 6,
            "scale": 0.12,
            "forestDensity": "medium",
            "treeMix": [{"type": "oak", "weight": 1.0}],
            "features": [{"type": "water", "seaLevel": int(size["y"] * 0.4), "lakeCount": 3, "lakeRadius": [4, 8], "wallBlock": "minecraft:dirt"}],
        }
    if preset == "badlands":
        return {
            "amplitude": 16,
            "scale": 0.07,
            "layers": [
                {"name": "bedrock", "block": "minecraft:bedrock", "thickness": 1, "fromTop": False},
                {"name": "terracotta", "block": "minecraft:terracotta", "thickness": size["y"] - 6, "fromTop": False},
                {"name": "red_sand", "block": "minecraft:red_sand", "thickness": 3, "fromTop": True},
            ],
        }
    return {}


def synthesize_spec(brief: str, size: Dict[str, int]) -> Dict[str, Any]:
    text = brief.lower()
    name = _slug_from_brief(brief)
    seed = _seed_from_name(brief)
    if "amoeba" in text and "lush valley" in text:
        preset = "blended_scene"
    else:
        preset = _detect_preset(text)
    preset_cfg = _preset_config(preset, size) if preset else {}

    is_snowy = "snow" in text or "alpine" in text
    has_cliffs = "cliff" in text or "ridge" in text or "rugged" in text
    has_lakes = "lake" in text or "water" in text
    sparse_trees = "sparse" in text
    is_volcanic = "volcanic" in text or "hellscape" in text or "lava" in text
    is_obsidian = "obsidian" in text or is_volcanic
    is_basalt = "basalt" in text or is_volcanic
    tree_type = "spruce" if ("pine" in text or "spruce" in text or "alpine" in text) else "oak"

    amplitude = preset_cfg.get("amplitude", 22 if has_cliffs else 10)
    scale = preset_cfg.get("scale", 0.05 if has_cliffs else 0.09)

    surface_rock = "minecraft:stone"
    surface_grass = "minecraft:grass_block"
    surface_snow = "minecraft:snow_block"
    if is_obsidian:
        surface_rock = "minecraft:obsidian"
        surface_grass = "minecraft:obsidian"
        surface_snow = "minecraft:obsidian"
    if is_basalt:
        surface_rock = "minecraft:basalt"
        surface_grass = "minecraft:basalt"
    if is_volcanic:
        surface_rock = "minecraft:basalt"
        surface_grass = "minecraft:blackstone"

    if is_volcanic:
        layers = [
            {"name": "bedrock", "block": "minecraft:bedrock", "thickness": 1, "fromTop": False},
            {"name": "basalt", "block": "minecraft:basalt", "thickness": size["y"] - 6, "fromTop": False},
            {"name": "blackstone", "block": "minecraft:blackstone", "thickness": 3, "fromTop": True},
            {"name": "magma", "block": "minecraft:magma_block", "thickness": 1, "fromTop": True},
        ]
    else:
        layers = [
            {"name": "bedrock", "block": "minecraft:bedrock", "thickness": 1, "fromTop": False},
            {"name": "stone", "block": "minecraft:stone", "thickness": size["y"] - 8, "fromTop": False},
            {"name": "dirt", "block": "minecraft:dirt", "thickness": 3, "fromTop": True},
            {"name": "grass", "block": surface_grass, "thickness": 1, "fromTop": True},
        ]
    if preset_cfg.get("layers"):
        layers = preset_cfg["layers"]
    if is_snowy and not preset_cfg.get("layers"):
        layers.append({"name": "snow", "block": surface_snow, "thickness": 1, "fromTop": True})

    features = []
    if has_cliffs:
        features.append({"type": "cliffs", "angleThreshold": 35})
    forest_density = preset_cfg.get("forestDensity")
    if "dense" in text:
        forest_density = "dense"
    if "sparse" in text:
        forest_density = "sparse"
    if ("tree" in text or "pine" in text or "spruce" in text or "forest" in text or forest_density) and "no trees" not in text:
        density = 0.01 if sparse_trees else 0.03
        if forest_density == "dense":
            density = 0.08
        elif forest_density == "medium":
            density = 0.04
        elif forest_density == "sparse":
            density = 0.015
        features.append(
            {
                "type": "trees",
                "treeType": tree_type,
                "density": density,
                "minY": 18,
                "variants": [
                    {"minHeight": 7, "maxHeight": 10, "canopy": 3, "root": 1},
                    {"minHeight": 10, "maxHeight": 14, "canopy": 4, "root": 2},
                    {"minHeight": 14, "maxHeight": 19, "canopy": 5, "root": 3},
                ],
                "canopyShapes": ["spruce", "oak", "irregular"],
                "lean": 0.15,
                "treeMix": preset_cfg.get("treeMix", []),
            }
        )
    if has_lakes:
        features.append(
            {
                "type": "water",
                "seaLevel": size["y"] // 3,
                "lakeCount": 5,
                "lakeRadius": [3, 6],
                "wallBlock": "minecraft:stone",
            }
        )
    if "cave" in text or "caves" in text:
        if "vanilla" in text:
            features.append({"type": "caves", "preset": "vanilla_like", "threshold": 0.35, "scale": 0.08, "octaves": 3})
        else:
            features.append({"type": "caves", "wormCount": 30, "steps": 80})
    if is_volcanic and not preset_cfg.get("features"):
        features.append(
            {
                "type": "lava",
                "surfaceLevel": int(size["y"] * 0.3),
                "surfaceCount": 6,
                "surfaceRadius": [3, 7],
                "caveCount": 8,
                "caveMinY": 6,
                "caveMaxY": int(size["y"] * 0.5),
                "caveRadius": [2, 4],
                "wallBlock": "minecraft:blackstone",
            }
        )
    if preset_cfg.get("features"):
        features.extend(preset_cfg["features"])

    whitelist = [
        "minecraft:air",
        "minecraft:bedrock",
        "minecraft:stone",
        "minecraft:dirt",
        "minecraft:grass_block",
        "minecraft:snow_block",
        "minecraft:water",
        "minecraft:spruce_log",
        "minecraft:spruce_leaves",
        "minecraft:oak_log",
        "minecraft:oak_leaves",
        "minecraft:birch_log",
        "minecraft:birch_leaves",
        "minecraft:jungle_log",
        "minecraft:jungle_leaves",
        "minecraft:acacia_log",
        "minecraft:acacia_leaves",
        "minecraft:dark_oak_log",
        "minecraft:dark_oak_leaves",
        "minecraft:mangrove_log",
        "minecraft:mangrove_leaves",
        "minecraft:cherry_log",
        "minecraft:cherry_leaves",
        "minecraft:lily_pad",
        "minecraft:basalt",
        "minecraft:blackstone",
        "minecraft:magma_block",
        "minecraft:lava",
        "minecraft:obsidian",
        "minecraft:netherrack",
        "minecraft:sand",
        "minecraft:sandstone",
        "minecraft:red_sand",
        "minecraft:terracotta",
        "minecraft:calcite",
        "minecraft:amethyst_block",
        "minecraft:packed_ice",
        "minecraft:mycelium",
        "minecraft:moss_block",
        "minecraft:pink_petals",
        "minecraft:mud",
        "minecraft:mossy_cobblestone",
        "minecraft:mushroom_stem",
        "minecraft:sugar_cane",
        "minecraft:brain_coral_block",
        "minecraft:fire_coral_block",
        "minecraft:horn_coral_block",
    ]

    slope_spec = None
    slope_match = re.search(r"from\\s*y\\s*(\\d+)\\s*to\\s*y\\s*(\\d+)", text)
    if slope_match:
        start_y = int(slope_match.group(1))
        end_y = int(slope_match.group(2))
        axis = "x"
        if "north" in text or "south" in text:
            axis = "z"
        slope_spec = {
            "axis": axis,
            "startY": start_y,
            "endY": end_y,
            "direction": "positive",
            "blend": 0.9 if is_obsidian else 0.5,
        }
    elif "slope" in text or "downward" in text:
        slope_spec = {"axis": "x", "startY": size["y"] - 1, "endY": 2, "direction": "positive", "blend": 0.3}

    snowline = int(size["y"] * (0.35 if is_snowy else 0.9))
    rock_slope = 4 if has_cliffs else 5
    if is_volcanic:
        snowline = size["y"] + 1
    if preset_cfg.get("surface"):
        surface_rock = preset_cfg["surface"].get("rockBlock", surface_rock)
        surface_grass = preset_cfg["surface"].get("grassBlock", surface_grass)
        surface_snow = preset_cfg["surface"].get("snowBlock", surface_snow)
        snowline = preset_cfg["surface"].get("snowline", snowline)
        rock_slope = preset_cfg["surface"].get("rockSlope", rock_slope)

    palette_rules = []
    if is_volcanic:
        palette_rules = [
            {"block": surface_rock, "weight": 1.0, "conditions": {"minY": int(size["y"] * 0.1)}},
            {"block": "minecraft:blackstone", "weight": 1.0, "conditions": {"maxY": int(size["y"] * 0.2)}},
        ]
    elif is_snowy:
        palette_rules = [
            {"block": "minecraft:snow_block", "weight": 1.0, "conditions": {"minY": snowline}},
            {"block": surface_grass, "weight": 1.0, "conditions": {"maxY": snowline - 1}},
        ]
    else:
        palette_rules = [
            {"block": surface_grass, "weight": 1.0, "conditions": {"minY": int(size["y"] * 0.2)}},
        ]
    if preset_cfg.get("paletteRules"):
        palette_rules = preset_cfg["paletteRules"]

    if preset == "blended_scene":
        valley_preset = _preset_config("lush_valley", size)
        spire_preset = _preset_config("crystal_spires", size)
        oasis_preset = _preset_config("oasis", size)
        coral_preset = _preset_config("coral_archipelago", size)
        blended = {
            "biomeBlend": {
                "presets": {
                    "lush_valley": {
                        "surface": {
                            "grassBlock": "minecraft:grass_block",
                            "rockBlock": "minecraft:stone",
                            "snowBlock": "minecraft:grass_block",
                            "snowline": size["y"] + 1,
                            "rockSlope": 4,
                        },
                        "heightfield": {"seed": seed + 1, "octaves": 4, "scale": 0.09, "amplitude": 10, "base": int(size["y"] * 0.25)},
                        "features": [
                            {"type": "trees", "density": 0.03, "minY": 8, "treeMix": [{"type": "oak", "weight": 0.7}, {"type": "birch", "weight": 0.3}], "canopyShapes": ["oak", "irregular"]},
                            {"type": "water", "seaLevel": int(size["y"] * 0.32), "lakeCount": 1, "lakeRadius": [6, 10], "edgeBlocks": ["minecraft:grass_block", "minecraft:dirt"], "wallBlock": "minecraft:dirt"},
                            {"type": "surface_patches", "count": 6, "radius": [4, 7], "block": "minecraft:moss_block", "baseBlocks": ["minecraft:grass_block"]},
                        ],
                    },
                    "crystal_spires": {
                        "surface": {
                            "grassBlock": "minecraft:calcite",
                            "rockBlock": "minecraft:stone",
                            "snowBlock": "minecraft:amethyst_block",
                            "snowline": size["y"] + 1,
                            "rockSlope": 3,
                        },
                        "heightfield": {"seed": seed + 2, "octaves": 4, "scale": 0.06, "amplitude": 18, "base": int(size["y"] * 0.35)},
                        "features": [
                            {
                                "type": "spires",
                                "count": 8,
                                "height": [16, 32],
                                "block": "minecraft:calcite",
                                "blocks": ["minecraft:calcite", "minecraft:amethyst_block"],
                                "accentBlocks": ["minecraft:amethyst_block"],
                                "accentChance": 0.35,
                                "baseRadius": [2, 5],
                                "cluster": 2,
                                "shardCount": 3,
                            }
                        ],
                    },
                    "oasis": {
                        "surface": {
                            "grassBlock": "minecraft:sand",
                            "rockBlock": "minecraft:sandstone",
                            "snowBlock": "minecraft:sand",
                            "snowline": size["y"] + 1,
                            "rockSlope": 5,
                        },
                        "heightfield": {"seed": seed + 3, "octaves": 3, "scale": 0.1, "amplitude": 8, "base": int(size["y"] * 0.22)},
                        "features": [
                            {"type": "water", "seaLevel": int(size["y"] * 0.35), "lakeCount": 2, "lakeRadius": [8, 12], "edgeBlocks": ["minecraft:sand", "minecraft:dirt", "minecraft:grass_block"], "wallBlock": "minecraft:sandstone"},
                            {"type": "trees", "density": 0.02, "minY": 6, "treeMix": [{"type": "palm", "weight": 0.5, "canopyShapes": ["palm"]}, {"type": "acacia", "weight": 0.3}, {"type": "oak", "weight": 0.2}], "canopyShapes": ["oak", "irregular"]},
                            {"type": "reeds", "count": 18, "block": "minecraft:sugar_cane"},
                            {"type": "surface_patches", "count": 8, "radius": [3, 6], "block": "minecraft:grass_block", "baseBlocks": ["minecraft:sand", "minecraft:sandstone"]},
                        ],
                    },
                    "coral_archipelago": {
                        "surface": {
                            "grassBlock": "minecraft:sand",
                            "rockBlock": "minecraft:sandstone",
                            "snowBlock": "minecraft:sand",
                            "snowline": size["y"] + 1,
                            "rockSlope": 6,
                        },
                        "heightfield": {"seed": seed + 4, "octaves": 3, "scale": 0.12, "amplitude": 5, "base": int(size["y"] * 0.18)},
                        "features": [
                            {"type": "sea", "seaLevel": int(size["y"] * 0.3), "shoreBlocks": ["minecraft:sand", "minecraft:sandstone"], "carveAbove": True},
                            {"type": "islands", "count": 8, "radius": [6, 12], "height": [3, 7], "topBlock": "minecraft:sand", "fillBlock": "minecraft:sandstone"},
                            {"type": "coral", "count": 16, "radius": [2, 4], "blocks": ["minecraft:brain_coral_block", "minecraft:fire_coral_block", "minecraft:horn_coral_block"]},
                        ],
                    },
                },
                "regions": [
                    {"preset": "lush_valley", "x": size["x"] // 2, "z": size["z"] // 2, "rx": size["x"] // 3, "rz": size["z"] // 4, "shape": "amoeba", "seed": seed},
                    {"preset": "crystal_spires", "x": size["x"] // 5, "z": size["z"] // 2, "rx": size["x"] // 5, "rz": size["z"] // 3},
                    {"preset": "oasis", "x": size["x"] * 4 // 5, "z": size["z"] // 2, "rx": size["x"] // 5, "rz": size["z"] // 3},
                    {"preset": "coral_archipelago", "x": size["x"] * 9 // 10, "z": size["z"] // 2, "rx": size["x"] // 8, "rz": size["z"] // 3},
                ],
            }
        }
        preset_cfg.update(blended)

    spec = {
        "name": name,
        "size": size,
        "origin": {"x": size["x"] // 2, "y": 0, "z": size["z"] // 2},
        "paletteRules": [] if preset == "blended_scene" else palette_rules,
        "heightfield": {
            "type": "noise",
            "params": {"seed": seed, "octaves": 4, "scale": scale, "amplitude": amplitude},
        },
        "slope": slope_spec,
        "ridge": {"strength": 8.0, "scale": 0.1, "seed": seed} if has_cliffs else None,
        "erosion": {"iterations": 1, "talus": 2, "strength": 0.4} if has_cliffs else None,
        "landmarks": preset_cfg.get("landmarks", []),
        "biomeBlend": preset_cfg.get("biomeBlend"),
        "surface": {
            "snowline": snowline,
            "rockSlope": rock_slope,
            "grassBlock": surface_grass,
            "rockBlock": surface_rock,
            "snowBlock": surface_snow,
            "snowOverridesRock": True if is_snowy else False,
            "noGrassOnRock": True,
        },
        "layers": layers,
        "features": [] if preset == "blended_scene" else features,
        "constraints": {
            "maxBlocks": size["x"] * size["y"] * size["z"],
            "allowAirCarving": True if ("cave" in text) else False,
            "blockWhitelist": whitelist,
        },
        "output": {"schematicVersion": 3, "includeEntities": False, "includeBiomes": False, "dryRun": False},
    }
    if preset == "blended_scene":
        spec["erosion"] = {"iterations": 2, "talus": 2, "strength": 0.4}
    return spec
