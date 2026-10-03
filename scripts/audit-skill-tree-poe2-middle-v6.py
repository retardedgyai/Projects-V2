"""Verify the requested protected structures against a retained V6 baseline."""
import json,sys,math,hashlib
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1];OUT=ROOT/'assets/core-ui/large-tree-preview/proposals/workshop-poe2-central-v6';CAP=ROOT/'.tools/middle-band-review-20261003';before_path=Path(sys.argv[1]) if len(sys.argv)>1 else CAP/'before-graph.json';a=json.loads(before_path.read_text(encoding='utf8'));b=json.loads((OUT/'candidate-graph.json').read_text(encoding='utf8'));p=json.loads((OUT/'central-authoring.json').read_text(encoding='utf8'));fixed=set(p['centralOpeningIds']);ba={n['id']:n for n in a['nodes']};bb={n['id']:n for n in b['nodes']};regional={i:r['id'] for r in a['groups'] for i in r['nodes']}
assert a['groups']==b['groups'] and a['origins']==b['origins'] and a['bounds']==b['bounds']
assert len(a['edges'])==len(b['edges'])==784
for id in fixed|set(regional):assert ba[id]==bb[id],id
for id in ba:assert {k:v for k,v in ba[id].items() if k not in ('x','y')}=={k:v for k,v in bb[id].items() if k not in ('x','y')},id
changed=[];attachments=[]
for e,f in zip(a['edges'],b['edges']):
 assert {k:v for k,v in e.items() if k not in ('points','a','b')}=={k:v for k,v in f.items() if k not in ('points','a','b')}
 if (e['a'],e['b'])!=(f['a'],f['b']):attachments.append({'before':[e['a'],e['b']],'after':[f['a'],f['b']]})
 if e['a'] in fixed or e['b'] in fixed or e['a'] in regional and regional.get(e['a'])==regional.get(e['b']):assert e==f,[e['a'],e['b']]
 if e!=f:changed.append([e['a'],e['b']])
def depart(graph,u,v):
 e=next(e for e in graph['edges'] if {e['a'],e['b']}=={u,v});q=e['points'] if e['a']==u else e['points'][::-1];return math.atan2(q[1][1]-q[0][1],q[1][0]-q[0][0])
def wedge(graph):
 x=abs(depart(graph,'g13n8','g13n7')-depart(graph,'g13n8','r6_35_2'));return math.degrees(min(x,2*math.pi-x))
assert len(attachments)==1 and set(attachments[0]['before'])=={'r2_22_1','g23n5'} and set(attachments[0]['after'])=={'r2_22_1','g18n5'}
report={'status':'PASS','baselineSha256':hashlib.sha256(before_path.read_bytes()).hexdigest(),'candidateSha256':hashlib.sha256((OUT/'candidate-graph.json').read_bytes()).hexdigest(),'central60NodesAndIncidentEdgesExact':True,'all47RegionalNodesAndInternalEdgesExact':True,'all645NonGeometryFieldsExact':True,'onlyOneSpecifiedTravelAttachmentChanged':attachments,'changedTravelEdges':changed,'movedRoadNodes':[id for id in ba if ba[id]!=bb[id]],'southwestDepartingAngleBeforeDegrees':wedge(a),'southwestDepartingAngleAfterDegrees':wedge(b),'visualJudgment':'See same-camera whole, west, and southwest actual-browser comparisons; angle metric alone does not establish composition quality.'}
assert report['southwestDepartingAngleAfterDegrees']>report['southwestDepartingAngleBeforeDegrees']
(OUT/'middle-band-verification.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf8');print(json.dumps({k:v for k,v in report.items() if k not in ('changedTravelEdges','movedRoadNodes','visualJudgment')}))
