"""Tier-specific pixel bows: limb/cord/nock attachment and forward-only release."""
import json
import math
import unittest
import numpy as np
from PIL import Image
from build_bow_tiers import JOBS, PIXELS, FIRST, ASSETS, model_data, tier_key
from build_specialist_armament_pack import pose, motion, bow_points, arrow_offset
from build_pixel_armament_pack import definition
from pixel_weapon_display import grip_point, transformed, vertices
from preview_class_armaments import rotated


class BowTiersTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls): cls.data={tier:model_data(tier) for tier in JOBS}

    def test_originals_reproduce_disjoint_same_density_limb_and_grip_art(self):
        manifest=json.loads((PIXELS/'manifest.json').read_text())
        palette=json.loads((FIRST/'manifest.json').read_text())['weapons']['bow']['palette']
        for tier,(pixels,textures,entry,base,parts,anchors) in self.data.items():
            self.assertEqual(entry,manifest['tiers'][str(tier)])
            self.assertEqual(entry['content_size'][1],92)
            self.assertEqual(entry['height'],30)
            self.assertEqual(entry['palette'],palette)
            self.assertEqual(set(np.unique(pixels[:,:,3])),{0,255})
            np.testing.assert_array_equal(pixels,np.asarray(Image.open(PIXELS/f'{tier_key(tier)}.png')))
            summed=np.zeros_like(pixels,dtype=int)
            for name in ('upper_limb','grip','lower_limb'):
                array=textures[name]; summed+=array
                np.testing.assert_array_equal(array,np.asarray(Image.open(PIXELS/f'{tier_key(tier)}-{name}.png')))
                self.assertEqual((PIXELS/f'{tier_key(tier)}-{name}.png').read_bytes(),
                    (ASSETS/f'textures/item/weapons/pixel_{tier_key(tier)}_{name}.png').read_bytes())
                yy,_=np.nonzero(array[:,:,3])
                if name=='upper_limb': self.assertLess(yy.max(),entry['cuts'][0])
                elif name=='lower_limb': self.assertGreaterEqual(yy.min(),entry['cuts'][1])
                else:
                    self.assertGreaterEqual(yy.min(),entry['cuts'][0])
                    self.assertLess(yy.max(),entry['cuts'][1])
            np.testing.assert_array_equal(summed,pixels)

    def test_all_75_saved_native_poses_resources_and_thin_faces(self):
        count=0
        for tier,(_,textures,entry,base,parts,anchors) in self.data.items():
            key=tier_key(tier)
            self.assertEqual(json.loads((ASSETS/f'items/weapons/pixel_{key}.json').read_text()),definition(key))
            for stage,frames in (('rest',1),('idle',12),('prepare',6),('release',6)):
                for frame in range(frames):
                    suffix='' if stage=='rest' else f'_{stage}{frame:02}'
                    actual=json.loads((ASSETS/f'models/item/weapons/pixel_{key}{suffix}.json').read_text())
                    self.assertEqual(actual,pose('bow',base,parts,anchors,stage,frame))
                    self.assertEqual(actual['display'],base['display'])
                    self.assertEqual([e for e in actual['elements'] if e['name'].startswith('grip:')],parts['grip'])
                    for resource in actual['textures'].values():
                        self.assertTrue((ASSETS/f'textures/{resource.split(":")[1]}.png').is_file())
                    for e in actual['elements']:
                        self.assertLessEqual(len(e['faces']),2)
                        self.assertTrue(all(math.isfinite(a) and math.isfinite(b) and -16<=a<=b<=32
                            for a,b in zip(e['from'],e['to'])))
                        for f in e['faces'].values(): self.assertTrue(all(0<=v<=16 for v in f['uv']))
                        if 'rotation' in e: self.assertTrue(-45<=e['rotation']['angle']<=45)
                    count+=1
            self.assertEqual(pose('bow',base,parts,anchors,'prepare',5),pose('bow',base,parts,anchors,'release',0))
            self.assertEqual(pose('bow',base,parts,anchors,'release',5),pose('bow',base,parts,anchors))
        self.assertEqual(count,75)

    def test_cords_follow_both_painted_tips_and_nock_on_every_pose(self):
        for _,textures,entry,base,parts,anchors in self.data.values():
            draws=[]
            for stage,frames in (('rest',1),('idle',12),('prepare',6),('release',6)):
                for frame in range(frames):
                    model=pose('bow',base,parts,anchors,stage,frame)
                    tips,nock=bow_points(anchors,motion(stage,frame)[0])
                    if stage=='prepare': draws.append(nock[0])
                    for label,tip in zip(('upper','lower'),tips):
                        string=next(e for e in model['elements'] if e['name']=='string_'+label)
                        center=(np.array(string['from'])+string['to'])/2
                        low,high=center.copy(),center.copy()
                        low[1]=string['from'][1]; high[1]=string['to'][1]
                        ends=[rotated(p,string['rotation']) for p in (low,high)]
                        self.assertLess(min(np.linalg.norm(p-tip) for p in ends),1e-5)
                        self.assertLess(min(np.linalg.norm(p-nock) for p in ends),1e-5)
                        limb=next(e for e in model['elements'] if e['name'].startswith(label+'_limb:'))
                        np.testing.assert_allclose(rotated(np.array(anchors[label+'_tip']),limb.get('rotation')),tip,atol=1e-5)
            self.assertTrue(all(a>b for a,b in zip(draws,draws[1:])))
            self.assertGreater(draws[0]-draws[-1],4)

    def test_arrows_nock_then_travel_forward_and_disappear_without_rewinding(self):
        for _,textures,entry,base,parts,anchors in self.data.values():
            for frame in range(6):
                nock=bow_points(anchors,motion('prepare',frame)[0])[1]
                model=pose('bow',base,parts,anchors,'prepare',frame)
                arrows=[e for e in model['elements'] if e['name'].startswith('arrow:')]
                self.assertEqual(len(arrows),len(parts['arrow']))
                for a,b in zip(parts['arrow'],arrows):
                    for edge in ('from','to'): np.testing.assert_allclose(np.array(b[edge])-nock,a[edge],atol=1e-6)
            positions=[arrow_offset(anchors,'release',f) for f in range(3)]
            self.assertTrue(all(b[0]>a[0] for a,b in zip(positions,positions[1:])))
            for stage,frames in (('rest',range(1)),('idle',range(12)),('release',range(3,6))):
                for frame in frames:
                    self.assertIsNone(arrow_offset(anchors,stage,frame))
                    self.assertFalse(any(e['name'].startswith('arrow:') for e in pose('bow',base,parts,anchors,stage,frame)['elements']))

    def test_native_grips_direction_and_inventory_fit(self):
        for _,textures,entry,base,parts,anchors in self.data.values():
            grip=grip_point('bow',entry,textures)
            for context,target in (('firstperson',[1.13,3.2,-1.5]),('thirdperson',[0,2,1])):
                for hand,left in (('righthand',False),('lefthand',True)):
                    expected=[-target[0] if left else target[0],*target[1:]]
                    display=base['display'][context+'_'+hand]
                    np.testing.assert_allclose(transformed(grip,display,left),expected,atol=2e-6)
                    direction=transformed([9,8,8],display,left)-transformed([8,8,8],display,left)
                    np.testing.assert_allclose(direction/np.linalg.norm(direction),[0,0,-1],atol=1e-7)
            bounds=transformed(vertices(pose('bow',base,parts,anchors)['elements']),base['display']['gui'])
            lo,hi=bounds.min(0),bounds.max(0)
            np.testing.assert_allclose((lo+hi)/2,[0,0,0],atol=2e-6)
            self.assertLessEqual(max((hi-lo)[:2]),14+3e-5)

    def test_four_distinct_silhouettes_and_tier_bounds(self):
        arrays=[np.asarray(Image.open(FIRST/'bow.png'))[:,:,3]]
        arrays += [data[0][:,:,3] for data in self.data.values()]
        for i,a in enumerate(arrays):
            for b in arrays[i+1:]:
                h,w=max(a.shape[0],b.shape[0]),max(a.shape[1],b.shape[1])
                aa=np.zeros((h,w),dtype=np.uint8); bb=aa.copy()
                aa[:a.shape[0],:a.shape[1]]=a; bb[:b.shape[0],:b.shape[1]]=b
                self.assertGreater(np.count_nonzero(aa!=bb),30)
        self.assertEqual(tier_key(1),'bow')
        for tier in (0,5,-1):
            with self.assertRaises(ValueError): tier_key(tier)


if __name__=='__main__': unittest.main()
