"""Rebuild head/collar/chest as real related cloth structures.
Palette is our approved v1. MatE gallery is a quality reference, not asset input.
"""
from pathlib import Path
from collections import defaultdict
import sys,json,copy,uuid,base64,hashlib,math
import numpy as np
from PIL import Image,ImageColor
sys.dont_write_bytecode=True
R=Path(__file__).resolve().parent;ROOT=R.parent
sys.path.insert(0,str(ROOT/'cloth-layered-23'))
from mesh_tools import Mesh,write,rotate
S=ROOT/'cloth-volume-29';D=R/'model';D.mkdir(parents=True,exist_ok=True)
PAL={'navy':['#192a42','#2b425f','#47637d','#728f9e','#a3bdbe'],'linen':['#53594f','#827e6b','#b7aa8b','#e3d1a8','#f4e5bb'],'pants':['#1c2c3f','#2b4258','#456175','#6c8590','#9aafb0']}
COL={k:np.array([ImageColor.getcolor(c,'RGBA') for c in cs],np.uint8) for k,cs in PAL.items()}
M=copy.deepcopy(json.loads((S/'model/model.json').read_text()))
drop=['turned canvas crown edge','folded cloth brow band','folded cloth jaw wrap','left gathered cloth cheek fold','right gathered cloth cheek fold','left fitted short wrap body','right fitted short wrap body','fitted dark coat back','left short body side seam','right short body side seam','new continuous folded shoulder cloth']
M['faces']=[f for f in M['faces'] if f['piece'] not in drop];M['parts']={p:v for p,v in M['parts'].items() if p not in drop}
Image.open(S/'model/new-cloth-boots.png').save(D/'new-cloth-boots.png')
before=np.array(Image.open(S/'model/native.png').convert('RGBA'));after=before.copy();V=np.array(M['vertices'])
targets=['close sloping cloth crown','short folded nape cloth','left shaped dark sleeve','right shaped dark sleeve','left sewn sleeve top','right sewn sleeve top']
targets += [p for p in M['parts'] if any(k in p for k in ['leg garment','cloth tab','hip cloth']) and 'hem facing' not in p]
records=json.loads((ROOT/'cloth-texture-25/baseline23/UV-provenance.json').read_text())['islands'];allowed=np.zeros(before.shape[:2],bool)
def retained_paint(p,piece,role):
 x,y,z=p
 if 'leg garment' in piece:
  side=piece.startswith('left');cx=6 if side else 10
  x,y,z=rotate(np.array([p]),[cx,12,8],5 if side else -5)[0]
  if role!='surface':return COL['pants'][0]
  # Connected leg mass, restrained below the head/collar/chest hierarchy.
  i=1
  if z<7.7 and cx-1.1<x<cx+.7:i=2
  if 5.5<y<7.4 and z<6.3 and x>cx:i=1
  if z>9.8 or y<3.5:i=0 if abs(x-cx)>1.4 else 1
  return COL['pants'][i]
 if 'cloth tab' in piece or 'hip cloth' in piece:
  if role!='surface':return COL['navy'][0]
  cx=5 if piece.startswith('left') else 11;i=1
  # Upper root connects to a narrowing long fold; no light hip badge.
  width=1.25 if y<11.5 else 1.8
  if abs(x-cx)<width and z<7.5:i=2
  if y>13.0 and cx-.5<x<cx+.5 and z<7.5:i=3
  if 'side fold' in piece or z>10.5:i=0 if y<11 else 1
  return COL['navy'][i]
 if role!='surface':return COL['navy'][0]
 if 'crown' in piece or 'nape' in piece:
  i=1
  # Crown/side plane has a connected middle value, not a rear badge.
  if y>=31.2 and 4.5<x<10.2:i=2
  if y>=33.2 and 5.2<x<7.5 and z<9:i=3
  if z>=11:
   # The middle-value field reaches the real left return, rather than sitting
   # inside a dark rectangular frame like a pasted patch on the hood back.
   i=2 if y>=27 and x<11-(33-y)*.8 else 1
   if x>11.4:i=0
  return COL['navy'][i]
 side=piece.startswith('left');x,y,z=rotate(np.array([p]),[2 if side else 14,24,8],8 if side else -8)[0];cx=2 if side else 14
 i=2 if cx-.9<x<cx+.6 and z<7.5 and 16.4<y<20.7 else 1
 if z>9.8 or abs(x-cx)>1.7:i=0
 if 18<y<19 and z<7:i=1
 return COL['navy'][i]
