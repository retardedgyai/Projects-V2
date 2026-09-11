import json
import unittest
import numpy as np
from build_warrior_flourish import CLIPS, FRAMES, PACK, build, contour


class WarriorFlourishTest(unittest.TestCase):
    def test_all_native_models_and_items_resolve_and_rebuild_exactly(self):
        assets=PACK/'assets/projects'
        index=set((PACK/'index.txt').read_text(encoding='utf-8').splitlines())
        counts=[]
        def check(path,value):
            self.assertEqual(value,json.loads(path.read_text(encoding='utf-8')))
            self.assertIn(path.relative_to(PACK).as_posix(),index)
            for texture in value.get('textures',{}).values():
                self.assertTrue((assets/('textures/'+texture.split(':')[1]+'.png')).is_file())
            if 'elements' in value:
                counts.append(len(value['elements']))
                for e in value['elements']:
                    self.assertTrue(all(-16<=n<=32 for n in e['from']+e['to']))
                    if 'rotation' in e: self.assertIn(e['rotation']['angle'],(-45,-22.5,0,22.5,45))
        build(assets,check)
        self.assertEqual(len(CLIPS)*FRAMES*2,len(counts))
        self.assertLessEqual(max(counts),420)

    def test_middle_shapes_have_body_then_separate_tails_not_repeated_full_slashes(self):
        signatures=[]
        for clip in CLIPS:
            frames=[contour(clip,t) for t in range(FRAMES)]
            coverage=[np.count_nonzero(f) for f in frames]
            self.assertFalse(np.any(frames[-1]))
            self.assertGreater(max(coverage),90,clip)
            self.assertLess(coverage[-2],max(coverage)*.6,clip)
            self.assertGreaterEqual(len({f.tobytes() for f in frames}),12,clip)
            self.assertLess(sum(np.count_nonzero(contour(clip,t,True)) for t in range(FRAMES)),sum(coverage)*.5,clip)
            signatures.append(b''.join(f.tobytes() for f in frames))
        self.assertEqual(len(CLIPS),len(set(signatures)))


if __name__=='__main__': unittest.main()
