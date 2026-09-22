import hashlib
import json
import math
import unittest
from build_mage_meteor import METEOR_CLIPS
from build_mage_materials import CLIPS, PACK, TEXTURED_MATERIALS, material_geometry, material_palette, ink_uvs
from build_mage_fire import source as fire_source
from build_mage_ice import source as ice_source
from build_mage_garden import TEXTURED_CLIPS


class MageMaterialTests(unittest.TestCase):
    def test_generated_assets_reproduce_and_use_only_valid_native_faces_and_materials(self):
        assets=PACK/'assets/projects'
        inks=ink_uvs(assets)
        index=set((PACK/'index.txt').read_text(encoding='utf-8').splitlines())
        for clip,(frames,_) in CLIPS.items():
            for frame in range(frames):
                key=f'combat_vfx/mage_material/{clip}_{frame}'
                model_path=assets/f'models/{key}.json'
                self.assertIn(model_path.relative_to(PACK).as_posix(),index)
                self.assertIn(f'assets/projects/items/{key}.json',index)
                model=json.loads(model_path.read_text(encoding='utf-8'))
                expected=material_geometry(clip,frame,inks)
                self.assertEqual(expected,model['elements'],(clip,frame))
                self.assertLessEqual(len(expected),1000,(clip,frame))
                item=json.loads((assets/f'items/{key}.json').read_text(encoding='utf-8'))
                colours=material_palette(clip)
                self.assertEqual('projects:'+key,item['model']['model'])
                self.assertEqual(colours,[t['value'] for t in item['model']['tints']])
                for e in expected:
                    self.assertTrue(all(math.isfinite(v) and -16<=v<=32 for v in e['from']+e['to']),(clip,frame))
                    self.assertTrue(all(a<b for a,b in zip(e['from'],e['to'])),(clip,frame))
                    self.assertTrue(set(e['faces'])<={'up','down','east','west','north','south'})
                    for f in e['faces'].values():
                        if clip in TEXTURED_MATERIALS:
                            self.assertIn(f['texture'],('#0','#1','#2','#3','#4') if clip in METEOR_CLIPS else ('#0','#1','#2'))
                            self.assertTrue(all(0<=v<=16 for v in f['uv']))
                            self.assertNotEqual(f['uv'][0],f['uv'][2]);self.assertNotEqual(f['uv'][1],f['uv'][3])
                        else:
                            self.assertEqual('#0',f['texture'])
                            self.assertEqual(inks[3],f['uv'])
                        self.assertIn(f['tintindex'],range(len(colours)))
                if clip in ('cinder','fire_stream','conductor'):
                    self.assertTrue(all(.0<=e['from'][2]<e['to'][2]<=16 for e in expected),(clip,frame))
                if frame==frames-1:self.assertEqual([],expected,clip)

    def test_material_phrases_have_opening_and_dissolution_not_full_card_rotation(self):
        inks=ink_uvs(PACK/'assets/projects')
        for clip,(count,_) in CLIPS.items():
            frames=[material_geometry(clip,i,inks) for i in range(count)]
            self.assertGreater(max(len(f) for f in frames),3,clip)
            # A falling rock moves by display translation; its intact material
            # need not wobble just to increase a frame-hash metric.
            self.assertGreaterEqual(len({json.dumps(f) for f in frames}),2 if clip=='meteor' else 3,clip)
            self.assertEqual([],frames[-1],clip)
            for f in frames:
                for e in f:
                    if 'rotation' in e:
                        self.assertIn(e['rotation']['angle'],(-45,-22.5,0,22.5,45))
                        self.assertIn(e['rotation']['axis'],('x','y','z'))

    def test_fire_and_ice_keep_broad_painted_materials_without_baked_checkerboard(self):
        fire,colours=fire_source()
        self.assertEqual((28,64),fire.shape)
        self.assertEqual(4,len(colours))
        self.assertTrue(all((c>>16)-(c&255)>38 for c in colours))
        forms,ice_colours=ice_source()
        self.assertEqual(3,len(forms))
        self.assertEqual(3,len({f.tobytes() for f in forms}))
        self.assertTrue(all((c&255)-(c>>16)>28 for c in ice_colours))
        # The approved style anchor is input-only, not repainted or overwritten.
        anchor=PACK.parents[4]/'assets/class-armaments/texture-first/sources/greatsword-material-v02.png'
        self.assertEqual('5962fb645e63ec851d64fcfff32d281291edea1cf87d59a5a443899aa305f3e3',
                         hashlib.sha256(anchor.read_bytes()).hexdigest())

    def test_original_material_png_bytes_are_shipped_and_every_model_texture_resolves(self):
        sources=PACK.parents[4]/'assets/combat-vfx/mage-v4/sources'
        folder=PACK/'assets/projects/textures/combat_vfx/mage_material'
        for name in ('glacial-strata','frozen-fracture','solar-flow','lunar-veins'):
            self.assertEqual((sources/f'{name}-v01.png').read_bytes(),
                             (folder/(name.replace('-','_')+'_v01.png')).read_bytes())
        for p in (PACK/'assets/projects/models/combat_vfx/mage_material').glob('*.json'):
            model=json.loads(p.read_text(encoding='utf-8'))
            for resource in model['textures'].values():
                namespace,path=resource.split(':')
                self.assertTrue((PACK/f'assets/{namespace}/textures/{path}.png').is_file(),(p,resource))
            for e in model['elements']:
                for f in e['faces'].values():
                    self.assertIn(f['texture'][1:],model['textures'],p)


if __name__=='__main__':unittest.main()