for rec in records:
 matches=[p for p in targets if rec['piece'].startswith(p+' ')]
 if not matches:continue
 piece=matches[0];x,y,w,h=rec['island'];allowed[y:y+h,x:x+w]=True
 for f in [f for f in M['faces'] if f['piece']==piece]:
  uv=np.array(f['uv']);cross=lambda a,b:float(a[0]*b[1]-a[1]*b[0]);den=cross(uv[1]-uv[0],uv[2]-uv[0]);lo=np.maximum([x,y],np.floor(uv.min(0)).astype(int));hi=np.minimum([x+w-1,y+h-1],np.ceil(uv.max(0)).astype(int))
  for py in range(lo[1],hi[1]+1):
   for px in range(lo[0],hi[0]+1):
    q=np.array([px+.5,py+.5])-uv[0];b=cross(q,uv[2]-uv[0])/den;c=cross(uv[1]-uv[0],q)/den;a=1-b-c
    if min(a,b,c)>=-1e-8:after[py,px]=retained_paint(np.array([a,b,c])@V[f['vertices']],piece,f['role'])
diff=np.any(after!=before,2);assert not np.any(diff&~allowed);Image.fromarray(after).save(D/'native.png')
NEW=Mesh('30 integrated cloth upper body');atlas=np.zeros((512,512,4),np.uint8);ax=2;ay=2;rh=0;islands=[]
def tile(w,h,painter,inside):
 global ax,ay,rh
 if ax+w+6>512:ax=2;ay+=rh+4;rh=0
 ox,oy=ax+2,ay+2
 for y in range(-2,h+3):
  for x in range(-2,w+3):atlas[oy+y,ox+x]=painter(max(0,min(w-1,x)),max(0,min(h-1,y)),inside)
 ax+=w+6;rh=max(rh,h+5);return ox,oy
def author(rows,piece,mat,painter,w,h,th=.06,vsteps=None):
 raw=np.array(rows,float);nr,nc=raw.shape[:2];start=len(NEW.faces);NEW.shell(rows,piece,mat,'lining' if mat=='plum' else 'canvas_shadow',th)
 u=np.r_[0,np.cumsum(np.linalg.norm(np.diff(raw[min(1,nr-1)],axis=0),axis=1))];u=u/u[-1]*w;vs=np.array(vsteps if vsteps is not None else np.linspace(0,h,nr));lookup={tuple(np.round(p,8)):[float(u[c]),float(vs[r])] for r,row in enumerate(raw) for c,p in enumerate(row)}
 periodic=np.linalg.norm(raw[0,0]-raw[0,-1])<1e-7
 places={role:tile(w,h,painter,role!='surface') for role in ['surface','inside']};edge=tile(2,2,lambda x,y,inside:COL['linen' if mat=='canvas' else 'navy'][1],False)
 previous=None
 for f in NEW.faces[start:]:
  if f['role']=='surface':
   q=[lookup[tuple(np.round(NEW.v[i],8))].copy() for i in f['vertices']]
   if periodic and max(p[0] for p in q)-min(p[0] for p in q)>w*.5:
    mid=[p[0] for p in q if .001<p[0]<w-.001];seam=w if np.mean(mid)>w*.5 else 0;q=[[seam if p[0]<.001 or p[0]>w-.001 else p[0],p[1]] for p in q]
   previous=q
  elif f['role']=='inside':q=previous[::-1]
  else:q=[[0,0],[2,0],[0,2]]
  ox,oy=places[f['role']] if f['role'] in places else edge;f['uv']=[[ox+p[0],oy+p[1]] for p in q];f['texture_index']=2
 islands.append({'piece':piece,'logical_size':[w,h],'outer':places['surface'],'inner':places['inside'],'thickness':th,'folds_are_real_mesh':True})
