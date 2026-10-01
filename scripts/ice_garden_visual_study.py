"""Native Ice Garden visual proposal, isolated from the live resource pack/runtime.

Generate real Minecraft cuboid models and pixel textures, then compare them with
38d948e4's Kotlin-exported poses. The proposed timing is a study, not gameplay.
No postprocess glow, painted effects, network access or Minecraft launch.
"""
from pathlib import Path
from functools import lru_cache
import argparse, hashlib, json, math, sys
import numpy as np
from PIL import Image, ImageDraw, ImageFont
from build_ice_garden import garden_art, OFFSETS

ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'.tools/ice-garden-visual-study'
LAB=OUT/'pack/assets/projects'
CURRENT=ROOT/'server-minestom/src/main/resources/core-ui-pack/assets/projects'
FONT_PATH='C:/Windows/Fonts/meiryob.ttc'
FONT=ImageFont.truetype(FONT_PATH,14)
SMALL=ImageFont.truetype(FONT_PATH,12)
PALETTE=['#192e42','#244d68','#458798','#8fc7ce','#d4ece9',
         '#333e49','#52616b','#78868c','#a3b0b1','#bbc4c2']
ANCHORS={(0,2),(-2,0),(1,-2),(2,1),(-1,1)}
W,H,SCALE=464,352,50
BASE='38d948e4ea1a419f5c0942ee2a369ce4d592a466'
CONTACT_AT=2.4

def json_file(path,data):
    path.parent.mkdir(parents=True,exist_ok=True)
    path.write_text(json.dumps(data,separators=(',',':'))+'\n',encoding='utf-8')

def box(x,y,z,w,h,d,ink=0,rotation=None,axis='z'):
    # Face-specific material, not added screen-space shading or bloom.
    tones={'up':ink+4,'south':ink+3,'east':ink+2,'north':ink+1,'west':ink+1,'down':ink}
    e={'from':[x,y,z],'to':[x+w,y+h,z+d],'shade':False,
       'faces':{side:{'texture':'#1','uv':[tone,0,tone+1,1]} for side,tone in tones.items()}}
    if rotation:e['rotation']={'origin':[x+w/2,y,z+d/2],'axis':axis,'angle':rotation,'rescale':False}
    return e

def floor(ending=False):
    e=box(0,8,0,16,.5,16,5 if ending else 0)
    e['faces']['up']={'texture':'#0','uv':[0,0,16,16]}
    return e

def prism(x,z,width,height,turn=0,ink=0):
    height=max(.08,height)
    bottom=box(x,8.55,z,width,height*.68,width,ink,turn or None)
    tip=box(x+width*.22,8.55+height*.68,z+width*.22,width*.56,height*.32,width*.56,ink)
    if turn:
        tip['rotation']={'origin':[x+width/2,8.55,z+width/2],'axis':'z','angle':turn,'rescale':False}
    return [bottom,tip]

