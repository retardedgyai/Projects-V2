"""Two targeted native model revisions: larger fractured core and one-direction pointed supports.
Preserves v3 bytes; no item/receiver/Jar/material redesign, generation service or imported art.
"""
from pathlib import Path
import json,hashlib,importlib.util,shutil
import numpy as np
from PIL import Image
spec=importlib.util.spec_from_file_location('m',Path(__file__).with_name('render-world-models.py'))
m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)
OLD=m.ROOT/'assets/model-lab/infusion-v3'
NEW=m.ROOT/'assets/model-lab/infusion-v4'
def hashes(root):return {str(p.relative_to(root)):hashlib.sha256(p.read_bytes()).hexdigest() for p in root.rglob('*') if p.is_file()}
before=hashes(OLD)
# Reuse precisely the approved-direction material pixels, without modifying the original masters.
for p in (OLD/'assets/projects/textures/infusion-v3').glob('*.png'):
    dest=NEW/p.relative_to(OLD);dest.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(p,dest)
def box(lo,hi,tex='basalt',rot=None,glyph=False):
    x,y,z=lo;X,Y,Z=hi
    uvs={'north':[16-X,16-Y,16-x,16-y],'south':[x,16-Y,X,16-y],
      'west':[z,16-Y,Z,16-y],'east':[16-Z,16-Y,16-z,16-y],
      'up':[x,z,X,Z],'down':[x,16-Z,X,16-z]}
    e={'from':lo,'to':hi,'faces':{f:{'texture':'#'+('carved' if glyph and f in ['north','east'] else tex),'uv':uv} for f,uv in uvs.items()}}
    if rot:e['rotation']={'origin':rot[0],'axis':rot[1],'angle':rot[2],'rescale':False}
    return e
def put(n,elements):
    p=NEW/f'assets/projects/models/infusion-v4/{n}.json';p.parent.mkdir(parents=True,exist_ok=True)
    p.write_text(json.dumps({'credit':'ProjectS original targeted stone revision / shared v3 hand painted pixels',
      'ambientocclusion':False,'textures':{n:'projects:infusion-v3/'+n for n in ['basalt','limestone','brass','carved','well','glass']},'elements':elements},indent=2)+'\n',encoding='utf8')
    p=NEW/f'assets/projects/items/infusion-v4/{n}.json';p.parent.mkdir(parents=True,exist_ok=True)
    p.write_text(json.dumps({'model':{'type':'minecraft:model','model':'projects:infusion-v4/'+n}},indent=2)+'\n',encoding='utf8')

# Three heavy fractured stone masses. The gaps go through the body, rather than painted cracks.
# An uneven, tapering outline and separate sharp end fragments keep it from being an inflated cube.
tilt=([8,8,8],'z',22.5)
core=[]
for lo,hi in [([-6,3,3],[4,20,15]),([-8,7,4],[-6,16,14]),([-4,0,4],[4,3,14]),
              ([-2,-3,5],[3,0,13]),([0,-6,6],[2,-3,12]),([-4,20,4],[3,23,14]),
              ([9,5,3],[22,18,15]),([10,18,4],[20,22,14]),([12,22,5],[18,25,13]),
              ([14,25,6],[17,28,12]),([15,28,7],[16,30,11]),([10,2,4],[21,5,14]),
              ([12,-.5,5],[19,2,13])]:core.append(box(lo,hi,rot=tilt,glyph=True))
# The center fissure has a changing jagged profile: solid teeth approach but never close the void.
core += [box([4,5,4],[5.5,9,13],rot=tilt),box([6.5,13,4],[9,17,13],rot=tilt),
         box([3,19,5],[5,21,12],rot=tilt),box([7.5,3,5],[10,6,12],rot=tilt)]
# Lower isolated shard, sharply stepped to a one-pixel point. No frame enclosing the stone.
for lo,hi in [([5,-7,5],[11,-4,13]),([6,-10,6],[10,-7,12]),([7,-12,7],[8.5,-10,11])]:core.append(box(lo,hi,rot=tilt,glyph=True))
core += [box([-6,10,2.5],[-5,13,3],'brass',tilt),box([21,7,2.5],[22,9,3],'brass',tilt)]
put('core',core)

# One straight stone blade with a continuous lean, not a bent/jointed arm. Foot remains stable.
support=[box([1,0,1],[15,2,15]),box([3,2,3],[13,3,13],'limestone')]
lean=([8,3,8],'z',22.5)
for lo,hi in [([4.5,3,5.5],[11.5,22,10.5]),([5.5,22,6],[10.5,25,10]),
              ([6.5,25,6.5],[9.5,28,9.5]),([7.25,28,7],[8.75,30,9]),([7.75,30,7.5],[8.25,32,8.5])]:
    support.append(box(lo,hi,rot=lean))
# A recessed-looking narrow carved face on the lower blade uses unchanged stone pixels.
support.append(box([5.5,8,5.3],[10.5,17,5.5],rot=lean,glyph=True))
put('support',support)

