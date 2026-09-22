"""Fixed camera crop of actual bbmodel bone poses. Not a game/interpolation capture."""
import importlib.util
from pathlib import Path
import numpy as np
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location("renderer", ROOT / "vendor/scorpius/bbmodel/preview_bbmodel.py")
renderer = importlib.util.module_from_spec(spec)
spec.loader.exec_module(renderer)


def main():
    data, elements, atlas, animations = renderer.load(ROOT / "model-lab/models/ice_fang.bbmodel")
    frames = []
    for time in np.linspace(0, 1.5, 46):
        panel = renderer.render(data, elements, atlas, animations["erupt"], float(time), "front", 6,
                                (384, 270), (-32, -42))
        draw = ImageDraw.Draw(panel)
        # Flat laboratory floor occludes below-ground geometry in this frontal projection.
        draw.rectangle((0, 253, 384, 270), fill=(57, 64, 74))
        frame = Image.new("RGB", (576, 447), (21, 26, 34))
        frame.paste(panel.resize((576, 405), Image.Resampling.NEAREST), (0, 42))
        draw = ImageDraw.Draw(frame)
        draw.text((12, 7), "ICE FANG / native bone animation / %.2fs" % time, fill=(210, 239, 240))
        draw.text((12, 23), "CPU asset preview - NOT Minecraft / one section of 3", fill=(150, 170, 181))
        frames.append(frame)
    out = ROOT / "model-lab/build/previews/ice-fang-frontal.gif"
    out.parent.mkdir(parents=True, exist_ok=True)
    frames[0].save(out, save_all=True, append_images=frames[1:], duration=33, loop=0, disposal=2)
    frames[10].save(out.with_suffix(".png"))
    print(out)


if __name__ == "__main__":
    main()
