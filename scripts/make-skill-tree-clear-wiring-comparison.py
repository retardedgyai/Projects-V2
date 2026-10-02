"""Compare preserved V2 and actual V3 canvas captures without editing either."""
import json
from pathlib import Path
from PIL import Image,ImageDraw,ImageFont
ROOT=Path(__file__).resolve().parents[1]
BASE=ROOT/'assets/core-ui/large-tree-preview/proposals'
OUT=BASE/'workshop-clear-wiring-v3'
def font(size):
    # Figure captions use the full Japanese font; the preserved UI subset lacks new caption glyphs.
    return ImageFont.truetype('C:/Windows/Fonts/YuGothM.ttc',size)
panels=[]
for folder,name in [('workshop-structure-v2','ProjectS_Structure_V2_Overview'),('workshop-clear-wiring-v3','ProjectS_Clear_Wiring_V3_Overview')]:
    qa=json.loads((BASE/folder/'browser-verification.json').read_text(encoding='utf-8'))
    r=next(f['rect'] for f in qa['frames'] if f['name']==name)
    image=Image.open(BASE/folder/(name+'.png')).convert('RGB')
    panels.append(image.crop((round(r['x']),round(r['y']),round(r['x']+r['w']),round(r['y']+r['h']))))
width=sum(p.width for p in panels)+72;height=max(p.height for p in panels)+245
image=Image.new('RGB',(width,height),'#101813');draw=ImageDraw.Draw(image)
draw.text((24,18),'ProjectS / ノードを跨がない配線の比較',font=font(32),fill='#e4ce9e')
x=24
for panel,label in zip(panels,['保存済み前案 a342d87c / 645点・741接続','配線案3 / 同じ645点・708接続']):
    draw.text((x,69),label,font=font(24),fill='#cbb98b');image.paste(panel,(x,110));draw.rectangle((x,110,x+panel.width-1,110+panel.height-1),outline='#6c6746',width=2);x+=panel.width+24
costs=json.loads((OUT/'cost-comparison.json').read_text(encoding='utf-8'))
previous=json.loads((BASE/'workshop-structure-v2/cost-comparison.json').read_text(encoding='utf-8'))
area_ratio=costs['newWorldArea']/previous['newWorldArea']
draw.text((24,height-104),'線の交差0・非接続ノードへの侵入0・文字との重なり0 / 断線・点線橋なし / 103表示状態を検査',font=font(21),fill='#a9b59e')
draw.text((24,height-68),f'視認性のための余白：面積は前案の約{area_ratio:.2f}倍 / 元の効果と左右対称645点を保持',font=font(23),fill='#ddc796')
draw.text((24,height-34),'採用前の実データUI案。戦士の2Key経路は入力条件の問題が残るため、次に共通接続路を修正。',font=font(18),fill='#a9b59e')
image.save(OUT/'ProjectS_Clear_Wiring_V3_Comparison.png')
print(json.dumps({'width':width,'height':height,'file':'ProjectS_Clear_Wiring_V3_Comparison.png'}))
