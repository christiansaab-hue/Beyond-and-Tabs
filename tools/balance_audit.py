#!/usr/bin/env python3
"""
Balance audit for the bar/ fork — run before every build that ships (part of the safety protocol, with
tools/check_java.py). Replicates ResourceCost.bakeValues offline: parses the config defaults, converts
RoN food/wood/ore to BAR Metal/Energy, and checks the economy's shape:
  - nothing (unit, building, research) may cost zero unless whitelisted
  - every cost constant defined in ResourceCosts must be baked in bakeValues
  - extractor/wind payback times stay in a sane band
  - a standard opening (extractors+winds) must afford the bot's army waves
Exit 1 on any failure so it can gate a push.
"""
import re, sys, math, os

ROOT = os.path.join(os.path.dirname(__file__), '..', 'bar/src/main/java/com/solegendary/reignofnether')

def rd(p):
    return open(os.path.join(ROOT, p)).read()

def to_metal(food, wood, ore): return round((ore + food * 0.6 + wood * 0.3) / 5) * 5
def to_energy(food, wood, ore): return round((wood * 1.5 + food * 0.8) / 10) * 10

cfg = rd('config/ReignOfNetherCommonConfigs.java')
entries = {}
for m in re.finditer(r'ResourceCostConfigEntry (\w+) = ResourceCostConfigEntry\.(Building|Unit|Research|Ability)\(\s*([-\d]+)\s*,\s*([-\d]+)\s*,\s*([-\d]+)\s*,\s*([-\d]+)\s*(?:,\s*([-\d]+))?', cfg):
    name, kind = m.group(1), m.group(2)
    a = [int(x) for x in m.groups()[2:] if x is not None]
    food, wood, ore = a[0], a[1], a[2]
    entries[name] = dict(kind=kind, food=food, wood=wood, ore=ore,
                         metal=to_metal(food, wood, ore), energy=to_energy(food, wood, ore))

costs_src = rd('resources/ResourceCosts.java')
declared = set(re.findall(r'ResourceCost (\w+) = new ResourceCost\(ID, "(?:\w+)"\);', costs_src))
baked = set(re.findall(r'(\w+)\.bakeValues\(', costs_src))

fails, warns = [], []

# 1) every declared configurable cost must be baked
for name in sorted(declared - baked):
    fails.append(f"NOT BAKED (cost stays zero): {name}")

# 2) free things (post-conversion) - abilities may be free, nothing else
# heroes (HeroProductionItem) are free to first-summon by design; pets/scenario units too
FREE_OK = {'SILVERFISH', 'BEE', 'PIGLIN_MERCHANT', 'WILDFIRE', 'HERO_EXTRA_REVIVE_COST_PER_LEVEL',
           'ENCHANTER', 'NECROMANCER', 'ROYAL_GUARD', 'WRETCHED_WRAITH'}
for name, e in sorted(entries.items()):
    if e['kind'] in ('Building', 'Unit') and e['metal'] == 0 and e['energy'] == 0 and name not in FREE_OK:
        fails.append(f"FREE {e['kind'].upper()}: {name} (food={e['food']} wood={e['wood']} ore={e['ore']} -> 0 metal, 0 energy)")

# 3) economy shape - keep in sync with the Java constants
EXTRACTOR_INCOME, WIND_INCOME = 2.0, 14.0      # per second (MetalExtractor / WindGenerator)
BASE_METAL, BASE_ENERGY = 2.0, 20.0            # EconomyServerEvents with a capitol
ex, wd = entries.get('METAL_EXTRACTOR'), entries.get('WIND_GENERATOR')
if ex:
    payback = ex['metal'] / EXTRACTOR_INCOME
    if not 15 <= payback <= 60:
        fails.append(f"Extractor metal payback {payback:.0f}s outside 15-60s (cost {ex['metal']}m/{ex['energy']}e)")
if wd:
    payback = wd['energy'] / WIND_INCOME if WIND_INCOME else 1e9
    if not 2 <= payback <= 40:
        warns.append(f"Wind energy payback {payback:.0f}s (cost {wd['metal']}m/{wd['energy']}e)")

# 4) can a normal opening afford the bot's waves?
#    income by ~6 min with 4 extractors and 4 winds; a medium wave = 14 t1 fighters
inc_m = BASE_METAL + 4 * EXTRACTOR_INCOME
inc_e = BASE_ENERGY + 4 * WIND_INCOME
wave = [entries.get('VINDICATOR'), entries.get('ZOMBIE')]
for u in wave:
    if not u:
        continue
    per_min_m, per_min_e = inc_m * 60, inc_e * 60
    affordable_m = per_min_m / max(1, u['metal'])
    affordable_e = per_min_e / max(1, u['energy'])
    rate = min(affordable_m, affordable_e)
    if rate < 3:
        fails.append(f"Army too expensive: ~{rate:.1f} per minute at a 4-ex/4-wind economy "
                     f"(unit {u['metal']}m/{u['energy']}e, income {inc_m:.0f}m/{inc_e:.0f}e per s)")
    elif rate > 30:
        warns.append(f"Army very cheap: ~{rate:.1f} per minute (unit {u['metal']}m/{u['energy']}e)")

print(f"{len(entries)} cost entries, {len(declared)} configurable constants, {len(baked)} baked")
for w in warns:
    print("warn:", w)
for f in fails:
    print("FAIL:", f)
if not fails:
    print("balance audit: OK")
sys.exit(1 if fails else 0)
