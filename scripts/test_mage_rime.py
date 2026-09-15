"""Geometry/routing guards; passing these is not visual-quality acceptance."""
import hashlib
import json
import math
import unittest
import numpy as np
from build_mage_rime import CLIPS,GROUPS,PACK,SOURCE,mesh,triangle,spear


class RimeTests(unittest.TestCase):
    def test_paint_is_shipped_byte_identical(self):
        dest=PACK/'assets/projects/textures/combat_vfx/mage_material/rime_faces_v01.png'
        self.assertEqual(hashlib.sha256(SOURCE.read_bytes()).digest(),hashlib.sha256(dest.read_bytes()).digest())

    def test_each_cluster_is_individually_composed(self):
        self.assertEqual(6,len({tuple(v) for v in map(tuple,GROUPS.values())}))
        for clip,spikes in GROUPS.items():
            self.assertEqual(3 if 'inner' in clip else 4,len(spikes))
            for angle,root,tip,height,width,depth in spikes:
                self.assertGreater(tip-root,height*.65)
                self.assertLess(tip,16)
                self.assertGreater(root-depth,1.5) # keep caster's centre empty

    def test_faces_have_valid_bounds_and_uvs(self):
        for clip in CLIPS:
            self.assertLessEqual(len(mesh(clip)),1000)
            for e in mesh(clip):
                self.assertTrue(all(a<=b for a,b in zip(e['from'],e['to'])))
                self.assertTrue(all(math.isfinite(n) and -16<=n<=32 for k in ('from','to') for n in e[k]))
                for face in e['faces'].values():
                    self.assertTrue(all(0<=n<=16 for n in face['uv']))

    def test_shaft_faces_use_continuous_paint_not_full_tile_per_strip(self):
        for clip in CLIPS[1:]:
            for index,e in enumerate(mesh(clip)):
                if index%208>=192: # two ground caps have eight rows; shaft faces have 48
                    continue
                for name,f in e['faces'].items():
                    if name not in ('up','down'):
                        self.assertLess(abs(f['uv'][3]-f['uv'][1]),.5)

    def test_runtime_models_reproducible_and_registered(self):
        entries=(PACK/'index.txt').read_text().splitlines()
        for clip in CLIPS:
            path=f'assets/projects/models/combat_vfx/mage_material/{clip}_0.json'
            self.assertIn(path,entries)
            self.assertIn(path.replace('/models/','/items/'),entries)
            self.assertEqual(mesh(clip),json.loads((PACK/path).read_text())['elements'])

    def test_26_2_euler_planes_recover_the_intended_triangle_orientation(self):
        for a,b,c in [((-2,8,6),(1,8,4),(4,15,20)),
                      ((0,8,2),(-4,8,5),(-15,13,-2)),
                      ((0,8,0),(2,8,0),(1,8,3))]:
            elems=triangle(a,b,c,0)
            r=elems[0]['rotation']
            ax,ay,az=[math.radians(r[k]) for k in ('x','y','z')]
            rx=np.array([[1,0,0],[0,math.cos(ax),-math.sin(ax)],[0,math.sin(ax),math.cos(ax)]])
            ry=np.array([[math.cos(ay),0,math.sin(ay)],[0,1,0],[-math.sin(ay),0,math.cos(ay)]])
            rz=np.array([[math.cos(az),-math.sin(az),0],[math.sin(az),math.cos(az),0],[0,0,1]])
            rot=rz@ry@rx
            origin=np.array(r['origin'])
            for p in (a,b,c):
                local=rot.T@(np.array(p)-origin)
                self.assertAlmostEqual(0,local[2],places=8)
            self.assertTrue(np.allclose(rot[:,0],(np.array(b)-a)/np.linalg.norm(np.array(b)-a)))

    def test_dominant_shard_has_an_offset_shoulder_not_a_single_pyramid(self):
        for key in ('rime_outer_a','rime_outer_b','rime_outer_c'):
            spec=max(GROUPS[key],key=lambda s:s[3])
            elements=spear(*spec)
            self.assertEqual(208,len(elements))
            self.assertNotEqual(elements[0]['rotation'],elements[8]['rotation'])
            # Lower body and crown meet at the same longitudinal painted value.
            self.assertAlmostEqual(elements[7]['faces']['south']['uv'][1],
                                   elements[8]['faces']['south']['uv'][3])


if __name__=='__main__':
    unittest.main()
