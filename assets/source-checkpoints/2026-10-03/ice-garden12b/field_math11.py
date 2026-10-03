"""Thin dark support paths; quiet hold. Exact supported boundary retained from10."""
import numpy as np
from field_math09 import CELL,CELLS,X,Z,GRAIN,PATHS,valid_mask,boundary_geometry,edges,contains
from field_math10 import textures as old_textures
VEIN=np.full(X.shape,100.);ALONG=np.zeros(X.shape)
for original in PATHS:
 path=[(2.30,.45) if abs(a-1.90)<1e-8 and abs(b-.45)<1e-8 else (a,b) for a,b in original];cumulative=0
 for a,b in zip(path,path[1:]):
  a,b=np.array(a),np.array(b);v=b-a;length=np.linalg.norm(v)
  q=np.clip(((X-a[0])*v[0]+(Z-a[1])*v[1])/(v@v),0,1);distance=np.hypot(X-a[0]-q*v[0],Z-a[1]-q*v[1]);closer=distance<VEIN
  VEIN=np.minimum(VEIN,distance);ALONG=np.where(closer,cumulative+q*length,ALONG);cumulative+=length
ARRIVAL=ALONG/8.8+VEIN*.24
ROOTDIST=np.minimum.reduce([np.hypot(X-a,Z-b) for a,b in [(-1.95,.10),(2.30,.45),(-1.19,2.17)]])
COLORS=np.array([[25,50,83],[34,69,108],[51,97,138],[80,137,177],[145,202,228],[214,247,252]],np.uint8)
def textures(t,contacts=(),cells=CELLS):
 _,fringe=old_textures(t,[],cells)
 valid=valid_mask(cells);width=.054+.016*(GRAIN<2)
 floor=(VEIN<width)|(ROOTDIST<.19)
 tone=np.full(X.shape,1);tone[(VEIN<.032)&(GRAIN<2)]=2;tone[ROOTDIST<.14]=0
 if t<.5 or t>=7.05:floor[:]=False
 elif t<.8:floor&=(ARRIVAL<(t-.5)*2.7);tone[:]=1
 elif t<1.33:
  progress=(t-.8)*1.90;floor&=ARRIVAL<progress
  tone[(abs(ARRIVAL-progress)<.08)&floor]=3
 elif t>=6.8:
  age=t-6.8;release=.055+np.clip((6.2-ALONG)/6.2,0,1)*.16+GRAIN*.008
  floor&=age<release
 # Compact one-contact light at the confirmed foot. No return wave over the whole bed.
 for hit,point in contacts:
  age=t-hit
  if 0<=age<.18 and t<6.8 and contains(point[0],point[2],cells):
   dx=X-point[0];dz=Z-point[2];local=(abs(dx)<.32)&(abs(dz)<.20)&((abs(dx)>.08)|(abs(dz)>.055))
   floor|=local;tone[local]=5 if age<.085 else 3
 floor&=valid
 rgba=np.concatenate([COLORS[tone],np.where(floor,255,0).astype(np.uint8)[...,None]],axis=-1)
 return rgba,fringe
