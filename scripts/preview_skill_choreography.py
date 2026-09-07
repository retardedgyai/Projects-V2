"""Render actual Kotlin-exported timeline poses and shipped models, NOT gameplay.

No post-process bloom or painted-in embellishment. Texture UVs/tints/alpha are read
from the pack; native model faces use the vanilla concrete base colours. This
orthographic QA does not emulate Minecraft occlusion, packet latency or lighting.
Run CoreSkillChoreographyTest first, then this script. Outputs stay in .tools.
"""
import argparse
from functools import lru_cache
import json
import math
from pathlib import Path
import numpy as np
from PIL import Image, ImageDraw, ImageFont
from preview_core_combat_models import COLORS

ROOT = Path(__file__).resolve().parents[1]
PACK = ROOT / 'server-minestom/src/main/resources/core-ui-pack/assets/projects'
FONT = ImageFont.truetype('C:/Windows/Fonts/meiryob.ttc', 14)
W, H = 360, 260

@lru_cache(maxsize=4096)
def model_for(key):
    item = json.loads((PACK / f'items/{key}.json').read_text())['model']
    model = json.loads((PACK / ('models/' + item['model'].split(':')[1] + '.json')).read_text())
    return model, item.get('tints', [{'value':0xffffff}])[0]['value']

@lru_cache(maxsize=256)
def texture_for(name, tint):
    texture = np.array(Image.open(PACK / ('textures/' + name.split(':')[1] + '.png')).convert('RGBA'))
    color = np.array([(tint >> 16) & 255, (tint >> 8) & 255, tint & 255])
    texture[:,:,:3] = (texture[:,:,:3].astype(float) * color / 255).astype('uint8')
    return Image.fromarray(texture)

def render(parts, name, tick):
    image = Image.new('RGBA', (W,H), '#1b222a')
    draw = ImageDraw.Draw(image)
    cx, cy, scale = 160, 184, 34
    draw.text((8,5), name, font=FONT, fill='#efe4c9')
    draw.text((8,25), f'{tick/20:.2f}s / 20 ticks per second', font=FONT, fill='#b8b8b0')
    def project(x,y,z):
        return (cx + (x*.94+z*.34)*scale, cy+(z*.53-x*.19-y*.82)*scale, z*.77-x*.28+y*.58)
    for n in range(-4,5):
        draw.line([project(n,0,-3)[:2],project(n,0,5)[:2]], fill='#2a343e')
        draw.line([project(-4,0,n)[:2],project(4,0,n)[:2]], fill='#2a343e')
    draw.rectangle((cx-9,cy-48,cx+9,cy),outline='#82909c')
    draw.rectangle((cx-8,cy-61,cx+8,cy-49),outline='#82909c')
    draw.line([project(0,0,0)[:2],project(0,0,4)[:2]],fill='#ab923e',width=2)
    faces=[]
    for p in parts:
        model,tint=model_for(p['model'])
        pitch,yaw,roll=p['pitch'],p['yaw'],p['roll']
        def transform(v):
            x,y,z=[(v[i]-8)/16*p['scale'][i] for i in range(3)]
            y,z=y*math.cos(pitch)-z*math.sin(pitch),y*math.sin(pitch)+z*math.cos(pitch)
            x,y=x*math.cos(roll)-y*math.sin(roll),x*math.sin(roll)+y*math.cos(roll)
            x,z=x*math.cos(yaw)+z*math.sin(yaw),-x*math.sin(yaw)+z*math.cos(yaw)
            return project(x+p['offset'][0],y+p['offset'][1],z+p['offset'][2])
        for e in model['elements']:
            lo,hi=e['from'],e['to']
            vertices=[transform([hi[j] if i & (1<<j) else lo[j] for j in range(3)]) for i in range(8)]
            if lo[1]==hi[1]:
                # Vanilla FaceInfo.UP: (-X,+Y,-Z), (-X,+Y,+Z), (+X,+Y,+Z), (+X,+Y,-Z).
                face=e['faces']['up']; uv=face['uv']
                texture=texture_for(model['textures'][face['texture'][1:]],tint)
                if uv[1]>uv[3]: texture=texture.transpose(Image.Transpose.FLIP_TOP_BOTTOM)
                if uv[0]>uv[2]: texture=texture.transpose(Image.Transpose.FLIP_LEFT_RIGHT)
                points=[vertices[i] for i in (2,3,6)] # image TL,TR,BL before UV reversal
                source=np.array([[v[0],v[1],1] for v in points])
                if abs(np.linalg.det(source))<.01: continue
                target=np.array([[0,0],[texture.width,0],[0,texture.height]])
                matrix=np.linalg.solve(source,target).T
                warped=texture.transform((W,H),Image.Transform.AFFINE,tuple(matrix.flatten()),Image.Resampling.BILINEAR)
                faces.append((sum(v[2] for v in points)/3,warped,None))
            else:
                for ids in ((0,1,3,2),(4,6,7,5),(0,4,5,1),(2,3,7,6),(0,2,6,4),(1,5,7,3)):
                    points=[vertices[i] for i in ids]
                    key=e['faces']['up']['texture'][1:]
                    color=COLORS[model['textures'][key].split('/')[-1]]
                    faces.append((sum(v[2] for v in points)/4,[v[:2] for v in points],color))
    for _,content,color in sorted(faces,key=lambda v:v[0],reverse=True):
        if color is None: image.alpha_composite(content)
        else: ImageDraw.Draw(image).polygon(content,fill=color)
    return image.convert('RGB')

def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('--ids',default='dash,slam,ass_execute,ass_fan,ass_poison,starfall,star_cloud,temp_pull')
    parser.add_argument('--prefix',default='choreography-review')
    args=parser.parse_args()
    source=json.loads((ROOT/'.tools/skill-choreography-frames.json').read_text(encoding='utf-8'))
    ids=args.ids.split(',')
    scenes=[next(s for s in source if s['id']==i) for i in ids]
    frames=[]
    end=max(len(s['frames']) for s in scenes)
    for tick in range(end+10):
        sheet=Image.new('RGB',(W*4,H*math.ceil(len(scenes)/4)+26),'#111820')
        ImageDraw.Draw(sheet).text((8,3),'実装モデル＋実時間の連続確認（ゲーム画面ではありません／灰枠は身長1.8m）',font=FONT,fill='#d7d0be')
        for i,s in enumerate(scenes):
            parts=s['frames'][tick] if tick<len(s['frames']) else []
            sheet.paste(render(parts,s['name']+' / '+s['id'],tick),(i%4*W,i//4*H+26))
        frames.append(sheet)
        if tick in (0,3,6,10,16,24,32): sheet.save(ROOT/f'.tools/{args.prefix}-{tick:02d}.png')
    frames[0].save(ROOT/f'.tools/{args.prefix}.gif',save_all=True,append_images=frames[1:],duration=50,loop=0)
    print(f'{len(scenes)} scenes / {end} timeline ticks rendered: .tools/{args.prefix}.gif')

if __name__=='__main__': main()
