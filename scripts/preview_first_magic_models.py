"""Offline isometric review of the shipped cuboid JSON. Not a Minecraft screenshot."""
import json
from pathlib import Path

from PIL import Image, ImageChops, ImageDraw, ImageEnhance, ImageFont

ROOT = Path(__file__).resolve().parents[1]
ASSET = ROOT / "server-minestom/src/main/resources/core-ui-pack/assets/projects"
OUT = ROOT / "assets/first-magic/model-sheet.png"


def textured_face(canvas, polygon, texture, light):
    """Affine preview of an authored face texture, with its original alpha."""
    p0,p1,_,p3 = polygon
    ux,uy = p1[0]-p0[0],p1[1]-p0[1]
    vx,vy = p3[0]-p0[0],p3[1]-p0[1]
    determinant = ux*vy-vx*uy
    if abs(determinant) < .01:
        return
    left = max(0,int(min(p[0] for p in polygon)))
    top = max(0,int(min(p[1] for p in polygon)))
    right = min(canvas.width,int(max(p[0] for p in polygon))+2)
    bottom = min(canvas.height,int(max(p[1] for p in polygon))+2)
    if right <= left or bottom <= top:
        return
    tex = Image.open(ASSET / f"textures/item/first_magic/model/{texture}.png").convert("RGBA")
    tw,th=tex.size
    au,bu=vy/determinant,-vx/determinant
    av,bv=-uy/determinant,ux/determinant
    coeff=(au*tw,bu*tw,(au*(left-p0[0])+bu*(top-p0[1]))*tw,
           av*th,bv*th,(av*(left-p0[0])+bv*(top-p0[1]))*th)
    warped=tex.transform((right-left,bottom-top),Image.Transform.AFFINE,coeff,Image.Resampling.NEAREST)
    mask=Image.new("L",warped.size,0)
    ImageDraw.Draw(mask).polygon([(x-left,y-top) for x,y in polygon],fill=255)
    rgb=ImageEnhance.Brightness(warped.convert("RGB")).enhance(light).convert("RGBA")
    rgb.putalpha(ImageChops.multiply(warped.getchannel("A"),mask))
    canvas.alpha_composite(rgb,(left,top))


def render(name, scale=(1, 1, 1), transparent=False):
    data = json.loads((ASSET / f"models/first_magic/{name}.json").read_text())
    image = Image.new("RGBA", (360, 340), (0,0,0,0) if transparent else "#1d272b")
    sx, sy, sz = scale

    def project(x, y, z):
        near_z = 16 - z
        return (180 + (x * sx - near_z * sz) * 7.3, 239 + (x * sx + near_z * sz) * 3.2 - y * sy * 8.2)

    ordered = sorted(data["elements"], key=lambda e:
                     (e["from"][0] + e["to"][0]) + (32 - e["from"][2] - e["to"][2])
                     + (e["from"][1] + e["to"][1]) * 3)
    for element in ordered:
        x1, y1, z1 = element["from"]
        x2, y2, z2 = element["to"]
        faces=element["faces"]
        textured_face(image,[project(x2,y2,z1),project(x2,y2,z2),project(x2,y1,z2),project(x2,y1,z1)],
                      faces["east"]["texture"].removeprefix("#"),.69)
        textured_face(image,[project(x1,y2,z1),project(x2,y2,z1),project(x2,y1,z1),project(x1,y1,z1)],
                      faces["north"]["texture"].removeprefix("#"),.83)
        textured_face(image,[project(x1,y2,z1),project(x2,y2,z1),project(x2,y2,z2),project(x1,y2,z2)],
                      faces["up"]["texture"].removeprefix("#"),1.08)
    return image


def main():
    panels = [("research_desk", (1.1,.85,1), "RESEARCH DESK"),
              ("crude_distiller", (1,1,1), "CRUDE DISTILLER"),
              ("jar_shelf", (1.5,1,1), "JAR SHELF + FOUR JARS"),
              ("jar_tide_high", (1,1,1), "TIDE JAR")]
    sheet = Image.new("RGBA", (720, 740), "#182126")
    d = ImageDraw.Draw(sheet)
    font = ImageFont.load_default()
    for i, (model, scale, title) in enumerate(panels):
        x, y = (i % 2) * 360, (i // 2) * 370
        view=render(model, scale)
        if model == "jar_shelf":
            for j,aspect in enumerate(("ember","tide","gale","stone")):
                bottle=render(f"jar_{aspect}_high",transparent=True)
                bounds=bottle.getbbox()
                if bounds:
                    bottle=bottle.crop(bounds)
                    bottle.thumbnail((46,68),Image.Resampling.NEAREST)
                    view.alpha_composite(bottle,(91+j*49,239-bottle.height))
        sheet.alpha_composite(view, (x, y))
        d.rectangle((x+12, y+12, x+348, y+350), outline="#ac8655", width=2)
        d.text((x+22, y+23), title, fill="#ead8ae", font=font)
    sheet.save(OUT, optimize=True)
    print(OUT)


if __name__ == "__main__":
    main()
