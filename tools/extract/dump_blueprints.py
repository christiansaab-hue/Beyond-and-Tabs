"""Reads every UnitBlueprint and Faction from the player's TABS install into tabs_units.json (local only, never shipped)."""
import sys, json, struct, UnityPy
from monolayout import Asm, Layout
TABS = sys.argv[1]; OUT = sys.argv[2]
DATA = TABS.rstrip("/\\") + "/TotallyAccurateBattleSimulator_Data/"
asm = Asm(DATA + "Managed/Assembly-CSharp.dll"); Asm(DATA + "Managed/Sirenix.Serialization.dll"); L = Layout(asm)
res = list(UnityPy.load(DATA + "resources.assets").files.values())[0]
sh = list(UnityPy.load(DATA + "sharedassets0.assets").files.values())[0]
gg = list(UnityPy.load(DATA + "globalgamemanagers.assets").files.values())[0]
rext = [None] + [e.path for e in res.externals]; sext = [None] + [e.path for e in sh.externals]
def script_class(raw, ext):
    fid, pid = struct.unpack_from("<iq", raw, 16)
    if ext[fid] == "globalgamemanagers.assets" and pid in gg.objects: return gg.objects[pid].read().m_ClassName
def name(pp):
    fid, pid = pp
    if fid != 0 or pid == 0 or pid not in sh.objects: return None
    try: return sh.objects[pid].peek_name()
    except Exception: return "?"
db = res.objects[[p for p, o in res.objects.items() if o.type.name == "MonoBehaviour" and o.peek_name() == "Landfall Unit Database"][0]].get_raw_data()
units, factions = {}, {}
for off in range(28, len(db) - 12, 4):
    fid, pid = struct.unpack_from("<iq", db, off)
    if not (1 <= fid < len(rext) and rext[fid] == "sharedassets0.assets" and pid in sh.objects): continue
    o = sh.objects[pid]
    if o.type.name != "MonoBehaviour": continue
    raw = o.get_raw_data(); cls = script_class(raw, sext)
    if cls not in ("UnitBlueprint", "Faction") or pid in units or pid in factions: continue
    try: d = L.read_mono(raw, cls)
    except Exception as e: print("skip", cls, pid, e); continue
    if d["_consumed"][0] != d["_consumed"][1]: print("WARN partial read", cls, d["_head"]["m_Name"], d["_consumed"])
    if cls == "UnitBlueprint":
        units[pid] = dict(path_id=pid, blueprint=d["_head"]["m_Name"], key=d["m_entity"]["m_name"], icon=d["m_entity"]["m_spriteIcon"][1],
            health=d["health"], speed=d["movementSpeedMuiltiplier"], anim=d["animationMultiplier"], mass=d["massMultiplier"],
            size=d["sizeMultiplier"], size_rand=[d["minSizeRandom"], d["maxSizeRandom"]], balance=d["balanceMultiplier"],
            balance_force=d["balanceForceMultiplier"], drag=d["dragMultiplier"], turn=d["turnSpeed"], cost_tweak=d["costTweak"],
            force_cost=d["forceCost"], secret=d["m_isSecret"], two_hands=d["holdinigWithTwoHands"],
            unit_base=[d["m_unitBase"][1], name(d["m_unitBase"])], right_weapon=[d["m_rightWeapon"][1], name(d["m_rightWeapon"])],
            left_weapon=[d["m_leftWeapon"][1], name(d["m_leftWeapon"])],
            props=[[p[1], name(p)] for p in d["m_props_primary"]], prop_data=d["m_propData"],
            children=[[p[1], name(p)] for p in d["objectsToSpawnAsChildren"]],
            riders=[r[1] for r in d["Riders"]], mount=d["Mount"][1], vocal=d["vocalRef"], death=d["deathRef"], foot=d["footRef"],
            hidden_parts=[k for k in ("Head","Hip","Torso","ArmLeft","ElbowLeft","ArmRight","ElbowRight","LegLeft","KneeLeft","LegRight","KneeRight","WristLeft","WristRight","FootLeft","FootRight") if d[k]])
    else:
        factions[pid] = dict(path_id=pid, name=d["_head"]["m_Name"], fields={k: v for k, v in d.items() if not k.startswith("_") and k != "serializationData"})
json.dump({"units": list(units.values()), "factions": list(factions.values())}, open(OUT, "w"), indent=1, default=str)
print(len(units), "units", len(factions), "factions ->", OUT)

# ---- weapons: real combat numbers from each weapon prefab and its projectile ----
def comps_of(pid):
    out = []
    if not pid or pid not in sh.objects: return out
    go = sh.objects[pid].read()
    for c in go.m_Component:
        o = c.component.deref()
        if o.type.name != "MonoBehaviour": continue
        raw = o.get_raw_data(); cls = script_class(raw, sext)
        if cls is None: continue
        try: out.append((cls, L.read_mono(raw, cls)))
        except Exception as e: out.append((cls, None))
    return out

def projectile_stats(pid):
    p = {"prefab": name((0, pid))}
    for cls, d in comps_of(pid):
        if d is None: p.setdefault("_unread", []).append(cls); continue
        if cls == "ProjectileHit": p.update(damage=d["damage"], force=d["force"], hits=d.get("allowedHits"))
        elif cls == "MoveTransform": p.update(gravity=d["gravity"], drag=d["drag"])
        elif "radius" in d and ("damage" in d or "force" in d):
            p.setdefault("aoe", []).append({"component": cls, "radius": d.get("radius"), "damage": d.get("damage"), "force": d.get("force")})
    return p

def weapon_stats(pid):
    if not pid or pid not in sh.objects: return None
    out = {"name": name((0, pid))}
    for cls, d in comps_of(pid):
        if d is None: out.setdefault("_unread", []).append(cls); continue
        if cls in ("MeleeWeapon", "RangeWeapon") or (d.get("internalCooldown") is not None and d.get("maxRange") is not None):
            out.update(kind="ranged" if d.get("isRange") or cls == "RangeWeapon" or "objectToSpawn" in d else "melee",
                       cooldown=d["internalCooldown"], range=d["maxRange"], attack_speed=d.get("attackSpeedM"), cls=cls)
            if d.get("objectToSpawn") and d["objectToSpawn"][1]:
                out["projectile"] = projectile_stats(d["objectToSpawn"][1])
                out.update(count=d.get("numberOfObjects"), spread=d.get("spread"), launch_force=d.get("force"))
        elif cls == "CollisionWeapon":
            out.update(damage=d["damage"], impact=d["impactMultiplier"], impact_force=d["onImpactForce"], mass_cap=d["massCap"])
        elif cls == "Cost":
            out["cost"] = d["m_cost"]
    return out
ws = {}
for u in units.values():
    for side in ("right_weapon", "left_weapon"):
        pid = u[side][0]
        if pid and pid not in ws: ws[pid] = weapon_stats(pid)
    u["weapons"] = [ws.get(u["right_weapon"][0]), ws.get(u["left_weapon"][0])]
json.dump({"units": list(units.values()), "factions": list(factions.values())}, open(OUT, "w"), indent=1, default=str)
print(len(ws), "weapons read")
