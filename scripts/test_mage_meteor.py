"""Structural checks, not a claim of visual/reference-quality acceptance."""
import json
import math
import unittest
from build_mage_meteor import METEOR_CLIPS, PALETTE, SOURCE, EMBER_SOURCE, IMPACT_SOURCE, BRIDGE_SOURCE, WAKE_SOURCE, PACK, mesh, ink_uvs, rock, burning_wake, impact_atlas
from build_mage_meteor import METEOR_FLOW_CLIPS, flow_mesh, flow_grid


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
            drawing=0 if clip=='meteor_front' else 1
            grid,_=flow_grid(clip)
            for x,y in zip(*np.nonzero(grid)):
                px=int((drawing+(x+.5)/48)*width/4)
                py=int((y+.5)/48*height/2)
                rgb=atlas[py,px].astype(int)
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

    def test_moving_contours_keep_geometry_across_temperature_states(self):
        self.assertEqual(5,len(METEOR_FLOW_CLIPS))
        for clip in METEOR_FLOW_CLIPS:
            def geometry_only(elements):
                return [(e['from'],e['to'],tuple(e['faces'])) for e in elements]
            baseline=flow_mesh(clip,0,self.uv)
            self.assertTrue(baseline,clip)
            self.assertLess(len(baseline),500,clip)
            self.assertEqual({'north','south','east','west','up','down'},{n for e in baseline for n in e['faces']})
            for state in range(3):
                elements=flow_mesh(clip,state,self.uv)
                self.assertEqual(geometry_only(baseline),geometry_only(elements))
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

    def test_initial_flame_regions_tile_one_solid_ignition_at_runtime_pivots(self):
        import numpy as np
        regions=[flow_grid(f'meteor_flow_{i}') for i in range(4)]
        # These pivots are mapped to world offsets by CoreMageChoreography.
        self.assertEqual([(34.5,36.5),(31.5,23.5),(15.,35.5),(13.5,27.5)],
                         [pivot for _,pivot in regions])
        occupied=np.stack([grid>0 for grid,_ in regions]).sum(axis=0)
        self.assertEqual(1,int(occupied.max()))  # never four overlapping copies
        self.assertEqual(569,int((occupied>0).sum()))
        self.assertTrue(all(int((grid>0).sum())>=60 for grid,_ in regions))
        self.assertGreater(sum(int((grid==3).sum()) for grid,_ in regions),60)
        # One joined initial mass; disconnection is caused by the server's
        # subsequent transforms, not holes already baked into a late blast.
        pending=set(zip(*np.nonzero(occupied)));stack=[pending.pop()]
        while stack:
            x,y=stack.pop()
            for p in ((x-1,y),(x+1,y),(x,y-1),(x,y+1)):
                if p in pending:pending.remove(p);stack.append(p)
        self.assertFalse(pending)

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
