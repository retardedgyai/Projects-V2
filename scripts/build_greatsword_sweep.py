"""Native pixel-contour animation for ONE greatsword attack, not a cropped slash PNG.

Geometry is reconstructed from the blade tip's sampled path at each authored tick.
Past samples have their own age/width and detach into a separate dissolving wake.
Only existing grayscale texels provide ink; no complete source silhouette is drawn.
"""
from pathlib import Path
import json
import math
import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
PACK = ROOT / 'server-minestom/src/main/resources/core-ui-pack'
SIZE = 64
# Uneven spacing is intentional: settle -> accelerate through the target -> recover.
ANGLES = (-1.24, -1.16, -.84, -.12, .65, 1.13, 1.30)
RADII = (5.2, 5.3, 5.65, 6.2, 6.35, 6.2, 6.1)


def lerp(values, t):
    t = min(len(values)-1, max(0, t))
    i = min(len(values)-2, int(t))
    return values[i] + (values[i+1]-values[i])*(t-i)


def tip(t):
    a, r = lerp(ANGLES, t), lerp(RADII, t)
    return np.array((8+math.sin(a)*r, 6.2+math.cos(a)*r*.72))


def polygon(grid, points, ink):
    """Quantized native geometry coverage. This does not write or modify a bitmap."""
    points = np.array(points)*4
    for y in range(max(0, int(points[:, 1].min())), min(SIZE, math.ceil(points[:, 1].max()))):
        crossings = []
        for a, b in zip(points, np.roll(points, -1, axis=0)):
            if (a[1] <= y+.5 < b[1]) or (b[1] <= y+.5 < a[1]):
                crossings.append(a[0]+(y+.5-a[1])*(b[0]-a[0])/(b[1]-a[1]))
        crossings.sort()
        for lo, hi in zip(crossings[::2], crossings[1::2]):
            for x in range(max(0, math.ceil(lo-.5)), min(SIZE, math.ceil(hi-.5))):
                grid[y, x] = max(grid[y, x], ink)


def ribbon(frame, wake=False):
    g = np.zeros((SIZE, SIZE), dtype=np.uint8)
    # 48 short sections follow a non-uniform moving tip. No fixed crescent mask.
    for i in range(48):
        birth = i/8
        age = frame-birth
        if wake:
            if not 1.5 < age < 8.0:
                continue
            life = (age-1.5)/6.5
            # Split along intentional seams; no random speckle noise.
            if life > .4 and i % 8 in (0, 1, 7):
                continue
            if life > .72 and i % 8 not in (3, 4):
                continue
            width = .95*(1-life)**.7
            ink = 2 if life < .5 else 1
            drift = .48*life + 1.05*life*life
        else:
            if not 0 <= age < 2.4:
                continue
            speed = abs(lerp(ANGLES, birth+.2)-lerp(ANGLES, birth-.2))/.4
            # Speed changes the actual silhouette, not the global display scale.
            width = (.35+speed*3.8)*math.sin(math.pi*(age+.12)/2.65)**.6
            # Long tapered scallops, not a uniform crescent or noisy teeth.
            width *= .78 + .22*math.sin(birth*2.1)**2
            ink, drift = 2, 0
        a, b = lerp(ANGLES, birth), lerp(ANGLES, birth+.125)
        pa, pb = tip(birth), tip(birth+.125)
        na = np.array((math.sin(a), math.cos(a)*.72))
        nb = np.array((math.sin(b), math.cos(b)*.72))
        pa, pb = pa+na*drift, pb+nb*drift
        polygon(g, [pa, pb, pb-nb*width, pa-na*width], ink if wake else 1)
        if not wake:
            # Thin high-value rim, broad mid-value body, dark interior.
            polygon(g, [pa, pb, pb-nb*width*.66, pa-na*width*.66], 2)
            polygon(g, [pa, pb, pb-nb*min(.45, width*.28),
                        pa-na*min(.45, width*.28)], 3)
    if not wake and frame <= 6:
        p = tip(frame)
        tangent = tip(min(6, frame+.15))-tip(max(0, frame-.15))
        tangent /= max(.001, np.linalg.norm(tangent))
        n = np.array((-tangent[1], tangent[0]))
        polygon(g, [p+tangent*.55, p+n*.2, p-tangent*.6, p-n*.2], 3)
    if wake:
        # Fine escaping rails have their own advancing heads and eroding tails.
        # They reuse the SAME blade path, rather than orbiting a finished image.
        for rail in range(2):
            for i in range(20, 46):
                birth = i/8
                age = frame-birth-(.4+rail*.65)
                if not 0 < age < 3.5:
                    continue
                pa, pb = tip(birth), tip(birth+.125)
                na = np.array((math.sin(lerp(ANGLES,birth)),math.cos(lerp(ANGLES,birth))*.72))
                nb = np.array((math.sin(lerp(ANGLES,birth+.125)),math.cos(lerp(ANGLES,birth+.125))*.72))
                drift = .35 + rail*.42 + age*.19
                width = (.26-rail*.06)*(1-age/3.5)
                pa, pb = pa+na*drift, pb+nb*drift
                polygon(g,[pa,pb,pb-nb*width,pa-na*width],2)
    return g


