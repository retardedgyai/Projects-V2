"""Regression checks for the isolated material redraw, not visual approval."""
import json
import unittest
import numpy as np
from PIL import Image

import process_sword_material_redraw as art
import build_blade_ember_study as motion
from build_pixel_armament_pack import geometry


class SwordMaterialRedrawTest(unittest.TestCase):
    def test_saved_art_regenerates_without_old_palette(self):
        pixels, textures, entry = art.convert()
        self.assertEqual(entry['source_crop'], [362, 56, 634, 1462])
        self.assertFalse(entry['palette_remap'])
        self.assertFalse(entry['quality_approved'])
        self.assertEqual(pixels.shape, (128, 32, 4))
        self.assertEqual(set(np.unique(pixels[:, :, 3])), {0, 255})
        np.testing.assert_array_equal(pixels, np.array(Image.open(art.OUT / 'greatsword.png')))
        for key, data in textures.items():
            np.testing.assert_array_equal(data, np.array(Image.open(art.OUT / f'greatsword-{key}.png')))
        self.assertEqual(json.loads((art.OUT / 'manifest.json').read_text()), entry)

    def test_gray_metal_and_gem_are_not_lost_in_cleanup(self):
        _, textures, entry = art.convert()
        grip = textures['body'][entry['rows'][-2]:entry['rows'][-1]]
        rgb = grip[:, :, :3].astype(int)
        gray = (grip[:, :, 3] > 0) & (rgb.max(2) - rgb.min(2) < 30) & (rgb.min(2) > 60)
        self.assertGreater(np.count_nonzero(gray), 10)
        gem = textures['jewel'][:, :, 3] > 0
        self.assertEqual(np.count_nonzero(gem), entry['jewel_pixels'])
        # Deliberate flat socket under the raised gem, not another painted gem.
        self.assertTrue(np.all(textures['body'][gem] == [35, 37, 45, 255]))

    def test_redraw_has_its_own_blade_anchors_and_valid_effect_geometry(self):
        entry, body_model, textures = motion.source(redraw=True)
        self.assertNotEqual(entry['ember_emitters'], motion.EMITTERS)
        for row, *_ in entry['ember_emitters']:
            self.assertLess(row, entry['rows'][1])
            self.assertTrue((textures['body'][row, :, 3] > 0).any())
        frames = motion.effect_frames(textures['body'], entry['ember_emitters'])
        model = motion.animated_model(entry, body_model, textures, frames)
        count = len(body_model['elements'])
        self.assertEqual(model['elements'][:count], body_model['elements'])
        self.assertEqual(model['display'], body_model['display'])
        for element in model['elements'][count:]:
            for bound in ('from', 'to'):
                self.assertTrue(all(-16 <= v <= 32 for v in element[bound]))
        exported = motion.ROOT / '.tools/blade-ember-redraw/pack/assets/projects'
        self.assertEqual(json.loads((exported / f'models/item/weapons/{motion.KEY}.json').read_text()), model)
        atlas = np.array(Image.open(exported / f'textures/item/weapons/{motion.KEY}_embers.png'))
        np.testing.assert_array_equal(atlas, np.concatenate(frames, axis=0))

    def test_guard_depth_preserves_blade_grip_and_real_negative_space(self):
        _, textures, entry = art.convert()
        base, _, _ = geometry('greatsword', entry, textures)
        result = art.guard_depth(base, textures, entry)
        unchanged = [e for e in base['elements'] if not e['name'].startswith('guard:')]
        self.assertEqual(result['elements'][:len(unchanged)], unchanged)
        self.assertEqual(result['display'], base['display'])
        branches = result['elements'][len(unchanged):]
        self.assertTrue(any(e['name'].startswith('guard_left') for e in branches))
        self.assertTrue(any(e['name'].startswith('guard_right') for e in branches))
        self.assertTrue(any(e['name'].startswith('guard_hub') for e in branches))
        for e in branches:
            self.assertLessEqual(len(e['faces']), 2)
            if e['name'].startswith('guard_hub'):
                self.assertNotIn('rotation', e)
            else:
                self.assertEqual(e['rotation']['axis'], 'y')
                self.assertIn(e['rotation']['angle'], (-22, 22))


if __name__ == '__main__':
    unittest.main()
