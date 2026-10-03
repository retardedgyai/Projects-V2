"""One contact, same camera/stone/path. No game operation or screenshot paintover."""
from pathlib import Path
import argparse,ctypes,json,time
import numpy as np
from PIL import Image,ImageDraw
from scene12 import render as old
from scene13 import render,assets
from raster_native import raster
ROOT=Path(__file__).resolve().parent;OUT=ROOT/'preview'
def label(im,title):
 out=Image.new('RGB',(im.width,im.height+28),(19,29,35));out.paste(im,(0,28));ImageDraw.Draw(out).text((10,8),title,fill=(222,238,245));return out
def compare(age,name,**kwargs):
 t=2.2+age;a,_=old(t,w=640,h=360,**kwargs);b,_=render(t,w=640,h=360,**kwargs)
 out=Image.new('RGB',(1280,388));out.paste(label(a,f'12b / CONTACT {age:+.3f}s'),(0,0));out.paste(label(b,f'13c / CONTACT ONLY / {age:+.3f}s'),(640,0));out.save(OUT/(name+'.png'));return out
def static():
 rows=[]
 for age in [-.04,.00,.02,.04,.07,.11,.17,.25,.34,.45,.48]:rows.append(compare(age,'contact-'+str(round(age*1000))))
 board=Image.new('RGB',(1280,388*len(rows)))
 for i,im in enumerate(rows):board.paste(im,(0,388*i))
 board.save(OUT/'contact-phases.png')
 for name,kwargs in [('oblique',dict(view='oblique')),('near',dict(camera={'eye':[0,1.62,-3.8],'target':[0,.25,.2]})),('light',dict(bg='light')),('dark',dict(bg='dark'))]:compare(.07,name,**kwargs)
 print(json.dumps({'contact_static_comparisons':15,'game_started':False}),flush=True)
def clip():
 k=ctypes.windll.kernel32;k.GetCurrentProcess.restype=ctypes.c_void_p;k.SetPriorityClass.argtypes=[ctypes.c_void_p,ctypes.c_uint];priority=bool(k.SetPriorityClass(k.GetCurrentProcess(),0x4000))
 start=time.perf_counter();aa=[];bb=[];rows=[];folder=OUT/'frames';folder.mkdir(exist_ok=True)
 # 0.8s real-time window at40fps: antecedent, split, peak pause, fall and clear.
 times=[1.98+i*.025 for i in range(32)]
 for i,t in enumerate(times):
  a,_=old(t,w=640,h=360);b,mask=render(t,w=640,h=360,legs_tag=9);_,bare=raster(t,assets(),[],w=640,h=360,legs_tag=9)
  rows.append({'time':round(t,3),'age':round(t-2.2,3),'head_body_occluded':int(((bare==5)&(mask!=5)).sum()),'leg_retention':float((mask==9).sum()/max(1,(bare==9).sum()))})
  aa.append(label(a,f'12b / CONTACT {t-2.2:+.3f}s'));bb.append(label(b,f'13c / CONTACT ONLY / {t-2.2:+.3f}s'));b.save(folder/f'{i:03d}.png')
  if i%12==0:print(json.dumps({'rendered':i+1,'total':len(times)}),flush=True)
 # GIF 10ms clock: alternate20/30ms to preserve exactly40fps elapsed timing.
 duration=[20 if i%2==0 else 30 for i in range(32)]
 bb[0].save(OUT/'contact-13.gif',save_all=True,append_images=bb[1:],duration=duration,loop=0,optimize=False)
 allframes=aa+bb;allframes[0].save(OUT/'12b-to-13-contact.gif',save_all=True,append_images=allframes[1:],duration=duration*2,loop=0,optimize=False)
 for begin in [0,16]:
  board=Image.new('RGB',(2000,239*5),(19,29,35))
  for j,im in enumerate(bb[begin:begin+16]):board.paste(im.resize((400,239),Image.Resampling.NEAREST),(j%5*400,j//5*239))
  board.save(OUT/f'inspection-{begin:03d}.png')
 out={'render_seconds':round(time.perf_counter()-start,3),'priority':'BelowNormal' if priority else 'not-set','frames':32,'fps':40,'max_head_body_occluded':max(x['head_body_occluded'] for x in rows),'minimum_leg_retention':min(x['leg_retention'] for x in rows),'samples':rows,'game_capture':False,'gpu_performance_verified':False}
 (ROOT/'render-evidence.json').write_text(json.dumps(out,indent=2)+'\n');print(json.dumps({k:v for k,v in out.items() if k!='samples'}),flush=True)
if __name__=='__main__':
 k=ctypes.windll.kernel32;k.GetCurrentProcess.restype=ctypes.c_void_p;k.SetPriorityClass.argtypes=[ctypes.c_void_p,ctypes.c_uint];k.SetPriorityClass(k.GetCurrentProcess(),0x4000)
 parser=argparse.ArgumentParser();parser.add_argument('--clip',action='store_true');args=parser.parse_args();clip() if args.clip else static()
