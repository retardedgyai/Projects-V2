"""Structural checks only; not a MatE-quality or in-game acceptance gate."""
import hashlib
import json
import unittest
import numpy as np
from build_mage_cryopillar import PACK, SOURCE, CLIPS, mesh, profiles


class CryopillarTests(unittest.TestCase):
    def test_texture_is_unmodified_source(self):
        target=PACK/'assets/projects/textures/combat_vfx/mage_material/cryopillar_faces_v02.png'
        self.assertEqual(hashlib.sha256(SOURCE.read_bytes()).digest(),hashlib.sha256(target.read_bytes()).digest())

    def test_native_faces_remain_valid_and_have_depth(self):
        for clip in CLIPS:
            elements=mesh(clip)
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
            if clip in ('cryo_buttress','cryo_seed'):
                self.assertLess(spans[1]/spans[0],.5)
            else:
                self.assertGreater(spans[1]/spans[0],1 if clip=='cryo_root' else 2)

    def test_shipped_models_match_generator_and_are_registered(self):
        index=(PACK/'index.txt').read_text().splitlines()
        for clip in CLIPS:
            path=PACK/f'assets/projects/models/combat_vfx/mage_material/{clip}_0.json'
            self.assertEqual(mesh(clip),json.loads(path.read_text())['elements'])
            self.assertIn(path.relative_to(PACK).as_posix(),index)
            self.assertIn(f'assets/projects/items/combat_vfx/mage_material/{clip}_0.json',index)

    def test_uv_rectangles_stay_inside_painted_rows_not_checkerboard(self):
        rgb,panels=profiles()
        height,width=rgb.shape[:2]
        for clip in CLIPS:
            for element in mesh(clip):
                for face in element['faces'].values():
                    u0,v0,u1,v1=face['uv']
                    x0,x1=sorted((int(round(u0*width/16)),int(round(u1*width/16))))
                    y0,y1=int(round(v0*height/16)),int(round(v1*height/16))
                    # Test inclusive rectangle against interval mask: quiet
                    # white interiors are allowed, achromatic outside is not.
                    table={y:(a,b) for y,a,b in panels[0 if x0<width//2 else 1]}
                    for y in range(y0,y1+1):
                        lo,hi=table[y]
                        self.assertGreaterEqual(x0,lo)
                        self.assertLessEqual(x1,hi)

    def test_native_opposite_face_uv_winding_and_distinct_secondary_shape(self):
        for clip in CLIPS:
            for e in mesh(clip):
                for a,b in (('north','south'),('east','west')):
                    if a in e['faces'] and b in e['faces']:
                        ua,ub=e['faces'][a]['uv'],e['faces'][b]['uv']
                        self.assertEqual(ua,[ub[2],ub[1],ub[0],ub[3]])
        self.assertNotEqual(mesh('cryo_crown'),mesh('cryo_pillar'))
        self.assertEqual(len(mesh('cryo_crown')),2*len(mesh('cryo_pillar')))


if __name__=='__main__':
    unittest.main()
