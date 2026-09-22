"""Source-specific, authorized pixel cleanup of the direct-reference redraw.

Keeps native facet colors rather than mapping gray metal into the old purple
family palette. No other weapon/Tier source or runtime pack is changed.
"""
import hashlib
import json
from copy import deepcopy
import numpy as np
from PIL import Image, ImageDraw

from process_armament_art import ROOT, pixelize, projected_box
from build_texture_first_sword import compile_model
from pixel_weapon_display import grip_pixels, rotation_xyz, transformed

SOURCE = ROOT / 'assets/class-armaments/texture-first/sources/greatsword-material-v02.png'
OUT = ROOT / 'assets/class-armaments/texture-first/processed-material-v02'
DIGEST = '5962fb645e63ec851d64fcfff32d281291edea1cf87d59a5a443899aa305f3e3'
PIXEL_HEIGHT = 80


def convert():
    if hashlib.sha256(SOURCE.read_bytes()).hexdigest() != DIGEST:
        raise ValueError('Reinspect changed redraw source before processing')
    with Image.open(SOURCE) as image:
        if image.mode != 'RGB' or image.size != (1024, 1536):
            raise ValueError('Unexpected redraw format')
        rgb = np.array(image).astype(np.int16)
    # Inspected reference redraw: neutral background is brighter than its metal.
    # Include gray facets that the previous <135 threshold would have clipped.
    mask = (rgb.max(2) < 170) | (rgb.max(2) - rgb.min(2) > 40)
    rgba = np.zeros((*mask.shape, 4), np.uint8)
    rgba[mask, :3] = rgb[mask]
    rgba[mask, 3] = 255
    pixels, transform = pixelize(Image.fromarray(rgba), PIXEL_HEIGHT)
    # Source-specific jewel outline. A rectangle alone also selects the blade.
    outline = ((525, 827), (592, 882), (544, 947), (482, 886))
    selection = Image.new('1', (pixels.shape[1], pixels.shape[0]))
    points = [tuple(projected_box([x, y, x, y], transform)[:2]) for x, y in outline]
    ImageDraw.Draw(selection).polygon(points, fill=1)
    color = pixels[:, :, :3].astype(int)
    selected = np.array(selection) & (pixels[:, :, 3] > 0) & (color[:, :, 0] > color[:, :, 1] * 1.5)
    if selected.sum() < 10:
        raise ValueError('Redraw jewel not captured')
    jewel = np.zeros_like(pixels)
    jewel[selected] = pixels[selected]
    body = pixels.copy()
    body[selected] = [35, 37, 45, 255]  # Dark graphite socket, not a second stone.
    entry = {**transform, 'source': str(SOURCE.relative_to(ROOT)), 'source_sha256': DIGEST,
             'height': 30.0, 'rows': [2] + [projected_box([0, y, 0, y], transform)[1]
                 for y in (740, 1045)] + [PIXEL_HEIGHT + 2],
             'jewel_pixels': int(selected.sum()), 'palette_remap': False,
             'colors': len(np.unique(pixels[pixels[:, :, 3] > 0, :3], axis=0)),
             'quality_approved': False,
             'ember_emitters': [[2, 0, 0, 10, 0, 4], [12, -1, 4, 13, 5, 7],
                               [22, 1, 10, 12, 4, 8], [31, -1, 15, 13, 5, 6],
                               [38, 1, 19, 12, 4, 7]]}
    return pixels, {'body': body, 'jewel': jewel}, entry


def build():
    pixels, textures, entry = convert()
    OUT.mkdir(parents=True, exist_ok=True)
    Image.fromarray(pixels).save(OUT / 'greatsword.png')
    for part, art in textures.items():
        Image.fromarray(art).save(OUT / f'greatsword-{part}.png')
    (OUT / 'manifest.json').write_text(json.dumps(entry, indent=2) + '\n', encoding='utf-8')
    print(json.dumps(entry))


