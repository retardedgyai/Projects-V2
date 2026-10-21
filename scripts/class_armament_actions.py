"""Authored held-weapon mechanisms. Pure native models, never fake damage trails.

Six anticipation poses and six release/recovery poses follow accepted VFX events.
The original material atlas and idle meshes are reused without image filtering.
"""
import math
from class_armament_geometry import authored_model, box, beam

ACTION_FRAMES = 6


def translate(element, delta):
    for key in ('from', 'to'):
        element[key] = [v+d for v,d in zip(element[key], delta)]
    if 'rotation' in element:
        element['rotation']['origin'] = [v+d for v,d in zip(element['rotation']['origin'], delta)]


def string_half(elements, start, end):
    # Native model rotations have discrete allowed angles. A connected pixel
    # staircase avoids a rounded-angle beam missing its nock at intermediate poses.
    steps = math.ceil(max(abs(a-b) for a,b in zip(start,end))*2)
    for i in range(steps):
        p=[a+(b-a)*i/steps for a,b in zip(start,end)]
        q=[a+(b-a)*(i+1)/steps for a,b in zip(start,end)]
        elements.append(box('drawn bowstring pixel', [min(a,b)-.07 for a,b in zip(p,q)],
                            [max(a,b)+.07 for a,b in zip(p,q)], 'leather'))


def drawn_bow(model, amount, nocked, tier):
    e=model['elements']
    e[:]=[part for part in e if part['name'] not in (
        'continuous horn limb','laminated green limb plate','limb binding rivet',
        'bronze nock','taut bowstring','travelling wind glint')]
    upper=[(6,16,8)]
    # Keeping the base/grip anchored makes the draw read as flex rather than
    # scaling the whole bow. Broad connected segments hide subpixel joint overlap.
    for length,angle in ((3,22.5),(3,45),(3,22.5),(2,-22.5)):
        x,y,z=upper[-1]; a=math.radians(angle)
        upper.append((x+length*math.sin(a), y+length*math.cos(a), z))
    flexed=[(x+amount*(i/4)**2*1.3, y-amount*(i/4)*.7,z) for i,(x,y,z) in enumerate(upper)]
    top=flexed[-1]; middle=(top[0]+amount*4.5,14,8)
    for sign in (-1,1):
        points=[(x,14+sign*(y-14),z) for x,y,z in flexed]
        for i,(p,q) in enumerate(zip(points,points[1:])):
            beam(e,'drawn horn limb',p,q,1.3,1.3,'ivory')
            if i<tier:
                beam(e,'drawn green limb',(p[0],p[1],7.2),(q[0],q[1],7.2),.85,.45,'green')
                x,y,_=p
                e.append(box('drawn binding rivet',[x-.3,y-.3,6.9],[x+.3,y+.3,7.2],'bronze'))
        x,y,z=points[-1]
        e.append(box('drawn bronze nock',[x-.65,y-.4,7.2],[x+.65,y+.4,8.8],'bronze'))
        string_half(e,points[-1],middle)
    if nocked:
        x=middle[0]
        e.append(box('nocked arrow shaft',[x-11.5,13.87,7.87],[x,14.13,8.13],'bronze'))
        e.append(box('nocked arrowhead',[x-12.5,13.55,7.55],[x-11.6,14.45,8.45],'steel',45))
        for z in (7.4,8.3):
            e.append(box('nocked arrow fletching',[x-2,13.7,z],[x-.5,14.3,z+.3],'moss'))


def turning_page(angle):
    # Bake quarter turns into the cuboid, leaving a legal +/-22.5 degree
    # native residual rotation. The hinge is the spine, never the leaf centre.
    pivot=(8,11,7.5); quarter=round(angle/90)*90; radians=math.radians(quarter)
    corners=[]
    for x in (8,13.6):
        for z in (6.4,6.58):
            dx,dz=x-pivot[0],z-pivot[2]
            corners.append((pivot[0]+dx*math.cos(radians)+dz*math.sin(radians),
                            pivot[2]-dx*math.sin(radians)+dz*math.cos(radians)))
    return box('turning illuminated page',[min(p[0] for p in corners),5.8,min(p[1] for p in corners)],
               [max(p[0] for p in corners),16.2,max(p[1] for p in corners)],
               'pages',angle-quarter,'y',origin=list(pivot))


