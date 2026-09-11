"""Exact hand transforms, viewed orthographically from the blade face; NOT an in-game camera."""
from copy import deepcopy
from pathlib import Path
import numpy as np
from PIL import Image, ImageDraw
from build_pixel_armament_pack import geometry
from process_sword_material_redraw import convert, guard_depth, approved_hand_display, approved_grip_point
from pixel_weapon_display import transformed, rotation_xyz
from preview_class_armaments import render_model, FONT


def main():
    _, textures, entry = convert()
    base, _, _ = geometry('greatsword', entry, textures)
    model = approved_hand_display(guard_depth(base, textures, entry), textures, entry)
    grip = approved_grip_point(textures, entry)
    sheet = Image.new('RGB', (1040, 510), '#1b1e23')
    draw = ImageDraw.Draw(sheet)
    draw.text((10, 8), '手持ち倍率・固定した握り位置の正投影比較 / 一人称カメラ・Minecraft画面の再現ではありません', font=FONT, fill='#eee6d6')
    for column, (context, old) in enumerate((('firstperson', True), ('firstperson', False), ('thirdperson', True), ('thirdperson', False))):
        display = deepcopy(model['display'][context+'_righthand'])
        anchor = transformed(grip, display)
        if old:
            display['scale'] = [.72]*3
            display['translation'] = (anchor-rotation_xyz(display['rotation'])@((grip-8)*.72)).tolist()
        def project(v):
            x,y,z = transformed(v, display)-anchor
            return np.array([130-z*10, 368-y*10, -x])
        tile = render_model(model, textures, size=(260, 450), projector=project)
        ink = ImageDraw.Draw(tile)
        ink.line((115,368,145,368), fill='#a28c77')
        ink.line((130,353,130,383), fill='#a28c77')
        label = ('一人称' if context=='firstperson' else '三人称') + (' 旧' if old else ' 新')
        ink.text((12, 6), f'{label} / 倍率 {display["scale"][0]:.2f}', font=FONT, fill='#eee6d6')
        sheet.paste(tile,(column*260,40))
    draw.text((12,488), '十字が握り位置。原稿・UV・刃の形・GUI表示は変更なし。赤い刃のアニメーションは既存のまま。', font=FONT, fill='#b8b1a6')
    path=Path(__file__).resolve().parents[1]/'.tools/warrior-grip-scale-review.png'
    path.parent.mkdir(exist_ok=True)
    sheet.save(path)
    print(path)


if __name__=='__main__': main()
