"""Same Kotlin gameplay sampler, actual model assets and vanilla Dust sprites; offline preview only."""
import json, hashlib, io
from pathlib import Path
import numpy as np
from PIL import Image,ImageDraw
import importlib.util
spec=importlib.util.spec_from_file_location('models',Path(__file__).with_name('render-world-models.py'))
m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)
trace_path=m.ROOT/'server-minestom/.tools/world-infusion-evidence/smoke-preview-trace.json'
trace=json.loads(trace_path.read_text());OUT=m.OUT
sprite=np.array(Image.open(m.PACK/'assets/projects/textures/infusion/smoke.png').convert('RGBA'))
def parts(amounts):
    result=m.boxes('projects:infusion/pedestal',(.5,.5,.5))+m.boxes('projects:infusion/matrix',(.5,3.5,.5))
    for x in [-1,1]:
        for z in [-1,1]:
            for y in [0,1]:result+=m.boxes('minecraft:block/polished_deepslate_wall_post',(x,y,z),centered=False)
            result+=m.boxes('minecraft:block/cut_copper',(x,2,z),centered=False)
    for x,z in [(3,0),(0,3),(-3,0),(0,-3)]:result+=m.boxes('projects:infusion/pedestal',(x+.5,.5,z+.5))
    for x,kind,color,n in zip([-4,4],['ember','tide'],['orange','cyan'],amounts):
        result+=m.boxes('projects:infusion/jar_'+kind,(x+.5,.5,-2.5))
        result+=m.boxes('minecraft:block/'+color+'_stained_glass',(x+.32,.13,-2.68),(.36,.5*n/4,.36),False)
    return result
cache={};frames=[];contact=[]
for index,f in enumerate(trace['frames']):
    key=tuple(f['amounts'])
    if key not in cache:
        cache[key]=m.render(parts(key),(960,590),38,24,with_depth=True)
        print('Rendered liquid state',key,flush=True)
    base,depth,project,factor=cache[key];canvas=base.copy().convert('RGBA')
    projected=[]
    for x,y,z,rgb,size,u in f['samples']:
        p,d=project((x,y,z));projected.append((d,p,rgb,size,u))
    for d,p,rgb,size,u in sorted(projected,key=lambda a:a[0]):
        pixels=sprite.copy().astype(float)
        tint=np.array([(rgb>>16)&255,(rgb>>8)&255,rgb&255])
        pixels[:,:,:3]*=tint[None,None,:]/255
        # Exact custom billboard PNG alpha and same server tint/scale; no unrelated glow layer.
        stamp=Image.fromarray(pixels.astype('uint8'),'RGBA')
        radius=max(2,int(factor*.13*size));stamp=stamp.resize((radius*2,radius*2),Image.Resampling.NEAREST)
        left,top=int(p[0])-radius,int(p[1])-radius
        x0,y0=max(0,left),max(0,top);x1,y1=min(960,left+radius*2),min(590,top+radius*2)
        if x1<=x0 or y1<=y0:continue
        clipped=np.array(stamp.crop((x0-left,y0-top,x1-left,y1-top)))
        clipped[:,:,3]=np.where(depth[y0:y1,x0:x1]>d,0,clipped[:,:,3])
        canvas.alpha_composite(Image.fromarray(clipped,'RGBA'),(x0,y0))
    frame=Image.new('RGB',(960,700),'#1b201b');frame.paste(canvas.convert('RGB'),(0,56))
    state='供給停止：残った煙だけが到達' if f['paused'] else 'Jar実消費 → 元素色の煙 → Matrix'
    if not f['samples'] and f['paused']:state='放出・移動が終了 / 追加消費なし'
    m.text(frame,(20,10),state,23)
    m.text(frame,(20,614),f"火 {key[0]}/4   潮 {key[1]}/4      {f['tick']/20:.1f}s",20)
    m.text(frame,(20,647),'不採用の旧モデルを仮置き / 今回は霧の動き比較',17)
    m.text(frame,(20,674),'実PNGの煙を合成。透過の重なりは仮描画 / ゲーム録画ではありません。',15)
    frames.append(frame)
    if f['tick'] in [20,70,100,150]:contact.append(frame)
palette=Image.new('RGB',(960*3,700))
for i,n in enumerate([10,35,50]):palette.paste(frames[n],(960*i,0))
palette=palette.quantize(colors=128)
indexed=[f.quantize(palette=palette,dither=Image.Dither.NONE) for f in frames]
gif=OUT/'ProjectS-Essentia-Smoke-Preview.gif'
indexed[0].save(gif,save_all=True,append_images=indexed[1:],duration=100,loop=0,disposal=2,optimize=True)
sheet=Image.new('RGB',(1920,1400),'#1b201b')
for i,f in enumerate(contact):sheet.paste(f,((i%2)*960,(i//2)*700))
sheet.save(OUT/'ProjectS-Essentia-Smoke-States.png')
assert gif.stat().st_size<10*1024*1024
record={'preview':'offline orthographic animation','frames':len(frames),'fps':10,'bytes':gif.stat().st_size,
 'sameGameKernel':'WorldInfusionSmoke.kt','traceSHA256':hashlib.sha256(trace_path.read_bytes()).hexdigest(),
 'sprites':'ProjectS original 16px smoke.png; server billboard ItemDisplay, custom-model tint, same .26 scale','modelEdits':False,'minecraftCapture':False,
 'limits':'Geometry/tint/scale/PNG alpha are shared; depth sorting, lighting and temporal interpolation are offline approximations. Vanilla client remains unverified.',
 'fixture':'Real WorldInfusionRules ticks: ember 3, tide 2 consumed; wind Jar withdrawn after start causes stop at tick 90. Only two supplying Jars shown.'}
(OUT/'smoke-preview-verification.json').write_text(json.dumps(record,indent=2),encoding='utf8')
print(json.dumps(record,indent=2))
