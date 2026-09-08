"""Approved drawing stays frozen; only native hand-context placement changes."""
import unittest
import json
import hashlib
import numpy as np
from build_pixel_armament_pack import geometry
from process_sword_material_redraw import convert, guard_depth, approved_hand_display, approved_grip_point, SOURCE, DIGEST
from pixel_weapon_display import transformed
from build_material_playtest_pack import candidate_resources


class MaterialGripTest(unittest.TestCase):
    def test_approved_drawing_geometry_and_inventory_are_unchanged(self):
        self.assertEqual(hashlib.sha256(SOURCE.read_bytes()).hexdigest(),DIGEST)
        _,textures,entry=convert()
        base,_,_=geometry('greatsword',entry,textures)
        base=guard_depth(base,textures,entry)
        fixed=approved_hand_display(base,textures,entry)
        self.assertEqual(fixed['elements'],base['elements'])
        self.assertEqual(fixed['textures'],base['textures'])
        for context in ('gui','fixed','ground'):
            self.assertEqual(fixed['display'].get(context),base['display'].get(context))
        self.assertNotEqual(fixed['display']['thirdperson_righthand'],base['display']['thirdperson_righthand'])

    def test_all_25_poses_hold_the_bare_handle_at_the_stock_sword_grip_in_both_hands(self):
        _,textures,entry=convert()
        grip=approved_grip_point(textures,entry)
        models=[json.loads(v) for k,v in candidate_resources().items() if '/models/' in k]
        self.assertEqual(len(models),25)
        for model in models:
            for prefix,angle,translation,scale in (
                    ('firstperson',25,[1.13,3.2,1.13],.68),('thirdperson',55,[0,4,.5],.85)):
                for left in (False,True):
                    native={'rotation':[0,90 if left else -90,-angle if left else angle],
                            'translation':translation,'scale':[scale]*3}
                    context=prefix+('_lefthand' if left else '_righthand')
                    np.testing.assert_allclose(transformed(grip,model['display'][context],left),
                        transformed([3.5,3.5,8],native,left),atol=1e-6)


if __name__=='__main__': unittest.main()
