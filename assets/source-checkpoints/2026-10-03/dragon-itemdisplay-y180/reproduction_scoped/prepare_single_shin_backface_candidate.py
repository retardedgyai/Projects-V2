from pathlib import Path
import json,zipfile,copy,hashlib,shutil
R=Path(__file__).resolve().parents[1];S=R/'outputs/reentry_direct_first_step/projects_bundle';O=R/'outputs/actual_game_local_autoplay';D=O/'single_shin_backface_candidate/projects_bundle'
assert not D.exists(),'Preserve an existing candidate; use a new scope'
shutil.copytree(S,D)
with zipfile.ZipFile(S/'pack.zip') as z:payload={n:z.read(n) for n in z.namelist()}
changes=[]
for state in ['normal','hit']:
 name=f'assets/worldseed/models/mobs/dragon_v8_polish_shared_atlas_study.bbmodel/{state}/front_l_shin.json';model=json.loads(payload[name]);before=copy.deepcopy(model)
 changed=0
 for e in model['elements']:
  assert abs(e['from'][2]-e['to'][2])<1e-10
  active=[(k,v) for k,v in e['faces'].items() if abs(v['uv'][2]-v['uv'][0])*abs(v['uv'][3]-v['uv'][1])>1e-10]
  assert len(active)==1 and active[0][0] in ['north','south']
  k,v=active[0];op='south' if k=='north' else 'north';u0,v0,u1,v1=v['uv']
  e['faces'][op]={'uv':[u1,v0,u0,v1],'texture':v['texture']};changed+=1
 for a,b in zip(before['elements'],model['elements']):assert {k:v for k,v in a.items() if k!='faces'}=={k:v for k,v in b.items() if k!='faces'}
 payload[name]=json.dumps(model,separators=(',',':')).encode();changes.append({'item_model':name,'added_reverse_faces':changed})
with zipfile.ZipFile(D/'pack.zip','w',zipfile.ZIP_DEFLATED,compresslevel=6) as z:
 for n,b in payload.items():z.writestr(n,b)
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
manifest=json.loads((D/'manifest.json').read_text());manifest['packSha256']=sha(D/'pack.zip');manifest['files']['pack.zip']=manifest['packSha256'];(D/'manifest.json').write_text(json.dumps(manifest,separators=(',',':')))
protected={str(p.relative_to(S)):sha(p)==sha(D/p.relative_to(S)) for p in S.rglob('*') if p.is_file() and p.name not in ['manifest.json','pack.zip']}
assert all(protected.values())
with zipfile.ZipFile(D/'pack.zip') as z:
 assert z.testzip() is None
 with zipfile.ZipFile(S/'pack.zip') as old:
  differing=[n for n in old.namelist() if old.read(n)!=z.read(n)]
assert differing==[x['item_model'] for x in changes] or set(differing)=={x['item_model'] for x in changes}
report={'scope':'ONE lower foreleg front_l_shin, both normal/hit states, isolated candidate only','root_cause_confirmed':False,'client_tested':False,'new_renderer':False,'entities_added':0,'bones_or_clip_keys_or_vertices_or_rotation_or_textures_modified':False,'changed_item_models':changes,'added_reverse_faces_total':sum(x['added_reverse_faces'] for x in changes),'original_pack_sha256':sha(S/'pack.zip'),'candidate_pack_sha256':sha(D/'pack.zip'),'candidate_pack_bytes':(D/'pack.zip').stat().st_size,'unchanged_nonpack_bundle_files':protected,'next_test':'After creator magic test, same READY pose/camera: compare the one near shin original versus this candidate; inspect whether missing surfaces close. Do not promote to all-body without real comparison.'}
(O/'SINGLE_SHIN_BACKFACE_CANDIDATE_CHECK.json').write_text(json.dumps(report,indent=2));print(json.dumps(report,indent=2))
