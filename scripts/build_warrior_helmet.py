"""ProjectS forge helmet: five structural pieces and a face-painted pixel atlas.

The supplied Isles pack is a quality reference. Geometry, UVs, palette, and
paint here are original ProjectS work. One coherent shell carries the surface
painting; only silhouette-critical fittings rise above it.
"""
from PIL import Image, ImageDraw
from class_armament_geometry import box


def _cuboid(name, lower, upper, faces):
    element = box(name, lower, upper, 'iron')
    element['faces'] = {side: {'uv': uv, 'texture': '#helm'}
                        for side, uv in faces.items()}
    return element


def _all_faces(front, side=None, back=None, top=None):
    return {'north': front, 'south': back or front,
            'west': side or front, 'east': side or front,
            'up': top or front, 'down': top or front}


def forge_elements():
    return [
        _cuboid('painted forged shell', [3.1,3.0,3.6], [12.9,13.3,12.4], {
            'north':[0,0,4,8], 'south':[4,0,8,8],
            'west':[8,0,12,8], 'east':[12,0,16,8],
            'up':[0,12,2,16], 'down':[6,12,8,16]}),
        _cuboid('projecting warm brow', [2.8,8.9,2.8], [13.2,10.3,4.0],
                _all_faces([14,8,16,12], top=[8,8,10,12])),
        _cuboid('central furnace clasp', [6.7,10.0,2.25], [9.3,13.0,3.25],
                _all_faces([8,12,10,16], side=[10,8,12,12], top=[10,8,12,12])),
        _cuboid('left sculpted cheek', [2.8,3.2,3.1], [5.2,8.9,9.1],
                _all_faces([2,12,4,16], side=[8,0,12,8], top=[2,8,4,12])),
        _cuboid('right sculpted cheek', [10.8,3.2,3.1], [13.2,8.9,9.1],
                _all_faces([2,12,4,16], side=[12,0,16,8], top=[2,8,4,12])),
    ]


