"""Render actual Kotlin-exported timeline poses and shipped models, NOT gameplay.

No post-process bloom or painted-in embellishment. Texture UVs/tints/alpha are read
from the pack; native model faces use the vanilla concrete base colours. This
projection QA does not emulate Minecraft occlusion, packet latency or lighting.
Run CoreSkillChoreographyTest first, then this script. Outputs stay in .tools.
"""
import argparse
from functools import lru_cache
import json
import math
from pathlib import Path
import numpy as np
from PIL import Image, ImageChops, ImageDraw, ImageFont
from preview_core_combat_models import COLORS

ROOT = Path(__file__).resolve().parents[1]
PACK = ROOT / 'server-minestom/src/main/resources/core-ui-pack/assets/projects'
FONT = ImageFont.truetype('C:/Windows/Fonts/meiryob.ttc', 14)
W, H = 360, 260

def clip_near(points, near_z=-.6):
    """Clip real world-space faces before perspective, never clamp behind-camera vertices."""
    result=[]
    for a,b in zip(points,points[1:]+points[:1]):
        inside_a,inside_b=a[2]>=near_z,b[2]>=near_z
        if inside_a: result.append(a)
        if inside_a!=inside_b:
            t=(near_z-a[2])/(b[2]-a[2])
            result.append(tuple(a[i]+(b[i]-a[i])*t for i in range(3)))
    return result

@lru_cache(maxsize=4096)
def model_for(key):
    item = json.loads((PACK / f'items/{key}.json').read_text())['model']
    model = json.loads((PACK / ('models/' + item['model'].split(':')[1] + '.json')).read_text())
    return model, tuple(t['value'] for t in item.get('tints', [{'value':0xffffff}]))

@lru_cache(maxsize=256)
def texture_for(name, tint):
    texture = np.array(Image.open(PACK / ('textures/' + name.split(':')[1] + '.png')).convert('RGBA'))
    color = np.array([(tint >> 16) & 255, (tint >> 8) & 255, tint & 255])
    texture[:,:,:3] = (texture[:,:,:3].astype(float) * color / 255).astype('uint8')
    return Image.fromarray(texture)

def clip_textured_face(world, uv, rotation=0, near_z=-.6):
    """Clip positions AND UVs before perspective division; never mirror behind-camera faces."""
    corners=[(uv[0],uv[1]),(uv[2],uv[1]),(uv[2],uv[3]),(uv[0],uv[3])]
    corners=corners[rotation:]+corners[:rotation]
    vertices=[tuple(p)+tuple(t) for p,t in zip(world,corners)]
    result=[]
    for a,b in zip(vertices,vertices[1:]+vertices[:1]):
        inside_a,inside_b=a[2]>=near_z,b[2]>=near_z
        if inside_a: result.append(a)
        if inside_a!=inside_b:
            t=(near_z-a[2])/(b[2]-a[2])
            result.append(tuple(near_z if i==2 else a[i]+(b[i]-a[i])*t for i in range(5)))
    return result


