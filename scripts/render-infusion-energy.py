"""Native models/pixels and Kotlin accepted transactions; supplemental supply is demo input.
Independent projection/lighting/transparency, not a client recording. No new art or Bloom.
"""
from pathlib import Path
import json,math,hashlib,importlib.util,io,argparse
import numpy as np
from PIL import Image,ImageDraw,ImageSequence
spec=importlib.util.spec_from_file_location('models',Path(__file__).with_name('render-world-models.py'))
m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)
TRACE=m.ROOT/'server-minestom/.tools/world-infusion-evidence/energy-animation-trace.json'
trace=json.loads(TRACE.read_text());OUT=m.OUT;SIZE=(600,420)
TEMP=OUT/'energy-frames';TEMP.mkdir(exist_ok=True)
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
    if f['pedestalGlow']>.02:
        seal,sd,_,_=m.render(get('infusion-energy/pedestal_seal'),SIZE,38,26,with_depth=True,frame_bounds=frame_bounds,item_tint=(f['pedestalGlow'],)*3)
        a=np.array(seal);b=np.array(canvas);changed=np.any(a!=np.array([36,41,34]),axis=2)
        mask=changed & (sd>depth);b[mask,:3]=a[mask];canvas=Image.fromarray(b);depth=np.maximum(depth,sd)
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
    if scene['scenario']=='cancel' and f['tick']>=scene['stoppedTick']:label='取消し / 成功の紋様・変性なし'
    elif f['paused']:label='元素不足 / 新規注入とE投入を停止'
    elif phase in ['ESSENTIA','INGREDIENTS'] and f['power']==0:label='エネルギー待ち / 投入済み量を保持'
    elif phase=='READY':label='中央装備・通常素材・設置Jarで起動'
    elif f['channel'] is not None:label='核 → 武器へ定着 / 必要Eを投入'
    elif phase=='ESSENTIA':label='全元素を同時に注入'
    elif phase=='INGREDIENTS':label='最後の霧の到達待ち' if f['samples'] else '周囲素材を吸収'
    else:label='成立 / 中央台の紋様 → 武器へ立ち上る光'
    frame=Image.new('RGB',(480,530),'#191c20')
    name={'zero':'ZERO','low':'LOW','high':'HIGH','interrupt':'中断・再開','cancel':'取消し','shortage':'元素不足'}[scene['scenario']]
    m.text(frame,(12,8),f"{name}   P={f['power']} E/s",20)
    m.text(frame,(12,39),label,14)
    energy=f"投入 {f['received']:.1f} / {f['required']:.0f} E　残り {f['remaining']:.1f} E"
    m.text(frame,(12,64),energy,14)
    d=ImageDraw.Draw(frame);d.rectangle((13,89,466,97),fill='#37333c');d.rectangle((13,89,13+round(453*f['received']/120),97),fill='#9363b8')
    frame.paste(canvas.convert('RGB').crop((104,120,523,380)).resize((480,296),Image.Resampling.NEAREST),(0,106))
    m.text(frame,(12,415),f"火 {key[0]}/4　水 {key[1]}/4　風 {key[2]}/4　"+("待機" if scene['scenario']=='zero' else f"{f['tick']/20:.2f}s"),14)
    m.text(frame,(12,445),'武器 枠0: 火 → 氷 / UUIDと他枠は保持' if phase=='COMPLETE' else '武器 枠0: 火属性 / 収束が終わってから変性',13)
    m.text(frame,(12,475),'E・Pは仮単位/仮数値。生成方法は未接続。',12)
    m.text(frame,(12,501),'実モデル/共通台帳の独立描画。武器は仮アイコン。',11)
    return frame

