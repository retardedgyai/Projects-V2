"""ProjectS plate-family study. Original pixels and native cuboid geometry.

Preview only: never writes to the server resource pack. The atlas is a layout
of independently painted low-resolution surfaces, not an upscaled concept image.
"""
from __future__ import annotations

import argparse
import copy
import io
import json
import math
import sys
import zipfile
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFont

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from preview_class_armaments import render_model

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / '.tools/armor-review/plate-guard'
PALETTE = {
    '.': '00000000',
    '0': '202632ff',  # occlusion, slit, gaps
    '1': '35465aff',  # turned-away metal
    '2': '546c83ff',  # steel shadow
    '3': '7995a6ff',  # broad steel plane
    '4': 'a4c0c6ff',  # bent edge / reflected plane
    '5': 'd0dfd5ff',  # small bevel highlight
    'b': '674333ff',  # brass underside
    'g': 'ab7b4dff',  # brass face
    'G': 'dcc080ff',  # brass upper bevel
    'r': '382833ff',  # padded cloth fold shadow
    's': '69373cff',  # oxblood cloth body
    't': '984d48ff',  # cloth raised fold
    'l': '493933ff',  # belt
    'L': '795644ff',  # belt lit edge
}

# Every cluster follows a surface: bright upper-left plate roll, wide midtone,
# lower/right turn, then a narrow contact shadow. No noise or random stippling.
SURFACES = {
 'crown_front': (
    '.23333332.', '2344443321', '3444433321', '2333333221', '3444444332',
    '..........', '1........1', '1........1', '12......11', '.12222110.'),
 'crown_side': (
    '.23333332.', '2344443321', '3444333321', '2333333221', '1233332221',
    '2333332221', '12gG111221', '1222222211', '1233332211', '.12222211.'),
 'crown_back': (
    '.23333332.', '2344443321', '3444433321', '2333333221', '1233332221',
    '1223332221', '1223332221', '1223332221', '2333333321', '.12222211.'),
 'crown_top': (
    '.23333332.', '2344444321', '3445554432', '3445544432', '3444444432',
    '2334444332', '2333333321', '2233333221', '1222222211', '.11111111.'),
 'visor_front': ('2345544321', '1234433221', '1203303211', '.01222110.'),
 'visor_side': ('3321', '3221', '2211', '.110'),
 'gorget': ('2GGgggG2', '23444321', '12333211'),
 'breast_front': (
    '..100001..', '.23GGgg32.', '2444433321', '3454443321',
    '3344433321', '2333333321', '2333333221', '1233332211',
    '.12333211.', '.23333321.', '.12222211.', '.01111110.'),
 'breast_back': (
    '..100001..', '.23333321.', '2344443321', '3444433321',
    '2333333221', '1233333221', '1223332221', '0123332210',
    '.12332210.', '.23333221.', '.12222211.', '.01111110.'),
 'breast_side': (
    'r111r', '12321', '23432', '23332', '23321', '12321',
    '12221', 'r111r', 'rstrr', 'rstrr', 'rsrrr', 'rrrrr'),
 'breast_top': ('..111111..','1233333321','2334443321','2333333221','1122222111'),
 'shoulder_front': ('344443', '455443', '333332', '122221', 'gGGGgb', '0bbb00'),
 'shoulder_side': ('344332','444332','333322','122221','gGGGgb','0bbb00'),
 'shoulder_top': ('234432','345543','344443','333332','233332','122221'),
 'upper_arm': ('1221','2332','1221','rstr','rstr','rsrr'),
 'bracer': ('34432','23321','23421','23421','23321','gGGgb'),
 'bracer_side': ('3332','2221','2331','2331','1221','gGgb'),
 'glove': ('lLLl','Llll','llll'),
 'belt_front': ('LLLLLLLLLL','llgGGGgbll','llglllgbll'),
 'belt_back': ('LLLLLLLLLL','llllllllll','llllllllll'),
 'belt_side': ('LLLLL','lllll','lllll'),
 'tasset': ('344432','233321','233321','122211','344432','233321','011110'),
 'tasset_side': ('321','432','321','321','221','Ggb','b00'),
 'thigh': ('rstrr','ststr','rsstr','rsstr','rrsrr','rrrrr'),
 'knee': ('23432','34543','23432','01110'),
 'greave': ('12221','23421','23531','23431','23421','23321','12221'),
 'greave_side': ('1111','2331','2331','2331','2321','1221','1111'),
 'toe': ('344432','233321','111110'),
 'toe_top': ('123321','344432','233321','344432','233332','122221'),
 'padding': ('rrssrr','rststr','rststr','rrssrr'),
 'sole': ('111111','000000'),
 'dark': ('0',),
 'steel': ('2332','3443','2332','1221'),
 'brass': ('GG','gb'),
 'rim': ('gGGGGg','bggggb'),
 'rim_top': ('GGGGGG',),
 'brow': ('3454444432',),
}


