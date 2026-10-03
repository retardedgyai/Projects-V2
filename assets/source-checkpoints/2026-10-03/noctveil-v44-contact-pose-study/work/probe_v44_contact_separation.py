"""Existing mesh renderer and rig; separate head from claw route at contact."""
import json,copy,numpy as np
from PIL import Image,ImageDraw,ImageFont
import noctveil_v42_pose_tools as p
import mesh_preview
env=p.prefix(p.ROOT/'work/probe_v43_three_gestures.py','rows');out=p.ROOT/'outputs/v44_contact_study';out.mkdir(exist_ok=True)
e=json.loads((p.ROOT/'outputs/v43_gesture_study/v43_three_pose_evidence.json').read_text());base=e['selected_poses'][1]['q'];bounds={'front':[np.array([-36,-52,-80.]),np.array([35,3,80.])],'hero':[np.array([-38,-48,-80.]),np.array([38,7,80.])]}
diag=copy.deepcopy(p.rig.source);colors=np.array([[i+1,0,0,255] for i in range(len(diag['elements']))],np.uint8)[None,:,:]
for i,el in enumerate(diag['elements']):
 for f in el['faces'].values():
  for uv in f['uv'].values():uv[:]=[i,0]
handids=[i+1 for i,el in enumerate(diag['elements']) if el['name'] in p.hand_names];headnames=set(p.descendants(p.rig.groups['head']));headids=[i+1 for i,el in enumerate(diag['elements']) if el['name'] in headnames]
isolated=copy.deepcopy(diag);isolated['elements']=[el for el in isolated['elements'] if el['name'] in p.hand_names]
headonly=copy.deepcopy(diag);headonly['elements']=[el for el in headonly['elements'] if el['name'] in headnames]
target=np.array([[x,y,z] for x in [-5,5] for y in [0,18] for z in [-32,-28]])
def screen(x,view):
 lo,hi=bounds[view];s=min(320/(hi[0]-lo[0]),240/(hi[1]-lo[1]));off=np.array([(320-(hi[0]-lo[0])*s)/2,(240-(hi[1]-lo[1])*s)/2]);return (mesh_preview.project(np.asarray(x),view)[:,:2]-lo[:2])*s+off
rows=[];selected=[]
for roll in [210,225,240,255,270,285,300]:
 v,r=p.closed_pose(base,roll,0)
 if v is None:continue
 for yaw in [-15,-25,-35]:
  wv=copy.deepcopy(v);wv[('neck','rotation')]=[-10,yaw,0]
  for n in ['left_crown','right_crown']:wv[(n,'rotation')]=[-20,0,0]
  wv=env['opposite'](wv);w=p.world(wv);head=env['env']['check']['head_crossings'](w);y=min(a[:,1].min() for a in w.values());a=p.audit(w)
  row={'roll':roll,'neck':[-10,yaw,0],'q':base,'head_and_crown_crossings':len(head),'head_crossing_details':head,'minimum_all_y':float(y),'shoulder':r['actual_shoulder'],'elbow':r['actual_elbow'],'wrist':r['actual_wrist'],'target_audit':a,'views':{}}
  if not head and y>=0:
   clip=p.frame_clip(wv)
   for view in bounds:
    pixels=np.array(mesh_preview.render(diag,colors,clip,0,view,(320,240),bounds[view],fullbright=True))[:,:,0]
    mask=np.isin(np.array(mesh_preview.render(isolated,colors,clip,0,view,(320,240),bounds[view],fullbright=True))[:,:,0],handids)
    headmask=np.isin(np.array(mesh_preview.render(headonly,colors,clip,0,view,(320,240),bounds[view],fullbright=True))[:,:,0],headids)
    visible=np.isin(pixels,handids);uv=np.argwhere(visible);hp=np.argwhere(headmask)
    gap=float(np.min(np.linalg.norm(uv[:,None,:]-hp[None,:,:],axis=2))) if len(uv) and len(hp) else 0
    row['views'][view]={'visible_hand_pixels':int(visible.sum()),'isolated_hand_pixels':int(mask.sum()),'projected_head_hand_overlap_pixels':int((mask&headmask).sum()),'visible_hand_to_projected_head_minimum_pixels':gap,'shoulder_elbow_wrist_pixels':screen([r['actual_shoulder'],r['actual_elbow'],r['actual_wrist']],view).tolist()}
   row['review_rank_only']=min(r['visible_hand_pixels'] for r in row['views'].values());selected.append((row,wv))
  rows.append(row)
 print(json.dumps({'roll':roll,'eligible':len(selected)}),flush=True)
selected.sort(key=lambda item:(min(r['visible_hand_to_projected_head_minimum_pixels'] for r in item[0]['views'].values())>=2,item[0]['review_rank_only']),reverse=True)
font=ImageFont.truetype('C:/Windows/Fonts/segoeui.ttf',12);board=Image.new('RGB',(1280,540),(31,28,35));ink=ImageDraw.Draw(board)
for col,(row,v) in enumerate(selected[:4]):
 for j,view in enumerate(bounds):
  im=mesh_preview.render(env['rd'],env['atlas'],p.frame_clip(v),0,view,(320,240),bounds[view]);d=ImageDraw.Draw(im);pts=screen(target,view)
  for a in range(8):
   for b in range(a+1,8):
    if np.count_nonzero(target[a]!=target[b])==1:d.line([tuple(pts[a]),tuple(pts[b])],fill=(113,130,151),width=1)
  board.paste(im,(col*320,j*270+30));ink.text((col*320+3,j*270+4),f"roll{row['roll']} neck{row['neck'][1]} / {view}",font=font,fill='white')
board.save(out/'noctveil_v44_contact_separation_candidates.png')
(out/'v44_contact_separation_scan.json').write_text(json.dumps({'rows':rows,'shortlist':[r for r,_ in selected[:4]],'fixed_target':p.target,'art_pass':False,'current_native_verified':False,'continuous_collision_proven':False,'game_connected':False},indent=2)+'\n',encoding='utf8')
