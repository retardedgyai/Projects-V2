import unittest

import numpy as np

import build_bedrock_greatsword as G
import class_armament_swing as S
import preview_greatsword_fp as P


class BedrockGreatswordTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        G.build()

    def test_written_swing_puts_the_sword_where_the_java_shader_does(self):
        eye = np.array((0.0, G.ASSUMED['eye_px'], 0.0))
        k = G.ASSUMED['entity_scale'] / 16
        grip = np.array(S.MODELS['greatsword_t3'][1])
        for beat in (1, 2, 3):
            keys = S.shader_keys('greatsword_t3', beat)
            L = G.LENGTHS[beat] / 20
            for u in np.linspace(0, 1, 23):
                h, q, s = P.shader_pose(keys, u)
                m, _ = G.bone_matrix(3, f'animation.projects.greatsword_t3.swing_{beat}', u * L, 'fp_sword')
                for p in (grip, grip + (0, 25, 0), grip + (3, 10, 0)):
                    java = h + P._qmat(q) @ (p - grip) * s / 16
                    bed = ((m @ np.append(p - grip + eye, 1.0))[:3] - eye) * k
                    self.assertLess(np.linalg.norm(java - bed), .04, f'beat {beat} u {u:.2f}')

    def test_each_beat_maps_from_its_mark(self):
        items = G.mapping()['items']['minecraft:diamond_sword']
        swing = [d for d in items if 'predicate' in d]
        self.assertEqual([121, 122, 123], [d['predicate']['threshold'] for d in swing])
        self.assertEqual([1, 2, 3], [d['priority'] for d in swing])


if __name__ == '__main__':
    unittest.main()
