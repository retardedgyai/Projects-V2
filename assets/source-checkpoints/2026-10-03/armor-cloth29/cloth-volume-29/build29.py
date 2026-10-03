"""Independent material/shape remake after explicit rejection of 27.
Own approved material-v1 palette and connected colour fields; no Isles pixels.
Neutral editable mesh first. Native inspection is required, no artistic pass.
"""
from pathlib import Path
from collections import defaultdict,Counter
import sys,json,copy,uuid,base64,hashlib,shutil,math
import numpy as np
from PIL import Image,ImageColor
sys.dont_write_bytecode=True
R=Path(__file__).resolve().parent;ROOT=R.parent
sys.path.insert(0,str(ROOT/'cloth-layered-23'))
from mesh_tools import Mesh,rotate,write
S=ROOT/'cloth-texture-tune-27';D=R/'model';D.mkdir(parents=True,exist_ok=True)
PAL={'navy':['#192a42','#2b425f','#47637d','#728f9e','#a3bdbe'],
     'linen':['#53594f','#827e6b','#b7aa8b','#e3d1a8','#f4e5bb'],
     'hide':['#352a27','#574032','#805b40','#a67c53','#c59a65'],
     'pants':['#1c2c3f','#2b4258','#456175','#6c8590','#9aafb0']}
COL={k:np.array([ImageColor.getcolor(c,'RGBA') for c in cs],np.uint8) for k,cs in PAL.items()}
DROP={p for p in json.loads((S/'model/model.json').read_text())['parts'] if any(t in p for t in ['shoulder mantle','hanging shoulder cloth','chest mantle fold','chest fold bound edge','rear shoulder cape','rear cape turned edge'])}
DROP.update(['left leather boot','right leather boot'])
BASE=json.loads((S/'model/model.json').read_text());M=copy.deepcopy(BASE)
M['faces']=[f for f in M['faces'] if f['piece'] not in DROP]
M['parts']={p:q for p,q in M['parts'].items() if p not in DROP}
for f in M['faces']:f['texture_index']=0
# The retained navy sleeve roof stood above the new drape. Adapt only its
# surplus upper height in this separate copy, while retaining body vertices.
adapted=[p for p in M['parts'] if 'shaped dark sleeve' in p or 'sewn sleeve top' in p]
for piece in adapted:
 ids={i for f in M['faces'] if f['piece']==piece for i in f['vertices']}
 for i in ids:
  if M['vertices'][i][1]>24.3:M['vertices'][i][1]=round(24.3+(M['vertices'][i][1]-24.3)*.25,8)
 part=M['parts'][piece];part.pop('front_back_pair_max_error',None)
 part.update(source_thickness=part.pop('thickness'),effective_thickness_range=[.02,.08],
  shape_adjustment='surplus sleeve roof y>24.3 compressed by .25 in candidate29 only')
 pv=np.array([M['vertices'][i] for i in sorted(ids)]);part['bounds']=[pv.min(0).tolist(),pv.max(0).tolist()]
V=np.array(M['vertices'])

def local(p,piece):
 p=np.array(p,float)
 if piece.startswith(('left','right')) and any(k in piece for k in ['sleeve','forearm','wrist']):
  side=piece.startswith('left');p=rotate(p[None,:],[2 if side else 14,24,8],-(-8 if side else 8))[0]
 if piece.startswith(('left','right')) and any(k in piece for k in ['leg garment','boot']):
  side=piece.startswith('left');p=rotate(p[None,:],[6 if side else 10,12,8],-(-5 if side else 5))[0]
 return p

