"""Original forge helmet with native-scale pixel painting on each model face.

The reference pack informed pixel density and value grouping. ProjectS uses
its own ember crest, geometry, palette, and hand-painted face patterns.
"""
from PIL import ImageDraw
from class_armament_geometry import box

COLORS={
    'a':'#1e2b3a', 'b':'#35485d', 'c':'#627f93',
    'd':'#8eabb2', 'e':'#b4c9c4', 'f':'#5c382d',
    'g':'#b88955', 'h':'#f0d78a', 'r':'#a94732', 'R':'#e07c45',
    'q':'#643833', 't':'#925445',
}

FRONT=(
    ' bcccccc b',
    'bccdddccbb',
    'bccdeedcbb',
    'bccddcccbb',
    'gghh  hhgg',
    'gbb    bbg',
    ' bb    bb ',
    '          ',
    'ddd    ddd',
    'bcd    dcb',
    ' bccccccb ',
)
SIDE=(
    ' bccccccb ',
    'bccdddccbb',
    'bccddccbbb',
    'bcdddcccbb',
    'gghhhhhggg',
    'gghhhhhggg',
    'abbbbbbaaa',
    'bbccccccbb',
    'bccddcccbb',
    'bcccddccbb',
    'abbbbbbbba',
)
BACK=(
    '  bccccc  ',
    ' bccddcc b',
    'bccdddccbb',
    'bccdddeccb',
    'bccccccbbb',
    'abbbbbbbba',
    'abbcccbbaa',
    'bbccccccbb',
    'bccdddccbb',
    'bccddcccbb',
    'abbbbbbbba',
)


def _uv(x,y,w,h):
    # This atlas is 128 x 64. A 10 x 11 pixel face paints a 10 x 11 unit shell.
    return [x/8,y/4,(x+w)/8,(y+h)/4]


def _part(name,lower,upper,faces):
    element=box(name,lower,upper,'iron')
    element['faces']={side:{'uv':rect,'texture':'#helm'} for side,rect in faces.items()}
    return element


def _all_faces(front,side=None,back=None,top=None):
    return {'north':front,'south':back or front,'west':side or front,
            'east':side or front,'up':top or front,'down':top or front}


def forge_elements():
    return [
        _part('forged shell',[3.25,0,3],[12.75,11,13],{
            'north':_uv(0,0,10,11),'south':_uv(12,0,10,11),
            'west':_uv(24,0,10,11),'east':_uv(36,0,10,11),
            'up':_uv(48,0,10,10),'down':_uv(48,0,10,10)}),
        _part('recessed face shadow',[5,0,3.35],[11,6,3.6],
              _all_faces(_uv(48,16,6,6))),
        _part('warm wraparound brow',[2.25,5,2.45],[13.75,7,13.55],
              _all_faces(_uv(0,16,12,2))),
        _part('raised ember ridge',[6.65,3,1.9],[9.35,13,3.2],
              _all_faces(_uv(16,16,3,8),side=_uv(20,16,1,8),top=_uv(16,16,3,1))),
    ]


def forge_texture(image,tier=1):
    """Paint distinct UV faces using whole pixel clusters and deliberate gaps."""
    image.paste((0,0,0,0),(0,0,128,64))
    def paint(x,y,rows):
        width=len(rows[0])
        assert all(len(row)==width for row in rows)
        for dy,row in enumerate(rows):
            for dx,symbol in enumerate(row):
                if symbol!=' ':
                    image.putpixel((x+dx,y+dy),tuple(bytes.fromhex(COLORS[symbol][1:]))+(255,))

    paint(0,0,FRONT)
    paint(12,0,BACK)
    paint(24,0,SIDE)
    paint(36,0,tuple(row[::-1] for row in SIDE))
    paint(48,0,(
        'abbccccbba','bccddddccb','bcccddccbb','bccccccbbb',
        'bcccccbbba','bccccccbbb','bcddddccbb','bccddddccb',
        'bbccccccbb','abbbbbbbba'))
    paint(0,16,('gghhhhhhhhgg','ffggggggggff'))
    paint(16,16,('ghg','hrh','gRg','grg','grg','grg','fgf','fff'))
    paint(20,16,('h','g','r','r','r','g','f','f'))
    paint(48,16,('aaaaaa','aaaaaa','abbbaa','abbbaa','aaaaaa','aaaaaa'))
    paint(64,0,('fffffffff','fqttffttf','fggghgggf'))
    paint(75,0,('fffff','fqttf','fgggf'))
    paint(82,0,('fffffffff','fqqtttqqf','fgggggggf'))
    paint(94,0,('fffffffff','fqqqqqqqf','fftttttff','ffgggggff','fffffffff'))
    paint(105,0,('fffffffff',)*5)
    # The thigh plates retain separate painted steel patches.
    d=ImageDraw.Draw(image)
    d.rectangle((0,48,15,63),fill=COLORS['b'])
    d.polygon([(1,48),(10,48),(15,52),(11,55),(0,52)],fill=COLORS['c'])
    d.line([(1,49),(9,49),(13,52)],fill=COLORS['d'],width=1)
    d.polygon([(7,56),(15,54),(15,63),(3,63)],fill=COLORS['a'])
    d.line([(1,58),(11,58)],fill=COLORS['c'],width=1)
    d.rectangle((16,48,31,63),fill=COLORS['b'])
    d.polygon([(16,48),(29,48),(31,51),(26,55),(16,53)],fill=COLORS['c'])
    d.line([(17,49),(27,49),(30,51)],fill=COLORS['e'],width=1)
    d.polygon([(24,55),(31,53),(31,63),(18,63)],fill=COLORS['a'])
    d.line([(18,58),(27,56)],fill=COLORS['d'],width=1)
    for mark in range(tier-1):
        image.putpixel((3+mark*2,2),tuple(bytes.fromhex(COLORS['h'][1:]))+(255,))
    return image
