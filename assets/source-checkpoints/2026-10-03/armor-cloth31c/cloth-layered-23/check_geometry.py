"""Finite static mesh/UV/body checks; no image renderer, no all-motion claims."""
from pathlib import Path
from collections import defaultdict,Counter
import json,numpy as np
from mesh_tools import write,rotate
R=Path(__file__).resolve().parent
def sat(tris,center,half):
 if not len(tris):return np.zeros(0,bool)
 p=tris-center;e=np.stack([p[:,1]-p[:,0],p[:,2]-p[:,1],p[:,0]-p[:,2]],axis=1)
 axes=np.concatenate([np.tile(np.eye(3),(len(p),1,1)),np.cross(e[:,0],e[:,1])[:,None,:],np.cross(e[:,:,None,:],np.eye(3)[None,None,:,:]).reshape(-1,9,3)],axis=1)
 dots=np.einsum('nvi,nai->nva',p,axes);rad=np.einsum('nai,ni->na',np.abs(axes),np.broadcast_to(half,(len(p),3)))
 return ~((dots.min(1)>rad+1e-9)|(dots.max(1)<-rad-1e-9)).any(1)
def verify_pose(D):
 m=json.loads((D/'model.json').read_text(encoding='utf8'));v=np.array(m['vertices']);f=m['faces'];tris=v[[x['vertices'] for x in f]];area=np.linalg.norm(np.cross(tris[:,1]-tris[:,0],tris[:,2]-tris[:,0]),axis=1)/2
 assert area.min()>1e-8
 uv=np.array([x['uv'] for x in f]);cross=(uv[:,1,0]-uv[:,0,0])*(uv[:,2,1]-uv[:,0,1])-(uv[:,1,1]-uv[:,0,1])*(uv[:,2,0]-uv[:,0,0]);assert np.min(np.abs(cross))>1e-8
 edges=defaultdict(Counter)
 for face in f:
  for a,b in zip(face['vertices'],face['vertices'][1:]+face['vertices'][:1]):edges[face['piece']][tuple(sorted([a,b]))]+=1
 invalid={k:sum(n!=2 for n in ee.values()) for k,ee in edges.items() if k in m['parts']};assert not any(invalid.values()),invalid
 boxes=defaultdict(set)
 for face in f:
  if face['role']=='avatar':boxes[face['piece']].update(face['vertices'])
 armor=[x for x in f if x['role']!='avatar'];at=v[[x['vertices'] for x in armor]];hits=[]
 for part,ids in boxes.items():
  body=v[sorted(ids)];local=at.copy()
  if 'arm' in part or 'hand' in part:
   right=part.startswith('right');origin=[14 if right else 2,24,8];angle=m['body_arm_angles']['right' if right else 'left'];body=rotate(body,origin,-angle);local=rotate(local.reshape(-1,3),origin,-angle).reshape(-1,3,3)
  elif 'leg' in part or 'foot' in part:
   right=part.startswith('right');origin=[10 if right else 6,12,8];angle=5 if right else -5;body=rotate(body,origin,-angle);local=rotate(local.reshape(-1,3),origin,-angle).reshape(-1,3,3)
  lo=body.min(0);hi=body.max(0);center=(lo+hi)/2;half=(hi-lo)/2
  candidates=np.flatnonzero(np.all(local.max(1)>=lo,axis=1)&np.all(local.min(1)<=hi,axis=1));tt=local[candidates];contact=sat(tt,center,half);low=np.zeros(len(tt));high=np.full(len(tt),float(min(half)))
  for _ in range(17):mid=(low+high)/2;inside=sat(tt,center,half-mid[:,None]);low=np.where(inside,mid,low);high=np.where(inside,high,mid)
  for i,depth,yes in zip(candidates,low,contact):
   if yes:hits.append({'piece':armor[i]['piece'],'body':part,'triangle':int(i),'depth':round(float(depth),6),'true_penetration':bool(depth>.1)})
 true=[h for h in hits if h['true_penetration']]
 out={'vertices':len(v),'triangles':len(f),'cloth_shells':len(m['parts']),'closed_shell_bad_edges':invalid,'degenerate_triangles':0,'UV_degenerate_triangles':0,'min_triangle_area':float(area.min()),'static_body_contact_allowance_units':.1,'static_true_penetration_triangle_body_pairs':len(true),'static_contact_triangle_body_pairs':len(hits)-len(true),'true_hits':true,'all_hits':hits,'game_wear_complete':False,'dynamic_movement_tested':False,'garment_self_collision_tested':False,'cloth_thickness':sorted(set(p['thickness'] for p in m['parts'].values()))}
 out['pose']=m['body_pose'];write(D/'geometry-verification.json',out);return out
def main():
 out=[verify_pose(R/name) for name in ['model','pose-casting']];write(R/'geometry-verification.json',{'poses':out,'no_dynamic_simulation':True})
 print(json.dumps([{k:x for k,x in r.items() if k not in ['all_hits','true_hits','closed_shell_bad_edges']} for r in out]))
if __name__=='__main__':main()
