/** Export the approved browser's forge-light phases and Georgia result numerals.
 *  Requires Playwright with a local Chrome installation. Generated PNGs are checked in;
 *  the normal Gradle/resource-pack build does not launch a browser.
 */
const { chromium } = require('playwright');
const fs = require('node:fs');
const path = require('node:path');
const { pathToFileURL } = require('node:url');

async function main() {
  const root = path.resolve(__dirname, '..');
  const source = path.join(root, 'assets/ui/polish05-import/reference/ProjectS_UI_Polish05_Workbench.html');
  const out = path.join(root, 'web-ui-lab/ui/polish05-effects');
  fs.mkdirSync(out, { recursive: true });
  const browser = await chromium.launch({ channel: 'chrome', headless: true });
  try {
    const page = await browser.newPage({ viewport: { width: 1440, height: 920 }, deviceScaleFactor: 1 });
    await page.goto(pathToFileURL(source).href);
    await page.evaluate(() => document.fonts.ready);
    await page.addStyleTag({ content: '#hero-weapon,#embers{visibility:hidden!important}' });
    const stage = page.locator('#weapon-stage');
    for (const [phase, className] of [['striking', 'striking'], ['result_warm', 'result-warm']]) {
      await stage.evaluate((element, active) => {
        element.classList.remove('striking', 'result-warm');
        element.classList.add(active);
      }, className);
      // Wait beyond CSS transition/animation, capturing the steady approved state.
      await page.waitForTimeout(400);
      await stage.screenshot({ path: path.join(out, `forge_environment_${phase}.png`), animations: 'disabled' });
    }

    // The original next-level text uses 39px Georgia and a 15px golden text shadow.
    // Include 20px transparent padding so the entire soft halo survives the glyph crop.
    await page.setContent(`<!doctype html><html><body style="margin:0;background:transparent">
      <span id="level" style="position:absolute;left:20px;top:15px;font:39px/1.18 Georgia,serif;
        color:#eccb85;text-shadow:0 0 15px #d4a44426;white-space:nowrap"></span>
      </body></html>`);
    const level = page.locator('#level');
    for (let n = 0; n <= 31; n++) {
      await level.evaluate((element, text) => { element.textContent = text; }, `+${n}`);
      const width = Math.ceil(await level.evaluate(element => element.getBoundingClientRect().width)) + 40;
      await page.setViewportSize({ width, height: 80 });
      await page.screenshot({ path: path.join(out, `next_level_${n}.png`), omitBackground: true });
    }
    fs.writeFileSync(path.join(out, 'SOURCE.md'),
      'Generated from the approved Polish05 HTML by scripts/export_polish05_effects.cjs.\n' +
      'The forge plates capture the CSS striking and result-warm phases without the sharp hero weapon or canvas embers.\n' +
      'The next-level sprites capture Georgia, #eccb85 and the original 15px #d4a44426 text shadow.\n' +
      'Sharp sword art, dynamic values, interaction and canvas-style ember movement remain separate.\n');
    console.log(`POLISH05_EFFECTS_EXPORTED ${out}`);
  } finally {
    await browser.close();
  }
}

main().catch(error => { console.error(error); process.exitCode = 1; });
