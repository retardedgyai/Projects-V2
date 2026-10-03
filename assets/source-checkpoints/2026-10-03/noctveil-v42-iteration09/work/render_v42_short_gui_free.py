"""Existing lightweight software preview; no Blockbench or new renderer."""
import sys,json,copy,base64,io,numpy as np
from pathlib import Path
from PIL import Image,ImageDraw,ImageFont
import preview_bbmodel as skeleton
import mesh_preview
root=Path(__file__).resolve().parents[1];iteration=sys.argv[1] if len(sys.argv)>1 else 'iteration_08_timing';out=root/'outputs/v42_path_study'/iteration;model=json.loads((out/'projects_noctveil_v42_timed_sweep_review.bbmodel').read_text());clip=model['animations'][0]
atlas=np.concatenate([np.array(Image.open(io.BytesIO(base64.b64decode(t['source'].split(',',1)[1]))).convert('RGBA')) for t in model['textures']],axis=0);rd=copy.deepcopy(model)
for e in rd['elements']:
 for face in e['faces'].values():
  if face.get('texture')==1:
   for uv in face['uv'].values():uv[1]+=512
times=[i/12 for i in range(25)];worlds=[];view='front';box=np.array([[x,y,z] for x in [-5,5] for y in [0,18] for z in [-32,-28]])
for t in times:
 mats=skeleton.transforms(model['outliner'][0],clip,t,np.eye(4),{});worlds.append(np.concatenate([(mats[e['uuid']][:3,:3]@np.array(list(e['vertices'].values())).T).T+mats[e['uuid']][:3,3] for e in model['elements']]))
points=np.concatenate([mesh_preview.project(w,view) for w in worlds]+[mesh_preview.project(box,view)]);lo,hi=points.min(0),points.max(0);lo[:2]-=3;hi[:2]+=3;W,H=320,240;scale=min(W/(hi[0]-lo[0]),H/(hi[1]-lo[1]));offset=np.array([(W-(hi[0]-lo[0])*scale)/2,(H-(hi[1]-lo[1])*scale)/2]);project=lambda q:(mesh_preview.project(q,view)[:,:2]-lo[:2])*scale+offset;frames=[];font=ImageFont.truetype('C:/Windows/Fonts/segoeui.ttf',10)
for i,t in enumerate(times):
 im=mesh_preview.render(rd,atlas,clip,t,view,(W,H),[lo,hi]);d=ImageDraw.Draw(im);q=project(box)
 for a in range(8):
  for b in range(a+1,8):
   if np.count_nonzero(box[a]!=box[b])==1:d.line([tuple(q[a]),tuple(q[b])],fill=(113,130,151),width=1)
 d.text((5,5),f'v42 / provisional sweep / {t:.2f}s',font=font,fill=(221,228,237));frames.append(im)
 if i%6==0:print(json.dumps({'frame':i,'time':t,'size':[W,H]}),flush=True)
durations=[(round(times[i+1]*100)-round(times[i]*100))*10 for i in range(len(times)-1)]+[50]
frames[0].save(out/'noctveil_v42_short_sweep_review.gif',save_all=True,append_images=frames[1:],duration=durations,loop=0,disposal=2,optimize=False)
board=Image.new('RGB',(5*W,5*(H+25)),(31,28,35));ink=ImageDraw.Draw(board)
for i,im in enumerate(frames):x=(i%5)*W;y=(i//5)*(H+25);ink.text((x+5,y+5),f'{times[i]:.2f}s',font=font,fill='white');board.paste(im,(x,y+25))
board.save(out/'noctveil_v42_short_sweep_all_frames.png')
(out/'v42_gui_free_motion_preview.json').write_text(json.dumps({'renderer':'Existing work/mesh_preview.py only','native_runtime_launched':False,'frames':25,'size':[320,240],'duration_ms':sum(durations),'same_fixed_camera_all_frames':True,'same_approved_texture_pixels_and_UV':True,'native_geometry_parity':'Current iteration_07 matched native4 static poses to4.60e−14 model units; timing-only copy retains poses. This is not a proof of native pixel/shading equivalence.','limitations':'Software shading differs from Blockbench. One forward sweep + load/exit holds only; idle-return/state transition unverified. No VFX, game hitbox/damage calibration, full collision or art pass.','original_reference_pixels_included':False,'game_connected':False},indent=2)+'\n',encoding='utf8')
print(json.dumps({'gif':'noctveil_v42_short_sweep_review.gif','duration_ms':sum(durations),'frame_count':len(frames)}))
