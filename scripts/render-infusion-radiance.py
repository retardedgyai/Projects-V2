"""Exact native model/UV pixels with shared Kotlin receipts/particles. Independent camera/light, not a client capture."""
from pathlib import Path
import json,math,hashlib,importlib.util,io
import numpy as np
from PIL import Image,ImageDraw

spec=importlib.util.spec_from_file_location('models',Path(__file__).with_name('render-world-models.py'))
m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)
OUT=m.OUT;SIZE=(600,420);ROOT=m.ROOT
TRACE=ROOT/'server-minestom/.tools/world-infusion-evidence/radiance-animation-trace.json'
trace=json.loads(TRACE.read_text());previous=json.loads((TRACE.parent/'charge-animation-trace.json').read_text())
def get(name,at=(0,0,0),yaw=0):return m.boxes('projects:'+name,at,centered=False,yaw=yaw)
def liquid(at,amount,rgb):
    if not amount:return []
    ref='liquid:'+str(rgb);m.textures[ref]=np.full((16,16,4),[(rgb>>16)&255,(rgb>>8)&255,rgb&255,180],dtype='uint8')
    lo=np.array([.32,.13,.32]);hi=np.array([.68,.13+.5*amount/4,.68])
    return [(lo,hi,{f:{'texture':ref,'uv':[0,0,16,16],'_light_emission':12,'_shade':True} for f in m.normals},{},np.eye(3),np.array(at))]
locations=[(-4,2),(4,2),(0,5)];colours=[0xee9850,0x84b6bd,0xc6d6a0];pedestals=[(3,0),(0,3),(-3,0),(0,-3)]
def baseparts(amounts):
    p=get('infusion-v6/center')
    for x,z,yaw in [(-1,-1,135),(1,-1,45),(-1,1,-135),(1,1,-45)]:p+=get('infusion-v4/support',(x,0,z),yaw)
    for x,z in pedestals:p+=get('infusion-v6/offering',(x,0,z))
    for (x,z),n,rgb in zip(locations,amounts,colours):p+=get('infusion-v3/jar',(x,0,z))+liquid((x,0,z),n,rgb)
    return p
coreparts=get('infusion-v7/core_ritual',(0,2.9,0));parts=baseparts([4,4,4])+coreparts
pts=np.concatenate([np.array([[x,y,z] for x in [lo[0],hi[0]] for y in [lo[1],hi[1]] for z in [lo[2],hi[2]]])@r.T+t for lo,hi,_,_,r,t in parts])
bounds=pts.min(0),pts.max(0);cache={}
cloud=np.array(Image.open(m.PACK/'assets/projects/textures/infusion/smoke.png').convert('RGBA'))
props={r:Image.open(m.PACK/f'assets/projects/textures/item/forge_materials/{n}.png').convert('RGBA') for r,n in [('INGOT','ingot'),('CLOTH','cloth'),('STONE_BLOCK','cut_stone')]}
weapon=Image.open(io.BytesIO(m.NATIVE.read('assets/minecraft/textures/item/iron_sword.png'))).convert('RGBA')

# Expand UV pixels once. Per frame only rotate/project/sort the exact native face quads.
quads=[];normals=[];col=[];tints=[];shades=[]
for lo,hi,faces,refs,matrix,offset in coreparts:
    for face,f in faces.items():
        n=np.linalg.inv(matrix).T@np.array(m.normals[face]);n/=np.linalg.norm(n)
        v=m.corners(lo,hi,face)@matrix.T+offset;uv=f.get('uv',[0,0,16,16]);name=f['texture']
        while isinstance(name,str) and name.startswith('#'):name=refs[name[1:]]
        if isinstance(name,dict):name=name['sprite']
        tex=m.texture(name)
        for j in range(16):
            for i in range(16):
                tx=int((uv[0]+(uv[2]-uv[0])*(i/16+.03125))*tex.shape[1]/16)%tex.shape[1]
                ty=int((uv[1]+(uv[3]-uv[1])*(j/16+.03125))*tex.shape[0]/16)%tex.shape[0]
                rgba=tex[ty,tx]
                if not rgba[3]:continue
                q=np.array([v[0]+(v[3]-v[0])*u+(v[1]-v[0])*t for u,t in [(i/16,j/16),(i/16,(j+1)/16),((i+1)/16,(j+1)/16),((i+1)/16,j/16)]])
                quads.append(q);normals.append(n);col.append(rgba);tints.append(f.get('tintindex')==0);shades.append(f.get('_shade',True))
