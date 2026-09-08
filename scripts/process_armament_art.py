"""User-authorized pixel conversion of the three preserved imagegen originals.

This is a source-specific cleanup, not a general background remover. RGB dagger
and staff have a bright neutral checkerboard; their art is dark or saturated.
Median pooling only samples foreground, preventing checker colors at the edge.
No dithering, antialiasing, painted checkerboard, or upscaled runtime textures.
"""
import hashlib
import json
from pathlib import Path
import numpy as np
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT/'assets/class-armaments/texture-first'
OUT = SOURCE/'processed-v01'
JOBS = {
    'greatsword': {'source':'sword-material-study-v01.png', 'height':92, 'family':'crimson',
        'sha256':'05f6fc344fad0e9a2b34143276fac13fcecb1230c7fb36260c2752521e14c650',
        'split_y':[1300,1730], 'jewel_box':[286,1428,437,1609]},
    'dagger': {'source':'sources/dagger-v01.png', 'height':60, 'family':'crimson',
        'sha256':'094a1357ccdb9ec9e946efb7ef873878c0fa4e82b0ccdfacd9612625ed159193',
        'split_y':[938,1120], 'jewel_box':[408,893,503,1002]},
    'staff': {'source':'sources/staff-v01.png', 'height':92, 'family':'cyan',
        'sha256':'dc1b338a366eb175cd6d29efd0095b596c801ebac524722bc4af607174e448f0',
        'split_y':[754,1390], 'jewel_box':[480,250,574,440]},
}


def foreground(image):
    pixels = np.asarray(image.convert('RGBA')).copy()
    if image.mode == 'RGBA':
        mask = pixels[:,:,3] >= 192
    elif image.mode == 'RGB':
        rgb = pixels[:,:,:3].astype(np.int16)
        # Specific to these two inspected images. Do not use on pale weapons.
        mask = (rgb.max(2) < 135) | ((rgb.max(2)-rgb.min(2)) > 45)
    else:
        raise ValueError('Unsupported preserved source mode')
    if not mask.any() or mask.all(): raise ValueError('Invalid foreground extraction')
    return pixels[:,:,:3], mask


def pixelize(image, height):
    rgb, mask = foreground(image)
    yy,xx = np.nonzero(mask)
    box = (int(xx.min()),int(yy.min()),int(xx.max()+1),int(yy.max()+1))
    x0,y0,x1,y1 = box
    width = max(1,round((x1-x0)*height/(y1-y0)))
    canvas = (2**(width+3).bit_length(),2**(height+3).bit_length())
    pixels = np.zeros((canvas[1],canvas[0],4),dtype=np.uint8)
    x_edges = np.linspace(x0,x1,width+1).round().astype(int)
    y_edges = np.linspace(y0,y1,height+1).round().astype(int)
    for y in range(height):
        for x in range(width):
            selected = mask[y_edges[y]:y_edges[y+1],x_edges[x]:x_edges[x+1]]
            if selected.mean() < .4: continue
            colors = rgb[y_edges[y]:y_edges[y+1],x_edges[x]:x_edges[x+1]][selected]
            pixels[y+2,x+2,:3] = np.median(colors,axis=0).round().astype(np.uint8)
            pixels[y+2,x+2,3] = 255
    return pixels, {'source_crop':list(box),'content_size':[width,height],
                    'canvas_size':list(canvas),'padding':2}


def reduce_palette(arrays, count):
    """One palette per related weapon family; zero background color pollution."""
    colors = np.concatenate([a[a[:,:,3]>0,:3] for a in arrays])
    quant = Image.fromarray(colors.reshape(1,-1,3)).quantize(
        colors=count,method=Image.Quantize.MEDIANCUT,dither=Image.Dither.NONE)
    palette = np.array(quant.getpalette(),dtype=np.uint8).reshape(-1,3)
    used = np.unique(np.asarray(quant))
    palette = palette[used]
    result = []
    for original in arrays:
        a = original.copy()
        foreground_mask = a[:,:,3]>0
        values = a[foreground_mask,:3].astype(float)
        distances = ((values[:,None,:]-palette[None,:,:])**2).sum(2)
        a[foreground_mask,:3] = palette[distances.argmin(1)]
        result.append(a)
    return result, palette.tolist()


