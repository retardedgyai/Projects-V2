"""Reserve all source regions in one map without claiming finished node wiring.

Centers and neighboring goals are editorial decisions, never a repeated cell grid.
The diagram shows real detailed nodes and named unbuilt regions separately.
"""
import collections,json,math
from pathlib import Path
from PIL import Image,ImageDraw,ImageFont
ROOT=Path(__file__).resolve().parents[1];BASE=ROOT/'assets/core-ui/large-tree-preview';OUT=BASE/'proposals/workshop-goal-study-v3'
s=json.loads((BASE/'graph.json').read_text(encoding='utf-8'));g=json.loads((OUT/'candidate-region.json').read_text(encoding='utf-8'));by={n['id']:n for n in s['nodes']};detailed={r['id']:r for r in g['groups']}
# Positions reserve room for different-size goal neighborhoods, not node rings.
future={
 'g6':(-2240,-2900,'装甲を積み、生命と受け流しへ回る'),
 'g18':(-1240,-1870,'生命・再生から両側の防御へ'),
 'g31':(-310,-3920,'仲間への障壁を伸ばす任意のKey11'),
 'g46':(940,-3230,'魔法防御と魔力を両側から拾う'),
 'g33':(1580,-2380,'回復入力の枝と一般再生の入口を分離'),
 'g40':(-1310,-4420,'資源運用をタンクと魔力側へ渡す'),
 'g16':(-3160,-1840,'貫通への投資、異系統武器Key07は任意'),
 'g44':(-3580,-570,'機動と手数を会心・装甲から選ぶ'),
 'g11':(-3820,-2810,'受け流しと攻撃速度の二入口'),
 'g28':(-2680,-4300,'大きな受け流しの投資、資源側へ戻る'),
 'g20':(2540,-4550,'MP運用と血の代価Key02、自然再生との交換'),
 'g12':(2300,-3280,'小さな魔力・効率目標、術式を通過条件にしない'),
 'g2':(3880,-4310,'二つの術式Notableを魔力と障壁から拾う'),
 'g24':(4590,-2860,'もう一つの術式目標、雷と資源の別入口'),
 'g27':(2860,-1980,'氷の機会Key13、一般障壁から任意に分岐'),
 'g3':(4320,-1370,'小さな氷・障壁目標、技能回転へ戻る'),
 'g47':(5470,-4440,'炎の持続Key14、燃焼入力不足は保持'),
 'g8':(5910,-2790,'短い炎目標、魔術加工は必須にしない'),
 'g9':(4610,-5710,'二つの雷Notableと単体収束Key15'),
 'g43':(2580,-6200,'小さな雷と回転の寄り道'),
 'g19':(730,-5220,'重ね撃ちKey09を資源と魔力から比較'),
 'g4':(3570,-260,'技能回転を物理・術式の間へ'),
 'g52':(4800,730,'回転を手数・機動へつなぐ'),
 'g39':(6580,-1190,'資源・効率から会心と術式へ回る'),
 'g32':(6500,130,'会心倍率をレンジャーと機動から育てる'),
 'g41':(5050,2020,'手数二目標、範囲と資源への迂回'),
 'g7':(3370,1620,'小さな範囲・物理目標、強制Keyなし'),
 'g42':(4250,3380,'別の範囲目標、手数と弱点を回る'),
 'g17':(6390,3010,'弱点と継続の毒刃Key12、印が別途必要'),
 'g10':(6250,1610,'機動と機会返還Key10、印供給と消費を区別')}
assert len(future)==30 and set(future)|set(detailed)=={r['id'] for r in s['groups']}
regions=[]
for r in s['groups']:
 if r['id'] in detailed:d=detailed[r['id']];x,y,motive=d['x'],d['y'],d['role'];state='detailed'
 else:x,y,motive=future[r['id']];state='reserved'
 regions.append({'id':r['id'],'name':r['name'],'x':x,'y':y,'state':state,'sourceNodeIds':r['nodes'],'purpose':motive,'notables':[i for i in r['nodes'] if by[i]['type']=='notable']})
