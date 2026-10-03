"""Freeze11c and copy only the owned foundation. No changes to original studies."""
from pathlib import Path
import hashlib,json,shutil
ROOT=Path(__file__).resolve().parent;OLD=ROOT.parent/'ice-garden-ridge-11';BASE=ROOT.parent.parent
hs=json.loads((OLD/'source-hashes.json').read_text())
for rel,sha in hs.items():assert hashlib.sha256((OLD/rel).read_bytes()).hexdigest()==sha,rel
archive=BASE/'deliverables/mage-ice-garden-11-prototype-review.zip'
assert hashlib.sha256(archive.read_bytes()).hexdigest()=='777143c6df995e7fb0edb5c2aecfd7c608c80ab55a71e941b9b4becef70bcbef'
for folder in ['native-study','native-context','preview']:(ROOT/folder).mkdir(exist_ok=True)
copies=['fixed07.py','raster_native.py','scene11.py','field_math11.py','field_math10.py','field_math09.py','layout10.py','author11.py','geometry_export_helpers.py','run_native_probe.py','NativeModelProbe.java']
for name in copies:shutil.copy2(OLD/name,ROOT/name)
for folder in ['native-study','native-context']:
 for p in (OLD/folder).iterdir():
  if p.is_file() and '-basis.' not in p.name:shutil.copy2(p,ROOT/folder/p.name)
guard={'11_files':hs,'11_zip_sha256':hashlib.sha256(archive.read_bytes()).hexdigest(),'frozen_foundation_copies':{n:hashlib.sha256((ROOT/n).read_bytes()).hexdigest() for n in copies},'11_library_items_modified':0,'game_and_other_skills_modified':False}
(ROOT/'protected11.json').write_text(json.dumps(guard,indent=2)+'\n')
print(json.dumps({'guarded11_files':len(hs),'owned_foundation_copied':len(copies),'11_modified':False,'game_modified':False}))
