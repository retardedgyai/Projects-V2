import json
import unittest
from PIL import Image
from render_warrior_hud_preview import HudRenderer, ASSETS


class WarriorHudPreviewTest(unittest.TestCase):
    def test_approved_status_ink_stays_above_the_actual_skill_sprite(self):
        renderer = HudRenderer()
        font = json.loads((ASSETS/'font/warrior_hud_status.json').read_text())
        code = ord(font['providers'][1]['chars'][0][0])
        snapshot = {'state':'guard', 'netAdvance':0, 'glyphs':[
            {'font':'projects:core_hud','code':0xE400,'x':-88,'advance':33,'color':0xffffff},
            {'font':'projects:warrior_hud_status','code':code,'x':-30,'advance':14,'color':0xffffff}]}
        _, report = renderer.render(snapshot)
        self.assertEqual(2,len(report['glyphs']))
        icon, caption = [g['inkBounds'] for g in report['glyphs']]
        self.assertLessEqual(caption[3],icon[1])
        self.assertIn('world',report['omitted'])
        self.assertEqual((96,96),renderer.glyph('projects:core_hud',0xE400)[0].size)
        self.assertEqual((96,96),renderer.glyph('projects:core_hud',0xE415)[0].size)
        provider=font['providers'][1]
        with Image.open(ASSETS/('textures/'+provider['file'].split(':')[1])) as atlas:
            # GUI-scale-3 caption must retain every source pixel, not resample
            # through an intermediate 14px Japanese glyph.
            expected=atlas.convert('RGBA').crop((0,0,42,144))
        self.assertEqual(expected.tobytes(),renderer.glyph('projects:warrior_hud_status',code)[0].tobytes())

    def test_missing_glyph_is_an_error_and_spacing_draws_no_fake_text(self):
        renderer = HudRenderer()
        self.assertEqual((None,0),renderer.glyph('projects:core_spacing',0xE100))
        with self.assertRaises(ValueError):
            renderer.glyph('projects:core_hud',ord('あ'))
        with self.assertRaises(ValueError):
            renderer.glyph('minecraft:default',ord('あ'))


if __name__=='__main__':
    unittest.main()
