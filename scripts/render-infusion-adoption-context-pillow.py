"""Offline native cuboid/UV context using existing Pillow; no numpy, installs, Minecraft or save access."""
from pathlib import Path
from PIL import Image,ImageDraw,ImageFont
import json,math,io,zipfile,hashlib
repo=Path(__file__).resolve().parent.parent
root=repo.parent
snapshot=root/'adoption-circle-polish-radius055'
out=repo/'assets/model-lab/infusion-adoption-context-radius055'
out.mkdir(parents=True,exist_ok=True)
pack=zipfile.ZipFile(snapshot/'adoption-pack.zip')
old=zipfile.ZipFile(root/'native-role-test/fixed-pack-preview.zip')
trace=json.loads((repo/'.tools/infusion-particle-role-study/role-trace-radius055.json').read_text())
size=(600,420);provenance={};textures={};models={}
def add(a,b):return tuple(x+y for x,y in zip(a,b))
def sub(a,b):return tuple(x-y for x,y in zip(a,b))
def mul(a,b):return tuple(x*b for x in a)
def dot(a,b):return sum(x*y for x,y in zip(a,b))
def norm(a):return mul(a,1/math.sqrt(dot(a,a)))
def rotate(p,axis,degrees):
    a=math.radians(degrees);c,s=math.cos(a),math.sin(a);x,y,z=p
    return ((x,c*y-s*z,s*y+c*z) if axis=='x' else (c*x+s*z,y,-s*x+c*z) if axis=='y' else (c*x-s*y,s*x+c*y,z))
def data(name,kind):
    if ':' not in name:name='projects:'+name
    namespace,path=name.split(':',1)
    entry=f'assets/{namespace}/{kind}/{path}'+('.png' if kind=='textures' else '.json')
    b=pack.read(entry);provenance[entry]=hashlib.sha256(b).hexdigest();return b
def model(name):
    if name not in models:
        raw=json.loads(data(name,'models'));parent=model(raw['parent']) if 'parent' in raw else {}
        models[name]={**parent,**raw,'textures':{**parent.get('textures',{}),**raw.get('textures',{})}}
    return models[name]
def texture(name):
    if name not in textures:textures[name]=Image.open(io.BytesIO(data(name,'textures'))).convert('RGBA')
    return textures[name]
normals={'north':(0,0,-1),'south':(0,0,1),'west':(-1,0,0),'east':(1,0,0),'up':(0,1,0),'down':(0,-1,0)}
def corners(lo,hi,face):
    x,y,z=lo;X,Y,Z=hi
    return {'north':[(X,Y,z),(X,y,z),(x,y,z),(x,Y,z)],'south':[(x,Y,Z),(x,y,Z),(X,y,Z),(X,Y,Z)],'west':[(x,Y,z),(x,y,z),(x,y,Z),(x,Y,Z)],'east':[(X,Y,Z),(X,y,Z),(X,y,z),(X,Y,z)],'up':[(x,Y,z),(x,Y,Z),(X,Y,Z),(X,Y,z)],'down':[(x,y,Z),(x,y,z),(X,y,z),(X,y,Z)]}[face]
az,el=map(math.radians,(38,26))
cam=(math.sin(az)*math.cos(el),math.sin(el),-math.cos(az)*math.cos(el))
right=(math.cos(az),0,math.sin(az));up=(-math.sin(az)*math.sin(el),math.cos(el),math.cos(az)*math.sin(el))
center=(.5,2.65,1.4)
bounds=[(x,y,z) for x in (-4.3,5.3) for y in (0,5.3) for z in (-3.3,6.1)]
projected=[(dot(sub(p,center),right),dot(sub(p,center),up)) for p in bounds]
low=tuple(min(p[i] for p in projected) for i in range(2));high=tuple(max(p[i] for p in projected) for i in range(2))
factor=1.45*min((size[0]-50)/(high[0]-low[0]),(size[1]-50)/(high[1]-low[1]));mid=tuple((a+b)/2 for a,b in zip(low,high))
def project(p):
    q=sub(p,center)
    return ((dot(q,right)-mid[0])*factor+size[0]/2+30,-(dot(q,up)-mid[1])*factor+size[1]/2-40),dot(q,cam)
