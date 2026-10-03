"""Role-specific sprite comparison using real sampler output; not a Minecraft capture."""
from pathlib import Path
import json, copy, hashlib, math, io, zipfile, argparse
from PIL import Image, ImageDraw, ImageFont

repo=Path(__file__).resolve().parent.parent
parser=argparse.ArgumentParser()
parser.add_argument('--output-dir',type=Path)
parser.add_argument('--trace',type=Path)
parser.add_argument('--gif-only',action='store_true')
parser.add_argument('--circle-polish',type=Path)
args=parser.parse_args()
out=args.output_dir or repo/'assets/model-lab/infusion-particle-role-study'
out.mkdir(parents=True,exist_ok=True)
trace_path=args.trace or repo/'.tools/infusion-particle-role-study/role-trace.json'
trace=json.loads(trace_path.read_text())
pack_path=repo.parent.parent/'magic-fix/fixed-pack-preview.zip'
pack=zipfile.ZipFile(pack_path)
models=repo/'server-minestom/src/main/resources/core-ui-pack'
prior=repo/'assets/model-lab/infusion-particle-texture-review'
sprites={
    'jar-mist':Image.open(prior/'b-lobed-mist/assets/projects/textures/infusion-texture-review/b-lobed-mist.png').convert('RGBA'),
    'intake-mote':Image.open(prior/'a-core-mote/assets/projects/textures/infusion-texture-review/a-core-mote.png').convert('RGBA'),
}
# Completion fleck is an original role hypothesis; the supplied TC6 still does not show completion.
rows=['................','................','....1...........','...123..........',
      '...2341.........','....3451........','....24541.......','.....23441......',
      '......23421.....','.......1221.....','.........11.....','................',
      '................','................','................','................']
inks={'.':(0,0,0,0),'1':(178,178,178,20),'2':(192,192,192,72),'3':(210,210,210,152),'4':(240,240,240,216),'5':(255,255,255,252)}
assert len(rows)==16 and all(len(r)==16 for r in rows)
spark=Image.new('RGBA',(16,16))
for y,row in enumerate(rows):
    for x,key in enumerate(row):spark.putpixel((x,y),inks[key])
sprites['success-spark']=spark
base_model=json.loads((models/'assets/projects/models/infusion/smoke.json').read_text())
base_item=json.loads((models/'assets/projects/items/infusion/smoke.json').read_text())
sprite_hashes={}
for name,im in sprites.items():
    identifier='infusion-role-study/'+name
    model=copy.deepcopy(base_model);item=copy.deepcopy(base_item)
    model['textures']['cloud']['sprite']='projects:'+identifier
    model['textures']['particle']='projects:'+identifier
    item['model']['model']='projects:'+identifier
    assert model['elements']==base_model['elements']
    for kind,value in [('textures',im),('models',model),('items',item)]:
        p=out/'assets/projects'/kind/(identifier+('.png' if kind=='textures' else '.json'))
        p.parent.mkdir(parents=True,exist_ok=True)
        if kind=='textures':value.save(p)
        else:p.write_text(json.dumps(value,indent=2)+'\n')
    sprite_hashes[name]=hashlib.sha256((out/'assets/projects/textures'/f'{identifier}.png').read_bytes()).hexdigest()

cache={}
polish_sprite=Image.open(args.circle_polish).convert('RGBA') if args.circle_polish else None
def sprite(model,polished=False):
    if polished and polish_sprite is not None and model in ('infusion-radiance/halo','infusion-radiance/wave'):return polish_sprite
    if model not in cache:
        name=model.split('/')[-1]
        cache[model]=sprites[name] if model.startswith('infusion-role-study/') else Image.open(io.BytesIO(pack.read('assets/projects/textures/'+model+'.png'))).convert('RGBA')
    return cache[model]
font=ImageFont.truetype(r'C:\Windows\Fonts\meiryo.ttc',17)
small=ImageFont.truetype(r'C:\Windows\Fonts\meiryo.ttc',13)
SIZE=(600,330);factor=44
az=math.radians(38);el=math.radians(22)
right=(math.cos(az),0,math.sin(az));up=(-math.sin(az)*math.sin(el),math.cos(el),math.cos(az)*math.sin(el))
cam=(math.sin(az)*math.cos(el),math.sin(el),-math.cos(az)*math.cos(el))
def project(p):
    v=[p[0]-.5,p[1]-1.9,p[2]-.5]
    return (350+factor*sum(v[i]*right[i] for i in range(3)),190-factor*sum(v[i]*up[i] for i in range(3)))
def tinted(im,rgb):
    tint=(rgb>>16&255,rgb>>8&255,rgb&255)
    result=im.copy();result.putdata([(round(r*tint[0]/255),round(g*tint[1]/255),round(b*tint[2]/255),a) for r,g,b,a in im.get_flattened_data()])
    return result