def tile_elements(x,z,phase):
    if phase==0:return [floor()]
    if phase>=16:
        progress=(phase-16)/5
        if progress>=1:return []
        # A crown breaks into a handful of chips; ordinary cells leave only a faint splinter.
        # Irregular native pieces replace the floor, so expiry does not resemble a new grey field.
        fragments=[]
        crown=(x,z) in ANCHORS
        if not crown and progress>.55-((x+z)%3)*.1:return []
        count=5 if crown else 1
        for i in range(count):
            angle=i*2*math.pi/count+(x*3+z)*.51
            spread=(2.6 if crown else 1.2)+(2 if crown else .3)*progress
            bx=7+math.sin(angle)*spread;bz=7+math.cos(angle)*spread
            leap=(3.4 if crown else .8)*math.sin(progress*math.pi)
            fragments.append(box(bx,8.3+leap+(i%2)*.5,bz,
                                 1.8 if crown else 1.1,.3*(1-progress)+.08,2.5 if crown else 1.8,5,
                                 (22.5 if i%2 else -22.5)))
        return fragments
    grown=1 if phase>=10 else max(.015,min(1,(phase-1-(abs(x)+abs(z))*.45)/5))
    warning=max(0,(phase-10)/5)
    crown_scale=grown*(1-warning*.48)
    elements=[floor()]
    if (x,z)!=(0,0):
        # Short raised roots; they point into the garden, never form a wall.
        elements.append(box(5.8,8.55,5.9,4.1,.65*grown+.08,4.2))
    if (x,z) in ANCHORS:
        # Five separated rosettes, with petals in both horizontal directions.
        # Their broad dark facets read as ice volumes rather than white lines.
        elements+=prism(5.8,5.8,4.4,9.4*crown_scale,0)
        elements.append(box(2.6,8.55,6.2,3.9,8.3*crown_scale+.08,3.7,0,22.5))
        elements.append(box(9.6,8.55,4.8,3.8,6.8*crown_scale+.08,3.7,0,-22.5))
        elements.append(box(6.6,8.55,2.2,3.7,7.4*crown_scale+.08,3.7,0,22.5,'x'))
        elements.append(box(4.8,8.55,9.6,3.8,6.0*crown_scale+.08,3.8,0,-22.5,'x'))
    elif (x,z)!=(0,0):
        h=(3.5 if abs(x)==2 or abs(z)==2 else 2.0)*crown_scale
        elements+=prism(8,7,2.6,h,-22.5 if (x+z)%2 else 22.5)
    # At the warning, a broken cap drops onto the same plate, without another display.
    if warning>.2 and (x,z) in ANCHORS:
        elements.append(box(4,8.7,11,2.1,1.1,2.6,0,-22.5))
    return elements

def contact_elements(frame):
    if frame>=10:return []
    t=frame/9
    elements=[]
    # The first two frames split a local root. Subsequent chips move out/down.
    if frame<3:
        elements+=prism(6,6,2.5,8*(frame+1)/3,-22.5)
        elements+=prism(9,8,2,6*(frame+1)/3,22.5)
        elements.append(box(3.5,8.4+frame,5,4,.7,6,0,22.5,'x'))
    for i in range(5):
        a=i*2*math.pi/5+.3
        spread=2+4.0*t
        x=7.2+math.cos(a)*spread;z=7.2+math.sin(a)*spread
        y=8.6+math.sin(t*math.pi)*6.3+(i%2)*1.2
        elements.append(box(x,y,z,1.5,1.2*(1-t)+.12,1.8,0 if frame<5 else 5,
                            -22.5 if i%2 else 22.5))
    return elements

def build_models():
    palette=Image.new('RGBA',(16,16),(0,0,0,0));draw=ImageDraw.Draw(palette)
    for i,ink in enumerate(PALETTE):draw.rectangle((i,0,i,15),fill=ink)
    path=LAB/'textures/combat_vfx/garden_study/facets.png';path.parent.mkdir(parents=True,exist_ok=True);palette.save(path)
    for phase in range(22):
        art=garden_art(2 if phase<=10 else 3 if phase<=15 else 4)
        if phase<=15:
            # Quiet the continuous ground material so the native ice crowns are the focal point.
            ink={'#253657':'#203641',
                 '#426b95':'#2b4651','#65adb9':'#4a7180','#a5dfe4':'#608d99'}
            table={tuple(bytes.fromhex(a[1:])):tuple(bytes.fromhex(b[1:])) for a,b in ink.items()}
            for yy in range(80):
                for xx in range(80):
                    r,g,b,a=art.getpixel((xx,yy));art.putpixel((xx,yy),(*table.get((r,g,b),(r,g,b)),a))
            draw=ImageDraw.Draw(art)
            for x,z in OFFSETS:
                xx,yy=(x+2)*16,(z+2)*16
                edge='#b5d7d5' if phase<=10 else '#779ca3'
                if (x-1,z) not in OFFSETS:draw.line([(xx,yy),(xx,yy+15)],fill=edge)
                if (x+1,z) not in OFFSETS:draw.line([(xx+15,yy),(xx+15,yy+15)],fill=edge)
                if (x,z-1) not in OFFSETS:draw.line([(xx,yy),(xx+15,yy)],fill=edge)
                if (x,z+1) not in OFFSETS:draw.line([(xx,yy+15),(xx+15,yy+15)],fill=edge)
        if phase>=16:art=Image.new('RGBA',(80,80))
        for x,z in OFFSETS:
            key=f'combat_vfx/garden_study/tile_{x+2}_{z+2}_{phase}'
            tex=LAB/f'textures/{key}.png';tex.parent.mkdir(parents=True,exist_ok=True)
            art.crop(((x+2)*16,(z+2)*16,(x+3)*16,(z+3)*16)).save(tex)
            model={'ambientocclusion':False,'textures':{'0':f'projects:{key}','1':'projects:combat_vfx/garden_study/facets'},
                   'elements':tile_elements(x,z,phase)}
            json_file(LAB/f'models/{key}.json',model)
            json_file(LAB/f'items/{key}.json',{'model':{'type':'minecraft:model','model':'projects:'+key}})
    for frame in range(11):
        key=f'combat_vfx/garden_study/contact_{frame}'
        json_file(LAB/f'models/{key}.json',{'ambientocclusion':False,'textures':{'1':'projects:combat_vfx/garden_study/facets'},
                                        'elements':contact_elements(frame)})
        json_file(LAB/f'items/{key}.json',{'model':{'type':'minecraft:model','model':'projects:'+key}})
    index=sorted(p.relative_to(OUT/'pack').as_posix() for p in LAB.rglob('*') if p.is_file())
    (OUT/'pack/index.txt').write_text('\n'.join(index)+'\n',encoding='utf-8')

