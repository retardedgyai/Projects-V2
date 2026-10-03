"""Compose retained actual-browser frames; never redraw either tree."""
from pathlib import Path
import json,shutil
from PIL import Image,ImageDraw,ImageFont
ROOT=Path(__file__).resolve().parents[1];OUT=ROOT/'assets/core-ui/large-tree-preview/proposals/workshop-poe2-central-v6';CAP=ROOT/'.tools/middle-band-review-20261003'
before=json.loads((CAP/'before-frames.json').read_text(encoding='utf8'));after=json.loads((CAP/'after-frames.json').read_text(encoding='utf8'))
for a,b in zip(before,after):assert a['name']==b['name'] and a['cam']==b['cam'] and a['rect']==b['rect']
W,H=3300,3930;im=Image.new('RGB',(W,H),'#1a291f');d=ImageDraw.Draw(im);font=lambda n:ImageFont.truetype('C:/Windows/Fonts/YuGothM.ttc',n)
def text(x,y,s,n=28,c='#e8d2a0'):d.text((x,y),s,font=font(n),fill=c)
text(50,26,'V6 西・南西の中間帯 / 限定修正の実画面比較',46)
text(50,94,'中央5起点・短い2出口、47地域内の形・接続、645点の効果・費用・前提を保持。',27)
text(50,154,'修正前 / 保存済み V6',30);text(1690,154,'修正後 / 同じ V6・同じカメラ',30)
items=[]
for row,(name,title) in enumerate([('Whole','全体 / 同じ倍率 0.06674'),('West','西の中間帯 / 同じ倍率 0.20'),('Southwest','南西〜真下 / 同じ倍率 0.20')]):
 y=218+row*1220;text(50,y,title,30)
 for col,kind in enumerate(['before','after']):
  src=CAP/(kind+'-'+name+'.png');dest=OUT/('ProjectS_PoE2_V6_Middle_'+kind+'_'+name+'.png');shutil.copyfile(src,dest);p=Image.open(src).convert('RGB');scale=1560/p.width;p=p.resize((1560,round(p.height*scale)),Image.Resampling.LANCZOS);x=50+col*1640;im.paste(p,(x,y+58));d.rectangle((x,y+58,x+p.width-1,y+58+p.height-1),outline='#839570',width=2);items.append({'file':dest.name,'camera':next(f['cam'] for f in before if f['name']==name),'beforeOrAfter':kind})
text(50,3880,'実ブラウザのcanvas範囲を同条件で撮影。通常点を大きく・明るく表示。nativeゲームは未反映。',24,'#becbae')
im.save(OUT/'ProjectS_PoE2_V6_Middle_Band_Before_After.png');(OUT/'middle-band-captures.json').write_text(json.dumps({'status':'ACTUAL_BROWSER_SAME_CAMERAS','viewport':[1920,1200],'profile':'warrior:support:plain:standard','overlayHiddenForComparison':True,'items':items},ensure_ascii=False,indent=2),encoding='utf8');print(json.dumps({'width':W,'height':H,'frames':len(items)}))
