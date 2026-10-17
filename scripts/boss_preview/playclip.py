"""Render a clip FROM the bbmodel keyframes (raw -> editor: x negated) to check what was written."""
import sys, json
from pathlib import Path
from rbb import *
path = str(Path(__file__).resolve().parents[2] / "assets/bosses/shade_wyrm.bbmodel")
sc = Scene(path)
d = json.load(open(path, encoding="utf-8")); A = {a["name"]: a for a in d["animations"]}
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
name = sys.argv[1]; a = A[name]; N = int(sys.argv[2]) if len(sys.argv) > 2 else 24
hidden = () if "enrage" in name else ("horn_l", "horn_r")
bg = ((60, 62, 76), (28, 28, 36)); frames = []
for i in range(N):
    pose = sample(a, a["length"] * i / N)
    img = Image.new("RGB", (900, 330), (28, 28, 34))
    img.paste(render(sc, pose, hidden=hidden, az=90, el=3, size=(450, 320), scale=1.9, center=(0, 40, -26), cy=.6, outline=False, bg=bg), (0, 10))
    img.paste(render(sc, pose, hidden=hidden, az=35, el=12, size=(450, 320), scale=2.1, center=(0, 40, -10), cy=.6, outline=False, bg=bg), (450, 10))
    text(img, (10, 4), sys.argv[3] if len(sys.argv) > 3 else name, MID)
    frames.append(img.convert("P", palette=Image.ADAPTIVE, colors=128))
frames[0].save(f"clip_{name}.gif", save_all=True, append_images=frames[1:], duration=int(a["length"] * 1000 / N), loop=0, optimize=True)
print(name)
