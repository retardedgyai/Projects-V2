#!/usr/bin/env python3
"""「不滅の王オシリオン」(5F ボス) の .bbmodel を生成する。

python3 bbmodel/gen_osirion.py <雛形.bbmodel> <出力.bbmodel>
（雛形は meta / textures の構造を借りるだけ。普段は bbmodel/osirion.bbmodel を両方に渡す）

造形はバニラ人型 (steve 骨格) をそのまま骨にし、装飾はすべて **skin と同じ密度のテクセル格子**
(1 texel = T unit) に乗せた箱で作る。彩色は面ごとのドット絵 (3 階調＋ハイライト) を焼き込む。
ボーン名・階層・ピボット・アニメ名と尺は Scala 側 (OsirionVisual / UndeadKingGoal) の契約なので変えない。
"""
import base64
import json
import struct
import sys
import uuid as uuidlib
import zlib

SRC = sys.argv[1]
OUT = sys.argv[2]

T = 0.3744          # 1 texel の model unit (steve: 頭 8px = 2.9952 unit)
SIZE = 128          # テクスチャ解像度 (左上 64x64 は skin 配置、残りが装飾用)

# ─── パレット (フラット 3 階調 + ハイライト) ────────────────────────────────
PAL = {
    ".": None,
    "r": (30, 26, 52),    "R": (48, 42, 78),    "d": (17, 15, 32),     # ローブ
    "g": (222, 172, 54),  "G": (248, 216, 112), "h": (150, 106, 26),   # 金
    "b": (212, 204, 180), "B": (238, 234, 216), "n": (156, 146, 120),  # 骨
    "k": (10, 8, 14),                                                   # 眼窩
    "s": (128, 255, 180), "S": (56, 186, 116),                          # 魂の緑
    "m": (80, 84, 100),   "M": (134, 140, 156), "x": (44, 46, 60),     # 鉄
    "t": (188, 196, 210), "T": (234, 238, 246), "u": (118, 126, 146),  # 刃
    "w": (58, 20, 40),    "W": (86, 32, 58),                            # マント裏地
}

pixels = [[(0, 0, 0, 0) for _ in range(SIZE)] for _ in range(SIZE)]


def paint(x0, y0, rows):
    for dy, row in enumerate(rows):
        for dx, ch in enumerate(row):
            c = PAL[ch]
            pixels[y0 + dy][x0 + dx] = (0, 0, 0, 0) if c is None else (*c, 255)


class Shelf:
    """装飾パーツ用の面テクスチャを棚詰めで置く単純アロケータ。"""
    def __init__(self, x, y, w, h):
        self.x, self.y, self.w, self.h = x, y, w, h
        self.cx = 0
        self.cy = 0
        self.row_h = 0

    def alloc(self, w, h):
        if self.cx + w > self.w:
            self.cx = 0
            self.cy += self.row_h
            self.row_h = 0
        assert self.cy + h <= self.h, "texture shelf overflow"
        x, y = self.x + self.cx, self.y + self.cy
        self.cx += w
        self.row_h = max(self.row_h, h)
        return x, y


shelves = [Shelf(64, 0, 64, 128), Shelf(0, 64, 64, 64)]
_art_cache = {}


def art_uv(rows):
    """ドット絵を空き棚に置き、その UV を返す (同じ絵は共有)。"""
    key = tuple(rows)
    if key in _art_cache:
        return _art_cache[key]
    w, h = len(rows[0]), len(rows)
    for s in shelves:
        try:
            x, y = s.alloc(w, h)
            break
        except AssertionError:
            continue
    else:
        raise AssertionError("no shelf space for %dx%d" % (w, h))
    paint(x, y, rows)
    uv = [x, y, x + w, y + h]
    _art_cache[key] = uv
    return uv


CLEAR = [0, 0, 1, 1]   # texel(0,0) は透明。見えない面はここを参照する

