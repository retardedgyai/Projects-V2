"""Structural checks only; not a MatE-quality or in-game acceptance gate."""
import hashlib
import json
import unittest
import numpy as np
from build_mage_cryopillar import PACK, SOURCE, CLIPS, mesh, profiles


class CryopillarTests(unittest.TestCase):
    def test_texture_is_unmodified_source(self):
        target=PACK/'assets/projects/textures/combat_vfx/mage_material/cryopillar_faces_v01.png'
        self.assertEqual(hashlib.sha256(SOURCE.read_bytes()).digest(),hashlib.sha256(target.read_bytes()).digest())

    def test_native_faces_remain_valid_and_have_depth(self):
        for root in (False,True):
            elements=mesh(root)
            self.assertLess(len(elements),1000)
            self.assertGreater(len(elements),30)
            coords=[]
            for element in elements:
                coords.extend((element['from'],element['to']))
                self.assertTrue(all(-16<=v<=32 for c in (element['from'],element['to']) for v in c))
                self.assertTrue(all(a<=b for a,b in zip(element['from'],element['to'])))
                self.assertIn(element.get('rotation',{}).get('angle',0),(-45,-22.5,0,22.5,45))
                for face in element['faces'].values():
                    self.assertTrue(all(0<=v<=16 for v in face['uv']))
            spans=np.ptp(coords,axis=0)
            self.assertGreater(spans[2],1.5)
            self.assertGreater(spans[1]/spans[0],1 if root else 2)

    def test_shipped_models_match_generator_and_are_registered(self):
        index=(PACK/'index.txt').read_text().splitlines()
        for clip in CLIPS:
            path=PACK/f'assets/projects/models/combat_vfx/mage_material/{clip}_0.json'
            self.assertEqual(mesh(clip=='cryo_root'),json.loads(path.read_text())['elements'])
            self.assertIn(path.relative_to(PACK).as_posix(),index)
            self.assertIn(f'assets/projects/items/combat_vfx/mage_material/{clip}_0.json',index)

    def test_uv_rectangles_stay_inside_painted_rows_not_checkerboard(self):
        rgb,panels=profiles()
        height,width=rgb.shape[:2]
        for root in (False,True):
            for element in mesh(root):
                for face in element['faces'].values():
                    u0,v0,u1,v1=face['uv']
                    x0,x1=int(round(u0*width/16)),int(round(u1*width/16))
                    y0,y1=int(round(v0*height/16)),int(round(v1*height/16))
                    # Test inclusive rectangle against interval mask: quiet
                    # white interiors are allowed, achromatic outside is not.
                    table={y:(a,b) for y,a,b in panels[0 if x0<width//2 else 1]}
                    for y in range(y0,y1+1):
                        lo,hi=table[y]
                        self.assertGreaterEqual(x0,lo)
                        self.assertLessEqual(x1,hi)


if __name__=='__main__':
    unittest.main()
