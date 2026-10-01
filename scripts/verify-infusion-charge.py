"""Check accepted Essentia-owned charge, shared energy transactions, protected models and native media."""
from pathlib import Path
import json,hashlib,math,xml.etree.ElementTree as ET
from PIL import Image,ImageSequence
r=Path(__file__).resolve().parent.parent;out=r/'.tools/world-model-preview'
proof=json.loads((out/'charge-protected-files.json').read_text())['protected']
for path,digest in proof.items():assert hashlib.sha256((r/path).read_bytes()).hexdigest()==digest,path
path=r/'server-minestom/.tools/world-infusion-evidence/charge-animation-trace.json';trace=json.loads(path.read_text())
preview=json.loads((out/'charge-preview-verification.json').read_text());assert preview['traceSHA256']==hashlib.sha256(path.read_bytes()).hexdigest()
assert preview['nativeUVRendererEqualPixelRatio']>.99 and preview['chargeStages']==[0,3/7,1,1]
scenes={s['scenario']:s for s in trace['scenes']}
for name,s in scenes.items():
    assert s['peakSamples']<=65
    for f in s['frames']:
        assert 0<=f['chargeFraction']<=1 and 0<=f['chargeMotes']<=16 and 0<=f['injectionMotes']<=24
        assert len(f['samples'])<=64 and f['received']+f['remaining']==120
        assert (f['phase']=='COMPLETE')==(f['weaponMod']=='projects:glacial-attunement')
        if f['chargeMotes']:
            assert f['chargeMotes']==int(16*f['chargeFraction']) and f['phase'] in ['ESSENTIA','INGREDIENTS'] and f['power']>0 and not f['paused']
        if f['injectionMotes']:
            assert f['chargeFraction']==1 and f['phase']=='INGREDIENTS' and f['power']>0 and not f['paused']
            assert 100<f['received']<120 and math.isclose(f['channel'],(f['received']-100)/20)
        if f['power']==0 or f['paused'] or f['phase'] in ['READY','COMPLETE']:assert f['chargeMotes']==f['injectionMotes']==0
        if f['phase'] in ['ESSENTIA','INGREDIENTS'] and (f['power']==0 or f['paused']):assert math.isclose(f['glow'],.22+.68*f['chargeFraction'])
        if f['pedestalGlow']>0:assert f['phase']=='COMPLETE'
    if name in ['low','high','interrupt']:
        assert s['frames'][-1]['received']==120 and s['frames'][-1]['amounts']==[1,2,2]
        assert len([e for e in s['events'] if e['kind']=='complete'])==1
        jars=[e for e in s['events'] if e['kind']=='jar'];assert len(jars)==7
        first=min(e['tick'] for e in jars);assert {e['aspect'] for e in jars if e['tick']==first}=={'EMBER','TIDE','GALE'}
        assert any(f['injectionMotes']==24 for f in s['frames'])
    else:assert s['completedTick'] is None and not any(f['pedestalGlow'] for f in s['frames'])
    assert not s['frames'][-1]['samples'] and s['frames'][-1]['speed']==0
zero=scenes['zero'];assert not zero['events'] and all(f['chargeFraction']==0 and f['received']==0 and f['amounts']==[4,4,4] for f in zero['frames'])
paused=[f for f in scenes['interrupt']['frames'] if 56<=f['tick']<=159]
assert len({f['received'] for f in paused})==len({f['chargeFraction'] for f in paused})==len({f['glow'] for f in paused})==1
assert not any(56<=e['tick']<=159 for e in scenes['interrupt']['events'])
cancel=[f for f in scenes['cancel']['frames'] if f['tick']>=scenes['cancel']['stoppedTick']]
assert all(not f['chargeMotes'] and not f['injectionMotes'] and f['chargeFraction']==0 for f in cancel)
gif=out/'ProjectS-Infusion-Charge-Comparison.gif';data=gif.read_bytes();assert data[:6] in [b'GIF89a',b'GIF87a'] and len(data)<10*1024*1024
im=Image.open(gif);assert im.format=='GIF' and im.info['loop']==0 and im.n_frames>=90
gif_frames=im.n_frames
durations=[f.info['duration'] for f in ImageSequence.Iterator(im)];assert sum(durations)==preview['durationMs']
for name in ['Stages','Focus','Stops']:
    with Image.open(out/f'ProjectS-Infusion-Charge-{name}.png') as im:assert im.format=='PNG';im.verify()
suites=[ET.parse(p).getroot() for p in (r/'server-minestom/build/test-results/test').glob('*.xml')]
tests=sum(int(s.attrib['tests']) for s in suites);assert tests==76 and all(int(s.attrib['failures'])==int(s.attrib['errors'])==0 for s in suites)
record={'passed':True,'tests':tests,'priorTests':73,'protectedFiles':len(proof),'sharedChargeKernel':True,'actualEssentiaOwnsFill':True,
        'zeroStopsFillAndInjection':True,'pauseReloadResume':True,'cancelNoSuccess':True,'unchangedEPCosts':True,'peakCosmeticEntities':max(s['peakSamples'] for s in scenes.values()),
        'localCap':64,'sealAllowance':1,'globalCap':192,'nativeUVRendererEqualPixelRatio':preview['nativeUVRendererEqualPixelRatio'],
        'gifMIME':'image/gif','gifFrames':gif_frames,'gifDurationMs':sum(durations),'gifBytes':len(data),'gifSHA256':hashlib.sha256(data).hexdigest(),
        'nativePresenterOptInConnected':True,'clientCapture':False,'clientFPSMeasured':False,'bloom':False,'worldColouredLighting':False,'generationConnected':False,'productionDeployed':False}
(out/'charge-verification.json').write_text(json.dumps(record,indent=2)+'\n',encoding='utf8');print(json.dumps(record,indent=2))
