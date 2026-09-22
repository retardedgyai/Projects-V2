"""Render the shipped JSON cuboid geometry for QA (not a Minecraft screenshot).

No concept art or new texture painting: faces are projected directly from pack models.
"""
from pathlib import Path
import json
import math
from PIL import Image, ImageDraw, ImageFont
from build_core_combat_models import scene_models, scene_rows

ROOT = Path(__file__).resolve().parents[1]
PACK = ROOT / "server-minestom/src/main/resources/core-ui-pack"
COLORS = {"white_concrete": "#eeeef0", "light_gray_concrete": "#969a9a", "gray_concrete": "#3b4044",
          "yellow_concrete": "#f1b817", "orange_concrete": "#e16816", "magenta_concrete": "#ab30a0",
          "purple_concrete": "#6a239c", "light_blue_concrete": "#259ad0", "blue_concrete": "#303292",
          "red_concrete": "#962d29", "lime_concrete": "#67ad18", "green_concrete": "#4d5e21", "cyan_concrete": "#167b82",
          "black_concrete": "#171820", "brown_concrete": "#65452d", "blackstone": "#2a232b",
          "white_wool": "#e9ecec", "yellow_wool": "#f9c629", "red_wool": "#a02722", "black_wool": "#15191a",
          "stripped_dark_oak_log": "#493a23", "gold_block": "#f6d33d"}


def render(parts, cx, cy, scale=36):
    polygons = []
    for shape, palette, size, offset, pitch, yaw, roll in parts:
        path = PACK / f"assets/projects/models/combat_vfx/{shape}_{palette}.json"
        model = json.loads(path.read_text())
        def transform(v):
            x, y, z = [(v[i] - 8) / 16 * size[i] for i in range(3)]
            y, z = y * math.cos(pitch) - z * math.sin(pitch), y * math.sin(pitch) + z * math.cos(pitch)
            x, y = x * math.cos(roll) - y * math.sin(roll), x * math.sin(roll) + y * math.cos(roll)
            x, z = x * math.cos(yaw) + z * math.sin(yaw), -x * math.sin(yaw) + z * math.cos(yaw)
            x, y, z = x + offset[0], y + offset[1], z + offset[2]
            return (cx + (x * .94 + z * .34) * scale, cy + (z * .53 - x * .19 - y * .82) * scale, z * .77 - x * .28 + y * .58)
        for e in model["elements"]:
            lo, hi = e["from"], e["to"]
            assert all(0 <= v <= 16 for v in lo + hi)
            vertices = [transform([hi[0] if i & 1 else lo[0], hi[1] if i & 2 else lo[1], hi[2] if i & 4 else lo[2]]) for i in range(8)]
            for ids in ((0,1,3,2), (4,6,7,5), (0,4,5,1), (2,3,7,6), (0,2,6,4), (1,5,7,3)):
                points = [vertices[i] for i in ids]
                key = e["faces"]["up"]["texture"][1:]
                color = COLORS[model["textures"][key].split("/")[-1]]
                polygons.append((sum(p[2] for p in points) / 4, [(p[0], p[1]) for p in points], color))
    return sorted(polygons, reverse=True)


def main():
    # Validate every generated model, not only the representative views.
    index = set((PACK / "index.txt").read_text().splitlines())
    for shape, palette in scene_models():
        path = f"assets/projects/models/combat_vfx/{shape}_{palette}.json"
        model = json.loads((PACK / path).read_text())
        assert path in index and 1 <= len(model["elements"]) < 400, (shape,len(model["elements"]))
        assert all(all(-16 <= n <= 32 for n in e["from"] + e["to"]) for e in model["elements"])
        assert all(e["to"][i] > e["from"][i] for e in model["elements"] for i in range(3))
    poses = json.loads((ROOT / ".tools/skill-scene-poses.json").read_text(encoding="utf-8"))
    font = ImageFont.truetype("C:/Windows/Fonts/meiryob.ttc", 13)
    for page in range(7):
        image = Image.new("RGB", (1080, 1740), "#17202b")
        draw = ImageDraw.Draw(image)
        draw.text((15,8), "実装データの形状確認：灰枠は身長1.8m、黄色線は前方。ゲーム画面ではありません。", font=font, fill="#ddd9c9")
        for row, scene in enumerate(poses[page*10:page*10+10]):
            for col,t in enumerate((0,.5,1)):
                x,y = col*360,row*170+30
                draw.text((x+8,y+2), f"{scene['name']} [{scene['id']}] {int(t*100)}%", font=font, fill="#eee2bd")
                cx,cy=x+160,y+116
                draw.line((cx-120,cy,cx+160,cy),fill="#344651")
                draw.rectangle((cx-9,cy-53,cx+9,cy),outline="#677380")
                draw.rectangle((cx-7,cy-68,cx+7,cy-54),outline="#677380")
                draw.line((cx,cy,cx+38,cy+58),fill="#a68d3a",width=2)
                parts=[]
                for p in scene['parts']:
                    size=p['start']+(p['end']-p['start'])*t
                    parts.append((p['shape'],p['palette'],[v*size for v in p['scale']],
                        [v+w*t for v,w in zip(p['offset'],p['travel'])],p['pitch'],p['yaw']+p['spin']*t,p['roll']))
                for _,points,color in render(parts,cx,cy,scale=36): draw.polygon(points,fill=color)
        out=ROOT/f".tools/skill-scenes-{page+1}.png"
        image.save(out)
    print(f"Validated {len(scene_models())} used model/color pairs, {len(set(s for s,p in scene_models()))} distinct shapes, 70 runtime scenes / 7 preview sheets.")


if __name__ == "__main__":
    main()
