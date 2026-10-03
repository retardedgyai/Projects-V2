"""No new tones: same original completion sound once, current timing vs 0.2s earlier."""
from pathlib import Path
from PIL import Image,ImageDraw,ImageFont
import importlib.util,json,array,math,wave,struct,hashlib
repo=Path(__file__).resolve().parent.parent;root=repo.parent;out=repo/'assets/model-lab/infusion-original-sound-timing'
trace=json.loads((repo/'assets/model-lab/infusion-synchronized-reception/full-ritual-trace.json').read_text())
source_meta=json.loads((out/'original-sound-provenance.json').read_text())
source=array.array('f');source.frombytes((root/'reference-only/original-amethyst-shimmer.f32').read_bytes());sr=48000
path=Path(__file__).with_name('render-infusion-adoption-context-pillow.py');spec=importlib.util.spec_from_file_location('native_preview',path);n=importlib.util.module_from_spec(spec);spec.loader.exec_module(n)
frames=[f for f in trace['frames'] if f['tick']>=164];assert len(frames)==54
fps=10;part=len(frames)/fps;gap=.6;total=part*2+gap
dest=out/'frames';dest.mkdir(exist_ok=True)
font=ImageFont.truetype(r'C:\Windows\Fonts\meiryo.ttc',22);small=ImageFont.truetype(r'C:\Windows\Fonts\meiryo.ttc',16)
def arc_layer(image,depth,arcs):
    for a in arcs:
        layer=Image.new('RGBA',n.size);ld=Image.new('F',n.size,-100000);d=ImageDraw.Draw(layer);dz=ImageDraw.Draw(ld)
        for u,v in zip(a['points'],a['points'][1:]):
            pu,du=n.project(u);pv,dv=n.project(v);steps=max(1,math.ceil(math.dist(pu,pv)*2))
            for i in range(steps):
                q=i/steps;r=(i+1)/steps
                aa=(pu[0]+(pv[0]-pu[0])*q,pu[1]+(pv[1]-pu[1])*q);bb=(pu[0]+(pv[0]-pu[0])*r,pu[1]+(pv[1]-pu[1])*r)
                d.line((aa,bb),fill=(118,43,177,round(a['alpha']*43)),width=4)
                d.line((aa,bb),fill=tuple(a['rgb'])+(round(a['alpha']*230),),width=2)
                dz.line((aa,bb),fill=du+(dv-du)*(q+r)/2,width=4)
        image.alpha_composite(n.clip_layer(layer,ld,depth))
for i,f in enumerate(frames):
    image,depth=n.assembly(f['yaw'],f['glow'],weapon=True);n.effects(image,depth,f['samples'],True);arc_layer(image,depth,f['arcs'])
    for variant in ('A','B'):
        canvas=Image.new('RGB',(960,744),'#171b22');d=ImageDraw.Draw(canvas)
        d.text((24,14),'A：前の音そのまま' if variant=='A' else 'B：同じ音を0.2秒だけ早く',font=font,fill='#eedcef')
        d.text((24,52),'魔法陣と同時に1回' if variant=='A' else '最後の注入到達から1回・音色／音量／長さは同じ',font=small,fill='#bec3cf')
        canvas.paste(image.resize((900,630),Image.Resampling.NEAREST).convert('RGB'),(30,84))
        d.text((24,718),f"{f['tick']/20:.1f}s　同じ映像・元のゲーム音のみ・比較候補／実機未変更",font=small,fill='#aab3c2')
        index=i if variant=='A' else len(frames)+round(gap*fps)+i
        canvas.save(dest/f'{index:04d}.png')
    if i%12==0:print(f'ORIGINAL_SOUND_COMPARE_RENDER frame={i}/{len(frames)}',flush=True)
for i in range(round(gap*fps)):
    canvas=Image.new('RGB',(960,744),'#171b22');d=ImageDraw.Draw(canvas)
    d.text((90,320),'B：音色はそのまま、タイミングだけ比較',font=font,fill='#eedcef');canvas.save(dest/f'{len(frames)+i:04d}.png')

