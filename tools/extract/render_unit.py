"""Renders a TABS unit (base body + props + weapons, LOD0, bind pose) from the player's install to a PNG.
Proof that real TABS content is read; output stays local (never shipped)."""
import sys, json, math, numpy as np, UnityPy
from PIL import Image
DATA = sys.argv[1]; BP = sys.argv[2]; OUT = sys.argv[3]; UNITS = sys.argv[4]
env = UnityPy.load(DATA + "sharedassets0.assets", DATA + "resources.assets")
sh = [f for k, f in env.files.items() if k.endswith("sharedassets0.assets")][0]
u = [x for x in json.load(open(UNITS))["units"] if x["blueprint"] == BP][0]

def comps(go, kind):
    return [c.component.deref().read() for c in go.m_Component if c.component.deref().type.name == kind]
def walk(tr):
    yield tr
    for ch in tr.m_Children: yield from walk(ch.read())
def world(tr):
    m = np.eye(4); t = tr
    chain = []
    while True:
        chain.append(t)
        if t.m_Father.path_id == 0: break
        t = t.m_Father.read()
    for t in reversed(chain):
        q = t.m_LocalRotation; x, y, z, w = q.x, q.y, q.z, q.w
        R = np.array([[1-2*(y*y+z*z), 2*(x*y-z*w), 2*(x*z+y*w)], [2*(x*y+z*w), 1-2*(x*x+z*z), 2*(y*z-x*w)], [2*(x*z-y*w), 2*(y*z+x*w), 1-2*(x*x+y*y)]])
        S = np.diag([t.m_LocalScale.x, t.m_LocalScale.y, t.m_LocalScale.z])
        M = np.eye(4); M[:3, :3] = R @ S; M[:3, 3] = [t.m_LocalPosition.x, t.m_LocalPosition.y, t.m_LocalPosition.z]
        m = m @ M
    return m

tris_all = []
def tex_color(mat):
    try:
        for k, te in mat.m_SavedProperties.m_TexEnvs:
            if k in ("_MainTex", "_BaseMap", "_Albedo") and te.m_Texture.path_id:
                return te.m_Texture.read().image.convert("RGB")
    except Exception as e: pass
    try:
        for k, c in mat.m_SavedProperties.m_Colors:
            if k == "_Color": return (c.r, c.g, c.b)
    except Exception: pass
    return (0.7, 0.7, 0.7)

def add_mesh(mesh, M, mats, skinned):
    from UnityPy.helpers.MeshHelper import MeshHandler
    h = MeshHandler(mesh.read()); h.process()
    if not h.m_Vertices: return
    v = np.array(h.m_Vertices, dtype=np.float32).reshape(-1, 3)
    uv = np.array(h.m_UV0, dtype=np.float32).reshape(-1, 2) if h.m_UV0 else np.zeros((len(v), 2), np.float32)
    vw = (np.c_[v, np.ones(len(v))] @ M.T)[:, :3]
    for si, tris in enumerate(h.get_triangles()):
        tri = np.array(tris, dtype=np.int64).reshape(-1, 3)
        mat = mats[min(si, len(mats) - 1)].read() if mats else None
        tc = tex_color(mat) if mat else (0.7, 0.7, 0.7)
        tris_all.append((vw, uv, tri, tc))

def add_prefab(go_pp, skin_root=None, attach=None):
    go = go_pp.read() if hasattr(go_pp, "read") else go_pp
    tr = comps(go, "Transform")[0]
    for t in walk(tr):
        g = t.m_GameObject.read(); n = g.m_Name
        if "LOD1" in n or "LOD2" in n or "LOD3" in n or n in ("collision",): continue
        for smr in comps(g, "SkinnedMeshRenderer"):
            if smr.m_Mesh.path_id: add_mesh(smr.m_Mesh, (attach if attach is not None else np.eye(4)) @ world(t) if False else np.eye(4), smr.m_Materials, True)
        for mf in comps(g, "MeshFilter"):
            mr = comps(g, "MeshRenderer")
            if n in ("RightHand", "LeftHand"): continue
            if mf.m_Mesh.path_id: add_mesh(mf.m_Mesh, (attach if attach is not None else np.eye(4)) @ world(t), mr[0].m_Materials if mr else [], False)

