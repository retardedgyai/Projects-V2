"""Tier silhouettes, original pixel density, thin native geometry and pose wiring."""
import json
import math
import unittest
import numpy as np
from PIL import Image
from build_dagger_tiers import JOBS, PIXELS, FIRST, ASSETS, convert, model_data, tier_key
from build_pixel_armament_pack import definition, pose
from pixel_weapon_display import grip_point, transformed, vertices


class DaggerTiersTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls): cls.data={tier:model_data(tier) for tier in JOBS}

    def test_preserved_sources_reproduce_same_density_crimson_layers(self):
        manifest=json.loads((PIXELS/'manifest.json').read_text())
        for tier,(pixels,textures,entry,base,gem) in self.data.items():
            self.assertEqual(entry,manifest['tiers'][str(tier)])
            self.assertEqual(entry['content_size'][1],60)
            self.assertAlmostEqual(entry['height']/60,30/92)
            self.assertLessEqual(max(entry['canvas_size']),64)
            self.assertEqual(set(np.unique(pixels[:,:,3])),{0,255})
            self.assertLessEqual(len(np.unique(pixels[pixels[:,:,3]>0,:3],axis=0)),16)
            np.testing.assert_array_equal(pixels,np.asarray(Image.open(PIXELS/f'{tier_key(tier)}.png')))
            for name,array in textures.items():
                np.testing.assert_array_equal(array,np.asarray(Image.open(PIXELS/f'{tier_key(tier)}-{name}.png')))
                self.assertEqual((PIXELS/f'{tier_key(tier)}-{name}.png').read_bytes(),
                    (ASSETS/f'textures/item/weapons/pixel_{tier_key(tier)}_{name}.png').read_bytes())

    def test_all_four_tiers_have_different_silhouettes_not_recolors(self):
        arrays=[np.asarray(Image.open(FIRST/'dagger.png'))[:,:,3]]
        arrays += [data[0][:,:,3] for data in self.data.values()]
        for i,a in enumerate(arrays):
            for b in arrays[i+1:]:
                h,w=max(a.shape[0],b.shape[0]),max(a.shape[1],b.shape[1])
                aa=np.zeros((h,w),dtype=np.uint8); bb=aa.copy()
                aa[:a.shape[0],:a.shape[1]]=a; bb[:b.shape[0],:b.shape[1]]=b
                self.assertGreater(np.count_nonzero(aa!=bb),30)

    def test_all_75_saved_poses_use_their_tier_art_without_cubes_or_missing_resources(self):
        count=0
        for tier,(_,textures,entry,base,gem) in self.data.items():
            key=tier_key(tier)
            self.assertEqual(json.loads((ASSETS/f'items/weapons/pixel_{key}.json').read_text()),definition(key))
            for stage,frames in (('rest',1),('idle',12),('prepare',6),('release',6)):
                for frame in range(frames):
                    suffix='' if stage=='rest' else f'_{stage}{frame:02}'
                    actual=json.loads((ASSETS/f'models/item/weapons/pixel_{key}{suffix}.json').read_text())
                    self.assertEqual(actual,pose(base,gem,'dagger',stage,frame))
                    for resource in actual['textures'].values():
                        self.assertTrue((ASSETS/f'textures/{resource.split(":")[1]}.png').is_file())
                    for e in actual['elements']:
                        self.assertLessEqual(len(e['faces']),2)
                        self.assertTrue(all(math.isfinite(a) and math.isfinite(b) and -16<=a<=b<=32
                            for a,b in zip(e['from'],e['to'])))
                        for f in e['faces'].values(): self.assertTrue(all(0<=v<=16 for v in f['uv']))
                    count+=1
            self.assertEqual(pose(base,gem,'dagger','release',5),pose(base,gem,'dagger'))
        self.assertEqual(count,75)

    def test_grips_and_gui_fit_hold_for_every_tier(self):
        for _,textures,entry,base,gem in self.data.values():
            grip=grip_point('dagger',entry,textures)
            for context,target in (('firstperson',[1.13,3.2,-1.5]),('thirdperson',[0,2,1])):
                for hand,left in (('righthand',False),('lefthand',True)):
                    expected=[-target[0] if left else target[0],*target[1:]]
                    np.testing.assert_allclose(transformed(grip,base['display'][context+'_'+hand],left),expected,atol=2e-6)
            bounds=transformed(vertices(pose(base,gem,'dagger')['elements']),base['display']['gui'])
            lo,hi=bounds.min(0),bounds.max(0)
            np.testing.assert_allclose((lo+hi)/2,[0,0,0],atol=2e-6)
            self.assertLessEqual(max((hi-lo)[:2]),14+3e-5)
        self.assertEqual(tier_key(1),'dagger')
        for tier in (0,5,-1):
            with self.assertRaises(ValueError): tier_key(tier)


if __name__=='__main__': unittest.main()
