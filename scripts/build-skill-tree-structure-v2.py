"""A separate district/fork proposal; c6a8c919 and b944e28c stay unchanged."""
import copy, importlib.util, json, math, re, sys
from pathlib import Path
import numpy as np

ROOT=Path(__file__).resolve().parents[1]
BASE=ROOT/'assets/core-ui/large-tree-preview'
OUT=BASE/'proposals/workshop-structure-v2'
PREVIOUS=BASE/'proposals/workshop-structure-v1'
spec=importlib.util.spec_from_file_location('retained_v1',ROOT/'scripts/build-skill-tree-structure-proposal.py')
base=importlib.util.module_from_spec(spec);spec.loader.exec_module(base)
route_spec=importlib.util.spec_from_file_location('routing_v2',ROOT/'scripts/skill-tree-structure-routing-v2.py')
routing=importlib.util.module_from_spec(route_spec);route_spec.loader.exec_module(routing)
retained_route=base.route
base.route=lambda graph,radii,mirror:routing.improve_routes(graph,radii,mirror,retained_route(graph,radii,mirror))
DISTRICTS=[
 ('A',(-950,-1320),460,[-130,-10,110],[('g2','g9'),('g24','g3'),('g4','g14')],('術式・回転','属性・資源')),
 ('B',(-2020,-1320),470,[-140,-50,40,130],[('g8','g43'),('g23','g51'),('g31','g52'),('g46','g49')],('障壁・耐久','防御・回転')),
 ('C',(-2110,0),480,[-130,-58,14,86,158],[('g12','g26'),('g38','g10'),('g16','g37'),('g7','g42'),('g25','g32')],('間合い・一撃','機動・会心')),
 ('D',(-820,1090),450,[-120,-30,60,150],[('g11','g28'),('g35','g21'),('g6','g34'),('g30','g44')],('武器・手数','範囲・継続')),
 ('E',(-1950,1580),460,[-140,-50,40,130],[('g40','g47'),('g45','g17'),('g13','g41'),('g48','g27')],('生命・吸収','弱点・手数')),
 ('F',(-860,2380),310,[-20,160],[('g18','g39'),('g5','g19')],('生命・裂傷','資源・回転'))]

def leaf_template(count,notables):
 if count!=8:return base.template(count,notables)
 pts=[(-220,0),(-105,-100),(30,-125),(160,-80),(-105,100),(30,125),(160,80),(280,0)]
 edges=[(0,1),(1,2),(2,3),(3,7),(0,4),(4,5),(5,6),(6,7)]
 return pts,edges,[7] if notables==1 else [3,6]

