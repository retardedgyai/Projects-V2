"""Separate planar ring/fork candidate. Preserve all earlier previews and effects."""
import copy,importlib.util,json,math,re
from pathlib import Path
import numpy as np
ROOT=Path(__file__).resolve().parents[1];BASE=ROOT/'assets/core-ui/large-tree-preview';OUT=BASE/'proposals/workshop-clear-wiring-v4';PREVIOUS=BASE/'proposals/workshop-clear-wiring-v3';UI_REFERENCE=BASE/'proposals/workshop-structure-v2'
spec=importlib.util.spec_from_file_location('v2_retained',ROOT/'scripts/build-skill-tree-structure-v2.py');v2=importlib.util.module_from_spec(spec);spec.loader.exec_module(v2);base=v2.base
clearance_route=base.route
def checked_route(graph,radii,mirror):
    audit=clearance_route(graph,radii,mirror)
    # Clearance obstacles include a single selection halo; body/body overlap does not.
    body=np.array([{'start':76,'small':24,'notable':43,'keystone':49,'road':16}[n['type']] for n in graph['nodes']]);ps=np.array([[n['x'],n['y']] for n in graph['nodes']]);dist=np.linalg.norm(ps[:,None,:]-ps[None,:,:],axis=2);np.fill_diagonal(dist,1e9)
    audit['nodeOverlaps']=[(graph['nodes'][i]['id'],graph['nodes'][j]['id']) for i,j in zip(*np.where(np.triu(dist<body[:,None]+body[None,:]+8,1)))]
    audit['clearanceIncludesSelectionHalo']=True
    return audit
base.route=checked_route
# Route explicit fan corners before testing the full, continuous wires.
import inspect
route_code=inspect.getsource(v2.retained_route).replace("ps=[[by[e['a']]['x'],by[e['a']]['y']],[by[e['b']]['x'],by[e['b']]['y']]]","ps=copy.deepcopy(e.get('guide')) or [[by[e['a']]['x'],by[e['a']]['y']],[by[e['b']]['x'],by[e['b']]['y']]]")
route_scope=dict(v2.retained_route.__globals__);route_scope['copy']=copy
exec(route_code,route_scope);clearance_route=route_scope['route']


def fork(count,notables):
    a=(count-2+1)//2;b=count-2-a;extent=(a+1)*70
    pts=[(-extent,0)];local=[];ends=[]
    for number,sign in [(a,-1),(b,1)]:
        path=[0]
        for i in range(number):
            path.append(len(pts));pts.append((-extent+(i+1)*2*extent/(number+1),sign*(100 if notables==2 and i==number-1 else 85)))
        ends.append(path[-1]);local.extend(zip(path,path[1:]))
    exit=len(pts);pts.append((extent,0))
    for i in ends:local.append((i,exit))
    return pts,local,[exit] if notables==1 else ends,extent

