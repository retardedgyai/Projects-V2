from pathlib import Path
import zipfile,json,hashlib,re
root=Path(__file__).parent
path=root/'fixed-pack-preview.zip'
with zipfile.ZipFile(path) as z:
    names=set(z.namelist())
    missing=[]
    checked=[]
    for name in names:
        if re.match(r'assets/projects/(items|models)/infusion[^/]*/.*\.json$',name):
            data=json.loads(z.read(name)); checked.append(name)
            if '/items/' in name:
                model=data['model']
                if model['type']=='minecraft:model':
                    ref=model['model']; ns,res=ref.split(':',1)
                    target=f'assets/{ns}/models/{res}.json'
                    if target not in names: missing.append([name,target])
            else:
                for value in data.get('textures',{}).values():
                    ref=value.get('sprite') if isinstance(value,dict) else value
                    if isinstance(ref,str) and ref.startswith('projects:'):
                        target='assets/projects/textures/'+ref.split(':',1)[1]+'.png'
                        if target not in names: missing.append([name,target])
    expected=['assets/projects/items/'+v+'.json' for v in ['infusion-v7/core_ritual','infusion-v6/center','infusion-v6/offering','infusion-v4/support','infusion-v3/jar','infusion-energy/pedestal_seal','infusion-radiance/halo','infusion-radiance/column','infusion-radiance/wave','infusion-radiance/burst']]
    missing+=['runtime item missing '+n for n in expected if n not in names]
    atlas=json.loads(z.read('assets/minecraft/atlases/items.json'))
    atlas_dirs={source['source']:source['prefix'] for source in atlas['sources'] if source['type']=='minecraft:directory'}
    uncovered=[]
    for name in names:
        if re.match(r'assets/projects/textures/infusion[^/]*/.*\.png$',name):
            family=name.split('/')[3]
            if atlas_dirs.get(family)!=family+'/': uncovered.append(name)
    report={'sha1':hashlib.sha1(path.read_bytes()).hexdigest(),'bytes':path.stat().st_size,'entries':len(names),'native_magic_definitions_checked':len(checked),'missing_references':missing,'required_runtime_items':expected,'png_signatures_valid':all(z.read(n).startswith(b'\x89PNG\r\n\x1a\n') for n in names if re.match(r'assets/projects/textures/infusion[^/]*/.*\.png$',n)),'item_atlas_unregistered_textures':uncovered,'item_atlas_sources':atlas['sources']}
    (root/'pack-reference-audit.json').write_text(json.dumps(report,indent=2),encoding='utf-8')
    print(json.dumps(report,indent=2))
    assert not missing and report['png_signatures_valid'] and not uncovered
