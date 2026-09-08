"""Native voxel meshes for transient ITEM_DISPLAY combat silhouettes, not UI icons.

XZ-plane geometry has a white-hot edge, saturated body and a darker trailing rim.
Contiguous cells are merged into cuboids. Original slash flipbook textures are added
alongside them; accepted UI textures, fonts and client code are not replaced.
"""
import math
from pathlib import Path
import json
from PIL import Image
from combat_vfx_shapes import AUTHORED_SHAPES, CROSSED, authored_cell

PALETTES = {
    "steel": ("white_concrete", "light_gray_concrete", "gray_concrete"),
    "gold": ("white_concrete", "yellow_concrete", "orange_concrete"),
    "astral": ("white_concrete", "magenta_concrete", "purple_concrete"),
    "ice": ("white_concrete", "light_blue_concrete", "blue_concrete"),
    "fire": ("yellow_concrete", "orange_concrete", "red_concrete"),
    "venom": ("white_concrete", "lime_concrete", "green_concrete"),
    "life": ("white_concrete", "lime_concrete", "cyan_concrete"),
    "shadow": ("light_gray_concrete", "purple_concrete", "black_concrete"),
    "hunter": ("white_concrete", "green_concrete", "brown_concrete"),
    "holy": ("white_concrete", "yellow_concrete", "light_blue_concrete"),
    "lightning": ("white_concrete", "light_blue_concrete", "purple_concrete"),
}


def cell(shape, x, z):
    if shape in AUTHORED_SHAPES:
        return authored_cell(shape, x, z)
    r = math.hypot(x, z)
    a = math.atan2(x, z)
    if shape == "crescent":
        # A continuous swept blade, front-facing in +Z; tapers to sharp tips.
        width = .28 * max(0, math.cos(a / 1.22))
        if abs(a) < 1.9 and .94 - width < r < .99:
            return 0 if r > .91 else 1 if r > .82 else 2
    elif shape == "orbit":
        if .82 < r < .98:
            return 0 if r > .94 else 1 if r > .87 else 2
        if (abs(x) < .045 or abs(z) < .045) and .70 < r < 1:
            return 0
    elif shape == "star":
        # Long four-point nucleus with smaller diagonal rays and a faceted centre.
        limit = max(.23, .98 * abs(math.cos(2 * a)) ** 7, .52 * abs(math.sin(2 * a)) ** 10)
        if r < limit:
            return 0 if r < limit * .52 else 1 if r < limit * .82 else 2
    elif shape == "lance":
        width = .19 * (1 - abs(z)) + .025
        if abs(z) < .99 and abs(x) < width:
            return 0 if abs(x) < width * .3 else 1 if abs(x) < width * .75 else 2
    elif shape == "burst":
        limit = .46 + .49 * abs(math.cos(4 * a)) ** 10
        if .18 < r < limit:
            return 0 if r < .35 else 1 if r < limit * .78 else 2
    elif shape == "rune":
        if .82 < r < .96:
            return 0 if r > .92 else 1
        if abs(math.sin(3 * a)) < .10 and .38 < r < .82:
            return 0
        if .30 < r < .40:
            return 2
    elif shape == "bolt":
        center = .17 * math.sin(z * 13)
        if abs(z) < .98 and abs(x - center) < .09:
            return 0 if abs(x - center) < .035 else 1
    return None


SHAPES = ("crescent", "orbit", "star", "lance", "burst", "rune", "bolt")


