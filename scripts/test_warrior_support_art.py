import json
import math
import unittest
import numpy as np
from PIL import Image
from build_warrior_support_art import (PACK, WIND_FRAMES, VOICE_FRAMES, build,
                                       cloth_pixels, standard, voice, guard_elements)
from process_sword_material_redraw import convert


class WarriorSupportArtTest(unittest.TestCase):
    def test_compiled_art_is_reproducible_and_every_texture_is_resolved(self):
        checked=[]
        assets=PACK/'assets/projects'
        def check(path,value):
            self.assertEqual(value,json.loads(path.read_text(encoding='utf-8')),str(path))
            if 'textures' in value:
                for texture in value['textures'].values():
                    self.assertTrue(texture.startswith('projects:'),texture)
                    self.assertTrue((assets/('textures/'+texture.split(':')[1]+'.png')).is_file(),texture)
            for element in value.get('elements',[]):
                for a,b in zip(element['from'],element['to']):
                    self.assertLessEqual(a,b)
                    self.assertGreaterEqual(a,-16)
                    self.assertLessEqual(b,32)
                self.assertGreaterEqual(sum(a<b for a,b in zip(element['from'],element['to'])),2)
                if 'rotation' in element:
                    self.assertIn(element['rotation']['angle'],(-45,-22.5,0,22.5,45))
            checked.append(path)
        build(assets,check)
        self.assertEqual(504,len(checked))

    def test_guard_keeps_exact_approved_pixels_and_painted_grip(self):
        pixels,_,_=convert()
        packed=np.array(Image.open(PACK/'assets/projects/textures/combat_vfx/warrior_support/guard.png'))
        np.testing.assert_array_equal(pixels,packed)
        self.assertEqual(255,packed[63,10,3])
        elements=guard_elements(pixels,0)
        self.assertTrue(any(e['from'][0]<=8<=e['to'][0] and e['from'][2]<=0<=e['to'][2] for e in elements))
        self.assertTrue(all(e['to'][1]-e['from'][1]<=.5 for e in elements))
        self.assertEqual([],guard_elements(pixels,7))

    def test_cloth_has_real_alpha_and_contiguous_folded_rows(self):
        pixels=np.array(cloth_pixels())
        self.assertEqual((80,48,4),pixels.shape)
        self.assertEqual(0,pixels[-1,24,3],'Swallowtail negative space must be transparent')
        self.assertGreater(np.count_nonzero(pixels[:,:,3]>128),2300)
        def end(e,top):
            lo,hi=e['from'],e['to']
            point=np.array([(lo[0]+hi[0])/2,hi[1] if top else lo[1],lo[2]])
            r=e.get('rotation')
            if r:
                o=np.array(r['origin']);p=point-o;a=math.radians(r['angle'])
                point=o+np.array([p[0],p[1]*math.cos(a)-p[2]*math.sin(a),p[1]*math.sin(a)+p[2]*math.cos(a)])
            return point
        for frame in range(1,4+WIND_FRAMES):
            rows=standard(frame,0)[4::12]
            np.testing.assert_allclose(end(rows[0],True)[1:],[27,8],atol=1e-10)
            for a,b in zip(rows,rows[1:]):
                np.testing.assert_allclose(end(a,False),end(b,True),atol=1e-10)
            self.assertEqual(standard(3,0)[0],standard(frame,0)[0])
        self.assertEqual(standard(3,0),standard(4,0))
        self.assertEqual([],standard(10,7))

    def test_voice_fronts_are_distinct_deforming_open_and_fully_dissolve(self):
        sequences=[]
        for variant in range(3):
            frames=[voice(variant,f) for f in range(VOICE_FRAMES)]
            self.assertGreaterEqual(len({f.tobytes() for f in frames}),14)
            self.assertTrue(all(not np.any(f[25:39,25:39]) for f in frames))
            self.assertGreater(np.count_nonzero(frames[3]),100)
            self.assertLess(np.count_nonzero(frames[16]),np.count_nonzero(frames[3])*.4)
            self.assertFalse(np.any(frames[-1]))
            sequences.append(b''.join(f.tobytes() for f in frames))
        self.assertEqual(3,len(set(sequences)))


if __name__=='__main__':
    unittest.main()
