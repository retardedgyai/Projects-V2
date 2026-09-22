import base64
import hashlib
import json
import unittest
from build_ice_fang import build, OUT, NAMES, ART, sections
from boss_models import validate_model


class IceFangTests(unittest.TestCase):
    def test_committed_assets_match_deterministic_authoring(self):
        for i,name in enumerate(NAMES):
            self.assertEqual(build(i),json.loads(OUT.with_name(name+'.bbmodel').read_text(encoding='utf-8')))

    def test_reference_paintings_are_preserved_byte_for_byte(self):
        expected={'rime-branches-v01.png':'d21fcade73f0b75bfcaecb0403df8e619d5e6afb53a3730b4338af368f44a1e2',
                  'rime-faces-v02.png':'456549430da2bdaf9bd48ae2898533b3e7e8b31268099ebb47648945c7accd83'}
        for name,digest in expected.items():
            raw=(ART/name).read_bytes()
            self.assertEqual(digest,hashlib.sha256(raw).hexdigest())
            for i in range(3):
                entry=next(t for t in build(i)['textures'] if t['name']==name)
                self.assertEqual(raw,base64.b64decode(entry['source'].split(',')[1]))

    def test_native_animation_and_budget(self):
        for i in range(3):
            model=build(i)
            cubes,bones,animations=validate_model(model)
            self.assertLessEqual(cubes,750) # Original contour art, not per-pixel entities.
            self.assertEqual(bones,4)
            self.assertEqual(animations,1)
            self.assertEqual(model['animations'][0]['length'],2.0)
            for track in model['animations'][0]['animators'].values():
                scales=[k for k in track['keyframes'] if k['channel']=='scale']
                self.assertEqual(scales[-1]['data_points'][0],dict(x=0,y=0,z=0))
                self.assertTrue(all(k['channel'] in ('position','scale') for k in track['keyframes']))

    def test_shapes_uvs_and_rotation_are_the_reference_not_a_new_approximation(self):
        for i in range(3):
            actual=build(i)['elements']
            j=0
            for part,mesh,*_ in sections(i):
                for source in mesh:
                    e=actual[j];j+=1
                    self.assertEqual(e['from'],[v-8 for v in source['from']])
                    self.assertEqual(e['to'],[v-8 for v in source['to']])
                    self.assertEqual(e['rotation'],[source['rotation'].get(a,0) for a in 'xyz'])
                    for face,value in source['faces'].items():
                        self.assertEqual(e['faces'][face]['uv'],value['uv'])

    def test_tall_branches_end_before_low_roots(self):
        model=build()
        tracks={t['name']:t for t in model['animations'][0]['animators'].values()}
        def scale(name,tick):
            return next(k['data_points'][0] for k in tracks[name]['keyframes']
                        if k['channel']=='scale' and k['time']==tick/20)
        self.assertEqual(scale('outer',29)['y'],0)
        self.assertGreater(scale('inner',29)['y'],0)
        self.assertGreater(scale('root',34)['y'],0)
        self.assertEqual(scale('root',39)['y'],0)


if __name__=='__main__':unittest.main()
