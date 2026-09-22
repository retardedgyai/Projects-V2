#!/usr/bin/env python3
"""10階ボス「貪食の母樹ラディクス」と、その戦闘で使うプロップの .bbmodel を生成する。

生成物（第2引数のディレクトリへ）:
  radix.bbmodel        — ボス本体（アニメ付き。idle / idle_bloom / attack / cast /
                          devour / bite / bloom / choke / death）
  root_spike.bbmodel   — 地中から突き立つ根の槍（根槍の着弾マーカー）
  rot_fruit.bbmodel    — 苗床の死骸に残る腐果（捕食に吸わせると母樹が噎せる）
  root_circle.bbmodel  — 根槍・捕食の予兆円（沼色の環＋赤の危険環）
  spore_pool.bbmodel   — 居座る毒胞子の霧（踏むと削られる領域）

使い方:
  python3 bbmodel/gen_radix.py bbmodel/osirion.bbmodel bbmodel

第1引数は meta / format / textures の構造を借りる既存 .bbmodel（雛形）。
ジオメトリ・テクスチャ・アニメは全面差し替えする（gen_sarcophagus.py と同じ流儀）。

寸法の目安: 描画サイズ[ブロック] = モデル単位 × scale / 14.4
（fan=±9単位を scale 8 で直径10ブロック、osirion=14.35単位を scale 3.2 で3.2ブロック、
  から割り出した実測値）。本体は 22 単位 ≒ scale 4.0 で 6.1 ブロックの巨躯になる。
"""
import base64
import json
import math
import struct
import sys
import uuid as uuidlib
import zlib

SRC = sys.argv[1]
OUT_DIR = sys.argv[2].rstrip("/")

W = H = 64

# ─── 配色（沼と腐敗。フラット塗り＋ハイライト1段）──────────────────────────
BARK    = (78, 60, 44, 255)     # 樹皮
BARK_D  = (52, 40, 30, 255)     # 樹皮の影
BARK_L  = (114, 92, 64, 255)    # 樹皮のハイライト（大顎の縁）
MOSS    = (94, 128, 54, 255)    # 苔・蔓
MOSS_D  = (62, 90, 38, 255)     # 苔の影
ROT     = (116, 78, 126, 255)   # 腐敗の斑
GULLET  = (128, 44, 60, 255)    # 喉の肉
GULLET_D= (78, 26, 40, 255)     # 喉の奥
FANG    = (228, 224, 200, 255)  # 牙
CORE    = (196, 248, 104, 255)  # 花芯の燐光
MIRE    = (156, 152, 84, 255)   # 胞子の黄
PETAL   = (198, 78, 132, 255)   # 花弁
PETAL_D = (142, 48, 96, 255)    # 花弁の脈


def new_img():
    return [[(0, 0, 0, 0) for _ in range(W)] for _ in range(H)]


def rect(img, x1, y1, x2, y2, c):
    for y in range(y1, y2):
        for x in range(x1, x2):
            if 0 <= x < W and 0 <= y < H:
                img[y][x] = c


def encode_png(img):
    raw = bytearray()
    for y in range(H):
        raw.append(0)
        for x in range(W):
            r, g, b, a = img[y][x]
            raw += bytes((r, g, b, a))
    comp = zlib.compress(bytes(raw), 9)

    def chunk(typ, data):
        c = typ + data
        return struct.pack(">I", len(data)) + c + struct.pack(">I", zlib.crc32(c) & 0xffffffff)

    sig = b"\x89PNG\r\n\x1a\n"
    ihdr = struct.pack(">IIBBBBB", W, H, 8, 6, 0, 0, 0)
    return sig + chunk(b"IHDR", ihdr) + chunk(b"IDAT", comp) + chunk(b"IEND", b"")


def data_uri(img):
    return "data:image/png;base64," + base64.b64encode(encode_png(img)).decode()


def uid():
    return str(uuidlib.uuid4())


def template():
    """雛形から meta / resolution / textures 構造だけを取り出した器。"""
    d = json.load(open(SRC))
    d["elements"] = []
    d["outliner"] = []
    d["animations"] = []
    d["textures"] = d["textures"][:1]
    return d


def write(d, name, img):
    d["name"] = name
    d["geometry_name"] = name
    d["textures"][0]["source"] = data_uri(img)
    d["textures"][0]["name"] = name + ".png"
    if "relative_path" in d["textures"][0]:
        d["textures"][0]["relative_path"] = name + ".png"
    path = f"{OUT_DIR}/{name}.bbmodel"
    json.dump(d, open(path, "w"))
    print(f"wrote {path}  elements={len(d['elements'])} anims={[a['name'] for a in d['animations']]}")


# ══════════════════════════════════════════════════════════════════════════
#  母樹ラディクス本体
# ══════════════════════════════════════════════════════════════════════════

