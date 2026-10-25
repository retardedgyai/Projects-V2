"""Regenerate the build-time part of server-minestom's core-ui-pack.

Generated files are not committed (see .gitignore); Gradle's
`:server-minestom:generateResourcePack` runs this before processResources.
build_core_ui_assets also builds the pixel combat HUD (build_combat_hud.py)
after the menu font; the Ice Garden and first-magic font builders are
independent leaves.
The final step rewrites index.txt from every file in the pack (committed and
generated), which is what CoreUiPackServer bundles.

Requires Pillow + numpy (scripts/requirements.txt). The menu builder downloads
one pinned, sha256-checked font into .tools/core-menu/ on first run.
"""
from pathlib import Path
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
SCRIPTS = ROOT / "scripts"
PACK = ROOT / "server-minestom/src/main/resources/core-ui-pack"

CHAIN = [
    "build_core_ui_assets.py",   # menu atlas (charset from */src/main/kotlin), pixel combat HUD, combat VFX models
    "build_ice_garden.py",       # combat_vfx/garden
    "build_ice_garden_bloom.py", # combat_vfx/garden_bloom
    # Only font/first_magic_canvas_*.json; the first_magic art itself is committed (partly hand-finished).
    ["build_first_magic_assets.py", "--fonts-only"],
    "build_boss_vfx.py",         # boss_vfx data maps + the core/item shader experiment
    "build_player_puppet.py",    # combat_vfx/puppet: player skin parts, one tinted face per skin pixel
]


def write_index():
    paths = sorted(p.relative_to(PACK).as_posix() for p in PACK.rglob("*") if p.is_file() and p.name != "index.txt")
    (PACK / "index.txt").write_text("\n".join(paths) + "\n", encoding="utf-8")
    return len(paths)


def main():
    for step in CHAIN:
        name, *args = [step] if isinstance(step, str) else step
        print(f"[gen_resource_pack] {name}", flush=True)
        subprocess.run([sys.executable, str(SCRIPTS / name), *args], cwd=ROOT, check=True, stdout=subprocess.DEVNULL)
    print(f"[gen_resource_pack] indexed {write_index()} files under {PACK.relative_to(ROOT).as_posix()}")


if __name__ == "__main__":
    main()
