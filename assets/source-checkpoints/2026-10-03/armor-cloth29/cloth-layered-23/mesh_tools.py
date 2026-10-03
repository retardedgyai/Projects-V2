"""Small indexed low-poly shell authoring and portable editable exporters."""
from pathlib import Path
from collections import defaultdict
import json, base64, uuid
import numpy as np
from PIL import Image

THICK=.08
COLORS={'avatar':'#243c54','head':'#bc8e69','hand':'#bc8e69','boot':'#473d35',
        'tide':'#8fbdbe','pearl':'#d8e5d8','fold':'#527f8a',
        'hide':'#79543d','hide_edge':'#ae7950','wind':'#d4d8ca',
        'clay':'#b7c5c2'}

def write(path,value):
    path.write_text(json.dumps(value,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')

class Mesh:
    def __init__(self,name):
        self.name=name;self.v=[];self.faces=[];self.keys={};self.parts={}
    def vertex(self,p,piece):
        k=(piece,)+tuple(round(float(x),8) for x in p)
        if k not in self.keys:self.keys[k]=len(self.v);self.v.append(list(k[1:]))
        return self.keys[k]
    def face(self,points,piece,mat,role='surface'):
        ids=[self.vertex(p,piece) for p in points]
        for i in range(1,len(ids)-1):
            tri=[ids[0],ids[i],ids[i+1]]
            pp=np.array([self.v[k] for k in tri])
            if np.linalg.norm(np.cross(pp[1]-pp[0],pp[2]-pp[0]))<1e-8:continue
            self.faces.append({'vertices':tri,'piece':piece,'material':mat,'role':role,'texture':'flat'})
    def box(self,lo,hi,piece,mat,transform=None):
        vs=np.array([[hi[j] if i&(1<<j) else lo[j] for j in range(3)] for i in range(8)])
        if transform:vs=transform(vs)
        for ids in [[0,2,3,1],[4,5,7,6],[0,4,6,2],[1,3,7,5],[0,1,5,4],[2,6,7,3]]:
            self.face(vs[ids],piece,mat,'avatar')
    def shell(self,rows,piece,mat,inside_mat=None,thickness=THICK):
        """Manually supplied curved strip; 0.08 normal thickness, closed cut edges."""
        vv=np.array(rows,float);nr,nc=vv.shape[:2];raw=vv.reshape(-1,3)
        # Periodic loops weld their repeated seam before averaging shell normals.
        flat=[];weld={};remap=[]
        for p in raw:
            key=tuple(round(float(v),8) for v in p)
            if key not in weld:weld[key]=len(flat);flat.append(p)
            remap.append(weld[key])
        flat=np.array(flat)
        tris=[]
        for r in range(nr-1):
            for c in range(nc-1):
                a=r*nc+c;b=a+1;d=(r+1)*nc+c;e=d+1
                tris += [[remap[a],remap[b],remap[e]],[remap[a],remap[e],remap[d]]]
        normals=np.zeros_like(flat);edges=defaultdict(list)
        for tri in tris:
            p=flat[tri];n=np.cross(p[1]-p[0],p[2]-p[0]);nn=np.linalg.norm(n)
            if nn<1e-8:raise ValueError('Degenerate authored surface: '+piece)
            for i in tri:normals[i]+=n/nn
            for a,b in zip(tri,tri[1:]+tri[:1]):edges[tuple(sorted([a,b]))].append((a,b))
        nlen=np.linalg.norm(normals,axis=1)
        if np.any(nlen<1e-8):raise ValueError('Invalid authored vertex normal: '+piece)
        normals/=nlen[:,None]
        inside=flat-normals*thickness
        for tri in tris:
            self.face(flat[tri],piece,mat)
            self.face(inside[tri[::-1]],piece,inside_mat or mat,'inside')
        for edge in edges.values():
            if len(edge)==1:
                a,b=edge[0]
                self.face([flat[b],flat[a],inside[a],inside[b]],piece,inside_mat or mat,'thin cut')
        self.parts[piece]={'kind':'curved thin shell','thickness':thickness,
                           'surface_vertices':len(flat),'surface_triangles':len(tris),
                           'front_back_pair_max_error':float(np.abs(np.linalg.norm(flat-inside,axis=1)-thickness).max()),
                           'bounds':[flat.min(0).tolist(),flat.max(0).tolist()]}
    def data(self):
        return {'name':self.name,'vertices':self.v,'faces':self.faces,'parts':self.parts,
                'stage':'shape blockout, flat material colors only','concept_unadopted':True,
                'game_renderer':False,'cloth_simulation':False,'rigged':False}

def palette(m,dest,clay=False):
    im=Image.new('RGBA',(32,32),(0,0,0,255));names=list(COLORS)
    for i,mat in enumerate(names):
        x=(i%4)*8;y=(i//4)*8
        color=COLORS['clay'] if clay and mat not in ['avatar','head','hand','boot'] else COLORS[mat]
        tile=Image.new('RGBA',(8,8),color);im.paste(tile,(x,y))
    for f in m['faces']:
        i=names.index(f['material']);x=(i%4)*8;y=(i//4)*8
        f['uv']=[[x+1,y+1],[x+6,y+1],[x+1,y+6]]
    if not clay:im.save(dest/'flat-palette.png')
    return np.array(im)

def export(m,dest):
    native='native.png' if (dest/'native.png').exists() else 'flat-palette.png'
    with Image.open(dest/native) as texture:width,height=texture.size
    write(dest/'model.json',m)
    obj=['mtllib material.mtl','usemtl shape_palette','s off']
    obj+=['v '+' '.join(f'{x:.8f}' for x in p) for p in m['vertices']]
    for i,f in enumerate(m['faces']):
        obj+=['vt '+f'{uv[0]/width:.8f} {1-uv[1]/height:.8f}' for uv in f['uv']]
        obj.append('f '+' '.join(f'{v+1}/{3*i+j+1}' for j,v in enumerate(f['vertices'])))
    (dest/'model.obj').write_text('\n'.join(obj)+'\n',encoding='utf-8')
    (dest/'material.mtl').write_text('newmtl shape_palette\nKd 1 1 1\nKa 0 0 0\nKs 0 0 0\nillum 1\nmap_Kd '+native+'\n',encoding='utf-8')
    grouped=defaultdict(list)
    for i,f in enumerate(m['faces']):grouped[f['piece']].append((i,f))
    elements=[];outliner=[]
    for piece,faces in grouped.items():
        uid=str(uuid.uuid5(uuid.NAMESPACE_URL,m['name']+'/'+piece))
        ids=sorted({v for _,f in faces for v in f['vertices']})
        ff={f'f{i}':{'vertices':[f'v{k}' for k in f['vertices']],
                      'uv':{f'v{k}':uv for k,uv in zip(f['vertices'],f['uv'])},'texture':0} for i,f in faces}
        elements.append({'name':piece,'type':'mesh','uuid':uid,'origin':[0,0,0],
                         'rotation':[0,0,0],'vertices':{f'v{k}':m['vertices'][k] for k in ids},
                         'faces':ff,'color':0,'visibility':True,'export':True,'autouv':0,'locked':False})
        outliner.append(uid)
    bb={'meta':{'format_version':'4.10','model_format':'free','box_uv':False},
        'name':m['name'],'model_identifier':dest.name,'visible_box':[4,3,0],
        'resolution':{'width':width,'height':height},'elements':elements,'outliner':outliner,
        'textures':[{'path':'','name':native,'folder':'','namespace':'','id':'0',
                     'uuid':str(uuid.uuid5(uuid.NAMESPACE_URL,m['name']+'/flat')),
                     'width':width,'height':height,'uv_width':width,'uv_height':height,'mode':'bitmap',
                     'source':'data:image/png;base64,'+base64.b64encode((dest/native).read_bytes()).decode(),
                     'render_mode':'default','render_sides':'double','visible':True}], 'animations':[]}
    write(dest/'model.bbmodel',bb)

def rotate(vs,origin,angle):
    a=np.radians(angle);c,s=np.cos(a),np.sin(a)
    matrix=np.array([[c,-s,0],[s,c,0],[0,0,1]])
    return (vs-np.array(origin))@matrix.T+np.array(origin)
