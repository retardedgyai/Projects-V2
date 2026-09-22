#!/usr/bin/env python3
"""20F 黄金の宝物殿。黄金卿ピグリン・ロードと予兆2種の bbmodel を再現可能に生成する。

python3 bbmodel/gen_piglin_lord.py [出力先=bbmodel]
ロードは 28unit × raw scale3 /16 = 5.25bl（冠の先まで）。1unit=3texel（16px/bl）。
丸みは段を付けた箱。曲線（斧の刃・腕盾・腰布の裾）はドット絵の板を同幅区間ごとに 1unit 厚の箱へ押し出し、
縁の面には絵の端の列/行を貼る。鎧は焼き込み 4 色（影・地・明・光点）。補間・PBR・ランタイム着色は使わない
（灼熱の演出だけは実行時 glow で重ねる——鎧の色そのものは変えない）。cube 回転は使わない（Java モデルの制限）。

ボーン名・アニメ名・尺は Scala 側（PiglinLordVisual / PiglinLordFight）との契約。名前を変えるときは両方を同じ変更で直す。
chestplate / crown / pauldron_l / pauldron_r は第二形態で setBoneVisible で隠される——剥がれる金だけを入れ、
下の焼け皮は胴・頭のボーンに完全な面で残す。

回転の向き（WSEE 実機＝Blockbench 旧形式）: x+ で上端が前(-z)へ倒れ（=ピボットの下側は後ろへ）、
y+ で正面が右(+x)へ向き、z+ で上端が左(-x)へ傾く。position の x は符号が反転して効く。
斧は右拳を中心に回る。休めの構えは刃が肩の高さで上向き。攻撃では axe を z=-180 へ回して柄を腕の延長へ向ける
（+180 だと刃が胸を横切る）。薙ぎは y=90 も足して刃の縁を振る向きへ寝かせる。
"""
import base64
import json
import math
from pathlib import Path
import struct
import sys
import uuid
import zlib

RIM_UNITS = 8
MATERIALS = {
    # 材質ごとに影・地・明・光点の4色。溶岩の熱に炙られ続けた皮は黒ずみ、金だけが冴える。
    "hide": ("2e1a12", "4e2d22", "6e4534", "8a5a46"),
    "gold": ("8a6414", "e0aa1e", "ffd447", "fff1a8"),
    "plate": ("1c171c", "30292f", "4a4148", "6d626b"),
    "cloth": ("4c0d12", "82161f", "ad2a30", "c94a48"),
    "tusk": ("a09368", "d9cfa4", "f3ecd2", "ffffff"),
    "eye": ("c83c08", "ff7a14", "ffc23a", "fff0a0"),
    "soot": ("100c0e", "1a1517", "27201f", "33292a"),
}
MATERIALS = {k: tuple(tuple(bytes.fromhex(c)) for c in v) for k, v in MATERIALS.items()}
COLORS = {"danger": (240, 170, 60), "ember": (255, 96, 24)}
# ドット絵用の文字→色。h/g/G/H=金の影・地・明・光点、j/k/K=黒鉄、d/c/C=緋布、e/E=灼眼。
ART = {"h": MATERIALS["gold"][0], "g": MATERIALS["gold"][1], "G": MATERIALS["gold"][2], "H": MATERIALS["gold"][3],
       "j": MATERIALS["plate"][0], "k": MATERIALS["plate"][1], "K": MATERIALS["plate"][2],
       "d": MATERIALS["cloth"][0], "c": MATERIALS["cloth"][1], "C": MATERIALS["cloth"][2],
       "e": MATERIALS["eye"][1], "E": MATERIALS["eye"][2],
       "t": MATERIALS["tusk"][1], "T": MATERIALS["tusk"][2]}

# 1文字=1unit の下絵。plate() へ渡す前に expand() で 3texel に起こす。
# 大斧の刃（+x 側から見た絵。左が柄、右が刃先）。上の角は前へ張り出し、髭は柄の下まで垂れる。
BLADE = [
    "ggggGG...",
    "gggggggG.",
    "hggggggGG",
    "hgggggggG",
    "hgggggggG",
    "hgggggggG",
    "hgggggggG",
    ".hgggggGG",
    "..hggggG.",
    "...hgggG.",
    "....hggG.",
    ".....hgG.",
]
# 左腕の腕盾。金の縁に黒鉄の面、中央に打ち出しの光点。
SHIELD = [
    "..hGGh..",
    ".hgggGh.",
    "hggkkggG",
    "hgkjjkgG",
    "hgkjKkgG",
    "hgkjjkgG",
    "hgkkkkgG",
    "hggkkggG",
    ".hgggGh.",
    "..hGGh..",
]
# 腰布（前から見た絵）。裾は焼けてほつれる。
LOINCLOTH = [
    "ccccccccc",
    "cCcccccCc",
    ".ccccdcc.",
    "..cd.cc..",
]
# 胸当てに打ち出した猪頭の紋（1文字=1texel）。黒鉄の頭骨、灼眼、牙。
EMBLEM = [
    "...jjjjj...",
    "..jkkkkkj..",
    ".jkkkkkkkj.",
    ".jkEkkkEkj.",
    ".jkkkkkkkj.",
    "..jkkjkkj..",
    "..jkjjjkj..",
    ".TjkkkkkjT.",
    ".T.jjjjj.T.",
    "..t.....t..",
]


def breastplate_art(w, h):
    """金の胸当て 1texel 精度。縁は上と左が明、下と右が影。中央に紋。"""
    rows = []
    for y in range(h):
        row = ""
        for x in range(w):
            if y == 0 or x == 0: row += "G"
            elif y == h - 1 or x == w - 1: row += "h"
            elif y == h // 2 and x % 5 != 2: row += "G"
            else: row += "g"
        rows.append(list(row))
    ox, oy = (w - len(EMBLEM[0])) // 2, (h - len(EMBLEM)) // 2
    for r, line in enumerate(EMBLEM):
        for c, ch in enumerate(line):
            if ch != ".":
                rows[oy + r][ox + c] = ch
    return ["".join(r) for r in rows]


def expand(rows, k=3):
    return ["".join(ch * k for ch in row) for row in rows for _ in range(k)]


def outline(rows, front="H", top="G"):
    """行ごとの先端 1texel を光点、列ごとの上端 1texel を明にして刃の縁を立てる。"""
    grid = [list(r) for r in rows]
    for row in grid:
        cols = [c for c, ch in enumerate(row) if ch != "."]
        if cols:
            row[cols[-1]] = front
    for c in range(len(grid[0])):
        for row in grid:
            if row[c] != ".":
                if row[c] != front:
                    row[c] = top
                break
    return ["".join(r) for r in grid]


def noise(x, y, seed):
    n = (x * 374761393 + y * 668265263 + seed * 1442695041) & 0xffffffff
    n = ((n ^ (n >> 13)) * 1274126177) & 0xffffffff
    return (n ^ (n >> 16)) & 0xffff


def png(pixels):
    height, width = len(pixels), len(pixels[0])
    def chunk(kind, data):
        return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data) & 0xffffffff)
    raw = b"".join(b"\0" + bytes(c for p in row for c in p) for row in pixels)
    return b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0)) + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b"")


