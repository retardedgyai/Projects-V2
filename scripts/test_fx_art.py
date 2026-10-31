"""The house style for effect sprites (fx_art.py): the ramps, the reference set, the check, the palette swap."""
import unittest

from PIL import Image

import fx_art
from fx_art import RAMPS, TONES, check, samples, art, canvas, cool


def luminance(rgb):
    r, g, b = rgb
    return .2126 * r + .7152 * g + .0722 * b


# The reference drawings are the fire bolt's own (scripts/build_mage_fire.py, PR #189); without it only the ramps,
# the check and the helpers can be tested.
needs_fire = unittest.skipUnless(fx_art.HAS_FIRE, fx_art.NEEDS_FIRE)


class RampTest(unittest.TestCase):
    def test_every_ramp_has_the_six_tones_edge_to_core(self):
        for name, ramp in RAMPS.items():
            self.assertEqual(set(ramp), set(TONES), name)
            lum = [luminance(ramp[t]) for t in TONES]
            self.assertEqual(lum, sorted(lum), f'{name} must go dark edge -> light core')
            self.assertLess(lum[0], 130, f'{name} edge is not dark')
            self.assertGreater(lum[-1], 220, f'{name} core is not near white')

    def test_ramps_do_not_share_colours(self):
        seen = {}
        for name, ramp in RAMPS.items():
            for tone, rgb in ramp.items():
                self.assertNotIn(rgb, seen, f'{name}.{tone} repeats {seen.get(rgb)}: the check could not tell the ramps apart')
                seen[rgb] = f'{name}.{tone}'


@needs_fire
class ReferenceSetTest(unittest.TestCase):
    def test_the_reference_is_the_fire_bolts_own_drawings(self):
        import build_mage_fire as fire
        ref = fx_art.reference()
        self.assertEqual(ref['ball_1'], fire.BALL_HEAD + fire.BALL_TAIL[1])
        self.assertIs(ref['tall_2'], fire.TALL[2])
        self.assertIs(fx_art.smear, fire.smear)
        theirs = fire.sprites()
        for name, img in samples(RAMPS['fire']).items():
            if name.startswith('ember'):
                continue
            self.assertEqual(list(img.getchannel('A').getdata()), list(theirs[name].getchannel('A').getdata()), f'{name}: not the game sprite')

    def test_the_fire_bolt_set_is_in_the_style(self):
        for name, img in samples(RAMPS['fire']).items():
            self.assertEqual(check(img), [], name)
            self.assertEqual(check(img, RAMPS['fire']), [], name)

    def test_sprite_names_and_canvases_match_the_game(self):
        s = samples(RAMPS['fire'])
        self.assertEqual(sorted(s), sorted([f'ball_{k}' for k in range(3)] + [f'lick_{k}' for k in range(3)] + ['burst_0', 'burst_1']
                                           + [f'tall_{k}' for k in range(4)] + [f'ember_{k}' for k in range(5)]))
        self.assertEqual(s['ball_0'].size, (18, 18))
        self.assertEqual(s['tall_0'].size, (26, 26))
        self.assertEqual(s['burst_1'].size, (40, 40))
        self.assertEqual(s['ember_4'].size, (1, 1))
        for name in ('tall_0', 'lick_1'):
            bottom = [s[name].getpixel((x, s[name].height - 1))[3] for x in range(s[name].width)]
            self.assertIn(255, bottom, f'{name} must stand on the canvas bottom edge')

    def test_the_embers_cool_from_white_to_the_edge_colour(self):
        s = samples(RAMPS['fire'])
        self.assertEqual(s['ember_0'].getpixel((0, 0))[:3], RAMPS['fire']['w'])
        self.assertEqual(s['ember_4'].getpixel((0, 0))[:3], RAMPS['fire']['k'])


class PaletteSwapTest(unittest.TestCase):
    @needs_fire
    def test_another_element_is_the_same_drawing_in_its_ramp(self):
        fire, frost = samples(RAMPS['fire']), samples(RAMPS['frost'])
        for name in fire:
            self.assertEqual(fire[name].size, frost[name].size, name)
            self.assertEqual(list(fire[name].getchannel('A').getdata()), list(frost[name].getchannel('A').getdata()), f'{name}: shape changed')
            self.assertEqual(check(frost[name], RAMPS['frost']), [], name)

    def test_cool_steps_every_tone_down_and_keeps_the_shape(self):
        rows = ['.wp.', 'kyo.']
        self.assertEqual(cool(rows), ['.py.', 'kor.'])
        self.assertEqual(cool(rows, 5), ['.kk.', 'kkk.'])


class CheckTest(unittest.TestCase):
    def test_a_translucent_or_gradient_sprite_is_rejected(self):
        img = Image.new('RGBA', (8, 8))
        for x in range(8):
            img.putpixel((x, 4), (232, 116, 44, 32 * x))
        problems = check(img)
        self.assertTrue(any('alpha' in p for p in problems), problems)

    def test_a_colour_outside_the_ramps_is_rejected_and_named(self):
        img = art(['.ow.', 'rrr.'], RAMPS['fire'])
        img.putpixel((0, 0), (0, 255, 0, 255))
        problems = check(img)
        self.assertTrue(any('nearest fire' in p and '(0, 255, 0)' in p for p in problems), problems)

    def test_a_sprite_bigger_than_four_blocks_is_rejected(self):
        img = art(['w' * 65], RAMPS['fire'])
        self.assertTrue(any('bigger' in p for p in check(img)), check(img))

    def test_one_flat_colour_is_rejected_but_a_single_pixel_is_fine(self):
        self.assertTrue(any('flat' in p for p in check(art(['rr', 'rr'], RAMPS['fire']))))
        self.assertEqual(check(fx_art.pixel(RAMPS['sakura'], 'p')), [])

    def test_canvas_centres_or_stands(self):
        rows = ['.w.', 'ryr']
        centred, standing = canvas(rows, RAMPS['iron']), canvas(rows, RAMPS['iron'], standing=True)
        self.assertEqual(centred.size, (3, 3)); self.assertEqual(standing.size, (3, 3))
        self.assertEqual(centred.getpixel((1, 0))[3], 255)      # row 0 of a 2-high drawing lands on row 0 when centred in 3
        self.assertEqual(standing.getpixel((1, 0))[3], 0)       # standing: pushed to the bottom
        self.assertEqual(standing.getpixel((1, 2))[:3], RAMPS['iron']['y'])


class SheetTest(unittest.TestCase):
    def test_sheet_renders_every_ramp_without_touching_the_sprites(self):
        import tempfile, pathlib
        out = pathlib.Path(tempfile.mkdtemp()) / 'kit.png'
        rows = ['.w.', 'ryr', 'kok']
        page = fx_art.sheet([(n, {'a': canvas(rows, r), 'b': fx_art.pixel(r, 'p')}) for n, r in RAMPS.items()], out, scale=2)
        self.assertTrue(out.exists())
        self.assertEqual(page.width, 14)                       # 3 px + 1 px tiles at scale 2, with the gaps
        self.assertGreater(page.height, 7 * 10)                # one strip per ramp
        self.assertEqual(check(canvas(rows, RAMPS['fire'])), [])

    def test_check_with_no_paths_fails_instead_of_passing(self):
        import subprocess, sys, pathlib
        r = subprocess.run([sys.executable, str(pathlib.Path(fx_art.__file__)), '--check'], capture_output=True, text=True)
        self.assertEqual(r.returncode, 2, r.stdout)


if __name__ == '__main__':
    unittest.main()
