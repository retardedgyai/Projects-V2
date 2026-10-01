"""Build a separate full-map presentation; source progression JSON stays exact.

The display projection rotates the two right-hand starts into the vacant upper
right / lower right positions. All 645 nodes and 952 edges remain present.
"""
import base64,copy,hashlib,heapq,json,math,re,sys
from pathlib import Path
import numpy as np
ROOT=Path(__file__).resolve().parents[1]
BASE=ROOT/'assets/core-ui/large-tree-preview';OUT=BASE/'proposals/workshop-full-v1'

def project(x,y):
 r=math.hypot(x,y);a=math.degrees(math.atan2(y,x))
 if a<-90:a+=360
 # Rigid bands preserve the matching opening-node shapes on the two sides.
 angles=[-90,-75,15,45,75,105,135,165,195,225,255,270]
 offsets=[0,0,-60,-60,-60,-60,0,0,0,0,0,0]
 shift=float(np.interp(a,angles,offsets));t=math.radians(a+shift)
 return [r*math.cos(t),r*math.sin(t)]

def build_display(source):
 nodes=source['nodes'];ids={n['id']:i for i,n in enumerate(nodes)}
 points=np.array([project(n['x'],n['y']) for n in nodes],dtype=float)
 fixed=np.array([n['type']=='start' or bool(n.get('opening')) for n in nodes])
 radii=np.array([{'start':76,'small':21,'road':12,'notable':30,'keystone':64}[n['type']] for n in nodes],float)
 starts={o['id']:o['root'] for o in source['origins']}
 expected={'mage':[0,-400],'tank':[-346.41,-200],'ranger':[346.41,-200],'warrior':[-346.41,200],'assassin':[346.41,200]}
 for o,p in expected.items():points[ids[starts[o]]]=p
 # One source opening point was moved by an earlier overlap correction.
 # Snap only the right-hand opening projection to the mirrored left template.
 for left,right in [('tank','ranger'),('warrior','assassin')]:
  left_ids=[i for i,n in enumerate(nodes) if n.get('origin')==left and fixed[i]]
  available={i for i,n in enumerate(nodes) if n.get('origin')==right and fixed[i]}
  for i in left_ids:
   target=np.array([-points[i,0],points[i,1]]);j=min(available,key=lambda k:np.linalg.norm(points[k]-target));available.remove(j);points[j]=target
 edges=np.array([[ids[e['a']],ids[e['b']]] for e in source['edges']],int)
 # Resolve compressed display spacing and retain short actual connections.
 for iteration in range(420):
  delta=points[:,None,:]-points[None,:,:];dist=np.linalg.norm(delta,axis=2)
  target=radii[:,None]+radii[None,:]+45;overlap=target-dist
  np.fill_diagonal(overlap,0);overlap=np.maximum(overlap,0)
  unit=delta/np.maximum(dist[:,:,None],1e-9)
  movement=(unit*overlap[:,:,None]*.52).sum(axis=1)
  a,b=edges[:,0],edges[:,1];dv=points[b]-points[a];dl=np.linalg.norm(dv,axis=1)
  extra=np.maximum(dl-574,0);force=dv/np.maximum(dl[:,None],1e-9)*extra[:,None]*.46
  ma=np.where(fixed[b],1.8,1);mb=np.where(fixed[a],1.8,1)
  np.add.at(movement,a,force*ma[:,None]);np.add.at(movement,b,-force*mb[:,None])
  movement[fixed]=0
  magnitude=np.linalg.norm(movement,axis=1);movement*=np.minimum(1,24/np.maximum(magnitude,1e-9))[:,None]
  points+=movement
  if overlap.max()<.02 and extra.max()<.05:break
 display=copy.deepcopy({k:source[k] for k in ['nodes','edges','groups','bounds']})
 for n,p in zip(display['nodes'],points):n['x'],n['y']=[round(float(v),3) for v in p]
 by={n['id']:n for n in display['nodes']}
 # Old detours belong to the old coordinates. Route the same source edges
 # afresh instead of stretching old waypoints into long zigzags.
 for e,old in zip(display['edges'],source['edges']):
  e['points']=[[by[e['a']]['x'],by[e['a']]['y']],[by[e['b']]['x'],by[e['b']]['y']]];e['crossingGaps']=[]
 # Route display strokes around unrelated node bodies; no new graph vertices.
 coords=np.array([[n['x'],n['y']] for n in display['nodes']]);route_radius=radii+14
 def hits(a,b,ends):
  a=np.array(a);b=np.array(b);d=b-a;den=float(d@d)
  t=np.clip(((coords-a)@d)/max(den,1e-9),0,1)
  distance=np.linalg.norm(coords-(a+t[:,None]*d),axis=1);mask=distance<route_radius
  mask[list(ends)]=False
  return np.where(mask)[0],t
 def detour(start,end,ends):
  # A small local grid is used only when the source curve cannot clear a body.
  for padding in [160,280,440]:
   step=8;left=math.floor((min(start[0],end[0])-padding)/step)*step;top=math.floor((min(start[1],end[1])-padding)/step)*step
   nx=math.ceil((max(start[0],end[0])+padding-left)/step)+1;ny=math.ceil((max(start[1],end[1])+padding-top)/step)+1
   yy,xx=np.mgrid[0:ny,0:nx];occupied=np.zeros((ny,nx),bool)
   for ni,p in enumerate(coords):
    if ni in ends:continue
    if left-route_radius[ni]<p[0]<left+nx*step+route_radius[ni] and top-route_radius[ni]<p[1]<top+ny*step+route_radius[ni]:occupied|=(xx*step+left-p[0])**2+(yy*step+top-p[1])**2<(route_radius[ni]+3)**2
   grid=lambda p:(round((p[0]-left)/step),round((p[1]-top)/step));a=grid(start);b=grid(end);occupied[a[1],a[0]]=False;occupied[b[1],b[0]]=False
   todo=[(0,0,a)];cost={a:0};previous={};found=False
   while todo:
    _,price,p=heapq.heappop(todo)
    if price>cost[p]+1e-8:continue
    if p==b:found=True;break
    for dx,dy in [(-1,0),(1,0),(0,-1),(0,1),(-1,-1),(1,-1),(-1,1),(1,1)]:
     q=(p[0]+dx,p[1]+dy)
     if not(0<=q[0]<nx and 0<=q[1]<ny) or occupied[q[1],q[0]]:continue
     if dx and dy and (occupied[p[1],q[0]] or occupied[q[1],p[0]]):continue
     nextprice=price+math.hypot(dx,dy)
     if nextprice<cost.get(q,math.inf):cost[q]=nextprice;previous[q]=p;heapq.heappush(todo,(nextprice+math.dist(q,b),nextprice,q))
   if not found:continue
   cells=[b]
   while cells[-1]!=a:cells.append(previous[cells[-1]])
   path=[list(start)]+[[left+x*step,top+y*step] for x,y in cells[::-1][1:-1]]+[list(end)]
   simple=[path[0]];k=0
   while k<len(path)-1:
    j=len(path)-1
    while j>k+1 and len(hits(path[k],path[j],ends)[0]):j-=1
    simple.append(path[j]);k=j
   if all(not len(hits(a,b,ends)[0]) for a,b in zip(simple,simple[1:])):return simple
  return None
 routed=0;unresolved=[];grid_detours=0
 for e in display['edges']:
  ends=(ids[e['a']],ids[e['b']]);ps=e['points']
  for attempt in range(32):
   collision=None
   for si,(a,b) in enumerate(zip(ps,ps[1:])):
    bad,ts=hits(a,b,ends)
    if len(bad):collision=(si,a,b,int(bad[0]),float(ts[bad[0]]));break
   if collision is None:break
   si,a,b,ni,t=collision;d=np.array(b)-a;length=np.linalg.norm(d)
   if length<1e-7:break
   foot=np.array(a)+d*t;normal=np.array([-d[1],d[0]])/length;candidates=[]
   for sign in [-1,1]:
    for extra in [22,60,115]:
     q=(foot+normal*(route_radius[ni]+extra)*sign).tolist()
     candidates.append((len(hits(a,q,ends)[0])+len(hits(q,b,ends)[0]),extra,q))
   _,_,q=min(candidates,key=lambda v:(v[0],v[1]));ps.insert(si+1,q);routed+=1
  # Inspect the final path separately; last-loop insertion can fix the hit.
  if any(len(hits(a,b,ends)[0]) for a,b in zip(ps,ps[1:])):
   replacement=detour(ps[0],ps[-1],ends)
   if replacement:ps=replacement;grid_detours+=1
  simple=[ps[0]];k=0
  while k<len(ps)-1:
   j=len(ps)-1
   while j>k+1 and len(hits(ps[k],ps[j],ends)[0]):j-=1
   simple.append(ps[j]);k=j
  ps=simple
  for a,b in zip(ps,ps[1:]):
   bad,_=hits(a,b,ends)
   if len(bad):unresolved.append({'edge':[e['a'],e['b']],'nodes':[nodes[i]['id'] for i in bad]});break
  e['points']=[[round(float(v),3) for v in p] for p in ps]
 # Crossing gaps, one stroke only, using the existing pass-under convention.
 seg=[]
 for ei,e in enumerate(display['edges']):
  for si,(a,b) in enumerate(zip(e['points'],e['points'][1:])):seg.append((ei,si,a,b))
 crossings=0
 for k,(ei,si,a,b) in enumerate(seg):
  for ej,sj,c,d in seg[k+1:]:
   if ei==ej or {display['edges'][ei]['a'],display['edges'][ei]['b']}&{display['edges'][ej]['a'],display['edges'][ej]['b']}:continue
   ux,uy=b[0]-a[0],b[1]-a[1];vx,vy=d[0]-c[0],d[1]-c[1];den=ux*vy-uy*vx
   if abs(den)<1e-7:continue
   wx,wy=c[0]-a[0],c[1]-a[1];t=(wx*vy-wy*vx)/den;v=(wx*uy-wy*ux)/den
   if 1e-5<t<1-1e-5 and 1e-5<v<1-1e-5:display['edges'][ei]['crossingGaps'].append({'segment':si,'t':round(t,8)});crossings+=1
 for g in display['groups']:
  g['x']=sum(by[i]['x'] for i in g['nodes'])/len(g['nodes']);g['y']=sum(by[i]['y'] for i in g['nodes'])/len(g['nodes'])
  # Subtle hull of the displayed cluster; no stale decorative outline.
  ps=sorted(set((by[i]['x'],by[i]['y']) for i in g['nodes']))
  cross=lambda a,b,c:(b[0]-a[0])*(c[1]-a[1])-(b[1]-a[1])*(c[0]-a[0])
  lower=[];upper=[]
  for p in ps:
   while len(lower)>1 and cross(lower[-2],lower[-1],p)<=0:lower.pop()
   lower.append(p)
  for p in ps[::-1]:
   while len(upper)>1 and cross(upper[-2],upper[-1],p)<=0:upper.pop()
   upper.append(p)
  hull=lower[:-1]+upper[:-1];g['outline']=[[g['x']+(x-g['x'])*1.12,g['y']+(y-g['y'])*1.12] for x,y in hull]
  if g['outline']:g['outline'].append(g['outline'][0])
  g['labelX']=g['x'];g['labelY']=g['y']-100
 allpoints=[[n['x'],n['y']] for n in by.values()]+[p for e in display['edges'] for p in e['points']]
 xs=[p[0] for p in allpoints];ys=[p[1] for p in allpoints]
 display['bounds']={'minX':min(xs)-90,'maxX':max(xs)+90,'minY':min(ys)-90,'maxY':max(ys)+90}
 lengths=[math.hypot(by[e['a']]['x']-by[e['b']]['x'],by[e['a']]['y']-by[e['b']]['y']) for e in display['edges']]
 routed_lengths=[sum(math.dist(a,b) for a,b in zip(e['points'],e['points'][1:])) for e in display['edges']]
 delta=coords[:,None,:]-coords[None,:,:];dd=np.linalg.norm(delta,axis=2);rr=radii[:,None]+radii[None,:]+8;np.fill_diagonal(dd,1e9)
 pairs=[(nodes[i]['id'],nodes[j]['id']) for i,j in zip(*np.where(np.triu(dd<rr,1)))]
 audit={'sourceNodes':645,'displayNodes':len(by),'sourceEdges':952,'displayEdges':len(display['edges']),'groups':len(display['groups']),'roots':expected,'fixedOpeningNodes':int(fixed.sum()),'relaxationIterations':iteration+1,'maxEndpointDistance':round(max(lengths),3),'endpointDistanceOver600':sum(l>600 for l in lengths),'worldNodeOverlaps':pairs,'routedCorrections':routed,'localGridDetours':grid_detours,'unresolvedEdgeNodeCollisions':unresolved,'nonVertexCrossingsMarkedWithGaps':crossings,'sourceGraphChanged':False,'progressionChanged':False}
 audit.update(maxRoutedLength=round(max(routed_lengths),3),routedLengthOver600=sum(l>600 for l in routed_lengths),longestRoutedEdges=sorted([{'a':e['a'],'b':e['b'],'length':round(l,3)} for e,l in zip(display['edges'],routed_lengths)],key=lambda e:-e['length'])[:8])
 assert len(by)==645 and len(display['edges'])==952 and len(display['groups'])==47
 assert not pairs and not unresolved,audit
 return display,audit

