"""Original hand-painted, class-specific ProjectS helmet item models.

The concept sheet is a visual target only. Every atlas pixel and cuboid here is
drawn for ProjectS; no source-pack texture or model data is imported.
"""
from PIL import Image, ImageDraw
from class_armament_geometry import box

PALETTES = {
    'warrior': ('13243a','264564','3f6e96','76a5c3','bed7df','de532e','ffba65'),
    'ranger': ('1a2b24','304732','537049','8e9c5c','d1b474','9d683e','f0c678'),
    'mage': ('1c1930','332954','594477','a4a9bc','d8d7db','a84bc9','efb6fa'),
    'assassin': ('171822','302b3c','59415a','886284','b6a0ae','9b4767','de9bb0'),
    'templar': ('38464c','657377','a9b9b4','e0dfc9','fff0d4','b68b4f','f6ce80'),
    'healer': ('354950','71847e','b4c0ac','e5e6d0','f9f1df','7fb9a5','d5f1d3'),
    'starweaver': ('142d44','294f70','4e7fa0','88b8c6','c9e1de','61c6c1','ddf7dc'),
}

def palette(job):
    return tuple('#'+color for color in PALETTES[job])

# The painted shell changes outline across jobs; alpha is the open face, not a
# black opaque rectangle. Coordinates are original 32px painting guides.
OUTLINES = {
    'warrior': [(8,0),(23,0),(27,3),(29,8),(29,15),(27,19),(26,27),(23,31),(19,31),(20,21),(12,21),(13,31),(9,31),(6,27),(3,19),(2,10),(5,3)],
    'ranger': [(7,2),(19,0),(25,3),(30,5),(29,12),(31,14),(27,17),(26,26),(22,31),(19,31),(20,20),(10,20),(11,30),(7,31),(3,25),(2,17),(0,14),(3,7)],
    'mage': [(11,0),(20,0),(25,4),(27,10),(29,13),(28,25),(30,31),(20,31),(20,18),(12,18),(12,31),(2,31),(4,25),(3,13),(6,7)],
    'assassin': [(9,3),(24,3),(29,6),(31,11),(30,17),(29,26),(27,31),(5,31),(3,26),(2,17),(0,11),(3,6)],
    'templar': [(6,2),(26,2),(30,7),(30,15),(29,30),(23,31),(22,19),(10,19),(9,31),(3,30),(2,15),(2,7)],
    'healer': [(7,1),(12,0),(16,6),(20,0),(25,1),(28,7),(30,16),(29,27),(26,31),(20,31),(20,20),(12,20),(12,31),(6,31),(3,27),(2,16),(4,7)],
    'starweaver': [(5,4),(9,0),(13,3),(19,3),(23,0),(27,4),(30,12),(29,20),(28,30),(22,31),(20,19),(12,19),(10,31),(4,30),(3,20),(2,12)],
}

CROWNS = {
    'warrior': [(7,2),(23,2),(27,6),(28,12),(24,15),(8,15),(4,12),(5,6)],
    'ranger': [(7,4),(20,2),(29,7),(27,11),(22,14),(6,14),(3,11)],
    'mage': [(11,2),(20,2),(25,7),(27,14),(5,14),(7,8)],
    'assassin': [(9,5),(24,5),(29,8),(30,13),(3,13),(4,8)],
    'templar': [(7,4),(25,4),(28,8),(28,15),(4,15),(4,8)],
    'healer': [(8,3),(13,4),(16,9),(19,4),(24,3),(27,9),(28,16),(4,16),(5,9)],
    'starweaver': [(6,6),(10,3),(13,6),(19,6),(22,3),(26,6),(28,13),(4,13)],
}

