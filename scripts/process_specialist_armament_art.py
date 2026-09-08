"""Approved source-specific cleanup of bow, mace, tome and astrolabe artwork.

Separate from the first three weapons so a new palette cannot change their art.
This exports pixel textures and articulation layers, not a runtime animation.
"""
import hashlib
import json
from pathlib import Path
import numpy as np
from PIL import Image, ImageDraw
from process_armament_art import pixelize, projected_box

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT/'assets/class-armaments/texture-first'
OUT = SOURCE/'processed-specialists-v01'
JOBS = {
    'bow': {'source':'sources/bow-v01.png', 'height':92,
        'sha256':'a2083526f39f4bbbb2b96c8f4fe72e475bd08628340f582fc0197bc86594d17b'},
    'mace': {'source':'sources/mace-v02.png', 'height':84,
        'sha256':'612191609ce1ae0bb9c6d6a4bfc65af88ce2c7f1d762ad41d9773c163c643def'},
    'tome': {'source':'sources/tome-v01.png', 'height':48,
        'sha256':'ada12adfd77e81fd12a18d31f165998c273acac70c17183fac11b0a574444c04'},
    'astrolabe': {'source':'sources/astrolabe-v01.png', 'height':92,
        'sha256':'d2f02051377ccd8925298b665524d547f03090903ed2ca264e74f409a63e0b3e'},
}


def extract(image):
    """These four RGB originals have neutral light checker backgrounds.

    Unlike the first batch, retain pale parchment and lilac stars. Inspected
    foreground is chromatic even when bright; the background is near-neutral.
    The lower dark threshold avoids admitting the tome's darker checker squares.
    """
    if image.mode != 'RGB':
        raise ValueError('Reinspect a changed source mode before extracting')
    rgb = np.asarray(image).astype(np.int16)
    mask = (rgb.max(2)-rgb.min(2) > 18) | (rgb.max(2) < 95)
    if not mask.any() or mask.all():
        raise ValueError('Invalid source-specific foreground')
    result = np.zeros((*mask.shape,4),dtype=np.uint8)
    result[mask,:3] = rgb[mask]
    result[mask,3] = 255
    return Image.fromarray(result)


def region(pixels, transform, box):
    x0,y0,x1,y1 = projected_box(box,transform)
    h,w = pixels.shape[:2]
    mask = np.zeros((h,w),dtype=bool)
    mask[max(0,y0):min(h,y1),max(0,x0):min(w,x1)] = True
    return mask & (pixels[:,:,3]>0)


def material_palette(pixels, count=16):
    """Deterministic farthest-color seeds preserve small gems and page sigils.

    Population-only median cut spent several entries on near-identical ivory
    pixels and replaced the small lilac star with beige. Color-separated seeds
    followed by weighted clustering retain those distinct material accents.
    """
    mask = pixels[:,:,3]>0
    colors,weights = np.unique(pixels[mask,:3],axis=0,return_counts=True)
    values = colors.astype(float)
    centers = [values[weights.argmax()]]
    for _ in range(min(count,len(colors))-1):
        distance = ((values[:,None,:]-np.array(centers)[None,:,:])**2).sum(2).min(1)
        centers.append(values[distance.argmax()])
    centers = np.array(centers)
    for _ in range(20):
        assigned = ((values[:,None,:]-centers[None,:,:])**2).sum(2).argmin(1)
        updated = centers.copy()
        for i in range(len(centers)):
            chosen = assigned==i
            if chosen.any(): updated[i] = np.average(values[chosen],axis=0,weights=weights[chosen])
        if np.allclose(centers,updated): break
        centers = updated
    palette = np.unique(centers.round().astype(np.uint8),axis=0)
    result = pixels.copy()
    indices = ((pixels[mask,:3].astype(float)[:,None,:]-palette[None,:,:])**2).sum(2).argmin(1)
    result[mask,:3] = palette[indices]
    return result,palette.tolist()


