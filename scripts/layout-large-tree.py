"""Apply reviewed positions without changing graph, costs, input rules or rewards."""
from pathlib import Path
import argparse,hashlib,json,math
ROOT=Path(__file__).resolve().parents[1];OUT=ROOT/'assets/core-ui/large-tree-preview'
GROUP_GEOMETRY={'x','y','rx','ry','rot','outline','labelX','labelY'}

def contract(d):
 return {'nodes':sorted(({k:v for k,v in n.items() if k not in {'x','y'}} for n in d['nodes']),key=lambda n:n['id']),
  'edges':sorted(({k:v for k,v in e.items() if k not in {'points','crossingGaps'}} for e in d['edges']),key=lambda e:(e['a'],e['b'])),
  'groups':sorted(({k:v for k,v in g.items() if k not in GROUP_GEOMETRY} for g in d['groups']),key=lambda g:g['id']),
  **{k:d[k] for k in ('origins','profiles','travelChoices','pointPolicy')}}

def fingerprint(d):
 return hashlib.sha256(json.dumps(contract(d),ensure_ascii=False,sort_keys=True,separators=(',',':')).encode()).hexdigest()

def geometry_metrics(d):
 by={n['id']:n for n in d['nodes']};lengths=[]
 for e in d['edges']:
  a,b=by[e['a']],by[e['b']];lengths.append(math.hypot(a['x']-b['x'],a['y']-b['y']))
 return {'nodes':len(by),'clusters':len(d['groups']),'edges':len(lengths),
  'endpointDistanceOver600':sum(x>600 for x in lengths),'endpointDistanceOver1000':sum(x>1000 for x in lengths),
  'maxEndpointDistance':round(max(lengths),3),'worldWidth':round(max(n['x'] for n in by.values())-min(n['x'] for n in by.values()),3),
  'worldHeight':round(max(n['y'] for n in by.values())-min(n['y'] for n in by.values()),3)}

def main():
 parser=argparse.ArgumentParser();parser.add_argument('--capture-plan',type=Path);parser.add_argument('--reference',type=Path);args=parser.parse_args()
 plan_path=OUT/'layout-plan.json'
 if args.capture_plan:
  candidate=json.loads(args.capture_plan.read_text(encoding='utf-8'));reference=json.loads(args.reference.read_text(encoding='utf-8'))
  assert contract(candidate)==contract(reference),'layout changed the graph/input/reward contract'
  plan={'schema':'projects.large-tree-layout-plan.v1','referenceCommit':'3681aca4','contractSha256':fingerprint(reference),
   'referenceMetrics':geometry_metrics(reference),'referenceCrossingGaps':reference['lineAudit']['nonVertexCrossingsMarkedWithGaps'],
   'positionPolicy':'47の地域を保ち、領域内の点をまとめる。戦士の3接続先と近い分岐・合流を配置し、橋の両端を実際に近づける。折れ線による距離の隠蔽はしない。',
   'nodes':[[n['id'],n['x'],n['y']] for n in candidate['nodes']],
   'groups':{g['id']:{k:v for k,v in g.items() if k in GROUP_GEOMETRY} for g in candidate['groups']}}
  plan_path.write_text(json.dumps(plan,ensure_ascii=False,indent=2),encoding='utf-8');print('Captured position-only layout plan');return
 d=json.loads((OUT/'graph.json').read_text(encoding='utf-8'));plan=json.loads(plan_path.read_text(encoding='utf-8'));before=contract(d)
 assert fingerprint(d)==plan['contractSha256'],'layout plan belongs to a different graph/input/reward contract'
 by={n['id']:n for n in d['nodes']};groups={g['id']:g for g in d['groups']}
 assert {r[0] for r in plan['nodes']}==set(by) and set(plan['groups'])==set(groups)
 for i,x,y in plan['nodes']:by[i]['x']=x;by[i]['y']=y
 for i,geometry in plan['groups'].items():groups[i].update(geometry)
 for e in d['edges']:e.pop('points',None);e.pop('crossingGaps',None)
 d['bounds']={'minX':min(n['x'] for n in by.values())-130,'maxX':max(n['x'] for n in by.values())+130,
  'minY':min(n['y'] for n in by.values())-130,'maxY':max(n['y'] for n in by.values())+130}
 assert contract(d)==before and fingerprint(d)==plan['contractSha256']
 audit={'schema':'projects.large-tree-layout-audit.v1','referenceCommit':plan['referenceCommit'],'layoutVersion':'positions-v3',
  'contractSha256':plan['contractSha256'],'nodesEdgesCostsEffectsInputsUnchanged':True,
  'before':plan['referenceMetrics'],'after':geometry_metrics(d),'beforeCrossingGaps':plan['referenceCrossingGaps'],
  'method':'ノードと地域の実位置を再配置。隣接・費用・効果・入力・47地域は同一。長い線の折れ線化だけで短く見せていない。',
  'visualGate':'拡大前後と線routing後の交差を確認する。距離値だけでは合格にしない。','nativeMinecraft':False,'balanceVerified':False}
 d['layoutAudit']=audit
 (OUT/'graph.json').write_text(json.dumps(d,ensure_ascii=False,indent=2),encoding='utf-8')
 (OUT/'layout-audit.json').write_text(json.dumps(audit,ensure_ascii=False,indent=2),encoding='utf-8')
 print(json.dumps(audit,ensure_ascii=False))

if __name__=='__main__':main()
