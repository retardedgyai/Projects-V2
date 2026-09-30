"""Original native 26.2 forbidden-device study. Actual cuboids, pixels and animated emission.
No image generation or postprocess Bloom. Previous studies and the running world are untouched.
"""
from pathlib import Path
import json,hashlib,importlib.util,shutil,math,sys
import numpy as np
from PIL import Image,ImageDraw
spec=importlib.util.spec_from_file_location('m',Path(__file__).with_name('render-world-models.py'))
m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)
V3=m.ROOT/'assets/model-lab/infusion-v3';V4=m.ROOT/'assets/model-lab/infusion-v4'
NEW=m.ROOT/'assets/model-lab/infusion-v5';OUT=m.OUT
def hashes(root):return {str(p.relative_to(root)):hashlib.sha256(p.read_bytes()).hexdigest() for p in root.rglob('*') if p.is_file()}
protected={'v3':hashes(V3),'v4':hashes(V4)}
for p in (V3/'assets/projects/textures/infusion-v3').glob('*.png'):
    dest=NEW/p.relative_to(V3);dest.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(p,dest)
T=NEW/'assets/projects/textures/infusion-v5';T.mkdir(parents=True,exist_ok=True)
# Deliberate angular intaglio, short pixel clusters, dark stone, no natural random crack pattern.
panel=Image.new('RGBA',(16,16),'#45434f');d=ImageDraw.Draw(panel)
for y in [0,5,11]:d.rectangle((0,y,15,y+1),fill='#4e4b57')
for xy in [(1,2,3,3),(11,7,14,8),(4,12,6,13)]:d.rectangle(xy,fill='#56525e')
paths=[[(1,1),(7,1),(7,4),(10,4),(10,8),(14,8)],[(2,5),(5,5),(5,9),(2,9),(2,14),(7,14)],
       [(9,11),(13,11),(13,14),(10,14)],[(11,1),(14,1),(14,5)]]
for pts in paths:
    d.line([(x,y+1) for x,y in pts],fill='#6e6375',width=1)
    d.line(pts,fill='#1c1b29',width=1)
panel.save(T/'sealed_carved.png')
well=Image.new('RGBA',(16,16),'#24222f');wd=ImageDraw.Draw(well)
for xy in [(1,3,5,4),(9,8,13,9),(4,13,8,14)]:wd.rectangle(xy,fill='#302b3c')
well.save(T/'seal_well.png')
# Pixel light sits at the bottom of a physical channel; it is not painted on the entire stone face.
def glow_frame(p):
    im=Image.new('RGBA',(16,16),(0,0,0,0));d=ImageDraw.Draw(im)
    outer=tuple(round(x*p) for x in (119,48,193))+(255,)
    middle=tuple(round(x*p) for x in (176,91,236))+(255,)
    hot=tuple(round(x*p) for x in (231,178,255))+(255,)
    d.rectangle((0,0,15,15),fill=outer)
    for y in range(0,16,4):d.rectangle((2,y,13,y+1),fill=middle)
    for xy in [(4,2,5,5),(10,7,11,9),(7,12,9,13)]:d.rectangle(xy,fill=hot)
    return im
glow_frame(.29).save(T/'seal_idle.png')
frames=[glow_frame(.43+.57*(.5-.5*math.cos(2*math.pi*i/24))) for i in range(24)]
strip=Image.new('RGBA',(16,16*len(frames)))
for i,f in enumerate(frames):strip.paste(f,(0,i*16))
strip.save(T/'seal_active.png')
(T/'seal_active.png.mcmeta').write_text(json.dumps({'animation':{'frametime':2,'interpolate':True}},indent=2)+'\n',encoding='utf8')
# A real graded-alpha sprite inside the aperture provides a small soft leak, not postprocess Bloom.
def leak_frame(p):
    im=Image.new('RGBA',(16,16));pixels=im.load()
    for y in range(16):
        for x in range(16):
            edge=max(0,1-abs(x-7.5)/6.5)*max(0,1-abs(y-7.5)/8)
            a=round((edge**1.4)*92*p)
            pixels[x,y]=(134,54,210,a)
    return im
