"""Audit entire wires, including shared-end edges and polyline self crossings."""
import json,math
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1];OUT=ROOT/'assets/core-ui/large-tree-preview/proposals/workshop-clear-wiring-v3'
g=json.loads((OUT/'candidate-graph.json').read_text(encoding='utf-8'));by={n['id']:n for n in g['nodes']};segments=[(ei,si,a,b) for ei,e in enumerate(g['edges']) for si,(a,b) in enumerate(zip(e['points'],e['points'][1:]))]
crosses=[];overlaps=[];minimum=math.inf;nearest=None
def distance(p,a,b):
    dx=b[0]-a[0];dy=b[1]-a[1];t=max(0,min(1,((p[0]-a[0])*dx+(p[1]-a[1])*dy)/max(dx*dx+dy*dy,1e-12)))
    return math.hypot(p[0]-a[0]-t*dx,p[1]-a[1]-t*dy)
for k,(ei,si,a,b) in enumerate(segments):
    e=g['edges'][ei]
    for ej,sj,c,d in segments[k+1:]:
        f=g['edges'][ej]
        if ei==ej and abs(si-sj)<=1:continue
        shared={e['a'],e['b']}&{f['a'],f['b']} if ei!=ej else set()
        ux,uy=b[0]-a[0],b[1]-a[1];vx,vy=d[0]-c[0],d[1]-c[1];wx,wy=c[0]-a[0],c[1]-a[1];den=ux*vy-uy*vx
        if abs(den)>1e-7:
            t=(wx*vy-wy*vx)/den;v=(wx*uy-wy*ux)/den
            if -1e-7<=t<=1+1e-7 and -1e-7<=v<=1+1e-7:
                p=[a[0]+t*ux,a[1]+t*uy]
                if not any(math.dist(p,[by[id]['x'],by[id]['y']])<.01 for id in shared):crosses.append({'edges':[ei,ej],'segments':[si,sj],'point':p,'sharedEnd':bool(shared),'sameEdge':ei==ej})
        else:
            size=math.hypot(ux,uy)
            if size>1e-5 and abs(wx*uy-wy*ux)/size<.001:
                t1=(wx*ux+wy*uy)/(size*size);t2=((d[0]-a[0])*ux+(d[1]-a[1])*uy)/(size*size);lo=max(0,min(t1,t2));hi=min(1,max(t1,t2))
                if (hi-lo)*size>.01:overlaps.append({'edges':[ei,ej],'length':round((hi-lo)*size,3),'sharedEnd':bool(shared)})
        if not shared and ei!=ej:
            gap=min(distance(a,c,d),distance(b,c,d),distance(c,a,b),distance(d,a,b))
            if gap<minimum:minimum=gap;nearest={'edges':[ei,ej],'segments':[si,sj]}
out={'status':'FIX-FIRST' if crosses or overlaps else 'PASS','nonVertexCrossings':crosses,'wireOverlaps':overlaps,'selfCrossingsIncluded':True,'sharedEndEdgesChecked':True,'minimumUnrelatedWireCenterGap':minimum,'minimumWirePaintGapAt10_5Percent':minimum*.105-1.65,'nearest':nearest}
(OUT/'complete-wire-verification.json').write_text(json.dumps(out,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps({**out,'nonVertexCrossings':len(crosses),'wireOverlaps':len(overlaps)}))
assert not crosses and not overlaps