def raster_quad(canvas, depth_buffer, points, texture, uv, perspective=False, vertex_uv=None):
    """Nearest native UV sampling with per-pixel depth, for intersecting models.

    No art is generated here: inputs are the actual authored quad, bitmap and
    display transform. Unlike painter sorting, one long face cannot overwrite
    an entire short face merely because its average depth is nearer.
    """
    tex=np.asarray(texture)
    coords=np.array(vertex_uv if vertex_uv is not None else
                    ((uv[0],uv[1]),(uv[2],uv[1]),(uv[2],uv[3]),(uv[0],uv[3])))/16
    for ids in ((0,i,i+1) for i in range(1,len(points)-1)):
        verts=np.array([points[i] for i in ids],dtype=float)
        tc=coords[list(ids)]
        xmin=max(0,int(math.floor(verts[:,0].min())))
        xmax=min(canvas.shape[1]-1,int(math.ceil(verts[:,0].max())))
        ymin=max(0,int(math.floor(verts[:,1].min())))
        ymax=min(canvas.shape[0]-1,int(math.ceil(verts[:,1].max())))
        if xmin>xmax or ymin>ymax: continue
        x,y=np.meshgrid(np.arange(xmin,xmax+1)+.5,np.arange(ymin,ymax+1)+.5)
        a,b,c=verts
        den=(b[1]-c[1])*(a[0]-c[0])+(c[0]-b[0])*(a[1]-c[1])
        if abs(den)<1e-9: continue
        w0=((b[1]-c[1])*(x-c[0])+(c[0]-b[0])*(y-c[1]))/den
        w1=((c[1]-a[1])*(x-c[0])+(a[0]-c[0])*(y-c[1]))/den
        weights=np.stack((w0,w1,1-w0-w1),axis=-1)
        inside=(weights>=-1e-7).all(axis=-1)
        if perspective:
            weighted=weights/verts[:,2]
            inv=weighted.sum(axis=-1)
            safe=np.where(abs(inv)>1e-10,inv,1e-10)
            depth=1/safe
            sample=sum(weighted[:,:,i,None]*tc[i] for i in range(3))/safe[:,:,None]
            inside &= depth>=.1
        else:
            depth=sum(weights[:,:,i]*verts[i,2] for i in range(3))
            sample=sum(weights[:,:,i,None]*tc[i] for i in range(3))
        tx=np.clip((sample[:,:,0]*texture.width).astype(int),0,texture.width-1)
        ty=np.clip((sample[:,:,1]*texture.height).astype(int),0,texture.height-1)
        ink=tex[ty,tx]
        target_depth=depth_buffer[ymin:ymax+1,xmin:xmax+1]
        mask=inside & (depth<target_depth-1e-8) & (ink[:,:,3]>127)
        canvas[ymin:ymax+1,xmin:xmax+1][mask]=ink[mask]
        target_depth[mask]=depth[mask]


