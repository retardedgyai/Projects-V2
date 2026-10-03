"""Entire authored fixture timeline. Not a recording of game operation."""
from pathlib import Path
import ctypes,json,time,hashlib
from PIL import Image
from scene13 import render,assets,live_contact
from raster_native import raster,actor_position,CONTACTS
from review13 import label
ROOT=Path(__file__).resolve().parent;OUT=ROOT/'preview'
def main():
 k=ctypes.windll.kernel32;k.GetCurrentProcess.restype=ctypes.c_void_p;k.SetPriorityClass.argtypes=[ctypes.c_void_p,ctypes.c_uint];priority=bool(k.SetPriorityClass(k.GetCurrentProcess(),0x4000))
 start=time.perf_counter();frames=[];rows=[];folder=OUT/'whole-frames';folder.mkdir(exist_ok=True)
 cached=ROOT/'checkpoints/13b-sinking-fall';available=(cached/'whole-evidence.json').is_file() and (cached/'frame-hashes.json').is_file();oldrows=json.loads((cached/'whole-evidence.json').read_text())['samples'] if available else [];hashes=json.loads((cached/'frame-hashes.json').read_text()) if available else {};reused=0;rendered=0
 for i in range(124):
  t=i/16
  if not available or live_contact(t,[(h,actor_position(h)) for h in CONTACTS]):
   im,mask=render(t,w=640,h=360,legs_tag=9);_,bare=raster(t,assets(),[],w=640,h=360,legs_tag=9);row={'time':t,'head_body_occluded':int(((bare==5)&(mask!=5)).sum()),'leg_retention':float((mask==9).sum()/max(1,(bare==9).sum())),'effect_pixels':int((mask==3).sum())};rendered+=1
  else:
   file=cached/'whole-frames'/f'{i:03d}.png';assert hashlib.sha256(file.read_bytes()).hexdigest()==hashes[file.name];im=Image.open(file).convert('RGB');row=dict(oldrows[i]);reused+=1
  frames.append(label(im,f'13c / WHOLE AUTHORED FIXTURE / {t:.3f}s'));im.save(folder/f'{i:03d}.png')
  rows.append(row)
  if i%20==0:print(json.dumps({'whole_rendered':i+1,'total':124}),flush=True)
 frames[0].save(OUT/'whole-13.gif',save_all=True,append_images=frames[1:],duration=[60,60,60,70]*31,loop=0,optimize=False)
 for begin in range(0,124,16):
  board=Image.new('RGB',(1600,972),(19,29,35))
  for j,im in enumerate(frames[begin:begin+16]):board.paste(im.resize((400,243),Image.Resampling.NEAREST),(j%4*400,j//4*243))
  board.save(OUT/f'whole-inspection-{begin:03d}.png')
 out={'revision':'13c','affected_frames_rendered':rendered,'unchanged_frames_reused':reused,'reuse_reason':'quiet renderer is unchanged;10tests include pixel identity; cached rawframe hashes checked','frames':124,'gif_duration_seconds':7.75,'source_fps':16,'priority':'BelowNormal' if priority else 'not-set','render_seconds':round(time.perf_counter()-start,3),'whole_timeline':[0,7.6875],'max_head_body_occluded':max(x['head_body_occluded'] for x in rows),'minimum_leg_retention':min(x['leg_retention'] for x in rows),'samples':rows,'fixture_contact_times':[2.2,3.2,4.2,5.2],'game_capture':False,'game_fps_measured':False,'human_feel_verified':False}
 (ROOT/'whole-evidence.json').write_text(json.dumps(out,indent=2)+'\n');print(json.dumps({k:v for k,v in out.items() if k!='samples'}),flush=True)
if __name__=='__main__':main()
