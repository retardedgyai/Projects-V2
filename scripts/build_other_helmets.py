"""Distinct class silhouettes for ProjectS helmets beyond the forge warrior."""
from class_armament_geometry import box

SWATCH={
    'body':[.5,8.5,1.5,9.5],
    'light':[2.5,8.5,3.5,9.5],
    'accent':[4.5,8.5,5.5,9.5],
    'shadow':[6.5,8.5,7.5,9.5],
    'hot':[8.5,8.5,9.5,9.5],
    'edge':[10.5,8.5,11.5,9.5],
}


def other_helmet_elements(job,tier):
    if job not in ('ranger','mage','assassin','templar','healer','starweaver'):
        raise ValueError(job)
    elements=[]
    def add(name,lo,hi,paint,angle=0,axis='z'):
        part=box(name,lo,hi,'iron',angle,axis)
        part['faces']={face:{'uv':SWATCH[paint],'texture':'#helm'}
                       for face in part['faces']}
        elements.append(part)
    # A thin, transparent painting carries the role-specific pixel art. Each
    # class builds its own crown, cheeks and back around it; there is no common
    # cuboid shell that makes seven recoloured copies of one helmet.
    faceplate=box('class face painting',[3.2,3,2.9],[12.8,12.4,3.02],'iron')
    faceplate['faces']={'north':{'uv':[0,0,4,8],'texture':'#helm'}}
    elements.append(faceplate)
    for name,lo,hi,face,uv in (
        ('left painted side',[3.04,3,3.2],[3.16,12.4,12.8],'west',[8,0,12,8]),
        ('right painted side',[12.84,3,3.2],[12.96,12.4,12.8],'east',[12,0,16,8]),
        ('painted back',[3.2,3,12.84],[12.8,12.4,12.96],'south',[4,0,8,8])):
        panel=box(name,lo,hi,'iron')
        panel['faces']={face:{'uv':uv,'texture':'#helm'}}
        elements.append(panel)
    if job=='ranger':
        add('moss hood top',[4.2,10.4,4],[11.8,13.0,11.9],'shadow')
        add('moss hood back',[4,6.2,9],[12,11.5,12],'body')
        add('left leaf stack',[3.3,11.5,6],[5.1,14.9,10.5],'body',-22.5)
        add('right leaf stack',[10.9,11.0,6],[12.5,14.2,10.5],'body',22.5)
        add('high swept bronze visor',[2.1,9.5,2.5],[13.4,10.8,4.7],'accent',-22.5)
        add('visor sharpened lip',[4.1,10.6,2.4],[12.5,11.0,3.2],'hot',-22.5)
        add('left hanging hood',[3.3,3.5,3.6],[5.4,9.5,9.8],'body')
        add('right hanging hood',[10.6,3.5,3.6],[12.7,9.5,9.8],'shadow')
        add('left bronze joint',[2.9,8.2,6.2],[3.5,8.9,7.4],'accent')
        add('right bronze joint',[12.5,8.2,6.2],[13.1,8.9,7.4],'accent')
    elif job=='mage':
        add('deep hood back',[4,5.0,8.2],[12,13.5,12.7],'shadow')
        add('high folded hood',[5.1,11.6,4.7],[10.8,15.8,11.5],'body',22.5)
        add('left hood wing',[2.9,4.1,3.8],[5.3,11.7,10.2],'body')
        add('right hood wing',[10.7,4.1,3.8],[13.1,11.7,10.2],'body')
        add('left silver arc',[3.2,8.7,2.45],[6.3,10.1,4.4],'edge',-22.5)
        add('right silver arc',[9.7,8.7,2.45],[12.8,10.1,4.4],'edge',22.5)
        add('amethyst bezel',[6.6,9.5,2.3],[9.4,12.3,3.2],'edge',45)
        add('amethyst inset',[7.25,10.1,1.95],[8.75,11.7,2.45],'accent',45)
        add('silver lower V left',[5.0,6.9,2.6],[8.1,7.6,3.3],'light',22.5)
        add('silver lower V right',[7.9,6.9,2.6],[11,7.6,3.3],'light',-22.5)
    elif job=='assassin':
        add('low charcoal cowl',[3.1,10.2,3.3],[12.9,12.9,12.5],'shadow')
        add('falling rear hood',[3.9,4.0,8],[12.1,10.9,12.7],'body')
        add('forward cowl lip',[2.7,9.2,2.3],[13.3,10.6,4.6],'shadow')
        add('angled left slit edge',[3.9,8.2,2.4],[8.2,8.7,3.2],'light',-22.5)
        add('angled right slit edge',[7.8,8.2,2.4],[12.1,8.7,3.2],'light',22.5)
        add('wrapped lower face',[4.0,4.4,2.7],[12.0,7.8,4.5],'body')
        add('left cowl drape',[3.3,3.0,4],[5.0,9,9.8],'shadow')
        add('right cowl drape',[11.0,3.0,4],[12.7,9,9.8],'shadow')
        add('left clasp',[2.9,8.4,6],[3.5,9,7.2],'accent')
    elif job=='templar':
        add('ivory shield crown',[3.5,9.7,3.6],[12.5,13.1,12.3],'body')
        add('rear neck shield',[3.5,4.0,10.1],[12.5,10.0,12.4],'shadow')
        add('central oath ridge',[7.3,11.8,3.2],[8.7,15.5,11.7],'accent')
        add('wide gold visor',[2.6,8.9,2.4],[13.4,10.0,4.5],'accent')
        add('visor upper lit edge',[3.1,10,2.5],[12.9,10.4,3.2],'hot')
        add('left shield cheek',[2.8,2.9,3.4],[5.1,8.8,9.6],'light')
        add('right shield cheek',[10.9,2.9,3.4],[13.2,8.8,9.6],'light')
        add('left inset panel',[3.0,4.5,2.9],[4.0,7.8,3.5],'shadow')
        add('right inset panel',[12.0,4.5,2.9],[13.0,7.8,3.5],'shadow')
    elif job=='healer':
        add('mitre base',[4.2,9.7,4],[11.8,12.7,11.8],'body')
        add('left mitre peak',[4.3,11.7,5],[7.6,16,10.5],'light',-22.5)
        add('right mitre peak',[8.4,11.7,5],[11.7,16,10.5],'light',22.5)
        add('rear soft hood',[4.1,4.2,8.9],[11.9,10,12.2],'shadow')
        add('mint brow',[3.4,9.0,2.7],[12.6,9.9,4.2],'accent')
        add('left prayer cloth',[3.6,3.1,3.7],[5.4,8.9,8.9],'body')
        add('right prayer cloth',[10.6,3.1,3.7],[12.4,8.9,8.9],'body')
        add('small prayer seal',[7.3,10.1,2.35],[8.7,11.7,2.9],'hot')
    else:
        add('astral cap',[3.8,9.8,4],[12.2,12.8,12.1],'shadow')
        add('rear night mantle',[3.6,4,9.3],[12.4,10.5,12.4],'body')
        add('left star prong',[3.5,11.1,4.5],[5.3,16,7.8],'light',-22.5)
        add('right star prong',[10.7,11.1,4.5],[12.5,16,7.8],'light',22.5)
        add('crystal brow',[3.0,8.9,2.7],[13.0,9.8,4.2],'light')
        add('left star cheek',[3.2,3,3.5],[5.2,8.7,8.7],'body')
        add('right star cheek',[10.8,3,3.5],[12.8,8.7,8.7],'body')
        add('star socket',[6.8,10.2,2.45],[9.2,12.6,3.1],'edge',45)
        add('star glass',[7.3,10.7,2.15],[8.7,12,2.55],'accent',45)
    return elements