def garment(p,piece,role,mat):
 """Coarse world-connected fields, chosen by actual anatomical region.
 Pixel steps follow folds, cuff and knee volume rather than triangle IDs.
 """
 x,y,z=local(p,piece);x,y,z=[math.floor(a+.08) for a in (x,y,z)]
 if role=='inside' or mat=='lining':return COL['navy'][0]
 if mat in ['canvas','canvas_shadow']:
  i=2
  if 'forearm' in piece:
   # A wrap has a broad upper plane and a lower flex shadow.
   i=3 if y>=16 and z<=8 else 2
   if y<=15 or z>=10:i=1
  elif 'boot top' in piece:
   i=3 if z<=7 else 2
   if z>=10:i=1
  elif 'hem facing' in piece:
   # Only the real folded return is shaded; no bright uniform contour.
   i=2 if z<10 else 1
   if (piece.startswith('left') and x<4) or (piece.startswith('right') and x>12):i=1
  elif 'crown edge' in piece:
   i=3 if y>=33 else 2
   if x<=4 or x>=11:i=1
  elif 'belt tie' in piece:i=3 if x<4 and y>=12 else (1 if y<11 else 2)
  if role=='thin cut':i=max(1,i-1)
  return COL['linen'][i]
 if mat=='wool':
  i=1;cx=6 if piece.startswith('left') else 10
  if 'leg garment' in piece:
   distance=abs(x-cx)
   # A continuous broad thigh/shin field; bend shadow and rear plane.
   i=2 if distance<=1 and z<=7 else 1
   if 7<=y<=10 and distance<=1 and z<=6:i=3
   if 5<=y<=6 and z<=7:i=1
   if y<=4 and z>=8:i=0
   if z>=10:i=0 if distance>=1 else 1
  else:i=2 if 5<=x<=10 and z<=7 else 1
  if role=='thin cut':i=max(0,i-1)
  return COL['pants'][i]
 # Navy cloth: crown, cheek, sleeve, body and tabs each have own mass.
 i=1
 if 'crown' in piece or 'nape' in piece:
  i=2
  if y>=32 and 5<=x<=10:i=3
  if y>=33 and x>=10:i=2
  if y<=28:i=1
  if z>=11:
   width=2 if y>=30 else 1
   i=2 if abs(x-7)<=width and y>=27 else 1
   if y>=31 and 6<=x<=9:i=3
  if x<=4 or x>=11:i=max(1,i-1)
 elif 'cheek' in piece:
  i=3 if y>=30 else (2 if y>=28 else 1)
 elif 'brow' in piece:
  i=2 if 5<=x<=10 else 1
 elif 'jaw' in piece:
  i=2 if 5<=x<=10 and y>=26 and z<=4 else 1
 elif 'sleeve' in piece:
  cx=2 if piece.startswith('left') else 14
  i=2 if abs(x-cx)<=1 and z<=7 else 1
  if y>=21 and z<=7 and abs(x-cx)<=1:i=3
  if 18<=y<=19 and z<=7:i=1
  if y<=16 or z>=10:i=1 if abs(x-cx)<=1 else 0
 elif 'wrist' in piece:i=2 if z<=7 else 1
 elif any(k in piece for k in ['wrap body','coat back','inner shirt']):
  cx=5 if piece.startswith('left') else (11 if piece.startswith('right') else 8)
  i=2 if abs(x-cx)<=1 else 1
  # Upper chest falls into a connected lower fold; no isolated waist square.
  if 18<=y<=21 and abs(x-cx)<=1:i=3
  if y<=17 and abs(x-cx)<=1:i=2
  if y<=15:i=1
  if 'wrap body' in piece:
   # Connected stair-step volume, not two rectangular chest labels.
   upper=6 if piece.startswith('left') else 10
   if y>=20:i=3 if x==upper else (2 if abs(x-upper)<=1 else 1)
   elif y>=18:i=3 if x==upper-1 else (2 if abs(x-upper)<=1 else 1)
   elif y>=16:i=2 if upper-1<=x<=upper else 1
  if 'back' in piece:i=2 if 6<=x<=9 and 16<=y<=22 else 1
 elif 'cloth tab' in piece or 'hip cloth' in piece:
  cx=5 if piece.startswith('left') else 11
  # Lengthwise field narrows toward the actual lower hem.
  width=1 if y<=10 else 2
  i=2 if abs(x-cx)<=width else 1
  if y>=12 and abs(x-cx)<=1:i=3
  if 'side fold' in piece:i=1 if z<=7 else 0
 elif 'neck' in piece:i=1
 if role=='thin cut':i=max(0,i-1)
 return COL['navy'][i]

