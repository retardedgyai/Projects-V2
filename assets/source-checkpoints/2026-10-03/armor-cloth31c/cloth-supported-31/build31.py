"""Real shallow hood folds and shoulder fall from supports; separate30 copy.
No blanket ornament; paint fields are authored on the corresponding real folds.
"""
from pathlib import Path
from collections import defaultdict
import sys,json,copy,uuid,base64,hashlib,math,shutil
import numpy as np
from PIL import Image,ImageColor
sys.dont_write_bytecode=True
R=Path(__file__).resolve().parent;ROOT=R.parent
sys.path.insert(0,str(ROOT/'cloth-layered-23'))
from mesh_tools import Mesh,write
S=ROOT/'cloth-structure-30';D=R/'model';D.mkdir(parents=True,exist_ok=True)
M=copy.deepcopy(json.loads((S/'model/model.json').read_text()))
DROP=['close sloping cloth crown','short folded nape cloth','left sewn hood cheek gusset','right sewn hood cheek gusset','supported falling shoulder cloth','folded hood to neck cowl','left turned front cheek return','right turned front cheek return']
M['faces']=[f for f in M['faces'] if f['piece'] not in DROP]
M['parts']={p:v for p,v in M['parts'].items() if p not in DROP}
for name in ['native.png','new-cloth-boots.png','structure-paint.png']:shutil.copyfile(S/'model'/name,D/name)
PAL={'navy':['#192a42','#2b425f','#47637d','#728f9e','#a3bdbe'],'linen':['#53594f','#827e6b','#b7aa8b','#e3d1a8','#f4e5bb']}
COL={k:np.array([ImageColor.getcolor(c,'RGBA') for c in cs],np.uint8) for k,cs in PAL.items()}
NEW=Mesh('31 supported cloth folds');atlas=np.zeros((512,512,4),np.uint8);ax=2;ay=2;rh=0;islands=[]

def tile(w,h,painter,inside):
 global ax,ay,rh
 if ax+w+6>512:ax=2;ay+=rh+4;rh=0
 ox,oy=ax+2,ay+2
 for y in range(-2,h+3):
  for x in range(-2,w+3):atlas[oy+y,ox+x]=painter(max(0,min(w-1,x)),max(0,min(h-1,y)),inside)
 ax+=w+6;rh=max(rh,h+5);return ox,oy

def author(rows,piece,mat,paint,w,h,th=.06,vsteps=None):
 raw=np.array(rows,float);nr,nc=raw.shape[:2];start=len(NEW.faces)
 NEW.shell(rows,piece,mat,'lining' if mat=='plum' else 'canvas_shadow',th)
 u=np.r_[0,np.cumsum(np.linalg.norm(np.diff(raw[min(1,nr-1)],axis=0),axis=1))];u=u/u[-1]*w
 vs=np.array(vsteps if vsteps is not None else np.linspace(0,h,nr))
 lookup={tuple(np.round(p,8)):[float(u[c]),float(vs[r])] for r,row in enumerate(raw) for c,p in enumerate(row)}
 periodic=np.linalg.norm(raw[0,0]-raw[0,-1])<1e-7
 def painter(x,y,inside):
  # Evaluate one coarse UV pixel on the manually authored cloth surface.
  # This makes the material field follow its crease, not a face-number patch.
  xx=min(w-1e-6,x+.5);yy=min(h-1e-6,y+.5)
  c=min(nc-2,int(np.searchsorted(u,xx,side='right')-1));r=min(nr-2,int(np.searchsorted(vs,yy,side='right')-1))
  a=(xx-u[c])/(u[c+1]-u[c]);b=(yy-vs[r])/(vs[r+1]-vs[r])
  # Match the actual two triangles' barycentric UV mapping, rather than
  # bilinear quad sampling, which misplaced colour edges on twisted folds.
  if b<=a:p=raw[r,c]*(1-a)+raw[r,c+1]*(a-b)+raw[r+1,c+1]*b
  else:p=raw[r,c]*(1-b)+raw[r+1,c+1]*a+raw[r+1,c]*(b-a)
  return paint(p,inside,xx,yy)
 places={role:tile(w,h,painter,role!='surface') for role in ['surface','inside']}
 edge=tile(2,2,lambda x,y,inside:COL['linen' if mat=='canvas' else 'navy'][1],False)
 previous=None
 for f in NEW.faces[start:]:
  if f['role']=='surface':
   q=[lookup[tuple(np.round(NEW.v[i],8))].copy() for i in f['vertices']]
   if periodic and max(p[0] for p in q)-min(p[0] for p in q)>w*.5:
    mid=[p[0] for p in q if .001<p[0]<w-.001];seam=w if np.mean(mid)>w*.5 else 0
    q=[[seam if p[0]<.001 or p[0]>w-.001 else p[0],p[1]] for p in q]
   previous=q
  elif f['role']=='inside':q=previous[::-1]
  else:q=[[0,0],[2,0],[0,2]]
  ox,oy=places[f['role']] if f['role'] in places else edge
  f['uv']=[[ox+p[0],oy+p[1]] for p in q];f['texture_index']=3
 islands.append({'piece':piece,'logical_size':[w,h],'outer':places['surface'],'inner':places['inside'],'thickness':th,'paint_follows_authored_surface':True})

