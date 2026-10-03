"""Offline proposed idle turn; approved models unchanged, no server or save access."""
from pathlib import Path
import importlib.util,json,hashlib,math
from PIL import Image,ImageDraw,ImageFont

path=Path(__file__).with_name('render-infusion-adoption-context-pillow.py')
spec=importlib.util.spec_from_file_location('native_preview',path)
native=importlib.util.module_from_spec(spec);spec.loader.exec_module(native)
out=native.repo/'assets/model-lab/infusion-idle-rotation-study'
out.mkdir(parents=True,exist_ok=True)
font=ImageFont.truetype(r'C:\Windows\Fonts\meiryo.ttc',16)
small=ImageFont.truetype(r'C:\Windows\Fonts\meiryo.ttc',12)
frames=[];records=[]
idle_speed=3.0;ritual_speed=90.0;duration_ms=200;count=60
for i in range(count):
    seconds=i*duration_ms/1000
    im=Image.new('RGB',(1200,520),'#16191e');draw=ImageDraw.Draw(im)
    draw.text((18,8),f'CORE ROTATION / proposed idle versus existing ritual maximum / {seconds:04.1f}s',font=font,fill='#e7dece')
    draw.text((18,38),'PROPOSED IDLE / 3 degrees per second / one turn in 120 seconds',font=small,fill='#bbc0c9')
    draw.text((618,38),'EXISTING RITUAL MAXIMUM / 90 degrees per second',font=small,fill='#bbc0c9')
    for col,speed,glow in [(0,idle_speed,.22),(1,ritual_speed,.88)]:
        assembly,_=native.assembly(seconds*speed,glow)
        im.paste(assembly.convert('RGB'),(col*600,64))
    draw.text((18,494),'Rotation-only offline study; ritual VFX omitted. Approved circles and particles untouched. Clip resets at loop.',font=small,fill='#a3a8b0')
    frames.append(im)
    records.append({'seconds':seconds,'idleYaw':seconds*idle_speed,'ritualYaw':seconds*ritual_speed})
    if i%10==0:print(f'IDLE_ROTATION_RENDER frame={i}',flush=True)
palette=frames[0].quantize(colors=256)
indexed=[im.quantize(palette=palette,dither=Image.Dither.NONE) for im in frames]
gif=out/'idle-versus-ritual-rotation.gif'
indexed[0].save(gif,save_all=True,append_images=indexed[1:],duration=duration_ms,loop=0,disposal=2,optimize=True)
frames[30].save(out/'idle-versus-ritual-context.png',optimize=True)
# A near view lets the unchanged core materials and the actual angular change be checked.
montage=Image.new('RGB',(800,380),'#16191e');md=ImageDraw.Draw(montage)
for col,index in enumerate((0,59)):
    frame=frames[index]
    montage.paste(frame.crop((180,90,580,410)),(400*col,36))
    md.text((10+400*col,8),f'Proposed idle / {records[index]["seconds"]:.1f}s / {records[index]["idleYaw"]:.1f} degrees',font=small,fill='#e7dece')
montage.save(out/'idle-change-detail.png',optimize=True)
report={'scope':'Offline idle rotation only; no native test or adoption','idleDegreesPerSecond':idle_speed,'idleRevolutionSeconds':120,'existingRitualMaximumDegreesPerTick':4.5,'assumedServerTicksPerSecond':20,'ritualDegreesPerSecond':ritual_speed,'frameCount':count,'durationMsPerFrame':duration_ms,'clipSeconds':count*duration_ms/1000,'idleNeutralTint':.22,'ritualIllustrativeNeutralTint':.88,'nativeNotApplied':True,'rejectedFilamentUsed':False,'approvedPackSha1':hashlib.sha1((native.snapshot/'adoption-pack.zip').read_bytes()).hexdigest(),'approvedCircleSha256':hashlib.sha256(native.pack.read('assets/projects/textures/infusion-radiance/wave.png')).hexdigest(),'sourceHashes':native.provenance,'frames':records,'gifBytes':gif.stat().st_size,'gifSha256':hashlib.sha256(gif.read_bytes()).hexdigest(),'limitations':['Offline lighting and fixture layout','Ritual VFX omitted solely to compare rotation speed','Maximum steady-state ritual speed, not acceleration or a full ritual','12-second excerpt resets orientation when GIF loops','No proposed transition easing or save changes implemented']}
(out/'verification.json').write_text(json.dumps(report,indent=2),encoding='utf-8')
print('IDLE_ROTATION_PREVIEW_PASS '+json.dumps({k:report[k] for k in ('idleDegreesPerSecond','frameCount','gifBytes','gifSha256','nativeNotApplied','rejectedFilamentUsed')}))
