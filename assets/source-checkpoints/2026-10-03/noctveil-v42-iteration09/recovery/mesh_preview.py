import numpy as np
from PIL import Image
import preview_bbmodel as old

def element_points(el):
 if el.get('type')=='mesh':return np.array(list(el['vertices'].values()),float)
 lo,hi=el['from'],el['to']
 return np.array([[x,y,z] for x in [lo[0],hi[0]] for y in [lo[1],hi[1]] for z in [lo[2],hi[2]]])

def geometry(data,anim=None,t=0):
 world=old.transforms(data['outliner'][0],anim,t,np.eye(4),{})
 for el in data['elements']:
  mat=world[el['uuid']];pivot=np.array(el.get('origin',[0,0,0]));r=old.rot(el.get('rotation',[0,0,0]))
  def xf(pts):
   pts=(r@(np.array(pts)-pivot).T).T+pivot
   return (mat[:3,:3]@pts.T).T+mat[:3,3]
  if el.get('type')=='mesh':
   for f in el['faces'].values():
    keys=f['vertices'];vs=xf([el['vertices'][k] for k in keys]);uv=np.array([f['uv'][k] for k in keys],float)
    for i in range(1,len(vs)-1):yield vs[[0,i,i+1]],uv[[0,i,i+1]],el
  else:
   lo,hi=np.array(el['from']),np.array(el['to'])
   for s,(*corners,n) in old.FACES.items():
    f=el['faces'][s];a,b,c,d=f['uv']
    vs=xf([[lo[j] if v[j]==0 else hi[j] for j in range(3)] for v in corners]);uv=np.array([[a,b],[c,b],[c,d],[a,d]])
    for inds in ([0,1,2],[0,2,3]):yield vs[inds],uv[inds],el

def project(points,view='hero'):
 if view=='side':return np.stack([points[:,2],-points[:,1],points[:,0]],axis=1)
 if view=='front':return np.stack([-points[:,0],-points[:,1],points[:,2]],axis=1)
 if view=='back':return np.stack([points[:,0],-points[:,1],-points[:,2]],axis=1)
 yaw=np.deg2rad(56 if view=='hero' else -56);elev=np.deg2rad(16)
 right=np.array([np.cos(yaw),0,np.sin(yaw)]);toward=np.array([-np.sin(yaw),0,np.cos(yaw)])
 up=np.array([0,np.cos(elev),0])+toward*np.sin(elev)
 depth=toward*np.cos(elev)-np.array([0,np.sin(elev),0])
 return np.stack([points@right,-points@up,points@depth],axis=1)

def render(data,atlas,anim=None,t=0,view='hero',size=(1100,900),bounds=None,fullbright=False):
 triangles=list(geometry(data,anim,t));ps=[project(v,view) for v,u,e in triangles]
 allpts=np.concatenate(ps)
 if bounds is None:
  lo,hi=allpts.min(axis=0),allpts.max(axis=0);lo[:2]-=3;hi[:2]+=3
 else:lo,hi=bounds
 W,H=size;scale=min(W/(hi[0]-lo[0]),H/(hi[1]-lo[1]));offset=((W-(hi[0]-lo[0])*scale)/2,(H-(hi[1]-lo[1])*scale)/2)
 rgb=np.full((H,W,3),(31,28,35),dtype=np.uint8);depth=np.full((H,W),np.inf)
 for (vs,uv,el),pts in zip(triangles,ps):
  coords=pts.copy();coords[:,0]=(coords[:,0]-lo[0])*scale+offset[0];coords[:,1]=(coords[:,1]-lo[1])*scale+offset[1]
  xlo=max(0,int(coords[:,0].min()));xhi=min(W,int(coords[:,0].max())+2);ylo=max(0,int(coords[:,1].min()));yhi=min(H,int(coords[:,1].max())+2)
  if xhi<=xlo or yhi<=ylo:continue
  a,b,c=coords;v1=b-a;v2=c-a;det=v1[0]*v2[1]-v1[1]*v2[0]
  if abs(det)<1e-7:continue
  yy,xx=np.mgrid[ylo:yhi,xlo:xhi];dx=xx+.5-a[0];dy=yy+.5-a[1]
  u=(dx*v2[1]-dy*v2[0])/det;v=(v1[0]*dy-v1[1]*dx)/det
  inside=(u>=0)&(v>=0)&(u+v<=1)
  z=a[2]+u*v1[2]+v*v2[2]
  ux=uv[0,0]+u*(uv[1,0]-uv[0,0])+v*(uv[2,0]-uv[0,0]);uy=uv[0,1]+u*(uv[1,1]-uv[0,1])+v*(uv[2,1]-uv[0,1])
  tex=atlas[np.clip(np.floor(uy).astype(int),0,atlas.shape[0]-1),np.clip(np.floor(ux).astype(int),0,atlas.shape[1]-1)]
  hit=inside&(tex[...,3]>0)&(z<depth[ylo:yhi,xlo:xhi])
  normal=np.cross(vs[1]-vs[0],vs[2]-vs[0]);normal/=max(np.linalg.norm(normal),1e-9)
  if fullbright or 'light' in el['name'] or 'eye' in el['name'] or 'soul' in el['name']:shade=1.
  else:shade=.77+.14*max(0,normal[1])+.09*max(0,-normal[2])
  rgb[ylo:yhi,xlo:xhi][hit]=np.clip(tex[hit,:3]*shade,0,255).astype(np.uint8)
  depth[ylo:yhi,xlo:xhi][hit]=z[hit]
 return Image.fromarray(rgb)
