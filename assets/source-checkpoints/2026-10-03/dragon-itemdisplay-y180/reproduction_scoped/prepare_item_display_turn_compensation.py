from pathlib import Path
import json,zipfile,copy,hashlib,shutil
R=Path(__file__).resolve().parents[1];S=R/'outputs/reentry_direct_first_step/projects_bundle';O=R/'outputs/actual_game_local_autoplay';D=O/'item_display_turn_compensation_candidate/projects_bundle'
assert not D.exists(),'Preserve an existing candidate'
shutil.copytree(S,D)
with zipfile.ZipFile(S/'pack.zip') as z:payload={n:z.read(n) for n in z.namelist()}
changes=[]
for name in payload:
 if '/models/mobs/dragon_v8_polish_shared_atlas_study.bbmodel/' not in name or not name.endswith('.json'):continue
 model=json.loads(payload[name]);old=copy.deepcopy(model);fixed=model['display']['fixed'];assert fixed.get('rotation',[0,0,0])==[0,0,0]
 fixed['translation']=[v*s for v,s in zip(fixed['translation'],[-1,1,-1])]
 fixed['scale']=[v*s for v,s in zip(fixed['scale'],[-1,1,-1])]
 assert model['elements']==old['elements'] and model['textures']==old['textures']
 changes.append({'item_model':name,'before_fixed':old['display']['fixed'],'after_fixed':fixed})
 payload[name]=json.dumps(model,separators=(',',':')).encode()
assert len(changes)==58
with zipfile.ZipFile(D/'pack.zip','w',zipfile.ZIP_DEFLATED,compresslevel=6) as z:
 for n,b in payload.items():z.writestr(n,b)
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
manifest=json.loads((D/'manifest.json').read_text());manifest['packSha256']=sha(D/'pack.zip');manifest['files']['pack.zip']=manifest['packSha256'];(D/'manifest.json').write_text(json.dumps(manifest,separators=(',',':')))
protected={str(p.relative_to(S)):sha(p)==sha(D/p.relative_to(S)) for p in S.rglob('*') if p.is_file() and p.name not in ['manifest.json','pack.zip']};assert all(protected.values())
with zipfile.ZipFile(D/'pack.zip') as z:
 assert z.testzip() is None
 with zipfile.ZipFile(S/'pack.zip') as old:assert set(n for n in old.namelist() if old.read(n)!=z.read(n))=={c['item_model'] for c in changes}
report={'scope':'Fixed ItemDisplay context only,58 model files normal/hit','primary_evidence':'Vanilla26.2 DisplayRenderer$ItemDisplayRenderer.submitInner calls Axis.YP.rotation(3.1415927f) before ItemStackRenderState.submit','matrix_identity':'J * D_new = D_old; J=diag(-1,1,-1); D_new translation=J*Told and scale=J*Sold','missing_client_half_turn_in_previous_CPU_checks':True,'client_tested':False,'new_renderer':False,'entities_added':0,'source_bones_clip_keys_vertices_UV_textures_unchanged':True,'candidate_pack_sha256':sha(D/'pack.zip'),'candidate_pack_bytes':(D/'pack.zip').stat().st_size,'unchanged_nonpack_bundle_files':protected,'changes':changes}
(O/'ITEM_DISPLAY_TURN_COMPENSATION_CANDIDATE_CHECK.json').write_text(json.dumps(report,indent=2));print(json.dumps({k:v for k,v in report.items() if k!='changes'},indent=2))
