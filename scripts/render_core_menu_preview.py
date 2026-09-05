"""Render a real CoreMenuCanvas.snapshot() JSON using the checked-in pack, without Minecraft input.

Only the title/canvas layer is reproduced: vanilla 3D item models, hover highlights,
tooltips and the localized player inventory label are intentionally not fabricated.
The output includes a footer stating this limitation, plus a machine-readable audit.
"""
from pathlib import Path
import argparse
import json

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]


class MenuRenderer:
    def __init__(self, repo=ROOT):
        self.repo = Path(repo)
        self.assets = self.repo / "server-minestom/src/main/resources/core-ui-pack/assets/projects"
        self.layout = json.loads((self.repo / "assets/core-ui/readable-menu-layout.json").read_text(encoding="utf-8"))
        self.cell = self.layout["text_cell"]
        self.source_cell = self.layout.get("source_cell", self.cell)
        self.raster_scale = self.layout.get("text_scale", 1)
        self.origin_x = self.layout["origin"][0]
        self.frame = Image.new("RGBA", tuple(self.layout["size"]))
        for index in range(2):
            with Image.open(self.assets / f"textures/gui/core/menu_canvas_{index}.png") as tile:
                self.frame.alpha_composite(tile.convert("RGBA"), (index * self.layout["frame_tile_width"], 0))
        self.atlas = Image.open(self.assets / "textures/gui/core/menu_text.png").convert("RGBA")
        self.buttons = Image.open(self.assets / "textures/gui/core/menu_buttons.png").convert("RGBA")
        self.card_atlases = {rows: Image.open(self.assets / f"textures/gui/core/menu_cards_{rows}.png").convert("RGBA")
                             for rows in range(1, 4)}
        self.art_atlas = Image.open(self.assets / "textures/gui/core/menu_art.png").convert("RGBA")
        self.art_ordinals = {}
        for line in (self.assets / "menu/art.tsv").read_text().splitlines():
            if line and not line.startswith("#"):
                name, ordinal, _, _ = line.split("\t")
                self.art_ordinals[name] = int(ordinal)
        self.metrics = {}
        for line in (self.assets / "menu/glyphs.tsv").read_text(encoding="utf-8").splitlines():
            if not line or line.startswith("#"): continue
            code, glyph, advance = line.split("\t")
            self.metrics[chr(int(code, 16))] = (int(glyph, 16), int(advance))
        self.text_base = min(glyph for glyph, advance in self.metrics.values())
        self.columns = self.atlas.width // self.source_cell
        self.tones = self.layout["button"]["tones"]

    def metric(self, char):
        return self.metrics.get(char, self.metrics["□"])

    def width(self, value):
        return sum(self.metric(char)[1] for char in value)

    def trim(self, value, maximum):
        if self.width(value) <= maximum: return value
        available = maximum - self.width("…")
        if available < 0: return ""
        result, used = "", 0
        for char in value:
            advance = self.metric(char)[1]
            if used + advance > available: break
            result += char
            used += advance
        return result + "…"

    def snap_y(self, y):
        return min(self.layout["text_ys"], key=lambda value: abs(value - y))

    def render(self, snapshot, scaled_width=None, show_icon_slots=False):
        scale = self.raster_scale
        result = self.frame.resize((self.frame.width * scale, self.frame.height * scale), Image.Resampling.NEAREST)
        report = {"title": snapshot["title"], "layer": "actual CoreMenuCanvas title layer",
                  "omitted": ["vanilla item models", "vanilla inventory label", "hover highlights", "tooltips"],
                  "warnings": [], "icon_slots": [], "drawn_text": [], "drawn_art": [],
                  "raster_scale": scale, "occupied_control_slots": []}

        def blit(picture, x, y, width=None, height=None):
            width, height = width or picture.width, height or picture.height
            result.alpha_composite(picture.resize((width * scale, height * scale), Image.Resampling.NEAREST),
                                   ((x - self.origin_x) * scale, y * scale))

        def art(value):
            if not value: return
            index = self.art_ordinals[value["art"]]
            sprite = self.art_atlas.crop((index % 8 * 32, index // 8 * 32,
                                          (index % 8 + 1) * 32, (index // 8 + 1) * 32))
            blit(sprite, value["x"], value["y"], value["size"], value["size"])
            report["drawn_art"].append(value)

        def text(x, y, value, color, maximum, where):
            y = self.snap_y(y)
            visible = self.trim(value, maximum)
            if visible != value:
                report["warnings"].append({"kind": "truncated", "where": where, "original": value,
                                           "visible": visible, "width": self.width(value), "available": maximum})
            missing = sorted(set(char for char in value if char not in self.metrics))
            if missing:
                report["warnings"].append({"kind": "missing_glyph", "where": where,
                                           "characters": missing, "codepoints": [f"U+{ord(char):04X}" for char in missing]})
            report["drawn_text"].append({"where": where, "x": x, "y": y, "text": visible, "width": self.width(visible)})
            for char in visible:
                glyph, advance = self.metric(char)
                index = glyph - self.text_base
                cell = self.atlas.crop(((index % self.columns) * self.source_cell, (index // self.columns) * self.source_cell,
                                       (index % self.columns + 1) * self.source_cell, (index // self.columns + 1) * self.source_cell))
                tinted = Image.new("RGBA", cell.size, f"#{color & 0xFFFFFF:06x}")
                tinted.putalpha(cell.getchannel("A"))
                blit(tinted, x, y, self.cell, self.cell)
                x += advance

        text(8, 6, snapshot["title"], snapshot["titleColor"], 160, "title")
        panel_layout = self.layout["panel"]
        for key, x in (("leftPanel", panel_layout["left_x"]), ("rightPanel", panel_layout["right_x"])):
            panel = snapshot.get(key)
            if not panel: continue
            if len(panel["lines"]) > panel_layout["lines"]:
                raise ValueError(f"{key} exceeds the canvas line guard: {len(panel['lines'])}")
            text(x, panel_layout["header_y"], panel["title"], panel["titleColor"], panel_layout["width"], key + ".title")
            art(panel.get("hero"))
            for row, line in enumerate(panel["lines"]):
                art(line.get("art"))
                text(line.get("x", x), line.get("y", panel_layout["line_y"] + row * panel_layout["line_height"]),
                     line["text"], line["color"], line.get("maxWidth", panel_layout["width"]), f"{key}.lines[{row}]")
        occupied = set()
        for card in snapshot.get("cards", []):
            expected = [card["firstSlot"] + row * 9 + column for row in range(card["rows"]) for column in range(card["columns"])]
            if expected != card["occupiedSlots"] or occupied.intersection(expected):
                raise ValueError(f"Card click rectangle does not match visual: {card}")
            occupied.update(expected)
            tone_row = list(self.tones).index(card["tone"])
            x = (card["columns"] - 1) * 160
            y = tone_row * card["height"]
            backdrop = self.card_atlases[card["rows"]].crop((x, y, x + card["width"], y + card["height"]))
            blit(backdrop, card["x"], card["y"])
            art(card["artPlacement"])
            text(card["labelX"], card["labelY"], card["label"], card["textColor"], card["labelMaxWidth"], f"card[{card['firstSlot']}]")
        for button in snapshot.get("buttons", []):
            slot, span, tone = button["firstSlot"], button["span"], button["tone"]
            if not (0 <= slot <= 53 and 1 <= span <= 9 and slot % 9 + span <= 9):
                raise ValueError(f"Invalid vanilla button slot: {button}")
            slots = set(range(slot, slot + span))
            if occupied.intersection(slots): raise ValueError(f"Overlapping button: {button}")
            occupied.update(slots)
            x, y = 8 + (slot % 9) * 18, 18 + (slot // 9) * 18
            extent = span * 18 - 2
            row = list(self.tones).index(tone)
            backdrop = self.buttons.crop(((span - 1) * 160, row * 16, (span - 1) * 160 + extent, row * 16 + 16))
            blit(backdrop, x, y)
            inset = self.layout["button"]["icon_reserved_width"] if button["icon"] else 0
            maximum = max(0, extent - inset)
            visible = self.trim(button["label"], maximum)
            text(x + inset + (extent - inset - self.width(visible)) // 2, y + 2, button["label"],
                 button["textColor"], maximum, f"button[{slot}]")
            if button["icon"]:
                report["icon_slots"].append(slot)
                if show_icon_slots:
                    # Optional diagnostic, explicitly not an invented Minecraft item render.
                    icon_x = x - self.origin_x
                    draw = ImageDraw.Draw(result)
                    draw.rectangle(tuple(value * scale for value in (icon_x + 2, y + 2, icon_x + 13, y + 13)), outline="#8AA6B5")
                    draw.line(tuple(value * scale for value in (icon_x + 3, y + 12, icon_x + 12, y + 3)), fill="#8AA6B5")
        for value in snapshot.get("arts", []): art(value)
        for index, value in enumerate(snapshot.get("texts", [])):
            text(value["x"], value["y"], value["value"], value["color"], value["maxWidth"], f"texts[{index}]")
        if scaled_width is not None:
            if scaled_width < 176: raise ValueError("Scaled width must accommodate the vanilla chest")
            viewport = Image.new("RGBA", (scaled_width * scale, result.height), "#111B23")
            viewport.alpha_composite(result, ((scaled_width * scale - result.width) // 2, 0))
            result = viewport
            report["scaled_width"] = scaled_width
            report["horizontal_clipping_each_side"] = max(0, (self.frame.width - scaled_width) // 2)
        # Snapshot describes presentation; disabled/read-only cards need not dispatch.
        report["occupied_control_slots"] = sorted(occupied)
        return result, report


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("snapshot", type=Path, help="JSON exported from a real CoreMenuCanvas.snapshot()")
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--repo", type=Path, default=ROOT, help="Repository whose checked-in pack should be rendered")
    parser.add_argument("--report", type=Path, help="Defaults to output.audit.json")
    parser.add_argument("--scale", type=int, default=3, choices=range(1, 7))
    parser.add_argument("--scaled-width", type=int, help="Emulate Minecraft's GUI-scaled viewport width, e.g. 320 or 400")
    parser.add_argument("--show-icon-slots", action="store_true", help="Draw diagnostic placeholders in omitted item-model slots")
    parser.add_argument("--strict", action="store_true", help="Exit with status 2 for any ellipsis or missing glyph")
    args = parser.parse_args()
    snapshot = json.loads(args.snapshot.read_text(encoding="utf-8-sig"))
    renderer = MenuRenderer(args.repo)
    picture, report = renderer.render(snapshot, args.scaled_width, args.show_icon_slots)
    picture = picture.resize((picture.width * args.scale // renderer.raster_scale,
                              picture.height * args.scale // renderer.raster_scale), Image.Resampling.NEAREST)
    # Caption outside the exact menu region, not a replacement for any Minecraft label.
    captioned = Image.new("RGBA", (picture.width, picture.height + 32), "#101820")
    captioned.alpha_composite(picture)
    draw = ImageDraw.Draw(captioned)
    draw.text((6, picture.height + 3), "ACTUAL MENU SNAPSHOT / TITLE LAYER ONLY", fill="#C2CBD1")
    draw.text((6, picture.height + 17), "Item models, inventory label and tooltips are not rendered.", fill="#8697A2")
    args.output.parent.mkdir(parents=True, exist_ok=True)
    captioned.save(args.output, optimize=True)
    report_path = args.report or args.output.with_suffix(".audit.json")
    report_path.parent.mkdir(parents=True, exist_ok=True)
    report_path.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"Rendered actual menu snapshot: {args.output}; {len(report['warnings'])} audit warnings; {len(report['icon_slots'])} omitted item icons")
    print(f"Audit: {report_path}")
    if args.strict and report["warnings"]: raise SystemExit(2)


if __name__ == "__main__":
    main()