def phase_for(t):
    if t<.3:return 0
    if t<.85:return min(10,1+int((t-.3)*18))
    if t<5.3:return 10
    if t<6.3:return min(15,11+int((t-5.3)*5))
    return min(21,16+int((t-6.3)*10))

def proposed_parts(t):
    phase=phase_for(t)
    if phase==0:return [] # Native prepare corners come from the unchanged current export.
    result=[{'model':f'combat_vfx/garden_study/tile_{x+2}_{z+2}_{phase}',
             'offset':[x*1.25,.035,z*1.25],'scale':[1.25,1,1.25],'yaw':0,'pitch':0,'roll':0} for x,z in OFFSETS]
    age=round((t-CONTACT_AT)*20)
    if 0<=age<10:result.append({'model':f'combat_vfx/garden_study/contact_{age}',
                              'offset':[1.25,.08,3.0],'scale':[1,1,1],'yaw':0,'pitch':0,'roll':0})
    return result

@lru_cache(maxsize=4096)
def native_model(pack,key):
    pack=Path(pack)
    item=json.loads((pack/f'items/{key}.json').read_text())['model']
    return json.loads((pack/f'models/{item["model"].split(":")[1]}.json').read_text())

@lru_cache(maxsize=2048)
def native_texture(pack,name):
    return Image.open(Path(pack)/f'textures/{name.split(":")[1]}.png').convert('RGBA')

def rotate(v,rotation):
    if not rotation:return v
    origin=rotation['origin'];a=math.radians(rotation['angle'])
    x,y,z=[v[i]-origin[i] for i in range(3)]
    if rotation['axis']=='z':x,y=x*math.cos(a)-y*math.sin(a),x*math.sin(a)+y*math.cos(a)
    elif rotation['axis']=='x':y,z=y*math.cos(a)-z*math.sin(a),y*math.sin(a)+z*math.cos(a)
    else:x,z=x*math.cos(a)+z*math.sin(a),-x*math.sin(a)+z*math.cos(a)
    return [x+origin[0],y+origin[1],z+origin[2]]

def project(x,y,z,side=False):
    if side:return W/2+z*SCALE,254-y*SCALE,-x
    return W/2+(x*.94+z*.34)*SCALE,196+(z*.53-x*.19-y*.82)*SCALE,z*.77-x*.28+y*.58

def actor_at(t):
    if t<1.8:return 1.25,4.6
    if t<2.4:return 1.25,4.6-(t-1.8)/.6*1.6
    if t<3.0:return 1.25,3.0-(t-2.4)/.6*2
    if t<4.0:return 1.25,1.0
    return 1.25,1.0+min(1,(t-4)/.8)*3.6

