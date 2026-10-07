"""Preflight: overlays every design sheet, lists unfilled cells, unverified cells and unresolved references.
Exit code 0 only when clean. Usage: python3 tools/preflight.py [--allow-unverified]"""
import json, glob, os, sys
D = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "design")
S = {os.path.basename(p)[:-5]: json.load(open(p)) for p in sorted(glob.glob(os.path.join(D, "*.json")))}
ids = {n: {r["id"] for r in s["rows"]} for n, s in S.items()}
empty, unver, refs = [], [], []
def ref(sheet, row, col, target_sheets, value, allow=("none",)):
    for v in str(value).split(","):
        v = v.strip()
        if not v or v in allow or v.startswith("mod:"): continue
        if not any(v in ids[t] for t in target_sheets):
            refs.append(f"{sheet}.{row}.{col} -> '{v}' not found in {'/'.join(target_sheets)}")
for n, s in S.items():
    for r in s["rows"]:
        for c in s["columns"]:
            if c == "unverified": continue
            v = r.get(c)
            if v is None or v == "" or v == "?": empty.append(f"{n}.{r.get('id')}.{c}")
        for c in r.get("unverified", []): unver.append(f"{n}.{r['id']}.{c}")
for r in S["units"]["rows"]:
    ref("units", r["id"], "race", ["races"], r["race"])
    ref("units", r["id"], "factory", ["buildings"], r["factory"])
    ref("units", r["id"], "weapon_class", ["weapon_classes"], r["weapon_class"])
    ref("units", r["id"], "physics_profile", ["bodies"], r["physics_profile"])
for r in S["buildings"]["rows"]:
    ref("buildings", r["id"], "race", ["races"], r["race"])
    ref("buildings", r["id"], "requires_tech", ["tech"], r["requires_tech"])
    if r["kind"] == "factory" and not any(u["factory"] == r["id"] for u in S["units"]["rows"]):
        refs.append(f"buildings.{r['id']}: factory produces no unit")
    if len(str(r["value_per_level"]).split(",")) != r["levels"]:
        refs.append(f"buildings.{r['id']}.value_per_level: {r['levels']} levels but values '{r['value_per_level']}'")
for r in S["tech"]["rows"]:
    ref("tech", r["id"], "race", ["races"], r["race"])
    ref("tech", r["id"], "researched_at", ["buildings"], r["researched_at"])
    ref("tech", r["id"], "requires", ["tech"], r["requires"])
    ref("tech", r["id"], "unlocks", ["buildings", "units"], r["unlocks"])
# every first-playable race needs a commander, a builder, and units at tiers 1..3
for race in [r for r in S["races"]["rows"] if r["first_playable"]]:
    us = [u for u in S["units"]["rows"] if u["race"] == race["id"]]
    for need in ("commander", "builder"):
        if not any(u["role"] == need for u in us): refs.append(f"races.{race['id']}: no {need}")
    for t in (1, 2, 3):
        if not any(u["tier"] == t for u in us): refs.append(f"races.{race['id']}: no tier {t} unit")
# economy derivations must match the formulas
E = {r["id"]: r["value"] for r in S["economy"]["rows"]}
for u in S["units"]["rows"]:
    m = int(round(u["base_cost"] * E["unit_cost_metal_factor"] / 5) * 5) or 5
    if u["metal"] != m: refs.append(f"units.{u['id']}.metal {u['metal']} != formula {m}")
allow_unver = "--allow-unverified" in sys.argv
print(f"sheets: {len(S)}  rows: {sum(len(s['rows']) for s in S.values())}")
for title, lst in (("UNFILLED", empty), ("UNRESOLVED", refs), ("UNVERIFIED", unver)):
    print(f"\n{title}: {len(lst)}")
    for x in lst[:40]: print("  ", x)
    if len(lst) > 40: print(f"   ... {len(lst)-40} more")
bad = empty or refs or (unver and not allow_unver)
print("\nPREFLIGHT", "FAIL" if bad else "CLEAN")
sys.exit(1 if bad else 0)
