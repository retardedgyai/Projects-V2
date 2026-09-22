"""Integration assertions over a freshly generated :model-lab:iceFangVisualTrace."""
import json
import unittest
import numpy as np
from preview_ice_fang import OUT, world_quads, euler


class NativeProjectionTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.trace=json.loads((OUT/'ice-fang-native.json').read_text())

    def test_cardinal_native_headings_rotate_the_whole_geometry_and_translation(self):
        probes=self.trace['headings']
        self.assertEqual([0,90,180,-90],[p['castYaw'] for p in probes])
        def points(probe):
            return np.concatenate([q for part in probe['parts'] for q,_,_ in world_quads(part)])-np.array([0,1,0])
        base=points(probes[0])
        self.assertGreater(base[:,2].max(),1.0) # The ice must grow forward, not toward the caster.
        self.assertLess(abs(base[:,2].min()),.3)
        for probe in probes[1:]:
            np.testing.assert_allclose(points(probe),base@euler([0,-probe['castYaw'],0]).T,atol=1e-5)

    def test_three_stages_and_cleanup_are_from_the_engine(self):
        frames=self.trace['frames']
        self.assertEqual(len(frames),56)
        self.assertEqual(len(frames[0]),3)
        self.assertEqual(len(frames[6]),6)
        self.assertEqual(len(frames[12]),9)
        self.assertEqual(frames[54],[])
        self.assertEqual(frames[55],[])
        self.assertTrue(all(p['yaw']==180 for frame in frames for p in frame))


if __name__=='__main__':unittest.main()
