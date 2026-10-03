def unit(v):return v/np.linalg.norm(v)
def euler(r):
 x,y,z=np.radians([r[k] for k in 'xyz']);cx,sx=np.cos(x),np.sin(x);cy,sy=np.cos(y),np.sin(y);cz,sz=np.cos(z),np.sin(z)
 return np.array([[cz,-sz,0],[sz,cz,0],[0,0,1]])@np.array([[cy,0,sy],[0,1,0],[-sy,0,cy]])@np.array([[1,0,0],[0,cx,-sx],[0,sx,cx]])
def rotation(u,v,origin):
 m=np.column_stack([u,v,np.cross(u,v)]);y=math.asin(np.clip(-m[2,0],-1,1))
 if abs(math.cos(y))>1e-7:x=math.atan2(m[2,1],m[2,2]);z=math.atan2(m[1,0],m[0,0])
 else:x=0;z=math.atan2(-m[0,1],m[1,1])
 r={'origin':origin.tolist(),'x':math.degrees(x),'y':math.degrees(y),'z':math.degrees(z),'rescale':False}
 assert np.allclose(euler(r),m,atol=1e-7)
 return r

def cleaved_solid(part):
 if part.get('kind')=='root-bridge':return root_bridge(part)
 o=np.array(part['origin']);axis=np.array(part['axis']);direction=unit(axis)
 u=unit(np.cross(direction,[0,0,1]));v=np.cross(u,direction);n=part['n']
 section=np.array([[-.50,-.25],[-.22,-.50],[.42,-.42],[.50,.24],[.10,.50],[-.50,.25]]) if n==6 else np.array([[-.50,-.40],[.42,-.50],[.50,.33],[-.34,.50]])
 assert part['profiles'][-2][0]<min(part['ends']),'Broken terminal must remain beyond its preceding section'
 vertices=[]
 for ri,(t,w,d,su,sv,right,left) in enumerate(part['profiles']):
  for j,(sx,sz) in enumerate(section):
   longitudinal=t if ri<len(part['profiles'])-1 else t*part['ends'][j]
   # Only one side loses a chunk; not a uniform collar around the shaft.
   bite=(-right if sx>0 else left)
   vertices.append(o+axis*longitudinal+u*((sx*w+bite)*part['width']+su)+v*(sz*d*part['depth']+sv))
 vertices=np.array(vertices);triangles=[];break_faces=[];tags=[]
 for ri in range(len(part['profiles'])-1):
  for j in range(n):
   k=(j+1)%n;a=ri*n+j;b=ri*n+k;c=(ri+1)*n+k;dd=(ri+1)*n+j
   triangles.extend([[a,b,c],[a,c,dd]]);tags.extend([f'side-{j}',f'side-{j}'])
   abrupt=part['profiles'][ri+1][0]-part['profiles'][ri][0]<.06
   break_faces.extend([abrupt,abrupt])
 for j in range(1,n-1):
  triangles.extend([[0,j+1,j],[(len(part['profiles'])-1)*n,(len(part['profiles'])-1)*n+j,(len(part['profiles'])-1)*n+j+1]])
  break_faces.extend([False,True]);tags.extend(['base','terminal'])
 # Ring connection winding is consistent even where a section is concave.
 if sum(np.dot(vertices[ids[0]],np.cross(vertices[ids[1]],vertices[ids[2]])) for ids in triangles)<0:
  triangles=[ids[::-1] for ids in triangles]
 # Paint as fracture only where the actual break surface turns toward the axis.
 # A short ring interval alone must not produce a white collar around every side.
 for i,ids in enumerate(triangles):
  if break_faces[i]:
   a,b,c=vertices[ids];normal=unit(np.cross(b-a,c-a))
   break_faces[i]=bool(abs(np.dot(normal,direction))>.34)
 directed=collections.Counter((a,b) for ids in triangles for a,b in zip(ids,ids[1:]+ids[:1]))
 assert all(count==directed[(b,a)]==1 for (a,b),count in directed.items()),part['name']
 return vertices,triangles,break_faces,u,direction,tags


def basis_candidates(points):
 candidates=[]
 for shift in range(3):
  q=np.roll(points,-shift,axis=0);a,b,c=q
  assert np.linalg.norm(np.cross(b-a,c-a))>1e-10
  u=unit(b-a);v=unit(c-a-u*np.dot(c-a,u));r=rotation(u,v,np.zeros(3))
  dist=[abs(r[j]%180-90) for j in 'xyz'];score=min([dd for dd in dist if dd>1e-6] or [90])
  candidates.append((score,q,u,v,r))
 return candidates

def prepare_native_bases(name,parts):
 elements=[]
 for part in parts:
  vertices,triangles,*_=cleaved_solid(part)
  for ids in triangles:
   for _,q,u,v,r in basis_candidates(vertices[ids]):
    r={**r,'origin':[8,8,8]}
    elements.append({'from':[8,8,8],'to':[24,24,8],'rotation':r,
                     'faces':{'south':{'texture':'#facets','uv':[0,0,16,16]}}})
 (ASSETS/(name+'-basis.model.json')).write_text(json.dumps({'textures':{'facets':'projects:study/'+name},'elements':elements},indent=2)+'\n')
 print(json.dumps({'temporary_native_basis_candidates':len(elements),'same_exact_source_geometry':True}))