class Atlas:
    def __init__(self):
        self.image = Image.new('RGBA', (128, 128))
        self.uv = {}
        self.regions = {}
        x = y = row_height = 0
        for name, rows in SURFACES.items():
            width, height = len(rows[0]), len(rows)
            assert all(len(row) == width for row in rows), name
            if x + width > 128:
                x = 0
                y += row_height + 1
                row_height = 0
            assert y + height <= 128
            for v, row in enumerate(rows):
                for u, value in enumerate(row):
                    self.image.putpixel((x+u, y+v), tuple(bytes.fromhex(PALETTE[value])))
            self.uv[name] = [x/8, y/8, (x+width)/8, (y+height)/8]
            self.regions[name] = [x, y, width, height]
            x += width + 1
            row_height = max(row_height, height)


def cube(atlas, name, lo, hi, front, side='steel', back=None, top='steel', bottom='dark', rotation=None):
    faces = {'north': front, 'south': back or front, 'east': side,
             'west': side, 'up': top, 'down': bottom}
    element = {'name': name, 'from': lo, 'to': hi,
        'faces': {face: {'texture':'#plate', 'uv':atlas.uv[region]}
                  for face,region in faces.items()}}
    if rotation:
        element['rotation'] = rotation
    return element


def build_parts(atlas):
    def box(name, lo, hi, *args, **kw):
        return cube(atlas, name, lo, hi, *args, **kw)
    head = [
        box('sallet shell', [3,23,3], [13,33,13], 'crown_front',
            'crown_side', 'crown_back', 'crown_top'),
        box('recessed visor darkness', [3.8,26,3.8], [12.2,29,4.2], 'dark','dark','dark','dark'),
        box('visor upper lip', [3,28,2.6], [13,28.55,3.5], 'brow','steel',top='brow'),
        box('lower face plate', [3.5,23,2.65], [12.5,27,4.2], 'visor_front',
            'visor_side', top='steel'),
        box('neck guard', [3.5,22.5,7.5], [12.5,24.5,13.5], 'steel', 'steel','steel','crown_top'),
    ]
    chest = [
        box('shaped cuirass', [3,11.8,5], [13,23.8,10.5], 'breast_front',
            'breast_side','breast_back','breast_top'),
        box('raised collar front', [4,21.8,4.55], [12,24.1,5.3], 'gorget'),
    ]
    for left in (True, False):
        x = 0 if left else 12
        sign = -1 if left else 1
        label = 'left' if left else 'right'
        rotation = {'axis':'z','angle':sign*22.5,'origin':[x+2,22.4,8]}
        chest.extend([
            box(label+' arm padding', [x-.2,12.5,5.6],[x+4.2,23,10.1], 'padding','padding',top='padding',rotation=rotation),
            box(label+' pauldron', [x-1,18.8,4.8],[x+5,24.3,10.8], 'shoulder_front',
                'shoulder_side','shoulder_front','shoulder_top',rotation=rotation),
            box(label+' brass shoulder lip', [x-1.15,19.55,4.6],[x+5.15,20.25,11],
                'rim','rim','rim','rim_top',rotation=rotation),
            box(label+' upper arm', [x,15.8,5.3],[x+4,21.8,10.3], 'upper_arm',rotation=rotation),
            box(label+' bracer', [x-.3,10.5,5.1],[x+4.3,16.3,10.4], 'bracer',
                'bracer_side','bracer','steel',rotation=rotation),
            box(label+' glove', [x,9.2,5.5],[x+4,11.5,10], 'glove','glove','glove','glove',rotation=rotation),
        ])
    legs = [box('waist belt', [3.2,11.2,4.8],[12.8,13.5,10.8], 'belt_front','belt_side','belt_back','belt_back')]
    boots = []
    for left in (True,False):
        x = 3 if left else 8.3
        label = 'left' if left else 'right'
        legs.extend([
            box(label+' padded thigh',[x,5.8,5.5],[x+4.7,12,10], 'thigh','thigh','thigh','padding'),
            box(label+' hanging thigh plate',[x-.35,7.4,4.45],[x+4.95,11.6,5.55], 'tasset','tasset_side',top='steel'),
            box(label+' knee',[x-.2,5.1,4.7],[x+4.9,7.5,6.3], 'knee','steel',top='steel'),
        ])
        boots.extend([
            box(label+' greave',[x,0.8,5.1],[x+4.7,7.4,10], 'greave','greave_side','greave_side','steel'),
            box(label+' sabaton',[x-.35,.4,2.8],[x+4.95,2.9,10.2], 'toe','greave_side','toe','toe_top'),
            box(label+' sole',[x-.35,0,2.8],[x+4.95,.7,10.2], 'sole','sole','sole','dark'),
        ])
    return {'helmet':head,'chestplate':chest,'leggings':legs,'boots':boots}