def guard_depth(base, textures, entry):
    """The two painted graphite guard branches occupy different thin planes.

    Trace their actual gray pixels; do not rotate the red blade or extrude a
    rectangle over the fork's negative space. The hub/grip stay fixed.
    """
    body = textures['body']
    yy, xx = np.indices(body.shape[:2])
    rgb = body[:, :, :3].astype(int)
    pivot, _ = grip_pixels('greatsword', entry, textures)
    gray = (rgb.max(2) - rgb.min(2) < 40) & (rgb.max(2) < 170)
    guard = (body[:, :, 3] > 0) & (yy >= entry['rows'][1]) & (yy < entry['rows'][2])
    branches = guard & gray & (yy < 54)
    masks = {'guard_left': branches & (xx < pivot), 'guard_right': branches & (xx >= pivot)}
    masks['guard_hub'] = guard & ~branches
    scale = entry['height'] / (entry['rows'][-1] - entry['rows'][0])
    result = dict(base)
    result['elements'] = [e for e in base['elements'] if not e['name'].startswith('guard:')]
    for name, mask in masks.items():
        if not mask.any():
            raise ValueError('Missing graphite guard component: ' + name)
        spec = {'height': entry['height'], 'top_pixel': entry['rows'][0],
                'bottom_pixel': entry['rows'][-1], 'pivot_pixel_x': pivot,
                'alpha_cutoff': 128, 'texture': base['textures']['body'],
                'parts': [{'name': name, 'rows': [entry['rows'][0], entry['rows'][-1]],
                           'thickness': .5, 'trace_painted_faces': True}]}
        model, _ = compile_model(spec, np.where(mask, 255, 0).astype(np.uint8))
        for element in model['elements']:
            for face in element['faces'].values():
                face['texture'] = '#body'
            if name != 'guard_hub':
                element['rotation'] = {'axis': 'y', 'angle': -22 if name == 'guard_left' else 22,
                                       'origin': [8, (entry['rows'][-1] - 54) * scale, 8],
                                       'rescale': False}
        result['elements'].extend(model['elements'])
    return result


def approved_grip_point(textures, entry):
    """Authored bare handle above the lower ornaments, not their pixel-weighted median."""
    px, py = 10.5, 63.5
    if textures['body'][int(py), int(px), 3] != 255:
        raise ValueError('Approved grip point no longer lies on painted handle')
    pivot, _ = grip_pixels('greatsword', entry, textures)
    scale = entry['height'] / entry['content_size'][1]
    return np.array([8+(px-pivot)*scale, (entry['rows'][-1]-py)*scale, 8])


def approved_hand_display(model, textures, entry):
    """Match the painted grip of the stock 26.2 iron sword, not its display translation.

    item/handheld's translation is NOT a hand socket. Its diagonal sprite's grip
    is near (3.5,3.5,8) in model coordinates. Our art is vertical: compensate
    its extra 45 degrees and put the bare handle at that same transformed point.
    No artwork, UV, geometry, GUI framing or other weapon changes.
    """
    result=deepcopy(model)
    grip=approved_grip_point(textures,entry)
    for context,angle,translation,scale,authored_scale in (
            ('firstperson',25,[1.13,3.2,1.13],.68,1.02),
            ('thirdperson',55,[0,4,.5],.85,1.16)):
        for hand,left in (('righthand',False),('lefthand',True)):
            native={'rotation':[0,90 if left else -90,-angle if left else angle],
                    'translation':translation,'scale':[scale]*3}
            target=transformed([3.5,3.5,8],native,left)
            authored=[0,90 if left else -90,-(angle-45) if left else angle-45]
            actual=np.array(authored,dtype=float)
            if left: actual[1:]*=-1
            offset=target-rotation_xyz(actual)@((grip-8)*authored_scale)
            if left: offset[0]*=-1
            result['display'][context+'_'+hand]={'rotation':authored,
                'translation':offset.round(6).tolist(),'scale':[authored_scale]*3}
    return result


if __name__ == '__main__':
    build()
