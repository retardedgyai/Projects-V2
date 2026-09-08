import unittest
import numpy as np
from build_greatsword_sweep import PACK, ribbon, impact, geometry, ink_uvs, tip, fold_rows


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

    def test_native_planes_are_pixel_sized_bounded_and_not_solid_cuboids(self):
        inks = ink_uvs(PACK/'assets/projects')
        for layer, count in (('blade',10),('wake',16),('impact',9)):
            for frame in range(count):
                grid = impact(frame) if layer=='impact' else ribbon(frame,layer=='wake')
                self.assertFalse(grid[0].any() or grid[-1].any() or grid[:,0].any() or grid[:,-1].any())
                elems = geometry(grid, inks, layer=='wake',frame,curved=layer!='impact')
                self.assertLess(len(elems),600)
                for e in elems:
                    self.assertEqual(e['from'][1],e['to'][1])
                    rotation = e.get('rotation')
                    if rotation:
                        self.assertEqual(rotation['axis'],'x')
                        self.assertIn(rotation['angle'],(-45,-22.5,22.5,45))
                        self.assertFalse(rotation['rescale'])
                        self.assertNotEqual(layer,'impact')
                    self.assertEqual(set(e['faces']),{'up','down'})
                    self.assertTrue(all(-16<=n<=32 and np.isfinite(n) for n in e['from']+e['to']))
                    for n in (e['from'][0],e['to'][0]):
                        self.assertEqual(n*4,round(n*4))
                    self.assertAlmostEqual(e['to'][2]-e['from'][2],.25)
        self.assertFalse(impact(8).any())

    def test_curved_surface_has_no_cracks_between_rows_or_temporal_spin(self):
        rows = fold_rows()
        for a,b in zip(rows,rows[1:]):
            def edge(row,sign):
                y,z,angle = row
                angle = np.deg2rad(angle)
                return np.array((y-sign*np.sin(angle)*.125,z+sign*np.cos(angle)*.125))
            np.testing.assert_allclose(edge(a,1),edge(b,-1),atol=1e-12)
        inks = ink_uvs(PACK/'assets/projects')
        # More than one normal is present in an apex frame; copies are not crossed.
        apex = geometry(ribbon(4),inks)
        self.assertGreater(len({e.get('rotation',{}).get('angle',0) for e in apex}),2)
        self.assertGreater(max(e['from'][1] for e in apex)-min(e['from'][1] for e in apex),.4)


if __name__=='__main__': unittest.main()