def render(parts, name, tick, view='iso', world_scale=34, fps=20, background='#1b222a', per_pixel_depth=False):
    image = Image.new('RGBA', (W,H), background)
    draw = ImageDraw.Draw(image)
    cx, cy, scale = W*160/360, H*184/260, world_scale
    draw.text((8,5), name, font=FONT, fill='#efe4c9')
    draw.text((8,25), f'{tick/fps:.2f}s / {fps} review frames per second', font=FONT, fill='#b8b8b0')
    def project(x,y,z):
        if view=='front':
            return (cx+x*scale,cy-y*scale,z)
        if view=='side':
            return (cx+z*scale,cy-y*scale,-x)
        if view=='eye':
            depth=z+.7
            if abs(depth)<1e-8: depth=math.copysign(1e-8,depth)
            return (W/2+x/depth*(W*160/360),H/2+(1.62-y)/depth*(W*160/360),depth)
        # Above-ground camera, looking forward and down. The former +Z screen-Y
        # and +Y depth combination viewed UNDERSIDES while sorting high surfaces
        # as farther away. It made closed ice faces look like detached boards.
        return (cx+(x*.8660254+z*.5)*scale,
                cy+(x*.25-z*.4330127-y*.8660254)*scale,
                z*.75-x*.4330127-y*.5)
    for n in range(-4,5):
        draw.line([project(n,0,0 if view=='eye' else -3)[:2],project(n,0,5)[:2]], fill='#2a343e')
        if view!='eye' or n>=0: draw.line([project(-4,0,n)[:2],project(4,0,n)[:2]], fill='#2a343e')
    if view!='eye':
        # Scale the 1.8m reference body with the world, including wide-area reviews.
        for width,bottom,top in ((.5,0,1.4),(.4,1.4,1.8)):
            corners=[project(x,y,0)[:2] for x,y in
                     ((-width/2,bottom),(width/2,bottom),(width/2,top),(-width/2,top))]
            draw.line(corners+[corners[0]],fill='#82909c')
    draw.line([project(0,0,0)[:2],project(0,0,4)[:2]],fill='#ab923e',width=2)
    canvas=np.array(image) if per_pixel_depth else None
    zbuffer=np.full((H,W),np.inf) if per_pixel_depth else None
    faces=[]
    for p in parts:
        model,tints=model_for(p['model'])
        pitch,yaw,roll=p['pitch'],p['yaw'],p['roll']
        def transform(v):
            x,y,z=[(v[i]-8)/16*p['scale'][i] for i in range(3)]
            if 'quaternion' in p:
                qx,qy,qz,qw=p['quaternion']
                tx,ty,tz=2*(qy*z-qz*y),2*(qz*x-qx*z),2*(qx*y-qy*x)
                x,y,z=x+qw*tx+qy*tz-qz*ty,y+qw*ty+qz*tx-qx*tz,z+qw*tz+qx*ty-qy*tx
                return (x+p['offset'][0],y+p['offset'][1],z+p['offset'][2])
            y,z=y*math.cos(pitch)-z*math.sin(pitch),y*math.sin(pitch)+z*math.cos(pitch)
            x,y=x*math.cos(roll)-y*math.sin(roll),x*math.sin(roll)+y*math.cos(roll)
            x,z=x*math.cos(yaw)+z*math.sin(yaw),-x*math.sin(yaw)+z*math.cos(yaw)
            return (x+p['offset'][0],y+p['offset'][1],z+p['offset'][2])
        for e in model['elements']:
            lo,hi=e['from'],e['to']
            def element_point(v):
                rotation=e.get('rotation')
                if not rotation: return v
                assert not rotation.get('rescale',False), 'Unsupported preview rescale'
                if 'axis' not in rotation:
                    # 26.2 CuboidRotation.EulerXYZRotation uses rotationZYX:
                    # apply X, then Y, then Z to the column vector.
                    origin=rotation['origin']
                    x,y,z=[v[i]-origin[i] for i in range(3)]
                    ax,ay,az=[math.radians(rotation.get(k,0)) for k in ('x','y','z')]
                    y,z=y*math.cos(ax)-z*math.sin(ax),y*math.sin(ax)+z*math.cos(ax)
                    x,z=x*math.cos(ay)+z*math.sin(ay),-x*math.sin(ay)+z*math.cos(ay)
                    x,y=x*math.cos(az)-y*math.sin(az),x*math.sin(az)+y*math.cos(az)
                    return [x+origin[0],y+origin[1],z+origin[2]]
                assert rotation['axis'] in ('x','y','z'), 'Unsupported preview rotation'
                angle=math.radians(rotation['angle']); origin=rotation['origin']
                if rotation['axis']=='y':
                    x,z=v[0]-origin[0],v[2]-origin[2]
                    return [origin[0]+x*math.cos(angle)+z*math.sin(angle),v[1],
                            origin[2]-x*math.sin(angle)+z*math.cos(angle)]
                if rotation['axis']=='x':
                    y,z=v[1]-origin[1],v[2]-origin[2]
                    return [v[0],origin[1]+y*math.cos(angle)-z*math.sin(angle),
                            origin[2]+y*math.sin(angle)+z*math.cos(angle)]
                x,y=v[0]-origin[0],v[1]-origin[1]
                return [origin[0]+x*math.cos(angle)-y*math.sin(angle),
                        origin[1]+x*math.sin(angle)+y*math.cos(angle),v[2]]
            vertices=[transform(element_point([hi[j] if i & (1<<j) else lo[j] for j in range(3)])) for i in range(8)]
            # Render only authored faces. Thin weapon backs and folded cloth may
            # use any plane; the old XZ-only shortcut painted nonexistent cube sides.
            for face_name,ids in (('north',(3,2,0,1)),('south',(6,7,5,4)),
                                  ('down',(4,5,1,0)),('up',(2,3,7,6)),
                                  ('west',(2,6,4,0)),('east',(7,3,1,5))):
                if face_name not in e['faces']:
                    continue
                face=e['faces'][face_name]
                world=[vertices[i] for i in ids]
                clipped=clip_near(world) if view=='eye' else world
                if len(clipped)<3: continue
                visible=[project(*v) for v in clipped]
                points=[project(*v) for v in world]
                depth=sum(v[2] for v in visible)/len(visible)
                area=sum(a[0]*b[1]-b[0]*a[1] for a,b in zip(visible,visible[1:]+visible[:1]))
                if area>=-1e-7:
                    continue
                texture_name=model['textures'][face['texture'][1:]]
                if per_pixel_depth:
                    # Native UVs are sampled directly. Cropping/rounding each
                    # contour row introduced false seams in the old projection.
                    if texture_name.startswith('minecraft:'):
                        texture=Image.new('RGBA',(1,1),COLORS[texture_name.split('/')[-1]])
                        uv=[0,0,16,16]
                    else:
                        tint=tints[face.get('tintindex',0)] if 'tintindex' in face else 0xffffff
                        texture=texture_for(texture_name,tint)
                        uv=face['uv']
                    rotation=face.get('rotation',0)//90
                    if view=='eye':
                        polygon=clip_textured_face(world,uv,rotation)
                        if len(polygon)<3: continue
                        raster_quad(canvas,zbuffer,[project(*p[:3]) for p in polygon],texture,uv,True,
                                    [p[3:] for p in polygon])
                    else:
                        if rotation: points=points[-rotation:]+points[:-rotation]
                        raster_quad(canvas,zbuffer,points,texture,uv)
                    continue
                if texture_name.startswith('minecraft:'):
                    color=COLORS[texture_name.split('/')[-1]]
                    faces.append((depth,[v[:2] for v in visible],color))
                    continue
                uv=face['uv']
                tint=tints[face.get('tintindex',0)] if 'tintindex' in face else 0xffffff
                texture=texture_for(texture_name,tint)
                box=(round(min(uv[0],uv[2])/16*texture.width),round(min(uv[1],uv[3])/16*texture.height),
                     round(max(uv[0],uv[2])/16*texture.width),round(max(uv[1],uv[3])/16*texture.height))
                if box[2]<=box[0] or box[3]<=box[1]:
                    # Native UV can legitimately cover less than one texel on
                    # a thin edge. Match nearest sampling instead of rejecting
                    # the model or painting an unrelated neighboring pixel.
                    x=max(0,min(texture.width-1,int((uv[0]+uv[2])/32*texture.width)))
                    y=max(0,min(texture.height-1,int((uv[1]+uv[3])/32*texture.height)))
                    box=(x if box[2]<=box[0] else box[0],y if box[3]<=box[1] else box[1],
                         x+1 if box[2]<=box[0] else box[2],y+1 if box[3]<=box[1] else box[3])
                texture=texture.crop(box)
                # Native pixel contours sample one opaque ink texel. Projecting
                # that face directly is identical material-wise and avoids a
                # full-canvas perspective warp for every little pixel strip.
                if texture.size==(1,1):
                    ink=texture.getpixel((0,0))
                    if ink[3]==0: continue
                    if ink[3]==255:
                        faces.append((depth,[v[:2] for v in visible],ink))
                        continue
                if uv[1]>uv[3]: texture=texture.transpose(Image.Transpose.FLIP_TOP_BOTTOM)
                if uv[0]>uv[2]: texture=texture.transpose(Image.Transpose.FLIP_LEFT_RIGHT)
                rotation=face.get('rotation',0)
                if rotation: texture=texture.rotate(-rotation,expand=True,resample=Image.Resampling.NEAREST)
                target=((0,0),(texture.width,0),(texture.width,texture.height),(0,texture.height))
                # Warp only the on-screen face rectangle. Real painted textures
                # previously allocated a full tile for every tiny native face.
                bx=max(0,math.floor(min(v[0] for v in visible)));by=max(0,math.floor(min(v[1] for v in visible)))
                ex=min(W,math.ceil(max(v[0] for v in visible)));ey=min(H,math.ceil(max(v[1] for v in visible)))
                if ex<=bx or ey<=by:continue
                matrix=[]; result=[]
                for (x,y,_),(u,v) in zip(points,target):
                    x-=bx;y-=by
                    matrix.extend(((x,y,1,0,0,0,-u*x,-u*y),(0,0,0,x,y,1,-v*x,-v*y)))
                    result.extend((u,v))
                try: coefficients=np.linalg.solve(matrix,result)
                except np.linalg.LinAlgError: continue
                warped=texture.transform((ex-bx,ey-by),Image.Transform.PERSPECTIVE,tuple(coefficients),Image.Resampling.NEAREST)
                if view=='eye' and any(v[2]<-.6 for v in world):
                    mask=Image.new('L',(ex-bx,ey-by),0)
                    ImageDraw.Draw(mask).polygon([(v[0]-bx,v[1]-by) for v in visible],fill=255)
                    warped.putalpha(ImageChops.multiply(warped.getchannel('A'),mask))
                faces.append((depth,((bx,by),warped),None))
    for _,content,color in sorted(faces,key=lambda v:v[0],reverse=True):
        if color is None: image.alpha_composite(content[1],content[0])
        else: ImageDraw.Draw(image).polygon(content,fill=color)
    return (Image.fromarray(canvas) if per_pixel_depth else image).convert('RGB')

