"""Geometry/routing guards; passing these is not visual-quality acceptance."""
import hashlib
import json
import math
import unittest
import numpy as np
from build_mage_rime import CLIPS,BUNDLES,PACK,SOURCE,TEXTURE,mesh,triangle,broken_plate
from PIL import Image


class RimeTests(unittest.TestCase):
    def test_paint_is_shipped_byte_identical(self):
        dest=PACK/'assets/projects/textures/combat_vfx/mage_material/rime_calm_faces_v01.png'
        self.assertEqual(hashlib.sha256(SOURCE.read_bytes()).digest(),hashlib.sha256(dest.read_bytes()).digest())

    def test_each_cluster_is_individually_composed(self):
        self.assertEqual(6,len({tuple(v) for v in map(tuple,BUNDLES.values())}))
        for clip,slabs in BUNDLES.items():
            self.assertEqual(3 if 'inner' in clip else 5,len(slabs))
            axes=[np.array(p[1])-p[0] for p in slabs]
            axes=[a/np.linalg.norm(a) for a in axes]
            self.assertTrue(any(np.dot(axes[0],a)<.9 for a in axes[1:]))
            for root,end,width,depth,roll,variant,panel in slabs:
                self.assertGreater(end[1],root[1])
                self.assertGreater(width,depth*2)

    def test_faces_have_valid_bounds_and_uvs(self):
        for clip in CLIPS:
            self.assertLessEqual(len(mesh(clip)),1000)
            for e in mesh(clip):
                self.assertTrue(all(a<=b for a,b in zip(e['from'],e['to'])))
                self.assertTrue(all(math.isfinite(n) and -16<=n<=32 for k in ('from','to') for n in e[k]))
                for face in e['faces'].values():
                    self.assertTrue(all(0<=n<=16 for n in face['uv']))

    def test_shaft_faces_use_continuous_paint_not_full_tile_per_strip(self):
        for variant in range(3):
            shaft_elements=broken_plate((8,8,8),(8,16,12),3,1,0,variant)
            for e in shaft_elements[:-12]:
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

    def test_outer_intakes_connect_inward_without_filling_the_casters_centre(self):
        for key in ('rime_outer_a','rime_outer_b','rime_outer_c'):
            root,end,*_=BUNDLES[key][-1]
            self.assertLess(root[2],-2.5)
            self.assertGreater(5.225+root[2],2.0)
            self.assertGreater(end[2],1)
            self.assertLess(end[1],1.5)

    def test_outer_side_branch_begins_inside_the_primary_shoulder_above_ground(self):
        for key in ('rime_outer_a','rime_outer_b','rime_outer_c'):
            specs=BUNDLES[key]
            primary_end=np.array(specs[0][1]);axis=primary_end/np.linalg.norm(primary_end)
            for slab in specs[1:3]:
                root=np.array(slab[0])
                self.assertGreater(root[1],1)
                self.assertLess(root[1],primary_end[1]*.55)
                self.assertLess(np.linalg.norm(root-axis*np.dot(root,axis)),1.1)

    def test_bundle_has_thick_closed_slabs_instead_of_crossed_sprites(self):
        for key,specs in BUNDLES.items():
            elements=mesh(key)
            self.assertTrue(all(f['texture']=='#0' for e in elements for f in e['faces'].values()))
            self.assertGreater(len({tuple(e['rotation']['origin']) for e in elements}),30)
            self.assertTrue(all(s[3]<s[2]*.55 for s in specs))

    def test_chipped_plate_has_multiple_upper_intervals_and_closed_edges(self):
        for variant in range(3):
            elems=broken_plate((8,8,8),(8,18,8),4,1,0,variant)
            rows={}
            for e in elems[:-12]:
                rows.setdefault(e['from'][1],[]).append(e)
            self.assertTrue(any(len(intervals)>=4 for intervals in rows.values()))
            self.assertEqual(12,len(elems[-12:]))
            self.assertTrue(all(e['faces']['south']['uv'][0]==11.5 for e in elems[-12:]))
            self.assertGreater(len({tuple(e['faces']['south']['uv']) for e in elems[-12:]}),4)

    def test_runtime_uses_opaque_surface_paint_not_the_rejected_cutout_sheet(self):
        rgba=np.asarray(Image.open(SOURCE).convert('RGBA'))
        self.assertTrue((rgba[:,:,3]==255).all())
        for clip in CLIPS:
            painted=[e for e in mesh(clip) if e['faces']['south']['texture']=='#1']
            self.assertFalse(painted) # Rejected feather sheet no longer drives runtime silhouettes.
            model=json.loads((PACK/f'assets/projects/models/combat_vfx/mage_material/{clip}_0.json').read_text())
            self.assertEqual(TEXTURE,model['textures']['0'])


if __name__=='__main__':
    unittest.main()
