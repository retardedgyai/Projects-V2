"""Isolated three-dimensional Ashen Knight head blockout.

The mask, hood, and scarf are shaped together here before any new face image
is approved for the production boss model. Run directly to write a bbmodel in
model-lab/build/head-v3/.
"""

from pathlib import Path
import sys
import math
from PIL import Image, ImageEnhance

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))
from build_ashen_knight import KnightModel  # noqa: E402


def build(out=ROOT / "model-lab" / "build" / "head-v3"):
    out.mkdir(parents=True, exist_ok=True)
    m = KnightModel("ashen_head_v3", size=512, texels=6)
    pieces = []

    def swatch(color, label):
        seed = sum((i + 1) * ord(ch) for i, ch in enumerate(label))

        def paint(px, py):
            grain = ((px * 43 + py * 71 + seed) ^
                     (px * py * 17 + seed // 3)) % 11
            fiber = (px + 2 * py + seed) % 9
            shift = (-3 if grain == 0 else 3 if grain == 10 else 0)
            if label.startswith(("cowl", "mane")) and fiber == 2:
                shift += 2
            return (*(max(0, min(255, channel + shift)) for channel in color), 255)

        uv = m.patch(8, 8, paint, label)
        return {face: uv for face in
                ("north", "south", "east", "west", "up", "down")}

    hood_swatches = [swatch(color, f"hood_{i}") for i, color in enumerate(
        ((9, 13, 18), (13, 18, 23), (17, 21, 25), (23, 27, 30)))]
    cloth_swatches = [swatch(color, f"cowl_{i}") for i, color in enumerate(
        ((7, 13, 21), (10, 18, 28), (14, 23, 34), (17, 27, 38)))]

    def emit_surface_voxels(label, occupied, step, origin, swatches):
        offsets = ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0),
                   (0, 0, 1), (0, 0, -1))
        surface = {cell for cell in occupied
                   if any(tuple(cell[a] + delta[a] for a in range(3))
                          not in occupied for delta in offsets)}

        def shade(cell):
            i, j, k = cell
            return ((i // 3) * 73 + (j // 3) * 31 + (k // 3) * 47) % len(swatches)

        while surface:
            i, j, k = min(surface)
            color = shade((i, j, k))
            dx = 1
            while dx < 3 and (i + dx, j, k) in surface and shade((i + dx, j, k)) == color:
                dx += 1
            dy = 1
            while dy < 3 and all((i + a, j + dy, k) in surface
                                 and shade((i + a, j + dy, k)) == color
                                 for a in range(dx)):
                dy += 1
            dz = 1
            while dz < 3 and all((i + a, j + b, k + dz) in surface
                                 and shade((i + a, j + b, k + dz)) == color
                                 for a in range(dx) for b in range(dy)):
                dz += 1
            for a in range(dx):
                for b in range(dy):
                    for c in range(dz):
                        surface.remove((i + a, j + b, k + c))
            x, y, z = (origin[0] + i * step[0],
                       origin[1] + j * step[1],
                       origin[2] + k * step[2])
            m.cube(f"{label}_{i}_{j}_{k}", [x, y, z],
                   [x + dx * step[0], y + dy * step[1], z + dz * step[2]],
                   "void", pieces, face_uv=swatches[color])

    hood_step = (.23, .23, .23)
    hood_origin = (-1.61, 23.35, -1.38)
    hood = set()
    for i in range(14):
        for j in range(21):
            for k in range(14):
                x = hood_origin[0] + (i + .5) * hood_step[0]
                y = hood_origin[1] + (j + .5) * hood_step[1]
                z = hood_origin[2] + (k + .5) * hood_step[2]
                shape_value = ((x / 1.38) ** 2 + ((y - 25.6) / 2.2) ** 2
                               + ((z - .2) / 1.37) ** 2)
                face_open = (z < -.56 and 24.0 < y < 27.55
                             and abs(x) < 1.08)
                if shape_value < 1 and not face_open:
                    hood.add((i, j, k))
    emit_surface_voxels("hood", hood, hood_step, hood_origin, hood_swatches)

    # A recessed dark face and cheek volume joins the sculpted iron to the
    # cowl. The eye openings then reveal shadow instead of empty background.
    cavity_step = (.23, .23, .23)
    cavity_origin = (-1.38, 23.65, -2.51)
    cavity = set()
    for i in range(12):
        for j in range(16):
            for k in range(13):
                x = cavity_origin[0] + (i + .5) * cavity_step[0]
                y = cavity_origin[1] + (j + .5) * cavity_step[1]
                z = cavity_origin[2] + (k + .5) * cavity_step[2]
                if ((x / 1.2) ** 2 + ((y - 25.43) / 1.64) ** 2
                        + ((z + 1.12) / 1.19) ** 2) < 1:
                    cavity.add((i, j, k))
    emit_surface_voxels("recessed_face", cavity, cavity_step,
                        cavity_origin, hood_swatches[:2])

    # Use the connected silhouette of the earlier hand-forged faceplate as a
    # small voxel relief. Horizontal position wraps the cheeks backward; the
    # forehead remains by the hood while the broken chin projects forward.
    source = Image.open(ROOT / "model-lab" / "references" /
                        "ashen_faceplate_source_v1.png").convert("RGBA")
    mask = source.resize((24, 36), Image.Resampling.LANCZOS)
    colors = ImageEnhance.Brightness(mask.convert("RGB")).enhance(.72)
    colors = colors.quantize(colors=20, method=Image.Quantize.MEDIANCUT).convert("RGB")
    tone_uv = {}
    for row in range(36):
        for col in range(24):
            if mask.getpixel((col, row))[3] < 95:
                continue
            tone = colors.getpixel((col, row))
            if tone not in tone_uv:
                tone_uv[tone] = m.patch(1, 1, lambda _x, _y, c=tone: (*c, 255),
                                        f"iron_pixel_{len(tone_uv)}")
            uv = tone_uv[tone]
            u = (col + .5) / 24 * 2 - 1
            v = (row + .5) / 36
            brow = math.exp(-((v - .32) / .2) ** 2)
            ridge = math.exp(-(u / .32) ** 2)
            front = (-1.67 - 1.45 * v - .32 * ridge * (1 - .35 * v)
                     - .17 * brow + .57 * abs(u) ** 1.7)
            if sum(tone) < 44:  # the dark eye cavities sit behind the iron
                front += .2
            left = -1.83 + col * (3.66 / 24)
            bottom = 28.32 - (row + 1) * (5.2 / 36)
            m.cube(f"visor_voxel_{row}_{col}",
                   [left, bottom, front],
                   [left + 3.66 / 24, bottom + 5.2 / 36, front + .28],
                   "edge", pieces,
                   face_uv={face: uv for face in
                            ("north", "south", "east", "west", "up", "down")})

    # Only the narrow nasal bridge has substantial depth; the cheeks stay
    # thin and turn into the hood. A full-width extrusion became a flat fin.
    bridge_step = (.18, .18, .18)
    bridge_origin = (-.72, 23.13, -3.63)
    bridge = set()
    bridge_swatches = [swatch(c, f"bridge_{i}") for i, c in enumerate(
        ((15, 18, 19), (22, 25, 25), (30, 31, 29), (39, 39, 35)))]
    for i in range(8):
        for j in range(21):
            for k in range(15):
                x = bridge_origin[0] + (i + .5) * bridge_step[0]
                y = bridge_origin[1] + (j + .5) * bridge_step[1]
                z = bridge_origin[2] + (k + .5) * bridge_step[2]
                v = max(0, min(1, (28.32 - y) / 5.2))
                radius = .53 - .26 * v
                nose_front = -1.67 - 1.45 * v - .32 * (1 - .35 * v)
                if abs(x) < radius and nose_front + .05 < z < -1.33:
                    bridge.add((i, j, k))
    emit_surface_voxels("solid_nose_bridge", bridge, bridge_step,
                        bridge_origin, bridge_swatches)

    # Three nested, sagging courses make a cloth collar around the neck.
    # Each ring has a different pull and frayed edge rather than blue slabs.
    cowl_step = (.23, .21, .23)
    cowl_origin = (-2.76, 20.9, -2.07)
    courses = ((1.7, 1.11, 23.55, .46),
               (2.13, 1.42, 22.76, .53),
               (2.53, 1.72, 21.96, .62))
    for layer, (rx, rz, base_y, half_height) in enumerate(courses):
        occupied = set()
        for i in range(24):
            for j in range(17):
                for k in range(19):
                    x = cowl_origin[0] + (i + .5) * cowl_step[0]
                    y = cowl_origin[1] + (j + .5) * cowl_step[1]
                    z = cowl_origin[2] + (k + .5) * cowl_step[2]
                    r = math.sqrt((x / rx) ** 2 + (z / rz) ** 2)
                    front_sag = max(0, -z / rz)
                    centre = base_y - .35 * front_sag + .09 * x
                    torn = (i * 7 + k * 11 + layer * 13) % 23 == 0
                    if .72 < r < 1.02 and abs(y - centre) < half_height:
                        if not (torn and y < centre - .05):
                            occupied.add((i, j, k))
        emit_surface_voxels(f"cowl_{layer}", occupied, cowl_step,
                            cowl_origin, cloth_swatches)

    # Uneven locks are swept from the hood's crown into separated ends.
    # Their dark surface and irregular paths distinguish hair from plate ribs.
    hair_swatches = [swatch(c, f"mane_{i}") for i, c in enumerate(
        ((8, 11, 15), (12, 15, 18), (17, 19, 21), (24, 24, 23)))]
    hair_step = (.18, .18, .18)
    hair_origin = (-3.24, 22.0, .45)
    starts = ((-1.05, 27.15, .72), (-.74, 27.49, .72),
              (-.39, 27.76, .66), (0, 27.86, .64),
              (.39, 27.73, .68), (.77, 27.44, .75),
              (1.06, 27.09, .82), (-.48, 26.54, 1.13),
              (.48, 26.53, 1.13))
    ends = ((-2.74, 25.34, 4.51), (-1.91, 26.57, 5.28),
            (-2.17, 23.76, 4.75), (-.66, 27.14, 5.76),
            (.36, 24.3, 5.36), (1.55, 26.42, 4.88),
            (2.36, 24.86, 4.42), (-1.25, 22.94, 4.34),
            (1.52, 22.62, 4.32))
    hair = set()
    for strand, (start, end) in enumerate(zip(starts, ends)):
        control = ((start[0] + end[0]) * .49,
                   start[1] + (1.15 if strand % 3 == 0 else .36),
                   start[2] + (end[2] - start[2]) * .52)
        for section in range(65):
            t = section / 64
            point = tuple(((1 - t) ** 2 * start[a]
                           + 2 * (1 - t) * t * control[a]
                           + t * t * end[a]) for a in range(3))
            radius = .36 * (1 - t) ** .75 + .055
            indices = [round((point[a] - hair_origin[a]) / hair_step[a] - .5)
                       for a in range(3)]
            reach = math.ceil(radius / hair_step[0]) + 1
            for di in range(-reach, reach + 1):
                for dj in range(-reach, reach + 1):
                    for dk in range(-reach, reach + 1):
                        i, j, k = indices[0] + di, indices[1] + dj, indices[2] + dk
                        x = hair_origin[0] + (i + .5) * hair_step[0]
                        y = hair_origin[1] + (j + .5) * hair_step[1]
                        z = hair_origin[2] + (k + .5) * hair_step[2]
                        if ((x - point[0]) ** 2 + (y - point[1]) ** 2
                                + (z - point[2]) ** 2) < radius ** 2:
                            hair.add((i, j, k))
    emit_surface_voxels("mane", hair, hair_step, hair_origin, hair_swatches)

    head = m.bone("head", [0, 25, 0], pieces)
    m.anim("idle", 1, {"head": [(0, [0, 0, 0]), (1, [0, 0, 0])]})
    return m.write(out, head)


if __name__ == "__main__":
    build()