scenes={s['scenario']:s for s in trace['scenes']};zero,lo,hi=[scenes[n] for n in ['zero','low','high']]
first=min(e['tick'] for e in lo['events'] if e['kind']=='jar');first=((first+6+2)//3)*3
high_channel=(hi['completedTick']-6)//3*3;high_success=(hi['completedTick']+12)//3*3
low_channel=(lo['completedTick']-6)//3*3;low_success=(lo['completedTick']+12)//3*3
selected={first:'Simultaneous',high_channel:'High-Channel',high_success:'High-Success',low_channel:'Low-Channel',low_success:'Low-Success'}
contacts={};zero_cache={}
reuse=argparse.ArgumentParser();reuse.add_argument('--reuse-frames',action='store_true');reuse=reuse.parse_args().reuse_frames
trace_stamp=TEMP/'trace.sha256';digest=hashlib.sha256(TRACE.read_bytes()).hexdigest()
if reuse:assert trace_stamp.read_text().strip()==digest
else:trace_stamp.write_text(digest+'\n',encoding='utf8')
for i,fs in enumerate(zip(zero['frames'],lo['frames'],hi['frames'])):
    if reuse:
        with Image.open(TEMP/f'{i:03d}.png') as existing:
            if fs[0]['tick'] in selected:contacts[selected[fs[0]['tick']]]=existing.copy()
        continue
    frame=Image.new('RGB',(1440,560),'#191c20')
    for col,(scene,f) in enumerate(zip([zero,lo,hi],fs)):
        if col==0:
            k=f['phase']
            if k not in zero_cache:zero_cache[k]=compose(scene,f)
            cell=zero_cache[k]
        else:cell=compose(scene,f)
        frame.paste(cell,(480*col,0))
    m.text(frame,(12,537),'必要総E=120で共通。供給0は待機 / 数値は仮 / 生成方法未接続 / ゲーム録画ではありません。',12)
    m.text(frame,(1290,537),f"t={fs[0]['tick']/20:.2f}s",12)
    frame.save(TEMP/f'{i:03d}.png')
    if fs[0]['tick'] in selected:
        label=selected[fs[0]['tick']];contacts[label]=frame;frame.save(OUT/f'ProjectS-Infusion-Energy-{label}.png')
    if i%20==0:print('Rendered E/P comparison tick',fs[0]['tick'],flush=True)
palette=Image.new('RGB',(2880,560))
for col,tick in enumerate([first,high_success]):
    with Image.open(TEMP/f'{tick//3:03d}.png') as im:palette.paste(im,(1440*col,0))
palette=palette.quantize(colors=256);indexed=[]
for p in sorted(TEMP.glob('*.png')):
    with Image.open(p) as im:indexed.append(im.resize((1200,467),Image.Resampling.NEAREST).quantize(palette=palette,dither=Image.Dither.NONE))
gif=OUT/'ProjectS-Infusion-Energy-Zero-Low-High.gif'
indexed[0].save(gif,save_all=True,append_images=indexed[1:],duration=150,loop=0,disposal=2,optimize=True)
assert gif.stat().st_size<10*1024*1024
sheet=Image.new('RGB',(1440,1680),'#191c20')
for row,label in enumerate(['Simultaneous','High-Success','Low-Success']):sheet.paste(contacts[label],(0,row*560))
sheet.save(OUT/'ProjectS-Infusion-Energy-States.png')
focus=Image.new('RGB',(1600,660),'#191c20')
for i,(tick,col,label) in enumerate([(high_channel,2,'HIGH 核から武器へ'),(high_success,2,'HIGH 台座から立ち上る光'),(low_channel,1,'LOW 核から武器へ'),(low_success,1,'LOW 台座から立ち上る光')]):
    with Image.open(TEMP/f'{tick//3:03d}.png') as im:
        focus.paste(im.crop((col*480+130,110,col*480+330,380)).resize((400,540),Image.Resampling.NEAREST),(i*400,48))
    m.text(focus,(i*400+9,8),label,17);m.text(focus,(i*400+9,610),'紋様の追加演出のみ / 元の台座の絵は保持',12)
focus.save(OUT/'ProjectS-Infusion-Energy-Pedestal-Focus.png')
# Reproducible early-detail card: crop actual complete frame and show the exact authored sprite.
with Image.open(OUT/'ProjectS-Infusion-Energy-High-Success.png') as src:
    detail=src.crop((2*480+130,110,2*480+330,380)).resize((600,810),Image.Resampling.NEAREST)
card=Image.new('RGB',(820,920),'#191c20');card.paste(detail,(0,55))
m.text(card,(16,12),'成功時のみ：中央台座の紋様 → 武器へ立ち上る光',20)
with Image.open(m.PACK/'assets/projects/textures/infusion-energy/pedestal_seal.png') as glyph:
    glyph=glyph.resize((192,192),Image.Resampling.NEAREST);card.paste(glyph,(610,135),glyph)
m.text(card,(615,100),'実16px sprite',14)
m.text(card,(16,880),'専用の一時表示。承認された台座本体・原本の絵は不変。ゲーム録画ではありません。',14)
card.save(OUT/'ProjectS-Infusion-Energy-Pedestal-Early-Detail.png')

stops=Image.new('RGB',(1440,1060),'#191c20')
for col,name in enumerate(['interrupt','cancel','shortage']):
    scene=scenes[name]
    for row,tick in enumerate([174,300] if name=='cancel' else [90,300]):stops.paste(compose(scene,next(f for f in scene['frames'] if f['tick']==tick)),(480*col,530*row))
stops.save(OUT/'ProjectS-Infusion-Energy-Interrupt-Cancel.png')
record={'traceSHA256':hashlib.sha256(TRACE.read_bytes()).hexdigest(),'frames':len(indexed),'durationMs':len(indexed)*150,
 'gifBytes':gif.stat().st_size,'modelsUnchanged':True,'newSuccessOnlyNativeSeal':True,'requiredE':120,'PComparison':[0,6,18],
 'fixedTotalCost':True,'demoUnitsRatesGenerationUnconnected':True,'sharedLogic':trace['kernels'],
 'actualClientCapture':False,'weaponIconMarker':True,'lightingLiquidTransparencyApproximate':True,'bloom':False,'worldColouredLighting':False}
(OUT/'energy-preview-verification.json').write_text(json.dumps(record,indent=2)+'\n',encoding='utf8');print(json.dumps(record,indent=2))
