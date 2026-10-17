import itertools
MIX = {"lux": ("aer","ignis"), "motus": ("aer","ordo"), "victus": ("aqua","terra"), "metallum": ("terra","ordo"), "mortuus": ("terra","perditio")}
PRIM = ["aer","aqua","ignis","terra","ordo","perditio"]
def rel(a,b): return (a in MIX and b in MIX[a]) or (b in MIX and a in MIX[b])
R=2
HEX=[(q,r) for q in range(-R,R+1) for r in range(-R,R+1) if abs(q+r)<=R]
def nb(h): q,r=h; return [(q+1,r),(q-1,r),(q,r+1),(q,r-1),(q+1,r-1),(q-1,r+1)]
def solved(cells, anchors):
    # connectivity over valid links
    start=anchors[0]; seen={start}; st=[start]
    while st:
        h=st.pop()
        for n in nb(h):
            if n in cells and n not in seen and rel(cells[h],cells[n]): seen.add(n); st.append(n)
    return all(a in seen for a in anchors)
def minsol(anch, allowed, maxk=4):
    anchors=list(anch); free=[h for h in HEX if h not in anch]
    for k in range(1,maxk+1):
        sols=0; ex=None
        for hs in itertools.combinations(free,k):
            for asp in itertools.product(allowed, repeat=k):
                cells=dict(anch); cells.update(zip(hs,asp))
                if solved(cells, anchors): sols+=1; ex=ex or dict(zip(hs,asp))
        if sols: return k, sols, ex
    return None
boards = {
 "lux":    {(-1,0):"aer", (1,0):"ignis"},
 "motus":  {(-2,1):"lux", (1,0):"ordo"},
 "victus": {(-2,1):"aqua", (1,-1):"terra"},
}
known=[]
for k,b in boards.items():
    allowed=PRIM+known+[k]
    print(k, minsol(b, allowed))
    known.append(k)
