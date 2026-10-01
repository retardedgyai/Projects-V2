"""Native models/pixels and Kotlin accepted transactions; supplemental supply is demo input.
Independent projection/lighting/transparency, not a client recording. No new art or Bloom.
"""
from pathlib import Path
import json,math,hashlib,importlib.util,io,argparse
import numpy as np
from PIL import Image,ImageDraw,ImageSequence
spec=importlib.util.spec_from_file_location('models',Path(__file__).with_name('render-world-models.py'))
m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)
TRACE=m.ROOT/'server-minestom/.tools/world-infusion-evidence/confluence-animation-trace.json'
trace=json.loads(TRACE.read_text());OUT=m.OUT;SIZE=(600,420)
TEMP=OUT/'confluence-frames';TEMP.mkdir(exist_ok=True)
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
    for (x,z),n,rgb in zip(locations,amounts,colours):p+=get('infusion-v3/jar',(x,0,z))+liquid((x,0,z),n,rgb)
    return p
parts=baseparts([4,4,4])+get('infusion-v7/core_ritual',(0,2.9,0))
pts=np.concatenate([np.array([[x,y,z] for x in [lo[0],hi[0]] for y in [lo[1],hi[1]] for z in [lo[2],hi[2]]])@r.T+t for lo,hi,_,_,r,t in parts])
frame_bounds=pts.min(0),pts.max(0)
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
    if key not in cache:cache[key]=m.render(baseparts(key),SIZE,38,26,with_depth=True,frame_bounds=frame_bounds)
    base,depth,project,factor=cache[key];canvas=base.copy().convert('RGBA')
    core,cd,_,_=m.render(get('infusion-v7/core_ritual',(0,2.9,0),f['yaw']),SIZE,38,26,
       with_depth=True,frame_bounds=frame_bounds,item_tint=(f['glow'],)*3)
    a=np.array(core);b=np.array(canvas);changed=np.any(a!=np.array([36,41,34]),axis=2)
    mask=changed & ((cd>depth)|((cd<-99999)&(depth<-99999)))
    b[mask,:3]=a[mask];canvas=Image.fromarray(b);depth=np.maximum(depth,cd)
    for (x,z),resource in zip(pedestals,f['items']):
        if resource:stamp(canvas,depth,project,factor,(x+.5,1.12,z+.5),props[resource],.55)
    stamp(canvas,depth,project,factor,(.5,1.15,.5),weapon,.55)
    points=sorted((project((x,y,z))[1],project((x,y,z))[0],rgb,scale) for x,y,z,rgb,scale,u in f['samples'])
    for d,p,rgb,scale in points:
        pixels=cloud.copy();tint=np.array([(rgb>>16)&255,(rgb>>8)&255,rgb&255])/255
        pixels[:,:,:3]=(pixels[:,:,:3]*tint).astype('uint8')
        n=max(2,round(factor*.26*scale));im=Image.fromarray(pixels).resize((n,n),Image.Resampling.NEAREST)
        left,top=round(p[0]-n/2),round(p[1]-n/2);x0,y0=max(0,left),max(0,top);x1,y1=min(SIZE[0],left+n),min(SIZE[1],top+n)
        if x0>=x1 or y0>=y1:continue
        a=np.array(im.crop((x0-left,y0-top,x1-left,y1-top)));a[:,:,3]=np.where(depth[y0:y1,x0:x1]>d,0,a[:,:,3])
        canvas.alpha_composite(Image.fromarray(a),(x0,y0))
    for e in scene['events']:
        age=f['tick']-e['tick']
        if e['kind']!='ingredient' or not 0<=age<=18:continue
        source=np.array([e['x']+.5,1.15,e['z']+.5]);angle=math.radians(f['yaw']);x=-.1875*math.sin(math.pi/8);z=-.125
        target=np.array([.5+x*math.cos(angle)+z*math.sin(angle),3.4+.1875*math.cos(math.pi/8),.5-x*math.sin(angle)+z*math.cos(angle)])
        for i in range(5):
            u=max(0,min(1,age/18-i*.045));p,d=project(source+(target-source)*u+np.array([0,math.sin(u*math.pi)*.65,0]))
            ix,iy=round(p[0]),round(p[1])
            if 0<=ix<SIZE[0] and 0<=iy<SIZE[1] and depth[iy,ix]<=d:ImageDraw.Draw(canvas).rectangle((ix-1,iy-1,ix+1,iy+1),fill='#e1cfab')
    phase=f['phase']
    if scene['scenario'].startswith('cancel') and f['tick']>=scene['stoppedTick']:label='取消し / 成功演出なし・残る霧は到達'
    elif f['paused']:label='元素不足 / 全元素の新規吸収を停止'
    elif phase=='READY':label='中央装備と通常素材を置いて起動'
    elif f['channel'] is not None:label='核 → 中央武器へ収束 / 変性を定着'
    elif phase=='ESSENTIA':label='必要な全元素を同時吸収'
    elif phase=='INGREDIENTS':label='最後の霧が到達 → 周囲素材を吸収' if f['samples'] else '周囲素材を吸収'
    else:label='成立 / 同じ武器の固定1枠が変性'
    frame=Image.new('RGB',(600,530),'#191c20')
    m.text(frame,(14,8),'補助供給 LOW 0' if scene['supply']==0 else '補助供給 HIGH 1',20)
    m.text(frame,(14,39),label,15)
    frame.paste(canvas.convert('RGB').crop((104,120,523,380)).resize((600,370),Image.Resampling.NEAREST),(0,67))
    m.text(frame,(14,448),f"火 {key[0]}/4　水 {key[1]}/4　風 {key[2]}/4　{f['tick']/20:.2f}s",16)
    m.text(frame,(14,479),'武器の枠0: 火属性 → 氷属性' if phase=='COMPLETE' else '武器の枠0: 火属性 / 他の枠・UUIDは保持',14)
    m.text(frame,(14,506),'仮供給量。総費用は同じ / 実モデル・共通計算の独立描画',11)
    return frame

