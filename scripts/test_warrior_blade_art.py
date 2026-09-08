import json
import unittest
import numpy as np
from build_warrior_blade_art import PACK, COLOURS, build, contour


class WarriorBladeArtTest(unittest.TestCase):
    def test_geometry_clusters_are_coarse_and_not_antialiased(self):
        for layer in ('cut', 'tip', 'tail'):
            g = contour(layer)
            self.assertTrue(np.array_equal(g, np.repeat(np.repeat(g[::8, ::2], 8, 0), 2, 1)))
            self.assertEqual(set(np.unique(g)), {0, 1, 2, 3, 4})
            if layer == 'cut':
                self.assertGreater(np.count_nonzero(g), g.size * .65)
                self.assertGreater(np.count_nonzero(g == 3), 350)

    def test_generated_resources_reproduce_and_keep_white_edge_red_accent(self):
        generated = {}
        build(PACK / 'assets/projects', lambda p, v: generated.__setitem__(p, v))
        self.assertEqual(50, len(generated))
        for path, value in generated.items():
            self.assertEqual(value, json.loads(path.read_text(encoding='utf-8')), str(path))
            if '/models/' in path.as_posix():
                self.assertLess(len(value['elements']), 300)
        for colours in COLOURS.values():
            self.assertGreater(colours[2] & 255, 220)
            accent = colours[3]
            self.assertGreater(accent >> 16, (accent >> 8) & 255)
        index = (PACK / 'index.txt').read_text(encoding='utf-8').splitlines()
        self.assertTrue(all(p.relative_to(PACK).as_posix() in index for p in generated))


if __name__ == '__main__':
    unittest.main()