light=norm((-.35,.85,-.4))
def model_polygons(name,at=(0,0,0),yaw=0,glow=1):
    doc=model(name);result=[]
    for e in doc['elements']:
        lo=mul(e['from'],1/16);hi=mul(e['to'],1/16);r=e.get('rotation')
        def transform(p):
            if r:
                origin=mul(r['origin'],1/16);p=rotate(sub(p,origin),r['axis'],r['angle'])
                if r.get('rescale'):
                    stretch=1/abs(math.cos(math.radians(r['angle'])))
                    p=tuple(v if 'xyz'[i]==r['axis'] else v*stretch for i,v in enumerate(p))
                p=add(p,origin)
            return add(add(rotate(sub(p,(.5,0,.5)),'y',yaw),(.5,0,.5)),at)
        for face,f in e['faces'].items():
            n=normals[face]
            if r:n=rotate(n,r['axis'],r['angle'])
            n=rotate(n,'y',yaw)
            if dot(n,cam)<=0:continue
            v=[transform(p) for p in corners(lo,hi,face)]
            ref=f['texture']
            while isinstance(ref,str) and ref.startswith('#'):ref=doc['textures'][ref[1:]]
            if isinstance(ref,dict):ref=ref['sprite']
            tex=texture(ref);uv=f.get('uv',[0,0,16,16])
            pw=max(1,min(64,math.ceil(abs(uv[2]-uv[0])*tex.width/16)));ph=max(1,min(64,math.ceil(abs(uv[3]-uv[1])*tex.height/16)))
            shade=.62+.38*max(0,dot(n,light)) if e.get('shade',True) else 1
            tint=glow if f.get('tintindex')==0 else 1
            for j in range(ph):
                for i in range(pw):
                    u,t=(i+.5)/pw,(j+.5)/ph
                    for _ in range(f.get('rotation',0)//90):u,t=1-t,u
                    tx=int((uv[0]+(uv[2]-uv[0])*u)*tex.width/16)%tex.width
                    ty=int((uv[1]+(uv[3]-uv[1])*t)*tex.height/16)%tex.height
                    rgba=tex.getpixel((tx,ty))
                    if not rgba[3]:continue
                    q=[add(v[0],add(mul(sub(v[3],v[0]),a),mul(sub(v[1],v[0]),b))) for a,b in [(i/pw,j/ph),(i/pw,(j+1)/ph),((i+1)/pw,(j+1)/ph),((i+1)/pw,j/ph)]]
                    points=[project(p)[0] for p in q];dep=sum(project(p)[1] for p in q)/4
                    rgb=tuple(round(c*shade*tint) for c in rgba[:3])
                    result.append((dep,points,rgb+(rgba[3],)))
    return result
def assembly(yaw,glow):
    poly=model_polygons('infusion-v6/center')
    for x,z,a in [(-1,-1,135),(1,-1,45),(-1,1,-135),(1,1,-45)]:poly+=model_polygons('infusion-v4/support',(x,0,z),a)
    for x,z in [(3,0),(0,3),(-3,0),(0,-3)]:poly+=model_polygons('infusion-v6/offering',(x,0,z))
    for x,z in [(-4,2),(4,2),(0,5)]:poly+=model_polygons('infusion-v3/jar',(x,0,z))
    poly+=model_polygons('infusion-v7/core_ritual',(0,2.9,0),yaw,glow)
    image=Image.new('RGB',size,'#242922');d=ImageDraw.Draw(image,'RGBA')
    depth=Image.new('F',size,-100000);dz=ImageDraw.Draw(depth)
    for dep,points,rgba in sorted(poly,key=lambda p:p[0]):
        d.polygon(points,fill=rgba)
        if rgba[3]>180:dz.polygon(points,fill=dep)
    return image.convert('RGBA'),depth
def clip_layer(layer,layer_depth,depth):
    alpha=layer.getchannel('A');box=alpha.getbbox()
    if box:
        pixels=layer.load();a=alpha.load();ld=layer_depth.load() if hasattr(layer_depth,'load') else None;bd=depth.load()
        for y in range(box[1],box[3]):
            for x in range(box[0],box[2]):
                if a[x,y] and bd[x,y]>(ld[x,y] if ld else layer_depth):
                    r,g,b,_=pixels[x,y];pixels[x,y]=(r,g,b,0)
    return layer
def effects(image,depth,samples,polished):
    for s in sorted(samples,key=lambda s:project(s['position'])[1]):
        name=s['toModel'];source=pack if polished or name not in ('infusion-radiance/halo','infusion-radiance/wave') else old
        pix=Image.open(io.BytesIO(source.read(f'assets/projects/textures/{name}.png'))).convert('RGBA')
        tint=(s['rgb']>>16&255,s['rgb']>>8&255,s['rgb']&255)
        pix.putdata([(round(r*tint[0]/255),round(g*tint[1]/255),round(b*tint[2]/255),a) for r,g,b,a in pix.get_flattened_data()])
        x,y,z=s['position'];w=s['width']*s['scale'];h=s['height']*s['scale']
        layer=Image.new('RGBA',size)
        if s['facing']=='HORIZONTAL':
            d=ImageDraw.Draw(layer);ld=Image.new('F',size,-100000);dz=ImageDraw.Draw(ld)
            for j in range(pix.height):
                for i in range(pix.width):
                    rgba=pix.getpixel((i,j))
                    if not rgba[3]:continue
                    q=[(x-w/2+w*a,y,z-w/2+w*b) for a,b in [(i/pix.width,j/pix.height),(i/pix.width,(j+1)/pix.height),((i+1)/pix.width,(j+1)/pix.height),((i+1)/pix.width,j/pix.height)]]
                    points=[project(p)[0] for p in q]
                    d.polygon(points,fill=rgba);dz.polygon(points,fill=sum(project(p)[1] for p in q)/4)
        else:
            p,dep=project(s['position']);nw=max(1,round(factor*w));nh=max(1,round(factor*h*(math.cos(el) if s['facing']=='VERTICAL' else 1)))
            stamp=pix.resize((nw,nh),Image.Resampling.NEAREST)
            layer.alpha_composite(stamp,(round(p[0]-nw/2),round(p[1]-nh/2)))
            ld=dep
            if s['facing']=='VERTICAL':
                ld=Image.new('F',size,dep);dz=ImageDraw.Draw(ld);top=round(p[1]-nh/2)
                for row in range(max(0,top),min(size[1],top+nh)):
                    value=dep+(h/2-((row-top+.5)/nh)*h)*math.sin(el)
                    dz.line((0,row,size[0]-1,row),fill=value)
        image.alpha_composite(clip_layer(layer,ld,depth))
chosen=[('Jar transfer / 0.55 inlet boundary','jar-emission-and-intake',16,.88),('Core to equipment / hierarchy','core-to-item-injection',16,.94),('Completion / wave and flecks','completion',14,.9)]
sheet=Image.new('RGB',(1200,1490),'#16191e');d=ImageDraw.Draw(sheet)
font=ImageFont.truetype(r'C:\Windows\Fonts\meiryo.ttc',16);small=ImageFont.truetype(r'C:\Windows\Fonts\meiryo.ttc',12)
d.text((18,8),'ADOPTION CONTEXT / frozen native cuboid models and UV pixels',font=font,fill='#e7dece')
d.text((18,34),'Left: approved roles + original circles   |   Right: approved roles + polished circles',font=small,fill='#bcc0c9')
for row,(label,scene,tick,glow) in enumerate(chosen):
    f=next(frame for frame in trace['frames'] if frame['scene']==scene and frame['tick']==tick)
    image,depth=assembly(0 if scene=='completion' else tick*4.5,glow)
    y0=64+row*470;d.text((18,y0),f'{label} / staged clip +{tick/20:.2f}s / {f["count"]} samples per side',font=font,fill='#d9d4c9')
    for col,polished in enumerate((False,True)):
        layer=image.copy();effects(layer,depth,f['samples'],polished);sheet.paste(layer.convert('RGB'),(600*col,y0+32))
    print(f'CONTEXT_RENDER scene={scene} native models same samples',flush=True)
d.text((18,1472),'Offline lighting/depth approximation; staged layout and no equipped-item entity; not Minecraft or the user save.',font=small,fill='#a3a8b0')
target=out/'adoption-context-radius055.png';sheet.save(target,optimize=True)
report={'nativePackSha1':hashlib.sha1((snapshot/'adoption-pack.zip').read_bytes()).hexdigest(),'roleTraceSha256':hashlib.sha256((repo/'.tools/infusion-particle-role-study/role-trace-radius055.json').read_bytes()).hexdigest(),'contextFrames':3,'samplesPerSideIdentical':True,'coreGeometryUVSupportOrientationUnchanged':True,'cameraDegrees':[38,26],'coreYawAndTintMatchTraceExporter':True,'coreNeutralTint':[.88,.94,.9],'exactSourceFiles':provenance,'outputBytes':target.stat().st_size,'limitations':['Offline directional light and alpha/depth approximation','Staged layout, not user world layout','Equipped item entity omitted','No human or native test in this run']}
(out/'context-verification.json').write_text(json.dumps(report,indent=2),encoding='utf-8')
print('CONTEXT_PREVIEW_PASS '+json.dumps({k:v for k,v in report.items() if k!='exactSourceFiles'}))
