from pathlib import Path
from datetime import datetime
import json, hashlib, bisect
from collections import Counter
from PIL import Image, ImageDraw, ImageFont
import numpy as np

R=Path(__file__).resolve().parents[1]
O=R/'outputs/actual_game_local_autoplay'
D=R/'work/actual_game_local_autoplay/captured_single_side_case'
def ts(s): return datetime.fromisoformat(s.replace('Z','+00:00')).timestamp()
frames=[json.loads(s) for s in (D/'CAPTURE_FRAME_TIMES.jsonl').read_text().splitlines()]
trace=[json.loads(s) for s in (O/'ACTUAL_GAME_OWNER_TRACE.jsonl').read_text().splitlines()]
times=[ts(f['wall_utc']) for f in frames]
stage_names={1:'READY',2:'INITIAL REENTRY',3:'INITIAL WALK',4:'CONTACT STOP / CLAW',5:'READY AFTER CLAW',6:'MOVING FIRST STEP',7:'RESUMED WALK',8:'SUPPORTED STOP',9:'FINAL READY'}
stage_first={}
state_first={}
for r in trace:
 stage_first.setdefault(r['stage'],r)
 state_first.setdefault(r['snapshot']['state'],r)
protected=json.loads((O/'PROTECTED_BEFORE_GAME_TEST.json').read_text())
checks={p:{'before':h,'after':hashlib.sha256((R/p).read_bytes()).hexdigest()} for p,h in protected.items()}
for v in checks.values():v['unchanged']=v['before']==v['after']
assert all(v['unchanged'] for v in checks.values())
(O/'PROTECTED_AFTER_GAME_TEST.json').write_text(json.dumps(checks,indent=2))

def nearest(t):return min(range(len(times)),key=lambda i:abs(times[i]-t))
def crop(i):
 im=Image.open(D/f'{i:05d}.png').convert('RGB')
 return im.crop((8,31,im.width-8,im.height-8))
review=[]
for stage in [1,2,4,5,6,7,8,9]:
 row=stage_first[stage]
 offset={1:.3,2:1.25,4:2.0,5:.3,6:1.50,7:1.5,8:.7,9:.3}[stage]
 i=nearest(ts(row['wall_utc'])+offset)
 im=crop(i)
 path=O/f'ACTUAL_GAME_{stage:02d}_{stage_names[stage].replace(" / ","_").replace(" ","_")}.png'
 im.save(path)
 a=np.asarray(im)
 review.append({'stage':stage,'label':stage_names[stage],'frame':i,'wall_utc':frames[i]['wall_utc'],'std_rgb':float(a.std()),'image':path.name})
