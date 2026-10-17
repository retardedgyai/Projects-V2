"""Pack the 魔術書 (grimoire) screen assets.

    python scripts/build_grimoire_assets.py

Reads assets/ui/grimoire/source/*.png (item icons and illustrations), draws the trunk map's dithered
nebula and the medallion halos, and writes
server-minestom/src/main/resources/polish05/grimoire.zip (private-namespace sprites, merged into
the UI pack by CoreUiPackServer) and polish05/grimoire.json (sprite chars and tile layouts).

It also builds crisp text pages for the 魔術書 only (families `gmv3r15` etc., same faces and advances as
the forge's `v3r15`): glyph alpha is snapped at 0.5, because the client draws every texel above its 0.1
text cutoff fully opaque and small anti-aliased kanji smear. Their widths go into
web-ui-lab/src/main/resources/polish05-font-metrics.json, which the UI renderer reads.

Images wider or taller than 256 px are split into tiles because larger font glyphs do not render.
Alpha is snapped to fully opaque or fully clear: a translucent glyph hides what is drawn after it.
The one exception is a single alpha-1 pixel that keeps a tile's advance at its full width.
"""
import io, json, math, random, sys, zipfile
from pathlib import Path
from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parent))
import build_forge_v3_assets as forge  # noqa: E402

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "assets/ui/grimoire/source"
OUT = ROOT / "server-minestom/src/main/resources/polish05"
NS = "projects_ui_polish05"
FONT = f"{NS}:grimoire"
TILE = 256
METRICS = ROOT / "web-ui-lab/src/main/resources/polish05-font-metrics.json"
TEXT_SOURCES = [ROOT / "server-minestom/src/main/kotlin/dev/projects/server/coreloop" / f for f in ("GrimoireFlow.kt", "GrimoireBook.kt")]
# The M PLUS 2 weights and sizes GrimoireFlow draws ("v3m15" -> crisp "gmv3m15").
CRISP = {"r": (12, 13, 14, 15), "m": (13, 14, 15, 16, 18), "b": (11, 15, 16, 17, 20, 24, 26, 44)}


def crisp_text(files: dict[str, bytes]) -> None:
    chars = {chr(c) for c in range(33, 127)}
    for source in TEXT_SOURCES:
        chars.update(c for c in source.read_text(encoding="utf-8") if ord(c) >= 127 and not c.isspace())
    chars = sorted(c for c in chars if not 0xE000 <= ord(c) <= 0xF8FF)
    metrics = json.loads(METRICS.read_text(encoding="utf-8"))
    for family in [f for f in metrics if f.startswith("gm")]:
        del metrics[family]
    for key, sizes in CRISP.items():
        for size in sizes:
            family = f"gmv3{key}{size}"
            forge.FAMILIES[family] = (*forge.SOURCES[key], size)
            forge.atlas(files, metrics, family, chars, cut=128)
    METRICS.write_text(json.dumps(metrics, ensure_ascii=False, sort_keys=True, separators=(",", ":")), encoding="utf-8")


