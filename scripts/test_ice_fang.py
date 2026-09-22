import base64
import json
import unittest
from build_ice_fang import build, OUT
from boss_models import validate_model


class IceFangTests(unittest.TestCase):
    def test_committed_asset_matches_deterministic_authoring(self):
        self.assertEqual(build(), json.loads(OUT.read_text(encoding="utf-8")))

    def test_native_animation_and_budget(self):
        model = build()
        cubes, bones, animations = validate_model(model)
        self.assertLessEqual(cubes, 150)
        self.assertEqual(bones, 9)
        self.assertEqual(animations, 1)
        self.assertEqual(model["resolution"], {"width": 32, "height": 32})
        self.assertEqual(model["animations"][0]["length"], 1.5)
        for track in model["animations"][0]["animators"].values():
            positions = [k for k in track["keyframes"] if k["channel"] == "position"]
            self.assertLess(positions[-1]["data_points"][0]["y"], 0)

    def test_silhouette_is_geometry_not_a_rectangular_billboard(self):
        model = build()
        rows = [e for e in model["elements"] if e["name"].startswith("fang0_")]
        self.assertEqual(len(rows), 32)
        self.assertLess(rows[0]["to"][0]-rows[0]["from"][0], rows[-1]["to"][0]-rows[-1]["from"][0])
        self.assertTrue(all(e["to"][2] > e["from"][2] for e in rows))
        self.assertTrue(base64.b64decode(model["textures"][0]["source"].split(",")[1]).startswith(b"\x89PNG"))


if __name__ == "__main__":
    unittest.main()
