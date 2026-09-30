"""Check model geometry, physical opening, resource animation, preservation and native-parser evidence."""
from pathlib import Path
import json,hashlib,math,importlib.util
import numpy as np
from PIL import Image,ImageSequence
root=Path(__file__).resolve().parent.parent;new=root/'assets/model-lab/infusion-v5';out=root/'.tools/world-model-preview'
record=json.loads((out/'sealed-core-provenance.json').read_text())
for version,files in record['protected'].items():
    for p,h in files.items():assert hashlib.sha256((root/'assets/model-lab'/('infusion-'+version)/p).read_bytes()).hexdigest()==h
models={s:json.loads((new/f'assets/projects/models/infusion-v5/core_{s}.json').read_text()) for s in ['idle','active','unlit']}
shape=lambda m:[(e['from'],e['to'],e['rotation']) for e in m['elements']]
assert shape(models['idle'])==shape(models['active'])==shape(models['unlit'])
total=emissive=0
for state,model in models.items():
    assert model['ambientocclusion'] is False
    for e in model['elements']:
        total+=1;assert all(-16<=v<=32 for v in e['from']+e['to'])
        assert all(a<=b for a,b in zip(e['from'],e['to']))
        assert sum(a<b for a,b in zip(e['from'],e['to']))>=2
        assert e['rotation']=={'origin':[8,8,8],'axis':'z','angle':22.5,'rescale':False}
        glow=e['name'] in ['sealed incision','inner emission','seal filament','aperture light sprite']
        if state=='unlit':assert 'light_emission' not in e and 'shade' not in e
        else:
            assert e.get('light_emission',0)==(15 if glow else 0)
            if glow:assert e['shade'] is False;emissive+=1
        for face in e['faces'].values():
            assert len(face['uv'])==4
            key=face['texture'][1:];ref=model['textures'][key]
            if isinstance(ref,dict):assert ref['force_translucent'];ref=ref['sprite']
            assert (new/('assets/projects/textures/'+ref.split(':')[1]+'.png')).is_file()
    item=json.loads((new/f'assets/projects/items/infusion-v5/core_{state}.json').read_text())
    assert item['model']=={'type':'minecraft:model','model':'projects:infusion-v5/core_'+state}
# A clear geometric through-opening beside the controlled spindle, from the front to the back.
for x in [5.5,10.5]:
    for y in [6,11,16]:
        for z in [1,3,6,10,13,15]:
            assert not any(all(a<q<b for a,q,b in zip(e['from'],[x,y,z],e['to'])) for e in models['idle']['elements'])
frames={}
for name in ['seal','leak']:
    active=new/f'assets/projects/textures/infusion-v5/{name}_active.png'
    im=Image.open(active);assert im.size==(16,384) and im.mode=='RGBA'
    meta=json.loads(active.with_suffix('.png.mcmeta').read_text())
    assert meta=={'animation':{'frametime':2,'interpolate':True}}
    pixels=np.asarray(im).reshape(24,16,16,4)
    channel=3 if name=='leak' else slice(0,3)
    levels=pixels[:,:,:,channel].mean(axis=(1,2))
    if levels.ndim>1:levels=levels.mean(axis=1)
    assert levels[12]>levels[0]*1.9 and levels[12]==levels.max()
    assert np.array_equal(pixels[1],pixels[23])
    if name=='leak':assert pixels[:,:,:,3].max()<=92 and pixels[:,:,:,3].min()==0
    else:assert np.all(pixels[:,:,:,3]==255)
    frames[name]={'frames':24,'minMean':float(levels.min()),'maxMean':float(levels.max()),'cycleTicks':48}
gif=Image.open(out/'ProjectS-Infusion-Sealed-Core-Pulse.gif')
assert gif.n_frames==24 and gif.info['loop']==0
assert sum(f.info['duration'] for f in ImageSequence.Iterator(gif))==2400
assert (out/'ProjectS-Infusion-Sealed-Core-Pulse.gif').stat().st_size<10*1024*1024
for name,size in [('Comparison',(1600,1430)),('Light-States',(1500,1020)),('Hero',(1500,1080))]:
    assert Image.open(out/f'ProjectS-Infusion-Sealed-Core-{name}.png').size==size
native_lines=(out/'sealed-core-native-parser.log').read_text(encoding='utf-8-sig').splitlines()
native=json.loads(next(line for line in native_lines if line.startswith('{"passed"')))
assert native['passed'] and native['models']==3 and native['elements']==total and native['emissiveElements']==emissive
assert not native['clientStarted'] and not native['gpuVerified']
# Atlas reviews actual 16px pixels, and the dim/peak frames of the resource animation.
spec=importlib.util.spec_from_file_location('m',root/'scripts/render-world-models.py');m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)
atlas=Image.new('RGB',(1100,760),'#171921');m.text(atlas,(24,16),'封印核 / 実ピクセルと発光フレーム',26)
tiles=[('石彫り','sealed_carved',0),('開口の暗石','seal_well',0),('待機の刻印','seal_idle',0),
       ('作動・弱','seal_active',0),('作動・強','seal_active',12),('透過光・強','leak_active',12)]
for i,(label,name,frame) in enumerate(tiles):
    im=Image.open(new/f'assets/projects/textures/infusion-v5/{name}.png').crop((0,frame*16,16,frame*16+16)).resize((224,224),Image.Resampling.NEAREST)
    tile=Image.new('RGBA',(224,224),'#27242e');tile.alpha_composite(im)
    x=30+i%3*360;y=104+i//3*300;atlas.paste(tile.convert('RGB'),(x,y));m.text(atlas,(x,y-35),label,19)
m.text(atlas,(24,720),'16px原画をnearest拡大。透過光は実sprite。全体Bloomや周囲照明の焼き込みなし。',16)
atlas.save(out/'ProjectS-Infusion-Sealed-Core-Pixels.png')
result={'passed':True,'models':3,'cuboids':total,'emissiveCuboids':emissive,'physicalThroughOpening':True,
  'previousFilesProtected':sum(map(len,record['protected'].values())),'sameShapeAcrossStates':True,'nativeParser':native,
  'nativeAnimation':frames,'gifFrames':24,'gifDurationMs':2400,'gifBytes':(out/'ProjectS-Infusion-Sealed-Core-Pulse.gif').stat().st_size,
  'serverCodeChanged':False,'activeGamePackChanged':False,'runtimeConnected':False,'bloom':False,'coloredWorldLight':False}
(out/'sealed-core-verification.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf8');print(json.dumps(result,indent=2))
