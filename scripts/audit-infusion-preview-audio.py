from pathlib import Path
import json,wave,array,math
root=Path(__file__).resolve().parent.parent/'assets/model-lab/infusion-synchronized-reception'
plan=json.loads((root/'sound-and-visual-plan.json').read_text(encoding='utf-8'))
with wave.open(str(root/'synchronized-original-sfx.wav'),'rb') as w:
    sr=w.getframerate();samples=array.array('h',w.readframes(w.getnframes()))
assert sr==48000 and len(samples)>13*sr and max(abs(v) for v in samples)<32767
def controlled(distance,cancel=None):
    gain=max(0,1-distance/16)
    return [round(v*gain*(1 if cancel is None or i/sr<cancel else max(0,1-(i/sr-cancel)/.1))) for i,v in enumerate(samples)]
at8=controlled(8);at16=controlled(16);cancelled=controlled(0,4.0)
assert not any(at16)
assert abs(sum(v*v for v in at8)/sum(v*v for v in samples)-.25)<.001
assert not any(cancelled[math.ceil(4.1*sr):])
events=plan['events'];absorbs=[e for e in events if e['type']=='absorption']
assert all(b['time']-a['time']>=.3 for a,b in zip(absorbs,absorbs[1:]))
assert len([e for e in events if e['type']=='completion'])==1
report={'scope':'Offline audio mixer contract, not native runtime test','sampleRate':sr,'clippedSamples':0,'peakAmplitude':max(abs(v) for v in samples)/32767,
 'distance8AmplitudeHalf':True,'distance8EnergyQuarter':True,'distance16Silent':True,'cancelAt4FadeEnds4_1':True,'absorptionCooldownAtLeast0_3':True,
 'completionCueExactlyOnce':True,'nativeAudioNotApplied':True}
(root/'audio-contract-audit.json').write_text(json.dumps(report,indent=2))
print('OFFLINE_AUDIO_CONTRACT_PASS '+json.dumps(report))
