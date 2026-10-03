"""Technical contact invariants. No aesthetic/game-feel acceptance claim."""
from pathlib import Path
import collections,hashlib,json,unittest
import numpy as np
from author11 import euler
from scene12 import render as old
from scene13 import assets,MESH,pose,live_contact,render,LIFE,contact_material
from field_math11 import textures
from field_math09 import valid_mask,CELLS,contains
ROOT=Path(__file__).resolve().parent
class Contact13(unittest.TestCase):
 def test_11_protected_and_foundation_frozen(self):
  guard=json.loads((ROOT/'protected12.json').read_text());src=ROOT.parent/'ice-garden-contact-12'
  for rel,h in guard['12_files'].items():self.assertEqual(hashlib.sha256((src/rel).read_bytes()).hexdigest(),h,rel)
  for rel,h in guard['foundation_copies'].items():self.assertEqual(hashlib.sha256((ROOT/rel).read_bytes()).hexdigest(),h,rel)
  self.assertEqual(hashlib.sha256((ROOT.parent.parent/'deliverables/mage-ice-garden-12-contact-review.zip').read_bytes()).hexdigest(),guard['12_zip_sha256'])
  for p in (src/'native-study').glob('*'):
   if p.is_file() and '-basis.' not in p.name:self.assertEqual(p.read_bytes(),(ROOT/'native-study'/p.name).read_bytes(),p.name)
 def test_three_closed_positive_volume_pieces(self):
  self.assertEqual(len(MESH),3)
  for p in MESH:
   v=np.array(p['vertices']);edges=collections.Counter((a,b) for t in p['triangles'] for a,b in zip(t,t[1:]+t[:1]))
   self.assertTrue(p['closed']);self.assertTrue(all(n==edges[(b,a)]==1 for (a,b),n in edges.items()))
   self.assertGreater(sum(np.dot(v[t[0]],np.cross(v[t[1]],v[t[2]])) for t in p['triangles'])/6,0)
 def test_actual_native_corners_and_uv(self):
  m,tx,n=assets()['contact-break-13'];self.assertTrue(n['actual_parser']);self.assertEqual(n['native_faces'],140);errors=[]
  for f in n['faces']:
   el=m['elements'][f['element']];lo=np.array(el['from']);hi=np.array(el['to']);r=el['rotation'];pivot=np.array(r['origin'])
   corners={k:np.where(np.array([k&1,k&2,k&4])>0,hi,lo) for k in f['cube_corner_ids']}
   expected=[((corners[k]-pivot)@euler(r).T+pivot-8)/16*6.25 for k in f['cube_corner_ids']]
   errors.append(float(np.max(np.linalg.norm(np.array(f['points_m'])-expected,axis=-1))))
   np.testing.assert_allclose(f['points_m'],expected,atol=2e-6)
   u0,v0,u1,v1=np.array(el['faces']['south']['uv'])/16;uvs={4:[u0,v1],5:[u1,v1],6:[u0,v0],7:[u1,v0]}
   np.testing.assert_allclose(f['uv'],[uvs[k] for k in f['cube_corner_ids']],atol=1e-7)
  (ROOT/'asset-validation.json').write_text(json.dumps({'actual_parser':'Minecraft26.2','contact_faces':140,'closed_parts':3,'max_corner_error_m':max(errors),'game_started':False,'gpu_verified':False},indent=2)+'\n')
 def test_quiet_before_and_after_match_11_pixels(self):
  for t in [1.55,2.16,2.70,7.57]:
   a,ma=old(t,w=320,h=180,contacts=True);b,mb=render(t,w=320,h=180,contacts=True)
   np.testing.assert_array_equal(np.array(a),np.array(b));np.testing.assert_array_equal(ma,mb)
 def test_rigid_and_fixed_contact_anchor(self):
  a=np.array([.48,0,2.4]);shift=np.array([.15,.75,-.20])
  for pi,p in enumerate(MESH):
   v=np.array(p['vertices']);distance=np.linalg.norm(v[:,None]-v,axis=-1)
   for age in [.05,.08,.15,.23,.34,.45]:
    q=pose(pi,2.2,2.2+age,a)(v,{});r=pose(pi,2.2,2.2+age,a+shift)(v,{})
    np.testing.assert_allclose(np.linalg.norm(q[:,None]-q,axis=-1),distance,atol=1e-8)
    np.testing.assert_allclose(r-q,np.broadcast_to(shift,q.shape),atol=1e-8)
   self.assertIsNone(pose(pi,2.2,2.19,a)(v,{}));self.assertIsNone(pose(pi,2.2,2.2+LIFE,a)(v,{}))
   self.assertIsNone(pose(pi,2.2,2.26,[2.1,0,2.65])(v,{}))
 def test_low_split_pause_then_heavy_fall(self):
  point=[.48,0,2.4]
  for pi,p in enumerate(MESH[:2]):
   v=np.array(p['vertices']);peak=pose(pi,2.2,2.30,point)(v,{})
   paused=pose(pi,2.2,2.34,point)(v,{})
   np.testing.assert_allclose(peak,paused,atol=1e-8)
   start=pose(pi,2.2,2.24,point)(v,{});end=pose(pi,2.2,2.64,point)(v,{})
   self.assertLess(start[:,1].max(),peak[:,1].max());self.assertLess(end[:,1].mean(),peak[:,1].mean()-.06)
   self.assertGreaterEqual(end[:,1].min(),.018-1e-9)
  self.assertGreater(np.linalg.norm(np.array(MESH[0]['axis'])),np.linalg.norm(np.array(MESH[1]['axis']))*1.6)
 def test_contact_separates_from_one_low_origin(self):
  point=np.array([.48,0,2.4]);root=np.array(MESH[2]['vertices']);q=pose(2,2.2,2.30,point)(root,{})
  self.assertLess(q[:,1].max(),.20)
  for pi in [0,1]:
   pivot=np.array(MESH[pi]['origin']);early=pose(pi,2.2,2.24,point)(pivot[None],{})[0];peak=pose(pi,2.2,2.30,point)(pivot[None],{})[0]
   self.assertGreater(abs(peak[0]-point[0]),abs(early[0]-point[0]))
  self.assertGreater(len(MESH[0]['profiles']),len(MESH[1]['profiles']))
 def test_floor_collision_and_material_retirement(self):
  for pi,p in enumerate(MESH):
   v=np.array(p['vertices'])
   for age in [.10,.17,.23,.34,.44]:self.assertGreaterEqual(pose(pi,2.2,2.2+age,[.48,0,2.4])(v,{})[:,1].min(),.018-1e-9)
  original=assets()['contact-break-13'][1]['facets'];prior=original[...,3]>0
  for age in [.365,.39,.42,.45]:
   frame=contact_material(age)['facets'];now=frame[...,3]>0
   self.assertFalse((now&~prior).any());np.testing.assert_array_equal(frame[...,:3],original[...,:3]);prior=now
  self.assertLess(prior.sum(),(original[...,3]>0).sum()*.2)
  np.testing.assert_array_equal(assets()['contact-break-13'][1]['facets'],original)
 def test_latest_only_and_outside_ignored(self):
  point=[.48,0,2.4];events=[(2.20,[-.8,0,2.3]),(2.21,[1.3,0,1.8]),(2.22,point)]
  self.assertEqual(live_contact(2.26,events)[0],2.22)
  a,ma=render(2.26,w=320,h=180,contact_events=events);b,mb=render(2.26,w=320,h=180,contact_events=[events[-1]])
  np.testing.assert_array_equal(a,b);np.testing.assert_array_equal(ma,mb)
  self.assertIsNone(live_contact(2.26,[(2.2,[2.1,0,2.65])]))
 def test_holes_and_floor_cue_remain_local(self):
  cells=tuple(c for c in CELLS if c not in [(0,0),(2,0)]);valid=valid_mask(cells)
  for layer in textures(2.26,[(2.2,np.array([.48,0,2.4]))],cells):self.assertFalse(layer[~valid,3].any())
  quiet,_=textures(2.26);hit,_=textures(2.26,[(2.2,np.array([.48,0,2.4]))]);changed=np.any(quiet!=hit,axis=-1)
  self.assertGreater(changed.sum(),20);self.assertLess(changed.sum(),500)
if __name__=='__main__':unittest.main(verbosity=2)
