import unittest
from unittest.mock import patch
from PIL import Image
from preview_skill_choreography import clip_near, render


class WarriorPreviewTest(unittest.TestCase):
    def test_near_plane_clips_crossing_faces_without_stretching_back_vertices(self):
        face=[(-1,0,-1),(1,0,-1),(1,1,1),(-1,1,1)]
        clipped=clip_near(face)
        self.assertEqual(4,len(clipped))
        self.assertTrue(all(p[2]>=-.6000001 for p in clipped))
        self.assertEqual(2,sum(abs(p[2]+.6)<1e-8 for p in clipped))
        self.assertEqual([],clip_near([(x,y,-1) for x,y,_ in face]))
        front=[(x,y,1) for x,y,_ in face]
        self.assertEqual(front,clip_near(front))

    def test_behind_camera_painted_face_does_not_cover_the_screen(self):
        model={'textures':{'0':'projects:test'},'elements':[{
            'from':[0,0,8],'to':[16,16,8],
            'faces':{name:{'texture':'#0','uv':[0,0,16,16]} for name in ('north','south')}}]}
        p={'model':'test','offset':[0,1,-2],'scale':[3,3,3],'pitch':0,'yaw':0,'roll':0}
        with patch('preview_skill_choreography.model_for',return_value=(model,(0xffffff,))), \
             patch('preview_skill_choreography.texture_for',return_value=Image.new('RGBA',(16,16),'red')):
            self.assertEqual(render([],"test",0,'eye').tobytes(),render([p],"test",0,'eye').tobytes())


if __name__=='__main__': unittest.main()