def forge_texture(image, tier=1):
    """Paint continuous front, back and side planes plus three raised fittings."""
    d = ImageDraw.Draw(image)
    ink='#1b3040'; deep='#304d62'; body='#537b92'; light='#9dbbc4'
    edge='#d1deda'; bronze='#b58b57'; gold='#e6c47d'; black='#101923'

    # The same plate turns through the front and side faces. Hard clusters,
    # seams, and edge highlights follow the metal's form instead of noise.
    d.rectangle((0,0,31,31),fill=(0,0,0,0))
    d.polygon([(4,0),(26,0),(30,4),(31,11),(30,20),(29,31),(2,31),(1,19),(1,6)],fill=ink)
    d.polygon([(5,1),(25,1),(29,5),(29,11),(3,11),(3,6)],fill=deep)
    d.polygon([(5,2),(14,2),(18,4),(15,9),(3,9),(3,6)],fill=body)
    d.polygon([(5,2),(14,2),(10,4),(4,5)],fill=light)
    d.line([(5,2),(13,2)],fill=edge,width=1)
    d.polygon([(19,3),(25,2),(29,6),(28,10),(19,9)],fill=body)
    d.line([(23,3),(26,4),(28,6)],fill=light,width=1)
    d.polygon([(3,11),(29,11),(30,15),(28,17),(4,17),(2,15)],fill=bronze)
    d.line([(4,11),(27,11)],fill=gold,width=2)
    d.line([(4,16),(28,16)],fill='#684832',width=1)
    d.polygon([(5,17),(27,17),(25,21),(7,21)],fill=black)
    d.polygon([(2,19),(9,20),(11,29),(7,31),(2,29)],fill=body)
    d.polygon([(30,19),(23,20),(21,29),(25,31),(30,29)],fill=deep)
    d.line([(3,20),(8,21),(10,28)],fill=light,width=2)
    d.line([(29,20),(24,21),(22,28)],fill=light,width=1)
    d.polygon([(11,22),(21,22),(22,29),(18,31),(13,31),(10,29)],fill=deep)
    d.polygon([(12,23),(19,23),(19,26),(13,26)],fill=body)
    d.line([(13,23),(18,23)],fill=light,width=1)
    d.line([(5,30),(10,30)],fill=edge,width=1)
    d.line([(22,30),(27,30)],fill=light,width=1)
    for mark in range(tier-1):
        x=9+mark*4
        d.rectangle((x,5,x+1,6),fill=gold)

    for offset,mirror in ((64,False),(96,True)):
        x=offset
        d.rectangle((x,0,x+31,31),fill=ink)
        d.polygon([(x+3,1),(x+24,1),(x+30,7),(x+29,18),(x+25,21),
                   (x+26,31),(x+3,31),(x+1,25),(x+1,10)],fill=deep)
        d.polygon([(x+4,2),(x+22,2),(x+27,6),(x+24,12),(x+3,12)],fill=body)
        d.polygon([(x+4,2),(x+15,2),(x+10,5),(x+3,6)],fill=light)
        d.line([(x+4,2),(x+20,2)],fill=edge,width=1)
        d.polygon([(x+1,12),(x+29,12),(x+30,16),(x+3,17)],fill=bronze)
        d.line([(x+2,12),(x+27,12)],fill=gold,width=2)
        d.polygon([(x+2,19),(x+25,19),(x+26,29),(x+22,31),(x+3,31)],fill=body)
        d.polygon([(x+20,20),(x+26,19),(x+26,29),(x+21,31)],fill=deep)
        d.line([(x+3,21),(x+18,21),(x+21,26)],fill=light,width=2)
        d.line([(x+4,27),(x+18,27)],fill=edge,width=1)
        if mirror:
            patch=image.crop((x,0,x+32,32)).transpose(Image.Transpose.FLIP_LEFT_RIGHT)
            image.paste(patch,(x,0))

    d.rectangle((32,0,63,31),fill=deep)
    d.polygon([(36,2),(58,2),(62,6),(61,14),(34,14),(33,7)],fill=body)
    d.line([(36,2),(56,2)],fill=light,width=1)
    d.rectangle((33,12,62,16),fill=bronze)
    d.line([(34,12),(59,12)],fill=gold,width=1)
    d.polygon([(35,18),(59,18),(60,30),(35,30)],fill=ink)
    d.polygon([(37,20),(57,20),(56,29),(38,29)],fill=body)
    d.line([(38,20),(55,20)],fill=light,width=1)

    # Raised shell, cheek, brow and clasp surfaces have their own paint.
    d.rectangle((112,32,127,47),fill='#76553b')
    d.polygon([(112,32),(124,32),(127,35),(127,38),(113,36)],fill=bronze)
    d.line([(112,33),(123,33)],fill=gold,width=1)
    d.polygon([(112,42),(127,40),(127,47),(112,47)],fill='#4a392f')
    d.rectangle((0,48,15,63),fill='#3b5d71')
    d.polygon([(0,48),(10,48),(15,52),(15,55),(5,53),(0,51)],fill='#597f9c')
    d.line([(1,49),(9,49),(13,52)],fill=edge,width=1)
    d.polygon([(9,55),(15,54),(15,63),(5,63)],fill='#274257')
    d.line([(1,57),(11,57)],fill='#52778d',width=1)
    d.rectangle((16,48,31,63),fill='#416b84')
    d.polygon([(16,48),(29,48),(31,51),(27,54),(16,53)],fill='#7fa5b5')
    d.line([(17,49),(27,49),(30,51)],fill=edge,width=1)
    d.polygon([(25,54),(31,52),(31,63),(19,63)],fill='#2c4c63')
    d.line([(18,58),(25,56),(29,56)],fill='#658da0',width=1)
    d.rectangle((64,48,79,63),fill=ink)
    d.polygon([(68,49),(75,49),(78,52),(78,60),(74,63),(68,63),
               (65,60),(65,52)],fill=bronze)
    d.polygon([(69,50),(74,50),(77,53),(77,59),(73,62),(69,62),
               (66,59),(66,53)],fill=gold)
    d.polygon([(71,51),(74,54),(74,57),(71,61),(68,57),(68,54)],fill='#9a362d')
    d.polygon([(71,52),(73,55),(71,58),(69,55)],fill='#e9693b')
    d.point((70,54),fill='#ffcc7b')
    return image