def hoodpaint(p,inside,*_):
 if inside:return COL['navy'][0]
 x,y,z=p;i=1
 if y>=31.6:i=2
 if y>33.5 and 5<x<8.5 and z<9:i=3
 if (x>12.65 or x<3.35) and 29.0<y<31.7:i=2
 if y<27.7 and z>5.8:i=1
 if z>12.72 and x<8.0 and y>26.5:i=2
 return COL['navy'][i]

# Front joins the preserved30 face opening. The side crease grows gradually
# toward the temple and returns into the nape; no flat full-height cheek wall.
front=[[12.6,26.9],[12.6,30.55],[12.6,31.8],[11.75,32.55],[10,33.05],[7.45,33.7],[5.9,33.65],[4.25,32.75],[3.4,31.8],[3.4,30.55],[3.4,26.9]]
profiles=[front,
 [[12.65,27.08],[12.82,30.15],[12.65,32.0],[11.75,32.8],[10,33.35],[7.45,34.0],[5.85,34.0],[4.25,33.05],[3.35,32.0],[3.18,30.15],[3.35,27.08]],
 [[12.65,27.2],[12.95,29.5],[12.65,32.1],[11.75,32.9],[10,33.4],[7.45,34.05],[5.85,34.05],[4.25,33.15],[3.35,32.1],[3.05,29.5],[3.35,27.2]],
 [[12.55,26.9],[12.83,29.2],[12.55,32.0],[11.75,32.7],[10,33.2],[7.45,33.75],[5.95,33.75],[4.25,33.0],[3.45,32.0],[3.17,29.2],[3.45,26.9]],
 [[12.55,26.4],[12.65,29.3],[12.55,31.9],[11.75,32.5],[10,33.0],[7.45,33.5],[6,33.5],[4.25,32.75],[3.45,31.9],[3.35,29.3],[3.45,26.4]]]
rows=[[[x,y,z] for x,y in p] for p,z in zip(profiles,[3.63,5.7,8,10,12.6])]
crown_arc=np.r_[0,np.cumsum(np.linalg.norm(np.diff(np.array(rows[1]),axis=0),axis=1))];crown_arc=crown_arc/crown_arc[-1]*30
def crownpaint(p,inside,u,v):
 if inside:return COL['navy'][0]
 x,y,z=p;i=1
 # One connected field follows the authored side crease around the crown.
 if crown_arc[1]-.45<=u<=crown_arc[-2]+.45:i=2
 if y>33.5 and 5<x<8.5 and z<9:i=3
 return COL['navy'][i]
author(rows,'continuous shallow folded hood crown','plum',crownpaint,30,10,.065,[0,2.4,5,7.2,10])

top=np.array(rows[-1][2:9]);nape=[top.tolist()]
for ys,zs in [([30.0,30.3,30.5,30.25,30.45,30.4,30.0],[12.65,12.88,13.00,12.72,13.05,12.88,12.65]),([27.8,27.5,27.3,26.9,27.4,27.5,27.8],[12.65,12.82,12.94,12.73,13.00,12.84,12.65]),([26.4,26.1,26.0,25.85,26.15,26.1,26.4],[12.65,12.77,12.86,12.75,12.90,12.80,12.65])]:
 nape.append([[float(x),y,z] for x,y,z in zip(top[:,0],ys,zs)])
author(nape,'folded crown to nape cloth','plum',hoodpaint,10,9,.065,[0,3,6,9])

# One hood surface now reaches the cowl. A separate temple wall would intersect
# its shallow fold and create the jagged colour islands seen in the first trial.
cowl=[
 [[4.35,27.05,3.25],[8,26.0,3.55],[11.65,27.05,3.25],[12.6,27.05,3.25],[12.6,27.35,8],[12.6,27.05,12.65],[8,27.0,12.65],[3.4,27.05,12.65],[3.4,27.35,8],[3.4,27.05,3.25],[4.35,27.05,3.25]],
 [[4.1,25.9,3.35],[8,25.45,3.4],[11.9,25.9,3.35],[12.65,25.9,3.35],[12.65,25.8,8],[12.65,25.55,12.7],[8,25.6,12.7],[3.35,25.55,12.7],[3.35,25.8,8],[3.35,25.9,3.35],[4.1,25.9,3.35]],
 [[4.2,23.95,3.6],[8,23.95,3.65],[11.8,23.95,3.6],[12.5,24.2,3.6],[12.5,24.15,8],[12.5,24.15,12.65],[8,24.15,12.65],[3.5,24.15,12.65],[3.5,24.15,8],[3.5,24.2,3.6],[4.2,23.95,3.6]],
 [[4.25,23.6,4.7],[8,23.65,5.05],[11.75,23.6,4.7],[12.45,24.05,4.7],[12.5,24.05,8],[12.5,24.05,12.55],[8,23.8,12.55],[3.5,24.05,12.55],[3.5,24.05,8],[3.55,24.05,4.7],[4.25,23.6,4.7]]]
