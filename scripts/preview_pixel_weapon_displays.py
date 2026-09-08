"""Saved model/UV/display QA at GUI scales 2 and 4, not a game screenshot."""
import json
from pathlib import Path
import numpy as np
from PIL import Image, ImageDraw
from build_pixel_armament_pack import ASSETS as FIRST
from build_specialist_armament_pack import ASSETS as EXTRA
from pixel_weapon_display import transformed
from preview_class_armaments import render_model, FONT

ROOT = Path(__file__).resolve().parents[1]
WEAPONS = [('greatsword','大剣'),('dagger','短剣'),('staff','魔杖'),('bow','長弓'),
           ('mace','戦槌'),('tome','聖典'),('astrolabe','星環杖')]


def main():
    sheet = Image.new('RGB',(7*160,420),'#171b20')
    draw = ImageDraw.Draw(sheet)
    draw.text((10,8),'保存済みJSON・PNG・表示変換の確認 / Minecraft画面ではありません',font=FONT,fill='#ded7ca')
    draw.text((10,31),'上: GUI倍率2相当 / 下: GUI倍率4相当（どちらも最近傍で拡大して比較）',font=FONT,fill='#a8adb2')
    for column,(key,label) in enumerate(WEAPONS):
        assets = FIRST if key in ('greatsword','dagger','staff') else EXTRA
        model = json.loads((assets/f'models/item/weapons/pixel_{key}.json').read_text())
        textures = {part:np.asarray(Image.open(assets/f'textures/{resource.split(":")[1]}.png').convert('RGBA'))
                    for part,resource in model['textures'].items() if part!='particle'}
        draw.text((column*160+16,60),label,font=FONT,fill='#ded7ca')
        for row,multiplier in enumerate((2,4)):
            size = 16*multiplier
            def project(v):
                x,y,z = transformed(v,model['display']['gui'])
                return np.array([size/2+x*multiplier,size/2-y*multiplier,z])
            rendered = render_model(model,textures,size=(size,size),projector=project)
            enlarged = rendered.resize((128,128),Image.Resampling.NEAREST)
            location = (column*160+16,86+row*160)
            sheet.paste(enlarged,location)
            draw.rectangle((location[0]-1,location[1]-1,location[0]+128,location[1]+128),outline='#686461')
    path = ROOT/'.tools/weapon-playtest-resources/display-review.png'
    path.parent.mkdir(parents=True,exist_ok=True)
    sheet.save(path)
    print(path)


if __name__=='__main__': main()
