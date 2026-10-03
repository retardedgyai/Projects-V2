"""Self-contained owned review; exclude reference originals/browser state/bases."""
from pathlib import Path
import hashlib,json,re,shutil,zipfile
ROOT=Path(__file__).resolve().parent;DEST=ROOT.parent.parent/'deliverables'
def excluded(p):
 rel=p.relative_to(ROOT);return ('reference-private' in rel.parts or '__pycache__' in rel.parts or any(part.startswith('.') for part in rel.parts) or '-basis.' in p.name or p.name=='source-hashes.json')
def main():
 # Canonical source hashes cover all public study files before upload.
 files=[p for p in sorted(ROOT.rglob('*')) if p.is_file() and not excluded(p)]
 sha={p.relative_to(ROOT).as_posix():hashlib.sha256(p.read_bytes()).hexdigest() for p in files}
 (ROOT/'source-hashes.json').write_text(json.dumps(sha,indent=2)+'\n');files.append(ROOT/'source-hashes.json')
 archive=DEST/'mage-ice-garden-11-prototype-review.zip'
 with zipfile.ZipFile(archive,'w',compression=zipfile.ZIP_DEFLATED,compresslevel=6) as z:
  for p in files:z.write(p,p.relative_to(ROOT).as_posix())
 # Test extraction in isolation, links, source bytes and all texture dependencies.
 isolated=ROOT/'reference-private/isolated-package';isolated.mkdir(exist_ok=True)
 with zipfile.ZipFile(archive) as z:z.extractall(isolated)
 for rel,digest in sha.items():assert hashlib.sha256((isolated/rel).read_bytes()).hexdigest()==digest,rel
 html=(isolated/'preview/viewer.html').read_text(encoding='utf-8')
 for name in re.findall(r"\['([^']+\.(?:gif|png))'",html):assert (isolated/'preview'/name).is_file(),name
 assert len(list((isolated/'preview/frames').glob('*.png')))==125
 for directory in ['native-study','native-context']:
  for model in (isolated/directory).glob('*.model.json'):
   for identifier in json.loads(model.read_text())['textures'].values():
    name=identifier.rsplit('/',1)[-1]+'.png';assert (isolated/directory/name).is_file(),(model.name,name)
 mapping=[]
 for name,target in [('comparison-10-to-11.gif','mage-ice-garden-11-comparison.gif'),('phases-10-to-11.png','mage-ice-garden-11-phases.png')]:
  path=DEST/target;shutil.copy2(ROOT/'preview'/name,path);mapping.append(dict(path=str(path),type='image'))
 mapping.append(dict(path=str(archive),type='other'))
 receipt={'status':'intermediate art prototype; MatE quality gate open','uploads':mapping,'zip_bytes':archive.stat().st_size,'zip_sha256':hashlib.sha256(archive.read_bytes()).hexdigest(),'zip_files':len(files),'public_hashes_checked':len(sha),'isolated_texture_dependencies_checked':True,'reference_media_included':False,'game_modified':False,'existing10_library_items_modified':0}
 (DEST/'mage-ice-garden-11-receipt.json').write_text(json.dumps(receipt,indent=2)+'\n');print(json.dumps(receipt))
if __name__=='__main__':main()
