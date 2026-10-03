"""Actual opaque front-facing mesh coverage of lateral head skin.
Ray lookup only, not a replacement preview renderer or aesthetic gate.
"""
from pathlib import Path
import json,numpy as np
from PIL import Image
R=Path(__file__).resolve().parent
def read(p):return json.loads(p.read_text())
models=[('29',R.parent/'cloth-volume-29/model'),('30',R/'model')];out=[]
for label,root in models:
 m=read(root/'model.json');v=np.array(m['vertices']);fs=m['faces'];tri=v[[f['vertices'] for f in fs]];uv=np.array([f['uv'] for f in fs]);names=['native.png','new-cloth-boots.png']+(['structure-paint.png'] if label=='30' else []);ims=[np.array(Image.open(root/n).convert('RGBA')) for n in names]
 normal=np.cross(tri[:,1]-tri[:,0],tri[:,2]-tri[:,0]);a=tri[:,0,1:];e1=tri[:,1,1:]-a;e2=tri[:,2,1:]-a;den=e1[:,0]*e2[:,1]-e1[:,1]*e2[:,0];valid=np.abs(den)>1e-8;safe=np.where(valid,den,1);exposed=[];samples=0
 for side,sign in [('left',-1),('right',1)]:
  for y in np.arange(26.65,31.6,.2):
   for z in np.arange(4.25,11.8,.2):
    q=np.array([y,z])-a;b=(q[:,0]*e2[:,1]-q[:,1]*e2[:,0])/safe;c=(e1[:,0]*q[:,1]-e1[:,1]*q[:,0])/safe;aa=1-b-c;mask=valid&(normal[:,0]*sign>0)&(aa>=-1e-8)&(b>=-1e-8)&(c>=-1e-8)
    hits=[]
    for i in np.flatnonzero(mask):
     weight=np.array([aa[i],b[i],c[i]]);p=weight@tri[i];uu=np.floor(weight@uv[i]).astype(int);uu=np.clip(uu,0,511);im=ims[fs[i]['texture_index']]
     if im[uu[1],uu[0],3]==255:hits.append((float(-p[0]*sign),fs[i]['piece'],fs[i]['role']))
    hits.sort();samples+=1
    if hits and hits[0][1]=='avatar head':exposed.append({'side':side,'y':round(float(y),2),'z':round(float(z),2)})
 record={'model':label,'head_side_grid_samples':samples,'visible_avatar_head_samples':len(exposed),'sample_exposures':exposed[:30]};out.append(record)
(R/'head-side-coverage.json').write_text(json.dumps({'method':'front-culling opaque mesh rays from +/-X through y26.65..31.55, z4.25..11.65; deliberate facial opening z<4 excluded','records':out,'dynamic_motion_certified':False,'aesthetic_pass_claimed':False},indent=2)+'\n')
print(json.dumps(out))
