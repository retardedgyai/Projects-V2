#!/usr/bin/env python3
"""Offline integrity/structure checks. Not a native Minecraft quality test."""
from __future__ import annotations
import argparse,base64,hashlib,json
from pathlib import Path
from PIL import Image
import soundfile as sf

def sha(b):return hashlib.sha256(b).hexdigest()
def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--root',type=Path,default=Path(__file__).resolve().parents[1]);a=p.parse_args();r=a.root
 manifest=json.loads((r/'assets/manifest.json').read_text());src=r/'reference'/manifest['source'];s=src.read_text()
 assert sha(src.read_bytes())==manifest['source_sha256'],'Source HTML changed'
 checks=[]
 for var,group in [('ASSETS','images'),('MC_AUDIO','audio')]:
  values=json.JSONDecoder().raw_decode(s.split('const '+var+'=',1)[1].lstrip())[0]
  for key,v in values.items():
   entry=manifest[group][key];path=r/entry['path'];data=path.read_bytes();b64=v.split(',',1)[1] if v.startswith('data:') else v
   assert data==base64.b64decode(b64,validate=True),key+' bytes changed'
   assert sha(data)==entry['sha256'],key+' hash mismatch'
   if group=='images':
    with Image.open(path) as im:im.verify()
   else:
    info=sf.info(str(path));assert info.frames>0,key+' audio empty'
   checks.append('source:'+key)
 snapshots=list((r/'layout').glob('*.json'))
 for pth in snapshots:
  data=json.loads(pth.read_text())
  if 'controls' not in data:continue
  assert data['frames']['#window']=={'x':66,'y':140,'w':1308,'h':697},pth.name+' frame changed'
  for ctl in data['controls']:
   box=ctl['rect'];assert box['w']>=0 and box['h']>=0
   assert all(abs(float(box[k]))<10000 for k in ['x','y','w','h'])
  checks.append('layout:'+pth.stem)
 pack=r/'resourcepack';assert not (pack/'assets/minecraft').exists(),'Global vanilla override'
 for pth in r.rglob('*'):
  assert pth.suffix.lower() not in {'.ttf','.otf','.woff','.woff2','.ttc','.exe'},'Unexpected bundled font/executable'
 for pth in (pack/'assets/projects_ui_polish05/sounds/ui').glob('*.ogg'):
  info=sf.info(str(pth));assert info.frames>0;checks.append('cue:'+pth.stem)
 provider=json.loads((pack/'assets/projects_ui_polish05/font/sprites.json').read_text())
 for gl in provider['providers']:
  assert 0<gl['height']<=256 and gl['ascent']<=gl['height']
  namespace,path=gl['file'].split(':',1);fp=pack/'assets'/namespace/'textures'/path
  assert fp.is_file();assert len(gl['chars'])==1 and len(gl['chars'][0])==1
  checks.append('glyph:'+path)
 for pth in (pack/'assets/projects_ui_polish05/textures/plates').glob('*.png'):
  with Image.open(pth) as im:assert im.width<=256 and im.height<=256
 report={'passed':True,'checks':checks,'scope':'source bytes, reference layout and resource structure only','nativeAdapterImplemented':False,'minecraftTested':False}
 (r/'verification/integrity.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
 print('PASS:',len(checks),'offline checks. Minecraft integration remains to be implemented and tested locally.')
if __name__=='__main__':main()
