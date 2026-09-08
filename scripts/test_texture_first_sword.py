"""Structural regressions only; these tests do not certify reference art parity."""
import hashlib
import json
import math
import unittest
from unittest.mock import patch

import numpy as np
from PIL import Image

from build_texture_first_sword import (OUT, SOURCE, compile_model, runs, rectangles,
    load_art, jewel_geometry_mask, jewel_model, posed_model, item_definition)
from preview_class_armaments import render_model


class TextureFirstSwordTest(unittest.TestCase):
    def test_exported_item_resolves_all_action_poses_only_in_hand_contexts(self):
        # Evaluate the exported native select/range graph, not merely the number
        # of JSON files. Minecraft runtime rendering still needs a manual check.
        path = OUT/'assets/projects/items/weapons/texture_first_sword_study.json'
        definition = json.loads(path.read_text())
        self.assertEqual(definition, item_definition())
        self.assertIs(definition['hand_animation_on_swap'], False)
        root = definition['model']
        self.assertEqual((root['type'], root['property']),
                         ('minecraft:select', 'minecraft:display_context'))
        case, = root['cases']
        hand_contexts = {'firstperson_righthand','firstperson_lefthand',
                         'thirdperson_righthand','thirdperson_lefthand'}
        self.assertEqual(set(case['when']), hand_contexts)
        dispatch = case['model']
        self.assertEqual((dispatch['type'],dispatch['property'],dispatch['index']),
                         ('minecraft:range_dispatch','minecraft:custom_model_data',0))
        self.assertEqual([e['threshold'] for e in dispatch['entries']],
                         [0, *range(12,25)])
        rest = 'projects:item/weapons/texture_first_sword_study'
        for context in hand_contexts | {'gui','fixed','ground','head','none'}:
            for value in (None,-1,0,5,11,11.99,*range(12,24),23.99,24,100):
                node = root['fallback']
                if context in hand_contexts:
                    node = dispatch['fallback']
                    if value is not None:
                        for entry in dispatch['entries']:
                            if value >= entry['threshold']: node = entry['model']
                expected = rest
                if context in hand_contexts and value is not None and 12 <= value < 24:
                    stage = 'prepare' if value < 18 else 'release'
                    expected += f'_{stage}{int(value) % 6:02d}'
                self.assertEqual(node, {'type':'minecraft:model','model':expected})
                namespace, resource = expected.split(':')
                self.assertTrue((OUT/f'assets/{namespace}/models/{resource}.json').is_file())

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
        base, _ = compile_model(spec, alpha)
        regenerated = posed_model(base, jewel_model(spec, load_art(tex)), spec)
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

    def test_painted_spans_reconstruct_mask_exactly(self):
        mask = np.array([[1,1,0,1],[1,1,0,1],[0,1,0,0],[1,1,1,0]], dtype=bool)
        restored = np.zeros_like(mask)
        for x0,y0,x1,y1 in rectangles(mask):
            self.assertFalse(restored[y0:y1,x0:x1].any())
            restored[y0:y1,x0:x1] = True
        self.assertTrue(np.array_equal(mask, restored))

    def test_rejects_fake_transparency_and_opaque_rgba(self):
        for mode, color in (('RGB', (220,220,220)), ('RGBA', (220,220,220,255)),
                            ('RGBA', (0,0,0,0))):
            with patch('build_texture_first_sword.Image.open', return_value=Image.new(mode,(8,8),color)):
                with self.assertRaises(ValueError): load_art('fixture')

    def test_jewel_motion_keeps_blade_grip_and_texture_fixed_and_returns_to_rest(self):
        spec = json.loads((SOURCE/'sword-mesh-v01.json').read_text())
        pixels = load_art(SOURCE/spec['source'])
        before = pixels.copy()
        mask = jewel_geometry_mask(spec,pixels)
        x0,y0,x1,y1 = spec['jewel']['pixel_bounds']
        self.assertTrue(mask[y0:y1,x0:x1].any())
        self.assertFalse(mask[:y0].any() or mask[y1:].any())
        self.assertFalse(mask[:,:x0].any() or mask[:,x1:].any())
        base,_ = compile_model(spec,pixels[:,:,3])
        jewel = jewel_model(spec,pixels)
        rest = posed_model(base,jewel,spec)
        for stage in ('prepare','release'):
            serialized = set()
            for frame in range(6):
                pose = posed_model(base,jewel,spec,stage,frame)
                serialized.add(json.dumps(pose))
                self.assertEqual(pose['elements'][:len(base['elements'])],base['elements'])
                self.assertEqual(pose['textures'],base['textures'])
                for original, changed in zip(jewel['elements'],pose['elements'][len(base['elements']):]):
                    self.assertEqual(original['faces'],changed['faces'])
                    for key in ('from','to'): self.assertEqual(original[key][:2],changed[key][:2])
                    self.assertAlmostEqual(original['to'][2]-original['from'][2],
                                           changed['to'][2]-changed['from'][2])
            self.assertEqual(len(serialized),6)
        self.assertEqual(posed_model(base,jewel,spec,'release',5),rest)
        self.assertTrue(np.array_equal(before,pixels))


if __name__ == '__main__': unittest.main()
