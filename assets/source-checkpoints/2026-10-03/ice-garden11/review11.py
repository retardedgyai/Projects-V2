"""Same stone/camera/size comparison; authored art fixture, not game validation."""
from pathlib import Path
import argparse,ctypes,json,time
import numpy as np
from PIL import Image,ImageDraw
from scene11 import render,assets
from baseline10 import render as old
from raster_native import raster
ROOT=Path(__file__).resolve().parent;OUT=ROOT/'preview';OUT.mkdir(exist_ok=True)
def label(im,title):
 out=Image.new('RGB',(im.width,im.height+28),(19,29,35));out.paste(im,(0,28));ImageDraw.Draw(out).text((12,8),title,fill=(220,231,237));return out
def compare(t,name,**kwargs):
 a,_=old(t,w=640,h=360,**kwargs);b,_=render(t,w=640,h=360,**kwargs)
 out=Image.new('RGB',(1280,388));out.paste(label(a,'10 / SAME CAMERA, TERRAIN AND TIME'),(0,0));out.paste(label(b,'11 / UNADOPTED ART PROTOTYPE'),(640,0));out.save(OUT/(name+'.png'));return out
def static():
 rows=[]
 for t,name in [(.66,'anticipation'),(.92,'root-and-main'),(1.06,'rising'),(1.55,'quiet'),(2.26,'contact'),(6.95,'collapse'),(7.57,'clear')]:rows.append(compare(t,name))
 board=Image.new('RGB',(1280,388*len(rows)))
 for i,row in enumerate(rows):board.paste(row,(0,388*i))
 board.save(OUT/'phases-10-to-11.png')
 for bg in ['light','dark']:compare(1.55,'quiet-'+bg,bg=bg)
 compare(1.55,'oblique',view='oblique');compare(2.26,'near',camera={'eye':[0,1.62,-3.8],'target':[0,.25,.2]})
 rows=[]
 for p in [[.48,0,2.65],[2.10,0,2.65]]:
  im,mask=render(1.55,w=640,h=360,contacts=False,actor_point=p,legs_tag=9)
  _,bare=raster(1.55,assets(),[],w=640,h=360,actor_point=p,legs_tag=9)
  rows.append(dict(point=p,head_body_occluded=int(((bare==5)&(mask!=5)).sum()),leg_retention=float((mask==9).sum()/max(1,(bare==9).sum()))))
 (ROOT/'static-evidence.json').write_text(json.dumps({'fixtures':rows,'same_camera':True,'game_test':False},indent=2)+'\n')
 print(json.dumps({'static_comparisons':11,'visibility':rows}),flush=True)
def clip(reuse_baseline=False):
 priority_set=False
 try:
  kernel=ctypes.windll.kernel32;kernel.GetCurrentProcess.restype=ctypes.c_void_p
  kernel.SetPriorityClass.argtypes=[ctypes.c_void_p,ctypes.c_uint];kernel.SetPriorityClass.restype=ctypes.c_int
  priority_set=bool(kernel.SetPriorityClass(kernel.GetCurrentProcess(),0x4000))
 except Exception:pass
 start=time.perf_counter();oldframes=[];newframes=[];samples=[];folder=OUT/'frames';folder.mkdir(exist_ok=True)
 if reuse_baseline:
  import hashlib
  guard=json.loads((ROOT/'protected10.json').read_text());oldroot=ROOT.parent/'ice-garden-living-10'
  for rel,sha in guard['10_files'].items():assert hashlib.sha256((oldroot/rel).read_bytes()).hexdigest()==sha
  for name,count in [('comparison-10-to-11.gif',100),('ending-10-to-11.gif',25)]:
   im=Image.open(OUT/name)
   for i in range(count):im.seek(i);oldframes.append(im.convert('RGB').copy())
 times=[i/25 for i in range(100)]+[6.72+i/25 for i in range(25)]
 for i,t in enumerate(times):
  if not reuse_baseline:a,_=old(t,w=800,h=450)
  b,mask=render(t,w=800,h=450,legs_tag=9)
  _,bare=raster(t,assets(),[],w=800,h=450,legs_tag=9)
  samples.append({'time':t,'head_body_occluded':int(((bare==5)&(mask!=5)).sum()),'leg_retention':float((mask==9).sum()/max(1,(bare==9).sum()))})
  newframes.append(label(b,f'11 / UNADOPTED PROTOTYPE / {t:.2f}s'))
  if not reuse_baseline:oldframes.append(label(a,f'10 / SAME CONDITIONS / {t:.2f}s'))
  b.save(folder/f'{i:03d}.png')
  if i%20==0:print(json.dumps({'completed':i+1,'total':len(times),'time':t}),flush=True)
 newframes[:100][0].save(OUT/'prototype-11.gif',save_all=True,append_images=newframes[1:100],duration=40,loop=0,optimize=False)
 comparison=oldframes[:100]+newframes[:100]
 comparison[0].save(OUT/'comparison-10-to-11.gif',save_all=True,append_images=comparison[1:],duration=40,loop=0,optimize=False)
 ending=oldframes[100:]+newframes[100:]
 ending[0].save(OUT/'ending-10-to-11.gif',save_all=True,append_images=ending[1:],duration=40,loop=0,optimize=False)
 # Every rendered frame appears in numbered chronological inspection boards.
 for starti in range(0,len(newframes),25):
  selected=newframes[starti:starti+25];board=Image.new('RGB',(400*5,239*5),(19,29,35))
  for j,im in enumerate(selected):board.paste(im.resize((400,239),Image.Resampling.NEAREST),(j%5*400,j//5*239))
  board.save(OUT/f'inspection-{starti:03d}.png')
 evidence={'cpu_raster_seconds':round(time.perf_counter()-start,3),'priority':'BelowNormal' if priority_set else 'not-set','one_renderer':True,'frames':len(times),'fps':25,'baseline_reused_from_unchanged10_segment':reuse_baseline,'old_new_same_camera_terrain_resolution':True,'max_head_body_occluded':max(s['head_body_occluded'] for s in samples),'minimum_leg_retention':min(s['leg_retention'] for s in samples),'samples':samples,'actual_game_fps':None,'gpu_rendered':False}
 (ROOT/'render-evidence.json').write_text(json.dumps(evidence,indent=2)+'\n');print(json.dumps({k:v for k,v in evidence.items() if k!='samples'}),flush=True)
if __name__=='__main__':
 parser=argparse.ArgumentParser();parser.add_argument('--clip',action='store_true');parser.add_argument('--reuse-baseline',action='store_true');args=parser.parse_args()
 clip(args.reuse_baseline) if args.clip else static()
