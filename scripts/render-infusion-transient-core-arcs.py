"""Preview-first transient core arcs, using frozen approved geometry and sampler traces."""
from pathlib import Path
import importlib.util,json,hashlib,math
from PIL import Image,ImageDraw,ImageFont
from infusion_core_arc_candidate import sample,LIFETIME_TICKS,IDLE_PERIOD_TICKS,RITUAL_PERIOD_TICKS

path=Path(__file__).with_name('render-infusion-adoption-context-pillow.py')
spec=importlib.util.spec_from_file_location('native_preview',path)
native=importlib.util.module_from_spec(spec);spec.loader.exec_module(native)
out=native.repo/'assets/model-lab/infusion-transient-core-arcs'
out.mkdir(parents=True,exist_ok=True)
font=ImageFont.truetype(r'C:\Windows\Fonts\meiryo.ttc',16);small=ImageFont.truetype(r'C:\Windows\Fonts\meiryo.ttc',12)
clipping={'paintedPixels':0,'occludedPixels':0};timeline=[]

def arcs_on(image,depth,arcs):
    for arc in arcs:
        points=arc['points']
        # Each stroke stays at its actual 3D depth; the core can hide its root or rear side.
        layer=Image.new('RGBA',native.size);ld=Image.new('F',native.size,-100000)
        dr=ImageDraw.Draw(layer);dz=ImageDraw.Draw(ld)
        for a,b in zip(points,points[1:]):
            pa,da=native.project(a);pb,db=native.project(b)
            steps=max(1,math.ceil(math.dist(pa,pb)*2))
            for n in range(steps):
                u=n/steps;v=(n+1)/steps
                p=(pa[0]+(pb[0]-pa[0])*u,pa[1]+(pb[1]-pa[1])*u)
                q=(pa[0]+(pb[0]-pa[0])*v,pa[1]+(pb[1]-pa[1])*v)
                dep=da+(db-da)*(u+v)/2
                alpha=arc['alpha']
                dr.line((p,q),fill=(118,43,177,round(alpha*43)),width=4)
                dr.line((p,q),fill=tuple(arc['rgb'])+(round(alpha*230),),width=2)
                dr.line((p,q),fill=(251,173,239,round(alpha*175)),width=1)
                dz.line((p,q),fill=dep,width=4)
        before=sum(1 for a in layer.getchannel('A').get_flattened_data() if a)
        native.clip_layer(layer,ld,depth)
        after=sum(1 for a in layer.getchannel('A').get_flattened_data() if a)
        clipping['paintedPixels']+=after;clipping['occludedPixels']+=before-after
        image.alpha_composite(layer)

frames=[];states=[];cache={}
# Idle is the approved direction; subsequent sections preserve the earlier staged ritual samples.
for i in range(40):states.append({'scene':'idle','tick':i*2,'samples':[],'count':0})
states+=native.trace['frames']
for index,f in enumerate(states):
    scene=f['scene'];tick=f['tick'];global_tick=index*2
    yaw=tick*.15 if scene=='idle' else 0 if scene=='completion' else tick*4.5
    glow=.22 if scene=='idle' else .88 if scene=='jar-emission-and-intake' else .94 if scene=='core-to-item-injection' else .9
    key=(yaw,glow)
    if key not in cache:cache[key]=native.assembly(yaw,glow)
    assembly,depth=cache[key]
    active=scene in ('jar-emission-and-intake','core-to-item-injection')
    arcs=sample(global_tick,yaw,active) if scene!='completion' else []
    # The right removes only the old broad core halo. No non-halo sample is changed.
    retained=[s for s in f['samples'] if s['toModel']!='infusion-radiance/halo']
    im=Image.new('RGB',(1200,520),'#16191e');draw=ImageDraw.Draw(im)
    draw.text((18,8),f'TRANSIENT CORE ARCS / {scene} / clip +{tick/20:.2f}s',font=font,fill='#e7dece')
    draw.text((18,36),'APPROVED circle family + slow idle direction',font=small,fill='#bbc0c9')
    draw.text((618,36),'PROPOSED short pink-violet arcs / born, bend changes, fade',font=small,fill='#bbc0c9')
    for col,samples in enumerate((f['samples'],retained)):
        image=assembly.copy();native.effects(image,depth,samples,True)
        if col:arcs_on(image,depth,arcs)
        im.paste(image.convert('RGB'),(col*600,64))
    draw.text((18,497),'Offline staged clips. Reference: TC6 Beta19 ~20:14.06-20:14.35; frequency, occlusion and fade are ProjectS proposals.',font=small,fill='#a3a8b0')
    frames.append(im)
    timeline.append({'frame':index,'scene':scene,'globalTick':global_tick,'yaw':yaw,'originalSamples':f['count'],'retainedNonHaloSamples':len(retained),'oldHaloSamplesRemovedOnRight':len(f['samples'])-len(retained),'arcs':arcs})
    if index%10==0:print(f'TRANSIENT_ARC_RENDER frame={index}',flush=True)
