"""Structural checks, not a claim of visual/reference-quality acceptance."""
import json
import math
import unittest
from build_mage_meteor import METEOR_CLIPS, PALETTE, SOURCE, EMBER_SOURCE, IMPACT_SOURCE, BRIDGE_SOURCE, WAKE_SOURCE, PACK, mesh, ink_uvs, rock, burning_wake, impact_atlas
from build_mage_meteor import METEOR_FLOW_CLIPS, METEOR_FLOW_STATES, COOLING_SOURCE, FLOW_PALETTE, flow_mesh
from build_mage_meteor_surface import centers, drawing, region_labels, KNOT_X, KNOT_Z, thermal_ink, VAPOR_UV


class MeteorTests(unittest.TestCase):
    def setUp(self):
        self.assets=PACK/'assets/projects'
        self.uv=ink_uvs(self.assets)[3]

    def test_shipped_three_phrases_reproduce_within_existing_budget(self):
        for clip in METEOR_CLIPS:
            for frame in range(24):
                elements=mesh(clip,frame,self.uv)
                self.assertLessEqual(len(elements),1000,(clip,frame))
                model=json.loads((self.assets/f'models/combat_vfx/mage_material/{clip}_{frame}.json').read_text())
                self.assertEqual(elements,model['elements'])
                for e in elements:
                    self.assertTrue(all(math.isfinite(v) and -16<=v<=32 for v in e['from']+e['to']))
                    self.assertTrue(all(a<b for a,b in zip(e['from'],e['to'])))
                    for f in e['faces'].values():
                        self.assertIn(f['texture'][1:],model['textures'])
                        self.assertIn(f['tintindex'],range(len(PALETTE)))
                        self.assertTrue(all(0<=v<=16 for v in f['uv']))
                        self.assertNotEqual(f['uv'][0],f['uv'][2]);self.assertNotEqual(f['uv'][1],f['uv'][3])
                item=json.loads((self.assets/f'items/combat_vfx/mage_material/{clip}_{frame}.json').read_text())
                self.assertEqual(PALETTE,[t['value'] for t in item['model']['tints']])
            self.assertEqual([],mesh(clip,23,self.uv))
        self.assertEqual(SOURCE.read_bytes(),(self.assets/'textures/combat_vfx/mage_material/meteor_basalt_v01.png').read_bytes())
        self.assertEqual(EMBER_SOURCE.read_bytes(),(self.assets/'textures/combat_vfx/mage_material/meteor_ember_v01.png').read_bytes())
        self.assertEqual(IMPACT_SOURCE.read_bytes(),(self.assets/'textures/combat_vfx/mage_material/meteor_impact_atlas_v01.png').read_bytes())
        self.assertEqual(BRIDGE_SOURCE.read_bytes(),(self.assets/'textures/combat_vfx/mage_material/meteor_impact_bridge_v01.png').read_bytes())
        self.assertEqual(WAKE_SOURCE.read_bytes(),(self.assets/'textures/combat_vfx/mage_material/meteor_wake_v01.png').read_bytes())
        self.assertEqual(COOLING_SOURCE.read_bytes(),(self.assets/'textures/combat_vfx/mage_material/meteor_cooling_v01.png').read_bytes())

    def test_painted_faces_preserve_original_rgb_and_exclude_every_background_texel(self):
        import numpy as np
        for clip in METEOR_FLOW_CLIPS:
            for state in range(METEOR_FLOW_STATES):
                atlas=impact_atlas()
                h,w=atlas.shape[:2]
                colours=set()
                for e in flow_mesh(clip,state,self.uv):
                    for f in e['faces'].values():
                        self.assertNotEqual('#5',f['texture'])  # no unrelated cold-atlas silhouette
                        if f['texture']!='#2':continue
                        self.assertIn(f['tintindex'],(0,8))  # authored paint, or cooling ember tint
                        u0,v0,u1,v1=f['uv']
                        x0,x1=round(min(u0,u1)/16*w),round(max(u0,u1)/16*w)
                        y0,y1=round(min(v0,v1)/16*h),round(max(v0,v1)/16*h)
                        rgb=atlas[y0:y1,x0:x1].astype(int)
                        self.assertTrue(rgb.size)
                        allowed=(rgb[:,:,0]-rgb[:,:,2]>12)&(rgb[:,:,0]-rgb[:,:,1]>6)&(rgb[:,:,0]>20)
                        self.assertTrue(np.all(allowed),(clip,state))
                        colours.update(map(tuple,rgb.reshape(-1,3)))
                if state==0:
                    self.assertGreater(len(colours),20,clip)  # original paint, not palette cells

    def test_rock_is_one_volume_with_continuous_world_uvs(self):
        body=rock(tuple(self.uv))
        self.assertGreater(len(body),6)
        for axis in range(3):
            span=max(e['to'][axis] for e in body)-min(e['from'][axis] for e in body)
            self.assertGreater(span,7.)
        self.assertEqual({'north','south','east','west','up','down'},
                         {n for e in body for n in e['faces']})
        self.assertTrue(all(len(e['faces'])==1 for e in body))
        self.assertEqual({'#0','#1'},{f['texture'] for e in body for f in e['faces'].values()})
        self.assertTrue(all(e['to'][1]<9.0 for e in body if next(iter(e['faces'].values()))['texture']=='#0'))
        # Falling motion belongs to the authoritative display, not a wobbling
        # rock-texture animation that restarts its cracks every frame.
        self.assertEqual(body,mesh('meteor',20,self.uv)[:len(body)])

    def test_thin_bent_surfaces_ship_exactly_within_existing_budget(self):
        self.assertEqual(7,len(METEOR_FLOW_CLIPS))
        for clip in METEOR_FLOW_CLIPS:
            for state in range(METEOR_FLOW_STATES):
                elements=flow_mesh(clip,state,self.uv)
                self.assertTrue(elements,clip)
                # Multi-material rupture uses more boundaries than one opaque
                # picture. Same 1000-element ceiling as the fixed meteor art;
                # Display/observer budgets are unchanged.
                self.assertLessEqual(len(elements),1000,(clip,state))
                model=json.loads((self.assets/f'models/combat_vfx/mage_material/{clip}_{state}.json').read_text())
                self.assertEqual(elements,model['elements'])
                item=json.loads((self.assets/f'items/combat_vfx/mage_material/{clip}_{state}.json').read_text())
                self.assertEqual(FLOW_PALETTE,[t['value'] for t in item['model']['tints']])
                for e in elements:
                    self.assertEqual({'east','west'} if clip=='meteor_flow_2' else {'north','south'},set(e['faces']))
                    self.assertFalse(e['shade'])
                    thin_axis=0 if clip=='meteor_flow_2' else 2
                    self.assertLess(e['to'][thin_axis]-e['from'][thin_axis],.003)
                    self.assertTrue(all(math.isfinite(v) and -16<=v<=32 for v in e['from']+e['to']))
                    self.assertTrue(all(a<b for a,b in zip(e['from'],e['to'])))
                    rotation=e.get('rotation')
                    if rotation:
                        self.assertEqual('y',rotation['axis'])
                        self.assertIn(rotation['angle'],(-45.,-22.5,22.5,45.))
                        self.assertFalse(rotation['rescale'])
                    for f in e['faces'].values():
                        self.assertTrue(all(0<=v<=16 for v in f['uv']))
            if clip in ('meteor_flow_1','meteor_break_1','meteor_break_2'):
                self.assertLess(len(flow_mesh(clip,5,self.uv)),len(flow_mesh(clip,0,self.uv)))
                self.assertNotEqual(flow_mesh(clip,0,self.uv),flow_mesh(clip,1,self.uv))
        # No old complete explosion remains beneath the moving art.
        self.assertFalse(any(f['texture'] in ('#2','#4') for frame in range(1,24)
                             for e in mesh('eruption',frame,self.uv) for f in e['faces'].values()))

    def test_regions_cover_actual_paint_once_and_keep_runtime_pivots_in_sync(self):
        import numpy as np
        for state in range(6):
            mask=thermal_ink(state)>0;labels=region_labels(mask.shape)
            parts=[mask&(labels==i) for i in range(3)]
            np.testing.assert_array_equal(np.stack(parts).sum(axis=0),mask.astype(int))
        kotlin=(PACK.parents[1]/'kotlin/dev/projects/server/coreloop/CoreMageChoreography.kt').read_text(encoding='utf-8')
        # Read the authoritative numeric centre definition, not an independent
        # hard-coded table that could diverge from runtime silently.
        import re
        block=kotlin.split('internal val meteorFragmentCenters=listOf(',1)[1].split('fun interpolated',1)[0]
        triples=re.findall(r'Vec\(([^)]+)\)',block)
        runtime=[[float(v.strip()) for v in triple.split(',')] for triple in triples]
        np.testing.assert_allclose(runtime,centers(),rtol=0,atol=1e-10)
        # The surface really bends through depth. Unlike the rejected volume,
        # the depth is curvature, not thickness or opaque brown end caps.
        self.assertGreater(np.ptp(KNOT_Z),5.)
        self.assertGreater(np.ptp(KNOT_X),20.)
        mask,_,_=drawing(2);h,w=mask.shape
        self.assertFalse(mask[int(h*.72):int(h*.84),int(w*.45):int(w*.55)].any())

    def test_rear_is_a_complementary_depth_surface(self):
        import numpy as np
        for front,rear in (('meteor_flow_1','meteor_flow_0'),('meteor_break_2','meteor_flow_3')):
            for state in range(METEOR_FLOW_STATES):
                a,b=flow_mesh(front,state,self.uv),flow_mesh(rear,state,self.uv)
                self.assertEqual(len(a),len(b))
                for f,r in zip(a,b):
                    np.testing.assert_allclose(f['from'][:2],r['from'][:2])
                    np.testing.assert_allclose(f['to'][:2],r['to'][:2])
                    self.assertAlmostEqual(f['from'][2],16-r['to'][2])
                    self.assertAlmostEqual(f['to'][2],16-r['from'][2])
                    self.assertEqual(f['rotation']['angle'] if 'rotation' in f else 0,
                                     -(r['rotation']['angle'] if 'rotation' in r else 0))
        # The transverse crest supplies a side-facing painted subject across
        # the impact, not two disconnected vertical edges. It replaces one
        # rear region; it isn't an extra whole explosion or an extra Display.
        crest=flow_mesh('meteor_flow_2',0,self.uv)
        self.assertGreater(max(e['to'][2] for e in crest)-min(e['from'][2] for e in crest),10)
        self.assertEqual({'east','west'},{f for e in crest for f in e['faces']})

    def test_cooling_retains_registered_curls_and_separates_real_vapor_from_solid_rims(self):
        import numpy as np
        from PIL import Image
        mask,_,_=drawing(2)
        phases=[thermal_ink(s) for s in range(6)]
        for phase in phases:self.assertFalse(phase[~mask].any())
        for phase in phases[:2]:np.testing.assert_array_equal(phase>0,mask)
        np.testing.assert_array_equal(phases[2]>0,phases[3]>0)
        self.assertLess(np.count_nonzero(phases[2]),mask.sum()*.8)
        self.assertTrue(np.all((phases[5]>0)<=(phases[4]>0)))
        self.assertLess(np.count_nonzero(phases[5]),mask.sum()*.25)
        self.assertEqual({0,2,3,4},set(np.unique(phases[2])))
        self.assertGreater(np.count_nonzero(phases[2]==3),np.count_nonzero(phases[2]==4))
        rgba=np.asarray(Image.open(self.assets/'textures/combat_vfx/warrior_support/cloth.png'))
        np.testing.assert_array_equal(rgba[61,24],[37,37,41,68])
        for clip in METEOR_FLOW_CLIPS-{'meteor_front'}:
            model=json.loads((self.assets/f'models/combat_vfx/mage_material/{clip}_2.json').read_text())
            self.assertEqual('projects:combat_vfx/warrior_support/cloth',model['textures']['6'])
            vapor=[f for e in model['elements'] for f in e['faces'].values() if f['texture']=='#6']
            self.assertTrue(vapor)
            for face in vapor:
                u0,v0,u1,v1=face['uv']
                self.assertEqual(sorted(VAPOR_UV[::2]),sorted((u0,u1)))
                self.assertEqual(VAPOR_UV[1::2],[v0,v1])
                self.assertEqual(0,face['tintindex'])

    def test_contact_keeps_only_short_heat_residue(self):
        for frame in (0,1):
            self.assertTrue(any(f['texture']=='#3' for e in mesh('eruption',frame,self.uv)
                                for f in e['faces'].values()))
        for frame in range(2,24):
            self.assertFalse(any(f['texture']=='#3' for e in mesh('eruption',frame,self.uv)
                                 for f in e['faces'].values()))

    def test_dense_painted_wake_extends_behind_intact_body_and_contact_has_height(self):
        early=burning_wake(0,self.uv);late=burning_wake(15,self.uv)
        # The long burning column extends behind the same rock; it cannot
        # regress to sparse narrow strips while satisfying only a height check.
        self.assertGreater(max(e['to'][1] for e in late),max(e['to'][1] for e in early)+10)
        self.assertGreater(max(e['to'][0] for e in late)-min(e['from'][0] for e in late),5.)
        self.assertEqual({'#3'},{f['texture'] for e in late for f in e['faces'].values()})
        self.assertNotEqual(early,late)
        self.assertGreater(max(e['to'][1] for e in mesh('eruption',0,self.uv)),11.5)
        self.assertTrue(any('north' in e['faces'] for e in mesh('eruption',0,self.uv)))

    def test_contact_disappears_before_wake_and_debris_are_finished(self):
        def plane_area(clip,frame):
            return sum((e['to'][0]-e['from'][0])*(e['to'][2]-e['from'][2])
                       for e in mesh(clip,frame,self.uv) if set(e['faces'])=={'up','down'})
        self.assertGreater(plane_area('eruption',2),20.)
        self.assertEqual(0,plane_area('eruption',9))
        self.assertGreater(plane_area('meteor_ring',9),1.)
        self.assertLess(plane_area('meteor_ring',21),plane_area('meteor_ring',9))
        self.assertTrue(mesh('eruption',9,self.uv))  # detached ballistic debris
        self.assertFalse(mesh('eruption',20,self.uv))


if __name__=='__main__':unittest.main()
