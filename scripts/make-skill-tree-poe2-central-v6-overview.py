"""Publish actual whole-tree and same-zoom outer-region comparisons."""
from pathlib import Path
import json
from PIL import Image,ImageDraw,ImageFont
ROOT=Path(__file__).resolve().parents[1];OUT=ROOT/'assets/core-ui/large-tree-preview/proposals/workshop-poe2-central-v6'
details=json.loads((OUT/'detail-captures.json').read_text(encoding='utf8'));assert len({x['cam']['z'] for x in details['details']})==1
W,H=3300,2480;im=Image.new('RGB',(W,H),'#1a291f');d=ImageDraw.Draw(im);font=lambda n:ImageFont.truetype('C:/Windows/Fonts/YuGothM.ttc',n)
def text(x,y,s,n=28,color='#e8d2a0'):d.text((x,y),s,font=font(n),fill=color)
def panel(name,x,y,w,h):
 p=Image.open(OUT/name).convert('RGB');scale=min(w/p.width,h/p.height);p=p.resize((round(p.width*scale),round(p.height*scale)),Image.Resampling.LANCZOS);ox=x+(w-p.width)//2;oy=y+(h-p.height)//2;im.paste(p,(ox,oy));d.rectangle((x,y,x+w-1,y+h-1),outline='#839570',width=2)
text(50,28,'全体と外側の道 / 645点の配置',46)
text(50,96,'中央は規則的に開始し、育つ先は大きさ・枝数・戻り道の違う47領域へ。',28)
text(50,165,'実参照 / PoE2 0.5.1',30);text(1690,165,'ProjectS V6 / 645点・47領域',30)
panel('Reference_PoE2_Full_0.5.1.png',50,220,1560,1120);panel('ProjectS_PoE2_V6_Overview_Canvas.png',1690,220,1560,1120)
text(50,1390,'外側の3領域 / 同じ表示倍率で拡大',34)
text(50,1450,'下の3枚は同じ画面寸法・同じ倍率。四角いセルや同じ扇形へ揃えていない。',26)
for k,(id,title,note) in enumerate([('g12','魔力 / 4点の小さな選択','小さな分岐を地域通路へつなぐ'),('g34','範囲 / 10点・2つの目標','複数の目標と、末端の任意Key'),('g30','攻撃速度 / 8点・2つの目標','別の地域へ曲がりながらつながる')]):
 x=50+k*1100;text(x,1530,title,28);panel('ProjectS_PoE2_V6_Region_'+id+'.png',x,1585,1040,745);text(x,2355,note,25,'#c3d1b3')
text(50,2420,'参照: cvenzin.github.io/poe2-skilltree/ · GGG公式PoE2データ。ProjectSは配置・操作の試作、native実装は未反映。',23,'#aebeaa')
im.save(OUT/'ProjectS_PoE2_Central_V6_Whole_And_Regions.png');print(json.dumps({'width':W,'height':H,'bytes':(OUT/'ProjectS_PoE2_Central_V6_Whole_And_Regions.png').stat().st_size}))
