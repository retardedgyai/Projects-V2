"""Mace Tier art, crossed alpha silhouettes, crystal motion and native placement."""
import json
import math
import unittest
import numpy as np
from PIL import Image
from build_mace_tiers import JOBS, PIXELS, FIRST, ASSETS, model_data, tier_key
from build_specialist_armament_pack import pose
from build_pixel_armament_pack import definition
from pixel_weapon_display import grip_pixels, grip_point, transformed, vertices


class MaceTiersTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls): cls.data={tier:model_data(tier) for tier in JOBS}

    def test_preserved_originals_reproduce_same_density_palette_and_separate_crystal(self):
        manifest=json.loads((PIXELS/'manifest.json').read_text())
        palette=json.loads((FIRST/'manifest.json').read_text())['weapons']['mace']['palette']
        for tier,(pixels,textures,entry,base,parts,anchors) in self.data.items():
            self.assertEqual(entry,manifest['tiers'][str(tier)])
            self.assertEqual(entry['content_size'][1],84)
            self.assertAlmostEqual(entry['height']/84,30/92)
            self.assertEqual(entry['palette'],palette)
            self.assertEqual(set(np.unique(pixels[:,:,3])),{0,255})
            self.assertLessEqual(len(np.unique(pixels[pixels[:,:,3]>0,:3],axis=0)),16)
            np.testing.assert_array_equal(pixels,np.asarray(Image.open(PIXELS/f'{tier_key(tier)}.png')))
            body,crystal=textures['body'],textures['crystal']
            self.assertFalse(np.any((body[:,:,3]>0)&(crystal[:,:,3]>0)))
            np.testing.assert_array_equal(body.astype(int)+crystal,pixels)
            x0,y0,x1,y1=entry['crystal_box']
            self.assertFalse(np.any(body[y0:y1,x0:x1,3]))
            for name,array in textures.items():
                np.testing.assert_array_equal(array,np.asarray(Image.open(PIXELS/f'{tier_key(tier)}-{name}.png')))
                self.assertEqual((PIXELS/f'{tier_key(tier)}-{name}.png').read_bytes(),
                    (ASSETS/f'textures/item/weapons/pixel_{tier_key(tier)}_{name}.png').read_bytes())

    def test_each_head_panel_and_single_shaft_cover_only_the_original_mask(self):
        for _,textures,entry,base,parts,anchors in self.data.values():
            body=textures['body']; h,w=body.shape[:2]
            yy,_=np.indices((h,w)); opaque=body[:,:,3]>0
            for group,expected in (('flange_a',opaque&(yy<entry['head_bottom'])),
                                   ('flange_b',opaque&(yy<entry['head_bottom'])),
                                   ('shaft',opaque&(yy>=entry['head_bottom']))):
                coverage=np.zeros((h,w),dtype=int)
                selected=[e for e in parts['body'] if e['name'].startswith('body:'+group+':')]
                self.assertTrue(selected)
                for e in selected:
                    if 'south' not in e['faces']: continue
                    u0,v0,u1,v1=e['faces']['south']['uv']
                    x0,x1=round(min(u0,u1)*w/16),round(max(u0,u1)*w/16)
                    y0,y1=round(min(v0,v1)*h/16),round(max(v0,v1)*h/16)
                    coverage[y0:y1,x0:x1]+=1
                np.testing.assert_array_equal(coverage,expected.astype(int),err_msg=group)
            head=[e for e in parts['body'] if e['name'].startswith('body:flange_')]
            self.assertEqual({e['rotation']['angle'] for e in head},{-45,45})
            self.assertTrue(all(e['rotation']['axis']=='y' and e['to'][2]-e['from'][2]<=.180001 for e in head))
            corners=vertices(head); span=corners.max(0)-corners.min(0)
            self.assertGreater(span[0],3)
            self.assertAlmostEqual(span[0],span[2],places=4)

    def test_75_saved_native_poses_animate_only_the_crystal_and_recover(self):
        count=0
        for tier,(_,textures,entry,base,parts,anchors) in self.data.items():
            key=tier_key(tier)
            self.assertEqual(json.loads((ASSETS/f'items/weapons/pixel_{key}.json').read_text()),definition(key))
            for stage,frames in (('rest',1),('idle',12),('prepare',6),('release',6)):
                for frame in range(frames):
                    suffix='' if stage=='rest' else f'_{stage}{frame:02}'
                    actual=json.loads((ASSETS/f'models/item/weapons/pixel_{key}{suffix}.json').read_text())
                    self.assertEqual(actual,pose('mace',base,parts,anchors,stage,frame))
                    self.assertEqual([e for e in actual['elements'] if e['name'].startswith('body:')],parts['body'])
                    self.assertEqual(actual['display'],base['display'])
                    for resource in actual['textures'].values():
                        self.assertTrue((ASSETS/f'textures/{resource.split(":")[1]}.png').is_file())
                    for e in actual['elements']:
                        self.assertLessEqual(len(e['faces']),2)
                        self.assertTrue(all(math.isfinite(a) and math.isfinite(b) and -16<=a<=b<=32
                            for a,b in zip(e['from'],e['to'])))
                        for face in e['faces'].values(): self.assertTrue(all(0<=v<=16 for v in face['uv']))
                    count+=1
            rest=pose('mace',base,parts,anchors)
            peak=pose('mace',base,parts,anchors,'prepare',5)
            self.assertNotEqual(rest,peak)
            self.assertEqual(peak,pose('mace',base,parts,anchors,'release',0))
            self.assertEqual(rest,pose('mace',base,parts,anchors,'release',5))
            crystals=[e for e in peak['elements'] if e['name'].startswith('crystal:')]
            for a,b in zip(parts['crystal'],crystals):
                np.testing.assert_allclose(np.array(b['from'])-a['from'],[0,0,-.5],atol=1e-6)
                self.assertEqual(b['rotation']['angle'],35)
        self.assertEqual(count,75)

    def test_original_specific_grips_and_inventory_fit(self):
        for _,textures,entry,base,parts,anchors in self.data.values():
            px,py=grip_pixels('mace',entry,textures)
            self.assertGreaterEqual(py,entry['grip_rows'][0])
            self.assertLess(py,entry['grip_rows'][1])
            self.assertGreater(textures['body'][int(py),int(px),3],0)
            grip=grip_point('mace',entry,textures)
            for context,target in (('firstperson',[1.13,3.2,-1.5]),('thirdperson',[0,2,1])):
                for hand,left in (('righthand',False),('lefthand',True)):
                    expected=[-target[0] if left else target[0],*target[1:]]
                    np.testing.assert_allclose(transformed(grip,base['display'][context+'_'+hand],left),expected,atol=2e-6)
            bounds=transformed(vertices(pose('mace',base,parts,anchors)['elements']),base['display']['gui'])
            lo,hi=bounds.min(0),bounds.max(0)
            np.testing.assert_allclose((lo+hi)/2,[0,0,0],atol=2e-6)
            self.assertLessEqual(max((hi-lo)[:2]),14+3e-5)

    def test_four_different_silhouettes_not_recolors(self):
        arrays=[np.asarray(Image.open(FIRST/'mace.png'))[:,:,3]]
        arrays += [data[0][:,:,3] for data in self.data.values()]
        for i,a in enumerate(arrays):
            for b in arrays[i+1:]:
                h,w=max(a.shape[0],b.shape[0]),max(a.shape[1],b.shape[1])
                aa=np.zeros((h,w),dtype=np.uint8); bb=aa.copy()
                aa[:a.shape[0],:a.shape[1]]=a; bb[:b.shape[0],:b.shape[1]]=b
                self.assertGreater(np.count_nonzero(aa!=bb),30)
        self.assertEqual(tier_key(1),'mace')
        for tier in (0,5,-1):
            with self.assertRaises(ValueError): tier_key(tier)


if __name__=='__main__': unittest.main()
