"""Key-frame sheet + GIFs of the ability clips, sampled FROM a bbmodel's keyframes (what the server plays)."""
import sys, json
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parent))
from rbb import *
path = sys.argv[1]; out = Path(sys.argv[2]); out.mkdir(exist_ok=True)
sc = Scene(path)
d = json.load(open(path, encoding="utf-8")); A = {a["name"]: a for a in d["animations"]}
def sample(a, t):
    pose = {}
    for an in a["animators"].values():
        ch = {}
        for k in an["keyframes"]:
            p = k["data_points"][0]; ch.setdefault(k["channel"], []).append((k["time"], np.array([p["x"], p["y"], p["z"]], float)))
        o = {}
        for c, keys in ch.items():
            keys.sort(key=lambda k: k[0]); v = keys[-1][1]
            for (t0, v0), (t1, v1) in zip(keys, keys[1:]):
                if t0 <= t <= t1: v = v0 + (v1 - v0) * ((t - t0) / (t1 - t0) if t1 > t0 else 1); break
            o[c] = v
        r = o.get("rotation", np.zeros(3))
        pose[an["name"]] = (np.array([-r[0], r[1], r[2]]), o.get("position", np.zeros(3)), o.get("scale", np.ones(3)))
    return pose
BG = ((150, 152, 166), (92, 92, 106))   # light backdrop: black fur vanishes on the night arena colours
def hidden(name):
    return ("wing_l", "wing_r") if ("enraged" in name or name in ("shade-breath", "shade-storm")) else ("horn_l", "horn_r", "cloak_l", "cloak_r")
def views(name, t, w=360, h=260):
    a = A[name]; pose = sample(a, t)
    img = Image.new("RGB", (2 * w, h))
    img.paste(render(sc, pose, hidden=hidden(name), az=90, el=4, size=(w, h), scale=1.45, center=(0, 40, -20), cy=.62, outline=True, bg=BG), (0, 0))
    img.paste(render(sc, pose, hidden=hidden(name), az=30, el=14, size=(w, h), scale=1.55, center=(0, 40, -10), cy=.62, outline=True, bg=BG), (w, 0))
    return img
HIT = {"shade-sweep": 1.1, "shade-pounce": 1.4, "shade-dust": 1.7, "shade-pulse": 1.2, "shade-breath": 1.6, "shade-storm": 2.2}
JP = {"shade-sweep": "翼脚薙ぎ", "shade-pounce": "飛びかかり", "shade-dust": "鱗粉炸裂", "shade-pulse": "触角の共鳴",
      "shade-breath": "翳の吐息", "shade-storm": "鱗粉嵐", "death": "死亡", "idle": "待機"}
names = sys.argv[3].split(",")
mode = sys.argv[4] if len(sys.argv) > 4 else "sheet"
for name in names:
    base = name.replace("_enraged", "")
    if mode == "sheet":
        hit = HIT[base]
        keys = [(0.0, "構え"), (hit - .15, "溜め"), (hit + .03, "決め（当たる瞬間）"), (hit + .4, "余韻")]
        row = Image.new("RGB", (720 * 2, 260 * 2 + 30), (24, 24, 30))
        for i, (t, lab) in enumerate(keys):
            img = views(name, t)
            row.paste(img, ((i % 2) * 720, 30 + (i // 2) * 260))
            text(row, ((i % 2) * 720 + 8, 30 + (i // 2) * 260 + 4), f"{t:.2f}s {lab}", MID, (20, 20, 26))
        text(row, (8, 4), f"{JP[base]}{'（怒り）' if 'enraged' in name or base in ('shade-breath','shade-storm') else ''}  {name}  当たる瞬間 {hit:.1f}s / 全体 {A[name]['length']}s", MID)
        row.save(out / f"keys_{name}.png")
    else:
        a = A[name]; n = int(a["length"] * 15); frames = []
        for i in range(n + 1):
            t = a["length"] * i / n
            img = views(name, t, 300, 220)
            hit = HIT.get(base)
            lab = f"{JP[base]} {t:.1f}s" + ("  ← 当たる" if hit and abs(t - hit) < .051 else "")
            text(img, (6, 4), lab, MID, (200, 40, 60) if "当たる" in lab else (20, 20, 26))
            frames.append(img.convert("P", palette=Image.ADAPTIVE, colors=96))
        frames[0].save(out / f"{name}.gif", save_all=True, append_images=frames[1:], duration=round(a["length"] * 1000 / n), loop=0, optimize=True)
    print(name, flush=True)
