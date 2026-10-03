"""Pose-only whole gesture alternatives; existing software renderer, no GUI."""
import json,copy,base64,io,numpy as np
from PIL import Image,ImageDraw,ImageFont
import noctveil_v42_pose_tools as p
import mesh_preview
env=p.prefix(p.ROOT/'work/build_v42_clear_sweep.py','values')
out=p.ROOT/'outputs/v43_gesture_study';out.mkdir(exist_ok=True)
approved=next(a for a in p.rig.source['animations'] if a['name']=='closed_idle_breath_v32')
left={n for n in p.rig.groups if n.startswith('left_wing_') or n.startswith('left_mantle_')}
def opposite(v):
 for u,a in approved['animators'].items():
  if a['name'] in left:
   for ch in {k['channel'] for k in a['keyframes']}:v[(a['name'],ch)]=p.skeleton.channel(approved,u,ch,0)
 return v
atlas=np.concatenate([np.array(Image.open(io.BytesIO(base64.b64decode(t['source'].split(',',1)[1]))).convert('RGBA')) for t in p.rig.source['textures']],axis=0)
rd=copy.deepcopy(p.rig.source)
for e in rd['elements']:
 for f in e['faces'].values():
  if f.get('texture')==1:
   for uv in f['uv'].values():uv[1]+=512
rows=[];candidates=[]
for i,t in enumerate([0,2,4]):
 q,_=env['design'](t)
 old_a=env['designenv']['shoulder'](q);radius=np.linalg.norm(np.array(q['wrist'])-old_a)
 if i==1:q.update(body=[-7,8,-4],shift=[.5,-2.2,-.8])
 a=env['designenv']['shoulder'](q)
 direction=np.array([[20,25,-1],[7.5,16.5,-25.4],[-18,18,-20]][i])-a
 q['wrist']=(a+radius*direction/np.linalg.norm(direction)).tolist()
 q['claw_direction']=[[.15,-.25,-1],[-1,-.2,-.35],[-1,.05,.35]][i]
 for roll in [180,210,240,270,285,300,315,330]:
  try:
   v,r=p.closed_pose(q,roll,t)
   if v is None:raise ValueError('closure failed')
   v[('neck','rotation')]=[10,q['body'][1]*.6,0]
   for n in ['left_crown','right_crown']:v[(n,'rotation')]=[-20,0,0]
   v=opposite(v);w=p.world(v,t);a=p.audit(w);head=env['check']['head_crossings'](w)
   row={'pose':i,'roll':roll,'q':q,'minimum_all_y':float(min(v[:,1].min() for v in w.values())),'head_crown_crossings':len(head),'head_details':head,'support_margin':r['support_proxy_margin'],'forearm_rotation':r['forearm_rotation'],'elbow':r['actual_elbow'],'audit':a}
   candidates.append((row,v,w));rows.append(row)
  except (ValueError,RuntimeError) as ex:rows.append({'pose':i,'roll':roll,'error':str(ex)})
 print(json.dumps({'pose':i,'valid':sum(c[0]['pose']==i for c in candidates)}),flush=True)
(out/'v43_three_gesture_probe.json').write_text(json.dumps({'rows':rows,'previous_iteration09_whole_gesture_pass':False,'original_shape_unchanged':True,'native_verified':False,'art_pass':False,'game_connected':False},indent=2)+'\n',encoding='utf8')
font=ImageFont.truetype('C:/Windows/Fonts/segoeui.ttf',12)
for view in ['front','hero']:
 pts=np.concatenate([mesh_preview.project(np.concatenate(list(w.values())),view) for _,_,w in candidates]);lo,hi=pts.min(0),pts.max(0);lo[:2]-=3;hi[:2]+=3
 board=Image.new('RGB',(8*320,3*270),(31,28,35));d=ImageDraw.Draw(board)
 for row,v,w in candidates:
  col=[180,210,240,270,285,300,315,330].index(row['roll']);y=row['pose']*270
  im=mesh_preview.render(rd,atlas,p.frame_clip(v),0,view,(320,240),[lo,hi]);board.paste(im,(col*320,y+30));d.text((col*320+3,y+3),f"pose{row['pose']} roll{row['roll']} head{row['head_crown_crossings']} y{row['minimum_all_y']:.2f}",font=font,fill='white')
 board.save(out/f'v43_three_gesture_rolls_{view}.png')
 print(json.dumps({'preview':view}),flush=True)
