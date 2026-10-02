"""Real browser frames comparing critical investment with the no-critical goal."""
import json
from pathlib import Path
from PIL import Image,ImageDraw,ImageFont
ROOT=Path(__file__).resolve().parents[1];OUT=ROOT/'assets/core-ui/large-tree-preview/proposals/workshop-goal-study-v3';qa=json.loads((OUT/'browser-verification.json').read_text(encoding='utf-8'));panels=[]
for id in ['critical','quiet']:
 f=next(f for f in qa['frames'] if f['name']=='ProjectS_Goal_Study_V3_Sector_'+id);r=f['rect'];p=Image.open(OUT/(f['name']+'.png')).convert('RGB');panels.append(p.crop((round(r['x']),round(r['y']),round(r['x']+r['w']),round(r['y']+r['h']))))
w=sum(p.width for p in panels)+72;h=max(p.height for p in panels)+320;im=Image.new('RGB',(w,h),'#141b16');d=ImageDraw.Draw(im);font=lambda n:ImageFont.truetype('C:/Windows/Fonts/YuGothM.ttc',n)
d.text((24,15),'ProjectS / 会心へ投資するか、会心を捨てて一撃へ向かうか',font=font(33),fill='#e1c58a');d.text((24,64),'守り・吸収・武器圏に4領域を接続 / 詳細197点・17領域・225接続。旧案と原645効果を保持。',font=font(22),fill='#aebe9f');x=24
for p,title,notes in zip(panels,['会心・弱点のNotableへ / 20pt・残り28pt','貫通から静かな刃へ / 16pt・残り32pt'],[['倍率へ短く届く腕と、会心率を積んで回る腕。','弱点入力は持たないため、混合Notableの会心側だけ有効。'],['貫通を先に拾う上の道と、物理を拾う下の道。','会心を無効にする代償。原案の直接ヒット22%乗算増は実戦未反映。']]):
 d.text((x,107),title,font=font(28),fill='#dec18c');im.paste(p,(x,150));d.rectangle((x,150,x+p.width-1,150+p.height-1),outline='#697650',width=2)
 for j,text in enumerate(notes):d.text((x,h-142+j*31),text,font=font(21),fill='#d5c396')
 x+=p.width+24
d.text((24,h-66),'同じ48pt・同じ入力・同じ配置。金が取得経路。入力不足を勝手に補わず、欲しい目標と拾う恩恵で道を変える。',font=font(23),fill='#dec18c');d.text((24,h-31),'全47領域・5職業の入口は別の全体配置計画に保持。全645点の詳細配線・native反映・実戦の強さは未完。',font=font(20),fill='#a5b29a');im.save(OUT/'ProjectS_Goal_Routes_V3_Comparison.png');print(json.dumps({'width':w,'height':h}))
