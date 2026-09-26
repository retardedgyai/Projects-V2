"""ProjectS armor paintings on Minecraft's native 64x32 equipment grid.

Isles inspired the large value groups and high contrast. These are original
pixel arrangements, with each job's own material palette and face design.
"""
from PIL import Image

# Ink, shadow, body, lit plane, glint, textile, accent, fitting, jewel.
ART={
 'warrior':{
  'colors':('263344','354b60','64879b','8faeb9','b9d1d0','4b2f2b','9d6a41','e0b96e','ce653a'),
  'front':('..1221..','.123321.','12333321','12244221','12322321','12333321','11233211','15577551','15677651','12222221','12211221','.123321.'),
  'back': ('..1221..','.123321.','12333321','23333332','23322332','22222222','22222222','12222221','15577551','15677551','12222221','.122221.'),
  'arm':  ('1221','2342','3443','2332','1551','0000','0000','0000','1221','2332','3443','1221'),
  'leg':  ('1551','5775','1221','2332','3442','3332','2332','1221','1111','2332','3443','1221'),
  'boot': ('0000',)*6+('1221','2332','3442','3773','2332','1111')},
 'mage':{
  'colors':('212942','354668','54739a','81aec3','b9d5d0','552f55','c57d67','e3b46b','d45ad9'),
  'front':('.715517.','71222717','12555521','22511522','25588552','25588552','25533552','12533521','66677666','65788756','55577555','.155551.'),
  'back': ('.715517.','71222717','12555521','22555522','25511552','25511552','25511552','12511521','66677666','65577556','55555555','.155551.'),
  'arm': ('7117','7227','2332','0000','0000','0000','0000','0000','6556','6776','5555','1551'),
  'leg': ('5775','7557','1551','2552','2552','2552','2552','2552','6556','6776','2552','1551'),
  'boot':('0000',)*6+('1551','2552','2552','6776','2332','1111')},
 'ranger':{
  'colors':('1c352f','2d5143','4e795a','84a67a','b6c59c','493b2b','996a40','d4b36a','c9a876'),
  'front':('.122221.','12333321','23344332','23644332','23364332','22536332','22533632','12553721','15577551','15777551','12222221','.123321.'),
  'back': ('.122221.','12333321','23333332','23322332','22222222','22522222','22522222','12522221','15577551','15777551','12222221','.122221.'),
  'arm':('1221','2332','3443','2332','1551','0000','0000','0000','0000','0000','0000','0000'),
  'leg':('1551','5775','1221','2332','3442','2332','2332','1551','1221','2332','3443','1221'),
  'boot':('0000',)*6+('1551','2332','3442','5775','2332','1111')},
 'assassin':{
  'colors':('1b202b','313143','54465b','827186','b8a3a8','30272f','813d55','bc8b6a','d05b80'),
  'front':('.122221.','12333321','23344332','23444332','23388332','22388322','22333322','12322321','15577551','15677551','12222221','.122221.'),
  'back': ('.122221.','12333321','23333332','23322332','22322322','22222222','22222222','12322321','15577551','15677551','12222221','.122221.'),
  'arm':('1221','2332','3443','2332','1551','0000','0000','0000','1221','2332','3443','1221'),
  'leg':('1551','5775','1221','2332','3442','2332','2332','1221','1111','2332','3443','1221'),
  'boot':('0000',)*6+('1221','2332','3442','6776','2332','1111')},
 'templar':{
  'colors':('35434b','566972','8a9fa0','bec8bd','eee4c9','39434a','a17a48','e0c27e','dce8d5'),
  'front':('.122221.','12344321','23444432','24444442','24477442','23477432','23477432','12333321','15577551','15777551','12222221','.123321.'),
  'back': ('.122221.','12333321','23344332','23333332','23322332','22222222','22222222','12333321','15577551','15777551','12222221','.122221.'),
  'arm':('1221','2342','3443','2332','2332','0000','0000','1551','1221','2332','3443','1221'),
  'leg':('1551','5775','1221','2342','3443','2332','2332','1221','1111','2342','3443','1221'),
  'boot':('0000',)*6+('1221','2342','3443','5775','2332','1111')},
 'healer':{
  'colors':('4b4b53','777d7b','aaa99c','dbd3b9','f2e7d0','536d66','8bab8e','d8b46e','bde5ce'),
  'front':('.733337.','73344337','33444433','34455443','34588543','34588543','34588543','13433431','15677651','56788765','33377333','.133331.'),
  'back': ('.733337.','73344337','33444433','34444443','34455443','34455443','34455443','13433431','15677651','56788765','33377333','.133331.'),
  'arm':('7337','3443','4554','3443','3553','3553','3553','3553','6776','6776','3333','1331'),
  'leg':('5775','7667','1331','3443','3553','3553','3553','3553','6776','6776','3333','1331'),
  'boot':('0000',)*6+('1331','3443','3553','6776','3333','1111')},
 'starweaver':{
  'colors':('1c2945','2e466a','4c7399','80afc3','bbdadd','34345d','608abd','dbbc76','9bdfd9'),
  'front':('.712217.','71233217','12333321','23344332','23388332','23388332','23344332','12333321','15577551','15788751','12222221','.122221.'),
  'back': ('.712217.','71233217','12333321','23344332','23322332','23322332','23344332','12333321','15577551','15788751','12222221','.122221.'),
  'arm':('7117','2332','3443','2332','2552','2552','2552','2552','2772','2332','3443','1221'),
  'leg':('5775','7557','1221','2332','3443','2332','2332','2332','1771','2332','3443','1221'),
  'boot':('0000',)*6+('1221','2332','3443','7777','2332','1111')},
}

