"""Exact source-asset lock for the user-supplied GIF; no subjective similarity score."""
import hashlib
import json
from pathlib import Path
import unittest
from build_approved_dash_v3 import PACK, build

MANIFEST = Path(__file__).with_name('approved_dash_v3_manifest.json')


class ApprovedDashV3Test(unittest.TestCase):
    def test_every_restored_resource_matches_the_original_git_blob(self):
        manifest = json.loads(MANIFEST.read_text(encoding='utf-8'))
        for name, expected in manifest['gitBlobs'].items():
            current = name.replace('combat_vfx/greatsword/', 'combat_vfx/approved_dash_v3/')
            data = (PACK / current).read_bytes()
            if current.endswith('.json'): data = data.replace(b'\r\n', b'\n')
            data = data.replace(b'combat_vfx/approved_dash_v3/', b'combat_vfx/greatsword/')
            digest = hashlib.sha1(b'blob ' + str(len(data)).encode() + b'\0' + data).hexdigest()
            self.assertEqual(expected, digest, name)

    def test_generation_and_pack_index_keep_the_approved_models(self):
        generated = {}
        build(PACK / 'assets/projects', lambda p, v: generated.__setitem__(p, v))
        self.assertEqual(70, len(generated))
        index = (PACK / 'index.txt').read_text(encoding='utf-8').splitlines()
        for path, value in generated.items():
            self.assertEqual(value, json.loads(path.read_text(encoding='utf-8')), str(path))
            self.assertIn(path.relative_to(PACK).as_posix(), index)


if __name__ == '__main__':
    unittest.main()
