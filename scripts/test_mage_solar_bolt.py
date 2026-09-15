"""Structural tests, not an art-quality or native rendering acceptance gate."""
import hashlib
import json
import unittest
from build_mage_solar_bolt import PACK, SOURCE, CLIPS, mesh


class SolarBoltTests(unittest.TestCase):
    def test_original_paint_is_shipped_unchanged(self):
        texture=PACK/'assets/projects/textures/combat_vfx/mage_material/solar_bolt_faces_v01.png'
        self.assertEqual(hashlib.sha256(SOURCE.read_bytes()).digest(),hashlib.sha256(texture.read_bytes()).digest())

    def test_no_longitudinal_geometry_outside_normalized_clipped_segment(self):
        for clip in CLIPS:
            for e in mesh(clip):
                self.assertTrue(all(a<=b for a,b in zip(e['from'],e['to'])))
                self.assertGreaterEqual(e['from'][2],0)
                self.assertLessEqual(e['to'][2],16)
                if 'rotation' in e:
                    self.assertEqual(e['rotation']['axis'],'z')
                    self.assertIn(e['rotation']['angle'],(-45,45))
                for f in e['faces'].values():
                    self.assertTrue(all(0<=n<=16 for n in f['uv']))

    def test_core_is_complete_but_surviving_fringe_is_not_a_duplicate_whole_bolt(self):
        self.assertEqual(min(e['from'][2] for e in mesh('solar_bolt_core')),0)
        self.assertEqual(max(e['to'][2] for e in mesh('solar_bolt_core')),16)
        for clip in ('solar_bolt_shell','solar_bolt_spark'):
            self.assertEqual(len(mesh(clip)),2)
            self.assertGreater(min(e['from'][2] for e in mesh(clip)),0)
            self.assertLess(max(e['to'][2] for e in mesh(clip)),16)
        self.assertNotEqual(mesh('solar_bolt_core'),mesh('solar_bolt_shell'))

    def test_side_material_does_not_repeat_whole_atlas_on_each_narrow_panel(self):
        for e in mesh('solar_bolt_core'):
            for name,f in e['faces'].items():
                if name in ('up','down','east','west'):
                    self.assertLessEqual(abs(f['uv'][3]-f['uv'][1]),1.0)

    def test_forward_layer_is_offset_instead_of_a_concentric_regular_pellet(self):
        rear=[e for e in mesh('solar_bolt_core') if e['from'][2]==0]
        front=[e for e in mesh('solar_bolt_core') if e['to'][2]==16]
        def center(elements,axis):
            return (min(e['from'][axis] for e in elements)+max(e['to'][axis] for e in elements))/2
        self.assertGreater(center(front,0),center(rear,0)+.5)
        self.assertGreater(center(front,1),center(rear,1)+.3)

    def test_wakes_are_thin_compared_with_volumetric_head(self):
        for clip in ('solar_bolt_wake','solar_bolt_thread'):
            for e in mesh(clip):
                self.assertLess(e['to'][0]-e['from'][0],.7)
                self.assertGreater(e['to'][1]-e['from'][1],0)
        self.assertTrue(any('rotation' in e for e in mesh('solar_bolt_core')))

    def test_assets_are_registered_and_reproducible(self):
        index=(PACK/'index.txt').read_text().splitlines()
        for clip in CLIPS:
            path=f'assets/projects/models/combat_vfx/mage_material/{clip}_0.json'
            self.assertIn(path,index)
            self.assertIn(path.replace('/models/','/items/'),index)
            self.assertEqual(mesh(clip),json.loads((PACK/path).read_text())['elements'])


if __name__=='__main__':
    unittest.main()
