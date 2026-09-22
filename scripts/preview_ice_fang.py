"""Project the actual pack models and live WSEE metadata, not a substitute animation."""
import argparse
from functools import lru_cache
import json
import math
from pathlib import Path
import numpy as np
from PIL import Image, ImageDraw, ImageFont
from ice_fang_raster import raster_quad

ROOT=Path(__file__).resolve().parents[1]
BUILD=ROOT/'model-lab/build'
PACK=BUILD/'boss-pack/bundle/resourcepack'
OUT=BUILD/'previews'
FACES={'north':(3,2,0,1),'south':(6,7,5,4),'down':(4,5,1,0),
       'up':(2,3,7,6),'west':(2,6,4,0),'east':(7,3,1,5)}


def euler(angles):
    x,y,z=np.radians(angles)
    rx=np.array([[1,0,0],[0,np.cos(x),-np.sin(x)],[0,np.sin(x),np.cos(x)]])
    ry=np.array([[np.cos(y),0,np.sin(y)],[0,1,0],[-np.sin(y),0,np.cos(y)]])
    rz=np.array([[np.cos(z),-np.sin(z),0],[np.sin(z),np.cos(z),0],[0,0,1]])
    return rz@ry@rx


def quaternion(q):
    x,y,z,w=q
    return np.array([[1-2*(y*y+z*z),2*(x*y-z*w),2*(x*z+y*w)],
                     [2*(x*y+z*w),1-2*(x*x+z*z),2*(y*z-x*w)],
                     [2*(x*z-y*w),2*(y*z+x*w),1-2*(x*x+y*y)]])


@lru_cache(None)
def texture(name):
    namespace, path=name.split(':')
    with Image.open(PACK/f'assets/{namespace}/textures/{path}.png') as im:
        return np.asarray(im.convert('RGBA'))


@lru_cache(None)
def geometry(path,context):
    model=json.loads((PACK/path).read_text())
    display=model.get('display',{}).get(context,{})
    rotation=euler(display.get('rotation',[0,0,0]))
    scale=np.array(display.get('scale',[1,1,1]))
    translation=np.array(display.get('translation',[0,0,0]))/16
    quads=[]
    for element in model['elements']:
        lo,hi=element['from'],element['to']
        points=np.array([[hi[j] if i&(1<<j) else lo[j] for j in range(3)] for i in range(8)])
        r=element.get('rotation',{})
        origin=np.array(r.get('origin',[0,0,0]))
        points=(points-origin)@euler([r.get(k,0) for k in 'xyz']).T+origin
        points=((points/16-.5)*scale)@rotation.T+translation
        for face, ids in FACES.items():
            material=element['faces'].get(face)
            if not material: continue
            uv=material['uv']
            if abs(uv[2]-uv[0])<1e-12 or abs(uv[3]-uv[1])<1e-12:continue
            key=str(material['texture']).lstrip('#')
            quads.append((points[list(ids)],texture(model['textures'][key]),uv))
    return quads


def world_quads(p):
    scale=np.array(p['scale'])
    if np.max(np.abs(scale))<1e-6:return
    left,right=quaternion(p['left']),quaternion(p['right'])
    rotation=euler([p['pitch'],-p['yaw'],0])
    for corners,ink,uv in geometry(p['model'],p['context']):
        local=((corners@right.T)*scale)@left.T+p['translation']
        yield local@rotation.T+p['position'],ink,uv


def render(parts,seconds,view='iso'):
    width,height=720,440
    image=Image.new('RGBA',(width,height),'#1b222a')
    draw=ImageDraw.Draw(image)
    try: font=ImageFont.truetype('C:/Windows/Fonts/meiryob.ttc',15)
    except OSError:font=ImageFont.load_default()
    draw.text((12,8),'氷牙の連鎖 / 実パック＋実エンジンの表示データ',font=font,fill='#efe4c9')
    draw.text((12,31),f'{seconds:.2f}s / CPU projection - NOT Minecraft gameplay',fill='#b8b8b0')
    def project(points):
        p=np.asarray(points);x,y,z=p[:,0],p[:,1]-1,p[:,2]
        if view=='side':return np.column_stack((100+z*62,355-y*62,-x))
        if view=='front':return np.column_stack((360+x*95,375-y*95,z))
        return np.column_stack((195+(x*.8660254+z*.5)*56,
                                405+(x*.25-z*.4330127-y*.8660254)*56,
                                z*.75-x*.4330127-y*.5))
    for n in range(-4,11):
        draw.line([tuple(v) for v in project([[-4,1,n],[4,1,n]])[:,:2]],fill='#2a343e')
    for n in range(-4,5):
        draw.line([tuple(v) for v in project([[n,1,-1],[n,1,10]])[:,:2]],fill='#2a343e')
    for width2,bottom,top in ((.5,1,2.4),(.4,2.4,2.8)):
        pts=project([[-width2/2,bottom,0],[width2/2,bottom,0],[width2/2,top,0],[-width2/2,top,0]])
        draw.line([tuple(v) for v in pts[:,:2]]+[tuple(pts[0,:2])],fill='#82909c',width=2)
    canvas=np.array(image);depth=np.full((height,width),np.inf)
    for part in parts:
        for corners,ink,uv in world_quads(part):
            points=project(corners)
            area=sum(a[0]*b[1]-b[0]*a[1] for a,b in zip(points,np.roll(points,-1,axis=0)))
            if area>=-1e-8: continue
            raster_quad(canvas,depth,points,ink,uv)
    return Image.fromarray(canvas).convert('RGB')


def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('--stills',action='store_true')
    parser.add_argument('--view',choices=['iso','side','front'],default='iso')
    args=parser.parse_args()
    trace=json.loads((OUT/'ice-fang-native.json').read_text())
    frames=trace['frames']
    selected=[0,4,10,16,24,32,40,48,54]
    images=[]
    indices=selected if args.stills else range(len(frames))
    for tick in indices:
        im=render(frames[tick],tick/20,args.view);images.append(im)
        if tick in selected:im.save(OUT/f'ice-fang-{args.view}-{tick:02}.png')
    if args.stills:
        sheet=Image.new('RGB',(720*3,440*3),'#1b222a')
        for i,im in enumerate(images):sheet.paste(im,(i%3*720,i//3*440))
        path=OUT/f'ice-fang-{args.view}-phases.png';sheet.save(path)
    else:
        path=OUT/f'ice-fang-{args.view}-native.gif'
        images[0].save(path,save_all=True,append_images=images[1:],duration=50,loop=0,disposal=2)
    print(path)


if __name__=='__main__':main()
