"""Structural checks, not a claim of visual/reference-quality acceptance."""
import json
import math
import unittest
from build_mage_meteor import METEOR_CLIPS, PALETTE, SOURCE, EMBER_SOURCE, IMPACT_SOURCE, BRIDGE_SOURCE, WAKE_SOURCE, PACK, mesh, ink_uvs, rock, burning_wake, impact_atlas
from build_mage_meteor import METEOR_FLOW_CLIPS, flow_mesh, flow_grid, lobe_views, lobe_volume, lobe_partitions, lobe_centers, blast_volume, BLAST_STEP, BLAST_ORIGIN, LOBE_SOURCE, PRESSURE_SOURCE


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

    def test_active_flame_geometry_never_uses_checkerboard_as_smoke(self):
        import numpy as np
        atlas=impact_atlas();height,width=atlas.shape[:2]
        for clip in METEOR_FLOW_CLIPS:
            if clip=='meteor_front':
                grid,_=flow_grid(clip)
                for x,y in zip(*np.nonzero(grid)):
                    rgb=atlas[int((y+.5)/48*height/2),int((x+.5)/48*width/4)].astype(int)
                    self.assertGreaterEqual(rgb[0]-rgb[2],32)
            for state in range(3):
                faces=[f for e in flow_mesh(clip,state,self.uv) for f in e['faces'].values()]
                self.assertTrue(faces)
                self.assertTrue(all(f['texture']=='#0' for f in faces))
                if state==2:self.assertTrue(all(f['tintindex'] in (1,2,7) for f in faces))

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

    def test_hot_contours_keep_geometry_then_lose_filling_as_ash(self):
        self.assertEqual(7,len(METEOR_FLOW_CLIPS))
        for clip in METEOR_FLOW_CLIPS:
            def geometry_only(elements):
                return [(e['from'],e['to'],tuple(e['faces'])) for e in elements]
            baseline=flow_mesh(clip,0,self.uv)
            self.assertTrue(baseline,clip)
            self.assertLess(len(baseline),500,clip)
            self.assertEqual({'north','south','east','west','up','down'},{n for e in baseline for n in e['faces']})
            for state in range(3):
                elements=flow_mesh(clip,state,self.uv)
                if state<2 or clip in ('meteor_front','meteor_flow_0','meteor_flow_2'):
                    self.assertEqual(geometry_only(baseline),geometry_only(elements))
                else:
                    self.assertNotEqual(geometry_only(baseline),geometry_only(elements))
                    self.assertLess(len(elements),len(baseline))
                    # Loss of filling, not a relocated ash picture: the remaining
                    # surface stays within the original body's local bounds.
                    for axis in range(3):
                        self.assertGreaterEqual(min(e['from'][axis] for e in elements),min(e['from'][axis] for e in baseline)-.0001)
                        self.assertLessEqual(max(e['to'][axis] for e in elements),max(e['to'][axis] for e in baseline)+.0001)
                model=json.loads((self.assets/f'models/combat_vfx/mage_material/{clip}_{state}.json').read_text())
                self.assertEqual(elements,model['elements'])
                for e in elements:
                    self.assertTrue(all(math.isfinite(v) and -16<=v<=32 for v in e['from']+e['to']))
                    self.assertTrue(all(a<b for a,b in zip(e['from'],e['to'])))
                    for f in e['faces'].values():
                        self.assertEqual('#0',f['texture'])
                        self.assertIn(f['tintindex'],range(len(PALETTE)))
            inks={f['tintindex'] for e in baseline for f in e['faces'].values()}
            self.assertLessEqual(len(inks),4)
        # No old complete explosion continues underneath the moving masses.
        self.assertFalse(any(f['texture'] in ('#2','#4') for frame in range(1,24)
                             for e in mesh('eruption',frame,self.uv) for f in e['faces'].values()))

    def test_full_flame_bodies_have_front_side_and_connected_rounded_volume(self):
        import numpy as np
        for source in (LOBE_SOURCE,PRESSURE_SOURCE):
            front,side=lobe_views(source);volume=lobe_volume(source)
            self.assertGreater(volume.sum(),300)
            self.assertTrue(np.all(~volume.any(axis=2)|(front>0)))
            self.assertTrue(np.all(~volume.any(axis=0).T|(side>0)))
            self.assertGreater(volume.any(axis=0).sum(),front.astype(bool).sum()*.5)
            # The shape isn't just the two unrounded perpendicular extrusions.
            self.assertLess(volume.sum(),((front>0)[:,:,None]&(side>0).T[None,:,:]).sum())
            pending=set(zip(*np.nonzero(volume)));stack=[pending.pop()]
            while stack:
                x,y,z=stack.pop()
                for p in ((x-1,y,z),(x+1,y,z),(x,y-1,z),(x,y+1,z),(x,y,z-1),(x,y,z+1)):
                    if p in pending:pending.remove(p);stack.append(p)
            self.assertFalse(pending,source.name)
        for i in (0,2,3):
            body=flow_mesh(f'meteor_flow_{i}',0,self.uv)
            spans=[max(e['to'][a] for e in body)-min(e['from'][a] for e in body) for a in range(3)]
            self.assertGreater(spans[2],3.5)
            self.assertTrue(all(len(e['faces'])==1 for e in body))
            # Side/back retain actual light and midtone regions, not a uniform
            # dark edge hiding a shallow extrusion.
            for side in ('north','south','east','west'):
                inks={e['faces'][side]['tintindex'] for e in body if side in e['faces']}
                self.assertIn(3,inks)
                self.assertIn(4,inks)
        self.assertFalse(np.array_equal(lobe_views(LOBE_SOURCE)[0],lobe_views(PRESSURE_SOURCE)[0]))
        with self.assertRaises(ValueError):flow_grid('meteor_flow_0')

    def test_peeling_regions_reconstruct_the_same_volume_and_own_real_interior_faces(self):
        import numpy as np
        parts=lobe_partitions();solid,front=blast_volume()
        self.assertTrue(np.array_equal(np.stack(parts).sum(axis=0),solid.astype(int)))
        self.assertTrue(all(p.sum()>150 for p in parts))
        np.testing.assert_allclose(lobe_centers(),[[-6.75,4,-.75],[1.875,10,.75],[7.5,4,-2.25]])
        # Authored arch leaves the central sight corridor empty, while its
        # sides reach the ground. It cannot regress to a hovering central ball.
        self.assertFalse(solid[13:18,:8,:].any())
        self.assertTrue(solid[:11,:2,:].any() and solid[23:,:2,:].any())
        self.assertGreater(np.argwhere(solid)[:,2].max()-np.argwhere(solid)[:,2].min(),7)
        self.assertGreater(np.ptp(np.argwhere(solid)[:,2])*BLAST_STEP[2],12)
        for member,part in enumerate(parts):
            pending=set(zip(*np.nonzero(part)));stack=[pending.pop()]
            while stack:
                x,y,z=stack.pop()
                for p in ((x-1,y,z),(x+1,y,z),(x,y-1,z),(x,y+1,z),(x,y,z-1),(x,y,z+1)):
                    if p in pending:pending.remove(p);stack.append(p)
            self.assertFalse(pending,member)
            clip='meteor_flow_1' if member==0 else f'meteor_break_{member}'
            body=flow_mesh(clip,0,self.uv)
            center=lobe_centers()[member]
            # Adding the runtime pivot restores the member's original position.
            for axis,step in enumerate(BLAST_STEP):
                native_min=min(e['from'][axis] for e in body)+center[axis]
                expected=BLAST_ORIGIN[axis]+np.argwhere(part)[:,axis].min()*step
                self.assertAlmostEqual(expected,native_min,places=4)
            self.assertTrue(all(len(e['faces'])==1 for e in body))

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