palette_source=Image.new('RGB',(1200,1560),'#16191e')
for row,index in enumerate((13,42,70)):palette_source.paste(frames[index],(0,row*520))
palette=palette_source.quantize(colors=256)
indexed=[im.quantize(palette=palette,dither=Image.Dither.NONE) for im in frames]
gif=out/'approved-versus-transient-core-arcs.gif'
indexed[0].save(gif,save_all=True,append_images=indexed[1:],duration=100,loop=0,disposal=2,optimize=True)
sheet=Image.new('RGB',(1200,1560),'#16191e')
for row,index in enumerate((13,42,70)):sheet.paste(frames[index],(0,row*520))
sheet.save(out/'transient-core-arcs-context.png',optimize=True)
# A fixed-pose contact sheet separates the effect's brief life from camera/model movement.
detail=Image.new('RGB',(1200,360),'#16191e');dd=ImageDraw.Draw(detail)
base,depth=native.assembly(0,.22)
for col,t in enumerate((4,6,8,10,12)):
    image=base.copy();arcs=sample(t,0,False);arcs_on(image,depth,arcs)
    detail.paste(image.crop((170,70,410,360)).convert('RGB'),(240*col,45))
    dd.text((8+240*col,8),['Before','Birth / 0.00s','Bend / 0.10s','Fade / 0.20s','Gone / 0.30s'][col],font=small,fill='#e7dece')
detail.save(out/'arc-lifetime-detail.png',optimize=True)
reference={'url':'https://www.bilibili.com/video/BV1Ss411A73D/?t=1210','version':'Thaumcraft 6 Beta19','observer':'Parent research worker; timed playback personally observed there, relayed to this worker','timecodesApproximate':True,'observations':[{'seconds':1213.92,'state':'No arc visible'},{'seconds':1214.06,'state':'Short bent pink-purple arc near lower left of core'},{'seconds':1214.22,'state':'Arc bulges slightly outward, resembling a bent V'},{'seconds':1214.35,'state':'Clear arc no longer visible'}],'notEstablished':['Smooth writhing: camera also moves','Exact root behind core/pillar','Fixed external endpoint connection','Flux versus essentia mechanic'],'copiedReferenceMediaIncluded':False}
(out/'reference-observation.json').write_text(json.dumps(reference,indent=2),encoding='utf-8')
report={'scope':'Preview-first independent transient core-arc candidate; not native applied','frameCount':len(frames),'durationMsPerFrame':100,'arcLifetimeTicks':LIFETIME_TICKS,'arcLifetimeSeconds':LIFETIME_TICKS/20,'proposedIdleEventPeriodSeconds':IDLE_PERIOD_TICKS/20,'proposedRitualEventPeriodSeconds':RITUAL_PERIOD_TICKS/20,'idleDegreesPerSecond':3,'rejectedFilamentUsed':False,'permanentRingUsed':False,'nativeNotApplied':True,'nonHaloSamplesUnchanged':True,'oldHaloReplacedOnlyOnRight':True,'fineRolesAndMovingInlet055Preserved':True,'approvedPackSha1':hashlib.sha1((native.snapshot/'adoption-pack.zip').read_bytes()).hexdigest(),'approvedWaveSha256':hashlib.sha256(native.pack.read('assets/projects/textures/infusion-radiance/wave.png')).hexdigest(),'modelSourceHashes':native.provenance,'clipping':clipping,'gifBytes':gif.stat().st_size,'gifSha256':hashlib.sha256(gif.read_bytes()).hexdigest(),'designProposalsBeyondObservation':['Event frequency and birth locations','Attachment to proposed slowly rotating core axes','Depth occlusion against native model geometry','Line width, colors and brightness envelope'],'limitations':['Offline lighting/depth approximation','Staged clips reset phase timing and are not a continuous human ritual','Equipped item entity omitted','Parent research relayed temporal observation; this worker did not replay the reference video']}
(out/'verification.json').write_text(json.dumps(report,indent=2),encoding='utf-8')
(out/'arc-timeline.json').write_text(json.dumps(timeline,indent=2),encoding='utf-8')
print('TRANSIENT_CORE_ARC_PREVIEW_PASS '+json.dumps({k:report[k] for k in ('frameCount','gifBytes','gifSha256','nativeNotApplied','clipping')}))
