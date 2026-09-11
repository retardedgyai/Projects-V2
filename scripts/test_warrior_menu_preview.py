"""The offline menu review must use the actual pack sprite, never an icon stand-in."""
import json
import unittest
from PIL import Image
from render_core_menu_preview import MenuRenderer


class WarriorMenuPreviewTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.renderer = MenuRenderer()

    def snapshot(self):
        return {"title": "戦士", "titleColor": 0xffffff,
                "buttons": [{"firstSlot": 0, "span": 2, "tone": "SELECTED", "label": "2",
                             "textColor": 0xffffff, "icon": True}]}

    def test_exported_inventory_sprite_is_composited_at_its_actual_slot(self):
        r = self.renderer
        snapshot = self.snapshot()
        expected, omitted = r.render(snapshot)
        self.assertEqual([0], omitted["omitted_icon_slots"])
        # Read the shipped item chain independently to check the displayed pixels.
        definition = json.loads((r.assets / "items/core_ui/dash.json").read_text())["model"]
        model = json.loads((r.assets / ("models/" + definition["model"].split(":")[1] + ".json")).read_text())
        with Image.open(r.assets / ("textures/" + model["textures"]["layer0"].split(":")[1] + ".png")) as source:
            icon = source.convert("RGBA").resize((16*r.raster_scale, 16*r.raster_scale), Image.Resampling.NEAREST)
        expected.alpha_composite(icon, ((8-r.origin_x)*r.raster_scale, 18*r.raster_scale))
        snapshot["itemModels"] = [{"slot": 0, "model": "projects:core_ui/dash"},
                                  {"slot": 1, "model": "projects:core_ui/blank"}]
        actual, report = r.render(snapshot)
        self.assertEqual(expected.tobytes(), actual.tobytes())
        self.assertEqual([0], report["rendered_item_slots"])
        self.assertEqual([], report["omitted_icon_slots"])
        self.assertEqual([], report["unsupported_item_slots"])
        self.assertEqual([], report["warnings"])

    def test_unsupported_models_are_reported_not_replaced_with_fake_art(self):
        snapshot = self.snapshot()
        expected, _ = self.renderer.render(snapshot)
        snapshot["itemModels"] = [{"slot": 0, "model": "projects:combat_vfx/warrior_skills/cleave_blade_5"}]
        actual, report = self.renderer.render(snapshot)
        self.assertEqual(expected.tobytes(), actual.tobytes())
        self.assertEqual([0], report["unsupported_item_slots"])
        self.assertEqual([0], report["omitted_icon_slots"])
        self.assertEqual([], report["rendered_item_slots"])


if __name__ == "__main__":
    unittest.main()
