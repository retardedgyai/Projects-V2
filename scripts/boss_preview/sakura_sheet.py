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
    sheet = Image.new("RGB", (W * 2, H * 2 + 26), (20, 18, 24))
    for i, t in enumerate(tiles):
        sheet.paste(t, (0, i * H))
    rbb.text(sheet, (12, H * 2 + 4), "桜牙獣（仮） bbmodel を CPU で描いた絵。Minecraft の画面ではない", font=rbb.SMALL)
    out = fang.ROOT / "build/boss-previews/sakura_fang_look.png"
    out.parent.mkdir(parents=True, exist_ok=True)
    sheet.save(out)
    print(out)


if __name__ == "__main__":
    main()
