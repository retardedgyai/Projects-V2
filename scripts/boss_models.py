"""Scorpius boss authoring adapter. Never writes into the live pack or checked-in sources.

generate --boss all: regenerate bbmodels into model-lab/build/authored-models
validate: verify geometry/bone/animation references before a JVM build
preview --boss vesper --animation sweep: fixed-camera animation GIF from real model data
This CPU preview is NOT a Minecraft screenshot or client interpolation verification.
"""
import argparse
import importlib.util
import json
import math
from pathlib import Path
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "vendor/scorpius/bbmodel"
OUTPUT = ROOT / "model-lab/build/authored-models"
BOSSES = ("osirion", "radix", "vesper", "piglin_lord")


def validate_model(data):
    elements = {e["uuid"]: e for e in data["elements"]}
    if len(elements) != len(data["elements"]):
        raise ValueError("duplicate element UUID")
    bones, assigned = {}, set()

    def walk(node):
        if node["uuid"] in bones:
            raise ValueError("duplicate bone UUID")
        bones[node["uuid"]] = node["name"]
        for child in node.get("children", []):
            if isinstance(child, dict):
                walk(child)
            else:
                if child not in elements or child in assigned:
                    raise ValueError(f"invalid or duplicate element reference: {child}")
                assigned.add(child)

    for root in data["outliner"]:
        if not isinstance(root, dict):
            raise ValueError("all elements must belong to a bone")
        walk(root)
    if assigned != set(elements):
        raise ValueError("unparented elements")
    names = set()
    for animation in data.get("animations", []):
        if animation["name"] in names:
            raise ValueError("duplicate animation name")
        names.add(animation["name"])
        length = animation["length"]
        if not math.isfinite(length) or length < 0:
            raise ValueError("invalid animation length")
        for bone, track in animation.get("animators", {}).items():
            if track.get("type") == "bone" and bone not in bones:
                raise ValueError(f"missing animation bone: {bone}")
            for key in track.get("keyframes", []):
                if not 0 <= key["time"] <= length + 1e-6:
                    raise ValueError("keyframe outside animation")
    for element in elements.values():
        if not all(math.isfinite(v) for v in element["from"] + element["to"]):
            raise ValueError("non-finite geometry")
        if any(a > b for a, b in zip(element["from"], element["to"])):
            raise ValueError("inverted cube")
    return len(elements), len(bones), len(names)


def generate(boss):
    OUTPUT.mkdir(parents=True, exist_ok=True)
    for name in BOSSES if boss == "all" else (boss,):
        script = SOURCE / f"gen_{name}.py"
        if name == "osirion":
            arguments = [SOURCE / "osirion.bbmodel", OUTPUT / "osirion.bbmodel"]
        elif name == "radix":
            arguments = [SOURCE / "osirion.bbmodel", OUTPUT]
        else:
            arguments = [OUTPUT]
        subprocess.run([sys.executable, "-X", "utf8", str(script), *map(str, arguments)], check=True)
    validate_directory(OUTPUT)
    print("Build generated sources with: gradlew :model-lab:buildBossPack -PmodelSource=model-lab/build/authored-models")


def validate_directory(path):
    files = sorted(path.glob("*.bbmodel"))
    if not files:
        raise ValueError(f"no bbmodels in {path}")
    total = [0, 0, 0]
    for file in files:
        counts = validate_model(json.loads(file.read_text(encoding="utf-8")))
        total = [a+b for a, b in zip(total, counts)]
    print(f"Validated {len(files)} models: {total[0]} cubes / {total[1]} bones / {total[2]} animations")


def preview(path, animation, fps):
    import numpy as np
    from PIL import Image, ImageDraw
    spec = importlib.util.spec_from_file_location("scorpius_preview", SOURCE / "preview_bbmodel.py")
    renderer = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(renderer)
    data, elements, atlas, animations = renderer.load(path)
    anim = animations[animation]
    # Refuse silently inaccurate playback for channels the borrowed renderer cannot reproduce.
    for track in anim["animators"].values():
        for key in track.get("keyframes", []):
            if key["channel"] not in ("position", "rotation") or key.get("interpolation", "linear") != "linear":
                raise ValueError("preview supports linear position/rotation only; use in-game /anim")
    times = np.linspace(0, anim["length"], max(2, math.ceil(anim["length"] * fps) + 1))
    points = []
    for t in times:
        world = renderer.transforms(data["outliner"][0], anim, float(t), np.eye(4), {})
        for uuid, element in elements.items():
            matrix = world[uuid]
            lo, hi = element["from"], element["to"]
            corners = np.array([[hi[i] if mask & (1 << i) else lo[i] for i in range(3)] for mask in range(8)])
            points.append(renderer.project((matrix[:3, :3] @ corners.T).T + matrix[:3, 3], "front"))
    points = np.concatenate(points)
    low, high = points.min(axis=0), points.max(axis=0)
    origin = (low[0] - 3, low[1] - 3)
    pixels = min(5, 500 / max(high[0] - low[0] + 6, high[1] - low[1] + 6))
    canvas = tuple(math.ceil((high[i] - low[i] + 6) * pixels) for i in (0, 1))
    frames = []
    for t in times:
        image = renderer.render(data, elements, atlas, anim, float(t), "front", pixels, canvas, origin)
        frame = Image.new("RGB", (max(canvas[0], 440), canvas[1] + 38), (24, 23, 30))
        frame.paste(image, ((frame.width - image.width) // 2, 38))
        draw = ImageDraw.Draw(frame)
        draw.text((8, 4), f"{path.stem} / {animation} / {t:.2f}s", fill="white")
        draw.text((8, 19), "CPU model preview - NOT Minecraft", fill=(200, 180, 160))
        frames.append(frame)
    out = ROOT / "model-lab/build/previews" / f"{path.stem}-{animation}.gif"
    out.parent.mkdir(parents=True, exist_ok=True)
    frames[0].save(out, save_all=True, append_images=frames[1:], duration=round(1000/fps), loop=0, disposal=2)
    print(out)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("action", choices=("generate", "validate", "preview"))
    parser.add_argument("--boss", choices=(*BOSSES, "all"), default="all")
    parser.add_argument("--source", type=Path, default=SOURCE)
    parser.add_argument("--animation", default="idle")
    parser.add_argument("--fps", type=int, choices=(20, 25, 50), default=25)
    args = parser.parse_args()
    if args.action == "generate":
        generate(args.boss)
    elif args.action == "validate":
        validate_directory(args.source)
    else:
        for boss in BOSSES if args.boss == "all" else (args.boss,):
            preview(args.source / f"{boss}.bbmodel", args.animation, args.fps)


if __name__ == "__main__":
    main()
