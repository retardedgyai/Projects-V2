"""Two painted branches with real hinges, an untouched grip, and no duplicate art."""
import json
import unittest
import numpy as np
from build_pixel_armament_pack import SOURCE, ASSETS, geometry, pose
from preview_class_armaments import rotated


class StaffArticulationTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.entry=json.loads((SOURCE/'manifest.json').read_text())['weapons']['staff']
        cls.base,cls.gem,cls.textures=geometry('staff',cls.entry)

    def test_branch_faces_partition_only_the_painted_head_without_duplicate_pixels(self):
        h,w=self.textures['body'].shape[:2]
        coverage=np.zeros((h,w),dtype=int)
        for e in self.base['elements']:
            if not e['name'].startswith('fin_') or 'north' not in e['faces']: continue
            u1,v0,u0,v1=e['faces']['north']['uv']
            x0,x1=round(u0*w/16),round(u1*w/16)
            y0,y1=round(v0*h/16),round(v1*h/16)
            coverage[y0:y1,x0:x1]+=1
        expected=self.textures['body'][:,:,3]>0
        expected[self.entry['rows'][1]:]=False
        np.testing.assert_array_equal(coverage,expected.astype(int))

    def test_fins_open_outward_around_fixed_roots_while_neck_and_handle_stay_still(self):
        for name,angle,sign in (('fin_left',9,-1),('fin_right',-14,1)):
            rest=[e for e in self.base['elements'] if e['name'].startswith(name+':')]
            peak=[e for e in pose(self.base,self.gem,'staff','prepare',5)['elements']
                  if e['name'].startswith(name+':')]
            self.assertTrue(rest)
            for original,opened in zip(rest,peak):
                self.assertEqual(opened['rotation']['angle'],angle)
                self.assertEqual(opened['rotation']['origin'],original['rotation']['origin'])
                root=np.array(original['rotation']['origin'])
                np.testing.assert_allclose(rotated(root,opened['rotation']),root,atol=1e-10)
                self.assertEqual(opened['faces'],original['faces'])
            top=max(peak,key=lambda e:e['to'][1])
            point=np.array(top['to'])
            self.assertGreater(sign*(rotated(point,top['rotation'])[0]-point[0]),.3)
        fixed=lambda m:[e for e in m['elements'] if e['name'].split(':')[0] in ('shaft','ferrule','fin_neck')]
        for stage,count in (('idle',12),('prepare',6),('release',6)):
            for frame in range(count):
                self.assertEqual(fixed(pose(self.base,self.gem,'staff',stage,frame)),fixed(self.base))

    def test_release_starts_from_prepared_pose_and_recovers_without_a_jump(self):
        self.assertEqual(pose(self.base,self.gem,'staff','prepare',5),pose(self.base,self.gem,'staff','release',0))
        self.assertEqual(pose(self.base,self.gem,'staff','release',5),pose(self.base,self.gem,'staff'))
        peak=pose(self.base,self.gem,'staff','prepare',5)
        rest=pose(self.base,self.gem,'staff')
        gems=lambda m:[e for e in m['elements'] if e['name'].startswith('jewel:')]
        for a,b in zip(gems(rest),gems(peak)):
            np.testing.assert_allclose(np.array(b['from'])-a['from'],[0,.9,-.65],atol=1e-6)
            self.assertEqual(b['rotation']['angle'],18)
            self.assertEqual(b['rotation']['axis'],'y')

    def test_all_saved_staff_poses_match_authored_geometry(self):
        for stage,count in (('rest',1),('idle',12),('prepare',6),('release',6)):
            for frame in range(count):
                suffix='' if stage=='rest' else f'_{stage}{frame:02}'
                actual=json.loads((ASSETS/f'models/item/weapons/pixel_staff{suffix}.json').read_text())
                self.assertEqual(actual,pose(self.base,self.gem,'staff',stage,frame))


if __name__=='__main__': unittest.main()
