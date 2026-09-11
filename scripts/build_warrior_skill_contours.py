"""Warrior-specific native pixel contours. No bitmap generation or finished slash rotation.

Uses the approved dash's legal folded strips and opaque ink texels, but authors different
tip paths for cuts, a thrust and complete turns. Each section has its own birth and erosion.
"""
import json
import math
import numpy as np
from build_approved_dash_v3 import PACK, SIZE, polygon, geometry, ink_uvs, impact

CLIPS = ('wound', 'counter', 'cleave', 'thrust', 'spin_a', 'spin_b', 'spin_c', 'rise', 'return', 'finish')
COUNTS = {'blade': 11, 'wake': 19, 'ember': 19}


def tip(clip, t):
    u = max(0., min(1., t / 7.))
    # Starts restrained, crosses the target quickly, then loses speed.
    v = u * u * (3 - 2 * u)
    if clip == 'thrust':
        return np.array((8. + .2 * math.sin(u * math.pi), 2.2 + 11.3 * v))
    if clip.startswith('spin_'):
        turn = {'spin_a': 0, 'spin_b': 1, 'spin_c': 2}[clip]
        a = -math.pi * .8 + math.pi * 2 * v + turn * .55
        r = 5.6 + .35 * math.sin(u * math.pi * (turn + 2))
        return np.array((8 + math.sin(a) * r, 8 + math.cos(a) * r))
    a = -1.22 + 2.45 * v
    r = 5.2 + .9 * math.sin(u * math.pi)
    x, z = 8 + math.sin(a) * r, 5.6 + math.cos(a) * r * .78
    if clip in ('counter', 'return'):
        x = 16 - x
    if clip == 'return':
        # Ultimate return crosses higher and flattens as it leaves the target.
        # It is a different tip path, not the counter image with a larger display scale.
        z = z*.76 + 1.4 + u*.65
        x += .45*math.sin(u*math.pi*2)
    if clip in ('cleave', 'finish', 'rise'):
        # The drawing plane is stood up at runtime. +Z becomes height.
        x, z = 7.7 + math.cos(a) * 2.1, 8 - math.sin(a) * 5.7
        if clip == 'rise':
            z = 16 - z
        if clip == 'finish':
            x += .45 * math.sin(u * math.pi)
    return np.array((x, z))


def normal(clip, t):
    d = tip(clip, t + .025) - tip(clip, t - .025)
    d /= max(.001, np.linalg.norm(d))
    n = np.array((-d[1], d[0]))
    if clip.startswith('spin_'):
        radial = tip(clip, t) - np.array((8, 8))
        if np.dot(n, radial) < 0:
            n *= -1
    elif clip in ('counter', 'return'):
        n *= -1
    return n


def contour(clip, layer, frame):
    g = np.zeros((SIZE, SIZE), dtype=np.uint8)
    for i in range(84):
        birth, step = i / 12, 1 / 12
        age = frame - birth
        a, b = tip(clip, birth), tip(clip, birth + step)
        na, nb = normal(clip, birth), normal(clip, birth + step)
        speed = np.linalg.norm(tip(clip, birth + .1) - tip(clip, birth - .1)) / .2
        if layer == 'blade':
            if not 0 <= age < 2.6:
                continue
            width = (.45 + min(3.5, speed * .68)) * math.sin(math.pi * (age + .12) / 2.85) ** .65
            width *= {'wound': .65, 'thrust': .34, 'finish': 1.1}.get(clip, 1.)
            if clip == 'thrust':
                # Split spear-like air displacement; never a crescent travelling sideways.
                for sign in (-1, 1):
                    polygon(g, [a, b, b + nb * width * sign, a + na * width * sign], 2)
                polygon(g, [a-na*.12,b-nb*.12,b+nb*.12,a+na*.12], 3)
            else:
                polygon(g, [a,b,b-nb*width,a-na*width], 1)
                polygon(g, [a,b,b-nb*width*.72,a-na*width*.72], 2)
                polygon(g, [a,b,b-nb*min(.4,width*.23),a-na*min(.4,width*.23)], 3)
        else:
            if not 1.8 < age < 10.5:
                continue
            life = (age - 1.8) / 8.7
            # Deliberately spaced torn lobes, not random holes or a single shrinking image.
            if life > .38 and i % 14 in (0,1,12,13):
                continue
            if life > .7 and i % 14 not in (5,6,7,8):
                continue
            width = (1.15 if layer == 'wake' else .24) * (1-life)**.8
            drift = life*.7 + life*life*.8
            if layer == 'ember':
                if i < 20 or i % 21 > 13:
                    continue
                drift += .4
            a, b = a + na*drift, b + nb*drift
            polygon(g, [a,b,b-nb*width,a-na*width], 2 if life < .55 else 1)
    return g


def fracture(frame):
    g = np.zeros((SIZE, SIZE), dtype=np.uint8)
    for branch in (-1, 0, 1):
        for i in range(15):
            birth = i*.13
            age = frame-birth
            if not 0 <= age < 12:
                continue
            z = 1 + i*.85
            x = 8 + branch*i*.19 + math.sin(i*1.7+branch)*.38
            w = .38*(1-age/12)
            polygon(g, [(x-w,z),(x+.22,z+.9),(x+w,z),(x-.17,z-.4)], 3 if age<2 else 2 if age<6 else 1)
    return g


def build(assets, write):
    inks = ink_uvs(assets)
    def model(name, grid, colour, curved=True):
        path = f'combat_vfx/warrior_skills/{name}'
        write(assets/f'models/{path}.json', {'ambientocclusion':False,
            'textures':{'0':'projects:combat_vfx/ribbon/slash_5'},
            'elements':geometry(grid,inks,curved=curved)})
        write(assets/f'items/{path}.json', {'model':{'type':'minecraft:model',
            'model':f'projects:{path}','tints':[{'type':'minecraft:constant','value':colour}]}})
    for clip in CLIPS:
        for layer, count in COUNTS.items():
            for frame in range(count):
                model(f'{clip}_{layer}_{frame}',contour(clip,layer,frame),
                      {'blade':0xeaf4ff,'wake':0x9db4c9,'ember':0xcc4359}[layer],
                      curved=not clip.startswith('spin_'))
    for frame in range(14):
        model(f'fracture_{frame}',fracture(frame),0xbe5362,curved=False)
    for frame in range(9):
        model(f'contact_{frame}',impact(frame),0xffd1ce,curved=False)


if __name__ == '__main__':
    def write(path, value):
        path.parent.mkdir(parents=True,exist_ok=True)
        path.write_text(json.dumps(value,separators=(',',':'))+'\n',encoding='utf-8')
    build(PACK/'assets/projects',write)
    (PACK/'index.txt').write_text('\n'.join(sorted(p.relative_to(PACK).as_posix()
        for p in PACK.rglob('*') if p.is_file() and p.name!='index.txt'))+'\n',encoding='utf-8')
