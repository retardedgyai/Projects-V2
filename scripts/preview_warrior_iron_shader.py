"""Usage: python3 scripts/preview_warrior_iron_shader.py <repo root> .tools/preview/shader-compare.gif [slow]
Run CoreSkillChoreographyTest's timeline export and scripts/build_core_combat_models.py first.

Shader preview: the actual exported timeline + shipped warrior_iron models, lit like Minecraft at night,
rendered twice: vanilla item shading vs scripts/shaders/item.fsh (same marker test, hash, flicker and halo maths).
Still a CPU projection, not the game client (no fog, no bloom, approximate directional light)."""
import json, math, sys
from functools import lru_cache
from pathlib import Path
import numpy as np
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(sys.argv[1]); OUT = sys.argv[2]
PACK = ROOT / 'server-minestom/src/main/resources/core-ui-pack/assets/projects'
FONT = ImageFont.truetype('/usr/share/fonts/opentype/noto/NotoSansCJK-Bold.ttc', 15, index=0)
W, H = 480, 300
NIGHT = 1.0                                   # CoreCombatMeshes forces brightness 15 on every effect display
L0 = np.array((.2, 1.0, -.7)); L0 /= np.linalg.norm(L0)
L1 = np.array((-.2, 1.0, .7)); L1 /= np.linalg.norm(L1)
palette = np.array(Image.open(PACK / 'textures/combat_vfx/warrior_iron/palette.png').convert('RGBA'))[0].astype(float)


@lru_cache(maxsize=2048)
def model(key):
    item = json.loads((PACK / f'items/{key}.json').read_text())['model']
    return json.loads((PACK / ('models/' + item['model'].split(':')[1] + '.json')).read_text())


VIEW = 'eye'


def project(p):
    x, y, z = p
    if VIEW == 'iso':
        # Over-the-shoulder from behind and above the left shoulder.
        cx, cy, cz = -3.6, 4.2, -3.4
        fx, fy, fz = .45, -.5, .74
        fl = math.sqrt(fx * fx + fy * fy + fz * fz); fx, fy, fz = fx / fl, fy / fl, fz / fl
        rx, ry, rz = -fz, 0.0, fx                      # screen right (Minecraft +x is the wielder's left)
        rl = math.sqrt(rx * rx + rz * rz); rx, rz = rx / rl, rz / rl
        ux, uy, uz = ry * fz - rz * fy, rz * fx - rx * fz, rx * fy - ry * fx
        dx, dy, dz = x - cx, y - cy, z - cz
        depth = dx * fx + dy * fy + dz * fz
        if depth < .1: return None
        return (W / 2 + (dx * rx + dy * ry + dz * rz) / depth * 330, H / 2 - (dx * ux + dy * uy + dz * uz) / depth * 330, depth)
    # Eye at 1.62 m behind the wielder looking forward, like the review 'eye' view.
    depth = z + .7
    if depth < .05: return None
    return (W / 2 - x / depth * 210, H / 2 + (1.62 - y) / depth * 210, depth)


def vhash(cell):
    v = math.sin(cell[0] * 12.9898 + cell[1] * 78.233 + cell[2] * 37.719) * 43758.5453
    return v - math.floor(v)


def shade(texel, normal, centre, tick, shader):
    r, g, b, a = texel
    marker = int(round(a))
    if shader and marker in (253, 121, 65):
        n = vhash(tuple(math.floor(c * 8) for c in centre))
        t = tick
        flicker = 1 + .12 * math.sin(t * 1.9 + n * 6.2832) + .06 * math.sin(t * 5.3 + n * 17.0)
        rgb = np.array((r, g, b)) / 255 * flicker
        alpha = 1.0
        if marker != 253:
            rgb = (rgb * .65 + np.array((1.0, .56, .24)) * .35) * 1.45
            alpha = (.6 if marker == 121 else .34) * (.85 + .15 * math.sin(t * 2.3 + n * 6.2832))
        return tuple(int(c) for c in np.clip(rgb, 0, 1) * 255), alpha
    light = min(1.0, (max(0, L0 @ normal) + max(0, L1 @ normal)) * .6 + .4)
    rgb = np.array((r, g, b)) / 255 * light * NIGHT
    return tuple(int(c) for c in np.clip(rgb, 0, 1) * 255), a / 255


