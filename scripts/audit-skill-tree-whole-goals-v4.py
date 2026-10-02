"""Audit real full-tree geometry; no old output paths are evaluated."""
import json,math
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1];OUT=ROOT/'assets/core-ui/large-tree-preview/proposals/workshop-whole-goals-v4';g=json.loads((OUT/'candidate-graph.json').read_text(encoding='utf-8'));by={n['id']:n for n in g['nodes']}
segments=[(i,e['a'],e['b'],e['points'][0],e['points'][-1]) for i,e in enumerate(g['edges'])];crosses=[];overlaps=[];close=[]
def dist(p,a,b):
 dx=b[0]-a[0];dy=b[1]-a[1];t=max(0,min(1,((p[0]-a[0])*dx+(p[1]-a[1])*dy)/max(dx*dx+dy*dy,1e-12)));return math.hypot(p[0]-a[0]-t*dx,p[1]-a[1]-t*dy)
for i,a,b,p,q in segments:
 for j,c,d,r,t in segments[i+1:]:
  u=(q[0]-p[0],q[1]-p[1]);v=(t[0]-r[0],t[1]-r[1]);w=(r[0]-p[0],r[1]-p[1]);den=u[0]*v[1]-u[1]*v[0]
  if abs(den)>1e-8:
   x=(w[0]*v[1]-w[1]*v[0])/den;y=(w[0]*u[1]-w[1]*u[0])/den
   if 1e-7<x<1-1e-7 and 1e-7<y<1-1e-7:crosses.append({'edges':[[a,b],[c,d]],'point':[p[0]+x*u[0],p[1]+x*u[1]]})
  elif abs(w[0]*u[1]-w[1]*u[0])<1e-5 and math.hypot(*u)>1e-5:
   l=u[0]*u[0]+u[1]*u[1];t0=(w[0]*u[0]+w[1]*u[1])/l;t1=((t[0]-p[0])*u[0]+(t[1]-p[1])*u[1])/l
   if min(1,max(t0,t1))-max(0,min(t0,t1))>1e-7:overlaps.append([[a,b],[c,d]])
 for n in g['nodes']:
  if n['id'] in [a,b]:continue
  clearance=dist((n['x'],n['y']),p,q)
  if clearance<65:close.append({'node':n['id'],'edge':[a,b],'distance':clearance})
out={'status':'FIX-FIRST' if crosses or overlaps or close else 'PASS','nodes':len(by),'regions':len(g['groups']),'crossings':crosses,'overlaps':overlaps,'nodeNearWires':sorted(close,key=lambda c:c['distance'])};(OUT/'geometry-verification.json').write_text(json.dumps(out,ensure_ascii=False,indent=2),encoding='utf-8');print(json.dumps({'status':out['status'],'nodes':len(by),'crossings':len(crosses),'overlaps':len(overlaps),'near':len(close),'firstCrossings':crosses[:20],'closest':out['nodeNearWires'][:15]},ensure_ascii=False))
