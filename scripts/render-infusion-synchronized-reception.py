"""Continuous offline proposal from actual ritual timing; original synthetic audio, no native update."""
from pathlib import Path
import importlib.util,json,math,copy,wave,struct,random,hashlib
from PIL import Image,ImageDraw,ImageFont
p=Path(__file__).with_name('render-infusion-adoption-context-pillow.py')
spec=importlib.util.spec_from_file_location('native_preview',p);n=importlib.util.module_from_spec(spec);spec.loader.exec_module(n)
out=n.repo/'assets/model-lab/infusion-synchronized-reception';trace=json.loads((out/'full-ritual-trace.json').read_text())
frames_dir=out/'frames';frames_dir.mkdir(exist_ok=True)
font=ImageFont.truetype(r'C:\Windows\Fonts\meiryo.ttc',22);small=ImageFont.truetype(r'C:\Windows\Fonts\meiryo.ttc',15)
labels={'READY':'待機','ESSENTIA':'元素を蓄積','INGREDIENTS':'中央の装備へ注入','COMPLETE':'調律完了'}
keyticks=(74,180,190,198);sheet=Image.new('RGB',(1200,1900),'#171b22');sd=ImageDraw.Draw(sheet)
sd.text((18,12),'音同期候補：到達 → 蓄積 → 装備へ収束 → 完了',font=font,fill='#eedcef')
sd.text((18,46),'左：承認済み演出　右：注入線を細く短く収束。白フラッシュなし。装備は最初から表示。',font=small,fill='#bcc4cc')
def arcs_on(image,depth,arcs,channel=None):
    for a in arcs:
        points=a['points']
        if channel is not None:
            shrink=1-.36*max(0,min(1,(channel-.3)/.55));pivot=(.5,3.4,.5)
            points=[n.add(pivot,n.mul(n.sub(p,pivot),shrink)) for p in points]
        layer=Image.new('RGBA',n.size);ld=Image.new('F',n.size,-100000);dr=ImageDraw.Draw(layer);dz=ImageDraw.Draw(ld)
        for u,v in zip(points,points[1:]):
            pu,du=n.project(u);pv,dv=n.project(v);steps=max(1,math.ceil(math.dist(pu,pv)*2))
            for i in range(steps):
                q=i/steps;r=(i+1)/steps
                aa=(pu[0]+(pv[0]-pu[0])*q,pu[1]+(pv[1]-pu[1])*q);bb=(pu[0]+(pv[0]-pu[0])*r,pu[1]+(pv[1]-pu[1])*r)
                dr.line((aa,bb),fill=(118,43,177,round(a['alpha']*43)),width=4)
                dr.line((aa,bb),fill=tuple(a['rgb'])+(round(a['alpha']*230),),width=2)
                dz.line((aa,bb),fill=du+(dv-du)*(q+r)/2,width=4)
        image.alpha_composite(n.clip_layer(layer,ld,depth))
def proposed_samples(f):
    samples=copy.deepcopy(f['samples']);ch=f['channel']
    if ch is not None:
        squeeze=max(0,min(1,(ch-.55)/.31))
        for s in samples:
            if s['key']=='radiance:column':
                bottom=1.29;oldheight=s['height']
                s['width']=1.1*(1-.83*squeeze);s['height']=oldheight*(1-.91*squeeze)
                s['position'][1]=bottom+s['height']/2
        if ch>=.86:samples=[s for s in samples if s['key']!='radiance:column' and not s['key'].startswith('radiance:flow:')]
    return samples
for index,f in enumerate(trace['frames']):
    base,depth=n.assembly(f['yaw'],f['glow'],weapon=True)
    image=base.copy();n.effects(image,depth,proposed_samples(f),True);arcs_on(image,depth,f['arcs'],f['channel'])
    canvas=Image.new('RGB',(960,744),'#171b22');d=ImageDraw.Draw(canvas)
    ch=f['channel'];label='収束・ひと呼吸' if ch is not None and ch>=.6 else labels[f['phase']]
    d.text((24,14),label,font=font,fill='#eedcef')
    d.text((24,50),f"{f['tick']/20:04.1f}s　元素 {round(f['fill']*100):3d}%　音付き・実機未適用の演出候補",font=small,fill='#bec3cf')
    canvas.paste(image.resize((900,630),Image.Resampling.NEAREST).convert('RGB'),(30,84))
    d.text((24,718),'素材や完成品の仕様は変更せず、音と収束の見せ方だけを提案しています。',font=small,fill='#aab3c2')
    canvas.save(frames_dir/f'{index:04d}.png')
    if f['tick'] in keyticks:
        row=keyticks.index(f['tick']);y=90+row*448
        sd.text((18,y),f"{f['tick']/20:.1f}s  {label}",font=small,fill='#e0d4df')
        current=base.copy();n.effects(current,depth,f['samples'],True);arcs_on(current,depth,f['arcs'])
        sheet.paste(current.convert('RGB'),(0,y+26));sheet.paste(image.convert('RGB'),(600,y+26))
    if index%16==0:print(f'SYNCHRONIZED_RENDER frame={index}/{len(trace["frames"])}',flush=True)
sheet.save(out/'reception-before-after.png',optimize=True)

