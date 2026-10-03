"""Isolated hand-pixel texture studies; never touches active pack, server, or client."""
from pathlib import Path
import json, copy, hashlib
from PIL import Image, ImageDraw, ImageFont

repo = Path(__file__).resolve().parent.parent
source_pack = repo / 'server-minestom/src/main/resources/core-ui-pack'
output = repo / 'assets/model-lab/infusion-particle-texture-review'
output.mkdir(parents=True, exist_ok=True)

# Explicit authored pixel clusters, not an image generator or Gaussian/noise field.
# The TC6 54:24 still establishes only a bright core and irregular translucent rim.
# It does not establish the source particle atlas, transfer motion, or particle role.
layouts = {
    'a-core-mote': [
        '................', '................', '......11........', '....112211......',
        '...12233211.....', '...123443211....', '..1234554321....', '..12345543211...',
        '...1234433211...', '....123332211...', '.....1222111....', '......11111.....',
        '........11......', '................', '................', '................',
    ],
    'b-lobed-mist': [
        '................', '................', '....11..........', '...12211........',
        '..12332211......', '..1234433211....', '..12345443221...', '...23455433221..',
        '...12344322321..', '....123322321...', '.....12222211...', '......112211....',
        '........11......', '................', '................', '................',
    ],
}
inks = {'.': (0, 0, 0, 0), '1': (178, 178, 178, 20), '2': (192, 192, 192, 72),
        '3': (210, 210, 210, 152), '4': (240, 240, 240, 216), '5': (255, 255, 255, 252)}
original_path = source_pack / 'assets/projects/textures/infusion/smoke.png'
original = Image.open(original_path).convert('RGBA')
original_sha = hashlib.sha256(original_path.read_bytes()).hexdigest()
model_base = json.loads((source_pack / 'assets/projects/models/infusion/smoke.json').read_text())
item_base = json.loads((source_pack / 'assets/projects/items/infusion/smoke.json').read_text())
sprites = {'current-smoke': original}
records = []
for name, rows in layouts.items():
    assert len(rows) == 16 and all(len(row) == 16 for row in rows)
    im = Image.new('RGBA', (16, 16))
    for y, row in enumerate(rows):
        for x, key in enumerate(row): im.putpixel((x, y), inks[key])
    assert all(im.getpixel((x, y))[3] == 0 for x, y in [(x, 0) for x in range(16)] + [(x, 15) for x in range(16)] + [(0, y) for y in range(16)] + [(15, y) for y in range(16)])
    candidate = output / name
    identifier = 'infusion-texture-review/' + name
    model = copy.deepcopy(model_base)
    model['textures']['cloud']['sprite'] = 'projects:' + identifier
    model['textures']['particle'] = 'projects:' + identifier
    assert model['elements'] == model_base['elements']
    item = copy.deepcopy(item_base)
    item['model']['model'] = 'projects:' + identifier
    for kind, value in [('textures', im), ('models', model), ('items', item)]:
        destination = candidate / 'assets/projects' / kind / (identifier + ('.png' if kind == 'textures' else '.json'))
        destination.parent.mkdir(parents=True, exist_ok=True)
        if kind == 'textures': value.save(destination)
        else: destination.write_text(json.dumps(value, indent=2) + '\n')
    sprites[name] = im
    alpha = [p[3] for p in im.get_flattened_data()]
    records.append({'name': name, 'size': [16, 16], 'nontransparent_pixels': sum(a > 0 for a in alpha),
                    'max_alpha': max(alpha), 'alpha_values': sorted(set(alpha)), 'alpha_sum': sum(alpha),
                    'bright_core_pixels': sum(a == 252 for a in alpha), 'billboard_geometry_uv_tint_unchanged': True})

def tinted(im, rgb):
    r, g, b = rgb >> 16 & 255, rgb >> 8 & 255, rgb & 255
    result = im.copy()
    result.putdata([(round(R*r/255), round(G*g/255), round(B*b/255), A) for R, G, B, A in im.get_flattened_data()])
    return result

