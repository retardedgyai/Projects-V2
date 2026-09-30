"""Original materialled Minecraft model study: floating carved stone and dedicated ritual kit.
Manual pixel drawing / native cuboids only; no reference geometry or images are copied.
Preserves v1 live models and v2 grey studies. This v3 kit awaits art review before runtime adoption.
"""
from pathlib import Path
import json,hashlib,importlib.util,math
from PIL import Image,ImageDraw
spec=importlib.util.spec_from_file_location('m',Path(__file__).with_name('render-world-models.py'))
m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)
LAB=m.ROOT/'assets/model-lab/infusion-v3';LAB.mkdir(parents=True,exist_ok=True)
TEX=LAB/'assets/projects/textures/infusion-v3';TEX.mkdir(parents=True,exist_ok=True)

# Six coordinated, low-colour 16px masters. All shaping/marks below are explicitly pixel drawn.
def stone(light=False):
    pal=['#aaa58f','#b9b39d','#cac3a9','#8e8e81','#74776e'] if light else ['#51505a','#5e5b65','#706a75','#43454e','#32363e']
    im=Image.new('RGBA',(16,16),pal[0]);d=ImageDraw.Draw(im)
    for rect,k in [((0,0,9,2),1),((3,2,7,3),1),((12,1,15,5),3),((2,6,5,9),3),((3,7,4,8),4),
                   ((9,6,13,8),1),((10,8,15,10),1),((0,12,5,15),3),((5,13,10,15),3),((9,13,12,14),4),((6,4,8,5),2),((1,10,2,11),1)]:d.rectangle(rect,fill=pal[k])
    for p in [(11,3),(7,10),(14,12),(2,3),(8,7)]:d.point(p,fill=pal[1])
    d.line([(0,0),(15,0)],fill=pal[2]);d.line([(0,0),(0,15)],fill=pal[1]);d.line([(0,15),(15,15)],fill=pal[4])
    return im
stone().save(TEX/'basalt.png');stone(True).save(TEX/'limestone.png')
brass=Image.new('RGBA',(16,16),'#796a48');d=ImageDraw.Draw(brass)
d.rectangle((0,0,15,2),fill='#a99463');d.line((0,0,15,0),fill='#c5b181');d.rectangle((0,11,15,15),fill='#594f38')
for r in [(2,5,5,7),(9,3,12,4),(12,8,15,9)]:d.rectangle(r,fill='#8d7b50')
brass.save(TEX/'brass.png')
glyph=stone();d=ImageDraw.Draw(glyph)
strokes=[[(4,4),(11,4),(11,7),(8,7),(8,11),(4,11),(4,8),(6,8)],[(11,10),(12,11),(12,13)],[(3,3),(2,2)],[(6,13),(8,13)]]
for pts in strokes:
    d.line([(x,y+1) for x,y in pts],fill='#817989',width=1)
    d.line(pts,fill='#242934',width=1)
for p in [(5,4),(6,4),(7,4),(8,7),(8,8),(5,11)]:d.point(p,fill='#a99aac')
glyph.save(TEX/'carved.png')
groove=stone(True);d=ImageDraw.Draw(groove)
d.rectangle((3,3,12,12),outline='#676b63');d.line((4,4,11,4),fill='#d6cdb1');d.rectangle((6,6,9,9),fill='#85887b')
groove.save(TEX/'well.png')
glass=Image.new('RGBA',(16,16),(118,145,145,25));d=ImageDraw.Draw(glass)
d.rectangle((0,0,15,15),outline=(74,98,102,210));d.line((2,2,2,10),fill=(185,204,188,150));d.line((3,2,5,2),fill=(208,220,199,150))
glass.save(TEX/'glass.png')

def box(lo,hi,tex='basalt',rotation=None,glyph=False):
    x,y,z=lo;X,Y,Z=hi
    uvs={'north':[16-X,16-Y,16-x,16-y],'south':[x,16-Y,X,16-y],
         'west':[z,16-Y,Z,16-y],'east':[16-Z,16-Y,16-z,16-y],
         'up':[x,z,X,Z],'down':[x,16-Z,X,16-z]}
    # UVs span exactly one source pixel per model unit. Tall supports repeat the same authored stone.
    faces={f:{'texture':'#'+('carved' if glyph and f in ['north','east'] else tex),'uv':uv} for f,uv in uvs.items()}
    e={'from':lo,'to':hi,'faces':faces}
    if rotation:e['rotation']={'origin':rotation[0],'axis':rotation[1],'angle':rotation[2],'rescale':False}
    return e