def navy(x,y,inside):return COL['navy'][0 if inside else 1]
# A returned face opening instead of a detached brow strip over a skin wedge.
outer=[[3.40,29.3,3.63],[3.40,31.8,3.63],[4.25,32.75,3.63],[5.9,33.65,3.63],[7.45,33.7,3.63],[10,33.05,3.63],[11.75,32.55,3.63],[12.60,31.8,3.63],[12.60,29.3,3.63]]
inner=[[4.35,27.05,3.28],[4.4,31.2,3.3],[4.85,31.8,3.3],[6.2,32.05,3.3],[8,32.15,3.3],[10,32.05,3.3],[11.2,31.8,3.3],[11.6,31.2,3.3],[11.65,27.05,3.28]]
crest=(np.array(outer)*.48+np.array(inner)*.52);crest[:,2]=3.15
ret=np.array(inner);ret[:,2]=3.42;ret[1:-1,1]+=.04;ret[0,0]+=.08;ret[-1,0]-=.08
def facepaint(x,y,inside):
 i=0 if inside else (2 if y<3 else 1)
 if not inside and y<=1 and 9<=x<=13:i=3
 if not inside and (x<4 or x>21):i=1 if y<3 else 0
 return COL['navy'][i]
author([outer,crest.tolist(),inner,ret.tolist()],'returned hood face opening','plum',facepaint,26,4,.05,[0,1.5,3.3,4])
# Thin side gussets physically cover the former triangular skin gaps.
for side,x in [('left',3.40),('right',12.60)]:
 rows=[[[x,31.85,3.28],[x,31.8,5.6],[x,30.5,8],[x,29.5,8.85]],[[x,26.9,3.28],[x,27.08,5.6],[x,27.2,8],[x,27.15,8.85]]]
 if side=='left':rows=[row[::-1] for row in rows]
 author(rows,side+' sewn hood cheek gusset','plum',navy,6,3,.05)
# The turned front edge joins the side gusset to the opening, closing the
# isolated skin triangle at the head-box corner. Main facial opening remains.
for side in ['left','right']:
 rows=[[[3.40,29.3,3.10],[4.4,29.3,3.10]],[[3.40,27.0,3.10],[4.35,27.0,3.10]]]
 if side=='right':rows=[[[16-p[0],p[1],p[2]] for p in row][::-1] for row in rows]
 author(rows,side+' turned front cheek return','plum',navy,1,3,.04)
cowl=[
 [[4.35,27.05,3.25],[8,26.0,3.55],[11.65,27.05,3.25],[12.6,27.05,3.25],[12.6,27.35,8],[12.6,27.05,12.65],[8,27.0,12.65],[3.4,27.05,12.65],[3.4,27.35,8],[3.4,27.05,3.25],[4.35,27.05,3.25]],
 [[4.1,25.9,3.35],[8,25.45,3.4],[11.9,25.9,3.35],[12.65,25.9,3.35],[12.65,25.8,8],[12.65,25.55,12.7],[8,25.6,12.7],[3.35,25.55,12.7],[3.35,25.8,8],[3.35,25.9,3.35],[4.1,25.9,3.35]],
 [[4.2,24.2,3.6],[8,24.2,3.65],[11.8,24.2,3.6],[12.5,24.2,3.6],[12.5,24.15,8],[12.5,24.15,12.65],[8,24.15,12.65],[3.5,24.15,12.65],[3.5,24.15,8],[3.5,24.2,3.6],[4.2,24.2,3.6]],
 [[4.25,23.0,4.7],[8,22.85,5.05],[11.75,23.0,4.7],[12.45,24.05,4.7],[12.5,24.05,8],[12.5,24.05,12.55],[8,23.05,12.55],[3.5,24.05,12.55],[3.5,24.05,8],[3.55,24.05,4.7],[4.25,23.0,4.7]]]