def projected_box(box, transform):
    x0,y0,x1,y1=transform['source_crop']; w,h=transform['content_size']
    return [round(2+(box[0]-x0)*w/(x1-x0)),round(2+(box[1]-y0)*h/(y1-y0)),
            round(2+(box[2]-x0)*w/(x1-x0)),round(2+(box[3]-y0)*h/(y1-y0))]


def build():
    OUT.mkdir(parents=True,exist_ok=True)
    images={}; transforms={}; hashes={}
    for key,job in JOBS.items():
        path=SOURCE/job['source']; hashes[key]=hashlib.sha256(path.read_bytes()).hexdigest()
        if job['sha256'] and hashes[key]!=job['sha256']: raise ValueError(f'{key}: source changed')
        with Image.open(path) as image: images[key],transforms[key]=pixelize(image,job['height'])
    palettes={}
    for family,count in (('crimson',16),('cyan',12)):
        keys=[k for k,j in JOBS.items() if j['family']==family]
        reduced,palettes[family]=reduce_palette([images[k] for k in keys],count)
        for key,array in zip(keys,reduced): images[key]=array
    manifest={'status':'pixel-cleanup-and-model-review-not-final-art',
        'authorization':'User approved Python transparency, fixed pixel resolution and palette reduction.',
        'source_tool':'built-in image_gen; no CLI/API fallback', 'palettes':palettes,'weapons':{}}
    for key,pixels in images.items():
        job=JOBS[key]; transform=transforms[key]
        Image.fromarray(pixels).save(OUT/f'{key}.png')
        jewel_box=projected_box(job['jewel_box'],transform)
        x0,y0,x1,y1=jewel_box
        rgb=pixels[:,:,:3].astype(int)
        color = ((rgb[:,:,0]>rgb[:,:,1]*1.5)&(rgb[:,:,0]>rgb[:,:,2]*1.2) if key!='staff'
                 else (rgb[:,:,1]>rgb[:,:,0]*1.4)&(rgb[:,:,2]>rgb[:,:,0]*1.4))
        selected=np.zeros(pixels.shape[:2],dtype=bool)
        selected[y0:y1,x0:x1]=color[y0:y1,x0:x1] & (pixels[y0:y1,x0:x1,3]>0)
        if not selected.any(): raise ValueError(f'{key}: missing crystal')
        jewel=np.zeros_like(pixels); jewel[selected]=pixels[selected]
        body=pixels.copy()
        if key=='staff': body[selected]=0
        else: body[selected]=[48,35,54,255]  # Dark socket beneath the separate raised stone.
        Image.fromarray(body).save(OUT/f'{key}-body.png')
        Image.fromarray(jewel).save(OUT/f'{key}-jewel.png')
        rows=[2]+[projected_box([0,v,0,v],transform)[1] for v in job['split_y']]+[2+job['height']]
        # All three have the same physical texel density, including short dagger.
        manifest['weapons'][key]={'source':job['source'],'source_sha256':hashes[key],**transform,
            'palette':job['family'],'rows':rows,'jewel_box':jewel_box,
            'height':round(job['height']*30/92,6),'colors':len(np.unique(pixels[pixels[:,:,3]>0,:3],axis=0)),
            'jewel_pixels':int(selected.sum())}
    (OUT/'manifest.json').write_text(json.dumps(manifest,indent=2)+'\n',encoding='utf-8')
    preview=Image.new('RGB',(720,560),'#28232e'); draw=ImageDraw.Draw(preview)
    for col,(key,pixels) in enumerate(images.items()):
        sprite=Image.fromarray(pixels); bbox=sprite.getbbox(); sprite=sprite.crop(bbox)
        zoom=sprite.resize((sprite.width*5,sprite.height*5),Image.Resampling.NEAREST)
        preview.paste(zoom,(col*240+(240-zoom.width)//2,48),zoom)
        draw.text((col*240+18,16),f'{key}: {manifest["weapons"][key]["content_size"]}',fill='#e5d7be')
    qa=ROOT/'.tools/texture-first-pixel'; qa.mkdir(parents=True,exist_ok=True)
    preview.save(qa/'pixel-review.png')
    print(json.dumps(manifest,ensure_ascii=False))


if __name__=='__main__': build()
