import unittest
import numpy as np
from build_greatsword_sweep import PACK, ribbon, impact, geometry, ink_uvs, tip


class GreatswordSweepTest(unittest.TestCase):
    def test_path_accelerates_and_geometry_changes_instead_of_revealing_a_fixed_mask(self):
        travel = [np.linalg.norm(tip(i+1)-tip(i)) for i in range(6)]
        self.assertGreater(travel[2], travel[0]*3)
        frames = [ribbon(i) for i in range(10)]
        self.assertEqual(len({f.tobytes() for f in frames}), 10)
        # At the apex NEW pixels appear and OLD pixels leave simultaneously.
        self.assertTrue(((frames[4]>0) & (frames[3]==0)).any())
        self.assertTrue(((frames[3]>0) & (frames[4]==0)).any())
        self.assertLess(np.count_nonzero(frames[0]), np.count_nonzero(frames[3])/3)
        self.assertFalse(frames[-1].any())

    def test_separate_wake_survives_blade_and_moves_before_dissolving(self):
        self.assertFalse(ribbon(0, True).any())
        self.assertFalse(ribbon(9).any())
        self.assertTrue(ribbon(9, True).any())
        self.assertFalse(ribbon(15, True).any())
        for i in range(3, 12):
            self.assertFalse(np.array_equal(ribbon(i, True), ribbon(i+1, True)))

    def test_native_planes_are_pixel_aligned_bounded_and_not_rotating_cuboids(self):
        inks = ink_uvs(PACK/'assets/projects')
        for layer, count in (('blade',10),('wake',16),('impact',9)):
            for frame in range(count):
                grid = impact(frame) if layer=='impact' else ribbon(frame,layer=='wake')
                self.assertFalse(grid[0].any() or grid[-1].any() or grid[:,0].any() or grid[:,-1].any())
                elems = geometry(grid, inks, layer=='wake',frame)
                self.assertLess(len(elems),600)
                for e in elems:
                    self.assertEqual(e['from'][1],e['to'][1])
                    self.assertNotIn('rotation',e)
                    self.assertEqual(set(e['faces']),{'up','down'})
                    self.assertTrue(all(-16<=n<=32 and np.isfinite(n) for n in e['from']+e['to']))
                    for n in e['from'][::2]+e['to'][::2]:
                        self.assertEqual(n*4,round(n*4))
        self.assertFalse(impact(8).any())


if __name__=='__main__': unittest.main()
