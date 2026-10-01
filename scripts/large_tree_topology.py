import collections,math

def adjacency(d,active=None):
 by={n['id']:n for n in d['nodes'] if n['type']!='keystone' and (active is None or n['id'] in active)};adj={i:[] for i in by}
 for e in d['edges']:
  if e['a'] in by and e['b'] in by:adj[e['a']].append(e['b']);adj[e['b']].append(e['a'])
 return by,adj

def chains(d,active=None):
 by,adj=adjacency(d,active);seen=set();out=[]
 for i in sorted(by):
  if len(adj[i])==2:continue
  for j in sorted(adj[i]):
   pair=tuple(sorted((i,j)))
   if pair in seen:continue
   path=[i,j];seen.add(pair)
   while len(adj[path[-1]])==2:
    a,b=path[-2:];c=next(x for x in adj[b] if x!=a);pair=tuple(sorted((b,c)))
    if pair in seen:break
    path.append(c);seen.add(pair)
   out.append(path)
 return out

def chain_report(d,active=None):
 paths=chains(d,active);hist=collections.Counter(len(p)-2 for p in paths)
 return {'maxDegree2Interiors':max(hist,default=0),'histogram':dict(sorted(hist.items())),
  'longChains':[{'interiors':len(p)-2,'path':p} for p in paths if len(p)>4],
  'definition':'Keyを除くgraph。分岐点または端から次の分岐点または端までの、次数2の中間ノード数。最大2個なら次選択まで最大3辺/3pt。'}

def old_route(adj,a,b):
 prev={a:None};q=collections.deque([a])
 while q:
  here=q.popleft()
  if here==b:
   p=[b]
   while prev[p[-1]] is not None:p.append(prev[p[-1]])
   return p[::-1]
  for nxt in adj[here]:
   if nxt not in prev:prev[nxt]=here;q.append(nxt)
 return None

def limit_chains(d,add,active=None,label='all-graph'):
 corrections=[]
 for iteration in range(500):
  paths=sorted((p for p in chains(d,active) if len(p)>4),key=lambda p:(-len(p),p))
  if not paths:return corrections
  path=paths[0];by,adj=adjacency(d,active)
  possible=[i for i in path[2:-1] if by[i]['type']!='start']
  if not possible:raise ValueError('No reusable intermediate point')
  a=possible[0];options=[];prev={a:None};q=collections.deque([a])
  while q:
   here=q.popleft()
   for nxt in adj[here]:
    if nxt not in prev:prev[nxt]=here;q.append(nxt)
  for b,n in by.items():
   if b in path or n['type']=='start' or not adj[b]:continue
   distance=math.hypot(by[a]['x']-n['x'],by[a]['y']-n['y'])
   if b not in prev:continue
   route=[b]
   while prev[route[-1]] is not None:route.append(prev[route[-1]])
   route=route[::-1]
   if len(route)<4:continue
   score=distance+max(0,len(adj[b])-3)*70+(0 if n.get('group')==by[a].get('group') else 90)
   options.append((score,distance,b,route))
  if not options:raise ValueError('No forward-return route for '+a)
  _,distance,b,route=min(options)
  if not add(a,b,'choice-interval-crossing',previousRoute=route,context=label):raise ValueError('Duplicate crossing')
  corrections.append({'a':a,'b':b,'oldRoute':route,'distance':round(distance,1),'context':label,
    'isDeadEnd':False,'sourceValidRoute':active is not None})
 raise ValueError('Choice interval did not converge')
