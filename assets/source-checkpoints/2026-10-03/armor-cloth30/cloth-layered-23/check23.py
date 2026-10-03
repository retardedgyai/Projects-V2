"""Actual full-equipment static-body and real-mesh topology/UV verification; no aesthetic pass."""
from pathlib import Path
from collections import Counter,defaultdict
import json,hashlib
import numpy as np
from check_geometry import sat
from mesh_tools import write
R=Path(__file__).resolve().parent
def check(D=None,model=None):
 m=model if model is not None else json.loads((D/'model.json').read_text(encoding='utf8'));v=np.array(m['vertices']);f=m['faces'];tri=v[[x['vertices'] for x in f]];areas=np.linalg.norm(np.cross(tri[:,1]-tri[:,0],tri[:,2]-tri[:,0]),axis=1)/2;assert areas.min()>1e-8
 uv=np.array([x['uv'] for x in f]);cross=(uv[:,1,0]-uv[:,0,0])*(uv[:,2,1]-uv[:,0,1])-(uv[:,1,1]-uv[:,0,1])*(uv[:,2,0]-uv[:,0,0]);assert np.abs(cross).min()>1e-8
 edges=defaultdict(Counter)
 for face in f:
  for a,b in zip(face['vertices'],face['vertices'][1:]+face['vertices'][:1]):edges[face['piece']][tuple(sorted([a,b]))]+=1
 bad={k:sum(n!=2 for n in e.values()) for k,e in edges.items() if k in m['parts']};assert not any(bad.values()),bad
 armor=[x for x in f if x['role']!='avatar'];at=v[[x['vertices'] for x in armor]];hits=[];errors={}
 for box in m['body_boxes']:
  center=np.array(box['center']);half=np.array(box['half']);rotation=np.array(box['rotation']);local=(at-center)@rotation
  ids={i for face in f if face['piece']==box['piece'] for i in face['vertices']};actual=(v[sorted(ids)]-center)@rotation;err=float(np.abs(np.abs(actual)-half).max());assert err<1e-7;errors[box['piece']]=err
  indices=np.flatnonzero(np.all(local.max(1)>=-half,axis=1)&np.all(local.min(1)<=half,axis=1));tt=local[indices];contact=sat(tt,np.zeros(3),half);low=np.zeros(len(tt));high=np.full(len(tt),float(half.min()))
  for _ in range(17):mid=(low+high)/2;inside=sat(tt,np.zeros(3),half-mid[:,None]);low=np.where(inside,mid,low);high=np.where(inside,high,mid)
  for i,depth,yes in zip(indices,low,contact):
   if yes:hits.append({'piece':armor[i]['piece'],'role':armor[i]['role'],'body':box['piece'],'depth':round(float(depth),6),'above0_10':bool(depth>.1)})
 true=[h for h in hits if h['above0_10']];groups=defaultdict(lambda:{'pairs':0,'max_depth':0})
 for h in true:g=groups[h['piece']+' -> '+h['body']];g['pairs']+=1;g['max_depth']=max(g['max_depth'],h['depth'])
 out={'pose':m['body_pose'],'vertices':len(v),'triangles':len(f),'cloth_shells':len(m['parts']),'closed_shell_bad_edges':bad,'degenerate_3D_UV_triangles':0,'minimum_triangle_area':float(areas.min()),'body_OBB_corner_max_errors':errors,'true_body_pairs_above0_10':len(true),'permitted_contacts':len(hits)-len(true),'groups':dict(groups),'all_hits':hits,'actual_leg_equipment_included':True,'aesthetic_pass_claimed':False,'game_wear_complete':False}
 if D is not None:write(D/'geometry-verification.json',out)
 return out
def main():
 samples=json.loads((R/'motion-samples.json').read_text(encoding='utf8'))['samples'];outputs=[check(R/s['directory']) for s in samples]
 atlas=[hashlib.sha256((R/s['directory']/'native.png').read_bytes()).hexdigest() for s in samples];assert len(set(atlas))==1;assert atlas[0]!=hashlib.sha256((R/'baseline22/native.png').read_bytes()).hexdigest()
 guard=R/'protected-inputs.json';protected=json.loads(guard.read_text(encoding='utf8')) if guard.exists() else {};changed=[p for p,h in protected.items() if not Path(p).is_file() or hashlib.sha256(Path(p).read_bytes()).hexdigest()!=h];assert not changed
 write(R/'geometry-summary.json',{'poses':outputs,'same_atlas_all_poses':True,'actual_leg_equipment_included':True,'protected_files':len(protected),'changed':changed,'source_protection_rechecked':guard.exists(),'aesthetic_pass_claimed':False});print(json.dumps([{'pose':p['pose'],'true_pairs':p['true_body_pairs_above0_10'],'contacts':p['permitted_contacts'],'groups':p['groups']} for p in outputs]))
if __name__=='__main__':main()
