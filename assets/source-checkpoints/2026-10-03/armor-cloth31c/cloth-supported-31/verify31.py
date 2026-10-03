"""Bounded neutral real-mesh checks and protection; no artistic acceptance."""
from pathlib import Path
import sys,json,hashlib,base64,io
import numpy as np
from PIL import Image
sys.dont_write_bytecode=True
R=Path(__file__).resolve().parent;ROOT=R.parent
sys.path.insert(0,str(ROOT/'cloth-layered-23'))
from check23 import check
from mesh_tools import write
S=ROOT/'cloth-structure-30'
m=json.loads((R/'model/model.json').read_text());old=json.loads((S/'model/model.json').read_text())
geo=check(R/'model',m)
assert geo['true_body_pairs_above0_10']==0,geo['groups']
def signature(model,pieces):
 v=model['vertices']
 return sorted((f['piece'],f['material'],f['role'],tuple(tuple(v[k]) for k in f['vertices']),tuple(tuple(u) for u in f['uv'])) for f in model['faces'] if f['piece'] in pieces)
protected={f['piece'] for f in old['faces'] if f['role']=='avatar' or f['role']=='hardware' or f['piece']=='broad soft leather waist belt' or 'new shaped leather boot' in f['piece'] or 'soft folded boot top' in f['piece']}
assert signature(m,protected)==signature(old,protected)

for name in ['native.png','new-cloth-boots.png','structure-paint.png']:assert (R/'model'/name).read_bytes()==(S/'model'/name).read_bytes()

KEEP={f['piece'] for f in old['faces']} - set(json.loads((R/'authoring-provenance.json').read_text())['removed_parts'])
assert signature(m,KEEP)==signature(old,KEEP)
bb=json.loads((R/'model/model.bbmodel').read_text());images=[np.array(Image.open(R/'model'/t['name']).convert('RGBA')) for t in bb['textures']]
for t,a in zip(bb['textures'],images):assert np.array_equal(a,np.array(Image.open(io.BytesIO(base64.b64decode(t['source'].split(',')[1]))).convert('RGBA')))
# Raster UV occupancy: transparent samples would become real holes in native BB.
missing=[];samples=0
for idx,f in enumerate(m['faces']):
 uv=np.array(f['uv']);a1=uv[1]-uv[0];a2=uv[2]-uv[0];den=a1[0]*a2[1]-a1[1]*a2[0];assert abs(den)>1e-8
 assert np.all(uv>=0) and np.all(uv<=512)
 lo=np.maximum(0,np.floor(uv.min(0)).astype(int));hi=np.minimum(511,np.ceil(uv.max(0)).astype(int))
 xx,yy=np.meshgrid(np.arange(lo[0],hi[0]+1)+.5,np.arange(lo[1],hi[1]+1)+.5);q=np.stack([xx,yy],-1)-uv[0]
 b=(q[...,0]*(uv[2,1]-uv[0,1])-q[...,1]*(uv[2,0]-uv[0,0]))/den
 c=((uv[1,0]-uv[0,0])*q[...,1]-(uv[1,1]-uv[0,1])*q[...,0])/den;a=1-b-c;mask=(a>=-1e-8)&(b>=-1e-8)&(c>=-1e-8)
 alpha=images[f['texture_index']][lo[1]:hi[1]+1,lo[0]:hi[0]+1,3];samples+=int(mask.sum())
 if np.any(alpha[mask]!=255):missing.append({'face':idx,'piece':f['piece'],'transparent_texels':int((alpha[mask]!=255).sum())})
assert not missing,missing[:10]
if '--portable' in sys.argv:
 guard={str(ROOT/p):h for p,h in json.loads((R/'replay-inputs.json').read_text()).items()};context='packaged authoring inputs'
else:
 guard=json.loads((R/'protected-inputs.json').read_text());context='all original protected inputs'
changed=[]
for p,h in guard.items():
 path=Path(p)
 if not path.is_file() or hashlib.sha256(path.read_bytes()).hexdigest()!=h:changed.append(p)
assert not changed,changed
out={'status':'NEUTRAL_TECHNICAL_CHECK_ONLY','geometry':{k:v for k,v in geo.items() if k not in ['all_hits','closed_shell_bad_edges']},'source_protection_context':context,'protected_source_files':len(guard),'protected_source_files_changed':changed,'body_hardware_belt_boot_geometry_UV_exact30':True,'BB_embedded_RGBA_exact_export':True,'raster_UV_samples':samples,'transparent_used_texels':0,'textures':len(images),'native_inspection_complete':False,'artistic_pass_claimed':False,'movement_or_game_wear_certified':False}
write(R/'verification.json',out)
print(json.dumps(out))
