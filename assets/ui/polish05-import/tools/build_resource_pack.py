#!/usr/bin/env python3
"""Package extracted, unchanged Polish05 assets into a namespaced resource pack.
This pack alone does not open a UI. The native lab renderer must consume it.
"""
from __future__ import annotations
import argparse, hashlib, json, shutil, subprocess, tempfile, zipfile
from pathlib import Path
from PIL import Image

NS='projects_ui_polish05'
CUES={
 'click':dict(source='click',offset=.045,duration=.115,rate=1.,gain=.58,fade=.018),
 'select':dict(source='click',offset=.045,duration=.115,rate=1.05,gain=.50,fade=.018),
 'back':dict(source='click',offset=.045,duration=.115,rate=.92,gain=.42,fade=.018),
 'equip':dict(source='click',offset=.045,duration=.115,rate=.95,gain=.58,fade=.018),
 'enhance_prepare':dict(source='enchant_charge',rate=1.,gain=.40,fade=.065),
 'enhance_success':dict(source='enchant_plus',rate=1.,gain=.72,fade=.10),
 'enhance_success_radiant':dict(source='enchant_radiant',rate=1.,gain=.72,fade=.10),
 'enhance_fail':dict(source='dissipate',rate=1.,gain=.46,fade=.12),
 'refine_strike':dict(source='anvil',offset=0.,duration=.285,rate=1.06,gain=.32,fade=.075),
 'refine_success':dict(source='orb',offset=0.,duration=.46,rate=1.18,gain=.38,fade=.10),
}

def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--root',type=Path,default=Path(__file__).resolve().parents[1]);p.add_argument('--pack-format',type=int,default=88)
 args=p.parse_args();root=args.root;pack=root/'resourcepack';asset=pack/'assets'/NS
 for d in ['textures/raw','textures/plates','sounds/ui','font']:(asset/d).mkdir(parents=True,exist_ok=True)
 def j(path,obj):path.write_text(json.dumps(obj,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
 j(pack/'pack.mcmeta',{'pack':{'description':'ProjectS Polish05 / isolated native UI import assets','min_format':[args.pack_format,0],'max_format':[args.pack_format,0]}})
 # All original images retained, including existing pixel sprites. No redraw, requantization or rescale.
 for path in (root/'assets/images').glob('*.png'):shutil.copy2(path,asset/'textures/raw'/path.name)
 sprite_manifest={};providers=[];cp=0xE900
 for path in sorted((root/'assets/images').glob('*.png')):
  with Image.open(path) as im:
   im=im.convert('RGBA')
   if im.width>256 or im.height>256:continue
   bbox=im.getchannel('A').getbbox()
   if not bbox:continue
   # All native font glyphs here are artwork, not copied TTF/OTF fonts.
   ch=chr(cp);cp+=1
   providers.append({'type':'bitmap','file':f'{NS}:raw/{path.name}','ascent':im.height,'height':im.height,'chars':[ch]})
   sprite_manifest[path.stem]={'char':ch,'codepoint':hex(cp-1),'font':f'{NS}:sprites','width':im.width,'height':im.height,'ascent':im.height,'expectedAdvance':bbox[2]+1,'source':f'{NS}:raw/{path.name}'}
 j(asset/'font/sprites.json',{'providers':providers})
 # Native integration can use <=256px bitmap tiles for exact static plates.
 plates={}
 for path in sorted((root/'assets/plates').glob('*.png')):
  with Image.open(path) as im:
   im=im.convert('RGBA');tiles=[]
   for y in range(0,im.height,256):
    for x in range(0,im.width,256):
     tile=im.crop((x,y,min(im.width,x+256),min(im.height,y+256)))
     name=f'{path.stem}_{x}_{y}.png';tile.save(asset/'textures/plates'/name)
     tiles.append({'file':f'{NS}:plates/{name}','x':x,'y':y,'w':tile.width,'h':tile.height})
   plates[path.stem]={'width':im.width,'height':im.height,'tiles':tiles,
      'purpose':'visual calibration only; full reference plate is NOT a completed interactive implementation' if 'reference' in path.stem else 'static environment backing; render weapon and state separately'}
 sounds={}
 # Ogg input bytes are preserved in assets/audio. Cue exports bake the same cut/rate/gain/envelope;
 # master volume and the browser compressor are not baked. Those require a game listening test.
 for key,c in CUES.items():
  src=root/'assets/audio'/(c['source']+'.ogg')
  import soundfile as sf
  info=sf.info(str(src));dur=min(c.get('duration',info.duration-c.get('offset',0)),info.duration-c.get('offset',0))/c['rate']
  off=c.get('offset',0);original_dur=dur*c['rate'];fade=min(c['fade'],dur*.3)
  flt=f"atrim=start={off}:duration={original_dur},asetpts=PTS-STARTPTS,asetrate={info.samplerate}*{c['rate']},aresample=44100,volume={c['gain']},afade=t=in:st=0:d=0.003,afade=t=out:st={max(.004,dur-fade)}:d={fade}"
  dest=asset/'sounds/ui'/(key+'.ogg')
  subprocess.run(['ffmpeg','-v','error','-y','-i',str(src),'-af',flt,'-c:a','libvorbis','-q:a','6',str(dest)],check=True)
  sounds['ui.'+key]={'sounds':[{'name':f'{NS}:ui/{key}','stream':False}]}
  c['event']=f'{NS}:ui.{key}';c['export_seconds']=dur;c['gain_baked']=True
 j(asset/'sounds.json',sounds)
 j(root/'layout/sprite_glyphs.json',sprite_manifest);j(root/'layout/static_plates.json',plates)
 j(root/'layout/audio_cues.json',{'masterVolume':.35,'doNotApplyCueGainTwice':True,'hoverSound':False,'clickDebounceMs':70,'maxVoices':4,'enhanceResultAtMs':720,'refineStrikeAtMs':185,'refineResultAtMs':900,'stopOnCloseOrDisconnect':True,'browserLimiterNotReproduced':True,'cues':CUES})
 target=root/'Polish05_Assets_26_2.zip'
 with zipfile.ZipFile(target,'w',zipfile.ZIP_DEFLATED) as z:
  for path in sorted(pack.rglob('*')):
   if path.is_file():z.write(path,path.relative_to(pack))
 j(root/'verification/resourcepack.json',{'pack_sha1':hashlib.sha1(target.read_bytes()).hexdigest(),'pack_sha256':hashlib.sha256(target.read_bytes()).hexdigest(),'format':args.pack_format,'formatSource':'Projects-V2 f534f846 server-minestom/src/main/resources/core-ui-pack/pack.mcmeta','spriteCount':len(sprite_manifest),'soundCount':len(sounds),'noMinecraftOverrides':not (pack/'assets/minecraft').exists(),'nativeLoadingTested':False})
 print('Resource pack built:',target)
if __name__=='__main__':main()
