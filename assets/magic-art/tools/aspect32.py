from PIL import Image
import math
def hx(s): s=s.lstrip('#'); return tuple(int(s[i:i+2],16) for i in (0,2,4))+(255,)
N=32
FR=['#2c1b0b','#5a3a18','#8a6a2c','#c9a94e','#f2e6a0']   # brass frame tones
def frame_px(x,y):
    # rounded square, corner cut 3 (symmetric). returns tone index or None
    def inside(x,y,m):  # inset by m
        if x<m or y<m or x>N-1-m or y>N-1-m: return False
        cx=min(x-m, N-1-m-x); cy=min(y-m, N-1-m-y)
        return not (cx+cy<3)
    if not inside(x,y,0): return None
    if not inside(x,y,1): return 0                    # outer dark line
    if not inside(x,y,2):                             # bevel ring
        if y<=2 or x<=2: return 4 if (x+y)<12 else 3
        return 1
    if not inside(x,y,3):                             # inner lip
        if y<=3 or x<=3: return 1
        return 2
    return 'in'
def sym_mirror(f):
    # make shape function symmetric around x=15.5
    return lambda x,y: f(min(x,31-x) if x>15 else x, y)
def render(shape_sd, ramp, bg, bgglow):
    im=Image.new('RGBA',(N,N),(0,0,0,0)); px=im.load()
    for y in range(N):
        for x in range(N):
            f=frame_px(x,y)
            if f is None: continue
            if f!='in': px[x,y]=hx(FR[f]); continue
            # background: radial dark->glow, symmetric
            d=math.hypot(x-15.5,y-15.5)
            px[x,y]=hx(bgglow if d<7 else bg)
            # symbol
            sx=x if x<=15 else 31-x       # mirror for exact symmetry
            sd,shade=shape_sd(sx+0.5,y+0.5)
            if sd<0:
                px[x,y]=hx(ramp[shade])
    return im
# --- Ignis: flame (symmetric, center tongue tallest) built from left half
def ignis(x,y):
    # x in 0..16 (left half incl centre)
    cx=16.0
    # outer silhouette: teardrop flame from y=6 (tip) to y=26 base
    t=(y-6)/20.0
    if t<0 or t>1: return 1,0
    halfw = 7.5*math.sin(min(1,t*1.15)*math.pi*0.62)**1.1
    # side tongue notch
    if 0.25<t<0.45: halfw*=0.82
    dx=cx-x
    if dx>halfw: return 1,0
    # inner layers
    core = dx<halfw*0.35 and t>0.55
    inner= dx<halfw*0.62 and t>0.35
    mid  = dx<halfw*0.85
    shade = 4 if core and t>0.7 else 3 if inner else 2 if mid else 1
    if t>0.92: shade=max(0,shade-2)
    return -1,shade
# --- Aqua: droplet
def aqua(x,y):
    cx,cy,r=16.0,19.0,6.6
    if y<cy:
        t=(y-6)/(cy-6)
        if t<0: return 1,0
        halfw=r*t**1.4
    else:
        dy=y-cy
        if dy>r: return 1,0
        halfw=math.sqrt(max(0,r*r-dy*dy))
    dx=cx-x
    if dx>halfw: return 1,0
    # shading: lit from top-left => left side brighter
    u=1-dx/max(halfw,0.01)   # 0 at edge,1 at centre line
    shade=2
    if dx>halfw-1.2: shade=1
    if y>cy+r-1.5: shade=0
    if 3<dx<halfw-1 and cy-3<y<cy+2: shade=3      # left highlight band
    if 4<=dx<=5 and cy-2<=y<=cy-1: shade=4         # specular
    return -1,shade
# --- Terra: faceted crystal-rock (hexagon) with fret line
def terra(x,y):
    cx,cy=16.0,16.0
    dx=cx-x; dy=y-cy
    if abs(dy)>8: return 1,0
    halfw=8-abs(dy)*0.45
    if dx>halfw: return 1,0
    shade=2
    if dy<-1: shade=3                    # top face lit
    if dy<-1 and dx>halfw-2.2: shade=4
    if dy>3: shade=1
    if dy>6.5: shade=0
    if abs(dy+1)<0.6: shade=1            # ridge line
    if dx<1.2 and -7<dy<-2: shade=4      # centre highlight seam
    return -1,shade
SPECS={
 'ignis':(ignis,['#5c1610','#b8341a','#ec7a26','#ffc552','#fff4cc'],'#200907','#2c0e0a'),
 'aqua':(aqua,['#0e2c55','#1d63a0','#3fa5d6','#9fe3f4','#f2fdff'],'#06101e','#0b1c32'),
 'terra':(terra,['#2a2412','#5a5226','#8f8a42','#c8c77a','#eeeeb8'],'#0d0e08','#16180d'),
}
for k,(f,ramp,bg,bgg) in SPECS.items():
    render(f,ramp,bg,bgg).save(f'aspect32_{k}.png')
# preview + symmetry check
out=Image.new('RGBA',(3*(N*8+30)+20,N*8+150),(14,16,20,255))
for i,k in enumerate(SPECS):
    a=Image.open(f'aspect32_{k}.png')
    # verify geometric symmetry of alpha
    al=[[a.getpixel((x,y))[3]>0 for x in range(N)] for y in range(N)]
    ok=all(al[y][x]==al[y][N-1-x] for y in range(N) for x in range(N))
    print(k,'alpha-symmetric',ok)
    b=a.resize((N*8,N*8),Image.NEAREST); out.paste(b,(20+i*(N*8+30),20),b)
    m=a.resize((64,64),Image.NEAREST); out.paste(m,(20+i*(N*8+30),N*8+40),m); out.paste(a,(100+i*(N*8+30),N*8+56),a)
out.save('prev_aspect32.png')