# Neighbors describe why to travel. Their node-level corridors are not generated.
pairs=[('g6','g18'),('g18','g45'),('g6','g11'),('g11','g16'),('g16','g37'),('g16','g44'),('g44','g26'),('g44','g25'),('g6','g28'),('g28','g40'),('g40','g31'),('g31','g46'),('g46','g33'),('g33','g51'),('g46','g23'),('g40','g19'),('g19','g20'),('g19','g43'),('g43','g9'),('g9','g47'),('g47','g8'),('g20','g2'),('g2','g47'),('g20','g12'),('g12','g46'),('g12','g27'),('g27','g51'),('g27','g3'),('g2','g24'),('g24','g3'),('g24','g8'),('g3','g4'),('g4','g35'),('g4','g52'),('g52','g39'),('g39','g24'),('g39','g32'),('g32','g10'),('g10','g52'),('g52','g41'),('g41','g7'),('g7','g34'),('g41','g42'),('g42','g17'),('g17','g10'),('g42','g5'),('g17','g50')]
entries={'warrior':{'x':-1300,'y':700,'neighbors':['g49','g30','g45']},'tank':{'x':-900,'y':-3110,'neighbors':['g6','g18','g31','g40']},'mage':{'x':3400,'y':-3260,'neighbors':['g12','g20','g2','g24']},'ranger':{'x':5740,'y':560,'neighbors':['g32','g39','g41','g52']},'assassin':{'x':3710,'y':4560,'neighbors':['g42','g17','g50','g5']}}
coverage=collections.Counter(i for r in regions for i in r['sourceNodeIds']);assert all(v==1 for v in coverage.values())
allids={n['id'] for n in s['nodes']};covered=set(coverage);nonregion=sorted(allids-covered);assert len(covered)+len(nonregion)==645
plan={'status':'WHOLE_LAYOUT_PLAN','fullNodeLayoutComplete':False,'detailedNodeCount':len(g['nodes']),'detailedRegionCount':len(detailed),'reservedRegionCount':len(future),'sourceNodeCount':645,'regions':regions,'origins':[dict(copy,**entries[copy['id']],sourceOpeningIds=[i for lane in copy['lanes'] for i in lane['nodes']]) for copy in s['origins']],'plannedNeighborPairs':pairs,'nonRegionSourceNodeIds':nonregion,'undetailedNodeIds':sorted(allids-{n['id'] for n in g['nodes']}),'sourceIdsCoveredExactlyOnce':True,'rules':['職業は入口であり、領域は所持入力に応じて全職共用','同じセルや同半径への複製を行わない','Keyは任意の終点で、通常育成の必須通過点にしない','領域間で長い中立一本道を作らず、道中の目標か別経路を置く','通路は元の197点から重複なく選び、実際の分岐と距離が決まってから使う'],'routingStatus':'予約領域間の辺は意味関係のみ。詳細座標・交差検証は未完。'}
plan['keyGoals']=[{'id':n['id'],'name':n['name'],'group':n['groupAnchor'],'requirements':n['requirements'],'state':'detailed' if n['id'] in {v['id'] for v in g['nodes']} else 'reserved'} for n in s['nodes'] if n['type']=='keystone']
# Prove the semantic whole plan remains one connected tree of neighborhoods.
member={i:r['id'] for r in g['groups'] for i in r['nodes']};nadj={n['id']:[] for n in g['nodes']}
for e in g['edges']:nadj[e['a']].append(e['b']);nadj[e['b']].append(e['a'])
r_adj={r['id']:set() for r in regions}
for r in g['groups']:
 seen=set(r['nodes']);queue=list(r['nodes'])
 for u in queue:
  for v in nadj[u]:
   if v in member and member[v]!=r['id']:r_adj[r['id']].add(member[v]);continue
   if v not in seen and by[v]['type']!='keystone':seen.add(v);queue.append(v)
for a,b in pairs:r_adj[a].add(b);r_adj[b].add(a)
seen={'g49'};queue=['g49']
for u in queue:
 for v in r_adj[u]:
  if v not in seen:seen.add(v);queue.append(v)
assert len(seen)==47 and all(set(o['neighbors'])<=seen for o in entries.values())
plan['all47NeighborhoodsConnectedSemantically']=True
plan['all5OriginsHaveSharedNeighborhoodEntries']=True
(OUT/'full-tree-plan.json').write_text(json.dumps(plan,ensure_ascii=False,indent=2),encoding='utf-8')
W,H=3200,3400;im=Image.new('RGB',(W,H),'#131b16');d=ImageDraw.Draw(im);font=lambda n:ImageFont.truetype('C:/Windows/Fonts/YuGothM.ttc',n)
scale=.215;origin=(1170,1570);pt=lambda x,y:(round(origin[0]+x*scale),round(origin[1]+y*scale))
d.text((45,24),'ProjectS / 全47領域を一枚へつなぐ配置計画',font=font(44),fill='#e3c78e');d.text((45,87),'詳細197点・17領域 / 残る30領域は名前と目標を予約。全645点の配線完了図ではありません。',font=font(25),fill='#a6b79a')
# Real detailed wires, projected without moving source study nodes.
for e in g['edges']:a,b=e['points'];d.line([pt(*a),pt(*b)],fill='#60795c',width=2)
for n in g['nodes']:
 x,y=pt(n['x'],n['y']);r=8 if n['type'] in ['notable','keystone','start'] else 3;d.ellipse((x-r,y-r,x+r,y+r),fill='#c5ad74' if n['type']=='keystone' else '#719567',outline='#d0bb86' if r==8 else '#719567',width=2)
