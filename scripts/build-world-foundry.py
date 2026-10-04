"""Original hand-authored ProjectS pixel equipment. Geometry is shared by pack and offline preview."""
from pathlib import Path
import json, hashlib
from PIL import Image

ROOT=Path(__file__).resolve().parents[1]
PACK=ROOT/'server-minestom/src/main/resources/core-ui-pack'
generated=[]
def put(path,value):
    p=PACK/path;p.parent.mkdir(parents=True,exist_ok=True)
    p.write_text(json.dumps(value,ensure_ascii=False,indent=2)+'\n',encoding='utf8');generated.append(path)
def texture(name,base,seams=False):
    im=Image.new('RGBA',(16,16))
    for y in range(16):
        for x in range(16):
            noise=((x*7+y*11+x*y)%7-3)*2
            if seams:
                # Fireclay flecks and small chips; avoid long vertical grain that reads as wood.
                noise=((x*13+y*17+x*y*3)%11-5)*2
                if (x*19+y*7)%53==0:noise=-18
            im.putpixel((x,y),tuple(max(0,min(255,v+noise)) for v in base)+(255,))
    path=f'assets/projects/textures/foundry/{name}.png';p=PACK/path;p.parent.mkdir(parents=True,exist_ok=True);im.save(p);generated.append(path)
for n,c in {'clay':(109,103,94),'iron':(70,79,83),'dark':(27,31,33),'sand':(194,162,112),'wood':(130,85,48),'metal':(168,178,180),'ember':(245,117,32),'liquid':(255,155,49)}.items():texture(n,c,n=='clay')
for i in range(1,6):
    t=(i-1)/4
    texture('cool'+str(i),tuple(round(a+(b-a)*t) for a,b in zip((168,178,180),(255,155,49))))
def box(a,b,tex,light=0):
    e={'from':a,'to':b,'faces':{f:{'texture':'#'+tex,'uv':[0,0,16,16]} for f in ['north','south','east','west','up','down']}}
    if light:e.update(light_emission=light,shade=False)
    return e
def model(name,elements):
    refs={n:'projects:foundry/'+n for n in ['clay','iron','dark','sand','wood','metal','ember','liquid']+[f'cool{i}' for i in range(1,6)]}
    put(f'assets/projects/models/foundry/{name}.json',{'textures':{**refs,'particle':refs['clay']},'elements':elements})
    put(f'assets/projects/items/foundry/{name}.json',{'model':{'type':'minecraft:model','model':'projects:foundry/'+name}})
# Open refractory trough, narrow iron bands and a warm fire mouth; visually separate material functions.
heater=[box([1,0,1],[15,2,15],'iron'),box([1,2,1],[4,13,15],'clay'),box([12,2,1],[15,13,15],'clay'),box([4,2,6],[12,13,15],'clay'),box([4,2,5.8],[12,10,6],'dark'),box([4,10,1],[12,13,6],'clay'),box([1,13,1],[15,15,15],'iron')]
for x in [3,6,9,12]:heater.append(box([x,15,2],[x+1,16,14],'dark'))
model('heater',heater)
melter=[box([1,0,1],[15,3,15],'clay')]
for a,b in [([1,3,1],[3,14,15]),([13,3,1],[15,14,15]),([3,3,1],[13,14,3]),([3,3,13],[13,14,15])]:melter.append(box(a,b,'clay'))
for a,b in [([.8,5,.8],[3.2,6,15.2]),([12.8,5,.8],[15.2,6,15.2]),([3,5,.8],[13,6,3.2]),([3,5,12.8],[13,6,15.2])]:melter.append(box(a,b,'iron'))
for x in [1,13]:
    for z in [1,13]:melter.append(box([x,13,z],[x+2,16,z+2],'iron'))
# Half-height open vessel: a player standing on the workshop floor can look into it.
for e in melter:
    for p in ['from','to']:e[p][1]*=.5
model('melter',melter)
# Default orientation: inlet at south edge, outlet centered over the table below.
faucet=[box([6,8,13],[10,12,16],'iron'),box([6.5,8.5,7],[9.5,11.5,14],'iron'),box([6.5,5.5,7],[9.5,9,10],'iron'),box([7,12,12],[9,15,14],'iron'),box([4,14,12],[12,15.5,14],'wood'),box([7,5.4,7.5],[9,5.6,9.5],'dark')]
for e in faucet:
    for p in ['from','to']:e[p][1]*=.7
model('faucet',faucet)
table=[]
for x in [2,12]:
    for z in [2,12]:table.extend([box([x,0,z],[x+2,11,z+2],'iron'),box([x-1,0,z-1],[x+3,1,z+3],'iron')])
table.extend([box([1,10,1],[15,12,15],'iron'),box([1,12,1],[3,16,15],'clay'),box([13,12,1],[15,16,15],'clay'),box([3,12,1],[13,16,3],'clay'),box([3,12,13],[13,16,15],'clay')])
model('table',table)
model('heat',[box([4.2,2.5,2],[11.8,4,5.5],'ember',12)])
model('input',[box([5,2,5],[10,3.5,9],'metal'),box([6,3.5,6],[11,5,10],'metal')])
for units in range(1,13):model('liquid_'+str(units),[box([3.05,1.55,3.05],[12.95,1.6+units*.42,12.95],'liquid',12)])
def part(shape,tex,y=14.9,height=.45,light=0):
    a,b=([7, y,4],[9,y+height,12]) if shape=='rod' else ([4,y,5],[12,y+height,11])
    return [box(a,b,tex,light)]
sand=[box([3.1,12,3.1],[12.9,14.8,12.9],'sand')]
model('sand',sand)
for shape in ['rod','plate']:
    model('pattern_'+shape,sand+part(shape,'wood',14.9,1.5))
    model('mold_'+shape,sand+part(shape,'dark'))
    model('pour_'+shape,sand+part(shape,'liquid',light=12))
    for seconds in range(1,6):model(f'cool_{shape}_{seconds}',sand+part(shape,'cool'+str(seconds),light=seconds*2))
    # Spent sand crumbles and leaves a clean part on the dark bed. Sand charge is consumed once per cast.
    model('part_'+shape,[box([3.1,12,3.1],[12.9,13,12.9],'dark')]+part(shape,'metal',13.1,1.2))
model('stream',[box([7.15,15,7.65],[8.85,19.85,9.35],'liquid',15)])
index=PACK/'index.txt';lines=[x for x in index.read_text(encoding='utf8').splitlines() if x and '/foundry/' not in x]
index.write_text('\n'.join(lines+generated)+'\n',encoding='utf8')
print(f'Generated {len(generated)} original foundry assets')
