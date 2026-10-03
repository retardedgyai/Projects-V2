"""Living frost with persistent two-tone edge fragments and local erosion."""
import numpy as np
from field_math09 import CELL,CELLS,X,Z,VEIN,ALONG,ARRIVAL,ROOTDIST,ROOTBED,FROST,WIDTH,GRAIN,COLORS,valid_mask,boundary_geometry,edges,contains
from layout10 import SWEEPS
def textures(t,contacts=(),cells=CELLS):
 valid=valid_mask(cells);distance,tooth=boundary_geometry(cells)
 facets=np.sin(X*3.1+Z*1.7)+.65*np.sin(X*1.2-Z*4.3)
 tone=np.full(X.shape,2);tone[facets>.10]=3;tone[(VEIN<WIDTH*.70)&(facets>.75)]=4;tone[ROOTBED]=4;tone[ROOTDIST<.20]=2
 tone[(ROOTDIST>.36)&(ROOTDIST<.48)&(facets>1.0)]=5
 floor=FROST.copy();edge=distance<.0625;etone=np.full(X.shape,2)
 # Separate short pale faces and dark bases: contrast survives light and dark ground.
 # Each fragment touches the actual outer edge; neither expands the supported footprint.
 for index,e in enumerate(edges(cells)):
  dx,dz=e['normal'];cx,cz=e['cell'];inward=CELL/2-((X-cx*CELL)*dx+(Z-cz*CELL)*dz)
  along=Z-cz*CELL if dx else X-cx*CELL;rng=np.random.default_rng(991+index*37)
  center=float(rng.uniform(-.18,.18));span=float(rng.uniform(.18,.28));width=float(rng.uniform(.12,.22))
  local=(abs(along-center)<span)&(inward>=0)&(inward<width)
  rough=((np.floor(along/.0625).astype(int)+index)%5)!=0
  patch=local&((inward<width*.68)|rough)
  edge|=patch;etone[patch]=4;etone[patch&(inward<.0625)]=1
  etone[patch&(inward>=.0625)&(inward<width*.54)]=5
 if t<.50 or t>=7.28:floor[:]=False;edge[:]=False
 elif t<.80:
  floor&=(VEIN<.062)&(ARRIVAL<(t-.5)*2.7);tone=np.minimum(tone,2)
  edge&=(distance<.0625)|((ARRIVAL<(t-.5)*2.7)&(distance<.22))
 elif t<1.45:
  progress=(t-.80)*1.8;floor&=(ARRIVAL<progress)|((VEIN<.062)&(ARRIVAL<.81))
  tone[(abs(ARRIVAL-progress)<.10)&(VEIN<WIDTH+.10)]=5
 elif t<6.8:
  # At most one brief moving front. It follows existing root routes, never a ring.
  for begin in SWEEPS:
   age=t-begin
   if 0<=age<1.05:
    front=age*1.34;wave=(abs(ARRIVAL-front)<.060)&(VEIN<WIDTH*.68)
    tone[wave]=5
 elif t>=6.8:
  age=t-6.8
  # Keep the material palette. Coarse connected chunks leave holes in the frost.
  tilex=np.floor(X/.1875);tilez=np.floor(Z/.1875)
  jitter=((tilex*13+tilez*7).astype(int)%5)*.024
  release=.07+np.clip((7.5-ALONG)/7.5,0,1)*.30+jitter
  floor&=age<release
  edge&=(age<(.07+GRAIN*.009))&(distance>=.0625)
 for hit,point in contacts:
  age=t-hit
  if 0<=age<.30 and t<6.8:
   a=np.array([point[0],point[2]]);b=np.array([-1.95,.10]);v=b-a;p=np.clip(((X-a[0])*v[0]+(Z-a[1])*v[1])/(v@v),0,1)
   dist=np.hypot(X-a[0]-p*v[0],Z-a[1]-p*v[1]);front=np.clip(age/.16,0,1)
   patch=(dist<.10+.06*(GRAIN<2))&(p<front)&(p>max(0,front-.32));floor|=patch;tone[patch]=5
 floor&=valid;edge&=valid
 return tuple(np.concatenate([COLORS[np.clip(tones,0,5)],np.where(mask,255,0).astype(np.uint8)[...,None]],axis=-1) for tones,mask in [(tone,floor),(etone,edge)])
