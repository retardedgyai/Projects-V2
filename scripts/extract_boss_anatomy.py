# One-off: python scripts/extract_boss_anatomy.py <noctveil.bbmodel> assets/bosses/shade_wyrm_anatomy.json
"""noctveil v86 (mesh, -Z front) -> shade wyrm anatomy JSON (oriented boxes, +Z front, scaled).

The left side (noct x<0, the finished folded-mantle side) is kept; the build mirrors it for the right.
Volumes: long axis from PCA with no roll, sliced along that axis; each slice is a box fitted to the mesh
surface inside it, so tapers and bends survive. Plates (membranes): one thin box plus the panel's outline
as 2D triangles in the box's local (y, z), which the build uses to cut the texture.
"""
import json, math, sys
import numpy as np

SRC, OUT = sys.argv[1], sys.argv[2]
K = 3.2
d = json.load(open(SRC, encoding="utf-8"))
els = {e["uuid"]: e for e in d["elements"]}


def conv(p):  # rotate 180 deg about Y (front -Z -> +Z, left x<0 -> x>0), then scale
    return np.array([-p[0], p[1], -p[2]], float) * K


RENAME = {"thorax": "body", "neck": "neck", "head": "skull", "jaw": "jaw", "left_crown": "crown_l",
          "left_fore_upper": "leg_fl", "left_fore_lower": "leg_fl_fore", "left_fore_foot": "leg_fl_paw",
          "left_hind_upper": "leg_hl", "left_hind_lower": "leg_hl_shin", "left_hind_foot": "leg_hl_foot",
          "tail_0": "tail", "tail_1": "tail_2", "tail_2": "tail_mid", "tail_3": "tail_end",
          "left_wing_shoulder": "wing_l", "left_wing_forearm": "wing_l_fore", "left_wing_hand": "wing_l_hand",
          "left_folded_membrane": "wing_l_membrane"}
bones, parts = [], []


def triangles(e):
    V = {k: conv(np.array(v) + np.array(e.get("origin", [0, 0, 0]))) for k, v in e["vertices"].items()}
    out = []
    for f in e["faces"].values():
        P = [V[i] for i in f["vertices"]]
        if len(P) == 4:   # order the quad around its centroid
            c = sum(P) / 4; n = np.cross(P[1] - P[0], P[2] - P[0])
            if np.linalg.norm(n) < 1e-9:
                n = np.cross(P[2] - P[0], P[3] - P[0])
            u = P[0] - c; u /= np.linalg.norm(u) + 1e-9; w = np.cross(n, u); w /= np.linalg.norm(w) + 1e-9
            P = [P[i] for i in np.argsort([math.atan2((p - c) @ w, (p - c) @ u) for p in P])]
            out += [(P[0], P[1], P[2]), (P[0], P[2], P[3])]
        elif len(P) == 3:
            out.append(tuple(P))
    return out


def surface_points(tris, step=.6):
    pts = []
    for a, b, c in tris:
        n = int(max(np.linalg.norm(b - a), np.linalg.norm(c - a), np.linalg.norm(c - b)) / step) + 2
        for i in range(n + 1):
            for j in range(n + 1 - i):
                pts.append(a + (b - a) * i / n + (c - a) * j / n)
    return np.array(pts)


def no_roll(major):
    a = major / np.linalg.norm(major)
    up = np.array([0, 1.0, 0])
    u = up - (up @ a) * a
    if np.linalg.norm(u) < 1e-3:
        u = np.array([1.0, 0, 0]) - a[0] * a
    u /= np.linalg.norm(u)
    return np.column_stack([np.cross(u, a), u, a])   # local X, Y (no roll), Z (long axis)


def fit(e):
    tris = triangles(e)
    S = surface_points(tris)
    c0 = S.mean(0); X = S - c0
    ev, U = np.linalg.eigh(X.T @ X)
    order = np.argsort(ev)
    span = [np.ptp(X @ U[:, i]) for i in range(3)]
    major = U[:, order[2]]
    if min(span) < .15 * max(span):           # plate
        xv = U[:, order[0]]
        z = major - (major @ xv) * xv; z /= np.linalg.norm(z)
        R = np.column_stack([xv, np.cross(z, xv), z])
        L = X @ R
        lo, hi = L.min(0), L.max(0)
        mid = (lo + hi) / 2
        outline = [[[float(p[1] - mid[1]), float(p[2] - mid[2])] for p in ((np.array(t) - c0) @ R for t in tri)] for tri in tris]
        return [(c0 + R @ mid, np.maximum(.3, (hi - lo) / 2), R, outline)]
    R = no_roll(major)
    L = X @ R
    lo, hi = L.min(0), L.max(0)
    ln = hi[2] - lo[2]
    width = max(np.ptp(L[:, 0]), np.ptp(L[:, 1]), 1e-6)
    n = 1 if ln < 1.3 * width else 2 if ln < 2.6 * width else 3
    out = []
    for k in range(n):
        z0 = lo[2] + ln * k / n; z1 = lo[2] + ln * (k + 1) / n
        sel = L[(L[:, 2] >= z0 - .01) & (L[:, 2] <= z1 + .01)]
        if len(sel) < 4:
            continue
        a, b = sel[:, :2].min(0), sel[:, :2].max(0)
        # A slice box slightly inside the outer hull reads closer to a smooth mesh than its full bound.
        cen = (a + b) / 2; half = (b - a) / 2 * (.92 if n > 1 else 1)
        out.append((c0 + R @ np.array([cen[0], cen[1], (z0 + z1) / 2]),
                    np.maximum(.3, [half[0], half[1], (z1 - z0) / 2 + .15]), R, None))
    return out


def keep(name):
    return name in RENAME or name.startswith("left_mantle_hinge")


def walk(n, parent):
    name = n["name"]
    if name == "root":
        for c in n["children"]:
            if isinstance(c, dict):
                walk(c, None)
        return
    if not keep(name):
        return
    ours = RENAME.get(name) or name.replace("left_mantle_hinge_", "wing_l_hinge")
    bones.append({"name": ours, "parent": parent, "pivot": [round(float(v), 3) for v in conv(n["origin"])]})
    for c in n["children"]:
        if isinstance(c, str) and c in els:
            e = els[c]
            if "right" in e["name"]:
                continue
            sided = "left" in e["name"] or ours.startswith(("leg_", "wing_", "crown_"))
            for k, (center, half, R, outline) in enumerate(fit(e)):
                pt = {"name": e["name"].replace("left_", "").replace("_left", "") + (f"_{k}" if k else ""), "bone": ours,
                      "sided": bool(sided), "center": [round(float(v), 3) for v in center],
                      "half": [round(float(v), 3) for v in half], "rotation": [[round(float(v), 6) for v in row] for row in R]}
                if outline:
                    pt["outline"] = [[[round(v, 3) for v in q] for q in tri] for tri in outline]
                parts.append(pt)
    for c in n["children"]:
        if isinstance(c, dict):
            walk(c, ours)


for n in d["outliner"]:
    if isinstance(n, dict):
        walk(n, None)
json.dump({"source": "noctveil v86 (Creator reference, Blockbench mesh) as oriented boxes; +Z front; scale %.1f" % K,
           "bones": bones, "parts": parts}, open(OUT, "w", encoding="utf-8"), indent=1)
print(len(bones), "bones", len(parts), "parts", sum("outline" in p for p in parts), "plates")
