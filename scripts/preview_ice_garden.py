"""Small native-model contact sheet and standalone 30-second comparison. No game recording."""
import json
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont
from preview_skill_choreography import render, model_for, texture_for
from preview_core_combat_models import COLORS

ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'.tools/ice-garden-review'
PACK=ROOT/'server-minestom/src/main/resources/core-ui-pack/assets/projects'

def main():
    source=json.loads((ROOT/'.tools/ice-garden-poses.json').read_text(encoding='utf-8'))
    OUT.mkdir(parents=True,exist_ok=True)
    # Twelve small projections, using shipped model faces and exact pixel UVs.
    sheet=Image.new('RGB',(1080,850),'#1b222a');d=ImageDraw.Draw(sheet)
    font=ImageFont.truetype('C:/Windows/Fonts/meiryob.ttc',15)
    d.text((12,8),'氷の庭 / 実装ポーズとパックのCPU投影。実機録画・手触り合格ではありません。',font=font,fill='#e9eadb')
    ticks=[0,14,50,106,126,136]
    for row,key in enumerate(['old','new']):
        for col,tick in enumerate(ticks):
            view='iso'
            panel=render(source['frames'][tick][key],('現行main' if key=='old' else 'リワーク')+f' / {tick/20:.1f}秒',tick,view,world_scale=34)
            panel=panel.convert('RGB')
            # Six frames across two rows of three; old above new.
            x=(col%3)*360;y=30+(row*2+col//3)*200
            sheet.paste(panel.resize((360,200)),(x,y))
    sheet.save(OUT/'ice-garden-contact-sheet.png')
    models={};textures={}
    for parts in [frame['old']+frame['new'] for frame in source['frames']] + source['contacts']:
        for part in parts:
            key=part['model']
            if key in models:continue
            model,tints=model_for(key);models[key]={'data':model,'tints':tints}
            for name in model['textures'].values():
                if name.startswith('minecraft:'):continue
                if name not in textures:
                    im=Image.open(PACK/f'textures/{name.split(":")[1]}.png').convert('RGBA')
                    textures[name]={'w':im.width,'h':im.height,'data':list(im.getdata())}
    payload={'frames':source['frames'],'contacts':source['contacts'],'models':models,'textures':textures,'colors':COLORS}
    template=Path(__file__).with_name('ice_garden_preview.html').read_text(encoding='utf-8')
    (OUT/'ice-garden-30s-preview.html').write_text(template.replace('/*PAYLOAD*/',json.dumps(payload,separators=(',',':'))),encoding='utf-8')
    print('12 native projections and one self-contained 30s preview: '+str(OUT))

if __name__=='__main__':main()
