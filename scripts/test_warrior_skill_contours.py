import hashlib
import json
import unittest
import numpy as np
from PIL import Image
from build_warrior_skill_contours import CLIPS, COUNTS, PACK, build, contour, tip


class WarriorContoursTest(unittest.TestCase):
    def test_hud_glyph_ink_is_identical_to_accepted_font_with_legal_padding(self):
        assets=PACK/'assets/projects'
        original=json.loads((assets/'font/core_menu_emphasis_y30.json').read_text())['providers'][1]
        current=json.loads((assets/'font/warrior_hud_status.json').read_text())['providers'][1]
        source=Image.open(assets/('textures/'+original['file'].split(':')[1])).convert('RGBA')
        atlas=Image.open(assets/('textures/'+current['file'].split(':')[1])).convert('RGBA')
        cw,ch=source.width//len(original['chars'][0]),source.height//len(original['chars'])
        self.assertLessEqual(current['ascent'],current['height'])
        self.assertEqual(cw,atlas.width//len(current['chars'][0]))
        for i,glyph in enumerate(current['chars'][0]):
            row=next(y for y,r in enumerate(original['chars']) if glyph in r)
            col=original['chars'][row].index(glyph)
            self.assertEqual(source.crop((col*cw,row*ch,(col+1)*cw,(row+1)*ch)).tobytes(),
                             atlas.crop((i*cw,0,(i+1)*cw,ch)).tobytes())
        self.assertIsNone(atlas.crop((0,ch,atlas.width,atlas.height)).getchannel('A').getbbox())

    def test_actual_shipped_output_is_reproducible(self):
        checked=[]
        def verify(path,value):
            self.assertEqual(value,json.loads(path.read_text(encoding='utf-8')),str(path))
            checked.append(path)
        build(PACK/'assets/projects',verify)
        self.assertEqual(1026,len(checked))

    def test_native_paths_have_different_silhouettes_not_only_different_names(self):
        signatures=[hashlib.sha256(b''.join(contour(c,'blade',f).tobytes() for f in range(11))).hexdigest() for c in CLIPS]
        self.assertEqual(len(CLIPS),len(set(signatures)))

    def test_each_stroke_crosses_then_independently_disappears(self):
        for c in CLIPS:
            frames=[contour(c,'blade',f) for f in range(11)]
            self.assertGreater(max(np.count_nonzero(f) for f in frames[3:7]),30,c)
            self.assertFalse(np.any(frames[-1]),c)
            self.assertFalse(np.any(contour(c,'wake',18)),c)
            self.assertGreater(np.count_nonzero(contour(c,'wake',9)),0,c)
            self.assertTrue(all(set(np.unique(f))<={0,1,2,3} for f in frames))

    def test_thrust_goes_forward_and_spin_turns_once(self):
        thrust=np.array([tip('thrust',i/10) for i in range(71)])
        self.assertTrue(np.all(np.diff(thrust[:,1])>=0))
        self.assertLess(np.ptp(thrust[:,0]),.3)
        for c in ('spin_a','spin_b','spin_c'):
            points=np.array([tip(c,i/100)-8 for i in range(701)])
            angles=np.unwrap(np.arctan2(points[:,0],points[:,1]))
            self.assertAlmostEqual(2*np.pi,angles[-1]-angles[0])

    def test_native_parts_remain_legal_and_bounded(self):
        for path in (PACK/'assets/projects/models/combat_vfx/warrior_skills').glob('*.json'):
            model=json.loads(path.read_text())
            self.assertLess(len(model['elements']),400,path.name)
            for e in model['elements']:
                self.assertEqual(e['from'][1],e['to'][1])
                self.assertEqual({'up','down'},set(e['faces']))
                self.assertTrue(all(-16<=v<=32 for v in e['from']+e['to']))
                if 'rotation' in e:
                    self.assertIn(e['rotation']['angle'],(-45,-22.5,0,22.5,45))
                    self.assertFalse(e['rotation']['rescale'])


if __name__=='__main__':
    unittest.main()
