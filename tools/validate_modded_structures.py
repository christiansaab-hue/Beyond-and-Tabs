#!/usr/bin/env python3
"""Checks that every modded block in our structure NBTs exists in its mod jar and that its property
combination is a real blockstate variant. A typo'd modded block silently loads as AIR in game, so run
this before pushing structure changes that use mod blocks.

Usage: python3 tools/validate_modded_structures.py <dir-with-mod-jars> [structure.nbt ...]
       (no structures given = every .nbt in bar/.../structures)
Get the jars by staging them from lovish's instance mods folder (they are not in the repo).
"""
import glob, json, os, sys, zipfile
sys.path.insert(0, os.path.dirname(__file__))
from preview_structure import load_nbt

STRUCTS = "bar/src/main/resources/assets/reignofnether/structures"


def jar_index(jar_dir):
    idx = {}
    for jar in glob.glob(os.path.join(jar_dir, "*.jar")):
        z = zipfile.ZipFile(jar)
        for n in z.namelist():
            parts = n.split("/")
            if len(parts) == 4 and parts[0] == "assets" and parts[2] == "blockstates" and n.endswith(".json"):
                idx[f"{parts[1]}:{parts[3][:-5]}"] = (z, n)
    # the game's own blocks live in the repo, not a jar
    for path in glob.glob("bar/src/main/resources/assets/*/blockstates/*.json"):
        parts = path.split("/")
        idx[f"{parts[-3]}:{os.path.basename(path)[:-5]}"] = (None, path)
    return idx


def main(argv):
    if not argv:
        print(__doc__); return 2
    idx = jar_index(argv[0])
    files = argv[1:] or sorted(glob.glob(os.path.join(STRUCTS, "*.nbt")))
    bad = 0
    for f in files:
        for p in load_nbt(f).get("palette", []):
            name = p.get("Name", "")
            if name.startswith("minecraft:"):
                continue
            if name not in idx:
                print(f"MISSING  {os.path.basename(f)}: {name}"); bad += 1; continue
            z, n = idx[name]
            bs = json.loads(z.read(n) if z else open(n).read())
            props = p.get("Properties", {})
            if "variants" in bs and not any(
                    all(kv.split("=")[1] == props.get(kv.split("=")[0]) for kv in k.split(",") if "=" in kv)
                    for k in bs["variants"]):
                print(f"BAD      {os.path.basename(f)}: {name} {props}"); bad += 1
    print("ALL VALID" if not bad else f"{bad} PROBLEM(S)")
    return 1 if bad else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