# The trunk map's sky and the medallion halos, in opaque pixel steps: the client draws soft glow sprites
# as flat discs, so the glow is dithered instead. One texel is 4 px of GrimoireFlow's 1920 layout.
NEBULA_SIZE = (1356 // 4, 880 // 4)          # GrimoireFlow MAP_W x MAP_H
NEBULA_STEPS = [(14, 21, 40), (17, 30, 56), (21, 40, 72), (27, 52, 88)]   # over the map fill #0b0e1b
BAYER = [[0, 8, 2, 10], [12, 4, 14, 6], [3, 11, 1, 9], [15, 7, 13, 5]]
# Medallion radius 44 px = 11 texels; three 2-texel rings, the outer one a checker.
HALO_TINTS = {"gold": (200, 160, 70), "cyan": (90, 210, 235), "violet": (180, 154, 232), "red": (200, 90, 75)}
HALO_INNER, HALO_RINGS = 11, ((2, 0.28), (4, 0.18), (6, 0.10))


def value_noise(cells: int, size: tuple[int, int], seed: int) -> Image.Image:
    rnd = random.Random(seed)
    small = Image.new("L", (cells * size[0] // size[1] + 1, cells))
    small.putdata([rnd.randrange(256) for _ in range(small.width * small.height)])
    return small.resize(size, Image.BICUBIC)


def nebula() -> Image.Image:
    """Two soft clouds over the trunk's opened part, fading out to the right where it is still locked."""
    w, h = NEBULA_SIZE
    coarse, fine = value_noise(5, (w, h), 1), value_noise(14, (w, h), 2)
    image = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    for y in range(h):
        for x in range(w):
            u, v = x / w, y / h
            field = 0.55 * math.exp(-(((u - 0.30) / 0.22) ** 2 + ((v - 0.45) / 0.30) ** 2)) \
                + 0.35 * math.exp(-(((u - 0.50) / 0.16) ** 2 + ((v - 0.62) / 0.22) ** 2))
            field = field * (0.55 + 0.75 * coarse.getpixel((x, y)) / 255) + 0.12 * fine.getpixel((x, y)) / 255
            level = min(4, max(0, int(field * 4.4 + (BAYER[y % 4][x % 4] / 16 - 0.5) * 0.9)))
            if level:  # level 0 is the map fill itself: left clear
                image.putpixel((x, y), (*NEBULA_STEPS[level - 1], 255))
    return image


def halo(tint: tuple[int, int, int]) -> Image.Image:
    size = 2 * (HALO_INNER + HALO_RINGS[-1][0])
    under = NEBULA_STEPS[1]
    image = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    for y in range(size):
        for x in range(size):
            d = math.hypot(x + 0.5 - size / 2, y + 0.5 - size / 2) - HALO_INNER
            for i, (out, a) in enumerate(HALO_RINGS):
                if 0 < d <= out:
                    if i < len(HALO_RINGS) - 1 or (x + y) % 2 == 0:
                        image.putpixel((x, y), (*(round(u + (t - u) * a) for u, t in zip(under, tint)), 255))
                    break
    return image


def snap_alpha(image: Image.Image) -> Image.Image:
    image = image.convert("RGBA")
    alpha = image.getchannel("A").point(lambda a: 255 if a >= 128 else 0)
    image.putalpha(alpha)
    return image


def main() -> None:
    files: dict[str, bytes] = {}
    providers: list[dict] = []
    sprites: dict[str, dict] = {}
    layouts: dict[str, dict] = {}
    code = [0xE000]

    def add(name: str, image: Image.Image) -> None:
        assert image.width <= TILE and image.height <= TILE, (name, image.size)
        # Vanilla measures a bitmap glyph's advance up to its last non-clear column, and the UI centres
        # glyphs by that advance: a tile whose right edge is clear would shift right and leave a gap.
        alpha = image.getchannel("A")
        if not any(alpha.getpixel((image.width - 1, y)) for y in range(image.height)):
            image = image.copy()
            r, g, b, _ = image.getpixel((image.width - 1, 0))
            image.putpixel((image.width - 1, 0), (r, g, b, 1))
        char = chr(code[0]); code[0] += 1
        rel = f"grimoire/{name}.png"
        buffer = io.BytesIO(); image.save(buffer, "PNG", optimize=True)
        files[f"assets/{NS}/textures/{rel}"] = buffer.getvalue()
        providers.append({"type": "bitmap", "file": f"{NS}:{rel}", "ascent": image.height, "height": image.height, "chars": [char]})
        sprites[name] = {"char": char, "font": FONT, "width": image.width, "height": image.height}

    generated = [("trunk_nebula", nebula())] + [(f"halo_{k}", halo(t)) for k, t in HALO_TINTS.items()]
    for name, image in [(p.stem, snap_alpha(Image.open(p))) for p in sorted(SOURCE.glob("*.png"))] + generated:
        if image.width <= TILE and image.height <= TILE:
            add(name, image)
            continue
        parts = []
        for y in range(0, image.height, TILE):
            for x in range(0, image.width, TILE):
                part = f"{name}_{x}_{y}"
                add(part, image.crop((x, y, min(x + TILE, image.width), min(y + TILE, image.height))))
                parts.append({"name": part, "x": x, "y": y})
        layouts[name] = {"width": image.width, "height": image.height, "tiles": parts}
    crisp_text(files)
    files[f"assets/{NS}/font/grimoire.json"] = json.dumps({"providers": providers}, separators=(",", ":")).encode()

    OUT.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(OUT / "grimoire.zip", "w", compression=zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
        for name in sorted(files):
            archive.writestr(zipfile.ZipInfo(name, date_time=(1980, 1, 1, 0, 0, 0)), files[name])
    (OUT / "grimoire.json").write_text(json.dumps({"sprites": sprites, "layouts": layouts}, ensure_ascii=False), encoding="utf-8")
    print(f"GRIMOIRE_PACK_BUILT sprites={len(sprites)} layouts={len(layouts)} bytes={(OUT / 'grimoire.zip').stat().st_size}")


if __name__ == "__main__":
    main()
