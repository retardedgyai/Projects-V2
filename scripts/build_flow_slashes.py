"""Native short surfaces for client-interpolated slash joints; no frame swapping."""
import numpy as np
from build_greatsword_sweep import SIZE, polygon, geometry, ink_uvs, pigments


def build(assets,write):
    inks=ink_uvs(assets)
    for family in ('steel','gold','shadow'):
        for layer in ('cut','tip','tail','wake'):
            grid=np.zeros((SIZE,SIZE),dtype=np.uint8)
            # Narrow irregular interior, broad midtone, thin luminous cutting edge.
            # The longitudinal ends stay broad enough for overlapping articulated joints.
            def inside(z):
                if layer=='tip': return 1+15*(z/16)**2
                if layer=='tail': return 1+15*(1-z/16)**2
                return 1+(.75 if 4<z<11 else 0)
            for row in range(64):
                z0,z1=row/4,(row+1)/4
                for ink,fraction in ((1,0),(2,.38),(3,.9)):
                    a=inside(z0);b=inside(z1)
                    polygon(grid,[(a+(16-a)*fraction,z0),(b+(16-b)*fraction,z1),(16,z1),(16,z0)],ink)
            if layer=='cut': polygon(grid,[(9,2),(9.5,5),(9,11),(10,14),(9.5,11),(10,5)],3)
            key=f'combat_vfx/flow/{family}_{layer}'
            # Merge identical adjacent native strips; four moving joints must not
            # multiply the old whole-stroke face count by four.
            merged=[]; last={}
            for e in geometry(grid,inks,curved=False,pigment=True):
                signature=(e['from'][0],e['to'][0],e['faces']['up']['tintindex'])
                old=last.get(signature)
                if old is not None and old['to'][2]==e['from'][2]:
                    old['to'][2]=e['to'][2]
                else:
                    merged.append(e);last[signature]=e
            write(assets/f'models/{key}.json',{'ambientocclusion':False,
                'textures':{'0':'projects:combat_vfx/ribbon/slash_5'},
                'elements':merged})
            write(assets/f'items/{key}.json',{'model':{'type':'minecraft:model','model':f'projects:{key}',
                'tints':pigments(family,'wake' if layer=='wake' else 'blade')}})