def build_radix():
    img = new_img()
    patches = {}

    def patch(name, gx, gy, color):
        x, y = gx * 8, gy * 8
        rect(img, x, y, x + 8, y + 8, color)
        patches[name] = [x, y, x + 8, y + 8]

    patch("bark",    0, 0, BARK)
    patch("barkd",   1, 0, BARK_D)
    patch("barkl",   2, 0, BARK_L)
    patch("moss",    3, 0, MOSS)
    patch("mossd",   4, 0, MOSS_D)
    patch("rot",     5, 0, ROT)
    patch("gullet",  6, 0, GULLET)
    patch("gulletd", 7, 0, GULLET_D)
    patch("fang",    0, 1, FANG)
    patch("core",    1, 1, CORE)
    patch("mire",    2, 1, MIRE)
    patch("petal",   3, 1, PETAL)
    patch("petald",  4, 1, PETAL_D)

    elements = []

    def faces(p):
        uv = patches[p]
        return {f: {"uv": list(uv), "texture": 0}
                for f in ("north", "east", "south", "west", "up", "down")}

    def cube(bucket, name, frm, to, p):
        u = uid()
        elements.append({"name": name, "rescale": False, "locked": False,
                         "from": frm, "to": to, "autouv": 0, "color": 0,
                         "origin": [0, 0, 0], "faces": faces(p), "uuid": u})
        bucket.append(u)
        return u

    base, trunk, maw_lower, maw_upper, core = [], [], [], [], []
    vine_l, vine_r = [], []
    petal_n, petal_s, petal_e, petal_w = [], [], [], []

    # ── 根の台座 ──────────────────────────────────────────────────────
    cube(base, "mound_bottom", [-5.4, 0.0, -5.4], [5.4, 1.0, 5.4], "barkd")
    cube(base, "mound_mid",    [-4.4, 1.0, -4.4], [4.4, 2.1, 4.4], "bark")
    cube(base, "mound_moss",   [-4.7, 1.9, -4.7], [4.7, 2.2, 4.7], "mossd")
    cube(base, "mound_top",    [-3.4, 2.1, -3.4], [3.4, 3.1, 3.4], "bark")
    # 四方へ這う太根（外へ向かって細く低くなる2段）
    for nm, sx, sz in (("n", 0, -1), ("s", 0, 1), ("w", -1, 0), ("e", 1, 0)):
        for i, (near, far, half, top) in enumerate(((4.6, 6.8, 1.5, 1.4), (6.6, 8.6, 1.0, 0.9))):
            if sz != 0:
                frm = [-half, 0.0, near * sz if sz > 0 else -far]
                to = [half, top, far if sz > 0 else -near * 1.0]
                frm[2], to[2] = (near, far) if sz > 0 else (-far, -near)
            else:
                frm = [(near if sx > 0 else -far), 0.0, -half]
                to = [(far if sx > 0 else -near), top, half]
            cube(base, f"root_{nm}{i}", frm, to, "barkd" if i else "bark")
    # 斜め方向の根は 45° 回転を使わず階段状の小箱で寄せる（Java モデルの回転制限を避ける）
    for nm, sx, sz in (("nw", -1, -1), ("ne", 1, -1), ("sw", -1, 1), ("se", 1, 1)):
        for i, (d0, d1, top) in enumerate(((3.6, 5.2, 1.3), (5.0, 6.4, 1.0), (6.2, 7.4, 0.7))):
            x0, x1 = (d0, d1) if sx > 0 else (-d1, -d0)
            z0, z1 = (d0, d1) if sz > 0 else (-d1, -d0)
            cube(base, f"root_{nm}{i}", [x0, 0.0, z0], [x1, top, z1], "bark" if i % 2 == 0 else "barkd")

    # ── 幹 ────────────────────────────────────────────────────────────
    cube(trunk, "trunk_low",  [-2.6, 3.0, -2.6], [2.6, 7.6, 2.6], "bark")
    cube(trunk, "trunk_mid",  [-2.2, 7.6, -2.2], [2.2, 11.2, 2.2], "bark")
    cube(trunk, "trunk_neck", [-2.8, 11.2, -2.8], [2.8, 13.0, 2.8], "barkl")
    # 苔と腐敗——「湿って腐りかけの巨木」を色だけで語る
    cube(trunk, "trunk_moss_w", [-2.72, 3.6, -1.8], [-2.56, 7.0, 1.2], "moss")
    cube(trunk, "trunk_moss_s", [-1.2, 8.0, 2.14], [1.6, 10.6, 2.3], "mossd")
    cube(trunk, "trunk_rot",    [-1.0, 5.4, -2.74], [0.9, 6.9, -2.56], "rot")
    cube(trunk, "trunk_rot2",   [1.4, 9.0, -2.3], [2.3, 10.2, -2.14], "rot")

    # ── 大顎・下（壺状の口）────────────────────────────────────────────
    cube(maw_lower, "bowl_n", [-3.9, 13.0, -3.9], [3.9, 16.2, -3.0], "barkl")
    cube(maw_lower, "bowl_s", [-3.9, 13.0, 3.0], [3.9, 16.2, 3.9], "barkl")
    cube(maw_lower, "bowl_w", [-3.9, 13.0, -3.0], [-3.0, 16.2, 3.0], "barkl")
    cube(maw_lower, "bowl_e", [3.0, 13.0, -3.0], [3.9, 16.2, 3.0], "barkl")
    cube(maw_lower, "gullet_floor", [-3.0, 13.0, -3.0], [3.0, 13.9, 3.0], "gulletd")
    cube(maw_lower, "gullet_n", [-3.0, 13.9, -3.0], [3.0, 16.2, -2.82], "gullet")
    cube(maw_lower, "gullet_s", [-3.0, 13.9, 2.82], [3.0, 16.2, 3.0], "gullet")
    cube(maw_lower, "gullet_w", [-3.0, 13.9, -2.82], [-2.82, 16.2, 2.82], "gullet")
    cube(maw_lower, "gullet_e", [2.82, 13.9, -2.82], [3.0, 16.2, 2.82], "gullet")
    # 縁の苔
    for nm, frm, to in (
        ("n", [-4.0, 16.1, -4.0], [4.0, 16.35, -2.95]),
        ("s", [-4.0, 16.1, 2.95], [4.0, 16.35, 4.0]),
        ("w", [-4.0, 16.1, -2.95], [-2.95, 16.35, 2.95]),
        ("e", [2.95, 16.1, -2.95], [4.0, 16.35, 2.95]),
    ):
        cube(maw_lower, f"lip_{nm}", frm, to, "moss")
    # 下の牙（上向き）
    for i, (fx, fz) in enumerate(((-2.4, -3.5), (0.0, -3.5), (2.4, -3.5),
                                  (-2.4, 3.1), (0.0, 3.1), (2.4, 3.1),
                                  (-3.5, -1.2), (-3.5, 1.0), (3.1, -1.2), (3.1, 1.0))):
        cube(maw_lower, f"fang_low{i}", [fx - 0.35, 16.3, fz - 0.35], [fx + 0.35, 17.4, fz + 0.35], "fang")

    # ── 大顎・上（蓋）───────────────────────────────────────────────────
    cube(maw_upper, "hood",     [-4.2, 17.6, -4.2], [4.2, 19.0, 4.2], "bark")
    cube(maw_upper, "hood_top", [-3.2, 19.0, -3.2], [3.2, 20.1, 3.2], "barkd")
    cube(maw_upper, "hood_trim", [-4.35, 18.7, -4.35], [4.35, 19.05, 4.35], "mossd")
    cube(maw_upper, "hood_rot", [-1.6, 20.05, -1.2], [0.8, 20.3, 1.4], "rot")
    # 上の牙（下向き。閉じたとき下の牙と噛み合う位置に置く）
    for i, (fx, fz) in enumerate(((-1.2, -3.5), (1.2, -3.5), (-1.2, 3.1), (1.2, 3.1),
                                  (-3.5, -2.4), (-3.5, 2.2), (3.1, -2.4), (3.1, 2.2))):
        cube(maw_upper, f"fang_up{i}", [fx - 0.35, 16.2, fz - 0.35], [fx + 0.35, 17.6, fz + 0.35], "fang")

    # ── 花芯（喉の奥で燐光る球。捕食の予兆でここが光る）───────────────────
    cube(core, "core_stem", [-0.55, 13.2, -0.55], [0.55, 14.0, 0.55], "mossd")
    cube(core, "core_bulb", [-1.5, 14.0, -1.5], [1.5, 15.8, 1.5], "core")
    cube(core, "core_tip",  [-0.8, 15.8, -0.8], [0.8, 16.5, 0.8], "mire")

    # ── 蔓の腕（左右対称。薙ぎ払いを担う）───────────────────────────────
    for bucket, s in ((vine_l, -1), (vine_r, 1)):
        tag = "l" if s < 0 else "r"

        def seg(name, x0, x1, y0, y1, hz, p):
            a, b = (x0 * s, x1 * s) if s > 0 else (x1 * s, x0 * s)
            cube(bucket, name, [a, y0, -hz], [b, y1, hz], p)

        seg(f"vine_{tag}0", 2.0, 5.6, 9.5, 10.7, 0.75, "mossd")
        seg(f"vine_{tag}1", 5.4, 8.4, 8.5, 9.8, 0.65, "mossd")
        seg(f"vine_{tag}2", 8.2, 10.8, 7.1, 8.7, 0.55, "moss")
        seg(f"vine_{tag}3", 10.6, 12.0, 7.0, 8.0, 0.4, "fang")

    # ── 花弁（閉じた蕾として立てておき、開花で外へ開く）──────────────────
    cube(petal_n, "petal_n", [-1.4, 16.2, -5.0], [1.4, 22.4, -4.35], "petal")
    cube(petal_n, "petal_n_vein", [-0.3, 16.4, -5.12], [0.3, 21.6, -4.95], "petald")
    cube(petal_s, "petal_s", [-1.4, 16.2, 4.35], [1.4, 22.4, 5.0], "petal")
    cube(petal_s, "petal_s_vein", [-0.3, 16.4, 4.95], [0.3, 21.6, 5.12], "petald")
    cube(petal_e, "petal_e", [4.35, 16.2, -1.4], [5.0, 22.4, 1.4], "petal")
    cube(petal_e, "petal_e_vein", [4.95, 16.4, -0.3], [5.12, 21.6, 0.3], "petald")
    cube(petal_w, "petal_w", [-5.0, 16.2, -1.4], [-4.35, 22.4, 1.4], "petal")
    cube(petal_w, "petal_w_vein", [-5.12, 16.4, -0.3], [-4.95, 21.6, 0.3], "petald")

    # ── ボーン階層 ────────────────────────────────────────────────────
    bone_uuid = {}

    def bone(name, origin, children):
        u = uid()
        bone_uuid[name] = u
        return {"name": name, "origin": origin, "uuid": u, "export": True,
                "isOpen": True, "visibility": True, "children": children}

    trunk_bone = bone("trunk", [0, 3.0, 0], trunk + [
        bone("maw_lower", [0, 13.0, 0], maw_lower),
        # 蓋の蝶番は後ろ（+Z）。開くと顔の正面（-Z）が大きく開く
        bone("maw_upper", [0, 17.5, 3.4], maw_upper),
        bone("core", [0, 13.5, 0], core),
        bone("vine_l", [-2.2, 10.1, 0], vine_l),
        bone("vine_r", [2.2, 10.1, 0], vine_r),
        bone("petal_n", [0, 16.2, -4.6], petal_n),
        bone("petal_s", [0, 16.2, 4.6], petal_s),
        bone("petal_e", [4.6, 16.2, 0], petal_e),
        bone("petal_w", [-4.6, 16.2, 0], petal_w),
    ])
    root = bone("model", [0, 0, 0], [bone("base", [0, 0, 0], base), trunk_bone])

    # ── アニメーション ─────────────────────────────────────────────────
    # 回転の符号は osirion（実機で検証済み）の流儀に合わせる: +X = 前（-Z 側）へ傾ぐ、
    # +X 側のパーツは +Z 回転で外へ開く。
    def kf(t, x, y, z, channel="rotation"):
        return {"channel": channel, "data_points": [{"x": x, "y": y, "z": z}],
                "uuid": uid(), "time": t, "color": -1, "interpolation": "linear"}

    def anim(name, loop, length, animators):
        return {"uuid": uid(), "name": name, "loop": loop, "override": False,
                "length": length, "snapping": 20, "selected": False,
                "anim_time_update": "", "blend_weight": "", "start_delay": "", "loop_delay": "",
                "animators": {bone_uuid[bn]: {"name": bn, "keyframes": ks}
                              for bn, ks in animators.items()}}

    # 開花後に保持する花弁の角度（idle_bloom / bloom の終端で共有する）
    OPEN = 74
    petals_open = {
        "petal_n": [kf(0, OPEN, 0, 0)],
        "petal_s": [kf(0, -OPEN, 0, 0)],
        "petal_e": [kf(0, 0, 0, OPEN)],
        "petal_w": [kf(0, 0, 0, -OPEN)],
    }

    animations = [
        # idle: 沼の底で重く呼吸する。蔓が垂れて揺れ、蓋がわずかに開閉する
        anim("idle", "loop", 5, {
            "trunk":     [kf(0, 0, 0, 0), kf(1.6, 2, 3, 1.5), kf(3.2, -1.5, -3, -1.5), kf(5, 0, 0, 0)],
            "maw_upper": [kf(0, 0, 0, 0), kf(2.5, -7, 0, 0), kf(5, 0, 0, 0)],
            "vine_l":    [kf(0, 0, 0, -6), kf(1.6, 6, -10, -12), kf(3.4, -4, 6, -3), kf(5, 0, 0, -6)],
            "vine_r":    [kf(0, 0, 0, 6), kf(1.8, 6, 10, 12), kf(3.6, -4, -6, 3), kf(5, 0, 0, 6)],
            "core":      [kf(0, 0, 0, 0), kf(2.5, 0, 40, 0), kf(5, 0, 0, 0)],
        }),
        # idle_bloom: 開花後の待機。花弁を開いたまま保持し、脈動を速める
        anim("idle_bloom", "loop", 4, {
            "trunk":     [kf(0, 0, 0, 0), kf(1.3, 3, 5, 2), kf(2.6, -2, -5, -2), kf(4, 0, 0, 0)],
            "maw_upper": [kf(0, -14, 0, 0), kf(2, -24, 0, 0), kf(4, -14, 0, 0)],
            "vine_l":    [kf(0, 0, 0, -14), kf(1.3, 10, -14, -22), kf(2.7, -6, 10, -8), kf(4, 0, 0, -14)],
            "vine_r":    [kf(0, 0, 0, 14), kf(1.4, 10, 14, 22), kf(2.8, -6, -10, 8), kf(4, 0, 0, 14)],
            "core":      [kf(0, 0, 0, 0), kf(2, 0, 90, 0), kf(4, 0, 0, 0)],
            "petal_n":   [kf(0, OPEN, 0, 0), kf(2, OPEN - 6, 0, 0), kf(4, OPEN, 0, 0)],
            "petal_s":   [kf(0, -OPEN, 0, 0), kf(2, -OPEN + 6, 0, 0), kf(4, -OPEN, 0, 0)],
            "petal_e":   [kf(0, 0, 0, OPEN), kf(2, 0, 0, OPEN - 6), kf(4, 0, 0, OPEN)],
            "petal_w":   [kf(0, 0, 0, -OPEN), kf(2, 0, 0, -OPEN + 6), kf(4, 0, 0, -OPEN)],
        }),
        # attack: 蔓を薙ぎ払う（右の蔓を大きく振り抜き、幹をひねる）
        anim("attack", "once", 1.0, {
            "vine_r": [kf(0, 0, 40, 10), kf(0.3, -20, 70, 20), kf(0.62, 10, -80, -10), kf(1.0, 0, 0, 6)],
            "vine_l": [kf(0, 0, -20, -6), kf(0.3, 0, -40, -10), kf(0.62, 0, 20, -4), kf(1.0, 0, 0, -6)],
            "trunk":  [kf(0, 0, 0, 0), kf(0.3, -6, 22, 0), kf(0.62, 8, -26, 0), kf(1.0, 0, 0, 0)],
        }),
        # cast: 幹を反らせて蓋を開き、胞子を吐き上げる
        anim("cast", "once", 1.4, {
            "trunk":     [kf(0, 0, 0, 0), kf(0.5, -16, 0, 0), kf(0.95, -16, 0, 0), kf(1.4, 0, 0, 0)],
            "maw_upper": [kf(0, 0, 0, 0), kf(0.5, -56, 0, 0), kf(0.95, -56, 0, 0), kf(1.4, 0, 0, 0)],
            "maw_lower": [kf(0, 0, 0, 0), kf(0.5, 12, 0, 0), kf(1.4, 0, 0, 0)],
            "core":      [kf(0, 0, 0, 0), kf(0.5, 0, 180, 0), kf(1.4, 0, 360, 0)],
            "vine_l":    [kf(0, 0, 0, -6), kf(0.6, -24, 0, -30), kf(1.4, 0, 0, -6)],
            "vine_r":    [kf(0, 0, 0, 6), kf(0.6, -24, 0, 30), kf(1.4, 0, 0, 6)],
        }),
        # devour: 大顎を開いたまま吸い込む。前のめりに構え、蔓を後ろへ払う
        anim("devour", "once", 2.4, {
            "trunk":     [kf(0, 0, 0, 0), kf(0.45, 16, 0, 0), kf(2.1, 20, 0, 0), kf(2.4, 20, 0, 0)],
            "maw_upper": [kf(0, 0, 0, 0), kf(0.45, -78, 0, 0), kf(2.4, -84, 0, 0)],
            "maw_lower": [kf(0, 0, 0, 0), kf(0.45, 20, 0, 0), kf(2.4, 22, 0, 0)],
            "core":      [kf(0, 0, 0, 0), kf(1.2, 0, 360, 0), kf(2.4, 0, 720, 0)],
            "vine_l":    [kf(0, 0, 0, -6), kf(0.5, 0, 34, 26), kf(2.4, 0, 34, 26)],
            "vine_r":    [kf(0, 0, 0, 6), kf(0.5, 0, -34, -26), kf(2.4, 0, -34, -26)],
        }),
        # bite: 噛み締める一閃
        anim("bite", "once", 0.7, {
            "trunk":     [kf(0, 20, 0, 0), kf(0.12, 30, 0, 0), kf(0.7, 0, 0, 0)],
            "maw_upper": [kf(0, -84, 0, 0), kf(0.12, 6, 0, 0), kf(0.7, 0, 0, 0)],
            "maw_lower": [kf(0, 22, 0, 0), kf(0.12, -4, 0, 0), kf(0.7, 0, 0, 0)],
        }),
        # bloom: 蕾がほどけて真花になる
        anim("bloom", "once", 2.2, {
            "trunk":     [kf(0, 0, 0, 0), kf(0.7, -18, 0, 0), kf(1.6, -6, 0, 0), kf(2.2, 0, 0, 0)],
            "maw_upper": [kf(0, 0, 0, 0), kf(0.7, -60, 0, 0), kf(2.2, -14, 0, 0)],
            "core":      [kf(0, 0, 0, 0), kf(1.1, 0, 540, 0), kf(2.2, 0, 720, 0)],
            "petal_n":   [kf(0, 0, 0, 0), kf(0.9, 12, 0, 0), kf(1.8, OPEN + 8, 0, 0), kf(2.2, OPEN, 0, 0)],
            "petal_s":   [kf(0, 0, 0, 0), kf(0.9, -12, 0, 0), kf(1.8, -OPEN - 8, 0, 0), kf(2.2, -OPEN, 0, 0)],
            "petal_e":   [kf(0, 0, 0, 0), kf(0.9, 0, 0, 12), kf(1.8, 0, 0, OPEN + 8), kf(2.2, 0, 0, OPEN)],
            "petal_w":   [kf(0, 0, 0, 0), kf(0.9, 0, 0, -12), kf(1.8, 0, 0, -OPEN - 8), kf(2.2, 0, 0, -OPEN)],
            "vine_l":    [kf(0, 0, 0, -6), kf(1.1, -30, 0, -40), kf(2.2, 0, 0, -14)],
            "vine_r":    [kf(0, 0, 0, 6), kf(1.1, -30, 0, 40), kf(2.2, 0, 0, 14)],
        }),
        # choke: 腐果を吸い込んで噎せ返る痙攣
        anim("choke", "once", 1.6, {
            "trunk":     [kf(0, 22, 0, 0), kf(0.25, 34, 0, -10), kf(0.5, 26, 0, 12),
                          kf(0.8, 34, 0, -8), kf(1.2, 24, 0, 6), kf(1.6, 8, 0, 0)],
            "maw_upper": [kf(0, -20, 0, 0), kf(0.25, -70, 0, 0), kf(0.6, -20, 0, 0),
                          kf(0.95, -70, 0, 0), kf(1.6, -24, 0, 0)],
            "maw_lower": [kf(0, 0, 0, 0), kf(0.3, 26, 0, 0), kf(0.9, 8, 0, 0), kf(1.6, 0, 0, 0)],
            "vine_l":    [kf(0, 0, 0, -6), kf(0.4, 30, -20, -10), kf(1.0, 10, 14, -20), kf(1.6, 0, 0, -6)],
            "vine_r":    [kf(0, 0, 0, 6), kf(0.4, 30, 20, 10), kf(1.0, 10, -14, 20), kf(1.6, 0, 0, 6)],
        }),
        # death: 根が抜け、前のめりに倒れて枯れる
        anim("death", "once", 2.6, {
            "trunk":     [kf(0, 0, 0, 0), kf(0.6, -16, 0, 0), kf(2.6, 84, 0, 6)],
            "maw_upper": [kf(0, 0, 0, 0), kf(0.7, -50, 0, 0), kf(2.6, -8, 0, 0)],
            "maw_lower": [kf(0, 0, 0, 0), kf(0.7, 18, 0, 0), kf(2.6, 4, 0, 0)],
            "vine_l":    [kf(0, 0, 0, -6), kf(2.6, 26, 0, -46)],
            "vine_r":    [kf(0, 0, 0, 6), kf(2.6, 26, 0, 46)],
            "petal_n":   [kf(0, 0, 0, 0), kf(2.6, -18, 0, 0)],
            "petal_s":   [kf(0, 0, 0, 0), kf(2.6, 18, 0, 0)],
            "petal_e":   [kf(0, 0, 0, 0), kf(2.6, 0, 0, -18)],
            "petal_w":   [kf(0, 0, 0, 0), kf(2.6, 0, 0, 18)],
        }),
    ]

    d = template()
    d["elements"] = elements
    d["outliner"] = [root]
    d["animations"] = animations
    write(d, "radix", img)


