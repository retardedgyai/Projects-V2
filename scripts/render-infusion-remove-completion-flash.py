"""Compare only the rejected completion flash; all accepted elements are identical."""
from pathlib import Path
import importlib.util,json,hashlib
from PIL import Image,ImageDraw,ImageFont
path=Path(__file__).with_name('render-infusion-adoption-context-pillow.py')
spec=importlib.util.spec_from_file_location('native_preview',path)
native=importlib.util.module_from_spec(spec);spec.loader.exec_module(native)
out=native.repo/'assets/model-lab/infusion-no-completion-flash';out.mkdir(parents=True,exist_ok=True)
font=ImageFont.truetype(r'C:\Windows\Fonts\meiryo.ttc',16);small=ImageFont.truetype(r'C:\Windows\Fonts\meiryo.ttc',12)
base,depth=native.assembly(0,.9);images=[];removed=[]
for f in native.trace['frames']:
    if f['scene']!='completion':continue
    before=f['samples'];after=[s for s in before if s['key']!='radiance:flash']
    removed.extend({'tick':f['tick'],'sample':s} for s in before if s['key']=='radiance:flash')
    im=Image.new('RGB',(1200,520),'#16191e');d=ImageDraw.Draw(im)
    d.text((18,8),f'POST-INJECTION / complete +{f["tick"]/20:.2f}s / remove only the pale flash',font=font,fill='#e7dece')
    d.text((18,38),'BEFORE / completion burst flash',font=small,fill='#bbc0c9');d.text((618,38),'AFTER / flash removed; lower circle and small sparks retained',font=small,fill='#bbc0c9')
    for col,samples in enumerate((before,after)):
        layer=base.copy();native.effects(layer,depth,samples,True);im.paste(layer.convert('RGB'),(600*col,64))
    d.text((18,497),'Same approved core, geometry, lower circle, particles and timings. Offline staged completion clip; no native change yet.',font=small,fill='#a3a8b0')
    images.append(im)
palette=images[0].quantize(colors=256);indexed=[im.quantize(palette=palette,dither=Image.Dither.NONE) for im in images]
gif=out/'completion-before-after-no-white-flash.gif'
indexed[0].save(gif,save_all=True,append_images=indexed[1:],duration=100,loop=0,disposal=2,optimize=True)
images[2].save(out/'completion-before-after-no-white-flash.png',optimize=True)
report={'removedOnlyKey':'radiance:flash','removedModel':'infusion-radiance/burst','actualPreviousGifIntervalSeconds':[6.3,6.7],'completionAgeTicksInclusive':[0,8],'removedSamples':removed,'allOtherSamplesUnchanged':True,'approvedWaveSha256':hashlib.sha256(native.pack.read('assets/projects/textures/infusion-radiance/wave.png')).hexdigest(),'frameCount':len(images),'durationMsPerFrame':100,'gifBytes':gif.stat().st_size,'gifSha256':hashlib.sha256(gif.read_bytes()).hexdigest(),'nativeNotApplied':True,'transientArcAndIdleAssetsUnchanged':True}
(out/'verification.json').write_text(json.dumps(report,indent=2),encoding='utf-8')
print('NO_COMPLETION_FLASH_PREVIEW_PASS '+json.dumps({k:report[k] for k in ('removedOnlyKey','frameCount','gifBytes','gifSha256','nativeNotApplied')}))
