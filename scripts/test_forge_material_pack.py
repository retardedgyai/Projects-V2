"""Check that storage, forge, and core menu share the same 16 px art."""
import json
import zipfile
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
CORE = ROOT / "server-minestom/src/main/resources/core-ui-pack/assets/projects"
POLISH = ROOT / "server-minestom/src/main/resources/polish05"
NAMES = ("wood", "ore", "stone", "hide", "fiber", "board", "ingot",
         "cut_stone", "leather", "cloth", "affix_dust")
SOURCE = ROOT / "assets/core-ui/forge-v4/source"


def main():
    mapping = json.loads((POLISH / "font-map.json").read_text(encoding="utf-8"))
    with zipfile.ZipFile(POLISH / "pack.zip") as pack:
        for name in NAMES:
            core = CORE / f"textures/item/forge_materials/{name}.png"
            assert core.read_bytes() == (SOURCE / f"{name}.png").read_bytes(), name
            with Image.open(core) as image:
                assert image.size == (16, 16), name
                assert image.getchannel("A").getbbox() is not None, name
                assert set(image.getchannel("A").tobytes()) <= {0, 255}, name
            entry = mapping[f"forge_material_{name}"]
            assert entry["width"] == entry["height"] == 16, name
            assert core.read_bytes() == pack.read(
                f"assets/projects_ui_polish05/textures/forge_materials/{name}.png"), name
            item = json.loads((CORE / f"items/forge_materials/{name}.json").read_text())
            model = json.loads((CORE / f"models/item/forge_materials/{name}.json").read_text())
            assert item["model"]["model"] == f"projects:item/forge_materials/{name}"
            assert model["textures"]["layer0"] == f"projects:item/forge_materials/{name}"
    for raw, refined in (("wood", "board"), ("ore", "ingot"), ("stone", "cut_stone"),
                         ("hide", "leather"), ("fiber", "cloth")):
        assert (CORE / f"textures/item/forge_materials/{raw}.png").read_bytes() != (
            CORE / f"textures/item/forge_materials/{refined}.png").read_bytes()
    assert (ROOT / "web-ui-lab/build/polish05/polish05-lab.zip").read_bytes() == (POLISH / "pack.zip").read_bytes()
    print("FORGE_MATERIAL_PACK_PASS 11 shared 16px sprites, 5 distinct raw/refined pairs, model/font/zip match")


if __name__ == "__main__":
    main()