scenes={s['scenario']:s for s in trace['scenes']};lo,hi=scenes['low'],scenes['high']
selected={0,75,105,150,165,225,366,387,402,414,474}
contacts=[]
for i,(a,b) in enumerate(zip(lo['frames'],hi['frames'])):
    frame=Image.new('RGB',(1200,560),'#191c20');frame.paste(compose(lo,a),(0,0));frame.paste(compose(hi,b),(600,0))
    m.text(frame,(15,537),'供給0でも継続 / 名称・生成方法・燃料・数値は未確定。ゲーム録画ではありません。武器は仮アイコン。',12)
    frame.save(TEMP/f'{i:03d}.png')
    if a['tick'] in selected:
        contacts.append((a['tick'],frame))
        preview_names={75:'Simultaneous',150:'Weapon',387:'Low-Weapon',402:'Success'}
        if a['tick'] in preview_names:frame.save(OUT/f'ProjectS-Infusion-Confluence-{preview_names[a["tick"]]}.png')
    if i%20==0:print('Rendered comparison tick',a['tick'],flush=True)
palette=Image.new('RGB',(2400,560))
for i,n in enumerate([30,52]):
    with Image.open(TEMP/f'{n:03d}.png') as im:palette.paste(im,(1200*i,0))
palette=palette.quantize(colors=256)
indexed=[]
for p in sorted(TEMP.glob('*.png')):
    with Image.open(p) as im:indexed.append(im.quantize(palette=palette,dither=Image.Dither.NONE))
gif=OUT/'ProjectS-Infusion-Confluence-Low-High.gif'
indexed[0].save(gif,save_all=True,append_images=indexed[1:],duration=150,loop=0,disposal=2,optimize=True)
assert gif.stat().st_size<10*1024*1024
# Present the three simultaneous intakes, high weapon channel, and low completion without waiting for GIF playback.
for label,tick in [('Simultaneous',75),('Weapon',150),('Low-Weapon',387),('Success',402)]:
    next(im for t,im in contacts if t==tick).save(OUT/f'ProjectS-Infusion-Confluence-{label}.png')
sheet=Image.new('RGB',(1200,1680),'#191c20')
for row,tick in enumerate([75,150,402]):sheet.paste(next(im for t,im in contacts if t==tick),(0,row*560))
sheet.save(OUT/'ProjectS-Infusion-Confluence-States.png')
focus=Image.new('RGB',(1800,640),'#191c20')
for i,(tick,col,label) in enumerate([(150,1,'HIGH 核から武器へ収束'),(156,1,'HIGH 武器の変性成立'),(387,0,'LOW 核から武器へ収束'),(402,0,'LOW 武器の変性成立')]):
    with Image.open(TEMP/f'{tick//3:03d}.png') as frame:
        focus.paste(frame.crop((col*600+175,85,col*600+400,355)).resize((450,540),Image.Resampling.NEAREST),(i*450,44))
    m.text(focus,(i*450+10,10),label,18)
    m.text(focus,(i*450+10,596),'同じ装備 / 固定1枠のみ。武器は仮表示アイコン。',12)
focus.save(OUT/'ProjectS-Infusion-Confluence-Weapon-Focus.png')
stops=Image.new('RGB',(1200,1060),'#191c20')
for col,name in enumerate(['shortage-high','cancel-high']):
    scene=scenes[name]
    for row,tick in enumerate([60,120]):stops.paste(compose(scene,next(f for f in scene['frames'] if f['tick']==tick)),(600*col,530*row))
stops.save(OUT/'ProjectS-Infusion-Confluence-Stops.png')
record={'traceSHA256':hashlib.sha256(TRACE.read_bytes()).hexdigest(),'frames':len(indexed),'durationMs':len(indexed)*150,
 'gifBytes':gif.stat().st_size,'nativeModels':'v5 engineered core via v7, v6 pedestals, v4 supports, v3 Jar; unchanged geometry/pixels',
 'sharedLogic':trace['kernels'],'supplementalEnergyDemoInput':True,'actualClientCapture':False,'weaponIconMarker':True,
 'projectionLightingLiquidTransparencyApproximate':True,'bloom':False,'worldColouredLighting':False,'newCurrenciesOrItems':False}
(OUT/'confluence-preview-verification.json').write_text(json.dumps(record,indent=2)+'\n',encoding='utf8');print(json.dumps(record,indent=2))
