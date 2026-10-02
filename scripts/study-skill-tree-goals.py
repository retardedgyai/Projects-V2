"""Read the official GGG tree geometry and inspect real paths, without importing it."""
import collections, hashlib, json, math
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont
ROOT=Path(__file__).resolve().parents[1];OUT=ROOT/'.tools/goal-tree-study'
raw=(OUT/'poe-official-data.json').read_bytes();data=json.loads(raw);nodes=data['nodes'];groups=data['groups'];radii=data['constants']['orbitRadii'];counts=data['constants']['skillsPerOrbit']
special=[0,30,45,60,90,120,135,150,180,210,225,240,270,300,315,330]
pos={}
for id,n in nodes.items():
    if 'group' not in n or str(n['group']) not in groups or n.get('ascendancyName') or n.get('isProxy') or n.get('isMastery'):continue
    g=groups[str(n['group'])];o=n['orbit'];i=n['orbitIndex'];angle=math.radians(special[i] if o in [2,3] else i*360/counts[o]);pos[id]=(g['x']+radii[o]*math.sin(angle),g['y']-radii[o]*math.cos(angle))
adj={id:set() for id in pos};edges=set()
for id in pos:
    for other in nodes[id].get('out',[]):
        if other in pos:adj[id].add(other);adj[other].add(id);edges.add(tuple(sorted((id,other))))
def search(start,end,forbidden=()):
    prev={start:None};queue=collections.deque([start]);blocked=set(forbidden)
    while queue:
        u=queue.popleft()
        if u==end:
            path=[u]
            while prev[path[-1]] is not None:path.append(prev[path[-1]])
            return path[::-1]
        for v in sorted(adj[u]):
            if v in prev or v in blocked or 'classStartIndex' in nodes[v] and v!=start:continue
            prev[v]=u;queue.append(v)
    raise ValueError(end)
root='50986';goals=['10661','10808','40907'];paths=[search(root,k) for k in goals]
pathids=set(i for p in paths for i in p);xs=[pos[i][0] for i in pathids];ys=[pos[i][1] for i in pathids];box=(min(xs)-1700,min(ys)-1200,max(xs)+1700,max(ys)+1200)
im=Image.new('RGB',(1700,1200),'#141a17');draw=ImageDraw.Draw(im);font=lambda s:ImageFont.truetype('C:/Windows/Fonts/YuGothM.ttc',s)
scale=min(1600/(box[2]-box[0]),1020/(box[3]-box[1]));screen=lambda p:(70+(p[0]-box[0])*scale,130+(p[1]-box[1])*scale)
for a,b in edges:
    if a in pos and b in pos:draw.line([screen(pos[a]),screen(pos[b])],fill='#414d3c',width=1)
colors=['#d7b778','#8fc9a5','#c18473']
for p,col in zip(paths,colors):
    for a,b in zip(p,p[1:]):draw.line([screen(pos[a]),screen(pos[b])],fill=col,width=4)
for id,p in pos.items():
    x,y=screen(p)
    if not 20<x<1680 or not 110<y<1150:continue
    n=nodes[id];r=10 if n.get('isKeystone') else 5 if n.get('isNotable') else 2
    draw.ellipse((x-r,y-r,x+r,y+r),fill='#17201a',outline='#e6c384' if r==10 else '#829579',width=2 if r>2 else 1)
for id in [root]+goals:
    x,y=screen(pos[id]);name=nodes[id]['name'];draw.rectangle((x-6,y+13,x+draw.textlength(name,font=font(21))+12,y+43),fill='#101713');draw.text((x,y+15),name,font=font(21),fill='#e6d3aa')
draw.rectangle((0,0,1700,105),fill='#141a17');draw.text((30,20),'GGG公式公開データ / Duelistから3つのKeyへの実接続',font=font(27),fill='#e2d0a6');draw.text((30,62),'座標・接続をそのまま描画。配色は参照用。ProjectSへのノード移植は行わない。',font=font(19),fill='#aab49e')
im.save(OUT/'PoE_Official_Goal_Routes.png')
report={'source':'https://github.com/grindinggear/skilltree-export/blob/master/data.json','sha256':hashlib.sha256(raw).hexdigest(),'sourceTree':data['tree'],'paths':[{'goal':nodes[k]['name'],'nodes':len(p),'notables':[nodes[i]['name'] for i in p if nodes[i].get('isNotable')],'ids':p,'goalNeighbours':[nodes[i]['name'] for i in adj[k]]} for k,p in zip(goals,paths)],'observations':['Keyそのものを通らず周辺の通路を継続できる。','目標までに分岐・再接続があり、周辺のNotableへ投資する経路は一律の最短道と異なる。','大きい生命の育成圏、小さい防御の枝、単独Keyの袋路が混在する。'],'projectSInterpretation':'同じ島形を反復せず、欲しい目標と近隣の補完恩恵から局所の接続を設計する。効果やMasteryは追加しない。'}
(OUT/'official-reference-report.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8');print(json.dumps(report,ensure_ascii=False))