FACES=(('north',(3,2,0,1)),('south',(6,7,5,4)),('down',(4,5,1,0)),
       ('up',(2,3,7,6)),('west',(2,6,4,0)),('east',(7,3,1,5)))

def render(parts,pack,t,side=False):
    image=Image.new('RGBA',(W,H),'#1b252e');d=ImageDraw.Draw(image)
    for n in range(-4,6):
        d.line([project(n,0,-3,side)[:2],project(n,0,5,side)[:2]],fill='#2b3741')
        d.line([project(-4,0,n,side)[:2],project(4,0,n,side)[:2]],fill='#2b3741')
    # Same 1.8m wireframe reference and contact trajectory on both sides; not an enemy model.
    actor=actor_at(t);faces=[]
    for p in parts:
        ppack=CURRENT if p['model'].startswith('combat_vfx/garden/') else pack
        model=native_model(str(ppack),p['model'])
        def transform(v):
            x,y,z=[(v[i]-8)/16*p['scale'][i] for i in range(3)]
            a,b,c=p['pitch'],p['roll'],p['yaw']
            y,z=y*math.cos(a)-z*math.sin(a),y*math.sin(a)+z*math.cos(a)
            x,y=x*math.cos(b)-y*math.sin(b),x*math.sin(b)+y*math.cos(b)
            x,z=x*math.cos(c)+z*math.sin(c),-x*math.sin(c)+z*math.cos(c)
            return [x+p['offset'][0],y+p['offset'][1],z+p['offset'][2]]
        for e in model['elements']:
            vertices=[transform(rotate([e['to'][j] if i&(1<<j) else e['from'][j] for j in range(3)],e.get('rotation'))) for i in range(8)]
            for side_name,ids in FACES:
                if side_name not in e['faces']:continue
                face=e['faces'][side_name];points=[project(*vertices[i],side) for i in ids]
                area=sum(a[0]*b[1]-b[0]*a[1] for a,b in zip(points,points[1:]+points[:1]))
                # Above-ground/front faces have positive screen winding for this native face order.
                if area<=1e-7:continue
                tex=native_texture(str(ppack),model['textures'][face['texture'][1:]])
                uv=face['uv'];boxuv=tuple(round(v/16*(tex.width if i%2==0 else tex.height)) for i,v in enumerate(uv))
                tex=tex.crop((min(boxuv[0],boxuv[2]),min(boxuv[1],boxuv[3]),max(boxuv[0],boxuv[2]),max(boxuv[1],boxuv[3])))
                depth=sum(v[2] for v in points)/4
                if tex.size==(1,1):
                    ink=tex.getpixel((0,0))
                    if ink[3]:faces.append((depth,[v[:2] for v in points],ink))
                    continue
                if uv[1]>uv[3]:tex=tex.transpose(Image.Transpose.FLIP_TOP_BOTTOM)
                if uv[0]>uv[2]:tex=tex.transpose(Image.Transpose.FLIP_LEFT_RIGHT)
                target=[(0,0),(tex.width,0),(tex.width,tex.height),(0,tex.height)]
                matrix=[];answer=[]
                for (x,y,_),(u,v) in zip(points,target):
                    matrix.extend([(x,y,1,0,0,0,-u*x,-u*y),(0,0,0,x,y,1,-v*x,-v*y)]);answer.extend([u,v])
                try:coeff=np.linalg.solve(matrix,answer)
                except np.linalg.LinAlgError:continue
                warp=tex.transform((W,H),Image.Transform.PERSPECTIVE,tuple(coeff),Image.Resampling.NEAREST)
                faces.append((depth,warp,None))
    # Positive depth is towards the overhead camera; draw far faces first.
    for _,content,ink in sorted(faces,key=lambda f:f[0]):
        if ink is None:image.alpha_composite(content)
        else:ImageDraw.Draw(image).polygon(content,fill=ink)
    d=ImageDraw.Draw(image)
    x,z=actor
    foot=project(x,0,z,side);head=project(x,1.8,z,side)
    d.line([(foot[0]-8,foot[1]),(head[0]-8,head[1]),(head[0]+8,head[1]),(foot[0]+8,foot[1])],fill='#dd9682',width=2)
    d.line([project(x,1.4,z,side)[:2],project(x,1.8,z,side)[:2]],fill='#dd9682',width=3)
    d.text((12,H-22),'橙枠: 1.8mの敵の模式位置',font=SMALL,fill='#dda390')
    return image.convert('RGB')

