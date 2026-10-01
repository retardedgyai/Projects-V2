"""Keep topology intact and make unrelated line crossings visibly pass under."""
from pathlib import Path
import json,math
ROOT=Path(__file__).resolve().parents[1];OUT=ROOT/'assets/core-ui/large-tree-preview'
d=json.loads((OUT/'graph.json').read_text(encoding='utf-8'));by={n['id']:n for n in d['nodes']}
def projection(p,a,b):
 dx,dy=b[0]-a[0],b[1]-a[1];den=dx*dx+dy*dy;t=max(0,min(1,((p[0]-a[0])*dx+(p[1]-a[1])*dy)/den)) if den else 0
 return t,(a[0]+t*dx,a[1]+t*dy)
def distance(p,a,b):t,q=projection(p,a,b);return math.hypot(p[0]-q[0],p[1]-q[1])
def radius(n):return {'start':38,'notable':30,'keystone':39,'small':21,'road':12}[n['type']]+9
unresolved=[];changed=0
for ei,e in enumerate(d['edges']):
 points=e.get('points') or [[by[e['a']]['x'],by[e['a']]['y']],[by[e['b']]['x'],by[e['b']]['y']]]
 points=[list(p) for p in points]
 for iteration in range(12):
  collision=None
  for s,(a,b) in enumerate(zip(points,points[1:])):
   for n in d['nodes']:
    if n['id'] in (e['a'],e['b']):continue
    if distance((n['x'],n['y']),a,b)<radius(n):collision=(s,a,b,n);break
   if collision:break
  if not collision:break
  s,a,b,n=collision;t,p=projection((n['x'],n['y']),a,b);dx=b[0]-a[0];dy=b[1]-a[1];length=math.hypot(dx,dy)
  if length<1:break
  candidates=[]
  for sign in (1,-1):
   for offset in (radius(n)+28,radius(n)+70,radius(n)+130):
    q=[p[0]-dy/length*offset*sign,p[1]+dx/length*offset*sign]
    hits=sum(distance((other['x'],other['y']),x,y)<radius(other) for other in d['nodes'] if other['id'] not in (e['a'],e['b']) for x,y in ((a,q),(q,b)))
    candidates.append((hits,offset,q))
  hits,_,q=min(candidates,key=lambda c:(c[0],c[1]));points.insert(s+1,q);changed+=1
 if collision:unresolved.append({'edge':[e['a'],e['b']],'near':collision[3]['id']})
 e['points']=points
# No intersection creates adjacency. Mark a gap in one stroke at each non-vertex crossing.
segments=[]
for index,e in enumerate(d['edges']):
 e['crossingGaps']=[]
 for si,(a,b) in enumerate(zip(e['points'],e['points'][1:])):segments.append((index,si,a,b))
crossings=0
for idx,(i,si,a,b) in enumerate(segments):
 for j,sj,c,f in segments[idx+1:]:
  if i==j or {d['edges'][i]['a'],d['edges'][i]['b']}&{d['edges'][j]['a'],d['edges'][j]['b']}:continue
  ux,uy=b[0]-a[0],b[1]-a[1];vx,vy=f[0]-c[0],f[1]-c[1];den=ux*vy-uy*vx
  if abs(den)<1e-7:continue
  wx,wy=c[0]-a[0],c[1]-a[1];t=(wx*vy-wy*vx)/den;v=(wx*uy-wy*ux)/den
  if .01<t<.99 and .01<v<.99:d['edges'][i]['crossingGaps'].append({'segment':si,'t':round(t,6)});crossings+=1
audit={'schema':'projects.large-tree-line-audit.v1','routedCorrections':changed,'unresolvedEdgeNodeCollisions':unresolved,'nonVertexCrossingsMarkedWithGaps':crossings,'crossingsCreateAdjacency':False}
d['lineAudit']=audit;(OUT/'graph.json').write_text(json.dumps(d,ensure_ascii=False,indent=2),encoding='utf-8');(OUT/'line-audit.json').write_text(json.dumps(audit,ensure_ascii=False,indent=2),encoding='utf-8');print(json.dumps(audit,ensure_ascii=False))
if unresolved:raise SystemExit(1)
