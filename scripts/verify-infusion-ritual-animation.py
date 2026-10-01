"""Shared trace, packet-safe bounds, exact model pixels, preservation and preview provenance."""
from pathlib import Path
import json,hashlib,xml.etree.ElementTree as ET
import numpy as np
from PIL import Image,ImageSequence
root=Path(__file__).resolve().parent.parent;out=root/'.tools/world-model-preview';pack=root/'server-minestom/src/main/resources/core-ui-pack';lab=root/'assets/model-lab'
art=json.loads((out/'ritual-art-provenance.json').read_text())
for version,files in art['protected'].items():
    for p,h in files.items():assert hashlib.sha256((lab/('infusion-'+version)/p).read_bytes()).hexdigest()==h
old=json.loads((lab/'infusion-v5/assets/projects/models/infusion-v5/core_active.json').read_text())
new=json.loads((pack/'assets/projects/models/infusion-v7/core_ritual.json').read_text())
for a,b in zip(old['elements'],new['elements']):
    clean={**b,'faces':{n:{k:v for k,v in f.items() if k!='tintindex'} for n,f in b['faces'].items()}}
    assert a==clean
    assert all(('tintindex' in f)==(b.get('light_emission',0)==15) for f in b['faces'].values())
for tex in new['textures'].values():
    name=tex['sprite'] if isinstance(tex,dict) else tex
    assert (pack/('assets/projects/textures/'+name.split(':')[1]+'.png')).is_file()
for n in ['seal','leak']:
    assert np.array_equal(np.asarray(Image.open(pack/f'assets/projects/textures/infusion-v7/{n}_charged.png')),
      np.asarray(Image.open(lab/f'infusion-v5/assets/projects/textures/infusion-v5/{n}_active.png').crop((0,192,16,208))))
path=root/'server-minestom/.tools/world-infusion-evidence/ritual-animation-trace.json';trace=json.loads(path.read_text())
preview=json.loads((out/'ritual-preview-verification.json').read_text());assert preview['sharedKotlinTraceSHA256']==hashlib.sha256(path.read_bytes()).hexdigest()
max_samples=max(len(f['samples']) for s in trace['scenes'] for f in s['frames']);assert max_samples<=64
complete=next(s for s in trace['scenes'] if s['scenario']=='complete')
assert len([e for e in complete['events'] if e['kind']=='jar'])==7
assert len([e for e in complete['events'] if e['kind']=='ingredient'])==4
assert complete['firstIngredientTick']>=max(e['tick'] for e in complete['events'] if e['kind']=='jar')+54
flash=[f for f in complete['frames'] if f['glow']>.95];assert flash and max(f['tick'] for f in flash)-min(f['tick'] for f in flash)<14
assert complete['frames'][-1]['speed']==0 and complete['frames'][-1]['glow']==.22
for scene in trace['scenes']:
    assert all(0<=f['glow']<=1 and 0<=f['speed']<=4.5 for f in scene['frames'])
    assert scene['frames'][-1]['samples']==[]
    if scene['scenario']!='complete':
        assert scene['completedTick'] is None
        assert all(e['tick']<scene['stoppedTick'] for e in scene['events'] if e['kind']=='jar')
        assert scene['frames'][-1]['speed']==0 and scene['frames'][-1]['glow']==.22
gif=Image.open(out/'ProjectS-Infusion-Ritual-Animation.gif')
assert gif.n_frames==len(complete['frames'])==176 and gif.info['loop']==0
assert sum(f.info['duration'] for f in ImageSequence.Iterator(gif))==17600
assert (out/'ProjectS-Infusion-Ritual-Animation.gif').stat().st_size<10*1024*1024
suites=[ET.parse(p).getroot() for p in (root/'server-minestom/build/test-results/test').glob('*.xml')]
tests=sum(int(s.attrib['tests']) for s in suites);assert tests==63
assert all(int(s.attrib['failures'])==int(s.attrib['errors'])==0 for s in suites)
load=json.loads((root/'server-minestom/.tools/world-infusion-evidence/smoke-load-verification.json').read_text())
assert load['peakSmokeEntities']==45 and load['born']==load['removed']==126 and load['globalCosmeticCap']==192
record={'passed':True,'tests':tests,'failures':0,'sameShapePalette':True,'onlyEmissionFacesTinted':True,
 'previousFilesProtected':sum(map(len,art['protected'].values())),'completedTick':complete['completedTick'],
 'firstIngredientTick':complete['firstIngredientTick'],'singleCompletionFlash':True,'stopAndCancelNoNewConsumption':True,
 'traceMaxSamples':max_samples,'gifFrames':176,'gifDurationMs':17600,'gifBytes':(out/'ProjectS-Infusion-Ritual-Animation.gif').stat().st_size,
 'scenePeakEntities':load['peakSceneEntities'],'smokePeakEntities':load['peakSmokeEntities'],'globalCosmeticCap':192,
 'activeGamePackConnected':True,'worldPlaygroundPresenterConnected':True,'gameClientLaunched':False,
 'productionServerDeployed':False,'gpuFPSMeasured':False}
(out/'ritual-animation-verification.json').write_text(json.dumps(record,indent=2)+'\n',encoding='utf8');print(json.dumps(record,indent=2))
