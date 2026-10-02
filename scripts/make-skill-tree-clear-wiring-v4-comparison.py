"""Compare preserved V3 with the compact, usable V4 using actual captures."""
import json
from pathlib import Path
from PIL import Image,ImageDraw,ImageFont
ROOT=Path(__file__).resolve().parents[1];BASE=ROOT/'assets/core-ui/large-tree-preview/proposals';OUT=BASE/'workshop-clear-wiring-v4'
def font(size):return ImageFont.truetype('C:/Windows/Fonts/YuGothM.ttc',size)
panels=[]
for folder,name in [('workshop-clear-wiring-v3','ProjectS_Clear_Wiring_V3_Overview'),('workshop-clear-wiring-v4','ProjectS_Clear_Wiring_V4_Overview')]:
    qa=json.loads((BASE/folder/'browser-verification.json').read_text(encoding='utf-8'));r=next(f['rect'] for f in qa['frames'] if f['name']==name)
    panel=Image.open(BASE/folder/(name+'.png')).convert('RGB');panels.append(panel.crop((round(r['x']),round(r['y']),round(r['x']+r['w']),round(r['y']+r['h']))))
width=sum(p.width for p in panels)+72;height=max(p.height for p in panels)+245
image=Image.new('RGB',(width,height),'#101813');draw=ImageDraw.Draw(image)
draw.text((24,18),'ProjectS / 共通路の条件を直し、中央と導線を詰めた配線',font=font(31),fill='#e4ce9e')
x=24
for panel,label in zip(panels,['保存済み配線案3 eeb913aa / 645点・708接続','配線案4 / 同じ645効果・708接続']):
    draw.text((x,69),label,font=font(24),fill='#cbb98b');image.paste(panel,(x,110));draw.rectangle((x,110,x+panel.width-1,110+panel.height-1),outline='#6c6746',width=2);x+=panel.width+24
cost=json.loads((OUT/'cost-comparison.json').read_text(encoding='utf-8'));old=json.loads((BASE/'workshop-clear-wiring-v3/cost-comparison.json').read_text(encoding='utf-8'));ratio=cost['newWorldArea']/old['newWorldArea']
draw.text((24,height-104),f'面積：約{(1-ratio)*100:.1f}%減 / 非接続ノード侵入0・文字と線の重なり0・線交差0 / 断線・点線橋なし',font=font(21),fill='#a9b59e')
draw.text((24,height-68),'戦士2Key：取得不可 → 魔術入力なしで42pt / 48・64・80ptの3方針を実HTMLで取得確認',font=font(24),fill='#ddc796')
draw.text((24,height-34),'採用前の実データUI案。原版と全旧案を保持。実ゲーム採用・実戦balance・本番予算は未確認。',font=font(18),fill='#a9b59e')
image.save(OUT/'ProjectS_Clear_Wiring_V4_Comparison.png')
print(json.dumps({'width':width,'height':height,'areaRatioToV3':round(ratio,4),'file':'ProjectS_Clear_Wiring_V4_Comparison.png'}))