SIDE_OUTLINES = {
    'warrior': [(5,2),(23,2),(29,7),(30,17),(25,20),(25,29),(21,31),(18,31),(17,22),(10,22),(9,31),(5,31),(2,27),(2,14)],
    'ranger': [(10,3),(19,1),(27,5),(31,9),(31,14),(24,16),(25,24),(21,30),(17,30),(18,21),(9,20),(7,31),(3,30),(1,23),(2,11)],
    'mage': [(11,0),(22,2),(26,7),(27,12),(30,17),(29,31),(21,31),(20,21),(11,21),(10,31),(2,31),(3,17),(5,9)],
    'assassin': [(8,5),(25,5),(30,10),(31,15),(28,21),(29,31),(3,31),(4,21),(1,15),(2,10)],
    'templar': [(6,3),(26,3),(30,7),(30,15),(27,18),(27,30),(22,31),(19,19),(12,19),(10,31),(5,31),(2,27),(2,9)],
    'healer': [(7,3),(11,0),(16,7),(21,0),(26,3),(29,10),(30,18),(27,20),(26,31),(20,31),(18,21),(13,21),(11,31),(5,31),(2,20),(3,10)],
    'starweaver': [(5,4),(9,0),(13,5),(20,5),(24,0),(28,5),(30,14),(27,21),(26,31),(21,31),(18,20),(13,20),(10,31),(5,31),(2,20),(2,14)],
}


