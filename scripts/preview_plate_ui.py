"""Inspect shipped context dispatch, atlas and icons rather than study outputs."""
import json
from pathlib import Path
from PIL import Image, ImageDraw
from class_armament_geometry import ASSETS
from plate_armor_ui import render_ui_model
from preview_class_armaments import FONT

OUT=Path(__file__).resolve().parents[1]/'.tools/armor-review/plate-ui-integrated'


def main():
    OUT.mkdir(parents=True,exist_ok=True)
    board=Image.new('RGB',(1000,370),'#171e22')
    draw=ImageDraw.Draw(board)
    draw.text((18,12),'工房・インベントリ用プレート / 実リソースの表示確認（ゲーム画面ではありません）',font=FONT,fill='#e8dfc9')
    for i,(slot,title) in enumerate((('helmet','頭'),('chestplate','胴'),('leggings','脚'),('boots','足'))):
        item=json.loads((ASSETS/f'items/armor/warrior_t1_{slot}.json').read_text())['model']
        fixed=next(c['model']['model'] for c in item['cases'] if 'fixed' in c['when'])
        model=json.loads((ASSETS/f'models/{fixed.split(":",1)[1]}.json').read_text())
        atlas=Image.open(ASSETS/f'textures/{model["textures"]["plate"].split(":",1)[1]}.png').convert('RGBA')
        hero=render_ui_model(model,atlas,192,'fixed')
        board.paste(hero,(i*250+29,55),hero)
        draw.text((i*250+18,39),title+' / 中央表示',font=FONT,fill='#e8dfc9')
        gui=next(c['model']['model'] for c in item['cases'] if 'gui' in c['when'])
        icon_model=json.loads((ASSETS/f'models/{gui.split(":",1)[1]}.json').read_text())
        icon=Image.open(ASSETS/f'textures/{icon_model["textures"]["layer0"].split(":",1)[1]}.png').convert('RGBA')
        board.paste(icon,(i*250+25,265),icon)
        small=icon.resize((24,24),Image.Resampling.NEAREST)
        board.paste(small,(i*250+125,285),small)
        draw.text((i*250+18,340),'アイコン 64px / 小表示 24px',font=FONT,fill='#a7b3ba')
    board.save(OUT/'plate-ui.png')
    print(OUT/'plate-ui.png')


if __name__=='__main__': main()