before=np.array(Image.open(S/'model/native.png').convert('RGBA'));after=before.copy()
records=json.loads((ROOT/'cloth-texture-25/baseline23/UV-provenance.json').read_text())['islands']
pieces={f['piece'] for f in M['faces'] if f['role']!='avatar' and f['material'] in ['plum','wool','canvas','canvas_shadow','lining']}
allowed=np.zeros(before.shape[:2],bool);edited=[]
for rec in records:
 matches=[p for p in pieces if rec['piece'].startswith(p+' ')]
 if not matches:continue
 piece=max(matches,key=len);x,y,w,h=rec['island'];allowed[y:y+h,x:x+w]=True;edited.append(rec['piece'])
 for f in [q for q in M['faces'] if q['piece']==piece]:
  uv=np.array(f['uv']);cross=lambda a,b:float(a[0]*b[1]-a[1]*b[0]);den=cross(uv[1]-uv[0],uv[2]-uv[0])
  lo=np.maximum([x,y],np.floor(uv.min(0)).astype(int));hi=np.minimum([x+w-1,y+h-1],np.ceil(uv.max(0)).astype(int))
  for py in range(lo[1],hi[1]+1):
   for px in range(lo[0],hi[0]+1):
    q=np.array([px+.5,py+.5])-uv[0];b=cross(q,uv[2]-uv[0])/den;c=cross(uv[1]-uv[0],q)/den;a=1-b-c
    if min(a,b,c)<-1e-8:continue
    after[py,px]=garment(np.array([a,b,c])@V[f['vertices']],piece,f['role'],f['material'])
diff=np.any(after!=before,axis=2);assert not np.any(diff&~allowed)
Image.fromarray(after).save(D/'native.png')

NEW=Mesh('29 manual cloth and boot volume')
# Inner neckline, shoulder ridge, relaxed drop, irregular lower edge.
# Rows are manually designed in 3D, NOT image cutout or cloth simulation.
inner=np.array([[8,22.7,4.55],[10.4,23.45,4.0],[12.65,24.45,3.85],
 [12.65,24.8,8],[12.65,24.45,12.65],[10.4,24.15,12.65],[8,24.1,12.65],
 [5.6,24.15,12.65],[3.35,24.45,12.65],[3.35,24.8,8],[3.35,24.45,3.85],
 [5.6,23.45,4.0],[8,22.7,4.55]])
ridge=np.array([[8,22.25,4.6],[11.1,23.3,3.75],[14.3,24.95,4.85],
 [14.45,25.35,8],[14.3,25.35,12.8],[11.15,23.75,12.85],[8,23.6,12.75],
 [4.85,23.75,12.85],[1.7,24.95,11.2],[1.55,25.35,8],[1.7,24.95,4.85],
 [4.9,23.3,4.22],[8,22.25,4.6]])
outer=np.array([[8,20.95,4.72],[12.0,21.55,4.58],[16.7,21.35,4.98],
 [17.85,22.15,8],[17.45,21.55,11.2],[11.85,21.5,12.83],[8,21.0,12.92],
 [4.15,21.5,12.83],[-1.45,21.25,11.2],[-1.85,22.05,8],[-.7,21.1,4.98],
 [4.0,21.5,4.58],[8,20.95,4.72]])
drop=ridge*.43+outer*.57
drop[:,1]+=[.03,-.12,.18,.14,-.1,.12,.04,.08,-.14,.18,.2,-.08,.03]
for ci in [2,3,4]:drop[ci]=[[16.8,24.85,4.95],[17.4,24.85,8],[17.2,24.85,11.25]][ci-2]
for ci in [8,9,10]:drop[ci]=[[-1.2,24.85,11.25],[-1.4,24.85,8],[-.8,24.85,4.95]][ci-8]
rows=np.array([inner,ridge,drop,outer]);NEW.shell(rows,'new continuous folded shoulder cloth','canvas','canvas_shadow',.055)
u=np.r_[0,np.cumsum(np.linalg.norm(np.diff(ridge,axis=0),axis=1))];u=u/u[-1]*72
rowv=[0,3,6,9];uv_lookup={}
for ri,row in enumerate(rows):
 for ci,p in enumerate(row):uv_lookup[tuple(np.round(p,8))]=[u[ci],rowv[ri]]
atlas=np.zeros((512,512,4),np.uint8)
def cape_colour(px,py,inside=False):
 t=px/72;v=py/9;i=2
 # Connected broad shoulder highlights; neckline and folds sit lower.
 if v<.22:i=1
 lightzones=[(.2,.6),(.3,.85),(.15,.65),(.15,.55),(.25,.85),(.25,.65),(.35,.6),(.25,.65),(.25,.75),(.15,.55),(.15,.65),(.3,.85)]
 sector=int(min(11,t*12));start,end=lightzones[sector]
 if start<=v<end:i=3
 if .12<t<.2 or .8<t<.88:
  if v>.45:i=1
 if .36<t<.42 or .58<t<.64:
  if .5<v<.9:i=1
 if inside:i=max(0,i-1)
 return COL['linen'][i]
