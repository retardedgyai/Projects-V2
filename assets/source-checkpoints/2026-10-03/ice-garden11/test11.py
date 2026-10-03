"""Visual/native invariants only; these are not game or aesthetic acceptance tests."""
from pathlib import Path
import collections,hashlib,json,unittest
import numpy as np
from author11 import euler
from scene11 import assets,MESHES,PLACEMENTS,field_transform,contact_transform,render,CONTACT_LENGTH
from field_math11 import textures
from field_math09 import valid_mask,CELLS,contains,X,Z,edges
ROOT=Path(__file__).resolve().parent
class Study11(unittest.TestCase):
 def test_protected10_source_and_archive(self):
  guard=json.loads((ROOT/'protected10.json').read_text());old=ROOT.parent/'ice-garden-living-10'
  for rel,h in guard['10_files'].items():self.assertEqual(hashlib.sha256((old/rel).read_bytes()).hexdigest(),h,rel)
  self.assertEqual(hashlib.sha256((ROOT.parent.parent/'deliverables/mage-ice-garden-living-10-review.zip').read_bytes()).hexdigest(),guard['10_zip_sha256'])
 def test_closed_solids_and_root_overlap(self):
  for name,parts in MESHES.items():
   root=next(p for p in parts if p['kind']=='root-bridge');rv=np.array(root['vertices']);rlo,rhi=rv.min(axis=0),rv.max(axis=0)
   for p in parts:
    v=np.array(p['vertices']);self.assertTrue(p['closed']);edges=collections.Counter((a,b) for t in p['triangles'] for a,b in zip(t,t[1:]+t[:1]))
    self.assertTrue(all(n==edges[(b,a)]==1 for (a,b),n in edges.items()),p['name'])
    self.assertGreater(sum(np.dot(v[t[0]],np.cross(v[t[1]],v[t[2]])) for t in p['triangles'])/6,0)
    self.assertTrue(np.all(np.maximum(v.min(axis=0),rlo)<np.minimum(v.max(axis=0),rhi)),p['name'])
 def test_native_actual_corner_rotation_and_uv(self):
  errors=[]
  for name in MESHES:
   m,tx,n=assets()[name];self.assertTrue(n['actual_parser']);self.assertEqual(len(m['elements']),n['native_faces'])
   for f in n['faces']:
    el=m['elements'][f['element']];lo=np.array(el['from']);hi=np.array(el['to']);r=el['rotation'];pivot=np.array(r['origin'])
    corners={k:np.where(np.array([k&1,k&2,k&4])>0,hi,lo) for k in f['cube_corner_ids']}
    expected=[((np.array(corners[k])-pivot)@euler(r).T+pivot-8)/16*6.25 for k in f['cube_corner_ids']]
    errors.append(float(np.max(np.linalg.norm(np.array(f['points_m'])-expected,axis=-1))))
    np.testing.assert_allclose(f['points_m'],expected,atol=2e-6)
    u0,v0,u1,v1=np.array(el['faces']['south']['uv'])/16;uvs={4:[u0,v1],5:[u1,v1],6:[u0,v0],7:[u1,v0]}
    np.testing.assert_allclose(f['uv'],[uvs[k] for k in f['cube_corner_ids']],atol=1e-7)
  (ROOT/'asset-validation.json').write_text(json.dumps({'actual_parser':'Minecraft26.2','faces':654,'closed_parts':16,'max_corner_error_m':max(errors),'max_heap_mb':256,'gpu_verified':False,'game_started':False},indent=2)+'\n')
 def test_new_hero_replaces_old_geometry(self):
  self.assertEqual(len(MESHES['hero-11']),7);self.assertGreater(max(v[1] for v in MESHES['hero-11'][0]['vertices']),2.3)
  self.assertNotEqual((ROOT/'native-study/hero-11.mesh.json').read_bytes(),(ROOT/'native-study/primary-remake.mesh.json').read_bytes())
 def test_quiet_hold_is_static(self):
  for a,b in zip(textures(1.55),textures(4.65)):np.testing.assert_array_equal(a,b)
  for side,p in PLACEMENTS.items():
   for f in assets()[p['asset']][2]['faces'][::15]:
    pts=np.array(f['points_m']);np.testing.assert_allclose(field_transform(side,1.55)(pts,f),field_transform(side,4.65)(pts,f),atol=1e-10)
 def test_floor_thin_dark_and_exact_supported_boundary(self):
  floor,fringe=textures(1.55);valid=valid_mask(CELLS);self.assertLess((floor[...,3]>0).sum()/valid.sum(),.20)
  self.assertLess(np.median(floor[floor[...,3]>0,:3]),110)
  for e in edges():
   a,b=np.array(e['a']),np.array(e['b']);normal=np.array(e['normal'])
   for q in [.15,.35,.55,.75,.85]:
    point=a*(1-q)+b*q-normal*.03125;pixel=np.argmin((X-point[0])**2+(Z-point[1])**2);self.assertEqual(fringe.reshape(-1,4)[pixel,3],255)
 def test_unsupported_holes_and_outside_contact_not_painted(self):
  cells=tuple(c for c in CELLS if c not in [(0,0),(2,0)]);valid=valid_mask(cells)
  for t in [.79,1.55,2.26,6.9]:
   for layer in textures(t,[(2.2,np.array([0,0,0]))],cells):self.assertFalse(layer[~valid,3].any())
  p=np.array([2.1,0,2.65]);self.assertFalse(contains(p[0],p[2]));f=assets()['hero-11'][2]['faces'][0]
  self.assertIsNone(contact_transform(0,2.2,2.26,p)(np.array(f['points_m']),f))
 def test_formation_and_expiry_are_rigid(self):
  for side,p in PLACEMENTS.items():
   for f in assets()[p['asset']][2]['faces'][::12]:
    pts=np.array(f['points_m']);d=np.linalg.norm(pts[:,None]-pts,axis=-1)
    for t in [.92,1.08,1.55,6.95,7.20]:
     q=field_transform(side,t)(pts,f)
     if q is not None:np.testing.assert_allclose(np.linalg.norm(q[:,None]-q,axis=-1),d,atol=1e-8)
    self.assertIsNone(field_transform(side,7.56)(pts,f))
 def test_contact_is_local_rigid_bounded_and_expires(self):
  for index,pi in enumerate([1,2]):
   p=MESHES['hero-11'][pi];v=np.array(p['vertices']);scale=.65 if index==0 else .70
   for age in [.02,.06,.17,.30]:
    q=contact_transform(index,2.2,2.2+age)(v,{})
    np.testing.assert_allclose(np.linalg.norm(q[:,None]-q,axis=-1),np.linalg.norm(v[:,None]-v,axis=-1)*scale,atol=1e-8)
    self.assertLess(abs(q[:,0]-.48).max(),1.2)
   self.assertIsNone(contact_transform(index,2.2,2.2+CONTACT_LENGTH)(v,{}))
  quiet,_=textures(2.26);hit,_=textures(2.26,[(2.2,np.array([.48,0,3.11]))]);changed=np.any(quiet!=hit,axis=-1)
  self.assertLess(changed.sum(),500);self.assertGreater(changed.sum(),20)
 def test_expiry_erodes_without_recolor_and_clears(self):
  a=textures(6.799)
  for t in [6.8,6.9,7.05,7.28]:
   b=textures(t)
   for x,y in zip(a,b):
    remains=(x[...,3]>0)&(y[...,3]>0);np.testing.assert_array_equal(x[remains,:3],y[remains,:3]);self.assertFalse(((x[...,3]==0)&(y[...,3]>0)).any())
   a=b
  self.assertFalse(any(i[...,3].any() for i in a));_,mask=render(7.56,w=320,h=180);self.assertFalse(np.isin(mask,[2,3,6,7]).any())
 def test_newest_contact_only_under_overlap(self):
  point=[.48,0,2.65];a,ma=render(2.26,w=320,h=180,contact_events=[(2.20,[-.8,0,2.3]),(2.21,[1.3,0,1.8]),(2.22,point)])
  b,mb=render(2.26,w=320,h=180,contact_events=[(2.22,point)])
  np.testing.assert_array_equal(ma,mb);np.testing.assert_array_equal(np.array(a),np.array(b))
if __name__=='__main__':unittest.main(verbosity=2)
