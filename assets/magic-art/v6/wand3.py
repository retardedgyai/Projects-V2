"""Wand-as-wrench v3, built as a small 3D model like the props (Thaumcraft wand = rod + caps):
a near-black greatwood rod with a leather grip, brass pommel / band / collar, and a brass
spanner head with two parallel prongs. The only glow is the cyan crystal seated in the throat."""
from PIL import Image
from mcrender import render, box, rotated, outline
import jar4
import desk9

paint = jar4.paint
WD = desk9.WD
BR = jar4.BR
LE = ['#24130a', '#46281a', '#6e4228', '#9a643c']
CY = ['#1f6fa8', '#49b4d9', '#a8ecf6', '#f0ffff']
T = dict(desk9.T)
T.update({
    'rod': paint(lambda x, y: WD[5] if x < 4 else WD[4] if x < 9 else WD[3] if x < 13 else WD[2], rim=(WD[5], WD[1])),
    'grip': paint(lambda x, y: LE[0] if y % 4 == 3 else (LE[3] if x < 5 else LE[2] if x < 11 else LE[1])),
    'crystal': paint(lambda x, y: CY[3] if x + y < 9 else CY[2] if x + y < 17 else CY[1] if x + y < 24 else CY[0]),
})
BRS = {'north': 'brass', 'south': 'brass', 'east': 'brass', 'west': 'brass', 'up': 'brass_top', 'down': 'brass'}
full = [0, 0, 16, 16]
FULL4 = {k: full for k in ('north', 'south', 'east', 'west')}


def parts():
    E = []
    # pommel
    E.append(box((6.8, 0.0, 6.8), (9.2, 1.6, 9.2), BRS))
    E.append(box((7.4, -0.8, 7.4), (8.6, 0.0, 8.6), BRS))
    # long slim rod, grip, band
    E.append(box((7.2, 1.6, 7.2), (8.8, 24.0, 8.8), 'rod', uv=FULL4))
    E.append(box((6.9, 2.4, 6.9), (9.1, 8.4, 9.1), 'grip', uv=FULL4))
    E.append(box((6.9, 14.4, 6.9), (9.1, 15.2, 9.1), BRS))
    # collar
    E.append(box((6.6, 23.6, 6.6), (9.4, 25.0, 9.4), BRS))
    # spanner head: narrow bridge + two slim prongs around an open slot
    E.append(box((5.4, 25.0, 7.0), (10.6, 27.0, 9.0), BRS, uv=FULL4))
    for (x0, x1) in ((5.4, 6.9), (9.1, 10.6)):
        E.append(box((x0, 27.0, 7.0), (x1, 30.4, 9.0), BRS, uv=FULL4))
    E.append(box((5.7, 30.4, 7.2), (6.7, 31.2, 8.8), BRS))
    E.append(box((9.3, 30.4, 7.2), (10.3, 31.2, 8.8), BRS))
    # crystal in the throat (the only glow)
    E.append(box((6.9, 26.4, 7.1), (9.1, 29.0, 8.9), 'crystal', uv={k: full for k in ('north', 'south', 'east', 'west', 'up')}, emissive=True))
    return E


def M(angle):
    return {'elements': [rotated(e, 'z', angle, (8, 15.2, 8)) for e in parts()]}


if __name__ == '__main__':
    import sys
    ang = float(sys.argv[1]) if len(sys.argv) > 1 else -45
    big = render(M(ang), T, size=192, scale=0.44, light=True, rot_x=20, rot_y=20, center=(8, 15.2, 8))
    big = outline(big, (255, 255, 255, 50))
    big.save('wand3_big.png')
    ico = render(M(ang), T, size=32, scale=0.64, light=True, rot_x=20, rot_y=20, center=(8, 15.2, 8))
    ico.save('wand3.png')
    sh = Image.new('RGBA', (192 * 3 + 200, 192 * 3 + 40), (14, 16, 20, 255))
    b = big.resize((576, 576), Image.NEAREST)
    sh.paste(b, (20, 20), b)
    for i, s in enumerate((32 * 4, 64)):
        c = ico.resize((s, s), Image.NEAREST)
        sh.paste(c, (620, 20 + i * 160), c)
    sh.paste(ico, (620, 260), ico)
    sh.save('prev_wand3.png')
    print('ok')
