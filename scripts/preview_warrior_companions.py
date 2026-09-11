"""Actual pack model parts at a common inspection scale, not gameplay or effect timing."""
from PIL import Image, ImageDraw
from preview_skill_choreography import ROOT, FONT, render, W, H

sheet=Image.new('RGB',(W*4,H*2+30),'#111820')
ImageDraw.Draw(sheet).text((8,4),'補助部品の形状確認（共通拡大率／実機の大きさ・動作ではありません）',font=FONT,fill='#deded6')
for row,fade in enumerate((0,5)):
    for column,(kind,label) in enumerate((('wind','風の欠片'),('spark','命中火花'),('chip','衝撃の欠片'),('boundary','範囲の輪郭'))):
        p={'model':f'combat_vfx/war_mote_{kind}_steel'+(f'_fade{fade}' if fade else ''),
           'offset':[0,.8,0],'scale':[2,2,2],'pitch':0,'yaw':0,'roll':0}
        sheet.paste(render([p],label,fade,world_scale=90),(column*W,row*H+30))
target=ROOT/'.tools/warrior-companion-parts.png'
target.parent.mkdir(parents=True,exist_ok=True)
sheet.save(target)
print(target)
