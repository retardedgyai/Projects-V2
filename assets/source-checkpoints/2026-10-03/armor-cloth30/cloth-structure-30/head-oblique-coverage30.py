"""Opaque mesh ray coverage outside the deliberate front facial opening.
This is a geometric diagnostic, not an image renderer or artistic gate.
"""
from pathlib import Path
import json,numpy as np
from PIL import Image
R=Path(__file__).resolve().parent
m=json.loads((R/'model/model.json').read_text());v=np.array(m['vertices']);fs=m['faces'];tri=v[[f['vertices'] for f in fs]];uv=np.array([f['uv'] for f in fs]);ims=[np.array(Image.open(R/'model'/n).convert('RGBA')) for n in ['native.png','new-cloth-boots.png','structure-paint.png']]
e1=tri[:,1]-tri[:,0];e2=tri[:,2]-tri[:,0];normal=np.cross(e1,e2)
aa=(e1*e1).sum(1);bb=(e1*e2).sum(1);cc=(e2*e2).sum(1);den=aa*cc-bb*bb;den=np.where(abs(den)<1e-10,1,den)
records=[]
for label,d in [('front',[0,0,-110]),('right-oblique',[55,12,-103]),('left-oblique',[-55,12,-103]),('right-side',[110,0,0]),('left-side',[-110,0,0])]:
 d=np.array(d,float);d/=np.linalg.norm(d);toward=normal@d;valid=toward>1e-8;safe=np.where(valid,toward,1)
 samples=[]
 for y in np.arange(24.1,32,.25):
  for x in np.arange(4.1,12,.25):
   # Central front opening is intentional; the surrounding corners are not.
   if not (4.4<x<11.6 and 26.2<y<32.2):samples.append(('front',[x,y,4]))
  for z in np.arange(4.1,12,.25):
   if d[0]>.01:samples.append(('right',[12,y,z]))
   if d[0]<-.01:samples.append(('left',[4,y,z]))
 if d[1]>.01:
  for x in np.arange(4.1,12,.25):
   for z in np.arange(4.1,12,.25):samples.append(('top',[x,32,z]))
 exposed=[]
 for side,p in samples:
  p=np.array(p);origin=p+d*100
  t=((origin-tri[:,0])*normal).sum(1)/safe
  hit=origin-t[:,None]*d;q=hit-tri[:,0];qe1=(q*e1).sum(1);qe2=(q*e2).sum(1)
  b=(cc*qe1-bb*qe2)/den;c=(aa*qe2-bb*qe1)/den;a=1-b-c
  mask=valid&(t>=0)&(a>=-1e-8)&(b>=-1e-8)&(c>=-1e-8)
  for i in sorted(np.flatnonzero(mask),key=lambda i:t[i]):
   weights=np.array([a[i],b[i],c[i]]);pu=np.clip(np.floor(weights@uv[i]).astype(int),0,511)
   if ims[fs[i]['texture_index']][pu[1],pu[0],3]!=255:continue
   if fs[i]['piece']=='avatar head':exposed.append({'surface':side,'point':np.round(p,3).tolist()})
   break
 records.append({'view':label,'samples_outside_face_opening':len(samples),'exposed_skin_samples':len(exposed),'exposure_surfaces':{s:sum(e['surface']==s for e in exposed) for s in ['front','left','right','top']},'sample_exposures':exposed[:12]})
out={'method':'opaque front-culling parallel rays; front opening x4.4..11.6,y26.2..32.2 excluded','records':records,'preview_renderer':False,'aesthetic_or_motion_acceptance':False}
(R/'head-oblique-coverage.json').write_text(json.dumps(out,indent=2)+'\n')
print(json.dumps(records))
