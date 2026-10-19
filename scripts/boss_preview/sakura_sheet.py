"""Look sheet for 桜牙獣: shells closed (rest) and opened (enraged), 3/4 and side. CPU preview, NOT Minecraft.

    python scripts/boss_preview/sakura_sheet.py   # -> build/boss-previews/sakura_fang_look.png
"""
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path[:0] = [str(HERE), str(HERE.parent)]
import numpy as np  # noqa: E402
from PIL import Image  # noqa: E402
import rbb  # noqa: E402
import build_boss_sakura_fang as fang  # noqa: E402

BG = ((118, 122, 140), (64, 64, 78))


def main():
    b = fang.main()
    sc = rbb.Scene(fang.ROOT / "assets/bosses/sakura_fang.bbmodel")
    opened = {n: (np.array(e, float), np.zeros(3), np.ones(3)) for n, e in fang.open_pose(b).items()}
    W, H = 640, 520
    tiles = []
    for label, pose in (("ふだん（花びらを閉じる）", None), ("怒り（花びらが開く）", opened)):
        row = Image.new("RGB", (W * 2, H))
        row.paste(rbb.render(sc, pose, az=52, el=16, size=(W, H), scale=3.6, cy=.86, center=(0, 0, -6), bg=BG), (0, 0))
        row.paste(rbb.render(sc, pose, az=90, el=4, size=(W, H), scale=3.4, cy=.86, center=(0, 0, -4), bg=BG), (W, 0))
        rbb.text(row, (16, 12), label)
        tiles.append(row)
    face = Image.new("RGB", (W * 2, H))
    for k, az in enumerate((40, 90, 0)):
        face.paste(rbb.render(sc, None, az=az, el=8, size=(W * 2 // 3, H), scale=6, cy=.6,
                              center=(0, 40, 38), bg=BG).crop((0, 0, W * 2 // 3, H)), (k * (W * 2 // 3), 0))
    rbb.text(face, (16, 12), "顔")
    tiles.append(face)
    sheet = Image.new("RGB", (W * 2, H * 3 + 26), (20, 18, 24))
    for i, t in enumerate(tiles):
        sheet.paste(t, (0, i * H))
    rbb.text(sheet, (12, H * 3 + 4), "桜牙獣（仮） bbmodel を CPU で描いた絵。Minecraft の画面ではない", font=rbb.SMALL)
    out = fang.ROOT / "build/boss-previews/sakura_fang_look.png"
    out.parent.mkdir(parents=True, exist_ok=True)
    sheet.save(out)
    print(out)


if __name__ == "__main__":
    main()
