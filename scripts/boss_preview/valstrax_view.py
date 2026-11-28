"""Offscreen views of the Valstrax study (CPU, NOT Minecraft / Blockbench).

    python scripts/boss_preview/valstrax_view.py OUT.png [clip time] [az el scale cx cy W H]
"""
import sys, json
from pathlib import Path
from rbb import *

PATH = Path(__file__).resolve().parents[2] / "assets/model-lab/valstrax/valstrax.bbmodel"
BG = ((31, 39, 51), (31, 39, 51))


def lit(tex):
    return tex[..., 3] == 251, tex[..., :3]


def sample(a, t):
    pose = {}
    for an in a["animators"].values():
        ch = {}
        for k in an["keyframes"]:
            p = k["data_points"][0]; ch.setdefault(k["channel"], []).append((k["time"], np.array([p["x"], p["y"], p["z"]], float)))
        out = {}
        for c, keys in ch.items():
            keys.sort(key=lambda k: k[0]); v = keys[-1][1]
            for (t0, v0), (t1, v1) in zip(keys, keys[1:]):
                if t0 <= t <= t1: v = v0 + (v1 - v0) * ((t - t0) / (t1 - t0) if t1 > t0 else 1); break
            out[c] = v
        r = out.get("rotation", np.zeros(3)); r = np.array([-r[0], r[1], r[2]])
        pose[an["name"]] = (r, out.get("position", np.zeros(3)), np.ones(3))
    return pose


def view(sc, pose=None, az=62, el=12, scale=5.2, size=(1920, 1080), center=(0, 42, -22), cx=.5, cy=.5, dist=520):
    """Like rbb.render, with a perspective camera `dist` px from `center` and no ground shadow."""
    mats = sc.mats(pose)
    a, e = math.radians(az), math.radians(el)
    cam = np.array([math.cos(e) * math.sin(a), math.sin(e), math.cos(e) * math.cos(a)])
    right = np.cross([0, 1.0, 0], cam); right /= np.linalg.norm(right); up = np.cross(cam, right)
    W, H = size
    ctr = np.array(center, float)
    col = np.ones((H, W, 3), np.float32) * np.array(BG[0], np.float32)
    zb = np.full((H, W), -1e9, np.float32)
    light = np.array([.35, .85, .4]); light = light[0] * right + light[1] * up + light[2] * cam; light /= np.linalg.norm(light)

    def P(p):
        q = p - ctr
        d = q @ cam
        k = scale * dist / max(40.0, dist - d)
        return np.array([W * cx + q @ right * k, H * cy - q @ up * k]), d
    for name in sc.order:
        M = mats[name]
        for cube in sc.bones[name]["cubes"]:
            lo, hi = np.array(cube["from"], float), np.array(cube["to"], float)
            CR = euler(np.array(cube.get("rotation", [0, 0, 0]), float)); co = np.array(cube.get("origin", lo), float)
            glow = "glow" in cube["name"]
            for face, corners in FACES.items():
                pts = [M[:3, :3] @ (CR @ (np.where(np.array(c) == 1, hi, lo) - co) + co) + M[:3, 3] for c in corners]
                n = -np.cross(pts[1] - pts[0], pts[2] - pts[0]); nn = np.linalg.norm(n)
                if nn < 1e-9:
                    continue
                n /= nn
                view_dir = (ctr + cam * dist) - pts[0]
                if n @ view_dir <= 0:
                    continue
                (p0, d0), (p1, d1), (p2, d2) = P(pts[0]), P(pts[1]), P(pts[2])
                E1, E2 = p1 - p0, p2 - p0
                det = E1[0] * E2[1] - E1[1] * E2[0]
                if abs(det) < 1e-6:
                    continue
                q = [p0, p1, p2, p1 + E2]
                x0, x1 = max(0, int(min(t[0] for t in q))), min(W, int(max(t[0] for t in q)) + 2)
                y0, y1 = max(0, int(min(t[1] for t in q))), min(H, int(max(t[1] for t in q)) + 2)
                if x0 >= x1 or y0 >= y1:
                    continue
                X, Y = np.meshgrid(np.arange(x0, x1) + .5, np.arange(y0, y1) + .5)
                dx, dy = X - p0[0], Y - p0[1]
                u = (dx * E2[1] - dy * E2[0]) / det; v = (E1[0] * dy - E1[1] * dx) / det
                inside = (u >= 0) & (u <= 1) & (v >= 0) & (v <= 1)
                if not inside.any():
                    continue
                depth = d0 + u * (d1 - d0) + v * (d2 - d0)
                uv = cube["faces"][face]["uv"]
                tu = np.clip(np.floor(uv[0] + u * (uv[2] - uv[0])).astype(int), min(uv[0], uv[2]), max(uv[0], uv[2]) - 1)
                tv = np.clip(np.floor(uv[1] + v * (uv[3] - uv[1])).astype(int), min(uv[1], uv[3]), max(uv[1], uv[3]) - 1)
                tex = sc.atlas[tv, tu]
                z = zb[y0:y1, x0:x1]
                ok = inside & (depth > z) & (tex[..., 3] > 8)
                z[ok] = depth[ok]
                k = .46 + .52 * max(0.0, n @ light) + .08 * max(0.0, n[1])
                c = tex[..., :3] * (1.0 if glow else k)
                em = tex[..., 3] == 251
                c[em] = tex[..., :3][em]
                col[y0:y1, x0:x1][ok] = c[ok]
    return Image.fromarray(np.clip(col, 0, 255).astype(np.uint8))


if __name__ == "__main__":
    sc = Scene(PATH)
    out = sys.argv[1]
    pose = None
    if len(sys.argv) > 3 and sys.argv[2] != "-":
        pose = sample(sc.anims[sys.argv[2]], float(sys.argv[3]))
    kw = {}
    if len(sys.argv) > 4:
        v = [float(x) for x in sys.argv[4:]]
        kw = dict(az=v[0], el=v[1], scale=v[2])
        if len(v) > 7: kw.update(center=(0, v[7], v[8]))
        if len(v) > 4: kw.update(cx=v[3], cy=v[4])
        if len(v) > 6: kw.update(size=(int(v[5]), int(v[6])))
    view(sc, pose, **kw).save(out)
