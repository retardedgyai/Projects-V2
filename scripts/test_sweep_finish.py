import json
import unittest
import numpy as np
from build_greatsword_sweep import PACK, chips, tip, ink_uvs, pigments, impact_mesh, build


class SweepFinishTest(unittest.TestCase):
    def test_authoring_pipeline_reproduces_every_shipped_frame_and_item(self):
        checked=[]
        def compare(path,value):
            self.assertEqual(json.loads(path.read_text()),value,str(path.relative_to(PACK)))
            checked.append(path)
        build(PACK/'assets/projects',compare)
        self.assertEqual(len(checked),654)

    def test_actual_shipped_models_resolve_three_pigments_and_stay_within_budget(self):
        assets=PACK/'assets/projects'
        checked=0
        for directory in ('greatsword','sweeps'):
            for path in (assets/f'models/combat_vfx/{directory}').rglob('*.json'):
                key=path.relative_to(assets/'models')
                item=json.loads((assets/'items'/key).read_text())['model']
                model=json.loads(path.read_text())
                self.assertEqual(len(item['tints']),3,str(key))
                self.assertEqual(len({t['value'] for t in item['tints']}),3)
                self.assertLess(len(model['elements']),600,str(key))
                for e in model['elements']:
                    self.assertEqual(e['from'][1],e['to'][1])
                    for face in e['faces'].values():
                        self.assertIn(face['tintindex'],range(3))
                if path.stem in ('blade_9','wake_15','impact_8'):
                    self.assertFalse(model['elements'],str(key))
                checked+=1
        self.assertEqual(checked,315)

    def test_splinters_are_born_on_the_path_then_move_out_of_its_plane(self):
        inks=ink_uvs(PACK/'assets/projects')
        self.assertFalse(chips(tip,2,inks))
        frames=[chips(tip,t,inks) for t in (3,4,5,6,7)]
        self.assertTrue(all(frames))
        self.assertEqual(len({json.dumps(f) for f in frames}),5)
        self.assertTrue(any(e['from'][1]>8 for e in frames[2]))
        self.assertTrue(any(e['from'][1]<8 for e in frames[2]))
        self.assertFalse(chips(tip,11,inks))

    def test_contact_after_flash_has_real_depth_and_fully_expires(self):
        inks=ink_uvs(PACK/'assets/projects')
        pieces=impact_mesh(4,inks)
        self.assertGreater(np.ptp([e['from'][1] for e in pieces]),.8)
        self.assertTrue(all('rotation' in e for e in pieces))
        self.assertFalse(impact_mesh(8,inks))

    def test_midtones_are_hue_authored_instead_of_monochrome_scaling(self):
        for family in ('steel','gold','shadow'):
            rgb=[np.array([(t['value']>>16)&255,(t['value']>>8)&255,t['value']&255],float) for t in pigments(family,'blade')]
            # A greyscale multiplier would keep normalized chroma unchanged.
            self.assertGreater(np.linalg.norm(rgb[1]/rgb[1].sum()-rgb[2]/rgb[2].sum()),.03)


if __name__=='__main__': unittest.main()
