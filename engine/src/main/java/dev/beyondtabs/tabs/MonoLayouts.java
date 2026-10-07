package dev.beyondtabs.tabs;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Works out, on the player's PC, how a Unity game's scripts serialize their fields (Unity 2019.4 rules, plus Odin's
 * SerializedScriptableObject base used by some games), from the game's own managed assemblies. Produces type trees for
 * {@link TreeReader}. Nothing derived from a game is ever stored with the mod; it is rebuilt from the install each time.
 */
public final class MonoLayouts {
    final List<DotNetMeta> asms = new ArrayList<>();
    final Map<String, TypeNode> cache = new HashMap<>();

    static final Set<String> UNITY_OBJECTS = Set.of("Object", "MonoBehaviour", "ScriptableObject", "Component", "Behaviour", "GameObject",
            "Transform", "Rigidbody", "Material", "Mesh", "Texture2D", "Texture", "Sprite", "AudioClip", "Shader", "AnimationClip",
            "RuntimeAnimatorController", "Collider", "Renderer", "Animator", "PhysicMaterial", "ParticleSystem", "Camera", "Light", "Font",
            "TextAsset", "AudioSource", "Rigidbody2D", "LineRenderer", "TrailRenderer", "SkinnedMeshRenderer", "MeshRenderer", "MeshFilter");
    static final Map<String, String[]> UNITY_STRUCTS = Map.of(
            "Vector2", new String[]{"float", "x", "y"}, "Vector3", new String[]{"float", "x", "y", "z"}, "Vector4", new String[]{"float", "x", "y", "z", "w"},
            "Quaternion", new String[]{"float", "x", "y", "z", "w"}, "Color", new String[]{"float", "r", "g", "b", "a"}, "Color32", new String[]{"UInt8", "r", "g", "b", "a"},
            "Rect", new String[]{"float", "x", "y", "width", "height"}, "Bounds", new String[]{"float", "cx", "cy", "cz", "ex", "ey", "ez"},
            "Vector2Int", new String[]{"int", "x", "y"}, "Vector3Int", new String[]{"int", "x", "y", "z"});

    public void add(DotNetMeta m) { asms.add(m); }

    record Found(DotNetMeta asm, DotNetMeta.TypeDefRow t) { }
    Found find(String name) {
        for (DotNetMeta a : asms) { DotNetMeta.TypeDefRow t = a.byName.get(name); if (t != null) return new Found(a, t); }
        return null;
    }

    // ---------------------------------------------------------------- type signatures
    /** Parsed field type: kind = prim|class|valuetype|array|generic|object|other; name; ref(src asm row); args. */
    record Ty(String kind, String name, String ns, boolean isRef, Ty elem, List<Ty> args) { }

    static final Map<Integer, String> PRIM = Map.ofEntries(Map.entry(0x02, "bool"), Map.entry(0x03, "char"), Map.entry(0x04, "i8"), Map.entry(0x05, "u8"),
            Map.entry(0x06, "i16"), Map.entry(0x07, "u16"), Map.entry(0x08, "i32"), Map.entry(0x09, "u32"), Map.entry(0x0a, "i64"), Map.entry(0x0b, "u64"),
            Map.entry(0x0c, "f32"), Map.entry(0x0d, "f64"), Map.entry(0x0e, "string"));

    static final class Sig { final byte[] s; int i; Sig(byte[] s, int i) { this.s = s; this.i = i; }
        int u8() { return s[i++] & 0xff; }
        int compressed() { int b = u8(); if ((b & 0x80) == 0) return b; if ((b & 0xC0) == 0x80) return ((b & 0x3f) << 8) | u8(); return ((b & 0x1f) << 24) | (u8() << 16) | (u8() << 8) | u8(); }
    }

    Ty parse(DotNetMeta a, Sig g) {
        int e = g.u8();
        while (e == 0x1f || e == 0x20) { g.compressed(); e = g.u8(); }   // custom modifiers
        if (PRIM.containsKey(e)) return new Ty("prim", PRIM.get(e), "", false, null, null);
        switch (e) {
            case 0x11, 0x12 -> {
                int v = g.compressed(), tag = v & 3, idx = v >>> 2;
                String kind = e == 0x11 ? "valuetype" : "class";
                if (tag == 0) { var t = a.typeDefs.get(idx - 1); return new Ty(kind, t.name, t.ns, false, null, null); }
                if (tag == 1) { var t = a.typeRefs.get(idx - 1); return new Ty(kind, t.name, t.ns, true, null, null); }
                return new Ty("other", "?", "", false, null, null);
            }
            case 0x1d -> { Ty in = parse(a, g); return new Ty("array", "", "", false, in, null); }
            case 0x15 -> {
                Ty gen = parse(a, g); int n = g.compressed(); List<Ty> args = new ArrayList<>();
                for (int k = 0; k < n; k++) args.add(parse(a, g));
                return new Ty("generic", gen.name, gen.ns, gen.isRef, null, args);
            }
            case 0x1c -> { return new Ty("object", "Object", "System", true, null, null); }
            case 0x13, 0x1e -> { g.compressed(); return new Ty("other", "T", "", false, null, null); }
            case 0x0f, 0x10 -> { parse(a, g); return new Ty("other", "ptr", "", false, null, null); }
            case 0x18, 0x19 -> { return new Ty("prim", e == 0x18 ? "i64" : "u64", "", false, null, null); }
            default -> { return new Ty("other", "0x" + Integer.toHexString(e), "", false, null, null); }
        }
    }

