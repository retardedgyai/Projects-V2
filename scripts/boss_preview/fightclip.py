"""GIF of a scripted fight recorded through the real encounter code (CPU preview, NOT Minecraft).

    SHADE_FIGHT_DUMP=/tmp/fight.jsonl ./gradlew :server-minestom:test --tests '*ShadeWyrmBossTest' --rerun
    python scripts/build_boss_vfx.py    # the shader data maps, if the pack was not built
    python scripts/boss_preview/fightclip.py /tmp/fight.jsonl 0 11 out.gif [flat|before|violet|glow] [zoom]

Each frame: the wyrm where the server had it, turned the way the model faced, posed from the clip on show at that
time, the shader displays the server showed (item.fsh's maths, see shaderclip.py), the player as a green post, and
a small map of the arena. Blends between clips are not drawn. The last argument picks the lighting: flat (none),
before (the overworld's warm block light), violet (翳の巣's tint and the server's light blocks), glow (the light as the server's rage clock had it, plus
the shade_glow post effect); see lightfx.py.
"""
import json
import math
import os
import sys
from pathlib import Path

from rbb import *
from vfxclip import sample
import shaderclip as SH
import lightfx as LF

ROOT = Path(__file__).resolve().parents[2]
JP = {"idle": "待機", "walk": "歩き", "strafe": "横移動", "dash": "回り込み", "hop_back": "跳び退き", "search": "探りの歩み",
      "enrage": "怒り", "shade-sweep": "翼脚薙ぎ", "shade-pounce": "飛びかかり", "shade-dust": "鱗粉炸裂", "shade-pulse": "触角の共鳴",
      "shade-breath": "翳の吐息", "shade-storm": "鱗粉嵐", "shade-turnsweep": "振り向き薙ぎ", "shade-dive": "飛び上がり急降下", "death": "死亡",
      "flinch": "怯み", "relocate": "飛び移り", "awaken": "第2形態へ", "shade-bite": "噛みつき3連", "shade-tailspin": "しっぽ回転",
      "shade-horn": "角の突き上げ", "shade-tailslap": "背後への尾打ち", "shade-orb": "鱗粉弾", "shade-channel": "翳の大詠唱", "shade-shell": "鱗粉の殻", "down": "ダウン", "shade-charge": "突進"}


def label(clip):
    base = clip.replace("_enraged", "")
    for k, v in JP.items():
        if base == k or base.startswith(k + "_") or (k in ("strafe", "dash") and base.startswith(k)):
            return v
    return base


