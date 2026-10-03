"""Assemble evidence from actual screenshots, without altering tree pixels."""
import hashlib,json
from pathlib import Path
from PIL import Image,ImageDraw,ImageFont
ROOT=Path(__file__).resolve().parents[1];OUT=ROOT/'assets/core-ui/large-tree-preview/proposals/workshop-contract-v8'
FONT=Path('C:/Windows/Fonts/meiryo.ttc');BG='#132319';GOLD='#d9bd7e';FG='#d8d8b7';sources=[]
def label(img,text,x,y,size=26,color=FG):ImageDraw.Draw(img).text((x,y),text,font=ImageFont.truetype(str(FONT),size),fill=color)
def paste(img,name,x,y,width):
 p=OUT/(name+'.png');s=Image.open(p).convert('RGB');height=round(s.height*width/s.width);img.paste(s.resize((width,height),Image.Resampling.LANCZOS),(x,y));sources.append(dict(file=p.name,sha256=hashlib.sha256(p.read_bytes()).hexdigest(),originalSize=list(s.size),placedAt=[x,y,width,height]));return height
width=2400;margin=24;gap=24;cell=(width-2*margin-gap)//2;cell_h=round(1124*cell/1570);row_h=cell_h+90
comparison=Image.new('RGB',(width,170+row_h*4+70),BG)
label(comparison,'ProjectS V8 — 必須の無効投資を避ける',24,18,38,GOLD)
label(comparison,'保存済みV7 / 比較前',24,83,27)
label(comparison,'V8候補 / 実戦・ゲーム保存への反映は未実施',24+cell+gap,83,27)
rows=[('Whole','全体：中央60点・中央接続を保持 / 追加は障壁への有料通路2点'),('Blood','血の代価：20pt・無効11点 → 30pt・無効0点 / 任意の既得MPは保持'),('Shield','障壁：MP専用の外周入口 → 一般経路＋HP・装甲の入口'),('Pen','静かな刃の後の貫通：会心だけの点を経由 → 物理が残る共通目標から')]
for row,(region,title) in enumerate(rows):
 y=170+row*row_h;label(comparison,title,24,y,25,GOLD)
 for col,version in enumerate(['Before','After']):paste(comparison,'ProjectS_Contract_V8_'+version+'_'+region,margin+col*(cell+gap),y+52,cell)
label(comparison,'実ブラウザ画像。各左右の中心・倍率は同一。費用は接続と追加値の比較で、実戦バランスの証明ではありません。',24,comparison.height-52,23)
comparison.save(OUT/'ProjectS_Contract_V8_Before_After.png')
overview=Image.new('RGB',(1968,5200),BG)
label(overview,'ProjectS V8 — 入口と、同じ消費で選ぶ育成',24,16,38,GOLD)
label(overview,'789点 / 62地域 / 14 Key候補＋1 Notable候補 / 全効果は実戦未反映',24,70,26)
paste(overview,'ProjectS_Contract_V8_Overview',24,116,1920)
label(overview,'同じ編成・48pt上限・43pt使用：手数と範囲 / 会心と攻撃速度',24,1330,29,GOLD)
paste(overview,'ProjectS_Contract_V8_Pair_assassin-equal-area-43',24,1385,948)
paste(overview,'ProjectS_Contract_V8_Pair_assassin-equal-crit-43',996,1385,948)
label(overview,'上限別・未消費ありの例：64ptは49 / 51pt、80ptは55 / 60pt。80pt固有の解放例ではない。',24,1990,24)
label(overview,'同じ43pt使用：防御再投資と、資源・効率も拾う旧経路',24,2050,29,GOLD)
paste(overview,'ProjectS_Contract_V8_Pair_tank-life-reinvest-43',24,2105,948)
paste(overview,'ProjectS_Contract_V8_Pair_legacy-tank-life',996,2105,948)
label(overview,'再投資側：HP＋34・装甲＋36 / 資源−22・効率−8・魔法防御−3。常に優れるとは扱わない。',24,2720,24)
label(overview,'血の代価と障壁：新規の無効投資0点 / 46pt',24,2780,28,GOLD)
paste(overview,'ProjectS_Contract_V8_mage-blood-shield',24,2835,1920)
label(overview,'守りの分配：数値交換なのでNotable候補。分類・3pt仮費用・倍率の採用は未決。',24,4055,26,GOLD)
paste(overview,'ProjectS_Contract_V8_Contract_key_11',24,4110,1500)
label(overview,'実ブラウザの画像を組み合わせた記録 / 承認済み字体・絵柄 / 戦闘・packet・ゲーム保存は変更なし',24,5144,22)
overview.save(OUT/'ProjectS_Contract_V8_Overview_And_Choices.png')
(OUT/'composition-sources.json').write_text(json.dumps(dict(status='COMPOSED_FROM_ACTUAL_BROWSER',sources=sources),ensure_ascii=False,indent=2),encoding='utf8')
print(json.dumps({p.name:dict(bytes=p.stat().st_size,size=Image.open(p).size) for p in [OUT/'ProjectS_Contract_V8_Before_After.png',OUT/'ProjectS_Contract_V8_Overview_And_Choices.png']}))
