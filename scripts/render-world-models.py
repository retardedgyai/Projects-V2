"""Offline orthographic render of the exact committed Minecraft cuboid models and PNG pixels.
No image generation, UI reconstruction, or new model design. Native blocks use cached 26.2 assets.
"""
from pathlib import Path
import json, zipfile, math, hashlib, os
import numpy as np
from PIL import Image, ImageDraw, ImageFont, ImageFilter

_local=Path(__file__).resolve().parent.parent
ROOT=Path(os.environ.get('PROJECTS_MODEL_REPO',str(_local if (_local/'server-minestom').is_dir() else Path(__file__).parent/'world-infusion-altar')))
PACK=ROOT/'server-minestom/src/main/resources/core-ui-pack'
OUT=Path(os.environ.get('PROJECTS_MODEL_OUT',str(ROOT/'.tools/world-model-preview' if _local==ROOT else Path(__file__).parent/'deliverables-world')))
NATIVE=zipfile.ZipFile(os.environ.get('MINECRAFT_CLIENT_JAR',r'C:\Users\xgaiz\.gradle\caches\fabric-loom\26.2\minecraft-client.jar'))
textures={}; provenance={}
def data(name,kind):
    if ':' not in name: name='minecraft:'+name
    ns,p=name.split(':',1); f=f'assets/{ns}/{kind}/{p}'+('.png' if kind=='textures' else '.json')
    b=(PACK/f).read_bytes() if ns=='projects' else NATIVE.read(f)
    provenance[f]=hashlib.sha256(b).hexdigest();return b
def model(name):
    raw=json.loads(data(name,'models')); parent=model(raw['parent']) if 'parent' in raw else {}
    return {**parent,**raw,'textures':{**parent.get('textures',{}),**raw.get('textures',{})}}
def texture(name):
    if name not in textures:
        import io
        im=Image.open(io.BytesIO(data(name,'textures'))).convert('RGBA')
        textures[name]=np.array(im.crop((0,0,im.width,im.width)))
    return textures[name]
def boxes(name,at=(0,0,0),scale=(1,1,1),centered=True,yaw=0):
    m=model(name);result=[]
    for e in m['elements']:
        lo=np.array(e['from'],float)/16;hi=np.array(e['to'],float)/16
        # Minecraft element rotations, rescale and display yaw applied to actual face vertices.
        matrix=np.eye(3);origin=np.zeros(3)
        if 'rotation' in e:
            r=e['rotation'];angle=math.radians(r['angle']);c,s=math.cos(angle),math.sin(angle)
            axis={'x':0,'y':1,'z':2}[r['axis']];origin=np.array(r['origin'],float)/16
            matrix=[np.array([[1,0,0],[0,c,-s],[0,s,c]]),np.array([[c,0,s],[0,1,0],[-s,0,c]]),np.array([[c,-s,0],[s,c,0],[0,0,1]])][axis]
            if r.get('rescale'):
                stretch=np.eye(3);stretch[[i for i in range(3) if i!=axis],[i for i in range(3) if i!=axis]]=1/abs(c)
                matrix=matrix@stretch
        a=math.radians(yaw);display=np.array([[math.cos(a),0,math.sin(a)],[0,1,0],[-math.sin(a),0,math.cos(a)]])
        transform=display@np.diag(scale)@matrix
        pivot=np.zeros(3) if centered else np.array([.5,0,.5])*np.array(scale)
        offset=np.array(at)+pivot-display@pivot+display@(np.array(scale)*(origin-matrix@origin-(.5 if centered else 0)))
        result.append((lo,hi,e['faces'],m['textures'],transform,offset))
    return result

normals={'north':(0,0,-1),'south':(0,0,1),'west':(-1,0,0),'east':(1,0,0),'up':(0,1,0),'down':(0,-1,0)}
def corners(lo,hi,face):
    x,y,z=lo;X,Y,Z=hi
    return np.array({'north':[(X,Y,z),(X,y,z),(x,y,z),(x,Y,z)],'south':[(x,Y,Z),(x,y,Z),(X,y,Z),(X,Y,Z)],
      'west':[(x,Y,z),(x,y,z),(x,y,Z),(x,Y,Z)],'east':[(X,Y,Z),(X,y,Z),(X,y,z),(X,Y,z)],
      'up':[(x,Y,z),(x,Y,Z),(X,Y,Z),(X,Y,z)],'down':[(x,y,Z),(x,y,z),(X,y,z),(X,y,Z)]}[face])
