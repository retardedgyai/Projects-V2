import json
import unittest
from build_approved_dash_v3 import PACK, ribbon
from build_approved_aa_v3 import build


class ApprovedAAV3Test(unittest.TestCase):
    def test_reverse_is_exact_geometric_reflection_and_indexed(self):
        generated = {}
        build(PACK/'assets/projects', lambda p, v: generated.__setitem__(p, v))
        self.assertEqual(52, len(generated))
        index = (PACK/'index.txt').read_text(encoding='utf-8').splitlines()
        for path, value in generated.items():
            self.assertEqual(value, json.loads(path.read_text(encoding='utf-8')))
            self.assertIn(path.relative_to(PACK).as_posix(), index)
            original = json.loads((path.parent.parent/'approved_dash_v3'/path.name).read_text(encoding='utf-8'))
            if 'elements' in value:
                for mirrored, source in zip(value['elements'], original['elements']):
                    self.assertEqual(16-source['to'][0], mirrored['from'][0])
                    self.assertEqual(16-source['from'][0], mirrored['to'][0])
                    self.assertEqual(source['from'][1:], mirrored['from'][1:])
                    self.assertEqual(source['faces'], mirrored['faces'])
            else:
                self.assertEqual(original['model']['tints'], value['model']['tints'])

    def test_approved_wake_erodes_to_empty_before_removal(self):
        coverage = [int((ribbon(frame, True) > 0).sum()) for frame in range(3, 16)]
        self.assertGreater(max(coverage), 100)
        self.assertGreater(coverage[-4], coverage[-3])
        self.assertEqual([0, 0], coverage[-2:])


if __name__ == '__main__':
    unittest.main()
