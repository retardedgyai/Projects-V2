"""Render the original study, optionally alongside the supplied local reference.

Outputs stay in the review directory; this does not change the resource pack.
"""
import argparse
import io
import json
import sys
import zipfile
from pathlib import Path
import numpy as np
from PIL import Image, ImageDraw

HERE=Path(__file__).resolve().parent
sys.path.insert(0,str(HERE.parent))
from preview_class_armaments import render_model, FONT
import forge_helm_study as study

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--reference-pack',type=Path)
    parser.add_argument('--output',type=Path,
                        default=HERE.parents[1]/'.tools/armor-review/forge-helm-reviewed')
    args=parser.parse_args()
    args.output.mkdir(parents=True,exist_ok=True)
    study.export(args.output)
    candidates=[]
    if args.reference_pack:
        with zipfile.ZipFile(args.reference_pack) as z:
            ref=json.loads(z.read('assets/isles/models/item/armor/ranged/elven_helmet.json'))
            ref_atlas=np.array(Image.open(io.BytesIO(z.read(
                'assets/isles/textures/custom/armor/ranged/elven_helmet.png'))).convert('RGBA'))
        candidates.append(('Isles reference',ref,{'0':ref_atlas}))
    model={'elements':study.elements()}
    textures={'helm':np.array(study.atlas())}
    candidates.append(('ProjectS rebuilt study',model,textures))
    count=len(candidates)
    sheet=Image.new('RGB',(320*count,620),'#1b1e23')
    draw=ImageDraw.Draw(sheet)
    for row,yaw in enumerate((-25,0)):
        for col,(label,m,t) in enumerate(candidates):
            frame=render_model(m,t,yaw=yaw,size=(320,240),scale=12)
            sheet.paste(frame,(320*col,270*row+26))
            draw.text((320*col+10,270*row+5),f'{label} | {yaw} deg',font=FONT,fill='#eee7d7')
    for col,(_,m,t) in enumerate(candidates):
        frame=render_model(m,t,size=(80,80),scale=2.8)
        sheet.paste(frame,(320*col+120,540))
    sheet.save(args.output/'comparison.png')
    angles=Image.new('RGB',(320*4,270),'#1b1e23')
    draw=ImageDraw.Draw(angles)
    for col,yaw in enumerate((-25,25,90,180)):
        angles.paste(render_model(model,textures,yaw=yaw,size=(320,240),scale=12),(col*320,26))
        draw.text((col*320+10,5),f'ProjectS | {yaw} deg',font=FONT,fill='#eee7d7')
    angles.save(args.output/'angles.png')
    print(args.output/'comparison.png')
    print(args.output/'angles.png')

if __name__=='__main__':
    main()
