"""Crossed painted fins: source coverage, side presence and fixed handle."""
import json
import unittest
import numpy as np
from build_specialist_armament_pack import SOURCE, ASSETS, geometry, pose
from pixel_weapon_display import vertices


class MaceFlangedHeadTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        entry=json.loads((SOURCE/'manifest.json').read_text())['weapons']['mace']
        cls.base,cls.parts,cls.textures,cls.anchors=geometry('mace',entry)

    def test_each_panel_contains_only_its_original_head_or_shaft_pixels(self):
        body=self.textures['body']; h,w=body.shape[:2]
        yy,_=np.indices((h,w)); opaque=body[:,:,3]>0
        for group,expected in (('flange_a',opaque & (yy<35)),('flange_b',opaque & (yy<35)),
                               ('shaft',opaque & (yy>=35))):
            coverage=np.zeros((h,w),dtype=int)
            selected=[e for e in self.parts['body'] if e['name'].startswith('body:'+group+':')]
            self.assertTrue(selected)
            for e in selected:
                if 'south' not in e['faces']: continue
                u0,v0,u1,v1=e['faces']['south']['uv']
                x0,x1=round(min(u0,u1)*w/16),round(max(u0,u1)*w/16)
                y0,y1=round(min(v0,v1)*h/16),round(max(v0,v1)*h/16)
                coverage[y0:y1,x0:x1]+=1
            np.testing.assert_array_equal(coverage,expected.astype(int),err_msg=group)

    def test_head_has_four_radial_fins_without_filled_blocks(self):
        head=[e for e in self.parts['body'] if e['name'].startswith('body:flange_')]
        self.assertEqual({e['rotation']['angle'] for e in head},{-45,45})
        for e in head:
            self.assertEqual(e['rotation']['axis'],'y')
            self.assertLessEqual(len(e['faces']),2)
            self.assertLessEqual(e['to'][2]-e['from'][2],.180001)
        corners=vertices(head)
        span=corners.max(0)-corners.min(0)
        self.assertGreater(span[0],5)
        self.assertGreater(span[2],5)  # Old entire head was only 0.7 units deep.
        self.assertAlmostEqual(span[0],span[2],places=4)

    def test_head_and_handle_stay_fixed_while_original_crystal_animates(self):
        for stage,count in (('rest',1),('idle',12),('prepare',6),('release',6)):
            for frame in range(count):
                model=pose('mace',self.base,self.parts,self.anchors,stage,frame)
                self.assertEqual([e for e in model['elements'] if e['name'].startswith('body:')],self.parts['body'])
                suffix='' if stage=='rest' else f'_{stage}{frame:02}'
                self.assertEqual(model,json.loads((ASSETS/f'models/item/weapons/pixel_mace{suffix}.json').read_text()))
        self.assertNotEqual(pose('mace',self.base,self.parts,self.anchors),
                            pose('mace',self.base,self.parts,self.anchors,'prepare',5))


if __name__=='__main__': unittest.main()
