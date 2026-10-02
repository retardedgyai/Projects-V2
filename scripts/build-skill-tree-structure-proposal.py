"""Separate, unadopted woven passive-map proposal. Preserve all 645 effects.

The original graph and approved workshop UI remain untouched. Only this
proposal rewires the graph. Costs are compared structurally, never as balance.
"""
import collections, copy, hashlib, heapq, json, math, re, sys
from pathlib import Path
import numpy as np

ROOT=Path(__file__).resolve().parents[1]
BASE=ROOT/'assets/core-ui/large-tree-preview'
OLD=BASE/'proposals/workshop-full-v1'
OUT=BASE/'proposals/workshop-structure-v1'
ROWS=[
 (-2100,[(700,'g8','g43'),(1400,'g23','g51')],'g20'),
 (-1400,[(350,'g2','g9'),(1050,'g24','g3'),(1750,'g31','g52'),(2450,'g46','g49')],None),
 (-700,[(350,'g4','g14'),(1050,'g12','g26'),(1750,'g38','g10'),(2450,'g16','g37')],None),
 (0,[(1400,'g7','g42'),(2100,'g25','g32')],'g33'),
 (700,[(350,'g11','g28'),(1050,'g35','g21'),(1750,'g6','g34'),(2450,'g30','g44')],None),
 (1400,[(350,'g40','g47'),(1050,'g45','g17'),(1750,'g13','g41'),(2450,'g48','g27')],None),
 (2100,[(700,'g18','g39'),(1400,'g5','g19')],'g50')]

def template(count,notables):
 # Two reward lanes converge or branch at a Notable. No repeated small circles.
 if count==10:
  pts=[(-250,0),(-150,-110),(-30,-135),(90,-110),(220,-90),(-150,110),(-30,135),(90,110),(220,90),(330,0)]
  edges=[(0,1),(1,2),(2,3),(3,4),(0,5),(5,6),(6,7),(7,8),(4,9),(8,9),(2,6)]
  ns=[4,8]
 elif count==9:
  pts=[(-240,0),(-120,-100),(0,-125),(120,-100),(240,-80),(-120,100),(0,125),(120,100),(240,80)]
  edges=[(0,1),(1,2),(2,3),(3,4),(0,5),(5,6),(6,7),(7,8),(2,6)];ns=[4,8]
 elif count==8:
  pts=[(-220,-85),(-80,-85),(60,-85),(200,-85),(-220,85),(-80,85),(60,85),(200,85)]
  edges=[(0,1),(1,2),(2,3),(4,5),(5,6),(6,7),(0,4),(1,5),(2,6),(3,7)];ns=[3] if notables==1 else [3,7]
 elif count==7:
  pts=[(-240,0),(-100,-100),(40,-100),(-100,100),(40,100),(190,0),(320,0)]
  edges=[(0,1),(1,2),(2,5),(0,3),(3,4),(4,5),(5,6)];ns=[5]
 elif count==6:
  pts=[(-180,0),(-60,-100),(60,-100),(-60,100),(60,100),(180,0)]
  edges=[(0,1),(1,2),(2,5),(0,3),(3,4),(4,5)];ns=[5]
 elif count==5:
  pts=[(-160,0),(-40,-95),(-40,95),(90,0),(220,0)]
  edges=[(0,1),(0,2),(1,3),(2,3),(3,4)];ns=[4]
 elif count==4:
  pts=[(-140,0),(0,-95),(0,95),(140,0)]
  edges=[(0,1),(1,3),(0,2),(2,3)];ns=[3]
 else:raise ValueError(count)
 return pts,edges,ns