pitch=1.3;gain=.7*.2;cue=[]
for i in range(math.ceil(len(source)/pitch)):
    x=i*pitch;k=int(x);u=x-k
    cue.append((source[k]*(1-u)+(source[k+1] if k+1<len(source) else 0)*u)*gain)
mix=[0.0]*math.ceil((total+.1)*sr)
times=[196/20-8.2,part+gap+192/20-8.2]
for start in times:
    offset=round(start*sr)
    for i,v in enumerate(cue):mix[offset+i]+=v
assert times[1]+len(cue)/sr<total and max(abs(v) for v in mix)<1
with wave.open(str(out/'synchronized-original-sfx.wav'),'wb') as w:
    w.setnchannels(1);w.setsampwidth(2);w.setframerate(sr);w.writeframes(b''.join(struct.pack('<h',round(v*32767)) for v in mix))
# Each half has exactly one bit-identical pitched cue, shifted only in time.
a=round(times[0]*sr);b=round(times[1]*sr)
assert mix[a:a+len(cue)]==mix[b:b+len(cue)]
video_trace={'frames':[{'index':i} for i in range(len(frames)*2+round(gap*fps))]}
(out/'full-ritual-trace.json').write_text(json.dumps(video_trace))
old=repo/'assets/model-lab/infusion-synchronized-reception';rejected=json.loads((old/'sound-and-visual-plan.json').read_text(encoding='utf-8'))
with wave.open(str(old/'synchronized-original-sfx.wav'),'rb') as w:
    pcm=array.array('h',w.readframes(w.getnframes()));rejected_complete=[v/32767 for v in pcm[round(9.8*sr):round(11.2*sr)]]
def metrics(pcm):
    peak=max(abs(v) for v in pcm);rms=math.sqrt(sum(v*v for v in pcm)/len(pcm));audible=[i for i,v in enumerate(pcm) if abs(v)>=.001]
    return {'durationSeconds':len(pcm)/sr,'peakAmplitude':peak,'rms':rms,'aboveMinus60DbIntervalSeconds':(audible[-1]-audible[0])/sr if audible else 0}
report={'status':'Preview only, original native audio unchanged','actuallyListened':False,'method':'Installed sound decoded; source and waveform measurements; no auditory tool exposed',
 'originalCompletion':metrics(cue),'rejectedSyntheticCompletion':metrics(rejected_complete),'originalWholeRitualCueCount':2,'rejectedWholeRitualCueCount':len(rejected['events']),
 'synthesisRejected':True,'synthesisUsedInThisPreview':False,'newSoundsAdded':0,'cueCountPerVariant':1,'aCompletionTick':196,'bCompletionTick':192,
 'earlierSeconds':.2,'basis':'Last existing injection flow sample enters 0.1-block receiver radius at tick192; existing completion circle begins tick196',
 'sameSoundBytesBeforeScheduling':True,'sameVolumePitchLengthAndCount':True,'baselineVisualSamplesUnchanged':True,'whiteFlash':False,
 'nativeAudioChanged':False,'nativeReplayPreserved':True,'acceptedVisualsPreserved':True,'originalAssetSha1':source_meta['assetSha1'],
 'actualSpeakerCapture':False,'limit':'Offline resampling and gain reproduce configured pitch/asset/server gain, not a recording of Minecraft/OpenAL or room output',
 'diagnosis':['Original game shimmer was replaced with simple oscillators, sweeps and noise, changing the sound identity',
 '9 cues replaced 2, including additional buildup/absorption and overlapping injection/convergence',
 'Original pitched completion sample lasts about 3.32 seconds; synthetic completion lasts1.4 seconds',
 'Native completion already aligns with the circle at tick196; the rejected proposal did not need to replace that cue',
 'Subjective unpleasantness cannot be diagnosed without listening; these are measured/source differences and possible contributors'],
 'previewSeconds':total,'frameCount':len(video_trace['frames'])}
(out/'comparison-verification.json').write_text(json.dumps(report,indent=2))
print('ORIGINAL_SOUND_COMPARE_PASS '+json.dumps({'seconds':total,'frames':report['frameCount'],'newSoundsAdded':0,'sameCueBothSides':True,'oldCompletion':report['originalCompletion'],'rejectedCompletion':report['rejectedSyntheticCompletion']}),flush=True)