def cowlpaint(p,inside,*_):
 if inside:return COL['navy'][0]
 x,y,z=p;i=1
 if 25.4<y<26.0 and z<3.5:i=2
 if z>10 and y<25.4:i=0
 return COL['navy'][i]
author(cowl,'short gathered hood neck cowl','plum',cowlpaint,40,5,.055,[0,1.5,3.5,5])
for side in ['left','right']:
 # Real return joins the crown's front side edge at z3.63 to the front
 # facing at z3.10. A front-only strip left the head side visible in oblique.
 rows=[[[3.40,31.2,3.63],[3.40,31.2,3.10],[4.4,31.2,3.10]],[[3.40,27.0,3.63],[3.40,27.0,3.10],[4.35,27.0,3.10]]]
 if side=='right':rows=[[[16-p[0],p[1],p[2]] for p in row][::-1] for row in rows]
 author(rows,side+' continuous front hood return','plum',lambda p,inside,*_:COL['navy'][0 if inside else 1],2,5,.04)

# Replace the annular shoulder shelf with two narrow supported lengths.
# Each travels in depth over a shoulder, turns down and overlaps in front.
# This is a real ribbon with width, a shallow centre crease and closed edges;
# it is not the silhouette of a painted scarf cut out on a flat card.
left_wrap=[
 [[4.2,24.2,12.95],[1.8,25.1,8],[2.2,24.65,4.75],[5.3,23.2,4.65],[8.5,21.4,4.65],[11.5,19.45,4.65]],
 [[2.8,24.15,12.9],[.65,24.85,8],[1.8,23.65,4.5],[4.75,22.25,4.35],[7.95,20.45,4.35],[10.95,18.6,4.42]],
 [[1.4,23.8,12.85],[-.5,23.65,8],[1.4,22.65,4.75],[4.2,21.3,4.68],[7.4,19.5,4.7],[10.3,17.75,4.7]]]
right_wrap=[
 [[12.1,24.15,12.75],[14.25,25.1,8],[13.9,24.5,4.75],[11.3,23.15,4.38],[7.95,21.0,4.2]],
 [[13.1,24.0,12.72],[15.45,24.85,8],[14.4,23.55,4.5],[11.8,22.1,4.2],[8.45,20.0,4.0]],
 [[14.1,23.8,12.65],[16.55,23.65,8],[14.9,22.6,4.75],[12.3,21.0,4.4],[8.95,19.0,4.2]]]
# Explicit arm-front/back turns keep the cloth around the actual arm box;
# a chord from top support straight to front chest would cut through the arm.
for ribbon in [left_wrap,right_wrap]:
 for ri,row in enumerate(ribbon):
  x,y,_=row[1]
  ribbon[ri]=[row[0],[x,y,10.55],row[1],[x,y,5.55],*row[2:]]
def clothpaint(p,inside,*_):
 if inside:return COL['linen'][1]
 x,y,z=p;i=2
 if y>24.7 and 6<z<9.5:i=3
 if z<4.45 and 4.5<x<9:i=3
 if z<4.38 and x>10.2 and y>21:i=3
 if z>9.5 and y<24.0:i=1
 return COL['linen'][i]
author(left_wrap,'left shoulder to chest diagonal cloth','canvas',clothpaint,25,3,.055,[0,1.5,3])
author([row[::-1] for row in right_wrap],'right shoulder overlapping cloth return','canvas',clothpaint,20,3,.055,[0,1.5,3])

Image.fromarray(atlas).save(D/'hood-shoulder.png')
offset=len(M['vertices']);M['vertices']+=NEW.v
for f in NEW.faces:f['vertices']=[i+offset for i in f['vertices']]
M['faces']+=NEW.faces;M['parts'].update(NEW.parts)
used=sorted({i for f in M['faces'] for i in f['vertices']});remap={i:k for k,i in enumerate(used)};M['vertices']=[M['vertices'][i] for i in used]
for f in M['faces']:f['vertices']=[remap[i] for i in f['vertices']]
M.update(name='ProjectS31 hood folds and supported shoulder fall',stage='representative upper-body trial; native inspection required',aesthetic_approval_claimed=False,game_wear_complete=False,concept_approved=False)
write(D/'model.json',M);exec((R/'export31.py').read_text())
write(R/'authoring-provenance.json',{'baseline30_preserved':True,'removed_parts':DROP,'new_parts':NEW.parts,'new_texture_islands':islands,'copied_three_source_atlases_byte_exact':True,'world_corresponding_pixel_fields':True,'body_face_opening_chest_belt_lowerbody_unchanged':True,'neck_cowl_lower_edge_shortened':True,'no_simulation_or_external_AI':True,'vertices':len(M['vertices']),'triangles':len(M['faces']),'meshes':len(elements),'native_inspection_complete':False,'aesthetic_acceptance_claimed':False})
print(json.dumps({'vertices':len(M['vertices']),'triangles':len(M['faces']),'meshes':len(elements),'new_parts':list(NEW.parts),'native_inspection':'pending'}))
