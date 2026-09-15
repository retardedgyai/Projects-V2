import json
import unittest
import numpy as np
from build_warrior_flourish import CLIPS, FRAMES, PACK, build, contour
from warrior_identity_contours import CLIPS as IDENTITY_CLIPS


class WarriorFlourishTest(unittest.TestCase):
    def test_major_skills_have_readable_large_shapes_and_leave_negative_space(self):
        # Native 64x64 drawing-space coverage, not a claim about game screen area.
        # Protect against reverting the hero silhouette into tiny flecks.
        minimum={'stone_spall':500,'sweep_pressure':450,'reversal':700,
                 'pierce_shell':380,'voice_compression':400,'ultimate_rise':800,
                 'ultimate_cross':550,'ultimate_fall':800}
        for clip,area in minimum.items():
            peak=max(np.count_nonzero(contour(clip,t)) for t in range(FRAMES))
            self.assertGreaterEqual(peak,area,clip)
            self.assertLess(peak,64*64*.35,clip)

    def test_dedicated_shapes_do_not_get_cut_off_at_the_drawing_boundary(self):
        for clip in IDENTITY_CLIPS:
            for frame in range(FRAMES):
                g=contour(clip,frame)
                self.assertFalse(np.any(g[0]) or np.any(g[-1]) or np.any(g[:,0]) or np.any(g[:,-1]),(clip,frame))

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
            # Guard glint/thread and the small flag-pole foot must not be
            # inflated to the coverage of a room-clearing pressure surface.
            minimum={'guard_edge':20,'cut_thread':20,'point_load':20,'standard_foot':50}.get(clip,90)
            self.assertGreater(max(coverage),minimum,clip)
            self.assertLess(coverage[-2],max(coverage)*.6,clip)
            self.assertGreaterEqual(len({f.tobytes() for f in frames}),12,clip)
            self.assertLess(sum(np.count_nonzero(contour(clip,t,True)) for t in range(FRAMES)),sum(coverage)*.5,clip)
            signatures.append(b''.join(f.tobytes() for f in frames))
        self.assertEqual(len(CLIPS),len(set(signatures)))

    def test_skill_identity_is_geometry_not_names_tints_or_reversed_common_frames(self):
        # Detect the old wound/sweep and dash/thrust mistake at the source level.
        drawings={c:np.stack([contour(c,t)>0 for t in range(FRAMES)]) for c in IDENTITY_CLIPS}
        for i,a in enumerate(IDENTITY_CLIPS):
            for b in IDENTITY_CLIPS[i+1:]:
                x,y=drawings[a],drawings[b]
                self.assertFalse(np.array_equal(x,y),(a,b))
                self.assertFalse(np.array_equal(x,y[:,:,::-1]),(a,b,'mirrored'))
                union=np.count_nonzero(x|y)
                self.assertLess(np.count_nonzero(x&y)/max(1,union),.8,(a,b))

    def test_dust_has_no_red_light_and_contact_flash_dies_without_restarting(self):
        for c in ('step_dust','impact_dust','sweep_dust'):
            self.assertFalse(any(np.any(contour(c,t,True)) for t in range(FRAMES)))
            self.assertTrue(all(set(np.unique(contour(c,t)))<={0,1,2} for t in range(FRAMES)))
        contact=[np.count_nonzero(contour('parry_metal',t)) for t in range(FRAMES)]
        self.assertGreater(contact[0],90)
        self.assertEqual(0,contact[-1])

    def test_counter_pressure_leads_opposite_to_wound_not_a_rescaled_fan(self):
        for frame in (2,3):
            self.assertLess(np.nonzero(contour('fan',frame))[1].mean(),32)
            self.assertGreater(np.nonzero(contour('counter',frame))[1].mean(),32)


if __name__=='__main__': unittest.main()
