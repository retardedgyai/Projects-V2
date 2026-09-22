"""Render shipped menu/HUD pixels for visual QA; this is not an in-game screenshot."""
import re
from PIL import Image, ImageDraw, ImageFont
from build_core_hud_assets import ROOT, SKILLS, CLASSES

SOURCE = ROOT / "assets/core-ui"
PACK = ROOT / "server-minestom/src/main/resources/core-ui-pack/assets/projects"
FONT = ROOT / ".tools/core-menu/x12y12pxMaruMinya.ttf"
NAMES = dict((icon, name) for name, icon in re.findall(r's\("([^"\n]+)", "([^"\n]+)"',
    (ROOT / "server-minestom/src/main/kotlin/dev/projects/server/coreloop/CoreSkillCatalog.kt").read_text(encoding="utf-8")))
JOBS = ("戦士", "ハンター", "メイジ", "星織師", "アサシン", "テンプラー", "ヒーラー")


def preview():
    font = ImageFont.truetype(str(FONT), 12)
    title = ImageFont.truetype(str(FONT), 16)
    sheet = Image.new("RGB", (988, 1064), "#211d22")
    draw = ImageDraw.Draw(sheet)
    draw.text((16, 12), "職業別スキルアイコンとフレーム", font=title, fill="#e6d7bb")
    draw.text((16, 37), "大：HUDの2倍表示　小：メニュー16px ／ HUD32px　※画像合成による確認用", font=font, fill="#c1b4a2")
    for index, name in enumerate(SKILLS):
        row, col = divmod(index, 10)
        x, y = 120 + col * 86, 64 + row * 140
        if col == 0:
            draw.text((12, y+38), JOBS[row], font=title, fill="#dfcfb4")
        with Image.open(PACK / f"textures/gui/core/skill_{name}_states.png") as atlas:
            hud = atlas.crop((0, 0, 32, 32))
        with Image.open(PACK / f"textures/gui/skills/{name}.png") as menu:
            small = menu.resize((16, 16), Image.Resampling.NEAREST)
        sheet.paste(hud.resize((64, 64), Image.Resampling.NEAREST), (x+7, y))
        draw.text((x, y+70), NAMES[name], font=font, fill="#ded4c2")
        sheet.paste(small, (x+10, y+105))
        sheet.paste(hud, (x+40, y+97))
    sheet.save(SOURCE / "skill-art-preview.png")
    states = Image.new("RGB", (728, 234), "#211d22")
    draw = ImageDraw.Draw(states)
    draw.text((10, 6), "職業フレーム ／ 使用可能・再使用待ち・資源不足", font=title, fill="#e6d7bb")
    for index, job in enumerate(CLASSES):
        x = index * 104
        draw.text((x+3, 34), JOBS[index], font=font, fill="#ded4c2")
        with Image.open(SOURCE / f"skill-frames/{job}.png") as frame:
            states.paste(frame.resize((64, 64), Image.Resampling.NEAREST), (x+20, 57), frame.resize((64, 64), Image.Resampling.NEAREST))
        name = SKILLS[index * 10]
        with Image.open(PACK / f"textures/gui/core/skill_{name}_states.png") as atlas:
            for order, state in enumerate((0, 10, 21)):
                left, top = state % 4 * 32, state // 4 * 32
                cell = atlas.crop((left, top, left+32, top+32))
                states.paste(cell, (x+order*34, 149))
        draw.text((x+1, 191), "可　待　不足", font=font, fill="#c1b4a2")
    states.save(SOURCE / "skill-frame-preview.png")
    print("Rendered 70 skills at menu 16px / HUD 32px, plus 7 class frame state comparisons")


if __name__ == "__main__":
    preview()
