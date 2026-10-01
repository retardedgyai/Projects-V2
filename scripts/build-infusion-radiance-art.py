"""Original hand/code pixel effects, four tiny native RGBA sprites. No model repaint, ImageGen or baked scene Bloom."""
from pathlib import Path
import math,json,hashlib
from PIL import Image,ImageDraw,ImageFont
r=Path(__file__).resolve().parent.parent;out=r/'.tools/world-model-preview';pack=r/'server-minestom/src/main/resources/core-ui-pack';lab=r/'assets/model-lab/infusion-radiance'
proof=json.loads((out/'charge-protected-files.json').read_text())['protected']
for p in out.glob('ProjectS-Infusion-Charge-*'):
    if p.suffix in ['.gif','.png']:proof[p.relative_to(r).as_posix()]=hashlib.sha256(p.read_bytes()).hexdigest()
sprites={}
halo=Image.new('RGBA',(32,32))
for y in range(32):
    for x in range(32):
        dx,dy=x-15.5,y-15.5;d=math.hypot(dx,dy)
        if d<8.6 or d>15.8:continue
        band=max(0,1-abs(d-12.2)/3.7);grain=.88+.12*((x*7+y*13)%5)/4
        a=round(195*band*grain)
        if a:halo.putpixel((x,y),(240,240,240,a))
sprites['halo']=halo
column=Image.new('RGBA',(16,32))
for y in range(32):
    axis=7.5+.65*math.sin(y*.6)
    for x in range(16):
        d=abs(x-axis);alpha=round(235*max(0,1-d/5.8))
        if abs(d-2.5)<.7:alpha=round(alpha*(.7+.3*((x+y)%3)/2))
        if alpha:column.putpixel((x,y),(255,255,255,alpha))
sprites['column']=column
wave=Image.new('RGBA',(16,16));burst=Image.new('RGBA',(16,16))
for y in range(16):
    for x in range(16):
        dx,dy=x-7.5,y-7.5;d=math.hypot(dx,dy)
        if 4.8<=d<=7.9:
            a=round(235*max(.12,1-abs(d-6.3)/1.6));wave.putpixel((x,y),(255,255,255,a))
        if max(abs(dx),abs(dy))<=3.5:continue # Transparent center retains the item silhouette.
        axis=min(abs(dx),abs(dy));diag=abs(abs(dx)-abs(dy))
        a=0
        if axis<1 and max(abs(dx),abs(dy))<7.4:a=245
        elif diag<.9 and d<9.4:a=210
        elif axis<2 and max(abs(dx),abs(dy))<6.4:a=95
        if a:burst.putpixel((x,y),(255,255,255,a))
sprites['wave']=wave;sprites['burst']=burst
for name,im in sprites.items():
    texture=f'infusion-radiance/{name}'
    faces={'up':{'texture':'#fx','uv':[0,0,16,16],'tintindex':0}} if name=='wave' else {f:{'texture':'#fx','uv':[0,0,16,16],'tintindex':0} for f in ['north','south']}
    geometry={'from':[0,8,0] if name=='wave' else [0,0,8],'to':[16,8,16] if name=='wave' else [16,16,8],
              'light_emission':15,'shade':False,'faces':faces}
    model={'credit':'ProjectS original local ritual radiance sprite; no shader or native model repaint','ambientocclusion':False,
           'textures':{'fx':{'sprite':'projects:'+texture,'force_translucent':True},'particle':'projects:'+texture},'elements':[geometry]}
    item={'model':{'type':'minecraft:model','model':'projects:'+texture,'tints':[{'type':'minecraft:custom_model_data','index':0,'default':16777215}]}}
    for dest in [pack,lab]:
        for kind,value in [('textures',im),('models',model),('items',item)]:
            p=dest/('assets/projects/'+kind+'/'+texture+('.png' if kind=='textures' else '.json'))
            p.parent.mkdir(parents=True,exist_ok=True)
            if kind=='textures':value.save(p)
            else:p.write_text(json.dumps(value,indent=2)+'\n',encoding='utf8')
for p,h in proof.items():assert hashlib.sha256((r/p).read_bytes()).hexdigest()==h,p
(out/'radiance-protected-files.json').write_text(json.dumps({'protected':proof},indent=2)+'\n',encoding='utf8')
sheet=Image.new('RGB',(1000,360),'#191c20');d=ImageDraw.Draw(sheet)
font=ImageFont.truetype(r'C:\Windows\Fonts\meiryo.ttc',18)
for i,(name,im) in enumerate(sprites.items()):
    tile=Image.new('RGBA',im.size,(39,35,45,255));violet=im.copy()
    for y in range(im.height):
        for x in range(im.width):
            R,G,B,A=violet.getpixel((x,y));violet.putpixel((x,y),(round(R*.8),round(G*.48),B,A))
    tile.alpha_composite(violet);big=tile.convert('RGB').resize((192,192 if im.height==im.width else 288),Image.Resampling.NEAREST)
    sheet.paste(big,(i*250+18,46));d.text((i*250+18,10),name+' / '+str(im.size),font=font,fill='#dddbc9')
sheet.save(out/'ProjectS-Infusion-Radiance-Pixels.png')
print(json.dumps({'sprites':len(sprites),'protectedFiles':len(proof),'nativeFullbright':True,'bloom':False,'staticModelsChanged':False}))
