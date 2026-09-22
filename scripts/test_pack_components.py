import tempfile
import unittest
from pathlib import Path
from preview_pack_components import ROOT, render_components, renderer_for


class PackComponentsTest(unittest.TestCase):
    def test_real_projects_icon_font(self):
        pack = ROOT / "server-minestom/src/main/resources/core-ui-pack"
        renderer = renderer_for(pack)
        table = renderer.FONTS.get("projects:core_icons")
        self.assertTrue(table)
        glyph = next(key for key, value in table.items() if value[0] == "bitmap")
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / "icons.png"
            missing = render_components([{"text": glyph, "font": "projects:core_icons"}],
                                        pack, output, "projects:core_icons")
            self.assertEqual([], missing)
            self.assertTrue(output.exists())

    def test_missing_glyph_is_not_silently_accepted(self):
        pack = ROOT / "server-minestom/src/main/resources/core-ui-pack"
        with tempfile.TemporaryDirectory() as directory:
            missing = render_components([{"text": "\U0010ffff"}], pack,
                                        Path(directory) / "missing.png", "projects:core_icons")
            self.assertEqual(1, len(missing))


if __name__ == "__main__":
    unittest.main()
