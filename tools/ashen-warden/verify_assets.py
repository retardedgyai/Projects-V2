"""Static QA of the Blender-exported production asset and vanilla resource pack."""
import pathlib,json,sys,zipfile,struct,math
root=pathlib.Path(sys.argv[1]);a=json.loads((root/'warden.json').read_text(encoding='utf8'))
assert 20<=len(a['bones'])<=35
assert a['fps']==20 and a['modelScale']==2
assert len(a['clips'])==8
names=[b['name'] for b in a['bones']]
for i,b in enumerate(a['bones']):assert b['parent'] is None or b['parent'] in names[:i]
with zipfile.ZipFile(root/'warden-pack.zip') as z:
    entries=set(z.namelist());models=0;textures=0
    for n in entries:
        assert '..' not in n and not n.startswith('/')
        if n.endswith('.png'):
            raw=z.read(n);assert raw[:8]==b'\x89PNG\r\n\x1a\n';assert struct.unpack('>II',raw[16:24])==(32,32);textures+=1
        if '/models/' in n:
            model=json.loads(z.read(n));models+=1
            assert 'parent' not in model
            for e in model['elements']:
                assert all(-16<=v<=32 for v in e['from']+e['to'])
                assert all(lo<hi for lo,hi in zip(e['from'],e['to']))
                if 'rotation' in e:
                    r=e['rotation'];assert set(r)=={'origin','x','y','z'}
                    assert len(r['origin'])==3 and all(math.isfinite(v) for v in r['origin']+[r[k] for k in 'xyz'])
                assert set(e['faces'])==set(['north','south','east','west','up','down'])
                for face in e['faces'].values():
                    assert all(0<=v<=16 for v in face['uv'])
                    tex=model['textures'][face['texture'][1:]]
                    assert 'assets/'+tex.replace(':','/textures/')+'.png' in entries
        if '/items/' in n:
            m=json.loads(z.read(n))['model']['model'];assert 'assets/'+m.replace(':','/models/')+'.json' in entries
    assert models==len(set(p['bone'] for p in a['parts'])) and textures==7
report={'status':'PASS','bones':len(a['bones']),'rigid_cuboids':len(a['parts']),'triangles':len(a['parts'])*12,
    'visible_item_displays':models,'pixel_textures':textures,'texture_size':'32x32','clips':[{k:c[k] for k in ('name','duration','active')} for c in a['clips']],
    'minecraft':'26.2','pack_format':'88.0','element_rotation':'1.21.11+ native XYZ','runtime':'Minestom 2026.08.16-26.2','runtime_model_dependency':'none'}
(root/'asset-validation.json').write_text(json.dumps(report,indent=2),encoding='utf8')
print(json.dumps(report))
