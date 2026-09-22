import unittest
import numpy as np
from build_expanded_slashes import PROFILES, contour
from build_greatsword_sweep import PACK, geometry, ink_uvs


class ExpandedSlashTest(unittest.TestCase):
    def test_all_profiles_grow_travel_and_expire(self):
        fingerprints=[]
        for name in PROFILES:
            frames=[contour(name,i) for i in range(10)]
            self.assertLess(np.count_nonzero(frames[1]),np.count_nonzero(frames[3]),name)
            self.assertTrue(((frames[4]>0)&(frames[3]==0)).any(),name)
            self.assertTrue(((frames[3]>0)&(frames[4]==0)).any(),name)
            self.assertFalse(frames[9].any(),name)
            self.assertTrue(contour(name,9,True).any(),name)
            self.assertFalse(contour(name,15,True).any(),name)
            fingerprints.append(b''.join(f.tobytes() for f in frames))
        self.assertEqual(len(set(fingerprints)),len(PROFILES))

    def test_return_paths_reverse_and_orbits_complete_one_turn(self):
        self.assertGreater(PROFILES['wound'][0][-1],PROFILES['wound'][0][0])
        self.assertLess(PROFILES['counter'][0][-1],PROFILES['counter'][0][0])
        for name in ('orbit','fan','fan_return'):
            angles=PROFILES[name][0]
            self.assertAlmostEqual(abs(angles[-1]-angles[0]),np.pi*2)
        self.assertGreater(PROFILES['fan'][0][-1],0)
        self.assertLess(PROFILES['fan_return'][0][-1],0)

    def test_profiles_stay_inside_pixel_canvas_and_native_budget(self):
        inks=ink_uvs(PACK/'assets/projects')
        for name in PROFILES:
            for wake,count in ((False,10),(True,16)):
                for frame in range(count):
                    g=contour(name,frame,wake)
                    self.assertFalse(g[0].any() or g[-1].any() or g[:,0].any() or g[:,-1].any(),name)
                    elements=geometry(g,inks,wake,frame)
                    self.assertLess(len(elements),600,name)
                    for e in elements:
                        self.assertEqual(e['from'][1],e['to'][1])
                        self.assertEqual(set(e['faces']),{'up','down'})


if __name__=='__main__': unittest.main()