leak_frame(.29).save(T/'leak_idle.png')
leak_frames=[leak_frame(.43+.57*(.5-.5*math.cos(2*math.pi*i/24))) for i in range(24)]
strip=Image.new('RGBA',(16,384))
for i,f in enumerate(leak_frames):strip.paste(f,(0,i*16))
strip.save(T/'leak_active.png')
(T/'leak_active.png.mcmeta').write_text(json.dumps({'animation':{'frametime':2,'interpolate':True}},indent=2)+'\n',encoding='utf8')

tilt=([8,8,8],'z',22.5)
def box(name,lo,hi,tex='basalt',emission=0,front=None,uv=None):
    x,y,z=lo;X,Y,Z=hi
    uvs={'north':[16-X,16-Y,16-x,16-y],'south':[x,16-Y,X,16-y],
         'west':[z,16-Y,Z,16-y],'east':[16-Z,16-Y,16-z,16-y],
         'up':[x,z,X,Z],'down':[x,16-Z,X,16-z]}
    e={'name':name,'from':lo,'to':hi,'rotation':{'origin':tilt[0],'axis':tilt[1],'angle':tilt[2],'rescale':False},
       'faces':{f:{'texture':'#'+(front if f in ['north','south'] and front else tex),'uv':uv or v} for f,v in uvs.items()}}
    if emission:e.update(light_emission=emission,shade=False)
    return e
core=[]
# Machined stepped-octagonal shell. Opposing plates have a wide, precise through-aperture.
for side,(a,b) in enumerate([(-7,4),(12,23)]):
    core.append(box('sealed flank '+str(side),[a,0,2],[b,22,14],front='carved'))
    core.append(box('bevel flank '+str(side),[a-1 if side==0 else b,4,4],[a if side==0 else b+1,18,12]))
for name,lo,hi in [
 ('upper bridge',[-4,22,3],[20,26,13]),('upper crown',[-1,26,4],[17,28,12]),
 ('lower bridge',[-4,-4,3],[20,0,13]),('lower keel',[-1,-6,4],[17,-4,12])]:
    core.append(box(name,lo,hi,front='carved'))
# Two recessed vertical channels are cut into the wide front planes, with independent stone lips.
for x in [-3,18]:
    core.append(box('recess bed',[x-1,3,.6],[x+1,19,2],'well'))
    for off in [-2,1]:core.append(box('channel lip',[x+off,2,.2],[x+off+1,20,2],'basalt'))
    for y in [4,10,16]:
        core.append(box('sealed incision',[x-.35,y,.55],[x+.35,y+3,.7],'glow',15,uv=[0,0,4,12]))
# Purple light is recessed behind the inward stone bevel. Air remains on each side of the seal.
for x in [4,11.5]:
    core.append(box('inner channel',[x,2,4],[x+.5,20,12],'well'))
    core.append(box('inner emission',[x+.12,3,4.5],[x+.38,19,11.5],'glow',15))
# A deliberately manufactured spindle floats in the cut aperture. Symmetric, no random rubble.
for y,h,w in [(2,3,1),(5,4,2),(9,4,3),(13,4,2),(17,3,1)]:
    core.append(box('contained seal',[8-w/2,y,6],[8+w/2,y+h,10],'well'))
    core.append(box('seal filament',[7.8,y+.2,5.8],[8.2,y+h-.2,6],'glow',15,uv=[4,0,8,16]))
core.append({'name':'aperture light sprite','from':[4.8,2,8],'to':[11.2,20,8],
  'rotation':{'origin':tilt[0],'axis':tilt[1],'angle':tilt[2],'rescale':False},'shade':False,'light_emission':15,
  'faces':{face:{'texture':'#leak','uv':[0,0,16,16]} for face in ['north','south']}})
# Four purposefully placed restraints grip the corners. Back straps complete the restraint geometry.
for x in [-5,19]:
    for y in [0,20]:
        core.append(box('restraint catch',[x-1,y,.2],[x+1,y+2,4],'brass'))
        core.append(box('stone over catch',[x-.5,y+.45,-.1],[x+.5,y+1.55,.2],'well'))
    core.append(box('back binding',[x-.5,-1,14],[x+.5,23,14.7],'brass'))
