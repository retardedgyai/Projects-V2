"""Import authored raster sprites with pixel-grid sizing and slot-safe composition.

This is not an artwork generator. No primitive-drawn replacement or old-icon fallback exists.
The unmodified full-size image_gen outputs are kept in .tools/skill-art-generation locally.
The committed 32px PNGs are the authoritative pixel masters for the resource-pack build.
"""
from pathlib import Path
import hashlib
import json
from PIL import Image
from build_core_hud_assets import ROOT, SKILLS, CLASSES

SOURCE = ROOT / "assets/core-ui"
RAW = ROOT / ".tools/skill-art-generation"
APPROVED = {"dash": "slash", "firebolt": "flame", "heal_shield": "guard"}


def fit_frame(image):
    """Eight-slice composition preserves complete corner/rail art in the 4px safe band."""
    # Remove only low-alpha export fringe before extracting the eight authored pieces.
    bounds = image.getchannel("A").point(lambda a: 255 if a >= 128 else 0).getbbox()
    if bounds is None:
        raise ValueError("Empty authored class frame")
    image = image.crop(bounds)
    w, h = image.size
    sx, sy = round(w * .22), round(h * .22)
    xs, ys = (0, sx, w-sx, w), (0, sy, h-sy, h)
    ds = (0, 4, 28, 32)
    frame = Image.new("RGBA", (32, 32))
    for row in range(3):
        for col in range(3):
            if row == col == 1:
                continue
            piece = image.crop((xs[col], ys[row], xs[col+1], ys[row+1]))
            ink = piece.getchannel("A").point(lambda a: 255 if a >= 128 else 0).getbbox()
            if ink is None:
                raise ValueError("Authored class frame has an empty corner or rail")
            piece = piece.crop(ink)
            # Area sampling retains thin metal/vine rails which a single-point sample skips.
            # Re-binarize alpha on the final pixel grid; no blurry edge enters Minecraft.
            piece = piece.resize((ds[col+1]-ds[col], ds[row+1]-ds[row]), Image.Resampling.BOX)
            piece.putalpha(piece.getchannel("A").point(lambda a: 255 if a >= 48 else 0))
            frame.alpha_composite(piece, (ds[col], ds[row]))
    return frame


def prepare():
    manifest = {"tool": "built-in image_gen", "approved_direction": "2026-09-08-v3",
                "prompts": "assets/core-ui/skill-art-prompts.json", "skills": {}, "frames": {}}
    # Validate all sources first: an incomplete generation must not partially replace the live set.
    sources = {name: (SOURCE / f"proposals/2026-09-08-v3/{APPROVED[name]}-source.png"
                     if name in APPROVED else RAW / f"skills/{name}.png") for name in SKILLS}
    sources.update({f"frame:{job}": SOURCE / f"skill-frames/source/{job}.png" for job in CLASSES})
    for name, path in sources.items():
        if not path.is_file():
            raise FileNotFoundError(f"Missing authored source for {name}: {path}")
        with Image.open(path) as image:
            image.verify()
    for name, path in sources.items():
        is_frame = name.startswith("frame:")
        key = name.removeprefix("frame:")
        with Image.open(path) as image:
            source_size = list(image.size)
            master = fit_frame(image.convert("RGBA")) if is_frame else image.convert("RGBA").resize((32, 32), Image.Resampling.NEAREST)
        if is_frame:
            # Guaranteed transparent icon well. This is a layout clip, not synthesized artwork.
            master.paste((0, 0, 0, 0), (4, 4, 28, 28))
            target = SOURCE / f"skill-frames/{key}.png"
        else:
            target = SOURCE / f"skills/{key}.png"
        target.parent.mkdir(parents=True, exist_ok=True)
        master.save(target)
        manifest["frames" if is_frame else "skills"][key] = {
            "source_sha256": hashlib.sha256(path.read_bytes()).hexdigest(),
            "master_sha256": hashlib.sha256(target.read_bytes()).hexdigest(),
            "source_size": source_size, "master_size": [32, 32],
            "preapproved": key in APPROVED if not is_frame else False,
        }
    (SOURCE / "skill-art-manifest.json").write_text(json.dumps(manifest, indent=2)+"\n", encoding="utf-8")
    print(f"Imported {len(SKILLS)} authored skill masters and {len(CLASSES)} class frames")


if __name__ == "__main__":
    prepare()
