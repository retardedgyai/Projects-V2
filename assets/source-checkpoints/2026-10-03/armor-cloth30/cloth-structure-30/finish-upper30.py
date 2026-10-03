"""Verify real Blockbench data and compose unchanged native frames, no renderer."""
from pathlib import Path
import json,base64,io,math,shutil
import numpy as np
from PIL import Image,ImageDraw,ImageFont
R=Path(__file__).resolve().parent
read=lambda p:json.loads(p.read_text(encoding='utf-8-sig'))
write=lambda p,x:p.write_text(json.dumps(x,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
font=ImageFont.truetype('C:/Windows/Fonts/meiryo.ttc',20);small=ImageFont.truetype('C:/Windows/Fonts/meiryo.ttc',15)
native=read(R/'native/verification.json');assert native['complete'] and len(native['records'])==2 and not native['page_errors']
records={r['label']:r for r in native['records']};checks=[]
for label,record in records.items():
 src=read(R/record['source_file']);expected=src['elements'];assert len(record['meshes'])==len(expected)
 err=0;faces=0
 for e,w in zip(record['meshes'],expected):
  assert e['name']==w['name'];assert e['vertices'].keys()==w['vertices'].keys();assert e['faces'].keys()==w['faces'].keys()
  for k,v in e['vertices'].items():err=max(err,max(abs(a-b) for a,b in zip(v,w['vertices'][k])))
  for k,f in e['faces'].items():
   x=w['faces'][k];assert f['vertices']==x['vertices'] and f['uv']==x['uv'];assert f['texture']==src['textures'][int(x['texture'])]['uuid'];faces+=1
 assert err<1e-7
 saved=read(R/'native'/f'{label}-resaved.bbmodel');assert len(saved['elements'])==len(expected)
 byuuid={e['uuid']:e for e in expected}
 saved_vertex_error=0.;saved_UV_error=0.
 for e in saved['elements']:
  w=byuuid[e['uuid']];assert e['vertices'].keys()==w['vertices'].keys() and e['faces'].keys()==w['faces'].keys()
  for k,v in e['vertices'].items():saved_vertex_error=max(saved_vertex_error,max(abs(a-b) for a,b in zip(v,w['vertices'][k])))
  for k,f in e['faces'].items():
   wantedface=w['faces'][k];assert f['vertices']==wantedface['vertices'] and f['texture']==wantedface['texture'];assert f['uv'].keys()==wantedface['uv'].keys()
   for vk,u in f['uv'].items():saved_UV_error=max(saved_UV_error,max(abs(a-b) for a,b in zip(u,wantedface['uv'][vk])))
 assert saved_vertex_error<=.00001 and saved_UV_error<=.00001
 texture_checks=[]
 for i,t in enumerate(record['textures']):
  assert t['error']==0 and t['nearest']==1003 and t['culling']==0
  wanted=np.array(Image.open(io.BytesIO(base64.b64decode(src['textures'][i]['source'].split(',')[1]))).convert('RGBA'))
  actual=np.array(Image.open(R/'native'/f'{label}-texture{i}.png').convert('RGBA'))
  savedpixels=np.array(Image.open(io.BytesIO(base64.b64decode(saved['textures'][i]['source'].split(',')[1]))).convert('RGBA'))
  opaque=wanted[:,:,3]>0
  for pixels in [actual,savedpixels]:
   assert np.array_equal(wanted[:,:,3],pixels[:,:,3]) and np.array_equal(wanted[opaque],pixels[opaque])
  full_exact=np.array_equal(wanted,actual) and np.array_equal(wanted,savedpixels)
  if label=='candidate30':assert full_exact
  texture_checks.append({'texture':i,'loaded_and_resaved_visible_RGBA_and_alpha_exact':True,'full_RGBA_exact':full_exact,'loaded_hidden_alpha0_RGB_normalized':int((np.any(wanted!=actual,axis=2)&~opaque).sum()),'saved_hidden_alpha0_RGB_normalized':int((np.any(wanted!=savedpixels,axis=2)&~opaque).sum())})
 checks.append({'label':label,'loaded_vertices_max_error':err,'faces_UV_checked':faces,'loaded_UV_exact':True,'texture_checks':texture_checks,'resaved_max_vertex_rounding':saved_vertex_error,'resaved_max_UV_rounding':saved_UV_error,'nearest_filter':1003,'version':record['version']})
same=[]
for view,shading in [('front',True),('oblique',True),('side',True),('back',True),('oblique',False)]+[('full-'+v,True) for v in ['front','back','oblique','side']]:
 rows=[next(v for v in records[l]['views'] if v['view']==view and v['shading']==shading) for l in ['baseline29','candidate30']]
 for key in ['camera','clip','shading','brightness','antialiasing','DPR']:assert rows[0][key]==rows[1][key],(view,key)
 same.append({'view':view,'shading':shading,'two_models_same_camera_light_clip_DPR':True})

def frame(label,view='oblique',shade=True):return Image.open(R/'native'/(f'{label}-{view}-'+('shaded' if shade else 'unlit')+'.png')).convert('RGB')

shading_differences={}
for label in records:
 a=np.array(frame(label,'oblique',True));b=np.array(frame(label,'oblique',False));n=int(np.any(a!=b,axis=2).sum());assert n>100,(label,'Native shading did not actually change')
 shading_differences[label]=n

def board(name,rows):
 w,h=frame('candidate30').size;out=Image.new('RGB',(w*len(rows[0]),(h+42)*len(rows)),'#182025');d=ImageDraw.Draw(out)
 for y,row in enumerate(rows):
  for x,(label,who,view,shade) in enumerate(row):
   out.paste(frame(who,view,shade),(x*w,y*(h+42)+42));d.text((x*w+12,y*(h+42)+8),label,font=font,fill='#dddacb')
 out.save(R/name)

board('ProjectS_cloth30_upper_four_views.png',[[('30 upper / '+v,'candidate30',v,True) for v in ['front','back']],[('30 upper / '+v,'candidate30',v,True) for v in ['oblique','side']]])
board('ProjectS_cloth30_upper_structure_comparison.png',[[('29 intermediate / '+v,'baseline29',v,True),('30 remake / '+v,'candidate30',v,True)] for v in ['front','oblique','side']])
board('ProjectS_cloth30_upper_material_comparison.png',[[('29 / '+label,'baseline29','oblique',shade),('30 / '+label,'candidate30','oblique',shade)] for label,shade in [('standard shading',True),('unlit texture inspection',False)]])
board('ProjectS_cloth30_native_four_views.png',[[('30 full / '+v,'candidate30','full-'+v,True) for v in ['front','back']],[('30 full / '+v,'candidate30','full-'+v,True) for v in ['oblique','side']]])
board('ProjectS_cloth30_full_comparison.png',[[('29 intermediate / '+v,'baseline29','full-'+v,True),('30 remake / '+v,'candidate30','full-'+v,True)] for v in ['front','oblique']])

game=Image.new('RGB',(1200,500),'#182025');d=ImageDraw.Draw(game);crops=[];p=R/'preview64';p.mkdir(exist_ok=True)
for ci,(label,who) in enumerate([('29 intermediate','baseline29'),('30 structure remake','candidate30')]):
 d.text((ci*600+12,8),label+' / front + oblique',font=font,fill='#dddacb')
 for vi,view in enumerate(['front','oblique']):
  r=next(v for v in records[who]['views'] if v['view']=='full-'+view);box=tuple((math.floor if i<2 else math.ceil)(x) for i,x in enumerate(r['model_screen_bounds']));im=frame(who,'full-'+view).crop(box)
  native64=im.resize((round(im.width*64/im.height),64),Image.Resampling.NEAREST);enlarged=native64.resize((native64.width*4,256),Image.Resampling.NEAREST)
  x=ci*600+vi*290+12;game.paste(native64,(x,72));game.paste(enlarged,(x,178));native64.save(p/f'{who}-{view}-64px.png');crops.append({'label':who,'view':view,'actual_mesh_crop':box,'native64_size':native64.size,'enlargement':4,'filter':'nearest'})
for view in ['back','side']:
 r=next(v for v in records['candidate30']['views'] if v['view']=='full-'+view);box=tuple((math.floor if i<2 else math.ceil)(x) for i,x in enumerate(r['model_screen_bounds']));im=frame('candidate30','full-'+view).crop(box);im.resize((round(im.width*64/im.height),64),Image.Resampling.NEAREST).save(p/f'candidate30-{view}-64px.png')
d.text((12,44),'Full body 64px original size',font=small,fill='#dddacb');d.text((12,146),'4x nearest enlargement of the exact same 64px pixels',font=small,fill='#dddacb');d.text((12,465),'Same native Blockbench cameras / light. Separate static concept model; quality work continues.',font=small,fill='#dddacb');game.save(R/'ProjectS_cloth30_native_64px_comparison.png')

probe=read(R/'native/window-state-probe.json');normal=np.array(Image.open(R/'native/window-normal-baseline29-front.png').convert('RGBA'));tested=np.array(Image.open(R/'native/baseline29-front-shaded.png').convert('RGBA'));probe['same_camera_normal_vs_minimized_changed_pixels']=int(np.any(normal!=tested,axis=2).sum());probe['same_pixels']=np.array_equal(normal,tested);probe['headless_renderer_equivalence_claimed']=False
write(R/'window-state-validation.json',probe)
minimized=read(R/'window-minimized-validation.json');assert minimized['RGBA_exact'] and all(minimized['same_camera_light_clip'].values())
write(R/'native-validation.json',{'actual_models_checked':checks,'same_conditions':same,'native_shading_toggle_actual_changed_pixels':shading_differences,'frames':sum(len(x['views']) for x in records.values()),'actual_64px_crops':crops,'window_probe':probe,'own_minimized_window_validation':minimized,'standard_Blockbench_renderer':True,'custom_renderer_or_painted_preview':False,'aesthetic_acceptance_claimed':False,'game_wear_complete':False})
quality=read(R/'verification.json');quality['native_inspection_complete']=True;quality['actual_native_frames']=18;write(R/'verification.json',quality)
print(json.dumps({'models_checked':len(checks),'frames':18,'same_conditions':len(same),'native_shading_actual_changed_pixels':shading_differences,'RGBA_exact_candidate30':True,'resave_rounding_maximum':max(max(x['resaved_max_vertex_rounding'],x['resaved_max_UV_rounding']) for x in checks),'window_probe':probe,'boards':6,'64px_files':6}))
