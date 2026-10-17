"""CPU render of a Bedrock bbmodel incl. rest bone rotations (Blockbench editor convention). NOT Minecraft."""
import base64, io, json, math
from pathlib import Path
import numpy as np
from PIL import Image, ImageDraw, ImageFont

try:
    FONT = ImageFont.truetype("C:/Windows/Fonts/meiryo.ttc", 24)
    MID = ImageFont.truetype("C:/Windows/Fonts/meiryo.ttc", 16)
    SMALL = ImageFont.truetype("C:/Windows/Fonts/meiryo.ttc", 13)
except OSError:
    FONT = MID = SMALL = ImageFont.load_default()


def rx(a):
    c, s = math.cos(a), math.sin(a); return np.array([[1, 0, 0], [0, c, -s], [0, s, c]])
def ry(a):
    c, s = math.cos(a), math.sin(a); return np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]])
def rz(a):
    c, s = math.cos(a), math.sin(a); return np.array([[c, -s, 0], [s, c, 0], [0, 0, 1]])
def euler(v):
    r = np.radians(v); return rz(r[2]) @ ry(r[1]) @ rx(r[0])


class Scene:
    def __init__(self, path):
        data = json.loads(Path(path).read_text(encoding="utf-8"))
        self.atlas = np.asarray(Image.open(io.BytesIO(base64.b64decode(data["textures"][0]["source"].split(",", 1)[1]))).convert("RGBA")).astype(np.float32)
        els = {e["uuid"]: e for e in data["elements"]}
        self.bones, self.order = {}, []

        def walk(n, parent):
            self.bones[n["name"]] = dict(pivot=np.array(n["origin"], float), rot=np.array(n.get("rotation", [0, 0, 0]), float),
                                         parent=parent, cubes=[els[c] for c in n["children"] if isinstance(c, str)])
            self.order.append(n["name"])
            for c in n["children"]:
                if not isinstance(c, str):
                    walk(c, n["name"])
        for n in data["outliner"]:
            walk(n, None)
        self.anims = {a["name"]: a for a in data["animations"]}

    def mats(self, pose=None):
        """pose: {bone: (extra_raw_euler, position, scale)} added on top of the rest rotation."""
        pose = pose or {}
        out = {}
        for name in self.order:
            b = self.bones[name]
            extra, pos, sc = pose.get(name, (np.zeros(3), np.zeros(3), np.ones(3)))
            R = euler(b["rot"] + extra) @ np.diag(sc)
            L = np.eye(4); L[:3, :3] = R; L[:3, 3] = b["pivot"] + pos - R @ b["pivot"]
            out[name] = out[b["parent"]] @ L if b["parent"] else L
        return out

    def point(self, bone, p, pose=None):
        M = self.mats(pose)[bone]; return M[:3, :3] @ np.array(p, float) + M[:3, 3]


FACES = {
    "north": ((1, 1, 0), (0, 1, 0), (1, 0, 0)), "south": ((0, 1, 1), (1, 1, 1), (0, 0, 1)),
    "east": ((1, 1, 1), (1, 1, 0), (1, 0, 1)), "west": ((0, 1, 0), (0, 1, 1), (0, 0, 0)),
    "up": ((0, 1, 0), (1, 1, 0), (0, 1, 1)), "down": ((0, 0, 1), (1, 0, 1), (0, 0, 0)),
}


def render(sc, pose=None, hidden=(), az=36, el=16, size=(900, 640), scale=3.4, cx=.5, cy=.84, flat=None,
           bg=((118, 122, 140), (64, 64, 78)), outline=True, center=None, unshaded=False):
    mats = sc.mats(pose)
    a, e = math.radians(az), math.radians(el)
    cam = np.array([math.cos(e) * math.sin(a), math.sin(e), math.cos(e) * math.cos(a)])
    right = np.cross([0, 1.0, 0], cam); right /= np.linalg.norm(right); up = np.cross(cam, right)
    W, H = size
    ox, oy = W * cx, H * cy
    if center is not None:
        c = np.array(center, float); ox -= c @ right * scale; oy += c @ up * scale
    g = np.linspace(0, 1, H)[:, None, None]
    col = np.ones((H, W, 3), np.float32) * (np.array(bg[0]) * (1 - g) + np.array(bg[1]) * g)
    if flat is None:
        yy, xx = np.mgrid[0:H, 0:W]
        gp = np.array([W * cx, H * cy]) if center is None else np.array([ox, oy])
        col[((xx - gp[0]) / (58 * scale)) ** 2 + ((yy - gp[1]) / (15 * scale)) ** 2 < 1] *= .6
    zb = np.full((H, W), -1e9, np.float32)
    light = np.array([.4, .85, .35]); light /= np.linalg.norm(light)
    hide = set(hidden)

    def P(p):
        return np.array([ox + p @ right * scale, oy - p @ up * scale]), p @ cam
    for name in sc.order:
        anc, hid = name, False
        while anc is not None:
            if anc in hide:
                hid = True
                break
            anc = sc.bones[anc]["parent"]
        if hid:
            continue
        M = mats[name]
        for cube in sc.bones[name]["cubes"]:
            lo, hi = np.array(cube["from"], float), np.array(cube["to"], float)
            CR = euler(np.array(cube.get("rotation", [0, 0, 0]), float)); co = np.array(cube.get("origin", lo), float)
            for face, corners in FACES.items():
                pts = [M[:3, :3] @ (CR @ (np.where(np.array(c) == 1, hi, lo) - co) + co) + M[:3, 3] for c in corners]
                n = -np.cross(pts[1] - pts[0], pts[2] - pts[0]); nn = np.linalg.norm(n)
                if nn < 1e-9 or n @ cam <= 0:
                    continue
                n /= nn
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
                if flat is not None:
                    col[y0:y1, x0:x1][ok] = flat
                else:
                    # Minecraft-like directional face shading, mild
                    shade = {"up": 1.0, "down": .55, "north": .78, "south": .9, "east": .84, "west": .72}[face]
                    shade = .55 * shade + .45 * (.55 + .45 * max(0, n @ light))
                    k = 1.0 if ("glow" in cube["name"] or unshaded) else min(1.15, shade * 1.15)
                    col[y0:y1, x0:x1][ok] = tex[..., :3][ok] * k
    img = np.clip(col, 0, 255).astype(np.uint8)
    if outline and flat is None:
        m = zb > -1e8
        edge = m & ~(np.roll(m, 1, 0) & np.roll(m, -1, 0) & np.roll(m, 1, 1) & np.roll(m, -1, 1))
        img[edge] = (12, 10, 16)
    return Image.fromarray(img)


def text(img, xy, s, font=MID, fill=(236, 228, 244)):
    ImageDraw.Draw(img).text(xy, s, font=font, fill=fill)
