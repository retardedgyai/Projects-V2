"""Old single-plane construction versus saved crossed-head JSON; not game footage."""
from copy import deepcopy
import json
from PIL import Image, ImageDraw
from build_specialist_armament_pack import SOURCE, OUT, ASSETS, geometry, pose
from build_texture_first_sword import compile_model
from pixel_weapon_display import grip_pixels
from preview_class_armaments import FONT, render_model


def build():
    entry=json.loads((SOURCE/'manifest.json').read_text())['weapons']['mace']
    base,parts,textures,anchors=geometry('mace',entry)
    top=entry['padding']; bottom=top+entry['content_size'][1]
    pivot,_=grip_pixels('mace',entry,textures)
    old,_=compile_model({'height':entry['height'],'top_pixel':top,'bottom_pixel':bottom,
        'pivot_pixel_x':pivot,'alpha_cutoff':128,'texture':'projects:item/weapons/pixel_mace_body',
        'parts':[{'name':'body','rows':[top,bottom],'thickness':.7}]},textures['body'][:,:,3])
    for e in old['elements']:
        for f in e['faces'].values(): f['texture']='#body'
    previous=deepcopy(parts); previous['body']=old['elements']
    old_model=pose('mace',base,previous,anchors)
    saved=json.loads((ASSETS/'models/item/weapons/pixel_mace.json').read_text())
    sheet=Image.new('RGB',(1280,760),'#1b1e23')
    for row,(label,model) in enumerate((('旧: 頭部1面',old_model),('新: 交差する薄板',saved))):
        for col,yaw in enumerate((0,45,90,135)):
            sheet.paste(render_model(model,textures,yaw=yaw,size=(320,350)),(col*320,row*380+30))
            ImageDraw.Draw(sheet).text((col*320+10,row*380+8),f'{label} / {yaw}°',font=FONT,fill='#dfd4c0')
    sheet.save(OUT/'mace-head-comparison.png')
    print(OUT/'mace-head-comparison.png')


if __name__=='__main__': build()
