from PIL import Image
import math
exec(open('aspect32b.py',encoding='utf-8').read().split('# ---------- Ignis')[0])
# left-half spans per row (pixel columns, inclusive), centre columns 15|16
OUT={5:[(15,15)],6:[(14,15)],7:[(14,15)],8:[(13,15)],9:[(13,15)],
     10:[(9,9),(12,15)],11:[(9,10),(12,15)],12:[(8,10),(11,15)],13:[(8,15)],14:[(8,15)],
     15:[(7,15)],16:[(7,15)],17:[(7,15)],18:[(7,15)],19:[(7,15)],20:[(7,15)],21:[(7,15)],
     22:[(8,15)],23:[(8,15)],24:[(9,15)],25:[(11,15)]}
# inner flame (yellow) and core (white) spans
MID={11:[(15,15)],12:[(14,15)],13:[(14,15)],14:[(13,15)],15:[(12,15)],16:[(11,15)],17:[(11,15)],18:[(10,15)],19:[(10,15)],20:[(10,15)],21:[(10,15)],22:[(11,15)],23:[(12,15)]}
CORE={15:[(15,15)],16:[(14,15)],17:[(14,15)],18:[(13,15)],19:[(13,15)],20:[(13,15)],21:[(13,15)],22:[(14,15)]}
def inspan(tab,x,y): return any(a<=x<=b for a,b in tab.get(y,[]))
ramp=['#5c1610','#b8341a','#ec7a26','#ffc552','#fff4cc']
im=Image.new('RGBA',(N,N),(0,0,0,0)); px=im.load()
for y in range(N):
    for x in range(N):
        f=frame_px(x,y)
        if f is None: continue
        if f!='in': px[x,y]=hx(FR[f]); continue
        d=math.hypot(x-15.5,y-15.5); px[x,y]=hx('#2c0e0a' if d<8 else '#200907')
        mx=x if x<=15 else 31-x
        if inspan(OUT,mx,y):
            s=2
            if x>16: s=1                                  # right half in shadow
            if inspan(MID,mx,y): s=3 if x<=16 or mx>=13 else 2
            if inspan(CORE,mx,y): s=4
            if y>=24: s=1 if x<=16 else 0                 # base darker
            if y<=9 and x>15: s=1
            # left rim highlight (lit edge)
            if x<16 and not inspan(OUT,mx-1,y) and y<24: s=max(s,3) if y<13 else 3
            px[x,y]=hx(ramp[s])
im.save('aspect32_ignis.png')
# rebuild combined preview
out=Image.new('RGBA',(3*(N*8+30)+20,N*8+150),(14,16,20,255))
for i,k in enumerate(['ignis','aqua','terra']):
    a=Image.open(f'aspect32_{k}.png')
    al=[[a.getpixel((x,y))[3]>0 for x in range(N)] for y in range(N)]
    print(k,'symmetric silhouette:',all(al[y][x]==al[y][N-1-x] for y in range(N) for x in range(N)))
    b=a.resize((N*8,N*8),Image.NEAREST); out.paste(b,(20+i*(N*8+30),20),b)
    m=a.resize((64,64),Image.NEAREST); out.paste(m,(20+i*(N*8+30),N*8+40),m); out.paste(a,(100+i*(N*8+30),N*8+56),a)
out.save('prev_aspect32.png')
