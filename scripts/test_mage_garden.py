"""Geometry invariants, not a visual-quality score."""
import unittest
import numpy as np
from build_mage_garden import SHAPE,STEP,skin,crystal,mesh
from build_approved_dash_v3 import PACK,ink_uvs


class GardenSculptureTests(unittest.TestCase):
    def test_surface_mesher_preserves_exposed_area_without_interior_faces(self):
        volume=np.zeros(SHAPE,dtype=np.uint8)
        volume[5:12,4:14,7:15]=3
        volume[7:9,8:14,7:11]=0
        expected=0
        for axis in range(3):
            for sign in (-1,1):
                neighbor=np.roll(volume,-sign,axis=axis)
                boundary=[slice(None)]*3;boundary[axis]=-1 if sign==1 else 0
                neighbor[tuple(boundary)]=0
                expected+=np.count_nonzero((volume>0)&(neighbor==0))
        actual=0
        for e in skin(volume,[0,0,1,1]):
            self.assertEqual(1,len(e['faces']))
            dimensions=sorted(b-a for a,b in zip(e['from'],e['to']))
            actual+=dimensions[1]*dimensions[2]/STEP**2
        self.assertAlmostEqual(expected,actual,places=5)

    def test_crystal_is_a_rooted_volume_with_a_long_taper_not_a_flat_extrusion(self):
        volume=np.zeros(SHAPE,dtype=np.uint8)
        crystal(volume,7.1,8,20,2.4,2.1,-2.7,.8,10)
        points=np.argwhere(volume>0)
        spans=np.ptp(points,axis=0)
        self.assertGreater(spans[2],spans[0]*.5)
        self.assertEqual(0,points[:,1].min())
        occupied=np.count_nonzero(volume,axis=(0,2))
        self.assertGreater(occupied[14],occupied[30]*2)
        self.assertGreater(len(np.unique(volume[volume>0])),3)

    def test_dissolution_finishes_before_entity_removal_and_beats_do_not_repeat_body(self):
        uv=ink_uvs(PACK/'assets/projects')[3]
        for clip in ('garden_spires','garden_fan'):
            self.assertTrue(mesh(clip,9,uv))
            self.assertEqual([],mesh(clip,46,uv))
            self.assertEqual([],mesh(clip,47,uv))
        self.assertEqual([],mesh('garden_beat',23,uv))
        self.assertLess(len(mesh('garden_beat',7,uv)),40)


if __name__=='__main__':unittest.main()
