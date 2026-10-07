"""Generates engine/src/main/java/dev/beyondtabs/engine/gen/*.java from design/*.json.
One row -> one record constant, built from that row's columns. Run after editing a sheet; never edit gen/ by hand."""
import json, os, re
ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
D = os.path.join(ROOT, "design"); OUT = os.path.join(ROOT, "engine/src/main/java/dev/beyondtabs/engine/gen")
S = {f[:-5]: json.load(open(os.path.join(D, f))) for f in os.listdir(D) if f.endswith(".json")}
def camel(s): return re.sub(r"_(.)", lambda m: m.group(1).upper(), s)
def const(s): return re.sub(r"[^A-Z0-9_]", "_", s.upper())
def jtype(v):
    if isinstance(v, bool): return "boolean"
    if isinstance(v, int): return "int"
    if isinstance(v, float): return "double"
    return "String"
def jlit(v, t):
    if t == "boolean": return "true" if v else "false"
    if t == "int": return str(int(v))
    if t == "double": return repr(float(v))
    return json.dumps(str(v))
def emit(sheet, cls, overrides=None):
    s = S[sheet]; cols = [c for c in s["columns"] if c != "unverified"]
    types = {}
    for c in cols:
        vals = [r[c] for r in s["rows"]]
        ts = {jtype(v) for v in vals}
        types[c] = "double" if ts <= {"int", "double"} and "double" in ts else ("String" if len(ts) > 1 else ts.pop())
    types.update(overrides or {})
    L = [f"// GENERATED from design/{sheet}.json by tools/gen_java.py - do not edit.", "package dev.beyondtabs.engine.gen;", "",
         "import java.util.List;", "import java.util.Map;", "import java.util.LinkedHashMap;", "",
         f"public record {cls}(" + ", ".join(f"{types[c]} {camel(c)}" for c in cols) + ") {"]
    for r in s["rows"]:
        L.append(f"    public static final {cls} {const(str(r['id']))} = new {cls}(" + ", ".join(jlit(r[c], types[c]) for c in cols) + ");")
    L.append(f"    public static final List<{cls}> ALL = List.of(" + ", ".join(const(str(r["id"])) for r in s["rows"]) + ");")
    L.append(f"    private static final Map<String, {cls}> BY_ID = new LinkedHashMap<>();")
    L.append(f"    static {{ for ({cls} x : ALL) BY_ID.put(x.id(), x); }}")
    L.append(f"    public static {cls} byId(String id) {{ {cls} x = BY_ID.get(id); if (x == null) throw new IllegalArgumentException(\"unknown {sheet} id \" + id); return x; }}")
    L.append("}")
    open(os.path.join(OUT, cls + ".java"), "w").write("\n".join(L) + "\n")
os.makedirs(OUT, exist_ok=True)
for f in os.listdir(OUT):
    if f.endswith(".java"): os.remove(os.path.join(OUT, f))
emit("units", "UnitDef"); emit("weapon_classes", "WeaponDef"); emit("bodies", "BodyDef"); emit("buildings", "BuildingDef")
emit("tech", "TechDef"); emit("races", "RaceDef"); emit("economy", "EconomyDef", {"value": "String"})
emit("performance", "PerfDef", {"value": "String"}); emit("controls", "ControlDef"); emit("ai", "AiDef"); emit("hooks", "HookDef"); emit("abilities", "AbilityDef"); emit("tactics", "TacticDef")
print("generated", sorted(os.listdir(OUT)))