# ══════════════════════════════════════════════════════════════════════════
#  プロップ
# ══════════════════════════════════════════════════════════════════════════

def build_root_spike():
    """地中から突き上がる根の槍。棘のある先細りの三段＋根元の土くれ。"""
    img = new_img()
    patches = {}

    def patch(name, gx, gy, color):
        x, y = gx * 8, gy * 8
        rect(img, x, y, x + 8, y + 8, color)
        patches[name] = [x, y, x + 8, y + 8]

    patch("bark", 0, 0, BARK)
    patch("barkd", 1, 0, BARK_D)
    patch("moss", 2, 0, MOSS_D)
    patch("thorn", 3, 0, FANG)
    patch("mire", 4, 0, MIRE)

    elements = []
    ids = []

    def faces(p):
        return {f: {"uv": list(patches[p]), "texture": 0}
                for f in ("north", "east", "south", "west", "up", "down")}

    def cube(name, frm, to, p):
        u = uid()
        elements.append({"name": name, "rescale": False, "locked": False, "from": frm, "to": to,
                         "autouv": 0, "color": 0, "origin": [0, 0, 0], "faces": faces(p), "uuid": u})
        ids.append(u)

    cube("clod",   [-2.6, 0.0, -2.6], [2.6, 1.4, 2.6], "mire")
    cube("stem0",  [-1.7, 0.8, -1.7], [1.7, 6.0, 1.7], "bark")
    cube("stem1",  [-1.2, 6.0, -1.2], [1.2, 11.0, 1.2], "barkd")
    cube("stem2",  [-0.7, 11.0, -0.7], [0.7, 15.0, 0.7], "bark")
    cube("tip",    [-0.32, 15.0, -0.32], [0.32, 16.6, 0.32], "thorn")
    cube("moss_a", [-1.8, 2.2, -0.7], [-1.62, 4.6, 0.7], "moss")
    cube("moss_b", [0.5, 7.0, 1.14], [1.3, 9.4, 1.32], "moss")
    # 側枝の棘（四方へ短く突き出す）
    for i, (x, z, y0) in enumerate(((2.0, 0, 4.4), (-2.0, 0, 6.6), (0, 2.0, 8.2), (0, -2.0, 3.2))):
        sx = 0.9 if x else 0.34
        sz = 0.9 if z else 0.34
        fx = (1.0, 2.6) if x > 0 else ((-2.6, -1.0) if x < 0 else (-sx, sx))
        fz = (1.0, 2.6) if z > 0 else ((-2.6, -1.0) if z < 0 else (-sz, sz))
        cube(f"thorn{i}", [fx[0], y0, fz[0]], [fx[1], y0 + 0.8, fz[1]], "thorn")

    d = template()
    d["elements"] = elements
    bu, ru = uid(), uid()
    d["outliner"] = [{"name": "model", "origin": [0, 0, 0], "uuid": ru, "export": True,
                      "isOpen": True, "visibility": True, "children": [
        {"name": "spike", "origin": [0, 0, 0], "uuid": bu, "export": True, "isOpen": True,
         "visibility": True, "children": ids}]}]
    write(d, "root_spike", img)


