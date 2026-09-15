"""Structural checks, not a claim of visual/reference-quality acceptance."""
import json
import unittest
from build_mage_meteor import METEOR_CLIPS, PALETTE, SOURCE, PACK, mesh, ink_uvs


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
                item=json.loads((self.assets/f'items/combat_vfx/mage_material/{clip}_{frame}.json').read_text())
                self.assertEqual(PALETTE,[t['value'] for t in item['model']['tints']])
            self.assertEqual([],mesh(clip,23,self.uv))
        self.assertEqual(SOURCE.read_bytes(),(self.assets/'textures/combat_vfx/mage_material/meteor_basalt_v01.png').read_bytes())

    def test_rock_is_one_volume_with_continuous_world_uvs(self):
        rock=mesh('meteor',0,self.uv)
        self.assertGreater(len(rock),6)
        for axis in range(3):
            span=max(e['to'][axis] for e in rock)-min(e['from'][axis] for e in rock)
            self.assertGreater(span,7.)
        self.assertEqual({'north','south','east','west','up','down'},
                         {n for e in rock for n in e['faces']})
        self.assertTrue(all(len(e['faces'])==1 for e in rock))
        self.assertEqual({'#0','#1'},{f['texture'] for e in rock for f in e['faces'].values()})
        self.assertTrue(all(e['to'][1]<=6.5 for e in rock if next(iter(e['faces'].values()))['texture']=='#0'))
        # Falling motion belongs to the authoritative display, not a wobbling
        # rock-texture animation that restarts its cracks every frame.
        self.assertEqual(rock,mesh('meteor',20,self.uv))

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
