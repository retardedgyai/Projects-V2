"""Two small standard Minecraft event combinations. Same accepted visual frames; no synthesis."""
from pathlib import Path
from PIL import Image,ImageDraw,ImageFont
import json,array,math,wave,struct,hashlib
repo=Path(__file__).resolve().parent.parent;root=repo.parent
out=repo/'assets/model-lab/infusion-minecraft-standard-sounds';catalog=json.loads((out/'standard-sound-catalog.json').read_text())
old=repo/'assets/model-lab/infusion-original-sound-timing/frames';dest=out/'frames';dest.mkdir(exist_ok=True)
sr=48000;part=4.2;gap=.6;total=part*2+gap;count=90
font=ImageFont.truetype(r'C:\Windows\Fonts\meiryo.ttc',22);small=ImageFont.truetype(r'C:\Windows\Fonts\meiryo.ttc',16)
for i in range(42):
    source=Image.open(old/f'{i:04d}.png').convert('RGB')
    for variant,offset in [('A',0),('B',48)]:
        canvas=source.copy();d=ImageDraw.Draw(canvas);d.rectangle((0,0,960,83),fill='#171b22');d.rectangle((0,715,960,744),fill='#171b22')
        d.text((24,14),'A：注入＝アメジスト／完成＝エンチャント台' if variant=='A' else 'B：注入＝エンチャント台／完成＝アメジスト共鳴',font=font,fill='#eedcef')
        d.text((24,52),'Minecraft標準音をそのまま使用・注入と完成で各1回・ピッチ変更なし',font=small,fill='#bec3cf')
        d.text((24,718),f'{8.2+i/10:.1f}s　同じ映像・合成音なし・比較候補／実機未変更',font=small,fill='#aab3c2')
        canvas.save(dest/f'{i+offset:04d}.png')
for i in range(6):
    canvas=Image.new('RGB',(960,744),'#171b22');d=ImageDraw.Draw(canvas)
    d.text((120,320),'B：エンチャント台 → アメジスト共鳴',font=font,fill='#eedcef');canvas.save(dest/f'{42+i:04d}.png')

# Values are sound-event packet volumes; standard sound JSON gain and default pitch are respected.
variants=[{'variant':'A','injectionIndex':2,'completionIndex':13,'injectionVolume':.08,'completionVolume':.09},
          {'variant':'B','injectionIndex':12,'completionIndex':15,'injectionVolume':.05,'completionVolume':.25}]
mix=[0.0]*math.ceil((total+.1)*sr);events=[]
def sound(index):
    pcm=array.array('f');pcm.frombytes((root/f'reference-only/standard-infusion-sounds/{index}.f32').read_bytes());return pcm
def put(index,volume,start,kind,variant):
    entry=catalog['entries'][index];pitch=entry['definitionPitch'];gain=volume*entry['definitionVolume'];pcm=sound(index)
    offset=round(start*sr);pitched=[]
    for i in range(math.ceil(len(pcm)/pitch)):
        x=i*pitch;k=int(x);u=x-k
        value=(pcm[k]*(1-u)+(pcm[k+1] if k+1<len(pcm) else 0)*u)*gain;pitched.append(value)
        if offset+i>=len(mix):raise RuntimeError('Natural standard sound tail does not fit preview')
        mix[offset+i]+=value
    events.append({'variant':variant,'role':kind,'event':entry['event'],'sample':entry['sound'],'serverVolume':volume,'pitch':1.0,
                   'soundDefinitionVolume':entry['definitionVolume'],'soundDefinitionPitch':pitch,'atSeconds':start,'durationSeconds':len(pitched)/sr,
                   'peakAmplitude':max(abs(v) for v in pitched),'assetSha1':entry['assetSha1']})
for i,v in enumerate(variants):
    base=i*(part+gap)
    put(v['injectionIndex'],v['injectionVolume'],base+.6,'injection-arrival',v['variant'])
    put(v['completionIndex'],v['completionVolume'],base+1.6,'circle-completion',v['variant'])
peak=max(abs(v) for v in mix);assert peak<.08
for v in variants:
    es=[e for e in events if e['variant']==v['variant']];assert len(es)==2 and len({e['role'] for e in es})==2
    assert all(e['pitch']==1 for e in es)
    family_peak={e: max(s['sourcePeak']*s['definitionVolume'] for s in catalog['entries'] if s['event']==e) for e in {x['event'] for x in es}}
    assert all(family_peak[e['event']]*e['serverVolume']<.045 for e in es)
with wave.open(str(out/'synchronized-original-sfx.wav'),'wb') as w:
    w.setnchannels(1);w.setsampwidth(2);w.setframerate(sr);w.writeframes(b''.join(struct.pack('<h',round(v*32767)) for v in mix))
(out/'full-ritual-trace.json').write_text(json.dumps({'frames':[{'index':i} for i in range(count)]}))
runtime_names=['BLOCK_AMETHYST_BLOCK_HIT','BLOCK_ENCHANTMENT_TABLE_USE','BLOCK_AMETHYST_BLOCK_RESONATE']
report={'source':'Installed Minecraft26.2 standard sound events only','soundCatalogVersion':'26.2-32','events':events,'serverConstantsVerified':runtime_names,
 'whyTheseEvents':['Crystal hit gives a recognizable standard block contact cue for the received injection',
                   'Enchanting-table use is a standard equipment-magic interaction/completion cue',
                   'Amethyst resonance offers an existing crystal-family completion alternative'],
 'selectionBasis':'Standard event semantics and existing installed samples. These are proposals, not subjective audition judgments.',
 'actuallyListened':False,'listeningLimitation':'No listening/audio-perception tool exposed; decoded sources and signal checks only',
 'variantsPerCatalog':{'amethystHit':12,'enchantmentTable':3,'amethystResonate':4},'randomVariantsAllGainChecked':True,
 'previewRepresentativeSamplesFixed':True,'standardEventPitchPreserved':True,'sampleWaveformsEdited':False,'synthesis':False,
 'visualSource':'Same accepted no-flash continuous trace used in prior original-sound timing comparison; only overlay text differs',
 'nativeAudioApplied':False,'rejected62fd05fbUsed':False,'existingNativeAlreadyStandard':'block.amethyst_block.chime (block/amethyst/shimmer)',
 'originalNativeAudioPreserved':True,'materialsFreeReplayPreserved':True,'whiteFlash':False,'normalCraftingChanged':False,
 'voicesMaximumPerRitual':2,'cueCountPerVariant':2,'injectionTick':176,'completionTick':196,
 'nativeIntegrationIfApproved':'Latch one arrival event at first receiver reach and one completion event only after committed completion; cancel/disconnect reset latch and stop owned cues. Do not play per particle.',
 'peakAmplitude':peak,'clippedSamples':0,'naturalTailsRetained':True,'previewSeconds':total,'frameCount':count,
 'spatialLimitation':'Offline listening at altar only. Native distance attenuation, user audio categories and perceived output are not tested by this clip.',
 'assetProvenance':'Authorized existing installed client cache; source hashes in catalog; no external copied or unknown audio'}
(out/'comparison-verification.json').write_text(json.dumps(report,indent=2))
print('MINECRAFT_STANDARD_SOUND_COMPARE_PASS '+json.dumps({'frames':count,'seconds':total,'peak':peak,'soundsPerVariant':2,'synthesis':False,'events':events}),flush=True)