    // ---------------------------------------------------------------- type classification
    String base(Found f) { String[] b = f.asm.baseOf(f.t); if (b == null) return null; return b[0].equals("System") && b[1].equals("Object") ? "System.Object" : b[1]; }
    boolean isUnityObject(String name, int depth) {
        if (UNITY_OBJECTS.contains(name)) return true;
        Found f = find(name); if (f == null || depth > 12) return false;
        String b = base(f); return b != null && !b.equals("System.Object") && isUnityObject(b, depth + 1);
    }
    boolean isEnum(String name) { Found f = find(name); return f != null && "Enum".equals(base(f)); }
    boolean isUnityEvent(String name, int depth) {
        if (name.startsWith("UnityEvent")) return true;
        Found f = find(name); if (f == null || depth > 6) return false;
        String b = base(f); return b != null && !b.equals("System.Object") && isUnityEvent(b, depth + 1);
    }

    boolean serializable(Ty t) {
        switch (t.kind) {
            case "prim": return true;
            case "array": return serializable(t.elem) && !t.elem.kind.equals("array") && !t.elem.kind.equals("generic");
            case "generic": return t.name.equals("List`1") && serializable(t.args.get(0)) && !t.args.get(0).kind.equals("array") && !t.args.get(0).kind.equals("generic");
            case "class": case "valuetype": {
                String n = t.name;
                if (isUnityEvent(n, 0) || UNITY_STRUCTS.containsKey(n) || n.equals("AnimationCurve") || n.equals("Gradient") || UNITY_OBJECTS.contains(n) || n.equals("LayerMask")) return true;
                if (t.ns.startsWith("System")) return t.kind.equals("valuetype");
                Found f = find(n);
                if (f == null) return t.kind.equals("valuetype");   // external enum
                if (isUnityObject(n, 0) || isEnum(n)) return true;
                String b = base(f); if ("MulticastDelegate".equals(b) || "Delegate".equals(b)) return false;
                int fl = f.t.flags;
                if ((fl & 0x20) != 0 || (fl & 0x80) != 0 && !isUnityObject(n, 0)) return false;   // interface / abstract
                return (fl & 0x2000) != 0;                                                          // [Serializable]
            }
            default: return false;
        }
    }

    record Field(String name, Ty type, boolean serRef) { }
    List<Field> fieldsOf(Found f, int depth) {
        List<Field> out = new ArrayList<>();
        String b = base(f);
        if (b != null && !b.equals("System.Object") && !UNITY_OBJECTS.contains(b) && !b.equals("ValueType") && !b.equals("Enum") && depth < 12) {
            Found bf = find(b); if (bf != null) out.addAll(fieldsOf(bf, depth + 1));
        }
        for (int i = f.t.fieldStart; i < f.t.fieldEnd; i++) {
            DotNetMeta.FieldRow fr = f.asm.fields.get(i - 1);
            int fl = fr.flags;
            if ((fl & 0x10) != 0 || (fl & 0x40) != 0 || (fl & 0x20) != 0 || (fl & 0x80) != 0) continue;   // static, literal, readonly, [NonSerialized]
            List<String> attrs = f.asm.fieldAttrs.getOrDefault(fr.index, List.of());
            boolean pub = (fl & 7) == 6;
            if (!pub && !attrs.contains("SerializeField") && !attrs.contains("SerializeReference")) continue;
            Ty ty = parse(f.asm, new Sig(fr.sig, 1));
            if (attrs.contains("SerializeReference")) { out.add(new Field(fr.name, ty, true)); continue; }
            if (!serializable(ty)) continue;
            out.add(new Field(fr.name, ty, false));
        }
        return out;
    }