def build_rot_fruit():
    """苗床の死骸に残る腐果。裂けた胞子嚢＝母樹には毒。"""
    img = new_img()
    patches = {}

    def patch(name, gx, gy, color):
        x, y = gx * 8, gy * 8
        rect(img, x, y, x + 8, y + 8, color)
        patches[name] = [x, y, x + 8, y + 8]

    patch("husk", 0, 0, (86, 74, 46, 255))   # 干からびた外皮
    patch("huskd", 1, 0, (60, 52, 32, 255))
    patch("pulp", 2, 0, ROT)                 # 裂け目から覗く腐肉
    patch("spore", 3, 0, MIRE)               # 噴き出す胞子
    patch("core", 4, 0, CORE)

    elements = []
    ids = []

    def faces(p):
        return {f: {"uv": list(patches[p]), "texture": 0}
                for f in ("north", "east", "south", "west", "up", "down")}

    def cube(name, frm, to, p):
        u = uid()
        elements.append({"name": name, "rescale": False, "locked": False, "from": frm, "to": to,
                         "autouv": 0, "color": 0, "origin": [0, 0, 0], "faces": faces(p), "uuid": u})
        ids.append(u)

    cube("husk_low", [-3.0, 0.0, -3.0], [3.0, 2.2, 3.0], "husk")
    cube("husk_top", [-2.3, 2.2, -2.3], [2.3, 3.4, 2.3], "huskd")
    cube("split",    [-1.1, 1.6, -3.1], [1.1, 3.5, 3.1], "pulp")   # 縦に裂けた口
    cube("pulp_top", [-1.5, 3.4, -1.5], [1.5, 4.0, 1.5], "pulp")
    cube("spore_a",  [-0.9, 4.0, -0.9], [0.9, 4.8, 0.9], "spore")
    cube("seed",     [-0.5, 4.8, -0.5], [0.5, 5.4, 0.5], "core")
    # 地を這う細根（吸い寄せられて滑る様子が分かるよう四方に伸ばす）
    for i, (fx, fz, tx, tz) in enumerate(((-4.4, -0.5, -2.8, 0.5), (2.8, -0.5, 4.4, 0.5),
                                          (-0.5, -4.4, 0.5, -2.8), (-0.5, 2.8, 0.5, 4.4))):
        cube(f"root{i}", [fx, 0.0, fz], [tx, 0.6, tz], "huskd")

    d = template()
    d["elements"] = elements
    bu, ru = uid(), uid()
    d["outliner"] = [{"name": "model", "origin": [0, 0, 0], "uuid": ru, "export": True,
                      "isOpen": True, "visibility": True, "children": [
        {"name": "fruit", "origin": [0, 0, 0], "uuid": bu, "export": True, "isOpen": True,
         "visibility": True, "children": ids}]}]
    write(d, "rot_fruit", img)


