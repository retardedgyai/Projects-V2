"""Bounded mesh-surface crossing audit; no renderer, physics, or solid-pass claim."""
import json,time,itertools,numpy as np
import noctveil_v42_pose_tools as p
OUT=p.ROOT/'outputs/v42_path_study/iteration_05';model=json.loads((OUT/'projects_noctveil_v42_wing_arc_candidate.bbmodel').read_text());clip=model['animations'][0]
owners=p.rig.element_owners
parents={}
def walk(g,parent=None):
 parents[g['name']]=parent
 for c in g['children']:
  if isinstance(c,dict):walk(c,g['name'])
walk(model['outliner'][0])
def geometry(clip,t):
 mats=p.skeleton.transforms(model['outliner'][0],clip,t,np.eye(4),{});records=[]
 for e in model['elements']:
  vs=(mats[e['uuid']][:3,:3]@np.array(list(e['vertices'].values())).T).T+mats[e['uuid']][:3,3];keys=list(e['vertices']);tri=[]
  for f in e['faces'].values():
   idx=[keys.index(k) for k in f['vertices']]
   tri.extend(vs[[idx[0],idx[j],idx[j+1]]] for j in range(1,len(idx)-1))
  tri=np.array(tri);records.append({'name':e['name'],'owner':owners[e['uuid']],'vertices':vs,'tri':tri,'lo':vs.min(0),'hi':vs.max(0),'tlo':tri.min(1),'thi':tri.max(1)})
 return records
def sat_pairs(a,b):
 i,j=np.where(np.all(a['tlo'][:,None]<=b['thi'][None]+1e-7,2)&np.all(b['tlo'][None]<=a['thi'][:,None]+1e-7,2))
 if len(i)==0:return []
 A=a['tri'][i];B=b['tri'][j];ea=np.roll(A,-1,axis=1)-A;eb=np.roll(B,-1,axis=1)-B;na=np.cross(ea[:,0],ea[:,1]);nb=np.cross(eb[:,0],eb[:,1])
 axes=np.concatenate([na[:,None],nb[:,None],np.cross(ea[:,:,None],eb[:,None]).reshape(len(A),9,3),np.cross(na[:,None],ea),np.cross(nb[:,None],eb)],1)
 lengths=np.linalg.norm(axes,axis=2);axes/=np.maximum(lengths[:,:,None],1e-20);pa=np.einsum('pkd,pvd->pkv',axes,A);pb=np.einsum('pkd,pvd->pkv',axes,B)
 separated=((pa.max(2)<pb.min(2)-1e-7)|(pb.max(2)<pa.min(2)-1e-7))&(lengths>1e-10);hit=np.where(~separated.any(1))[0]
 return [(A[k],B[k]) for k in hit]
def inside(P,T,tol=1e-7):
 v0,v1=T[1]-T[0],T[2]-T[0];v2=P-T[0];aa,ab,bb=v0@v0,v0@v1,v1@v1;den=aa*bb-ab*ab
 if den<1e-15:return False
 u=(bb*(v2@v0)-ab*(v2@v1))/den;v=(aa*(v2@v1)-ab*(v2@v0))/den
 return u>=-tol and v>=-tol and u+v<=1+tol
def crossing(A,B):
 na=np.cross(A[1]-A[0],A[2]-A[0]);nb=np.cross(B[1]-B[0],B[2]-B[0]);na/=max(np.linalg.norm(na),1e-20);nb/=max(np.linalg.norm(nb),1e-20)
 if np.linalg.norm(np.cross(na,nb))<1e-6:return None # coplanar contacts are explicitly outside this surface-crossing audit
 points=[]
 for T,U,N in [(A,B,nb),(B,A,na)]:
  ds=(T-U[0])@N
  if not(ds.min()<-1e-6 and ds.max()>1e-6):continue # excludes shared-edge/one-sided touches
  for i in range(3):
   j=(i+1)%3
   if ds[i]*ds[j]<-1e-12:
    q=T[i]+(T[j]-T[i])*ds[i]/(ds[i]-ds[j])
    if inside(q,U):points.append(q)
 if len(points)<2:return None
 a,b=max(itertools.combinations(points,2),key=lambda ab:np.linalg.norm(ab[0]-ab[1]));length=float(np.linalg.norm(a-b))
 return {'segment_length':length,'points':[a.tolist(),b.tolist()]} if length>.02 else None