def impact(frame):
    g = np.zeros((SIZE, SIZE), dtype=np.uint8)
    if frame >= 8:
        return g
    # Contact-only flash: a compressed core bursts into independently escaping shards.
    for i, (angle, length) in enumerate(((.15, 4.2), (1.4, 2.5), (2.8, 3.4), (4.0, 2.2), (5.3, 3.0))):
        d = np.array((math.cos(angle), math.sin(angle)))
        n = np.array((-d[1], d[0]))
        if frame <= 1:
            c = np.array((8., 8.))
            # A single bright compression -> burst, not repeated full flashes.
            length *= .65 if frame==0 else 1.0
            polygon(g, [c-n*.4, c+d*length, c+n*.32, c-d*.45], 3)
        else:
            life = (frame-2)/6
            if life > .6 and i % 2 == 0:
                continue
            c = np.array((8., 8.))+d*(1.2+life*3.1)
            polygon(g, [c-d*(1-life)*1.1, c+n*(1-life)*.3,
                        c+d*(1-life)*.65, c-n*(1-life)*.2], 2 if frame < 4 else 1)
    return g


def fold_rows():
    """Continuous bent membrane in Y/Z, made from legal native planar strips.

    Integrating each row's tangent makes adjacent endpoints meet exactly. The old
    height staircase left disconnected horizontal strips which vanished edge-on.
    This is curvature across one surface, NOT crossed copies or solid cube sides.
    """
    out = []
    y, z = 8., 0.
    for row in range(SIZE):
        u = (row+.5)/4
        angle = -45 if u<7 else -22.5 if u<8 else 0 if u<8.5 else 22.5 if u<9.5 else 45
        # Curl the cutting edge UP into the cast plane. The opposite bend cancels
        # its pitch and makes the bright outer band edge-on to the casting player.
        angle = -angle
        a = math.radians(angle)
        dy, dz = -math.sin(a)/4, math.cos(a)/4
        out.append((y+dy/2,z+dz/2,angle))
        y, z = y+dy,z+dz
    # Keep the middle of the bend at the model origin; framing is not a camera trick.
    yc, zc, _ = out[32]
    return [(y-yc+8,z-zc+8,angle) for y,z,angle in out]


def ink_uvs(assets):
    # Sample opaque grayscale ink only, not the original painted crescent/alpha shape.
    rgba = np.asarray(Image.open(assets/'textures/combat_vfx/ribbon/slash_5.png').convert('RGBA'))
    out = {}
    for ink, value in ((1, 64), (2, 192), (3, 255)):
        yy, xx = np.nonzero((rgba[:, :, 0] == value) & (rgba[:, :, 3] == 255))
        x, y = int(xx[0]), int(yy[0])
        out[ink] = [x/4, y/4, (x+1)/4, (y+1)/4]
    return out


def geometry(grid, inks, wake=False, frame=0, curved=True):
    elements = []
    rows = fold_rows()
    for z, row in enumerate(grid):
        start = 0
        while start < SIZE:
            ink = int(row[start])
            end = start+1
            while end < SIZE and row[end] == ink:
                end += 1
            if ink:
                y, center_z, angle = rows[z] if curved else (8,z/4+.125,0)
                if wake:
                    y += .10*max(0, frame-3)
                u0, v0, u1, v1 = inks[ink]
                element = {'from':[start/4, y, center_z-.125], 'to':[end/4, y, center_z+.125],
                    'shade':False, 'faces':{
                        'up':{'texture':'#0','uv':[u0,v1,u1,v0],'tintindex':0},
                        'down':{'texture':'#0','uv':[u0,v0,u1,v1],'tintindex':0}}}
                if angle:
                    element['rotation']={'origin':[(start+end)/8,y,center_z],
                                         'axis':'x','angle':angle,'rescale':False}
                elements.append(element)
            start = end
    return elements


def build(assets, write):
    from build_expanded_slashes import build as build_expanded
    inks = ink_uvs(assets)
    for layer, count in (('blade', 10), ('wake', 16), ('impact', 9)):
        for frame in range(count):
            grid = impact(frame) if layer == 'impact' else ribbon(frame, layer == 'wake')
            name = f'combat_vfx/greatsword/{layer}_{frame}'
            write(assets/f'models/{name}.json', {'ambientocclusion':False,
                'textures':{'0':'projects:combat_vfx/ribbon/slash_5'},
                'elements':geometry(grid, inks, layer=='wake', frame, curved=layer!='impact')})
            # Steel-white edge, blue-gray wake, warm contact. No borrowed purple/red identity.
            color = {'blade':0xeaf4ff, 'wake':0x9dc4e3, 'impact':0xffd899}[layer]
            write(assets/f'items/{name}.json', {'model':{'type':'minecraft:model',
                'model':f'projects:{name}', 'tints':[{'type':'minecraft:constant','value':color}]}})
    build_expanded(assets,write)


if __name__ == '__main__':
    def write(path, value):
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps(value, separators=(',', ':'))+'\n', encoding='utf-8')
    build(PACK/'assets/projects', write)
    (PACK/'index.txt').write_text('\n'.join(sorted(p.relative_to(PACK).as_posix()
        for p in PACK.rglob('*') if p.is_file() and p.name!='index.txt'))+'\n', encoding='utf-8')
