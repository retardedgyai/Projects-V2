"""Mage v2 material build. No fallback to the rejected geometric v1 artwork.

Source images are read unchanged; native geometry/materials carry their selected
painted planes. This only builds RP assets, never changes combat authority.
"""
import json
from build_approved_dash_v3 import PACK, ink_uvs
from build_mage_fire import FIRE_CLIPS, mesh as fire_mesh, palette as fire_palette
from build_mage_ice import ICE_CLIPS, mesh as ice_mesh, palette as ice_palette
from build_mage_arcane import ARCANE_CLIPS, mesh as arcane_mesh, PALETTE as ARCANE_PALETTE
from build_mage_cataclysm import CATACLYSM_CLIPS, mesh as cataclysm_mesh, palette as cataclysm_palette

CLIPS = {
    'cinder': (24, 0xffaa69), 'fire_stream': (24, 0xffaa69), 'flame_hit': (18, 0xffc385),
    'meteor': (24, 0xff8a58), 'eruption': (24, 0xffbc86),
    'pyre': (48, 0xff865e), 'corona': (48, 0xffbe86),
    'solar_flare': (24, 0xffbe86),
    'frost_wave': (30, 0xb6e7f4), 'crystal': (48, 0x8dd7ed),
    'frost_trace': (30, 0xb6e7f4), 'ice_shelf': (48, 0x8dd7ed),
    'ice_root': (48, 0x709bc9), 'zero_crown': (48, 0xc6f0f4),
    'zero_floor': (48, 0x7bafdd), 'ice_hit': (18, 0xc2edf2),
    'zero_shelf': (48, 0x7bafdd), 'zero_wing': (48, 0x7bafdd), 'ice_pulse': (24, 0xc2edf2),
    'conductor': (24, 0xc7b4ff), 'thunder_hit': (18, 0xe4d7ff),
    'arcane_forks': (24, 0xc7b4ff),
    'discharge': (28, 0xcdbaff), 'rupture': (28, 0x9276d6),
    'fold_in': (24, 0xbbadf5), 'fold_out': (24, 0xd2c3ff),
    'ward': (30, 0xa69be4), 'ward_mote': (30, 0xdfd7ff),
    'fire_charge': (24, 0xffbd82), 'ice_charge': (24, 0xc1e9f3),
    'arcane_charge': (24, 0xd8c7ff),
}

MATERIALS = ((FIRE_CLIPS,fire_mesh,fire_palette),(ICE_CLIPS,ice_mesh,ice_palette),
             (ARCANE_CLIPS,arcane_mesh,lambda:ARCANE_PALETTE),
             (CATACLYSM_CLIPS,cataclysm_mesh,cataclysm_palette))
assert set(CLIPS)==set.union(*(names for names,_,_ in MATERIALS))
assert sum(len(names) for names,_,_ in MATERIALS)==len(CLIPS)


def material_palette(clip):
    return next(palette() for names,_,palette in MATERIALS if clip in names)


def material_geometry(clip,frame,inks):
    return next(mesh(clip,frame,inks[3]) for names,mesh,_ in MATERIALS if clip in names)


def build(assets,write):
    inks=ink_uvs(assets)
    for clip,(frames,_) in CLIPS.items():
        for frame in range(frames):
            key=f'combat_vfx/mage_material/{clip}_{frame}'
            write(assets/f'models/{key}.json',{'ambientocclusion':False,
                'textures':{'0':'projects:combat_vfx/ribbon/slash_5'},
                'elements':material_geometry(clip,frame,inks)})
            write(assets/f'items/{key}.json',{'model':{'type':'minecraft:model','model':'projects:'+key,
                'tints':[{'type':'minecraft:constant','value':colour} for colour in material_palette(clip)]}})


if __name__=='__main__':
    def write(path,value):
        path.parent.mkdir(parents=True,exist_ok=True)
        path.write_text(json.dumps(value,separators=(',',':'))+'\n',encoding='utf-8')
    build(PACK/'assets/projects',write)
    (PACK/'index.txt').write_text('\n'.join(sorted(p.relative_to(PACK).as_posix()
        for p in PACK.rglob('*') if p.is_file() and p.name!='index.txt'))+'\n',encoding='utf-8')
