"""Builds Unity serialization layouts for TABS MonoBehaviours from Assembly-CSharp.dll metadata
(field order + types, Unity 2019.4 rules), so their data can be read without Unity's type trees."""
import dnfile, struct

PRIM = {0x02: "bool", 0x03: "char", 0x04: "i8", 0x05: "u8", 0x06: "i16", 0x07: "u16", 0x08: "i32",
        0x09: "u32", 0x0a: "i64", 0x0b: "u64", 0x0c: "f32", 0x0d: "f64", 0x0e: "string"}
UNITY_STRUCTS = {  # serialized size layouts of common Unity value types
    "Vector2": ["f32"] * 2, "Vector3": ["f32"] * 3, "Vector4": ["f32"] * 4, "Quaternion": ["f32"] * 4,
    "Color": ["f32"] * 4, "Color32": ["u8"] * 4, "Rect": ["f32"] * 4, "Bounds": ["f32"] * 6,
    "Vector2Int": ["i32"] * 2, "Vector3Int": ["i32"] * 3, "LayerMask": ["u32"],
}
UNITY_OBJECT_BASES = {"Object", "MonoBehaviour", "ScriptableObject", "Component", "Behaviour", "GameObject",
                      "Transform", "Rigidbody", "Material", "Mesh", "Texture2D", "Texture", "Sprite", "AudioClip",
                      "Shader", "AnimationClip", "RuntimeAnimatorController", "Collider", "Renderer", "Animator",
                      "PhysicMaterial", "ParticleSystem", "Camera", "Light", "Font", "TextAsset", "AudioSource"}

