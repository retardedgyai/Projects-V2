"""地味版の武器（2026-10-17 承認）を、ドット絵テクスチャから RP のモデルとアイテム定義にする。
テクスチャ（textures/item/weapons/plain/plain_*.png）が原本。1マス=1px で、16の倍数の正方形に余白を付けてある。
- 職業6種の T1 は、この絵に差し替える（items/weapons/<family>_t1.json だけを書き換える。既存の3Dモデルは残す）。
- 斧・大鎌・盾は素材だけ（items/weapons/plain_<name>.json）。どの装備からも参照しない。
絵は縦向きに描いてあるので、手に持つ表示はバニラの斜めの剣に合うよう面内で -45° 回してある。
1マスの大きさをアイテム間で揃えるため、手に持つ表示の倍率はテクスチャの一辺 / 48 を掛ける。"""
import json
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
PACK = ROOT / 'server-minestom/src/main/resources/core-ui-pack'
ASSETS = PACK / 'assets/projects'
TEX = ASSETS / 'textures/item/weapons/plain'
REF = 48
T1 = {'greatsword': 'greatsword', 'dagger': 'dagger', 'mace': 'lance', 'staff': 'staff', 'tome': 'tome', 'bow': 'bow'}
EXTRAS = ('axe', 'scythe', 'shield')
STYLE = {'greatsword': 'blade', 'dagger': 'blade', 'lance': 'blade', 'staff': 'blade', 'axe': 'blade',
         'scythe': 'blade', 'bow': 'bow', 'tome': 'flat', 'shield': 'flat'}


def r(v): return round(v, 3)


def display(style, f):
    hand3, hand1 = 0.85 * f, 0.68 * f
    if style == 'blade':                      # バニラ item/handheld の z（55 / 25）から -45°
        return {'thirdperson_righthand': {'rotation': [0, -90, 10], 'translation': [0, 4, 0.5], 'scale': [r(hand3)] * 3},
                'thirdperson_lefthand': {'rotation': [0, 90, -100], 'translation': [0, 4, 0.5], 'scale': [r(hand3)] * 3},
                'firstperson_righthand': {'rotation': [0, -90, -20], 'translation': [1.13, 3.2, 1.13], 'scale': [r(hand1)] * 3},
                'firstperson_lefthand': {'rotation': [0, 90, -70], 'translation': [1.13, 3.2, 1.13], 'scale': [r(hand1)] * 3},
                'gui': {'rotation': [0, 0, -45], 'translation': [0, 0, 0], 'scale': [0.95] * 3},
                'ground': {'rotation': [0, 0, -45], 'translation': [0, 2, 0], 'scale': [0.5] * 3},
                'fixed': {'rotation': [0, 180, -45], 'translation': [0, 0, 0], 'scale': [1] * 3}}
    if style == 'bow':                        # バニラの弓の表示から -45°
        return {'thirdperson_righthand': {'rotation': [-80, 260, -85], 'translation': [-1, -2, 2.5], 'scale': [r(0.9 * f)] * 3},
                'thirdperson_lefthand': {'rotation': [-80, -280, -5], 'translation': [-1, -2, 2.5], 'scale': [r(0.9 * f)] * 3},
                'firstperson_righthand': {'rotation': [0, -90, -20], 'translation': [1.13, 3.2, 1.13], 'scale': [r(hand1)] * 3},
                'firstperson_lefthand': {'rotation': [0, 90, -70], 'translation': [1.13, 3.2, 1.13], 'scale': [r(hand1)] * 3},
                'gui': {'rotation': [0, 0, -45], 'translation': [0, 0, 0], 'scale': [0.95] * 3},
                'ground': {'rotation': [0, 0, -45], 'translation': [0, 2, 0], 'scale': [0.5] * 3},
                'fixed': {'rotation': [0, 180, -45], 'translation': [0, 0, 0], 'scale': [1] * 3}}
    return {'thirdperson_righthand': {'rotation': [0, 0, 0], 'translation': [0, 3, 1], 'scale': [r(0.55 * f)] * 3},
            'thirdperson_lefthand': {'rotation': [0, 0, 0], 'translation': [0, 3, 1], 'scale': [r(0.55 * f)] * 3},
            'firstperson_righthand': {'rotation': [0, -90, 25], 'translation': [1.13, 3.2, 1.13], 'scale': [r(hand1)] * 3},
            'firstperson_lefthand': {'rotation': [0, 90, -25], 'translation': [1.13, 3.2, 1.13], 'scale': [r(hand1)] * 3},
            'ground': {'rotation': [0, 0, 0], 'translation': [0, 2, 0], 'scale': [0.5] * 3},
            'fixed': {'rotation': [0, 180, 0], 'translation': [0, 0, 0], 'scale': [1] * 3}}


def model(name):
    size = Image.open(TEX / f'plain_{name}.png').size[0]
    return {'credit': 'ProjectS original pixel armament (plain set, 2026-10-17)', 'parent': 'minecraft:item/generated',
            'gui_light': 'front', 'textures': {'layer0': f'projects:item/weapons/plain/plain_{name}'},
            'display': display(STYLE[name], size / REF)}


def static(name):
    return {'model': {'type': 'minecraft:model', 'model': f'projects:item/weapons/plain/plain_{name}'}, 'hand_animation_on_swap': False}


def build():
    files = {}
    for name in set(T1.values()) | set(EXTRAS):
        files[f'models/item/weapons/plain/plain_{name}.json'] = model(name)
    for family, name in T1.items():
        files[f'items/weapons/{family}_t1.json'] = static(name)
    # The greatsword swings in first person (class_armament_swing): its frames dispatch on custom_model_data.
    import class_armament_swing as swing
    files['items/weapons/greatsword_t1.json'] = {'model': {'type': 'minecraft:range_dispatch', 'property': 'minecraft:custom_model_data',
        'index': 0, 'fallback': static('greatsword')['model'], 'entries': swing.entries('greatsword_t1')}, 'hand_animation_on_swap': False}
    files.update(swing.files('greatsword_t1'))
    for name in EXTRAS:
        files[f'items/weapons/plain_{name}.json'] = static(name)
    for relative, data in files.items():
        path = ASSETS / relative
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps(data, ensure_ascii=False, separators=(',', ':')) + '\n', encoding='utf-8')
    index = PACK / 'index.txt'
    paths = sorted(str(p.relative_to(PACK)).replace('\\', '/') for p in PACK.rglob('*') if p.is_file() and p.name != 'index.txt')
    index.write_text('\n'.join(paths) + '\n', encoding='utf-8')
    print(f'Wrote {len(files)} plain weapon files; index has {len(paths)} entries.')


if __name__ == '__main__':
    build()