def mesh(shape, palette):
    elements = []
    grid = [[cell(shape, (x + .5) / 24 - 1, (z + .5) / 24 - 1) for x in range(48)] for z in range(48)]
    for z, row in enumerate(grid):
        x = 0
        while x < 48:
            ink = row[x]
            end = x + 1
            while end < 48 and row[end] == ink:
                end += 1
            if ink is not None:
                bottom = z + 1
                while bottom < 48 and all(grid[bottom][i] == ink for i in range(x, end)):
                    bottom += 1
                for merged in range(z + 1, bottom):
                    for i in range(x, end):
                        grid[merged][i] = None
                elements.append({
                    "from": [x / 3, 7.75, z / 3], "to": [end / 3, 8.25, bottom / 3],
                    "shade": False,
                    "faces": {face: {"texture": f"#{ink}", "uv": [2, 2, 3, 3]} for face in ("up", "down", "north", "south", "east", "west")},
                })
            x = end
    if shape in ("lance", "bolt", "star") or shape in CROSSED:
        # Cross-section stays visible from the side: beams are not paper-thin ribbons.
        # Swap X/Y for a spear, Y/Z for the star's perpendicular radiant plane.
        axis = (0, 2, 1) if shape in ("star", "star_core", "celestial_core", "star_seed") else (1, 0, 2)
        crossed = [{**e, "from": [e["from"][i] for i in axis], "to": [e["to"][i] for i in axis]} for e in elements]
        elements += crossed
    return {"ambientocclusion": False, "textures": {str(i): f"minecraft:block/{texture}" for i, texture in enumerate(PALETTES[palette])}, "elements": elements}


def scene_rows():
    source = Path(__file__).resolve().parents[1] / "server-minestom/src/main/resources/combat-art/skill-scenes.psv"
    rows = [line.split("|") for line in source.read_text(encoding="utf-8").splitlines() if line and not line.startswith("#")]
    assert all(len(row) == 12 for row in rows)
    assert len({row[0] for row in rows}) == len(rows)
    return rows


def scene_models():
    return sorted({(shape, row[3]) for row in scene_rows() for shape in row[4:8]})


def build_combat_models(assets, write_json):
    for shape, palette in scene_models():
        assert shape in AUTHORED_SHAPES, shape
        name = f"{shape}_{palette}"
        model = mesh(shape, palette)
        write_json(assets / f"models/combat_vfx/{name}.json", model)
        write_json(assets / f"items/combat_vfx/{name}.json", {"model": {"type": "minecraft:model", "model": f"projects:combat_vfx/{name}"}})
        # Spatial erosion, not collapsing the entire effect into a paper-thin plane.
        for stage in range(1, 8):
            def remains(element):
                center = [(a+b)*.5 for a,b in zip(element['from'],element['to'])]
                noise = (math.sin(center[0]*12.9898+center[1]*37.719+center[2]*78.233)*43758.5453)%1
                return noise > stage/8
            eroded = {**model, "elements": [e for e in model['elements'] if remains(e)]}
            fade = f"{name}_fade{stage}"
            write_json(assets / f"models/combat_vfx/{fade}.json", eroded)
            write_json(assets / f"items/combat_vfx/{fade}.json", {"model": {"type": "minecraft:model", "model": f"projects:combat_vfx/{fade}"}})
    build_slash_frames(assets, write_json)
    build_stellar_frames(assets, write_json)


def build_stellar_frames(assets, write_json):
    master = Path(__file__).resolve().parents[1] / 'assets/combat-vfx/stellar-burst-pixel-v1.png'
    atlas = Image.open(master).convert('L')
    for frame in range(16):
        x,y=frame%4,frame//4
        tile=atlas.crop((round(x*atlas.width/4),round(y*atlas.height/4),
                         round((x+1)*atlas.width/4),round((y+1)*atlas.height/4)))
        tile=tile.resize((64,64),Image.Resampling.NEAREST)
        tile=tile.point(lambda v: 0 if v<32 else 64 if v<96 else 128 if v<160 else 192 if v<224 else 255)
        alpha=tile.point(lambda v: 255 if v else 0)
        for box in ((0,0,64,4),(0,60,64,64),(0,0,4,64),(60,0,64,64)):
            assert alpha.crop(box).getbbox() is None, f'{master.name} frame {frame}: clipped border'
        target=assets/f'textures/combat_vfx/stellar/burst_{frame}.png'
        target.parent.mkdir(parents=True,exist_ok=True)
        Image.merge('RGBA',(tile,tile,tile,alpha)).save(target)
        name=f'combat_vfx/stellar/burst_{frame}'
        write_json(assets/f'models/{name}.json',{
            'ambientocclusion':False,'textures':{'0':f'projects:{name}'},
            'elements':[{'from':[0,8,0],'to':[16,8,16],'shade':False,'faces':{
                'up':{'texture':'#0','uv':[0,16,16,0],'tintindex':0},
                'down':{'texture':'#0','uv':[0,0,16,16],'tintindex':0}}}]})
        for palette,color in dict(astral=0xdca6ff,ice=0x85e2ff).items():
            write_json(assets/f'items/combat_vfx/stellar/burst_{palette}_{frame}.json',
                {'model':{'type':'minecraft:model','model':f'projects:{name}',
                          'tints':[{'type':'minecraft:constant','value':color}]}})


