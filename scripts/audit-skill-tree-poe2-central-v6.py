"""Audit every actual polyline segment and the central start requirement."""
import json,math,collections
from pathlib import Path
import numpy as np
ROOT=Path(__file__).resolve().parents[1];OUT=ROOT/'assets/core-ui/large-tree-preview/proposals/workshop-poe2-central-v6';g=json.loads((OUT/'candidate-graph.json').read_text(encoding='utf8'));p=json.loads((OUT/'central-authoring.json').read_text(encoding='utf8'));b={n['id']:n for n in g['nodes']};unused=set(p['remainingRoadPool']);segs=[];cells=collections.defaultdict(list)
for ei,e in enumerate(g['edges']):
 assert e['points'][0]==[b[e['a']]['x'],b[e['a']]['y']] and e['points'][-1]==[b[e['b']]['x'],b[e['b']]['y']]
 for si,(a,c) in enumerate(zip(e['points'],e['points'][1:])):
  ix=len(segs);segs.append((ei,si,e['a'],e['b'],a,c))
  for x in range(math.floor(min(a[0],c[0])/240),math.floor(max(a[0],c[0])/240)+1):
   for y in range(math.floor(min(a[1],c[1])/240),math.floor(max(a[1],c[1])/240)+1):cells[x,y].append(ix)
pairs=set()
for ids in cells.values():
 for k,i in enumerate(ids):
  for j in ids[k+1:]:pairs.add((min(i,j),max(i,j)))
crosses=[];overlaps=[]
for i,j in pairs:
 ei,si,a,c,P,Q=segs[i];ej,sj,d,f,R,T=segs[j]
 if ei==ej and abs(si-sj)<=1:continue
 ux,uy=Q[0]-P[0],Q[1]-P[1];vx,vy=T[0]-R[0],T[1]-R[1];wx,wy=R[0]-P[0],R[1]-P[1];den=ux*vy-uy*vx
 if abs(den)>1e-9:
  t=(wx*vy-wy*vx)/den;u=(wx*uy-wy*ux)/den
  if -1e-8<=t<=1+1e-8 and -1e-8<=u<=1+1e-8:
   hit=[P[0]+t*ux,P[1]+t*uy];shared={a,c}&{d,f}
   if any(math.hypot(hit[0]-b[k]['x'],hit[1]-b[k]['y'])<1e-3 for k in shared):continue
   crosses.append({'edges':[[a,c],[d,f]],'point':hit})
 elif abs(wx*uy-wy*ux)<1e-4:
  length=ux*ux+uy*uy
  if length<1e-10:continue
  t0=(wx*ux+wy*uy)/length;t1=((T[0]-P[0])*ux+(T[1]-P[1])*uy)/length
  if min(1,max(t0,t1))-max(0,min(t0,t1))>1e-5:overlaps.append([[a,c],[d,f]])
A=np.array([r[4] for r in segs]);B=np.array([r[5] for r in segs]);D=B-A;den=(D*D).sum(axis=1);near=[];minimum=float('inf')
for id,n in b.items():
 if id in unused:continue
 mask=np.array([id not in (r[2],r[3]) for r in segs]);v=np.array([n['x'],n['y']])-A;t=np.clip((v*D).sum(axis=1)/den,0,1);dist=np.sqrt(((v-t[:,None]*D)**2).sum(axis=1));dist[~mask]=float('inf');j=int(np.argmin(dist));minimum=min(minimum,float(dist[j]))
 if dist[j]<65-1e-5:near.append({'node':id,'edge':[segs[j][2],segs[j][3]],'distance':float(dist[j])})
roots=[b[o['root']] for o in g['origins']];center=np.array(p['center']);startRadius=max(float(np.linalg.norm(np.array([n['x'],n['y']])-center)) for n in roots);bounds=g['bounds'];mapCenter=np.array([(bounds['minX']+bounds['maxX'])/2,(bounds['minY']+bounds['maxY'])/2]);offset=float(np.linalg.norm(center-mapCenter));width=bounds['maxX']-bounds['minX'];height=bounds['maxY']-bounds['minY']
startRadii=[math.hypot(n['x']-center[0],n['y']-center[1]) for n in roots]
startAngles=sorted(math.degrees(math.atan2(n['y']-center[1],n['x']-center[0]))%360 for n in roots)
angleSteps=[(startAngles[(j+1)%5]-startAngles[j])%360 for j in range(5)]
voidDistances=[]
for _,_,_,_,a,c in segs:
 a=np.array(a);c=np.array(c);delta=c-a;t=np.clip(float((center-a)@delta)/float(delta@delta),0,1);voidDistances.append(float(np.linalg.norm(center-a-t*delta)))
regular=max(startRadii)-min(startRadii)<.001 and all(abs(x-72)<.001 for x in angleSteps)
voidClear=min(voidDistances)>=p['centralVoidRadius']
assert regular and voidClear
out=dict(regularStarts=regular,startRadii=startRadii,angularSpacing=angleSteps,centralVoidClear=voidClear,minWireRadiusFromCenter=min(voidDistances),status='PASS' if not crosses and not overlaps and not near and not unused else 'FIX-FIRST',nodes=645,placedNodes=645-len(unused),edges=len(g['edges']),actualSegments=len(segs),crossings=crosses,overlaps=overlaps,nodeNearWires=near,minNodeWireWorld=minimum,centralStartRadius=startRadius,centralStartDiameterFraction=max(2*startRadius/width,2*startRadius/height),centralClusterOffsetFromMapCenter=offset,centralCenterWithinTwoPercent=min(width,height)*.02>offset,remainingRoads=sorted(unused))
(OUT/'geometry-verification.json').write_text(json.dumps(out,ensure_ascii=False,indent=2),encoding='utf8');print(json.dumps({k:v for k,v in out.items() if k not in ['crossings','overlaps','nodeNearWires','remainingRoads']}));print(json.dumps({'crossings':crosses[:10],'overlaps':overlaps[:10],'nodeNearWires':near[:10]}))