def _disc(name, painter):
    """地面に敷く薄板1枚（上面だけにテクスチャ、側面・底面は透明）。"""
    img = new_img()
    painter(img)
    FULL = [0, 0, 64, 64]
    CLEAR = [0, 0, 1, 1]   # texel(0,0) は透明であること
    f = {n: {"uv": list(CLEAR), "texture": 0} for n in ("north", "east", "south", "west", "down")}
    f["up"] = {"uv": list(FULL), "texture": 0}
    u = uid()
    d = template()
    d["elements"] = [{"name": "disc", "rescale": False, "locked": False,
                      "from": [-9.0, 0.0, -9.0], "to": [9.0, 0.08, 9.0],
                      "autouv": 0, "color": 0, "origin": [0, 0, 0], "faces": f, "uuid": u}]
    bu, ru = uid(), uid()
    d["outliner"] = [{"name": "model", "origin": [0, 0, 0], "uuid": ru, "export": True,
                      "isOpen": True, "visibility": True, "children": [
        {"name": "circle", "origin": [0, 0, 0], "uuid": bu, "export": True, "isOpen": True,
         "visibility": True, "children": [u]}]}]
    write(d, name, img)


def build_root_circle():
    """根槍・捕食の予兆円。外周＝赤の危険環、内側＝地を割る根の筋。"""
    RED = (224, 48, 48, 210)
    ROOT = (128, 100, 58, 210)
    ROOTD = (86, 66, 38, 190)
    GREEN = (128, 186, 74, 190)
    cx = cy = 32.0

    def paint(img):
        for y in range(H):
            for x in range(W):
                dx, dy = x + 0.5 - cx, y + 0.5 - cy
                dd = math.hypot(dx, dy)
                if 28.0 <= dd <= 31.0:
                    img[y][x] = RED
                elif 24.0 <= dd <= 25.6:
                    img[y][x] = GREEN
        # 中心から外へ割れ広がる12本の根（太さが外へ向かって細る）
        for k in range(12):
            a = k * math.pi / 6 + 0.13
            for step in range(24, 118):
                r = step / 4.0
                if r > 27.0:
                    break
                px = cx + math.cos(a) * r
                py = cy + math.sin(a) * r
                wdt = 2 if r < 14 else 1
                for oy in range(-wdt, wdt + 1):
                    for ox in range(-wdt, wdt + 1):
                        ix, iy = int(px + ox), int(py + oy)
                        if 0 <= ix < W and 0 <= iy < H:
                            img[iy][ix] = ROOT if (ox + oy) % 2 == 0 else ROOTD
        # 中心の窪み
        for y in range(H):
            for x in range(W):
                if math.hypot(x + 0.5 - cx, y + 0.5 - cy) <= 4.0:
                    img[y][x] = ROOTD

    _disc("root_circle", paint)


