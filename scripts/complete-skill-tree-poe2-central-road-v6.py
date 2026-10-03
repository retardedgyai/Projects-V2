"""Place every retained road ID along existing routes, keeping short road-only runs."""
import json,math,collections
from pathlib import Path
import numpy as np
ROOT=Path(__file__).resolve().parents[1];OUT=ROOT/'assets/core-ui/large-tree-preview/proposals/workshop-poe2-central-v6';g=json.loads((OUT/'candidate-graph.json').read_text(encoding='utf8'));p=json.loads((OUT/'central-authoring.json').read_text(encoding='utf8'));pool=p['remainingRoadPool'];b={n['id']:n for n in g['nodes']};placements=[]
def xy(id):return np.array([b[id]['x'],b[id]['y']])
def adjacency():
 a={i:[] for i in b}
 for e in g['edges']:a[e['a']].append(e['b']);a[e['b']].append(e['a'])
 return a
def runsize(e,ad):
 seen=set();q=[e['a'],e['b']]
 for u in q:
  if u in seen or b[u]['type']!='road' or len(ad[u])!=2:continue
  seen.add(u);q.extend(v for v in ad[u] if v not in seen)
 return len(seen)+1
while pool:
 ad=adjacency();allsegments=[(ei,np.array(a),np.array(c)) for ei,e in enumerate(g['edges']) for a,c in zip(e['points'],e['points'][1:])];A=np.array([x[1] for x in allsegments]);D=np.array([x[2]-x[1] for x in allsegments]);den=(D*D).sum(axis=1);owners=np.array([x[0] for x in allsegments]);candidates=sorted(((sum(np.linalg.norm(np.array(c)-np.array(a)) for a,c in zip(e['points'],e['points'][1:])),ei) for ei,e in enumerate(g['edges']) if runsize(e,ad)<=2),reverse=True);done=False
 for length,ei in candidates:
  if length<280:continue
  e=g['edges'][ei];pts=[np.array(q) for q in e['points']];lengths=[np.linalg.norm(c-a) for a,c in zip(pts,pts[1:])];cum=np.cumsum([0]+lengths)
  for f in [.5,.4,.6,.3,.7]:
   target=length*f;j=int(np.searchsorted(cum,target,side='right')-1);v=pts[j]+(pts[j+1]-pts[j])*(target-cum[j])/lengths[j]
   if any(i not in pool and np.linalg.norm(v-xy(i))<({'start':160,'keystone':145,'notable':125,'small':115,'road':115}[n['type']]) for i,n in b.items()):continue
   delta=v-A;t=np.clip((delta*D).sum(axis=1)/den,0,1);dist=np.sqrt(((delta-t[:,None]*D)**2).sum(axis=1));dist[owners==ei]=float('inf')
   if np.min(dist)<85:continue
   id=pool.pop(0);coord=[round(float(v[0]),5),round(float(v[1]),5)];b[id]['x'],b[id]['y']=coord;left=e['points'][:j+1]+[coord];right=[coord]+e['points'][j+1:];g['edges'].pop(ei)
   for a,c,path in [(e['a'],id,left),(id,e['b'],right)]:g['edges'].append(dict(a=a,b=c,road=True,proposalKind=e['proposalKind'],points=path,crossingGaps=[]))
   placements.append({'id':id,'edge':[e['a'],e['b']],'fraction':f});done=True;break
  if done:break
 if not done:raise ValueError(('No clear short-run road placement',pool))
p.update(remainingRoadPool=pool,roadRedistribution=placements);g.update(fullTreeLayoutComplete=True,layoutStage='poe2-central-v6-complete',scope='5職業の起点を中央の共有網に集約。異なる47領域と全645効果を保持。')
(OUT/'candidate-graph.json').write_text(json.dumps(g,ensure_ascii=False,indent=2),encoding='utf8');(OUT/'central-authoring.json').write_text(json.dumps(p,ensure_ascii=False,indent=2),encoding='utf8');print(json.dumps({'placed':len(placements),'nodes':len(g['nodes']),'edges':len(g['edges'])}))
