"""Create a separate UI proposal without altering the approved reference or graph."""
import base64,hashlib,json,re
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1];BASE=ROOT/'assets/core-ui/large-tree-preview';OUT=BASE/'proposals/workshop-v1'
html=(BASE/'ProjectS_LargeTree_Route_Preview.html').read_text(encoding='utf-8');css=(OUT/'proposal.css').read_text(encoding='utf-8');js=(OUT/'proposal.js').read_text(encoding='utf-8')
world=ROOT/'assets/ui/polish05-import/assets/images/world.png';css=':root{--proposal-world:url(data:image/png;base64,'+base64.b64encode(world.read_bytes()).decode()+')}\n'+css
html=html.replace('<title>','<title>別UI提案 / ',1).replace('</body>','<style>'+css+'</style><script>'+js+'</script></body>')
target=OUT/'ProjectS_Passive_Workshop_Proposal.html';target.write_text(html,encoding='utf-8')
graph=json.loads((BASE/'graph.json').read_text(encoding='utf-8'));embedded=json.loads(re.search(r'<script id="treeData" type="application/json">(.*?)</script>',html,re.S).group(1));assert embedded==graph
manifest={'schema':'projects.passive-workshop-ui-proposal.v1','sourceCommit':'341037fa','graphSha256':hashlib.sha256((BASE/'graph.json').read_bytes()).hexdigest(),'sameFullGraph':True,'nodes':len(graph['nodes']),'edges':len(graph['edges']),'sourceReference':'assets/ui/polish05-import/reference/screens/forge_initial.png','displayScope':'Five symmetric start medallions; selected origin first-3pt braid with 10 nodes and all existing local edges. Full 47-region layout is not redesigned here.','approvedOriginalsChanged':False,'activeSkillsObtained':False,'sixthOriginAdded':False,'nativeMinecraft':False}
(OUT/'proposal-manifest.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2),encoding='utf-8');print(json.dumps({'bytes':target.stat().st_size,'sameFullGraph':True,'sourceNodes':len(graph['nodes']),'sourceEdges':len(graph['edges'])}))