Q=np.array(quads);N=np.array(normals);C=np.array(col);T=np.array(tints);S=np.array(shades)
light=np.array([-.35,.85,-.4]);light/=np.linalg.norm(light)
ay,el=map(math.radians,[38,26]);cam=np.array([math.sin(ay)*math.cos(el),math.sin(el),-math.cos(ay)*math.cos(el)])
def fastcore(yaw,glow,project):
    angle=math.radians(yaw);r=np.array([[math.cos(angle),0,math.sin(angle)],[0,1,0],[-math.sin(angle),0,math.cos(angle)]])
    pivot=np.array([.5,2.9,.5]);q=(Q-pivot)@r.T+pivot;n=N@r.T;visible=n@cam>0
    origin,origin_d=project((0,0,0));basis=np.array([project(e)[0]-origin for e in np.eye(3)])
    xy=q@basis+origin;depth=q.mean(1)@cam+origin_d
    rgb=C[:,:3].copy();rgb[T]=(rgb[T]*glow).astype('uint8')
    shade=np.where(S,.62+.38*np.maximum(0,n@light),1);rgb=(rgb*shade[:,None]).astype('uint8')
    im=Image.new('RGB',SIZE,'#242922');d=ImageDraw.Draw(im,'RGBA');z=Image.new('F',SIZE,-100000);zd=ImageDraw.Draw(z)
    for i in np.flatnonzero(visible)[np.argsort(depth[visible],kind='stable')]:
        points=[tuple(p) for p in xy[i]];d.polygon(points,fill=tuple(map(int,rgb[i]))+(int(C[i,3]),))
        if C[i,3]>180:zd.polygon(points,fill=float(depth[i]))
    return im,np.array(z)

def stamp(canvas,depth,project,factor,pos,image,width):
    p,d=project(pos);n=max(2,round(factor*width));im=image.resize((n,n),Image.Resampling.NEAREST)
    left,top=round(p[0]-n/2),round(p[1]-n/2);x0,y0=max(0,left),max(0,top);x1,y1=min(SIZE[0],left+n),min(SIZE[1],top+n)
    if x0>=x1 or y0>=y1:return
    a=np.array(im.crop((x0-left,y0-top,x1-left,y1-top)));a[:,:,3]=np.where(depth[y0:y1,x0:x1]>d,0,a[:,:,3]);canvas.alpha_composite(Image.fromarray(a),(x0,y0))

def canvas(scene,f):
    key=tuple(f['amounts'])
    if key not in cache:cache[key]=m.render(baseparts(key),SIZE,38,26,with_depth=True,frame_bounds=bounds)
    base,depth,project,factor=cache[key];image=base.copy().convert('RGBA')
    core,cd=fastcore(f['yaw'],f['glow'],project);a=np.array(core);b=np.array(image)
    mask=np.any(a!=np.array([36,41,34]),axis=2)&(cd>depth);b[mask,:3]=a[mask];image=Image.fromarray(b);depth=np.maximum(depth,cd)
    if f['pedestalGlow']>.02:
        seal,sd,_,_=m.render(get('infusion-energy/pedestal_seal'),SIZE,38,26,with_depth=True,frame_bounds=bounds,item_tint=(f['pedestalGlow'],)*3)
        a=np.array(seal);b=np.array(image);mask=np.any(a!=np.array([36,41,34]),axis=2)&(sd>depth);b[mask,:3]=a[mask];image=Image.fromarray(b);depth=np.maximum(depth,sd)
    for (x,z),resource in zip(pedestals,f['items']):
        if resource:stamp(image,depth,project,factor,(x+.5,1.12,z+.5),props[resource],.55)
    stamp(image,depth,project,factor,(.5,1.15,.5),weapon,.55)
    for sample in sorted(f['samples'],key=lambda s:project(s[:3])[1]):
        draw_effect(image,depth,project,factor,sample)
    for e in scene['events']:
        age=f['tick']-e['tick']
        if e['kind']!='ingredient' or not 0<=age<=18:continue
        source=np.array([e['x']+.5,1.15,e['z']+.5]);angle=math.radians(f['yaw']);x=-.1875*math.sin(math.pi/8);z=-.125
        target=np.array([.5+x*math.cos(angle)+z*math.sin(angle),3.4+.1875*math.cos(math.pi/8),.5-x*math.sin(angle)+z*math.cos(angle)])
        for i in range(5):
            u=max(0,min(1,age/18-i*.045));p,d=project(source+(target-source)*u+np.array([0,math.sin(u*math.pi)*.65,0]));ix,iy=map(round,p)
            if 0<=ix<SIZE[0] and 0<=iy<SIZE[1] and depth[iy,ix]<=d:ImageDraw.Draw(image).rectangle((ix-1,iy-1,ix+1,iy+1),fill='#e1cfab')
    return image

