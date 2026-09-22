"""Nock tracking, one-way held release and directional hand transforms."""
import json
import unittest
import numpy as np
from PIL import Image
from process_bow_arrow_art import convert, geometry as arrow_geometry, OUT, SCALE
from build_specialist_armament_pack import SOURCE, ASSETS, geometry, pose, bow_points, motion, arrow_offset
from pixel_weapon_display import transformed


class BowArrowAnimationTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        entry=json.loads((SOURCE/'manifest.json').read_text())['weapons']['bow']
        cls.base,cls.parts,cls.textures,cls.anchors=geometry('bow',entry)

    def test_preserved_source_reproduces_binary_pixel_texture_at_bow_density(self):
        pixels,entry=convert()
        np.testing.assert_array_equal(pixels,np.asarray(Image.open(OUT/'arrow.png')))
        self.assertEqual(json.loads((OUT/'manifest.json').read_text()),entry)
        self.assertEqual(set(np.unique(pixels[:,:,3])),{0,255})
        self.assertLessEqual(len(np.unique(pixels[pixels[:,:,3]>0,:3],axis=0)),12)
        self.assertEqual(entry['model_units_per_pixel'],30/92)
        self.assertEqual(entry['content_size'][1],52)
        elements,_,_=arrow_geometry()
        self.assertLessEqual(max(e['to'][0] for e in elements),52*SCALE+1e-5)
        self.assertAlmostEqual(min(e['from'][0] for e in elements),0,places=5)

    def test_held_arrow_tracks_nock_for_every_draw_pose_without_changing_shape(self):
        for frame in range(6):
            nock=bow_points(self.anchors,motion('prepare',frame)[0])[1]
            np.testing.assert_allclose(arrow_offset(self.anchors,'prepare',frame),nock)
            model=pose('bow',self.base,self.parts,self.anchors,'prepare',frame)
            arrow=[e for e in model['elements'] if e['name'].startswith('arrow:')]
            self.assertEqual(len(arrow),len(self.parts['arrow']))
            for original,moved in zip(self.parts['arrow'],arrow):
                for edge in ('from','to'):
                    np.testing.assert_allclose(np.array(moved[edge])-nock,original[edge],atol=1e-6)
                self.assertEqual(original['faces'],moved['faces'])

    def test_release_moves_forward_then_removes_arrow_never_rewinds(self):
        prepared=pose('bow',self.base,self.parts,self.anchors,'prepare',5)
        self.assertEqual(prepared,pose('bow',self.base,self.parts,self.anchors,'release',0))
        positions=[arrow_offset(self.anchors,'release',f) for f in range(3)]
        self.assertTrue(all(b[0]>a[0] for a,b in zip(positions,positions[1:])))
        for stage,frames in (('release',range(3,6)),('idle',range(12)),('rest',range(1))):
            for frame in frames:
                self.assertIsNone(arrow_offset(self.anchors,stage,frame))
                model=pose('bow',self.base,self.parts,self.anchors,stage,frame)
                self.assertFalse(any(e['name'].startswith('arrow:') for e in model['elements']))

    def test_arrow_points_away_from_player_in_both_hand_local_frames(self):
        model=json.loads((ASSETS/'models/item/weapons/pixel_bow_prepare05.json').read_text())
        for context in ('firstperson','thirdperson'):
            for hand,left in (('righthand',False),('lefthand',True)):
                display=model['display'][context+'_'+hand]
                a=transformed([8,8,8],display,left)
                b=transformed([9,8,8],display,left)
                direction=(b-a)/np.linalg.norm(b-a)
                np.testing.assert_allclose(direction,[0,0,-1],atol=1e-7)


if __name__=='__main__': unittest.main()
