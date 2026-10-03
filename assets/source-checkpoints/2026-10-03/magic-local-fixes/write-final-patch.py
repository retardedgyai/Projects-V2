from pathlib import Path
import difflib

root = Path(__file__).parent
project = Path(r'C:\Users\xgaiz\Documents\Codex\2026-09-30\task\world-infusion-altar')
base = Path('server-minestom/src/main')
files = [(base / 'kotlin/dev/projects/server/coreloop/WorldInfusionServer.kt', root / 'WorldInfusionServer.kt')]
for relative in ['index.txt', 'assets/minecraft/atlases/items.json', 'assets/projects/models/infusion-v4/support.json', 'assets/projects/models/infusion-v7/core_ritual.json']:
    files.append((base / 'resources/core-ui-pack' / relative, root / 'classes/core-ui-pack' / relative))
patch = []
for relative, override in files:
    before = (project / relative).read_text(encoding='utf-8-sig').splitlines(keepends=True)
    after = override.read_text(encoding='utf-8-sig').splitlines(keepends=True)
    patch.extend(difflib.unified_diff(before, after, fromfile='a/' + relative.as_posix(), tofile='b/' + relative.as_posix()))
destination = root / 'magic-local-fixes.patch'
destination.write_text(''.join(patch), encoding='utf-8')
print(f'Patch regenerated: {len(files)} files, {destination.stat().st_size} bytes')