def put(n,els):
    p=LAB/f'assets/projects/models/infusion-v3/{n}.json';p.parent.mkdir(parents=True,exist_ok=True)
    p.write_text(json.dumps({'credit':'ProjectS original carved stone kit / hand drawn 16px textures','ambientocclusion':False,
       'textures':{n:'projects:infusion-v3/'+n for n in ['basalt','limestone','brass','carved','well','glass']},'elements':els},indent=2)+'\n',encoding='utf8')
    p=LAB/f'assets/projects/items/infusion-v3/{n}.json';p.parent.mkdir(parents=True,exist_ok=True)
    p.write_text(json.dumps({'model':{'type':'minecraft:model','model':'projects:infusion-v3/'+n}},indent=2)+'\n',encoding='utf8')

# A substantial stepped stone octagon, tilted in the native model, with a detached chipped corner.
r=([8,8,8],'z',22.5)
core=[]
for lo,hi in [([0,4,3],[16,12,13]),([2,2,3],[14,4,13]),([4,0,4],[12,2,12]),
              ([2,12,3],[14,14,13]),([4,14,4],[12,16,12])]:core.append(box(lo,hi,rotation=r,glyph=True))
# Carved relief seam on the front; brass lives at two contact pins, never enclosing the stone.
core += [box([1,5,2.5],[2,10,3],rotation=r),box([14,6,2.5],[15,11,3],rotation=r),
         box([5,-2,5],[9,-.6,9],rotation=r,glyph=True),box([16.7,9,5],[19.5,13,10],rotation=r,glyph=True),
         box([0,7,2.4],[1,9,3],'brass',r),box([13,13,2.4],[14,14,3],'brass',r)]
put('core',core)

# Center: broad socket, clear actual concavity, heavy lower plinth and a narrow carved waist.
center=[box([1,0,1],[15,2,15]),box([2,2,2],[14,3,14],'limestone'),box([4,3,4],[12,8,12],'limestone'),
        box([3,8,3],[13,9,13],'brass'),box([1,9,1],[15,11,15],'limestone'),box([3,11,3],[13,12,13],'well')]
for lo,hi in [([0,11,1],[3,14,15]),([13,11,1],[16,14,15]),([3,11,0],[13,14,3]),([3,11,13],[13,14,16])]:center.append(box(lo,hi,'limestone'))
for lo,hi in [([6,3,3.5],[10,7,4]),([6,3,12],[10,7,12.5])]:center.append(box(lo,hi,'basalt',glyph=True))
put('center',center)

# Slim side dish: open rim, inset landing area, flared foot. Fewer masses than the center.
side=[box([2,0,2],[14,2,14]),box([4,2,4],[12,3,12],'limestone'),box([5,3,5],[11,10,11],'limestone'),
      box([4,9,4],[12,10,12],'brass'),box([2,10,2],[14,12,14],'limestone'),box([4,12,4],[12,12.5,12],'well')]
for lo,hi in [([2,12,2],[14,13.5,4]),([2,12,12],[14,13.5,14]),([2,12,4],[4,13.5,12]),([12,12,4],[14,13.5,12])]:side.append(box(lo,hi,'limestone'))
put('offering',side)

# Dedicated two-block stone supports. Top leans toward the focus; not a material stand.
support=[box([1,0,1],[15,2,15]),box([3,2,3],[13,4,13],'limestone'),box([5,4,5],[11,15,11],'basalt',glyph=True),
         box([4,14,4],[12,16,12],'limestone'),box([5,16,5],[11,28,11],'basalt',([8,16,8],'z',22.5),glyph=True),
         box([1,26,5],[7,28,11],'limestone'),box([1,28,5],[7,29,11],'brass'),
         box([2,29,6],[6,31,10],'basalt',glyph=True)]
put('support',support)

