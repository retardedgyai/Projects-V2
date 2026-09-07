import pathlib,sys
from PIL import Image,ImageDraw,ImageFont
frames=pathlib.Path(sys.argv[1]);out=pathlib.Path(sys.argv[2]);out.mkdir(parents=True,exist_ok=True)
labels=(frames/'labels.txt').read_text().splitlines()
font=ImageFont.truetype('C:/Windows/Fonts/arial.ttf',18)
palette=Image.open(frames/'0000.png').convert('RGB').quantize(colors=128)
w,h=palette.size
images=[]
for i,name in enumerate(labels):
    im=Image.open(frames/f'{i:04d}.png').convert('RGB')
    d=ImageDraw.Draw(im);d.rectangle((0,h-42,w,h),fill=(16,22,25));d.text((16,h-32),f'NIGHT WARDEN  /  {name}',font=font,fill=(194,188,239))
    images.append(im.quantize(palette=palette,dither=Image.Dither.NONE))
images[0].save(out/'animation-preview.gif',save_all=True,append_images=images[1:],duration=50,loop=0,optimize=False)
keys=[('idle',0),('walk',8),('slash_01',16),('slash_01',20),('slash_01',27),('heavy_slash',27),('heavy_slash',32),('dash',20),('dash',27),('hurt',3),('phase_transition',35),('death',55)]
selected=[labels.index(name)+tick for name,tick in keys]
sheet=Image.new('RGB',(w*3,h*4),(16,22,25))
for i,n in enumerate(selected):sheet.paste(images[n].convert('RGB'),((i%3)*w,(i//3)*h))
sheet.save(out/'animation-storyboard.jpg',quality=92)
print('PREVIEW_PACKAGED',len(images),'frames')
