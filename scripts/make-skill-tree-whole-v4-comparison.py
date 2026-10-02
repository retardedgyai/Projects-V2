"""One full comparison assembled from verified real browser captures."""
import json,io,base64,re
from pathlib import Path
from PIL import Image,ImageDraw,ImageFont
ROOT=Path(__file__).resolve().parents[1];OUT=ROOT/'assets/core-ui/large-tree-preview/proposals/workshop-whole-goals-v4'
qa=json.loads((OUT/'browser-verification.json').read_text(encoding='utf8'));examples={e['id']:e for e in json.loads((OUT/'route-examples.json').read_text(encoding='utf8'))};top=json.loads((OUT/'topology-verification.json').read_text(encoding='utf8'))
assert qa['status']=='PASS' and len(qa['focusAudits'])==94 and top['nodes']==645
frames={f['name']:f for f in qa['frames']}
html=(OUT/'ProjectS_Whole_Goals_V4.html').read_text(encoding='utf8');payload=json.loads(re.search(r'<script id="wholeTreeData" type="application/json">(.*?)</script>',html,re.S)[1])
def shot(name):
 f=frames[name];r=f['rect'];return Image.open(OUT/(name+'.png')).convert('RGB').crop((round(r['x']),round(r['y']),round(r['x']+r['w']),round(r['y']+r['h'])))
W,H=3200,5890;im=Image.new('RGB',(W,H),'#141b16');d=ImageDraw.Draw(im)
font=lambda n:ImageFont.truetype('C:/Windows/Fonts/YuGothM.ttc',n)
def text(x,y,s,size=25,color='#d9c493'):d.text((x,y),s,font=font(size),fill=color)
def wrap(x,y,s,width,size=23,color='#b4c1a5',line=36):
 row=''
 for c in s:
  if d.textlength(row+c,font=font(size))>width:text(x,y,row,size,color);y+=line;row=c
  else:row+=c
 if row:text(x,y,row,size,color);y+=line
 return y
text(50,25,'ProjectS / 645点の全体から、育てる道を選ぶ',46,'#e3ca94')
text(50,96,'全47領域・5職業・15 Key・776接続。効果・費用・入力条件は原データのまま。',27,'#acba9c')
p=shot('ProjectS_Whole_V4_Overview');p=p.resize((2160,1546),Image.Resampling.LANCZOS);im.paste(p,(45,157));d.rectangle((45,157,2204,1702),outline='#65734f',width=2)
x,y=2250,170;text(x,y,'全体の釣合いと入口',33);y+=64
notes=[('warrior','戦士','一撃・会心・範囲・吸収を選ぶ。左下から守り側にも回れる。'),('tank','タンク〈仮称〉','左上の装甲・生命・障壁。仲間の障壁へ向かうKeyは任意。'),('mage','メイジ','中央上のMP・効率・術式。血の代価、氷と障壁にも別の入口。'),('ranger','レンジャー','右側の会心・手数・資源。印を持つ編成では回避の機会へ。'),('assassin','アサシン','右下の手数・範囲・機動。弱点の混合点は会心側にも届く。')]
for oid,name,note in notes:
 root=next(o for o in payload['source']['origins'] if o['id']==oid);data=base64.b64decode(payload['icons'][root['icon']].split(',')[1]);icon=Image.open(io.BytesIO(data)).convert('RGBA').resize((54,54),Image.Resampling.NEAREST);im.paste(icon,(x,y),icon);text(x+72,y+4,name,29);y=wrap(x+72,y+49,note,760,23)+32
y+=10
for title,note in [('645点すべてを接続','351 Small・77 Notable・197通路・15 Key・5起点。未配置0。左右333／312点。鏡写しにせず目標の大小を分けた配置。'),('同型反復と長い一本道を抑える','47領域は異なる座標形。分岐のない通路だけの連続は最大2点。Keyを必須の通過点にしない。'),('選べる入力を確認','60装備構成×吸収MOD有無の120条件で、使える恩恵とKeyへ到達。技能やMODをツリーから自動供給しない。')]:
 text(x,y,title,27);y=wrap(x,y+45,note,850,22)+27
assert y<1700,y
rows=[('会心へ投資するか、貫通から静かな刃へ','critical','quiet',
 ['会心・倍率を育てる。弱点入力がなくても混合Notableの会心側へ。','会心を失う代償で直接ヒットを伸ばす原案。装甲・手数の別道も残る。']),
 ('同じタンクの技能で、生命か仲間の障壁か','tank-life','tank-shield',
 ['短く装甲を取るか、HPのNotableまで回るか。回復入力は補わない。','障壁源は持ち込む。Keyの仲間向け強化は原案で、取得は任意。']),
 ('同じメイジの技能で、MPの器か血の代価か','mage-mp','mage-blood',
 ['MP・効率の育成値を保持。最終消費の効率計算は実装済み扱いにしない。','技能の基礎MP消費を同量のHPへ置換。MP・効率・再生の値は無効、接続は保持。'])]
y0=1760
for title,left,right,notes in rows:
 text(50,y0,title,33,'#e2c993');text(50,y0+50,'同じ48pt予算・同じ入力・同じ視点。金は取得した道。',22,'#acba9c')
 for col,id in enumerate([left,right]):
  x=50+col*1580;e=examples[id];text(x,y0+90,f"{e['label']} / {e['spent']}pt・残り{48-e['spent']}pt",27)
  p=shot('ProjectS_Whole_V4_Pair_'+id); ph=round(1520*p.height/p.width);p=p.resize((1520,ph),Image.Resampling.LANCZOS)
  # Preserve the browser canvas aspect by cropping neither content nor data; this is a scaled comparison frame.
  im.paste(p,(x,y0+135));d.rectangle((x,y0+135,x+1519,y0+135+ph-1),outline='#65734f',width=2)
  wrap(x,y0+155+ph,notes[col],1510,23,line=34)
 y0+=1310
text(50,5770,'配置・配分UIの全体試作。native画面・戦闘効果・packet・ゲーム保存への反映は未実施。',26,'#debf88')
text(50,5820,'検証：交差0・重複線0・点を跨ぐ線0。PC／スマホ94領域画面、20実画面、120入力条件。旧案を保持。',23,'#aab89a')
im.save(OUT/'ProjectS_Whole_645_V4_Comparison.png')
print(json.dumps({'width':W,'height':H,'bytes':(OUT/'ProjectS_Whole_645_V4_Comparison.png').stat().st_size}))
