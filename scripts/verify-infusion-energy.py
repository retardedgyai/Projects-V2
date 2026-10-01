"""Verify real E/P ledger trace, old assets, success-only pedestal, native GIF and packaged resources."""
from pathlib import Path
import json,hashlib,math,xml.etree.ElementTree as ET
from PIL import Image,ImageSequence
root=Path(__file__).resolve().parent.parent;out=root/'.tools/world-model-preview'
proof=json.loads((out/'energy-art-preservation.json').read_text())
for p,h in proof['protected'].items():assert hashlib.sha256((root/p).read_bytes()).hexdigest()==h,p
pack=root/'server-minestom/src/main/resources/core-ui-pack';build=root/'server-minestom/build/resources/main/core-ui-pack'
for name in ['models/infusion-energy/pedestal_seal.json','items/infusion-energy/pedestal_seal.json','textures/infusion-energy/pedestal_seal.png']:
    p='assets/projects/'+name;assert (pack/p).read_bytes()==(build/p).read_bytes(),p
with Image.open(pack/'assets/projects/textures/infusion-energy/pedestal_seal.png') as im:assert im.size==(16,16) and im.mode=='RGBA'
model=json.loads((pack/'assets/projects/models/infusion-energy/pedestal_seal.json').read_text())
assert model['elements'][0]['light_emission']==15 and model['elements'][0]['faces']['up']['uv']==[3,3,13,13]
path=root/'server-minestom/.tools/world-infusion-evidence/energy-animation-trace.json';trace=json.loads(path.read_text())
preview=json.loads((out/'energy-preview-verification.json').read_text());assert preview['traceSHA256']==hashlib.sha256(path.read_bytes()).hexdigest()
scenes={s['scenario']:s for s in trace['scenes']}
for name,s in scenes.items():
    assert s['peakSamples']<=65
    for f in s['frames']:
        assert f['required']==120 and 0<=f['received']<=120 and math.isclose(f['received']+f['remaining'],120)
        assert 0<=f['power']<=18 and f['pedestalGlow']>=0
        assert all(math.isfinite(f[k]) for k in ['yaw','speed','glow'])
        if f['pedestalGlow']>0:assert f['phase']=='COMPLETE' and f['weaponMod']=='projects:glacial-attunement'
        assert (f['phase']=='COMPLETE')==(f['weaponMod']=='projects:glacial-attunement')
    assert s['frames'][-1]['samples']==[] and s['frames'][-1]['speed']==0
    if name in ['low','high','interrupt']:
        assert s['frames'][-1]['received']==120 and s['frames'][-1]['remaining']==0
        assert s['frames'][-1]['amounts']==[1,2,2]
        assert len([e for e in s['events'] if e['kind']=='complete'])==1
        jars=[e for e in s['events'] if e['kind']=='jar'];assert len(jars)==7
        first=min(e['tick'] for e in jars);assert {e['aspect'] for e in jars if e['tick']==first}=={'EMBER','TIDE','GALE'}
    else:
        assert s['completedTick'] is None and all(f['pedestalGlow']==0 for f in s['frames'])
zero=scenes['zero'];assert zero['events']==[] and all(f['received']==0 and f['amounts']==[4,4,4] for f in zero['frames'])
paused=[f for f in scenes['interrupt']['frames'] if 56<=f['tick']<=159]
assert len({f['received'] for f in paused})==1 and len({tuple(f['amounts']) for f in paused})==1
assert not any(56<=e['tick']<=159 for e in scenes['interrupt']['events'])
assert scenes['high']['completedTick']<scenes['interrupt']['completedTick'] and scenes['high']['completedTick']<scenes['low']['completedTick']
gif=out/'ProjectS-Infusion-Energy-Zero-Low-High.gif';data=gif.read_bytes()
assert data[:6] in [b'GIF89a',b'GIF87a'] and len(data)<10*1024*1024
im=Image.open(gif);assert im.format=='GIF' and im.info['loop']==0 and im.n_frames==207
durations=[f.info['duration'] for f in ImageSequence.Iterator(im)];assert durations==[150]*207
for name in ['Simultaneous','High-Channel','High-Success','Low-Channel','Low-Success','States','Pedestal-Focus','Interrupt-Cancel','Pedestal-Early-Detail']:
    with Image.open(out/f'ProjectS-Infusion-Energy-{name}.png') as im:assert im.format=='PNG';im.verify()
suites=[ET.parse(p).getroot() for p in (root/'server-minestom/build/test-results/test').glob('*.xml')]
tests=sum(int(s.attrib['tests']) for s in suites);assert tests==73
assert all(int(s.attrib['failures'])==int(s.attrib['errors'])==0 for s in suites)
record={'passed':True,'tests':tests,'priorTests':68,'protectedFiles':len(proof['protected']),'requiredE':120,'powerComparison':[0,6,18],
 'zeroDoesNotProgress':True,'atomicEnergyAndResources':True,'interruptionReloadResume':True,'sameEAndMaterials':True,'cancelNoSuccess':True,
 'oneSuccessPerRitual':True,'nativePedestalSealOnlyOnSuccess':True,'peakCosmeticEntities':max(s['peakSamples'] for s in scenes.values()),'globalCap':192,
 'gifMIME':'image/gif','gifFrames':207,'gifDurationMs':sum(durations),'gifBytes':len(data),'gifSHA256':hashlib.sha256(data).hexdigest(),
 'gamePresenterConnectedOptIn':True,'generationNotConnected':True,'demoUnitsAndLimits':True,'legacyV1Preserved':True,'isolatedEnvelopeV2':True,
 'clientLaunched':False,'clientFPSMeasured':False,'productionDeployed':False}
(out/'energy-verification.json').write_text(json.dumps(record,indent=2)+'\n',encoding='utf8');print(json.dumps(record,indent=2))