font = ImageFont.truetype(r'C:\Windows\Fonts\meiryo.ttc', 16)
small_font = ImageFont.truetype(r'C:\Windows\Fonts\meiryo.ttc', 13)
sheet = Image.new('RGB', (1050, 790), '#16191e')
draw = ImageDraw.Draw(sheet)
draw.text((22, 12), 'TEXTURE-ONLY STUDY / CURRENT vs A COMPACT CORE vs B LOBED MIST', font=font, fill='#ece6d6')
draw.text((22, 40), 'Hand pixels after TC6 54:24 reference review. No core-halo, timing, path, count or live-pack change.', font=small_font, fill='#b6bac3')
draw.text((22, 62), 'Static inspection only. Sizes are screen pixels; model geometry and existing aspect tints are identical.', font=small_font, fill='#b6bac3')
titles = ['Current smoke', 'A / core mote', 'B / lobed mist']
for col, (name, im) in enumerate(sprites.items()):
    x = 22 + col * 345
    draw.text((x, 98), titles[col], font=font, fill='#e5decd')
    for row, (label, bg, tint) in enumerate([
        ('Neutral / dark stone', (37, 34, 43), 0xffffff),
        ('Current TIDE / daylight', (111, 110, 108), 0x84b6bd),
        ('Current core small-mote tint / dark', (37, 34, 43), 0xb872f1),
    ]):
        y = 135 + row * 192
        draw.text((x, y), label, font=small_font, fill='#c3c2c6')
        slab = Image.new('RGBA', (317, 150), (*bg, 255))
        particle = tinted(im, tint)
        for size, px in [(8, 12), (16, 38), (24, 77), (48, 123), (112, 195)]:
            sample = particle.resize((size, size), Image.Resampling.NEAREST)
            slab.alpha_composite(sample, (px, 16))
        sheet.paste(slab.convert('RGB'), (x, y + 25))
        draw.text((x + 9, y + 149), '8px   16px    24px     48px        7x detail', font=small_font, fill='#bbb8c0')
    # Preserve exact existing aspect tints, compare texture shape only.
    y = 726
    draw.text((x, y - 24), 'Same 16px sprite / EMBER, TIDE, GALE, purple', font=small_font, fill='#b8bac0')
    for i, rgb in enumerate([0xee9850, 0x84b6bd, 0xc6d6a0, 0xb872f1]):
        tile = Image.new('RGBA', (64, 44), (42, 38, 48, 255))
        tile.alpha_composite(tinted(im, rgb).resize((32, 32), Image.Resampling.NEAREST), (16, 6))
        sheet.paste(tile.convert('RGB'), (x + i * 74, y))
sheet.save(output / 'current-vs-texture-candidates.png')
assert hashlib.sha256(original_path.read_bytes()).hexdigest() == original_sha
manifest = {
    'status': 'Two isolated static sprite candidates, not applied or user-approved',
    'reference': 'https://jaguidesminecraft.com/wp-content/uploads/2026/07/Thaumcraft-6-Full-Tutorial-1.12.2_at_54-24.png.webp',
    'reference_limits': 'Single TC6 1.12.2 tutorial still: role and motion unknown, actual TC6 atlas unavailable. TC7 and shader lightning are not design evidence here.',
    'approach': 'Small bright asymmetric core, clustered semi-transparent body, faint irregular rim. Explicit 16x16 hand-pixel map; no Gaussian field, imagegen, noise or borrowed sprite.',
    'source_smoke_sha256': original_sha,
    'source_alpha_sum': sum(p[3] for p in original.get_flattened_data()),
    'candidates': records,
    'not_changed': ['main core halo/mesh/brightness', 'world display/UV repairs', 'live pack', 'server/client', 'save', 'counts', 'paths', 'rhythm', 'aspect tints'],
    'application_limits': 'Do not overwrite shared infusion/smoke without deciding affected roles. Assets have distinct identifiers and no live pack index/atlas registration. No server mapping changes are included.',
}
(output / 'candidate-manifest.json').write_text(json.dumps(manifest, indent=2) + '\n')
print(json.dumps({'output': str(output), 'candidates': records, 'original_unchanged': True}))
