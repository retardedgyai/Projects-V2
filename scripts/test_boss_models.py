import copy
import json
import unittest
from boss_models import SOURCE, validate_model


class ModelValidationTest(unittest.TestCase):
    def setUp(self):
        self.model = json.loads((SOURCE / "vesper.bbmodel").read_text(encoding="utf-8"))

    def test_all_imported_models(self):
        for path in SOURCE.glob("*.bbmodel"):
            with self.subTest(model=path.name):
                validate_model(json.loads(path.read_text(encoding="utf-8")))

    def test_duplicate_cube_rejected(self):
        self.model["elements"].append(copy.deepcopy(self.model["elements"][0]))
        with self.assertRaisesRegex(ValueError, "duplicate element"):
            validate_model(self.model)

    def test_missing_bone_rejected(self):
        anim = self.model["animations"][0]
        anim["animators"]["missing-bone"] = next(iter(anim["animators"].values()))
        with self.assertRaisesRegex(ValueError, "missing animation bone"):
            validate_model(self.model)

    def test_out_of_range_keyframe_rejected(self):
        anim = self.model["animations"][0]
        next(iter(anim["animators"].values()))["keyframes"][0]["time"] = anim["length"] + 1
        with self.assertRaisesRegex(ValueError, "outside animation"):
            validate_model(self.model)

    def test_unparented_cube_rejected(self):
        extra = copy.deepcopy(self.model["elements"][0])
        extra["uuid"] = "extra"
        self.model["elements"].append(extra)
        with self.assertRaisesRegex(ValueError, "unparented"):
            validate_model(self.model)

    def test_nonfinite_cube_rejected(self):
        self.model["elements"][0]["from"][0] = float("nan")
        with self.assertRaisesRegex(ValueError, "non-finite"):
            validate_model(self.model)


if __name__ == "__main__":
    unittest.main()
