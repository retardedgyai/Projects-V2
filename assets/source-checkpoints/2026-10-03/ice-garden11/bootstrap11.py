"""Copy owned10 dependency files without importing or modifying the study."""
from pathlib import Path
import hashlib,json,shutil
ROOT=Path(__file__).resolve().parent;OLD=ROOT.parent/'ice-garden-living-10';BASE=ROOT.parent.parent
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest();manifest=json.loads((OLD/'source-hashes.json').read_text())
for rel,h in manifest.items():assert sha(OLD/rel)==h,rel
for name in ['fixed07.py','baseline08.py','field_math08.py','raster_native.py','effect_policy_base07.py','field_math09.py','baseline09.py','layout10.py']:
 shutil.copyfile(OLD/name,ROOT/name)
shutil.copyfile(OLD/'field_math.py',ROOT/'field_math10.py');shutil.copyfile(OLD/'scene10.py',ROOT/'baseline10.py')
text=(ROOT/'baseline10.py').read_text();(ROOT/'baseline10.py').write_text(text.replace('from field_math import textures','from field_math10 import textures'))
for folder in ['native-study','native-context']:
 target=ROOT/folder;target.mkdir(exist_ok=True)
 for file in (OLD/folder).iterdir():
  if file.is_file() and '-basis' not in file.name:shutil.copyfile(file,target/file.name)
(ROOT/'protected10.json').write_text(json.dumps({'10_files':manifest,'10_zip_sha256':sha(BASE/'deliverables/mage-ice-garden-living-10-review.zip'),'10_library_items_modified':0,'original09_and_game_worktrees_modified':False},indent=2)+'\n')
print(json.dumps({'protected10_files':len(manifest),'support_owned_assets_copied':True,'game_started':False}))
