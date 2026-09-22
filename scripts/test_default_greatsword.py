"""Normal startup must retain the creator-approved sword without a local overlay."""
import json
import unittest
from build_material_playtest_pack import TARGET, MODEL_NAME, candidate_resources, assemble
from promote_approved_greatsword import PACK


class DefaultGreatswordTest(unittest.TestCase):
    def test_default_assets_equal_approved_generator(self):
        expected = candidate_resources()
        expected[TARGET] = expected[f'assets/projects/items/weapons/{MODEL_NAME}.json']
        index = (PACK / 'index.txt').read_text(encoding='utf-8').splitlines()
        for name, data in expected.items():
            self.assertEqual(index.count(name), 1, name)
            self.assertEqual((PACK / name).read_bytes(), data, name)

    def test_legacy_preview_is_idempotent_after_promotion(self):
        candidate = candidate_resources()
        base = {**candidate, TARGET: candidate[f'assets/projects/items/weapons/{MODEL_NAME}.json']}
        self.assertEqual(assemble(base, candidate), base)
        damaged = dict(base)
        damaged[next(iter(candidate))] = b'wrong generation'
        with self.assertRaises(ValueError):
            assemble(damaged, candidate)

    def test_other_tiers_keep_their_own_routing(self):
        for tier in (2, 3, 4):
            raw = (PACK / f'assets/projects/items/weapons/greatsword_t{tier}.json').read_text()
            self.assertIn(f'greatsword_t{tier}', raw)
            self.assertNotIn(MODEL_NAME, raw)
            self.assertIn('model', json.loads(raw))


if __name__ == '__main__':
    unittest.main()