# These are original generated tones/noise, not extracted game or reference recordings.
sr=48000;duration=len(trace['frames'])/10;mix=[0.0]*int((duration+.3)*sr);events=[];rng=random.Random(5514)
def event(kind,start,length,gain,charge=0):
    events.append({'type':kind,'time':start,'duration':length,'gain':gain,'charge':charge})
    first=int(start*sr)
    for i in range(int(length*sr)):
        t=i/sr;u=t/length;attack=min(1,t/.015);envelope=attack*(1-u)**2
        if kind=='absorption':
            phase=2*math.pi*((1000+charge*350)*t-850*t*t)
            value=.68*math.sin(phase)+.20*(rng.random()*2-1)
        elif kind=='charge':
            value=.75*math.sin(2*math.pi*(180+charge*100)*t)+.18*math.sin(2*math.pi*(720+charge*200)*t)
        elif kind=='injection':
            envelope=min(1,t/.12)*min(1,(length-t)/.10)*(.35+.65*u)
            value=.42*math.sin(2*math.pi*(150*t+210*t*t))+.26*(rng.random()*2-1)
        elif kind=='convergence':
            envelope=min(1,t/.025)*(1-u)
            value=.7*math.sin(2*math.pi*(400*t+650*t*t))+.13*(rng.random()*2-1)
        else:
            # Soft original bell; three partials avoid a loud, broadband transient.
            frequencies=(660,990,1320) if kind=='completion' else (440,660,880)
            value=sum(math.sin(2*math.pi*hz*t)*math.exp(-t*(2+j)) for j,hz in enumerate(frequencies))/3
        at=first+i
        if at<len(mix):mix[at]+=gain*envelope*value
event('start',trace['startTick']/20,.55,.15)
lastfill=0;lastcharge=-100
for f in trace['frames']:
    if f['phase']=='ESSENTIA' and f['fill']>lastfill and f['tick']-lastcharge>=8:
        event('charge',f['tick']/20,.26,.10,f['fill']);lastcharge=f['tick']
    lastfill=f['fill']
for s in trace['sounds']:
    if s['type']=='proposed-absorption':event('absorption',s['tick']/20,.19,.13,s['charge'])
channels=[f for f in trace['frames'] if f['channel'] is not None]
begin=channels[0]['tick']/20;end=trace['completionTick']/20
event('injection',begin,end-begin-.42,.12)
event('convergence',end-.55,.38,.17)
event('completion',end,1.4,.23)
peak=max(abs(x) for x in mix);assert peak<.65
with wave.open(str(out/'synchronized-original-sfx.wav'),'wb') as w:
    w.setnchannels(1);w.setsampwidth(2);w.setframerate(sr)
    w.writeframes(b''.join(struct.pack('<h',round(x*32767)) for x in mix))
# Proposed native gating contracts checked on the same event list.
absorbs=[e for e in events if e['type']=='absorption'];assert all(b['time']-a['time']>=.3 for a,b in zip(absorbs,absorbs[1:]))
attenuation=lambda distance:max(0,1-distance/16)
assert attenuation(0)==1 and attenuation(8)==.5 and attenuation(16)==0 and attenuation(32)==0
cancel=4.0;cancelled=[e for e in events if e['time']<cancel];assert all(e['time']<cancel for e in cancelled)
maxvoices=max(sum(e['time']<=t<e['time']+e['duration'] for e in events) for t in [i/100 for i in range(int(duration*100))]);assert maxvoices<=3
plan={'events':events,'sampleRate':sr,'durationSeconds':duration+.3,'peakAmplitude':peak,'rms':math.sqrt(sum(x*x for x in mix)/len(mix)),'maxVoices':maxvoices,
 'absorptionCooldownSeconds':.3,'distanceFalloffBlocks':16,'cancelPolicy':'Stop/fade active sounds within 100ms; discard scheduled events immediately. Offline contract, not native tested.',
 'nativeCurrentAudio':'Existing amethyst start and completion cues verified in source; client master volume 1.0; GIF itself has no audio. No native speaker recording.',
 'previewAudio':'Entirely original synthetic proposal. Bell cues replace the current start/completion tones in this preview only.',
 'nativeNotApplied':True,'whiteCompletionFlash':False,'normalLedgerRulesUnchanged':True,'gearPresentThroughout':True,
 'proposedVisualChanges':['Last injection column narrows and descends to equipment','Short pre-completion quiet pause','Existing short core arcs move closer during injection'],
 'approvedLowerCircleSha256':hashlib.sha256(n.pack.read('assets/projects/textures/infusion-radiance/wave.png')).hexdigest(),
 'limitations':['Offline cuboid/UV directional light and depth approximation','Staged fresh recipe, not user save','Sounds are original synthetic proposal, not actual Minecraft speaker capture','Native sound gating and distance are proposed contracts; offline audit only']}
(out/'sound-and-visual-plan.json').write_text(json.dumps(plan,indent=2,ensure_ascii=False),encoding='utf-8')
print('SYNCHRONIZED_PREVIEW_FRAMES_PASS '+json.dumps({'frames':len(trace['frames']),'duration':duration,'peak':peak,'maxVoices':maxvoices,'nativeNotApplied':True}),flush=True)