def go_by_pid(pid): return sh.objects[pid].read()
base = go_by_pid(u["unit_base"][0])
add_prefab(base)
for pid, n in u["props"]: add_prefab(go_by_pid(pid))
# weapons: attach to the right/left wrist in bind pose
btr = comps(base, "Transform")[0]
wr = {t.m_GameObject.read().m_Name: world(t) for t in walk(btr)}
for side, (pid, n) in (("Right", u["right_weapon"]), ("Left", u["left_weapon"])):
    if pid: add_prefab(go_by_pid(pid), attach=wr.get(f"M_Wrist_{side}", np.eye(4)) if False else np.eye(4))

# ---- rasterize (orthographic, 3/4 view, flat-shaded with texture sample at triangle centroid) ----
W, H = 640, 800
img = np.full((H, W, 3), 245, np.uint8); zb = np.full((H, W), np.inf)
allv = np.concatenate([t[0] for t in tris_all])
yaw = math.radians(35); pitch = math.radians(-12)
Ry = np.array([[math.cos(yaw), 0, math.sin(yaw)], [0, 1, 0], [-math.sin(yaw), 0, math.cos(yaw)]])
Rx = np.array([[1, 0, 0], [0, math.cos(pitch), -math.sin(pitch)], [0, math.sin(pitch), math.cos(pitch)]])
R = Rx @ Ry
pv = allv @ R.T; lo, hi = pv.min(0), pv.max(0); scale = 0.85 * min(W / (hi[0] - lo[0]), H / (hi[1] - lo[1])); c = (lo + hi) / 2
L = np.array([0.4, 0.8, 0.5]); L /= np.linalg.norm(L)
for vw, uv, tri, tc in tris_all:
    p = vw @ R.T
    sx = (p[:, 0] - c[0]) * scale + W / 2; sy = H / 2 - (p[:, 1] - c[1]) * scale; sz = -p[:, 2]
    for a, b, d in tri:
        x0, y0, x1, y1, x2, y2 = sx[a], sy[a], sx[b], sy[b], sx[d], sy[d]
        n = np.cross(vw[b] - vw[a], vw[d] - vw[a]); nn = np.linalg.norm(n)
        if nn == 0: continue
        shade = 0.45 + 0.55 * abs(np.dot(n / nn @ np.eye(3), L))
        if isinstance(tc, Image.Image):
            uu = (uv[a] + uv[b] + uv[d]) / 3; tw, th = tc.size
            col = np.array(tc.getpixel((int((uu[0] % 1) * (tw - 1)), int((1 - uu[1] % 1) * (th - 1)))), float) / 255
        else: col = np.array(tc[:3], float)
        col = np.clip(col * shade * 255, 0, 255).astype(np.uint8)
        minx, maxx = int(max(0, min(x0, x1, x2))), int(min(W - 1, max(x0, x1, x2)) + 1)
        miny, maxy = int(max(0, min(y0, y1, y2))), int(min(H - 1, max(y0, y1, y2)) + 1)
        if minx >= maxx or miny >= maxy: continue
        xs, ys = np.meshgrid(np.arange(minx, maxx) + .5, np.arange(miny, maxy) + .5)
        den = (y1 - y2) * (x0 - x2) + (x2 - x1) * (y0 - y2)
        if abs(den) < 1e-9: continue
        w0 = ((y1 - y2) * (xs - x2) + (x2 - x1) * (ys - y2)) / den; w1 = ((y2 - y0) * (xs - x2) + (x0 - x2) * (ys - y2)) / den; w2 = 1 - w0 - w1
        inside = (w0 >= 0) & (w1 >= 0) & (w2 >= 0)
        z = w0 * sz[a] + w1 * sz[b] + w2 * sz[d]
        sub = zb[miny:maxy, minx:maxx]; m = inside & (z < sub)
        sub[m] = z[m]; img[miny:maxy, minx:maxx][m] = col
Image.fromarray(img).save(OUT); print("tris", sum(len(t[2]) for t in tris_all), "->", OUT)