def build(source):
 nodes=copy.deepcopy(source['nodes']);by={n['id']:n for n in nodes};groups=copy.deepcopy(source['groups']);gb={g['id']:g for g in groups}
 positions={};mirror={};edges={};centers={};gm={};module={};districts={};basis={};slots={};fixed=set()
 def put(id,p):positions[id]=np.array(p,float)
 def pair(a,b,p):put(a,p);put(b,[-p[0],p[1]]);mirror[a]=b;mirror[b]=a
 def axis(id,p):put(id,[0,p[1]]);mirror[id]=id
 def edge(a,b,kind):
  if a!=b:edges[tuple(sorted((a,b)))]=dict(a=a,b=b,road=kind!='regional',proposalKind=kind)
 def assign(g,pts,ns):
  ids=[None]*len(pts);notables=sorted(id for id in g['nodes'] if by[id]['type']=='notable');small=sorted(id for id in g['nodes'] if by[id]['type']!='notable')
  for i,id in zip(ns,notables):ids[i]=id
  for i,id in zip([i for i,v in enumerate(ids) if v is None],small):ids[i]=id
  assert all(ids);return ids
 for tag,center,radius,angles,pairs,names in DISTRICTS:
  for side,name in zip(['L','R'],names):
   key=tag+'_'+side;districts[key]={'id':key,'name':name,'center':[center[0] if side=='L' else -center[0],center[1]],'groups':[],'nodes':[],'mirror':tag+('_R' if side=='L' else '_L')}
  for angle,(left,right) in zip(angles,pairs):
   theta=math.radians(angle);u=np.array([math.cos(theta),math.sin(theta)]);v=np.array([-u[1],u[0]]);p=np.array(center)+radius*u
   g,h=gb[left],gb[right];nn=sum(by[id]['type']=='notable' for id in g['nodes']);assert len(g['nodes'])==len(h['nodes']) and nn==sum(by[id]['type']=='notable' for id in h['nodes'])
   pts,local,ns=leaf_template(len(g['nodes']),nn);ids=assign(g,pts,ns);rids=assign(h,pts,ns)
   for a,b,q in zip(ids,rids,pts):pair(a,b,p+u*q[0]+v*q[1])
   for a,b in local:edge(ids[a],ids[b],'regional');edge(rids[a],rids[b],'regional')
   centers[left]=p;centers[right]=np.array([-p[0],p[1]]);gm[left]=right;gm[right]=left
   basis[left]=(u,v);basis[right]=(np.array([-u[0],u[1]]),np.array([-v[0],v[1]]));slots[left]=ids;slots[right]=rids
   for gid,side,ids2 in [(left,'L',ids),(right,'R',rids)]:
    key=tag+'_'+side;module[gid]=key;districts[key]['groups'].append(gid);districts[key]['nodes'].extend(ids2)
 for gid,y in [('g20',-2100),('g33',0),('g50',2150)]:
  g=gb[gid];centers[gid]=np.array([0,y]);gm[gid]=gid;module[gid]=gid
  if gid=='g20':
   pts=[(-120,-170),(120,-170),(-120,-40),(120,-40),(-230,80),(230,80),(-120,200),(120,200),(-230,320),(230,320)];ns=[0,1];local=[(0,2),(2,4),(4,6),(6,8),(1,3),(3,5),(5,7),(7,9),(2,3),(6,7)]
  elif gid=='g33':pts=[(0,0),(-140,-110),(140,-110),(-140,110),(140,110)];ns=[0];local=[(0,1),(0,2),(0,3),(0,4),(1,3),(2,4)]
  else:pts=[(0,140),(-120,0),(120,0)];ns=[0];local=[(0,1),(0,2),(1,2)]
  ids=assign(g,pts,ns);slots[gid]=ids;basis[gid]=(np.array([1.,0]),np.array([0.,1.]));districts[gid]={'id':gid,'name':g['name'],'center':[0,y],'groups':[gid],'nodes':ids.copy(),'mirror':gid}
  for i,p in enumerate(pts):
   put(ids[i],[p[0],y+p[1]]);mirror[ids[i]]=ids[next(j for j,q in enumerate(pts) if q[0]==-p[0] and q[1]==p[1])]
  for a,b in local:edge(ids[a],ids[b],'regional')
 # Retain the five starts and all original initial rewards, with wide readable lanes.
 rootpos={'warrior':(-600,360),'tank':(-600,-360),'mage':(0,-700),'ranger':(600,-360),'assassin':(600,360)}
 angles={'warrior':150,'tank':-150,'mage':-90,'ranger':-30,'assassin':30}
 ob={o['id']:o for o in source['origins']}
 for o in source['origins']:
  p=np.array(rootpos[o['id']]);put(o['root'],p);fixed.add(o['root']);t=math.radians(angles[o['id']]);u=np.array([math.cos(t),math.sin(t)]);v=np.array([-u[1],u[0]])
  for lane,entry in enumerate(o['lanes']):
   prev=o['root']
   for depth,id in enumerate(entry['nodes'],1):put(id,p+u*150*depth+v*300*(lane-1));edge(prev,id,'opening');fixed.add(id);prev=id
 for a,b in [('tank','ranger'),('warrior','assassin')]:
  aa=[id for id in fixed if by[id].get('origin')==a];bb={id for id in fixed if by[id].get('origin')==b}
  for id in aa:
   p=positions[id];q=min(bb,key=lambda q:np.linalg.norm(positions[q]-[-p[0],p[1]]));bb.remove(q);pair(id,q,p)
 mage=[id for id in fixed if by[id].get('origin')=='mage']
 for id in mage:
  p=positions[id];mirror[id]=min(mage,key=lambda q:np.linalg.norm(positions[q]-[-p[0],p[1]]))
 pool=[n['id'] for n in nodes if n['id'] not in positions and n['type']!='keystone']
 def take(road=False):
  ix=next((i for i,id in enumerate(pool) if not road or by[id]['type']=='road'),None);assert ix is not None
  return pool.pop(ix)
 def reserve(p,road=False):
  if abs(p[0])<1e-6:a=take(road);axis(a,p);return a,a
  a,b=take(road),take(road);pair(a,b,p);return a,b
 def member(gid,ids):districts[module[gid]]['nodes'].extend(ids)
 # General-reward gateways sit between the specialist fingers, not across them.
 junction={}
 for gid,p in centers.items():
  if gid in junction:continue
  if gm[gid]==gid:q=(0,p[1]+440) if gid!='g33' else (0,-260)
  else:
   center=np.array(districts[module[gid]]['center']);theta=math.atan2(p[1]-center[1],p[0]-center[0])+math.radians(25);q=center+230*np.array([math.cos(theta),math.sin(theta)])
  a,b=reserve(q,True);junction[gid]=a;junction[gm[gid]]=b;member(gid,[a]);
  if gm[gid]!=gid:member(gm[gid],[b])
 for gid,id in junction.items():
  # Start at the first Small, then choose the two reward lanes to the Notable.
  if gm[gid]!=gid:entry=slots[gid][0]
  else:entry=min((id2 for id2 in gb[gid]['nodes'] if by[id2]['type']=='small'),key=lambda q:np.linalg.norm(positions[q]-positions[id]))
  edge(id,entry,'entry-choice')
  if gm[gid]==gid and mirror[entry]!=entry:edge(id,mirror[entry],'entry-choice')
 # A deeper optional branch gives the rule-changing Key a visible investment.
 keygroup={n['group']:n['id'] for n in nodes if n['type']=='keystone'};handled=set();key_bays=[]
 for gid,key in keygroup.items():
  if gid in handled:continue
  other=keygroup.get(gm[gid]);partner=gm[gid];handled|={gid,partner};p=centers[gid]
  if gid=='g50':
   path=[]
   for y in [p[1]+270,p[1]+380,p[1]+490]:a,b=reserve((0,y),True);path.append(a)
   axis(key,(0,p[1]+670));anchor=gb[gid]['notables'][0]
   for a,b in zip([anchor]+path,path+[key]):edge(a,b,'optional-keystone')
   member(gid,path+[key]);key_bays.append({'key':key,'anchor':anchor,'detourNodes':path});continue
  u,v=basis[gid]
  if gid=='g20':u=np.array([1.,0]);v=np.array([0.,-1.])
  depth=4 if key in ['key_01','key_05'] or other in ['key_01','key_05'] else 3
  # A compact hooked bay, rather than stretching three/four nodes into a road.
  raw=[(335,180),(405,280),(290,370),(135,330)] if depth==4 else [(330,160),(420,290),(260,380)]
  ps=[p+u*x+v*y for x,y in raw];kp=p+u*90+v*440
  if gid=='g20':kp=p+np.array([330,-460]);ps=[p+np.array([460,-120]),p+np.array([510,-260]),p+np.array([440,-350])]
  put(key,kp)
  if other and partner!=gid:put(other,[-kp[0],kp[1]]);mirror[key]=other;mirror[other]=key;leaf=other
  else:leaf=take();put(leaf,[-kp[0],kp[1]]);mirror[key]=leaf;mirror[leaf]=key
  path=[];rpath=[]
  for q in ps:a,b=reserve(q,True);path.append(a);rpath.append(b)
  anchor=min(gb[gid]['notables'],key=lambda q:np.linalg.norm(positions[q]-ps[0]));ra=mirror[anchor]
  for a,b in zip([anchor]+path,path+[key]):edge(a,b,'optional-keystone')
  for a,b in zip([ra]+rpath,rpath+[leaf]):edge(a,b,'optional-keystone' if other and partner!=gid else 'reward-bay')
  member(gid,path+[key]);member(partner,rpath+[leaf]);key_bays.append({'key':key,'anchor':anchor,'detourNodes':path})
  if other and partner!=gid:key_bays.append({'key':other,'anchor':ra,'detourNodes':rpath})
 # Regional cycles are small choices within a district, with one useful crossway.
 links={}
 def macro(a,b,kind):
  k=tuple(sorted((a,b)));links[k]=(a,b,kind);ma,mb=mirror[a],mirror[b];links[tuple(sorted((ma,mb)))]=(ma,mb,kind)
 for d in districts.values():
  gs=d['groups']
  if len(gs)>1:
   js=[junction[g] for g in gs]
   for a,b in zip(js,js[1:]+([js[0]] if len(js)>2 else [])):macro(a,b,'district-crossway')
   if len(js)>=4:macro(js[0],js[2],'district-choice')
 def bridge(da,db):
  aa=districts[da]['groups'];bb=districts[db]['groups']
  a,b=min(((junction[a],junction[b]) for a in aa for b in bb),key=lambda pair:math.dist(positions[pair[0]],positions[pair[1]]))
  macro(a,b,'district-bridge')
 for a,b in [('A_L','B_L'),('B_L','C_L'),('C_L','E_L'),('E_L','F_L'),('F_L','D_L'),('D_L','C_L'),('A_L','g20'),('B_L','g20'),('A_L','g33'),('D_L','g33'),('C_L','g33'),('D_L','g50'),('F_L','g50')]:bridge(a,b)
 for a,b in [('tank','ranger'),('warrior','assassin'),('mage','mage')]:
  if a=='mage':targets=['g2','g20','g9']
  else:targets=sorted((g for g in centers if centers[g][0]<=0),key=lambda g:math.dist(positions[junction[g]],rootpos[a]))[:3]
  for lane,gid in zip(ob[a]['lanes'],targets):macro(lane['nodes'][-1],junction[gid],'opening-exit')
 # Spend the original finite connector pool on these short meaningful crossings.
 orbits=[];seen=set()
 for k,(a,b,kind) in links.items():
  if k in seen:continue
  ma,mb=mirror[a],mirror[b];mk=tuple(sorted((ma,mb)));seen|={k,mk};length=math.dist(positions[a],positions[b])
  count=max(0,math.ceil(length/390)-1);orbits.append(dict(a=a,b=b,ma=ma,mb=mb,paired=k!=mk,count=count,kind=kind,length=length))
 needed=sum(o['count']*(2 if o['paired'] else 1) for o in orbits)
 while needed>len(pool):
  q=min((o for o in orbits if o['count']>0),key=lambda o:o['length']/o['count']);q['count']-=1;needed-=2 if q['paired'] else 1
 while needed<len(pool):
  q=max((o for o in orbits if (2 if o['paired'] else 1)<=len(pool)-needed),key=lambda o:o['length']/(o['count']+1));q['count']+=1;needed+=2 if q['paired'] else 1
 for o in orbits:
  a,b=o['a'],o['b'];ps=[];ms=[]
  for i in range(o['count']):
   t=(i+1)/(o['count']+1);p=positions[a]*(1-t)+positions[b]*t
   if o['paired']:q,m=reserve(p);ps.append(q);ms.append(m)
   elif abs(p[0])<1e-6:q,m=reserve(p);ps.append(q)
   elif i<(o['count']+1)//2:q,m=reserve(p);ps.append(q);ms.insert(0,m)
  if not o['paired']:ps+=ms
  for x,y in zip([a]+ps,ps+[b]):edge(x,y,o['kind'])
  if o['paired']:
   for x,y in zip([o['ma']]+ms,ms+[o['mb']]):edge(x,y,o['kind'])
 assert not pool and len(positions)==645,(len(pool),len(positions))
 ids=[n['id'] for n in nodes];ix={id:i for i,id in enumerate(ids)};points=np.array([positions[id] for id in ids]);target=points.copy();mi=np.array([ix[mirror[id]] for id in ids]);locked=np.array([id in fixed for id in ids])
 radii=np.array([{'start':76,'small':24,'notable':48,'keystone':77,'road':14}[by[id]['type']] for id in ids],float);shared=np.maximum(radii,radii[mi]);threshold=shared[:,None]+shared[None,:]+28
 for iteration in range(320):
  dv=points[:,None,:]-points[None,:,:];dist=np.linalg.norm(dv,axis=2);overlap=np.maximum(threshold-dist,0);np.fill_diagonal(overlap,0);force=(dv/np.maximum(dist[:,:,None],1e-9)*overlap[:,:,None]*.45).sum(axis=1)+(target-points)*.018
  mf=force[mi].copy();mf[:,0]*=-1;force=(force+mf)*.5;force[locked]=0;length=np.linalg.norm(force,axis=1);force*=np.minimum(1,14/np.maximum(length,1e-9))[:,None];points+=force;mp=points[mi].copy();mp[:,0]*=-1;points=(points+mp)*.5
 for n,p in zip(nodes,points):n['x'],n['y']=[round(float(v),3) for v in p]
 graph=copy.deepcopy(source);graph.update(nodes=nodes,groups=groups,edges=list(edges.values()),status='unadopted_structure_v2',runtimeApplied=False)
 for key in ['budgetAudit','lineAudit','layoutAudit','chainReview','startBraids']:graph.pop(key,None)
 graph['structureProposal']={'adopted':False,'sourceCommit':'b944e28c','same645Effects':True,'noActiveSkillAcquisition':True,'districts':list(districts.values()),'keyBays':key_bays,'regionForms':{g['id']:'outward reward fork' for g in groups},'macroConnections':len(links),'note':'横帯を領域のまとまりへ変更した比較案。取得費用は未採用。'}
 return graph,mirror,shared,iteration+1