def faces_of(part):
    m = model(part['model'])
    pitch, yaw, roll = part['pitch'], part['yaw'], part['roll']
    sx, sy, sz = part['scale']; ox, oy, oz = part['offset']
    def tf(v):
        x, y, z = (v[0] - 8) / 16 * sx, (v[1] - 8) / 16 * sy, (v[2] - 8) / 16 * sz
        y, z = y * math.cos(pitch) - z * math.sin(pitch), y * math.sin(pitch) + z * math.cos(pitch)
        x, y = x * math.cos(roll) - y * math.sin(roll), x * math.sin(roll) + y * math.cos(roll)
        x, z = x * math.cos(yaw) + z * math.sin(yaw), -x * math.sin(yaw) + z * math.cos(yaw)
        return np.array((x + ox, y + oy, z + oz))
    out = []
    for e in m['elements']:
        lo, hi = e['from'], e['to']
        rot = e.get('rotation')
        def ep(v):
            if not rot: return v
            ang = math.radians(rot['angle']); o = rot['origin']
            if rot['axis'] == 'x':
                y, z = v[1] - o[1], v[2] - o[2]
                return [v[0], o[1] + y * math.cos(ang) - z * math.sin(ang), o[2] + y * math.sin(ang) + z * math.cos(ang)]
            x, y = v[0] - o[0], v[1] - o[1]
            return [o[0] + x * math.cos(ang) - y * math.sin(ang), o[1] + x * math.sin(ang) + y * math.cos(ang), v[2]]
        verts = [tf(ep([hi[j] if i & (1 << j) else lo[j] for j in range(3)])) for i in range(8)]
        for name, ids in (('north', (3, 2, 0, 1)), ('south', (6, 7, 5, 4)), ('down', (4, 5, 1, 0)),
                          ('up', (2, 3, 7, 6)), ('west', (2, 6, 4, 0)), ('east', (7, 3, 1, 5))):
            if name not in e['faces']: continue
            u = e['faces'][name]['uv']
            texel = palette[int(min(u[0], u[2]))]
            world = [verts[i] for i in ids]
            nrm = np.cross(world[1] - world[0], world[2] - world[0]); ln = np.linalg.norm(nrm)
            nrm = nrm / ln if ln > 1e-9 else np.array((0, 1, 0.))
            out.append((world, texel, nrm))
    return out


def body(d):
    # The wielder, as a simple 1.8 m silhouette, for scale in the over-the-shoulder view.
    for (w, lo, hi), col in (((.5, 0, 1.4), (60, 66, 80)), ((.42, 1.4, 1.82), (170, 140, 120))):
        q = [project(v) for v in ((-w / 2, lo, 0), (w / 2, lo, 0), (w / 2, hi, 0), (-w / 2, hi, 0))]
        if all(q): d.polygon([v[:2] for v in q], fill=col)


def render(parts, tick, shader, label):
    im = Image.new('RGBA', (W, H), (8, 10, 18, 255))
    d = ImageDraw.Draw(im, 'RGBA')
    for n in range(-6, 7):                         # dim night ground grid
        a, b = project((n, 0, 0)), project((n, 0, 12))
        if a and b: d.line([a[:2], b[:2]], fill=(26, 30, 40))
    for k in range(0, 13):
        a, b = project((-6, 0, k)), project((6, 0, k))
        if a and b: d.line([a[:2], b[:2]], fill=(26, 30, 40))
    polys = []
    for p in parts:
        for world, texel, nrm in faces_of(p):
            pr = [project(v) for v in world]
            if any(q is None for q in pr): continue
            area = sum(a[0] * b[1] - b[0] * a[1] for a, b in zip(pr, pr[1:] + pr[:1]))
            if area >= -1e-7: continue
            centre = sum(world) / 4
            colour, alpha = shade(texel, nrm, centre, tick, shader)
            polys.append((sum(q[2] for q in pr) / 4, [q[:2] for q in pr], colour + (int(alpha * 255),)))
    if VIEW == 'iso':
        polys.append((project((0, 1, 0))[2], None, None))
    for _, pts, c in sorted(polys, key=lambda v: -v[0]):
        if pts is None: body(d)
        else: d.polygon(pts, fill=c)
    d.text((10, 8), label, font=FONT, fill=(235, 230, 220))
    d.text((W - 70, H - 24), f'{tick / 20:.2f}s', font=FONT, fill=(170, 170, 170))
    return im.convert('RGB')


scenes = {s['id']: s for s in json.loads((ROOT / '.tools/skill-choreography-frames.json').read_text())}
ids = ['slam', 'whirl']
names = {'slam': '地割り', 'whirl': '押し薙ぎ'}
end = max(len(scenes[i]['frames']) for i in ids) + 6
SLOW = len(sys.argv) > 3 and sys.argv[3] == 'slow'
frames = []
for t in range(end):
    sheet = Image.new('RGB', (W * 2, H * 4 + 34), (0, 0, 0))
    ImageDraw.Draw(sheet).text((10, 8), ('【1/2スロー】' if SLOW else '【実時間】') +
        '夜・演出は明るさ15固定／左：今のまま　右：シェーダーあり　※CPUでの再現、ゲーム画面ではない', font=FONT, fill=(220, 214, 200))
    row = 0
    for i in ids:
        for VIEW in ('eye', 'iso'):
            globals()['VIEW'] = VIEW
            parts = scenes[i]['frames'][t] if t < len(scenes[i]['frames']) else []
            tag = '一人称' if VIEW == 'eye' else '斜め後ろ上'
            sheet.paste(render(parts, t, False, f'{names[i]}（{tag}）シェーダーなし'), (0, 34 + row * H))
            sheet.paste(render(parts, t, True, f'{names[i]}（{tag}）シェーダーあり'), (W, 34 + row * H))
            row += 1
    frames.append(sheet.resize((sheet.width * 3 // 4, sheet.height * 3 // 4), Image.LANCZOS))
durations = [100 if SLOW else 50] * len(frames)
durations[-1] = 700                                   # hold before the loop restarts
frames[0].save(OUT, save_all=True, append_images=frames[1:], duration=durations, loop=0, disposal=1)
frames[4].save(OUT.replace('.gif', '_t4.png'))
print(len(frames), 'frames')
