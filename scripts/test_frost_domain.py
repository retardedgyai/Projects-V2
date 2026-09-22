import hashlib
import json
import tempfile
import unittest
from pathlib import Path
import numpy as np
from PIL import Image
from build_frost_domain import ROOT, INKS, floor_art, band_art, flake_art, erode, build_frost_domain


class FrostDomainTest(unittest.TestCase):
    def test_pixel_material_and_open_silhouette(self):
        for image in (floor_art(),band_art(),flake_art()):
            a=np.array(image);mask=a[:,:,3]>0
            self.assertEqual({0,255},set(a[:,:,3].flat))
            self.assertLess(mask.mean(),.4)
            self.assertGreater(mask.mean(),.08)
            self.assertTrue(set(map(tuple,a[mask,:3]))<=set(map(tuple,INKS)))
            self.assertFalse(mask[0].any() or mask[-1].any() or mask[:,0].any() or mask[:,-1].any())
        floor=np.array(floor_art())
        self.assertFalse(floor[44:52,44:52,3].any())

    def test_cluster_breakup_is_monotone_and_not_blur(self):
        for image in (floor_art(),band_art(),flake_art()):
            prior=np.array(image)[:,:,3]>0
            for i in range(1,8):
                mask=np.array(erode(image,i))[:,:,3]>0
                self.assertFalse((mask & ~prior).any())
                self.assertLess(mask.sum(),prior.sum())
                prior=mask

    def test_export_is_deterministic_native_planes_and_all_dependencies_ship(self):
        with tempfile.TemporaryDirectory() as temp:
            assets=Path(temp)
            def write(path,value):
                path.parent.mkdir(parents=True,exist_ok=True)
                path.write_text(json.dumps(value,separators=(',',':'))+'\n',encoding='utf-8')
            build_frost_domain(assets,write)
            files=sorted(p for p in assets.rglob('*') if p.is_file())
            self.assertEqual(72,len(files))
            shipped=ROOT/'server-minestom/src/main/resources/core-ui-pack/assets/projects'
            index=(shipped.parents[1]/'index.txt').read_text().splitlines()
            for p in files:
                relative=p.relative_to(assets)
                self.assertEqual(p.read_bytes(),(shipped/relative).read_bytes())
                self.assertIn('assets/projects/'+relative.as_posix(),index)
                if relative.parts[0]=='models':
                    model=json.loads(p.read_text())
                    for e in model['elements']:
                        self.assertEqual(e['from'][1],e['to'][1])
                        self.assertEqual({'up','down'},set(e['faces']))
                        self.assertTrue(all(-16<=x<=32 for x in e['from']+e['to']))
                        if 'rotation' in e: self.assertIn(e['rotation']['angle'],(-45,45))
                        self.assertEqual([0,16,16,0],e['faces']['up']['uv'])
                        self.assertEqual([0,0,16,16],e['faces']['down']['uv'])


if __name__=='__main__': unittest.main()
