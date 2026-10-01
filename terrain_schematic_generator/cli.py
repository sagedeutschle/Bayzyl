import argparse
import json
import os

from .brief_to_spec import synthesize_spec
from .generator import generate_from_file, generate


def main() -> int:
    parser = argparse.ArgumentParser(description="Generate Sponge .schem from TerrainSpec")
    parser.add_argument("--spec", help="Path to TerrainSpec JSON")
    parser.add_argument("--brief", help="Natural language terrain brief")
    parser.add_argument("--size", help="Size as XxYxZ, e.g., 100x50x100")
    parser.add_argument("--out", default=".", help="Output directory")
    parser.add_argument("--emit-spec", action="store_true", help="Write synthesized TerrainSpec JSON")
    args = parser.parse_args()

    if not args.spec and not args.brief:
        parser.error("Provide --spec or --brief")

    if args.spec:
        schem_path, manifest_path = generate_from_file(args.spec, args.out)
        print(schem_path)
        print(manifest_path)
        return 0

    if not args.size:
        parser.error("--size is required with --brief")

    try:
        x, y, z = [int(p) for p in args.size.lower().split("x")]
    except Exception as exc:
        raise SystemExit("Invalid --size, use XxYxZ") from exc

    spec = synthesize_spec(args.brief, {"x": x, "y": y, "z": z})
    schem, manifest = generate(spec)

    os.makedirs(args.out, exist_ok=True)
    base = spec["name"]
    schem_path = os.path.join(args.out, f"{base}.schem")
    manifest_path = os.path.join(args.out, f"{base}_manifest.json")
    if schem:
        with open(schem_path, "wb") as f:
            f.write(schem)
    with open(manifest_path, "w", encoding="utf-8") as f:
        json.dump(manifest, f, indent=2)

    if args.emit_spec:
        spec_path = os.path.join(args.out, f"{base}_spec.json")
        if spec.get("biomeBlend") and "map" in spec["biomeBlend"]:
            spec["biomeBlend"].pop("map", None)
        with open(spec_path, "w", encoding="utf-8") as f:
            json.dump(spec, f, indent=2)
        print(spec_path)

    print(schem_path)
    print(manifest_path)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
