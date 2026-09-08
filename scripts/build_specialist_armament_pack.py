"""Four texture-led, articulated native weapons; isolated from the playing server.

Consumes preserved pixel layers. Bow tips drive the string endpoints, pages hinge
at the gutter, and the astral ring and star have distinct axes/timing. No client
code, resource-pack index, equipment registration or combat logic is changed.
"""
from copy import deepcopy
import json
import math
from pathlib import Path
import shutil
import zipfile
import numpy as np
from PIL import Image, ImageDraw
from build_texture_first_sword import compile_model
from build_pixel_armament_pack import definition, write_json
from preview_class_armaments import FONT, render_model, rotated
from process_specialist_armament_art import OUT as SOURCE, JOBS
from pixel_weapon_display import grip_pixels, grip_point, calibrated_display
from process_bow_arrow_art import geometry as arrow_geometry, build as build_arrow_art

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT/'.tools/specialist-armament-pack'
PACK = OUT/'pack'
ASSETS = PACK/'assets/projects'
DEPTHS = {
    'bow': {'upper_limb':.20,'grip':.85,'lower_limb':.20},
    'mace': {'body':.7,'crystal':.55},
    'tome': {'binding_cover':.6,'page_backing':.6,'left_page':.12,'right_page':.12},
    'astrolabe': {'shaft':.8,'ring':.18,'star':.4},
}


def centroid(pixels):
    yy,xx = np.nonzero(pixels[:,:,3]>0)
    return [float(np.median(xx))+.5,float(np.median(yy))+.5]


def translate(elements,offset):
    result = deepcopy(elements)
    for e in result:
        for edge in ('from','to'):
            e[edge] = [round(v+d,6) for v,d in zip(e[edge],offset)]
    return result


def turn(elements,axis,angle,origin):
    result = deepcopy(elements)
    if abs(angle)<1e-8: return result
    if not -45<=angle<=45: raise ValueError('Pose exceeds the chosen native rotation range')
    for e in result:
        e['rotation'] = {'axis':axis,'angle':round(angle,6),'origin':list(origin),'rescale':False}
    return result


def mace_body(spec,pixels):
    """Four radial painted flanges, not an edge-on flat head or a cuboid cage.

    The v02 head ends at row 35, above the narrow fixed neck. Traced face masks
    ensure each panel samples only head artwork, never another copy of the grip.
    Two thin crossed silhouettes supply four radial fins at +/-45 degrees.
    """
    yy,_=np.indices(pixels.shape[:2])
    opaque=pixels[:,:,3]>0
    head=opaque & (yy<35)
    shaft=opaque & ~head
    origin=[8,(spec['bottom_pixel']-35)*spec['height']/(spec['bottom_pixel']-spec['top_pixel']),8]
    result=[]
    for label,mask,depth,angle in (('flange_a',head,.18,45),('flange_b',head,.18,-45),
                                   ('shaft',shaft,.7,0)):
        model,_=compile_model({**spec,'parts':[{'name':f'body:{label}',
            'rows':[spec['top_pixel'],spec['bottom_pixel']],
            'thickness':depth,'trace_painted_faces':True}]},np.where(mask,255,0).astype(np.uint8))
        result.extend(turn(model['elements'],'y',angle,origin))
    return result


def geometry(key,entry):
    textures = {part:np.asarray(Image.open(SOURCE/f'{key}-{part}.png')) for part in entry['parts']}
    # Each weapon is anchored at its grip/gutter, never at the texture's center.
    pivot,_ = grip_pixels(key,entry,textures)
    scale = entry['height']/entry['content_size'][1]
    top,bottom = entry['padding'],entry['padding']+entry['content_size'][1]
    def point(px,py,z=8): return [round(8+(px-pivot)*scale,6),round((bottom-py)*scale,6),z]
    parts = {}; base = None
    for part,pixels in textures.items():
        spec = {'height':entry['height'],'top_pixel':top,'bottom_pixel':bottom,
            'pivot_pixel_x':pivot,'alpha_cutoff':128,
            'texture':f'projects:item/weapons/pixel_{key}_{part}',
            'parts':[{'name':part,'rows':[top,bottom],'thickness':DEPTHS[key][part]}]}
        model,_ = compile_model(spec,pixels[:,:,3])
        if key=='mace' and part=='body': model['elements']=mace_body(spec,pixels)
        if base is None: base = model
        for e in model['elements']:
            for face in e['faces'].values(): face['texture'] = '#'+part
        parts[part] = model['elements']
    base['elements'] = []
    base['textures'] = {part:f'projects:item/weapons/pixel_{key}_{part}' for part in textures}
    base['textures']['particle'] = next(iter(base['textures'].values()))
    base['credit'] = 'ProjectS specialist texture-led weapon; isolated native animation review'
    anchors = {}
    if key=='bow':
        for label,layer,upper in (('upper','upper_limb',True),('lower','lower_limb',False)):
            pixels = textures[layer]; yy,xx = np.nonzero(pixels[:,:,3]>0)
            end_y = yy.min() if upper else yy.max()
            anchors[label+'_tip'] = point(float(np.mean(xx[yy==end_y]))+.5,end_y+.5)
            root_y = yy.max() if upper else yy.min()
            anchors[label+'_root'] = point(float(np.mean(xx[yy==root_y]))+.5,root_y+.5)
        anchors['nock_y'] = (anchors['upper_tip'][1]+anchors['lower_tip'][1])/2
        # One authored-material-colored texel used only by two thin cord surfaces.
        textures['string'] = np.array([[[214,225,145,255]]],dtype=np.uint8)
        base['textures']['string'] = 'projects:item/weapons/pixel_bow_string'
        parts['arrow'],textures['arrow'],_ = arrow_geometry()
        base['textures']['arrow'] = 'projects:item/weapons/pixel_bow_arrow'
    elif key=='tome':
        anchors['hinge'] = point(pivot,top+entry['content_size'][1]/2,7.54)
        parts['left_page'] = translate(parts['left_page'],[0,0,-.46])
        parts['right_page'] = translate(parts['right_page'],[0,0,-.46])
    elif key=='astrolabe': anchors['star'] = point(*centroid(textures['star']))
    else: anchors['crystal'] = point(*centroid(textures['crystal']))
    base['display'] = calibrated_display(key,grip_point(key,entry,textures),
        pose(key,base,parts,anchors)['elements'],base['display'])
    return base,parts,textures,anchors


