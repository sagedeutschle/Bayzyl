import gzip
import json
import math
import os
import random
from typing import Dict, List, Tuple, Optional

from .nbt import (
    TAG_BYTE_ARRAY,
    TAG_COMPOUND,
    TAG_INT,
    TAG_LIST,
    TAG_SHORT,
    write_nbt,
)
from .noise import fractal_noise, fractal_noise_3d


Block = str


def _default_origin(size: Dict[str, int]) -> Dict[str, int]:
    return {"x": size["x"] // 2, "y": 0, "z": size["z"] // 2}


def _clamp(v: int, lo: int, hi: int) -> int:
    return max(lo, min(hi, v))


def _heightfield(spec: Dict) -> List[List[int]]:
    size = spec["size"]
    hf = [[0 for _ in range(size["z"])] for _ in range(size["x"])]
    biome_blend = spec.get("biomeBlend")
    h = spec["heightfield"]
    htype = h["type"]
    params = h["params"]

    if biome_blend:
        regions = biome_blend.get("regions", [])
        presets = biome_blend.get("presets", {})
        for x in range(size["x"]):
            for z in range(size["z"]):
                weights: List[Tuple[str, float]] = []
                for region in regions:
                    cx = region.get("x", size["x"] // 2)
                    cz = region.get("z", size["z"] // 2)
                    rx = region.get("rx", region.get("radius", 50))
                    rz = region.get("rz", region.get("radius", 50))
                    dx = (x - cx) / max(1, rx)
                    dz = (z - cz) / max(1, rz)
                    d = dx * dx + dz * dz
                    if region.get("shape") == "amoeba":
                        wobble = fractal_noise(x, z, int(region.get("seed", 1337)), 2, 0.1)
                        d *= 1.0 + wobble * 0.3
                    weight = max(0.0, 1.0 - d)
                    if weight > 0:
                        weights.append((region.get("preset"), weight))
                if not weights:
                    weights = [(None, 1.0)]
                weights.sort(key=lambda t: t[1], reverse=True)
                weights = weights[:3]
                total = sum(w for _, w in weights)
                height_acc = 0.0
                for preset_name, w in weights:
                    preset = presets.get(preset_name, params)
                    seed = int(preset.get("seed", 1337))
                    octaves = int(preset.get("octaves", 4))
                    scale = float(preset.get("scale", 0.06))
                    amplitude = float(preset.get("amplitude", 12))
                    base = int(preset.get("base", size["y"] // 3))
                    n = fractal_noise(x, z, seed, octaves, scale)
                    height_acc += (base + n * amplitude) * (w / max(1e-6, total))
                hf[x][z] = _clamp(int(round(height_acc)), 1, size["y"] - 1)
    elif htype == "noise":
        seed = int(params.get("seed", 1337))
        octaves = int(params.get("octaves", 4))
        scale = float(params.get("scale", 0.06))
        amplitude = float(params.get("amplitude", 12))
        base = int(params.get("base", size["y"] // 3))
        for x in range(size["x"]):
            for z in range(size["z"]):
                n = fractal_noise(x, z, seed, octaves, scale)
                height = int(round(base + n * amplitude))
                hf[x][z] = _clamp(height, 1, size["y"] - 1)
    elif htype == "spline":
        points = params.get("controlPoints", [])
        falloff = float(params.get("falloff", 0.02))
        base = int(params.get("base", size["y"] // 3))
        for x in range(size["x"]):
            for z in range(size["z"]):
                hval = base
                for p in points:
                    dx = x - p["x"]
                    dz = z - p["z"]
                    d2 = dx * dx + dz * dz
                    hval += int(p["height"] * math.exp(-d2 * falloff))
                hf[x][z] = _clamp(hval, 1, size["y"] - 1)
    elif htype == "heightmap":
        data = params["data"]
        scale = float(params.get("scale", 1.0))
        for x in range(size["x"]):
            for z in range(size["z"]):
                hval = int(round(data[x][z] * scale))
                hf[x][z] = _clamp(hval, 1, size["y"] - 1)
    else:
        raise ValueError(f"Unsupported heightfield type: {htype}")

    landmarks = spec.get("landmarks", [])
    for lm in landmarks:
        ltype = lm.get("type")
        cx = int(lm.get("x", size["x"] // 2))
        cz = int(lm.get("z", size["z"] // 2))
        radius = int(lm.get("radius", min(size["x"], size["z"]) // 4))
        height = int(lm.get("height", size["y"] // 2))
        for x in range(size["x"]):
            for z in range(size["z"]):
                dx = x - cx
                dz = z - cz
                d2 = dx * dx + dz * dz
                if d2 > radius * radius:
                    continue
                t = 1.0 - (d2 / (radius * radius))
                if ltype == "mountain":
                    hf[x][z] = _clamp(hf[x][z] + int(height * t), 1, size["y"] - 1)
                elif ltype == "plateau":
                    plateau = int(lm.get("plateauY", height))
                    if hf[x][z] < plateau:
                        hf[x][z] = _clamp(int(hf[x][z] + (plateau - hf[x][z]) * t), 1, size["y"] - 1)
                elif ltype == "crater":
                    depth = int(lm.get("depth", height // 2))
                    hf[x][z] = _clamp(hf[x][z] - int(depth * t), 1, size["y"] - 1)

    slope = spec.get("slope")
    if slope:
        axis = slope.get("axis", "x")
        start_y = int(slope.get("startY", size["y"] - 1))
        end_y = int(slope.get("endY", 1))
        direction = slope.get("direction", "positive")
        blend = float(slope.get("blend", 0.7))
        for x in range(size["x"]):
            for z in range(size["z"]):
                t = x / max(1, size["x"] - 1) if axis == "x" else z / max(1, size["z"] - 1)
                if direction == "negative":
                    t = 1.0 - t
                target = int(round(start_y + (end_y - start_y) * t))
                hf[x][z] = int(round(hf[x][z] * (1.0 - blend) + target * blend))

    ridge = spec.get("ridge")
    if ridge:
        strength = float(ridge.get("strength", 4.0))
        scale = float(ridge.get("scale", 0.12))
        seed = int(ridge.get("seed", params.get("seed", 1337)))
        for x in range(size["x"]):
            for z in range(size["z"]):
                r = fractal_noise(x, z, seed + 911, 2, scale)
                hf[x][z] = _clamp(hf[x][z] + int(round(r * strength)), 1, size["y"] - 1)

    erosion = spec.get("erosion")
    if erosion:
        iterations = int(erosion.get("iterations", 2))
        talus = int(erosion.get("talus", 2))
        strength = float(erosion.get("strength", 0.5))
        for _ in range(iterations):
            for x in range(1, size["x"] - 1):
                for z in range(1, size["z"] - 1):
                    h0 = hf[x][z]
                    for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                        nx = x + dx
                        nz = z + dz
                        diff = h0 - hf[nx][nz]
                        if diff > talus:
                            move = max(1, int(diff * strength))
                            hf[x][z] -= move
                            hf[nx][nz] += move
                            h0 = hf[x][z]

    return hf


def _apply_layers(spec: Dict, hf: List[List[int]]) -> List[List[List[Block]]]:
    size = spec["size"]
    air = "minecraft:air"
    volume = [[[air for _ in range(size["z"])] for _ in range(size["y"])] for _ in range(size["x"])]
    layers = spec["layers"]

    for x in range(size["x"]):
        for z in range(size["z"]):
            height = hf[x][z]
            # Fill base from bottom up with fromTop=False layers
            y = 0
            for layer in layers:
                if layer.get("fromTop", False):
                    continue
                thickness = int(layer["thickness"])
                block = layer["block"]
                for _ in range(thickness):
                    if y > height:
                        break
                    volume[x][y][z] = block
                    y += 1
            # Fill remaining below surface with stone if not already filled
            while y <= height:
                volume[x][y][z] = "minecraft:stone"
                y += 1

            # Apply fromTop layers from surface downward
            y = height
            for layer in layers:
                if not layer.get("fromTop", False):
                    continue
                thickness = int(layer["thickness"])
                block = layer["block"]
                for _ in range(thickness):
                    if y < 0:
                        break
                    volume[x][y][z] = block
                    y -= 1

    return volume


def _choose_palette_block(
    rules: List[Dict],
    y: int,
    slope: int,
    rng: random.Random,
) -> Optional[Block]:
    matches: List[Tuple[Block, float]] = []
    for rule in rules:
        conditions = rule.get("conditions", {})
        min_y = conditions.get("minY", -1_000_000)
        max_y = conditions.get("maxY", 1_000_000)
        min_slope = conditions.get("minSlope", -1_000_000)
        max_slope = conditions.get("maxSlope", 1_000_000)
        if not (min_y <= y <= max_y):
            continue
        if not (min_slope <= slope <= max_slope):
            continue
        matches.append((rule["block"], float(rule.get("weight", 1.0))))

    if not matches:
        return None

    total = sum(w for _, w in matches)
    pick = rng.random() * total
    for block, weight in matches:
        pick -= weight
        if pick <= 0:
            return block
    return matches[-1][0]


def _slope(hf: List[List[int]], x: int, z: int) -> int:
    base = hf[x][z]
    max_diff = 0
    for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        nx = x + dx
        nz = z + dz
        if 0 <= nx < len(hf) and 0 <= nz < len(hf[0]):
            max_diff = max(max_diff, abs(base - hf[nx][nz]))
    return max_diff


def _edge_block_for(
    edge_blocks: Optional[List[Block]],
    fallback: Block,
    x: int,
    y: int,
    z: int,
    seed: int,
) -> Block:
    if not edge_blocks:
        return fallback
    erng = random.Random((x * 7349 + y * 9151 + z * 1931 + seed) & 0xFFFFFFFF)
    return edge_blocks[erng.randrange(len(edge_blocks))]


def _place_leaves(
    volume: List[List[List[Block]]],
    cx: int,
    cy: int,
    cz: int,
    radius: int,
    leaves: Block,
    rng: random.Random,
    overwrite: bool = False,
) -> None:
    x_len = len(volume)
    y_len = len(volume[0])
    z_len = len(volume[0][0])
    for dx in range(-radius, radius + 1):
        for dz in range(-radius, radius + 1):
            if abs(dx) + abs(dz) > radius + rng.randint(0, 1):
                continue
            x = cx + dx
            z = cz + dz
            if 0 <= x < x_len and 0 <= z < z_len and 0 <= cy < y_len:
                if overwrite or volume[x][cy][z] == "minecraft:air":
                    volume[x][cy][z] = leaves


def _cover_trunk_top(
    volume: List[List[List[Block]]],
    cx: int,
    cy: int,
    cz: int,
    leaves: Block,
    rng: random.Random,
) -> None:
    # Ensure the topmost log is covered for natural-looking canopies.
    _place_leaves(volume, cx, cy, cz, 1, leaves, rng, overwrite=True)
    _place_leaves(volume, cx, cy + 1, cz, 1, leaves, rng, overwrite=True)


def _place_tree(
    volume: List[List[List[Block]]],
    hf: List[List[int]],
    x: int,
    y: int,
    z: int,
    tree_type: str,
    height: int,
    trunk_thickness: int,
    canopy: int,
    shape: str,
    rng: random.Random,
) -> None:
    if tree_type == "palm":
        log = "minecraft:jungle_log"
        leaves = "minecraft:jungle_leaves"
    else:
        log = f"minecraft:{tree_type}_log"
        leaves = f"minecraft:{tree_type}_leaves"
    x_len = len(volume)
    y_len = len(volume[0])
    z_len = len(volume[0][0])

    # trunk with slight curve
    cx = x
    cz = z
    for i in range(height):
        if i % 3 == 0:
            cx = _clamp(cx + rng.choice([-1, 0, 1]), 1, x_len - 2)
            cz = _clamp(cz + rng.choice([-1, 0, 1]), 1, z_len - 2)
        for dx in range(trunk_thickness):
            for dz in range(trunk_thickness):
                tx = _clamp(cx + dx, 0, x_len - 1)
                tz = _clamp(cz + dz, 0, z_len - 1)
                if 0 <= y + i < y_len:
                    volume[tx][y + i][tz] = log

    # roots only for 2x2 trunks, follow terrain downward
    if trunk_thickness == 2 and canopy >= 3:
        root_len = max(1, canopy // 2)
        for dx, dz in ((2, 0), (-2, 0), (0, 2), (0, -2)):
            for r in range(1, root_len + 1):
                rx = _clamp(x + dx * r, 1, x_len - 2)
                rz = _clamp(z + dz * r, 1, z_len - 2)
                ground_y = hf[rx][rz]
                if ground_y > y:
                    continue
                # connect down to terrain to avoid floating roots
                for ry in range(y - 1, ground_y - 1, -1):
                    volume[rx][ry][rz] = log

    top = y + height - 1

    if shape == "spruce":
        _cover_trunk_top(volume, cx, top, cz, leaves, rng)
        canopy_start = y + max(2, height // 3)
        for cy in range(top, canopy_start - 1, -1):
            layer = top - cy
            radius = min(canopy, max(1, 1 + layer // 2 + rng.choice([0, 1])))
            jx = _clamp(cx + rng.choice([-1, 0, 0, 1]), 1, x_len - 2)
            jz = _clamp(cz + rng.choice([-1, 0, 0, 1]), 1, z_len - 2)
            _place_leaves(volume, jx, cy, jz, radius, leaves, rng)
        # add a few branches mid trunk
        for by in range(y + height // 2, top - 2, 2):
            for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                if rng.random() < 0.4:
                    bx = _clamp(cx + dx * 2, 1, x_len - 2)
                    bz = _clamp(cz + dz * 2, 1, z_len - 2)
                    volume[bx][by][bz] = log
                    _place_leaves(volume, bx, by, bz, 2, leaves, rng, overwrite=True)
    elif shape == "oak":
        _cover_trunk_top(volume, cx, top, cz, leaves, rng)
        # rounder canopy with branches
        crown_center = top - 2
        for cy in range(crown_center - canopy, crown_center + 2):
            radius = max(2, canopy - abs(cy - crown_center))
            radius += rng.choice([0, 1])
            _place_leaves(volume, cx, cy, cz, radius, leaves, rng, overwrite=True)
        for dx, dz in ((2, 0), (-2, 0), (0, 2), (0, -2)):
            if rng.random() < 0.7:
                bx = _clamp(cx + dx, 1, x_len - 2)
                bz = _clamp(cz + dz, 1, z_len - 2)
                volume[bx][crown_center][bz] = log
                _place_leaves(volume, bx, crown_center, bz, 3, leaves, rng, overwrite=True)
    elif shape == "palm":
        _cover_trunk_top(volume, cx, top, cz, leaves, rng)
        frond = canopy + 2
        for dy in range(2):
            y0 = top - dy
            for dx, dz in ((frond, 0), (-frond, 0), (0, frond), (0, -frond)):
                bx = _clamp(cx + dx, 1, x_len - 2)
                bz = _clamp(cz + dz, 1, z_len - 2)
                _place_leaves(volume, bx, y0, bz, 2, leaves, rng, overwrite=True)
            _place_leaves(volume, cx, y0, cz, 2, leaves, rng, overwrite=True)
    else:
        # irregular blob
        _cover_trunk_top(volume, cx, top, cz, leaves, rng)
        for cy in range(top - canopy, top + 1):
            radius = rng.randint(2, canopy + 1)
            _place_leaves(volume, cx, cy, cz, radius, leaves, rng, overwrite=True)


def _apply_features(spec: Dict, volume: List[List[List[Block]]], hf: List[List[int]]) -> None:
    size = spec["size"]
    features = spec.get("features", [])
    base_seed = int(
        spec.get(
            "seed",
            spec.get("heightfield", {}).get("params", {}).get("seed", 1337),
        )
    )
    rng = random.Random(base_seed)

    surface = spec.get("surface", {})
    snowline = int(surface.get("snowline", int(size["y"] * 0.6)))
    rock_slope = int(surface.get("rockSlope", 4))
    grass_block = surface.get("grassBlock", "minecraft:grass_block")
    rock_block = surface.get("rockBlock", "minecraft:stone")
    snow_block = surface.get("snowBlock", "minecraft:snow_block")
    snow_overrides_rock = bool(surface.get("snowOverridesRock", False))
    no_grass_on_rock = bool(surface.get("noGrassOnRock", True))
    biome_blend = spec.get("biomeBlend")
    blend_map = biome_blend.get("map") if biome_blend else None

    palette_rules = spec.get("paletteRules", [])
    for x in range(size["x"]):
        for z in range(size["z"]):
            y = hf[x][z]
            s = _slope(hf, x, z)
            if biome_blend:
                region = biome_blend.get("map", {}).get((x, z))
                if region:
                    surface = region.get("surface", surface)
                    snowline = int(surface.get("snowline", snowline))
                    rock_slope = int(surface.get("rockSlope", rock_slope))
                    grass_block = surface.get("grassBlock", grass_block)
                    rock_block = surface.get("rockBlock", rock_block)
                    snow_block = surface.get("snowBlock", snow_block)
                    snow_overrides_rock = bool(surface.get("snowOverridesRock", snow_overrides_rock))
            block = None
            if palette_rules:
                rule_rng = random.Random((x * 7349 + z * 9151 + base_seed) & 0xFFFFFFFF)
                block = _choose_palette_block(palette_rules, y, s, rule_rng)
            if block:
                volume[x][y][z] = block
            else:
                if y >= snowline and snow_overrides_rock:
                    volume[x][y][z] = snow_block
                elif s >= rock_slope and no_grass_on_rock:
                    volume[x][y][z] = rock_block
                elif y >= snowline:
                    volume[x][y][z] = snow_block
                else:
                    volume[x][y][z] = grass_block

    for feat in features:
        ftype = feat["type"]
        region_preset = feat.get("regionPreset")
        if ftype == "cliffs":
            threshold = int(feat.get("angleThreshold", 35))
            for x in range(size["x"]):
                for z in range(size["z"]):
                    if _slope(hf, x, z) >= max(1, threshold // 5):
                        y = hf[x][z]
                        volume[x][y][z] = "minecraft:stone"
                        if y - 1 >= 0:
                            volume[x][y - 1][z] = "minecraft:stone"
        elif ftype == "trees":
            density = float(feat.get("density", 0.01))
            tree_type = feat.get("treeType", "spruce")
            min_y = int(feat.get("minY", 0))
            variants = feat.get("variants", [])
            if not variants:
                variants = [
                    {"minHeight": 6, "maxHeight": 9, "canopy": 3, "root": 1},
                    {"minHeight": 10, "maxHeight": 14, "canopy": 4, "root": 2},
                    {"minHeight": 14, "maxHeight": 18, "canopy": 5, "root": 3},
                ]
            canopy_shapes = feat.get("canopyShapes", ["spruce", "oak", "irregular"])
            tree_mix = feat.get("treeMix", [])
            mix_total = sum(float(t.get("weight", 1.0)) for t in tree_mix) if tree_mix else 0.0
            for x in range(2, size["x"] - 2):
                for z in range(2, size["z"] - 2):
                    if rng.random() < density:
                        if region_preset and blend_map and blend_map.get((x, z), {}).get("name") != region_preset:
                            continue
                        y = hf[x][z]
                        if y < min_y or y + 6 >= size["y"]:
                            continue
                        base_block = volume[x][y][z]
                        if base_block not in (
                            "minecraft:grass_block",
                            "minecraft:dirt",
                            "minecraft:snow_block",
                            "minecraft:sand",
                            "minecraft:sandstone",
                            "minecraft:moss_block",
                            "minecraft:blackstone",
                            "minecraft:basalt",
                            "minecraft:netherrack",
                        ):
                            continue
                        if tree_mix and mix_total > 0:
                            pick = rng.random() * mix_total
                            chosen = tree_mix[-1]
                            for t in tree_mix:
                                pick -= float(t.get("weight", 1.0))
                                if pick <= 0:
                                    chosen = t
                                    break
                            tree_type = chosen.get("type", tree_type)
                            variants = chosen.get("variants", variants)
                            canopy_shapes = chosen.get("canopyShapes", canopy_shapes)
                        variant = rng.choice(variants)
                        height = rng.randint(variant["minHeight"], variant["maxHeight"])
                        shape = rng.choice(canopy_shapes)
                        trunk_thickness = 2 if height >= 14 else 1
                        canopy = int(variant.get("canopy", 3))
                        _place_tree(volume, hf, x, y, z, tree_type, height, trunk_thickness, canopy, shape, rng)
        elif ftype == "water":
            sea_level = int(feat.get("seaLevel", size["y"] // 3))
            lake_count = int(feat.get("lakeCount", 4))
            min_r, max_r = feat.get("lakeRadius", [3, 6])
            wall_block = feat.get("wallBlock", "minecraft:stone")
            depth_min, depth_max = feat.get("depth", [2, 4])
            carve = bool(feat.get("carveToSurface", True))
            contain_sides = bool(feat.get("containSides", True))
            edge_blocks = feat.get("edgeBlocks")
            for _ in range(lake_count):
                cx = rng.randint(5, size["x"] - 6)
                cz = rng.randint(5, size["z"] - 6)
                if region_preset and blend_map and blend_map.get((cx, cz), {}).get("name") != region_preset:
                    continue
                r = rng.randint(min_r, max_r)
                for x in range(cx - r, cx + r + 1):
                    for z in range(cz - r, cz + r + 1):
                        if 0 <= x < size["x"] and 0 <= z < size["z"]:
                            if region_preset and blend_map and blend_map.get((x, z), {}).get("name") != region_preset:
                                continue
                            if (x - cx) ** 2 + (z - cz) ** 2 <= r * r:
                                surface = min(sea_level, hf[x][z])
                                if surface <= 1:
                                    continue
                                depth = rng.randint(depth_min, depth_max)
                                floor = max(1, surface - depth)
                                if surface <= floor:
                                    continue
                                for y in range(floor, surface + 1):
                                    volume[x][y][z] = "minecraft:water"
                                if carve and hf[x][z] > surface:
                                    for y in range(surface + 1, hf[x][z] + 1):
                                        volume[x][y][z] = "minecraft:air"
                                    hf[x][z] = surface
            # enforce containment: water sides must be solid, top is open
            if contain_sides:
                for x in range(1, size["x"] - 1):
                    for z in range(1, size["z"] - 1):
                        for y in range(1, size["y"] - 1):
                            if volume[x][y][z] != "minecraft:water":
                                continue
                            if (
                                volume[x + 1][y][z] == "minecraft:air"
                                or volume[x - 1][y][z] == "minecraft:air"
                                or volume[x][y][z + 1] == "minecraft:air"
                                or volume[x][y][z - 1] == "minecraft:air"
                            ):
                                # replace side air with a matching surface block instead of removing water
                                edge_block = wall_block
                                if edge_blocks:
                                    edge_block = _edge_block_for(edge_blocks, wall_block, x, y, z, base_seed)
                                elif y >= snowline:
                                    edge_block = snow_block
                                elif _slope(hf, x, z) >= rock_slope:
                                    edge_block = rock_block
                                else:
                                    edge_block = grass_block
                                if volume[x + 1][y][z] == "minecraft:air":
                                    volume[x + 1][y][z] = edge_block
                                if volume[x - 1][y][z] == "minecraft:air":
                                    volume[x - 1][y][z] = edge_block
                                if volume[x][y][z + 1] == "minecraft:air":
                                    volume[x][y][z + 1] = edge_block
                                if volume[x][y][z - 1] == "minecraft:air":
                                    volume[x][y][z - 1] = edge_block
        elif ftype == "lava":
            surface_level = int(feat.get("surfaceLevel", size["y"] // 3))
            surface_count = int(feat.get("surfaceCount", 4))
            min_r, max_r = feat.get("surfaceRadius", [3, 6])
            wall_block = feat.get("wallBlock", rock_block)
            depth_min, depth_max = feat.get("depth", [2, 4])
            carve = bool(feat.get("carveToSurface", True))
            contain_sides = bool(feat.get("containSides", True))
            edge_blocks = feat.get("edgeBlocks")
            for _ in range(surface_count):
                cx = rng.randint(5, size["x"] - 6)
                cz = rng.randint(5, size["z"] - 6)
                if region_preset and blend_map and blend_map.get((cx, cz), {}).get("name") != region_preset:
                    continue
                r = rng.randint(min_r, max_r)
                for x in range(cx - r, cx + r + 1):
                    for z in range(cz - r, cz + r + 1):
                        if 0 <= x < size["x"] and 0 <= z < size["z"]:
                            if region_preset and blend_map and blend_map.get((x, z), {}).get("name") != region_preset:
                                continue
                            if (x - cx) ** 2 + (z - cz) ** 2 <= r * r:
                                surface = min(surface_level, hf[x][z])
                                if surface <= 1:
                                    continue
                                depth = rng.randint(depth_min, depth_max)
                                floor = max(1, surface - depth)
                                if surface <= floor:
                                    continue
                                for y in range(floor, surface + 1):
                                    volume[x][y][z] = "minecraft:lava"
                                if carve and hf[x][z] > surface:
                                    for y in range(surface + 1, hf[x][z] + 1):
                                        volume[x][y][z] = "minecraft:air"
                                    hf[x][z] = surface
            cave_count = int(feat.get("caveCount", 6))
            cave_min_y = int(feat.get("caveMinY", 6))
            cave_max_y = int(feat.get("caveMaxY", size["y"] // 2))
            cave_min_r, cave_max_r = feat.get("caveRadius", [2, 4])
            for _ in range(cave_count):
                cx = rng.randint(4, size["x"] - 5)
                cz = rng.randint(4, size["z"] - 5)
                cy = rng.randint(cave_min_y, cave_max_y)
                if region_preset and blend_map and blend_map.get((cx, cz), {}).get("name") != region_preset:
                    continue
                r = rng.randint(cave_min_r, cave_max_r)
                for x in range(cx - r, cx + r + 1):
                    for z in range(cz - r, cz + r + 1):
                        if 0 <= x < size["x"] and 0 <= z < size["z"]:
                            if region_preset and blend_map and blend_map.get((x, z), {}).get("name") != region_preset:
                                continue
                            if (x - cx) ** 2 + (z - cz) ** 2 <= r * r:
                                volume[x][cy][z] = "minecraft:lava"
                                if cy + 1 < size["y"]:
                                    volume[x][cy + 1][z] = "minecraft:air"
            # enforce containment: lava sides must be solid, top is open
            if contain_sides:
                for x in range(1, size["x"] - 1):
                    for z in range(1, size["z"] - 1):
                        for y in range(1, size["y"] - 1):
                            if volume[x][y][z] != "minecraft:lava":
                                continue
                            if (
                                volume[x + 1][y][z] == "minecraft:air"
                                or volume[x - 1][y][z] == "minecraft:air"
                                or volume[x][y][z + 1] == "minecraft:air"
                                or volume[x][y][z - 1] == "minecraft:air"
                            ):
                                edge_block = wall_block
                                if edge_blocks:
                                    edge_block = _edge_block_for(edge_blocks, wall_block, x, y, z, base_seed)
                                elif y >= snowline:
                                    edge_block = snow_block
                                elif _slope(hf, x, z) >= rock_slope:
                                    edge_block = rock_block
                                else:
                                    edge_block = grass_block
                                if volume[x + 1][y][z] == "minecraft:air":
                                    volume[x + 1][y][z] = edge_block
                                if volume[x - 1][y][z] == "minecraft:air":
                                    volume[x - 1][y][z] = edge_block
                                if volume[x][y][z + 1] == "minecraft:air":
                                    volume[x][y][z + 1] = edge_block
                                if volume[x][y][z - 1] == "minecraft:air":
                                    volume[x][y][z - 1] = edge_block
        elif ftype == "boulders":
            count = int(feat.get("count", 8))
            min_r, max_r = feat.get("radius", [2, 4])
            block = feat.get("block", rock_block)
            for _ in range(count):
                cx = rng.randint(4, size["x"] - 5)
                cz = rng.randint(4, size["z"] - 5)
                cy = hf[cx][cz]
                r = rng.randint(min_r, max_r)
                for x in range(cx - r, cx + r + 1):
                    for z in range(cz - r, cz + r + 1):
                        for y in range(cy - r, cy + r + 1):
                            if 0 <= x < size["x"] and 0 <= z < size["z"] and 0 <= y < size["y"]:
                                if (x - cx) ** 2 + (z - cz) ** 2 + (y - cy) ** 2 <= r * r:
                                    volume[x][y][z] = block
        elif ftype == "spires":
            count = int(feat.get("count", 6))
            min_h, max_h = feat.get("height", [10, 24])
            block = feat.get("block", "minecraft:amethyst_block")
            blocks = feat.get("blocks")
            accent_blocks = feat.get("accentBlocks", [])
            accent_chance = float(feat.get("accentChance", 0.25))
            base_r_min, base_r_max = feat.get("baseRadius", [2, 4])
            cluster = int(feat.get("cluster", 1))
            shard_count = int(feat.get("shardCount", 2))
            for _ in range(count):
                cx = rng.randint(4, size["x"] - 5)
                cz = rng.randint(4, size["z"] - 5)
                for _c in range(cluster):
                    ox = _clamp(cx + rng.randint(-3, 3), 2, size["x"] - 3)
                    oz = _clamp(cz + rng.randint(-3, 3), 2, size["z"] - 3)
                    base_y = hf[ox][oz]
                    h = rng.randint(min_h, max_h)
                    base_r = rng.randint(base_r_min, base_r_max)
                    drift_x = 0
                    drift_z = 0
                    for y in range(base_y, min(base_y + h, size["y"] - 1)):
                        t = (y - base_y) / max(1, h)
                        radius = max(1, int(round(base_r * (1.0 - t) ** 1.2)))
                        if rng.random() < 0.3:
                            drift_x += rng.choice([-1, 0, 1])
                            drift_z += rng.choice([-1, 0, 1])
                        sx = _clamp(ox + drift_x, 1, size["x"] - 2)
                        sz = _clamp(oz + drift_z, 1, size["z"] - 2)
                        for dx in range(-radius, radius + 1):
                            for dz in range(-radius, radius + 1):
                                x = sx + dx
                                z = sz + dz
                                if 0 <= x < size["x"] and 0 <= z < size["z"]:
                                    use_block = block
                                    if blocks:
                                        use_block = rng.choice(blocks)
                                    if accent_blocks and (t > 0.7 or rng.random() < accent_chance):
                                        use_block = rng.choice(accent_blocks)
                                    volume[x][y][z] = use_block
                    for _s in range(shard_count):
                        scx = _clamp(ox + rng.randint(-4, 4), 2, size["x"] - 3)
                        scz = _clamp(oz + rng.randint(-4, 4), 2, size["z"] - 3)
                        sb = hf[scx][scz]
                        sh = rng.randint(4, 10)
                        for y in range(sb, min(sb + sh, size["y"] - 1)):
                            radius = 1 if y > sb + sh // 2 else 2
                            for dx in range(-radius, radius + 1):
                                for dz in range(-radius, radius + 1):
                                    x = scx + dx
                                    z = scz + dz
                                    if 0 <= x < size["x"] and 0 <= z < size["z"]:
                                        volume[x][y][z] = block
        elif ftype == "coral":
            count = int(feat.get("count", 12))
            min_r, max_r = feat.get("radius", [2, 4])
            coral_blocks = feat.get("blocks", ["minecraft:brain_coral_block", "minecraft:fire_coral_block"])
            for _ in range(count):
                cx = rng.randint(4, size["x"] - 5)
                cz = rng.randint(4, size["z"] - 5)
                cy = hf[cx][cz] - 1
                r = rng.randint(min_r, max_r)
                block = rng.choice(coral_blocks)
                for x in range(cx - r, cx + r + 1):
                    for z in range(cz - r, cz + r + 1):
                        if 0 <= x < size["x"] and 0 <= z < size["z"] and (x - cx) ** 2 + (z - cz) ** 2 <= r * r:
                            if 0 <= cy < size["y"]:
                                if volume[x][cy][z] == "minecraft:water" or (
                                    cy + 1 < size["y"] and volume[x][cy + 1][z] == "minecraft:water"
                                ):
                                    volume[x][cy][z] = block
        elif ftype == "sea":
            sea_level = int(feat.get("seaLevel", size["y"] // 3))
            shore_blocks = feat.get("shoreBlocks")
            shore_block = feat.get("shoreBlock", "minecraft:sand")
            carve_above = bool(feat.get("carveAbove", True))
            for x in range(1, size["x"] - 1):
                for z in range(1, size["z"] - 1):
                    if region_preset and blend_map and blend_map.get((x, z), {}).get("name") != region_preset:
                        continue
                    surface = hf[x][z]
                    if surface > sea_level and carve_above:
                        for y in range(sea_level + 1, surface + 1):
                            volume[x][y][z] = "minecraft:air"
                        hf[x][z] = sea_level
                        volume[x][sea_level][z] = _edge_block_for(shore_blocks, shore_block, x, sea_level, z, base_seed)
                    else:
                        for y in range(surface + 1, sea_level + 1):
                            if y < size["y"]:
                                volume[x][y][z] = "minecraft:water"
        elif ftype == "islands":
            count = int(feat.get("count", 8))
            min_r, max_r = feat.get("radius", [6, 12])
            min_h, max_h = feat.get("height", [4, 10])
            top_block = feat.get("topBlock", "minecraft:sand")
            fill_block = feat.get("fillBlock", "minecraft:sandstone")
            for _ in range(count):
                cx = rng.randint(6, size["x"] - 7)
                cz = rng.randint(6, size["z"] - 7)
                if region_preset and blend_map and blend_map.get((cx, cz), {}).get("name") != region_preset:
                    continue
                r = rng.randint(min_r, max_r)
                h = rng.randint(min_h, max_h)
                for x in range(cx - r, cx + r + 1):
                    for z in range(cz - r, cz + r + 1):
                        if 0 <= x < size["x"] and 0 <= z < size["z"]:
                            if region_preset and blend_map and blend_map.get((x, z), {}).get("name") != region_preset:
                                continue
                            d2 = (x - cx) ** 2 + (z - cz) ** 2
                            if d2 <= r * r:
                                t = 1.0 - d2 / max(1, r * r)
                                raise_by = max(1, int(round(h * t)))
                                target = _clamp(hf[x][z] + raise_by, 1, size["y"] - 1)
                                for y in range(hf[x][z] + 1, target):
                                    volume[x][y][z] = fill_block
                                volume[x][target][z] = top_block
                                hf[x][z] = target
        elif ftype == "surface_patches":
            count = int(feat.get("count", 10))
            min_r, max_r = feat.get("radius", [3, 6])
            block = feat.get("block", grass_block)
            base_blocks = feat.get("baseBlocks")
            for _ in range(count):
                cx = rng.randint(4, size["x"] - 5)
                cz = rng.randint(4, size["z"] - 5)
                if region_preset and blend_map and blend_map.get((cx, cz), {}).get("name") != region_preset:
                    continue
                r = rng.randint(min_r, max_r)
                for x in range(cx - r, cx + r + 1):
                    for z in range(cz - r, cz + r + 1):
                        if 0 <= x < size["x"] and 0 <= z < size["z"] and (x - cx) ** 2 + (z - cz) ** 2 <= r * r:
                            if region_preset and blend_map and blend_map.get((x, z), {}).get("name") != region_preset:
                                continue
                            y = hf[x][z]
                            if 0 <= y < size["y"]:
                                if not base_blocks or volume[x][y][z] in base_blocks:
                                    volume[x][y][z] = block
        elif ftype == "reeds":
            count = int(feat.get("count", 20))
            block = feat.get("block", "minecraft:sugar_cane")
            for _ in range(count):
                cx = rng.randint(3, size["x"] - 4)
                cz = rng.randint(3, size["z"] - 4)
                cy = hf[cx][cz]
                if volume[cx][cy][cz] in ("minecraft:water", "minecraft:air") and cy + 2 < size["y"]:
                    volume[cx][cy + 1][cz] = block
                    volume[cx][cy + 2][cz] = block
        elif ftype == "lilypads":
            density = float(feat.get("density", 0.2))
            for x in range(2, size["x"] - 2):
                for z in range(2, size["z"] - 2):
                    if region_preset and blend_map and blend_map.get((x, z), {}).get("name") != region_preset:
                        continue
                    y = hf[x][z]
                    if y + 1 >= size["y"]:
                        continue
                    if volume[x][y][z] == "minecraft:water" and volume[x][y + 1][z] == "minecraft:air":
                        if rng.random() < density:
                            volume[x][y + 1][z] = "minecraft:lily_pad"
        elif ftype == "mushrooms":
            count = int(feat.get("count", 10))
            block = feat.get("block", "minecraft:red_mushroom_block")
            for _ in range(count):
                cx = rng.randint(4, size["x"] - 5)
                cz = rng.randint(4, size["z"] - 5)
                cy = hf[cx][cz]
                if cy + 4 >= size["y"]:
                    continue
                volume[cx][cy][cz] = "minecraft:mushroom_stem"
                for dy in range(2):
                    radius = 2 - dy
                    for dx in range(-radius, radius + 1):
                        for dz in range(-radius, radius + 1):
                            volume[cx + dx][cy + 2 + dy][cz + dz] = block
        elif ftype == "fallen_logs":
            count = int(feat.get("count", 6))
            block = feat.get("block", "minecraft:oak_log")
            for _ in range(count):
                cx = rng.randint(4, size["x"] - 5)
                cz = rng.randint(4, size["z"] - 5)
                cy = hf[cx][cz]
                length = rng.randint(3, 6)
                dir_x, dir_z = rng.choice([(1, 0), (-1, 0), (0, 1), (0, -1)])
                for i in range(length):
                    x = _clamp(cx + dir_x * i, 1, size["x"] - 2)
                    z = _clamp(cz + dir_z * i, 1, size["z"] - 2)
                    volume[x][cy][z] = block
        elif ftype == "caves":
            if not spec["constraints"].get("allowAirCarving", False):
                continue
            preset = feat.get("preset", "wormy")
            if preset == "vanilla_like":
                threshold = float(feat.get("threshold", 0.35))
                scale = float(feat.get("scale", 0.08))
                octaves = int(feat.get("octaves", 3))
                min_y = int(feat.get("minY", 6))
                max_y = int(feat.get("maxY", size["y"] - 6))
                for x in range(1, size["x"] - 1):
                    for z in range(1, size["z"] - 1):
                        if region_preset and blend_map and blend_map.get((x, z), {}).get("name") != region_preset:
                            continue
                        for y in range(min_y, max_y):
                            n = fractal_noise_3d(x, y, z, base_seed + 123, octaves, scale)
                            if n > threshold:
                                volume[x][y][z] = "minecraft:air"
            else:
                worm_count = int(feat.get("wormCount", 20))
                steps = int(feat.get("steps", 50))
                for _ in range(worm_count):
                    x = rng.randint(2, size["x"] - 3)
                    y = rng.randint(5, size["y"] - 6)
                    z = rng.randint(2, size["z"] - 3)
                    if region_preset and blend_map and blend_map.get((x, z), {}).get("name") != region_preset:
                        continue
                    for _ in range(steps):
                        volume[x][y][z] = "minecraft:air"
                        x += rng.choice([-1, 0, 1])
                        y += rng.choice([-1, 0, 1])
                        z += rng.choice([-1, 0, 1])
                        x = _clamp(x, 2, size["x"] - 3)
                        y = _clamp(y, 5, size["y"] - 6)
                        z = _clamp(z, 2, size["z"] - 3)


def _build_palette(volume: List[List[List[Block]]]) -> Tuple[List[Block], List[int]]:
    palette: List[Block] = []
    index_map: Dict[Block, int] = {}
    indices: List[int] = []
    x_len = len(volume)
    y_len = len(volume[0])
    z_len = len(volume[0][0])

    for y in range(y_len):
        for z in range(z_len):
            for x in range(x_len):
                block = volume[x][y][z]
                if block not in index_map:
                    index_map[block] = len(palette)
                    palette.append(block)
                indices.append(index_map[block])

    return palette, indices


def _build_biome_blend_map(spec: Dict) -> Dict[Tuple[int, int], Dict]:
    size = spec["size"]
    biome_blend = spec.get("biomeBlend", {})
    regions = biome_blend.get("regions", [])
    presets = biome_blend.get("presets", {})
    mapping: Dict[Tuple[int, int], Dict] = {}
    for x in range(size["x"]):
        for z in range(size["z"]):
            best = None
            best_w = -1.0
            for region in regions:
                cx = region.get("x", size["x"] // 2)
                cz = region.get("z", size["z"] // 2)
                rx = region.get("rx", region.get("radius", 50))
                rz = region.get("rz", region.get("radius", 50))
                dx = (x - cx) / max(1, rx)
                dz = (z - cz) / max(1, rz)
                d = dx * dx + dz * dz
                if region.get("shape") == "amoeba":
                    wobble = fractal_noise(x, z, int(region.get("seed", 1337)), 2, 0.1)
                    d *= 1.0 + wobble * 0.3
                weight = max(0.0, 1.0 - d)
                if weight > best_w:
                    best_w = weight
                    best = region.get("preset")
            preset = presets.get(best, {})
            if preset:
                mapping[(x, z)] = {"name": best, **preset}
    return mapping


def _encode_varint_array(values: List[int]) -> bytes:
    out = bytearray()
    for v in values:
        val = v & 0xFFFFFFFF
        while True:
            b = val & 0x7F
            val >>= 7
            if val:
                out.append(b | 0x80)
            else:
                out.append(b)
                break
    return bytes(out)


def _validate(spec: Dict, palette: List[Block], indices: List[int]) -> None:
    size = spec["size"]
    if len(indices) != size["x"] * size["y"] * size["z"]:
        raise ValueError("BlockData length does not match volume")
    max_index = len(palette) - 1
    for idx in indices:
        if idx < 0 or idx > max_index:
            raise ValueError("Palette index out of range")

    constraints = spec["constraints"]
    whitelist = set(constraints.get("blockWhitelist", []))
    blacklist = set(constraints.get("blockBlacklist", []))
    if whitelist:
        for b in palette:
            if b not in whitelist:
                raise ValueError(f"Block not in whitelist: {b}")
    if blacklist:
        for b in palette:
            if b in blacklist:
                raise ValueError(f"Block in blacklist: {b}")


def _manifest(spec: Dict, palette: List[Block], indices: List[int]) -> Dict:
    size = spec["size"]
    origin = spec.get("origin", _default_origin(size))
    offset = {"x": -origin["x"], "y": -origin["y"], "z": -origin["z"]}

    counts: Dict[str, int] = {}
    for idx in indices:
        block = palette[idx]
        counts[block] = counts.get(block, 0) + 1

    return {
        "name": spec["name"],
        "dimensions": size,
        "origin": origin,
        "suggestedPasteOffset": offset,
        "paletteSummary": counts,
        "generator": {
            "heightfield": spec["heightfield"],
            "slope": spec.get("slope"),
            "ridge": spec.get("ridge"),
            "erosion": spec.get("erosion"),
            "surface": spec.get("surface"),
            "layers": spec["layers"],
            "features": spec.get("features", []),
            "seed": spec.get("seed", 1337),
        },
    }


def _write_schem_v3(spec: Dict, palette: List[Block], indices: List[int]) -> bytes:
    size = spec["size"]
    blockdata = _encode_varint_array(indices)
    palette_compound = {b: (TAG_INT, i) for i, b in enumerate(palette)}
    blocks_compound = {
        "Palette": (TAG_COMPOUND, palette_compound),
        "Data": (TAG_BYTE_ARRAY, blockdata),
        "BlockEntities": (TAG_LIST, (TAG_COMPOUND, [])),
    }
    root = {
        "Version": (TAG_INT, 3),
        "DataVersion": (TAG_INT, int(spec.get("dataVersion", 3120))),
        "Width": (TAG_SHORT, size["x"]),
        "Height": (TAG_SHORT, size["y"]),
        "Length": (TAG_SHORT, size["z"]),
        "Blocks": (TAG_COMPOUND, blocks_compound),
    }
    if spec.get("output", {}).get("includeEntities", False):
        root["Entities"] = (TAG_LIST, (TAG_COMPOUND, []))
    if spec.get("output", {}).get("includeBiomes", False):
        root["Biomes"] = (TAG_BYTE_ARRAY, bytes(size["x"] * size["z"]))
    wrapper = {"Schematic": (TAG_COMPOUND, root)}
    return gzip.compress(write_nbt("", wrapper))


def _write_schem_v2(spec: Dict, palette: List[Block], indices: List[int]) -> bytes:
    size = spec["size"]
    blockdata = _encode_varint_array(indices)
    palette_compound = {b: (TAG_INT, i) for i, b in enumerate(palette)}
    root = {
        "Version": (TAG_INT, 2),
        "Width": (TAG_SHORT, size["x"]),
        "Height": (TAG_SHORT, size["y"]),
        "Length": (TAG_SHORT, size["z"]),
        "BlockPalette": (TAG_COMPOUND, palette_compound),
        "BlockData": (TAG_BYTE_ARRAY, blockdata),
    }
    wrapper = {"Schematic": (TAG_COMPOUND, root)}
    return gzip.compress(write_nbt("", wrapper))


def generate(spec: Dict) -> Tuple[bytes, Dict]:
    size = spec["size"]
    max_blocks = int(spec["constraints"].get("maxBlocks", size["x"] * size["y"] * size["z"]))
    if size["x"] * size["y"] * size["z"] > max_blocks:
        raise ValueError("maxBlocks exceeded by volume size")

    if "origin" not in spec:
        spec["origin"] = _default_origin(size)

    if spec.get("biomeBlend"):
        spec["biomeBlend"]["map"] = _build_biome_blend_map(spec)
        blended_features = []
        for name, preset in spec["biomeBlend"].get("presets", {}).items():
            for feat in preset.get("features", []):
                copied = dict(feat)
                copied["regionPreset"] = name
                blended_features.append(copied)
        if blended_features:
            spec["features"] = spec.get("features", []) + blended_features
    hf = _heightfield(spec)
    volume = _apply_layers(spec, hf)
    _apply_features(spec, volume, hf)
    palette, indices = _build_palette(volume)
    _validate(spec, palette, indices)

    manifest = _manifest(spec, palette, indices)
    output = spec.get("output", {})
    if output.get("dryRun", False):
        return b"", manifest

    version = int(output.get("schematicVersion", 3))
    if version == 2:
        schem = _write_schem_v2(spec, palette, indices)
    else:
        schem = _write_schem_v3(spec, palette, indices)
    return schem, manifest


def generate_from_file(spec_path: str, out_dir: str) -> Tuple[str, str]:
    with open(spec_path, "r", encoding="utf-8") as f:
        spec = json.load(f)

    schem, manifest = generate(spec)

    os.makedirs(out_dir, exist_ok=True)
    base = spec.get("name", "terrain")
    schem_path = os.path.join(out_dir, f"{base}.schem")
    manifest_path = os.path.join(out_dir, f"{base}_manifest.json")

    if schem:
        with open(schem_path, "wb") as f:
            f.write(schem)
    with open(manifest_path, "w", encoding="utf-8") as f:
        json.dump(manifest, f, indent=2)

    return schem_path, manifest_path