# ─── skin 配置 (64x64 左上) に骨格の絵を描く ─────────────────────────────
# 頭: 頭巾をかぶった髑髏。目は眼窩の奥に魂の緑
HEAD_FRONT = [
    "dddddddd",
    "ddrrrrdd",
    "dbBBBBbd",
    "dkkbbkkd",
    "dksnnskd",
    "dbbnnbbd",
    "dnbnbnbd",
    "ddnnnndd",
]
HEAD_SIDE = ["dddddddd", "ddrrrrdd", "drrrrrrd", "drrrrrrd", "drrrrrrd", "drrrrrrd", "ddrrrrdd", "dddddddd"]
HEAD_TOP = ["dddddddd", "drrrrrrd", "drRRRRrd", "drRRRRrd", "drRRRRrd", "drRRRRrd", "drrrrrrd", "dddddddd"]
HEAD_BOTTOM = ["dddddddd"] * 8
paint(8, 8, HEAD_FRONT)      # north
paint(24, 8, HEAD_SIDE)      # south (後頭部)
paint(0, 8, HEAD_SIDE)       # east
paint(16, 8, HEAD_SIDE)      # west
paint(8, 0, HEAD_TOP)        # up
paint(16, 0, HEAD_BOTTOM)    # down

# 胴: 金の襟・胸の魂の宝玉・帯
BODY_FRONT = [
    "gGGGGGGg",
    "ghrrrrhg",
    "rrrggrrr",
    "rrgssgrr",
    "rrgssgrr",
    "rrrggrrr",
    "rrrrrrrr",
    "rRrrrrRr",
    "hggggggh",
    "drrrrrrd",
    "drrrrrrd",
    "ddrrrrdd",
]
BODY_BACK = ["gggggggg", "hrrrrrrh", "rrrrrrrr", "rrrrrrrr", "rrrrrrrr", "rrrrrrrr",
             "rrrrrrrr", "rrrrrrrr", "hggggggh", "drrrrrrd", "drrrrrrd", "ddrrrrdd"]
BODY_SIDE = ["gggg", "hrrh", "rrrr", "rrrr", "rrrr", "rrrr", "rrrr", "rrrr", "hggh", "drrd", "drrd", "dddd"]
paint(20, 20, BODY_FRONT)    # north
paint(32, 20, BODY_BACK)     # south
paint(16, 20, BODY_SIDE)     # east
paint(28, 20, BODY_SIDE)     # west
paint(20, 16, ["gggggggg", "gggggggg", "gggggggg", "gggggggg"])   # up
paint(28, 16, ["dddddddd"] * 4)                                    # down

# 腕: 袖に金の袖口、先は骨の手
ARM = ["dddd", "rrrr", "rrrr", "rrrr", "rrrr", "rrrr", "rrrr", "rrrr", "gGGg", "hggh", "bBBb", "nbbn"]
ARM_TOP = ["dddd", "dddd", "dddd", "dddd"]
ARM_BOTTOM = ["nbbn", "bbbb", "bbbb", "nbbn"]
for x, y in ((44, 20), (36, 52)):            # right arm / left arm の帯
    paint(x, y, ARM)                          # north
    paint(x + 8, y, ARM)                      # south
    paint(x - 4, y, ARM)                      # east
    paint(x + 4, y, ARM)                      # west
    paint(x, y - 4, ARM_TOP)                  # up
    paint(x + 4, y - 4, ARM_BOTTOM)           # down

# 脚: 裾に隠れる上部は暗く、足元は鉄の脚甲
LEG = ["dddd", "dddd", "dddd", "dddd", "dddd", "dddd", "dddd", "dddd", "hggh", "mMMm", "mmmm", "xxxx"]
LEG_BOTTOM = ["xxxx"] * 4
for x, y in ((4, 20), (20, 52)):             # right leg / left leg の帯
    paint(x, y, LEG)
    paint(x + 8, y, LEG)
    paint(x - 4, y, LEG)
    paint(x + 4, y, LEG)
    paint(x, y - 4, ["dddd"] * 4)
    paint(x + 4, y - 4, LEG_BOTTOM)


def encode_png():
    raw = bytearray()
    for row in pixels:
        raw.append(0)
        for r, g, b, a in row:
            raw += bytes((r, g, b, a))
    comp = zlib.compress(bytes(raw), 9)

    def chunk(typ, data):
        c = typ + data
        return struct.pack(">I", len(data)) + c + struct.pack(">I", zlib.crc32(c) & 0xffffffff)
    return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", SIZE, SIZE, 8, 6, 0, 0, 0))
            + chunk(b"IDAT", comp) + chunk(b"IEND", b""))