# Coordinated physical Jar silhouette: broad reservoir, shoulder, narrow mouth, distinct lid.
jar=[box([3,0,3],[13,1.5,13]),box([3,1.5,3],[13,3,13],'brass'),box([4,3,4],[12,11,12],'glass'),
     box([3.5,11,3.5],[12.5,12,12.5],'brass'),box([5,12,5],[11,14,11],'glass'),
     box([4.5,14,4.5],[11.5,15,11.5],'basalt'),box([5,15,5],[11,16,11],'brass')]
put('jar',jar)

old=m.PACK;m.PACK=LAB
def get(n,at=(0,0,0),yaw=0):return m.boxes('projects:infusion-v3/'+n,at,centered=False,yaw=yaw)
def assembly():
    p=get('center')+get('core',(0,2.75,0))
    # Keep focus high and target visible in the open corridor between the four leaning supports.
    for x,z,rot in [(-1,-1,135),(1,-1,45),(-1,1,-135),(1,1,-45)]:p+=get('support',(x,0,z),rot)
    for x,z in [(-3,0),(3,0),(0,3),(0,-3)]:
        p+=get('offering',(x,0,z))
    for x,z in [(-4,2),(4,2)]:p+=get('jar',(x,0,z))
    return p

OUT=m.OUT
parts=Image.new('RGB',(1600,1120),'#1b201b');m.text(parts,(26,14),'浮遊する刻印石 / 専用の祭壇一式・材質付き初稿',28)
entries=[('core','浮遊核：厚い石・彫刻・傾き'),('center','中央台：装備を受ける凹み'),('offering','材料台：細い首・浅い皿'),('support','支柱：核へ向く石の腕')]
for col,(n,label) in enumerate(entries):
    m.text(parts,(col*400+20,65),label,17)
    for row,(yaw,el,label2) in enumerate([(0,12,'正面'),(38,26,'斜め')]):
        tile=m.render(get(n),(375,460),yaw,el);parts.paste(tile,(col*400+12,95+row*485));m.text(parts,(col*400+25,105+row*485),label2,16)
m.text(parts,(25,1078),'実JSON・16px PNGを直接描画。専用モデルだけで構成／旧モデル保護。Minecraft実機の画像ではありません。',17)
parts.save(OUT/'ProjectS-Infusion-Stone-Kit-Parts.png')
scene=Image.new('RGB',(1600,990),'#1b201b');m.text(scene,(26,14),'浮遊核を主役にした祭壇 / 専用支柱・中央台・材料台・Jar',28)
for col,(yaw,el,label) in enumerate([(0,12,'正面：核と対象品の間に余白'),(38,26,'斜め：外側の材料皿とJar')]):
    tile=m.render(assembly(),(775,800),yaw,el);scene.paste(tile,(col*800+12,78));m.text(scene,(col*800+28,88),label,18)
m.text(scene,(25,905),'造形確認用の一式。素材を置く凹み・核へ傾く支柱・瓶の口を役割ごとに分けています。',19)
m.text(scene,(25,946),'新モデルはまだゲーム構造へ未接続。供給色の霧・装備モデル・ゲームの照明はこの静止画に未収録。',18)
scene.save(OUT/'ProjectS-Infusion-Stone-Kit-Assembly.png')
texsheet=Image.new('RGB',(1200,270),'#1b201b');m.text(texsheet,(25,12),'手で描いた16px材質 / 原寸と拡大',22)
for i,n in enumerate(['basalt','carved','limestone','well','brass','glass']):
    im=Image.open(TEX/(n+'.png'));texsheet.paste(im.resize((144,144),Image.Resampling.NEAREST),(i*200+26,57),im.resize((144,144),Image.Resampling.NEAREST))
    texsheet.paste(im,(i*200+26,218),im);m.text(texsheet,(i*200+52,218),n,16)
texsheet.save(OUT/'ProjectS-Infusion-Stone-Kit-Pixels.png')
files={str(p.relative_to(LAB)):hashlib.sha256(p.read_bytes()).hexdigest() for p in LAB.rglob('*') if p.is_file()}
(OUT/'stone-kit-provenance.json').write_text(json.dumps({'originalNativeModels':True,'runtimeConnected':False,'minecraftScreenshot':False,
  'elementRotationRendered':True,'referenceImagesRedistributed':False,'textureDensity':'16px per block, manual UV','files':files},indent=2),encoding='utf8')
print('Original materialled stone kit generated; live models unchanged; element rotations rendered.')
