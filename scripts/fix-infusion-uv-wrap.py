"""Make the altar models render in 26.2 exactly as their approved previews.

The preview renderer (render-world-models.py) repeats a texture when a face UV
leaves 0..16. Vanilla 26.2 does not: it reads the neighbouring atlas area, which
is transparent, so the support blades and core stones lost their side faces.
Each such face now points at a 3x3 tiled copy of its texture (48px, covering
UV -16..32) with the UV remapped, giving the same pixels without repeating.
Shapes, sizes and colours stay unchanged. Safe to run again.
"""
from pathlib import Path
import json
from PIL import Image

PACK = Path(__file__).resolve().parents[1] / "server-minestom/src/main/resources/core-ui-pack/assets/projects"
MODELS = ["infusion-v4/support", "infusion-v7/core_ritual"]


def outside(uv):
    return min(uv) < 0 or max(uv) > 16


def tiled(sprite):
    name = sprite + "_tiled"
    folder, base = sprite.split(":")[1].split("/")
    source = Image.open(PACK / f"textures/{folder}/{base}.png").convert("RGBA")
    assert source.size == (16, 16), sprite
    out = Image.new("RGBA", (48, 48))
    for x in range(3):
        for y in range(3):
            out.paste(source, (x * 16, y * 16))
    out.save(PACK / f"textures/{folder}/{base}_tiled.png")
    return name


for model_name in MODELS:
    path = PACK / f"models/{model_name}.json"
    model = json.loads(path.read_text(encoding="utf-8"))
    textures = model["textures"]
    for element in model["elements"]:
        for face in element["faces"].values():
            if not outside(face["uv"]):
                continue
            assert all(-16 <= v <= 32 for v in face["uv"]), (model_name, face["uv"])
            key = face["texture"][1:]
            sprite = textures[key]
            assert isinstance(sprite, str), (model_name, key)
            textures.setdefault(key + "_tiled", tiled(sprite))
            face["texture"] = "#" + key + "_tiled"
            face["uv"] = [round((v + 16) / 3, 5) for v in face["uv"]]
    path.write_text(json.dumps(model, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(model_name, "ok")
