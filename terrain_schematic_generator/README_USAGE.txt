Usage examples:

1) Generate from TerrainSpec JSON:
python3 -m terrain_schematic_generator.cli --spec terrain_spec_snowy_alpine.json --out out

2) Generate from a brief:
python3 -m terrain_schematic_generator.cli --brief "snowy alpine ridge with cliffs and sparse pines, with a few small lakes" --size 100x50x100 --out out --emit-spec
