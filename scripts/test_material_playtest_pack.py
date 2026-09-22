"""Equipment routing and byte-preservation checks; no game launch."""
import json
import unittest
import zipfile
from pathlib import Path
from tempfile import TemporaryDirectory
from build_pixel_armament_pack import definition
import build_material_playtest_pack as review


class MaterialPlaytestPackTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.base = review.installed_pack()
        cls.candidate = review.candidate_resources()
        cls.files = review.assemble(cls.base, cls.candidate)
        cls.temp = TemporaryDirectory(prefix='projects-material-pack-')
        cls.addClassCleanup(cls.temp.cleanup)
        cls.output = Path(cls.temp.name)
        review.build(cls.output)
        cls.report = json.loads((cls.output / 'report.json').read_text())

    def test_exactly_one_existing_equipment_graph_changes(self):
        changed = {name for name, data in self.base.items() if self.files[name] != data}
        self.assertEqual(changed, {review.TARGET})
        self.assertEqual(self.report['replaced_item_definitions'], [review.TARGET])
        self.assertEqual(self.report['unchanged_installed_files'], len(self.base) - 1)
        self.assertFalse(self.report['runtime_applied'])
        self.assertFalse(self.report['quality_approved'])
        self.assertEqual(len(self.candidate), 32)

    def test_all_hand_pose_states_resolve_with_same_animated_layer(self):
        item = json.loads(self.files[review.TARGET])
        self.assertEqual(item, definition(review.KEY))
        graph = item['model']['cases'][0]['model']
        self.assertEqual([e['threshold'] for e in graph['entries']], list(range(25)))
        display = None
        effects = None
        gem_poses = set()
        for leaf in [graph['fallback'], *[e['model'] for e in graph['entries']]]:
            path = 'assets/projects/models/' + leaf['model'].split(':')[1] + '.json'
            model = json.loads(self.files[path])
            if display is None:
                display = model['display']
            self.assertEqual(model['display'], display)
            for reference in model['textures'].values():
                self.assertIn('assets/projects/textures/' + reference.split(':')[1] + '.png', self.files)
            current = [e for e in model['elements'] if e['name'].startswith('blade_embers:')]
            self.assertTrue(current)
            if effects is None:
                effects = current
            self.assertEqual(current, effects)
            gem_poses.add(json.dumps([e for e in model['elements'] if e['name'].startswith('jewel:')]))
        self.assertGreater(len(gem_poses), 5)
        meta = json.loads(self.files[f'assets/projects/textures/item/weapons/{review.MODEL_NAME}_embers.png.mcmeta'])
        self.assertEqual(meta['animation']['frames'], list(range(24)))
        self.assertFalse(meta['animation']['interpolate'])

    def test_saved_snapshot_index_zip_and_source_hash_are_exact(self):
        self.assertEqual(self.report['server_jar_sha256'], review.digest(review.SERVER_JAR.read_bytes()))
        self.assertEqual((self.output / 'core-ui-pack/index.txt').read_text().splitlines(), sorted(self.files))
        with zipfile.ZipFile(self.output / 'projects-material-playtest.zip') as archive:
            self.assertEqual(archive.namelist(), sorted(self.files))
            self.assertIsNone(archive.testzip())
            for name, data in self.files.items():
                self.assertEqual(archive.read(name), data, name)
                self.assertEqual((self.output / 'core-ui-pack' / name).read_bytes(), data, name)
                self.assertEqual(self.report['files_sha256'][name], review.digest(data), name)

    def test_energy_material_keeps_native_light_in_all_twenty_five_poses(self):
        models=[json.loads(data) for name,data in self.candidate.items() if '/models/' in name]
        self.assertEqual(25,len(models))
        for model in models:
            found=set()
            for e in model['elements']:
                name=e['name']
                expected=(15 if name.startswith('blade_embers:') else
                          12 if name.startswith('blade:') else 9 if name.startswith('jewel:') else 0)
                self.assertEqual(expected,e.get('light_emission',0),name)
                found.add(expected)
            self.assertEqual({0,9,12,15},found)

    def test_lighting_does_not_repaint_or_modify_geometry_uvs_and_display(self):
        from copy import deepcopy
        from build_blade_ember_study import source,effect_frames,animated_model,material_lighting
        entry,base,textures=source(True)
        frames=effect_frames(textures['body'],entry['ember_emitters'])
        original=animated_model(entry,base,textures,frames)
        before=deepcopy(original)
        lit=material_lighting(original)
        self.assertEqual(original,before)
        for e in lit['elements']: e.pop('light_emission',None)
        self.assertEqual(original,lit)

    def test_missing_assets_collisions_and_scope_expansion_fail_closed(self):
        bad = dict(self.candidate)
        bad.pop(next(iter(bad)))
        with self.assertRaises(ValueError):
            review.assemble(self.base, bad)
        bad = {**self.candidate, 'assets/projects/textures/ui/accidental.png': b'bad'}
        with self.assertRaises(ValueError):
            review.assemble(self.base, bad)
        bad_base = {**self.base, next(iter(self.candidate)): b'existing'}
        with self.assertRaises(ValueError):
            review.assemble(bad_base, self.candidate)
        bad_base = dict(self.base)
        bad_base.pop(review.TARGET)
        with self.assertRaises(ValueError):
            review.assemble(bad_base, self.candidate)


if __name__ == '__main__':
    unittest.main()
