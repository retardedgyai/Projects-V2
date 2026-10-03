"""Only retime the saved clear-sweep candidate. Not a game-ready attack."""
import json,copy,hashlib
from pathlib import Path
root=Path(__file__).resolve().parents[1];source=root/'outputs/v42_path_study/iteration_07/projects_noctveil_v42_wing_arc_candidate.bbmodel';out=root/'outputs/v42_path_study/iteration_08_timing';out.mkdir(exist_ok=True)
model=json.loads(source.read_text());clip=model['animations'][0];clip['name']='wing_arm_sweep_timing_review_v42';clip['length']=2.05;clip['snapping']=60
markers=[(0,.25),(1,.75),(2,.93),(3,1.11),(4,1.55)]
def timing(progress):
 for (a,x),(b,y) in zip(markers,markers[1:]):
  if a<=progress<=b:return x+(y-x)*(progress-a)/(b-a)
 raise ValueError(progress)
for animator in clip['animators'].values():
 channels={k['channel'] for k in animator['keyframes']};extra=[];end=[]
 for channel in channels:
  first=copy.deepcopy(next(k for k in animator['keyframes'] if k['channel']==channel));first['time']=0;first['uuid']+='-outside-hold';extra.append(first)
  last=copy.deepcopy(next(k for k in reversed(animator['keyframes']) if k['channel']==channel));last['time']=2.05;last['uuid']+='-exit-hold';end.append(last)
 for k in animator['keyframes']:k['time']=timing(k['time'])
 animator['keyframes']=extra+animator['keyframes']+end
model['name']=model['model_identifier']='projects_noctveil_v42_timed_sweep_review';path=out/(model['name']+'.bbmodel');path.write_text(json.dumps(model,separators=(',',':'))+'\n',encoding='utf8')
result={'model':str(path.relative_to(root)),'sha256':hashlib.sha256(path.read_bytes()).hexdigest(),'source_candidate':str(source.relative_to(root)),'scope':'A provisional2.05-second whole forward sweep from outside-load through opposite-side exit, with0.25-second load hold and0.50-second exit hold. Same exact saved poses; original3 clips retained. Return to idle, state transition, final timing, game hitbox/damage and combat balance NOT approved or verified.','progress_time_markers':markers,'body_counterstep_after_first_hook_contact':True,'full_motion_preview_authorized':True,'heavy_render_authorized':False,'art_pass':False,'game_connected':False}
(out/'v42_timing_review_evidence.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf8');print(json.dumps(result))
