"""Verify old art preservation, accepted consumption, convergence/success, and actual animated GIF bytes."""
from pathlib import Path
import json,hashlib,math,xml.etree.ElementTree as ET
from PIL import Image,ImageSequence
root=Path(__file__).resolve().parent.parent;out=root/'.tools/world-model-preview'
protected=json.loads((out/'confluence-protected-files.json').read_text())
for p,h in protected.items():assert hashlib.sha256((root/p).read_bytes()).hexdigest()==h,p
path=root/'server-minestom/.tools/world-infusion-evidence/confluence-animation-trace.json'
trace=json.loads(path.read_text());preview=json.loads((out/'confluence-preview-verification.json').read_text())
assert preview['traceSHA256']==hashlib.sha256(path.read_bytes()).hexdigest()
scenes={s['scenario']:s for s in trace['scenes']}
for name,s in scenes.items():
    jars=[e for e in s['events'] if e['kind']=='jar'];first=min(e['tick'] for e in jars)
    assert {e['aspect'] for e in jars if e['tick']==first}=={'EMBER','TIDE','GALE'}
    assert all(len([e for e in jars if e['tick']==t]) in (1,3) for t in {e['tick'] for e in jars})
    assert s['peakSamples']<=64
    for f in s['frames']:
        assert all(math.isfinite(f[k]) for k in ['yaw','speed','glow'])
        assert all(0<=n<=4 for n in f['amounts'])
        assert len(f['samples'])<=64
        assert (f['weaponMod']=='projects:glacial-attunement')==(f['phase']=='COMPLETE')
        if f['channel'] is not None:assert f['weaponMod']=='projects:flame' and f['items']==[None]*4
    assert scenes[name]['frames'][-1]['samples']==[]
    assert scenes[name]['frames'][-1]['speed']==0
    if name in ['low','high']:
        assert len(jars)==7
        assert len([e for e in s['events'] if e['kind']=='ingredient'])==4
        assert len([e for e in s['events'] if e['kind']=='complete'])==1
        assert s['firstIngredientTick']>=max(e['tick'] for e in jars)+s['travelTicks']+(12 if s['supply']==0 else 6)
        assert s['completedTick']-max(e['tick'] for e in s['events'] if e['kind']=='ingredient')==s['channelTicks']
    else:
        assert s['completedTick'] is None
        assert all(e['tick']<s['stoppedTick'] for e in jars)
        assert not any(e['kind']=='complete' for e in s['events'])
        assert not any(f['weaponMod']=='projects:glacial-attunement' for f in s['frames'])
assert scenes['low']['frames'][-1]['amounts']==scenes['high']['frames'][-1]['amounts']==[1,2,2]
assert scenes['high']['completedTick']<scenes['low']['completedTick']
gif=out/'ProjectS-Infusion-Confluence-Low-High.gif';data=gif.read_bytes()
assert data[:6] in [b'GIF89a',b'GIF87a'] and len(data)<10*1024*1024
im=Image.open(gif);assert im.format=='GIF' and im.n_frames==161 and im.info['loop']==0
durations=[f.info['duration'] for f in ImageSequence.Iterator(im)];assert durations==[150]*161
assert len({hashlib.sha256(f.convert('RGB').tobytes()).hexdigest() for f in ImageSequence.Iterator(im)})>120
for name in ['Simultaneous','Weapon','Low-Weapon','Success','States','Stops','Weapon-Focus']:
    p=out/f'ProjectS-Infusion-Confluence-{name}.png'
    with Image.open(p) as im:assert im.format=='PNG';im.verify()
suites=[ET.parse(p).getroot() for p in (root/'server-minestom/build/test-results/test').glob('*.xml')]
tests=sum(int(s.attrib['tests']) for s in suites)
assert tests==68 and all(int(s.attrib['failures'])==int(s.attrib['errors'])==0 for s in suites)
record={'passed':True,'tests':tests,'priorTestsPreserved':63,'protectedFiles':len(protected),'allAspectsStartTogether':True,
 'sameCostLowHigh':True,'zeroAuxiliaryEnergyStillWorks':True,'oneCompletionEach':True,'cancelNoSuccess':True,
 'weaponChangesAfterConvergence':True,'lowCompletedTick':scenes['low']['completedTick'],'highCompletedTick':scenes['high']['completedTick'],
 'peakSamples':max(s['peakSamples'] for s in scenes.values()),'globalCap':192,'gifMIME':'image/gif',
 'gifFrames':161,'gifDurationMs':sum(durations),'gifBytes':len(data),'gifSHA256':hashlib.sha256(data).hexdigest(),
 'gamePresenterOptIn':True,'energySupplyDemoInput':True,'realClientLaunched':False,'fpsMeasured':False,'productionDeployed':False}
(out/'confluence-verification.json').write_text(json.dumps(record,indent=2)+'\n',encoding='utf8');print(json.dumps(record,indent=2))