font_path=Path('C:/Windows/Fonts/arial.ttf')
font=ImageFont.truetype(str(font_path),22)
small=ImageFont.truetype(str(font_path),17)
sheet=Image.new('RGB',(1280,4*402+76),(19,25,33))
draw=ImageDraw.Draw(sheet)
draw.text((20,10),'ACTUAL VANILLA 26.2 / LOCAL WSEE PACK / VISUAL REVIEW: NOT PASSED',font=font,fill=(244,245,248))
draw.text((20,42),'Existing 14 clips unchanged. Screen captures, no synthetic renderer. Foot / limb gaps remain.',font=small,fill=(235,179,104))
for n,r in enumerate(review):
 im=Image.open(O/r['image']);im=im.resize((632,356),Image.Resampling.NEAREST)
 x=(n%2)*640;y=76+(n//2)*402
 sheet.paste(im,(x,y+36));draw.text((x+8,y+8),f'{r["label"]} | frame {r["frame"]}',font=small,fill='white')
sheet.save(O/'dragon_ACTUAL_GAME_SINGLE_SIDE_CASE_VISUAL_NOT_PASSED.png')

first=nearest(ts(stage_first[1]['wall_utc']))
last=nearest(ts(trace[-1]['wall_utc']))
chosen=list(range(first,last+1))
# GIF centiseconds are accumulated from actual UTC timestamps (not nominal FPS).
boundaries=[round((times[i]-times[first])*100) for i in chosen]
end_cs=round((times[last]-times[first]+np.median(np.diff(times)))*100)
durations=[10*(b-a) for a,b in zip(boundaries,boundaries[1:]+[end_cs])]
assert min(durations)>0
animation=[]
trace_times=[ts(t['wall_utc']) for t in trace]
for i in chosen:
 j=max(0,bisect.bisect_right(trace_times,times[i])-1)
 row=trace[j]
 im=crop(i).resize((948,533),Image.Resampling.NEAREST)
 canvas=Image.new('RGB',(948,575),(19,25,33));canvas.paste(im,(0,42))
 dr=ImageDraw.Draw(canvas)
 dr.text((10,4),f'ACTUAL GAME | {row["snapshot"]["state"]} | elapsed {times[i]-times[first]:.2f}s',font=small,fill='white')
 dr.text((10,23),'Single finite case / real captured timing / appearance NOT approved',font=small,fill=(235,179,104))
 animation.append(canvas.quantize(colors=256,method=Image.Quantize.MEDIANCUT))
gif=O/'dragon_ACTUAL_GAME_STOP_CLAW_REENTRY_WALK_NOT_VISUAL_PASS.gif'
animation[0].save(gif,save_all=True,append_images=animation[1:],duration=durations,disposal=2,optimize=False)
with Image.open(gif) as g:
 ds=[]
 for i in range(g.n_frames):g.seek(i);ds.append(g.info.get('duration',0))
 assert sum(ds)==sum(durations)
 assert g.n_frames==len(animation)
 assert 'loop' not in g.info
audit={
 'actual_game_client':'Vanilla 26.2','local_pack_loaded': 'SUCCESSFULLY_LOADED' in (O/'LAB_STDOUT.txt').read_text(),
 'finite_case_completed':json.loads((O/'AUTO_CASE_COMPLETE.json').read_text()),
 'server_trace_samples':len(trace),'stages':[{ 'stage':k,'label':stage_names.get(k),'first_wall_utc':v['wall_utc'],'first_tick':v['tick']} for k,v in stage_first.items()],
 'states':dict(Counter(t['snapshot']['state'] for t in trace)),
 'actor_start':trace[0]['snapshot']['actor'],'actor_final':trace[-1]['snapshot']['actor'],
 'actual_actor_root_distance_blocks':trace[-1]['snapshot']['actor']['z']-trace[0]['snapshot']['actor']['z'],
 'saved_frames':len(frames),'saved_frame_timestamp_span_seconds':times[-1]-times[0],
 'capture_stopwatch_final_seconds':frames[-1]['elapsed_ms']/1000,
 'capture_mode':'Graphics.CopyFromScreen of owned visible secondary-monitor rectangle',
 'capture_raw_PrintWindow_ok_field_is_generic_capture_success':True,
 'foreground_owned_frames':sum(f['foreground_is_owned'] for f in frames),
 'no_SendKeys_SendInput_or_SetForegroundWindow_called':True,
 'launch_and_SetWindowPos_requested_no_activation':True,
 'foreground_note':'Application was nevertheless foreground in all captured frames; passive capture did not activate it.',
 'review_samples':review,'protected_file_count':len(checks),'all_protected_sources_unchanged':True,
 'gif':{'file':gif.name,'bytes':gif.stat().st_size,'frames':len(animation),'duration_ms':sum(durations),'source_first_frame':first,'source_last_frame':last,'nearest_crop_resize':True,'loop':'unspecified; finite case'},
 'visual_pass':False,'performance_pass':False,
 'cleanup':json.loads((O/'OWNED_FINAL_CLEANUP.json').read_text()),
 'limits':['Daylight sky appears in current side case; body is still very dark.', 'Feet and limb segments show visible gaps in actual game. Cause not established.', 'No claimed MatE comparison; referenced X pages returned 403.', 'Single client / one boss is not MMO load or VRAM measurement.']
}
(O/'ACTUAL_GAME_CAPTURE_AUDIT.json').write_text(json.dumps(audit,indent=2),encoding='utf8')
print(json.dumps(audit,indent=2))
