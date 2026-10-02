"""Compose actual captures of two paths with unchanged source budget examples."""
import json
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont
ROOT=Path(__file__).resolve().parents[1];OUT=ROOT/'assets/core-ui/large-tree-preview/proposals/workshop-goal-study-v1'
report=json.loads((OUT/'browser-verification.json').read_text(encoding='utf-8'));examples=json.loads((OUT/'route-examples.json').read_text(encoding='utf-8'))
panels=[]
for id in ['direct','growth']:
    frame=next(f for f in report['frames'] if f['name']=='ProjectS_Goal_Study_'+id);r=frame['rect'];im=Image.open(OUT/(frame['name']+'.png')).convert('RGB');panels.append(im.crop((round(r['x']),round(r['y']),round(r['x']+r['w']),round(r['y']+r['h']))))
width=sum(p.width for p in panels)+72;height=max(p.height for p in panels)+336;im=Image.new('RGB',(width,height),'#141b16');draw=ImageDraw.Draw(im);font=lambda size:ImageFont.truetype('C:/Windows/Fonts/YuGothM.ttc',size)
draw.text((24,14),'ProjectS / 同じ目標へ、二つの回り方',font=font(34),fill='#e1c58a');draw.text((24,63),'反復する島形をやめ、目標と道中の恩恵から組んだ局所試作。109点・9領域 / 原645効果を保持。',font=font(22),fill='#aebaa3')
x=24
for panel,id,title in zip(panels,['direct','growth'],['Keyに先に届く / 29pt・残り19pt','生命・耐性・吸収を拾う / 42pt・残り6pt']):
    draw.text((x,105),title,font=font(29),fill='#dec18c');im.paste(panel,(x,153));draw.rectangle((x,153,x+panel.width-1,153+panel.height-1),outline='#697650',width=2)
    e=next(e for e in examples if e['id']==id);s=e['stats'];y=height-161
    draw.text((x,y),f"HP +{s.get('hp',0)}% / 装甲 +{s.get('armor',0)}% / 耐性 +{s.get('resist',0)}% / 障壁 +{s.get('barrier',0)}%",font=font(22),fill='#d5c396')
    draw.text((x,y+35),f"再生 +{s.get('regen',0)}% / パッシブ吸収 +{s.get('leech',0)}%",font=font(22),fill='#aebe9f');x+=panel.width+24
draw.text((24,height-72),'どちらも「不動の核」「守りの循環」。同じ48pt・所有済み吸収MOD・魔術入力なし。取得経路は金で表示。',font=font(24),fill='#e0c18a')
draw.text((24,height-33),'全645点の新配置・実ゲーム採用・実戦balanceは未完成。合計は仮値で、装備基礎値やKeyの乗算ペナルティは未合成。',font=font(19),fill='#a5b29a')
im.save(OUT/'ProjectS_Goal_Routes_Comparison.png');print(json.dumps({'width':width,'height':height,'file':'ProjectS_Goal_Routes_Comparison.png'}))