# ─── ジオメトリ ─────────────────────────────────────────────────────────
def uid(name):
    return str(uuidlib.uuid5(uuidlib.NAMESPACE_URL, "scorpius/osirion/" + name))


elements = []
children = {}   # bone name -> element uuid list


def cube(bone, name, frm, to, faces):
    """frm/to は texel 座標。faces は face 名 -> ドット絵 (list[str]) か既製 uv。無い面は透明。"""
    u = uid("el/" + name)
    fd = {}
    for f in ("north", "east", "south", "west", "up", "down"):
        v = faces.get(f)
        if v is None:
            uv = CLEAR
        elif isinstance(v, list) and v and isinstance(v[0], str):
            uv = art_uv(v)
        else:
            uv = list(v)
        fd[f] = {"uv": uv, "texture": 0}
    elements.append({
        "name": name, "rescale": False, "locked": False,
        "from": [round(c * T, 4) for c in frm], "to": [round(c * T, 4) for c in to],
        "autouv": 0, "color": 0, "origin": [0, 0, 0], "faces": fd, "uuid": u,
    })
    children.setdefault(bone, []).append(u)


def skin(x, y, w, h, d):
    """skin 配置の箱 UV (steve と同じ並び)。"""
    return {
        "north": [x + d, y + d, x + d + w, y + d + h],
        "south": [x + 2 * d + w, y + d, x + 2 * d + 2 * w, y + d + h],
        "east": [x, y + d, x + d, y + d + h],
        "west": [x + d + w, y + d, x + 2 * d + w, y + d + h],
        "up": [x + d, y, x + d + w, y + d],
        "down": [x + d + w, y, x + d + 2 * w, y + d],
    }


# 骨格 (steve の寸法そのまま)
cube("_head", "head", [-4, 24, -4], [4, 32, 4], skin(0, 0, 8, 8, 8))
cube("body", "body", [-4, 12, -2], [4, 24, 2], skin(16, 16, 8, 12, 4))
cube("right_arm", "right_arm", [4, 12, -2], [8, 24, 2], skin(40, 16, 4, 12, 4))
cube("left_arm", "left_arm", [-8, 12, -2], [-4, 24, 2], skin(32, 48, 4, 12, 4))
cube("right_leg", "right_leg", [0, 0, -2], [4, 12, 2], skin(0, 16, 4, 12, 4))
cube("left_leg", "left_leg", [-4, 0, -2], [0, 12, 2], skin(16, 48, 4, 12, 4))

# 王冠 (_head): 頭頂を囲む金の環 + 四隅の尖り + 前中央に魂の宝玉を抱いた尖塔
RING_LONG = ["GGGGGGGGGG", "hggggggggh"]
RING_SHORT = ["GGGGGGGG", "hggggggh"]
RING_END = ["GG", "hh"]
cube("_head", "crown_n", [-5, 31, -5], [5, 33, -4],
     {"north": RING_LONG, "south": RING_LONG, "east": RING_END, "west": RING_END, "up": ["GGGGGGGGGG"]})
cube("_head", "crown_s", [-5, 31, 4], [5, 33, 5],
     {"north": RING_LONG, "south": RING_LONG, "east": RING_END, "west": RING_END, "up": ["GGGGGGGGGG"]})
cube("_head", "crown_e", [4, 31, -4], [5, 33, 4],
     {"east": RING_SHORT, "west": RING_SHORT, "up": ["GGGGGGGG"]})
cube("_head", "crown_w", [-5, 31, -4], [-4, 33, 4],
     {"east": RING_SHORT, "west": RING_SHORT, "up": ["GGGGGGGG"]})
SPIKE = ["GG", "gg", "hh"]
for nm, x, z in (("crown_fl", -5, -5), ("crown_fr", 3, -5), ("crown_bl", -5, 3), ("crown_br", 3, 3)):
    cube("_head", nm, [x, 33, z], [x + 2, 36, z + 2],
         {"north": SPIKE, "south": SPIKE, "east": SPIKE, "west": SPIKE, "up": ["GG", "GG"]})
