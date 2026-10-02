"""Compare two currently usable goals on the same real V2 canvas."""
import json
from pathlib import Path
from PIL import Image,ImageDraw,ImageFont
ROOT=Path(__file__).resolve().parents[1];OUT=ROOT/'assets/core-ui/large-tree-preview/proposals/workshop-goal-study-v2'
qa=json.loads((OUT/'browser-verification.json').read_text(encoding='utf-8'));examples=json.loads((OUT/'route-examples.json').read_text(encoding='utf-8'));panels=[]
for id in ['area','single']:
    f=next(f for f in qa['frames'] if f['name']=='ProjectS_Goal_Study_V2_'+id);r=f['rect'];p=Image.open(OUT/(f['name']+'.png')).convert('RGB');panels.append(p.crop((round(r['x']),round(r['y']),round(r['x']+r['w']),round(r['y']+r['h']))))
w=sum(p.width for p in panels)+72;h=max(p.height for p in panels)+285;im=Image.new('RGB',(w,h),'#141b16');draw=ImageDraw.Draw(im);font=lambda s:ImageFont.truetype('C:/Windows/Fonts/YuGothM.ttc',s)
draw.text((24,15),'ProjectS / 欲しい戦い方から、伸ばす道を選ぶ',font=font(33),fill='#e1c58a');draw.text((24,64),'再開後の別版 / 153点・13領域・173接続。原645効果を保持。全体の新配置は未完成。',font=font(22),fill='#aebe9f')
x=24
for p,id,title in zip(panels,['area','single'],['近接の広さを選ぶ / 24pt・残り24pt','連撃を一撃へ集約 / 16pt・残り32pt']):
    draw.text((x,107),title,font=font(28),fill='#dec18c');im.paste(p,(x,150));draw.rectangle((x,150,x+p.width-1,150+p.height-1),outline='#697650',width=2)
    text='範囲半径と一撃の交換。範囲Notableへ回る道。' if id=='area' else '原案のKey08。命中機会・手数を失う代償。'
    draw.text((x,h-110),text,font=font(22),fill='#d5c396');x+=p.width+24
draw.text((24,h-66),'取得経路は金。目標を狙うと候補経路と費用を表示。守り・吸収・資源への回り方を残す。',font=font(24),fill='#dec18c')
draw.text((24,h-30),'出血源は現行catalogになく、裂傷Keyは取得不可。係数・実戦効果・本番採用は未検証。既存案はすべて保持。',font=font(19),fill='#a5b29a')
im.save(OUT/'ProjectS_Goal_Routes_V2_Comparison.png');print(json.dumps({'width':w,'height':h}))
