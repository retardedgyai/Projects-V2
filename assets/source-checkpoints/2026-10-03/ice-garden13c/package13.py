"""Owned independent source package. No original reference media or browser profile."""
from pathlib import Path
import hashlib,json,re,shutil,zipfile,subprocess,sys,ctypes
ROOT=Path(__file__).resolve().parent;DEST=ROOT.parent.parent/'deliverables'
def excluded(p):
 rel=p.relative_to(ROOT)
 return 'reference-private' in rel.parts or '__pycache__' in rel.parts or any(x.startswith('.') for x in rel.parts) or '-basis.' in p.name or p.name=='source-hashes.json'
def main():
 k=ctypes.windll.kernel32;k.GetCurrentProcess.restype=ctypes.c_void_p;k.SetPriorityClass.argtypes=[ctypes.c_void_p,ctypes.c_uint];k.SetPriorityClass(k.GetCurrentProcess(),0x4000)
 files=[p for p in sorted(ROOT.rglob('*')) if p.is_file() and not excluded(p)]
 sha={p.relative_to(ROOT).as_posix():hashlib.sha256(p.read_bytes()).hexdigest() for p in files}
 (ROOT/'source-hashes.json').write_text(json.dumps(sha,indent=2)+'\n');files.append(ROOT/'source-hashes.json')
 archive=DEST/'mage-ice-garden-13-contact-review.zip'
 with zipfile.ZipFile(archive,'w',zipfile.ZIP_DEFLATED,compresslevel=6) as z:
  for p in files:z.write(p,p.relative_to(ROOT).as_posix())
 isolated=ROOT/'reference-private/isolated-package';isolated.mkdir(exist_ok=True)
 with zipfile.ZipFile(archive) as z:z.extractall(isolated)
 for rel,h in sha.items():assert hashlib.sha256((isolated/rel).read_bytes()).hexdigest()==h,rel
 for folder in ['native-study','native-context']:
  for p in (isolated/folder).glob('*.model.json'):
   for ident in json.loads(p.read_text())['textures'].values():assert (p.parent/(ident.rsplit('/',1)[-1]+'.png')).is_file(),(p.name,ident)
 assert len(list((isolated/'preview/frames').glob('*.png')))==32;assert len(list((isolated/'preview/whole-frames').glob('*.png')))==124
 for file in ['12b-to-13-contact.gif','contact-13.gif','contact-phases.png','oblique.png','near.png','light.png','dark.png']:assert (isolated/'preview'/file).is_file()
 check=subprocess.run([sys.executable,'-X','utf8','-c',"from scene13 import render;[render(t,w=320,h=180) for t in [2.24,2.30,2.54,2.60,2.70]];print('isolated render PASS')"],cwd=isolated,capture_output=True,text=True,encoding='utf-8')
 assert check.returncode==0,check.stderr
 mapping=[]
 for name,target in [('12b-to-13-contact.gif','mage-ice-garden-13-comparison.gif'),('contact-phases.png','mage-ice-garden-13-contact-phases.png'),('whole-13.gif','mage-ice-garden-13-whole.gif')]:
  p=DEST/target;shutil.copy2(ROOT/'preview'/name,p);mapping.append({'path':str(p),'type':'image'})
 mapping.append({'path':str(archive),'type':'other'})
 receipt={'status':'13c contact art prototype; FIX-FIRST / game and MatE gate open','uploads':mapping,'zip_bytes':archive.stat().st_size,'zip_sha256':hashlib.sha256(archive.read_bytes()).hexdigest(),'zip_files':len(files),'public_hashes_checked':len(sha),'isolated_texture_dependencies_checked':True,'isolated_render_times':[2.24,2.30,2.54,2.60,2.70],'reference_media_included':False,'game_modified':False,'existing11_12_library_items_modified':0,'push_performed_by_this_agent':False}
 (DEST/'mage-ice-garden-13-receipt.json').write_text(json.dumps(receipt,indent=2)+'\n');print(json.dumps(receipt))
if __name__=='__main__':main()