def current_parts(t,source):
    tick=min(len(source['frames'])-1,round(t*20))
    parts=[dict(p) for p in source['frames'][tick]['new']]
    age=round((t-CONTACT_AT)*20)
    if 0<=age<len(source['contacts']):
        for p in source['contacts'][age]:
            p=dict(p);p['offset']=[p['offset'][0]+1.25,p['offset'][1],p['offset'][2]+3.0];parts.append(p)
    return parts

def caption(t):
    if t<.3:return '予兆 / 支持床の境界は現行のまま'
    if t<.9:return '展開 / 境界は即時。低い結晶だけが中心から育つ'
    if 2.4<=t<2.9:return '初回接触 / 足元の根が割れ、少量の欠片が飛ぶ'
    if t<5.3:return '保持 / 中央の見通しを残し、結晶は静かに残る'
    if t<6.3:return '残り1秒 / 結晶の先端が折れ、背が低くなる'
    return '終了 / 有効境界を消し、無彩色の欠片が低く崩れる'

def compare_frame(t,source,side=False):
    image=Image.new('RGB',(W*2,H+90),'#111a23');d=ImageDraw.Draw(image)
    d.text((12,5),'氷の庭 / 現行モデルと立体演出案の同倍率比較',font=FONT,fill='#e1ebe6')
    d.text((12,26),'native形状・pixel UVのCPU投影。別案の時間変化は未実装。実機映像ではありません。',font=SMALL,fill='#a8b6bb')
    d.text((12,49),'現行 38d948e4 / Kotlin出力ポーズ',font=FONT,fill='#d0dadb')
    d.text((W+12,49),'別案「氷の芽吹き」/ nativeモデル試作',font=FONT,fill='#c9e5df')
    image.paste(render(current_parts(t,source),CURRENT,t,side),(0,73))
    proposal=source['frames'][round(t*20)]['new'] if t<.3 else proposed_parts(t)
    image.paste(render(proposal,LAB,t,side),(W,73))
    d=ImageDraw.Draw(image);d.line([(W,46),(W,H+77)],fill='#43515a')
    d.text((12,H+74),f'{t:.2f}s   '+caption(t),font=SMALL,fill='#e4ddc8')
    return image

def validate():
    models=list((LAB/'models').rglob('*.json'));counts=[];max_height=0
    for path in models:
        model=json.loads(path.read_text())
        assert model.get('ambientocclusion') is False
        assert len(model['elements'])<=10,(path,len(model['elements']))
        counts.append(len(model['elements']))
        for e in model['elements']:
            assert all(b>a for a,b in zip(e['from'],e['to']))
            assert all(-16<=v<=32 for v in e['from']+e['to'])
            if 'rotation' in e:assert e['rotation']['angle'] in [-22.5,22.5] and not e['rotation']['rescale']
            for i in range(8):
                v=rotate([e['to'][j] if i&(1<<j) else e['from'][j] for j in range(3)],e.get('rotation'))
                max_height=max(max_height,(v[1]-8)/16+.035)
                if path.name.startswith('tile_'):
                    assert -.5<=v[0]<=16.5 and -.5<=v[2]<=16.5,(path,v)
                    assert (v[1]-8)/16+.035<.72,(path,v)
            for face in e['faces'].values():
                assert face['texture'].startswith('#') and face['texture'][1:] in model['textures']
                assert all(0<=v<=16 for v in face['uv'])
        for name in model['textures'].values():assert (LAB/f'textures/{name.split(":")[1]}.png').is_file()
    for path in (LAB/'textures').rglob('*.png'):
        im=Image.open(path).convert('RGBA');assert im.size==(16,16)
        assert set(im.getchannel('A').tobytes())<={0,255}
    centre=tile_elements(0,0,10)
    assert len(centre)==1 and max(e['to'][1] for e in centre)<9
    assert len(proposed_parts(2.5))==22 and len(proposed_parts(3.0))==21
    assert all(not json.loads(p.read_text())['elements'] for p in (LAB/'models').rglob('tile_*_21.json'))
    # No cyan survives the expiry: only the neutral facet strip is referenced by ending faces.
    for path in (LAB/'models').rglob('tile_*_16.json'):
        for e in json.loads(path.read_text())['elements']:
            assert all(face['texture']=='#1' and face['uv'][0]>=5 for face in e['faces'].values())
    return {'native_models':len(models),'max_elements_per_cell_or_contact':max(counts),
            'max_native_height_metres':round(max_height,4),'field_displays':21,'contact_displays':1,
            'mature_native_cuboids':sum(len(tile_elements(x,z,10)) for x,z in OFFSETS),
            'central_plate_height_metres':.06625,'checks':'native bounds/rotation/UV, pixel alpha, centre sightline, display count, neutral expiry, final empty models'}