def render(parts,size,yaw,elevation,with_depth=False):
    a,e=math.radians(yaw),math.radians(elevation)
    cam=np.array([math.sin(a)*math.cos(e),math.sin(e),-math.cos(a)*math.cos(e)])
    right=np.array([math.cos(a),0,math.sin(a)]);up=np.cross(right,cam)
    def unpack(p):
        return (*p,np.eye(3),np.zeros(3)) if len(p)==4 else p
    parts=[unpack(p) for p in parts]
    allpts=np.concatenate([np.array([[x,y,z] for x in [lo[0],hi[0]] for y in [lo[1],hi[1]] for z in [lo[2],hi[2]]])@matrix.T+offset for lo,hi,_,_,matrix,offset in parts]);center=(allpts.min(0)+allpts.max(0))*.5
    poly=[];light=np.array([-.35,.85,-.4]);light/=np.linalg.norm(light)
    for lo,hi,faces,refs,matrix,offset in parts:
        for face,f in faces.items():
            n=np.linalg.inv(matrix).T@np.array(normals[face]);n=n/np.linalg.norm(n)
            if np.dot(n,cam)<=0:continue
            v=corners(lo,hi,face)@matrix.T+offset;uv=f.get('uv',[0,0,16,16]);name=f['texture']
            while isinstance(name,str) and name.startswith('#'):name=refs[name[1:]]
            if isinstance(name,dict): name=name['sprite']
            tex=texture(name);shade=.62+.38*max(0,np.dot(n,light))
            if np.all(tex==tex[0,0]):
                rgba=tex[0,0]
                if rgba[3]:
                    projected=np.stack([(v-center)@right,(v-center)@up],axis=1)
                    poly.append((float((v.mean(0)-center)@cam),projected,tuple(int(rgba[k]*shade) for k in range(3))+(int(rgba[3]),)))
                continue
            # Each source pixel remains a nearest-neighbour quad on its actual UV plane.
            for j in range(16):
                for i in range(16):
                    u0=i/16;u1=(i+1)/16;v0=j/16;v1=(j+1)/16
                    tx=int((uv[0]+(uv[2]-uv[0])*(u0+.03125))*tex.shape[1]/16)%tex.shape[1]
                    ty=int((uv[1]+(uv[3]-uv[1])*(v0+.03125))*tex.shape[0]/16)%tex.shape[0]
                    rgba=tex[ty,tx];alpha=int(rgba[3])
                    if alpha==0:continue
                    q=np.array([v[0]+(v[3]-v[0])*u+(v[1]-v[0])*t for u,t in [(u0,v0),(u0,v1),(u1,v1),(u1,v0)]])
                    projected=np.stack([(q-center)@right,(q-center)@up],axis=1)
                    c=tuple(int(rgba[k]*shade) for k in range(3))+(alpha,)
                    poly.append((float((q.mean(0)-center)@cam),projected,c))
    points=np.concatenate([p for _,p,_ in poly]);low=points.min(0);high=points.max(0)
    factor=min((size[0]-50)/(high[0]-low[0]),(size[1]-50)/(high[1]-low[1]));mid=(low+high)*.5
    image=Image.new('RGB',size,'#242922');draw=ImageDraw.Draw(image,'RGBA')
    depth=Image.new('F',size,-100000.0);depthdraw=ImageDraw.Draw(depth)
    for d,p,c in sorted(poly,key=lambda x:x[0]):
        p=(p-mid)*[factor,-factor]+[size[0]/2,size[1]/2]
        draw.polygon([tuple(v) for v in p],fill=c)
        if c[3]>180: depthdraw.polygon([tuple(v) for v in p],fill=d)
    if with_depth:
        def project(p):
            q=np.array(p)-center
            return (np.array([q@right,q@up])-mid)*[factor,-factor]+[size[0]/2,size[1]/2],float(q@cam)
        return image,np.array(depth),project,factor
    return image