def build(source):
    nodes=copy.deepcopy(source['nodes']);by={n['id']:n for n in nodes};groups=copy.deepcopy(source['groups']);gb={g['id']:g for g in groups}
    pos={};mirror={};edges={};centers={};basis={};slots={};gm={};districts={};module={};extents={}
    def put(id,p):pos[id]=np.array(p,float)
    def pair(a,b,p):put(a,p);put(b,[-p[0],p[1]]);mirror[a]=b;mirror[b]=a
    def axis(a,p):put(a,[0,p[1]]);mirror[a]=a
    def edge(a,b,kind):
        if a!=b:edges[tuple(sorted((a,b)))]=dict(a=a,b=b,road=kind!='regional',proposalKind=kind)
    def assign(g,pts,ns):
        ids=[None]*len(pts);notable=sorted(i for i in g['nodes'] if by[i]['type']=='notable');small=sorted(i for i in g['nodes'] if by[i]['type']!='notable')
        for j,i in zip(ns,notable):ids[j]=i
        for j,i in zip([j for j,i in enumerate(ids) if i is None],small):ids[j]=i
        assert all(ids);return ids
    def district(key,name,theta,gs,other):
        districts[key]={'id':key,'name':name,'groups':gs,'nodes':[],'center':[1600*math.cos(theta),1600*math.sin(theta)],'mirror':other,'theta':theta}
        for g in gs:module[g]=key
    for index,(tag,oldcenter,oldr,angles,pairs,names) in enumerate(v2.DISTRICTS,1):
        theta=math.radians(-90-index*180/7);district(tag+'_L',names[0],theta,[a for a,b in pairs],tag+'_R');district(tag+'_R',names[1],math.pi-theta,[b for a,b in pairs],tag+'_L')
        count=len(pairs)
        for j,(left,right) in enumerate(pairs):
            angle=theta+math.radians(-10+20*j/max(count-1,1));u=np.array([math.cos(angle),math.sin(angle)]);v=np.array([-u[1],u[0]]);p=u*(2550+(520 if j%2 else 0));g,h=gb[left],gb[right]
            nn=sum(by[i]['type']=='notable' for i in g['nodes']);pts,local,ns,extent=fork(len(g['nodes']),nn);ids=assign(g,pts,ns);rids=assign(h,pts,ns)
            for a,b,q in zip(ids,rids,pts):pair(a,b,p+u*q[0]+v*q[1])
            for a,b in local:edge(ids[a],ids[b],'regional');edge(rids[a],rids[b],'regional')
            centers[left]=p;centers[right]=[-p[0],p[1]];gm[left]=right;gm[right]=left;slots[left]=ids;slots[right]=rids;extents[left]=extents[right]=extent
            basis[left]=(u,v);basis[right]=(np.array([-u[0],u[1]]),np.array([-v[0],v[1]]))
            districts[tag+'_L']['nodes']+=ids;districts[tag+'_R']['nodes']+=rids
    for gid,y in [('g20',-2400),('g50',2400),('g33',0)]:
        g=gb[gid];gm[gid]=gid;centers[gid]=np.array([0,y]);district(gid,g['name'],-math.pi/2 if y<0 else math.pi/2,[gid],gid)
        if gid=='g20':pts,local,ns,extent=fork(10,2);u=np.array([0.,-1]);v=np.array([1.,0])
        elif gid=='g50':pts=[(-140,-110),(-140,110),(140,0)];local=[(0,1),(0,2),(1,2)];ns=[2];extent=140;u=np.array([0.,1]);v=np.array([1.,0])
        else:pts=[(-150,120),(-150,-120),(150,120),(150,-120),(0,-280)];local=[(0,1),(1,4),(2,3),(3,4)];ns=[4];extent=280;u=np.array([1.,0]);v=np.array([0.,1])
        ids=assign(g,pts,ns);slots[gid]=ids;basis[gid]=(u,v);extents[gid]=extent
        for id,q in zip(ids,pts):put(id,np.array([0,y])+u*q[0]+v*q[1])
        for id in ids:
            p=pos[id];mirror[id]=next(other for other in ids if np.linalg.norm(pos[other]-[-p[0],p[1]])<.001)
        for a,b in local:edge(ids[a],ids[b],'regional')
        districts[gid]['nodes']+=ids
    # Five separate three-lane openings; only their reward exits merge.
    originangles={'mage':-90,'tank':-162,'warrior':126,'ranger':-18,'assassin':54};ob={o['id']:o for o in source['origins']};innerpos={}
    for o in source['origins']:
        angle=math.radians(originangles[o['id']]);u=np.array([math.cos(angle),math.sin(angle)]);v=np.array([-u[1],u[0]]);put(o['root'],600*u);innerpos[o['id']]=1310*u
        for lane,entry in enumerate(o['lanes']):
            previous=o['root']
            for depth,id in enumerate(entry['nodes'],1):put(id,u*(600+160*depth)+v*200*(lane-1));edge(previous,id,'opening');previous=id
    for left,right in [('warrior','assassin'),('tank','ranger')]:
        aa=[i for i in pos if by[i].get('origin')==left];bb={i for i in pos if by[i].get('origin')==right}
        for a in aa:
            p=pos[a];b=min(bb,key=lambda b:np.linalg.norm(pos[b]-[-p[0],p[1]]));bb.remove(b);pair(a,b,p)
    aa=[i for i in pos if by[i].get('origin')=='mage']
    for a in aa:
        p=pos[a];mirror[a]=min(aa,key=lambda b:np.linalg.norm(pos[b]-[-p[0],p[1]]))
    pool=[n['id'] for n in nodes if n['id'] not in pos and n['type']!='keystone']
    def take(road=False):
        index=next((j for j,i in enumerate(pool) if not road or by[i]['type']=='road'),None);assert index is not None;return pool.pop(index)
    def reserve(p,road=False):
        p=np.array(p)
        if abs(p[0])<1e-6:a=take(road);axis(a,p);return a,a
        a,b=take(road),take(road);pair(a,b,p);return a,b
    def member(gid,ids):districts[module[gid]]['nodes']+=ids
    portals={}
    for gid,p in centers.items():
        if gid in portals:continue
        p=np.array(p);u,v=basis[gid]
        q=p-u*(extents[gid]+180) if gid!='g33' else np.array([0.,350.])
        a,b=reserve(q,True);portals[gid]=a;portals[gm[gid]]=b;member(gid,[a])
        if gm[gid]!=gid:member(gm[gid],[b])
    for gid,a in portals.items():
        if gid=='g33':targets=[i for i in slots[gid] if pos[i][1]>0]
        elif gid=='g50':targets=slots[gid][:2]
        else:targets=[slots[gid][0]]
        for b in targets:edge(a,b,'entry-choice')
    # Optional hooked branches keep their original 3pt Keys and existing rewards.
    keygroups={n['group']:n['id'] for n in nodes if n['type']=='keystone'};handled=set();bays=[]
    for gid,key in keygroups.items():
        if gid in handled:continue
        partner=gm[gid];other=keygroups.get(partner);handled|={gid,partner};p=np.array(centers[gid]);u,v=basis[gid];extent=extents[gid]
        if gid=='g50':
            anchor=gb[gid]['notables'][0];path=[]
            for delta in [140,280,420]:a,b=reserve(pos[anchor]+[0,delta],True);path.append(a)
            axis(key,pos[anchor]+[0,640])
            for a,b in zip([anchor]+path,path+[key]):edge(a,b,'optional-keystone')
            member(gid,path+[key]);bays.append({'key':key,'anchor':anchor,'detourNodes':path});continue
        anchor=max(gb[gid]['notables'],key=lambda i:float((pos[i]-p)@u)-(float((pos[i]-p)@v)>0)*.01);depth=4 if key in ['key_01','key_05'] or other in ['key_01','key_05'] else 3
        raw=[(extent+140,-80),(extent+280,-80),(extent+420,0)]
        if depth==4:raw.append((extent+280,110))
        kp=p+u*(extent+(140 if depth==4 else 280))+v*110
        if gid=='g20':raw=[(extent+160,-220),(extent+340,-330),(extent+520,-220)];kp=p+u*(extent+340)-v*110
        if key in ['key_04','key_07'] or other in ['key_04','key_07']:
            raw=[(x+320,y) for x,y in raw];kp+=u*320
        put(key,kp)
        if other and partner!=gid:put(other,[-kp[0],kp[1]]);mirror[key]=other;mirror[other]=key;leaf=other
        else:leaf=take(True);put(leaf,[-kp[0],kp[1]]);mirror[key]=leaf;mirror[leaf]=key
        path=[];rpath=[]
        for x,y in raw:a,b=reserve(p+u*x+v*y,True);path.append(a);rpath.append(b)
        ra=mirror[anchor]
        for a,b in zip([anchor]+path,path+[key]):edge(a,b,'optional-keystone')
        for a,b in zip([ra]+rpath,rpath+[leaf]):edge(a,b,'optional-keystone' if other and partner!=gid else 'reward-bay')
        member(gid,path+[key]);member(partner,rpath+[leaf]);bays.append({'key':key,'anchor':anchor,'detourNodes':path})
        if other and partner!=gid:bays.append({'key':other,'anchor':ra,'detourNodes':rpath})
    gates={}
    for name,d in districts.items():
        if name=='g33' or name in gates:continue
        a,b=reserve(d['center'],True);gates[name]=a;gates[d['mirror']]=b
    inner={}
    for name in ['warrior','tank','mage']:
        a,b=reserve(innerpos[name],True);inner[name]=a;inner[{'warrior':'assassin','tank':'ranger','mage':'mage'}[name]]=b
    for o in source['origins']:
        for lane in o['lanes']:edge(lane['nodes'][-1],inner[o['id']],'opening-merge')
    links={}
    def macro(a,b,kind):
        links[tuple(sorted((a,b)))]=(a,b,kind);ma,mb=mirror[a],mirror[b];links[tuple(sorted((ma,mb)))]=(ma,mb,kind)
    ring=['g20']+[tag+'_L' for tag in 'ABCDEF']+['g50']+[tag+'_R' for tag in 'FEDCBA']
    for a,b in zip(ring,ring[1:]+ring[:1]):macro(gates[a],gates[b],'district-ring')
    innerring=['mage','tank','warrior','assassin','ranger']
    for a,b in zip(innerring,innerring[1:]+innerring[:1]):macro(inner[a],inner[b],'opening-ring')
    for job,d in [('mage','g20'),('tank','C_L'),('warrior','F_L'),('ranger','C_R'),('assassin','F_R')]:macro(inner[job],gates[d],'district-bridge')
    for gid,a in portals.items():
        if gid!='g33':macro(gates[module[gid]],a,'regional-approach')
    # A real common node on the bottom inner edge gives healing its own branch.
    bottom=reserve((0,1310*math.sin(math.radians(126))),True)[0]
    del links[tuple(sorted((inner['warrior'],inner['assassin'])))];macro(inner['warrior'],bottom,'opening-ring');macro(bottom,portals['g33'],'healing-branch')
    orbits=[];seen=set()
    for k,(a,b,kind) in links.items():
        if k in seen:continue
        ma,mb=mirror[a],mirror[b];mk=tuple(sorted((ma,mb)));seen|={k,mk};length=math.dist(pos[a],pos[b]);orbits.append(dict(a=a,b=b,ma=ma,mb=mb,paired=k!=mk,count=max(0,math.ceil(length/450)-1),kind=kind,length=length))
    needed=sum(o['count']*(2 if o['paired'] else 1) for o in orbits)
    while needed>len(pool):
        o=min((o for o in orbits if o['count']>0),key=lambda o:o['length']/o['count']);o['count']-=1;needed-=2 if o['paired'] else 1
    while needed<len(pool):
        o=max((o for o in orbits if (2 if o['paired'] else 1)<=len(pool)-needed),key=lambda o:o['length']/(o['count']+1));o['count']+=1;needed+=2 if o['paired'] else 1
    for o in orbits:
        a,b=o['a'],o['b'];ps=[];ms=[]
        for j in range(o['count']):
            t=(j+1)/(o['count']+1);p=pos[a]*(1-t)+pos[b]*t
            if o['kind']=='regional-approach':
                turn=pos[b]/np.linalg.norm(pos[b])*1800
                p=turn if j==0 else turn+(pos[b]-turn)*j/o['count']
            if o['paired']:q,m=reserve(p);ps.append(q);ms.append(m)
            elif abs(p[0])<1e-6:q,m=reserve(p);ps.append(q)
            elif j<(o['count']+1)//2:q,m=reserve(p);ps.append(q);ms.insert(0,m)
        if not o['paired']:ps+=ms
        for x,y in zip([a]+ps,ps+[b]):edge(x,y,o['kind'])
        if o['paired']:
            for x,y in zip([o['ma']]+ms,ms+[o['mb']]):edge(x,y,o['kind'])
        if o['kind']=='regional-approach' and not ps:
            turn=pos[b]/np.linalg.norm(pos[b])*1800
            edges[tuple(sorted((a,b)))]['guide']=[pos[a].tolist(),turn.tolist(),pos[b].tolist()]
            if o['paired']:edges[tuple(sorted((o['ma'],o['mb'])))]['guide']=[pos[o['ma']].tolist(),[-turn[0],turn[1]],pos[o['mb']].tolist()]

    assert not pool and len(pos)==645,(len(pool),len(pos))
    # Specialist inputs belong to optional local reward choices, never common travel.
    hubs={n['group']:n['id'] for n in nodes if n['type']=='small' and n['id'].endswith('hub') and n['id'] not in gb[n['group']]['nodes']}
    input_branches=[];exchange={};old_mirror=mirror.copy();old_pos={i:p.copy() for i,p in pos.items()}
    for gid,hub in hubs.items():
        road=portals[gid];assert by[road]['type']=='road';exchange[hub]=road;exchange[road]=hub
        input_branches.append({'node':hub,'group':gid,'commonRoadPreserved':True,'exchangedRoad':road,'entry':slots[gid][0]})
    for id,other in exchange.items():
        pos[id]=old_pos[other];mirror[id]=exchange.get(old_mirror[other],old_mirror[other])
    remapped={}
    for e in edges.values():
        e=copy.deepcopy(e);e['a']=exchange.get(e['a'],e['a']);e['b']=exchange.get(e['b'],e['b']);remapped[tuple(sorted((e['a'],e['b'])))]=e
    edges=remapped
    for d in districts.values():d['nodes']=[exchange.get(i,i) for i in d['nodes']]
    for n in nodes:n['x'],n['y']=[round(float(v),3) for v in pos[n['id']]]
    ids=[n['id'] for n in nodes];ix={i:j for j,i in enumerate(ids)};radii=np.array([{'start':140,'small':70,'notable':100,'keystone':120,'road':70}[n['type']] for n in nodes],float);radii=np.maximum(radii,np.array([radii[ix[mirror[i]]] for i in ids]))
    graph=copy.deepcopy(source);graph.update(nodes=nodes,groups=groups,edges=list(edges.values()),status='unadopted_clear_wiring_v4',runtimeApplied=False)
    for k in ['budgetAudit','lineAudit','layoutAudit','chainReview','startBraids']:graph.pop(k,None)
    graph['structureProposal']={'adopted':False,'sourceCommit':'eeb913aa','same645Effects':True,'noActiveSkillAcquisition':True,'districts':list(districts.values()),'keyBays':bays,'optionalInputBranches':input_branches,'note':'環状経路と各領域の恩恵分岐。接続以外の線・背景で隠す交差は不使用。'}
    return graph,mirror,radii,0

