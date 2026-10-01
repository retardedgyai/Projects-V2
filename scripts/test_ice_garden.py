"""Contracts for the native shipped assets; no software installation required."""
import json
import unittest
from pathlib import Path
from PIL import Image

ROOT=Path(__file__).resolve().parents[1]
PACK=ROOT/'server-minestom/src/main/resources/core-ui-pack'
ASSETS=PACK/'assets/projects'

class GardenAssets(unittest.TestCase):
    def test_every_model_texture_and_item_is_indexed_and_resolvable(self):
        index=set((PACK/'index.txt').read_text(encoding='utf-8').splitlines())
        models=list((ASSETS/'models/combat_vfx/garden').glob('*.json'))
        self.assertEqual(170,len(models))
        for path in models:
            key=path.relative_to(ASSETS/'models').as_posix().removesuffix('.json')
            item_path=ASSETS/f'items/{key}.json'
            self.assertEqual('projects:'+key,json.loads(item_path.read_text())['model']['model'])
            for file in [path,item_path]: self.assertIn(file.relative_to(PACK).as_posix(),index)
            model=json.loads(path.read_text())
            for texture in model['textures'].values():
                texture_path=ASSETS/f'textures/{texture.split(":")[1]}.png'
                self.assertTrue(texture_path.is_file());self.assertIn(texture_path.relative_to(PACK).as_posix(),index)

    def test_hard_pixel_alpha_native_rotation_and_plate_footprint(self):
        for path in (ASSETS/'textures/combat_vfx/garden').glob('*.png'):
            im=Image.open(path).convert('RGBA')
            self.assertEqual((16,16),im.size)
            self.assertTrue({c[3] for c in im.getdata()} <= {0,255})
        for path in (ASSETS/'models/combat_vfx/garden').glob('tile_*.json'):
            elements=json.loads(path.read_text())['elements']
            self.assertEqual([0,8,0],elements[0]['from']);self.assertEqual([16,8.35,16],elements[0]['to'])
            for element in elements:
                self.assertTrue(all(-16<=v<=32 for v in element['from']+element['to']))
                self.assertTrue(all(b>a for a,b in zip(element['from'],element['to'])))
                if 'rotation' in element:self.assertIn(element['rotation']['angle'],[-22.5,22.5])

    def test_centre_stays_clear_and_crystals_have_two_deliberate_sizes(self):
        centre=json.loads((ASSETS/'models/combat_vfx/garden/tile_2_2_2.json').read_text())['elements']
        cardinal=json.loads((ASSETS/'models/combat_vfx/garden/tile_4_2_2.json').read_text())['elements']
        corner=json.loads((ASSETS/'models/combat_vfx/garden/tile_4_3_2.json').read_text())['elements']
        self.assertEqual(1,len(centre));self.assertEqual(3,len(cardinal));self.assertEqual(3,len(corner))
        self.assertGreater(cardinal[1]['to'][1]-cardinal[1]['from'][1],corner[1]['to'][1]-corner[1]['from'][1])
        ended=json.loads((ASSETS/'models/combat_vfx/garden/tile_4_2_4.json').read_text())['elements']
        self.assertEqual(1,len(ended))

    def test_expiry_has_no_active_boundary_and_last_frame_is_transparent(self):
        for path in (ASSETS/'textures/combat_vfx/garden').glob('tile_*_8.png'):
            self.assertIsNone(Image.open(path).convert('RGBA').getbbox())
        active=Image.open(ASSETS/'textures/combat_vfx/garden/tile_2_2_2.png').convert('RGBA')
        ended=Image.open(ASSETS/'textures/combat_vfx/garden/tile_2_2_4.png').convert('RGBA')
        self.assertLess(sum(c[3]>0 for c in ended.getdata()),sum(c[3]>0 for c in active.getdata())/2)
        for r,g,b,a in ended.getdata():
            if a:self.assertLessEqual(b-r,15)

if __name__=='__main__':unittest.main()