def motion(stage,frame):
    count = {'rest':1,'idle':12,'prepare':6,'release':6}.get(stage)
    if count is None or frame not in range(count): raise ValueError('Invalid specialist pose')
    if stage=='prepare': return frame/5,0.0
    if stage=='release': return (1,.55,.18,.05,.015,0)[frame],0.0
    return 0.0,math.sin(frame*math.tau/12) if stage=='idle' else 0.0


def cord(name,a,b):
    """A thin two-sided strip, not a row of filled cubes or a painted rigid string."""
    a,b = sorted((np.asarray(a),np.asarray(b)),key=lambda p:p[1])
    center = (a+b)/2; delta = b-a
    if abs(delta[2])>1e-6: raise ValueError('Bow string must lie in the limb plane')
    length = float(np.linalg.norm(delta[:2]))
    angle = -math.degrees(math.atan2(delta[0],delta[1]))
    if not -45<=angle<=45: raise ValueError('Cord exceeds native rotation range')
    return {'name':name,'from':[float(center[0]-.09),float(center[1]-length/2),float(center[2])],
        'to':[float(center[0]+.09),float(center[1]+length/2),float(center[2])],
        'shade':False,'rotation':{'axis':'z','angle':angle,'origin':center.tolist(),'rescale':False},
        'faces':{side:{'texture':'#string','uv':[0,0,16,16]} for side in ('north','south')}}


def bow_points(anchors,charge):
    tips = []
    for label,sign in (('upper',1),('lower',-1)):
        r = {'axis':'z','angle':sign*12*charge,'origin':anchors[label+'_root']}
        tips.append(rotated(np.asarray(anchors[label+'_tip']),r))
    # Draw away from the grip. Using the animated tips keeps both ends attached.
    rest_x = (tips[0][0]+tips[1][0])/2
    nock = np.array([rest_x-3.2*charge,anchors['nock_y'],8])
    return tips,nock


def arrow_offset(anchors,stage,frame):
    """A held cosmetic arrow, never a substitute for a server projectile.

    Prepare and the first release pose share the exact nock. The next two poses
    move forward; then the held arrow disappears instead of returning backwards.
    """
    if stage=='prepare': return bow_points(anchors,motion(stage,frame)[0])[1]
    if stage=='release' and frame<3:
        return bow_points(anchors,1)[1]+np.array([(0,5.5,12)[frame],0,0])
    return None


def pose(key,base,parts,anchors,stage='rest',frame=0):
    charge,idle = motion(stage,frame)
    result = deepcopy(base); elements = result['elements']
    if key=='bow':
        elements.extend(deepcopy(parts['grip']))
        for label,sign in (('upper',1),('lower',-1)):
            elements.extend(turn(parts[label+'_limb'],'z',sign*12*charge,anchors[label+'_root']))
        tips,nock = bow_points(anchors,charge)
        elements.extend((cord('string_upper',nock,tips[0]),cord('string_lower',tips[1],nock)))
        arrow = arrow_offset(anchors,stage,frame)
        if arrow is not None: elements.extend(translate(parts['arrow'],arrow))
    elif key=='tome':
        elements.extend(deepcopy(parts['binding_cover']+parts['page_backing']))
        lift = .6*(1-math.cos(frame*math.tau/12)) if stage=='idle' else 0
        elements.extend(turn(parts['left_page'],'y',-36*charge-lift,anchors['hinge']))
        elements.extend(turn(parts['right_page'],'y',24*charge+lift,anchors['hinge']))
    elif key=='astrolabe':
        elements.extend(deepcopy(parts['shaft']))
        elements.extend(turn(parts['ring'],'z',32*charge+3*idle,anchors['star']))
        star = turn(parts['star'],'z',-40*charge-6*idle,anchors['star'])
        offset = [0,.18*idle,-.7*charge]
        star = translate(star,offset)
        for e in star:
            if 'rotation' in e:
                e['rotation']['origin'] = [round(v+d,6) for v,d in zip(e['rotation']['origin'],offset)]
        elements.extend(star)
    elif key=='mace':
        elements.extend(deepcopy(parts['body']))
        crystal = turn(parts['crystal'],'y',35*charge+4*idle,anchors['crystal'])
        offset = [0,0,-.5*charge]
        crystal = translate(crystal,offset)
        for e in crystal:
            if 'rotation' in e:
                e['rotation']['origin'] = [round(v+d,6) for v,d in zip(e['rotation']['origin'],offset)]
        elements.extend(crystal)
    else: raise ValueError('Unknown weapon')
    return result