def front_paint(job, tier):
    ink, shadow, body, light, edge, accent, hot = palette(job)
    image = Image.new('RGBA',(32,32))
    d = ImageDraw.Draw(image)
    d.polygon(OUTLINES[job],fill=ink)
    d.polygon(CROWNS[job],fill=shadow)
    if job == 'warrior':
        d.polygon([(6,4),(15,2),(15,12),(8,14),(4,11)],fill=body)
        d.line([(7,4),(14,2),(19,2)],fill=edge,width=1)
        d.polygon([(17,3),(23,3),(27,8),(26,12),(17,12)],fill=body)
        d.polygon([(3,14),(29,14),(28,17),(24,18),(8,18),(4,17)],fill=light)
        d.line([(4,14),(27,14)],fill=edge,width=1)
        d.polygon([(6,17),(26,17),(24,19),(8,19)],fill=ink)
        d.polygon([(2,18),(4,17),(5,25),(7,30),(6,31),(3,26)],fill=edge)
        d.polygon([(30,18),(28,17),(27,25),(25,30),(26,31),(29,26)],fill=edge)
        d.line([(5,20),(6,25)],fill=light,width=1)
        d.line([(27,20),(26,25)],fill=light,width=1)
        for x,flip in ((3,False),(29,True)):
            points=[(3,18),(9,20),(12,27),(10,31),(7,30),(3,25),(2,21)]
            if flip: points=[(32-a,b) for a,b in points]
            d.polygon(points,fill=body)
            x0=3 if not flip else 26
            d.line([(x0,19),(x0,25),(8 if not flip else 24,30)],fill=edge,width=1)
        d.polygon([(14,4),(17,4),(19,7),(17,12),(14,11),(12,7)],fill=shadow)
        d.polygon([(15,5),(17,5),(18,7),(16,10),(14,8)],fill=accent)
        d.point((15,6),fill=hot)
    elif job == 'ranger':
        d.polygon([(5,6),(19,2),(27,6),(25,12),(5,13)],fill=body)
        d.polygon([(3,11),(11,8),(23,7),(30,9),(28,13),(17,15),(4,15)],fill=accent)
        d.line([(3,11),(11,8),(23,7),(29,9)],fill=hot,width=1)
        d.polygon([(7,16),(25,13),(25,18),(19,21),(9,21)],fill=ink)
        d.polygon([(4,19),(10,20),(11,28),(8,31),(4,26)],fill=body)
        d.polygon([(25,19),(29,18),(27,27),(23,31),(20,29),(21,22)],fill=shadow)
        d.line([(7,22),(9,27)],fill=light,width=1)
        d.polygon([(9,2),(15,3),(20,7),(13,7)],fill=light)
    elif job == 'mage':
        d.polygon([(11,2),(19,2),(24,8),(25,14),(7,14),(8,8)],fill=body)
        d.line([(11,2),(19,2),(23,7)],fill=light,width=1)
        d.polygon([(5,14),(27,14),(28,19),(23,22),(9,22),(4,19)],fill=light)
        d.line([(5,15),(10,18),(16,16),(22,18),(27,15)],fill=edge,width=2)
        d.polygon([(9,20),(23,20),(22,30),(10,30)],fill=ink)
        d.polygon([(4,20),(10,22),(12,31),(3,31)],fill=body)
        d.polygon([(28,20),(22,22),(20,31),(29,31)],fill=body)
        d.line([(5,22),(9,25),(10,29)],fill=edge,width=1)
        d.line([(27,22),(23,25),(22,29)],fill=edge,width=1)
        d.polygon([(16,4),(21,9),(16,15),(11,9)],fill=accent)
        d.polygon([(16,6),(18,9),(16,12),(14,9)],fill=hot)
    elif job == 'assassin':
        d.polygon([(8,5),(24,5),(29,10),(30,14),(3,14),(4,10)],fill=body)
        d.polygon([(2,13),(30,13),(29,17),(23,18),(8,18),(3,17)],fill=shadow)
        d.line([(4,14),(28,14)],fill=light,width=1)
        d.polygon([(7,18),(25,18),(24,22),(8,22)],fill=ink)
        d.polygon([(6,23),(25,23),(28,29),(26,31),(6,31),(4,29)],fill=body)
        d.line([(8,24),(23,24),(26,27)],fill=light,width=1)
        d.line([(8,27),(22,27)],fill=shadow,width=1)
        d.polygon([(5,18),(10,20),(9,25),(4,25)],fill=shadow)
        d.polygon([(27,18),(22,20),(23,25),(28,25)],fill=shadow)
        d.rectangle((5,19,6,20),fill=accent)
    elif job == 'templar':
        d.polygon([(7,4),(25,4),(28,9),(28,16),(4,16),(4,9)],fill=body)
        d.polygon([(6,5),(16,4),(26,5),(27,9),(5,9)],fill=light)
        d.line([(6,5),(25,5)],fill=edge,width=1)
        d.polygon([(3,14),(29,14),(29,18),(25,19),(7,19),(3,18)],fill=accent)
        d.line([(3,15),(29,15)],fill=hot,width=1)
        d.polygon([(8,19),(24,19),(22,31),(10,31)],fill=(0,0,0,0))
        d.polygon([(3,19),(9,20),(10,30),(6,31),(3,28)],fill=light)
        d.polygon([(29,19),(23,20),(22,30),(26,31),(29,28)],fill=light)
        d.line([(4,20),(7,21),(8,29)],fill=edge,width=1)
        d.line([(28,20),(25,21),(24,29)],fill=edge,width=1)
        d.polygon([(15,5),(17,5),(18,16),(16,19),(14,16)],fill=accent)
    elif job == 'healer':
        d.polygon([(8,3),(13,4),(16,10),(19,4),(24,3),(27,10),(27,16),(5,16),(5,10)],fill=light)
        d.line([(8,3),(12,4),(16,10),(20,4),(24,3)],fill=edge,width=1)
        d.polygon([(4,15),(28,15),(28,19),(24,21),(8,21),(4,19)],fill=accent)
        d.line([(5,16),(27,16)],fill=hot,width=1)
        d.polygon([(9,20),(23,20),(21,31),(11,31)],fill=(0,0,0,0))
        d.polygon([(3,20),(9,22),(11,30),(7,31),(3,27)],fill=body)
        d.polygon([(29,20),(23,22),(21,30),(25,31),(29,27)],fill=body)
        d.rectangle((15,7,17,15),fill=body)
        d.rectangle((12,10,20,12),fill=body)
        d.point((16,10),fill=edge)
    else:
        d.polygon([(6,6),(12,5),(16,8),(20,5),(26,6),(28,13),(4,13)],fill=body)
        d.line([(6,6),(12,5),(16,8),(20,5),(26,6)],fill=edge,width=1)
        d.polygon([(3,14),(29,14),(28,19),(25,20),(7,20),(4,19)],fill=light)
        d.polygon([(9,20),(23,20),(22,31),(10,31)],fill=(0,0,0,0))
        d.polygon([(3,20),(10,21),(11,30),(7,31),(3,27)],fill=body)
        d.polygon([(29,20),(22,21),(21,30),(25,31),(29,27)],fill=body)
        d.polygon([(16,3),(20,8),(16,14),(12,8)],fill=accent)
        d.polygon([(16,5),(18,8),(16,11),(14,8)],fill=hot)
        d.point((16,6),fill=edge)
    if tier > 1:
        for x in range(1,tier):
            d.point((6+x*2,3),fill=hot)
    return image