def main():
    global W,H
    parser=argparse.ArgumentParser()
    parser.add_argument('--ids',default='dash,slam,ass_execute,ass_fan,ass_poison,starfall,star_cloud,temp_pull')
    parser.add_argument('--prefix',default='choreography-review')
    parser.add_argument('--view',choices=('iso','eye','side','front'),default='iso')
    parser.add_argument('--timeline',default='.tools/skill-choreography-frames.json')
    parser.add_argument('--world-scale',type=float,default=34,help='Isometric pixels per block; does not alter gameplay/model size')
    parser.add_argument('--tile-width',type=int,default=360,help='Native projection canvas width, not a resize of a low-resolution render')
    parser.add_argument('--per-pixel-depth',action='store_true',help='Opaque/cutout depth and direct UV sampling; no translucent blending or GPU claim')
    parser.add_argument('--ticks',help='Comma-separated snapshot ticks; skips GIF rendering for broad reviews')
    parser.add_argument('--relative-ticks',help='Snapshot ticks relative to each scene startup, for fair class-wide comparisons')
    parser.add_argument('--fps',type=int,default=20,choices=(20,60),help='60 requires the exported client interpolation timeline')
    parser.add_argument('--background',default='#1b222a',help='Flat contrast-review backdrop, not simulated game lighting')
    args=parser.parse_args()
    if args.tile_width<360 or args.tile_width>1440: parser.error('tile-width must be 360..1440')
    W,H=args.tile_width,round(args.tile_width*260/360)
    source=json.loads((ROOT/args.timeline).read_text(encoding='utf-8'))
    ids=args.ids.split(',')
    scenes=[next(s for s in source if s['id']==i) for i in ids]
    columns=min(4,len(scenes))
    frames=[]
    # The exported test clock retains a long empty tail to prove cleanup. Keep
    # those data unchanged, but don't make a visual review idle for several seconds.
    end=max(max((i for i,p in enumerate(s['frames']) if p),default=0) for s in scenes)+1
    if args.ticks and args.relative_ticks: parser.error('Choose absolute or relative ticks, not both')
    selection=args.ticks or args.relative_ticks
    selected=[int(t) for t in selection.split(',')] if selection else range(end+10)
    for tick in selected:
        sheet=Image.new('RGB',(W*columns,H*math.ceil(len(scenes)/columns)+26),'#111820')
        label='目線高1.62mの簡易透視投影' if args.view=='eye' else '灰枠は身長1.8m'
        heading='実装モデルの確認／ゲーム画面ではありません' if columns==1 else f'実装モデル＋実時間の連続確認（ゲーム画面ではありません／{label}）'
        ImageDraw.Draw(sheet).text((8,3),heading,font=FONT,fill='#d7d0be')
        for i,s in enumerate(scenes):
            actual=tick+s['startup'] if args.relative_ticks else tick
            parts=s['frames'][actual] if 0<=actual<len(s['frames']) else []
            sheet.paste(render(parts,s['name']+' / '+s['id'],actual,args.view,args.world_scale,args.fps,args.background,args.per_pixel_depth),(i%columns*W,i//columns*H+26))
        frames.append(sheet)
        if selection or tick in (0,3,6,10,16,24,32): sheet.save(ROOT/f'.tools/{args.prefix}-{tick:02d}.png')
    if not selection:
        # GIF delays are quantized to 10ms. Distribute 10/20ms holds rather than
        # truncating every 60fps frame to 10ms and accidentally speeding it up.
        durations=[round((i+1)*100/args.fps)*10-round(i*100/args.fps)*10 for i in range(len(frames))]
        frames[0].save(ROOT/f'.tools/{args.prefix}.gif',save_all=True,append_images=frames[1:],duration=durations,loop=0)
    print(f'{len(scenes)} scenes / {len(selected)} review ticks rendered: .tools/{args.prefix}')

if __name__=='__main__': main()
