"""Visual footprint matches the isolated rework's supported rectangle union."""
import numpy as np
CELL=1.25
CELLS=tuple((x,z) for x in range(-2,3) for z in range(-2,3) if x*x+z*z<=5)
ROOT=(-1.95,.10)
PALETTE=np.array([[24,69,115],[31,102,159],[49,142,191],[75,177,215],[124,216,238],[197,247,254]],np.uint8)
PATHS=[[( -1.95,.10),(-1.35,.24),(-.75,-.04),(-.20,.20),(.60,.15),(1.2,.50),(1.90,.45)],
       [(-1.95,.10),(-1.35,-.60),(-.82,-1.10),(-.55,-1.75),(.15,-2.2),(0,-3.125)],
       [(-1.95,.10),(-1.25,.92),(-1.45,1.55),(-1.19,2.17),(-.35,2.55),(.45,3.125)]]
def contains(x,z,cells=CELLS,size=CELL):
 return any(abs(x-cx*size)<=size/2+1e-10 and abs(z-cz*size)<=size/2+1e-10 for cx,cz in cells)
def edges(cells=CELLS,size=CELL):
 cells=set(cells);out=[]
 for x,z in sorted(cells):
  lo=np.array([(x-.5)*size,(z-.5)*size]);hi=lo+size
  for dx,dz,p,q in [(0,-1,[lo[0],lo[1]],[hi[0],lo[1]]),(1,0,[hi[0],lo[1]],[hi[0],hi[1]]),
                    (0,1,[hi[0],hi[1]],[lo[0],hi[1]]),(-1,0,[lo[0],hi[1]],[lo[0],lo[1]])]:
   if (x+dx,z+dz) not in cells:out.append({'cell':[x,z],'normal':[dx,dz],'a':p,'b':q})
 return out

def edge_rect(edge,width,cells=CELLS,size=CELL):
 cx,cz=edge['cell'];dx,dz=edge['normal'];xmin=(cx-.5)*size;xmax=xmin+size;zmin=(cz-.5)*size;zmax=zmin+size
 if dz:return [xmin,zmin if dz<0 else zmax-width,xmax,zmin+width if dz<0 else zmax]
 if (cx,cz-1) not in cells:zmin+=width
 if (cx,cz+1) not in cells:zmax-=width
 return [xmin if dx<0 else xmax-width,zmin,xmin+width if dx<0 else xmax,zmax]
def atlas_coords(n=256):
 yy,xx=np.mgrid[:n,:n];x=(xx+.5)/n*6.25-3.125;z=3.125-(yy+.5)/n*6.25
 return np.floor(x/.0625)*.0625+.03125,np.floor(z/.0625)*.0625+.03125
def path_distance(x,z):
 best=np.full(x.shape,100.)
 for path in PATHS:
  for a,b in zip(path,path[1:]):
   a=np.array(a);b=np.array(b);v=b-a;p=np.clip(((x-a[0])*v[0]+(z-a[1])*v[1])/(v@v),0,1)
   best=np.minimum(best,np.hypot(x-a[0]-p*v[0],z-a[1]-p*v[1]))
 return best
X,Z=atlas_coords();VEIN=path_distance(X,Z);DIST=np.hypot(X-ROOT[0],Z-ROOT[1])
# Unequal broad plates, not a brick/checker grid. Deterministic sites inside the field.
SITES=np.array([[-2.35,-.90],[-.85,-2.35],[1.15,-2.50],[2.40,-.65],[.65,-.30],[-1.05,.45],[-2.30,1.80],[.45,2.00],[2.25,1.55]])
DISTANCES=np.array([(X-s[0])**2+(Z-s[1])**2 for s in SITES]);ORDER=np.argsort(DISTANCES,axis=0)
NEAREST=ORDER[0];NEXT=ORDER[1];NEAR_SITES=SITES[NEAREST];NEXT_SITES=SITES[NEXT]
NEAR_D=np.take_along_axis(DISTANCES,NEAREST[None,...],axis=0)[0];NEXT_D=np.take_along_axis(DISTANCES,NEXT[None,...],axis=0)[0]
JOINT_DISTANCE=(NEXT_D-NEAR_D)/(2*np.maximum(np.linalg.norm(NEXT_SITES-NEAR_SITES,axis=-1),.1))
TONE=2+(NEAREST%3==1).astype(int);JOINT=JOINT_DISTANCE<.032
angle=(NEAREST%7)*.31;cross=(X-NEAR_SITES[...,0])*np.cos(angle)+(Z-NEAR_SITES[...,1])*np.sin(angle)
along=-(X-NEAR_SITES[...,0])*np.sin(angle)+(Z-NEAR_SITES[...,1])*np.cos(angle)
REFLECT=(cross>-.20)&(cross<-.02)&(along>-.42)&(along<.31)
def texture(t,contacts=(),cells=CELLS):
 tone=TONE.copy();tone[JOINT]=0;tone[REFLECT]+=1
 tone[(JOINT_DISTANCE>=.032)&(JOINT_DISTANCE<.09)]=4
 tone[VEIN<.09]=3;tone[VEIN<.030]=4
 alpha=np.where(JOINT & (VEIN>.10),0,255).astype(np.uint8)
 valid=np.zeros(X.shape,bool)
 for cx,cz in cells:valid|=(abs(X-cx*CELL)<=CELL/2)&(abs(Z-cz*CELL)<=CELL/2)
 if .5<=t<.8:
  # Full perimeter is separate; preparation pulls the three broad roots inward.
  alpha=np.where((VEIN<.065)&valid,255,0).astype(np.uint8);tone=np.minimum(tone,3)
 elif .8<=t<1.12:
  front=np.clip((t-.8)/.25,0,1)*5.8
  alpha=np.where((DIST<=front)&valid,alpha,0).astype(np.uint8)
  tone[(abs(DIST-front)<.24)&(VEIN<.26)]=5
 elif t>=6.8:
  # Stop the active cue immediately. Plates release from the outside toward the root.
  closing=np.clip((t-6.8)/.48,0,1)
  alpha=np.where((DIST<5.8*(1-closing))&valid,alpha,0).astype(np.uint8);tone=np.maximum(tone-2,0)
 for hit,point in contacts:
  age=t-hit
  if 0<=age<.30 and t<6.8:
   distance=np.hypot(X-point[0],Z-point[2]);front=.14+age*4.0
   stamp=(distance<front)&(distance>max(0,front-.28))&valid
   tone[stamp]=5;alpha[stamp]=255
   # A broad return through the EXISTING root paths, not a moving UI bracket.
   return_front=np.clip((age-.05)/.15,0,1)
   lane=(VEIN<.14)&(DIST<(1-return_front)*5.8)&(DIST>(1-return_front)*5.8-.7)
   tone[lane]=5
 alpha[~valid]=0
 return np.concatenate([PALETTE[np.clip(tone,0,5)],alpha[...,None]],axis=-1)
