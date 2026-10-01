"""Trace the overlap correction against the exact image reviewed at 14:01."""
import argparse,hashlib,importlib.util,json,math,re,subprocess
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1];OUT=ROOT/'assets/core-ui/large-tree-preview'
spec=importlib.util.spec_from_file_location('layout',ROOT/'scripts/layout-large-tree.py');layout=importlib.util.module_from_spec(spec);spec.loader.exec_module(layout)
parser=argparse.ArgumentParser();parser.add_argument('--git',required=True);args=parser.parse_args()
git=[args.git,'-c','safe.directory='+ROOT.as_posix()]
before=json.loads(subprocess.check_output(git+['show','e04a69a3:assets/core-ui/large-tree-preview/graph.json'],cwd=ROOT).decode())
after=json.loads((OUT/'graph.json').read_text(encoding='utf-8'));views=json.loads((OUT/'overlap-verification.json').read_text(encoding='utf-8'))
assert layout.contract(before)==layout.contract(after)
source_fields=('statInputs','flagKinds','lostStats','currentCatalog','requirements','assumptions','travelChoices','profiles','pointPolicy')
assert all(before[k]==after[k] for k in source_fields)
assert before['budgetAudit']==after['budgetAudit'] and before['chainReview']==after['chainReview']
embedded=re.search(r'<script id="treeData" type="application/json">(.*?)</script>',(OUT/'ProjectS_LargeTree_Route_Preview.html').read_text(encoding='utf-8'),re.S)
assert json.loads(embedded.group(1))==after
assert views['status']=='PASS'
for v in views['frames']:
 if v['phase'] in ('After','Close','Overview'):assert not any(v[k] for k in ('labelPairs','labelNodes','labelLines','nodePairs','unplaced'))
b={n['id']:n for n in before['nodes']};a={n['id']:n for n in after['nodes']}
coordinates=[{'id':i,'before':[b[i]['x'],b[i]['y']],'after':[a[i]['x'],a[i]['y']],'distance':round(math.dist([b[i]['x'],b[i]['y']],[a[i]['x'],a[i]['y']]),3)} for i in sorted(a)]
lengths=[sum(math.dist(x,y) for x,y in zip(e['points'],e['points'][1:])) for e in after['edges']]
assert max(lengths)<=600 and not after['lineAudit']['unresolvedEdgeNodeCollisions']
result={'schema':'projects.large-tree-overlap-review-evidence.v1','referenceCommit':'e04a69a3','referenceLibraryGraph':{'library_file_id':'libfile_33796070ac748191b891a91164d76713','version':2},
 'status':'PASS','contractSha256':layout.fingerprint(after),'nodeContractsEdgesCostsEffectsInputsEqual':True,'sourceFieldsEqual':source_fields,'HTMLGraphEqual':True,'budgetAndChainAuditsEqual':True,
 'captionPolicy':'Preserve all captions, keep them near their node, reserve DOM controls, and draw opaque 2px-padded backgrounds. Strokes behind text rectangles are concealed; complete edges are not removed. This is distinct from geometric edge crossing gaps.',
 'before':layout.geometry_metrics(before),'after':layout.geometry_metrics(after),'changedCoordinatesOver0_1':sum(c['distance']>.1 for c in coordinates),'maxCoordinateMove':max(c['distance'] for c in coordinates),
 'maxRoutedLength':round(max(lengths),3),'routedLengthsOver600':0,'lineAudit':after['lineAudit'],'coordinates':coordinates,'views':views,
 'scope':'Standalone display review. Native Minecraft integration, combat balance and save migration remain unimplemented.',
 'fileSHA256':{p.name:hashlib.sha256(p.read_bytes()).hexdigest() for p in [OUT/'graph.json',OUT/'layout-plan.json',OUT/'line-audit.json',OUT/'preview-labels.js',OUT/'ProjectS_LargeTree_Route_Preview.html',*sorted(OUT.glob('ProjectS_Overlap_*.png'))]}}
if (OUT/'fixed-ui-verification.json').exists():
 fixed=json.loads((OUT/'fixed-ui-verification.json').read_text(encoding='utf-8'));assert fixed['status']=='PASS'
 fixed_reference=json.loads(subprocess.check_output(git+['show','7bbd730d:assets/core-ui/large-tree-preview/graph.json'],cwd=ROOT).decode())
 assert after==fixed_reference,'fixed UI correction changed graph data'
 result['fixedUI']=fixed;result['fixedUIReferenceCommit']='7bbd730d';result['fixedUIChangedGraphData']=False
 result['fileSHA256'].update({p.name:hashlib.sha256(p.read_bytes()).hexdigest() for p in (OUT/'fixed-ui-verification.json',OUT/'preview.css')})
(OUT/'ProjectS_Overlap_Review_Evidence.json').write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps({k:result[k] for k in ('status','changedCoordinatesOver0_1','maxCoordinateMove','maxRoutedLength','routedLengthsOver600')}))