    // ---------------------------------------------------------------- type trees
    static TypeNode N(String t, String n, boolean align, TypeNode... ch) { return new TypeNode(t, n, align, List.of(ch)); }
    static TypeNode string(String n) { return N("string", n, false, N("Array", "Array", true, N("int", "size", false), N("char", "data", false))); }
    static TypeNode pptr(String n) { return N("PPtr<Object>", n, false, N("int", "m_FileID", false), N("SInt64", "m_PathID", false)); }
    static TypeNode vector(String n, TypeNode elem) { return N("vector", n, false, N("Array", "Array", true, N("int", "size", false), elem)); }
    static final Map<String, String[]> PRIM_NODE = Map.ofEntries(Map.entry("bool", new String[]{"bool", "1"}), Map.entry("u8", new String[]{"UInt8", "1"}),
            Map.entry("i8", new String[]{"SInt8", "1"}), Map.entry("i16", new String[]{"SInt16", "1"}), Map.entry("u16", new String[]{"UInt16", "1"}),
            Map.entry("char", new String[]{"UInt16", "1"}), Map.entry("i32", new String[]{"int", "0"}), Map.entry("u32", new String[]{"unsigned int", "0"}),
            Map.entry("i64", new String[]{"SInt64", "0"}), Map.entry("u64", new String[]{"UInt64", "0"}), Map.entry("f32", new String[]{"float", "0"}),
            Map.entry("f64", new String[]{"double", "0"}));

    TypeNode node(String name, Ty t, boolean inArray, int depth) {
        if (depth > 10) throw new IllegalStateException("layout too deep at " + name);
        switch (t.kind) {
            case "prim": {
                if (t.name.equals("string")) return string(name);
                String[] p = PRIM_NODE.get(t.name); return N(p[0], name, !inArray && p[1].equals("1"));
            }
            case "array": return vector(name, node("data", t.elem, true, depth + 1));
            case "generic": return vector(name, node("data", t.args.get(0), true, depth + 1));
            default: {
                String n = t.name;
                String[] st = UNITY_STRUCTS.get(n);
                if (st != null) { TypeNode[] ch = new TypeNode[st.length - 1]; for (int i = 1; i < st.length; i++) ch[i - 1] = N(st[0], st[i], false); return new TypeNode(n, name, false, List.of(ch)); }
                if (n.equals("LayerMask")) return N("LayerMask", name, false, N("unsigned int", "m_Bits", false));
                if (n.equals("AnimationCurve")) {
                    TypeNode kf = N("Keyframe", "data", false, N("float", "time", false), N("float", "value", false), N("float", "inSlope", false), N("float", "outSlope", false),
                            N("int", "weightedMode", false), N("float", "inWeight", false), N("float", "outWeight", false));
                    return N("AnimationCurve", name, false, vector("m_Curve", kf), N("int", "m_PreInfinity", false), N("int", "m_PostInfinity", false), N("int", "m_RotationOrder", false));
                }
                if (n.equals("Gradient")) return N("Skip164", name, true);
                if (isUnityEvent(n, 0)) {
                    TypeNode args = N("ArgumentCache", "m_Arguments", false, pptr("m_ObjectArgument"), string("m_ObjectArgumentAssemblyTypeName"), N("int", "m_IntArgument", false),
                            N("float", "m_FloatArgument", false), string("m_StringArgument"), N("bool", "m_BoolArgument", true));
                    TypeNode call = N("PersistentCall", "data", false, pptr("m_Target"), string("m_MethodName"), N("int", "m_Mode", false), args, N("int", "m_CallState", false));
                    return N("UnityEvent", name, false, N("PersistentCallGroup", "m_PersistentCalls", false, vector("m_Calls", call)));
                }
                if (t.ns.startsWith("System") && t.kind.equals("valuetype")) return N("int", name, false);
                Found f = find(n);
                if (f == null) return t.kind.equals("valuetype") ? N("int", name, false) : pptr(name);
                if (isEnum(n)) return N("int", name, false);
                if (isUnityObject(n, 0) || t.kind.equals("object")) return pptr(name);
                List<TypeNode> ch = new ArrayList<>();
                for (Field fd : fieldsOf(f, 0)) ch.add(fd.serRef ? N("SInt64", fd.name, false) : node(fd.name, fd.type, false, depth + 1));
                return new TypeNode(n, name, false, ch);
            }
        }
    }

    /** The full type tree of a MonoBehaviour / ScriptableObject script class (with the MonoBehaviour header). */
    public TypeNode script(String className) {
        return cache.computeIfAbsent(className, cn -> {
            Found f = find(cn);
            if (f == null) return null;
            List<TypeNode> ch = new ArrayList<>(List.of(pptr("m_GameObject"), N("UInt8", "m_Enabled", true), pptr("m_Script"), string("m_Name")));
            for (Field fd : fieldsOf(f, 0)) ch.add(fd.serRef ? N("SInt64", fd.name, false) : node(fd.name, fd.type, false, 0));
            return new TypeNode(cn, "Base", false, ch);
        });
    }
}
