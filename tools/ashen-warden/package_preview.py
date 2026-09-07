import pathlib,sys
from PIL import Image,ImageDraw,ImageFont
frames=pathlib.Path(sys.argv[1]);out=pathlib.Path(sys.argv[2]);out.mkdir(parents=True,exist_ok=True)
labels=(frames/'labels.txt').read_text().splitlines()
font=ImageFont.truetype('C:/Windows/Fonts/arial.ttf',18)
palette=Image.open(frames/'0000.png').convert('RGB').quantize(colors=128)
images=[]
for i,name in enumerate(labels):
    im=Image.open(frames/f'{i:04d}.png').convert('RGB')
    d=ImageDraw.Draw(im);d.rectangle((0,438,480,480),fill=(16,22,25));d.text((16,448),f'ASHEN WARDEN  /  {name}',font=font,fill=(165,228,210))
    images.append(im.quantize(palette=palette,dither=Image.Dither.NONE))
images[0].save(out/'animation-preview.gif',save_all=True,append_images=images[1:],duration=50,loop=0,optimize=False)
selected=[0,48,115,119,127,160,164,173,204,241,276,351]
sheet=Image.new('RGB',(1440,1920),(16,22,25))
for i,n in enumerate(selected):sheet.paste(images[n].convert('RGB'),((i%3)*480,(i//3)*480))
sheet.save(out/'animation-storyboard.jpg',quality=92)
print('PREVIEW_PACKAGED',len(images),'frames')
