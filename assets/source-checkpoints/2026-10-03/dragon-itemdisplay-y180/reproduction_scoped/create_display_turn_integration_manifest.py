from pathlib import Path
import hashlib,json
R=Path(__file__).resolve().parents[1];O=R/'outputs/actual_game_local_autoplay';C=O/'item_display_turn_compensation_candidate/projects_bundle'
files=[O/'ITEM_DISPLAY_TURN_COMPENSATION_CANDIDATE_CHECK.json',O/'saved_surface_diagnosis/PRIMARY_ITEMDISPLAY_Y180_CAUSE_AND_COMPENSATION_AUDIT.json',O/'saved_surface_diagnosis/HANDOFF_DISPLAY_TURN_CAUSE_JA.txt']
files+=sorted(p for p in C.rglob('*') if p.is_file())
script_names=['prepare_item_display_turn_compensation.py','render_saved_item_display_turn_diagnosis.py','audit_saved_leg_culling.py','audit_actual_ready_display_geometry.py']
files+=[R/'work'/n for n in script_names]
payload=[]
for p in files:
 payload.append({'source_path':str(p.resolve()),'task4_relative_path':str(p.relative_to(R)),'bytes':p.stat().st_size,'sha256':hashlib.sha256(p.read_bytes()).hexdigest(),'classification':'unverified_display_fix_study_and_reproduction'})
manifest={'source_root':str(R),'git_repository':False,'branch':None,'commit':None,'push_owner':'Parent central integration task 01a1022f-884a-7190-92a6-e08bb1289093','new_push_authorization_acknowledged':True,'this_worker_repo_mutation_push_merge_deploy':False,'game_ready':False,'candidate_client_tested':False,'old_source_and_existing_walk_claw_stop_reentry_keys_unchanged':True,'approved_editable_source_reference':str(R/'outputs/reentry_direct_first_step/dragon_v8_REENTRY_OLD_NEW_WALK26_3CLIPS.bbmodel'),'files':payload,'excluded_from_git_candidates':['work/actual_game_local_autoplay/dragon_vanilla_private.args','work/actual_game_local_autoplay/client_profile','full/large GIF and captured frames','diagnostic ZIP','third party reference originals','single_shin_backface_candidate (weak alternative retained only as diagnosis history)'],'required_next_game_check':'After magic play test: fixed-camera original vs compensated pack READY, then existing walk/stop/claw/reentry; no key changes.'}
(O/'INTEGRATION_SAVED_POINT_MANIFEST.json').write_text(json.dumps(manifest,indent=2),encoding='utf8');print(json.dumps({'non_git':True,'curated_files':len(payload),'candidate_pack_bytes':(C/'pack.zip').stat().st_size,'game_ready':False}))
