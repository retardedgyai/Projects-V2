"""Exported placement contracts; not an in-game arm/camera or art-quality check."""
import json
from pathlib import Path
import unittest
import numpy as np
from build_pixel_armament_pack import SOURCE as FIRST_SOURCE, ASSETS as FIRST_ASSETS, geometry as first_geometry
from build_specialist_armament_pack import SOURCE as EXTRA_SOURCE, ASSETS as EXTRA_ASSETS, geometry as extra_geometry
from pixel_weapon_display import grip_pixels, grip_point, transformed, vertices, rotation_xyz

ROOT = Path(__file__).resolve().parents[1]
TARGETS = {'firstperson': [1.13, 3.2, -1.5], 'thirdperson': [0, 2, 1]}


def authored_weapons():
    for source, assets, geometry in ((FIRST_SOURCE, FIRST_ASSETS, first_geometry),
                                      (EXTRA_SOURCE, EXTRA_ASSETS, extra_geometry)):
        for key, entry in json.loads((source/'manifest.json').read_text())['weapons'].items():
            built = geometry(key, entry)
            yield key, entry, built[2], assets/'models/item/weapons'


class PixelWeaponDisplayTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.weapons = list(authored_weapons())

    def test_native_rotation_order_with_noncommuting_axes(self):
        # Rx * Ry * Rz: z sends X to Y, y keeps Y, x sends Y to Z.
        np.testing.assert_allclose(rotation_xyz([90,90,90])@[1,0,0], [0,0,1], atol=1e-10)
        display = {'rotation':[0,0,90], 'translation':[2,3,4], 'scale':[2,3,4]}
        np.testing.assert_allclose(transformed([9,8,8],display), [2,5,4], atol=1e-10)
        np.testing.assert_allclose(transformed([9,8,8],display,True), [-2,1,4], atol=1e-10)

    def test_each_painted_grip_reaches_the_same_native_hand_anchor(self):
        for key, entry, textures, directory in self.weapons:
            model = json.loads((directory/f'pixel_{key}.json').read_text())
            grip = grip_point(key,entry,textures)
            for prefix, target in TARGETS.items():
                right = model['display'][prefix+'_righthand']
                left = model['display'][prefix+'_lefthand']
                if key!='bow': self.assertEqual(left['rotation'],right['rotation'])
                np.testing.assert_allclose(transformed(grip,right),target,atol=2e-6,err_msg=key)
                np.testing.assert_allclose(transformed(grip,left,True),[-target[0],*target[1:]],atol=2e-6,err_msg=key)

    def test_gui_and_fixed_models_fit_and_are_centered_without_stretching(self):
        for key, _, _, directory in self.weapons:
            model = json.loads((directory/f'pixel_{key}.json').read_text())
            for context, extent in (('gui',14),('fixed',12)):
                display = model['display'][context]
                self.assertEqual(len(set(display['scale'])),1)
                self.assertGreater(display['scale'][0],0)
                bounds = transformed(vertices(model['elements']),display)
                low,high = bounds.min(0),bounds.max(0)
                np.testing.assert_allclose((low+high)/2,[0,0,0],atol=2e-6,err_msg=key)
                self.assertLessEqual(max((high-low)[:2]),extent+3e-5,key)

    def test_animation_never_changes_the_display_transform(self):
        count = 0
        for key, _, _, directory in self.weapons:
            rest = json.loads((directory/f'pixel_{key}.json').read_text())
            for path in directory.glob(f'pixel_{key}*.json'):
                self.assertEqual(json.loads(path.read_text())['display'],rest['display'],path.name)
                count += 1
        self.assertEqual(count,175)

    def test_grips_are_in_authored_handle_regions_not_the_whole_canvas_center(self):
        points = {}
        for key, entry, textures, _ in self.weapons:
            px,py = grip_pixels(key,entry,textures)
            points[key] = grip_point(key,entry,textures)[1]
            if key=='tome': continue  # Book is supported below the binding, not a rod.
            layer = textures['grip'] if key=='bow' else textures['shaft'] if key=='astrolabe' else textures['body']
            self.assertGreater(layer[int(py),int(px),3],0,key)
        self.assertGreater(points['staff'],points['greatsword']+5)
        self.assertGreater(points['bow'],points['greatsword']+5)


def export_native_contract():
    """Points/targets for the actual 26.2 ItemTransform, not a second Python mock."""
    cases = []
    for key, entry, textures, directory in authored_weapons():
        grip = grip_point(key,entry,textures).tolist()
        for path in sorted(directory.glob(f'pixel_{key}*.json')):
            cases.append({'path':str(path), 'grip':grip, 'targets':TARGETS})
    from build_greatsword_tiers import JOBS, ASSETS, model_data, tier_key
    for tier in JOBS:
        _,textures,entry,_,_=model_data(tier)
        grip=grip_point('greatsword',entry,textures).tolist()
        for path in sorted((ASSETS/'models/item/weapons').glob(f'pixel_{tier_key(tier)}*.json')):
            cases.append({'path':str(path),'grip':grip,'targets':TARGETS})
    output = ROOT/'.tools/native-cuboid-check/display-contract.json'
    output.parent.mkdir(parents=True,exist_ok=True)
    output.write_text(json.dumps(cases,indent=2)+'\n',encoding='utf-8')
    print(output)


if __name__=='__main__':
    result = unittest.main(exit=False)
    if not result.result.wasSuccessful(): raise SystemExit(1)
    export_native_contract()
