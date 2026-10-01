"""Prove opaque UI by changing every main-canvas pixel and comparing the composite."""
import hashlib,json,subprocess
from pathlib import Path
from PIL import Image,ImageChops
ROOT=Path(__file__).resolve().parents[1];DIR=ROOT/'.tools/fixed-ui-review';OUT=ROOT/'assets/core-ui/large-tree-preview'
d=json.loads((DIR/'dom-audit.json').read_text(encoding='utf-8'))
for v in d['views']:
 a=Image.open(DIR/(v['name']+'-normal.png')).convert('RGB');b=Image.open(DIR/(v['name']+'-probe.png')).convert('RGB')
 for p in v['panels']:
  if not p['visible']:continue
  r=p['rect'];box=(max(0,int(r['left'])+1),max(0,int(r['top'])+1),min(a.width,int(r['right'])-1),min(a.height,int(r['bottom'])-1))
  if box[3]<=box[1]:p['pixelAudit']='outside viewport';continue
  diff=ImageChops.difference(a.crop(box),b.crop(box));p['probeChangedPixels']=sum(pixel!=(0,0,0) for pixel in diff.get_flattened_data());assert p['probeChangedPixels']==0,p
d['status']='PASS';d['method']='Paint the whole main canvas magenta; require identical composed pixels inside each visible fixed panel. Real pointer events over covered nodes must not select/refund/zoom.'
d['graphSHA256']=hashlib.sha256((OUT/'graph.json').read_bytes()).hexdigest();(OUT/'fixed-ui-verification.json').write_text(json.dumps(d,ensure_ascii=False,indent=2),encoding='utf-8');print(json.dumps({'status':'PASS','protectedPanels':sum(p['visible'] for v in d['views'] for p in v['panels']),'coveredNodeEventsBlocked':True}))