class Asm:
    def __init__(self, path):
        self.pe = dnfile.dnPE(path); md = self.pe.net.mdtables; self.md = md
        Asm.world.append(self)
        self.typedefs = md.TypeDef.rows; self.typerefs = md.TypeRef.rows
        self.by_name = {}
        for t in self.typedefs: self.by_name.setdefault(str(t.TypeName), t)
        self.attr_fields = {}  # field rid -> set(attr names)
        for ca in (md.CustomAttribute.rows if md.CustomAttribute else []):
            try:
                parent = ca.Parent; ctor = ca.Type.row
                cls = ctor.Class.row if hasattr(ctor, "Class") else None
                an = str(cls.TypeName) if cls is not None and hasattr(cls, "TypeName") else ""
                if parent.table and parent.table.name == "Field":
                    self.attr_fields.setdefault(parent.row_index, set()).add(an)
            except Exception: pass
        self.enums = set()
        for t in self.typedefs:
            b = self.base_name(t)
            if b == "Enum": self.enums.add(str(t.TypeName))

    def base_name(self, t):
        try:
            r = t.Extends.row
            if r is None: return None
            ns, n = str(r.TypeNamespace), str(r.TypeName)
            if ns == "System" and n == "Object": return "System.Object"
            return n
        except Exception: return None

    def is_enum_ref(self, name): return name in self.enums
    def is_unity_object(self, t, depth=0):
        if t is None or depth > 12: return False
        n = str(t.TypeName)
        if n in UNITY_OBJECT_BASES: return True
        b = self.base_name(t)
        if b in UNITY_OBJECT_BASES: return True
        a2, bt = self.find(b) if b else (None, None)
        return a2.is_unity_object(bt, depth + 1) if bt is not None else False

    def _coded(self, sig, i):
        # compressed unsigned int
        b = sig[i]
        if b & 0x80 == 0: return b, i + 1
        if b & 0xC0 == 0x80: return ((b & 0x3F) << 8) | sig[i + 1], i + 2
        return ((b & 0x1F) << 24) | (sig[i + 1] << 16) | (sig[i + 2] << 8) | sig[i + 3], i + 4

    def _typedeforref(self, sig, i):
        v, i = self._coded(sig, i)
        tag, idx = v & 3, v >> 2
        if tag == 0: t = self.typedefs[idx - 1]; return ("def", str(t.TypeName), t), i
        if tag == 1: t = self.typerefs[idx - 1]; return ("ref", str(t.TypeName), t), i
        return ("spec", "?", None), i

    def parse_type(self, sig, i):
        e = sig[i]; i += 1
        if e in PRIM: return ("prim", PRIM[e]), i
        if e in (0x11, 0x12):  # valuetype / class
            (k, name, t), i = self._typedeforref(sig, i)
            return ("valuetype" if e == 0x11 else "class", name, t, k), i
        if e == 0x1d:  # SZARRAY
            inner, i = self.parse_type(sig, i); return ("array", inner), i
        if e == 0x15:  # GENERICINST
            gen, i = self.parse_type(sig, i)
            n, i = self._coded(sig, i); args = []
            for _ in range(n): a, i = self.parse_type(sig, i); args.append(a)
            return ("generic", gen[1], args), i
        if e == 0x1c: return ("object",), i
        if e in (0x13, 0x1e): _, i = self._coded(sig, i); return ("genericparam",), i
        if e == 0x0f: inner, i = self.parse_type(sig, i); return ("ptr", inner), i
        if e == 0x18: return ("prim", "i64"), i
        return ("unknown", hex(e)), i

    def is_unity_event(self, name, t, depth=0):
        if name.startswith("UnityEvent"): return True
        if t is None or depth > 6 or not hasattr(t, "Extends"): return False
        b = self.base_name(t)
        if not b or b == "System.Object": return False
        if b.startswith("UnityEvent"): return True
        a2, bt = self.find(b)
        return a2.is_unity_event(b, bt, depth + 1) if bt is not None else False

    def serializable(self, ty):
        k = ty[0]
        if k in ("prim",): return True
        if k == "array": return self.serializable(ty[1]) and ty[1][0] != "array"
        if k == "generic": return ty[1] == "List`1" and self.serializable(ty[2][0]) and ty[2][0][0] not in ("array", "generic")
        if k in ("valuetype", "class"):
            name, t, src = ty[1], ty[2], ty[3]
            if self.is_unity_event(name, t): return True
            if name in UNITY_STRUCTS or name in ("AnimationCurve", "Gradient") or name in UNITY_OBJECT_BASES: return True
            if src == "ref":
                try:
                    ns = str(t.TypeNamespace)
                except Exception: ns = ""
                if ns.startswith("System"): return k == "valuetype" and name not in ("Nullable`1",)
                a2, t2 = self.find(name)
                if t2 is None: return k == "valuetype" or name in UNITY_OBJECT_BASES
                return a2.serializable((k, name, t2, "def"))
            if self.is_unity_object(t): return True
            if str(self.base_name(t)) == "Enum": return True
            if str(self.base_name(t)) in ("MulticastDelegate", "Delegate"): return False
            fl = t.Flags
            if getattr(fl, "tdInterface", False) or getattr(fl, "tdAbstract", False) and not self.is_unity_object(t): return False
            return bool(getattr(fl, "tdSerializable", False))
        return False

    world = []  # all loaded assemblies, for cross-assembly base classes
    def find(self, name):
        if name in self.by_name: return self, self.by_name[name]
        for a in Asm.world:
            if name in a.by_name: return a, a.by_name[name]
        return None, None

    def fields_of(self, t):
        out = []
        b = self.base_name(t)
        if b and b not in UNITY_OBJECT_BASES and b not in ("ValueType", "Enum", "System.Object", "Attribute", "Exception"):
            a2, bt = self.find(b)
            if bt is not None: out += a2.fields_of(bt)
        for fr in t.FieldList:
            fd = fr.row; fl = fd.Flags
            if fl.fdStatic or fl.fdLiteral or fl.fdInitOnly or fl.fdNotSerialized: continue
            attrs = self.attr_fields.get(fr.row_index, set())
            if not fl.fdPublic and "SerializeField" not in attrs: continue
            sig = bytes(fd.Signature.value) if hasattr(fd.Signature, "value") else bytes(fd.Signature.__data__)
            ty, _ = self.parse_type(sig, 1)
            if "SerializeReference" in attrs: ty = ("serref",)
            elif not self.serializable(ty): continue
            out.append((str(fd.Name), ty))
        return out