def main(dump, t0, t1, out, mode="flat", zoom=1.0, fps=10):
    model = ROOT / "assets/bosses/shade_wyrm.bbmodel"
    sc = Scene(model)
    import base64, io
    atlases = [np.asarray(Image.open(io.BytesIO(base64.b64decode(t["source"].split(",", 1)[1]))).convert("RGBA")).astype(np.float32)
               for t in json.loads(model.read_text(encoding="utf-8"))["textures"]]
    A = {a["name"]: a for a in json.loads(model.read_text(encoding="utf-8"))["animations"]}
    rows = [json.loads(l) for l in Path(dump).read_text().splitlines()]
    tex = {p.stem: np.asarray(Image.open(p).convert("RGBA")).astype(np.float32) for p in SH.TEX.glob("*.png")}
    W, H, zoom = 640, 400, float(zoom)
    az, el = 30, 30
    a_, e_ = math.radians(az), math.radians(el)
    cam = np.array([math.cos(e_) * math.sin(a_), math.sin(e_), math.cos(e_) * math.cos(a_)])
    right = np.cross([0, 1.0, 0], cam); right /= np.linalg.norm(right); up = np.cross(cam, right)
    frames = []
    step = max(1, round(20 / fps))
    for r in rows[::step]:
        if not (t0 <= r["t"] <= t1):
            continue
        clip = r["clip"] if r["clip"] in A else ("idle_enraged" if r["enraged"] else "idle")
        a = A[clip]
        ct = r["clipT"] % a["length"] if a["loop"] == "loop" else min(r["clipT"], a["length"])
        pose = sample(a, ct)
        bx, by, bz = r["boss"]
        carrier = np.array([-bx * 16, by * 16, bz * 16])
        b = pose.get("body", (np.zeros(3), np.zeros(3), np.ones(3)))
        yaw = r["yaw"]
        R = euler(np.array([0, yaw, 0]))
        pose["body"] = (b[0] + np.array([0, yaw, 0]), R @ b[1] + carrier, b[2] if len(b) > 2 else np.ones(3))
        if r["enraged"] and clip != "enrage":
            hidden = ("wing_l", "wing_r")
        elif clip == "enrage":
            hidden = ()
        else:
            hidden = ("horn_l", "horn_r", "cloak_l", "cloak_r")
        horns, fan_right, fan_left = (r.get("broken") or [False, False, False])
        hidden += (("horn_l_tip", "horn_r_tip") if horns else ()) + (("cloak_l_tatters",) if fan_right else ()) + \
            (("cloak_r_tatters",) if fan_left else ())
        hidden = tuple(b for b in sc.bones if any(b == h or b.startswith(h + "_") for h in hidden))   # rays and their tatters
        px, pz = r["player"]
        # on the wyrm's feet (the ground shadow is drawn under the centre); SHADE_FIXED_CAMERA=1 holds the arena still
        center = (0, 0, 0) if os.environ.get("SHADE_FIXED_CAMERA") else (carrier[0], 0, carrier[2])
        sc.atlas = atlases[1 if r.get("glowing") and len(atlases) > 1 else 0]   # the second form's glowing texture
        panel, zb = render(sc, pose, hidden=hidden, az=az, el=el, size=(W, H), scale=zoom, center=center, cy=.72,
                           bg=((52, 50, 64), (22, 20, 28)), outline=False, with_depth=True, flat=None)
        arr = np.asarray(panel).astype(np.float32).copy()
        ox = W * .5 - np.array(center) @ right * zoom; oy = H * .72 + np.array(center) @ up * zoom
        proj = lambda p: (int(ox + p @ right * zoom), int(oy - p @ up * zoom))
        if mode != "flat":
            # glow: the arena's light as the server's rage clock had it (the dump's "rage", 0 calm .. 1 violet)
            look = "before" if mode == "before" else LF.rage_look(r["rage"]) if "rage" in r else "violet"
            LF.light(arr, zb, r.get("lights", []), look, cam, right, up, ox, oy, zoom, np.abs(render.alpha - 251) < .5)
        # floor dots every 2 blocks and the arena wall (radius 22)
        for gx in (range(-22, 23, 2) if mode == "flat" else ()):
            for gz in range(-22, 23, 2):
                if gx * gx + gz * gz > 22 * 22:
                    continue
                x, y = proj(np.array([-gx * 16, 0, gz * 16], float))
                if 1 <= x < W - 1 and 1 <= y < H - 1 and zb[y, x] < -1e8:
                    arr[y - 1:y + 1, x - 1:x + 1] = (70, 66, 84)
        for k in range(120):
            th = k * 2 * math.pi / 120
            x, y = proj(np.array([-math.cos(th) * 22 * 16, 0, math.sin(th) * 22 * 16]))
            if 0 <= x < W and 0 <= y < H and zb[y, x] < -1e8:
                arr[y, x] = (110, 100, 130)
        live = [dict(v) for v in r["shader"]]
        if live:
            SH.draw(arr, zb, live, 100 + r["t"], cam, right, up, ox, oy, zoom, tex)
        if mode == "glow":
            LF.bloom(arr)
        img = Image.fromarray(np.clip(arr, 0, 255).astype(np.uint8))
        d = ImageDraw.Draw(img)
        p0 = proj(np.array([-px * 16, 0, pz * 16])); p1 = proj(np.array([-px * 16, 28, pz * 16]))
        d.line([p0, p1], fill=(110, 220, 160), width=4)
        d.ellipse((p1[0] - 4, p1[1] - 4, p1[0] + 4, p1[1] + 4), fill=(110, 220, 160))
        # map: arena, player (green), wyrm (violet arrow toward its facing)
        m = Image.new("RGB", (130, 130), (30, 28, 38)); md = ImageDraw.Draw(m)
        md.ellipse((5, 5, 125, 125), outline=(110, 100, 130))
        to_m = lambda x, z: (65 - x * 60 / 22, 65 + z * 60 / 22)   # x mirrored like the 3D view
        gx, gz = to_m(px, pz); md.ellipse((gx - 3, gz - 3, gx + 3, gz + 3), fill=(110, 220, 160))
        wx, wz = to_m(bx, bz); f = math.radians(yaw); fx, fz = math.sin(f), math.cos(f)
        md.polygon([(wx + fx * 7, wz + fz * 7), (wx - fx * 5 + fz * 4, wz - fz * 5 - fx * 4), (wx - fx * 5 - fz * 4, wz - fz * 5 + fx * 4)], fill=(180, 140, 240))
        img.paste(m, (W - 138, 8))
        er = r.get("erosion", 0.0)
        if er > 0:                                    # the erosion boss bar (purple, 10 notches)
            bx, by = W // 2 - 100, 40
            d.rectangle((bx - 4, by - 18, bx + 204, by + 10), fill=(16, 12, 22))
            text(img, (bx + 80, by - 18), "侵食", SMALL, (210, 170, 250))
            d.rectangle((bx, by, bx + 200, by + 5), fill=(60, 48, 72)); d.rectangle((bx, by, bx + int(200 * er / 100), by + 5), fill=(196, 120, 240))
        bars = r.get("bars")
        if bars:                                      # the MMO gimmick's two boss bars: time left, interrupt / shell
            gid, left, gauge = bars
            bx, by = W // 2 - 100, 78
            d.rectangle((bx - 4, by - 18, bx + 204, by + 22), fill=(16, 12, 22))
            text(img, (bx, by - 18), "翳の大詠唱（殴って止めろ）" if gid == "shade-channel" else "鱗粉の殻（割れ）", SMALL, (226, 190, 255))
            d.rectangle((bx, by, bx + 200, by + 5), fill=(60, 48, 72)); d.rectangle((bx, by, bx + int(200 * left), by + 5), fill=(196, 120, 240))
            d.rectangle((bx, by + 10, bx + 200, by + 15), fill=(60, 52, 30)); d.rectangle((bx, by + 10, bx + int(200 * gauge), by + 15), fill=(255, 200, 90))
        if r.get("down"):
            text(img, (W // 2 - 90, 70), "ダウン！ 殴り放題", MID, (255, 220, 140))
        broke = [n for n, b in zip(("角が折れた", "右の扇が裂けた", "左の扇が裂けた"), (horns, fan_right, fan_left)) if b]
        hdr = f"{r['t']:5.1f}s  {label(r['clip'])}" + ("（第3段階・怒り）" if r["enraged"] else "（第2形態）" if r.get("glowing") else "") + (f"  [{'・'.join(broke)}]" if broke else "")
        text(img, (10, 8), hdr, FONT, (236, 228, 244))
        frames.append(img.convert("P", palette=Image.ADAPTIVE, colors=128))
    frames[0].save(out, save_all=True, append_images=frames[1:], duration=round(1000 / fps), loop=0, optimize=True)
    print(out, len(frames))


if __name__ == "__main__":
    main(sys.argv[1], float(sys.argv[2]), float(sys.argv[3]), sys.argv[4], *sys.argv[5:7])
