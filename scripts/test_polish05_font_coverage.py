"""Catch missing Japanese glyphs in live smith UI strings before packaging."""
import json
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
METRICS = ROOT / "web-ui-lab/src/main/resources/polish05-font-metrics.json"
PACK = ROOT / "server-minestom/src/main/resources/polish05/pack.zip"
SOURCES = (
    "web-ui-lab/src/main/kotlin/dev/projects/webui/Polish05Scene.kt",
    "server-minestom/src/main/kotlin/dev/projects/server/coreloop/CoreAccount.kt",
    "server-minestom/src/main/kotlin/dev/projects/server/coreloop/CorePolish05ForgeFlow.kt",
    "server-minestom/src/main/kotlin/dev/projects/server/coreloop/CoreEnhancementCatalog.kt",
)


def main() -> None:
    required = {c for source in SOURCES for c in (ROOT / source).read_text(encoding="utf-8")
                if ord(c) >= 127}
    metrics = json.loads(METRICS.read_text(encoding="utf-8"))
    with zipfile.ZipFile(PACK) as archive:
        for family in ("sans", "serif"):
            covered = {chr(int(cp)) for cp in metrics[family]}
            provider = json.loads(archive.read(f"assets/projects_ui_polish05/font/{family}.json"))
            packed = {c for item in provider["providers"] if item["type"] == "bitmap"
                      for row in item["chars"] for c in row if c != "\0"}
            missing = required - covered.intersection(packed)
            assert not missing, f"{family} missing glyphs: {''.join(sorted(missing))}"
    print(f"POLISH05_FONT_COVERAGE_PASS production glyphs={len(required)}")


if __name__ == "__main__":
    main()
