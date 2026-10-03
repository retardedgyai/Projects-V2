"""Root-linked frost, not an opaque floor plate. CPU study compositor, unbound."""
import numpy as np
CELL=1.25
CELLS=tuple((x,z) for x in range(-2,3) for z in range(-2,3) if x*x+z*z<=5)
ROOT=(-1.95,.10)
ROOTS=[(-1.95,.10),(1.90,.45),(-1.19,2.17)]
PATHS=[
 [ROOT,(-1.2,.25),(-.65,-.08),(.05,.22),(.9,.05),(1.90,.45),(2.55,.18),(3.125,.30)],
 [ROOT,(-1.25,.8),(-1.45,1.4),(-1.19,2.17),(-.6,2.45),(.05,3.125)],
 [ROOT,(-1.5,-.55),(-.82,-1.15),(-.52,-1.95),(.2,-2.3),(0,-3.125)],
 [ROOT,(-2.45,-.2),(-3.125,-.55)],
 [ROOT,(-1.7,-.65),(-2.25,-1.25),(-2.7,-1.875)],
 [ROOT,(-1.4,-.55),(-.7,-.9),(.3,-1.15),(1.0,-1.8),(1.5,-2.5),(1.875,-2.75)],
 [ROOT,(-1.2,.25),(-.65,-.08),(.05,.22),(.9,.05),(1.9,.45),(2.1,1.1),(2.65,1.875)],
 [ROOT,(-1.25,.8),(-1.45,1.4),(-1.19,2.17),(-1.7,2.45),(-1.875,2.8)],
 [ROOT,(-1.2,.25),(-.65,-.08),(.05,.22),(.9,.05),(1.5,-.7),(2.2,-1.1),(3.125,-1.0)]
]
def contains(x,z,cells=CELLS,size=CELL):
 return any(abs(x-cx*size)<=size/2+1e-10 and abs(z-cz*size)<=size/2+1e-10 for cx,cz in cells)
def edges(cells=CELLS,size=CELL):
 cells=set(cells);out=[]
 for x,z in sorted(cells):
  lo=np.array([(x-.5)*size,(z-.5)*size]);hi=lo+size
  for dx,dz,p,q in [(0,-1,[lo[0],lo[1]],[hi[0],lo[1]]),(1,0,[hi[0],lo[1]],[hi[0],hi[1]]),(0,1,[hi[0],hi[1]],[lo[0],hi[1]]),(-1,0,[lo[0],hi[1]],[lo[0],lo[1]])]:
   if (x+dx,z+dz) not in cells:out.append({'cell':[x,z],'normal':[dx,dz],'a':p,'b':q})
 return out
def atlas_coords(n=256):
 yy,xx=np.mgrid[:n,:n];x=(xx+.5)/n*6.25-3.125;z=3.125-(yy+.5)/n*6.25
 return np.floor(x/.0625)*.0625+.03125,np.floor(z/.0625)*.0625+.03125
X,Z=atlas_coords()
VEIN=np.full(X.shape,100.);ARRIVAL=np.full(X.shape,100.);ALONG=np.zeros(X.shape)
for path in PATHS:
 cumulative=0
 for a,b in zip(path,path[1:]):
  a=np.array(a);b=np.array(b);v=b-a;length=np.linalg.norm(v)
  p=np.clip(((X-a[0])*v[0]+(Z-a[1])*v[1])/(v@v),0,1)
  distance=np.hypot(X-a[0]-p*v[0],Z-a[1]-p*v[1]);closer=distance<VEIN
  VEIN=np.minimum(VEIN,distance);ALONG=np.where(closer,cumulative+p*length,ALONG)
  cumulative+=length
# Branch arrival along actual root routes; not a expanding disk or shrinking circle.
ARRIVAL=ALONG/8.8+VEIN*.24
ROOTDIST=np.minimum.reduce([np.hypot(X-a,Z-b) for a,b in ROOTS])
GRAIN=(np.floor(X/.125)*17+np.floor(Z/.125)*11).astype(int)%7
WIDTH=(.27+.12*np.sin(ALONG*2.3)**2)/(1+.13*ALONG)
ROOTWIDTH=.60+.13*np.sin(X*5+Z*7)**2
FROST=(VEIN<WIDTH)|(ROOTDIST<ROOTWIDTH)
ROOTBED=ROOTDIST<ROOTWIDTH*.76
COLORS=np.array([[34,59,86],[54,83,120],[104,146,175],[157,191,214],[204,229,237],[239,252,250]],np.uint8)
def valid_mask(cells):
 valid=np.zeros(X.shape,bool)
 for cx,cz in cells:valid|=(abs(X-cx*CELL)<=CELL/2)&(abs(Z-cz*CELL)<=CELL/2)
 return valid
