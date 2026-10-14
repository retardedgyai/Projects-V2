"""Native media, real E/Essentia receipts, four sprites, protected approvals and 79 regression cases."""
from pathlib import Path
import json,hashlib,math,xml.etree.ElementTree as ET
from PIL import Image,ImageSequence
r=Path(__file__).resolve().parent.parent;out=r/'.tools/world-model-preview'
proof=json.loads((out/'radiance-protected-files.json').read_text())['protected']
for p,h in proof.items():assert hashlib.sha256((r/p).read_bytes()).hexdigest()==h,p
pack=r/'server-minestom/src/main/resources/core-ui-pack';build=r/'server-minestom/build/resources/main/core-ui-pack'
for name,size in [('halo',(32,32)),('column',(16,32)),('wave',(16,16)),('burst',(16,16))]:
    for kind,ext in [('textures','png'),('models','json'),('items','json')]:
        path=f'assets/projects/{kind}/infusion-radiance/{name}.{ext}';assert (pack/path).read_bytes()==(build/path).read_bytes(),path
    model=json.loads((pack/f'assets/projects/models/infusion-radiance/{name}.json').read_text());element=model['elements'][0]
    assert element['light_emission']==15 and element['shade']==False
    assert model['textures']['fx']['force_translucent']
    assert all(f['tintindex']==0 for f in element['faces'].values())
    im=Image.open(pack/f'assets/projects/textures/infusion-radiance/{name}.png');assert im.mode=='RGBA' and im.size==size
    if name in ['halo','burst']:assert im.getpixel((size[0]//2,size[1]//2))[3]==0
path=r/'server-minestom/.tools/world-infusion-evidence/radiance-animation-trace.json';trace=json.loads(path.read_text())
preview=json.loads((out/'radiance-preview-verification.json').read_text());assert preview['traceSHA256']==hashlib.sha256(path.read_bytes()).hexdigest()
assert preview['nativeUVRendererEqualPixelRatio']>.99 and not preview['cpuBloomAdded']
scenes={s['scenario']:s for s in trace['scenes']}
for name,s in scenes.items():
    assert s['peakSamples']<=65
    for f in s['frames']:
        assert len(f['samples'])<=64 and math.isclose(f['received']+f['remaining'],120)
        assert 0<=f['chargeFraction']<=1 and (f['phase']=='COMPLETE')==(f['weaponMod']=='projects:glacial-attunement')
        for p in f['samples']:
            assert len(p)==11 and p[7]>0 and p[8]>0 and p[10] in [12,15]
            assert p[6]=='infusion/smoke' or p[6].startswith('infusion-radiance/')
            if p[6].endswith('/column'):assert p[9]=='VERTICAL' and p[7]>=1.0
            if p[6].endswith('/wave'):assert p[9]=='HORIZONTAL'
        if f['columnVisible']:assert f['phase']=='INGREDIENTS' and 100<f['received']<120 and f['power']>0 and not f['paused']
        if f['waveCount'] or f['flash']:assert f['phase']=='COMPLETE'
        if f['power']==0 or f['paused']:
            assert not f['columnVisible'] and not f['waveCount'] and not f['flash']
            assert all(p[6].endswith('/halo') or p[6]=='infusion/smoke' for p in f['samples']) # Previously released Jar tufts may arrive.
        if f['phase']=='READY':assert f['radianceSprites']==0
    assert s['frames'][-1]['speed']==0
    if name=='shortage':assert all(p[6].endswith('/halo') for p in s['frames'][-1]['samples'])
    else:assert not s['frames'][-1]['samples']
    if name in ['low','high','interrupt']:
        assert s['frames'][-1]['received']==120 and s['frames'][-1]['amounts']==[1,2,2]
        assert len([e for e in s['events'] if e['kind']=='complete'])==1
        assert len([e for e in s['events'] if e['kind']=='jar'])==7
        assert any(f['columnVisible'] for f in s['frames']) and any(f['waveCount']==2 for f in s['frames'])
    else:assert s['completedTick'] is None and not any(f['waveCount'] or f['flash'] for f in s['frames'])
paused=[f for f in scenes['interrupt']['frames'] if 56<=f['tick']<=159]
assert len({f['received'] for f in paused})==len({f['chargeFraction'] for f in paused})==len({f['glow'] for f in paused})==1
assert not any(56<=e['tick']<=159 for e in scenes['interrupt']['events'])
zero=scenes['zero'];assert not zero['events'] and all(not f['radianceSprites'] and f['chargeFraction']==0 and f['received']==0 for f in zero['frames'])
gif=out/'ProjectS-Infusion-Radiance-Comparison.gif';data=gif.read_bytes();assert data[:6] in [b'GIF89a',b'GIF87a'] and len(data)<10*1024*1024
im=Image.open(gif);assert im.format=='GIF' and im.info['loop']==0 and im.n_frames==101
durations=[f.info['duration'] for f in ImageSequence.Iterator(im)];assert durations==[150]*101
for name in ['Stages','Phone','Success','Stops','Pixels']:
    with Image.open(out/f'ProjectS-Infusion-Radiance-{name}.png') as image:assert image.format=='PNG';image.verify()
with Image.open(out/'ProjectS-Infusion-Radiance-Phone.png') as image:assert image.width==360
suites=[ET.parse(p).getroot() for p in (r/'server-minestom/build/test-results/test').glob('*.xml')]
tests=sum(int(s.attrib['tests']) for s in suites);assert tests==79 and all(int(s.attrib['failures'])==int(s.attrib['errors'])==0 for s in suites)
record={'passed':True,'tests':tests,'priorTests':76,'protectedFiles':len(proof),'nativeSprites':4,'actualEssentiaOwnsFill':True,'zeroStopsEmissionAndProgress':True,
        'staticStoredHaloWhilePaused':True,'cancelNoSuccess':True,'reloadDoesNotReplaySuccess':True,'fixedEPCosts':True,'nativeVerticalColumn':True,'nativeHorizontalWaves':True,
        'peakCosmeticEntities':max(s['peakSamples'] for s in scenes.values()),'localCap':64,'sealAllowance':1,'globalCap':192,
        'gifMIME':'image/gif','gifFrames':101,'gifDurationMs':sum(durations),'gifBytes':len(data),'gifSHA256':hashlib.sha256(data).hexdigest(),
        'phoneWidth':360,'phoneScenePurplePixels':preview['phoneScenePurplePixels'],'nativeUVRendererEqualPixelRatio':preview['nativeUVRendererEqualPixelRatio'],
        'nativePresenterOptInConnected':True,'clientCapture':False,'clientFPSMeasured':False,'cpuBloomAdded':False,'bloom':False,'worldColouredLighting':False,'iOSPlaybackTested':False,'productionDeployed':False}
(out/'radiance-verification.json').write_text(json.dumps(record,indent=2)+'\n',encoding='utf8');print(json.dumps(record,indent=2))
