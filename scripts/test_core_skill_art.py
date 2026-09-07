"""Read-only regression checks for authored class framing and cooldown composition."""
import hashlib
import json
import unittest
from PIL import Image
from build_core_hud_assets import ROOT, SKILLS, CLASSES, class_for_skill, class_ornament, fit_skill, skill_frame

SOURCE = ROOT / "assets/core-ui"
PACK = ROOT / "server-minestom/src/main/resources/core-ui-pack/assets/projects"


class SkillArtworkTest(unittest.TestCase):
    def test_all_authored_masters_are_unique_and_manifested(self):
        manifest = json.loads((SOURCE / "skill-art-manifest.json").read_text())
        self.assertEqual(set(SKILLS), set(manifest["skills"]))
        self.assertEqual(set(CLASSES), set(manifest["frames"]))
        for kind, folder in (("skills", "skills"), ("frames", "skill-frames")):
            hashes = []
            for name, entry in manifest[kind].items():
                path = SOURCE / folder / f"{name}.png"
                digest = hashlib.sha256(path.read_bytes()).hexdigest()
                self.assertEqual(entry["master_sha256"], digest)
                if kind == "frames":
                    raw = SOURCE / f"skill-frames/source/{name}.png"
                    self.assertEqual(entry["source_sha256"], hashlib.sha256(raw.read_bytes()).hexdigest())
                hashes.append(digest)
                with Image.open(path) as image:
                    self.assertEqual((32, 32), image.size)
                    self.assertIsNotNone(image.getchannel("A").getbbox())
            self.assertEqual(len(hashes), len(set(hashes)))

    def test_class_assignment_and_clear_icon_wells(self):
        for job, first in zip(CLASSES, ("dash", "pierce", "firebolt", "star_thread", "ass_stab", "temp_mace", "heal_light")):
            self.assertEqual(job, class_for_skill(first))
            ornament = class_ornament(first)
            self.assertIsNone(ornament.crop((4, 4, 28, 28)).getchannel("A").getbbox())

    def test_every_shipped_menu_and_hud_uses_its_class_frame(self):
        for name in SKILLS:
            with self.subTest(skill=name), Image.open(SOURCE / f"skills/{name}.png") as source:
                fitted = fit_skill(source)
                ornament = class_ornament(name)
                menu = skill_frame(fitted, 0, ornament, key_badge=False)
                with Image.open(PACK / f"textures/gui/skills/{name}.png") as shipped:
                    self.assertEqual(menu.tobytes(), shipped.convert("RGBA").tobytes())
                expected = [skill_frame(fitted, state, ornament) for state in range(23)]
                with Image.open(PACK / f"textures/gui/core/skill_{name}_states.png") as sheet:
                    for state in range(24):
                        x, y = state % 4 * 32, state // 4 * 32
                        self.assertEqual(expected[min(state, 22)].tobytes(), sheet.crop((x,y,x+32,y+32)).tobytes())
                # At full cooldown the art is darker, but class ornaments remain identical.
                self.assertNotEqual(expected[0].tobytes(), expected[20].tobytes())
                for state in range(1, 23):
                    for y in range(32):
                        for x in range(32):
                            if x < 4 or x >= 28 or y < 4 or y >= 28:
                                self.assertEqual(expected[0].getpixel((x,y)), expected[state].getpixel((x,y)))
                self.assertEqual((0,0,32,32), expected[0].getchannel("A").getbbox())


if __name__ == "__main__":
    unittest.main()
