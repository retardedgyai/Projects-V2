import json
import unittest
import numpy as np
from build_warrior_companions import PACK, build, contour


class WarriorCompanionsTest(unittest.TestCase):
    def test_compiled_models_resolve_and_match_native_sources(self):
        assets=PACK/'assets/projects'
        checked=[]
        index=set((PACK/'index.txt').read_text(encoding='utf-8').splitlines())
        def check(path,value):
            self.assertEqual(value,json.loads(path.read_text(encoding='utf-8')))
            self.assertIn(path.relative_to(PACK).as_posix(),index)
            for texture in value.get('textures',{}).values():
                self.assertTrue((assets/('textures/'+texture.split(':')[1]+'.png')).is_file())
            for element in value.get('elements',[]):
                self.assertEqual(element['from'][1],element['to'][1])
                self.assertTrue(all(float(v*4).is_integer() for v in element['from']+element['to']))
            checked.append(path)
        build(assets,check)
        self.assertEqual(129,len(checked))

    def test_contours_end_empty_and_boundary_has_no_filled_disc(self):
        for kind in ('wind','spark','chip','boundary'):
            self.assertGreater(np.count_nonzero(contour(kind,0)),0)
            self.assertFalse(np.any(contour(kind,7)))
        yy,xx=np.indices((64,64))
        interior=(xx+.5-32)**2+(yy+.5-32)**2 < 26**2
        self.assertFalse(np.any(contour('boundary',0)[interior]))


if __name__=='__main__': unittest.main()
