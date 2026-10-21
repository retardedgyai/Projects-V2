"""Warrior skill icons, iron-mass concept (2026-10-17).

Hand-placed 16x16 pixel art in the Monumenta style the Creator chose: black ground, no frame, one glowing hue
per icon with a 6-step ramp, a dark same-hue tint around the subject. Each row below is placed by hand;
nothing is generated from formulas except the straight blade runs. The 32 px masters in assets/core-ui/skills
are exact 2x copies, so the HUD's 16 px reduction shows the original pixels.

Run: python3 scripts/build_warrior_iron_icons.py   (then update skill-art-manifest.json hashes)
"""
from pathlib import Path
from PIL import Image
import hashlib, json

ROOT = Path(__file__).resolve().parents[1]

# 共通の材質
BASE = {".": "08070c",
        "a": "161e2e", "b": "34465a", "c": "66848a", "d": "9cb6b4", "e": "eef4f2",   # 鋼（左上が明るい）
        "r": "241c20", "s": "3e3236", "t": "64545a"}                                 # 岩
FIRE = {"q": "1a0610", "p": "2e0c18", "1": "5a1418", "2": "9a2410", "3": "d0501a", "4": "f08a22", "5": "f8c050", "6": "fff6c8"}
ICE = {"q": "0a1020", "p": "13203a", "1": "1c3458", "2": "2c5a8e", "3": "4a8cc8", "4": "86c4ec", "5": "c8ecfa", "6": "ffffff"}

JIWARI = (FIRE, [
    "......edbca.....",
    "......edbca.r...",
    "....s.edbca.....",
    "......edbca.....",
    "..ts..edbca.tt..",
    "..s...edbca..s..",
    "......edbca4....",
    "...4..edbca.....",
    ".3...p54231p..3.",
    "...qp2366532pq..",
    "ssr1245665421rss",
    "r.qp24355342pq.r",
    ".qp35pqp5p.53pq.",
    ".q34p.q4p..p43q.",
    "q23p..p3p...p32q",
    "2q.....2q.....q2",
])

OSHINAGI = (ICE, [
    "................",
    "......p1221p..p.",
    "....p234444321.q",
    "...2345555554321",
    "..2356pqqqp65543",
    ".1346pq...qp6554",
    ".245pq.....qp655",
    "q24pq.......qp65",
    "13pq.........p66",
    "2p...........p66",
    "1q...........356",
    "q..........23566",
    ".1.........p2344",
    "q...........p123",
    "..p.............",
    "................",
])

EARTH = {"q": "140c08", "p": "26160c", "1": "4a2c14", "2": "7a4a1e", "3": "b0742e", "4": "d8a050", "5": "f0cc88", "6": "fff2d0"}
AMBER = {"q": "0e0804", "p": "2a1806", "1": "5a3408", "2": "9a6010", "3": "d09a1c", "4": "f2c234", "5": "ffe486", "6": "fffbe0"}
RED = {"q": "1c0410", "p": "34081a", "1": "641020", "2": "a8161e", "3": "e03a24", "4": "ff7a3c", "5": "ffc080", "6": "fff2dc"}

# 轟進：鉄塊の刃を前に突き出して突っ込む。後ろに土煙と速さの筋、切っ先に衝突の火花

# 地走り：手前（左下）から奥（右上）へ、太さを変えながら走る琥珀の亀裂

# 根張り：地に突き立てた鉄塊の刃。左から来た攻撃が刃で砕け、足元の地面が沈む

# 鉄槌：上から落ちてきた赤熱の大剣。着地で地平いっぱいの衝撃

def grid(cells, fill="."):
    g = [[fill for _ in range(16)] for _ in range(16)]
    for (x, y, c) in cells:
        if 0 <= x < 16 and 0 <= y < 16: g[y][x] = c
    return g

# 轟進：切っ先を前に突き出した鉄塊の楔。後ろへ流れる土煙と速さの筋、切っ先に火花
def goshin_rows():
    cells = []
    edges = {5: (4, 10, "eeedddc"), 6: (4, 12, "ddccccccb"), 7: (4, 14, "cccccccccb"), 8: (4, 14, "bbbbbbbbba"), 9: (4, 12, "baaaaaaaa"), 10: (4, 10, "aaaaaaa")}
    for y, (x0, x1, cols) in edges.items():
        for k, x in enumerate(range(x0, x0 + len(cols))): cells.append((x, y, cols[k]))
    for y, c in zip(range(4, 12), "cdecbbaa"): cells.append((3, y, c))             # 鍔
    cells += [(0, 7, "2"), (1, 7, "3"), (2, 7, "3"), (0, 8, "1"), (1, 8, "2"), (2, 8, "2")]  # 柄
    cells += [(15, 7, "6"), (15, 8, "5"), (15, 6, "3"), (15, 9, "3"), (14, 5, "2"), (14, 10, "2")]  # 火花
    for y, row in ((11, "2343321p"), (12, "34554321p"), (13, "1233221p"), (14, "p1q1p")):     # 後ろへ流れる土煙
        cells += [(x, y, c) for x, c in enumerate(row) if c != "."]
    for y, x0, row in ((2, 1, "p1221p"), (3, 7, "p1221"), (13, 10, "p11p"), (1, 9, "p1p")):  # 速さの筋
        cells += [(x0 + k, y, c) for k, c in enumerate(row)]
    return ["".join(r) for r in grid(cells)]

