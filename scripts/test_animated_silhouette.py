"""Moving texture fragments must have a native side at every frame's boundary."""
import unittest
import numpy as np
from build_texture_first_sword import compile_model
from build_blade_ember_study import source, effect_frames, WIDTH, HEIGHT, PAD_X
from pixel_weapon_display import grip_pixels


class AnimatedSilhouetteTest(unittest.TestCase):
    def spec(self,w,h,pivot=0,height=None):
        return {'height':height or h,'top_pixel':0,'bottom_pixel':h,'pivot_pixel_x':pivot,
                'alpha_cutoff':128,'texture':'projects:item/test',
                'parts':[{'name':'effect','rows':[0,h],'thickness':.2,'trace_painted_faces':True}]}

    def boundaries(self,model,w,h):
        covered={direction:np.zeros((h,w),bool) for direction in ('west','east','up','down')}
        for e in model['elements']:
            if len(e['faces'])!=1: continue
            direction,face=next(iter(e['faces'].items()))
            u0,v0,u1,v1=face['uv']
            if direction in ('west','east'):
                x=round(u0*w/16-.5)
                covered[direction][round(v0*h/16):round(v1*h/16),x]=True
            else:
                y=round(v0*h/16-.5)
                covered[direction][y,round(u0*w/16):round(u1*w/16)]=True
        return covered

    def expected(self,mask):
        padded=np.pad(mask,1)
        return {name:mask & ~adj for name,adj in
                {'west':padded[1:-1,:-2],'east':padded[1:-1,2:],
                 'up':padded[:-2,1:-1],'down':padded[2:,1:-1]}.items()}

    def test_moving_pixel_exposes_internal_union_edge_regression(self):
        frames=[]
        for x in (2,3,4):
            a=np.zeros((8,8),np.uint8);a[3,x]=255;frames.append(a)
        union=np.maximum.reduce(frames);spec=self.spec(8,8)
        legacy,_=compile_model(spec,union)
        actual,_=compile_model(spec,union,boundary_frames=frames)
        old=self.boundaries(legacy,8,8);new=self.boundaries(actual,8,8)
        self.assertFalse(old['east'][3,2])
        self.assertFalse(old['west'][3,3])
        for a in frames:
            for direction,expected in self.expected(a>0).items():
                self.assertFalse((expected & ~new[direction]).any())
        # Broad painted faces and their UVs don't change at all.
        self.assertEqual([e for e in legacy['elements'] if len(e['faces'])==2],
                         [e for e in actual['elements'] if len(e['faces'])==2])

    def test_both_actual_sword_sources_cover_every_frame_boundary_without_pixel_cubes(self):
        for redraw in (False,True):
            entry,_,textures=source(redraw)
            from build_blade_ember_study import EMITTERS
            frames=effect_frames(textures['body'],entry.get('ember_emitters',EMITTERS))
            alphas=[f[:,:,3] for f in frames]
            pivot,_=grip_pixels('greatsword',entry,textures)
            scale=entry['height']/(entry['rows'][-1]-entry['rows'][0])
            model,_=compile_model(self.spec(WIDTH,HEIGHT,pivot+PAD_X,HEIGHT*scale),
                                  np.maximum.reduce(alphas),boundary_frames=alphas)
            covered=self.boundaries(model,WIDTH,HEIGHT)
            for frame,a in enumerate(alphas):
                for direction,expected in self.expected(a>0).items():
                    self.assertFalse((expected & ~covered[direction]).any(),f'{redraw} frame={frame} {direction}')
            for direction in covered:
                expected_union=np.logical_or.reduce([self.expected(a>0)[direction] for a in alphas])
                np.testing.assert_array_equal(covered[direction],expected_union)
            self.assertTrue(all(len(e['faces'])<=2 for e in model['elements']))
            self.assertTrue(all(abs(e['to'][2]-e['from'][2]-.2)<1e-6 for e in model['elements']))

    def test_reject_mismatched_animation(self):
        a=np.zeros((8,8),np.uint8);a[3,2]=255
        for frames in ([],[np.zeros((4,4),np.uint8)],[np.zeros((8,8),np.uint8)]):
            with self.assertRaises(ValueError): compile_model(self.spec(8,8),a,boundary_frames=frames)


if __name__=='__main__': unittest.main()
