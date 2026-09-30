"""Original native Minecraft cuboid A/B studies, intentionally plain grey for silhouette review."""
from pathlib import Path
import json,hashlib,importlib.util
from PIL import Image
spec=importlib.util.spec_from_file_location('m',Path(__file__).with_name('render-world-models.py'))
m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)
LAB=m.ROOT/'assets/model-lab/infusion-v2';LAB.mkdir(parents=True,exist_ok=True)
for n,c in [('light',(181,181,171)),('dark',(58,64,62)),('joint',(120,126,118))]:
    p=LAB/f'assets/projects/textures/infusion-v2/{n}.png';p.parent.mkdir(parents=True,exist_ok=True)
    im=Image.new('RGBA',(16,16),c+(255,));im.save(p)
def box(lo,hi,tex='light'):
    return {'from':lo,'to':hi,'faces':{f:{'texture':'#'+tex,'uv':[0,0,16,16]} for f in m.normals}}
def put(n,els):
    p=LAB/f'assets/projects/models/infusion-v2/{n}.json';p.parent.mkdir(parents=True,exist_ok=True)
    p.write_text(json.dumps({'credit':'ProjectS original grey shape study; no copied reference geometry','textures':{n:'projects:infusion-v2/'+n for n in ['light','dark','joint']},'elements':els},indent=2)+'\n',encoding='utf8')
    ip=LAB/f'assets/projects/items/infusion-v2/{n}.json';ip.parent.mkdir(parents=True,exist_ok=True)
    ip.write_text(json.dumps({'model':{'type':'minecraft:model','model':'projects:infusion-v2/'+n}},indent=2)+'\n',encoding='utf8')

# A: wide real recessed receiving bowl. 2px top rim + 1px inner step leaves a 10px well.
a=[box([1,0,1],[15,2,15],'dark'),box([3,2,3],[13,4,13]),box([0,4,0],[16,6,16])]
for lo,hi in [([0,6,0],[16,10,2]),([0,6,14],[16,10,16]),([0,6,2],[2,10,14]),([14,6,2],[16,10,14]),
              ([2,6,2],[14,8,3]),([2,6,13],[14,8,14]),([2,6,3],[3,8,13]),([13,6,3],[14,8,13])]:a.append(box(lo,hi))
for x,z in [(0,7),(15,7),(7,0),(7,15)]:a.append(box([x,4,z],[x+1,6,z+1],'joint'))
put('center_a',a)
# Two broken C-shaped stone halves; center is open, never an opaque rune box.
put('focus_a',[box([-3,3,1],[1,8,15],'dark'),box([-3,3,0],[6,7,3],'dark'),box([-3,3,13],[5,7,16],'dark'),
 box([15,2,1],[19,7,15],'dark'),box([10,2,0],[19,6,3],'dark'),box([11,2,13],[19,6,16],'dark'),
 box([-3,8,3],[0,12,6],'dark'),box([-2,7,10],[1,10,13],'dark'),box([16,7,8],[19,11,11],'dark'),
 box([3,3,0],[5,7,1],'joint'),box([11,2,15],[13,6,16],'joint')])
put('side_a',[box([2,0,2],[14,2,14],'dark'),box([5,2,5],[11,10,11]),box([3,10,3],[13,12,13]),
 box([2,12,2],[14,13,14]),box([2,13,2],[14,14,4]),box([2,13,12],[14,14,14]),
 box([2,13,4],[4,14,12]),box([12,13,4],[14,14,12]),box([5,9,5],[11,10,11],'joint')])
# B: dark inset floor plate. Four separate grooves lead to the small center socket.
b=[box([-4,0,-4],[20,2,20],'dark')]
for lo,hi in [([-4,2,-4],[20,3,-2]),([-4,2,18],[20,3,20]),([-4,2,-2],[-2,3,18]),([18,2,-2],[20,3,18]),
              ([-2,2,-2],[6,3,6]),([10,2,-2],[18,3,6]),([-2,2,10],[6,3,18]),([10,2,10],[18,3,18])]:b.append(box(lo,hi,'light'))
b+=[box([4,2,4],[12,3,12],'joint'),box([4,3,4],[12,5,5],'light'),box([4,3,11],[12,5,12],'light'),
    box([4,3,5],[5,5,11],'light'),box([11,3,5],[12,5,11],'light')]