def side_paint(job, mirror=False):
    ink, shadow, body, light, edge, accent, hot = palette(job)
    image=Image.new('RGBA',(32,32))
    d=ImageDraw.Draw(image)
    bottom=28 if job in ('assassin','templar') else 31
    d.polygon([(4,2),(23,2),(29,7),(31,13),(30,19),(27,21),
               (26,bottom),(22,31),(18,31),(17,21),(11,21),
               (10,29),(6,31),(2,27),(1,16)],fill=ink)
    d.polygon([(5,3),(23,3),(28,8),(29,16),(25,18),(6,18),(2,15)],fill=shadow)
    d.polygon([(6,5),(22,4),(27,8),(27,14),(19,16),(4,14)],fill=body)
    d.line([(6,5),(22,4),(26,7)],fill=edge,width=1)
    d.polygon([(3,18),(10,19),(12,23),(9,29),(5,30),(2,25)],fill=body)
    d.polygon([(22,18),(29,17),(28,26),(24,30),(20,28),(19,22)],fill=shadow)
    d.line([(3,19),(10,20),(11,23)],fill=light,width=1)
    d.line([(22,19),(27,19)],fill=light,width=1)
    if job in ('ranger','templar','healer'):
        d.polygon([(7,8),(25,8),(29,11),(22,13),(5,12)],fill=accent)
        d.line([(7,8),(25,8)],fill=hot,width=1)
    elif job=='mage':
        d.polygon([(8,9),(22,7),(26,12),(23,16),(7,15)],fill=accent)
        d.polygon([(16,9),(20,12),(16,15),(13,12)],fill=hot)
    elif job=='starweaver':
        d.polygon([(9,8),(15,5),(22,8),(25,13),(7,13)],fill=accent)
        d.point((17,9),fill=hot)
    elif job=='assassin':
        d.line([(5,16),(27,16)],fill=accent,width=2)
        d.polygon([(4,22),(28,21),(26,27),(5,28)],fill=shadow)
    else:
        d.rectangle((24,18,25,19),fill=accent)
    mask=Image.new('L',(32,32))
    ImageDraw.Draw(mask).polygon(SIDE_OUTLINES[job],fill=255)
    image.putalpha(mask)
    if mirror: image=image.transpose(Image.Transpose.FLIP_LEFT_RIGHT)
    return image


def back_paint(job):
    ink, shadow, body, light, edge, accent, hot = palette(job)
    image=Image.new('RGBA',(32,32))
    d=ImageDraw.Draw(image)
    d.polygon(OUTLINES[job],fill=ink)
    d.polygon(CROWNS[job],fill=shadow)
    d.polygon([(7,4),(24,4),(27,10),(26,16),(6,16),(5,10)],fill=body)
    d.line([(7,5),(23,5),(26,9)],fill=light,width=1)
    d.line([(5,18),(27,18)],fill=accent,width=2)
    d.polygon([(3,20),(10,21),(11,29),(7,31),(3,27)],fill=shadow)
    d.polygon([(29,20),(22,21),(21,29),(25,31),(29,27)],fill=shadow)
    return image


def helmet_texture(job,tier):
    """128x64 atlas: four 32px painted faces plus compact ornament swatches."""
    image=Image.new('RGBA',(128,64))
    for art,xy in ((front_paint(job,tier),(0,0)),(back_paint(job),(32,0)),
                   (side_paint(job),(64,0)),(side_paint(job,True),(96,0))):
        image.paste(art,xy)
    ink,shadow,body,light,edge,accent,hot=palette(job)
    d=ImageDraw.Draw(image)
    for i,color in enumerate((body,light,accent,shadow,hot,edge)):
        x=i*16
        d.rectangle((x,32,x+15,47),fill=color)
        d.line([(x,32),(x+11,32)],fill=edge if i<3 else light,width=1)
        d.line([(x+15,35),(x+15,47)],fill=ink,width=1)
    return image


