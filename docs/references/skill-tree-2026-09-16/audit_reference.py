from pathlib import Path
import json,collections
root=Path(__file__).parent
d=json.loads((root/'v6-graph-original.json').read_text(encoding='utf-8'))
by={n['id']:n for n in d['nodes']};adj={i:[] for i in by};unknown=[]
for e in d['edges']:
 if e['a'] not in by or e['b'] not in by:unknown.append(e);continue
 adj[e['a']].append(e['b']);adj[e['b']].append(e['a'])
assert not unknown
assert len(by)==len(d['nodes'])
assert len({tuple(sorted((e['a'],e['b']))) for e in d['edges']})==len(d['edges'])
lanes=[];origins=[]
for origin in d['origins']:
 root_id=origin['root'];dist={root_id:0};q=collections.deque([root_id])
 while q:
  here=q.popleft()
  if by[here]['type']=='keystone':continue
  for nxt in adj[here]:
   n=by[nxt]
   if nxt in dist or n['type']=='mastery' or (n['type']=='start' and nxt!=root_id):continue
   dist[nxt]=dist[here]+1;q.append(nxt)
 for lane in origin['lanes']:
  assert all(by[i]['stats'].get(lane['stat'],0)>0 for i in lane['nodes'])
  assert [dist[i] for i in lane['nodes']]==[1,2,3]
  lanes.append({'origin':origin['id'],'primary':lane['stat'],'nodes':lane['nodes'],'first_three_same_primary':True})
 keys=[{'id':n['id'],'name':n['name'],'cost':dist.get(n['id']),'within_old_44':dist.get(n['id'],10000)<=44} for n in d['nodes'] if n['type']=='keystone']
 noteworthy=[dist[n['id']] for n in d['nodes'] if n['type']=='notable' and n['id'] in dist]
 all_notables=sum(n['type']=='notable' for n in d['nodes'])
 origins.append({'origin':origin['id'],'reachable_path_nodes':len(dist),'notable_count':all_notables,'notables_within_old_44':sum(v<=44 for v in noteworthy),'notable_max_cost':max(noteworthy),'keystones':keys})
patterns=collections.Counter(g['pattern'] for g in d['groups'])
themes=collections.Counter(g['theme'] for g in d['groups'])
missing_rules=[]
for n in d['nodes']:
 if n['type']=='keystone' and not n.get('desc'):missing_rules.append(n['id'])
report={'schema':'projects.skill-tree-reference-audit.v1','historical_only':True,'production_budget_unfixed':True,'duplicate_node_ids':0,'duplicate_edges':0,'unknown_edge_endpoints':unknown,'nodes':len(d['nodes']),'edges':len(d['edges']),'clusters':len(d['groups']),'patterns':dict(patterns),'themes':dict(themes),'opening_lanes':lanes,'origin_costs':origins,'missing_keystone_descriptions':missing_rules,'runtime_applied':False,'parent_user_evidence':'9/16 20:21 UTC: fixed profession route rejected; route value emphasized (parent-reported; no independent transcript access)'}
(root/'v6-structure-audit.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps({'patterns':dict(patterns),'themes':dict(themes),'same_primary_3pt_lanes':len(lanes),'origin_costs':origins},ensure_ascii=False))