def audit(clip,t):
 r=geometry(clip,t);lo=np.array([x['lo'] for x in r]);hi=np.array([x['hi'] for x in r]);mask=np.all(lo[:,None]<=hi[None]+1e-7,2)&np.all(lo[None]<=hi[:,None]+1e-7,2);i,j=np.where(np.triu(mask,1));hits=[]
 for ia,ib in zip(i,j):
  a,b=r[ia],r[ib]
  if a['owner']==b['owner']:continue # same rigid assembly cannot acquire new internal crossings
  crossings=[c for A,B in sat_pairs(a,b) if (c:=crossing(A,B))]
  if not crossings:continue
  c=max(crossings,key=lambda c:c['segment_length']);oa,ob=a['owner'],b['owner'];hits.append({'a':a['name'],'b':b['name'],'owners':[oa,ob],'directly_adjacent_bones':parents.get(oa)==ob or parents.get(ob)==oa,'attacking_wing_involved':a['name'] in p.wing_names or b['name'] in p.wing_names,'crossing_triangle_pairs':len(crossings),**c})
 return {'progress':t,'broad_phase_mesh_pairs':len(i),'crossings':hits}
start=time.monotonic();baselines=[];rows=[]
for c,t in [(next(a for a in p.rig.source['animations'] if a['name']=='closed_idle_breath_v32'),0),(p.rig.approved,1.1)]:
 row=audit(c,t);baselines.append({'clip':c['name'],**row});print(json.dumps({'baseline':c['name'],'crossing_mesh_pairs':len(row['crossings'])}),flush=True)
baseline_pairs={tuple(sorted((h['a'],h['b']))) for row in baselines for h in row['crossings']}
for t in np.linspace(0,4,17):
 row=audit(clip,float(t));rows.append(row)
 for h in row['crossings']:h['also_present_in_two_sample_original_baseline']=tuple(sorted((h['a'],h['b']))) in baseline_pairs
 (OUT/'v42_self_surface_crossing_partial.json').write_text(json.dumps({'baselines':baselines,'samples':rows})+'\n',encoding='utf8')
 print(json.dumps({'progress':float(t),'crossings':len(row['crossings']),'new_wing_nonadjacent':sum(h['attacking_wing_involved'] and not h['directly_adjacent_bones'] and not h['also_present_in_two_sample_original_baseline'] for h in row['crossings'])}),flush=True)
 if time.monotonic()-start>65:break
result={'model':'outputs/v42_path_study/iteration_05/projects_noctveil_v42_wing_arc_candidate.bbmodel','scope':'All128 meshes, broad-phase AABB and actual noncoplanar triangle-surface crossings.17 finite new-clip poses +2 original-clip baselines. Same rigid-owner pairs skipped as invariant. Coplanar overlap, complete solid containment, swept volume and continuous-time collision NOT proved. Original baseline presence does not imply a crossing is harmless. Adjacent-bone crossings retained/classified, not automatically passed.','baselines':baselines,'samples':rows,'all_requested_samples_complete':len(rows)==17,'elapsed_seconds':time.monotonic()-start,'full_body_self_collision_pass':False,'art_pass':False,'game_connected':False}
(OUT/'v42_self_surface_crossing_audit.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf8')
print(json.dumps({'complete':result['all_requested_samples_complete'],'seconds':result['elapsed_seconds'],'new_wing_nonadjacent_pairs':sorted({tuple(sorted((h['a'],h['b']))) for r in rows for h in r['crossings'] if h['attacking_wing_involved'] and not h['directly_adjacent_bones'] and not h['also_present_in_two_sample_original_baseline']})}))
