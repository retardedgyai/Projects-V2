"""Render large orthographic review views of the authored Ashen Knight."""

from pathlib import Path
import math
import sys

import numpy as np
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "vendor" / "scorpius" / "bbmodel"))
import preview_bbmodel as preview  # noqa: E402

source = ROOT / "model-lab" / "models" / "ashen_knight.bbmodel"
target = ROOT / "model-lab" / "build" / "previews" / "ashen_knight_review.png"
data, elements, atlas, animations = preview.load(source)
axis_project = preview.project


def review_project(points, view):
    if view == "back":
        return np.stack((points[:, 0], -points[:, 1], -points[:, 2]), axis=1)
    if view != "quarter" and not view.startswith("yaw_"):
        return axis_project(points, view)
    yaw = math.radians(35 if view == "quarter" else int(view.split("_", 1)[1]))
    x = math.cos(yaw) * points[:, 0] + math.sin(yaw) * points[:, 2]
    z = -math.sin(yaw) * points[:, 0] + math.cos(yaw) * points[:, 2]
    return np.stack((-x, -points[:, 1], z), axis=1)


preview.project = review_project
views = [("idle", 0, "front"), ("idle", 0, "side"),
         ("idle", 0, "quarter"), ("cleave", .9, "quarter")]
tiles = [(f"{name} {view}", preview.render(data, elements, atlas,
          animations[name], at, view, 10)) for name, at, view in views]
width = max(image.width for _, image in tiles) * 2 + 36
height = max(image.height for _, image in tiles) * 2 + 56
sheet = Image.new("RGBA", (width, height), (18, 19, 25, 255))
draw = ImageDraw.Draw(sheet)
for index, (label, tile) in enumerate(tiles):
    x = 12 + (index % 2) * (width // 2)
    y = 12 + (index // 2) * (height // 2)
    draw.text((x, y), label, fill=(240, 235, 227, 255))
    sheet.paste(tile, (x, y + 16))
target.parent.mkdir(parents=True, exist_ok=True)
sheet.save(target)
print(target)

back = preview.render(data, elements, atlas, animations["idle"], 0, "back", 12)
back_target = target.with_name("ashen_knight_back_review.png")
back.save(back_target)
print(back_target)

turn_angles = range(0, 360, 45)
turn_tiles = [(angle, preview.render(data, elements, atlas, animations["idle"],
               0, f"yaw_{angle}", 8)) for angle in turn_angles]
turn_w = max(tile.width for _, tile in turn_tiles)
turn_h = max(tile.height for _, tile in turn_tiles)
turn_sheet = Image.new("RGBA", (4 * (turn_w + 16), 2 * (turn_h + 26)), (18, 19, 25, 255))
turn_draw = ImageDraw.Draw(turn_sheet)
for index, (angle, tile) in enumerate(turn_tiles):
    x = (index % 4) * (turn_w + 16) + 8
    y = (index // 4) * (turn_h + 26) + 8
    turn_draw.text((x, y), f"{angle}°", fill=(240, 235, 227, 255))
    turn_sheet.paste(tile, (x, y + 16))
turn_target = target.with_name("ashen_knight_turntable.png")
turn_sheet.save(turn_target)
print(turn_target)

motion = [("walk", .4), ("run", .3), ("cleave", .65),
          ("thrust", .55), ("leap", .55), ("slam", .95)]
poses = [(f"{name} {at:g}", preview.render(data, elements, atlas,
          animations[name], at, "quarter", 8)) for name, at in motion]
tile_w = max(tile.width for _, tile in poses)
tile_h = max(tile.height for _, tile in poses)
motion_sheet = Image.new("RGBA", (3 * (tile_w + 16), 2 * (tile_h + 26)),
                         (18, 19, 25, 255))
motion_draw = ImageDraw.Draw(motion_sheet)
for index, (label, tile) in enumerate(poses):
    x = (index % 3) * (tile_w + 16) + 8
    y = (index // 3) * (tile_h + 26) + 8
    motion_draw.text((x, y), label, fill=(240, 235, 227, 255))
    motion_sheet.paste(tile, (x, y + 16))
motion_target = target.with_name("ashen_knight_motion_review.png")
motion_sheet.save(motion_target)
print(motion_target)

slam_views = [("windup front", .7, "front"), ("impact front", .95, "front"),
              ("impact side", .95, "side"), ("impact quarter", .95, "quarter")]
slam_tiles = [(label, preview.render(data, elements, atlas, animations["slam"], at, view, 10))
              for label, at, view in slam_views]
slam_w = max(tile.width for _, tile in slam_tiles)
slam_h = max(tile.height for _, tile in slam_tiles)
slam_sheet = Image.new("RGBA", (2 * (slam_w + 16), 2 * (slam_h + 26)),
                       (18, 19, 25, 255))
slam_draw = ImageDraw.Draw(slam_sheet)
for index, (label, tile) in enumerate(slam_tiles):
    x = (index % 2) * (slam_w + 16) + 8
    y = (index // 2) * (slam_h + 26) + 8
    slam_draw.text((x, y), label, fill=(240, 235, 227, 255))
    slam_sheet.paste(tile, (x, y + 16))
slam_target = target.with_name("ashen_knight_slam_review.png")
slam_sheet.save(slam_target)
print(slam_target)