def cowlpaint(x,y,inside):
 i=0 if inside else 1
 if not inside and y==1 and x<11:i=2
 if not inside and x>16 and y>=2:i=0
 return COL['navy'][i]
author(cowl,'folded hood to neck cowl','plum',cowlpaint,40,5,.055,[0,1.5,3.5,5])
# Real overlap and longitudinal folds, with lower edges tucked into the belt.
lf=[[[3.95,23.1,5.5],[4.85,23.05,5.3],[5.7,22.95,5.15],[6.7,22.65,5.3],[7.9,22.25,5.34]],[[3.95,21,5.45],[4.85,21,5.28],[5.8,21,5.0],[6.8,21,5.18],[7.9,21,5.27]],[[4.05,18,5.48],[4.95,18,5.3],[5.95,18,5.08],[6.8,18,5.25],[7.85,18,5.33]],[[4.35,14.3,5.58],[5.1,14.3,5.48],[6,14.3,5.39],[6.9,14.3,5.49],[7.8,14.3,5.55]]]
rf=[[[7.65,22.2,4.98],[8.7,22.7,5.22],[9.8,22.9,5.14],[10.95,23.0,5.35],[12.05,23.1,5.5]],[[7.65,21,4.9],[8.7,21,5.25],[9.8,21,5.08],[10.95,21,5.35],[12.05,21,5.5]],[[7.7,18,5.0],[8.7,18,5.29],[9.8,18,5.16],[10.85,18,5.4],[11.95,18,5.54]],[[7.72,14.3,5.24],[8.65,14.3,5.48],[9.7,14.3,5.39],[10.75,14.3,5.49],[11.65,14.3,5.58]]]
leftpattern=['1110','1221','1221','1211','1211','1210','1210','1110','0110'];rightpattern=['0111','0122','0122','0121','0121','0121','0111','0111','0011']
def pattern(p):return lambda x,y,inside:COL['navy'][0 if inside else int(p[min(len(p)-1,y)][min(len(p[0])-1,x)])]
author(lf,'left longitudinal folded coat front','plum',pattern(leftpattern),4,9,.06,[0,2,5,9]);author(rf,'right overlapping coat front','plum',pattern(rightpattern),4,9,.06,[0,2,5,9])
rear=[]
for y,lo,hi,zs in [(22.9,4,12,[10.6,10.8,10.96,10.73,10.6]),(21,3.95,12.05,[10.65,10.85,11.02,10.78,10.65]),(18,4.05,11.95,[10.65,10.84,10.97,10.76,10.65]),(14.3,4.35,11.65,[10.5,10.68,10.8,10.66,10.5])]:rear.append([[float(x),y,z] for x,z in zip(np.linspace(lo,hi,5),zs)][::-1])
backpattern=['11122111','11222211','11222211','11222111','11122111','11122111','01122110','01111110','00111100'];author(rear,'continuous shaped coat back','plum',pattern(backpattern),8,9,.06,[0,2,5,9])
for side,front,col,bc in [('left',lf,0,-1),('right',rf,-1,0)]:
 rows=[]
 for ri in range(4):
  y=front[ri][col][1];offset=[3.96,3.93,3.9,3.9][ri];x=offset if side=='left' else 16-offset
  row=[front[ri][col],[x,y,6.05],[x,y,8],[x,y,10.25],rear[ri][bc]]
  rows.append(row if side=='right' else row[::-1])
 author(rows,side+' continuous fitted coat side','plum',navy,6,9,.055,[0,2,5,9])
