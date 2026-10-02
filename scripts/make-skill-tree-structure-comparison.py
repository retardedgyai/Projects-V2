"""Compose the two verified UI renders; do not edit the supplied reference."""
import base64,io,json,re
from pathlib import Path
from PIL import Image,ImageDraw,ImageFont
ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'assets/core-ui/large-tree-preview/proposals/workshop-structure-v1'
qa=json.loads((OUT/'browser-verification.json').read_text(encoding='utf-8'))
html=(OUT/'ProjectS_Passive_Structure_Comparison.html').read_text(encoding='utf-8')
match=re.search(r"font-family:ps-sans;[^}]*?base64,([A-Za-z0-9+/=]+)",html)
font=lambda size:ImageFont.truetype(io.BytesIO(base64.b64decode(match[1])),size) if match else ImageFont.truetype('C:/Windows/Fonts/YuGothM.ttc',size)
panels=[]
for suffix in ['Original','Proposal']:
 f=next(f for f in qa['frames'] if f['name']==f'ProjectS_Structure_{suffix}_Overview');r=f['rect']
 im=Image.open(OUT/(f['name']+'.png')).convert('RGB');panels.append(im.crop((round(r['x']),round(r['y']),round(r['x']+r['w']),round(r['y']+r['h']))))
width=sum(p.width for p in panels)+72;height=max(p.height for p in panels)+172
im=Image.new('RGB',(width,height),'#101813');d=ImageDraw.Draw(im)
d.text((24,18),'ProjectS / 全体構造の比較',font=font(32),fill='#e4ce9e')
labels=['原版 / 645点・47領域・952接続','構造案 / 同じ645点・47領域・849接続']
x=24
for p,label in zip(panels,labels):
 d.text((x,69),label,font=font(24),fill='#cbb98b');im.paste(p,(x,110));d.rectangle((x,110,x+p.width-1,110+p.height-1),outline='#6c6746',width=2);x+=p.width+24
d.text((24,height-44),'各図は全体が入る倍率。構造案は採用前の比較用。原版・効果は保持し、配置と接続だけを別データで変更。',font=font(19),fill='#a9b59e')
im.save(OUT/'ProjectS_Structure_Old_New_Comparison.png')
print(json.dumps({'width':width,'height':height,'file':'ProjectS_Structure_Old_New_Comparison.png'}))
