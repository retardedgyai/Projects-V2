"""Textured orthographic QA of exported native models. NOT a Minecraft screenshot.

Reads real exported geometry/UVs and nearest-samples the actual 64px atlas.
No invented texture, smooth shading, bloom or retouching of the rendered art.
"""
import argparse
import json
import math
from pathlib import Path
import numpy as np
from PIL import Image, ImageDraw, ImageFont
from class_armament_geometry import ASSETS, KINDS, FRAMES

ROOT=ASSETS.parents[6]
OUT=Path(__file__).resolve().parents[1]/'.tools/armament-review'
W,H=224,368
FONT=ImageFont.truetype('C:/Windows/Fonts/meiryob.ttc',13)
LABELS=('戦士 / 大剣','メイジ / 杖','レンジャー / 長弓','アサシン / 短剣','テンプラー / 戦槌','ヒーラー / 聖典','星織り師 / 星環杖')


def rotated(v, r):
    if not r: return v
    axis='xyz'.index(r['axis']); a=math.radians(r['angle']); o=np.array(r['origin']); p=v-o
    if axis==0: matrix=np.array([[1,0,0],[0,math.cos(a),-math.sin(a)],[0,math.sin(a),math.cos(a)]])
    elif axis==1: matrix=np.array([[math.cos(a),0,math.sin(a)],[0,1,0],[-math.sin(a),0,math.cos(a)]])
    else: matrix=np.array([[math.cos(a),-math.sin(a),0],[math.sin(a),math.cos(a),0],[0,0,1]])
    return p@matrix.T+o


def render_model(model,textures,yaw=-25,size=(W,H),scale=9.2):
    width,height=size
    pixels=np.full((height,width,3),[27,30,35],dtype=np.uint8); depth=np.full((height,width),np.inf)
    yaw,pitch=math.radians(yaw),math.radians(8)
    def project(v):
        x,y,z=v-np.array([8,0,8]); x,z=x*math.cos(yaw)+z*math.sin(yaw),-x*math.sin(yaw)+z*math.cos(yaw)
        y,z=y*math.cos(pitch)-z*math.sin(pitch),y*math.sin(pitch)+z*math.cos(pitch)
        return np.array([width/2+x*scale,height-33-y*scale,z])
    for e in model['elements']:
        vertices=[project(rotated(np.array([e['to'][j] if i&(1<<j) else e['from'][j] for j in range(3)]),e.get('rotation'))) for i in range(8)]
        # Screen TL/TR/BR/BL at the unrotated visible front; each face still uses
        # the exported UV rectangle. Independent z-buffer handles recessed parts.
        for name,indices in [('north',(2,3,1,0)),('south',(7,6,4,5)),('west',(6,2,0,4)),('east',(3,7,5,1)),('up',(6,7,3,2)),('down',(0,1,5,4))]:
            face=e['faces'][name]; atlas=textures[face['texture'][1:]]
            uv=face['uv']; tex=np.array([[uv[0],uv[1]],[uv[2],uv[1]],[uv[2],uv[3]],[uv[0],uv[3]]])*np.array([atlas.shape[1]/16,atlas.shape[0]/16])
            p=np.array([vertices[i] for i in indices])
            for ids in ((0,1,2),(0,2,3)):
                t=p[list(ids)]; tt=tex[list(ids)]
                lo=np.maximum(np.floor(t[:,:2].min(axis=0)).astype(int),[0,0]); hi=np.minimum(np.ceil(t[:,:2].max(axis=0)).astype(int),[width-1,height-1])
                if np.any(lo>hi): continue
                xx,yy=np.meshgrid(np.arange(lo[0],hi[0]+1)+.5,np.arange(lo[1],hi[1]+1)+.5)
                matrix=np.array([[t[0,0],t[1,0],t[2,0]],[t[0,1],t[1,1],t[2,1]],[1,1,1]])
                if abs(np.linalg.det(matrix))<1e-7: continue
                bary=np.linalg.solve(matrix,np.stack([xx.ravel(),yy.ravel(),np.ones(xx.size)]))
                z=t[:,2]@bary; uvs=tt.T@bary
                ys=yy.ravel().astype(int); xs=xx.ravel().astype(int)
                coords=np.floor(uvs).astype(int)
                coords[0]=np.clip(coords[0],0,atlas.shape[1]-1); coords[1]=np.clip(coords[1],0,atlas.shape[0]-1)
                visible=atlas[coords[1],coords[0],3]>=128 if atlas.shape[2]==4 else np.ones(xs.size,dtype=bool)
                # Stable tie breaking for adjacent coplanar inflated armor
                # cubes; floating-point solve noise is not material detail.
                mask=np.all(bary>=-1e-6,axis=0)&(z<depth[ys,xs]-1e-6)&visible
                xs,ys=xs[mask],ys[mask]; coords=coords[:,mask]
                shade={'up':1,'down':.5,'north':.85,'south':.85,'east':.65,'west':.65}[name]
                pixels[ys,xs]=(atlas[coords[1],coords[0],:3]*shade).astype(np.uint8); depth[ys,xs]=z[mask]
    return Image.fromarray(pixels)


def render(kind,tier,frame,yaw=-25):
    model=json.loads((ASSETS/f'models/item/weapons/{kind}_t{tier}_frame{frame:02d}.json').read_text())
    atlas=np.array(Image.open(ASSETS/'textures/item/weapons/materials.png').convert('RGBA'))
    image=render_model(model,{'atlas':atlas},yaw); draw=ImageDraw.Draw(image)
    draw.text((10,8),LABELS[KINDS.index(kind)],font=FONT,fill='#ece3cd')
    draw.text((10,28),f'T{tier} / frame {frame:02d}',font=FONT,fill='#9faeb8')
    return image


def main():
    parser=argparse.ArgumentParser(); parser.add_argument('--animate',action='store_true'); args=parser.parse_args()
    OUT.mkdir(parents=True,exist_ok=True)
    sheet=Image.new('RGB',(W*7,H*4+30),'#171b20')
    ImageDraw.Draw(sheet).text((10,5),'実装モデル・実テクスチャの正投影確認（Minecraft画面ではありません）',font=FONT,fill='#d5cec0')
    for tier in range(1,5):
        for i,kind in enumerate(KINDS): sheet.paste(render(kind,tier,0),(i*W,(tier-1)*H+30))
    sheet.save(OUT/'all-tiers.png')
    if args.animate:
        frames=[]
        for frame in range(FRAMES):
            strip=Image.new('RGB',(W*7,H+30),'#171b20')
            ImageDraw.Draw(strip).text((10,5),'実装の12コマ / 10fps（パケット・ゲーム内表示は別途検証）',font=FONT,fill='#d5cec0')
            for i,kind in enumerate(KINDS): strip.paste(render(kind,4,frame),(i*W,30))
            frames.append(strip)
        frames[0].save(OUT/'idle.gif',save_all=True,append_images=frames[1:],duration=100,loop=0)
    print(OUT)


if __name__=='__main__': main()
