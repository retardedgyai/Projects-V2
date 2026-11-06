"""The key art behind the dungeon gate's dialog: 桜牙獣 (enraged) over a dithered dusk with falling petals, in dots.

    python scripts/build_dungeon_keyart.py   # -> assets/core-ui/dungeons/sakura_keyart.png

Aion 2's dungeon screen fills the page with the dungeon's boss and darkens the left for the list and the text
(藍 2026-11-06 「アイオン2のインスタンスダンジョンと同じ構図で」). Here the boss is our own model (assets/bosses/
sakura_fang.bbmodel) rendered on the CPU (scripts/boss_preview/rbb.py) at one texel per dot, so the page stays pixel
art like the 冒険の手帳. One dot = 2 design px of the 464 x 189 dialog canvas (build_menu_dialog_assets.py).
"""
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path[:0] = [str(HERE / "boss_preview"), str(HERE)]
import numpy as np  # noqa: E402
from PIL import Image  # noqa: E402
import rbb  # noqa: E402
import build_boss_sakura_fang as fang  # noqa: E402

ROOT = HERE.parent
OUT = ROOT / "assets/core-ui/dungeons/sakura_keyart.png"
W, H = 232, 95                      # dots: the canvas at 2 design px a dot
BAYER = np.array([[0, 8, 2, 10], [12, 4, 14, 6], [3, 11, 1, 9], [15, 7, 13, 5]]) / 16.0
SKY_TOP, SKY_LOW = np.array([22, 16, 34]), np.array([92, 38, 62])
GLOW = np.array([236, 120, 150])
PETALS = [(255, 196, 214), (240, 150, 182), (255, 230, 238)]


def sky():
    """Dusk in three-ish bands, blended by ordered dither so it stays dots, with a rose glow behind the boss."""
    y, x = np.mgrid[0:H, 0:W]
    t = (y / (H - 1)) ** 1.3
    img = SKY_TOP * (1 - t[..., None]) + SKY_LOW * t[..., None]
    glow = np.clip(1 - np.hypot((x - 168) / 70.0, (y - 52) / 48.0), 0, 1) ** 1.4
    img = img * (1 - 0.55 * glow[..., None]) + GLOW * 0.55 * glow[..., None]
    step = 18.0                     # quantise to a small palette, dithered
    q = np.floor(img / step + BAYER[y % 4, x % 4][..., None]) * step
    return np.clip(q, 0, 255).astype(np.uint8)


def ground(img):
    """A dark rise of earth along the bottom, ragged, with a petal carpet near the boss."""
    rng = np.random.default_rng(7)
    edge = (H - 14 + 3 * np.sin(np.arange(W) / 9.0) + rng.integers(0, 2, W)).astype(int)
    for x in range(W):
        img[edge[x]:, x] = (18, 12, 20)
        img[edge[x], x] = (46, 26, 40)
        if 120 < x < 220 and rng.random() < 0.5:
            img[edge[x] + rng.integers(0, 3), x] = PETALS[rng.integers(0, 2)]
    return img


def boss():
    """The enraged 桜牙獣, three quarters towards the text, rendered at dot size; returns (rgb, mask)."""
    # fang.main() rebuilds the bbmodel (with fresh ids); only its pose is wanted here, so the file is put back.
    model = ROOT / "assets/bosses/sakura_fang.bbmodel"
    kept = model.read_bytes()
    b = fang.main()
    sc = rbb.Scene(model)
    model.write_bytes(kept)
    opened = {n: (np.array(e, float), np.zeros(3), np.ones(3)) for n, e in fang.open_pose(b).items()}
    size = (160, 92)
    rgb, depth = rbb.render(sc, opened, az=50, el=14, size=size, scale=0.86, cy=.97, with_depth=True)
    rgb = np.asarray(rgb.convert("RGB") if hasattr(rgb, "convert") else rgb)
    return rgb, depth > -1e8


def petals(img):
    rng = np.random.default_rng(11)
    for _ in range(70):
        x, y = rng.integers(0, W), rng.integers(0, H - 16)
        img[y, x] = PETALS[rng.integers(0, 3)]
        if rng.random() < 0.3 and x + 1 < W:
            img[y, x + 1] = PETALS[1]
    return img


def main():
    img = sky()
    rgb, mask = boss()
    ox, oy = W - rgb.shape[1], H - rgb.shape[0] - 2
    region = img[oy:oy + rgb.shape[0], ox:ox + rgb.shape[1]]
    region[mask] = rgb[mask]
    img = petals(ground(img))
    OUT.parent.mkdir(parents=True, exist_ok=True)
    Image.fromarray(img).save(OUT)
    Image.fromarray(img).resize((W * 4, H * 4), Image.NEAREST).save(ROOT / "build/dungeon_keyart_x4.png")
    print("DUNGEON_KEYART", OUT, img.shape)


if __name__ == "__main__":
    main()
