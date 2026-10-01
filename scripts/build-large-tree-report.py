from pathlib import Path
import json
from fontTools.ttLib import TTFont
from fontTools import subset
from fontTools.varLib.instancer import instantiateVariableFont
from reportlab.pdfgen import canvas
from reportlab.lib.colors import HexColor
from reportlab.lib.pagesizes import A4,landscape
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont as RLFont
from reportlab.lib.utils import ImageReader
ROOT=Path(__file__).resolve().parents[1];OUT=ROOT/'assets/core-ui/large-tree-preview';PRIVATE=ROOT/'.tools/large-tree-preview'
d=json.loads((OUT/'graph.json').read_text(encoding='utf-8'));audit=d['budgetAudit'];keys=sorted((n for n in d['nodes'] if n['type']=='keystone'),key=lambda n:n['id'])
letters={ord(c) for c in Path(__file__).read_text(encoding='utf-8')+json.dumps(d,ensure_ascii=False) if ord(c)>=32};font=TTFont('C:/Windows/Fonts/NotoSansJP-VF.ttf');letters-= {8629,8630,8631,8981};assert not letters-set(font.getBestCmap())
options=subset.Options();sub=subset.Subsetter(options=options);sub.populate(unicodes=letters);sub.subset(font);font=instantiateVariableFont(font,{'wght':400},inplace=True);fontpath=PRIVATE/'report-jp.ttf';font.save(fontpath);pdfmetrics.registerFont(RLFont('JP',str(fontpath)))
W,H=landscape(A4);target=OUT/'ProjectS_LargeTree_Route_Review.pdf';c=canvas.Canvas(str(target),pagesize=(W,H));c.setTitle('ProjectS 共通成長樹・大盤面と経路比較');c.setAuthor('ProjectS design preview')
def text(x,y,s,size=10,color='#343b2e'):
 c.setFillColor(HexColor(color));c.setFont('JP',size);c.drawString(x,y,s)
def lines(x,y,s,width,size=10,leading=15):
 for paragraph in s.split('\n'):
  row=''
  for char in paragraph:
   if pdfmetrics.stringWidth(row+char,'JP',size)>width:text(x,y,row,size);y-=leading;row=''
   row+=char
  text(x,y,row,size);y-=leading
 return y
def page(title,sub):
 c.setFillColor(HexColor('#f4f0e4'));c.rect(0,0,W,H,fill=1,stroke=0);text(24,H-34,title,18);text(24,H-53,sub,10,'#697157')
def picture(name,x,y,maxw,maxh):
 im=ImageReader(str(OUT/name));iw,ih=im.getSize();scale=min(maxw/iw,maxh/ih);c.drawImage(im,x+(maxw-iw*scale)/2,y+(maxh-ih*scale)/2,width=iw*scale,height=ih*scale)
page('ProjectS 共通成長樹 — 大盤面の比較案','5起点 / 47クラスタ / Small・Notable・Keystoneを区別 / 全体表示・検索・仮取得・パンとズーム')
picture('ProjectS_LargeTree_Overview.png',24,65,W-48,H-132)
text(24,46,'旧v6の広がりを保持。序盤は3直線から分岐・合流・横断へ変更。47クラスタすべてに複数の出入口。',9)
text(24,31,'645点・48/64/80pt・Keystone3ptは比較用。名称・係数・配置・ポイント制度は未採用。実戦効果は未反映。',9,'#8b623d')
text(24,16,'Minecraft内の共通大盤面、実戦効果、保存・移行は未実装。Windowsで検証。iOS Safari実機は未確認。',8)
c.showPage()
page('同じ目標へ、違う力を拾って進む','序盤の等費用2経路 / 5起点×15候補の到達費用 / ノードは旧仮値の計画表、実戦DPSではない')
picture('ProjectS_LargeTree_Branch_Compare.png',24,251,470,270)
y=232
y=lines(24,y,'同じNotable「軽やかな連撃」へ、どちらも3pt。\n威力側：物理+12% ／ 攻撃速度+5% ／ 自分の資源+4%\n耐久側：HP+8% ／ 攻撃速度+5% ／ 自分の資源+4%\n途中で横断・合流し、別のNotableへも進める。',470,10,15)
y=lines(24,y-7,'全Keystoneを除いても主要地域が接続。KeystoneなしのNotable投資例と、同予算のKey巡回例を操作版で比較できる。64ptで巡回できた例は3〜5個。新効果の強さが未決なので、巡回が最適でないことの実戦検証は未完了。',470,9,14)
y=lines(24,y-7,'取得しても入力が足りない条件を表示：越境先の武器・技能・AP・資源、出血の供給、味方に届く障壁、燃焼・連鎖の供給。炎／雷技能名だけでは燃焼／連鎖を自動供給しない。防具の自由混合と任意の魔術加工を維持する。',470,9,14)
x=516;top=H-79;namew=127;colw=34
text(x,top,'Keystone候補（すべて未採用）',9)
headers=['戦','盾','魔','弓','暗']
for j,label in enumerate(headers):text(x+namew+j*colw,top,label,9)
for i,key in enumerate(keys):
 row=top-(i+1)*17;c.setStrokeColor(HexColor('#c6c8b8'));c.line(x,row-4,W-24,row-4);text(x,row,key['name'],9)
 for j,o in enumerate(d['origins']):
  cost=next(m for m in audit['matrix'] if m['origin']==o['id'])['costs'][key['id']];text(x+namew+j*colw,row,str(cost),9,'#8b623d' if cost>48 else '#343b2e')
text(x,top-284,'数値は起点からの最短費用（Key 3ptを含む）',8)
text(x,top-301,'予算 / Notable到達 / Key到達（起点による幅）',8)
for i,b in enumerate(audit['budgets']):text(x,top-318-i*15,f"{b['budget']}pt  /  {b['notablesMin']}〜{b['notablesMax']}/77  /  {b['keysMin']}〜{b['keysMax']}/15",9)
lines(x,top-386,'当面64ptを比較基準に提案。最遠の候補57ptへどの起点からも届き、48ptの地域制限と80ptの広い投資を比べられる。全取得や強さの保証ではない。',W-x-24,8.5,14)
text(24,17,'検証：75費用照合・40仮配分・別経路維持・孤立返還拒否・入力警告。交差16箇所は線の切れ目で区別。外部通信・JS例外0。',8)
c.save();print(str(target))
