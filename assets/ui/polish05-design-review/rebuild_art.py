from pathlib import Path
import sys, shutil, math
import numpy as np

root = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(root / 'scripts'))
from plate_armor_ui import ui_model, source
from preview_class_armaments import render_model, rotated

dest = root / 'assets/ui/polish05-design-review/art'
dest.mkdir(parents=True, exist_ok=True)
pack = root / 'server-minestom/src/main/resources/core-ui-pack/assets/projects/textures/item'
for slot in ['helmet', 'chestplate', 'leggings', 'boots']:
    shutil.copy2(pack / f'armor/icons/warrior_t1_{slot}.png', dest / f'{slot}-icon.png')
    model = ui_model(slot)
    display = model['display']['fixed']
    scale = display['scale'][0]
    # Rotate around the mesh centre, matching the workshop display presentation.
    centre = (np.min([e['from'] for e in model['elements']], axis=0) + np.max([e['to'] for e in model['elements']], axis=0)) / 2
    for frame in range(16):
        yaw, pitch = math.radians(-25 + frame*22.5), math.radians(15)
        def project(v):
            x,y,z = np.asarray(v)-centre
            x,z = x*math.cos(yaw)+z*math.sin(yaw), -x*math.sin(yaw)+z*math.cos(yaw)
            y,z = y*math.cos(pitch)-z*math.sin(pitch), y*math.sin(pitch)+z*math.cos(pitch)
            return np.array([128+x*scale*16,128-y*scale*16,z])
        image = render_model(model, {'plate':np.array(source()[0])}, size=(256,256), projector=project, transparent=True)
        image.save(dest/f'{slot}-{frame}.png')
for name in ['ingot','board','leather','cloth','cut_stone','affix_dust']:
    shutil.copy2(pack / f'forge_materials/{name}.png',dest/f'{name}.png')
for name in ['sword_t2_hero','sword_t2_thumb','forge_environment','world','hammer']:
    shutil.copy2(root/f'assets/ui/polish05-import/assets/images/{name}.png',dest/f'{name}.png')
print('Review assets prepared from approved ProjectS sources:', len(list(dest.glob('*.png'))))