# 地走り：暗い地面を、手前（左下）から地平（右上）へジグザグに走る琥珀の亀裂
def jibashiri_rows():
    g = grid([], ".")
    for y in range(8, 16):
        for x in range(16): g[y][x] = "q"
    rows = {8: {11: "4", 10: "p", 12: "p"}, 9: {10: "5", 9: "p", 11: "2"},
            10: {11: "5", 12: "3", 10: "2", 13: "p", 9: "p"}, 11: {9: "6", 8: "3", 10: "4", 7: "p", 11: "p"},
            12: {8: "6", 7: "4", 9: "5", 6: "2", 10: "2", 5: "p", 11: "p", 4: "3", 3: "2", 2: "p"},
            13: {9: "6", 8: "4", 10: "5", 7: "2", 11: "2", 6: "p", 12: "3", 13: "2", 14: "p"},
            14: {6: "6", 7: "6", 5: "4", 8: "4", 4: "2", 9: "2", 3: "p", 10: "p"},
            15: {4: "6", 5: "6", 3: "5", 2: "3", 6: "4", 1: "2", 7: "2", 0: "p", 8: "p"}}
    for y, xs in rows.items():
        for x, c in xs.items(): g[y][x] = c
    for (x, y, c) in ((11, 7, "2"), (11, 6, "1"), (12, 5, "3"), (10, 3, "2"), (9, 6, "t"), (13, 6, "s"), (14, 4, "1"), (7, 9, "1")):
        g[y][x] = c
    return ["".join(r) for r in g]

# 根張り：地に突き立てた大剣。その前に鋼の光の壁が立ち、左からの攻撃を受け止める
def nebari_rows():
    cells = [(x, 0, "t") for x in (7, 8, 9)] + [(7, y, "s") for y in (1, 2)] + [(8, y, "t") for y in (1, 2)] + [(9, y, "s") for y in (1, 2)]
    cells += [(4 + k, 3, c) for k, c in enumerate("bcdddddcb")] + [(4 + k, 4, c) for k, c in enumerate("abbbbbbba")]
    for y in range(5, 13): cells += [(6 + k, y, c) for k, c in enumerate("edbca")]
    for y in range(5, 12): cells.append((5, y, "q")); cells.append((11, y, "q"))
    cells += [(4, 5, "4"), (3, 5, "2"), (3, 6, "5"), (2, 6, "3"), (3, 7, "6"), (2, 7, "4"), (1, 7, "2"), (3, 8, "6"), (2, 8, "4"), (1, 8, "2"),
              (3, 9, "5"), (2, 9, "3"), (4, 10, "4"), (3, 10, "2"), (4, 11, "2"), (0, 8, "5"), (0, 7, "3"), (0, 9, "1"), (1, 6, "1")]
    for y, row in ((12, "rsst..edbca.tssr"), (13, "rrsstt2bbb2ttssr"), (14, ".rr.ss.tt.ss.rr.")):
        cells += [(x, y, c) for x, c in enumerate(row) if c != "."]
    return ["".join(r) for r in grid(cells)]

# 鉄槌：右上から弧を描いて振り下ろされた赤熱の大剣。切っ先が地を打ち、炎が左右へ噴く
def tettsui_rows():
    cells = []
    for y in range(0, 10):
        x = y
        cols = "edca" if y < 3 else ("4321" if y < 5 else "6542")
        for k, c in enumerate(cols): cells.append((x - 1 + k, y, c))
    cells += [(9, 10, "6"), (10, 10, "6"), (8, 10, "5"), (11, 10, "5")]
    cells += [(6, 0, "1"), (9, 0, "1"), (11, 1, "2"), (12, 2, "2"), (13, 3, "3"), (13, 4, "3"), (13, 5, "4"), (12, 6, "4"),  # 振り下ろしの弧
              (10, 0, "p"), (12, 1, "p"), (14, 3, "p"), (14, 5, "p"), (12, 7, "3")]
    cells += [(13, 9, "4"), (14, 8, "3"), (15, 7, "2"), (12, 9, "5"), (6, 10, "4"), (5, 9, "3"), (4, 8, "2"), (7, 9, "p")]   # 噴く炎
    for x in range(1, 16): cells.append((x, 11, "5" if 7 <= x <= 12 else "3" if 4 <= x <= 14 else "2"))
    for x in range(0, 16): cells.append((x, 12, "2" if 6 <= x <= 13 else "p"))
    for y, row in ((13, "rr.p12222221p.rr"), (14, "rrs..p1111p..srr"), (15, "rss.....q......r")):
        cells += [(x, y, c) for x, c in enumerate(row) if c != "."]
    return ["".join(r) for r in grid(cells)]

