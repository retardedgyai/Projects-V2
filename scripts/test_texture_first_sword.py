"""Structural regressions only; these tests do not certify reference art parity."""
import hashlib
import json
import math
import unittest

import numpy as np
from PIL import Image

from build_texture_first_sword import OUT, SOURCE, compile_model, runs
from preview_class_armaments import render_model


class TextureFirstSwordTest(unittest.TestCase):
    def fixture(self, alpha):
        h, w = alpha.shape
        return {'height': h, 'top_pixel': 0, 'bottom_pixel': h, 'pivot_pixel_x': w/2,
            'alpha_cutoff': 128, 'texture': 'projects:test',
            'parts': [{'name': 'test', 'rows': [0, h], 'thickness': .12}]}

    def test_runs_do_not_bridge_transparent_gaps(self):
        self.assertEqual(runs([1, 1, 0, 1, 0, 0, 1]), [(0, 2), (3, 4), (6, 7)])
        self.assertEqual(runs([0, 0]), [])

    def test_rectangle_has_only_four_side_faces_not_voxel_cubes(self):
        alpha = np.full((8, 6), 255, dtype=np.uint8)
        model, report = compile_model(self.fixture(alpha), alpha)
        self.assertEqual(report[0]['side_quads'], 4)
        self.assertEqual(len(model['elements']), 5)
        self.assertEqual(set(model['elements'][0]['faces']), {'north', 'south'})
        self.assertTrue(all(len(e['faces']) == 1 for e in model['elements'][1:]))

    def test_holes_gain_inner_edges_and_keep_broad_faces_transparent(self):
        alpha = np.full((6, 6), 255, dtype=np.uint8)
        alpha[2:4, 2:4] = 0
        model, report = compile_model(self.fixture(alpha), alpha)
        self.assertEqual(report[0]['side_quads'], 8)
        self.assertEqual(len(model['elements']), 9)

    def test_partition_rejects_gaps_overlaps_and_box_depth(self):
        alpha = np.full((8, 6), 255, dtype=np.uint8)
        for rows in ([[0, 3], [4, 8]], [[0, 5], [4, 8]]):
            spec = self.fixture(alpha)
            spec['parts'] = [{'name': str(i), 'rows': row, 'thickness': .12} for i, row in enumerate(rows)]
            with self.assertRaises(ValueError): compile_model(spec, alpha)
        spec = self.fixture(alpha)
        spec['parts'][0]['thickness'] = 4
        with self.assertRaises(ValueError): compile_model(spec, alpha)

    def test_actual_export_preserves_art_and_has_native_finite_geometry(self):
        spec = json.loads((SOURCE/'sword-mesh-v01.json').read_text())
        tex = OUT/'assets/projects/textures/item/weapons/texture_first_sword_study.png'
        self.assertEqual(hashlib.sha256(tex.read_bytes()).digest(),
                         hashlib.sha256((SOURCE/spec['source']).read_bytes()).digest())
        model = json.loads((OUT/'assets/projects/models/item/weapons/texture_first_sword_study.json').read_text())
        alpha = np.asarray(Image.open(tex))[:, :, 3]
        regenerated, _ = compile_model(spec, alpha)
        self.assertEqual(model, regenerated)
        for e in model['elements']:
            for a, b in zip(e['from'], e['to']):
                self.assertTrue(math.isfinite(a) and math.isfinite(b))
                self.assertTrue(-16 <= a <= b <= 32)
            for f in e['faces'].values():
                self.assertTrue(all(0 <= v <= 16 for v in f['uv']))
                self.assertEqual(f['texture'], '#art')
        production_index = SOURCE.parents[2]/'server-minestom/src/main/resources/core-ui-pack/index.txt'
        self.assertNotIn('texture_first_sword_study', production_index.read_text())

    def test_asymmetric_art_has_correct_north_uv_and_transparent_cutout(self):
        alpha = np.full((8, 8), 255, dtype=np.uint8)
        alpha[:, 3:5] = 0
        model, _ = compile_model(self.fixture(alpha), alpha)
        texture = np.zeros((8, 8, 4), dtype=np.uint8)
        texture[:, :4, 0] = 255
        texture[:, 4:, 2] = 255
        texture[:, :, 3] = alpha
        # With no nearest-neighbor filtering or simulated material repainting,
        # the first broad face must reproduce left red / right blue exactly.
        front = model['elements'][0]['faces']['north']['uv']
        self.assertEqual(front, [16, 0, 0, 16])
        rendered = np.asarray(render_model(model, {'art': texture}, yaw=0, size=(224, 180), scale=10))
        self.assertTrue(np.any(np.all(rendered[:, :110] == [255, 0, 0], axis=2)))
        self.assertTrue(np.any(np.all(rendered[:, 114:] == [0, 0, 255], axis=2)))
        self.assertTrue(np.all(rendered[80:120, 108:116] == [27, 30, 35]))


if __name__ == '__main__': unittest.main()