for inside,yo in [(False,2),(True,18)]:
 for y in range(12):
  for x in range(76):atlas[yo+y,2+x]=cape_colour(max(0,min(71,x-1)),max(0,min(8,y-1)),inside)
for f in NEW.faces:
 if f['piece']!='new continuous folded shoulder cloth':continue
 uv=[]
 for idx in f['vertices']:
  p=np.array(NEW.v[idx]);key=tuple(np.round(p,8))
  if key not in uv_lookup:
   # Shell's inside is an offset of the nearest matching authored outer point.
   d=np.linalg.norm(rows.reshape(-1,3)-p,axis=1);k=int(d.argmin());ri,ci=divmod(k,13);q=[u[ci],rowv[ri]]
  else:q=uv_lookup[key]
  uv.append([q[0],q[1]])
 if max(q[0] for q in uv)-min(q[0] for q in uv)>36:
  mid=[q[0] for q in uv if .01<q[0]<71.99];seam=72 if np.mean(mid)>36 else 0
  uv=[[seam if q[0]<.01 or q[0]>71.99 else q[0],q[1]] for q in uv]
 uv=[[q[0]+3,q[1]+(3 if f['role']=='surface' else 19)] for q in uv]
 if f['role']=='thin cut':
  # Dedicated opaque low contrast return, valid area even along one boundary.
  uv=[[85,3],[87,3],[85,5]];atlas[1:7,83:90]=COL['linen'][2]
 f['uv']=uv;f['texture_index']=1

# Real stepped toe/instep/shaft volume, with light shell thickness and open ankle.
for side,cx,ang in [('left',6,-5),('right',10,5)]:
 bootrows=[]
 for y,w,zf,zb in [(3.35,4.38,5.5,10.25),(1.6,4.45,5.3,10.25),(.6,4.58,4.55,10.3),(.08,4.5,4.7,10.3)]:
  a,b=cx-w/2,cx+w/2;s=.32
  loop=[[a+s,y,zf],[b-s,y,zf],[b,y,zf+s],[b,y,zb-s],[b-s,y,zb],[a+s,y,zb],[a,y,zb-s],[a,y,zf+s],[a+s,y,zf]]
  bootrows.append(rotate(np.array(loop),[cx,12,8],ang).tolist())
 piece=side+' new shaped leather boot';start=len(NEW.faces);NEW.shell(bootrows,piece,'leather','leather',.085)
 # Circumference/depth UV: broad rim, instep highlight, side and heel falloff.
 raw=np.array(bootrows);bu=np.r_[0,np.cumsum(np.linalg.norm(np.diff(raw[0],axis=0),axis=1))];bu=bu/bu[-1]*20
 yo=42 if side=='left' else 68
 for yy in range(18):
  for xx in range(26):
   ax=max(0,min(19,xx-2));ay=max(0,min(12,yy-2));i=2
   if ay<=1:i=3 if ax<5 else 2
   if 2<=ay<=8 and ax<=4:i=3
   if 6<=ay<=9 and ax<=2:i=3
   if ay>=11:i=1
   if 8<=ax<=13:i=1 if ay>=7 else 2
   atlas[yo+yy,2+xx]=COL['hide'][i]
 for f in NEW.faces[start:]:
  uv=[]
  for idx in f['vertices']:
   p=np.array(NEW.v[idx]);d=np.linalg.norm(raw.reshape(-1,3)-p,axis=1);k=int(d.argmin());ri,ci=divmod(k,9)
   uv.append([bu[ci],[0,6,10,13][ri]])
  if max(q[0] for q in uv)-min(q[0] for q in uv)>10:
   uv=[[20 if q[0]<1 else q[0],q[1]] for q in uv]
  uv=[[q[0]+4,q[1]+yo+2] for q in uv]
  if f['role']=='thin cut':uv=[[35,yo+2],[37,yo+2],[35,yo+4]];atlas[yo:yo+7,33:40]=COL['hide'][1]
  f['uv']=uv;f['texture_index']=1