def layers(key, pixels, transform):
    """Disjoint original pixels plus an explicit backing under the two pages.

    Bow string is intentionally absent from the painting. It must connect to
    articulated limb tips in the native model, not be painted into a rigid bow.
    """
    opaque = pixels[:,:,3]>0
    rgb = pixels[:,:,:3].astype(int)
    if key == 'bow':
        upper = region(pixels,transform,[0,0,793,790])
        lower = region(pixels,transform,[0,1220,793,1983])
        masks = {'upper_limb':upper,'grip':opaque & ~upper & ~lower,'lower_limb':lower}
    elif key == 'mace':
        crystal = region(pixels,transform,[350,435,445,560]) & (rgb[:,:,1]>rgb[:,:,0]*1.4)
        masks = {'body':opaque & ~crystal,'crystal':crystal}
    elif key == 'tome':
        warm = (rgb[:,:,0]>rgb[:,:,1]) & (rgb[:,:,1]>rgb[:,:,2]*1.12)
        left = region(pixels,transform,[105,213,594,944]) & warm
        right = region(pixels,transform,[658,213,1148,944]) & warm
        masks = {'binding_cover':opaque & ~left & ~right,'left_page':left,'right_page':right}
    elif key == 'astrolabe':
        star = region(pixels,transform,[385,342,533,508])
        ring = region(pixels,transform,[0,0,887,648]) & ~star
        masks = {'shaft':opaque & ~star & ~ring,'ring':ring,'star':star}
    else:
        raise ValueError('Unknown weapon')
    result = {}
    for name,mask in masks.items():
        if not mask.any():
            raise ValueError(f'{key}: empty {name}')
        layer = np.zeros_like(pixels)
        layer[mask] = pixels[mask]
        result[name] = layer
    if key == 'tome':
        # A physical cover must still exist underneath lifted pages. Use an
        # existing dark leather color, not a second copy of the page illustration.
        backing = np.zeros_like(pixels)
        leather = pixels[opaque & (rgb[:,:,0]>rgb[:,:,1]*1.3) & (rgb.max(2)<100),:3]
        if not len(leather): raise ValueError('Missing dark leather palette color')
        colors,counts = np.unique(leather,axis=0,return_counts=True)
        backing[left|right] = [*colors[counts.argmax()].tolist(),255]
        result['page_backing'] = backing
    return result


def convert(key):
    job = JOBS[key]
    path = SOURCE/job['source']
    digest = hashlib.sha256(path.read_bytes()).hexdigest()
    if digest != job['sha256']: raise ValueError(f'{key}: original changed')
    with Image.open(path) as original:
        mode,size = original.mode,original.size
        pixels,transform = pixelize(extract(original),job['height'])
    pixels,palette = material_palette(pixels)
    parts = layers(key,pixels,transform)
    entry = {'source':job['source'],'source_sha256':digest,'source_mode':mode,
             'source_size':list(size),**transform,'palette':palette,
             'height':round(job['height']*30/92,6),
             'parts':{k:int((v[:,:,3]>0).sum()) for k,v in parts.items()}}
    return pixels,parts,entry


def build():
    OUT.mkdir(parents=True,exist_ok=True)
    manifest = {'status':'pixel-textures-and-articulation-layers-only',
        'runtime_applied':False,'native_models_exported':False,
        'authorization':'User approved Python transparency, pixel resolution and palette reduction.',
        'source_tool':'built-in image_gen; no CLI/API fallback','weapons':{}}
    sheet = Image.new('RGB',(1200,560),'#28232e')
    draw = ImageDraw.Draw(sheet)
    for col,key in enumerate(JOBS):
        pixels,parts,entry = convert(key)
        manifest['weapons'][key] = entry
        Image.fromarray(pixels).save(OUT/f'{key}.png')
        for name,array in parts.items(): Image.fromarray(array).save(OUT/f'{key}-{name}.png')
        sprite = Image.fromarray(pixels)
        sprite = sprite.crop(sprite.getbbox())
        zoom = sprite.resize((sprite.width*4,sprite.height*4),Image.Resampling.NEAREST)
        sheet.paste(zoom,(col*300+(300-zoom.width)//2,60+(400-zoom.height)//2),zoom)
        draw.text((col*300+16,20),f'{key} / {entry["content_size"]}',fill='#e5d7be')
        draw.text((col*300+16,490),'16 colors / actual RGBA',fill='#c3b9aa')
    (OUT/'manifest.json').write_text(json.dumps(manifest,indent=2)+'\n',encoding='utf-8')
    qa = ROOT/'.tools/texture-first-specialists'; qa.mkdir(parents=True,exist_ok=True)
    sheet.save(qa/'pixel-review.png')
    print(json.dumps(manifest))


if __name__ == '__main__': build()
