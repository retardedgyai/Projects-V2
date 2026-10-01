"""Exact native models/pixels + trace exported by the same Kotlin ritual/animation/smoke kernels.
Offline projection/lighting/sort approximations; no generated art, Bloom or Minecraft capture.
"""
from pathlib import Path
import json,math,hashlib,importlib.util,io
import numpy as np
from PIL import Image,ImageDraw
spec=importlib.util.spec_from_file_location('m',Path(__file__).with_name('render-world-models.py'))
m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)
TRACE=m.ROOT/'server-minestom/.tools/world-infusion-evidence/ritual-animation-trace.json'
trace=json.loads(TRACE.read_text());OUT=m.OUT;SIZE=(900,560);BG='#242922'
TEMP=OUT/'ritual-frames';TEMP.mkdir(exist_ok=True)
def get(name,at=(0,0,0),yaw=0):return m.boxes('projects:'+name,at,centered=False,yaw=yaw)
def liquid(at,amount,rgb):
    if not amount:return []
    ref='liquid:'+str(rgb);m.textures[ref]=np.full((16,16,4),[(rgb>>16)&255,(rgb>>8)&255,rgb&255,180],dtype='uint8')
    lo=np.array([.32,.13,.32]);hi=np.array([.68,.13+.5*amount/4,.68])
    faces={f:{'texture':ref,'uv':[0,0,16,16],'_light_emission':12,'_shade':True} for f in m.normals}
    return [(lo,hi,faces,{},np.eye(3),np.array(at))]
locations=[(-4,2),(4,2),(0,5)];colours=[0xee9850,0x84b6bd,0xc6d6a0]
pedestals=[(3,0),(0,3),(-3,0),(0,-3)]
def baseparts(amounts):
    p=get('infusion-v6/center')
    for x,z,yaw in [(-1,-1,135),(1,-1,45),(-1,1,-135),(1,1,-45)]:p+=get('infusion-v4/support',(x,0,z),yaw)
    for x,z in pedestals:p+=get('infusion-v6/offering',(x,0,z))
    for (x,z),amount,rgb in zip(locations,amounts,colours):p+=get('infusion-v3/jar',(x,0,z))+liquid((x,0,z),amount,rgb)
    return p
def bound(parts):
    pts=np.concatenate([np.array([[x,y,z] for x in [lo[0],hi[0]] for y in [lo[1],hi[1]] for z in [lo[2],hi[2]]])@r.T+t for lo,hi,_,_,r,t in parts])
    return pts.min(0),pts.max(0)
frame_bounds=bound(baseparts([4,4,4])+get('infusion-v7/core_ritual',(0,2.9,0)))
cache={};cloud=np.array(Image.open(m.PACK/'assets/projects/textures/infusion/smoke.png').convert('RGBA'))
props={r:Image.open(m.PACK/f'assets/projects/textures/item/forge_materials/{name}.png').convert('RGBA') for r,name in [('INGOT','ingot'),('CLOTH','cloth'),('STONE_BLOCK','cut_stone')]}
weapon=Image.open(io.BytesIO(m.NATIVE.read('assets/minecraft/textures/item/iron_sword.png'))).convert('RGBA')
def stamp(canvas,depth,project,factor,pos,image,width):
    p,d=project(pos);n=max(2,round(factor*width));im=image.resize((n,n),Image.Resampling.NEAREST)
    left,top=round(p[0]-n/2),round(p[1]-n/2);x0,y0=max(0,left),max(0,top);x1,y1=min(SIZE[0],left+n),min(SIZE[1],top+n)
    if x0>=x1 or y0>=y1:return
    a=np.array(im.crop((x0-left,y0-top,x1-left,y1-top)))
    a[:,:,3]=np.where(depth[y0:y1,x0:x1]>d,0,a[:,:,3]);canvas.alpha_composite(Image.fromarray(a),(x0,y0))