def helmet_elements(job,tier):
    shell=box('painted class shell',[3.2,3,3.2],[12.8,12.4,12.8],'iron')
    shell['faces']={face:{'uv':uv,'texture':'#helm'} for face,uv in {
        'north':[0,0,4,8],'south':[4,0,8,8],
        'west':[8,0,12,8],'east':[12,0,16,8],
        'up':[1,8.5,2,9.5]}.items()}
    elements=[shell]
    swatch={'body':[.5,8.5,1.5,9.5], 'light':[2.5,8.5,3.5,9.5],
            'accent':[4.5,8.5,5.5,9.5], 'shadow':[6.5,8.5,7.5,9.5],
            'hot':[8.5,8.5,9.5,9.5], 'edge':[10.5,8.5,11.5,9.5]}
    def piece(name,lo,hi,paint,angle=0,axis='z'):
        part=box(name,lo,hi,'iron',angle,axis)
        part['faces']={face:{'uv':swatch[paint], 'texture':'#helm'}
                       for face in part['faces']}
        elements.append(part)
    if job=='warrior':
        piece('flowing crown ridge',[7.3,11.6,3.4],[8.7,13.4,11.6],'light')
        piece('rear ember pennant',[7.3,12.1,11.7],[8.7,15.0,12.5],'accent',-22.5,'x')
        piece('ruby socket',[7.0,10.4,2.6],[9.0,12.0,3.3],'shadow')
        piece('ruby facet',[7.45,10.65,2.35],[8.55,11.55,2.65],'accent')
        piece('visor edge',[3.1,8.75,2.6],[12.9,9.4,3.35],'edge')
    elif job=='ranger':
        piece('swept leaf visor',[2.3,10.3,2.7],[13.2,11.1,4.3],'accent',-22.5)
        piece('visor lit edge',[4.2,11.0,2.5],[11.9,11.3,3.2],'hot')
        piece('rear leafy crest',[5.1,12.2,8.5],[6.5,15.5,10.5],'body',-22.5)
    elif job=='mage':
        piece('hood crown',[5.1,12.0,4.6],[11,15.1,11.6],'shadow',22.5)
        piece('amulet socket',[6.7,8.6,2.5],[9.3,11.3,3.1],'edge',45)
        piece('amethyst cut',[7.35,9.2,2.2],[8.65,10.7,2.6],'accent',45)
        piece('hood face hem',[3.5,7.7,2.7],[12.5,8.4,4.1],'light')
    elif job=='assassin':
        piece('low cowl brow',[2.6,10.5,2.5],[13.4,11.4,4.3],'shadow')
        piece('oblique steel slit',[4.2,8.2,2.5],[11.8,8.7,3.2],'light',-22.5)
        piece('wrapped lower mask',[4.5,5.1,2.7],[11.5,7.8,3.8],'body')
    elif job=='templar':
        piece('shield crown ridge',[7.3,11.4,3.4],[8.7,14.2,11.4],'light')
        piece('oath visor',[2.7,8.8,2.6],[13.3,9.8,4.2],'accent')
        piece('central oath fitting',[7.4,8.0,2.3],[8.6,11.8,3.0],'hot')
        piece('left jaw shield',[2.8,3.6,3.4],[4.1,8.1,6.8],'body')
        piece('right jaw shield',[11.9,3.6,3.4],[13.2,8.1,6.8],'body')
    elif job=='healer':
        piece('left split mitre',[5.1,11.8,5.0],[7.0,15.3,10.4],'light',-22.5)
        piece('right split mitre',[9.0,11.8,5.0],[10.9,15.3,10.4],'light',22.5)
        piece('gentle sun visor',[3.6,8.9,2.7],[12.4,9.7,4.0],'accent')
        piece('prayer seal',[7.4,10.2,2.5],[8.6,11.7,3.0],'hot')
    else:
        piece('left celestial prong',[4.1,11.5,4.0],[5.2,15.2,6.3],'light',-22.5)
        piece('right celestial prong',[10.8,11.5,4.0],[11.9,15.2,6.3],'light',22.5)
        piece('star visor',[3.0,8.8,2.6],[13.0,9.7,4.0],'light')
        piece('forehead star socket',[7.2,10.3,2.5],[8.8,12.0,3.1],'accent',45)
    return elements
