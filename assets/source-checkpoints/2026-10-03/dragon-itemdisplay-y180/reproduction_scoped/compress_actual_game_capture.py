from pathlib import Path
from PIL import Image
import json,hashlib
R=Path(__file__).resolve().parents[1];O=R/'outputs/actual_game_local_autoplay'
src=O/'dragon_ACTUAL_GAME_STOP_CLAW_REENTRY_WALK_NOT_VISUAL_PASS.gif'
source_sha=hashlib.sha256(src.read_bytes()).hexdigest();frames=[];dur=[]
with Image.open(src) as im:
 for i in range(im.n_frames):im.seek(i);frames.append(im.convert('RGB'));dur.append(im.info['duration'])
dest=O/'dragon_ACTUAL_GAME_FINITE_CASE_lightweight_REAL_TIMING_NOT_VISUAL_PASS.gif'
for width in [640,512]:
 size=(width,round(frames[0].height*width/frames[0].width))
 small=[f.resize(size,Image.Resampling.NEAREST) for f in frames]
 atlas=Image.new('RGB',(width,size[1]*8))
 for j in range(8):atlas.paste(small[round(j*(len(small)-1)/7)],(0,j*size[1]))
 palette=atlas.quantize(colors=192,method=Image.Quantize.MEDIANCUT)
 encoded=[f.quantize(palette=palette,dither=Image.Dither.NONE) for f in small]
 encoded[0].save(dest,save_all=True,append_images=encoded[1:],duration=dur,optimize=True,disposal=1)
 if dest.stat().st_size<10_000_000:break
with Image.open(dest) as g:
 got=[]
 for i in range(g.n_frames):g.seek(i);g.load();got.append(g.info['duration'])
 assert got==dur and g.n_frames==len(frames) and 'loop' not in g.info
 assert dest.stat().st_size<10_000_000
assert hashlib.sha256(src.read_bytes()).hexdigest()==source_sha
report={'file':dest.name,'bytes':dest.stat().st_size,'resolution':size,'frames':len(frames),'duration_ms':sum(got),'per_frame_duration_matches_actual_source_gif':True,'source_sha256_unchanged':source_sha,'new_render':False,'visual_pass':False,'loop':'unspecified; finite case','resize':'nearest','palette_colors':192}
(O/'LIGHTWEIGHT_CAPTURE_PLAYBACK_CHECK.json').write_text(json.dumps(report,indent=2));print(json.dumps(report))
