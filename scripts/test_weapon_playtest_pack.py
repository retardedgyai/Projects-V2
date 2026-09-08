"""Opt-in equipment integration and preservation of the installed non-weapon pack."""
import json
import unittest
import zipfile
from build_weapon_playtest_pack import ROOT, SERVER_JAR, OUT, PACK, FAMILIES, installed_pack, assemble, digest, valid_path


class WeaponPlaytestPackTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.report = json.loads((OUT/'report.json').read_text())
        cls.base = installed_pack()
        cls.files,cls.additions,cls.replacements = assemble(cls.base)

    def test_only_28_weapon_definitions_change_and_every_ui_and_armor_asset_is_preserved(self):
        changed = {p for p,data in self.base.items() if data!=self.files[p]}
        self.assertEqual(changed,set(self.replacements))
        self.assertEqual(len(changed),28)
        self.assertTrue(all('/items/weapons/' in p for p in changed))
        self.assertEqual(self.report['armor_changes'],0)
        for p,data in self.base.items():
            if p not in self.replacements:
                self.assertEqual(data,(PACK/p).read_bytes(),p)
        self.assertEqual(self.report['unchanged_installed_files'],len(self.base)-28)

    def test_all_existing_tier_weapon_ids_use_the_new_native_pose_graph(self):
        for family in FAMILIES:
            for tier in range(1,5):
                item = json.loads(self.files[f'assets/projects/items/weapons/{family}_t{tier}.json'])
                self.assertFalse(item['hand_animation_on_swap'])
                graph = item['model']['cases'][0]['model']
                self.assertEqual([e['threshold'] for e in graph['entries']],list(range(25)))
                for leaf in [graph['fallback'],*[e['model'] for e in graph['entries']]]:
                    model_path = 'assets/projects/models/'+leaf['model'].split(':')[1]+'.json'
                    self.assertIn(model_path,self.files)
                    model = json.loads(self.files[model_path])
                    for resource in model['textures'].values():
                        self.assertIn('assets/projects/textures/'+resource.split(':')[1]+'.png',self.files)
        self.assertFalse(self.report['tier_art_distinct'])
        self.assertEqual(self.report['distinct_tier_families'],['greatsword','dagger','staff'])
        for family in FAMILIES:
            appearances={self.files[f'assets/projects/items/weapons/{family}_t{tier}.json'] for tier in range(1,5)}
            self.assertEqual(len(appearances),4 if family in ('greatsword','dagger','staff') else 1)
        self.assertFalse(self.report['runtime_applied'])

    def test_index_report_zip_and_classpath_files_match_exactly(self):
        index = (PACK/'index.txt').read_text().splitlines()
        self.assertEqual(index,sorted(self.files))
        self.assertEqual(self.report['server_jar_sha256'],digest(SERVER_JAR.read_bytes()))
        for p,data in self.files.items():
            self.assertEqual(data,(PACK/p).read_bytes())
            self.assertEqual(digest(data),self.report['files_sha256'][p])
        with zipfile.ZipFile(OUT/'projects-weapon-playtest.zip') as archive:
            self.assertIsNone(archive.testzip())
            self.assertEqual(archive.namelist(),sorted(self.files))
            for p,data in self.files.items(): self.assertEqual(archive.read(p),data)

    def test_source_pack_stays_unmodified_and_collision_fails_closed(self):
        source_index = (ROOT/'server-minestom/src/main/resources/core-ui-pack/index.txt').read_text()
        for family in FAMILIES: self.assertNotIn('pixel_'+family,source_index)
        bad = dict(self.base)
        bad[self.additions[0]] = b'existing asset'
        with self.assertRaises(ValueError): assemble(bad)
        bad = dict(self.base)
        del bad['assets/projects/items/weapons/bow_t1.json']
        with self.assertRaises(ValueError): assemble(bad)
        for p in ('/outside','../outside','a\\b','a/../b'):
            self.assertFalse(valid_path(p))


if __name__=='__main__': unittest.main()