def build_spore_pool():
    """居座る毒胞子の霧。まだら模様で「踏むと痛い床」であることを色で示す。"""
    MIST = (120, 158, 68, 150)
    MIST_D = (86, 118, 48, 160)
    SPOT = (168, 178, 82, 170)
    ROTS = (122, 82, 132, 160)
    cx = cy = 32.0

    def paint(img):
        for y in range(H):
            for x in range(W):
                dx, dy = x + 0.5 - cx, y + 0.5 - cy
                dd = math.hypot(dx, dy)
                if dd > 30.0:
                    continue
                hsh = (x * 31 + y * 17 + (x // 4) * 7 + (y // 4) * 11) % 9
                if dd > 27.0:
                    img[y][x] = MIST_D if hsh % 2 == 0 else (0, 0, 0, 0)
                elif hsh == 0:
                    img[y][x] = SPOT
                elif hsh == 1:
                    img[y][x] = ROTS
                elif hsh % 3 == 0:
                    img[y][x] = MIST_D
                else:
                    img[y][x] = MIST
        # 泡立つ胞子の粒
        for k in range(26):
            a = k * 2.399
            r = 4.0 + (k % 7) * 3.4
            px, py = int(cx + math.cos(a) * r), int(cy + math.sin(a) * r)
            for oy in range(-1, 2):
                for ox in range(-1, 2):
                    if abs(ox) + abs(oy) <= 1 and 0 <= px + ox < W and 0 <= py + oy < H:
                        img[py + oy][px + ox] = SPOT

    _disc("spore_pool", paint)


build_radix()
build_root_spike()
build_rot_fruit()
build_root_circle()
build_spore_pool()
