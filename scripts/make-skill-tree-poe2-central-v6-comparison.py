"""Compare actual PoE2 and ProjectS browser captures with equal start-ring size."""
import json,math
from pathlib import Path
from PIL import Image,ImageDraw,ImageFont
ROOT=Path(__file__).resolve().parents[1];OUT=ROOT/'assets/core-ui/large-tree-preview/proposals/workshop-poe2-central-v6'
qa=json.loads((OUT/'browser-verification.json').read_text(encoding='utf8'));assert qa['status']=='PASS'
geom=json.loads((OUT/'geometry-verification.json').read_text(encoding='utf8'));assert geom['regularStarts'] and geom['centralVoidClear']
oldf=json.loads((OUT/'Reference_ProjectS_Unshared_V5_Frame.json').read_text(encoding='utf8'))
def frame(report,name):return next(x for x in report['frames'] if x['name']==name)
def mapCenter(f,c):
 r=f['rect'];cam=f['cam'];return (r['w']/2+(c[0]-cam['x'])*cam['z'],r['h']/2+(c[1]-cam['y'])*cam['z'])
def normalize(path,c,radius,w=1040,h=1080,target=240):
 im=Image.open(path).convert('RGB');scale=target/radius
 # Sampling changes framing only; no reference coordinates or wires are repainted.
 return im.transform((w,h),Image.Transform.AFFINE,(1/scale,0,c[0]-w/2/scale,0,1/scale,c[1]-h/2/scale),Image.Resampling.BICUBIC)
W,H=3300,2110;im=Image.new('RGB',(W,H),'#1a291f');d=ImageDraw.Draw(im)
font=lambda n:ImageFont.truetype('C:/Windows/Fonts/YuGothM.ttc',n)
def text(x,y,s,size=28,color='#e8d2a0'):d.text((x,y),s,font=font(size),fill=color)
def wrap(x,y,s,width=1000,size=25,color='#c3d1b3'):
 row=''
 for ch in s:
  if d.textlength(row+ch,font=font(size))>width:text(x,y,row,size,color);y+=40;row=ch
  else:row+=ch
 if row:text(x,y,row,size,color);y+=40
 return y
text(50,32,'PoE2の中央を実見して、5起点を組み直す',46)
text(50,103,'開始位置の円周を同じ大きさに揃えた、実ブラウザ画像の比較。ProjectSの効果645点は保持。',27)
center=[1230,-1245];newf=frame(qa,'ProjectS_PoE2_V6_Center')
panels=[
 ('途中候補 / 修正前',normalize(OUT/'Reference_ProjectS_Unshared_V5_Canvas.png',mapCenter(oldf,center),385*oldf['cam']['z']),
  '中央を不規則な網で埋め、開始位置の半径と角度が揃っていなかった。中央へ寄せるだけでは参照の構造に合わない。'),
 ('実参照 / PoE2 0.5.1',normalize(OUT/'Reference_PoE2_Center_0.5.1.png',(956,582),166),
  '中央の肖像領域を囲む6開始位置。公式データでも、ほぼ同じ半径・60度間隔。通常ツリーへの初手は各2本。'),
 ('再構成 / ProjectS V6',normalize(OUT/'ProjectS_PoE2_V6_Center_Canvas.png',mapCenter(newf,center),900*newf['cam']['z']),
  '中央余白を囲む5起点を、同じ半径・72度間隔に配置。短い2出口の先で3つ目の選択へ分岐し、隣の開始部にも回れる。')]
for k,(title,p,note) in enumerate(panels):
 x=50+k*1100;text(x,170,title,31);im.paste(p,(x,225));d.rectangle((x,225,x+1039,1304),outline='#839570',width=2);wrap(x,1385,note)
text(50,1570,'中央の規則性と、外側の不規則さを分ける',34)
wrap(50,1630,'外側は、大小の閉路・三角分岐・梯子・開いたU字などが地域通路につながる。ProjectSでは既存47領域の違う形・大きさ・枝を保持し、5本の長い放射通路で仕切っていない。',3130,27)
text(50,1770,'実操作と検証',32)
wrap(50,1825,'PoE2で職業切替・拡大・初手の選択と払い戻しを実施。ProjectSは120入力条件、PC/スマホ94領域の表示、マウス・タッチ、保存・復元、Keyの代償と払い戻しを確認。配線の交差・重なりは0。',3130,26)
text(50,1980,'参照: cvenzin.github.io/poe2-skilltree/  ·  公式データ: github.com/grindinggear/poe2-skilltree-export',22,'#aebeaa')
text(50,2028,'配置と操作のプレビュー。native画面・実戦効果・packet・ゲーム保存への反映は未実装。main・merge・pushなし。',24,'#c3d1b3')
im.save(OUT/'ProjectS_PoE2_Central_V6_Reference_Comparison.png')
print(json.dumps({'width':W,'height':H,'bytes':(OUT/'ProjectS_PoE2_Central_V6_Reference_Comparison.png').stat().st_size}))
