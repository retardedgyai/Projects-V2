"""Compile small native pixel contours using the APPROVED dash's ink/geometry pipeline.
No new raster art, resampling, vanilla particle overrides or replacement of accepted meshes.
"""
import json
import math
import numpy as np
from build_approved_dash_v3 import PACK, SIZE, polygon, geometry, ink_uvs


def contour(kind, fade):
    g = np.zeros((SIZE, SIZE), dtype=np.uint8)
    if fade == 7:
        return g
    if kind == 'boundary':
        # The centreline is exactly seven model units from the origin.
        for i in range(96):
            if i % 12 < fade:
                continue
            a, b = i*math.tau/96, (i+.82)*math.tau/96
            def p(angle, r): return (8+math.sin(angle)*r, 8+math.cos(angle)*r)
            polygon(g, [p(a,6.91),p(a,7.09),p(b,7.09),p(b,6.91)], 2)
        return g
    start = 2+fade*1.5
    if kind == 'wind':
        for i in range(24):
            z = 2+i*.5
            if z < start: continue
            x = 8+math.sin(i*.1)*.8
            width = .7*(1-i/26)
            polygon(g,[(x-width,z),(x+.2,z),(x+.1,z+.7),(x-width*.7,z+.7)],2)
            polygon(g,[(x,z),(x+.2,z),(x+.1,z+.7)],3)
    elif kind == 'spark':
        polygon(g,[(7.2,start),(8.3,6),(8,15),(7.7,8)],2)
        polygon(g,[(7.9,max(start,6)),(8.3,6),(8,15)],3)
    else:
        polygon(g,[(5,start+1),(8,start),(11,10),(9,13),(5.5,10)],1)
        polygon(g,[(5,start+1),(8,start),(10,9),(7,8)],2)
        polygon(g,[(5,start+1),(8,start),(7,3+fade)],3)
    return g


def build(assets, write):
    inks = ink_uvs(assets)
    for kind in ('wind','spark','chip','boundary'):
        for palette,tint in (('steel',0xc4d5df),('warred',0xbb4b61)):
            for fade in range(8):
                key=f'combat_vfx/war_mote_{kind}_{palette}'+(f'_fade{fade}' if fade else '')
                write(assets/f'models/{key}.json', {'ambientocclusion':False,
                    'textures':{'0':'projects:combat_vfx/ribbon/slash_5'},
                    'elements':geometry(contour(kind,fade),inks,curved=False)})
                write(assets/f'items/{key}.json', {'model':{'type':'minecraft:model','model':'projects:'+key,
                    'tints':[{'type':'minecraft:constant','value':tint}]}})
    write(assets/'font/warrior_mark.json', {'providers':[{'type':'bitmap',
        'file':'projects:gui/skills/war_wound.png','height':12,'ascent':10,'chars':['\ue001']}]})


if __name__=='__main__':
    def write(path,value):
        path.parent.mkdir(parents=True,exist_ok=True)
        path.write_text(json.dumps(value,separators=(',',':'))+'\n',encoding='utf-8')
    build(PACK/'assets/projects',write)
    (PACK/'index.txt').write_text('\n'.join(sorted(p.relative_to(PACK).as_posix()
        for p in PACK.rglob('*') if p.is_file() and p.name!='index.txt'))+'\n',encoding='utf-8')