class Model:
    def __init__(self, name, size=256, texels=3):
        self.name, self.size, self.texels = name, size, texels
        self.serial = 0
        self.elements, self.bones, self.animations = [], {}, []
        self.pixels = [[(0, 0, 0, 0) for _ in range(size)] for _ in range(size)]
        self.uv_cache = {}
        self.pen_x, self.pen_y, self.row_height = 2, 2, 0

    def uid(self):
        self.serial += 1
        return str(uuid.uuid5(uuid.NAMESPACE_URL, f"scorpius/piglin_lord/{self.name}/{self.serial}"))

    def patch(self, w, h, paint, key):
        if key in self.uv_cache:
            return self.uv_cache[key]
        if self.pen_x + w + 1 >= self.size:
            self.pen_x, self.pen_y = 2, self.pen_y + self.row_height + 2
            self.row_height = 0
        assert self.pen_y + h + 1 < self.size, f"{self.name}: UV atlas full ({w}x{h})"
        x0, y0 = self.pen_x, self.pen_y
        for y in range(h):
            for x in range(w):
                self.pixels[y0 + y][x0 + x] = paint(x, y)
        uv = [x0, y0, x0 + w, y0 + h]
        self.uv_cache[key] = uv
        self.pen_x += w + 2
        self.row_height = max(self.row_height, h)
        return uv

    def surface(self, w, h, material, side, motif):
        w, h = max(1, round(w * self.texels)), max(1, round(h * self.texels))
        side = "top" if side in ("up", "down") else "side"
        seed = sum(ord(c) * (i + 1) for i, c in enumerate(material + side + motif))
        def paint(x, y):
            palette = MATERIALS[material]
            cluster = noise(x // 3, y // 3, seed)
            tone = 1
            if cluster % 13 == 0:
                tone = 0
            elif cluster % 19 == 0:
                tone = 2
            if material == "hide":
                # 焼け皮。横に走る皺、まだらの火傷跡、ときおり煤の斑。
                if noise(0, y // 2, seed) % 5 == 0 and (x + y) % 7 != 3: tone = 0
                if noise(x // 4, y // 4, seed + 3) % 11 == 0: tone = 2
                if noise(x // 3, y // 3, seed + 9) % 31 == 0: return (*MATERIALS["soot"][1], 255)
                if motif == "shade": tone = 0 if cluster % 5 else 1
                if motif == "jaw" and y == 1: return (*MATERIALS["soot"][0], 255)
                if motif in ("snout", "ear") and tone == 1: tone = 2
                if motif == "snout" and side == "side" and y >= h - 2: tone = 0
            elif material == "gold":
                # 打ち出しの金板。中央の稜線に光が走り、隅は煤で沈む。
                if motif == "plate" and w >= 9 and h >= 9:
                    if y == h // 2 and x % 5 != 2: tone = 2
                    if (x <= 1 or x >= w - 2) and (y <= 1 or y >= h - 2): tone = 0
                    if x % 6 == 3 and y % 6 == 3: tone = 3
                if motif == "band" and h >= 3 and y == 1: tone = 2
            elif material == "plate":
                if motif == "rivet" and x % 6 == 2 and y % 6 == 2: tone = 3
                if motif == "haft" and (y // 4) % 5 == 0: tone = 0
            elif material == "cloth":
                if (x + noise(0, y // 6, seed)) % 6 == 0: tone = 0
            if w >= 6 and h >= 6:
                if y == 0 and x % 7 != 2: tone = 2
                elif y == h - 1 or x == w - 1: tone = 0
            return (*palette[tone], 255)
        return self.patch(w, h, paint, (w, h, material, side, motif))

    def cube(self, name, start, end, material, bucket, motif="plain", face_uv=None):
        ident = self.uid()
        dx, dy, dz = [b - a for a, b in zip(start, end)]
        faces = {}
        for side, w, h in (("north", dx, dy), ("south", dx, dy), ("east", dz, dy),
                           ("west", dz, dy), ("up", dx, dz), ("down", dx, dz)):
            uv = face_uv.get(side, [0, 0, 1, 1]) if face_uv else self.surface(w, h, material, side, motif)
            faces[side] = {"uv": uv[:], "texture": 0}
        self.elements.append({"name": name, "uuid": ident, "from": start, "to": end,
                              "origin": [0, 0, 0], "rescale": False, "autouv": 0, "color": 0, "faces": faces})
        bucket.append(ident)

    def plate(self, name, rows, bucket, plane, thick, y_top, edge, material="gold"):
        """ドット絵の板を押し出す。rows は 1文字=1texel（'.' は透明）。
        plane 'yz' は x に薄い板で +x 側から見た絵（列は -z へ進む）、'xy' は z に薄い板で前(-z)から見た絵（列は -x へ進む）。
        thick は薄い軸の範囲、y_top は絵の上端、edge は絵の左端（yz なら z、xy なら x）。
        同じ横幅が続く行をひとつの箱にし、表裏には絵（裏は鏡像）、縁の面には端の列/行を貼る。"""
        t, h, w = self.texels, len(rows), len(rows[0])
        def color(ch):
            return (0, 0, 0, 0) if ch == "." else (*ART[ch], 255)
        front = self.patch(w, h, lambda x, y: color(rows[y][x]), ("art", name, False))
        back = self.patch(w, h, lambda x, y: color(rows[y][w - 1 - x]), ("art", name, True))
        tk = max(1, round((thick[1] - thick[0]) * t))
        def vstrip(cells):
            return self.patch(tk, len(cells), lambda x, y: cells[y], ("strip", name, "v", tuple(cells)))
        def hstrip(cells):
            return self.patch(len(cells), tk, lambda x, y: cells[x], ("strip", name, "h", tuple(cells)))
        def sub(uv, c0, r0, c1, r1):
            return [uv[0] + c0, uv[1] + r0, uv[0] + c1, uv[1] + r1]
        runs = []
        for r, row in enumerate(rows):
            cols = [c for c, ch in enumerate(row) if ch != "."]
            if not cols:
                continue
            span = (cols[0], cols[-1] + 1)
            if runs and runs[-1][0] == span and runs[-1][2] == r:
                runs[-1][2] = r + 1
            else:
                runs.append([span, r, r + 1])
        for (c0, c1), r0, r1 in runs:
            y0, y1 = y_top - r1 / t, y_top - r0 / t
            column = lambda c: [color(rows[r][c]) for r in range(r0, r1)]
            line = lambda r: [color(rows[r][c]) for c in range(c1 - 1, c0 - 1, -1)]
            if plane == "yz":
                start, end = [thick[0], y0, edge - c1 / t], [thick[1], y1, edge - c0 / t]
                faces = {"east": sub(front, c0, r0, c1, r1), "west": sub(back, w - c1, r0, w - c0, r1),
                         "north": vstrip(column(c1 - 1)), "south": vstrip(column(c0)),
                         "up": vstrip(line(r0)), "down": vstrip(line(r1 - 1))}
            else:
                start, end = [edge - c1 / t, y0, thick[0]], [edge - c0 / t, y1, thick[1]]
                faces = {"north": sub(front, c0, r0, c1, r1), "south": sub(back, w - c1, r0, w - c0, r1),
                         "east": vstrip(column(c0)), "west": vstrip(column(c1 - 1)),
                         "up": hstrip(line(r0)), "down": hstrip(line(r1 - 1))}
            self.cube(name, [round(v, 4) for v in start], [round(v, 4) for v in end], material, bucket, face_uv=faces)

    def bone(self, name, pivot, children):
        ident = self.uid()
        self.bones[name] = ident
        return {"name": name, "uuid": ident, "origin": pivot, "rotation": [0, 0, 0],
                "export": True, "visibility": True, "isOpen": True, "children": children}

    def anim(self, name, length, tracks, loop="once"):
        animators = {}
        for bone, frames in tracks.items():
            keys = []
            for t, xyz, *channel in frames:
                keys.append({"channel": channel[0] if channel else "rotation", "time": t,
                             "data_points": [dict(zip(("x", "y", "z"), xyz))],
                             "uuid": self.uid(), "interpolation": "linear", "color": -1})
            animators[self.bones[bone]] = {"name": bone, "type": "bone", "keyframes": keys}
        self.animations.append({"name": name, "uuid": self.uid(), "loop": loop, "length": length,
                                "override": False, "snapping": 20, "animators": animators})

    def write(self, out, root):
        texture = {"name": self.name + ".png", "uuid": self.uid(), "id": "0", "mode": "bitmap",
                   "width": self.size, "height": self.size, "uv_width": self.size, "uv_height": self.size,
                   "particle": False, "source": "data:image/png;base64," + base64.b64encode(png(self.pixels)).decode()}
        data = {"meta": {"format_version": "4.10", "model_format": "free", "box_uv": False},
                "name": self.name, "model_identifier": self.name, "geometry_name": self.name,
                "resolution": {"width": self.size, "height": self.size}, "elements": self.elements,
                "outliner": [root], "textures": [texture], "animations": self.animations}
        for e in self.elements:
            assert all(-16 <= v <= 32 for v in e["from"] + e["to"]), e["name"]
        assert self.pixels[0][0][3] == 0
        path = out / (self.name + ".bbmodel")
        path.write_text(json.dumps(data, ensure_ascii=False, separators=(",", ":")) + "\n")
        print(f"{path}: {len(self.elements)} cubes / {len(self.bones)} bones / {len(self.animations)} animations / {self.size}px atlas")


def lord(out):
    """黄金卿。前は -z。足元原点、冠の先が y=28（scale3 で 5.25bl）。"""
    m = Model("piglin_lord", 512)
    body, chest, head, crown, axe = ([] for _ in range(5))
    pauldrons, arms, hands, legs, feet = ([[], []] for _ in range(5))

    # 胴。腹より胸が張り出し、頭の後ろに焼け皮の瘤が盛り上がる前屈みの巨躯。
    m.cube("pelvis", [-6.5, 9, -3.5], [6.5, 12.5, 3.5], "hide", body)
    m.cube("belly", [-7, 12, -4], [7, 16, 4.5], "hide", body)
    m.cube("chest", [-7.5, 15.5, -4.5], [7.5, 21, 4], "hide", body)
    m.cube("hump", [-5.5, 19, 3.5], [5.5, 23, 6.5], "hide", body)
    m.cube("hump_top", [-4, 22.5, 2.5], [4, 24.5, 5.2], "hide", body)
    m.cube("neck", [-3.2, 19, -3], [3.2, 22, 2.4], "hide", body)
    m.cube("belt", [-6.8, 9.3, -3.8], [6.8, 10.9, 3.8], "plate", body, "rivet")
    m.cube("buckle", [-1.6, 9.1, -4.1], [1.6, 11.1, -3.7], "gold", body, "band")
    m.plate("loincloth", expand(LOINCLOTH), body, "xy", (-4.6, -3.8), 10.2, 4.5, "cloth")
    m.cube("loincloth_back", [-4.5, 6.5, 3.4], [4.5, 10, 4.2], "cloth", body)
    # 胸の金板は phase2 で剥がれる。猪頭の紋を打ち出した胸当て・腹帯・脇板・肩口の鋲。
    m.plate("breastplate", breastplate_art(42, 21), chest, "xy", (-5.2, -4.3), 21.4, 7.0)
    m.cube("belly_plate", [-6.6, 12.8, -4.7], [6.6, 14.5, -3.9], "gold", chest, "plate")
    m.cube("plate_l", [-8, 15, -4.6], [-7.3, 21, 2.2], "gold", chest, "plate")
    m.cube("plate_r", [7.3, 15, -4.6], [8, 21, 2.2], "gold", chest, "plate")
    m.cube("plate_rivets", [-7.3, 20.6, -5.4], [7.3, 21.5, -5.15], "plate", chest, "rivet")
    # 頭。突き出た鼻面と下顎、大きな牙、重い眉、外へ垂れる耳。目は眉の下で小さく灼ける。
    m.cube("skull", [-5, 20.5, -6], [5, 27, 2], "hide", head)
    m.cube("jaw", [-4, 19.7, -6.7], [4, 21.2, -1.5], "hide", head, "jaw")
    m.cube("snout", [-3.1, 21.2, -9.6], [3.1, 24.4, -5.9], "hide", head, "snout")
    m.cube("brow", [-5.3, 25, -6.7], [5.3, 26.2, -5.8], "hide", head, "shade")
    for side in (-1, 1):
        def span(a, b, side=side):
            return sorted((side * a, side * b))
        xa, xb = span(.6, 1.9); m.cube("nostril", [xa, 22.3, -9.8], [xb, 23.4, -9.55], "soot", head)
        xa, xb = span(1.6, 2.9); m.cube("eye", [xa, 24, -6.25], [xb, 25.1, -5.95], "eye", head)
        xa, xb = span(2.2, 3.5); m.cube("tusk", [xa, 20.4, -8], [xb, 23.6, -6.8], "tusk", head)
        xa, xb = span(2.7, 3.8); m.cube("tusk_tip", [xa, 23.4, -8.3], [xb, 24.8, -7.3], "tusk", head)
        xa, xb = span(4.9, 6.4); m.cube("ear_root", [xa, 24.6, -3.2], [xb, 27.2, 1], "hide", head, "ear")
        xa, xb = span(6.4, 7.9); m.cube("ear_mid", [xa, 23.4, -3.6], [xb, 26.4, .8], "hide", head, "ear")
        xa, xb = span(7.9, 9.4); m.cube("ear_tip", [xa, 22.8, -3.2], [xb, 25.2, .4], "hide", head, "ear")
    m.cube("earring", [-9.0, 23, -3.9], [-8.2, 23.8, -3.2], "gold", head, "band")
    # 冠。頭頂の帯と五つの棘、額に灼眼の石。棘の先が身長 28 を決める。
    m.cube("crown_band", [-4.8, 26.6, -5.8], [4.8, 27.4, 1.9], "gold", crown, "band")
    for x, z in ((-4, -5), (4, -5), (-4.3, 1.4), (4.3, 1.4)):
        m.cube("crown_spike", [x - .55, 27.4, z - .55], [x + .55, 28, z + .55], "gold", crown)
    m.cube("crown_crest", [-.7, 27.4, -5.9], [.7, 28, -4.7], "gold", crown)
    m.cube("crown_gem", [-.6, 26.7, -6.1], [.6, 27.3, -5.8], "eye", crown)
    for i, side in enumerate((-1, 1)):
        x = side * 3.6
        m.cube("thigh", [x - 2.1, 6.5, -2.3], [x + 2.1, 11, 2.3], "hide", legs[i])
        m.cube("shin", [x - 1.8, 2.5, -1.9], [x + 1.8, 7, 1.7], "hide", legs[i])
        m.cube("greave", [x - 2.0, 2.7, -2.8], [x + 2.0, 7.8, -1.8], "gold", legs[i], "plate")
        m.cube("boot", [x - 2.4, 0, -3.6], [x + 2.4, 2.6, 2.4], "plate", feet[i], "rivet")
        m.cube("toe_cap", [x - 2.2, 0, -4.3], [x + 2.2, 1.5, -3.5], "plate", feet[i], "rivet")
        m.cube("boot_trim", [x - 2.5, 2.2, -3.7], [x + 2.5, 2.9, 2.5], "gold", feet[i], "band")
        # 肩当ては二段で丸みを出し、下段の縁は黒鉄。棘が輪郭に立つ。phase2 で剥がれる。
        xa, xb = sorted((side * 6.2, side * 11.2)); m.cube("pauldron_cap", [xa, 20.4, -3.4], [xb, 22.6, 3.4], "gold", pauldrons[i], "plate")
        xa, xb = sorted((side * 7.4, side * 12.0)); m.cube("pauldron_tier", [xa, 18.2, -3.8], [xb, 20.6, 3.8], "gold", pauldrons[i], "plate")
        xa, xb = sorted((side * 7.3, side * 12.1)); m.cube("pauldron_rim", [xa, 17.9, -3.9], [xb, 18.5, 3.9], "plate", pauldrons[i], "rivet")
        xa, xb = sorted((side * 10.0, side * 11.2)); m.cube("pauldron_spike", [xa, 22.5, -.6], [xb, 24.6, .6], "gold", pauldrons[i])
        x = side * 9.3
        m.cube("upper_arm", [x - 2.2, 13.5, -2.2], [x + 2.2, 20.5, 2.2], "hide", arms[i])
        m.cube("forearm", [x - 2.6, 7.5, -2.6], [x + 2.6, 13.9, 2.6], "hide", hands[i])
        m.cube("bracer", [x - 2.9, 9.4, -2.9], [x + 2.9, 12.4, 2.9], "gold", hands[i], "plate")
        m.cube("bracer_strap", [x - 2.75, 8.7, -2.75], [x + 2.75, 9.5, 2.75], "plate", hands[i], "rivet")
        m.cube("fist", [x - 2.8, 4.8, -3.0], [x + 2.8, 7.8, 2.6], "hide", hands[i])
    # 左前腕の外側に金の腕盾。右拳の前に大斧——柄は縦、刃は前へ張り出し、柄尻は足元に届く。
    m.plate("shield", expand(SHIELD), hands[0], "yz", (-13.6, -12.6), 15, 4.0)
    m.cube("shield_boss", [-14.3, 9.1, -1.2], [-13.6, 11.1, 1.2], "gold", hands[0], "band")
    m.cube("shield_strap", [-12.7, 8.5, -1.6], [-12.1, 12, 1.6], "plate", hands[0], "rivet")
    m.cube("axe_haft", [8.5, .4, -4.4], [10.1, 24.4, -2.8], "plate", axe, "haft")
    m.cube("axe_grip", [8.3, 3.4, -4.6], [10.3, 8.6, -2.6], "cloth", axe)
    m.cube("axe_pommel", [8.2, .1, -4.7], [10.4, 1.4, -2.5], "gold", axe, "band")
    m.plate("axe_blade", outline(expand(BLADE)), axe, "yz", (8.8, 9.8), 25.4, -4.4)
    m.cube("axe_socket_hi", [8.3, 22.1, -4.75], [10.3, 23.7, -2.55], "plate", axe, "rivet")
    m.cube("axe_socket_lo", [8.3, 17.3, -4.75], [10.3, 18.7, -2.55], "plate", axe, "rivet")
    m.cube("axe_poll", [8.9, 20.3, -2.8], [9.7, 22.5, -.6], "gold", axe)
    m.cube("axe_poll_tip", [9.05, 20.8, -.6], [9.55, 22, .7], "gold", axe)
    root = m.bone("root", [0, 0, 0], [
        m.bone("body", [0, 10.5, 0], body + [
            m.bone("chestplate", [0, 17, -4.5], chest),
            m.bone("head", [0, 20.2, -1.2], head + [m.bone("crown", [0, 26.8, -1.5], crown)]),
            m.bone("pauldron_l", [-8.3, 21, 0], pauldrons[0]),
            m.bone("pauldron_r", [8.3, 21, 0], pauldrons[1]),
            m.bone("arm_l", [-9.3, 20, 0], arms[0] + [m.bone("hand_l", [-9.3, 13.5, 0], hands[0])]),
            m.bone("arm_r", [9.3, 20, 0], arms[1] + [
                m.bone("hand_r", [9.3, 13.5, 0], hands[1] + [m.bone("axe", [9.3, 6.3, -3.6], axe)])])]),
        m.bone("leg_l", [-3.6, 10.5, 0], legs[0] + [m.bone("foot_l", [-3.6, 2.6, 0], feet[0])]),
        m.bone("leg_r", [3.6, 10.5, 0], legs[1] + [m.bone("foot_r", [3.6, 2.6, 0], feet[1])])])

    z3 = [0, 0, 0]
    # 前屈みの基本姿勢。WSEE の repeat は同時に1本なので、各ループは単体で完全な構えを持つ。
    # 単発は基本姿勢から入り、基本姿勢へ戻る。
    S = {"body": [12, 0, 0], "head": [-10, 0, 0], "arm_l": [-6, 0, -4], "arm_r": [-4, 0, 4]}
    FLIP, SWEEP = [0, 0, -180], [0, 90, -180]
    m.anim("idle", 2.4, {
        "body": [(0, S["body"]), (1.2, [15, 0, 0]), (2.4, S["body"])],
        "head": [(0, S["head"]), (.6, [-12, -6, 0]), (1.2, [-13, 0, 0]), (1.8, [-11, 6, 0]), (2.4, S["head"])],
        "arm_l": [(0, S["arm_l"]), (1.2, [-9, 0, -6]), (2.4, S["arm_l"])],
        "arm_r": [(0, S["arm_r"]), (1.2, [-7, 0, 5]), (2.4, S["arm_r"])],
        "hand_r": [(0, z3), (1.2, [3, 0, 0]), (2.4, z3)]}, "loop")
    # 速い重歩。足首は接地中 -脚 で床に平らに、蹴り出しで踵が浮く。
    m.anim("walk", .7, {
        "leg_l": [(0, [35, 0, 0]), (.35, [-35, 0, 0]), (.7, [35, 0, 0])],
        "leg_r": [(0, [-35, 0, 0]), (.35, [35, 0, 0]), (.7, [-35, 0, 0])],
        "foot_l": [(0, [-10, 0, 0]), (.175, [15, 0, 0]), (.35, [30, 0, 0]), (.525, z3), (.7, [-10, 0, 0])],
        "foot_r": [(0, [30, 0, 0]), (.175, z3), (.35, [-10, 0, 0]), (.525, [15, 0, 0]), (.7, [30, 0, 0])],
        "root": [(0, [0, -.6, 0], "position"), (.175, [0, .4, 0], "position"), (.35, [0, -.6, 0], "position"),
                 (.525, [0, .4, 0], "position"), (.7, [0, -.6, 0], "position")],
        "body": [(0, [14, 4, -3]), (.35, [14, -4, 3]), (.7, [14, 4, -3])],
        "head": [(0, [-12, -4, 0]), (.175, [-10, 0, 0]), (.35, [-12, 4, 0]), (.525, [-10, 0, 0]), (.7, [-12, -4, 0])],
        "arm_l": [(0, [-35, 0, -6]), (.35, [15, 0, -6]), (.7, [-35, 0, -6])],
        "arm_r": [(0, [8, 0, 5]), (.35, [-22, 0, 5]), (.7, [8, 0, 5])],
        "hand_r": [(0, [-4, 0, 0]), (.35, [10, 0, 0]), (.7, [-4, 0, 0])]}, "loop")
    # 突進の構え。低く沈んで右足で床を掻き、斧は柄を返して後ろへ引きずる。
    m.anim("charge_windup", .8, {
        "root": [(0, z3, "position"), (.3, [0, -1.8, 0], "position"), (.45, [0, -1.4, 0], "position"), (.8, [0, -1.4, 0], "position")],
        "body": [(0, S["body"]), (.45, [34, 6, 0]), (.8, [36, 6, 0])],
        "head": [(0, S["head"]), (.45, [-14, -6, 0]), (.8, [-16, -6, 0])],
        "leg_l": [(0, z3), (.45, [-28, 0, 0]), (.8, [-30, 0, 0])],
        "leg_r": [(0, z3), (.45, [30, 0, 0]), (.8, [34, 0, 0])],
        "foot_l": [(0, z3), (.45, [20, 0, 0]), (.8, [24, 0, 0])],
        "foot_r": [(0, z3), (.45, [-10, 0, 0]), (.8, [-8, 0, 0])],
        "arm_l": [(0, S["arm_l"]), (.45, [30, 0, -12]), (.8, [36, 0, -14])],
        "arm_r": [(0, S["arm_r"]), (.45, [38, 0, 10]), (.8, [44, 0, 12])],
        "hand_r": [(0, z3), (.45, [10, 0, 0]), (.8, [12, 0, 0])],
        "axe": [(0, z3), (.35, FLIP), (.8, FLIP)]})
    # 全力の疾走。歩きより深く屈み、歩幅は 100 度、跳ぶように弾む。左腕が振れ、斧は後ろに流れる。
    m.anim("charge", .4, {
        "body": [(0, [36, 3, -2]), (.2, [36, -3, 2]), (.4, [36, 3, -2])],
        "head": [(0, [-18, -3, 0]), (.2, [-18, 3, 0]), (.4, [-18, -3, 0])],
        "leg_l": [(0, [50, 0, 0]), (.2, [-50, 0, 0]), (.4, [50, 0, 0])],
        "leg_r": [(0, [-50, 0, 0]), (.2, [50, 0, 0]), (.4, [-50, 0, 0])],
        "foot_l": [(0, [-20, 0, 0]), (.1, [20, 0, 0]), (.2, [40, 0, 0]), (.3, z3), (.4, [-20, 0, 0])],
        "foot_r": [(0, [40, 0, 0]), (.1, z3), (.2, [-20, 0, 0]), (.3, [20, 0, 0]), (.4, [40, 0, 0])],
        "root": [(0, [0, -1.2, 0], "position"), (.1, [0, .8, 0], "position"), (.2, [0, -1.2, 0], "position"),
                 (.3, [0, .8, 0], "position"), (.4, [0, -1.2, 0], "position")],
        "arm_l": [(0, [-55, 0, -10]), (.2, [25, 0, -10]), (.4, [-55, 0, -10])],
        "arm_r": [(0, [48, 0, 12]), (.2, [40, 0, 12]), (.4, [48, 0, 12])],
        "hand_r": [(0, [12, 0, 0]), (.4, [12, 0, 0])],
        "axe": [(0, FLIP), (.4, FLIP)]}, "loop")
    # 薙ぎの構え。胴を右へ捩り、右腕を水平に上げて yaw で右後ろへ引く（x で上げてから y で回すと水平のまま振れる）。
    # 斧は柄を腕の延長へ、刃の縁を振る向きへ。左腕は釣り合いに開く。
    m.anim("cleave_windup", .7, {
        "root": [(0, z3, "position"), (.45, [0, -.8, 0], "position"), (.7, [0, -1, 0], "position")],
        "body": [(0, S["body"]), (.45, [4, 32, -4]), (.7, [2, 35, -6])],
        "head": [(0, S["head"]), (.45, [-8, -28, 0]), (.7, [-8, -32, 0])],
        "arm_r": [(0, S["arm_r"]), (.45, [-80, 85, 0]), (.7, [-84, 95, 0])],
        "axe": [(0, z3), (.45, SWEEP), (.7, SWEEP)],
        "arm_l": [(0, S["arm_l"]), (.45, [-30, 0, -30]), (.7, [-35, 0, -35])],
        "leg_l": [(0, z3), (.45, [-10, 0, 0]), (.7, [-12, 0, 0])],
        "leg_r": [(0, z3), (.45, [12, 0, 0]), (.7, [15, 0, 0])],
        "foot_l": [(0, z3), (.45, [10, 0, 0]), (.7, [12, 0, 0])],
        "foot_r": [(0, z3), (.45, [-12, 0, 0]), (.7, [-15, 0, 0])]})
    # 薙ぎ。0.25s（5tick）で刃が正面を通る。振り抜いて左へ流れ、構えへ戻る。
    m.anim("cleave", .6, {
        "root": [(0, [0, -1, 0], "position"), (.25, [0, -1.6, 0], "position"), (.6, z3, "position")],
        "body": [(0, [2, 35, -6]), (.25, [14, 0, 0]), (.35, [16, -30, 5]), (.6, S["body"])],
        "head": [(0, [-8, -32, 0]), (.25, [-12, 0, 0]), (.35, [-10, 20, 0]), (.6, S["head"])],
        "arm_r": [(0, [-84, 95, 0]), (.25, [-86, 0, 0]), (.35, [-78, -40, 0]), (.6, S["arm_r"])],
        "axe": [(0, SWEEP), (.35, SWEEP), (.6, z3)],
        "arm_l": [(0, [-35, 0, -35]), (.25, [10, 0, -10]), (.6, S["arm_l"])],
        "leg_l": [(0, [-12, 0, 0]), (.25, [8, 0, 0]), (.6, z3)],
        "leg_r": [(0, [15, 0, 0]), (.25, [-5, 0, 0]), (.6, z3)],
        "foot_l": [(0, [12, 0, 0]), (.25, [-8, 0, 0]), (.6, z3)],
        "foot_r": [(0, [-15, 0, 0]), (.25, [5, 0, 0]), (.6, z3)]})
    # 叩きつけの構え。脚を後ろへ折って深く沈み（足は床に平ら）、両腕を頭上へ、斧は柄を返して高く。
    m.anim("slam_windup", .9, {
        "root": [(0, z3, "position"), (.5, [0, -2.6, 0], "position"), (.9, [0, -3, 0], "position")],
        "body": [(0, S["body"]), (.5, [28, 0, 0]), (.9, [32, 0, 0])],
        "head": [(0, S["head"]), (.5, [-30, 0, 0]), (.9, [-34, 0, 0])],
        "leg_l": [(0, z3), (.5, [50, 0, -4]), (.9, [55, 0, -4])],
        "leg_r": [(0, z3), (.5, [50, 0, 4]), (.9, [55, 0, 4])],
        "foot_l": [(0, z3), (.5, [-50, 0, 0]), (.9, [-55, 0, 0])],
        "foot_r": [(0, z3), (.5, [-50, 0, 0]), (.9, [-55, 0, 0])],
        "arm_r": [(0, S["arm_r"]), (.5, [-150, 0, 16]), (.9, [-170, 0, 20])],
        "arm_l": [(0, S["arm_l"]), (.5, [-130, 0, -20]), (.9, [-145, 0, -25])],
        "hand_r": [(0, z3), (.5, [-15, 0, 0]), (.9, [-20, 0, 0])],
        "axe": [(0, z3), (.4, FLIP), (.9, FLIP)]})
    # 空中。脚は後ろへ流れ、両腕と斧を高く掲げたまま落ちる。目は着地点へ。
    m.anim("leap", .6, {
        "body": [(0, [22, 0, 0]), (.3, [20, 0, 0]), (.6, [22, 0, 0])],
        "head": [(0, [4, 0, 0]), (.6, [4, 0, 0])],
        "leg_l": [(0, [45, 0, -6]), (.6, [45, 0, -6])],
        "leg_r": [(0, [45, 0, 6]), (.6, [45, 0, 6])],
        "foot_l": [(0, [40, 0, 0]), (.6, [40, 0, 0])],
        "foot_r": [(0, [40, 0, 0]), (.6, [40, 0, 0])],
        "arm_r": [(0, [-175, 0, 22]), (.3, [-180, 0, 22]), (.6, [-175, 0, 22])],
        "arm_l": [(0, [-150, 0, -26]), (.6, [-150, 0, -26])],
        "hand_r": [(0, [-25, 0, 0]), (.6, [-25, 0, 0])],
        "axe": [(0, FLIP), (.6, FLIP)]}, "loop")
    # 叩きつけ。0 で着弾済み——上腕は前へ水平、肘から前腕を折って柄を前下へ、刃の下半分が前方の床に埋まる。
    # 沈み込んでから立ち上がる。
    m.anim("slam", .6, {
        "root": [(0, [0, -3.2, 0], "position"), (.1, [0, -3.6, 0], "position"), (.6, z3, "position")],
        "body": [(0, [30, 0, 0]), (.1, [34, 0, 0]), (.6, S["body"])],
        "head": [(0, [-14, 0, 0]), (.1, [-18, 0, 0]), (.6, S["head"])],
        "leg_l": [(0, [-40, 0, -8]), (.1, [-44, 0, -8]), (.6, z3)],
        "leg_r": [(0, [-40, 0, 8]), (.1, [-44, 0, 8]), (.6, z3)],
        "foot_l": [(0, [40, 0, 0]), (.1, [44, 0, 0]), (.6, z3)],
        "foot_r": [(0, [40, 0, 0]), (.1, [44, 0, 0]), (.6, z3)],
        "arm_r": [(0, [-120, 0, 10]), (.1, [-116, 0, 10]), (.6, S["arm_r"])],
        "arm_l": [(0, [-110, 0, -30]), (.1, [-106, 0, -30]), (.6, S["arm_l"])],
        "hand_r": [(0, [70, 0, 0]), (.1, [73, 0, 0]), (.35, [25, 0, 0]), (.6, z3)],
        "hand_l": [(0, [50, 0, 0]), (.1, [52, 0, 0]), (.6, z3)],
        "axe": [(0, FLIP), (.3, FLIP), (.6, z3)]})
    # 咆哮。胸を開いて天を仰ぎ、斧と盾を左右へ掲げて震える。
    m.anim("roar", 1.6, {
        "root": [(0, z3, "position"), (.4, [0, .8, 0], "position"), (1.2, [0, .6, 0], "position"), (1.6, z3, "position")],
        "body": [(0, S["body"]), (.4, [-14, 0, 0]), (1.2, [-12, 0, 0]), (1.6, S["body"])],
        "head": [(0, S["head"]), (.4, [-42, 0, 0]), (.6, [-40, -8, 0]), (.8, [-42, 8, 0]), (1.0, [-40, -8, 0]), (1.2, [-38, 0, 0]), (1.6, S["head"])],
        "arm_r": [(0, S["arm_r"]), (.4, [-150, 0, -40]), (1.2, [-145, 0, -35]), (1.6, S["arm_r"])],
        "arm_l": [(0, S["arm_l"]), (.4, [-150, 0, 40]), (1.2, [-145, 0, 35]), (1.6, S["arm_l"])],
        "axe": [(0, z3), (.4, FLIP), (1.2, FLIP), (1.6, z3)]})
    # 壁への激突。突進の姿勢から弾かれて仰け反り、腕が泳ぎ、頭を振ってふらつく。
    m.anim("crash", 2.0, {
        "root": [(0, [0, -1.2, 0], "position"), (.12, [0, -.4, 1.5], "position"), (.5, [0, -1, 2], "position"),
                 (1.7, [0, -1, 2], "position"), (2, z3, "position")],
        "body": [(0, [36, 3, -2]), (.12, [-10, 0, 6]), (.45, [8, 0, -8]), (.8, [-4, 0, 6]), (1.2, [6, 0, -4]), (1.7, [10, 0, 0]), (2, S["body"])],
        "head": [(0, [-18, 0, 0]), (.12, [-40, 15, 0]), (.45, [-30, -20, 8]), (.8, [-36, 18, -6]), (1.2, [-28, -12, 4]), (1.7, [-14, 0, 0]), (2, S["head"])],
        "arm_r": [(0, [48, 0, 12]), (.12, [-70, 0, 45]), (.5, [-55, 0, 55]), (1.7, [-40, 0, 40]), (2, S["arm_r"])],
        "arm_l": [(0, [-55, 0, -10]), (.12, [-70, 0, -45]), (.5, [-55, 0, -55]), (1.7, [-40, 0, -40]), (2, S["arm_l"])],
        "hand_r": [(0, [12, 0, 0]), (.12, [-20, 0, 0]), (1.7, [-10, 0, 0]), (2, z3)],
        "axe": [(0, FLIP), (1.7, FLIP), (2, z3)],
        "leg_l": [(0, [50, 0, 0]), (.12, [-30, 0, -8]), (1.7, [-15, 0, -8]), (2, z3)],
        "leg_r": [(0, [-50, 0, 0]), (.12, [-20, 0, 8]), (1.7, [-10, 0, 8]), (2, z3)],
        "foot_l": [(0, [-20, 0, 0]), (.12, [30, 0, 0]), (1.7, [15, 0, 0]), (2, z3)],
        "foot_r": [(0, [40, 0, 0]), (.12, [20, 0, 0]), (1.7, [10, 0, 0]), (2, z3)]})
    # 灼けた鎧が砕ける。弾かれてから膝をつき、頭を垂れて長く震え、最後に立ち上がる。
    m.anim("shatter", 3.0, {
        "root": [(0, [0, -1.2, 0], "position"), (.15, [0, -.4, 1.2], "position"), (.55, [0, -4, 1.5], "position"),
                 (2.5, [0, -4, 1.5], "position"), (3, z3, "position")],
        "body": [(0, [36, 3, -2]), (.15, [-8, 0, 6]), (.55, [42, 0, 0]), (1.0, [46, 0, 5]), (1.6, [44, 0, -5]), (2.2, [46, 0, 4]), (2.5, [40, 0, 0]), (3, S["body"])],
        "head": [(0, [-18, 0, 0]), (.15, [-36, 12, 0]), (.55, [10, 0, 0]), (1.2, [14, -12, 0]), (1.9, [12, 12, 0]), (2.5, [-6, 0, 0]), (3, S["head"])],
        "leg_l": [(0, [50, 0, 0]), (.15, [-20, 0, -6]), (.55, [65, 0, -6]), (2.5, [65, 0, -6]), (3, z3)],
        "leg_r": [(0, [-50, 0, 0]), (.15, [-10, 0, 6]), (.55, [65, 0, 6]), (2.5, [65, 0, 6]), (3, z3)],
        "foot_l": [(0, [-20, 0, 0]), (.15, [20, 0, 0]), (.55, [-65, 0, 0]), (2.5, [-65, 0, 0]), (3, z3)],
        "foot_r": [(0, [40, 0, 0]), (.15, [10, 0, 0]), (.55, [-65, 0, 0]), (2.5, [-65, 0, 0]), (3, z3)],
        "arm_r": [(0, [48, 0, 12]), (.15, [-60, 0, 30]), (.55, [-82, 0, 15]), (2.5, [-80, 0, 15]), (3, S["arm_r"])],
        "arm_l": [(0, [-55, 0, -10]), (.15, [-60, 0, -30]), (.55, [-35, 0, -25]), (2.5, [-30, 0, -20]), (3, S["arm_l"])],
        "hand_r": [(0, [12, 0, 0]), (.55, z3), (2.5, z3), (3, z3)],
        "axe": [(0, FLIP), (2.5, FLIP), (3, z3)]})
    # 死。仰け反り、膝から落ち、前へ倒れて伏す。冠が傾き、頭は横を向く。
    m.anim("death", 2.8, {
        "root": [(0, z3), (.5, [-8, 0, 0]), (1.0, [-4, 0, 0]), (1.6, [78, 0, 0]), (1.75, [72, 0, 0]), (2.8, [78, 0, 0]),
                 (0, z3, "position"), (.5, [0, .4, 0], "position"), (1.0, [0, -4, 0], "position"),
                 (1.6, [0, 1.5, 0], "position"), (2.8, [0, 1.5, 0], "position")],
        "body": [(0, S["body"]), (.5, [-6, 0, 0]), (1.0, [30, 0, 0]), (1.6, [20, 0, 0]), (2.8, [20, 0, 0])],
        "head": [(0, S["head"]), (.5, [-35, 0, 0]), (1.0, [-10, 0, 0]), (1.6, [-30, 40, 0]), (2.8, [-30, 40, 0])],
        "leg_l": [(0, z3), (1.0, [60, 0, -6]), (1.6, [20, 0, -10]), (2.8, [20, 0, -10])],
        "leg_r": [(0, z3), (1.0, [60, 0, 6]), (1.6, [20, 0, 10]), (2.8, [20, 0, 10])],
        "foot_l": [(0, z3), (1.0, [-60, 0, 0]), (1.6, [-20, 0, 0]), (2.8, [-20, 0, 0])],
        "foot_r": [(0, z3), (1.0, [-60, 0, 0]), (1.6, [-20, 0, 0]), (2.8, [-20, 0, 0])],
        "arm_r": [(0, S["arm_r"]), (.5, [-30, 0, 30]), (1.0, [-40, 0, 20]), (1.6, [-80, 0, 45]), (2.8, [-80, 0, 45])],
        "arm_l": [(0, S["arm_l"]), (.5, [-30, 0, -30]), (1.0, [-40, 0, -20]), (1.6, [-80, 0, -45]), (2.8, [-80, 0, -45])],
        "hand_r": [(0, z3), (1.0, [-20, 0, 0]), (1.6, [-60, 0, 0]), (2.8, [-60, 0, 0])],
        "crown": [(0, z3), (1.3, [0, 0, 20]), (1.6, [0, 0, 55]), (2.8, [0, 0, 55])]})
    # ── 溜めのある技（読み合い）。windup で構えに入り、hold ループを溜めの長さだけ回し、本体で放つ。 ──
    # 名前と尺・当たりの時刻は Scala 側（PiglinLordFight の *Windup / *Hold / *Strike）との契約。
    # hold は windup 終端と同じ値から始めて同じ値に戻る単体で完全な構え。震えは 4Hz の小さな揺れ＋沈み込み。
    # 回転斬り: 薙ぎと同じ握りで斧を右後ろへ水平に構え、震えながら溜め、全身ごと左へ回りながら走る。
    # root の y を 0→-360 で回す（薙ぎと同じ向き＝右腕の刃の縁が先導する。+360 だと刃の背で殴る）。
    # 胴の捩りは hold のまま保つので、hold→spin の継ぎ目で腕が跳ばない。ループ端 -360≡0 は同じ向き。
    SPIN = {"body": [4, 40, -6], "head": [-8, -34, 0], "arm_r": [-84, 100, 0], "arm_l": [-45, 0, -45],
            "leg_l": [-12, 0, 0], "leg_r": [15, 0, 0], "foot_l": [12, 0, 0], "foot_r": [-15, 0, 0]}
    m.anim("spin_windup", .7, {
        "root": [(0, z3, "position"), (.45, [0, -1, 0], "position"), (.7, [0, -1, 0], "position")],
        "body": [(0, S["body"]), (.45, [6, 34, -4]), (.7, SPIN["body"])],
        "head": [(0, S["head"]), (.45, [-8, -30, 0]), (.7, SPIN["head"])],
        "arm_r": [(0, S["arm_r"]), (.45, [-80, 90, 0]), (.7, SPIN["arm_r"])],
        "axe": [(0, z3), (.4, SWEEP), (.7, SWEEP)],
        "arm_l": [(0, S["arm_l"]), (.45, [-40, 0, -40]), (.7, SPIN["arm_l"])],
        "leg_l": [(0, z3), (.45, [-10, 0, 0]), (.7, SPIN["leg_l"])],
        "leg_r": [(0, z3), (.45, [12, 0, 0]), (.7, SPIN["leg_r"])],
        "foot_l": [(0, z3), (.45, [10, 0, 0]), (.7, SPIN["foot_l"])],
        "foot_r": [(0, z3), (.45, [-12, 0, 0]), (.7, SPIN["foot_r"])]})
    m.anim("spin_hold", .5, {
        "root": [(0, [0, -1, 0], "position"), (.25, [0, -1.5, 0], "position"), (.5, [0, -1, 0], "position")],
        "body": [(0, SPIN["body"]), (.125, [6, 43, -7]), (.25, SPIN["body"]), (.375, [3, 38, -5]), (.5, SPIN["body"])],
        "head": [(0, SPIN["head"]), (.125, [-9, -36, 2]), (.25, SPIN["head"]), (.375, [-7, -32, -2]), (.5, SPIN["head"])],
        "arm_r": [(0, SPIN["arm_r"]), (.125, [-87, 103, 0]), (.25, SPIN["arm_r"]), (.375, [-82, 98, 0]), (.5, SPIN["arm_r"])],
        "axe": [(0, SWEEP), (.5, SWEEP)],
        "arm_l": [(0, SPIN["arm_l"]), (.25, [-48, 0, -48]), (.5, SPIN["arm_l"])],
        "leg_l": [(0, SPIN["leg_l"]), (.5, SPIN["leg_l"])], "leg_r": [(0, SPIN["leg_r"]), (.5, SPIN["leg_r"])],
        "foot_l": [(0, SPIN["foot_l"]), (.5, SPIN["foot_l"])], "foot_r": [(0, SPIN["foot_r"]), (.5, SPIN["foot_r"])]}, "loop")
    m.anim("spin", .5, {
        "root": [(0, z3), (.25, [0, -180, 0]), (.5, [0, -360, 0]),
                 (0, [0, -1, 0], "position"), (.125, [0, .3, 0], "position"), (.25, [0, -1, 0], "position"),
                 (.375, [0, .3, 0], "position"), (.5, [0, -1, 0], "position")],
        "body": [(0, SPIN["body"]), (.25, [8, 40, -8]), (.5, SPIN["body"])],
        "head": [(0, SPIN["head"]), (.5, SPIN["head"])],
        "arm_r": [(0, SPIN["arm_r"]), (.5, SPIN["arm_r"])],
        "axe": [(0, SWEEP), (.5, SWEEP)],
        "arm_l": [(0, [-60, 0, -60]), (.5, [-60, 0, -60])],
        "leg_l": [(0, [25, 0, 0]), (.25, [-25, 0, 0]), (.5, [25, 0, 0])],
        "leg_r": [(0, [-25, 0, 0]), (.25, [25, 0, 0]), (.5, [-25, 0, 0])],
        "foot_l": [(0, [-8, 0, 0]), (.125, [15, 0, 0]), (.25, [25, 0, 0]), (.375, z3), (.5, [-8, 0, 0])],
        "foot_r": [(0, [25, 0, 0]), (.125, z3), (.25, [-8, 0, 0]), (.375, [15, 0, 0]), (.5, [25, 0, 0])]}, "loop")
    # 突き上げ: 左へ半身に沈み、柄を返した斧を右後ろ下へ引く。溜めて、下から正面を通して振り上げる
    # （0.15s で刃が正面上へ抜ける＝当たり）。振り抜きで背を反らせて伸び上がり、宙に浮く。
    UPPER = {"root": [0, -2.2, 0], "body": [32, -20, 0], "head": [-24, 18, 0], "arm_r": [45, 0, 25],
             "arm_l": [-25, 0, -25], "leg_l": [-30, 0, 0], "leg_r": [30, 0, 0], "foot_l": [30, 0, 0], "foot_r": [-10, 0, 0]}
    m.anim("uppercut_windup", .6, {
        "root": [(0, z3, "position"), (.4, [0, -2, 0], "position"), (.6, UPPER["root"], "position")],
        "body": [(0, S["body"]), (.4, [28, -16, 0]), (.6, UPPER["body"])],
        "head": [(0, S["head"]), (.4, [-22, 14, 0]), (.6, UPPER["head"])],
        "arm_r": [(0, S["arm_r"]), (.4, [38, 0, 22]), (.6, UPPER["arm_r"])],
        "axe": [(0, z3), (.35, FLIP), (.6, FLIP)],
        "arm_l": [(0, S["arm_l"]), (.4, [-22, 0, -22]), (.6, UPPER["arm_l"])],
        "leg_l": [(0, z3), (.4, [-26, 0, 0]), (.6, UPPER["leg_l"])],
        "leg_r": [(0, z3), (.4, [26, 0, 0]), (.6, UPPER["leg_r"])],
        "foot_l": [(0, z3), (.4, [26, 0, 0]), (.6, UPPER["foot_l"])],
        "foot_r": [(0, z3), (.4, [-8, 0, 0]), (.6, UPPER["foot_r"])]})
    m.anim("uppercut_hold", .5, {
        "root": [(0, UPPER["root"], "position"), (.25, [0, -2.7, 0], "position"), (.5, UPPER["root"], "position")],
        "body": [(0, UPPER["body"]), (.125, [34, -21, 1]), (.25, UPPER["body"]), (.375, [31, -19, -1]), (.5, UPPER["body"])],
        "head": [(0, UPPER["head"]), (.125, [-26, 19, 0]), (.25, UPPER["head"]), (.375, [-23, 17, 0]), (.5, UPPER["head"])],
        "arm_r": [(0, UPPER["arm_r"]), (.125, [49, 0, 27]), (.25, UPPER["arm_r"]), (.375, [43, 0, 24]), (.5, UPPER["arm_r"])],
        "axe": [(0, FLIP), (.5, FLIP)],
        "arm_l": [(0, UPPER["arm_l"]), (.25, [-28, 0, -28]), (.5, UPPER["arm_l"])],
        "leg_l": [(0, UPPER["leg_l"]), (.5, UPPER["leg_l"])], "leg_r": [(0, UPPER["leg_r"]), (.5, UPPER["leg_r"])],
        "foot_l": [(0, UPPER["foot_l"]), (.5, UPPER["foot_l"])], "foot_r": [(0, UPPER["foot_r"]), (.5, UPPER["foot_r"])]}, "loop")
    m.anim("uppercut", .5, {
        "root": [(0, UPPER["root"], "position"), (.15, [0, 1.6, 0], "position"), (.25, [0, 2.2, 0], "position"), (.5, z3, "position")],
        "body": [(0, UPPER["body"]), (.15, [-14, 8, 0]), (.3, [-18, 10, 0]), (.5, S["body"])],
        "head": [(0, UPPER["head"]), (.15, [-34, 0, 0]), (.3, [-30, 0, 0]), (.5, S["head"])],
        "arm_r": [(0, UPPER["arm_r"]), (.15, [-135, 0, 10]), (.3, [-165, 0, 10]), (.5, S["arm_r"])],
        "axe": [(0, FLIP), (.3, FLIP), (.5, z3)],
        "arm_l": [(0, UPPER["arm_l"]), (.15, [30, 0, -15]), (.5, S["arm_l"])],
        "leg_l": [(0, UPPER["leg_l"]), (.15, [5, 0, 0]), (.5, z3)],
        "leg_r": [(0, UPPER["leg_r"]), (.15, [-5, 0, 0]), (.5, z3)],
        "foot_l": [(0, UPPER["foot_l"]), (.2, [20, 0, 0]), (.5, z3)],
        "foot_r": [(0, UPPER["foot_r"]), (.2, [20, 0, 0]), (.5, z3)]})
    # 足踏み: 右膝を高く上げ、柄を返した斧と盾を左右へ開いて左足に体重を乗せる。溜めて、踏み下ろす
    # （0.1s で着地＝当たり）。着地で沈み、腕が振り下ろされ、斧は休めの握りへ戻る。
    STOMP = {"root": [0, -.4, 0], "body": [-8, 0, 8], "head": [-16, 0, -4], "leg_r": [-75, 0, 8], "foot_r": [45, 0, 0],
             "leg_l": [4, 0, -4], "arm_r": [-50, 0, 40], "arm_l": [-50, 0, -40]}
    m.anim("stomp_windup", .5, {
        "root": [(0, z3, "position"), (.35, [0, -.4, 0], "position"), (.5, STOMP["root"], "position")],
        "body": [(0, S["body"]), (.35, [-6, 0, 7]), (.5, STOMP["body"])],
        "head": [(0, S["head"]), (.35, [-15, 0, -3]), (.5, STOMP["head"])],
        "leg_r": [(0, z3), (.35, [-66, 0, 8]), (.5, STOMP["leg_r"])],
        "foot_r": [(0, z3), (.35, [40, 0, 0]), (.5, STOMP["foot_r"])],
        "leg_l": [(0, z3), (.35, [4, 0, -4]), (.5, STOMP["leg_l"])],
        "arm_r": [(0, S["arm_r"]), (.35, [-45, 0, 36]), (.5, STOMP["arm_r"])],
        "axe": [(0, z3), (.3, FLIP), (.5, FLIP)],
        "arm_l": [(0, S["arm_l"]), (.35, [-45, 0, -36]), (.5, STOMP["arm_l"])]})
    m.anim("stomp_hold", .5, {
        "root": [(0, STOMP["root"], "position"), (.25, [0, -.8, 0], "position"), (.5, STOMP["root"], "position")],
        "body": [(0, STOMP["body"]), (.125, [-9, 0, 10]), (.25, STOMP["body"]), (.375, [-7, 0, 7]), (.5, STOMP["body"])],
        "head": [(0, STOMP["head"]), (.125, [-17, 2, -5]), (.25, STOMP["head"]), (.375, [-15, -2, -3]), (.5, STOMP["head"])],
        "leg_r": [(0, STOMP["leg_r"]), (.125, [-79, 0, 9]), (.25, STOMP["leg_r"]), (.375, [-72, 0, 7]), (.5, STOMP["leg_r"])],
        "foot_r": [(0, STOMP["foot_r"]), (.5, STOMP["foot_r"])],
        "leg_l": [(0, STOMP["leg_l"]), (.5, STOMP["leg_l"])],
        "arm_r": [(0, STOMP["arm_r"]), (.25, [-53, 0, 43]), (.5, STOMP["arm_r"])],
        "axe": [(0, FLIP), (.5, FLIP)],
        "arm_l": [(0, STOMP["arm_l"]), (.25, [-53, 0, -43]), (.5, STOMP["arm_l"])]}, "loop")
    m.anim("stomp", .4, {
        "root": [(0, STOMP["root"], "position"), (.1, [0, -1.8, 0], "position"), (.25, [0, -1, 0], "position"), (.4, z3, "position")],
        "body": [(0, STOMP["body"]), (.1, [26, 0, 0]), (.4, S["body"])],
        "head": [(0, STOMP["head"]), (.1, [-22, 0, 0]), (.4, S["head"])],
        "leg_r": [(0, STOMP["leg_r"]), (.1, z3), (.4, z3)],
        "foot_r": [(0, STOMP["foot_r"]), (.1, z3), (.4, z3)],
        "leg_l": [(0, STOMP["leg_l"]), (.1, [-6, 0, -4]), (.4, z3)],
        "arm_r": [(0, STOMP["arm_r"]), (.1, [10, 0, 20]), (.4, S["arm_r"])],
        "axe": [(0, FLIP), (.1, z3), (.4, z3)],
        "arm_l": [(0, STOMP["arm_l"]), (.1, [15, 0, -20]), (.4, S["arm_l"])]})
    m.write(out, root)


def disc(out, name, color, fan=False):
    m = Model(name, 64)
    size = m.size
    for y in range(size):
        for x in range(size):
            dx, dz = x + .5 - size / 2, y + .5 - size / 2
            radius = math.hypot(dx, dz)
            edge = 30 <= radius <= 32
            spokes = 8 <= radius <= 25 and (abs(dx) < 1 or abs(dz) < 1)
            angle = math.atan2(dx, -dz)
            if fan:
                half = math.radians(52)
                edge = (edge and abs(angle) <= half) or (abs(abs(angle) - half) < .035 and radius <= 32)
                spokes = False
            if edge or (not fan and spokes):
                tone = .82 if radius < 31 else 1
                m.pixels[y][x] = (*tuple(round(c * tone) for c in COLORS[color]), 255)
    cubes = []
    uv = [0, 0, size, size]
    m.cube("rim", [-RIM_UNITS, 0, -RIM_UNITS], [RIM_UNITS, .025, RIM_UNITS], "plate", cubes,
           face_uv={"up": uv, "down": uv})
    m.write(out, m.bone("circle", [0, 0, 0], cubes))


if __name__ == "__main__":
    out = Path(sys.argv[1] if len(sys.argv) > 1 else "bbmodel")
    out.mkdir(parents=True, exist_ok=True)
    lord(out)
    disc(out, "lord_mark", "danger")
    disc(out, "lord_fan", "danger", fan=True)
