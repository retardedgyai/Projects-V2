from PIL import Image
import math
exec(open('aspect32b.py',encoding='utf-8').read().split('# ---------- Ignis')[0])
SILVER=['#1e2126','#454b55','#7d8592','#b9c0ca','#eef2f6']
def render2(inside, shade, ramp, bg, bgglow, frame=None, extra=None):
    global FR
    old=FR
    if frame: FR=frame
    im=render(inside, shade, ramp, bg, bgglow)
    FR=old
    if extra: extra(im)
    return im
T=['#2a1f12','#5a4226','#8a6a3c','#bc9a62','#e8d4a2']   # earth brown ramp (hue drifts to warm ochre)
GR=['#1f3a16','#3f6e24','#74a83a','#b8dc6a']
# A: earth clod with grass tuft
def clod_in(x,y):
    cx,cy=16,18.5
    rx,ry=8.6,6.6
    if ((x-cx)/rx)**2+((y-cy)/ry)**2<=1 and y>=13: return True
    return False
def clod_sh(x,y):
    s=2
    if (x-16)+(y-18)< -4: s=3
    if (x-16)+(y-18)< -8: s=4
    if (x-16)+(y-18)> 5: s=1
    if y>23.5: s=min(s,1)
    if y>24.5: s=0
    # cracks
    if (abs(x-13.5)<0.6 and 18<y<22) or (abs(y-20.5)<0.6 and 18<x<21): s=0
    return s
def clod_extra(im):
    px=im.load()
    # grass tuft on top: symmetric silhouette rows
    rows={10:[(15,16)],11:[(12,12),(15,16),(19,19)],12:[(12,13),(14,17),(18,19)],13:[(11,20)],14:[(10,21)]}
    for y,sp in rows.items():
        for a,b in sp:
            for x in range(a,b+1):
                s=3 if x<16 else 2
                if y==14: s=1 if x>16 else 2
                if y<=11: s=3 if x<=16 else 2
                px[x,y]=hx(GR[s])
# B: crystal cluster (centre tall + two side)
def cry_spans(y):
    sp=[]
    # centre crystal: point at y6, body to y24, half width 3.5
    if 6<=y<=24:
        w = min(3.5,(y-6)*0.9) if y<10 else 3.5
        sp.append((16-w,16))
    # side crystal (left), point y12, leaning
    if 12<=y<=24:
        w=min(2.4,(y-12)*0.8)
        c=10.0+(y-12)*0.12
        sp.append((c-w,c+w))
    return sp
def cry_in(x,y): return any(a<=x<=b+0.01 for a,b in cry_spans(y))
CR=['#2d2210','#6b5a20','#a99234','#e0cc6a','#fff6c2']
def cry_sh(x,y):
    s=2
    if x<15.5 and y>9: s=3
    if 14<x<15.5 and y>9: s=4
    if x>16.5: s=1
    if x>18.5: s=1
    if y<9: s=4 if x<16 else 2
    if y>22: s=max(0,s-1)
    # side crystal shading
    if x<12.5: s=3 if x<10.2 else 2
    if 12<=y<=14 and x<12: s=4
    return s
def base_extra(im):
    px=im.load()
    for x in range(8,24):
        px[x,25]=hx(T[1] if x<16 else T[0]); px[x,24]=hx(T[2] if x<16 else T[1]) if px[x,24][:3] in (hx('#0d0e08')[:3],hx('#16180d')[:3]) else px[x,24]
# C: mountain range (big peak + two small), concave slopes, snowcap
def mtn_in(x,y):
    if y>25.5: return False
    # big peak
    if y>=7: 
        t=(y-7)/18.5; hw=9.5*t**0.85
        if 16-x<=hw: return True
    # small left peak centred x=9
    if y>=14:
        t=(y-14)/11.5; hw=5.5*t**0.9
        if abs(x-9)<=hw: return True
    return False
MT=['#1f1d14','#4a4630','#77724c','#aaa47a','#f4f2e2']
def mtn_sh(x,y):
    s=3 if x<16 else 1
    if x>=16 and x<17.5: s=2
    if y<11.5: s=4 if x<16.5 else 3                 # snow
    if 11.5<=y<12.6 and int(x)%3==1 and x<17: s=4
    if abs(x-9)<5 and y>=14 and x<11:                # small peak
        s=3 if x<9 else 2
        if y<16.5: s=4 if x<9.5 else 3
    if x>22 and y>16 and abs(x-23)<5.5: s=1         # mirrored small peak (right) in shadow
    if y>23: s=max(0,s-1)
    return s
SP=[
 ('A 土の塊＋草', render2(clod_in,clod_sh,T,'#0d0e08','#16180d',extra=clod_extra)),
 ('B 原石（結晶）', render2(cry_in,cry_sh,CR,'#0d0e08','#16180d',extra=base_extra)),
 ('C 山並み', render2(mtn_in,mtn_sh,MT,'#0d0e08','#16180d')),
]
# frame demo: primal (brass) vs compound (silver) using aqua icon recolored frame
aq=Image.open('aspect32_aqua.png')
def reframe(im,src,dst):
    im=im.copy(); px=im.load()
    m={hx(a)[:3]:hx(b) for a,b in zip(src,dst)}
    for y in range(N):
        for x in range(N):
            if px[x,y][:3] in m and frame_px(x,y)!='in': px[x,y]=m[px[x,y][:3]]
    return im
FRB=['#2c1b0b','#5a3a18','#8a6a2c','#c9a94e','#f2e6a0']
aq_s=reframe(aq,FRB,SILVER)
for i,(n,im) in enumerate(SP): im.save(f'terra_opt_{"ABC"[i]}.png')
from PIL import ImageDraw, ImageFont
F=ImageFont.truetype('C:/Windows/Fonts/meiryob.ttc',20)
out=Image.new('RGBA',(4*(N*7+30)+40,N*7+150),(14,16,20,255)); d=ImageDraw.Draw(out)
items=SP+[('枠：銀（組み合わせの相）',aq_s)]
for i,(n,im) in enumerate(items):
    al=[[im.getpixel((x,y))[3]>0 for x in range(N)] for y in range(N)]
    print(n,'symmetric:',all(al[y][x]==al[y][N-1-x] for y in range(N) for x in range(N)))
    b=im.resize((N*7,N*7),Image.NEAREST); out.paste(b,(20+i*(N*7+30),50),b)
    d.text((20+i*(N*7+30),12),n,font=F,fill=(242,240,235))
    m=im.resize((64,64),Image.NEAREST); out.paste(m,(20+i*(N*7+30),N*7+70),m); out.paste(im,(100+i*(N*7+30),N*7+86),im)
out.save('prev_terra_opts.png')
