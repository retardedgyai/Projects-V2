from PIL import Image, ImageDraw, ImageFont
from pathlib import Path
F = ImageFont.truetype(str(Path(__file__).resolve().parents[3] / ".tools/core-menu/x12y12pxMaruMinya.ttf"), 24)
flow = Image.open("flow.png")
opts = [("A  ダイアログ型", "左に一覧、右に見本。バニラのDialogで作る。早い・確実", "option-a-dialog.png"),
        ("B  桟橋の場面型", "港の本物の桟橋に6人を立たせ、カメラ固定で選ぶ。一番MMOらしい・手間大", "option-b-dock.png"),
        ("C  七つの旗型", "旗を横に並べ、選んだ旗が下りてくる。全画面UI（地図と同じ作り）・中くらい", "option-c-banners.png")]
tw = 813; th = tw * 1080 // 1920
W = flow.width
H = 70 + flow.height + 40 + th + 140
im = Image.new("RGB", (W, H), (16, 17, 22)); d = ImageDraw.Draw(im)
d.text((40, 24), "初めて入った人の流れ（4画面）", fill=(208, 179, 90), font=F)
im.paste(flow, (0, 70))
y = 70 + flow.height + 40
for i, (t, sub, f) in enumerate(opts):
    x = 40 + i * (tw + 40)
    d.text((x, y), t, fill=(208, 179, 90), font=F)
    im.paste(Image.open(f).resize((tw, th), Image.LANCZOS), (x, y + 40))
    a, b = sub.split("。", 1)
    d.text((x, y + th + 54), a, fill=(232, 228, 216), font=F)
    d.text((x, y + th + 88), b, fill=(138, 136, 130), font=F)
# put options in two columns instead if too tall
im.save("overview.png")
print(im.size)
