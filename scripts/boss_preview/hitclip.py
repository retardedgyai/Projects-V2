"""The 黒翳竜's hit spheres (ShadeWyrmHitbox.kt) drawn over the model (CPU preview, NOT Minecraft).

    python scripts/boss_preview/hitclip.py out_dir

Writes {calm,rage}_{side,front,top}_{new,old}.png: the idle pose before / after the enrage, with the spheres the
server tests hits against (new) or the single box and ray point it used before (old).
"""
import math
import re
import sys
from pathlib import Path

from rbb import *
from vfxclip import sample

ROOT = Path(__file__).resolve().parents[2]
SRC = ROOT / "server-minestom/src/main/kotlin/dev/projects/server/mob/ShadeWyrmHitbox.kt"


def spheres():
    groups, cur = {}, None
    for line in SRC.read_text(encoding="utf-8").splitlines():
        m = re.match(r"\s*val (BODY|WINGS|FANS) = listOf\(", line)
        if m:
            cur = groups.setdefault(m.group(1), [])
        m = re.match(r'\s*S\("(\w+)", ([-\d.]+), ([-\d.]+), ([-\d.]+), ([-\d.]+)\)', line)
        if m and cur is not None:
            cur.append((m.group(1), np.array([float(m.group(k)) for k in (2, 3, 4)]), float(m.group(5))))
    return groups


VIEWS = {"side": (90, 8), "front": (30, 22), "top": (0, 85)}


def main(out):
    out = Path(out); out.mkdir(parents=True, exist_ok=True)
    model = ROOT / "assets/bosses/shade_wyrm.bbmodel"
    sc = Scene(model)
    A = {a["name"]: a for a in json.loads(model.read_text(encoding="utf-8"))["animations"]}
    G = spheres()
    for phase in ("calm", "rage"):
        clip = A["idle" if phase == "calm" else "idle_enraged"]
        pose = sample(clip, 0.0)
        hidden = ("horn_l", "horn_r", "cloak_l", "cloak_r") if phase == "calm" else ("wing_l", "wing_r")
        parts = G["BODY"] + (G["WINGS"] if phase == "calm" else G["FANS"])
        M = sc.mats(pose)
        world = [(M[b][:3, :3] @ p + M[b][:3, 3], r) for b, p, r in parts]
        for vname, (az, el) in VIEWS.items():
            zoom = 2.4 if phase == "calm" else 1.8
            size = (720, 460)
            center = (0, 40, -20 if phase == "calm" else 10)
            panel = render(sc, pose, hidden=hidden, az=az, el=el, size=size, scale=zoom, center=center, cy=.55,
                           bg=((52, 50, 64), (22, 20, 28)), outline=False)
            a_, e_ = math.radians(az), math.radians(el)
            cam = np.array([math.cos(e_) * math.sin(a_), math.sin(e_), math.cos(e_) * math.cos(a_)])
            right = np.cross([0, 1.0, 0], cam); right /= np.linalg.norm(right); up = np.cross(cam, right)
            ox = size[0] * .5 - np.array(center, float) @ right * zoom; oy = size[1] * .55 + np.array(center, float) @ up * zoom
            proj = lambda p: (ox + p @ right * zoom, oy - p @ up * zoom)
            for mode in ("new", "old"):
                img = panel.copy().convert("RGBA")
                layer = Image.new("RGBA", img.size, (0, 0, 0, 0)); d = ImageDraw.Draw(layer)
                if mode == "new":
                    for c, r in sorted(world, key=lambda w: w[0] @ cam):
                        x, y = proj(c); rr = r * 16 * zoom
                        col = (255, 196, 80) if any(np.allclose(c, w[0]) for w in world[:len(G["BODY"])]) else (110, 220, 255)
                        d.ellipse((x - rr, y - rr, x + rr, y + rr), fill=col + (60,), outline=col + (230,), width=2)
                else:
                    # the old box: 1 block ahead, 1.8 up, half 2 x 1.8 x 2; and the point rays aimed at (feet + 1)
                    c = np.array([0, 1.8, 1.0]) * 16; h = np.array([2.0, 1.8, 2.0]) * 16
                    corners = [c + h * np.array(s) for s in [(i, j, k) for i in (-1, 1) for j in (-1, 1) for k in (-1, 1)]]
                    for i, p in enumerate(corners):
                        for j, q in enumerate(corners):
                            if i < j and sum(1 for a, b in zip(p, q) if a != b) == 1:
                                d.line([proj(p), proj(q)], fill=(255, 120, 120, 230), width=2)
                    x, y = proj(np.array([0, 16.0, 0])); rr = 2.65 * 16 * zoom
                    d.ellipse((x - rr, y - rr, x + rr, y + rr), fill=(255, 120, 120, 40), outline=(255, 120, 120, 200), width=2)
                Image.alpha_composite(img, layer).convert("RGB").save(out / f"{phase}_{vname}_{mode}.png")
    print(out)


if __name__ == "__main__":
    main(sys.argv[1])
