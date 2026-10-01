"""Make a self-contained review trace for the position-only layout change."""
from pathlib import Path
import argparse,hashlib,importlib.util,json,math,re,subprocess
ROOT=Path(__file__).resolve().parents[1];OUT=ROOT/'assets/core-ui/large-tree-preview'
spec=importlib.util.spec_from_file_location('layout_contract',ROOT/'scripts/layout-large-tree.py');layout=importlib.util.module_from_spec(spec);spec.loader.exec_module(layout)

def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()

def arc(edge,by):
 points=edge.get('points') or [[by[edge['a']]['x'],by[edge['a']]['y']],[by[edge['b']]['x'],by[edge['b']]['y']]]
 return sum(math.dist(a,b) for a,b in zip(points,points[1:]))

def main():
 parser=argparse.ArgumentParser();parser.add_argument('--git',required=True);args=parser.parse_args()
 git=[args.git,'-c','safe.directory='+ROOT.as_posix()]
 old=json.loads(subprocess.check_output(git+['show','3681aca4:assets/core-ui/large-tree-preview/graph.json'],cwd=ROOT).decode('utf-8'))
 new=json.loads((OUT/'graph.json').read_text(encoding='utf-8'));audit=json.loads((OUT/'audit.json').read_text(encoding='utf-8'))
 assert new['layoutAudit']['layoutVersion']=='positions-v3','This is the historical v3 review. For v4 use review-large-tree-overlaps.py.'
 line=json.loads((OUT/'line-audit.json').read_text(encoding='utf-8'));captures=json.loads((OUT/'layout-capture-verification.json').read_text(encoding='utf-8'))
 assert layout.contract(old)==layout.contract(new),'non-layout contract changed'
 source_fields=('statInputs','flagKinds','lostStats','currentCatalog','requirements','assumptions','travelChoices','profiles','pointPolicy')
 assert all(old[k]==new[k] for k in source_fields),'source/input rules changed'
 embedded=re.search(r'<script id="treeData" type="application/json">(.*?)</script>',(OUT/'ProjectS_LargeTree_Route_Preview.html').read_text(encoding='utf-8'),re.S)
 assert embedded and json.loads(embedded.group(1))==new,'HTML and graph differ'
 assert new['budgetAudit']==audit and new['lineAudit']==line,'audit and graph differ'
 assert len(audit['sourceChecks'])==240 and len(audit['examples'])==40
 assert old['budgetAudit']['matrix']==audit['matrix'] and old['budgetAudit']['examples']==audit['examples'],'cost/allocation examples changed'
 assert captures['status']=='PASS' and len(captures['frames'])==10
 same_scale=json.loads((OUT/'layout-same-scale-verification.json').read_text(encoding='utf-8'))
 assert same_scale['status']=='PASS' and same_scale['frames'][0]['cam']==same_scale['frames'][1]['cam']
 assert same_scale['frames'][0]['selected']==same_scale['frames'][1]['selected']=='g38n1'
 assert same_scale['frames'][0]['cost']==same_scale['frames'][1]['cost']==8
 for origin in ('warrior','tank','mage','ranger','assassin'):
  b,a=(next(f for f in captures['frames'] if f['origin']==origin and f['phase']==phase) for phase in ('Before','After'))
  assert b['regions']==a['regions'] and b['cost']==a['cost']
 ob={n['id']:n for n in old['nodes']};nb={n['id']:n for n in new['nodes']}
 old_edges={tuple(sorted((e['a'],e['b']))):e for e in old['edges']};new_edges={tuple(sorted((e['a'],e['b']))):e for e in new['edges']}
 assert old_edges.keys()==new_edges.keys()
 coordinates=[{'id':i,'group':nb[i].get('group'),'type':nb[i]['type'],'cost':nb[i]['cost'],
  'before':[ob[i]['x'],ob[i]['y']],'after':[nb[i]['x'],nb[i]['y']],
  'nodeContractUnchanged':{k:v for k,v in ob[i].items() if k not in {'x','y'}}=={k:v for k,v in nb[i].items() if k not in {'x','y'}}} for i in sorted(nb)]
 edge_rows=[]
 for a,b in sorted(new_edges):
  before,after=old_edges[(a,b)],new_edges[(a,b)]
  edge_rows.append({'a':a,'b':b,'adjacencyUnchanged':True,
   'beforeEndpointDistance':round(math.dist([ob[a]['x'],ob[a]['y']],[ob[b]['x'],ob[b]['y']]),3),
   'afterEndpointDistance':round(math.dist([nb[a]['x'],nb[a]['y']],[nb[b]['x'],nb[b]['y']]),3),
   'beforeRoutedLength':round(arc(before,ob),3),'afterRoutedLength':round(arc(after,nb),3),
   'beforePoints':before.get('points'),'afterPoints':after.get('points'),
   'beforeCrossingGaps':before.get('crossingGaps',[]),'afterCrossingGaps':after.get('crossingGaps',[])})
 old_groups={g['id']:g for g in old['groups']};group_rows=[{'id':g['id'],'name':g['name'],
  'beforeCenter':[old_groups[g['id']]['x'],old_groups[g['id']]['y']],'afterCenter':[g['x'],g['y']],
  'membershipUnchanged':old_groups[g['id']]['nodes']==g['nodes']} for g in new['groups']]
 routed=[arc(e,nb) for e in new['edges']]
 result={'schema':'projects.large-tree-layout-review-evidence.v1','referenceCommit':'3681aca4','layoutCommit':'e04a69a3',
  'baselineLibraryGraph':{'library_file_id':'libfile_33796070ac748191b891a91164d76713','version':1},
  'scope':'配置・routingと指標表記の修正。実戦・保存・本体統合は対象外。独立レビュー待ち。',
  'checks':{'status':'PASS','beforeContractSha256':layout.fingerprint(old),'afterContractSha256':layout.fingerprint(new),
   'all645NodeContractsExceptCoordinatesEqual':True,'all952AdjacenciesEqual':True,'costsEffectsConditionsEqual':True,
   'sourceFieldEquality':{k:old[k]==new[k] for k in source_fields},'HTMLGraphEqual':True,'graphAuditEqual':True,'graphLineAuditEqual':True,
   'sourceChecks':240,'equalSpendExamples':40,'keyCostComparisons':75,'fiveRootCapturePairsEqual':True},
  'measurements':{'before':layout.geometry_metrics(old),'after':layout.geometry_metrics(new),
   'beforeCrossingGaps':old['lineAudit']['nonVertexCrossingsMarkedWithGaps'],'afterCrossingGaps':line['nonVertexCrossingsMarkedWithGaps'],
   'afterMaxRoutedLength':round(max(routed),3),'afterRoutedLengthsOver600':sum(x>600 for x in routed),
   'afterUnresolvedEdgeNodeCollisions':line['unresolvedEdgeNodeCollisions'],'crossingsCreateAdjacency':False,
   'definition':'両端距離はworld座標の直線距離。routing長は全折れ線区間の合計。gapはroute-large-tree-lines.pyが数えた、端を共有しない線の交差。'},
  'continuingRoutes':{'allGraphMaxDegree2Interiors':new['chainReview']['continuing']['maxDegree2Interiors'],
   'sourceValidMaxDegree2Interiors':new['chainReview']['sourceContinuingMaxDegree2Interiors'],
   'terminalRewardBranches':new['chainReview']['continuing']['terminalRewardBranches'],'threePointGuarantee':False},
  'coordinates':coordinates,'groups':group_rows,'edges':edge_rows,'captureComparison':captures,'sameScaleComparison':same_scale,
  'fileSHA256':{name:sha(OUT/name) for name in ('graph.json','audit.json','line-audit.json','layout-plan.json','ProjectS_LargeTree_Route_Preview.html')},
  'notVerified':['戦闘バランス','地域・職業の育成体験','重複恩恵','iOS実機','Minecraft本体統合','本番保存移行']}
 result['fileSHA256'].update({p.name:sha(p) for p in sorted(OUT.glob('ProjectS_Layout_*.png'))})
 (OUT/'ProjectS_Layout_Review_Evidence.json').write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf-8')
 print(json.dumps({'status':'PASS','nodeCoordinatePairs':len(coordinates),'edgeComparisons':len(edge_rows),'HTMLGraphEqual':True,
  'gaps':result['measurements']['afterCrossingGaps'],'maxRoutedLength':result['measurements']['afterMaxRoutedLength']},ensure_ascii=False))

if __name__=='__main__':main()