def model(elements):
    return {'credit':'ProjectS original plate-family art study', 'gui_light':'front',
            'ambientocclusion':False, 'textures':{'plate':'projects:item/armor/studies/plate_guard'},
            'elements':elements}


def item_parts(parts):
    """Center each standalone item without changing its model or UV density."""
    shifts = {'helmet':-22.5, 'chestplate':-9.2, 'leggings':-5.1, 'boots':0}
    models = {}
    for slot, elements in parts.items():
        elements = copy.deepcopy(elements)
        for e in elements:
            for key in ('from','to'):
                e[key][1] += shifts[slot]
            if 'rotation' in e:
                e['rotation']['origin'][1] += shifts[slot]
        models[slot] = model(elements)
    return models


def render_full(m, texture, yaw=-25, size=(350,560), scale=14):
    angle,pitch = math.radians(yaw), math.radians(8)
    def project(v):
        x,y,z = v-np.array([8,0,8])
        x,z = x*math.cos(angle)+z*math.sin(angle), -x*math.sin(angle)+z*math.cos(angle)
        y,z = y*math.cos(pitch)-z*math.sin(pitch), y*math.sin(pitch)+z*math.cos(pitch)
        return np.array([size[0]/2+x*scale,size[1]-24-y*scale,z])
    return render_model(m,texture,size=size,scale=scale,projector=project)


