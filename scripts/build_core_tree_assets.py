"""Native title-font connectors for the server's actual 18-node dependency graph.

This draws UI routing lines, never ability artwork. White masks are tinted by learned state.
"""
from pathlib import Path
import json
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'server-minestom/src/main/resources/core-ui-pack/assets/projects'

def edges():
    result=[]
    for b in range(3):
        start, a, c, da, dc, key = 10+b*3, 18+b*3, 20+b*3, 27+b*3, 29+b*3, 37+b*3
        result += [(4,start),(start,a),(start,c),(a,da),(c,dc),(da,key),(dc,key)]
    return result

def build_tree():
    providers=[]
    for source,target in edges():
        tile=Image.new('RGBA',(162,90))
        draw=ImageDraw.Draw(tile)
        x1,y1=source%9*18+8,source//9*18+8
        x2,y2=target%9*18+8,target//9*18+8
        middle=(y1+y2)//2
        draw.line([(x1,y1),(x1,middle),(x2,middle),(x2,y2)],fill='white',width=1)
        # Rightmost visible pixel makes bitmap advance deterministic (162 + 1).
        tile.putpixel((161,89),(255,255,255,1))
        path=ASSETS/f'textures/gui/core/tree_{source}_{target}.png'
        path.parent.mkdir(parents=True,exist_ok=True)
        tile.save(path)
        providers.append({'type':'bitmap','file':f'projects:gui/core/tree_{source}_{target}.png',
                          'height':90,'ascent':-5,'chars':[chr(0xE700+source*45+target)]})
    path=ASSETS/'font/core_menu_tree.json'
    path.write_text(json.dumps({'providers':providers},indent=2)+'\n',encoding='utf-8')

if __name__=='__main__': build_tree()