def card(scene,f,title):
    im=Image.new('RGB',(600,580),'#191c20');m.text(im,(14,8),title,20)
    charge=f.get('chargeFraction');label=f"核充填 {charge*100:.0f}% / {round(charge*7)}/7 Essentia" if charge is not None else '前版：小さな収束光'
    m.text(im,(14,40),label,16)
    m.text(im,(14,68),f"P={f['power']}E/s  投入{f['received']:.1f}/120E",15)
    m.text(im,(488,70),f"{f['tick']/20:.2f}s",14)
    im.paste(canvas(scene,f).convert('RGB'),(0,102))
    status='供給停止：蓄積も注入も進まない' if f['power']==0 else '核 → 中央アイテムへ注入' if f['channel'] is not None else '成功：中央台座が短く応答' if f['phase']=='COMPLETE' else 'Jar → 核へ蓄積'
    if f['phase']=='READY':status='起動前 / 取消後：注入しない'
    m.text(im,(14,527),status,15);m.text(im,(14,555),'共通Kotlin + 実モデル。独立描画 / 実client撮影ではありません',12)
    return im


sprite_cache={}
def draw_effect(image,depth,project,factor,s):
    x,y,z,rgb,scale,u=s[:6]
    model,w,h,facing=(s[6:10] if len(s)>6 else ('infusion/smoke',.26,.26,'CENTER'))
    if model not in sprite_cache:sprite_cache[model]=np.array(Image.open(m.PACK/('assets/projects/textures/'+model+'.png')).convert('RGBA'))
    pixels=sprite_cache[model].copy();tint=np.array([(rgb>>16)&255,(rgb>>8)&255,rgb&255])/255;pixels[:,:,:3]=(pixels[:,:,:3]*tint).astype('uint8')
    w*=scale;h*=scale
    if facing=='HORIZONTAL':
        # Exact native up-facing quad: world X/Z, 16px texture, item model centered at sample position.
        layer=Image.new('RGBA',SIZE);d=ImageDraw.Draw(layer);zd=Image.new('F',SIZE,-100000);dz=ImageDraw.Draw(zd)
        for j in range(16):
            for i in range(16):
                rgba=pixels[j,i]
                if not rgba[3]:continue
                quad=[(x-w/2+w*a,y,z-w/2+w*b) for a,b in [(i/16,j/16),(i/16,(j+1)/16),((i+1)/16,(j+1)/16),((i+1)/16,j/16)]]
                q=[tuple(project(p)[0]) for p in quad];value=project(np.mean(quad,axis=0))[1]
                d.polygon(q,fill=tuple(map(int,rgba)));dz.polygon(q,fill=value)
        a=np.array(layer);a[:,:,3]=np.where(depth>np.array(zd),0,a[:,:,3]);image.alpha_composite(Image.fromarray(a));return
    p,dep=project((x,y,z));nw=max(2,round(factor*w));nh=max(2,round(factor*h*(math.cos(math.radians(26)) if facing=='VERTICAL' else 1)))
    im=Image.fromarray(pixels).resize((nw,nh),Image.Resampling.NEAREST)
    left,top=round(p[0]-nw/2),round(p[1]-nh/2);x0,y0=max(0,left),max(0,top);x1,y1=min(SIZE[0],left+nw),min(SIZE[1],top+nh)
    if x0>=x1 or y0>=y1:return
    a=np.array(im.crop((x0-left,y0-top,x1-left,y1-top)))
    if facing=='VERTICAL':
        heights=h/2-((np.arange(y0,y1)-top+.5)/nh)*h;dep=dep+heights[:,None]*math.sin(math.radians(26))
    a[:,:,3]=np.where(depth[y0:y1,x0:x1]>dep,0,a[:,:,3]);image.alpha_composite(Image.fromarray(a),(x0,y0))

scenes={s['scenario']:s for s in trace['scenes']};oldscenes={s['scenario']:s for s in previous['scenes']}
high=scenes['high'];oldhigh=oldscenes['high'];oldframes={f['tick']:f for f in oldhigh['frames']}
stages=[('0',next(f for f in high['frames'] if f['phase']=='ESSENTIA' and f['chargeFraction']==0)),
        ('中',next(f for f in high['frames'] if f['chargeFraction']==3/7)),
        ('満',next(f for f in high['frames'] if f['chargeFraction']==1 and f['phase']=='INGREDIENTS' and f['channel'] is None)),
        ('注入',next(f for f in high['frames'] if f['channel'] is not None and f['channel']>=.55))]
success=next(f for f in high['frames'] if f['tick']==high['completedTick']+6)
def stageframe(f):
    frozen=dict(f);frozen['yaw']=0;particles=[list(s) for s in f['samples'][:f['radianceSprites']]];a=math.radians(-f['yaw'])
    for p in particles:
        if p[6]!='infusion/smoke' or p[1]<3.1:continue
        x,z=p[0]-.5,p[2]-.5;p[0]=.5+x*math.cos(a)-z*math.sin(a);p[2]=.5+x*math.sin(a)+z*math.cos(a)
    frozen['samples']=particles;return frozen