CREST = ["GG", "ss", "sS", "gg", "hh"]
CREST_SIDE = ["GG", "gg", "gg", "gg", "hh"]
cube("_head", "crown_crest", [-1, 33, -5], [1, 38, -3],
     {"north": CREST, "south": CREST_SIDE, "east": CREST_SIDE, "west": CREST_SIDE, "up": ["GG", "GG"]})

# 肩当て (body): 金の縁取りの鉄の板
PAULDRON_TOP = ["GGGGGG", "GmmmmG", "GmMmmG", "GmmmmG", "GmmmmG", "GGGGGG"]
PAULDRON_SIDE = ["GGGGGG", "mmmmmm", "xxxxxx"]
for nm, x in (("pauldron_r", 3), ("pauldron_l", -9)):
    cube("body", nm, [x, 23, -3], [x + 6, 26, 3],
         {"north": PAULDRON_SIDE, "south": PAULDRON_SIDE, "east": PAULDRON_SIDE, "west": PAULDRON_SIDE,
          "up": PAULDRON_TOP, "down": ["xxxxxx"] * 6})

# マント (torso): 上段は背に沿い、下段は裾へ広がる。外は黒地に金縁、裏は臙脂
CAPE_HI_OUT = ["gddddddddg", "gddrddrddg", "gddrddrddg", "gddrddrddg", "gddrddrddg", "gddrddrddg",
               "gddrddrddg", "gddrddrddg", "gddrddrddg", "gddrddrddg", "gddrddrddg", "gddddddddg"]
CAPE_HI_IN = ["wwwwwwwwww", "wwWwwwwWww", "wwWwwwwWww", "wwWwwwwWww", "wwWwwwwWww", "wwWwwwwWww",
              "wwWwwwwWww", "wwWwwwwWww", "wwWwwwwWww", "wwWwwwwWww", "wwWwwwwWww", "wwwwwwwwww"]
CAPE_LO_OUT = ["gddddddddddg", "gddrddddrddg", "gddrddSdrddg", "gddrdSsSrddg", "gddrddSdrddg",
               "gddrddddrddg", "gddrddddrddg", "gddrddddrddg", "gddrddddrddg", "gddrddddrddg",
               "hhhhhhhhhhhh", "gggggggggggg"]
CAPE_LO_IN = ["wwwwwwwwwwww", "wwWwwwwwwWww", "wwWwwwwwwWww", "wwWwwwwwwWww", "wwWwwwwwwWww", "wwWwwwwwwWww",
              "wwWwwwwwwWww", "wwWwwwwwwWww", "wwWwwwwwwWww", "wwWwwwwwwWww", "wwwwwwwwwwww", "gggggggggggg"]
cube("torso", "cape_hi", [-5, 12, 2], [5, 24, 3],
     {"south": CAPE_HI_OUT, "north": CAPE_HI_IN, "east": ["g"] * 12, "west": ["g"] * 12, "up": ["gggggggggg"]})
cube("torso", "cape_lo", [-6, 0, 2], [6, 12, 3],
     {"south": CAPE_LO_OUT, "north": CAPE_LO_IN, "east": ["g"] * 12, "west": ["g"] * 12, "down": ["gggggggggggg"]})

# ローブの裾 (torso): 帯の下から二段。前面に金の縦線、下段の裾に金の縁。足元の鉄靴だけ覗く
SKIRT_HI_F = ["drrrggrrrd"] * 6 + ["ddrrggrrdd"]
SKIRT_HI_B = ["drrrrrrrrd"] * 6 + ["ddrrrrrrdd"]
SKIRT_HI_S = ["drrrrd"] * 7
SKIRT_LO_F = ["drrRrggrRrrd"] * 4 + ["hhhhhgghhhhh", "gggggggggggg"]
SKIRT_LO_B = ["drrRrrrrRrrd"] * 4 + ["hhhhhhhhhhhh", "gggggggggggg"]
SKIRT_LO_S = ["drrRrrRd"] * 4 + ["hhhhhhhh", "gggggggg"]
cube("torso", "skirt_hi", [-5, 8, -3], [5, 15, 3],
     {"north": SKIRT_HI_F, "south": SKIRT_HI_B, "east": SKIRT_HI_S, "west": SKIRT_HI_S})
