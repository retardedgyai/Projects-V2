"""Two palette-only model studies; preserve the actual native shapes, UVs and original masters."""
from pathlib import Path
import json,hashlib,importlib.util,shutil
import numpy as np
from PIL import Image
spec=importlib.util.spec_from_file_location('m',Path(__file__).with_name('render-world-models.py'))
m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)
OLD=m.ROOT/'assets/model-lab/infusion-v3';NEW=m.ROOT/'assets/model-lab/infusion-v6';OUT=m.OUT
def hashes(root):return {str(p.relative_to(root)):hashlib.sha256(p.read_bytes()).hexdigest() for p in root.rglob('*') if p.is_file()}
protected={v:hashes(m.ROOT/('assets/model-lab/infusion-'+v)) for v in ['v3','v4','v5']}
# Keep the source pixel cluster topology and change only the requested stone palette.
palette={'#aaa58f':'#625b6d','#b9b39d':'#776d81','#cac3a9':'#93859d','#8e8e81':'#4c4758','#74776e':'#34333e',
         '#676b63':'#38323f','#d6cdb1':'#8f7da0','#85887b':'#554b63'}
def rgb(s):return tuple(bytes.fromhex(s[1:]))
mapping={rgb(a):rgb(b) for a,b in palette.items()}
T=NEW/'assets/projects/textures/infusion-v6';T.mkdir(parents=True,exist_ok=True)
for name in ['limestone','well']:
    im=Image.open(OLD/f'assets/projects/textures/infusion-v3/{name}.png').convert('RGBA');array=np.array(im)
    original=array.copy()
    for source,target in mapping.items():array[np.all(original[:,:,:3]==source,axis=2),:3]=target
    assert np.array_equal(original[:,:,3],array[:,:,3])
    Image.fromarray(array).save(T/(name+'.png'))
for name in ['basalt','brass','carved','glass']:
    source=OLD/f'assets/projects/textures/infusion-v3/{name}.png';dest=NEW/source.relative_to(OLD)
    dest.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(source,dest)
for name in ['center','offering']:
    model=json.loads((OLD/f'assets/projects/models/infusion-v3/{name}.json').read_text())
    model['credit']='ProjectS original v3 geometry / palette-only purple-gray pedestal revision'
    for material in ['limestone','well']:model['textures'][material]='projects:infusion-v6/'+material
    target=NEW/f'assets/projects/models/infusion-v6/{name}.json';target.parent.mkdir(parents=True,exist_ok=True)
    target.write_text(json.dumps(model,indent=2)+'\n',encoding='utf8')
    target=NEW/f'assets/projects/items/infusion-v6/{name}.json';target.parent.mkdir(parents=True,exist_ok=True)
    target.write_text(json.dumps({'model':{'type':'minecraft:model','model':'projects:infusion-v6/'+name}},indent=2)+'\n',encoding='utf8')

def get(name,new=False,at=(0,0,0),yaw=0):
    version='v6' if new and name in ['center','offering'] else 'v5' if name.startswith('core_') else 'v4' if name=='support' else 'v3'
    m.PACK=m.ROOT/('assets/model-lab/infusion-'+version)
    model='projects:infusion-'+version+'/'+name
    for ref in m.model(model)['textures'].values():m.texture(ref['sprite'] if isinstance(ref,dict) else ref)
    return m.boxes(model,at,centered=False,yaw=yaw)
def assembly(new=False):
    p=get('center',new)+get('core_idle',at=(0,2.9,0))
    for x,z,yaw in [(-1,-1,135),(1,-1,45),(-1,1,-135),(1,1,-45)]:p+=get('support',at=(x,0,z),yaw=yaw)
    for x,z in [(-3,0),(3,0),(0,3),(0,-3)]:p+=get('offering',new,(x,0,z))
    for x,z in [(-4,2),(4,2)]:p+=get('jar',at=(x,0,z))
    return p
def bounds(parts):
    pts=np.concatenate([np.array([[x,y,z] for x in [lo[0],hi[0]] for y in [lo[1],hi[1]] for z in [lo[2],hi[2]]])@r.T+t for lo,hi,_,_,r,t in parts])
    return pts.min(0),pts.max(0)

if __name__=='__main__':
    old,new=assembly(),assembly(True);frame=bounds(old)
    comparison=Image.new('RGB',(1600,1410),'#191c20');m.text(comparison,(26,16),'台座の色合わせ / 形を保った同角度の比較',27)
    for row,(yaw,el) in enumerate([(0,12),(38,26)]):
        for col,(parts,label) in enumerate([(old,'前 / 白灰・黄味の台'),(new,'後 / 核と揃えた紫灰の台')]):
            comparison.paste(m.render(parts,(775,600),yaw,el,frame_bounds=frame),(12+800*col,78+625*row))
            m.text(comparison,(27+800*col,81+625*row),label,18)
    m.text(comparison,(26,1360),'核・支柱・Jar・金属接点は保持。中央台と材料台の石色だけを変更。ゲーム表示へ未接続。',17)
    comparison.save(OUT/'ProjectS-Infusion-Pedestal-Palette-Assembly.png')
    detail=Image.new('RGB',(1600,1110),'#191c20');m.text(detail,(26,16),'中央台・材料台 / 縁とくぼみの明暗を残す',27)
    for col,(name,new,label) in enumerate([('center',False,'中央台 / 前'),('center',True,'中央台 / 後'),('offering',False,'材料台 / 前'),('offering',True,'材料台 / 後')]):
        parts=get(name,new);frame=bounds(parts)
        for row,(yaw,el) in enumerate([(0,16),(38,28)]):
            detail.paste(m.render(parts,(375,440),yaw,el,frame_bounds=frame),(12+400*col,88+470*row))
            m.text(detail,(24+400*col,90+470*row),label+(' / 正面' if row==0 else ' / 斜め'),17)
    m.text(detail,(26,1058),'native形状・UV・サイズは同一。16px材質の主面/稜線/暗部を紫灰へ。台の発光は追加しない。',17)
    detail.save(OUT/'ProjectS-Infusion-Pedestal-Palette-Detail.png')
    record={'onlyChangedModels':['center','offering'],'geometryUVUnchanged':True,'originalPixelClustersPreserved':True,
      'palette':palette,'protected':protected,'files':hashes(NEW),'sameCameraScale':True,'runtimeConnected':False,'clientStarted':False}
    (OUT/'pedestal-palette-provenance.json').write_text(json.dumps(record,indent=2)+'\n',encoding='utf8')
    print(json.dumps({'images':2,'modifiedModels':2,'previousFilesProtected':sum(map(len,protected.values())),'runtimeConnected':False}))
