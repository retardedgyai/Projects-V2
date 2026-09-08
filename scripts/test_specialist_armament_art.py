"""Pixel/source contracts for the second batch; not game-side art acceptance."""
import hashlib
import json
import unittest
import numpy as np
from PIL import Image
from process_specialist_armament_art import JOBS, SOURCE, OUT, convert, extract, region


class SpecialistArtTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.manifest = json.loads((OUT/'manifest.json').read_text())

    def test_originals_are_preserved_and_conversion_reproduces_every_layer(self):
        for key,job in JOBS.items():
            with self.subTest(key=key):
                self.assertEqual(hashlib.sha256((SOURCE/job['source']).read_bytes()).hexdigest(),job['sha256'])
                pixels,parts,entry = convert(key)
                self.assertEqual(entry,self.manifest['weapons'][key])
                np.testing.assert_array_equal(pixels,np.asarray(Image.open(OUT/f'{key}.png')))
                for name,array in parts.items():
                    np.testing.assert_array_equal(array,np.asarray(Image.open(OUT/f'{key}-{name}.png')))

    def test_pale_materials_survive_without_neutral_checker(self):
        colors = np.array([[[252,234,191],[200,184,243],[39,40,46]],
                           [[125,125,125],[190,190,190],[245,244,246]]],dtype=np.uint8)
        image = np.asarray(extract(Image.fromarray(colors)))
        self.assertEqual(image[:,:,3].tolist(),[[255,255,255],[0,0,0]])
        np.testing.assert_array_equal(image[0,:,:3],colors[0])
        self.assertTrue((image[1]==0).all())
        with self.assertRaises(ValueError): extract(Image.new('RGBA',(8,8)))

    def test_real_low_resolution_and_shared_physical_texel_density(self):
        for key,entry in self.manifest['weapons'].items():
            image = Image.open(OUT/f'{key}.png'); pixels = np.asarray(image)
            self.assertEqual(image.mode,'RGBA')
            self.assertEqual(image.size,tuple(entry['canvas_size']))
            self.assertLessEqual(max(image.size),128)
            self.assertEqual(set(np.unique(pixels[:,:,3])),{0,255})
            self.assertTrue((pixels[pixels[:,:,3]==0]==0).all())
            self.assertLessEqual(len(np.unique(pixels[pixels[:,:,3]>0,:3],axis=0)),16)
            self.assertAlmostEqual(entry['content_size'][1]/entry['height'],92/30,places=6)
            self.assertTrue((pixels[:2]==0).all() and (pixels[:,:2]==0).all())

    def test_articulation_layers_reconstruct_art_without_duplicate_motifs(self):
        for key,entry in self.manifest['weapons'].items():
            original = np.asarray(Image.open(OUT/f'{key}.png'))
            combined = np.zeros_like(original)
            coverage = np.zeros(original.shape[:2],dtype=int)
            for name in entry['parts']:
                if name=='page_backing': continue
                part = np.asarray(Image.open(OUT/f'{key}-{name}.png'))
                visible = part[:,:,3]>0
                self.assertEqual(int(visible.sum()),entry['parts'][name])
                self.assertGreater(int(visible.sum()),0)
                coverage += visible
                combined[visible] = part[visible]
            self.assertLessEqual(coverage.max(),1)
            np.testing.assert_array_equal(combined,original)
        backing = np.asarray(Image.open(OUT/'tome-page_backing.png'))
        left = np.asarray(Image.open(OUT/'tome-left_page.png'))[:,:,3]>0
        right = np.asarray(Image.open(OUT/'tome-right_page.png'))[:,:,3]>0
        np.testing.assert_array_equal(backing[:,:,3]>0,left|right)
        self.assertTrue((backing[left|right,:3]<100).all())
        self.assertGreater(int(left.sum()),800)
        self.assertGreater(int(right.sum()),800)

    def test_small_colored_focal_points_are_not_replaced_by_body_material(self):
        star = np.asarray(Image.open(OUT/'astrolabe-star.png'))
        colors = star[star[:,:,3]>0,:3].astype(int)
        self.assertTrue(((colors[:,2]>colors[:,0]+15)&(colors[:,2]>colors[:,1]+25)).any())
        bow = np.asarray(Image.open(OUT/'bow-grip.png'))
        colors = bow[bow[:,:,3]>0,:3].astype(int)
        self.assertTrue(((colors[:,1]>colors[:,0]*1.7)&(colors[:,1]>colors[:,2]*1.5)).any())
        tome = np.asarray(Image.open(OUT/'tome-binding_cover.png'))
        colors = tome[tome[:,:,3]>0,:3].astype(int)
        self.assertTrue(((colors[:,0]>150)&(colors[:,1]<40)&(colors[:,2]<60)).any())
        # Space inside the star ring remains truly open after detaching the star.
        ring = np.asarray(Image.open(OUT/'astrolabe-ring.png'))
        interior = region(np.full_like(ring,255),self.manifest['weapons']['astrolabe'],[410,305,520,525])
        self.assertTrue((ring[interior,3]==0).all())

    def test_outputs_remain_isolated_from_playing_server(self):
        self.assertFalse(self.manifest['runtime_applied'])
        self.assertFalse(self.manifest['native_models_exported'])
        index = (SOURCE.parents[2]/'server-minestom/src/main/resources/core-ui-pack/index.txt').read_text()
        self.assertNotIn('processed-specialists-v01',index)
        for key in JOBS: self.assertNotIn(f'pixel_{key}',index)


if __name__=='__main__': unittest.main()
