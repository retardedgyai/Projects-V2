"""Finite small CPU geometry poses from final BB keys. No full animation render."""
from pathlib import Path
import json,base64,io,numpy as np
from PIL import Image,ImageDraw,ImageFont
import mesh_preview as mesh
import author_reentry_direct_first_step as s
M=json.loads((s.O/'dragon_v8_MOVING_FIRST_STEP_TRANSITION_ONLY.bbmodel').read_text())
T=s.common.tables(M['animations'][0]);atlas=np.array(Image.open(io.BytesIO(base64.b64decode(M['textures'][0]['source'].split(',',1)[1]))).convert('RGBA'))
OWN={}
def own(node):
    for v in node.get('children',[]):
        if isinstance(v,dict):own(v)
        else:OWN[v]=node['name']
for n in M['outliner']:own(n)
CURRENT={}
def geometry(data,anim=None,t=0):
    for el in data['elements']:
        mat=CURRENT[OWN[el['uuid']]];pivot=np.array(el.get('origin',[0,0,0]));r=mesh.old.rot(el.get('rotation',[0,0,0]))
        def xf(pts):
            pts=(r@(np.array(pts)-pivot).T).T+pivot
            return (mat[:3,:3]@pts.T).T+mat[:3,3]
        if el.get('type')=='mesh':
            for f in el['faces'].values():
                keys=f['vertices'];vs=xf([el['vertices'][k] for k in keys]);uv=np.array([f['uv'][k] for k in keys],float)
                for i in range(1,len(vs)-1):yield vs[[0,i,i+1]],uv[[0,i,i+1]],el
        else:
            lo,hi=np.array(el['from']),np.array(el['to'])
            for k,(*corners,n) in mesh.old.FACES.items():
                f=el['faces'][k];a,b,c,d=f['uv'];vs=xf([[lo[j] if v[j]==0 else hi[j] for j in range(3)] for v in corners]);uv=np.array([[a,b],[c,b],[c,d],[a,d]])
                for ids in ([0,1,2],[0,2,3]):yield vs[ids],uv[ids],el
mesh.geometry=geometry
TIMES=[0.,.40,.80,1.20,1.48,1.78,2.,2.10]
W,H=400,286;font=ImageFont.truetype('C:/Windows/Fonts/meiryo.ttc',13)
samples=[]
for t in TIMES:
    ms=s.common.fk(T,t)
    for v in ms.values():v[:3,3]-=[0,0,s.travel(t)]
    samples.append(ms)
for view in ['side','hero']:
    projected=[]
    for ms in samples:
        CURRENT=ms;projected.extend(mesh.project(v,view) for v,uv,el in geometry(M))
    pts=np.concatenate(projected);lo,hi=pts.min(0),pts.max(0);lo[:2]-=6;hi[:2]+=6
    sheet=Image.new('RGB',(4*W,2*H+44),'#1f1c23');d=ImageDraw.Draw(sheet)
    d.text((12,8),'MOVING FIRST STEP | saved BB keys | CPU geometry poses, NOT native GUI/client',font=font,fill='white')
    for i,(t,ms) in enumerate(zip(TIMES,samples)):
        CURRENT=ms;im=mesh.render(M,atlas,None,t,view,(W,H-40),(lo,hi));x=(i%4)*W;y=44+(i//4)*H
        sheet.paste(im,(x,y+24));d.text((x+10,y),f'{t:.2f}s source | actor {s.travel(t):.2f} BB forward',font=font,fill='#c7d4dc')
        d.text((x+10,y+H-16),'Fixed camera / same size / original geometry + pixels',fill='#b1b7c2')
    path=s.O/f'LIGHT_FIRST_STEP_POSES_{view.upper()}.png';sheet.save(path);print('SAVED_LIGHT_POSES',path.name,flush=True)
