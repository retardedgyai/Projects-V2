"""Four different painted silhouettes, thin native poses, and fixed equipment IDs."""
import hashlib
import json
import math
import unittest
import numpy as np
from PIL import Image
from build_greatsword_tiers import JOBS, SOURCE, PIXELS, FIRST, ASSETS, convert, model_data, tier_key
from build_pixel_armament_pack import pose, definition
from pixel_weapon_display import transformed, vertices, grip_point


class GreatswordTiersTest(unittest.TestCase):
    def test_source_hashes_and_processed_layers_reproduce(self):
        manifest=json.loads((PIXELS/'manifest.json').read_text())
        for tier,job in JOBS.items():
            self.assertEqual(hashlib.sha256((SOURCE/job['source']).read_bytes()).hexdigest(),job['sha256'])
            pixels,textures,entry=convert(tier)
            self.assertEqual(entry,manifest['tiers'][str(tier)])
            np.testing.assert_array_equal(pixels,np.asarray(Image.open(PIXELS/f'{tier_key(tier)}.png')))
            self.assertLessEqual(len(np.unique(pixels[pixels[:,:,3]>0,:3],axis=0)),16)
            self.assertEqual(set(np.unique(pixels[:,:,3])),{0,255})
            self.assertEqual(entry['height'],30)
            self.assertEqual(entry['content_size'][1],92)
            self.assertLessEqual(max(entry['canvas_size']),128)
            for part,array in textures.items():
                np.testing.assert_array_equal(array,np.asarray(Image.open(PIXELS/f'{tier_key(tier)}-{part}.png')))
                self.assertEqual((PIXELS/f'{tier_key(tier)}-{part}.png').read_bytes(),
                    (ASSETS/f'textures/item/weapons/pixel_{tier_key(tier)}_{part}.png').read_bytes())

    def test_tiers_have_different_alpha_silhouettes_not_only_different_colors(self):
        silhouettes=[np.asarray(Image.open(FIRST/'greatsword.png'))[:,:,3]]
        for tier in JOBS: silhouettes.append(convert(tier)[0][:,:,3])
        for i,a in enumerate(silhouettes):
            for b in silhouettes[i+1:]:
                h,w=max(a.shape[0],b.shape[0]),max(a.shape[1],b.shape[1])
                aa=np.zeros((h,w),dtype=np.uint8); bb=aa.copy()
                aa[:a.shape[0],:a.shape[1]]=a; bb[:b.shape[0],:b.shape[1]]=b
                self.assertGreater(np.count_nonzero(aa!=bb),30)

    def test_all_75_native_models_and_pose_graphs_match_without_blocks(self):
        count=0
        for tier in JOBS:
            _,textures,entry,base,gem=model_data(tier)
            key=tier_key(tier)
            self.assertEqual(json.loads((ASSETS/f'items/weapons/pixel_{key}.json').read_text()),definition(key))
            for stage,frames in (('rest',1),('idle',12),('prepare',6),('release',6)):
                for frame in range(frames):
                    suffix='' if stage=='rest' else f'_{stage}{frame:02}'
                    actual=json.loads((ASSETS/f'models/item/weapons/pixel_{key}{suffix}.json').read_text())
                    self.assertEqual(actual,pose(base,gem,'greatsword',stage,frame))
                    for e in actual['elements']:
                        self.assertLessEqual(len(e['faces']),2)
                        self.assertTrue(all(math.isfinite(a) and math.isfinite(b) and -16<=a<=b<=32
                            for a,b in zip(e['from'],e['to'])))
                        for face in e['faces'].values(): self.assertTrue(all(0<=v<=16 for v in face['uv']))
                    count+=1
            self.assertEqual(pose(base,gem,'greatsword','release',5),pose(base,gem,'greatsword'))
        self.assertEqual(count,75)

    def test_each_tier_is_grip_anchored_and_gui_fitted(self):
        for tier in JOBS:
            _,textures,entry,base,gem=model_data(tier)
            grip=grip_point('greatsword',entry,textures)
            for hand,left in (('righthand',False),('lefthand',True)):
                np.testing.assert_allclose(transformed(grip,base['display']['firstperson_'+hand],left),
                    [-1.13 if left else 1.13,3.2,-1.5],atol=2e-6)
            bounds=transformed(vertices(pose(base,gem,'greatsword')['elements']),base['display']['gui'])
            lo,hi=bounds.min(0),bounds.max(0)
            np.testing.assert_allclose((lo+hi)/2,[0,0,0],atol=2e-6)
            self.assertLessEqual(max((hi-lo)[:2]),14+3e-5)

    def test_tier_keys_are_bounded_and_keep_t1_original(self):
        self.assertEqual(tier_key(1),'greatsword')
        for tier in (0,5,-1):
            with self.assertRaises(ValueError): tier_key(tier)


if __name__=='__main__': unittest.main()