def _place(image,where,rows,colors):
    x0,y0=where
    for y,row in enumerate(rows):
        for x,char in enumerate(row):
            if char!='0' and char!='.':
                image.putpixel((x0+x,y0+y),(*bytes.fromhex(colors[int(char)][1:]),255))

def _sides(front,width,shade):
    return tuple('0'*width if row[0]=='0' and row[-1]=='0' else shade*width
                 for row in front)

def _silhouette(rows,job,part):
    """Cut skin-revealing openings and hems into the painted equipment layers."""
    cut=set()
    if part in ('front','back'):
        cut.update((x,0) for x in (3,4))
        if job=='mage' and part=='front':
            cut.update((x,y) for y in (4,5,6) for x in (3,4))
            cut.update((x,11) for x in (3,4))
        if job in ('ranger','starweaver'):
            cut.update((x,y) for y in range(9,12) for x in (0,1,6,7))
        if job=='healer':
            cut.update((x,y) for y in (10,11) for x in (0,7))
        if job=='assassin':
            cut.update((x,y) for y in (9,10,11) for x in (0,7))
    elif part=='arm':
        if job=='warrior':
            cut.update((x,y) for y in (4,5,6,7) for x in (1,2))
            cut.update((x,y) for y in (10,11) for x in (0,3))
        elif job=='ranger':
            cut.update((x,y) for y in (4,5,6) for x in (3,))
            cut.update((x,y) for y in (7,8,9,10,11) for x in range(4))
        elif job=='assassin':
            cut.update((x,y) for y in (4,5,6) for x in (2,3))
        elif job=='mage':
            cut.update((x,y) for y in (8,9,10) for x in (3,))
        elif job=='healer':
            cut.update((x,y) for y in (9,10) for x in (3,))
    elif part=='leg':
        if job=='warrior':
            cut.update((x,y) for y in (0,1,8,9,10,11) for x in range(4))
            cut.update((x,7) for x in (0,3))
        elif job=='ranger':
            cut.update((x,y) for y in range(8,12) for x in range(4))
            cut.update((3,y) for y in (5,6,7))
        elif job=='assassin':
            cut.update((x,y) for y in range(8,12) for x in range(4))
        elif job=='templar':
            cut.update((x,y) for y in range(8,12) for x in range(4))
        else:
            cut.update((x,y) for y in range(7,12) for x in range(4))
    elif part=='boot':
        if job in ('ranger','starweaver'):
            cut.update((x,6) for x in range(4))
            cut.update((x,7) for x in (0,3))
        elif job=='mage':
            cut.update((3,7))
    return tuple(''.join('0' if (x,y) in cut else c for x,c in enumerate(row))
                 for y,row in enumerate(rows))

def _cube(image,origin,width,depth,front,back,colors,side_color='1'):
    u,v=origin
    side=_sides(front,depth,side_color)
    for location,rows in (((u,v+depth),side),((u+depth,v+depth),front),
                          ((u+depth+width,v+depth),side),
                          ((u+2*depth+width,v+depth),back)):
        _place(image,location,rows,colors)
    _place(image,(u+depth,v),('2'*width,)*depth,colors)
    _place(image,(u+depth+width,v),('1'*width,)*depth,colors)

def armor_texture(job,tier,inner=False):
    art=ART[job]
    colors=tuple('#'+c for c in art['colors'])
    image=Image.new('RGBA',(64,32),(0,0,0,0))
    if inner:
        leg=_silhouette(art['leg'],job,'leg')
        _cube(image,(0,16),4,4,leg,leg,colors)
        return image
    front=list(_silhouette(art['front'],job,'front'))
    if tier>1:
        x=min(tier,6)
        front[2]=front[2][:x]+'7'+front[2][x+1:]
    _cube(image,(16,16),8,4,tuple(front),_silhouette(art['back'],job,'back'),colors)
    arm=_silhouette(art['arm'],job,'arm')
    _cube(image,(40,16),4,4,arm,arm,colors)
    boot=_silhouette(art['boot'],job,'boot')
    _cube(image,(0,16),4,4,boot,boot,colors)
    # Boots occupy only the lower half of the leg; let leggings show above.
    for x in range(16):
        for y in range(16,26): image.putpixel((x,y),(0,0,0,0))
    return image