def author(name,parts,clay):
 primitives=[];pieces=[]
 for pi,part in enumerate(parts):
  vertices,triangles,break_faces,u,direction,tags=cleaved_solid(part)
  primitives.append({**part,'vertices':vertices.tolist(),'triangles':triangles,'break_faces':break_faces,'closed':True,'surface_tags':tags})
  for ids,fracture,tag in zip(triangles,break_faces,tags):pieces.append((vertices[ids],pi,u,direction,fracture,tag))
 cols=16;tw,th=48,80;rows=math.ceil(len(pieces)/cols)
 atlas=Image.new('RGBA',(cols*tw,rows*th));elements=[]
 basis_file=ASSETS/(name+'-basis.native-faces.json')
 basis_readback=json.loads(basis_file.read_text())['faces'] if basis_file.exists() else None
 if basis_readback is not None:assert len(basis_readback)==len(pieces)*3
 yy,xx=np.mgrid[:th,:tw];samples=np.stack([xx/(tw-1),1-yy/(th-1)],axis=-1)
 for k,(points,pi,body_u,direction,fracture,tag) in enumerate(pieces):
  candidates=basis_candidates(points)
  if basis_readback is None:points=max(candidates,key=lambda row:row[0])[1]
  else:
   errors=[]
   for ci,(_,q,cu,cv,_) in enumerate(candidates):
    face=basis_readback[k*3+ci];assert face['element']==k*3+ci
    by={j:np.array(p) for j,p in zip(face['cube_corner_ids'],face['points_m'])}
    actual_u=(by[5]-by[4])/6.25;actual_v=(by[6]-by[4])/6.25
    qa,qb,qc=q;coordinates=np.array([[0,0],[np.linalg.norm(qb-qa),0],[np.dot(qc-qa,cu),np.dot(qc-qa,cv)]])
    lo,hi=coordinates.min(axis=0),coordinates.max(axis=0)
    errors.append(max(np.linalg.norm(x*(actual_u-cu)+y*(actual_v-cv)) for x in [lo[0],hi[0]] for y in [lo[1],hi[1]]))
   points=candidates[int(np.argmin(errors))][1]
  a,b,c=points;u=unit(b-a);v=unit(c-a-u*np.dot(c-a,u));normal=np.cross(u,v)
  coords=np.array([[0,0],[np.linalg.norm(b-a),0],[np.dot(c-a,u),np.dot(c-a,v)]])
  low,high=coords.min(axis=0),coords.max(axis=0);size=high-low;local=(coords-low)/size;anchor=a+low[0]*u+low[1]*v
  world=anchor+samples[...,0,None]*size[0]*u+samples[...,1,None]*size[1]*v
  color=paint(world,normal,parts[pi],body_u,direction,fracture,clay,tag)
  mask=np.ones((th,tw),bool)
  for p,q in zip(local,np.roll(local,-1,axis=0)):
   edge=q-p;dist=edge[0]*(samples[...,1]-p[1])-edge[1]*(samples[...,0]-p[0]);tolerance=.52*(abs(edge[0])/(th-1)+abs(edge[1])/(tw-1))
   mask &= dist>=-tolerance
  rgba=np.concatenate([color,np.where(mask,255,0).astype(np.uint8)[...,None]],axis=-1);atlas.paste(Image.fromarray(rgba),((k%cols)*tw,(k//cols)*th))
  origin=8+anchor/SCALE*16;end=origin+np.array([*size,0])/SCALE*16;span,vs=16/cols,16/rows
  uv=[k%cols*span+span/(tw*2),k//cols*vs+vs/(th*2),(k%cols+1)*span-span/(tw*2),(k//cols+1)*vs-vs/(th*2)]
  elements.append({'name':parts[pi]['name']+'_'+str(k),'from':origin.tolist(),'to':end.tolist(),'shade':False,
                   'rotation':rotation(u,v,origin),'faces':{'south':{'texture':'#facets','uv':uv}}})
 model={'textures':{'facets':'projects:study/'+name},'elements':elements,
        '_part_for_element':[row[1] for row in pieces],
        '_physical_break_for_element':[row[4] for row in pieces],
        '_surface_tag_for_element':[row[5] for row in pieces],
        '_composition':'TALL MAIN, MEDIUM COLUMNS, FINE NEEDLES, ONE CONNECTED ROOT',
        '_status':'11 OWNED GEOMETRY; SHORT UNADOPTED PROTOTYPE; QUALITY GATE OPEN'}
 (ASSETS/(name+'.model.json')).write_text(json.dumps(model,indent=2)+'\n')
 (ASSETS/(name+'.mesh.json')).write_text(json.dumps(primitives,indent=2)+'\n')
 atlas.save(ASSETS/(name+('-clay.png' if clay else '-texture.png')))
 atlas.save(ASSETS/(name+'.png'))
 print(json.dumps({'asset':name,'parts':len(parts),'planes':len(pieces),'clay':clay,'whole_form_rebuilt':True,'shared_world_material':True,
                   'reference_assets_read':False,'repo_or_game_changed':False}))