# Reserved regions are text only; no fictitious node wiring or repeated circles.
boxes=[]
for o in plan['origins']:
 x,y=pt(o['x'],o['y']);text=o['name']+'の入口';width=d.textlength(text,font=font(23))+20;box=(x-width/2,y-27,x+width/2,y+28);boxes.append(box);d.rectangle(box,fill='#4d4730',outline='#d4bc84',width=2);d.text((box[0]+10,y-19),text,font=font(23),fill='#efdaab')
def point_distance(p,a,b):
 dx=b[0]-a[0];dy=b[1]-a[1];t=max(0,min(1,((p[0]-a[0])*dx+(p[1]-a[1])*dy)/max(dx*dx+dy*dy,1e-12)));return math.hypot(p[0]-a[0]-t*dx,p[1]-a[1]-t*dy)
def line_box(a,b,box):
 x0,y0,x1,y1=box;lo,hi=0.,1.;dx=b[0]-a[0];dy=b[1]-a[1]
 for p,q in [(-dx,a[0]-x0),(dx,x1-a[0]),(-dy,a[1]-y0),(dy,y1-a[1])]:
  if abs(p)<1e-12:
   if q<0:return False
  elif p<0:lo=max(lo,q/p)
  else:hi=min(hi,q/p)
 return lo<=hi
segments=[(pt(*e['points'][0]),pt(*e['points'][1])) for e in g['edges']]
for r in regions:
 x,y=pt(r['x'],r['y']);text=r['name']+' / '+r['id'];width=d.textlength(text,font=font(22))+8
 offsets=[(0,-55),(0,45),(-75,-65),(70,-65),(-100,45),(100,45),(0,-115),(0,100),(-130,0),(130,0),(-180,-80),(180,80)]
 offsets+=sorted([(dx,dy) for dx in range(-360,361,40) for dy in range(-240,241,40)],key=lambda p:p[0]*p[0]+p[1]*p[1])
 for dx,dy in offsets:
  bx,by_=x+dx-width/2,y+dy;box=(bx-4,by_-4,bx+width+4,by_+59)
  if any(box[0]<q[2] and box[2]>q[0] and box[1]<q[3] and box[3]>q[1] for q in boxes) or any(line_box(a,b,box) for a,b in segments):continue
  if any(math.hypot(p[0]-max(box[0],min(box[2],p[0])),p[1]-max(box[1],min(box[3],p[1])))<13 for p in [pt(n['x'],n['y']) for n in g['nodes']]):continue
  break
 else:raise ValueError('No clear diagram label position: '+r['id'])
 boxes.append(box);d.text((bx,by_),text,font=font(22),fill='#e6c88e' if r['state']=='detailed' else '#92abc1');d.text((bx,by_+33),str(len(r['sourceNodeIds']))+'点・'+('詳細済' if r['state']=='detailed' else '配置予約'),font=font(15),fill='#a9b89b' if r['state']=='detailed' else '#758e9e')
for id,x,y,lines in [('west',95,2980,['西：装甲・生命から、会心または貫通へ','17領域は詳細配線済み。Key04は貫通側の任意の末端。']),('north',95,3070,['北：仲間への障壁・魔力運用・属性へ','血の代価、氷、炎、雷は入力と代償を持つ寄り道。']),('east',1590,2980,['東：会心・手数から範囲・弱点へ','レンジャーにも近い会心圏を残し、西へ強制横断しない。']),('south',1590,3070,['南：範囲・資源と裂傷の境界へ','アサシンも共有領域から入る。出血入力は未実装のまま。'])]:
 for j,line in enumerate(lines):d.text((x,y+j*34),line,font=font(25 if j==0 else 20),fill='#d8c292' if j==0 else '#9eae96')
d.text((45,3230),'金の名称：詳細済 / 青灰の名称：位置・目的のみ予約 / 予約領域の接続はfull-tree-plan.jsonに記録。',font=font(23),fill='#b8c1a7');d.text((45,3280),'5職業は入口。防具混合・魔術MODを条件にせず、既に持つ入力への育成を選ぶ。旧案・原効果は保持。',font=font(23),fill='#b8c1a7')
im.save(OUT/'ProjectS_Whole_Tree_Plan_V3.png');print(json.dumps({'regions':len(regions),'detailed':len(detailed),'reserved':len(future),'sourceIds':len(allids),'positions':'reservation only outside detailed scope'}))
