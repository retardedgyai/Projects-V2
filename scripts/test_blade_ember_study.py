"""Structural motion/export checks, not an art-quality approval."""
import json
import unittest

import numpy as np
from PIL import Image

import build_blade_ember_study as study


class BladeEmberStudyTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.entry, cls.base, cls.textures = study.source()
        cls.frames = study.effect_frames(cls.textures['body'])
        cls.model = study.animated_model(cls.entry, cls.base, cls.textures, cls.frames)

    def test_deterministic_pixel_frames_and_period(self):
        other = study.effect_frames(self.textures['body'])
        self.assertEqual(len(self.frames), 24)
        self.assertEqual(len({f.tobytes() for f in self.frames}), 24)
        for a, b in zip(self.frames, other):
            np.testing.assert_array_equal(a, b)
            self.assertEqual(a.shape, (128, 64, 4))
            self.assertEqual(set(np.unique(a[:, :, 3])), {0, 255})
        # Wrapping is another ordinary step, not a wholesale reset flash.
        changes = [np.count_nonzero(a[:, :, 3] != b[:, :, 3])
                   for a, b in zip(self.frames, self.frames[1:] + self.frames[:1])]
        self.assertLessEqual(changes[-1], max(changes[:-1]))

    def test_blade_only_no_grip_cloud_and_visible_outside_body(self):
        mask = np.zeros((study.HEIGHT, study.WIDTH), bool)
        body = self.textures['body'][:, :, 3] > 0
        mask[study.PAD_Y:, study.PAD_X:study.PAD_X + body.shape[1]] = body[:study.HEIGHT-study.PAD_Y]
        for frame in self.frames:
            active = frame[:, :, 3] > 0
            self.assertTrue((active & ~mask).any())
            self.assertLess(np.nonzero(active)[0].max(), self.entry['rows'][1] + study.PAD_Y)

    def test_body_display_and_geometry_unchanged(self):
        self.assertEqual(self.model['display'], self.base['display'])
        count = len(self.base['elements'])
        self.assertEqual(self.model['elements'][:count], self.base['elements'])
        for element in self.model['elements'][count:]:
            self.assertLessEqual(len(element['faces']), 2)
            self.assertTrue(all(f['texture'] == '#embers' for f in element['faces'].values()))
            for bound in ('from', 'to'):
                self.assertTrue(all(-16 <= v <= 32 for v in element[bound]))

    def test_saved_atlas_and_metadata_match_model(self):
        assets = study.PACK / 'assets/projects'
        model = json.loads((assets / f'models/item/weapons/{study.KEY}.json').read_text())
        self.assertEqual(model, self.model)
        atlas = np.array(Image.open(assets / f'textures/item/weapons/{study.KEY}_embers.png'))
        np.testing.assert_array_equal(atlas, np.concatenate(self.frames, axis=0))
        meta = json.loads((assets / f'textures/item/weapons/{study.KEY}_embers.png.mcmeta').read_text())
        self.assertEqual(meta, study.metadata())
        self.assertFalse(meta['animation']['interpolate'])
        for part in ('body', 'jewel'):
            self.assertEqual((assets / f'textures/item/weapons/pixel_greatsword_{part}.png').read_bytes(),
                             (study.SOURCE / f'greatsword-{part}.png').read_bytes())


if __name__ == '__main__':
    unittest.main()