def main():
 source=json.loads((BASE/'graph.json').read_text(encoding='utf-8'));previous=json.loads((PREVIOUS/'candidate-graph.json').read_text(encoding='utf-8'))
 base.OUT=OUT;base.build=build
 base.main()
 graph=json.loads((OUT/'candidate-graph.json').read_text(encoding='utf-8'))
 report=json.loads((OUT/'cost-comparison.json').read_text(encoding='utf-8'));prev=base.compare(previous,graph)
 for table in ['singleKeyCosts','twoKeyExactSteinerCosts']:
  for row,old in zip(report[table],prev[table]):row['previous']=old['original'];row['deltaFromPrevious']=row['proposal']-row['previous']
 area=lambda b:(b['maxX']-b['minX'])*(b['maxY']-b['minY'])
 report['previousWorldArea']=round(area(previous['bounds']));report['areaRatioToPrevious']=round(area(graph['bounds'])/area(previous['bounds']),3)
 (OUT/'cost-comparison.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
 html=(OUT/'ProjectS_Passive_Structure_Comparison.html').read_text(encoding='utf-8')
 html=re.sub(r'(<script id="structureComparisonData" type="application/json">).*?(</script>)',lambda m:m[1]+json.dumps(report,ensure_ascii=False,separators=(',',':'))+m[2],html,flags=re.S)
 html=html.replace('</body>','<script id="previousGraphData" type="application/json">'+json.dumps(previous,ensure_ascii=False,separators=(',',':'))+'</script><script>'+(OUT/'district-ui.js').read_text(encoding='utf-8')+'</script></body>')
 html=html.replace('for(const g of (window.workshopFull?.display||DATA).groups){','for(const g of (window.structureV2?.captionGroups()||(window.workshopFull?.display||DATA).groups)){')
 html=html.replace('<title>ProjectS — 全体構造の比較案・未採用</title>','<title>ProjectS — 領域と分岐の構造案2・未採用</title>')
 (OUT/'ProjectS_Passive_Structure_V2.html').write_text(html,encoding='utf-8');(OUT/'ProjectS_Passive_Structure_Comparison.html').unlink()
 manifest=json.loads((OUT/'proposal-manifest.json').read_text(encoding='utf-8'));manifest.update(sourceCommit='b944e28c',previousProposalRetained=True,districtCount=15,previewStageOnly=True)
 (OUT/'proposal-manifest.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2),encoding='utf-8')
 print(json.dumps({'previousAreaRatio':report['areaRatioToPrevious'],'pairCases':report['twoKeyExactSteinerCosts'][:4]},ensure_ascii=False))
if __name__=='__main__':main()