def build_slash_frames(assets, write_json):
    master = Path(__file__).resolve().parents[1] / 'assets/combat-vfx/slash-pixel-atlas-v1.png'
    atlas = Image.open(master).convert('RGB')
    layout=json.loads(master.with_suffix('.layout.json').read_text())
    assert list(atlas.size)==layout['sourceSize'] and len(layout['frames'])==16
    colors = dict(steel=0xeaf4ff,gold=0xffd87b,astral=0xdca6ff,ice=0x85e2ff,fire=0xffab52,
                  venom=0xb5ff5a,life=0xadffcb,shadow=0xe1b5ff,hunter=0xe8ffa3,holy=0xffedb0,lightning=0xb9dfff)
    for frame in range(16):
        # Generated rows are not perfectly evenly spaced: use inspected frame rectangles,
        # including their empty gutters, rather than cutting a shard into the next frame.
        tile=atlas.crop(layout['frames'][frame])
        # This source was authored as pixel clusters, not the rejected smooth atlas.
        # Import to its 64px logical grid without smoothing and normalize the four inks.
        # RGB black is background, not a translucent rectangle; frame breakup supplies the fade.
        tile=tile.resize((64,64),Image.Resampling.NEAREST).convert('L')
        tile=tile.point(lambda v: 0 if v<32 else 64 if v<96 else 128 if v<160 else 192 if v<224 else 255)
        alpha=tile.point(lambda v: 255 if v else 0)
        for box in ((0,0,64,4),(0,60,64,64),(0,0,4,64),(60,0,64,64)):
            assert alpha.crop(box).getbbox() is None, f'{master.name} frame {frame}: artwork reaches tile gutter'
        rgba=Image.merge('RGBA',(tile,tile,tile,alpha))
        texture=assets/f'textures/combat_vfx/ribbon/slash_{frame}.png'
        texture.parent.mkdir(parents=True,exist_ok=True);rgba.save(texture)
        # Vanilla FaceInfo.UP starts at MIN_Z. Reverse V so the image top is +Z.
        # The underside maps to the same world texels. Return cuts mirror U, never negative scale.
        for reverse in (False,True):
            name=f'slash_{"reverse_" if reverse else ""}'
            u0,u1=(16,0) if reverse else (0,16)
            model={"ambientocclusion":False,"textures":{"0":f"projects:combat_vfx/ribbon/slash_{frame}"},
                   "elements":[{"from":[0,8,0],"to":[16,8,16],"shade":False,
                      "faces":{"up":{"texture":"#0","uv":[u0,16,u1,0],"tintindex":0},
                               "down":{"texture":"#0","uv":[u0,0,u1,16],"tintindex":0}}}]}
            write_json(assets/f'models/combat_vfx/ribbon/{name}{frame}.json',model)
            for palette,color in colors.items():
                write_json(assets/f'items/combat_vfx/ribbon/{name}{palette}_{frame}.json',
                           {"model":{"type":"minecraft:model","model":f"projects:combat_vfx/ribbon/{name}{frame}",
                                     "tints":[{"type":"minecraft:constant","value":color}]}})


if __name__ == "__main__":
    pack = Path(__file__).resolve().parents[1] / "server-minestom/src/main/resources/core-ui-pack"
    def write(path, value):
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps(value, separators=(",", ":")) + "\n", encoding="utf-8")
    build_combat_models(pack / "assets/projects", write)
    (pack / "index.txt").write_text("\n".join(sorted(str(p.relative_to(pack)).replace("\\", "/") for p in pack.rglob("*") if p.is_file() and p.name != "index.txt")) + "\n", encoding="utf-8")