def main():
    parser=argparse.ArgumentParser();parser.add_argument('--baseline-timeline',required=True)
    parser.add_argument('--snapshots-only',action='store_true');args=parser.parse_args()
    OUT.mkdir(parents=True,exist_ok=True);build_models()
    timeline=Path(args.baseline_timeline)
    source=json.loads(timeline.read_text(encoding='utf-8'));checks=validate()
    stills=[.1,.5,1.2,2.6,5.7,6.5]
    sheet=Image.new('RGB',(W*2,(H+90)*len(stills)),'#111a23')
    for i,t in enumerate(stills):
        image=compare_frame(t,source);sheet.paste(image,(0,i*(H+90)))
        image.save(OUT/f'compare-{t:.1f}s.png')
    sheet.save(OUT/'ice-garden-visual-storyboard.png')
    compare_frame(3.0,source,True).save(OUT/'ice-garden-height-side.png')
    if not args.snapshots_only:
        frames=[compare_frame(t/10,source) for t in range(69)]
        # A fixed palette from the material inks prevents GIF palette shimmer on static ice.
        atlas=Image.new('RGB',(W*2,(H+90)*3))
        for i,t in enumerate([.6,2.6,6.5]):atlas.paste(compare_frame(t,source),(0,(H+90)*i))
        palette=atlas.quantize(colors=128,method=Image.Quantize.MEDIANCUT)
        frames=[im.quantize(palette=palette,dither=Image.Dither.NONE) for im in frames]
        frames[0].save(OUT/'ice-garden-current-vs-bloom.gif',save_all=True,append_images=frames[1:],
                       duration=100,loop=0,optimize=False,disposal=2)
    evidence={'status':'VISUAL STUDY — NOT IMPLEMENTED / NOT MANUAL SMOKE PASS','fixed_logic_commit':BASE,
              'baseline_timeline_sha256':hashlib.sha256(timeline.read_bytes()).hexdigest(),
              'sampled_cell_size':1.25,'active_duration_seconds':6,'contact_time_schematic_seconds':CONTACT_AT,
              'production_files_modified':False,'models':checks,
              'projection_correction':'Same corrected front-face winding and far-to-near depth sort for both variants',
              'proposal_timing':'Formation .3–.85s; static hold; first-contact .5s; warning5.3–6.3s; neutral collapse .5s',
              'runtime_changes_needed':['Garden-only model phase selection for formation/warning/expiry',
                                        'Garden-only contact model frames, preserving first accepted-hit trigger'],
              'not_verified':['Game input/feel, audio, client FPS, network visibility, runtime metadata cost'],
              'not_changed':['Damage/hit/slow/resources/cancel/boss/terrain/active duration/input','Shipped resource pack','Native scene/owner caps','RP-less supported-corner prepare']}
    json_file(OUT/'evidence.json',evidence)
    print(json.dumps({'output':str(OUT),'checks':checks,'gif_frames':0 if args.snapshots_only else 69},ensure_ascii=False,indent=2))

if __name__=='__main__':
    sys.stdout.reconfigure(encoding='utf-8');main()
