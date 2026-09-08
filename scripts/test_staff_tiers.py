"""Original-specific painted head partitions, fixed roots and native Tier poses."""
import json
import math
import unittest
import numpy as np
from PIL import Image
from build_staff_tiers import JOBS, PIXELS, FIRST, ASSETS, model_data, tier_key
from build_pixel_armament_pack import definition, pose
from pixel_weapon_display import grip_point, transformed, vertices
from preview_class_armaments import rotated


class StaffTiersTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls): cls.data={tier:model_data(tier) for tier in JOBS}

    def test_originals_reproduce_same_density_palette_and_disjoint_gem(self):
        manifest=json.loads((PIXELS/'manifest.json').read_text())
        for tier,(pixels,textures,entry,base,gem) in self.data.items():
            self.assertEqual(entry,manifest['tiers'][str(tier)])
            self.assertEqual(entry['content_size'][1],92)
            self.assertEqual(entry['height'],30)
            self.assertEqual(set(np.unique(pixels[:,:,3])),{0,255})
            self.assertLessEqual(len(np.unique(pixels[pixels[:,:,3]>0,:3],axis=0)),12)
            np.testing.assert_array_equal(pixels,np.asarray(Image.open(PIXELS/f'{tier_key(tier)}.png')))
            body,jewel=textures['body'],textures['jewel']
            self.assertFalse(np.any((body[:,:,3]>0)&(jewel[:,:,3]>0)))
            np.testing.assert_array_equal(body.astype(int)+jewel,pixels)
            x0,y0,x1,y1=entry['jewel_box']
            self.assertFalse(np.any(body[y0:y1,x0:x1,3]))
            for name,array in textures.items():
                np.testing.assert_array_equal(array,np.asarray(Image.open(PIXELS/f'{tier_key(tier)}-{name}.png')))
                self.assertEqual((PIXELS/f'{tier_key(tier)}-{name}.png').read_bytes(),
                    (ASSETS/f'textures/item/weapons/pixel_{tier_key(tier)}_{name}.png').read_bytes())

    def test_each_head_partition_covers_exactly_its_source_pixels(self):
        for _,textures,entry,base,gem in self.data.values():
            h,w=textures['body'].shape[:2]
            coverage=np.zeros((h,w),dtype=int)
            for e in base['elements']:
                if not e['name'].startswith('fin_') or 'north' not in e['faces']: continue
                u1,v0,u0,v1=e['faces']['north']['uv']
                x0,x1=round(u0*w/16),round(u1*w/16)
                y0,y1=round(v0*h/16),round(v1*h/16)
                coverage[y0:y1,x0:x1]+=1
            expected=textures['body'][:,:,3]>0
            expected[entry['rows'][1]:]=False
            np.testing.assert_array_equal(coverage,expected.astype(int))

    def test_each_head_opens_outward_from_fixed_roots_and_recovers(self):
        for _,textures,entry,base,gem in self.data.values():
            peak=pose(base,gem,'staff','prepare',5)
            for name,angle,sign in (('fin_left',9,-1),('fin_right',-14,1)):
                rest=[e for e in base['elements'] if e['name'].startswith(name+':')]
                opened=[e for e in peak['elements'] if e['name'].startswith(name+':')]
                self.assertTrue(rest)
                for a,b in zip(rest,opened):
                    self.assertEqual(b['rotation']['angle'],angle)
                    self.assertEqual(a['rotation']['origin'],b['rotation']['origin'])
                    root=np.array(a['rotation']['origin'])
                    np.testing.assert_allclose(rotated(root,b['rotation']),root,atol=1e-10)
                    self.assertEqual(a['faces'],b['faces'])
                top=max(opened,key=lambda e:e['to'][1]); point=np.array(top['to'])
                self.assertGreater(sign*(rotated(point,top['rotation'])[0]-point[0]),.3)
            self.assertEqual(peak,pose(base,gem,'staff','release',0))
            self.assertEqual(pose(base,gem,'staff','release',5),pose(base,gem,'staff'))
            fixed=lambda m:[e for e in m['elements'] if e['name'].split(':')[0] in ('shaft','ferrule','fin_neck')]
            for stage,count in (('idle',12),('prepare',6),('release',6)):
                for frame in range(count): self.assertEqual(fixed(pose(base,gem,'staff',stage,frame)),fixed(base))

    def test_75_saved_poses_resources_native_bounds_and_hand_anchors(self):
        count=0
        for tier,(_,textures,entry,base,gem) in self.data.items():
            key=tier_key(tier)
            self.assertEqual(json.loads((ASSETS/f'items/weapons/pixel_{key}.json').read_text()),definition(key))
            grip=grip_point('staff',entry,textures)
            for context,target in (('firstperson',[1.13,3.2,-1.5]),('thirdperson',[0,2,1])):
                for hand,left in (('righthand',False),('lefthand',True)):
                    expected=[-target[0] if left else target[0],*target[1:]]
                    np.testing.assert_allclose(transformed(grip,base['display'][context+'_'+hand],left),expected,atol=2e-6)
            bounds=transformed(vertices(pose(base,gem,'staff')['elements']),base['display']['gui'])
            lo,hi=bounds.min(0),bounds.max(0)
            np.testing.assert_allclose((lo+hi)/2,[0,0,0],atol=2e-6)
            self.assertLessEqual(max((hi-lo)[:2]),14+3e-5)
            for stage,frames in (('rest',1),('idle',12),('prepare',6),('release',6)):
                for frame in range(frames):
                    suffix='' if stage=='rest' else f'_{stage}{frame:02}'
                    actual=json.loads((ASSETS/f'models/item/weapons/pixel_{key}{suffix}.json').read_text())
                    self.assertEqual(actual,pose(base,gem,'staff',stage,frame))
                    for resource in actual['textures'].values():
                        self.assertTrue((ASSETS/f'textures/{resource.split(":")[1]}.png').is_file())
                    for e in actual['elements']:
                        self.assertLessEqual(len(e['faces']),2)
                        self.assertTrue(all(math.isfinite(a) and math.isfinite(b) and -16<=a<=b<=32
                            for a,b in zip(e['from'],e['to'])))
                        for f in e['faces'].values(): self.assertTrue(all(0<=v<=16 for v in f['uv']))
                    count+=1
        self.assertEqual(count,75)

    def test_four_distinct_silhouettes_and_bounded_tiers(self):
        arrays=[np.asarray(Image.open(FIRST/'staff.png'))[:,:,3]]
        arrays += [data[0][:,:,3] for data in self.data.values()]
        for i,a in enumerate(arrays):
            for b in arrays[i+1:]:
                h,w=max(a.shape[0],b.shape[0]),max(a.shape[1],b.shape[1])
                aa=np.zeros((h,w),dtype=np.uint8); bb=aa.copy()
                aa[:a.shape[0],:a.shape[1]]=a; bb[:b.shape[0],:b.shape[1]]=b
                self.assertGreater(np.count_nonzero(aa!=bb),30)
        self.assertEqual(tier_key(1),'staff')
        for tier in (0,5,-1):
            with self.assertRaises(ValueError): tier_key(tier)


if __name__=='__main__': unittest.main()