def view(frame,candidate,show_markers=True):
    im=Image.new('RGBA',SIZE,(34,31,40,255));draw=ImageDraw.Draw(im)
    # Coordinates only: these markers are not altar or Jar model proxies.
    markers=[((-3.5,.95,2.5),'Jar outlet'),((.5,3.4,.5),'Core pivot'),((.5,1.15,.5),'Equipment')]
    for point,label in (markers if show_markers else []):
        x,y=project(point);draw.line((x-3,y,x+3,y),fill='#6c6570');draw.line((x,y-3,x,y+3),fill='#6c6570')
        draw.text((x+7,y+3),label,font=small,fill='#948c99')
    for s in sorted(frame['samples'],key=lambda s:sum(s['position'][i]*cam[i] for i in range(3))):
        name=s['toModel'] if candidate or args.circle_polish else s['fromModel']
        pixels=tinted(sprite(name,candidate and bool(args.circle_polish)),s['rgb']);x,y,z=s['position'];w=s['width']*s['scale'];h=s['height']*s['scale']
        if s['facing']=='HORIZONTAL':
            layer=Image.new('RGBA',SIZE);pd=ImageDraw.Draw(layer)
            for j in range(pixels.height):
                for i in range(pixels.width):
                    rgba=pixels.getpixel((i,j))
                    if not rgba[3]:continue
                    quad=[project((x-w/2+w*a,y,z-w/2+w*b)) for a,b in [(i/pixels.width,j/pixels.height),(i/pixels.width,(j+1)/pixels.height),((i+1)/pixels.width,(j+1)/pixels.height),((i+1)/pixels.width,j/pixels.height)]]
                    pd.polygon(quad,fill=rgba)
            im.alpha_composite(layer)
        else:
            px,py=project((x,y,z));nw=max(1,round(factor*w));nh=max(1,round(factor*h*(math.cos(el) if s['facing']=='VERTICAL' else 1)))
            stamp=pixels.resize((nw,nh),Image.Resampling.NEAREST)
            im.alpha_composite(stamp,(round(px-nw/2),round(py-nh/2)))
    return im.convert('RGB')

labels={'jar-emission-and-intake':'Jar mist -> core intake (moving inlet distance <= 0.55 blocks)',
        'core-to-item-injection':'Core -> equipment: injection and convergence',
        'completion':'Completion: small flecks; wave timing and size unchanged'}
def card(frame):
    card=Image.new('RGB',(1200,460),'#16191e');d=ImageDraw.Draw(card)
    d.text((20,8),labels[frame['scene']],font=font,fill='#e6dfcd')
    d.text((20,35),f"Sampler clip +{frame['tick']/20:.2f}s | {frame['count']} samples each side | position/count/tint/scale/time identical",font=small,fill='#b2b5bf')
    d.text((20,62),'CURRENT role candidate / original circles' if args.circle_polish else 'CURRENT shared smoke',font=small,fill='#d7cedc')
    d.text((620,62),'PROPOSED circle polish / same role particles' if args.circle_polish else 'PROPOSED role sprite selection',font=small,fill='#d7cedc')
    card.paste(view(frame,False),(0,90));card.paste(view(frame,True),(600,90))
    d.text((20,427),'Particle-only orthographic study. No model occlusion, client lighting or interpolation; not a game capture.',font=small,fill='#9da1af')
    return card

chosen=[]
for scene,tick in [('jar-emission-and-intake',10),('core-to-item-injection',16),('completion',14)]:
    chosen.append(next(f for f in trace['frames'] if f['scene']==scene and f['tick']==tick))
