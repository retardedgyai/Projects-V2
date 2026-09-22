"""Geometry invariants, not a visual-quality score."""
import unittest
import numpy as np
from build_mage_garden import SHAPE,STEP,skin,crystal,mesh,textured_faces
from build_mage_fire import box
from build_approved_dash_v3 import PACK,ink_uvs


class GardenSculptureTests(unittest.TestCase):
    def test_adjacent_native_faces_keep_the_same_texture_coordinate_at_shared_edges(self):
        for face,axis in (('north',0),('east',2)):
            elements=[]
            for start in (0,2):
                lo=[0,8,0];hi=[2,12,2]
                lo[axis]=start;hi[axis]=start+2
                e=box(lo,hi,2,[0,0,1,1]);e['faces']={face:e['faces'][face]};elements.append(e)
            a,b=textured_faces(elements,'garden_spires')
            self.assertEqual(a['faces'][face]['uv'][0],b['faces'][face]['uv'][2],face)
        # Fracture caps use a separate material, not a rotated crystal stripe.
        e=textured_faces([box((0,8,0),(2,12,2),2,[0,0,1,1])],'garden_spires')[0]
        self.assertEqual('#2',e['faces']['up']['texture'])
        self.assertEqual('#1',e['faces']['north']['texture'])

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

    def test_zero_floor_has_real_surface_growth_and_wave_has_a_forward_lean(self):
        from build_mage_glacier import mesh as glacier
        uv=ink_uvs(PACK/'assets/projects')[3]
        def top_area(elements):
            return sum((e['to'][0]-e['from'][0])*(e['to'][2]-e['from'][2])
                       for e in elements if 'up' in e['faces'])
        early=glacier('zero_floor',2,uv);grown=glacier('zero_floor',18,uv)
        self.assertGreater(top_area(grown),top_area(early)*2)
        self.assertEqual([],glacier('zero_floor',47,uv))
        wave=glacier('frost_wave',7,uv)
        self.assertGreater(max(e['to'][1] for e in wave)-8,5)
        self.assertTrue(any(e['to'][2]>11 for e in wave))


if __name__=='__main__':unittest.main()
