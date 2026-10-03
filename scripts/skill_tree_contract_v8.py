"""Preview-only input and allocation model; source fixtures remain verbatim."""
import collections, copy, heapq, json
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
BASE=ROOT/'assets/core-ui/large-tree-preview'
OLD=BASE/'proposals/workshop-middle-choice-v7'
OUT=BASE/'proposals/workshop-contract-v8'
def read(path): return json.loads(path.read_text(encoding='utf8'))
SOURCE=read(BASE/'graph.json')
OVERLAY=read(OUT/'input-overlay.json')
EFFECTIVE=copy.deepcopy(SOURCE)
EFFECTIVE['statInputs'].update(OVERLAY['statInputs'])
EFFECTIVE['requirements'].update(OVERLAY['requirements'])
for p in EFFECTIVE['profiles']:
    if OVERLAY['profileAreaSources'].get(p['id']):
        p['flags'].append('AREA_ATTACK')
    p['areaSources']=OVERLAY['profileAreaSources'].get(p['id'],[])
GRAPH=read(OUT/'candidate-graph.json')
BY={n['id']:n for n in GRAPH['nodes']}
ADJ={i:[] for i in BY}
for e in GRAPH['edges']:
    ADJ[e['a']].append(e['b']);ADJ[e['b']].append(e['a'])
PROFILES={p['id']:p for p in EFFECTIVE['profiles'] if p['weaponUsable']}
ROOTS={o['id']:o['root'] for o in SOURCE['origins']}

class Allocation:
    def __init__(self,pid,siphon=False,flags=None,owned=None):
        self.profile=PROFILES[pid]
        self.flags=set(self.profile['flags'] if flags is None else flags)|({'LIFESTEAL'} if siphon else set())
        self.owned=set(owned or [ROOTS[self.profile['start']]])
    def lost(self,extra=None):
        return {k for i in self.owned|({extra} if extra else set()) for k in SOURCE['lostStats'].get(BY[i].get('rule'),[])}
    def input_stats(self,i):
        n=BY[i];raw={'hp':2} if n['type']=='road' else n['stats']
        return {k:v for k,v in raw.items() if set(EFFECTIVE['statInputs'].get(k,[]))<=self.flags}
    def eligible(self,i,loss=()):
        n=BY[i]
        if n['type']=='start':return i==ROOTS[self.profile['start']]
        if n['type']=='keystone':return set(n.get('requirements',[]))<=self.flags
        return any(k not in loss for k in self.input_stats(i))
    def conflict(self,i):
        return any(j in BY[i].get('conflicts',[]) or i in BY[j].get('conflicts',[]) for j in self.owned)
    def route(self,target):
        loss=self.lost(target)
        if not self.eligible(target,loss) or self.conflict(target):return None
        prices={i:0 for i in self.owned};prev={};q=[(0,i) for i in self.owned];heapq.heapify(q)
        while q:
            c,u=heapq.heappop(q)
            if prices[u]!=c:continue
            if u==target:
                path=[u]
                while path[-1] in prev:path.append(prev[path[-1]])
                return path[::-1]
            for v in ADJ[u]:
                if v not in self.owned and (not self.eligible(v,loss) or self.conflict(v) or BY[v]['type']=='keystone' and v!=target):continue
                nc=c+(0 if v in self.owned else BY[v]['cost'])
                if nc<prices.get(v,10**9):prices[v]=nc;prev[v]=u;heapq.heappush(q,(nc,v))
        return None
    def add(self,target):
        path=self.route(target)
        if path is None:raise ValueError(('Unreachable',self.profile['id'],target))
        self.owned.update(path);return dict(target=target,nodes=path)
    def spent(self):return sum(BY[i]['cost'] for i in self.owned)
    def stats(self):
        total=collections.Counter();loss=self.lost()
        for i in self.owned:total.update({k:v for k,v in self.input_stats(i).items() if k not in loss})
        return dict(total)
    def dead(self):return sorted(i for i in self.owned if BY[i]['type'] not in ('start','keystone') and not any(k not in self.lost() for k in self.input_stats(i)))

def example(spec):
    a=Allocation(spec['profileId'],spec['siphon'])
    if 'preservedLearned' in spec:
        a.owned=set(spec['preservedLearned']);paths=spec['preservedPaths']
    else:paths=[a.add(t) for t in spec['targets']]
    clean={k:v for k,v in spec.items() if not k.startswith('preserved')}
    return dict(**clean,learned=sorted(a.owned),paths=paths,spent=a.spent(),stats=a.stats(),keys=sorted(i for i in a.owned if BY[i]['type']=='keystone'),remaining=spec['budget']-a.spent())
