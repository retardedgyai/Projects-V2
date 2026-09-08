import unittest
import numpy as np
from PIL import Image
from build_directional_slash_models import PACK,WINDOWS,mask_at,elements


class DirectionalSlashTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.alpha=np.asarray(Image.open(PACK/'assets/projects/textures/combat_vfx/ribbon/slash_5.png'))[:,:,3]>0

    def test_tip_advances_once_and_tail_clears_without_repainting_rotating_or_reappearing(self):
        frames=[mask_at(self.alpha,i) for i in range(12)]
        for i in range(4): self.assertFalse((frames[i]&~frames[i+1]).any())
        np.testing.assert_array_equal(frames[4],self.alpha)
        for i in range(4,11): self.assertFalse((frames[i+1]&~frames[i]).any())
        self.assertFalse(frames[-1].any())
        heads=[np.nonzero(m)[1].max() for m in frames[:5]]
        self.assertTrue(all(a<b for a,b in zip(heads,heads[1:])))

    def test_native_uv_faces_cover_exactly_the_selected_texels_in_both_directions(self):
        for frame in range(12):
            mask=mask_at(self.alpha,frame)
            for reverse in (False,True):
                cover=np.zeros_like(mask,dtype=int)
                for e in elements(mask,reverse):
                    self.assertNotIn('rotation',e)
                    self.assertEqual(e['from'][1],e['to'][1])
                    up=e['faces']['up']['uv']; down=e['faces']['down']['uv']
                    self.assertEqual(up,[down[0],down[3],down[2],down[1]])
                    x0,x1=round(e['from'][0]*4),round(e['to'][0]*4)
                    y0,y1=round((16-e['to'][2])*4),round((16-e['from'][2])*4)
                    cover[y0:y1,x0:x1]+=1
                np.testing.assert_array_equal(cover,mask[:,::-1] if reverse else mask)


if __name__=='__main__': unittest.main()