def build(source):
 nodes=copy.deepcopy(source['nodes']);by={n['id']:n for n in nodes}
 groups=copy.deepcopy(source['groups']);gb={g['id']:g for g in groups}
 positions={};mirror={};edges={};centers={};gm={};patterns={};fixed=set()
 def put(id,p):positions[id]=np.array(p,float)
 def pair(a,b,p):put(a,p);put(b,[-p[0],p[1]]);mirror[a]=b;mirror[b]=a
 def axis(id,p):put(id,[0,p[1]]);mirror[id]=id
 def edge(a,b,kind='regional'):
  if a==b:return
  key=tuple(sorted((a,b)));edges[key]={'a':a,'b':b,'road':kind!='regional','proposalKind':kind}
 def assign(g,pts,ns):
  notables=sorted([id for id in g['nodes'] if by[id]['type']=='notable'])
  small=sorted([id for id in g['nodes'] if by[id]['type']!='notable'])
  ids=[None]*len(pts)
  for i,id in zip(ns,notables):ids[i]=id
  for i,id in zip([i for i,v in enumerate(ids) if v is None],small):ids[i]=id
  assert all(ids)
  return ids
 for y,pairs,central in ROWS:
  for x,left,right in pairs:
   g,h=gb[left],gb[right];assert len(g['nodes'])==len(h['nodes'])
   nn=sum(by[id]['type']=='notable' for id in g['nodes']);assert nn==sum(by[id]['type']=='notable' for id in h['nodes'])
   pts,local,ns=template(len(g['nodes']),nn);variant=len(patterns)//2%3
   if variant==1:pts=[(v*.85,-u*.8) for u,v in pts]
   elif variant==2:pts=[(u+v*.35,v+u*.2) for u,v in pts]
   ids=assign(g,pts,ns);rids=assign(h,pts,ns)
   for a,b,p in zip(ids,rids,pts):pair(a,b,[-x+p[0],y+p[1]])
   for a,b in local:edge(ids[a],ids[b]);edge(rids[a],rids[b])
   centers[left]=(-x,y);centers[right]=(x,y);gm[left]=right;gm[right]=left
   patterns[left]=patterns[right]=['fork / two lanes','vertical ladder','slanted fan'][variant]
  if central:
   g=gb[central];centers[central]=(0,y);gm[central]=central
   if central=='g20':
    pts=[(-120,-170),(120,-170),(-120,-40),(120,-40),(-230,80),(230,80),(-120,200),(120,200),(-230,320),(230,320)]
    ns=[0,1];local=[(0,2),(2,4),(4,6),(6,8),(1,3),(3,5),(5,7),(7,9),(2,3),(6,7)]
   elif central=='g33':
    pts=[(0,0),(-140,-110),(140,-110),(-140,110),(140,110)];ns=[0];local=[(0,1),(0,2),(0,3),(0,4),(1,3),(2,4)]
   else:pts=[(0,140),(-120,0),(120,0)];ns=[0];local=[(0,1),(0,2),(1,2)]
   ids=assign(g,pts,ns)
   for i,p in enumerate(pts):
    put(ids[i],[p[0],y+p[1]])
    j=next(j for j,q in enumerate(pts) if abs(q[0]+p[0])<1e-8 and q[1]==p[1]);mirror[ids[i]]=ids[j]
   for a,b in local:edge(ids[a],ids[b])
   patterns[central]='symmetric split / spine'
 # Five starts and the original first three-point reward choices stay intact.
 origin_by={o['id']:o for o in source['origins']}
 rootpos={'warrior':(-600,360),'tank':(-600,-360),'mage':(0,-700),'ranger':(600,-360),'assassin':(600,360)}
 angles={'warrior':150,'tank':-150,'mage':-90,'ranger':-30,'assassin':30}
 for o in source['origins']:
  root=o['root'];put(root,rootpos[o['id']]);fixed.add(root)
  theta=math.radians(angles[o['id']]);unit=np.array([math.cos(theta),math.sin(theta)]);normal=np.array([-unit[1],unit[0]])
  for lane,entry in enumerate(o['lanes']):
   prev=root
   for depth,id in enumerate(entry['nodes'],1):
    put(id,np.array(rootpos[o['id']])+unit*(150*depth)+normal*((lane-1)*300));edge(prev,id,'opening');prev=id;fixed.add(id)
 for a,b in [('tank','ranger'),('warrior','assassin')]:
  aa=[n['id'] for n in nodes if n.get('origin')==a and n['id'] in fixed]
  bb=set(n['id'] for n in nodes if n.get('origin')==b and n['id'] in fixed)
  for id in aa:
   p=positions[id];j=min(bb,key=lambda q:np.linalg.norm(positions[q]-[-p[0],p[1]]));bb.remove(j);pair(id,j,p)
 mage=[n['id'] for n in nodes if n.get('origin')=='mage' and n['id'] in fixed]
 for id in mage:
  p=positions[id];j=min(mage,key=lambda q:np.linalg.norm(positions[q]-[-p[0],p[1]]));mirror[id]=j
 # Reuse the original connector nodes. No additional reward nodes are created.
 pool=[n['id'] for n in nodes if n['id'] not in positions and n['type']!='keystone']
 def take(road=False):
  ix=next((i for i,id in enumerate(pool) if not road or by[id]['type']=='road'),None)
  assert ix is not None
  return pool.pop(ix)
 def reserve(p,road=False):
  p=np.array(p,float)
  if abs(p[0])<1e-6:
   id=take(road);axis(id,p);return id,id
  a=take(road);b=take(road);pair(a,b,p);return a,b
 # Each region has a general-reward junction, so specialist passives are optional.
 junction={}
 for gid,center in centers.items():
  if gid in junction:continue
  x,y=center
  p=(x-320 if x<0 else 0,y+40 if x else y+440)
  if not x and gid=='g33':p=(0,-260)
  a,b=reserve(p,True);junction[gid]=a;junction[gm[gid]]=b
 for gid,id in junction.items():
  g=gb[gid];near=sorted(g['nodes'],key=lambda q:np.linalg.norm(positions[q]-positions[id]))
  candidates=[q for q in near if by[q]['type']=='small'][:2]
  for q in candidates:edge(id,q,'entry-choice')
 # Optional Keystone bays branch from a regional Notable, never gate travel.
 key_by_group={n['group']:n['id'] for n in nodes if n['type']=='keystone'}
 handled=set();key_links=[]
 for gid,key in key_by_group.items():
  if gid in handled:continue
  partner=gm[gid];other=key_by_group.get(partner);handled|={gid,partner}
  cx,cy=centers[gid];sign=-1 if cx<0 else 1;vertical=-1 if cy<=0 else 1
  if gid=='g50':
   kp=(0,cy+650);path=[]
   for y in [cy+300,cy+430]:a,b=reserve((0,y),True);path.append(a)
   axis(key,kp);anchor=max(gb[gid]['notables'],key=lambda q:positions[q][1])
   for a,b in zip([anchor]+path,path+[key]):edge(a,b,'optional-keystone')
   key_links.append({'key':key,'anchor':anchor,'detourNodes':path});continue
  kp=(cx+sign*180,cy+vertical*400)
  if cx==0:kp=(300,cy-410)
  put(key,kp)
  if other and partner!=gid:
   put(other,(-kp[0],kp[1]));mirror[key]=other;mirror[other]=key;leaf=other
  else:
   leaf=take();put(leaf,(-kp[0],kp[1]));mirror[key]=leaf;mirror[leaf]=key
  path=[];rpath=[]
  for p in [(kp[0]+sign*170,kp[1]-vertical*240),(kp[0]+sign*160,kp[1]-vertical*85)]:
   a,b=reserve(p,True);path.append(a);rpath.append(b)
  anchor=min(gb[gid]['notables'],key=lambda q:np.linalg.norm(positions[q]-positions[path[0]]))
  ra=mirror[anchor]
  for a,b in zip([anchor]+path,path+[key]):edge(a,b,'optional-keystone')
  for a,b in zip([ra]+rpath,rpath+[leaf]):edge(a,b,'optional-keystone' if other else 'reward-bay')
  key_links.append({'key':key,'anchor':anchor,'detourNodes':path})
  if other and partner!=gid:key_links.append({'key':other,'anchor':ra,'detourNodes':rpath})
 # Nearby regions form a mesh of short crossways and loops, not isolated rings.
 macro=set()
 for y,pairs,central in ROWS:
  row=sorted([g for g,c in centers.items() if c[1]==y],key=lambda g:centers[g][0])
  macro.update(tuple(sorted((a,b))) for a,b in zip(row,row[1:]))
 ys=[r[0] for r in ROWS]
 for ya,yb in zip(ys,ys[1:]):
  aa=[g for g,c in centers.items() if c[1]==ya];bb=[g for g,c in centers.items() if c[1]==yb]
  for left,right in [(aa,bb),(bb,aa)]:
   for a in left:
    b=min(right,key=lambda g:(math.dist(centers[a],centers[g]),abs(centers[g][0])))
    macro.add(tuple(sorted((a,b))));macro.add(tuple(sorted((gm[a],gm[b]))))
  # One mirrored diagonal pair per band supplies a crossway without a wire web.
  diagonal=sorted([(math.dist(centers[a],centers[b]),a,b) for a in aa for b in bb if centers[a][0]<0 and centers[b][0]<0 and 500<abs(centers[a][0]-centers[b][0])<900])
  if diagonal:
   _,a,b=diagonal[len(diagonal)//2];macro.add(tuple(sorted((a,b))));macro.add(tuple(sorted((gm[a],gm[b]))))
 # Join each three-point opening to distinct nearby regions; mirror the two pairs.
 start_routes=[]
 for a,b in [('tank','ranger'),('warrior','assassin'),('mage','mage')]:
  o=origin_by[a];choices=sorted(centers,key=lambda g:math.dist(centers[g],rootpos[a]))
  choices=[g for g in choices if centers[g][0]<=0] if a!='mage' else ['g2','g20','g9']
  for lane,gid in zip(o['lanes'],choices[:3]):
   tail=lane['nodes'][-1];start_routes.append((tail,junction[gid]))
   mt=mirror[tail];mg=gm[gid]
   if mt!=tail:start_routes.append((mt,junction[mg]))
 macro_paths=[(junction[a],junction[b],'crossway') for a,b in sorted(macro)]+[(a,b,'opening-exit') for a,b in start_routes]
 # Route each geometric edge orbit once and reflect its existing reward nodes.
 orbits=[];seen=set()
 for a,b,kind in macro_paths:
  key=tuple(sorted((a,b)))
  if key in seen:continue
  ma,mb=mirror[a],mirror[b];mk=tuple(sorted((ma,mb)));seen|={key,mk}
  d=math.dist(positions[a],positions[b]);count=max(1,math.ceil(d/410)-1)
  orbits.append({'a':a,'b':b,'ma':ma,'mb':mb,'paired':key!=mk,'count':count,'kind':kind,'length':d})
 needed=sum(o['count']*(2 if o['paired'] else 1) for o in orbits)
 # Keep the finite connector pool. Longest crossways receive the remaining nodes.
 while needed>len(pool):
  candidates=[o for o in orbits if o['count']>0]
  q=min(candidates,key=lambda o:o['length']/max(1,o['count']))
  q['count']-=1;needed-=2 if q['paired'] else 1
 while needed<len(pool):
  candidates=[o for o in orbits if (2 if o['paired'] else 1)<=len(pool)-needed]
  if not candidates:break
  q=max(candidates,key=lambda o:o['length']/(o['count']+1));q['count']+=1;needed+=2 if q['paired'] else 1
 assert needed==len(pool),(needed,len(pool))
 for o in orbits:
  a,b=o['a'],o['b'];ma,mb=o['ma'],o['mb'];ps=[];ms=[]
  for i in range(o['count']):
   t=(i+1)/(o['count']+1);p=positions[a]*(1-t)+positions[b]*t
   if o['paired']:v,m=reserve(p);ps.append(v);ms.append(m)
   elif abs(p[0])<1e-6:v,m=reserve(p);ps.append(v)
   else:
    # A self-reflecting crossway has paired positions along the same path.
    if i>=o['count']//2 and o['count']%2==0:continue
    if i>o['count']//2 and o['count']%2:continue
    v,m=reserve(p);ps.append(v)
    if v!=m:ms.insert(0,m)
  if not o['paired']:ps+=ms
  for x,y in zip([a]+ps,ps+[b]):edge(x,y,o['kind'])
  if o['paired']:
   for x,y in zip([ma]+ms,ms+[mb]):edge(x,y,o['kind'])
 assert not pool and len(positions)==645,(len(pool),len(positions))
 # Symmetric relaxation clears real bodies without changing the macro silhouette.
 ids=[n['id'] for n in nodes];index={id:i for i,id in enumerate(ids)}
 points=np.array([positions[id] for id in ids]);target=points.copy();mi=np.array([index[mirror[id]] for id in ids]);locked=np.array([id in fixed for id in ids]);radii=np.array([{'start':76,'small':24,'notable':48,'keystone':77,'road':14}[by[id]['type']] for id in ids],float)
 shared=np.maximum(radii,radii[mi]);radius=shared[:,None]+shared[None,:]+24
 for iteration in range(280):
  delta=points[:,None,:]-points[None,:,:];dist=np.linalg.norm(delta,axis=2);overlap=np.maximum(radius-dist,0);np.fill_diagonal(overlap,0)
  force=(delta/np.maximum(dist[:,:,None],1e-9)*overlap[:,:,None]*.42).sum(axis=1)
  force+=(target-points)*.022
  mirror_force=force[mi].copy();mirror_force[:,0]*=-1;force=(force+mirror_force)*.5;force[locked]=0
  magnitude=np.linalg.norm(force,axis=1);force*=np.minimum(1,15/np.maximum(magnitude,1e-9))[:,None];points+=force
  mirrored=points[mi].copy();mirrored[:,0]*=-1;points=(points+mirrored)*.5
 for n,p in zip(nodes,points):n['x'],n['y']=[round(float(v),3) for v in p]
 graph=copy.deepcopy(source);graph['nodes']=nodes;graph['edges']=list(edges.values());graph['groups']=groups
 graph['status']='unadopted_structure_proposal';graph['runtimeApplied']=False
 # Never carry old numerical layout/connection audits as the new graph's evidence.
 for k in ['budgetAudit','lineAudit','layoutAudit','chainReview','startBraids']:graph.pop(k,None)
 graph['structureProposal']={'adopted':False,'sourceCommit':'c6a8c919','same645Effects':True,'noActiveSkillAcquisition':True,'keyBays':key_links,'regionForms':patterns,'macroConnections':len(macro),'note':'接続・配置を変更した比較案。取得費用は原版と異なる。本番未採用。'}
 return graph,mirror,shared,iteration+1

def route(graph,radii,mirror):
 nodes=graph['nodes'];by={n['id']:n for n in nodes};ids={n['id']:i for i,n in enumerate(nodes)};coords=np.array([[n['x'],n['y']] for n in nodes]);rr=np.array(radii)+10
 def hits(a,b,ends):
  a=np.array(a);b=np.array(b);d=b-a;t=np.clip(((coords-a)@d)/max(float(d@d),1e-9),0,1);mask=np.linalg.norm(coords-(a+t[:,None]*d),axis=1)<rr;mask[list(ends)]=False
  return np.where(mask)[0],t
 unresolved=[];detours=0
 for e in graph['edges']:
  ends=(ids[e['a']],ids[e['b']]);ps=[[by[e['a']]['x'],by[e['a']]['y']],[by[e['b']]['x'],by[e['b']]['y']]]
  for attempt in range(45):
   collision=None
   for si,(a,b) in enumerate(zip(ps,ps[1:])):
    bad,ts=hits(a,b,ends)
    if len(bad):collision=(si,np.array(a),np.array(b),int(bad[0]),float(ts[bad[0]]));break
   if collision is None:break
   si,a,b,ni,t=collision;d=b-a;length=np.linalg.norm(d)
   if length<1e-7:break
   foot=a+d*t;normal=np.array([-d[1],d[0]])/length;options=[]
   for sign in [-1,1]:
    for extra in [18,50,100,180]:
     q=(foot+normal*(rr[ni]+extra)*sign).tolist();options.append((len(hits(a,q,ends)[0])+len(hits(q,b,ends)[0]),extra,q))
   _,_,q=min(options,key=lambda x:(x[0],x[1]));ps.insert(si+1,q);detours+=1
  simple=[ps[0]];k=0
  while k<len(ps)-1:
   j=len(ps)-1
   while j>k+1 and len(hits(ps[k],ps[j],ends)[0]):j-=1
   simple.append(ps[j]);k=j
  e['points']=[[round(float(v),3) for v in p] for p in simple];e['crossingGaps']=[]
  for a,b in zip(simple,simple[1:]):
   bad,_=hits(a,b,ends)
   if len(bad):unresolved.append({'edge':[e['a'],e['b']],'nodes':[nodes[i]['id'] for i in bad]});break
 segments=[(ei,si,a,b) for ei,e in enumerate(graph['edges']) for si,(a,b) in enumerate(zip(e['points'],e['points'][1:]))];crossings=0;wire_overlaps=[]
 for k,(ei,si,a,b) in enumerate(segments):
  for ej,sj,c,d in segments[k+1:]:
   if ei==ej or {graph['edges'][ei]['a'],graph['edges'][ei]['b']}&{graph['edges'][ej]['a'],graph['edges'][ej]['b']}:continue
   ux,uy=b[0]-a[0],b[1]-a[1];vx,vy=d[0]-c[0],d[1]-c[1];den=ux*vy-uy*vx
   if abs(den)<1e-7:
    length=math.hypot(ux,uy);wx,wy=c[0]-a[0],c[1]-a[1]
    if length>1e-5 and abs(wx*uy-wy*ux)/length<.001:
     t1=(wx*ux+wy*uy)/(length*length);t2=((d[0]-a[0])*ux+(d[1]-a[1])*uy)/(length*length)
     overlap=(min(1,max(t1,t2))-max(0,min(t1,t2)))*length
     if overlap>2:wire_overlaps.append({'edges':[ei,ej],'length':round(overlap,3)})
    continue
   wx,wy=c[0]-a[0],c[1]-a[1];t=(wx*vy-wy*vx)/den;v=(wx*uy-wy*ux)/den
   if 1e-5<t<1-1e-5 and 1e-5<v<1-1e-5:graph['edges'][ei]['crossingGaps'].append({'segment':si,'t':round(t,8)});crossings+=1
 for g in graph['groups']:
  ps=np.array([[by[id]['x'],by[id]['y']] for id in g['nodes']]);g['x'],g['y']=ps.mean(axis=0).tolist();g['labelX']=g['x'];g['labelY']=float(ps[:,1].min()-85)
  # No decorative closed-circle outline: the route and fork define each region.
  g['outline']=[]
 points=coords.tolist()+[p for e in graph['edges'] for p in e['points']];xs=[p[0] for p in points];ys=[p[1] for p in points]
 graph['bounds']={'minX':min(xs)-120,'maxX':max(xs)+120,'minY':min(ys)-120,'maxY':max(ys)+120}
 dd=np.linalg.norm(coords[:,None,:]-coords[None,:,:],axis=2);rs=np.array(radii);threshold=rs[:,None]+rs[None,:]+8;np.fill_diagonal(dd,1e9)
 pairs=[(nodes[i]['id'],nodes[j]['id']) for i,j in zip(*np.where(np.triu(dd<threshold,1)))]
 symmetry=max(math.dist([by[id]['x'],by[id]['y']],[-by[m]['x'],by[m]['y']]) for id,m in mirror.items())
 lengths=[sum(math.dist(a,b) for a,b in zip(e['points'],e['points'][1:])) for e in graph['edges']]
 audit={'nodes':len(nodes),'regions':len(graph['groups']),'connections':len(graph['edges']),'all645CoordinatesMirrored':symmetry<.003,'mirrorMaxDeviation':round(symmetry,6),'nodeOverlaps':pairs,'edgeNodeCollisions':unresolved,'collinearWireOverlaps':wire_overlaps,'detours':detours,'nonVertexCrossingsMarkedWithGaps':crossings,'maxRoutedLength':round(max(lengths),2),'medianRoutedLength':round(float(np.median(lengths)),2),'bounds':graph['bounds']}
 return audit

def compare(source,graph):
 def distances(d,root,allowed_keys=()):
  by={n['id']:n for n in d['nodes']};adj={id:[] for id in by}
  for e in d['edges']:adj[e['a']].append(e['b']);adj[e['b']].append(e['a'])
  blocked={id for id,n in by.items() if n['type']=='start' and id!=root or n['type']=='keystone' and id not in allowed_keys}
  def search(s):
   price={id:math.inf for id in by};price[s]=by[s]['cost'];todo=[(price[s],s)];prev={}
   while todo:
    cost,u=heapq.heappop(todo)
    if cost!=price[u]:continue
    for v in adj[u]:
     if v in blocked:continue
     new=cost+by[v]['cost']
     if new<price[v]:price[v]=new;prev[v]=u;heapq.heappush(todo,(new,v))
   return price,prev
  return by,search
 singles=[];pairs=[];keys=[n['id'] for n in source['nodes'] if n['type']=='keystone']
 for o in source['origins']:
  for key in keys:
   prices=[]
   for d in [source,graph]:
    by,search=distances(d,o['root'],[key]);prices.append(search(o['root'])[0][key])
   singles.append({'origin':o['id'],'key':key,'original':prices[0],'proposal':prices[1],'delta':prices[1]-prices[0]})
  for ka,kb in [('key_01','key_05'),('key_02','key_09'),('key_13','key_15'),('key_14','key_15')]:
   costs=[]
   for d in [source,graph]:
    by,search=distances(d,o['root'],[ka,kb]);a,_=search(o['root']);b,_=search(ka);c,_=search(kb)
    costs.append(min(a[id]+b[id]+c[id]-2*by[id]['cost'] for id in by))
   pairs.append({'origin':o['id'],'keys':[ka,kb],'original':costs[0],'proposal':costs[1],'delta':costs[1]-costs[0]})
 # Reject any hidden dependency on a Keystone for general map traversal.
 for o in source['origins']:
  by,search=distances(graph,o['root']);reachable=search(o['root'])[0]
  assert all(math.isfinite(v) for id,v in reachable.items() if by[id]['type'] not in ['start','keystone'])
 old_bounds=json.loads((OLD/'display-layout.json').read_text(encoding='utf-8'))['bounds'];nb=graph['bounds']
 area=lambda b:(b['maxX']-b['minX'])*(b['maxY']-b['minY'])
 return {'structuralOnly':True,'inputRequirementsStillEnforcedByUI':True,'otherStartsCannotBeTransit':True,'allNonKeyNodesReachableWithoutKeys':True,'singleKeyCosts':singles,'twoKeyExactSteinerCosts':pairs,'oldWorldArea':round(area(old_bounds)),'newWorldArea':round(area(nb)),'areaRatio':round(area(nb)/area(old_bounds),3),'warning':'取得費用の低下は強化採用を意味しない。複数Keyを取る予算は再検討が必要。効果・係数・本番ポイントは未採用。'}

def main():
 source=json.loads((BASE/'graph.json').read_text(encoding='utf-8'))
 if '--reuse-layout' in sys.argv:
  graph=json.loads((OUT/'candidate-graph.json').read_text(encoding='utf-8'));mirror=json.loads((OUT/'mirror-map.json').read_text(encoding='utf-8'));audit=json.loads((OUT/'layout-verification.json').read_text(encoding='utf-8'))
 else:
  graph,mirror,radii,iterations=build(source);audit=route(graph,radii,mirror);audit['relaxationIterations']=iterations
 assert not audit['nodeOverlaps'] and not audit['edgeNodeCollisions'] and not audit.get('collinearWireOverlaps',[]),json.dumps(audit,ensure_ascii=False)
 assert audit['all645CoordinatesMirrored']
 strip=lambda n:{k:v for k,v in n.items() if k not in ['x','y']}
 assert [strip(n) for n in graph['nodes']]==[strip(n) for n in source['nodes']]
 report=compare(source,graph);OUT.mkdir(parents=True,exist_ok=True)
 for name,obj in [('candidate-graph.json',graph),('layout-verification.json',audit),('cost-comparison.json',report),('mirror-map.json',mirror)]:
  (OUT/name).write_text(json.dumps(obj,ensure_ascii=False,indent=2),encoding='utf-8')
 baseline_display=json.loads((OLD/'display-layout.json').read_text(encoding='utf-8'))
 display={k:graph[k] for k in ['nodes','edges','groups','bounds']}
 html=(OLD/'ProjectS_Passive_Workshop_Full.html').read_text(encoding='utf-8')
 html=re.sub(r'(<script id="displayData" type="application/json">).*?(</script>)',lambda m:m[1]+json.dumps(display,ensure_ascii=False,separators=(',',':'))+m[2],html,flags=re.S)
 html=html.replace('window.workshopFull={focusStarts,viewNodes,viewEdges,display:DISPLAY','window.workshopFull={focusStarts,viewNodes,viewGroups,viewEdges,display:DISPLAY')
 html=html.replace("(n.opening&&n.origin===origin&&cam.z>.29)","(n.opening&&n.origin===origin&&cam.z>.29&&(by.get(selected).type==='start'||by.get(selected).opening))")
 html=html.replace('<title>ProjectS — 工房UI・全47領域</title>','<title>ProjectS — 全体構造の比較案・未採用</title>')
 extra='<script id="originalDisplayData" type="application/json">'+json.dumps(baseline_display,ensure_ascii=False,separators=(',',':'))+'</script><script id="structureGraphData" type="application/json">'+json.dumps(graph,ensure_ascii=False,separators=(',',':'))+'</script><script id="structureComparisonData" type="application/json">'+json.dumps(report,ensure_ascii=False,separators=(',',':'))+'</script><script>'+(OUT/'proposal.js').read_text(encoding='utf-8')+'</script>'
 html=html.replace('</body>',extra+'</body>')
 assert json.loads(re.search(r'<script id="treeData" type="application/json">(.*?)</script>',html,re.S)[1])==source
 (OUT/'ProjectS_Passive_Structure_Comparison.html').write_text(html,encoding='utf-8')
 manifest={'sourceCommit':'c6a8c919','referenceLibraryId':'libfile_207828053bd48191bfabd38b31e47244','adopted':False,'originalsChanged':False,'same645NodeEffects':True,'connectionsChanged':True,'nativeMinecraft':False,'activeSkillsObtained':False,'sourceSha256':hashlib.sha256((BASE/'graph.json').read_bytes()).hexdigest()}
 (OUT/'proposal-manifest.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2),encoding='utf-8');print(json.dumps(audit,ensure_ascii=False));print(json.dumps({'areaRatio':report['areaRatio'],'singleCostCases':len(report['singleKeyCosts']),'pairCostCases':len(report['twoKeyExactSteinerCosts'])}))
if __name__=='__main__':main()
