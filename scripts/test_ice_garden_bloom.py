"""Native resource contracts for the compact runtime Ice Garden bloom."""
import json,unittest
from pathlib import Path
from PIL import Image
from build_ice_garden_bloom import PACK,ASSETS,PREFIX,CROWNS,OFFSETS,elements
from ice_garden_visual_study import rotate

class BloomAssets(unittest.TestCase):
    def test_compact_models_items_textures_and_index_resolve(self):
        index=set((PACK/'index.txt').read_text(encoding='utf-8').splitlines())
        models=list((ASSETS/f'models/{PREFIX}').glob('*.json'))
        self.assertEqual(47,len(models))
        for path in models:
            key=path.relative_to(ASSETS/'models').as_posix().removesuffix('.json')
            item=ASSETS/f'items/{key}.json'
            self.assertEqual('projects:'+key,json.loads(item.read_text())['model']['model'])
            self.assertIn(path.relative_to(PACK).as_posix(),index);self.assertIn(item.relative_to(PACK).as_posix(),index)
            model=json.loads(path.read_text())
            for name in model['textures'].values():
                tex=ASSETS/f'textures/{name.split(":")[1]}.png'
                self.assertTrue(tex.is_file());self.assertIn(tex.relative_to(PACK).as_posix(),index)
            for e in model['elements']:
                for f in e['faces'].values():
                    self.assertIn(f['texture'][1:],model['textures']);self.assertTrue(all(0<=v<=16 for v in f['uv']))

    def test_native_volumes_remain_low_and_inside_supported_cells(self):
        for path in (ASSETS/f'models/{PREFIX}').glob('*.json'):
            for e in json.loads(path.read_text())['elements']:
                self.assertTrue(all(b>a for a,b in zip(e['from'],e['to'])))
                if 'rotation' in e:
                    self.assertIn(e['rotation']['angle'],[-22.5,22.5]);self.assertFalse(e['rotation']['rescale'])
                for i in range(8):
                    v=rotate([e['to'][j] if i&(1<<j) else e['from'][j] for j in range(3)],e.get('rotation'))
                    self.assertLess((v[1]-8)/16+(.12 if path.name.startswith('contact_') else .035),.72)
                    if path.name.startswith('tile_'):
                        self.assertTrue(-.5<=v[0]<=16.5 and -.5<=v[2]<=16.5)
                    self.assertTrue(all(-16<=n<=32 for n in v))

    def test_pixel_alpha_and_footprint_are_preserved_in_every_active_stage(self):
        for path in (ASSETS/f'textures/{PREFIX}').glob('*.png'):
            im=Image.open(path).convert('RGBA');self.assertEqual((16,16),im.size)
            self.assertTrue(set(im.getchannel('A').tobytes())<={0,255})
        for path in (ASSETS/f'models/{PREFIX}').glob('tile_*.json'):
            e=json.loads(path.read_text())['elements'][0]
            self.assertEqual([0,8,0],e['from']);self.assertEqual([16,8.5,16],e['to'])
        for x,z in OFFSETS:
            im=Image.open(ASSETS/f'textures/{PREFIX}/tile_{x+2}_{z+2}.png').convert('RGBA')
            for neighbour,axis,coordinate in [((x-1,z),0,0),((x+1,z),0,15),((x,z-1),1,0),((x,z+1),1,15)]:
                if neighbour not in OFFSETS:
                    for n in range(16):self.assertEqual((181,215,213,255),im.getpixel((coordinate,n) if axis==0 else (n,coordinate)))

    def test_five_crowns_leave_the_centre_clear_with_bounded_held_geometry(self):
        self.assertEqual(5,len(CROWNS))
        self.assertEqual(1,len(elements(0,0)))
        self.assertEqual(61,sum(len(elements(x,z)) for x,z in OFFSETS))
        self.assertLessEqual(max(len(elements(x,z,s)) for x,z in OFFSETS for s in [1,2,3,4,5]),6)

    def test_expiry_has_no_active_boundary_or_cyan_and_final_is_empty(self):
        for stage in range(3):
            model=json.loads((ASSETS/f'models/{PREFIX}/collapse_{stage}.json').read_text())
            self.assertNotIn('0',model['textures'])
            for e in model['elements']:
                for f in e['faces'].values():self.assertGreaterEqual(f['uv'][0],5)
        self.assertEqual([],json.loads((ASSETS/f'models/{PREFIX}/collapse_2.json').read_text())['elements'])

    def test_formation_warning_and_contact_have_distinct_native_shapes(self):
        for x,z in CROWNS:
            shapes=[json.loads((ASSETS/f'models/{PREFIX}/tile_{x+2}_{z+2}_{s}.json').read_text())['elements'] for s in range(1,6)]
            self.assertEqual(5,len(set(map(lambda x:json.dumps(x),shapes))))
            self.assertGreater(max(e['to'][1] for e in shapes[2]),max(e['to'][1] for e in shapes[4]))
        self.assertEqual(3,len(set((ASSETS/f'models/{PREFIX}/contact_{s}.json').read_text() for s in range(3))))

if __name__=='__main__':unittest.main()
