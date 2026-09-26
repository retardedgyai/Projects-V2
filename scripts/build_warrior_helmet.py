"""ProjectS forge-warrior helmet: independently modelled silhouette and plates.

This model follows the approved ProjectS concept: a tall blue crown, ruby
mount, uninterrupted dark eye aperture, and two separated jaw guards. It does
not use any geometry, UV mapping, or pixels from the user's reference pack.
"""
from PIL import ImageDraw
from class_armament_geometry import box

TILES={
    'blue':[0,8,2,12], 'light':[2,8,4,12],
    'ruby':[4,8,6,12], 'shadow':[6,8,8,12],
    'gold':[8,8,10,12], 'silver':[10,8,12,12],
    'black':[12,8,14,12], 'cap':[0,12,2,16],
    'jaw':[2,12,4,16], 'lower':[4,12,6,16],
    'rear':[6,12,8,16], 'crest':[8,12,10,16],
}


def forge_elements():
    elements=[]
    def add(name,lo,hi,paint,angle=0,axis='z'):
        part=box(name,lo,hi,'iron',angle,axis)
        tile=TILES[paint]
        dx,dy,dz=[hi[i]-lo[i] for i in range(3)]
        sizes={'north':(dx,dy),'south':(dx,dy),'east':(dz,dy),
               'west':(dz,dy),'up':(dx,dz),'down':(dx,dz)}
        part['faces']={}
        for face,(width,height) in sizes.items():
            du=min(1.75,width/8); dv=min(3.5,height/4)
            uc=(tile[0]+tile[2])/2; vc=(tile[1]+tile[3])/2
            part['faces'][face]={
                'uv':[uc-du/2,vc-dv/2,uc+du/2,vc+dv/2],
                'texture':'#helm'}
        elements.append(part)
    add('rear crown wall',[3.7,9.5,7.5],[12.3,12.7,12.2],'rear')
    add('left crown plate',[3.9,10.4,3.9],[7.5,12.9,11.7],'cap')
    add('right crown plate',[8.5,10.4,3.9],[12.1,12.9,11.7],'cap')
    add('crown spine',[7.1,12.1,4.4],[8.9,14.7,11.8],'crest')
    add('rising rear fin',[6.9,13.4,9.5],[9.1,16.0,12.2],'crest',-22.5,'x')
    add('fin cut edge',[7.05,14.5,9.1],[7.5,16.0,11.6],'light',-22.5,'x')
    add('recessed eye opening',[4.9,6.7,3.7],[11.1,9.3,4.2],'black')
    add('continuous silver brow',[3.2,9.15,2.9],[12.8,10.4,4.1],'silver')
    add('brow lower shade',[3.8,8.75,2.9],[12.2,9.2,4.1],'shadow')
    add('left temple sweep',[2.8,8.7,3.4],[5.0,11.0,7.5],'light',-22.5)
    add('right temple sweep',[11.0,8.7,3.4],[13.2,11.0,7.5],'light',22.5)
    add('ruby mount',[6.7,10.35,2.5],[9.3,12.5,3.4],'silver',45)
    add('ruby inset',[7.25,10.8,2.15],[8.75,12.0,2.65],'ruby',45)
    add('ruby glint',[7.55,11.45,1.95],[7.95,11.85,2.3],'gold')
    add('left upper jaw',[2.8,5.7,3.2],[5.8,8.9,9.1],'jaw')
    add('right upper jaw',[10.2,5.7,3.2],[13.2,8.9,9.1],'jaw')
    add('left lower jaw',[3.1,2.5,3.2],[5.2,6.0,8.7],'lower',-22.5)
    add('right lower jaw',[10.8,2.5,3.2],[12.9,6.0,8.7],'lower',22.5)
    add('left bright cheek rim',[2.65,3.0,2.8],[3.35,8.8,4.2],'silver',-22.5)
    add('right bright cheek rim',[12.65,3.0,2.8],[13.35,8.8,4.2],'silver',22.5)
    add('left jaw hem',[3.15,2.5,3.05],[5.3,3.15,8.8],'light',-22.5)
    add('right jaw hem',[10.7,2.5,3.05],[12.85,3.15,8.8],'light',22.5)
    add('left ear socket',[2.55,7.0,6.9],[3.05,9.0,9.1],'shadow')
    add('right ear socket',[12.95,7.0,6.9],[13.45,9.0,9.1],'shadow')
    add('left ear pin',[2.35,7.9,7.6],[2.75,8.3,8.1],'gold')
    add('right ear pin',[13.25,7.9,7.6],[13.65,8.3,8.1],'gold')
    return elements


def forge_texture(image):
    """Paint material-specific planes instead of repeating one noise tile."""
    d=ImageDraw.Draw(image)
    d.rectangle((96,32,111,47),fill='#101722')
    d.rectangle((0,48,15,63),fill='#294b6d')
    d.polygon([(0,48),(10,48),(15,52),(15,55),(5,53),(0,51)],fill='#476f94')
    d.line([(1,49),(9,49),(13,52)],fill='#8db3c7')
    d.polygon([(9,55),(15,54),(15,63),(5,63)],fill='#1b344f')
    d.line([(1,57),(11,57)],fill='#345c7f')
    d.rectangle((16,48,31,63),fill='#315879')
    d.polygon([(16,48),(29,48),(31,51),(27,54),(16,53)],fill='#6893af')
    d.line([(17,49),(27,49),(30,51)],fill='#bed5db')
    d.polygon([(25,54),(31,52),(31,63),(19,63)],fill='#203d5d')
    d.line([(18,58),(25,56),(29,56)],fill='#456e8d')
    d.rectangle((32,48,47,63),fill='#1e3a59')
    d.polygon([(32,49),(44,48),(47,51),(47,55),(35,53)],fill='#3f6788')
    d.line([(33,50),(42,49)],fill='#6e9db4')
    d.polygon([(38,57),(47,54),(47,63),(32,63)],fill='#142c48')
    d.line([(33,60),(43,60)],fill='#567d9a')
    d.rectangle((48,48,63,63),fill='#172d48')
    d.rectangle((49,49,61,52),fill='#305575')
    d.line([(49,49),(58,49)],fill='#6e9db4')
    d.line([(49,55),(63,55)],fill='#355a77')
    d.line([(49,60),(63,60)],fill='#101f32')
    d.rectangle((64,48,79,63),fill='#1b3551')
    d.polygon([(64,49),(71,48),(79,51),(79,54),(70,53)],fill='#345e84')
    d.line([(66,49),(74,49)],fill='#8aafc1')
    d.line([(70,54),(70,62)],fill='#759cb1')
    return image
