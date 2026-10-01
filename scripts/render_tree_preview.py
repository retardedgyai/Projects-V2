"""Rasterize the exported native UiScene, using the same approved fonts, plates and item sprites."""
import argparse
import json
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont
from fontTools.ttLib import TTFont

ROOT = Path(__file__).resolve().parents[1]


def render(source, target):
    data = json.loads(Path(source).read_text(encoding="utf-8-sig"))
    scene = data["scene"]
    sprites = json.loads((ROOT / "web-ui-lab/ui/polish05-font-map.json").read_text(encoding="utf-8"))
    names = {v["char"]: k for k, v in sprites.items()}
    scale = 2
    image = Image.new("RGBA", (round(scene["width"] * scale), round(scene["height"] * scale) + 36), "#101619")
    draw = ImageDraw.Draw(image)
    fonts, cmaps = {}, {}
    warnings = []
    for family in ("sans", "serif"):
        path = ROOT / f"web-ui-lab/ui/polish05-fonts/{family}.ttf"
        cmaps[family] = TTFont(path).getBestCmap()
    for node in sorted(scene["nodes"], key=lambda n: n["depth"]):
        b, style = node["box"], node["style"]
        x, y, w, h = [round(b[k] * scale) for k in ("x", "y", "w", "h")]
        if b["x"] < 0 or b["y"] < 0 or b["x"] + b["w"] > scene["width"] + .01 or b["y"] + b["h"] > scene["height"] + .01:
            warnings.append(f"bounds: {node['id']}")
        if style.get("background-color"):
            draw.rectangle((x, y, x + w - 1, y + h - 1), fill=style["background-color"])
        if node.get("item"):
            icon = node["item"].split('/')[-1]
            path = ROOT / f"server-minestom/src/main/resources/core-ui-pack/assets/projects/textures/item/core_ui/{icon}.png"
            item = Image.open(path).convert("RGBA")
            size = round(min(w, h) * .8)
            item = item.resize((size, size), Image.Resampling.NEAREST)
            image.alpha_composite(item, (x + (w - size) // 2, y + (h - size) // 2))
        elif node.get("sprite"):
            name = names[node["sprite"]["char"]]
            plate = ROOT / f"assets/ui/polish05-import/resourcepack/assets/projects_ui_polish05/textures/plates/{name.replace('/', '_')}.png"
            art = Image.open(plate).convert("RGBA").resize((w, h), Image.Resampling.LANCZOS)
            image.alpha_composite(art, (x, y))
        elif node["text"]:
            family = style.get("font-family", "sans").split(':')[-1]
            size = max(1, round(float(style["font-size"].removesuffix("px")) * scale))
            key = (family, size)
            if key not in fonts:
                fonts[key] = ImageFont.truetype(ROOT / f"web-ui-lab/ui/polish05-fonts/{family}.ttf", size)
            font = fonts[key]
            missing = [c for c in node["text"] if ord(c) not in cmaps[family]]
            if missing:
                warnings.append(f"glyphs: {node['id']} {''.join(missing)}")
            width = draw.textlength(node["text"], font=font)
            if width > w + 2:
                warnings.append(f"text overflow: {node['id']} {width:.1f} > {w}")
            align = style.get("text-align", "left")
            tx = x + ((w - width) / 2 if align == "center" else w - width if align == "right" else 0)
            box = draw.textbbox((0, 0), node["text"], font=font)
            ty = y + (h - (box[3] - box[1])) / 2 - box[1]
            color = style.get("color", "#ddd1a9") if node["enabled"] else "#788579"
            draw.text((tx, ty), node["text"], font=font, fill=color)
    footer_font = ImageFont.truetype(ROOT / "web-ui-lab/ui/polish05-fonts/sans.ttf", 12)
    draw.text((24, image.height - 28), "実UiSceneの静止画 / 比較キャラクター Lv12・T2装備 / Minecraft実機の操作感は未検証", font=footer_font, fill="#a3ad9d")
    target = Path(target)
    target.parent.mkdir(parents=True, exist_ok=True)
    image.convert("RGB").save(target)
    audit = {"source": str(source), "nodes": len(scene["nodes"]), "warnings": warnings,
             "snapshot": {k: data["snapshot"][k] for k in ("job", "level", "budget", "spent", "originalSpent", "selected", "changed")}}
    target.with_suffix(".audit.json").write_text(json.dumps(audit, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps({"image": str(target), "nodes": len(scene["nodes"]), "warnings": warnings}, ensure_ascii=False))
    if warnings:
        raise SystemExit(1)


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("source")
    parser.add_argument("target")
    args = parser.parse_args()
    render(args.source, args.target)