# Greatsword swing (2026-10-19, Creator: the player must visibly swing). A Minecraft player cannot move its body, so
# the held model itself moves in the hand: the windup lifts the blade over the right shoulder, the release slams it
# forward and down, then it settles back. Values are item display transforms (degrees / pixels), checked in
# scripts/preview_warrior_cast.py with the vanilla hand transform.
SWING_POSES = {
    'thirdperson': {'idle': ([0, -90, 55], [0, 2, 1], .75), 'raised': ([-10, -90, -140], [2, -1, 14], .75),
                    'struck': ([0, -90, 100], [0, 12, 0], .9)},
    'firstperson': {'idle': ([0, -90, 25], [1.13, 3.2, -1.5], .72), 'raised': ([0, -90, -35], [1.13, 6.2, -1.5], .72),
                    'struck': ([0, -90, 75], [1.13, 1.2, -2.5], .8)},
}


def _mix(a, b, t):
    return [round(x + (y - x) * t, 3) for x, y in zip(a, b)]


def swing_display(model, stage, u):
    if stage == 'prepare':
        a, b, t = 'idle', 'raised', 1 - (1 - u) ** 2          # eases into the top of the lift
    else:
        a, b, t = 'struck', 'idle', u ** .7                   # lands at once, settles back slower
    display = dict(model['display'])
    for view, poses in SWING_POSES.items():
        (ra, ta, sa), (rb, tb, sb) = poses[a], poses[b]
        rotation, translation, scale = _mix(ra, rb, t), _mix(ta, tb, t), round(sa + (sb - sa) * t, 3)
        for side, sign in (('righthand', 1), ('lefthand', -1)):
            display[f'{view}_{side}'] = {'rotation': [rotation[0], rotation[1] * sign, rotation[2] * sign],
                'translation': translation, 'scale': [scale] * 3}
    model['display'] = display


def action_model(kind, tier, stage, frame):
    assert stage in ('prepare','release') and 0 <= frame < ACTION_FRAMES
    model=authored_model(kind,tier,0)
    u=frame/(ACTION_FRAMES-1)
    # Anticipation opens/energizes, release discharges and settles fully back.
    amount=u if stage=='prepare' else (1-u)**2
    e=model['elements']
    if kind=='bow':
        drawn_bow(model,amount,stage=='prepare',tier)
    elif kind=='staff':
        for part in e:
            name=part['name']; x=sum((part['from'][0],part['to'][0]))/2
            if name=='split crystal cage' or name.startswith(('hanging articulated','petal enamel')):
                translate(part,[(1 if x>8 else -1)*amount*1.15,amount*.2,0])
            elif name.startswith(('floating elemental','crystal upper')):
                translate(part,[0,amount*.8,0])
            elif name=='orbiting crystal mote':
                z=sum((part['from'][2],part['to'][2]))/2
                translate(part,[(8-x)*amount*.5,amount*.5,(8-z)*amount*.5])
    elif kind=='tome':
        # A separate broad page crosses the spine. It has real thickness and
        # discrete native Y poses; neither the book nor its binding is stretched.
        if stage=='prepare' and frame not in (0,5):
            e.append(turning_page((0,22.5,67.5,112.5,157.5,180)[frame]))
        for part in e:
            if part['name'].startswith('suspended prayer seal'): translate(part,[0,amount*1.3,0])
            elif part['name']=='prayer seal ray':
                x=sum((part['from'][0],part['to'][0]))/2
                translate(part,[(x-8)*amount*.3,amount*1.3,0])
    elif kind=='mace':
        # Reliquary slides apart at the four seams; the shaft and grip stay fixed.
        for part in e:
            if part['name'].startswith(('broad flange','flange recessed','oath inlay','flange crown')):
                x,z=[(part['from'][i]+part['to'][i])/2-8 for i in (0,2)]
                length=math.hypot(x,z)
                translate(part,[x/length*amount*.65,0,z/length*amount*.65])
            elif part['name']=='suspended oath core': translate(part,[0,amount*.7,0])
    elif kind in ('greatsword','dagger'):
        # Energy travels along existing grooves, not an oversized second blade.
        for part in e:
            if part['name']=='travelling forge spark':
                translate(part,[0,amount*5,0])
            elif part['name']=='venom bead': translate(part,[0,amount*8,0])
        if amount>0:
            if kind=='greatsword':
                for z in (6.65,9.05):
                    e.append(box('charged fuller edge',[7.6,11,z],[8.05,11+15*amount,z+.2],'ember'))
            else:
                for i in range(3):
                    e.append(box('charged cutting reflection',[6.55+i*.2,10+i*3,6.75],
                        [6.9+i*.2,10+i*3+2*amount,6.95],'steel'))
    if kind=='greatsword': swing_display(model,stage,u)
    # End pose returns exactly to the authored idle pose, no lingering nocked arrow.
    if stage=='release' and frame==ACTION_FRAMES-1: return authored_model(kind,tier,0)
    return model
