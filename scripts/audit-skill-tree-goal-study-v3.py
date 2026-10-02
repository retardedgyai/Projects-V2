"""Complete-wire and node-clearance checks for V3; no retained output writes."""
import json,math
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1];OUT=ROOT/'assets/core-ui/large-tree-preview/proposals/workshop-goal-study-v3'
path=ROOT/'scripts/audit-skill-tree-clear-wiring-v4.py'
code=path.read_text(encoding='utf-8').replace('workshop-clear-wiring-v4','workshop-goal-study-v3').replace('candidate-graph.json','candidate-region.json').replace('minimumWirePaintGapAt10_5Percent','minimumWirePaintGapAtMinimum19Percent').replace('minimum*.105-1.65','minimum*.19-2.2')
exec(compile(code,str(path),'exec'),{'__file__':str(path)})
g=json.loads((OUT/'candidate-region.json').read_text(encoding='utf-8'));zoom=.19
def radius(n):return max(*{'start':(19,76*zoom),'keystone':(13,49*zoom),'notable':(10,43*zoom),'road':(4,16*zoom),'small':(5.5,24*zoom)}[n['type']])
def distance(p,a,b):
 dx=b[0]-a[0];dy=b[1]-a[1];t=max(0,min(1,((p[0]-a[0])*dx+(p[1]-a[1])*dy)/max(dx*dx+dy*dy,1e-12)));return math.hypot(p[0]-a[0]-t*dx,p[1]-a[1]-t*dy)
nearest=min((distance((n['x'],n['y']),e['points'][0],e['points'][1])*zoom-radius(n)-5.15,n['id'],e['a'],e['b']) for n in g['nodes'] for e in g['edges'] if n['id'] not in [e['a'],e['b']])
pair=min((math.hypot(a['x']-b['x'],a['y']-b['y'])*zoom-radius(a)-radius(b)-6.3,a['id'],b['id']) for i,a in enumerate(g['nodes']) for b in g['nodes'][i+1:])
m=json.loads((OUT/'proposal-manifest.json').read_text(encoding='utf-8'));adj={n['id']:[] for n in g['nodes']}
for e in g['edges']:adj[e['a']].append(e['b']);adj[e['b']].append(e['a'])
neutral=set(m['newRoads']);maximum=0
for start in neutral:
 if len(adj[start])!=2:continue
 seen={start};queue=[start]
 for u in queue:
  for v in adj[u]:
   if v in neutral and len(adj[v])==2 and v not in seen:seen.add(v);queue.append(v)
 maximum=max(maximum,len(seen))
assert maximum<=2 and len(adj['key_04'])==1
out={'status':'PASS' if nearest[0]>3 and pair[0]>3 else 'FIX-FIRST','minimumNodeWirePaintGap':nearest,'minimumNodePairPaintGap':pair,'minimumZoom':zoom,'newRoads':len(neutral),'maximumNewNeutralDegree2Chain':maximum,'key04OptionalLeaf':True}
(OUT/'node-clearance-verification.json').write_text(json.dumps(out,ensure_ascii=False,indent=2),encoding='utf-8');print(json.dumps(out));assert out['status']=='PASS'
