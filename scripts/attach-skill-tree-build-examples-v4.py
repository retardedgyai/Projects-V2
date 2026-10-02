"""Embed the nine verified examples into the self-contained V4 preview."""
import json,re
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1];OUT=ROOT/'assets/core-ui/large-tree-preview/proposals/workshop-clear-wiring-v4'
report=json.loads((OUT/'build-budget-comparison.json').read_text(encoding='utf-8'))
cases=[b for b in report['builds'] if b['version']=='proposal']
assert len(cases)==9 and all(b.get('buildCode') for b in cases)
html_path=OUT/'ProjectS_Passive_Clear_Wiring_V4.html';html=html_path.read_text(encoding='utf-8')
html=re.sub(r'<script id="buildExampleData".*?</script><script id="buildExampleUI">.*?</script>','',html,flags=re.S)
block='<script id="buildExampleData" type="application/json">'+json.dumps(cases,ensure_ascii=False,separators=(',',':'))+'</script><script id="buildExampleUI">'+(OUT/'build-examples.js').read_text(encoding='utf-8')+'</script>'
html_path.write_text(html.replace('</body>',block+'</body>'),encoding='utf-8')
print(json.dumps({'embeddedExamples':len(cases),'nativeMinecraft':False}))
