from PIL import Image
import math
def hx(s): s=s.lstrip('#'); return tuple(int(s[i:i+2],16) for i in (0,2,4))+(255,)
P=['#45412e','#6f6d50','#9d9c78','#c4c39f','#e2e0c0','#f5f2dc']
W=['#2e170c','#56301a','#82482a','#ad6a3e','#d39462']
C=['#3b1d10','#7a3f22','#b56a3c','#e29a62','#ffd2a6']
INK=['#1e120b','#3c2416','#5e3c26']
G=['#1f6fa8','#49b4d9','#bff0fb','#ffffff']
N=32
im=Image.new('RGBA',(N,N),(0,0,0,0)); px=im.load()
def put(x,y,c):
    if 0<=x<N and 0<=y<N: px[x,y]=hx(c)
def get(x,y): return px[x,y][:3]
SX0,SX1,SY0,SY1=8,23,8,25
# sheet
for y in range(SY0,SY1+1):
    for x in range(SX0,SX1+1):
        u=(x-SX0)/(SX1-SX0)
        c=P[3]
        if u<0.07: c=P[5]
        elif u<0.2: c=P[4]
        elif u>0.93: c=P[1]
        elif u>0.8: c=P[2]
        put(x,y,c)
for y in range(SY0+2,SY1-1):
    put(13,y,P[4])
# shadow under roller
for x in range(SX0,SX1+1): put(x,SY0,P[1]); put(x,SY0+1,P[2] if x<SX1-1 else P[1])
# faint writing lines at the bottom of sheet
for x in range(10,22,1):
    if x%3!=1: put(x,23,P[2])
# bottom curl
for x in range(SX0,SX1+1):
    u=(x-SX0)/(SX1-SX0)
    put(x,SY1+1,P[5] if u<0.35 else P[4]); put(x,SY1+2,P[2] if u<0.7 else P[1]); put(x,SY1+3,P[0])
put(SX0-1,SY1+2,P[1]); put(SX1+1,SY1+2,P[0])
# roller x 6..25 y 4..7
for x in range(6,26):
    for y,c in zip(range(4,8),(W[4],W[3],W[1],W[0])): put(x,y,c)
for x in range(8,25,4): put(x,5,W[4])
# finials: collar (1px) + bulb (3 wide) + tip
def finial(collar_x, d):
    for y in range(3,9): put(collar_x,y,C[2] if 3<y<8 else C[1])
    put(collar_x,4,C[3])
    bulb=[collar_x+d*1,collar_x+d*2,collar_x+d*3]
    rows={bulb[0]:(4,7),bulb[1]:(3,8),bulb[2]:(4,7)}
    for x in bulb:
        a,b=rows[x]
        for y in range(a,b+1):
            put(x,y,C[3] if y<=a+1 else C[2] if y<b else C[1])
        put(x,a,C[4] if x==bulb[1] or x==bulb[0] else C[3])
    tip=collar_x+d*4
    put(tip,5,C[2]); put(tip,6,C[1])
finial(5,-1); finial(26,1)
# glyph: hook arrow. stroke points (hand-placed for clean curve), 2px thick
stroke=[(11,20),(11,19),(11,18),(11,17),(12,16),(12,15),(13,14),(14,13),(15,12),(16,12),(17,12),(18,13),(19,14),(19,15),(20,16)]
for (x,y) in stroke:
    put(x,y,INK[0]); put(x+1,y,INK[0]) if (x,y) in stroke[:6] else put(x,y+1,INK[0])
# inner AA (mid ink) along the inside of the curve
for (x,y) in [(13,15),(14,14),(15,13),(16,13),(17,13),(12,17),(12,18)]:
    if get(x,y)!=hx(INK[0])[:3]: put(x,y,INK[2])
# arrowhead pointing down at end (20,17..)
for dx in (-2,-1,0,1,2): put(20+dx,17,INK[0])
for dx in (-1,0,1): put(20+dx,18,INK[0])
put(20,19,INK[0])
# anchor bar (base line) with tick
for x in range(15,23): put(x,21,INK[1])
for x in range(16,22): put(x,22,INK[0])
# single glow: at arrow tip
put(20,19,G[1]); put(20,20,G[3]); put(19,20,G[1]); put(21,20,G[1])
im.save('scroll32_flat.png')
lean=Image.new('RGBA',(N,N),(0,0,0,0)); lp=lean.load()
for x in range(N):
    s=(x-16)//6
    for y in range(N):
        yy=y+s
        if 0<=yy<N: lp[x,y]=px[x,yy]
lean.save('scroll32_lean.png')
out=Image.new('RGBA',(2*(N*10+40)+260,N*10+40),(14,16,20,255))
for i,name in enumerate(('scroll32_flat','scroll32_lean')):
    a=Image.open(name+'.png'); b=a.resize((N*10,N*10),Image.NEAREST); out.paste(b,(20+i*(N*10+40),20),b)
    m=a.resize((N*2,N*2),Image.NEAREST); out.paste(m,(2*(N*10+40)+20+i*120,40),m); out.paste(a,(2*(N*10+40)+36+i*120,130),a)
out.save('prev_scroll32_both.png')