def compose(scene,f):
    key=tuple(f['amounts'])
    if key not in cache:
        cache[key]=m.render(baseparts(key),SIZE,38,26,with_depth=True,frame_bounds=frame_bounds)
    base,depth,project,factor=cache[key];canvas=base.copy().convert('RGBA')
    core,cd,_,_=m.render(get('infusion-v7/core_ritual',(0,2.9,0),f['yaw']),SIZE,38,26,
       with_depth=True,frame_bounds=frame_bounds,item_tint=(f['glow'],)*3)
    a=np.array(core);b=np.array(canvas);changed=np.any(a!=np.array([36,41,34]),axis=2)
    mask=changed & ((cd>depth)|((cd<-99999)&(depth<-99999)))
    b[mask,:3]=a[mask];canvas=Image.fromarray(b);depth=np.maximum(depth,cd)
    for (x,z),resource in zip(pedestals,f['items']):
        if resource:stamp(canvas,depth,project,factor,(x+.5,1.12,z+.5),props[resource],.55)
    # Equipment icon is a fixture marker, not a redesign of the actual production weapon mesh.
    stamp(canvas,depth,project,factor,(.5,1.15,.5),weapon,.55)
    pts=[]
    for x,y,z,rgb,scale,u in f['samples']:
        p,d=project((x,y,z));pts.append((d,p,rgb,scale))
    for d,p,rgb,scale in sorted(pts):
        pixels=cloud.copy();tint=np.array([(rgb>>16)&255,(rgb>>8)&255,rgb&255])/255
        pixels[:,:,:3]=(pixels[:,:,:3]*tint).astype('uint8')
        # Same authored alpha, native tint and .26 world-space scale as the runtime billboards.
        n=max(2,round(factor*.26*scale));im=Image.fromarray(pixels).resize((n,n),Image.Resampling.NEAREST)
        left,top=round(p[0]-n/2),round(p[1]-n/2);x0,y0=max(0,left),max(0,top);x1,y1=min(SIZE[0],left+n),min(SIZE[1],top+n)
        if x0>=x1 or y0>=y1:continue
        a=np.array(im.crop((x0-left,y0-top,x1-left,y1-top)));a[:,:,3]=np.where(depth[y0:y1,x0:x1]>d,0,a[:,:,3])
        canvas.alpha_composite(Image.fromarray(a),(x0,y0))
    # The game uses a short Dust trail after each committed ordinary-item consumption.
    for e in scene['events']:
        age=f['tick']-e['tick']
        if e['kind']!='ingredient' or not 0<=age<=18:continue
        source=np.array([e['x']+.5,1.15,e['z']+.5]);yaw=next(v['yaw'] for v in scene['frames'] if v['tick']==e['tick'])
        angle=math.radians(yaw);x=-.1875*math.sin(math.pi/8);z=-.125
        target=np.array([.5+x*math.cos(angle)+z*math.sin(angle),3.4+.1875*math.cos(math.pi/8),.5-x*math.sin(angle)+z*math.cos(angle)])
        for i in range(5):
            u=max(0,min(1,age/18-i*.045));p,d=project(source+(target-source)*u+np.array([0,math.sin(u*math.pi)*.65,0]))
            ix,iy=round(p[0]),round(p[1])
            if 0<=ix<SIZE[0] and 0<=iy<SIZE[1] and depth[iy,ix]<=d:ImageDraw.Draw(canvas).rectangle((ix-1,iy-1,ix+1,iy+1),fill='#e1cfab')
    phase=f['phase'];tick=f['tick']
    label='材料を並べ、注入を起動' if phase=='READY' else '核が回転 / Jar由来の元素色のもやを吸引' if phase=='ESSENTIA' else '周辺素材を吸収' if phase=='INGREDIENTS' else '同じ装備の1枠を変性 / 一度光って減速'
    if phase=='INGREDIENTS' and f['samples']:label='最後のエッセンシアが核へ到達'
    if f['paused']:label='供給不足 / 新規放出停止、残りのもやは到達'
    if scene['scenario']=='cancel' and tick>=90:label='取消 / 新規放出停止、核は減速'
    frame=Image.new('RGB',(900,680),'#191c20')
    frame.paste(canvas.convert('RGB').crop((155,160,785,552)).resize(SIZE,Image.Resampling.NEAREST),(0,61))
    m.text(frame,(18,12),label,21)
    m.text(frame,(18,622),f"炎 {key[0]}/4   水 {key[1]}/4   風 {key[2]}/4     {tick/20:.1f}s",18)
    m.text(frame,(18,652),'実モデル＋共有Kotlinタイミング。独立描画 / 実client未確認 / 装備アイコンは仮表示',13)
    return frame

main=next(s for s in trace['scenes'] if s['scenario']=='complete')
contacts=[]
for i,f in enumerate(main['frames']):
    image=compose(main,f);image.save(TEMP/f'{i:03d}.png')
    if f['tick'] in [0,80,140,208,276,340]:contacts.append(image)
    if i%20==0:print('Rendered complete timeline tick',f['tick'],flush=True)
palette=Image.new('RGB',(2700,680))
for i,n in enumerate([15,70,138]):
    with Image.open(TEMP/f'{n:03d}.png') as im:palette.paste(im,(900*i,0))
palette=palette.quantize(colors=256)
indexed=[]
for p in sorted(TEMP.glob('*.png')):
    with Image.open(p) as im:indexed.append(im.quantize(palette=palette,dither=Image.Dither.NONE))
gif=OUT/'ProjectS-Infusion-Ritual-Animation.gif'
indexed[0].save(gif,save_all=True,append_images=indexed[1:],duration=100,loop=0,disposal=2,optimize=True)
assert gif.stat().st_size<10*1024*1024
sheet=Image.new('RGB',(1800,2040),'#191c20')
for i,im in enumerate(contacts):sheet.paste(im,(i%2*900,i//2*680))
sheet.save(OUT/'ProjectS-Infusion-Ritual-Animation-States.png')
stops=Image.new('RGB',(1800,1360),'#191c20')
for col,scenario in enumerate(['shortage','cancel']):
    scene=next(s for s in trace['scenes'] if s['scenario']==scenario)
    for row,tick in enumerate([140,210]):stops.paste(compose(scene,next(f for f in scene['frames'] if f['tick']==tick)),(900*col,680*row))
stops.save(OUT/'ProjectS-Infusion-Ritual-Stop-States.png')
record={'sharedKotlinTraceSHA256':hashlib.sha256(TRACE.read_bytes()).hexdigest(),'frames':len(indexed),'durationMs':len(indexed)*100,
 'gifBytes':gif.stat().st_size,'sameModelShapePalette':True,'wholeCoreRotation':True,'movingSealInlet':True,
 'gamePresenterConnected':True,'minecraftClientCapture':False,'clientFPSMeasured':False,
 'previewOnly':'orthographic renderer, approximate native lighting/transparency; equipment icon fixture marker',
 'bloom':False,'colouredWorldLighting':False,'ordinaryMaterials':'existing forge pixel textures; no magic-only materials'}
(OUT/'ritual-preview-verification.json').write_text(json.dumps(record,indent=2)+'\n',encoding='utf8');print(json.dumps(record,indent=2))
