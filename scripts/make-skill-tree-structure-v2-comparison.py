"""Compare actual verified V1/V2 canvas renders, preserving both originals."""
import base64,io,json,re
from pathlib import Path
from PIL import Image,ImageDraw,ImageFont
ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'assets/core-ui/large-tree-preview/proposals/workshop-structure-v2'
qa=json.loads((OUT/'browser-verification.json').read_text(encoding='utf-8'))
html=(OUT/'ProjectS_Passive_Structure_V2.html').read_text(encoding='utf-8')
match=re.search(r'font-family:ps-sans;[^}]*?base64,([A-Za-z0-9+/=]+)',html)
font=lambda size:ImageFont.truetype(io.BytesIO(base64.b64decode(match[1])),size) if match else ImageFont.truetype('C:/Windows/Fonts/YuGothM.ttc',size)
panels=[]
for name in ['ProjectS_Structure_V2_Previous_Overview','ProjectS_Structure_V2_Overview']:
    f=next(f for f in qa['frames'] if f['name']==name);r=f['rect']
    im=Image.open(OUT/(name+'.png')).convert('RGB')
    panels.append(im.crop((round(r['x']),round(r['y']),round(r['x']+r['w']),round(r['y']+r['h']))))
width=sum(p.width for p in panels)+72;height=max(p.height for p in panels)+245
im=Image.new('RGB',(width,height),'#101813');d=ImageDraw.Draw(im)
d.text((24,18),'ProjectS / 領域と分岐の構造比較',font=font(32),fill='#e4ce9e')
labels=['前案 b944e28c / 645点・47能力群・849接続','改善案2 / 同じ645点・47能力群・741接続']
x=24
for p,label in zip(panels,labels):
    d.text((x,69),label,font=font(24),fill='#cbb98b');im.paste(p,(x,110));d.rectangle((x,110,x+p.width-1,110+p.height-1),outline='#6c6746',width=2);x+=p.width+24
costs=json.loads((OUT/'cost-comparison.json').read_text(encoding='utf-8'))
audit=json.loads((OUT/'layout-verification.json').read_text(encoding='utf-8'))
previous=json.loads((OUT.parent/'workshop-structure-v1/layout-verification.json').read_text(encoding='utf-8'))
d.text((24,height-104),f'非接続線の交差：{previous["nonVertexCrossingsMarkedWithGaps"]} → {audit["nonVertexCrossingsMarkedWithGaps"]}か所（切れ目で区別） / 全645点の左右対称と効果を保持 / 全体の面積：前案比 +{(costs["areaRatioToPrevious"]-1)*100:.1f}%',font=font(21),fill='#a9b59e')
row=next(r for r in costs['twoKeyExactSteinerCosts'] if r['origin']=='warrior' and r['keys']==['key_01','key_05'])
d.text((24,height-68),f'戦士・守りの循環＋不動の核：原版 {row["original"]}pt / 前案 {row["previous"]}pt / 改善案2 {row["proposal"]}pt',font=font(24),fill='#ddc796')
d.text((24,height-34),'構造と入力条件の比較用プレビュー。実ゲームへの採用、実戦効果、本番のポイント予算は未確定。',font=font(18),fill='#a9b59e')
im.save(OUT/'ProjectS_Structure_V2_Comparison.png')
print(json.dumps({'width':width,'height':height,'file':'ProjectS_Structure_V2_Comparison.png'}))