def build():
    build_arrow_art()
    manifest = json.loads((SOURCE/'manifest.json').read_text())
    write_json(PACK/'pack.mcmeta',{'pack':{'description':'ProjectS articulated specialists / review',
        'min_format':[88,0],'max_format':[88,0]}})
    loaded = {}; report = {}
    for key,entry in manifest['weapons'].items():
        base,parts,textures,anchors = geometry(key,entry)
        loaded[key] = textures
        for part,array in textures.items():
            target = ASSETS/f'textures/item/weapons/pixel_{key}_{part}.png'
            target.parent.mkdir(parents=True,exist_ok=True)
            if part in ('string','arrow'): Image.fromarray(array).save(target)
            else: shutil.copyfile(SOURCE/f'{key}-{part}.png',target)
            write_json(target.with_suffix('.png.mcmeta'),{'texture':{'blur':False,'clamp':False}})
        for stage,count in (('rest',1),('idle',12),('prepare',6),('release',6)):
            for frame in range(count):
                suffix = '' if stage=='rest' else f'_{stage}{frame:02d}'
                write_json(ASSETS/f'models/item/weapons/pixel_{key}{suffix}.json',
                    pose(key,base,parts,anchors,stage,frame))
        write_json(ASSETS/f'items/weapons/pixel_{key}.json',definition(key))
        report[key] = {'parts':{p:len(es) for p,es in parts.items()},'anchors':anchors,
            'poses':24,'texture_size':entry['canvas_size']}
    sheet = Image.new('RGB',(1280,1520),'#1b1e23')
    for row,key in enumerate(JOBS):
        for col,yaw in enumerate((0,-35,90,180)):
            model = json.loads((ASSETS/f'models/item/weapons/pixel_{key}.json').read_text())
            sheet.paste(render_model(model,loaded[key],yaw=yaw,size=(320,350)),(col*320,row*380+30))
            ImageDraw.Draw(sheet).text((col*320+12,row*380+8),f'{key} / {yaw}',font=FONT,fill='#e0d2bb')
    sheet.save(OUT/'model-review.png')
    frames = []
    for stage,count in (('prepare',6),('release',6)):
        for frame in range(count):
            strip = Image.new('RGB',(1280,380),'#1b1e23')
            for col,key in enumerate(JOBS):
                model = json.loads((ASSETS/f'models/item/weapons/pixel_{key}_{stage}{frame:02d}.json').read_text())
                strip.paste(render_model(model,loaded[key],yaw=-35,size=(320,350)),(col*320,30))
                ImageDraw.Draw(strip).text((col*320+12,8),f'{key} / {stage} {frame}',font=FONT,fill='#e0d2bb')
            frames.append(strip)
    frames[0].save(OUT/'actions.gif',save_all=True,append_images=frames[1:],duration=100,loop=0)
    frames[5].save(OUT/'prepared.png')
    bow_sequence=Image.new('RGB',(960,760),'#1b1e23')
    for i,(stage,frame) in enumerate((('prepare',0),('prepare',5),('release',0),
                                     ('release',1),('release',2),('release',3))):
        model=json.loads((ASSETS/f'models/item/weapons/pixel_bow_{stage}{frame:02d}.json').read_text())
        col,row=i%3,i//3
        bow_sequence.paste(render_model(model,loaded['bow'],yaw=0,size=(320,350)),(col*320,row*380+30))
        ImageDraw.Draw(bow_sequence).text((col*320+12,row*380+8),f'{stage} {frame}',font=FONT,fill='#e0d2bb')
    bow_sequence.save(OUT/'bow-sequence.png')
    with zipfile.ZipFile(OUT/'projects-specialist-armaments-review.zip','w',zipfile.ZIP_DEFLATED) as archive:
        for path in sorted(PACK.rglob('*')):
            if path.is_file(): archive.write(path,path.relative_to(PACK).as_posix())
    write_json(OUT/'report.json',{'runtime_applied':False,'native_models_exported':True,
        'weapons':report,'remaining':['game hand transforms and action timing','art quality approval',
        'unique back-side artwork','production pack/equipment registration']})
    print(json.dumps(report))


if __name__=='__main__': build()