def main():
 source=json.loads((BASE/'graph.json').read_text(encoding='utf-8'))
 if '--reuse-layout' in sys.argv:
  display=json.loads((OUT/'display-layout.json').read_text(encoding='utf-8'));audit=json.loads((OUT/'layout-verification.json').read_text(encoding='utf-8'))
 else:display,audit=build_display(source)
 OUT.mkdir(parents=True,exist_ok=True)
 (OUT/'display-layout.json').write_text(json.dumps(display,ensure_ascii=False,separators=(',',':')),encoding='utf-8')
 (OUT/'layout-verification.json').write_text(json.dumps(audit,ensure_ascii=False,indent=2),encoding='utf-8')
 css=(OUT/'proposal.css').read_text(encoding='utf-8');js=(OUT/'proposal.js').read_text(encoding='utf-8')
 html=(BASE/'ProjectS_LargeTree_Route_Preview.html').read_text(encoding='utf-8')
 old_radius="Math.max(n.type==='keystone'?11:n.type==='start'?8:n.type==='notable'?5.5:n.type==='road'?2.2:3.5,BASE_RADIUS[n.type]*cam.z)"
 assert html.count(old_radius)==2
 html=html.replace(old_radius,'displayRadius(n)')
 html=html.replace('r:displayRadius(n)}','r:displayRadius(n),collisionRadius:displayRadius(n)*(n.type===\'notable\'?1.25:n.type===\'keystone\'?1.19:1)}')
 html=html.replace('return Math.hypot(x-n.x,y-n.y)<n.r+3','return Math.hypot(x-n.x,y-n.y)<(n.collisionRadius||n.r)+3')
 html=html.replace('<nodes[i].r+nodes[j].r+2','<(nodes[i].collisionRadius||nodes[i].r)+(nodes[j].collisionRadius||nodes[j].r)+2')
 html=html.replace("else if(r>6){const s=", "else if(r>=5.5||n.type==='keystone'&&cam.z>.065){const s=")
 html=html.replace("if((n.opening&&n.origin===origin&&cam.z>.29)||(n.type==='start'&&cam.z>.2)){", "if(((n.opening&&n.origin===origin&&cam.z>.29)||(n.type==='start'&&cam.z>.2))&&p.x-r>=7&&p.x+r<=w-7&&p.y-r>=7&&p.y+r<=h-7){")
 html=html.replace("if(cam.z<=.2)for(const o of DATA.origins){const p=worldToScreen(by.get(o.root));", "if(cam.z<=.2)for(const o of DATA.origins){const p=worldToScreen(by.get(o.root));if(p.x<12||p.x>w-12||p.y<12||p.y>h-24)continue;")
 for old,new in [("'#516970'","'#4d5e4b'"),("'#657679'","'#65745d'"),("'#b5d395'","'#d5be84'"),("'#254447'","'#353b27'"),("'#b4ece5'","'#e1d5a2'")]:html=html.replace(old,new)
 html=html.replace("if(b.kind==='node'&&Math.hypot(c.x,c.y)>80)continue;", "if(b.kind==='node'&&Math.hypot(c.x,c.y)>(owner&&(owner.x<110||owner.x>w-110||owner.y<110||owner.y>h-110)?136:80))continue;")
 # Caption audits must use the displayed paths rather than the untouched source paths.
 html=html.replace('const segments=[];for(const e of DATA.edges)', 'const segments=[];for(const e of (window.workshopFull?.display||DATA).edges)')
 assert html.count('for(const g of DATA.groups){')==2
 html=html.replace('for(const g of DATA.groups){','for(const g of (window.workshopFull?.display||DATA).groups){')
 html=html.replace('<title>ProjectS — 大成長樹・経路比較</title>','<title>ProjectS — 工房UI・全47領域</title>')
 radius_function=re.search(r'^function displayRadius.*$',js,re.M).group(0)
 world=ROOT/'assets/ui/polish05-import/assets/images/world.png'
 css=':root{--atelier-world:url(data:image/png;base64,'+base64.b64encode(world.read_bytes()).decode()+')}\n'+css
 html=html.replace('</head>','<style>'+css+'</style><script>'+radius_function+'</script></head>')
 html=html.replace('</body>','<script id="displayData" type="application/json">'+json.dumps(display,ensure_ascii=False,separators=(',',':'))+'</script><script>'+js+'</script></body>')
 assert json.loads(re.search(r'<script id="treeData" type="application/json">(.*?)</script>',html,re.S).group(1))==source
 (OUT/'ProjectS_Passive_Workshop_Full.html').write_text(html,encoding='utf-8')
 manifest={'schema':'projects.passive-workshop-full.v1','sourceCommit':'c97ea4a3','sameEmbeddedProgressionGraph':True,'sourceGraphSha256':hashlib.sha256((BASE/'graph.json').read_bytes()).hexdigest(),'displayProjectionOnly':True,'scope':'All 645 nodes, 952 edges, 47 groups, existing pan/zoom and progression preview. Five mirrored starts.','nativeMinecraft':False,'activeSkillsObtained':False,'originalsChanged':False}
 (OUT/'proposal-manifest.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2),encoding='utf-8');print(json.dumps(audit,ensure_ascii=False))
if __name__=='__main__':main()
