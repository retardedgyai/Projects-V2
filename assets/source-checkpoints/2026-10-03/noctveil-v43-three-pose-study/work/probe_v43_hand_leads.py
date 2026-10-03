"""Normal-size visibility counterexample scan, protected shape. Not art scoring."""
import json,copy,numpy as np
from PIL import Image,ImageDraw,ImageFont
import noctveil_v42_pose_tools as p
import mesh_preview
env=p.prefix(p.ROOT/'work/probe_v43_three_gestures.py','rows');out=env['out'];base,_=env['env']['design'](2)
base.update(body=[-7,8,-4],shift=[.5,-2.2,-.8],claw_direction=[-.8,-.4,-.8])
A=env['env']['designenv']['shoulder'](base);radius=np.linalg.norm(np.asarray(base['wrist'])-A)
diag=copy.deepcopy(p.rig.source);colors=np.array([[k+1,0,0,255] for k in range(len(diag['elements']))],dtype=np.uint8)[None,:,:]
for k,e in enumerate(diag['elements']):
 for f in e['faces'].values():
  for uv in f['uv'].values():uv[:]=[k,0]
indices=[k+1 for k,e in enumerate(diag['elements']) if e['name'] in p.hand_names]
rows=[];best=[]
directions=[[3,19,-27],[5,20,-27],[5,19.5,-27]]
# Fixed conservative camera bounds across the scan; no automatic zoom per candidate.
bounds={'front':[np.array([-36,-52,-80.]),np.array([35,3,80.])],'hero':[np.array([-38,-48,-80.]),np.array([38,7,80.])]}
for i,D in enumerate(directions):
 q=copy.deepcopy(base);d=np.asarray(D)-A;q['wrist']=(A+radius*d/np.linalg.norm(d)).tolist()
 for roll in [255,270,285,300]:
  try:
   v,r=p.closed_pose(q,roll,0)
   if v is None:raise ValueError('closure failed')
   v[('neck','rotation')]=[10,4.8,0]
   for n in ['left_crown','right_crown']:v[(n,'rotation')]=[-20,0,0]
   v=env['opposite'](v);w=p.world(v);head=env['env']['check']['head_crossings'](w);y=float(min(a[:,1].min() for a in w.values()))
   audit=p.audit(w)
   row={'direction':i,'roll':roll,'q':q,'head_pairs':len(head),'minimum_y':y,'target_audit':audit}
   if not head and y>=0:
    clip=p.frame_clip(v);row['visible_pixels']={}
    for view in ['front','hero']:
     mask=np.array(mesh_preview.render(diag,colors,clip,0,view,(320,240),bounds[view],fullbright=True))[:,:,0];row['visible_pixels'][view]=int(np.isin(mask,indices).sum())
    row['rank_for_review_only']=min(row['visible_pixels'].values())
    if audit['hook_overlap']:best.append((row,v))
   rows.append(row)
  except ValueError as ex:rows.append({'direction':i,'roll':roll,'error':str(ex)})
 print(json.dumps({'direction':i,'eligible':sum('visible_pixels' in r for r in rows),'best_min_pixels':max([r['rank_for_review_only'] for r,_ in best] or [0])}),flush=True)
best.sort(key=lambda item:item[0]['rank_for_review_only'],reverse=True)
(out/'v43_hand_lead_scan.json').write_text(json.dumps({'rows':rows,'selected_for_visual_review':[r for r,_ in best[:4]],'metrics_do_not_establish_aesthetic_pass':True,'game_connected':False},indent=2)+'\n',encoding='utf8')
board=Image.new('RGB',(1280,540),(31,28,35));d=ImageDraw.Draw(board);font=ImageFont.truetype('C:/Windows/Fonts/segoeui.ttf',12)
for col,(row,v) in enumerate(best[:4]):
 for j,view in enumerate(['front','hero']):
  im=mesh_preview.render(env['rd'],env['atlas'],p.frame_clip(v),0,view,(320,240),bounds[view]);board.paste(im,(col*320,j*270+30));d.text((col*320+3,j*270+4),f"direction{row['direction']} roll{row['roll']} {view} / pixels{row['visible_pixels'][view]}",font=font,fill='white')
board.save(out/'v43_hand_lead_contact_alternatives.png')
