from pathlib import Path
import json,hashlib
R=Path(__file__).resolve().parents[1];O=R/'outputs/reentry_direct_first_step';B=O/'projects_bundle';mid='dragon_v8_polish_shared_atlas_study.bbmodel'
BASE=R/'outputs/locomotion_reentry_isolation/projects_bundle'
old=json.loads((BASE/f'models/{mid}/model.animation.json').read_text())
add=json.loads((O/'WSEE_FIRST_STEP.json').read_text());assert len(old['animations'])==13 and len(add['animations'])==1
merged={**old,'animations':{**old['animations'],**add['animations']}}
assert all(merged['animations'][n]==v for n,v in old['animations'].items())
(B/f'models/{mid}/model.animation.json').write_text(json.dumps(merged,separators=(',',':')),encoding='utf8')
name=next(iter(add['animations']));assert len(add['animations'][name]['bones'])==34
catalog=json.loads((B/'catalog.json').read_text());catalog[mid]['animations'][name]=2.1
(B/'catalog.json').write_text(json.dumps(catalog,separators=(',',':')),encoding='utf8')
ref=json.loads((B/'motion_reference.json').read_text());ref.update(resume_before_clip=ref['resume_clip'],resume_before_seconds=ref['resume_seconds'],resume_before_entry_phase=ref['resume_entry_phase'],resume_clip=name,resume_seconds=2.1,resume_entry_phase=0.,resume_actor_travel_BB=11.4,resume_source_acceleration_seconds=1.35,resume_actor_starts_moving_immediately=True,resume_source_rate_formula='commanded_speed/nominalSpeed; accepted root distance inverts profile',resume_acceleration_seconds_after_join=0.,actual_existing_runtime_animation_count=14,native_full_visual_pending=True)
(B/'motion_reference.json').write_text(json.dumps(ref,indent=2),encoding='utf8')
def sha(path):
    with path.open('rb') as f:return hashlib.file_digest(f,'sha256').hexdigest()
for p in ['pack.zip',f'models/{mid}/model.geo.json']:
    assert sha(B/p)==sha(BASE/p),p
manifest={'packSha256':sha(B/'pack.zip'),'modelCount':1,'models':[mid],'files':{p.relative_to(B).as_posix():sha(p) for p in sorted(B.rglob('*')) if p.is_file() and p.name!='manifest.json'}}
(B/'manifest.json').write_text(json.dumps(manifest,separators=(',',':')),encoding='utf8')
print('NEW_FIRST_STEP_14CLIPS_PRIOR13_IDENTICAL_34BONES_PACK_GEOMETRY_UNCHANGED',flush=True)