textures={n:'projects:infusion-v3/'+n for n in ['basalt','limestone','brass','well','glass']}
textures.update(carved='projects:infusion-v5/sealed_carved',well='projects:infusion-v5/seal_well',
  glow='projects:infusion-v5/seal_idle',leak={'sprite':'projects:infusion-v5/leak_idle','force_translucent':True})
M=NEW/'assets/projects/models/infusion-v5';I=NEW/'assets/projects/items/infusion-v5'
M.mkdir(parents=True,exist_ok=True);I.mkdir(parents=True,exist_ok=True)
base={'credit':'ProjectS original engineered seal / manually drawn pixel textures','ambientocclusion':False,'textures':textures,'elements':core}
for state in ['idle','active','unlit']:
    texture_state='idle' if state=='unlit' else state
    data={**base,'textures':{**textures,'glow':'projects:infusion-v5/seal_'+texture_state,
      'leak':{'sprite':'projects:infusion-v5/leak_'+texture_state,'force_translucent':True}}}
    if state=='unlit':data['elements']=[{k:v for k,v in e.items() if k not in ['light_emission','shade']} for e in core]
    (M/('core_'+state+'.json')).write_text(json.dumps(data,indent=2)+'\n',encoding='utf8')
    (I/('core_'+state+'.json')).write_text(json.dumps({'model':{'type':'minecraft:model','model':'projects:infusion-v5/core_'+state}},indent=2)+'\n',encoding='utf8')

def get(n,v='new',at=(0,0,0),yaw=0):
    if n.startswith('core') and v=='new':m.PACK=NEW;name='projects:infusion-v5/'+n
    elif n in ['core','support']:m.PACK=V4;name='projects:infusion-v4/'+n
    else:m.PACK=V3;name='projects:infusion-v3/'+n
    for texture in m.model(name)['textures'].values():m.texture(texture['sprite'] if isinstance(texture,dict) else texture)
    return m.boxes(name,at,centered=False,yaw=yaw)
def assembly(v='new',state='idle'):
    p=get('center')+get('core_'+state if v=='new' else 'core',v,(0,2.9,0))
    for x,z,yaw in [(-1,-1,135),(1,-1,45),(-1,1,-135),(1,1,-45)]:p+=get('support','old',(x,0,z),yaw)
    for x,z in [(-3,0),(3,0),(0,3),(0,-3)]:p+=get('offering',at=(x,0,z))
    for x,z in [(-4,2),(4,2)]:p+=get('jar',at=(x,0,z))
    return p
def bound(parts):
    pts=np.concatenate([np.array([[x,y,z] for x in [lo[0],hi[0]] for y in [lo[1],hi[1]] for z in [lo[2],hi[2]]])@r.T+t for lo,hi,_,_,r,t in parts])
    return pts.min(0),pts.max(0)
def union(a,b):return np.minimum(a[0],b[0]),np.maximum(a[1],b[1])

if __name__=='__main__' and '--assets-only' in sys.argv:
    record=json.loads((OUT/'sealed-core-provenance.json').read_text())
    record['files']=hashes(NEW)
    record['unlitFallback']='projects:infusion-v5/core_unlit'
    (OUT/'sealed-core-provenance.json').write_text(json.dumps(record,indent=2)+'\n',encoding='utf8')
    print(json.dumps({'assetsOnly':True,'models':3}))