def main():
    OUT.mkdir(parents=True,exist_ok=True)
    (OUT/'proposal.js').write_text((PREVIOUS/'proposal.js').read_text(encoding='utf-8').replace('z:Math.min(.65,(w-160)/1800,(h-220)/1500)','z:Math.max(.115,Math.min(.65,(w-160)/1800,(h-220)/1500))'),encoding='utf-8')
    ui=(UI_REFERENCE/'district-ui.js').read_text(encoding='utf-8').replace('ctx.lineWidth=.8;ctx.stroke()','ctx.lineWidth=.8').replace('b944e28c','eeb913aa').replace('改善案2','配線案4').replace('前案：横帯と近隣の接続。','改善案2：領域をまとめた前案。')
    start=ui.index(' const districts=');end=ui.index(' const districtOf=',start)
    section=ui[start:end].replace('const districts=candidate.structureProposal.districts.map','const makeDistricts=(graph)=>graph.structureProposal.districts.map').replace('candidate.nodes','graph.nodes').replace('candidate.groups','graph.groups')
    ui=ui[:start]+section+' const districts=makeDistricts(candidate),previousDistricts=makeDistricts(previous);\n'+ui[end:]
    ui=ui.replace("return mode==='proposal'&&cam.z<=.24?districts:DISPLAY.groups", "return cam.z<=.24?(mode==='proposal'?districts:mode==='previous'?previousDistricts:DISPLAY.groups):DISPLAY.groups")
    ui=ui.replace('前案 eeb913aa を保持して比較','保持した配線案3 / 共通路の入力と広さを比較')
    (OUT/'district-ui.js').write_text(ui,encoding='utf-8')
    base.OUT=OUT;base.build=build;base.main()
    graph=json.loads((OUT/'candidate-graph.json').read_text(encoding='utf-8'));previous=json.loads((PREVIOUS/'candidate-graph.json').read_text(encoding='utf-8'));report=json.loads((OUT/'cost-comparison.json').read_text(encoding='utf-8'));comp=base.compare(previous,graph)
    for table in ['singleKeyCosts','twoKeyExactSteinerCosts']:
        for row,old in zip(report[table],comp[table]):row['previous']=old['original'];row['deltaFromPrevious']=row['proposal']-row['previous']
    (OUT/'cost-comparison.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
    html=(OUT/'ProjectS_Passive_Structure_Comparison.html').read_text(encoding='utf-8')
    html=re.sub(r'(<script id="structureComparisonData" type="application/json">).*?(</script>)',lambda m:m[1]+json.dumps(report,ensure_ascii=False,separators=(',',':'))+m[2],html,flags=re.S)
    html=html.replace('</body>','<script id="previousGraphData" type="application/json">'+json.dumps(previous,ensure_ascii=False,separators=(',',':'))+'</script><script>'+ui+'</script></body>')
    html=html.replace('for(const g of (window.workshopFull?.display||DATA).groups){','for(const g of (window.structureV2?.captionGroups()||(window.workshopFull?.display||DATA).groups)){')
    # Label placement must avoid the complete wire, even when a backplate exists.
    html=html.replace('&&!nodes.some(n=>circleHit(q,n));','&&!nodes.some(n=>circleHit(q,n))&&!segments.some(s=>lineHit({left:q.left-4,right:q.right+4,top:q.top-4,bottom:q.bottom+4},s));')
    html=html.replace("if(c.text==='PROJECTS / CLASS START'", "const knownOrigin=DATA.origins.find(o=>[o.name,o.name.replace(/〈.*〉/,''),by.get(o.root).name.replace('の起点','')].includes(c.text));if(knownOrigin){owner=knownOrigin.root;kind='node'}if(c.text==='PROJECTS / CLASS START'")
    # A start hidden behind an opaque HUD panel must not emit a detached caption.
    # All five class names remain in the persistent class selector; this is recorded.
    caption_guard="""const nativeFill=ctx.fillText;const captions=[];window.clearWiringOccludedCaptions=[];
    const originCaptionOccluded=text=>{const o=DATA.origins.find(o=>[o.name,o.name.replace(/〈.*〉/,''),by.get(o.root).name.replace('の起点','')].includes(String(text)));if(!o)return false;const n=by.get(o.root),p=worldToScreen(n),r=displayRadius(n)+3,tr=canvas.getBoundingClientRect();const blocked=[...document.querySelectorAll('.map-info,.mapheading,.preview-legend,.filterbar,.minimap,.mapnav,.searchbanner')].filter(e=>getComputedStyle(e).display!=='none').some(e=>{const b=e.getBoundingClientRect(),left=b.left-tr.left,right=b.right-tr.left,top=b.top-tr.top,bottom=b.bottom-tr.top;return p.x+r>left&&p.x-r<right&&p.y+r>top&&p.y-r<bottom});if(blocked)window.clearWiringOccludedCaptions.push({node:o.root,text:String(text),reason:'start_body_behind_opaque_hud'});return blocked;};"""
    html=html.replace('const nativeFill=ctx.fillText;const captions=[];',caption_guard)
    html=html.replace('ctx.fillText=function(text,x,y,maxWidth){','ctx.fillText=function(text,x,y,maxWidth){if(originCaptionOccluded(text))return;')
    html=html.replace('const knownOrigin=DATA.origins.find',"const knownGroupCaption=(window.structureV2?.captionGroups()||DISPLAY.groups).some(g=>{if(g.name!==c.text)return false;const p=worldToScreen(g);return Math.abs(c.x-p.x)<2&&Math.abs(c.y-(p.y-Math.max(24,75*cam.z)))<2});if(knownGroupCaption){owner=null;kind='group'}"+'const knownOrigin=DATA.origins.find')
    html=html.replace('labelLines:arrange?[]:labelLines,captionBackplateIntersections:arrange?labelLines:[]','labelLines,captionBackplateIntersections:labelLines')
    html=html.replace('y=-176;y<=176','y=-240;y<=240').replace('x=-176;x<=176','x=-240;x<=240')
    html=html.replace('if(nodes.some(n=>n.id!==owner.id&&Math.hypot', 'if(nodes.some(n=>n.id!==owner.id&&(!DATA.origins.some(o=>o.root===owner.id)||DATA.origins.some(o=>o.root===n.id))&&Math.hypot')
    # Backplates and the maximum active line width must stay separated at every zoom.
    pad='(4+Math.max(1.65,cam.z*5)/2)'
    html=html.replace('left:q.left-4,right:q.right+4,top:q.top-4,bottom:q.bottom+4',f'left:q.left-{pad},right:q.right+{pad},top:q.top-{pad},bottom:q.bottom+{pad}')
    html=html.replace('Math.hypot(x-n.x,y-n.y)<(n.collisionRadius||n.r)+3','Math.hypot(x-n.x,y-n.y)<(n.collisionRadius||n.r)+4')
    paint_audit="const paintPad=3+Math.max(1.65,cam.z*5)/2;const paintedLabelLines=placed.flatMap(p=>segments.filter(s=>lineHit({left:p.box.left-paintPad,right:p.box.right+paintPad,top:p.box.top-paintPad,bottom:p.box.bottom+paintPad},s)).map(s=>({text:p.text,edge:s.edge})));"
    html=html.replace('const nodePairs=[];for(let i=0;i<nodes.length;',paint_audit+'const nodePairs=[];for(let i=0;i<nodes.length;')
    html=html.replace('captionBackplateIntersections:labelLines,nodePairs','captionBackplateIntersections:labelLines,paintedLabelLines,nodePairs')
    # No pass-under cuts or decorative boundary strokes in the candidate.
    html=html.replace("const stops=(e.crossingGaps||[]).filter", "const stops=(window.structureProposal?.getMode()==='proposal'?[]:(e.crossingGaps||[])).filter")
    html=html.replace('shape(p.x,p.y,r+4,n.type)','shape(p.x,p.y,r+2,n.type)')
    html=html.replace('cam.z=Math.max(.02,Math.min(1.7,z))',"cam.z=Math.max(window.structureProposal?.getMode()==='proposal'?.115:.02,Math.min(1.7,z))")
    html=html.replace('z:Math.max(.02,Math.min((w-85)',"z:Math.max(window.structureProposal?.getMode()==='proposal'?.115:.02,Math.min((w-85)")
    (OUT/'ProjectS_Passive_Clear_Wiring_V4.html').write_text(html,encoding='utf-8');(OUT/'ProjectS_Passive_Structure_Comparison.html').unlink()
    manifest=json.loads((OUT/'proposal-manifest.json').read_text(encoding='utf-8'));manifest.update(sourceCommit='eeb913aa',previousProposalRetained=True,nonVertexCrossings=0,crossingGapsUsed=False,previewStageOnly=True)
    (OUT/'proposal-manifest.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2),encoding='utf-8')
    print(json.dumps({'costCases':len(report['singleKeyCosts']),'pairCases':len(report['twoKeyExactSteinerCosts'])}))
if __name__=='__main__':main()