Image.fromarray(atlas).save(D/'new-cloth-boots.png')
offset=len(M['vertices']);M['vertices']+=NEW.v
for f in NEW.faces:f['vertices']=[i+offset for i in f['vertices']]
M['faces']+=NEW.faces;M['parts'].update(NEW.parts)
# Remove vertices orphaned by discarded mantle/boot geometry.
used=sorted({i for f in M['faces'] for i in f['vertices']});remap={old:i for i,old in enumerate(used)}
M['vertices']=[M['vertices'][i] for i in used]
for f in M['faces']:f['vertices']=[remap[i] for i in f['vertices']]
M.update(name='ProjectS cloth volume29 / unapproved separate remake',stage='real mesh and connected pixel material candidate; native review pending',aesthetic_approval_claimed=False,concept_approved=False,game_wear_complete=False,dynamic_rig_certified=False,material_reference='libfile_a09d9058fed88191a189833a23ae26dc version1',remade_after_user_rejection27=True)
write(D/'model.json',M)
grouped=defaultdict(list)
for i,f in enumerate(M['faces']):grouped[f['piece']].append((i,f))
elements=[]
for piece,faces in grouped.items():
 ids=sorted({i for _,f in faces for i in f['vertices']});uid=str(uuid.uuid5(uuid.NAMESPACE_URL,'ProjectS29/'+piece))
 elements.append({'name':piece,'type':'mesh','uuid':uid,'origin':[0,0,0],'rotation':[0,0,0],
  'vertices':{'v'+str(i):M['vertices'][i] for i in ids},'faces':{'f'+str(i):{'vertices':['v'+str(k) for k in f['vertices']],'uv':{'v'+str(k):uv for k,uv in zip(f['vertices'],f['uv'])},'texture':f['texture_index']} for i,f in faces},'visibility':True,'export':True,'autouv':0,'locked':False})
textures=[]
for i,name in enumerate(['native.png','new-cloth-boots.png']):
 textures.append({'path':'','name':name,'folder':'','namespace':'','id':str(i),'uuid':str(uuid.uuid5(uuid.NAMESPACE_URL,'ProjectS29/texture'+str(i))), 'width':512,'height':512,'uv_width':512,'uv_height':512,'mode':'bitmap','source':'data:image/png;base64,'+base64.b64encode((D/name).read_bytes()).decode(),'render_mode':'default','render_sides':'front','visible':True})
write(D/'model.bbmodel',{'meta':{'format_version':'4.10','model_format':'free','box_uv':False},'name':M['name'],'model_identifier':'model','visible_box':[4,3,0],'resolution':{'width':512,'height':512},'elements':elements,'outliner':[e['uuid'] for e in elements],'textures':textures,'animations':[]})
obj=['mtllib material.mtl','s off']+['v '+' '.join(f'{x:.8f}' for x in p) for p in M['vertices']]
last=None
for i,f in enumerate(M['faces']):
 if f['texture_index']!=last:last=f['texture_index'];obj.append('usemtl material'+str(last))
 obj+=['vt '+f'{uv[0]/512:.8f} {1-uv[1]/512:.8f}' for uv in f['uv']];obj.append('f '+' '.join(f'{v+1}/{3*i+j+1}' for j,v in enumerate(f['vertices'])))
(D/'model.obj').write_text('\n'.join(obj)+'\n')
(D/'material.mtl').write_text('\n'.join('newmtl material'+str(i)+'\nKd 1 1 1\nKa 0 0 0\nKs 0 0 0\nillum 1\nmap_Kd '+n+'\n' for i,n in enumerate(['native.png','new-cloth-boots.png'])))
write(R/'authoring-provenance.json',{'status':'EDITABLE_CANDIDATE_NATIVE_UNCHECKED','primary_material_reference':'libfile_a09d9058fed88191a189833a23ae26dc version1','palettes_from_own_approved_material_source':PAL,'Isles_pixels_or_models_copied_to_candidate':False,'26_adopted':False,'old_head_geometry_restored':False,'dropped_source27_pieces':sorted(DROP),'adapted_navy_sleeve_roof_parts':adapted,'new_parts':NEW.parts,'protected_body_hardware_belt_RGBA_changed':int(np.any(diff&~allowed)),'changed_old_atlas_RGBA_pixels':int(diff.sum()),'repainted_old_atlas_islands':len(edited),'logical_colour_step_unit':1,'real_mesh':True,'new_cape_thickness':.055,'boot_shell_thickness':.085,'vertices':len(M['vertices']),'triangles':len(M['faces']),'meshes':len(elements),'native_render_started':False,'neutral_only':True,'aesthetic_pass_claimed':False,'game_wear_complete':False})
print(json.dumps({'status':'native unchecked','vertices':len(M['vertices']),'triangles':len(M['faces']),'meshes':len(elements),'RGBA_changed':int(diff.sum()),'new_parts':list(NEW.parts)}))