_,_,project,_=m.render(baseparts([4,4,4]),SIZE,38,26,with_depth=True,frame_bounds=bounds)
native=m.render(get('infusion-v7/core_ritual',(0,2.9,0),47.1),SIZE,38,26,frame_bounds=bounds,item_tint=(.63,)*3)
fast,_=fastcore(47.1,.63,project);equal=float(np.mean(np.all(np.array(native)==np.array(fast),axis=2)));assert equal>.99
stage_sheet=Image.new('RGB',(1200,1160),'#191c20')
for i,(name,f) in enumerate(stages):stage_sheet.paste(card(high,stageframe(f),name+' / 同一向き・実充填に連動'),((i%2)*600,(i//2)*580))
stage_sheet.save(OUT/'ProjectS-Infusion-Radiance-Stages.png');print('Saved bold four-stage PNG',flush=True)
phone=Image.new('RGB',(360,388),'#191c20')
for i,(name,f) in enumerate([stages[0],stages[1],stages[3],('完成',success)]):
    at=((i%2)*180,(i//2)*190)
    view=canvas(high,stageframe(f)).convert('RGB').crop((110,45,530,410)).resize((180,156),Image.Resampling.NEAREST)
    phone.paste(view,(at[0],at[1]+28));m.text(phone,(at[0]+5,at[1]+5),name+' / '+{'0':'未充填','中':'吸収中','注入':'核 → 装備','完成':'台座から波'}[name],14)
m.text(phone,(5,377),'360px縮小 / native sprite・実model / 独立描画',10)
phone.save(OUT/'ProjectS-Infusion-Radiance-Phone.png')
comparison=Image.new('RGB',(1200,580),'#191c20');comparison.paste(card(oldhigh,oldframes[success['tick']],'前版 / 完成'),(0,0));comparison.paste(card(high,success,'今回 / 台座から光の波とflash'),(600,0))
comparison.save(OUT/'ProjectS-Infusion-Radiance-Success.png')
stops=Image.new('RGB',(1800,1160),'#191c20')
for col,(name,ticks) in enumerate([('zero',[90,240]),('interrupt',[90,300]),('cancel',[174,300])]):
    scene=scenes[name]
    for row,tick in enumerate(ticks):stops.paste(card(scene,next(f for f in scene['frames'] if f['tick']==tick),name+' / 停止・再開・取消'),(600*col,580*row))
stops.save(OUT/'ProjectS-Infusion-Radiance-Stops.png')
frames=[]
for i,f in enumerate(high['frames']):
    if f['tick']>300:break
    frame=Image.new('RGB',(1200,580),'#191c20');frame.paste(card(oldhigh,oldframes[f['tick']],'前版 / 小さな光の節'),(0,0));frame.paste(card(high,f,'今回 / 紫の蓄積・集束光・完成波'),(600,0));frames.append(frame)
    if i%20==0:print('Rendered bold comparison tick',f['tick'],flush=True)
palette=Image.new('RGB',(4800,580))
for i,index in enumerate([0,17,35,62]):palette.paste(frames[index],(1200*i,0))
palette=palette.quantize(colors=256);indexed=[f.quantize(palette=palette,dither=Image.Dither.NONE) for f in frames]
gif=OUT/'ProjectS-Infusion-Radiance-Comparison.gif';indexed[0].save(gif,save_all=True,append_images=indexed[1:],duration=150,loop=0,disposal=2,optimize=True)
assert gif.stat().st_size<10*1024*1024
counts=[]
for _,f in stages:
    im=canvas(high,stageframe(f)).convert('RGB').crop((110,45,530,410)).resize((180,156),Image.Resampling.NEAREST);a=np.array(im).astype(float)
    counts.append(int(np.count_nonzero((a[:,:,2]>90)&(a[:,:,2]>1.25*a[:,:,1])&(a[:,:,0]>1.25*a[:,:,1]))))
record={'traceSHA256':hashlib.sha256(TRACE.read_bytes()).hexdigest(),'frames':len(indexed),'durationMs':len(indexed)*150,'gifBytes':gif.stat().st_size,
        'chargeFromAcceptedEssentia':True,'nativeUVRendererEqualPixelRatio':equal,'stageGalleryFixedYaw':0,'phoneWidth':360,'phoneScenePurplePixels':counts,
        'nativeModelsUnchanged':True,'nativeSpritesAdded':4,'sharedKernels':trace['kernels'],'clientCapture':False,'weaponIconMarker':True,
        'approximateLightingAndTransparency':True,'cpuBloomAdded':False,'bloom':False,'worldColouredLighting':False}
(OUT/'radiance-preview-verification.json').write_text(json.dumps(record,indent=2)+'\n',encoding='utf8');print(json.dumps(record,indent=2))