put('center_b',b)
segments=[box([4,3,-2],[12,5,1],'dark'),box([-2,3,6],[1,5,12],'dark'),box([15,3,6],[18,5,12],'dark')]
put('focus_b',segments)
put('side_b',[box([2,0,2],[14,2,14],'dark'),box([2,2,3],[14,3,14]),box([2,3,11],[14,6,14]),
 box([2,3,7],[4,5,11]),box([12,3,7],[14,5,11]),box([2,3,4],[3,4,7]),box([13,3,4],[14,4,7])])
# Neutral item-location markers, separate assets clearly named as placeholders.
put('target_marker',[box([7,0,7],[9,4,9],'joint'),box([6.5,4,7.5],[9.5,10,8.5],'joint'),box([7.25,10,7.5],[8.75,12,8.5],'joint')])
put('material_marker',[box([4,0,5],[12,3,11],'joint'),box([5,3,6],[11,4,10],'joint')])
old=m.PACK;m.PACK=LAB
def get(n,at=(0,0,0)):return m.boxes('projects:infusion-v2/'+n,at,centered=False)
def scene(v,state):
    p=get('center_'+v)
    p+=get('focus_'+v,(0,1.9 if v=='a' else (.75 if state=='active' else 0),0))
    for x,z in [(2,0),(0,2),(-2,0),(0,-2)]:
        p+=get('side_'+v,(x,0,z))
        if state!='empty' and state!='complete':p+=get('material_marker',(x,.875 if v=='a' else .22,z))
    if state!='empty':p+=get('target_marker',(0,.40 if v=='a' else .18,0))
    return p
OUT=m.OUT
sheet=Image.new('RGB',(1600,1040),'#1b201b')
m.text(sheet,(25,12),'A / B 造形だけの比較：中央受け・焦点・周辺材料皿',28)
for col,v in enumerate(['a','b']):
    title='A：くぼんだ白灰鉢 + 分割冠 / 高さのある儀式' if v=='a' else 'B：低い象嵌盤 + 流入溝 / 稼働時に焦点が浮く'
    m.text(sheet,(col*800+22,65),title,22)
    for row,(yaw,el,label) in enumerate([(0,17,'正面 / 空'),(38,28,'斜め / 材料の位置')]):
        tile=m.render(scene(v,'empty' if row==0 else 'loaded'),(770,405),yaw,el)
        sheet.paste(tile,(col*800+15,105+row*435));m.text(sheet,(col*800+32,118+row*435),label,18)
m.text(sheet,(25,992),'独自の実JSON形状 / 灰色マーカーは配置説明用。色・模様・詳細材質は未制作。ゲーム構造判定へ未接続。',18)
sheet.save(OUT/'ProjectS-Infusion-Shape-AB.png')
detail=Image.new('RGB',(1600,760),'#1b201b');m.text(detail,(25,12),'中央と周辺を別の形に：実際の凹みと素材受け',28)
for col,(v,n,label) in enumerate([('a','center_a','A 中央：10pxの凹み'),('a','side_a','A 周辺：細首＋受け皿'),('b','center_b','B 中央：溝＋6px受け口'),('b','side_b','B 周辺：低い供物トレー')]):
    tile=m.render(get(n),(375,595),38,32);detail.paste(tile,(col*400+12,80));m.text(detail,(col*400+20,690),label,18)
detail.save(OUT/'ProjectS-Infusion-Receivers-AB.png')
stages=Image.new('RGB',(1600,960),'#1b201b');m.text(stages,(25,12),'配置と段階の形確認 / 動作のゲーム統合前',28)
for row,v in enumerate(['a','b']):
 for col,(state,label) in enumerate([('empty','空'),('loaded','中央品と周辺素材'),('active','供給中：焦点位置'),('complete','吸収後：中央品が残る')]):
  tile=m.render(scene(v,state),(380,360),38,28);stages.paste(tile,(col*400+10,70+row*435))
  m.text(stages,(col*400+20,78+row*435),v.upper()+' / '+label,17)
m.text(stages,(25,918),'形の比較用。素材マーカー・段階配置は説明用仮定。Essentia煙と完成品の性能はこの画像に未収録。',18)
stages.save(OUT/'ProjectS-Infusion-Shape-Stages.png')
files={str(p.relative_to(LAB)):hashlib.sha256(p.read_bytes()).hexdigest() for p in LAB.rglob('*') if p.is_file()}
(OUT/'shape-ab-provenance.json').write_text(json.dumps({'originalMinecraftModels':True,'style':'unpainted grey shape study','runtimeConnected':False,'referenceImagesRedistributed':False,'files':files},indent=2),encoding='utf8')
print('A/B original model silhouettes, receivers and stages rendered. Old models and game structure unchanged.')
