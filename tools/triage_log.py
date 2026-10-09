#!/usr/bin/env python3
"""
Boil a Minecraft latest.log down to what matters for a Beyond and Tabs playtest.

    python3 tools/triage_log.py <latest.log> [--all]

Prints, per game launch: launch time, every skirmish start / battlefield / wall / bot / defeat / victory line,
every mod ERROR with the first frames of its stack (deduplicated with a count), and the first lines of any
"think failed" / NPE. Vanilla resource-pack noise (blockstates, missing textures, sprite loader) is dropped.
"""
import re
import sys
from collections import Counter, OrderedDict

NOISE = re.compile(r"blockstate|SpriteLoader|Missing pack_format|JsonSyntax|Unknown item id|Error loading class|"
                   r"uses unexpected schema|VersionChecker|Found non-pack entry|does not exist, cannot add it|"
                   r"farmersdelight|Could not load image")
INTEREST = re.compile(r"\[Skirmish\]|\[Battlefield|\[Player\] (startRTS|defeat)|\[Defeat\]|\[Bot\]|bot_added|"
                      r"is victorious|is defeated|players? remaining|Deleting level|Stopping!|Stopping server|"
                      r"StartAreaClearing|late base|\[Building\] .*placed|placeBuilding|Saving chunks for level "
                      r"'ServerLevel\[.*overworld|RTS pathfinder|Loaded \d+ hero", re.I)
LAUNCH = re.compile(r"ModLauncher running")
ERR = re.compile(r"/(ERROR|FATAL)\]")
TS = re.compile(r"^\[(\d\d\w{3}\d{4} [\d:.]+)\]")


def short(line, n=170):
    line = line.rstrip("\n")
    return line if len(line) <= n else line[:n] + "..."


def main(path, show_all=False):
    lines = open(path, errors="replace").readlines()
    launches = 0
    errors = OrderedDict()
    i = 0
    while i < len(lines):
        line = lines[i]
        if LAUNCH.search(line):
            launches += 1
            ts = TS.match(line)
            print(f"\n=== LAUNCH {launches} at {ts.group(1) if ts else '?'} ===")
        elif ERR.search(line) and not NOISE.search(line):
            key = re.sub(r"^\[[^\]]+\] ", "", line).strip()
            key = re.sub(r"Bot\d+", "BotN", key)
            frames = []
            j = i + 1
            while j < len(lines) and (lines[j].startswith("\t") or lines[j].startswith("java.") or
                                      lines[j].startswith("Caused") or lines[j].startswith("com.")):
                if "reignofnether" in lines[j] or lines[j].startswith("java.") or lines[j].startswith("Caused"):
                    frames.append(lines[j].strip())
                j += 1
            if key not in errors:
                errors[key] = [1, frames[:6], TS.match(line).group(1) if TS.match(line) else "?"]
            else:
                errors[key][0] += 1
            i = j
            continue
        elif INTEREST.search(line) and not NOISE.search(line):
            print(short(re.sub(r"\[(Server|Render) thread/INFO\] \[[^\]]+\]: ", "", line)))
        elif show_all and "reignofnether" in line and not NOISE.search(line):
            print(short(line))
        i += 1

    if errors:
        print("\n=== MOD ERRORS (deduplicated) ===")
        for key, (count, frames, first) in errors.items():
            print(f"x{count}  first at {first}: {short(key)}")
            for f in frames:
                print("     " + short(f, 150))
    else:
        print("\n(no mod errors)")
    print(f"\n{launches} launch(es), {len(lines)} lines")


if __name__ == "__main__":
    if len(sys.argv) < 2:
        print(__doc__)
        sys.exit(2)
    main(sys.argv[1], "--all" in sys.argv)