def font(n):
    for p in [r'C:\Windows\Fonts\meiryo.ttc',r'C:\Windows\Fonts\YuGothM.ttc']:
        if Path(p).exists():return ImageFont.truetype(p,n)
    return ImageFont.load_default()
def text(im,at,v,n=22,color='#dddbc9'):ImageDraw.Draw(im).text(at,v,font=font(n),fill=color)
OUT.mkdir(parents=True,exist_ok=True)

if __name__ == '__main__':
    matrix=boxes('projects:infusion/matrix');pedestal=boxes('projects:infusion/pedestal')
    jar=boxes('projects:infusion/jar_ember')+boxes('minecraft:block/orange_stained_glass',(-.18,-.37,-.18),(.36,.5,.36),False)
    sheet=Image.new('RGB',(1500,950),'#1b201b');text(sheet,(28,15),'製作済みの実モデル / 原寸16pxテクスチャ',30)
    names=['Matrix（頭上に浮く本体）','台座（中心・外側共通）','Jar（液体は別Entity）']
    for col,(name,parts) in enumerate(zip(names,[matrix,pedestal,jar])):
        text(sheet,(col*500+25,70),name,23)
        for row,(yaw,elev,label) in enumerate([(0,8,'正面'),(38,24,'斜め')]):
            tile=render(parts,(470,360),yaw,elev);sheet.paste(tile,(col*500+15,110+row*395));text(sheet,(col*500+30,120+row*395),label,18)
    text(sheet,(28,910),'制作中の比較用：JSONの形・UV・PNGを直接描画。Minecraftの実画面ではありません。',19)
    sheet.save(OUT/'ProjectS-Infusion-Model-Parts.png')
    
    assembly=boxes('projects:infusion/pedestal',(.5,.5,.5))+boxes('projects:infusion/matrix',(.5,3.5,.5))
    for x in [-1,1]:
        for z in [-1,1]:
            for y in [0,1]:assembly+=boxes('minecraft:block/polished_deepslate_wall_post',(x,y,z),centered=False)
            assembly+=boxes('minecraft:block/cut_copper',(x,2,z),centered=False)
    for x,z in [(3,0),(0,3),(-3,0),(0,-3)]:assembly+=boxes('projects:infusion/pedestal',(x+.5,.5,z+.5))
    for x,kind,color in [(-4,'ember','orange'),(0,'tide','cyan'),(4,'gale','lime')]:
        assembly+=boxes('projects:infusion/jar_'+kind,(x+.5,.5,4.5))
        assembly+=boxes('minecraft:block/'+color+'_stained_glass',(x+.32,.13,4.32),(.36,.5,.36),False)
    scene=Image.new('RGB',(1500,900),'#1b201b');text(scene,(28,15),'祭壇＋台座＋設置Jar / 現コードの組合せ',30)
    for col,(yaw,elev,label) in enumerate([(0,10,'正面'),(38,25,'斜め')]):
        tile=render(assembly,(725,730),yaw,elev);scene.paste(tile,(col*750+12,75));text(scene,(col*750+35,90),label,20)
    text(scene,(28,818),'中央台座・浮遊Matrix・外側台座・Jarは今回の実モデル。支柱は現実装のMinecraft標準ブロック。',20)
    text(scene,(28,852),'仮モデル：形と材質のレビュー用。粒子・霧・装備・ワールド背景はこの画像には含めていません。',19)
    scene.save(OUT/'ProjectS-Infusion-Model-Assembly.png')
    (OUT/'model-render-provenance.json').write_text(json.dumps({'renderer':'offline exact cuboid/UV PNG render','modelCommit':'fc1a46644e304eed063979a454fbc771214f599e','minecraftAssets':'cached 26.2 jar','minecraftScreenshot':False,'modelFiles':provenance},indent=2),encoding='utf8')
    print('Rendered exact model parts and assembly front/isometric; no gameplay or browser processes')
    
