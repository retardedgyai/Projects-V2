"""Native articulation/geometry/resource contracts, not reference art approval."""
from copy import deepcopy
import json
import math
import unittest
import zipfile
import numpy as np
from PIL import Image
from build_specialist_armament_pack import (ASSETS, OUT, PACK, SOURCE, JOBS,
    geometry, pose, motion, bow_points, definition)
from preview_class_armaments import rotated


class SpecialistPackTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.manifest = json.loads((SOURCE/'manifest.json').read_text())
        cls.built = {key:geometry(key,entry) for key,entry in cls.manifest['weapons'].items()}

    def model(self,key,stage='rest',frame=0):
        suffix = '' if stage=='rest' else f'_{stage}{frame:02d}'
        return json.loads((ASSETS/f'models/item/weapons/pixel_{key}{suffix}.json').read_text())

    def test_all_100_exported_models_match_authored_poses_without_mutating_inputs(self):
        for key,(base,parts,textures,anchors) in self.built.items():
            before = deepcopy((base,parts,anchors))
            rest = self.model(key)
            self.assertEqual(self.model(key,'release',5),rest)
            self.assertEqual(self.model(key,'idle',0),rest)
            self.assertEqual(self.model(key,'prepare',5),self.model(key,'release',0))
            for stage,count in (('rest',1),('idle',12),('prepare',6),('release',6)):
                for frame in range(count):
                    expected = pose(key,base,parts,anchors,stage,frame)
                    self.assertEqual(self.model(key,stage,frame),expected)
            self.assertEqual((base,parts,anchors),before)
            self.assertNotEqual(self.model(key,'prepare',5),rest)

    def test_native_geometry_has_no_filled_cubes_or_invalid_uv_and_rotation(self):
        for path in (ASSETS/'models/item/weapons').glob('*.json'):
            model = json.loads(path.read_text())
            for e in model['elements']:
                self.assertLessEqual(len(e['faces']),2)
                self.assertTrue(all(math.isfinite(a) and math.isfinite(b) and -16<=a<=b<=32
                    for a,b in zip(e['from'],e['to'])),(path.name,e))
                for f in e['faces'].values():
                    self.assertTrue(all(math.isfinite(v) and 0<=v<=16 for v in f['uv']))
                    self.assertIn(f['texture'][1:],model['textures'])
                if 'rotation' in e:
                    r = e['rotation']
                    self.assertIn(r['axis'],('x','y','z'))
                    self.assertTrue(-45<=r['angle']<=45)
                    self.assertFalse(r['rescale'])
                    self.assertTrue(all(math.isfinite(v) for v in r['origin']))

    def test_bow_string_stays_connected_at_both_tips_and_nock_through_draw_and_release(self):
        base,parts,textures,anchors = self.built['bow']
        nock_positions = []
        for stage in ('prepare','release'):
            for frame in range(6):
                model = self.model('bow',stage,frame)
                charge,_ = motion(stage,frame)
                tips,nock = bow_points(anchors,charge)
                if stage=='prepare': nock_positions.append(nock[0])
                for label,tip in zip(('upper','lower'),tips):
                    string = next(e for e in model['elements'] if e['name']=='string_'+label)
                    center = (np.array(string['from'])+string['to'])/2
                    low,high = center.copy(),center.copy()
                    low[1] = string['from'][1]; high[1] = string['to'][1]
                    ends = [rotated(p,string['rotation']) for p in (low,high)]
                    self.assertLess(min(np.linalg.norm(p-tip) for p in ends),1e-5)
                    self.assertLess(min(np.linalg.norm(p-nock) for p in ends),1e-5)
                    limb = next(e for e in model['elements'] if e['name'].startswith(label+'_limb:'))
                    np.testing.assert_allclose(rotated(np.array(anchors[label+'_tip']),limb.get('rotation')),tip,atol=1e-5)
                self.assertEqual([e for e in model['elements'] if e['name'].startswith('grip:')],parts['grip'])
        self.assertTrue(all(a>b for a,b in zip(nock_positions,nock_positions[1:])))
        self.assertGreater(nock_positions[0]-nock_positions[-1],4)

    def test_pages_hinge_at_binding_over_a_static_unillustrated_backing(self):
        base,parts,textures,anchors = self.built['tome']
        prepared = self.model('tome','prepare',5)
        expected = {'left_page':-36,'right_page':24}
        for name,angle in expected.items():
            pages = [e for e in prepared['elements'] if e['name'].startswith(name+':')]
            self.assertTrue(pages)
            for e in pages:
                self.assertEqual(e['rotation']['axis'],'y')
                self.assertEqual(e['rotation']['angle'],angle)
                self.assertEqual(e['rotation']['origin'],anchors['hinge'])
        for name in ('binding_cover','page_backing'):
            self.assertEqual([e for e in prepared['elements'] if e['name'].startswith(name+':')],parts[name])
        backing = textures['page_backing']
        self.assertTrue((backing[backing[:,:,3]>0,:3]<100).all())

    def test_astral_ring_and_star_counter_rotate_while_shaft_stays_fixed(self):
        base,parts,textures,anchors = self.built['astrolabe']
        prepared = self.model('astrolabe','prepare',5)
        self.assertEqual([e for e in prepared['elements'] if e['name'].startswith('shaft:')],parts['shaft'])
        ring = next(e for e in prepared['elements'] if e['name'].startswith('ring:'))
        star = next(e for e in prepared['elements'] if e['name'].startswith('star:'))
        self.assertEqual(ring['rotation']['angle'],32)
        self.assertEqual(star['rotation']['angle'],-40)
        self.assertAlmostEqual(star['rotation']['origin'][2],anchors['star'][2]-.7)
        self.assertEqual(ring['rotation']['origin'],anchors['star'])

    def test_pack_graph_and_byte_exact_textures_are_complete_without_live_registration(self):
        for key in JOBS:
            item = json.loads((ASSETS/f'items/weapons/pixel_{key}.json').read_text())
            self.assertEqual(item,definition(key))
            graph = item['model']['cases'][0]['model']
            self.assertEqual([e['threshold'] for e in graph['entries']],list(range(25)))
            for leaf in [graph['fallback'],*[entry['model'] for entry in graph['entries']]]:
                resource = leaf['model'].split(':')[1]
                self.assertTrue((ASSETS/f'models/{resource}.json').is_file())
            for part in self.manifest['weapons'][key]['parts']:
                target = ASSETS/f'textures/item/weapons/pixel_{key}_{part}.png'
                self.assertEqual(target.read_bytes(),(SOURCE/f'{key}-{part}.png').read_bytes())
            for resource in self.model(key)['textures'].values():
                self.assertTrue((ASSETS/f'textures/{resource.split(":")[1]}.png').is_file())
            for hand in ('firstperson','thirdperson'):
                self.assertIn(hand+'_lefthand',self.model(key)['display'])
                self.assertIn(hand+'_righthand',self.model(key)['display'])
        with zipfile.ZipFile(OUT/'projects-specialist-armaments-review.zip') as archive:
            self.assertIsNone(archive.testzip())
            self.assertEqual(set(archive.namelist()),{p.relative_to(PACK).as_posix() for p in PACK.rglob('*') if p.is_file()})
            self.assertFalse(any(p.startswith('assets/minecraft/') for p in archive.namelist()))
        report = json.loads((OUT/'report.json').read_text())
        self.assertFalse(report['runtime_applied']); self.assertTrue(report['native_models_exported'])
        index = (SOURCE.parents[3]/'server-minestom/src/main/resources/core-ui-pack/index.txt').read_text()
        for key in JOBS: self.assertNotIn('pixel_'+key,index)

    def test_invalid_animation_requests_are_rejected(self):
        for stage,frame in (('idle',12),('prepare',-1),('release',6),('rest',1),('attack',0)):
            with self.assertRaises(ValueError): motion(stage,frame)


if __name__=='__main__': unittest.main()
