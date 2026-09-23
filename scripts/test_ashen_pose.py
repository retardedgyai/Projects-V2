"""Guard the authored sword arc against obvious visual ground clipping."""

from pathlib import Path
import sys
import unittest

import numpy as np

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "vendor" / "scorpius" / "bbmodel"))
import preview_bbmodel as preview  # noqa: E402


class AshenPoseTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        source = ROOT / "model-lab" / "models" / "ashen_knight.bbmodel"
        cls.data, elements, _, cls.animations = preview.load(source)
        cls.blade_id, blade = next((ident, element) for ident, element in elements.items()
                                   if element["name"] == "blade_worn_faces")
        cls.tip = np.array([5.0, float(blade["from"][1]), 0.0, 1.0])

    @classmethod
    def tip_y(cls, name, seconds):
        transform = preview.transforms(cls.data["outliner"][0],
                                       cls.animations[name], seconds, np.eye(4), {})[cls.blade_id]
        return float((transform @ cls.tip)[1])

    def minimum_tip_y(self, name):
        length = float(self.animations[name]["length"])
        return min(self.tip_y(name, float(seconds))
                   for seconds in np.linspace(0, length, max(2, int(length * 40) + 1)))

    def test_sword_sweep_remains_above_ground(self):
        self.assertGreaterEqual(self.minimum_tip_y("cleave"), -1.0)
        self.assertGreater(self.tip_y("cleave", .38), 15)

    def test_slam_touches_ground_without_deep_clipping(self):
        minimum = self.minimum_tip_y("slam")
        self.assertGreaterEqual(minimum, -1.5)
        self.assertLessEqual(minimum, 1.5)
        self.assertGreater(self.tip_y("slam", .7), 25)
        self.assertLessEqual(abs(self.tip_y("slam", .94)), 1.5)

    def test_long_blade_does_not_cut_through_floor_in_other_motions(self):
        for name in self.animations:
            with self.subTest(animation=name):
                self.assertGreaterEqual(self.minimum_tip_y(name), -1.5)

    def test_idle_sword_tip_rests_near_ground(self):
        self.assertGreaterEqual(self.tip_y("idle", 0), -1)
        self.assertLessEqual(self.tip_y("idle", 0), 1)


if __name__ == "__main__":
    unittest.main()
