"""Writes the Unity 2019.4 type trees of the built-in classes the mod reads (GameObject, Transform, Mesh, Material,
renderers, MonoScript) as compact JSON for the Java reader: [type, name, align, [children...]]."""
import sys, json
from UnityPy.helpers.Tpk import get_typetree_node
from UnityPy.helpers.UnityVersion import UnityVersion
ver = UnityVersion.from_str(sys.argv[1]); out = sys.argv[2]
CLASSES = {1: "GameObject", 4: "Transform", 21: "Material", 23: "MeshRenderer", 33: "MeshFilter", 43: "Mesh",
           114: "MonoBehaviour", 115: "MonoScript", 137: "SkinnedMeshRenderer", 54: "Rigidbody", 213: "Sprite", 28: "Texture2D"}
def conv(n):
    return [n.m_Type, n.m_Name, 1 if (n.m_MetaFlag or 0) & 0x4000 else 0, [conv(c) for c in n.m_Children]]
res = {}
for cid, name in CLASSES.items():
    res[str(cid)] = conv(get_typetree_node(cid, ver))
json.dump({"unity": sys.argv[1], "classes": res}, open(out, "w"), separators=(",", ":"))
print("wrote", len(res), "type trees")
