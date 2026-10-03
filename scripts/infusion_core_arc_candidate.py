"""Independent offline core-near arc candidate; not a Thaumcraft mechanic or sprite."""
import math

LIFETIME_TICKS=6
IDLE_PERIOD_TICKS=18
RITUAL_PERIOD_TICKS=11

def turn(p,axis,degrees):
    a=math.radians(degrees);c,s=math.cos(a),math.sin(a);x,y,z=p
    return (c*x-s*y,s*x+c*y,z) if axis=='z' else (c*x+s*z,y,-s*x+c*z)

def sample(tick,yaw,active):
    period=RITUAL_PERIOD_TICKS if active else IDLE_PERIOD_TICKS
    start=6
    if tick<start:return []
    event=(tick-start)//period;age=(tick-start)%period
    if age>=LIFETIME_TICKS:return []
    # Sparse, separated birth sites: no permanent ring and no end-to-end connection.
    angle=math.radians((-145,-65,25,115,-105,165)[event%6])
    radial=(math.cos(angle),0,math.sin(angle));tangent=(-math.sin(angle),0,math.cos(angle))
    y=-.27+.10*((event*3)%5)/4
    bulge=.06+.16*math.sin(math.pi*(age+.5)/LIFETIME_TICKS)
    side=.035*math.sin(age*math.pi/3)
    shape=[(.91,y,0),(1.05+bulge,y+.08,-.14+side),(1.00+bulge*.35,y+.22,.12+side),(.93,y+.32,.20)]
    points=[]
    for radius,height,offset in shape:
        p=tuple(radial[i]*radius+tangent[i]*offset+(height if i==1 else 0) for i in range(3))
        p=turn(turn(p,'z',22.5),'y',yaw)
        points.append((p[0]+.5,p[1]+3.4,p[2]+.5))
    alpha=(.72,.98,.96,.65,.30,.06)[age]
    return [{'event':event,'ageTicks':age,'lifeTicks':LIFETIME_TICKS,'alpha':alpha,'points':points,'bulgeBlocks':bulge,'rgb':[231,118,219],'lineWidthBlocks':.027}]
