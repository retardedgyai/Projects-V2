#!/usr/bin/env python3
"""15F 廃鐘鋳造所。面ごとのピクセルUV・接地原点・再現可能なbbmodel。

python3 bbmodel/gen_vesper.py [出力先=bbmodel]
番人は28unit × raw scale3 /16 = 5.25bl。1unit=3texel（16px/bl）。
丸みは段を付けた箱、鎖は2枚のドット絵。補間・PBR・ランタイム着色は使わない。
"""
import base64
import copy
import json
import math
from pathlib import Path
import struct
import sys
import uuid
import zlib

RIM_UNITS = 8
MATERIALS = {
    # 材質ごとに影・地・明の3色。緑青は銅の大きな島として描く。
    "copper": ("87503b", "ac6b4b", "c88e65"),
    "patina": ("446958", "5c8a70", "7aa184"),
    "iron": ("343b3c", "505958", "727a73"),
    "rim": ("60655b", "858a77", "a6aa90"),
    "wood": ("352920", "4d3b2a", "6a5036"),
    "soot": ("1b2221", "28302b", "3c4336"),
    "heart": ("9b652b", "dfae54", "ffe39a"),
}
MATERIALS = {k: tuple(tuple(bytes.fromhex(c)) for c in v) for k, v in MATERIALS.items()}
COLORS = {"danger": (230, 160, 84), "soul": (133, 205, 179)}


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
        return str(uuid.uuid5(uuid.NAMESPACE_URL, f"scorpius/vesper/{self.name}/{self.serial}"))

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
        # 同形の左右・脚はUVを共用する。部材の面積に応じた密度を保つ。
        seed = sum(ord(c) * (i + 1) for i, c in enumerate(material + side + motif))
        def paint(x, y):
            palette = MATERIALS[material]
            cluster = noise(x // 3, y // 3, seed)
            tone = 1
            if cluster % 13 == 0:
                tone = 0
            elif cluster % 19 == 0:
                tone = 2
            if material == "copper":
                # 継ぎ目や吊り側に緑青がたまり、裾は手擦れの銅色を残す。
                island = noise((x + 2) // 5, (y + 1) // 4, seed + 7) % 17
                if island < (7 if y < h // 3 else 3) and y < h - 3:
                    palette = MATERIALS["patina"]
                if motif == "shell" and h >= 16:
                    if y == h - 5: tone = 0
                    elif y == h - 4: tone = 2
                    if y == h - 9 and x % 9 in (3, 4): tone = 0
            if material == "wood":
                tone = 0 if (y + noise(x // 8, 0, seed) % 3) % 7 == 0 else 1
                if noise(x // 4, y // 2, seed) % 23 == 0: tone = 2
            # 1pxの欠けた縁。滑らかな勾配や全周の白い縁取りにはしない。
            if w >= 6 and h >= 6:
                if y == 0 and x % 7 != 2: tone = 2
                elif y == h - 1 or x == w - 1: tone = 0
                if motif in ("plate", "shell") and w >= 12 and h >= 12:
                    if x in (2, w - 3) and y in (2, h - 3):
                        return (*MATERIALS["rim"][2], 255)
                    if x in (3, w - 2) and y in (3, h - 2): tone = 0
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

    def chain(self, x, y, z, bucket):
        # バニラのchain同様、交差する2枚の切り抜き。環を箱で細分化しない。
        w, h = 7, 21
        def paint(px, py):
            row = py % 7
            on = (row in (0, 6) and 2 <= px <= 4) or (1 <= row <= 5 and px in (1, 5))
            if not on: return (0, 0, 0, 0)
            return (*MATERIALS["iron"][2 if px <= 2 or row == 0 else 1], 255)
        uv = self.patch(w, h, paint, "chain")
        self.cube("chain_front", [x - 7/6, y, z - .06], [x + 7/6, y + 7, z + .06], "iron", bucket,
                  face_uv={"north": uv, "south": uv})
        self.cube("chain_side", [x - .06, y, z - 7/6], [x + .06, y + 7, z + 7/6], "iron", bucket,
                  face_uv={"east": uv, "west": uv})

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


def keeper(out):
    m = Model("vesper")
    body, head, core = [], [], []
    arms, hands, chains, legs, feet, shutters = ([[], []] for _ in range(6))
    # 中空の銅鐘。前面は左右の鐘殻、背面と肩で空洞を支える。
    m.cube("bell_back", [-5, 10, 2], [5, 20, 4], "copper", body, "shell")
    m.cube("bell_shoulder", [-4, 19, -3], [4, 21, 3], "copper", body, "shell")
    m.cube("bell_lip_back", [-6, 8, 2], [6, 11, 5], "copper", body, "shell")
    for i, side in enumerate((-1, 1)):
        xa, xb = sorted((side * 4, side * 6))
        m.cube("bell_cheek", [xa, 10, -3], [xb, 19, 3], "copper", body, "shell")
        xa, xb = sorted((side * 4, side * 7))
        m.cube("bell_lip_side", [xa, 8, -4], [xb, 11, 3], "copper", body, "shell")
        xa, xb = sorted((side * .15, side * 4))
        m.cube("bell_valve", [xa, 10, -4], [xb, 19, -2.7], "copper", shutters[i], "shell")
        m.cube("valve_flared_lip", [xa, 8, -5], [xb, 11, -2.5], "copper", shutters[i], "shell")
    m.cube("cavity", [-3.9, 10.2, 1.8], [3.9, 18.9, 2], "soot", body)
    m.cube("living_clapper", [-.65, 10, -.4], [.65, 19, .9], "heart", core)
    m.cube("clapper_head", [-2.1, 10, -1.3], [2.1, 13, 1.8], "heart", core, "plate")
    # 鐘の木製吊り梁は肩を貫く。工房の什器がそのまま体になった構造。
    m.cube("timber_yoke", [-11, 20, -2], [11, 23, 3], "wood", body)
    for x in (-7.5, 7.5):
        m.cube("yoke_band", [x - .65, 19.8, -2.3], [x + .65, 23.2, 3.3], "iron", body, "plate")
    m.cube("head", [-3, 23, -2.5], [3, 27, 2.5], "patina", head, "plate")
    m.cube("brow", [-3.5, 25, -3.2], [3.5, 26, -2.2], "copper", head)
    m.cube("visor_shadow", [-2.7, 23.8, -2.7], [2.7, 25, -2.5], "soot", head)
    m.cube("nose", [-.7, 22.5, -3.7], [.7, 25, -2.5], "iron", head)
    for x in (-1.6, 1.6):
        m.cube("eye", [x - .5, 24.1, -2.8], [x + .5, 24.5, -2.65], "heart", head)
    m.cube("crown_socket", [-1.5, 27, -1.2], [1.5, 28, 1.2], "iron", head)
    for i, side in enumerate((-1, 1)):
        x = side * 3.5
        m.cube("leg", [x - 1.4, 3.8, -1.5], [x + 1.4, 9, 1.5], "iron", legs[i], "plate")
        m.cube("boot", [x - 2.1, 0, -3.7], [x + 2.1, 4, 2.3], "iron", feet[i], "plate")
        m.cube("boot_copper_toe", [x - 2.1, .3, -4], [x + 2.1, 1.3, -3.65], "copper", feet[i])
        x = side * 9
        m.cube("upper_arm", [x - 1.5, 13, -1.5], [x + 1.5, 21, 1.5], "iron", arms[i], "plate")
        m.cube("shoulder_cap", [x - 2, 19, -2.5], [x + 2, 22, 3.5], "copper", arms[i], "plate")
        m.cube("forearm", [x - 1.7, 7, -1.7], [x + 1.7, 14, 1.7], "wood", hands[i])
        # 右の鋳鉄の重りと左の吊り鎖で、遠目でも腕の役目が違って見える。
        r = 2.7 if side == 1 else 2.0
        m.cube("counterweight" if side == 1 else "chain_gauntlet", [x-r, 5, -r], [x+r, 9, r], "iron", hands[i], "plate")
        m.cube("wrist_band", [x-r-.15, 7.9, -r-.15], [x+r+.15, 9.15, r+.15], "copper", hands[i])
        m.chain(x, .6, -2.0, chains[i])
    root = m.bone("root", [0, 0, 0], [
        m.bone("body", [0, 9, 0], body + [
            m.bone("head", [0, 23, 0], head), m.bone("core", [0, 19, 0], core),
            m.bone("shutter_l", [-4, 18, -2.7], shutters[0]),
            m.bone("shutter_r", [4, 18, -2.7], shutters[1]),
            *[m.bone("arm_" + side, [x, 21, 0], arms[i] + [
                m.bone("hand_" + side, [x, 13, 0], hands[i] + [
                    m.bone("chain_" + side, [x, 7.6, -2], chains[i])])])
              for i, (side, x) in enumerate((("l", -9), ("r", 9)))]]),
        m.bone("leg_l", [-3.5, 9, 0], legs[0] + [m.bone("foot_l", [-3.5, 4, 0], feet[0])]),
        m.bone("leg_r", [3.5, 9, 0], legs[1] + [m.bone("foot_r", [3.5, 4, 0], feet[1])])])
    zero = [0, 0, 0]
    m.anim("idle", 3, {"body": [(0, zero), (1.5, [1.5, 0, 0]), (3, zero)],
                       "core": [(0, [0, 0, -4]), (1.5, [0, 0, 4]), (3, [0, 0, -4])],
                       "chain_l": [(0, [5, 0, 0]), (1.5, [-5, 0, 0]), (3, [5, 0, 0])],
                       "chain_r": [(0, [-4, 0, 0]), (1.5, [4, 0, 0]), (3, [-4, 0, 0])]}, "loop")
    m.anim("unbound", 1, {"shutter_l": [(0, [0, 65, 0])], "shutter_r": [(0, [0, -65, 0])]}, "loop")
    m.anim("walk", 1.2, {
        "leg_l": [(0, [20, 0, 0]), (.5, [-20, 0, 0]), (.6, [-20, 0, 0]), (1.1, [20, 0, 0]), (1.2, [20, 0, 0])],
        "leg_r": [(0, [-20, 0, 0]), (.5, [20, 0, 0]), (.6, [20, 0, 0]), (1.1, [-20, 0, 0]), (1.2, [-20, 0, 0])],
        "body": [(0, [0, 0, -2]), (.5, [0, 0, 2]), (.6, [0, 0, 2]), (1.1, [0, 0, -2]), (1.2, [0, 0, -2])],
        "arm_l": [(0, [-12, 0, 0]), (.6, [12, 0, 0]), (1.2, [-12, 0, 0])],
        "arm_r": [(0, [12, 0, 0]), (.6, [-12, 0, 0]), (1.2, [12, 0, 0])]}, "loop")
    hoist = {"body": [(0, zero), (.8, [-8, 0, 0]), (1.4, [-8, 0, 0])],
             "head": [(0, zero), (.8, [-18, 0, 0]), (1.4, [-18, 0, 0])]}
    for side, sign in (("l", -1), ("r", 1)):
        hoist["arm_" + side] = [(0, zero), (.3, [-20, 0, sign*8]), (1.05, [-145, 0, sign*8]), (1.4, [-145, 0, sign*8])]
        hoist["hand_" + side] = [(0, zero), (.8, [-28, 0, 0]), (1.4, [-28, 0, 0])]
        hoist["chain_" + side] = [(0, zero), (1.1, [100, 0, 0]), (1.4, [90, 0, 0])]
    m.anim("hoist", 1.4, hoist)
    m.anim("suspend", 1, {"body": [(0, [-8, 0, 0])], "head": [(0, [-18, 0, 0])],
                         "arm_l": [(0, [-145, 0, -8])], "arm_r": [(0, [-145, 0, 8])],
                         "hand_l": [(0, [-28, 0, 0])], "hand_r": [(0, [-28, 0, 0])],
                         "chain_l": [(0, [90, 0, 0])], "chain_r": [(0, [90, 0, 0])]}, "loop")
    drop = {"body": [(0, [-8, 0, 0]), (.4, [20, 0, 0]), (.6, [12, 0, 0]), (1.1, zero)]}
    for side, sign in (("l", -1), ("r", 1)):
        drop["arm_" + side] = [(0, [-145, 0, sign*8]), (.4, [-45, 0, 0]), (.6, [-15, 0, 0]), (1.1, zero)]
        drop["hand_" + side] = [(0, [-28, 0, 0]), (.4, zero), (1.1, zero)]
        drop["chain_" + side] = [(0, [90, 0, 0]), (.4, [-50, 0, 0]), (.7, [25, 0, 0]), (1.1, zero)]
    m.anim("drop", 1.1, drop)
    m.anim("sweep_windup", 1.2, {"body": [(0, zero), (.8, [0, -28, 0]), (1.2, [0, -28, 0])],
                                  "arm_r": [(0, zero), (.8, [-45, -40, 35]), (1.2, [-45, -40, 35])],
                                  "hand_r": [(0, zero), (.8, [-60, 0, 0]), (1.2, [-60, 0, 0])]})
    m.anim("return_windup", 1, {"body": [(0, zero), (.7, [0, 28, 0]), (1, [0, 28, 0])],
                               "arm_l": [(0, zero), (.7, [-45, 40, -35]), (1, [-45, 40, -35])],
                               "hand_l": [(0, zero), (.7, [-60, 0, 0]), (1, [-60, 0, 0])]})
    for reverse in (False, True):
        s = -1 if reverse else 1
        arm = "l" if reverse else "r"
        m.anim("sweep_return" if reverse else "sweep", .9, {
            "body": [(0, [0, -28*s, 0]), (.3, [0, 35*s, 0]), (.48, [0, 39*s, 0]), (.9, zero)],
            "arm_" + arm: [(0, [-45, -40*s, 35*s]), (.3, [-80, 65*s, 20*s]), (.48, [-80, 70*s, 20*s]), (.9, zero)],
            "hand_" + arm: [(0, [-60, 0, 0]), (.3, zero), (.9, zero)],
            "chain_" + arm: [(0, zero), (.3, [-40, 0, -55*s]), (.6, [25, 0, 15*s]), (.9, zero)]})
    m.anim("toll", 1.2, {"body": [(0, zero), (.9, [-14, 0, 0]), (1.2, [15, 0, 0])],
                          "arm_l": [(0, zero), (.9, [-65, 0, -15]), (1.2, [-12, 0, 0])],
                          "arm_r": [(0, zero), (.9, [-65, 0, 15]), (1.2, [-12, 0, 0])],
                          "core": [(0, zero), (.9, [-35, 0, 0]), (1.2, [25, 0, 0])]})
    m.anim("rupture", 2.2, {"body": [(0, zero), (.6, [-16, 0, 0]), (1.2, [10, 0, 0]), (2.2, zero)],
                             "shutter_l": [(0, zero), (.9, [0, 8, 0]), (1.2, [0, 85, 0]), (2.2, [0, 65, 0])],
                             "shutter_r": [(0, zero), (.9, [0, -8, 0]), (1.2, [0, -85, 0]), (2.2, [0, -65, 0])]})
    crouch = -5 * (1 - math.cos(math.radians(55)))
    m.anim("kneel", 5, {"root": [(0, zero, "position"), (.3, [0, crouch, 0], "position"), (4.4, [0, crouch, 0], "position"), (5, zero, "position")],
                         "body": [(0, zero), (.3, [24, 0, 0]), (.5, [18, 0, 0]), (4.4, [18, 0, 0]), (5, zero)],
                         "shutter_l": [(0, zero), (.3, [0, 110, 0]), (4.4, [0, 110, 0]), (5, zero)],
                         "shutter_r": [(0, zero), (.3, [0, -110, 0]), (4.4, [0, -110, 0]), (5, zero)],
                         "leg_l": [(0, zero), (.3, [55, 0, 0]), (4.4, [55, 0, 0]), (5, zero)],
                         "leg_r": [(0, zero), (.3, [55, 0, 0]), (4.4, [55, 0, 0]), (5, zero)],
                         "foot_l": [(0, zero), (.3, [-55, 0, 0]), (4.4, [-55, 0, 0]), (5, zero)],
                         "foot_r": [(0, zero), (.3, [-55, 0, 0]), (4.4, [-55, 0, 0]), (5, zero)],
                         "chain_l": [(0, zero), (.3, [35, 0, 0]), (4.4, [35, 0, 0]), (5, zero)],
                         "chain_r": [(0, zero), (.3, [35, 0, 0]), (4.4, [35, 0, 0]), (5, zero)]})
    m.anim("finale", 4.8, {"body": [(0, zero), (.6, [-10, 0, 0]), (3.8, [-10, 0, 0]), (4.5, [18, 0, 0]), (4.8, zero)],
                           "arm_l": [(0, zero), (.6, [-120, 0, -25]), (3.8, [-120, 0, -25]), (4.5, [-20, 0, 0]), (4.8, zero)],
                           "arm_r": [(0, zero), (.6, [-120, 0, 25]), (3.8, [-120, 0, 25]), (4.5, [-20, 0, 0]), (4.8, zero)],
                           "core": [(0, zero), (.6, [-28, 0, 0]), (1.4, [28, 0, 0]), (2.2, [-28, 0, 0]), (3, [28, 0, 0]), (4.8, zero)]})
    m.anim("death", 2.8, {"root": [(0, zero), (.35, [0, 0, 4]), (.9, [15, 0, 4]), (1.45, [82, 0, 8]), (1.6, [72, 0, 8]), (2.8, [82, 0, 8])],
                           "shutter_l": [(0, zero), (1.45, [0, 130, 0]), (2.8, [0, 130, 0])],
                           "shutter_r": [(0, zero), (1.45, [0, -130, 0]), (2.8, [0, -130, 0])],
                           "arm_l": [(0, zero), (1.45, [0, 0, -25]), (2.8, [0, 0, -25])],
                           "arm_r": [(0, zero), (1.45, [0, 0, 25]), (2.8, [0, 0, 25])]})
    # WSEEのrepeatは1本だけ。開いた胸を別ループの重ね合わせに任せない。
    opened = next(a for a in m.animations if a["name"] == "unbound")
    idle = next(a for a in m.animations if a["name"] == "idle")
    opened["length"] = idle["length"]
    opened["animators"].update(copy.deepcopy(idle["animators"]))
    for anim in list(m.animations):
        if anim["name"] in ("idle", "unbound", "rupture"):
            continue
        variant = copy.deepcopy(anim)
        variant["name"] += "_unbound"
        variant["uuid"] = m.uid()
        for side in ("l", "r"):
            bone = m.bones["shutter_" + side]
            if bone in variant["animators"]:
                for key in variant["animators"][bone]["keyframes"]:
                    if key["data_points"][0]["y"] == 0:
                        key["data_points"][0]["y"] = 65 if side == "l" else -65
            else:
                variant["animators"][bone] = copy.deepcopy(opened["animators"][bone])
        m.animations.append(variant)
    for anim in m.animations:
        for track in anim["animators"].values():
            for key in track["keyframes"]:
                key["uuid"] = m.uid()
    m.write(out, root)


def bell(out):
    m = Model("vesper_bell", 256, texels=4)
    cubes = []
    for y0, y1, r in ((0, 2, 6), (2, 7, 5), (7, 9, 4)):
        for frm, to in (([-r, y0, -r], [r, y1, -r + 1]), ([-r, y0, r - 1], [r, y1, r]),
                        ([-r, y0, -r + 1], [-r + 1, y1, r - 1]), ([r - 1, y0, -r + 1], [r, y1, r - 1])):
            m.cube("cast_bell", frm, to, "copper", cubes, "shell")
    m.cube("cap", [-4, 9, -4], [4, 10, 4], "copper", cubes, "shell")
    m.cube("clapper", [-.75, .5, -.75], [.75, 9, .75], "iron", cubes)
    m.cube("clapper_end", [-1.5, .5, -1.5], [1.5, 2, 1.5], "rim", cubes)
    m.cube("hanger", [-1, 10, -1], [1, 12, 1], "iron", cubes)
    root = m.bone("bell", [0, 12, 0], cubes)
    m.anim("ring", 2.4, {"bell": [(0, [0, 0, -12]), (.4, [0, 0, 10]), (.9, [0, 0, -7]), (1.4, [0, 0, 4]), (2, [0, 0, -2]), (2.4, [0, 0, 0])]})
    m.write(out, root)


def disc(out, name, color, wave=False, fan=False):
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
                edge = (edge and abs(angle) <= math.pi/3) or (abs(abs(angle)-math.pi/3) < .035 and radius <= 32)
                spokes = False
            if edge or (not wave and spokes):
                # 段を持ったドットの縁。危険域に半透明の面を重ねて床を隠さない。
                tone = .82 if radius < 31 else 1
                m.pixels[y][x] = (*tuple(round(c*tone) for c in COLORS[color]), 255)
    cubes = []
    uv = [0, 0, size, size]
    m.cube("rim", [-RIM_UNITS, 0, -RIM_UNITS], [RIM_UNITS, .025, RIM_UNITS], "iron", cubes,
           face_uv={"up": uv, "down": uv})
    m.write(out, m.bone("circle", [0, 0, 0], cubes))


if __name__ == "__main__":
    out = Path(sys.argv[1] if len(sys.argv) > 1 else "bbmodel")
    out.mkdir(parents=True, exist_ok=True)
    keeper(out)
    bell(out)
    disc(out, "vesper_mark", "danger")
    disc(out, "vesper_wave", "soul", wave=True)
    disc(out, "vesper_toll", "danger", wave=True)
    disc(out, "vesper_sweep", "danger", fan=True)
