"""Structural checks, not a claim of visual/reference-quality acceptance."""
import json
import math
import unittest
from build_mage_meteor import METEOR_CLIPS, PALETTE, SOURCE, EMBER_SOURCE, IMPACT_SOURCE, BRIDGE_SOURCE, WAKE_SOURCE, PACK, mesh, ink_uvs, rock, burning_wake, pressure_burst, impact_atlas, impact_drawing


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

    def test_impact_has_no_checkerboard_faces_and_is_readable_from_side(self):
        for frame in range(7):
            source,drawing,columns,rows,texture=impact_drawing(frame)
            atlas=impact_atlas(source);height,width=atlas.shape[:2]
            faces=[f for e in pressure_burst(frame,self.uv) for f in e['faces'].values()]
            self.assertTrue(faces)
            for f in faces:
                self.assertEqual(texture,f['texture'])
                self.assertEqual(0,f['tintindex'])  # original colours, no extra orange tint
                u0,v0,u1,v1=f['uv']
                px=int((u0+u1)/32*width);py=int((v0+v1)/32*height)
                rgb=atlas[py,px].astype(int)
                self.assertGreaterEqual(rgb[0]-rgb[2],32)
                self.assertEqual(drawing%columns,int(px/width*columns))
                self.assertEqual(drawing//columns,int(py/height*rows))
            self.assertEqual({'north','south','east','west'},
                             {n for e in pressure_burst(frame,self.uv) for n in e['faces']})
        # The same separated pieces cool into explicitly authored neutral ash;
        # the RGB backdrop is never sampled as smoke, and ignition never loops.
        self.assertTrue(pressure_burst(8,self.uv))
        for frame in range(8,15):
            self.assertTrue(all(f['texture']=='#0' and f['tintindex'] in (2,7)
                                for e in pressure_burst(frame,self.uv) for f in e['faces'].values()))
        self.assertEqual([],pressure_burst(15,self.uv))
        self.assertGreater(max(e['to'][1] for e in pressure_burst(4,self.uv)),
                           max(e['to'][1] for e in pressure_burst(0,self.uv)))

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

    def test_bridge_opens_central_space_and_contact_keeps_only_short_heat_residue(self):
        atlas=impact_atlas(BRIDGE_SOURCE);height,width=atlas.shape[:2]
        holes=[]
        for drawing in range(4):
            hole=0
            for x in range(17,31):
                for y in range(29,43):
                    px=int((drawing%2+(x+.5)/48)*width/2)
                    py=int((drawing//2+(y+.5)/48)*height/2)
                    rgb=atlas[py,px].astype(int)
                    hole+=int(rgb[0]-rgb[2]<32)
            holes.append(hole)
        self.assertTrue(all(a<b for a,b in zip(holes,holes[1:])),holes)
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
        self.assertGreater(max(e['to'][1] for e in pressure_burst(3,self.uv)),11.5)
        self.assertTrue(any('north' in e['faces'] for e in pressure_burst(3,self.uv)))
        self.assertEqual([],pressure_burst(14,self.uv))

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
