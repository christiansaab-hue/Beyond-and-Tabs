"""Writes BalanceTune output ("TUNE id value" lines on stdin or a file) into design/units.json."""
import json, sys
src = open(sys.argv[1]) if len(sys.argv) > 1 else sys.stdin
tune = {}
for line in src:
    p = line.split()
    if len(p) == 3 and p[0] == "TUNE": tune[p[1]] = round(float(p[2]), 3)
path = "design/units.json"; d = json.load(open(path))
for r in d["rows"]:
    if r["id"] in tune: r["tune"] = tune[r["id"]]
json.dump(d, open(path, "w"), indent=1)
print(f"applied {len(tune)} tune values")