# Cloth width follows shoulder support and drops farther on one side.
inner=np.array([[8,22.7,4.55],[10.4,23.45,4],[12.65,24.45,3.85],[12.65,24.8,8],[12.65,24.45,12.65],[10.4,24.15,12.65],[8,24.1,12.65],[5.6,24.15,12.65],[3.35,24.45,12.65],[3.35,24.8,8],[3.35,24.45,3.85],[5.6,23.45,4],[8,22.7,4.55]])
ridge=np.array([[8,22.25,4.55],[11.1,23.3,4.55],[14.3,24.95,4.85],[14.45,25.35,8],[14.3,25.35,11.2],[11.15,23.75,12.6],[8,23.6,12.6],[4.85,23.75,12.6],[1.7,24.95,11.2],[1.55,25.35,8],[1.7,24.95,4.85],[4.9,23.3,4.55],[8,22.25,4.55]])
outer=np.array([[8,20.4,4.55],[12,21.1,4.55],[16.7,21.45,4.85],[17.85,22.15,8],[17.45,21.55,11.2],[11.85,21.0,12.6],[8,20.2,12.6],[4.15,18.9,12.6],[-1.45,20.2,11.2],[-1.85,21.5,8],[-.7,20.3,4.85],[4,18.85,4.55],[8,20.4,4.55]])
fall=ridge*.43+outer*.57
for ci,p in {2:[16.8,24.85,4.85],3:[17.4,24.85,8],4:[17.2,24.85,11.2],8:[-1.2,24.85,11.2],9:[-1.4,24.85,8],10:[-.8,24.85,4.85]}.items():fall[ci]=p
# Front/rear broad panels now have coherent surface directions. A small
# longitudinal folded ridge is physically authored rather than diagonal fans.
mantle_rows=[inner,ridge,fall,outer]
for ri,row in enumerate(mantle_rows):
 p=row[11]*.42+row[12]*.58;p[2]=[4.5,4.28,4.30,4.34][ri]
 mantle_rows[ri]=np.insert(row,12,p,axis=0)
def mantlepaint(x,y,inside):
 i=1 if inside else 2
 # Connected stepped fields follow the long left fall and its real fold.
 s=x if x<38 else x-76
 if not inside:
  if -11+y//4<=s<=-3-y//5 and y<=10:i=3
  if 3<=s<=7 and y<=3:i=3
  if (s in [-2,-1] and y>=3) or (s<-12 and y>=7):i=1
  if 25<=x<=45 and y>=4:i=1 if x>=37+y//3 else 2
 return COL['linen'][i]
author(mantle_rows,'supported falling shoulder cloth','canvas',mantlepaint,76,12,.055,[0,3,6,12])
Image.fromarray(atlas).save(D/'structure-paint.png')
offset=len(M['vertices']);M['vertices']+=NEW.v
for f in NEW.faces:f['vertices']=[v+offset for v in f['vertices']]
M['faces']+=NEW.faces;M['parts'].update(NEW.parts)
used=sorted({v for f in M['faces'] for v in f['vertices']});remap={v:i for i,v in enumerate(used)};M['vertices']=[M['vertices'][i] for i in used]
for f in M['faces']:f['vertices']=[remap[v] for v in f['vertices']]
M.update(name='ProjectS30 integrated cloth upper body / quality work in progress',stage='upper body structural remake; native inspection pending',aesthetic_approval_claimed=False,game_wear_complete=False,concept_approved=False)
write(D/'model.json',M)
exec((R/'export30.py').read_text())
write(R/'authoring-provenance.json',{'status':'UPPER_BODY_NATIVE_PENDING','baseline29_preserved':True,'approved_material_v1_primary':True,'MatE_gallery_actual_static_armor_seen':True,'MatE_geometry_pixels_copied':False,'removed29_parts':drop,'new_parts':NEW.parts,'new_islands':islands,'retained_atlas_pixels_changed':int(diff.sum()),'body_hardware_belt_boots_preserved':True,'vertices':len(M['vertices']),'triangles':len(M['faces']),'meshes':len(elements),'no_full_quality_or_wear_claim':True})
print(json.dumps({'vertices':len(M['vertices']),'triangles':len(M['faces']),'meshes':len(elements),'new_structural_parts':list(NEW.parts),'status':'upper body native pending'}))
