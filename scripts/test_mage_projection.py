"""Projection math checks only; never evidence for artistic quality or GPU parity."""
import unittest
import numpy as np
from PIL import Image
from preview_skill_choreography import raster_quad,clip_textured_face


class ProjectionTests(unittest.TestCase):
    def test_eye_plane_clips_texture_and_geometry_before_perspective_division(self):
        clipped=clip_textured_face([(-1,0,-1),(1,0,-1),(1,1,1),(-1,1,1)],[0,0,16,16])
        self.assertEqual(4,len(clipped))
        self.assertTrue(all(p[2]>=-.6 for p in clipped))
        crossing=[p for p in clipped if abs(p[2]+.6)<1e-8]
        self.assertEqual(2,len(crossing))
        self.assertTrue(all(abs(p[4]-3.2)<1e-8 for p in crossing))
        self.assertEqual([],clip_textured_face([(-1,0,-2),(1,0,-2),(1,1,-1),(-1,1,-1)],[0,0,16,16]))

    def test_crossing_faces_choose_depth_per_pixel_not_average_or_submission_order(self):
        quads=[([(0,0,1),(8,0,5),(8,8,5),(0,8,1)],(30,80,160,255)),
               ([(0,0,3),(8,0,3),(8,8,3),(0,8,3)],(220,240,255,255))]
        results=[]
        for order in (quads,list(reversed(quads))):
            canvas=np.zeros((8,8,4),dtype=np.uint8)
            depth=np.full((8,8),np.inf)
            for points,color in order:
                raster_quad(canvas,depth,points,Image.new('RGBA',(1,1),color),[0,0,16,16])
            results.append(canvas)
            self.assertEqual((30,80,160,255),tuple(canvas[4,1]))
            self.assertEqual((220,240,255,255),tuple(canvas[4,6]))
        np.testing.assert_array_equal(*results)

    def test_sub_texel_strips_sample_original_uv_without_rounded_crop_seams(self):
        texture=Image.fromarray(np.array([[[10,20,30,255]],[[50,60,70,255]]],dtype=np.uint8))
        canvas=np.zeros((8,8,4),dtype=np.uint8)
        depth=np.full((8,8),np.inf)
        for row in range(8):
            raster_quad(canvas,depth,[(0,row,1),(8,row,1),(8,row+1,1),(0,row+1,1)],
                        texture,[0,row*2,16,(row+1)*2])
        np.testing.assert_array_equal(canvas[:4],np.tile([10,20,30,255],(4,8,1)))
        np.testing.assert_array_equal(canvas[4:],np.tile([50,60,70,255],(4,8,1)))

    def test_transparent_cutout_does_not_hide_the_surface_behind_it(self):
        canvas=np.zeros((8,8,4),dtype=np.uint8)
        depth=np.full((8,8),np.inf)
        points=[(0,0,1),(8,0,1),(8,8,1),(0,8,1)]
        raster_quad(canvas,depth,points,Image.new('RGBA',(1,1),(255,255,255,0)),[0,0,16,16])
        self.assertTrue(np.isinf(depth).all())
        self.assertFalse(canvas.any())


if __name__=='__main__': unittest.main()
