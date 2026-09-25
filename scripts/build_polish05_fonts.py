"""Subset the OFL Noto JP fonts used by the approved HTML for Vanilla's TTF provider.

This is an optional regeneration tool. Committed subsets are used by the normal pack build.
Requires fonttools and locally installed NotoSansJP-VF.ttf / NotoSerifJP-VF.ttf.
"""
from __future__ import annotations

import json
import os
from pathlib import Path

from fontTools import subset
from fontTools.ttLib import TTFont
from fontTools.varLib.instancer import instantiateVariableFont


ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "web-ui-lab/ui/polish05-fonts"
SOURCES = [
    ROOT / "assets/ui/polish05-import/reference/ProjectS_UI_Polish05_Workbench.html",
    ROOT / "web-ui-lab/src/main/kotlin/dev/projects/webui/Polish05Scene.kt",
    ROOT / "web-ui-lab/src/main/kotlin/dev/projects/webui/Polish05Flow.kt",
    ROOT / "assets/ui/polish05-import/native/src/main/kotlin/dev/projects/webui/polish05/Polish05PreviewModel.kt",
]


def main() -> None:
    out = OUT
    out.mkdir(parents=True, exist_ok=True)
    letters = set(range(32, 127))
    for source in SOURCES:
        letters.update(ord(c) for c in source.read_text(encoding="utf-8") if ord(c) >= 127)
    font_dir = Path(os.environ.get("POLISH05_SYSTEM_FONT_DIR", r"C:\Windows\Fonts"))
    metrics = {}
    for key, filename in (("sans", "NotoSansJP-VF.ttf"), ("serif", "NotoSerifJP-VF.ttf")):
        font = TTFont(font_dir / filename)
        available = font.getBestCmap()
        options = subset.Options()
        options.name_IDs = [0, 1, 2, 3, 4, 5, 6, 13, 14]
        options.name_languages = [0x409]
        options.layout_features = ["kern", "liga"]
        sub = subset.Subsetter(options=options)
        sub.populate(unicodes=letters & available.keys())
        sub.subset(font)
        if "fvar" in font:
            font = instantiateVariableFont(font, {"wght": 400}, inplace=True)
        font.save(out / f"{key}.ttf")
        unit = font["head"].unitsPerEm
        cmap = font.getBestCmap()
        metrics[key] = {str(cp): round(font["hmtx"].metrics[name][0] * 32 / unit, 4)
                        for cp, name in cmap.items()}
        print(f"POLISH05_FONT_SUBSET {key} glyphs={len(cmap)} bytes={(out / f'{key}.ttf').stat().st_size}")
    (ROOT / "web-ui-lab/src/main/resources").mkdir(parents=True, exist_ok=True)
    (ROOT / "web-ui-lab/src/main/resources/polish05-font-metrics.json").write_text(
        json.dumps(metrics, ensure_ascii=False, sort_keys=True, separators=(",", ":")), encoding="utf-8")


if __name__ == "__main__":
    main()