if not args.gif_only:
    sheet=Image.new('RGB',(1200,1660),'#16191e');d=ImageDraw.Draw(sheet)
    d.text((20,12),'ROLE STUDY: sprite IDs + distance policy aligned with isolated native candidate',font=font,fill='#e8dfcd')
    d.text((20,41),'TC6 54:24 still informs bright-core / faint-rim contrast only. Jar and completion roles are hypotheses.',font=small,fill='#b1b4bf')
    for i,f in enumerate(chosen):sheet.paste(card(f),(0,74+460*i))
    for i,name in enumerate(['jar-mist','intake-mote','success-spark']):
        x=22+i*395;y=1463
        d.text((x,y),name+' / 16px, 32px, 7x pixels',font=small,fill='#d1cddd')
        im=tinted(sprites[name],0x84b6bd if name=='jar-mist' else 0xb872f1)
        tile=Image.new('RGBA',(360,140),(34,31,40,255))
        for size,px in [(16,16),(32,58),(112,133)]:tile.alpha_composite(im.resize((size,size),Image.Resampling.NEAREST),(px,16))
        sheet.paste(tile.convert('RGB'),(x,y+28))
    sheet.save(out/'current-vs-role-sprites.png',optimize=True)
    detail=Image.new('RGB',(1100,1050),'#16191e');dd=ImageDraw.Draw(detail)
    dd.text((20,12),'PARTICLE DETAILS / source pixels enlarged with NEAREST',font=font,fill='#e8dfcd')
    dd.text((20,42),'Only mapped small particles are extracted here; the context board retains all large core effects.',font=small,fill='#b1b4bf')
    detail_specs=[('Jar mist','jar-emission-and-intake',8,'jar-mist'),('Intake / late Jar transfer','jar-emission-and-intake',12,'intake-mote'),('Completion small flecks','completion',14,'success-spark')]
    for row,(label,scene,tick,role) in enumerate(detail_specs):
        f=next(f for f in trace['frames'] if f['scene']==scene and f['tick']==tick)
        selected=[s for s in f['samples'] if s['role']==role]
        assert selected
        subset={**f,'samples':selected}
        bounds=[]
        for s in selected:
            px,py=project(s['position']);rad=max(s['width'],s['height'])*s['scale']*factor/2+12
            bounds.append((px-rad,py-rad,px+rad,py+rad))
        box=(max(0,math.floor(min(p[0] for p in bounds))),max(0,math.floor(min(p[1] for p in bounds))),min(SIZE[0],math.ceil(max(p[2] for p in bounds))),min(SIZE[1],math.ceil(max(p[3] for p in bounds))))
        scale=max(1,min(490//(box[2]-box[0]),220//(box[3]-box[1])))
        y=85+row*318
        dd.text((20,y),f'{label} | same {len(selected)} samples | +{tick/20:.2f}s | {scale}x source pixels',font=font,fill='#e0d9cb')
        dd.text((20,y+31),'CURRENT shared smoke',font=small,fill='#c6c3cf');dd.text((565,y+31),'PROPOSED '+role,font=small,fill='#c6c3cf')
        for col,candidate in enumerate([False,True]):
            patch=view(subset,candidate,False).crop(box)
            patch=patch.resize((patch.width*scale,patch.height*scale),Image.Resampling.NEAREST)
            slab=Image.new('RGB',(520,230),'#221f28')
            slab.paste(patch,((520-patch.width)//2,(230-patch.height)//2))
            detail.paste(slab,(20+545*col,y+58))
    detail.save(out/'role-particle-details.png',optimize=True)
frames=[card(f) for f in trace['frames']]
# Shared palette preserves comparison conditions; animation represents three sampler clips, not a full ritual.
palette=Image.new('RGB',(1200,460*3))
for i,f in enumerate(chosen):palette.paste(card(f),(0,460*i))
palette=palette.quantize(colors=256)
indexed=[im.quantize(palette=palette,dither=Image.Dither.NONE) for im in frames]
gif_name='current-vs-circle-polish-radius055.gif' if args.circle_polish else ('current-vs-role-sprites-radius055.gif' if args.gif_only else 'current-vs-role-sprites.gif')
indexed[0].save(out/gif_name,save_all=True,append_images=indexed[1:],duration=100,loop=0,disposal=2,optimize=True)
large_hashes={name:hashlib.sha256(pack.read('assets/projects/textures/infusion-radiance/'+name+'.png')).hexdigest() for name in ['halo','column','wave','burst']}
report={
    'scope':'Offline preview aligned with isolated native role candidate; this run changes no server, client, pack or save',
    'trace_sha256':hashlib.sha256(trace_path.read_bytes()).hexdigest(),
    'samplers':'Installed WorldInfusionSmoke/Charge/Radiance/Confluence; deterministic staged fixture, no server or save',
    'trace_frame_count':len(trace['frames']),
    'role_counts':trace['roleCounts'],
    'only_model_reference_changes':trace['onlyModelReferencesChanged'],
    'geometry_uv_tint_definition_unchanged':True,
    'large_effect_samples_preserved':trace['preservedLargeEffectSamples'],
    'protected_radiance_texture_hashes':large_hashes,
    'candidate_texture_hashes':sprite_hashes,
    'reference_url':'https://jaguidesminecraft.com/wp-content/uploads/2026/07/Thaumcraft-6-Full-Tutorial-1.12.2_at_54-24.png.webp',
    'personally_observed':'Small bright blue/cyan cores, irregular faint rims and varying apparent size/density on TC6 altar still',
    'not_inferred':'Actual TC6 atlas, source/transfer/completion roles, trajectories, complete sequence, TC7 release or completion cycle',
    'policy_proposal':'Jar particle distance <= .55 from moving pose.inlet(pivot) switches from jar-mist to intake-mote; radiance flow and convergence use intake-mote; weapon-success uses success-spark; orbits/pedestal-rise and all large core effects stay original',
    'limits':['Boundary .55 is a proposed ProjectS choice, not evidence from TC6', 'Policy matches isolated native candidate; this preview is not an in-game recording', 'No scene occlusion/client lighting/interpolation; human feel remains unverified', 'Vanilla DUST ingredient trails unchanged and remain a distinct feedback candidate'],
    'circle_texture_polish': {'source':'ImageGen original grayscale circular design; nearest conversion to 64px', 'sprite_sha256':hashlib.sha256(args.circle_polish.read_bytes()).hexdigest(), 'replaced_only_offline':['infusion-radiance/halo','infusion-radiance/wave'], 'column_and_burst_preserved':True, 'sample_fields_and_role_particles_identical_both_sides':True} if args.circle_polish else None,
    'outputs':{name:(out/name).stat().st_size for name in ([gif_name] if args.gif_only else ['current-vs-role-sprites.png',gif_name,'role-particle-details.png'])},
}
(out/'role-study-verification.json').write_text(json.dumps(report,indent=2)+'\n')
(out/'role-trace.json').write_bytes(trace_path.read_bytes())
print(json.dumps(report))
