"""Compose unchanged accepted glyphs into padded HUD cells. No repainting or resampling."""
import json
from PIL import Image
from build_approved_dash_v3 import PACK

TEXT = '反撃の好機防御秒0123456789'


def build(assets, write):
    mapping={}
    for line in (assets/'menu/glyphs-emphasis.tsv').read_text(encoding='utf-8').splitlines():
        if line and not line.startswith('#'):
            code,glyph,width=line.split('\t')
            mapping[chr(int(code,16))]=(chr(int(glyph,16)),int(width))
    providers=json.loads((assets/'font/core_menu_emphasis_y30.json').read_text(encoding='utf-8'))['providers']
    bitmap=next(p for p in providers if p['type']=='bitmap')
    source=Image.open(assets/('textures/'+bitmap['file'].split(':')[1])).convert('RGBA')
    cw,ch=source.width//len(bitmap['chars'][0]),source.height//len(bitmap['chars'])
    assert ch%bitmap['height']==0
    ratio=ch//bitmap['height']
    # Vanilla requires ascent <= height. Pad beneath the 14px ink rather than
    # asking for an invalid 45-ascent/14-height provider or scaling the lettering.
    chars=''.join(dict.fromkeys(TEXT))
    sheet=Image.new('RGBA',(cw*len(chars),48*ratio))
    codes=[]
    for i,c in enumerate(chars):
        glyph,_=mapping[c]
        row=next(y for y,r in enumerate(bitmap['chars']) if glyph in r)
        column=bitmap['chars'][row].index(glyph)
        cell=source.crop((column*cw,row*ch,(column+1)*cw,(row+1)*ch))
        sheet.alpha_composite(cell,(i*cw,0));codes.append(glyph)
    sheet.save(assets/'textures/gui/core/warrior_status_text.png',compress_level=9)
    space,width=mapping[' ']
    write(assets/'font/warrior_hud_status.json',{'providers':[
        {'type':'space','advances':{space:width}},
        {'type':'bitmap','file':'projects:gui/core/warrior_status_text.png','height':48,'ascent':45,'chars':[''.join(codes)]}]})


if __name__=='__main__':
    def write(path,value):
        path.write_text(json.dumps(value,separators=(',',':'))+'\n',encoding='utf-8')
    build(PACK/'assets/projects',write)
    (PACK/'index.txt').write_text('\n'.join(sorted(p.relative_to(PACK).as_posix()
        for p in PACK.rglob('*') if p.is_file() and p.name!='index.txt'))+'\n',encoding='utf-8')
