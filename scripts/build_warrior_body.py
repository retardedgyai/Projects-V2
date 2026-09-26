"""ProjectS forge-warrior armor, painted for the native 64x32 equipment UV.

Each exposed torso and arm face is authored separately. The design uses a
deep-set furnace core framed by cool plate and a two-level copper waist.
"""
from PIL import Image


COLORS = (
    '111821',  # deep unlit seam
    '35485d',  # blue-steel shadow
    '627f93',  # blue-steel body
    '8eabb2',  # lit plate
    'b4c9c4',  # cold highlight
    '5c382d',  # smoke leather
    'aa7952',  # forged copper
    'ddc17a',  # brass rim
    'df623d',  # ember clasp
)

# 8 x 12 torso. Wide collar, convex breastplate, then a separate copper waist.
# The dark furnace socket breaks the broad plate without outlining every pixel.
TORSO_FRONT = (
    '..1221..',
    '.1233321',
    '12677621',
    '16777761',
    '13444321',
    '13443211',
    '12308211',
    '12233211',
    '.566665.',
    '.677776.',
    '.122221.',
    '.112221.',
)
TORSO_BACK = (
    '..1221..',
    '.1233321',
    '12333321',
    '12333221',
    '12322221',
    '12322211',
    '12212211',
    '12222221',
    '.566665.',
    '.677776.',
    '.122221.',
    '.112221.',
)
TORSO_LEFT = (
    '1221','2331','3442','3432','2332','2232',
    '2232','2232','5665','6776','2221','1221',
)
TORSO_RIGHT = (
    '1121','1231','2331','2221','2221','2211',
    '2211','2221','5665','6776','2221','1221',
)
ARM_FRONT = (
    '1221','2332','3442','6776','....','6..6',
    '5555','5665','1551','....','....','....',
)
ARM_BACK = (
    '1221','2332','2332','6776','....','6..6',
    '5555','5665','1551','....','....','....',
)
ARM_LEFT = (
    '1121','1231','2331','6776','....','7..6',
    '5555','5665','1551','....','....','....',
)
ARM_RIGHT = (
    '1121','1231','2331','6776','....','6..6',
    '5555','5665','1551','....','....','....',
)
LEG_FRONT = (
    '5665','6776','1111','1221','2331','2331',
    '1221','....','....','....','....','....',
)
LEG_BACK = (
    '5665','6776','1111','1221','2221','2221',
    '1221','....','....','....','....','....',
)
LEG_LEFT = (
    '5665','6776','1111','1221','2331','2331',
    '1221','....','....','....','....','....',
)
LEG_RIGHT = (
    '5665','6776','1111','1111','1221','1221',
    '1221','....','....','....','....','....',
)
ITEM_BOOT_FRONT = (
    '5665','6776','6556','2332','3442','2332',
    '1221','1221','1221','2332','3442','1111',
)
ITEM_BOOT_BACK = (
    '5665','6776','6556','2221','2321','2221',
    '1221','1221','1221','2221','2321','1111',
)
ITEM_BOOT_LEFT = (
    '5665','6776','6556','2331','3431','2331',
    '1221','1221','1221','2331','3431','1111',
)
ITEM_BOOT_RIGHT = (
    '5665','6776','6556','1221','2321','1221',
    '1221','1221','1221','1221','2321','1111',
)
BOOT_FRONT = ('....',) * 6 + ('5665','6776','1221','2332','3442','1111')
BOOT_BACK = ('....',) * 6 + ('5665','6776','1221','2221','2321','1111')
BOOT_LEFT = ('....',) * 6 + ('5665','6776','1221','2331','3431','1111')
BOOT_RIGHT = ('....',) * 6 + ('5665','6776','1221','1221','2321','1111')
ITEM_FRONT = (
    '..1221..','.1233321','12344321','13433321',
    '13332211','12383211','12233211','12222211',
    '15666651','16777761','12233221','11122111',
)
ITEM_BACK = TORSO_BACK[:8]+('15666651','16777761','12222221','11122111')
ITEM_ARM_FRONT = (
    '1221','2332','3442','6776','1221','2332',
    '2332','1221','1551','5665','5665','1111',
)
ITEM_ARM_BACK = (
    '1221','2332','2332','6776','1221','2221',
    '2221','1221','1551','5665','5665','1111',
)
ITEM_ARM_SIDE = (
    '1121','1231','2331','6776','1221','2221',
    '2221','1221','1551','5665','5665','1111',
)


def _paint(image, origin, rows):
    ox,oy=origin
    for y,row in enumerate(rows):
        for x,char in enumerate(row):
            if char != '.':
                image.putpixel((ox+x,oy+y),tuple(bytes.fromhex(COLORS[int(char)]))+(255,))


def _cube(image, origin, width, depth, left, front, right, back):
    u,v=origin
    for xy,rows in (((u,v+depth),left),((u+depth,v+depth),front),
                    ((u+depth+width,v+depth),right),
                    ((u+2*depth+width,v+depth),back)):
        ox,oy=xy
        for yy in range(len(rows)):
            for xx in range(len(rows[yy])):
                image.putpixel((ox+xx,oy+yy),(0,0,0,0))
        _paint(image,xy,rows)
    _paint(image,(u+depth,v),('2'*width,)*depth)
    _paint(image,(u+depth+width,v),('1'*width,)*depth)


def armor_texture(tier, inner=False):
    if inner:
        image=Image.new('RGBA',(64,32))
        _cube(image,(0,16),4,4,LEG_LEFT,LEG_FRONT,LEG_RIGHT,LEG_BACK)
        return image
    from build_bold_class_armor import armor_texture as base
    image=base('warrior',tier,False)
    _cube(image,(16,16),8,4,TORSO_LEFT,TORSO_FRONT,TORSO_RIGHT,TORSO_BACK)
    _cube(image,(40,16),4,4,ARM_LEFT,ARM_FRONT,ARM_RIGHT,ARM_BACK)
    _cube(image,(0,16),4,4,BOOT_LEFT,BOOT_FRONT,BOOT_RIGHT,BOOT_BACK)
    for i in range(tier-1):
        image.putpixel((24+i,21),tuple(bytes.fromhex(COLORS[8]))+(255,))
    return image


def item_texture(tier, inner=False):
    """A separately painted surface for the 3D item; worn cutouts stay open."""
    image=armor_texture(tier,inner)
    if not inner:
        _cube(image,(16,16),8,4,TORSO_LEFT,ITEM_FRONT,TORSO_RIGHT,ITEM_BACK)
        _cube(image,(40,16),4,4,ITEM_ARM_SIDE,ITEM_ARM_FRONT,
              ITEM_ARM_SIDE,ITEM_ARM_BACK)
        _cube(image,(0,16),4,4,ITEM_BOOT_LEFT,ITEM_BOOT_FRONT,
              ITEM_BOOT_RIGHT,ITEM_BOOT_BACK)
        for i in range(tier-1):
            image.putpixel((24+i,21),tuple(bytes.fromhex(COLORS[8]))+(255,))
    return image