class Layout:
    """Reads a MonoBehaviour's script data by the layout derived from the assembly."""
    def __init__(self, asm, extern_asms=()):
        self.asm = asm; self.ext = list(extern_asms)

    def resolve(self, name):
        return self.asm.find(name)

    def read_value(self, r, ty, depth=0, in_array=False):
        k = ty[0]
        if k == "prim":
            p = ty[1]
            if p == "string": return r.str()
            v = r.prim(p)
            if p in ("bool", "u8", "i8", "i16", "u16", "char") and not in_array: r.align()
            return v
        if k == "array": return self.read_list(r, ty[1], depth)
        if k == "serref": return ("serref", r.prim("i64"))
        if k == "generic":
            if ty[1] == "List`1": return self.read_list(r, ty[2][0], depth)
            return ("<generic %s unsupported>" % ty[1]) if depth > 0 else None
        if k in ("valuetype", "class"):
            name = ty[1]
            if name in UNITY_STRUCTS:
                return [r.prim(p) for p in UNITY_STRUCTS[name]]
            if self.asm.is_unity_event(name, ty[2]): return r.unity_event()
            if name == "AnimationCurve": return r.anim_curve()
            if name == "Gradient": return r.gradient()
            a, t = self.resolve(name)
            if t is not None and (k == "valuetype" and a.base_name(t) == "Enum" or name in a.enums):
                return r.prim("i32")
            if t is None and k == "valuetype":
                return r.prim("i32")  # external enum (UnityEngine) - 4 bytes
            if name in UNITY_OBJECT_BASES or (t is not None and a.is_unity_object(t)) or (t is None and k == "class"):
                return r.pptr()
            if depth > 7: raise ValueError("too deep")
            return {fn: self.read_value(r, ft, depth + 1) for fn, ft in a.fields_of(t)}
        raise ValueError("unsupported " + repr(ty))

    def read_list(self, r, inner, depth):
        n = r.prim("i32")
        if n < 0 or n > 200000: raise ValueError("bad list length %d" % n)
        out = [self.read_value(r, inner, depth + 1, in_array=True) for _ in range(n)]
        if inner[0] == "prim" and inner[1] in ("bool", "u8", "i8"): pass
        r.align(); return out

    def read_mono(self, raw, class_name):
        r = Reader(raw)
        head = {"m_GameObject": r.pptr(), "m_Enabled": r.prim("u8")}; r.align()
        head["m_Script"] = r.pptr(); head["m_Name"] = r.str()
        a, t = self.resolve(class_name)
        body = {fn: self.read_value(r, ft) for fn, ft in a.fields_of(t)}
        body["_head"] = head; body["_consumed"] = (r.i, len(raw)); return body

class Reader:
    def __init__(self, b): self.b = b; self.i = 0
    def align(self): self.i = (self.i + 3) & ~3
    def prim(self, p):
        f = {"bool": "?", "char": "H", "i8": "b", "u8": "B", "i16": "h", "u16": "H", "i32": "i", "u32": "I",
             "i64": "q", "u64": "Q", "f32": "f", "f64": "d"}[p]
        v = struct.unpack_from("<" + f, self.b, self.i)[0]; self.i += struct.calcsize(f); return v
    def str(self):
        n = self.prim("i32"); s = self.b[self.i:self.i + n].decode("utf8", "replace"); self.i += n; self.align(); return s
    def pptr(self): return (self.prim("i32"), self.prim("i64"))
    def anim_curve(self):
        n = self.prim("i32")
        self.i += n * 28  # Keyframe(float): time,value,inSlope,outSlope,weightedMode,inWeight,outWeight
        self.prim("i32"); self.prim("i32"); self.prim("i32"); return "<curve>"
    def unity_event(self):
        n = self.prim("i32"); calls = []
        for _ in range(n):
            target = self.pptr(); method = self.str(); mode = self.prim("i32")
            obj = self.pptr(); objtype = self.str(); iv = self.prim("i32"); fv = self.prim("f32"); sv = self.str()
            bv = self.prim("u8"); self.align(); state = self.prim("i32")
            calls.append((method, mode))
        return ("unityevent", calls)
    def gradient(self):
        self.i += 8 * 16 + 8 * 2 + 8 * 2 + 2 + 1 + 1; self.align(); return "<gradient>"
