"""Three actual poses around the contact with alternate trailing membrane folds."""
import json,copy,hashlib,numpy as np
from PIL import Image,ImageDraw,ImageFont
import noctveil_v42_pose_tools as p
import noctveil_v44_pose_tools as new
import mesh_preview
env=p.prefix(p.ROOT/'work/probe_v44_contact_separation.py','rows');out=env['out']/'independent_membrane_flanks_03';out.mkdir(exist_ok=True)
def fold(pts):
 a=pts[5];W=p.poseenv['axis_rotation'](pts[8]-a,-120);pts[9:]=(W@(pts[9:]-a).T).T+a;return pts
designs=[]
for name,body,shift,wrist,direction,neck in [
 ('OUTSIDE',[-5,-3,-3],[.7,-1.3,-.4],[14,19.2,-24.5],[-.5,-.7,-1],[0,-25,0]),
 ('CONTACT',[-7,8,-4],[.5,-2.2,-.8],[8,18,-27.5],[-.9,-.65,-.65],[0,-35,0]),
 ('OPPOSITE',[-3,25,4],[-1.3,-1.9,-.6],[-22,20,-16],[-.3,-1,-.7],[10,15,0])]:
 q=copy.deepcopy(env['base']);q.update(name=name,body=body,shift=shift,wrist=wrist,claw_direction=direction)
 if name=='OPPOSITE':q.update(hind_foot_yaw={'right_hind':-14},foot_step={'leg':'right_fore','delta':[-.4,1.6,-2.5],'toe_pitch':5})
 designs.append((q,neck))
poses=[];rows=[]
for i,(q,neck) in enumerate(designs):
 try:
  roll=315 if i<2 else 220
  membrane=[0,0,-25]
  v,r=new.closed_pose(q,roll,0,membrane_rotation=membrane)
  if v is None:raise ValueError('closure failed '+str(r['steps'][-1]))
  v[('neck','rotation')]=neck
  for n in ['left_crown','right_crown']:v[(n,'rotation')]=[-20,0,0]
  v=env['env']['opposite'](v);w=p.world(v);head=env['env']['env']['check']['head_crossings'](w);audit=p.audit(w);minimum=float(min(a[:,1].min() for a in w.values()))
  c=p.frame_clip(v);c['uuid']=p.uid('v44-'+q['name']);c['name']='v44_'+q['name'].lower()+'_static';poses.append(c)
  row={'phase':q['name'],'q':q,'neck':neck,'roll':roll,'twist':0,'membrane_rotation':membrane,'outer_fold_seed_degrees':None,'closure':r,'head_and_crown_crossings':head,'minimum_all_y':minimum,'target_audit':audit,'views':{}}
  for view in env['bounds']:
   mask=np.array(mesh_preview.render(env['diag'],env['colors'],c,0,view,(320,240),env['bounds'][view],fullbright=True))[:,:,0]
   hand=np.isin(mask,env['handids']);h=np.isin(mask,env['headids']);a=np.argwhere(hand);b=np.argwhere(h);gap=float(np.min(np.linalg.norm(a[:,None,:]-b[None,:,:],axis=2))) if len(a) and len(b) else 0
   row['views'][view]={'visible_hand_pixels':int(hand.sum()),'visible_head_hand_gap':gap,'wrist_pixels':env['screen']([q['wrist']],view)[0].tolist()}
  rows.append(row);print(json.dumps({'phase':q['name'],'head_pairs':len(head),'minimum_y':minimum,'hook':audit['hook_overlap'],'non_hook':audit['non_hook_overlap'],'views':row['views']}),flush=True)
 except ValueError as ex:rows.append({'phase':q['name'],'error':str(ex)});print(json.dumps(rows[-1]),flush=True)
model=copy.deepcopy(p.rig.source);model['name']=model['model_identifier']='projects_noctveil_v44_contact_flanks';model['animations']=poses+copy.deepcopy(p.rig.source['animations'])
assert all(model[k]==p.rig.source[k] for k in ['elements','outliner','textures']) and model['animations'][len(poses):]==p.rig.source['animations']
path=out/(model['name']+'.bbmodel');path.write_text(json.dumps(model,separators=(',',':'))+'\n',encoding='utf8')
board=Image.new('RGB',(960,1110),(31,28,35));d=ImageDraw.Draw(board);font=ImageFont.truetype('C:/Windows/Fonts/segoeui.ttf',12)
for j,view in enumerate(env['bounds']):
 for col,c in enumerate(poses):
  im=mesh_preview.render(env['env']['rd'],env['env']['atlas'],c,0,view,(320,240),env['bounds'][view]);y=30+j*540;board.paste(im,(col*320,y+30));d.text((col*320+3,y+5),c['name']+' / '+view+' / plain',font=font,fill='white')
  guided=im.copy();ink=ImageDraw.Draw(guided);pts=env['screen'](env['target'],view)
  for a in range(8):
   for b in range(a+1,8):
    if np.count_nonzero(env['target'][a]!=env['target'][b])==1:ink.line([tuple(pts[a]),tuple(pts[b])],fill=(113,130,151),width=1)
  board.paste(guided,(col*320,y+300));d.text((col*320+3,y+275),c['name']+' / fixed guide',font=font,fill='white')
d.text((8,5),'V44 / three static poses / 320x240 unchanged camera per view / protected source pixels',font=font,fill='white');board.save(out/'noctveil_v44_contact_flanks.png')
result={'model':str(path.relative_to(p.ROOT)),'sha256':hashlib.sha256(path.read_bytes()).hexdigest(),'pose_count':len(poses),'rows':rows,'source_geometry_hierarchy_uv_textures_original3_clips_exact':True,'scope':'Static contact and its two flanks. Existing right_folded_membrane bone moves independently from forearm; original outer crease branch, no knot-producing alternative branch. Real mesh geometry and original pixels. Software shading differs from native. No joint markers. Same fixed opponent guide; wire overlay is not a depth guarantee. No native/current motion/interpolation/idle-return/full-body/artistic pass.','root_cause':'Earlier candidate controller kept right_folded_membrane at0 and implicitly tied three leading triangles to forearm. Existing bone supports separate15degree fold at contact; upper arm and physical forearm/hand path remain anatomical. Membrane-root loops solved again against torso. No added bones or changed source geometry required. Wrist junction and full-body contacts still checked independently.','fixed_target':p.target,'art_pass':False,'whole_body_gesture_pass':False,'game_connected':False}
(out/'v44_contact_flanks_evidence.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf8')
