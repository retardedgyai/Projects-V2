"""Export smooth approved HTML typography for Vanilla's bitmap font provider.

Requires Pillow, Playwright for Python, and locally installed Chrome. The
generated PNGs are checked in; the normal pack build needs only Pillow.
"""
from __future__ import annotations

from io import BytesIO
from math import ceil
from pathlib import Path
import sys

from PIL import Image
from playwright.sync_api import sync_playwright


ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "assets/ui/polish05-import/reference/ProjectS_UI_Polish05_Workbench.html"
OUT = ROOT / "web-ui-lab/ui/polish05-effects"


def save_smooth(raw: bytes, size: tuple[int, int], destination: Path) -> None:
    image = Image.open(BytesIO(raw)).convert("RGBA")
    # Chrome rounds fractional element bounds outwards at high DPI.
    assert abs(image.width - size[0] * 3) <= 3 and abs(image.height - size[1] * 3) <= 3, \
        (destination.name, image.size, size)
    # Keep two texture pixels per approved CSS pixel. Vanilla then downsamples
    # the glyph at display time instead of magnifying a 1x bitmap edge.
    image = image.resize((size[0] * 2, size[1] * 2), Image.Resampling.LANCZOS)
    if destination.name == "next_level_max.png" and image.width > 256:
        # The final six pixels are empty shadow padding. Vanilla bitmap
        # providers reject a glyph wider than 256px.
        assert image.getbbox()[2] <= 256
        image = image.crop((0, 0, 256, image.height))
    image.save(destination, optimize=True)


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    with sync_playwright() as playwright:
        browser = playwright.chromium.launch(channel="chrome", headless=True)
        try:
            page = browser.new_page(viewport={"width": 1440, "height": 920}, device_scale_factor=3)
            page.goto(SOURCE.as_uri())
            page.evaluate("document.fonts.ready")
            button = page.locator("#enhance-btn")
            box = button.bounding_box()
            assert box and box["width"] == 326 and box["height"] == 51, box
            save_smooth(button.screenshot(animations="disabled"), (326, 51),
                        OUT / "enhance_button_smooth.png")

            page.set_content('''<!doctype html><html><body style="margin:0;background:transparent">
                <div id="tab" style="width:66px;height:30px;display:flex;align-items:center;
                justify-content:center;font:17px 'Noto Sans CJK JP','Yu Gothic',Meiryo,sans-serif;
                color:#f0ddad;-webkit-font-smoothing:antialiased">強化</div>
                </body></html>''')
            save_smooth(page.locator("#tab").screenshot(omit_background=True), (66, 30),
                        OUT / "tab_label_forge_smooth.png")
            if "--tab-only" in sys.argv:
                return

            page.set_content('''<!doctype html><html><body style="margin:0;background:transparent">
                <span id="level" style="position:absolute;left:20px;top:15px;
                font:39px/1.18 Georgia,serif;white-space:nowrap"></span>
                </body></html>''')
            level = page.locator("#level")
            for family in ("current", "next"):
                level.evaluate("(el, next) => { el.style.color = next ? '#eccb85' : '#ddd8c4';"
                               "el.style.textShadow = next ? '0 0 15px #d4a44426' : 'none'; }",
                               family == "next")
                labels = [(str(n), f"+{n}") for n in range(31)]
                if family == "next":
                    labels.append(("max", "MAX"))
                for suffix, caption in labels:
                    level.evaluate("(el, value) => el.textContent = value", caption)
                    width = ceil(level.bounding_box()["width"]) + 40
                    page.set_viewport_size({"width": width, "height": 80})
                    save_smooth(page.screenshot(omit_background=True), (width, 80),
                                OUT / f"{family}_level_{suffix}.png")
            print(f"POLISH05_TYPOGRAPHY_EXPORTED {OUT}")
        finally:
            browser.close()


if __name__ == "__main__":
    main()