cube("torso", "skirt_lo", [-6, 2, -4], [6, 8, 4],
     {"north": SKIRT_LO_F, "south": SKIRT_LO_B, "east": SKIRT_LO_S, "west": SKIRT_LO_S, "down": ["dddddddddddd"] * 8})

# 大剣 (right_arm): 手の下に鍔、刃は切っ先まで 1 枚の板。鍔中央に魂の宝玉
GUARD_F = ["GGGGGGGG", "hhhhhhhh"]
GUARD_TOP = ["GGGGGGGG", "GGGGGGGG"]
GUARD_END = ["GG", "hh"]
cube("right_arm", "sword_guard", [2, 10, -1], [10, 12, 1],
     {"north": GUARD_F, "south": GUARD_F, "east": GUARD_END, "west": GUARD_END,
      "up": GUARD_TOP, "down": ["hhhhhhhh", "hhhhhhhh"]})
BLADE_F = ["Tt"] * 13 + ["Tt", "Tu", "T."]
BLADE_E = ["u"] * 15 + ["."]
cube("right_arm", "sword_blade", [5, -6, 0], [7, 10, 1],
     {"north": BLADE_F, "south": BLADE_F, "east": BLADE_E, "west": BLADE_E, "up": ["tt"]})
GEM = ["ss", "sS"]
cube("right_arm", "sword_gem", [5, 10, -2], [7, 12, -1],
     {"north": GEM, "east": GEM, "west": GEM, "up": GEM, "down": GEM})

# ─── ボーン階層 (steve のピボットをそのまま使う。Scala 側の契約) ──────────
def bone(name, origin, kids):
    return {"name": name, "origin": origin, "color": 0, "uuid": uid("bone/" + name),
            "export": True, "mirror_uv": False, "isOpen": True, "locked": False,
            "visibility": True, "autouv": 0,
            "children": children.get(name, []) + kids}


outliner = [bone("model", [0, 0, 0], [
    bone("right_leg", [0.71136, 4.4928, 0], []),
    bone("left_leg", [-0.71136, 4.4928, 0], []),
    bone("torso", [0, 4.4, 0], [
        bone("body", [0, 4.5856, 0], [
            bone("right_arm", [1.872, 8.2368, 0], []),
            bone("left_arm", [-1.872, 8.2368, 0], []),
        ]),
        bone("_head", [0, 8.9856, 0], []),
    ]),
])]
assert set(children) <= {"model", "right_leg", "left_leg", "torso", "body", "right_arm", "left_arm", "_head"}
for name in ("crown_n", "cape_hi", "sword_blade"):
    e = next(e for e in elements if e["name"] == name)
    assert all(-16 <= c <= 32 for c in e["from"] + e["to"]), name


# ─── アニメーション (名前・尺は Scala 側の契約) ───────────────────────────
BONE = {nm: uid("bone/" + nm) for nm in
        ("_head", "right_arm", "left_arm", "torso", "body", "right_leg", "left_leg", "model")}


_kf_count = [0]


def kf(t, x, y, z):
    _kf_count[0] += 1
    return {"channel": "rotation", "data_points": [{"x": x, "y": y, "z": z}],
            "uuid": uid("kf/%d" % _kf_count[0]), "time": t, "color": -1, "interpolation": "linear"}


def make_anim(name, loop, length, animators):
    out = {"uuid": uid("anim/" + name), "name": name, "loop": loop, "override": False,
           "length": length, "snapping": 20, "selected": False, "anim_time_update": "",
           "blend_weight": "", "start_delay": "", "loop_delay": "", "animators": {}}
    for bn, kfs in animators.items():
        out["animators"][BONE[bn]] = {"name": bn, "keyframes": kfs}
    return out


