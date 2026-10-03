"""Save a reproducible, separate concept snapshot and a safe push allowlist.
Private profiles, connector helpers, old revisions and third-party raw refs excluded.
"""
from pathlib import Path
import json,hashlib,shutil,zipfile,subprocess,sys
R=Path(__file__).resolve().parent;ROOT=R.parent
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
write=lambda p,x:p.write_text(json.dumps(x,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
stage=R/'work/package-root';stage.mkdir(parents=True,exist_ok=True)
relative=[]
own=['README.txt','build30.py','export30.py','verify30.py','head-coverage30.py','head-oblique-coverage30.py','capture-upper30.cjs','capture-minimized30.cjs','finish-upper30.py','launch-native30.ps1','minimize-native30.ps1','close-native30.ps1','package30.py','authoring-provenance.json','verification.json','native-validation.json','head-side-coverage.json','head-oblique-coverage.json','window-state-validation.json','window-minimized-validation.json']
own += [p.name for p in R.glob('ProjectS_cloth30_*.png')]
own += ['model/'+p.name for p in (R/'model').iterdir() if p.is_file()]
own += ['preview64/'+p.name for p in (R/'preview64').glob('*.png')]
own += ['native/'+p.name for p in (R/'native').glob('candidate30-*') if p.suffix in ['.png','.bbmodel']]
own += ['native/verification.json','native/window-normal30-fullback.png','native/window-minimized30-fullback.png','native/window-minimized30-capture.json','native/window-normal-baseline29-front.png','native/baseline29-front-shaded.png','native/window-state-probe.json']
for item in sorted(set(own)):
 src=R/item;dst=stage/'cloth-structure-30'/item;dst.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(src,dst);relative.append(dst.relative_to(stage).as_posix())
inputs=['cloth-volume-29/model/model.json','cloth-volume-29/model/model.bbmodel','cloth-volume-29/model/native.png','cloth-volume-29/model/new-cloth-boots.png','cloth-texture-25/baseline23/UV-provenance.json','cloth-layered-23/mesh_tools.py','cloth-layered-23/check_geometry.py','cloth-layered-23/check23.py']
guard={}
for item in inputs:
 src=ROOT/item;dst=stage/item;dst.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(src,dst);relative.append(item);guard[item]=sha(src)
write(R/'replay-inputs.json',guard);shutil.copyfile(R/'replay-inputs.json',stage/'cloth-structure-30/replay-inputs.json');relative.append('cloth-structure-30/replay-inputs.json')
refs=stage/'cloth-structure-30/references';refs.mkdir(exist_ok=True)
approved=ROOT/'material-reference-28/approved_material_version1.png';assert sha(approved)=='ee452e39cfaaf8cb8a33ac83439e1929a44f367db2172dda0cab2587d3008adb'
shutil.copyfile(approved,refs/'approved_material_v1.png');relative.append('cloth-structure-30/references/approved_material_v1.png')
write(refs/'own-material-source.json',{'primary_reference_library_id':'libfile_a09d9058fed88191a189833a23ae26dc','version':1,'original_filename':'ProjectS_material_same_shape_comparison.png','bytes':approved.stat().st_size,'sha256':sha(approved),'unchanged_byte_copy':True,'new_model_artistic_approval_claimed':False});relative.append('cloth-structure-30/references/own-material-source.json')
manifest={'status':'QUALITY_WORK_CONTINUES_SEPARATE_STATIC_CONCEPT','files':[{'path':p,'bytes':(stage/p).stat().st_size,'sha256':sha(stage/p)} for p in sorted(relative)],'thirdparty_raw_assets_included':False,'app_profiles_auth_helpers_included':False,'primary_material_reference':'libfile_a09d9058fed88191a189833a23ae26dc version1','MatE_equivalence_claimed':False,'game_wear_complete':False}
write(stage/'artifact-manifest.json',manifest);relative.append('artifact-manifest.json')
zpath=R/'ProjectS_cloth30_structure_editable.zip'
with zipfile.ZipFile(zpath,'w',zipfile.ZIP_DEFLATED,compresslevel=6) as z:
 for p in sorted(relative):z.write(stage/p,p)
check=R/'work/portable-check';check.mkdir(exist_ok=True)
with zipfile.ZipFile(zpath) as z:
 assert set(z.namelist())==set(relative)
 for p in relative:assert hashlib.sha256(z.read(p)).hexdigest()==sha(stage/p)
 z.extractall(check)
for script,args in [('build30.py',[]),('verify30.py',['--portable'])]:
 p=subprocess.run([sys.executable,str(check/'cloth-structure-30'/script),*args],cwd=check,capture_output=True,text=True);assert p.returncode==0,p.stderr
names=['model/model.json','model/model.bbmodel','model/model.obj','model/material.mtl','model/native.png','model/new-cloth-boots.png','model/structure-paint.png','authoring-provenance.json']
comparisons=[{'file':n,'bytes_exact':(R/n).read_bytes()==(check/'cloth-structure-30'/n).read_bytes()} for n in names];assert all(x['bytes_exact'] for x in comparisons),comparisons
out={'status':'REPRODUCIBLE_EDITABLE_SAVEPOINT_QUALITY_CONTINUES','ZIP':str(zpath),'bytes':zpath.stat().st_size,'sha256':sha(zpath),'files':len(relative),'ZIP_member_SHA_all_match':True,'self_contained_replay_outputs':comparisons,'neutral_replay_checks_pass':True,'native_frames':18,'MatE_equivalence_claimed':False,'game_wear_complete':False}
write(R/'package-verification.json',out)
allowlist={'local_git_repository':False,'repo':None,'branch':None,'commit':None,'push_owner_thread':'01a1022f-884a-7190-92a6-e08bb1289093','source_root':str(stage),'destination_requirement':'Copy this snapshot under a new isolated directory preserving its relative topology; do not replace approved or previous-source files.','files':[{'path':p,'bytes':(stage/p).stat().st_size,'sha256':sha(stage/p)} for p in sorted(relative)],'archive':{'path':str(zpath),'bytes':zpath.stat().st_size,'sha256':sha(zpath)},'private_helpers_profiles_reference_originals_old_reviews_excluded':True,'push_performed_here':False}
write(R/'push-savepoint-manifest.json',allowlist)
print(json.dumps(out))
