"""Format, silhouette separation and native animation contracts; not art approval."""
import hashlib
import json
import math
import unittest
import zipfile
import numpy as np
from PIL import Image
from process_armament_art import JOBS, SOURCE, OUT as PIXELS, foreground, pixelize, reduce_palette
from build_pixel_armament_pack import ASSETS, OUT, PACK, geometry, pose, definition


class PixelArmamentTest(unittest.TestCase):
    def setUp(self):
        self.manifest=json.loads((PIXELS/'manifest.json').read_text())

    def test_source_originals_match_locked_hashes(self):
        for key,job in JOBS.items():
            digest=hashlib.sha256((SOURCE/job['source']).read_bytes()).hexdigest()
            self.assertEqual(digest,job['sha256'])
            self.assertEqual(digest,self.manifest['weapons'][key]['source_sha256'])

    def test_bright_checker_removed_without_erasing_dark_grip_or_colored_blade(self):
        pixels=np.array([[[240,240,240],[180,185,182],[25,20,40]],
                         [[220,220,220],[180,25,55],[95,220,240]]],dtype=np.uint8)
        _,mask=foreground(Image.fromarray(pixels))
        self.assertEqual(mask.tolist(),[[False,False,True],[False,True,True]])
        # Input with a real alpha channel must honor alpha rather than color.
        rgba=np.array([[[240,240,240,255],[20,20,20,0]]],dtype=np.uint8)
        _,mask=foreground(Image.fromarray(rgba))
        self.assertEqual(mask.tolist(),[[True,False]])

    def test_pooling_never_mixes_background_into_surface(self):
        source=np.full((20,20,3),220,dtype=np.uint8)
        source[2:18,5:15]=[120,20,50]
        result,info=pixelize(Image.fromarray(source),8)
        self.assertEqual(info['source_crop'],[5,2,15,18])
        self.assertEqual(np.unique(result[result[:,:,3]>0,:3],axis=0).tolist(),[[120,20,50]])
        self.assertEqual(set(np.unique(result[:,:,3])),{0,255})

    def test_exports_use_real_low_resolution_binary_alpha_and_limited_palette(self):
        for key,entry in self.manifest['weapons'].items():
            image=Image.open(PIXELS/f'{key}.png'); pixels=np.asarray(image)
            self.assertEqual(image.mode,'RGBA')
            self.assertEqual(image.size,tuple(entry['canvas_size']))
            self.assertLessEqual(max(image.size),128)
            self.assertEqual(set(np.unique(pixels[:,:,3])),{0,255})
            self.assertTrue(np.all(pixels[pixels[:,:,3]==0]==0))
            colors=np.unique(pixels[pixels[:,:,3]>0,:3],axis=0)
            self.assertLessEqual(len(colors),16 if key!='staff' else 12)
            self.assertAlmostEqual(entry['content_size'][1]/entry['height'],92/30,places=6)
            self.assertTrue(np.all(pixels[:2,:,3]==0) and np.all(pixels[:,:2,3]==0))

    def test_pixel_conversion_is_reproducible(self):
        arrays={}
        for key,job in JOBS.items():
            with Image.open(SOURCE/job['source']) as image: arrays[key]=pixelize(image,job['height'])[0]
        for family,count in (('crimson',16),('cyan',12)):
            keys=[k for k,j in JOBS.items() if j['family']==family]
            converted,palette=reduce_palette([arrays[k] for k in keys],count)
            self.assertEqual(palette,self.manifest['palettes'][family])
            for key,array in zip(keys,converted):
                np.testing.assert_array_equal(array,np.asarray(Image.open(PIXELS/f'{key}.png')))

    def test_detached_crystals_do_not_leave_a_second_painted_crystal(self):
        for key in JOBS:
            original=np.asarray(Image.open(PIXELS/f'{key}.png'))
            body=np.asarray(Image.open(PIXELS/f'{key}-body.png'))
            gem=np.asarray(Image.open(PIXELS/f'{key}-jewel.png'))
            mask=gem[:,:,3]>0
            self.assertTrue(mask.any())
            np.testing.assert_array_equal(gem[mask],original[mask])
            np.testing.assert_array_equal(body[~mask],original[~mask])
            if key=='staff': self.assertTrue(np.all(body[mask]==0))
            else: self.assertTrue(np.all(body[mask]==[48,35,54,255]))

    def test_revised_staff_moves_the_entire_crystal_region(self):
        entry=self.manifest['weapons']['staff']
        x0,y0,x1,y1=entry['jewel_box']
        body=np.asarray(Image.open(PIXELS/'staff-body.png'))
        jewel=np.asarray(Image.open(PIXELS/'staff-jewel.png'))
        self.assertTrue(np.all(body[y0:y1,x0:x1]==0))
        self.assertGreater(int((jewel[:,:,3]>0).sum()),30)
        self.assertGreaterEqual(entry['content_size'][0],25)
        original=np.asarray(Image.open(PIXELS/'staff.png'))
        np.testing.assert_array_equal(jewel[y0:y1,x0:x1],original[y0:y1,x0:x1])

    def test_every_exported_pose_is_native_bounded_and_returns_to_rest(self):
        for key,entry in self.manifest['weapons'].items():
            base,gem,_=geometry(key,entry)
            rest=pose(base,gem,key)
            self.assertEqual(pose(base,gem,key,'release',5),rest)
            self.assertEqual(pose(base,gem,key,'idle',0),rest)
            for stage,count in (('idle',12),('prepare',6),('release',6)):
                seen=set()
                for frame in range(count):
                    expected=pose(base,gem,key,stage,frame)
                    path=ASSETS/f'models/item/weapons/pixel_{key}_{stage}{frame:02d}.json'
                    exported=json.loads(path.read_text()); self.assertEqual(exported,expected)
                    self.assertEqual(exported['elements'][:len(base['elements'])],base['elements'])
                    seen.add(path.read_text())
                    for e in exported['elements']:
                        # Only broad front/back surfaces or boundary side quads, never six-face cubes.
                        self.assertLessEqual(len(e['faces']),2)
                        for a,b in zip(e['from'],e['to']):
                            self.assertTrue(math.isfinite(a) and math.isfinite(b) and -16<=a<=b<=32)
                        for f in e['faces'].values():
                            self.assertTrue(all(0<=v<=16 for v in f['uv']))
                            self.assertIn(f['texture'],('#body','#jewel'))
                self.assertGreater(len(seen),1)
            with self.assertRaises(ValueError): pose(base,gem,key,'idle',12)

    def test_pack_is_complete_and_does_not_change_live_server_pack(self):
        expected=set()
        for key in JOBS:
            item=json.loads((ASSETS/f'items/weapons/pixel_{key}.json').read_text())
            self.assertEqual(item,definition(key))
            branch=item['model']['cases'][0]['model']
            self.assertEqual([e['threshold'] for e in branch['entries']],list(range(25)))
            for leaf in [branch['fallback'],*[e['model'] for e in branch['entries']]]:
                resource=leaf['model'].split(':')[1]
                self.assertTrue((ASSETS/f'models/{resource}.json').exists())
            for part in ('body','jewel'):
                self.assertEqual((ASSETS/f'textures/item/weapons/pixel_{key}_{part}.png').read_bytes(),
                                 (PIXELS/f'{key}-{part}.png').read_bytes())
        with zipfile.ZipFile(OUT/'projects-pixel-armaments-review.zip') as archive:
            self.assertIsNone(archive.testzip())
            self.assertEqual(set(archive.namelist()),{p.relative_to(PACK).as_posix() for p in PACK.rglob('*') if p.is_file()})
            self.assertFalse(any(n.startswith('assets/minecraft/') for n in archive.namelist()))
        root=SOURCE.parents[2]
        index=(root/'server-minestom/src/main/resources/core-ui-pack/index.txt').read_text()
        for key in JOBS: self.assertNotIn(f'pixel_{key}',index)


if __name__=='__main__': unittest.main()