elif __name__=='__main__':
    previous=assembly('old');current=assembly();fr=union(bound(previous),bound(current))
    compare=Image.new('RGB',(1600,1430),'#191c20');m.text(compare,(26,16),'人工の封印核 / 同じ角度・縮尺で前後比較',28)
    for row,(yaw,el) in enumerate([(0,12),(38,26)]):
        for col,(p,label) in enumerate([(previous,'前：割れ石の核'),(current,'後：加工石殻・拘束具・深い封印孔')]):
            compare.paste(m.render(p,(775,610),yaw,el,frame_bounds=fr),(12+col*800,82+row*635))
            m.text(compare,(28+col*800,83+row*635),label,18)
    m.text(compare,(26,1378),'支柱・中央台・材料台・Jarは前版を保持。実JSON/PNGのモデルプレビュー。ゲーム接続前。',17)
    compare.save(OUT/'ProjectS-Infusion-Sealed-Core-Comparison.png')
    states=Image.new('RGB',(1500,1020),'#171921');m.text(states,(25,16),'禁忌の装置 / 刻印と内部だけを紫に発光',28)
    coreframe=union(bound(get('core','old')),bound(get('core_idle')))
    for col,(state,ambient,label) in enumerate([('idle',1,'通常 / 静かな封印'),('idle',.14,'暗所 / 自発光'),('active',.14,'作動 / 内部光が脈打つ')]):
        if state=='active':
            m.textures['projects:infusion-v5/seal_active']=np.array(frames[12])
            m.textures['projects:infusion-v5/leak_active']=np.array(leak_frames[12])
        for row,(yaw,el) in enumerate([(0,12),(38,26)]):
            states.paste(m.render(get('core_'+state),(475,420),yaw,el,frame_bounds=coreframe,ambient=ambient,background='#171921'),(12+500*col,92+445*row))
        m.text(states,(23+500*col,63),label,18)
    m.text(states,(25,960),'native発光＋透過sprite＋PNGアニメーション。照明は概算表示。Bloom・周辺紫照明は未実装。',16)
    states.save(OUT/'ProjectS-Infusion-Sealed-Core-Light-States.png')
    hero=Image.new('RGB',(1500,1080),'#191c20');m.text(hero,(26,16),'封じられた石の核 / 専用モデル一式',28)
    hero.paste(m.render(current,(1460,920),38,26,frame_bounds=fr),(20,74))
    m.text(hero,(26,1020),'造形・画素・局所発光の比較試作。実client未確認。Shaderpackや新client追加なし。',18)
    hero.save(OUT/'ProjectS-Infusion-Sealed-Core-Hero.png')
    # The animation uses exactly the authored native PNG frames, not CPU-added light/blur.
    anim=[];static_parts=get('core_active');bounds=bound(static_parts)
    for i,f in enumerate(frames):
        m.textures['projects:infusion-v5/seal_active']=np.array(f)
        m.textures['projects:infusion-v5/leak_active']=np.array(leak_frames[i])
        frame=Image.new('RGB',(820,700),'#171921')
        frame.paste(m.render(static_parts,(780,575),38,26,frame_bounds=bounds,ambient=.14,background='#171921'),(20,65))
        m.text(frame,(22,14),'封印核 / 作動時の紫光',24)
        m.text(frame,(22,653),'実PNGの24フレーム。概算照明 / Bloomなし / ゲーム接続前',14)
        anim.append(frame)
    anim[0].save(OUT/'ProjectS-Infusion-Sealed-Core-Pulse.gif',save_all=True,append_images=anim[1:],duration=100,loop=0,optimize=True)
    assert hashes(V3)==protected['v3'] and hashes(V4)==protected['v4']
    record={'minecraftVersion':'26.2','nativeModels':True,'runtimeConnected':False,'minecraftScreenshot':False,
      'renderer':'offline exact native cuboid/UV; ambient approximation; no postprocess light or Bloom',
      'lightEmission':15,'glowShade':False,'activeAnimation':{'frames':24,'frametimeTicks':2,'interpolate':True},
      'operatingStateContract':'select core_idle/core_active via existing ItemDisplay item model; caller not yet wired',
      'unlitFallback':'projects:infusion-v5/core_unlit',
      'maxCoreDisplayEntities':1,'serverAnimationPackets':0,'bloom':False,'coloredWorldLight':False,
      'protected':protected,'files':hashes(NEW),'onlyChangedProp':'core','supportVersion':'v4'}
    (OUT/'sealed-core-provenance.json').write_text(json.dumps(record,indent=2)+'\n',encoding='utf8')
    print(json.dumps({'images':3,'animation':1,'coreCuboids':len(core),'protected':sum(map(len,protected.values())),'runtimeConnected':False}))
