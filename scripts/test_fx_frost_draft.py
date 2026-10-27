"""霜の波紋の1版目（build_fx_frost_draft.py）: every sprite is in the frost ramp and the storyboard builds."""
import pathlib
import tempfile
import unittest

import build_fx_frost_draft as draft
from fx_art import RAMPS, check, samples


class FrostDraftTest(unittest.TestCase):
    def test_every_sprite_is_in_the_frost_ramp(self):
        for name, img in draft.frost_sprites().items():
            self.assertEqual(check(img, RAMPS['frost']), [], name)

    def test_the_fire_set_is_reused_unchanged_apart_from_the_ramp(self):
        fire, frost = samples(RAMPS['fire']), draft.frost_sprites()
        for name in fire:
            self.assertEqual(list(fire[name].getchannel('A').getdata()), list(frost[name].getchannel('A').getdata()), name)

    def test_the_ice_column_stands_on_its_base_with_a_white_rim(self):
        for k in range(3):
            img = draft.frost_sprites()[f'column_{k}']
            bottom = [img.getpixel((x, img.height - 1)) for x in range(img.width)]
            self.assertTrue(any(p[3] == 255 for p in bottom), f'column_{k} must touch the bottom row')
            self.assertIn(RAMPS['frost']['w'], [p[:3] for p in bottom if p[3]], f'column_{k} base must have the white rim')
            self.assertEqual(img.getpixel((0, 0))[3], 0, f'column_{k} must taper to a point')

    def test_storyboard_builds(self):
        out = pathlib.Path(tempfile.mkdtemp()) / 'frost.png'
        page = draft.storyboard(out)
        self.assertTrue(out.exists())
        self.assertEqual(page.width, 3 * (draft.W + 6) + 6)


if __name__ == '__main__':
    unittest.main()
