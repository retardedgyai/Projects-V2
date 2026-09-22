"""Preserved imagegen arrow -> same-density, binary-alpha native bow texture."""
import hashlib
import json
from pathlib import Path
import numpy as np
from PIL import Image
from process_armament_art import pixelize, reduce_palette
from build_texture_first_sword import compile_model

ROOT=Path(__file__).resolve().parents[1]
SOURCE=ROOT/'assets/class-armaments/texture-first'
ORIGINAL=SOURCE/'sources/bow-arrow-v01.png'
OUT=SOURCE/'processed-bow-arrow-v01'
SHA='3229c1c4c5db36af9b518851e1cbc9a407687c83f595786be5579110f320985a'
SCALE=30/92


def convert():
    if hashlib.sha256(ORIGINAL.read_bytes()).hexdigest()!=SHA:
        raise ValueError('Arrow source changed')
    with Image.open(ORIGINAL) as image:
        mode,size=image.mode,list(image.size)
        vertical,entry=pixelize(image,52)
    reduced,palette=reduce_palette([vertical],12)
    # Upward artwork rotated once into model +X; runtime UV remains unfiltered.
    pixels=np.rot90(reduced[0],k=-1).copy()
    yy,xx=np.nonzero(pixels[:,:,3])
    left,right,top,bottom=int(xx.min()),int(xx.max()+1),int(yy.min()),int(yy.max()+1)
    tail_rows=yy[xx==left]
    entry.update({'source_sha256':SHA,'source_mode':mode,'source_size':size,
        'palette':palette,'rotated_canvas_size':[pixels.shape[1],pixels.shape[0]],
        'bounds':[left,top,right,bottom],
        'nock_pixel':[left,float(np.mean(tail_rows))+.5],
        'model_units_per_pixel':SCALE,'runtime_applied':False})
    return pixels,entry


def geometry():
    pixels,entry=convert()
    left,top,right,bottom=entry['bounds']
    nx,ny=entry['nock_pixel']
    spec={'height':(bottom-top)*SCALE,'top_pixel':top,'bottom_pixel':bottom,
        'pivot_pixel_x':nx,'alpha_cutoff':128,'texture':'projects:item/weapons/pixel_bow_arrow',
        'parts':[{'name':'arrow','rows':[top,bottom],'thickness':.16}]}
    model,_=compile_model(spec,pixels[:,:,3])
    origin=np.array([8,(bottom-ny)*SCALE,8])
    for e in model['elements']:
        for edge in ('from','to'): e[edge]=np.round(np.asarray(e[edge])-origin,6).tolist()
        for face in e['faces'].values(): face['texture']='#arrow'
    return model['elements'],pixels,entry


def build():
    pixels,entry=convert()
    OUT.mkdir(parents=True,exist_ok=True)
    Image.fromarray(pixels).save(OUT/'arrow.png')
    (OUT/'manifest.json').write_text(json.dumps(entry,indent=2)+'\n',encoding='utf-8')
    print('Arrow original converted; same 30/92 native pixel scale; not running.')


if __name__=='__main__': build()