anims = [
    # idle: 重い呼吸。腕・頭・胴を揺らしてマントと裾に動きを出す
    make_anim("idle", "loop", 4, {
        "right_arm": [kf(0, 0, 0, 4), kf(2, -6, 0, 9), kf(4, 0, 0, 4)],
        "left_arm":  [kf(0, 0, 0, -4), kf(2, -4, 0, -9), kf(4, 0, 0, -4)],
        "_head":     [kf(0, 0, 0, 0), kf(2, 5, 4, 0), kf(4, 0, 0, 0)],
        "torso":     [kf(0, 0, 0, 0), kf(1, 0, 0, 2), kf(2, 3, 0, 0), kf(3, 0, 0, -2), kf(4, 0, 0, 0)],
    }),
    # attack: 大剣を振り上げ叩きつけ
    make_anim("attack", "once", 1, {
        "right_arm": [kf(0, 0, 0, 0), kf(0.3, -170, 0, 0), kf(0.55, 50, 0, 0), kf(1, 0, 0, 0)],
        "torso":     [kf(0, 0, 0, 0), kf(0.3, -10, 0, 0), kf(0.55, 16, 0, 0), kf(1, 0, 0, 0)],
        "_head":     [kf(0, 0, 0, 0), kf(0.55, 14, 0, 0), kf(1, 0, 0, 0)],
    }),
    # charge: 前傾し大剣を前方へ突き出すランジ (突進中に保持)
    make_anim("charge", "once", 0.9, {
        "torso":     [kf(0, 0, 0, 0), kf(0.15, 26, 0, 0), kf(0.75, 26, 0, 0), kf(0.9, 8, 0, 0)],
        "right_arm": [kf(0, 0, 0, 4), kf(0.15, -100, 0, 0), kf(0.75, -100, 0, 6), kf(0.9, -20, 0, 0)],
        "left_arm":  [kf(0, 0, 0, -4), kf(0.15, 36, 0, -16), kf(0.75, 36, 0, -16), kf(0.9, 0, 0, -4)],
        "_head":     [kf(0, 0, 0, 0), kf(0.15, 16, 0, 0), kf(0.75, 16, 0, 0), kf(0.9, 4, 0, 0)],
    }),
    # cast: 両腕を広げ天を仰ぐ詠唱
    make_anim("cast", "once", 1.6, {
        "right_arm": [kf(0, 0, 0, 0), kf(0.5, -125, 0, 40), kf(1.1, -125, 0, 40), kf(1.6, 0, 0, 0)],
        "left_arm":  [kf(0, 0, 0, 0), kf(0.5, -125, 0, -40), kf(1.1, -125, 0, -40), kf(1.6, 0, 0, 0)],
        "_head":     [kf(0, 0, 0, 0), kf(0.5, -28, 0, 0), kf(1.1, -28, 0, 0), kf(1.6, 0, 0, 0)],
        "torso":     [kf(0, 0, 0, 0), kf(0.5, -12, 0, 0), kf(1.1, -12, 0, 0), kf(1.6, 0, 0, 0)],
    }),
    # death: 前のめりに崩れ落ちる
    make_anim("death", "once", 2, {
        "torso":     [kf(0, 0, 0, 0), kf(0.6, -14, 0, 0), kf(2, 80, 0, 0)],
        "_head":     [kf(0, 0, 0, 0), kf(2, 32, 0, 0)],
        "right_arm": [kf(0, 0, 0, 4), kf(2, 24, 0, 28)],
        "left_arm":  [kf(0, 0, 0, -4), kf(2, 24, 0, -28)],
    }),
]

# ─── 書き出し ───────────────────────────────────────────────────────────
d = json.load(open(SRC))
d["name"] = "osirion"
d["geometry_name"] = "osirion"
d["resolution"] = {"width": SIZE, "height": SIZE}
d["elements"] = elements
d["outliner"] = outliner
d["animations"] = anims
tex = d["textures"][0]
tex["source"] = "data:image/png;base64," + base64.b64encode(encode_png()).decode()
tex["name"] = "osirion.png"
if "relative_path" in tex:
    tex["relative_path"] = "osirion.png"
for k in ("uv_width", "uv_height", "width", "height"):
    if k in tex:
        tex[k] = SIZE
d["textures"] = [tex]

json.dump(d, open(OUT, "w"))
print("wrote", OUT)
print("elements:", len(elements))
print("animations:", [a["name"] for a in anims])