def get(n,version='new',at=(0,0,0),yaw=0):
    m.PACK=NEW if version=='new' and n in ['core','support'] else OLD
    return m.boxes('projects:infusion-'+('v4' if m.PACK==NEW else 'v3')+'/'+n,at,centered=False,yaw=yaw)
def assembly(v):
    p=get('center',v)+get('core',v,(0,2.9 if v=='new' else 2.75,0))
    for x,z,yaw in [(-1,-1,135),(1,-1,45),(-1,1,-135),(1,1,-45)]:p+=get('support',v,(x,0,z),yaw)
    for x,z in [(-3,0),(3,0),(0,3),(0,-3)]:p+=get('offering',v,(x,0,z))
    for x,z in [(-4,2),(4,2)]:p+=get('jar',v,(x,0,z))
    return p
def bound(parts):
    pts=np.concatenate([np.array([[x,y,z] for x in [lo[0],hi[0]] for y in [lo[1],hi[1]] for z in [lo[2],hi[2]]])@r.T+t for lo,hi,_,_,r,t in parts])
    return pts.min(0),pts.max(0)
def union(a,b):return (np.minimum(a[0],b[0]),np.maximum(a[1],b[1]))
oldparts,newparts=assembly('old'),assembly('new')
frame=union(bound(oldparts),bound(newparts))
OUT=m.OUT
sheet=Image.new('RGB',(1600,1450),'#1b201b');m.text(sheet,(26,14),'核を大きく・禍々しく / 支柱を直線の石刃へ',28)
for row,(yaw,el) in enumerate([(0,12),(38,26)]):
    for col,(v,p,label) in enumerate([('old',oldparts,'変更前：小さい核・曲がる石腕'),('new',newparts,'変更後：裂けた大きい核・尖った石刃')]):
        tile=m.render(p,(775,620),yaw,el,frame_bounds=frame);sheet.paste(tile,(col*800+12,76+row*650))
        m.text(sheet,(col*800+28,86+row*650),('正面 / ' if row==0 else '斜め / ')+label,18)
m.text(sheet,(25,1400),'左右は同角度・同照明・同縮尺。中央台・材料台・Jarと6材質の原本は変更なし。新旧とも実JSONを描画。',17)
sheet.save(OUT/'ProjectS-Infusion-Stone-Revision-Assembly.png')

details=Image.new('RGB',(1600,1170),'#1b201b');m.text(details,(26,14),'核・支柱の形を拡大 / 同縮尺で前後比較',28)
corebounds=union(bound(get('core','old')),bound(get('core')))
supportbounds=union(bound(get('support','old')),bound(get('support')))
for col,(n,v,label,fr) in enumerate([('core','old','核 / 前',corebounds),('core','new','核 / 後：深い空隙',corebounds),
                                  ('support','old','支柱 / 前',supportbounds),('support','new','支柱 / 後：一方向の傾き',supportbounds)]):
    for row,(yaw,el) in enumerate([(0,12),(38,26)]):
        tile=m.render(get(n,v),(375,490),yaw,el,frame_bounds=fr);details.paste(tile,(col*400+12,84+row*520))
        m.text(details,(col*400+22,93+row*520),label+(' / 正面' if row==0 else ' / 斜め'),16)
m.text(details,(25,1128),'native cuboid形状・22.5度の傾き・16px材質。原寸のゲーム画面／実clientの照明ではありません。',17)
details.save(OUT/'ProjectS-Infusion-Stone-Revision-Detail.png')

# Also give a clean large single scene to review the direction without the comparison labels.
hero=Image.new('RGB',(1500,1020),'#1b201b');m.text(hero,(26,14),'浮遊刻印石・形状改訂 / 大きい裂けた核と尖った専用支柱',27)
hero.paste(m.render(newparts,(1460,860),38,26,frame_bounds=frame),(20,70))
m.text(hero,(26,961),'モデル先行の比較案。ゲーム構造へ未接続。霧・装備表示はこの静止画に未収録。',18)
hero.save(OUT/'ProjectS-Infusion-Stone-Revision-Hero.png')
assert hashes(OLD)==before,'Original v3 must remain byte-for-byte intact'
oldcore,newcore=bound(get('core','old')),bound(get('core'))
ratio=(newcore[1]-newcore[0])/(oldcore[1]-oldcore[0])
record={'originalNativeModels':True,'runtimeConnected':False,'minecraftScreenshot':False,'modifiedModels':['core','support'],
 'unchangedV3Files':before,'newFiles':hashes(NEW),'sameCameraLightingScale':True,'coreBoundsScaleXYZ':ratio.tolist()}
(OUT/'stone-revision-provenance.json').write_text(json.dumps(record,indent=2),encoding='utf8')
print(json.dumps({'images':3,'modifiedModels':2,'originalV3FilesProtected':len(before),'coreBoundsScaleXYZ':ratio.tolist(),'runtimeConnected':False}))
