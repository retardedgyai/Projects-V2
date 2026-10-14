from PIL import Image
import math
exec(open('aspect32.py',encoding='utf-8').read().split('# --- Ignis')[0])   # reuse hx, frame_px, FR, N
def render(inside, shade, ramp, bg, bgglow):
    im=Image.new('RGBA',(N,N),(0,0,0,0)); px=im.load()
    for y in range(N):
        for x in range(N):
            f=frame_px(x,y)
            if f is None: continue
            if f!='in': px[x,y]=hx(FR[f]); continue
            d=math.hypot(x-15.5,y-15.5)
            px[x,y]=hx(bgglow if d<8 else bg)
            mx=x if x<=15 else 31-x            # silhouette mirrored -> exact symmetry
            if inside(mx+0.5,y+0.5):
                px[x,y]=hx(ramp[shade(x+0.5,y+0.5)])   # shading uses true x (light from top-left)
    return im
# ---------- Ignis: three tongues
def flame_halfwidth(y):
    # main body (teardrop) ; returns list of (x0,x1) in left-half coordinates (0..16)
    spans=[]
    t=(y-5)/21
    if 0<=t<=1:
        w=7.2*math.sin(min(1,t*1.1)*math.pi*0.6)**1.2
        if t>0.85: w*= (1-(t-0.85)/0.15*0.35)
        spans.append((16-w,16))
    # side tongue (left), peaks at y=12, leans outward
    if 11<=y<=20:
        tt=(y-11)/9
        cxs=10.6+tt*1.8; ww=1.0+tt*1.6
        spans.append((cxs-ww,cxs+ww))
    return spans
def ign_in(x,y):
    return any(a<=x<=b for a,b in flame_halfwidth(y))
def ign_shade(x,y):
    dx=abs(x-16); t=(y-5)/21
    s=1
    if dx<4.6 and t>0.3: s=2
    if dx<3.0 and t>0.45: s=3
    if dx<1.6 and t>0.62: s=4
    if t>0.93: s=max(0,s-2)
    if x<16 and dx>4.6 and t<0.5: s=2       # lit left tongue
    if x>16 and dx>5.5: s=max(0,s-1)       # right side in shadow
    return s
# ---------- Aqua: droplet
def aq_in(x,y):
    cy,r=19.2,6.8
    if y<cy:
        t=(y-5.5)/(cy-5.5)
        if t<0: return False
        hw=r*t**1.35
    else:
        dy=y-cy
        if dy>r: return False
        hw=math.sqrt(r*r-dy*dy)
    return 16-x<=hw
def aq_shade(x,y):
    cy=19.2
    s=2
    if x>19.5: s=1
    if y>cy+4.5 and x>14: s=1
    if y>cy+5.6: s=0
    if 11.5<x<14.5 and cy-3<y<cy+2: s=3
    if 12<x<13.5 and cy-2.2<y<cy-0.6: s=4
    if 15<x<17 and y<9.5: s=3
    return s
# ---------- Terra: mountain
def te_in(x,y):
    top,base=7.5,25.5
    if y<top or y>base: return False
    hw=(y-top)/(base-top)*9.5
    return 16-x<=hw
def te_shade(x,y):
    top,base=7.5,25.5
    left = x<16
    s=3 if left else 1
    if left and x>13.5 and y<14: s=4          # lit ridge near summit
    if not left and x<18: s=2                 # ridge on right side mid
    if y>22: s=max(0,s-1)
    if y<10.5: s=4                            # snow cap
    if 10.5<=y<11.6 and (int(x)%3==0): s=4    # snow drips
    if not left and y>22: s=0
    return s
SPECS={
 'ignis':(ign_in,ign_shade,['#5c1610','#b8341a','#ec7a26','#ffc552','#fff4cc'],'#200907','#2c0e0a'),
 'aqua':(aq_in,aq_shade,['#0e2c55','#1d63a0','#3fa5d6','#9fe3f4','#f2fdff'],'#06101e','#0b1c32'),
 'terra':(te_in,te_shade,['#2a2412','#5a5226','#8f8a42','#c8c77a','#f2f0d6'],'#0d0e08','#16180d'),
}
out=Image.new('RGBA',(3*(N*8+30)+20,N*8+150),(14,16,20,255))
for i,(k,(fi,fs,ramp,bg,bgg)) in enumerate(SPECS.items()):
    a=render(fi,fs,ramp,bg,bgg); a.save(f'aspect32_{k}.png')
    al=[[a.getpixel((x,y))[3]>0 for x in range(N)] for y in range(N)]
    b=a.resize((N*8,N*8),Image.NEAREST); out.paste(b,(20+i*(N*8+30),20),b)
    m=a.resize((64,64),Image.NEAREST); out.paste(m,(20+i*(N*8+30),N*8+40),m); out.paste(a,(100+i*(N*8+30),N*8+56),a)
out.save('prev_aspect32.png')
