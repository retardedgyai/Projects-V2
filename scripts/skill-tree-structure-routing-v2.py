"""Shorten only geometric detours; never change nodes, edges, or point costs."""
import heapq, math
import numpy as np

def improve_routes(graph, radii, mirror, audit):
    nodes=graph['nodes']; by={n['id']:n for n in nodes}; ix={n['id']:i for i,n in enumerate(nodes)}
    coords=np.array([[n['x'],n['y']] for n in nodes]); rr=np.array(radii)+10
    edges=graph['edges']; lookup={tuple(sorted((e['a'],e['b']))):e for e in edges}
    def length(ps):return sum(math.dist(a,b) for a,b in zip(ps,ps[1:]))
    def clear(a,b,ends):
        a=np.array(a);b=np.array(b);dv=b-a;t=np.clip(((coords-a)@dv)/max(float(dv@dv),1e-9),0,1)
        hit=np.linalg.norm(coords-(a+t[:,None]*dv),axis=1)<rr;hit[list(ends)]=False
        return not hit.any()
    def visibility_path(e, orbit):
        a=np.array(e['points'][0]);b=np.array(e['points'][-1]);ends=(ix[e['a']],ix[e['b']]);dv=b-a
        t=np.clip(((coords-a)@dv)/max(float(dv@dv),1e-9),0,1);distance=np.linalg.norm(coords-(a+t[:,None]*dv),axis=1)
        nearby=np.where(distance<rr+110)[0];nearby=[i for i in nearby if i not in ends]
        points=[a,b]
        # Circumscribed polygons keep their chords outside each node's clearance.
        for i in nearby:
            radius=(rr[i]+5)/math.cos(math.pi/16)
            for j in range(16):
                theta=j*math.tau/16+(orbit%17)*.009
                p=coords[i]+radius*np.array([math.cos(theta),math.sin(theta)])
                if all(math.dist(p,coords[k])>=rr[k]+1 for k in range(len(nodes)) if k not in ends):points.append(p)
        adj=[[] for _ in points]
        for i,p in enumerate(points):
            for j in range(i+1,len(points)):
                q=points[j]
                if clear(p,q,ends):
                    cost=math.dist(p,q);adj[i].append((j,cost));adj[j].append((i,cost))
        dist=[math.inf]*len(points);dist[0]=0;parent={};queue=[(0,0)]
        while queue:
            cost,i=heapq.heappop(queue)
            if cost!=dist[i]:continue
            if i==1:break
            for j,w in adj[i]:
                if cost+w<dist[j]:dist[j]=cost+w;parent[j]=i;heapq.heappush(queue,(dist[j],j))
        if not math.isfinite(dist[1]):return e['points']
        path=[1]
        while path[-1]!=0:path.append(parent[path[-1]])
        return [[round(float(v),3) for v in points[i]] for i in reversed(path)]
    seen=set();shortened=0;before=max(length(e['points']) for e in edges)
    for orbit,e in enumerate(edges):
        key=tuple(sorted((e['a'],e['b'])))
        if key in seen:continue
        reflected=tuple(sorted((mirror[e['a']],mirror[e['b']])));other=lookup.get(reflected);seen|={key,reflected}
        ps=e['points'];ends=(ix[e['a']],ix[e['b']])
        if other is not None and other is not e:
            reflected_points=[[-p[0],p[1]] for p in other['points']]
            if other['a']!=mirror[e['a']]:reflected_points.reverse()
            if length(reflected_points)<length(ps) and all(clear(a,b,ends) for a,b in zip(reflected_points,reflected_points[1:])):ps=reflected_points
        direct=math.dist(ps[0],ps[-1])
        e['points']=ps
        if length(ps)>600 or length(ps)>direct*1.45:
            candidate=visibility_path(e,orbit)
            if length(candidate)<length(ps) and all(clear(a,b,ends) for a,b in zip(candidate,candidate[1:])):
                ps=candidate;shortened+=1
        e['points']=ps
        if other is not None and other is not e:
            ps2=[[-p[0],p[1]] for p in ps]
            if other['a']!=mirror[e['a']]:ps2.reverse()
            other['points']=ps2
    unresolved=[];segments=[]
    for ei,e in enumerate(edges):
        e['crossingGaps']=[];ends=(ix[e['a']],ix[e['b']])
        for si,(a,b) in enumerate(zip(e['points'],e['points'][1:])):
            if not clear(a,b,ends):unresolved.append({'edge':[e['a'],e['b']],'segment':si})
            segments.append((ei,si,a,b))
    crossings=0;overlaps=[]
    for k,(ei,si,a,b) in enumerate(segments):
        for ej,sj,c,d in segments[k+1:]:
            if ei==ej or {edges[ei]['a'],edges[ei]['b']}&{edges[ej]['a'],edges[ej]['b']}:continue
            ux,uy=b[0]-a[0],b[1]-a[1];vx,vy=d[0]-c[0],d[1]-c[1];den=ux*vy-uy*vx;wx,wy=c[0]-a[0],c[1]-a[1]
            if abs(den)<1e-7:
                size=math.hypot(ux,uy)
                if size>1e-5 and abs(wx*uy-wy*ux)/size<.001:
                    t1=(wx*ux+wy*uy)/(size*size);t2=((d[0]-a[0])*ux+(d[1]-a[1])*uy)/(size*size)
                    overlap=(min(1,max(t1,t2))-max(0,min(t1,t2)))*size
                    if overlap>2:overlaps.append({'edges':[ei,ej],'length':round(overlap,3)})
                continue
            t=(wx*vy-wy*vx)/den;v=(wx*uy-wy*ux)/den
            if 1e-5<t<1-1e-5 and 1e-5<v<1-1e-5:edges[ei]['crossingGaps'].append({'segment':si,'t':round(t,8)});crossings+=1
    points=coords.tolist()+[p for e in edges for p in e['points']];xs=[p[0] for p in points];ys=[p[1] for p in points]
    graph['bounds']={'minX':min(xs)-120,'maxX':max(xs)+120,'minY':min(ys)-120,'maxY':max(ys)+120}
    lengths=[length(e['points']) for e in edges]
    audit.update(edgeNodeCollisions=unresolved,collinearWireOverlaps=overlaps,nonVertexCrossingsMarkedWithGaps=crossings,
                 maxRoutedLength=round(max(lengths),2),medianRoutedLength=round(float(np.median(lengths)),2),bounds=graph['bounds'],
                 shortenedDetourOrbits=shortened,maxBeforeDetourImprovement=round(before,2))
    return audit