def boundary_geometry(cells):
 distance=np.full(X.shape,100.);width=np.zeros(X.shape)
 for e in edges(cells):
  cx,cz=e['cell'];dx,dz=e['normal']
  inward=CELL/2-((X-cx*CELL)*dx+(Z-cz*CELL)*dz)
  along=(Z-cz*CELL) if dx else (X-cx*CELL)
  local=(abs(along)<=CELL/2+1e-10)&(inward>=0)&(inward<CELL)
  # Unequal frost tongues, independent of a repeating sawtooth period.
  seed=(cx+7)*131+(cz+7)*197+(dx+2)*53+(dz+2)*71
  rng=np.random.default_rng(seed)
  centers=np.array([-.61,-.29,.08,.46])+rng.uniform(-.08,.08,4)
  spans=rng.uniform(.13,.29,4);peaks=rng.uniform(.07,.26,4)
  lobes=np.max(peaks[:,None,None]*np.clip(1-abs(along[None,...]-centers[:,None,None])/spans[:,None,None],0,1),axis=0)
  tooth=.075+lobes+np.clip(.13-VEIN*.30,0,.13)
  choose=local&(inward<distance);width=np.where(choose,tooth,width);distance=np.where(choose,inward,distance)
 return distance,width
def textures(t,contacts=(),cells=CELLS):
 valid=valid_mask(cells);distance,tooth=boundary_geometry(cells)
 # Broad uneven frost faces; no nested outlines running along every branch.
 facets=np.sin(X*3.1+Z*1.7)+.65*np.sin(X*1.2-Z*4.3)
 tone=np.full(X.shape,2);tone[facets>.10]=3
 tone[(VEIN<WIDTH*.70)&(facets>.75)]=4
 tone[ROOTBED]=4;tone[ROOTDIST<.20]=2
 tone[(ROOTDIST>.36)&(ROOTDIST<.48)&(facets>1.0)]=5
 floor=FROST.copy();edge=distance<tooth
 etone=np.full(X.shape,2);etone[(distance>tooth*.55)&(facets>.20)]=3
 etone[(distance>tooth*.75)&(facets>.95)]=4
 etone[distance<.060]=2 # uninterrupted blue exact cutline, no white curb/shadow rail
 if t<.50 or t>=7.28:floor[:]=False;edge[:]=False
 elif t<.80:
  floor&=(VEIN<.062)&(ARRIVAL<(t-.5)*2.7);tone=np.minimum(tone,2)
  edge&=distance<.06
  etone=np.minimum(etone,2)
 elif t<1.45:
  progress=(t-.80)*1.8
  floor&=(ARRIVAL<progress)|((VEIN<.062)&(ARRIVAL<.81))
  tone[(abs(ARRIVAL-progress)<.10)&(VEIN<WIDTH+.10)]=5
  edge&=((ARRIVAL<progress)|(distance<.06))
 elif t>=6.8:
  # Exact active boundary dies immediately; frost breaks locally and roots recede last.
  age=t-6.8;release=.055+np.minimum(ALONG/8,.34)+GRAIN*.025
  floor&=age<release
  tone=np.maximum(tone-2,0);edge&=age<.12;etone=np.maximum(etone-2,0)
 for hit,point in contacts:
  age=t-hit
  if 0<=age<.30 and t<6.8:
   # One grounded fissure runs back to the primary root, never a circular hit stamp.
   a=np.array([point[0],point[2]]);b=np.array(ROOT);v=b-a
   p=np.clip(((X-a[0])*v[0]+(Z-a[1])*v[1])/(v@v),0,1)
   dist=np.hypot(X-a[0]-p*v[0],Z-a[1]-p*v[1]);front=np.clip(age/.16,0,1)
   patch=(dist<.10+.06*(GRAIN<2))&(p<front)&(p>max(0,front-.32))
   floor|=patch;tone[patch]=5
 floor&=valid;edge&=valid
 return tuple(np.concatenate([palette[np.clip(tones,0,5)],np.where(mask,255,0).astype(np.uint8)[...,None]],axis=-1) for palette,tones,mask in [(COLORS,tone,floor),(COLORS,etone,edge)])
def texture(t,contacts=(),cells=CELLS):return textures(t,contacts,cells)[0]
