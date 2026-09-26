"""QA sheet from exported armor models and textures; not a client screenshot."""
import json
import math
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

from build_class_armor_assets import ASSETS, JOBS, SLOTS
from preview_class_armaments import FONT, render_model


OUT = Path(__file__).resolve().parents[1] / '.tools/armor-review'


def icon(job, tier, slot, size=96):
    key = f'armor/{job}_t{tier}'
    model = json.loads((ASSETS / f'models/item/{key}_{slot}.json').read_text())
    textures = {
        'atlas': np.array(Image.open(ASSETS / 'textures/item/weapons/materials.png').convert('RGBA')),
        'outer': np.array(Image.open(ASSETS / f'textures/item/{key}_outer.png').convert('RGBA')),
        'inner': np.array(Image.open(ASSETS / f'textures/item/{key}_inner.png').convert('RGBA')),
    }
    yaw, pitch = math.radians(-25), math.radians(15)
    # One 16-unit model fills one item square, then the exported GUI transform
    # applies. Do not auto-fit each part: that would hide inventory clipping.
    center = np.array([8, 8, 8], dtype=float)
    display = model['display']['gui']
    scale = size / 16 * display['scale'][0]

    def project(v):
        x, y, z = v - center
        y += display['translation'][1]
        x, z = x * math.cos(yaw) + z * math.sin(yaw), -x * math.sin(yaw) + z * math.cos(yaw)
        y, z = y * math.cos(pitch) - z * math.sin(pitch), y * math.sin(pitch) + z * math.cos(pitch)
        return np.array([size / 2 + x * scale, size / 2 - y * scale, z])

    return render_model(model, textures, size=(size, size), projector=project)


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    tile, gutter = 96, 22
    for tier, rendered_size, filename in ((1, 96, 't1-items.png'), (1, 24, 't1-items-ui-scale.png'),
                                          (4, 24, 't4-items-ui-scale.png'),
                                          (1, 0, 't1-icons.png'), (4, 0, 't4-icons.png')):
        sheet = Image.new('RGB', ((tile + gutter) * len(SLOTS), (tile + 20) * len(JOBS) + 30), '#171b20')
        draw = ImageDraw.Draw(sheet)
        for col, slot in enumerate(SLOTS):
            draw.text((col * (tile + gutter) + 8, 6), slot, font=FONT, fill='#e3d7ba')
        for row, job in enumerate(JOBS):
            for col, slot in enumerate(SLOTS):
                x, y = col * (tile + gutter), row * (tile + 20) + 30
                if rendered_size == 0:
                    item = Image.open(ASSETS / f'textures/item/armor/icons/{job}_t{tier}_{slot}.png').convert('RGBA')
                    item = item.resize((tile, tile), Image.Resampling.NEAREST)
                    sheet.paste(item, (x, y), item)
                    draw.text((x + 6, y + tile), job, font=FONT, fill='#b6b2a8')
                    continue
                item = icon(job, tier, slot, rendered_size)
                if rendered_size != tile:
                    item = item.resize((tile, tile), Image.Resampling.NEAREST)
                sheet.paste(item, (x, y))
                draw.text((x + 6, y + tile), job, font=FONT, fill='#b6b2a8')
        sheet.save(OUT / filename)
        print(OUT / filename)


if __name__ == '__main__':
    main()
