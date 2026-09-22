"""Orthographic QA of exported real Minestom blocks / authored native boss parts.

Not a Minecraft screenshot: full cubes approximate stairs, fences and lanterns.
Run HarborSceneTest + WardenModelTest first. No live server/client is used.
"""
from pathlib import Path
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
REPORT = ROOT / 'server-minestom/build/reports'
OUT = ROOT / 'assets/harbor-ascension'

def color(name):
    for token, rgb in (
        ('lantern',(243,196,99)), ('shroomlight',(239,154,60)), ('gold',(208,166,64)),
        ('blue_ice',(99,169,202)), ('blue_wool',(53,82,137)), ('cyan_wool',(58,119,132)),
        ('orange_wool',(188,110,60)), ('red_wool',(156,60,46)), ('white_wool',(221,212,185)),
        ('stained_glass',(88,133,142)), ('copper',(171,103,70)), ('deepslate',(56,65,73)),
        ('stone_bricks',(136,140,128)), ('mud',(156,124,85)), ('bricks',(150,82,58)), ('calcite',(215,209,178)),
        ('sandstone',(192,172,128)), ('sand',(199,184,141)), ('leaves',(67,113,62)),
        ('moss',(91,117,55)), ('grass',(108,138,74)), ('dirt',(112,89,59)),
        ('dark_oak',(72,53,35)), ('spruce',(119,85,48)), ('oak',(142,111,69)),
        ('andesite',(135,139,135)), ('stone',(128,133,128)), ('campfire',(205,117,44)),
        ('chain',(70,77,81)), ('quartz',(226,221,202))):
        if token in name: return rgb
    return (136,127,108)

def draw_cubes(cubes, output, size, scale, origin, title):
    canvas = Image.new('RGB',size,(34,72,85)); pen=ImageDraw.Draw(canvas)
    def project(x,y,z): return (origin[0]+scale*(x-.5*z), origin[1]+scale*(.24*x+.64*z-y))
    for x,y,z,w,h,d,name in sorted(cubes,key=lambda c:c[0]*.5+c[2]+c[1]*.12):
        rgb=color(name)
        faces=[([(x,y,z+d),(x+w,y,z+d),(x+w,y+h,z+d),(x,y+h,z+d)],.77),
               ([(x+w,y,z),(x+w,y,z+d),(x+w,y+h,z+d),(x+w,y+h,z)],.59),
               ([(x,y+h,z),(x+w,y+h,z),(x+w,y+h,z+d),(x,y+h,z+d)],1.0)]
        for points,shade in faces:
            pen.polygon([project(*p) for p in points],fill=tuple(int(n*shade) for n in rgb))
    pen.rectangle((18,16,size[0]-18,56),fill=(27,36,40))
    pen.text((30,29),title,fill=(230,218,185))
    output.parent.mkdir(parents=True,exist_ok=True); canvas.save(output)
    print(output)

def main():
    cubes=[]
    for line in (REPORT/'harbor-blocks.tsv').read_text().splitlines():
        x,y,z,name=line.split('\t'); cubes.append((int(x),int(y)-38,int(z),1,1,1,name))
    draw_cubes(cubes,OUT/'harbor-preview.png',(1300,920),7.6,(570,565),'HARBOR / actual generated block export - orthographic QA (not an in-game screenshot)')
    parts=[]
    for line in (REPORT/'warden-parts.tsv').read_text().splitlines():
        *numbers,name=line.split('\t'); x,y,z,w,h,d=map(float,numbers)
        parts.append((x-w/2,y-h/2,z-d/2,w,h,d,name))
    draw_cubes(parts,OUT/'warden-preview.png',(700,760),150,(355,659),'FORGE SENTINEL / 22 articulated native display parts')

if __name__ == '__main__': main()
