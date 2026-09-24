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
        cls.data, elements, cls.atlas, cls.animations = preview.load(source)
        def find_bone(node, name):
            if node["name"] == name:
                return node
            for child in node.get("children", []):
                if isinstance(child, dict):
                    found = find_bone(child, name)
                    if found is not None:
                        return found
            return None

        sword = find_bone(cls.data["outliner"][0], "sword")
        cls.sword_elements = {ident: elements[ident] for ident in sword["children"]}
        cls.blade_id, blade = next((ident, element) for ident, element in elements.items()
                                   if element["name"] == "blade_worn_faces")
        cls.tip = np.array([5.0, float(blade["from"][1]), 0.0, 1.0])
        cls.cape = {ident: element for ident, element in elements.items()
                    if element["name"].startswith(("cape_strip_", "cape_fold_",
                                                   "torn_shoulder_mantle"))}
        cls.shoulder_bridge = [element for element in elements.values()
                               if element["name"].startswith("scarf_left_shoulder_bridge_")]
        cls.boots = {side: {ident: element for ident, element in elements.items()
                            if element["name"].startswith(side + "_boot_")}
                     for side in ("left", "right")}

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

    def test_idle_visible_blade_stays_above_ground(self):
        background = np.array((36, 32, 40))
        ground = np.array((150, 120, 60))
        for view in ("front", "side"):
            for seconds in np.linspace(0, 2, 9):
                pixels = np.asarray(preview.render(
                    self.data, self.sword_elements, self.atlas,
                    self.animations["idle"], float(seconds), view, 12,
                    continuous_light=True, show_grid=False))[:, :, :3]
                ground_rows = np.flatnonzero(np.all(pixels == ground, axis=2).any(axis=1))
                self.assertGreater(len(ground_rows), 0)
                below = pixels[int(ground_rows[0]) + 1:]
                with self.subTest(view=view, seconds=seconds):
                    self.assertFalse(np.any(np.any(below != background, axis=2)))

    def test_idle_feet_remain_near_floor(self):
        for seconds in np.linspace(0, 2, 9):
            world = preview.transforms(self.data["outliner"][0],
                                       self.animations["idle"], float(seconds),
                                       np.eye(4), {})
            for side, elements in self.boots.items():
                lowest = float("inf")
                for ident, element in elements.items():
                    lo, hi = element["from"], element["to"]
                    corners = np.array([[x, y, z] for x in (lo[0], hi[0])
                                        for y in (lo[1], hi[1])
                                        for z in (lo[2], hi[2])])
                    pivot = np.array(element["origin"])
                    corners = (preview.rot(element.get("rotation", [0, 0, 0]))
                               @ (corners - pivot).T).T + pivot
                    transform = world[ident]
                    corners = (transform[:3, :3] @ corners.T).T + transform[:3, 3]
                    lowest = min(lowest, float(corners[:, 1].min()))
                with self.subTest(side=side, seconds=seconds):
                    self.assertGreaterEqual(lowest, -0.25)
                    self.assertLessEqual(lowest, 0.6)

    def test_long_cloak_does_not_pass_through_ground(self):
        for name, animation in self.animations.items():
            lowest = float("inf")
            for seconds in np.linspace(0, float(animation["length"]),
                                       max(2, int(float(animation["length"]) * 20) + 1)):
                world = preview.transforms(self.data["outliner"][0], animation,
                                           float(seconds), np.eye(4), {})
                for ident, element in self.cape.items():
                    lo, hi = element["from"], element["to"]
                    corners = np.array([[x, y, z] for x in (lo[0], hi[0])
                                        for y in (lo[1], hi[1]) for z in (lo[2], hi[2])])
                    pivot = np.array(element["origin"])
                    corners = (preview.rot(element.get("rotation", [0, 0, 0]))
                               @ (corners - pivot).T).T + pivot
                    transform = world[ident]
                    corners = (transform[:3, :3] @ corners.T).T + transform[:3, 3]
                    lowest = min(lowest, float(corners[:, 1].min()))
            with self.subTest(animation=name):
                self.assertGreaterEqual(lowest, -1.5)

    def test_cloak_keeps_visible_side_faces(self):
        strips = [element for element in self.cape.values()
                  if element["name"].startswith("cape_strip_")]
        self.assertEqual({int(element["name"].split("_")[2]) for element in strips},
                         {0, 1, 2})
        for element in strips:
            for side in ("east", "west"):
                uv = element["faces"][side]["uv"]
                x, y = (uv[0] + uv[2]) // 2, (uv[1] + uv[3]) // 2
                with self.subTest(element=element["name"], side=side):
                    self.assertGreater(self.atlas[y, x, 3], 0)

    def test_shoulder_to_cape_connection_has_visible_depth(self):
        self.assertEqual(len(self.shoulder_bridge), 3)
        for element, side in ((self.shoulder_bridge[0], "west"),
                              (self.shoulder_bridge[-1], "east")):
            uv = element["faces"][side]["uv"]
            x, y = (uv[0] + uv[2]) // 2, (uv[1] + uv[3]) // 2
            with self.subTest(side=side):
                self.assertGreater(self.atlas[y, x, 3], 0)
                self.assertGreater(element["to"][2] - element["from"][2], 1)


if __name__ == "__main__":
    unittest.main()
