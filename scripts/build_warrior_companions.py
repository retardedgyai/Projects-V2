"""Compile small native pixel contours using the APPROVED dash's ink/geometry pipeline.
No new raster art, resampling, vanilla particle overrides or replacement of accepted meshes.
"""
import json
import math
import numpy as np
from build_approved_dash_v3 import PACK, SIZE, polygon, geometry, ink_uvs


def contour(kind, fade):
    resolution=256 if kind=='boundary' else SIZE
    g = np.zeros((resolution, resolution), dtype=np.uint8)
    if fade == 7:
        return g
    if kind == 'boundary':
        # Fine native steps keep a seven-metre range marker a LINE, not a
        # necklace of 25cm squares. This changes geometry, not texture resolution.
        yy,xx=np.indices(g.shape)
        x,z=(xx+.5)/16-8,(yy+.5)/16-8
        radius=np.sqrt(x*x+z*z)
        angle=np.mod(np.arctan2(x,z),math.tau)
        sector=np.floor(angle/math.tau*96).astype(int)
        visible=(np.abs(radius-7)<.065) & (sector%12>=fade)
        g[visible]=2
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


def boundary_geometry(grid,inks):
    elements=[]
    for row,values in enumerate(grid):
        start=0
        while start<len(values):
            ink=int(values[start]);end=start+1
            while end<len(values) and values[end]==ink: end+=1
            if ink:
                u0,v0,u1,v1=inks[ink]
                elements.append({'from':[start/16,8,row/16],'to':[end/16,8,(row+1)/16],
                    'shade':False,'faces':{
                        'up':{'texture':'#0','uv':[u0,v1,u1,v0],'tintindex':0},
                        'down':{'texture':'#0','uv':[u0,v0,u1,v1],'tintindex':0}}})
            start=end
    return elements


def build(assets, write):
    inks = ink_uvs(assets)
    for kind in ('wind','spark','chip','boundary'):
        for palette,tint in (('steel',0xc4d5df),('warred',0xbb4b61)):
            for fade in range(8):
                key=f'combat_vfx/war_mote_{kind}_{palette}'+(f'_fade{fade}' if fade else '')
                write(assets/f'models/{key}.json', {'ambientocclusion':False,
                    'textures':{'0':'projects:combat_vfx/ribbon/slash_5'},
                    'elements':boundary_geometry(contour(kind,fade),inks) if kind=='boundary' else
                        geometry(contour(kind,fade),inks,curved=False)})
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