GOSHIN = (EARTH, goshin_rows())
JIBASHIRI = (AMBER, jibashiri_rows())
NEBARI = (ICE, nebari_rows())
TETTSUI = (RED, tettsui_rows())

def sekinetsu_rows():
    g = [["." for _ in range(16)] for _ in range(16)]
    def put(x, y, c):
        if 0 <= x < 16 and 0 <= y < 16: g[y][x] = c
    for y in range(2, 13):          # 内側から光る斜めの刃（左上のふちが暗い赤、芯が白熱）
        x0 = 14 - y
        put(x0, y, "3"); put(x0 + 1, y, "6"); put(x0 + 2, y, "4"); put(x0 + 3, y, "p")
    put(14, 1, "5"); put(15, 0, "3"); put(15, 1, "p")
    for (x, y, c) in ((1, 13, "1"), (2, 13, "2"), (0, 14, "1"), (1, 14, "2"), (0, 15, "1"),   # 柄
                      (0, 11, "p"), (1, 12, "1"), (3, 14, "p"), (4, 15, "q")):
        put(x, y, c)
    for (x, y, c) in ((4, 9, "5"), (4, 8, "4"), (3, 7, "3"), (4, 6, "2"), (3, 5, "1"),         # 刃の背から立つ炎
                      (7, 6, "5"), (7, 5, "4"), (6, 4, "3"), (7, 3, "2"), (6, 2, "1"),
                      (10, 3, "5"), (10, 2, "4"), (9, 1, "3"), (10, 0, "2"),
                      (5, 8, "3"), (8, 5, "3"), (3, 8, "2"), (6, 5, "2")):
        put(x, y, c)
    for (x, y, c) in ((2, 3, "3"), (5, 1, "2"), (12, 7, "2"), (1, 6, "1"), (13, 9, "1"), (8, 0, "1")):  # 火の粉
        put(x, y, c)
    for y in range(4, 15):           # 刃の下側に広がる暗い熱
        x = 18 - y
        if 0 <= x < 16 and g[y][x] == ".": g[y][x] = "q"
    return ["".join(r) for r in g]

SEKINETSU = (FIRE, sekinetsu_rows())

def render(icon):
    pal, rows = icon
    colors = {**BASE, **pal}
    assert len(rows) == 16 and all(len(r) == 16 for r in rows), [len(r) for r in rows]
    im = Image.new("RGB", (16, 16))
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            h = colors[ch]; im.putpixel((x, y), tuple(int(h[i:i + 2], 16) for i in (0, 2, 4)))
    return im


ICONS = {"dash": GOSHIN, "slam": JIWARI, "whirl": OSHINAGI, "war_wound": JIBASHIRI,
         "war_guard": NEBARI, "war_ult": TETTSUI, "war_rampage": SEKINETSU}


def render(icon):
    pal, rows = icon
    colors = {**BASE, **pal}
    assert len(rows) == 16 and all(len(r) == 16 for r in rows)
    im = Image.new("RGBA", (16, 16))
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            h = colors[ch]; im.putpixel((x, y), tuple(int(h[i:i + 2], 16) for i in (0, 2, 4)) + (255,))
    return im


def main():
    small = ROOT / "assets/core-ui/skills/warrior-16"
    small.mkdir(parents=True, exist_ok=True)
    manifest_path = ROOT / "assets/core-ui/skill-art-manifest.json"
    manifest = json.loads(manifest_path.read_text())
    for name, icon in ICONS.items():
        art = render(icon)
        art.save(small / f"{name}.png")
        if name not in manifest["skills"]:
            continue  # The rampage icon waits for a HUD buff slot.
        master = ROOT / f"assets/core-ui/skills/{name}.png"
        art.resize((32, 32), Image.NEAREST).save(master)
        manifest["skills"][name] = {"source_sha256": hashlib.sha256((small / f"{name}.png").read_bytes()).hexdigest(),
            "master_sha256": hashlib.sha256(master.read_bytes()).hexdigest(), "source_size": [16, 16], "master_size": [32, 32],
            "preapproved": False, "source": f"assets/core-ui/skills/warrior-16/{name}.png", "authored_by": "scripts/build_warrior_iron_icons.py"}
    manifest_path.write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
