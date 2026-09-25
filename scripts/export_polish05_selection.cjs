/** Capture the approved HTML's warm selection treatments without their live content.
 *  Requires Playwright and local Chrome only when regenerating checked-in plates.
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
    await page.addStyleTag({ content: '.gear-choice > *, .tab.active > * {visibility:hidden!important}' });
    const gear = page.locator('.gear-choice.selected').first();
    const gearBox = await gear.boundingBox();
    if (gearBox.x !== 95 || gearBox.y !== 277 || gearBox.width !== 229 || gearBox.height !== 82)
      throw new Error(`Approved gear geometry changed: ${JSON.stringify(gearBox)}`);
    await gear.screenshot({ path: path.join(out, 'gear_selected.png'), animations: 'disabled' });
    const unselected = page.locator('.gear-choice:not(.selected)').first();
    const unselectedBox = await unselected.boundingBox();
    if (unselectedBox.x !== 95 || unselectedBox.y !== 359 || unselectedBox.width !== 229 || unselectedBox.height !== 82)
      throw new Error(`Approved unselected gear geometry changed: ${JSON.stringify(unselectedBox)}`);
    await unselected.screenshot({ path: path.join(out, 'gear_unselected.png'), animations: 'disabled' });

    await page.addStyleTag({ content: '.catalyst > *, .big-slot > *, .recipe > * {visibility:hidden!important}' });
    const catalyst = page.locator('.catalyst').first();
    await catalyst.screenshot({ path: path.join(out, 'catalyst_off.png'), animations: 'disabled' });
    await catalyst.click();
    if (await catalyst.getAttribute('aria-pressed') !== 'true') throw new Error('Catalyst did not toggle');
    await catalyst.screenshot({ path: path.join(out, 'catalyst_on.png'), animations: 'disabled' });

    await page.locator('[data-view="bag"]').first().click();
    for (const [selector, name] of [
      ['.big-slot.selected', 'bag_slot_selected'],
      ['.big-slot:not(.selected):not(.empty)', 'bag_slot_regular'],
      ['.big-slot.empty', 'bag_slot_empty'],
    ]) await page.locator(selector).first().screenshot({ path: path.join(out, `${name}.png`), animations: 'disabled' });

    await page.locator('[data-view="refine"]').first().click();
    for (const [selector, name] of [
      ['.recipe.active', 'recipe_selected'],
      ['.recipe:not(.active)', 'recipe_regular'],
    ]) await page.locator(selector).first().screenshot({ path: path.join(out, `${name}.png`), animations: 'disabled' });

    const css = fs.readFileSync(source, 'utf8').match(/<style>([\s\S]*?)<\/style>/)[1];
    for (const [view, width] of [['forge', 130], ['refine', 130], ['bag', 138]]) {
      await page.setContent(`<!doctype html><html><style>${css}</style><body style="margin:0;background:#222326">
        <div id="capture" style="width:${width}px;height:71px;background:linear-gradient(#30302bd9,#212324),#222326">
          <button class="tab active" style="width:${width}px;height:63px;padding:0"></button>
        </div></body></html>`);
      // Eight extra pixels retain the approved gold shadow below the underline.
      await page.locator('#capture').screenshot({ path: path.join(out, `tab_active_${view}.png`), animations: 'disabled' });
    }
    console.log(`POLISH05_SELECTION_EXPORTED ${out}`);
  } finally {
    await browser.close();
  }
}

main().catch(error => { console.error(error); process.exitCode = 1; });