def export(out, reference_pack=None):
    out.mkdir(parents=True,exist_ok=True)
    atlas = Atlas()
    parts = build_parts(atlas)
    full = model([e for group in parts.values() for e in group])
    items = item_parts(parts)
    texture = {'plate':np.array(atlas.image)}
    atlas.image.save(out/'plate_guard.png')
    (out/'atlas-regions.json').write_text(json.dumps(atlas.regions,indent=2))
    for slot,m in items.items():
        for e in m['elements']:
            assert all(-16<=v<=32 for key in ('from','to') for v in e[key]), e['name']
            assert all(a<b for a,b in zip(e['from'],e['to'])),e['name']
            for f in e['faces'].values():
                assert all(0<=v<=16 for v in f['uv'])
        (out/f'plate_guard_{slot}.json').write_text(json.dumps(m,indent=2))
    (out/'assembled-preview.json').write_text(json.dumps(full,indent=2))
    font=ImageFont.truetype('C:/Windows/Fonts/meiryob.ttc',16)
    small=ImageFont.truetype('C:/Windows/Fonts/meiryo.ttc',12)
    sheet=Image.new('RGB',(1320,840),'#1b1e23')
    draw=ImageDraw.Draw(sheet)
    draw.text((24,16),'ProjectS  /  PLATE 01  —  港の遠征鎧',font=font,fill='#e7e0cf')
    draw.text((24,46),'実モデル・実テクスチャのプレビュー / ゲーム内の装着検証前',font=small,fill='#a8afb4')
    for i,(yaw,label) in enumerate(((-25,'斜め'),(0,'正面'),(155,'背面'))):
        frame=render_full(full,texture,yaw,size=(330,520),scale=13.3)
        sheet.paste(frame,(i*330,70))
        draw.text((i*330+24,592),label,font=small,fill='#a8afb4')
    draw.text((1020,95),'小さな表示',font=small,fill='#a8afb4')
    tiny=render_full(full,texture,size=(160,215),scale=5.4)
    sheet.paste(tiny,(1040,115))
    for i,(slot,m) in enumerate(items.items()):
        frame=render_model(m,texture,size=(245,195),scale=10.8)
        sheet.paste(frame,(i*250+8,630))
        draw.text((i*250+22,626),slot,font=small,fill='#c6cdd0')
    draw.text((1030,378),'冷たい鋼 / 真鍮 / 赤茶の裏地',font=small,fill='#d7cebd')
    for i,color in enumerate(('1','2','3','4','5','b','g','G','r','s','t')):
        x=1030+(i%6)*34; y=411+(i//6)*34
        draw.rectangle((x,y,x+26,y+26),fill='#'+PALETTE[color][:6])
    sheet.save(out/'plate-overview.png')
    views=Image.new('RGB',(1400,560),'#1b1e23')
    for i,yaw in enumerate((-25,25,90,180)):
        views.paste(render_full(full,texture,yaw),(i*350,0))
    views.save(out/'plate-angles.png')
    # Light-independent view tests whether volume is really in the painting.
    unlit=copy.deepcopy(full)
    for e in unlit['elements']: e['shade']=False
    paint=Image.new('RGB',(700,560),'#1b1e23')
    paint.paste(render_full(full,texture),(0,0))
    paint.paste(render_full(unlit,texture),(350,0))
    d=ImageDraw.Draw(paint)
    d.text((20,12),'面ごとの描画陰影あり',font=small,fill='white')
    d.text((370,12),'描画陰影なし / テクスチャだけ',font=small,fill='white')
    paint.save(out/'painted-volume.png')
    if reference_pack:
        comparison=Image.new('RGB',(1200,680),'#1b1e23')
        d=ImageDraw.Draw(comparison)
        with zipfile.ZipFile(reference_pack) as pack:
            for col,(slot,current) in enumerate(items.items()):
                reference=json.loads(pack.read(f'assets/isles/models/item/armor/melee/elite_warrior_{slot}.json'))
                refs={key:np.array(Image.open(io.BytesIO(pack.read(
                    'assets/'+source.replace(':','/textures/')+'.png'))).convert('RGBA'))
                    for key,source in reference['textures'].items() if ':' in source}
                for row,(m,t,label) in enumerate(((reference,refs,'Isles / Elite warrior'),
                                                  (current,texture,'ProjectS / Plate 01'))):
                    frame=render_model(m,t,size=(300,290),scale=12)
                    comparison.paste(frame,(col*300,row*340+30))
                    d.text((col*300+12,row*340+8),label+' / '+slot,font=small,fill='#e7e0cf')
        comparison.save(out/'plate-reference-comparison.png')
    print(f'Validated {len(full["elements"])} cuboids / {len(PALETTE)-1} colors / 4 items')
    print(out)


if __name__ == '__main__':
    parser=argparse.ArgumentParser()
    parser.add_argument('--output',type=Path,default=OUT)
    parser.add_argument('--reference-pack',type=Path)
    args=parser.parse_args()
    export(args.output,args.reference_pack)
