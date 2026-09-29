// Targeted checks for the local HTML proposal; no game/server interaction.
// Requires Playwright with an installed Edge browser. Set NODE_PATH if necessary.
const { chromium } = require('playwright');
const assert = require('node:assert/strict');
const path = require('node:path');
const { pathToFileURL } = require('node:url');
const fs = require('node:fs');

(async () => {
  const out = path.resolve(__dirname, '../../../.tools/polish05-design-review');
  fs.mkdirSync(out, { recursive: true });
  const browser = await chromium.launch({ headless: true, channel: 'msedge' });
  const page = await browser.newPage({ viewport: { width: 1920, height: 1080 }, reducedMotion: 'reduce' });
  const errors = [];
  page.on('pageerror', e => errors.push(e.message));
  page.on('console', m => { if (m.type() === 'error') errors.push(m.text()); });
  await page.goto(pathToFileURL(path.join(__dirname, 'index.html')).href);
  await page.locator('#hero').evaluate(i => i.decode());
  assert.equal(await page.locator('.gear').count(), 5);
  assert.equal(await page.locator('#chance-value').innerText(), '87.5%');
  assert.equal(await page.locator('#costs .cost').count(), 3);
  assert.equal(await page.locator('#stat-label').innerText(), '最大HP（合計）');
  await page.screenshot({ path: path.join(out, 'normal-1920.png'), fullPage: true });
  assert.ok(await page.locator('#enhance').evaluate(e => e.getBoundingClientRect().bottom <= innerHeight), 'Default action fits 1080p');
  for (const id of ['weapon', 'head', 'chest', 'legs', 'feet']) {
    await page.locator(`[data-id="${id}"]`).click();
    assert.equal(await page.locator(`[data-id="${id}"]`).getAttribute('aria-pressed'), 'true');
    await page.locator('#hero').evaluate(i => i.decode());
  }
  await page.locator('[data-id="chest"]').click();
  await page.locator('#preview').click();
  await page.mouse.move(1100, 600);
  assert.equal(await page.locator('#preview').getAttribute('aria-pressed'), 'true');
  assert.ok(!(await page.locator('#hero').getAttribute('src')).endsWith('-0.png'), 'Real model alternate view');
  await page.locator('#enhance').click();
  assert.equal(await page.locator('#confirmation').evaluate(d => d.open), true);
  assert.equal(await page.locator('#preview').getAttribute('aria-pressed'), 'false');
  await page.keyboard.press('Escape');
  assert.equal(await page.locator('#enhance').evaluate(e => e === document.activeElement), true);
  await page.locator('#catalyst').check();
  assert.equal(await page.locator('#chance-value').innerText(), '100.0%');
  assert.equal(await page.locator('#costs .cost').count(), 4);
  await page.locator('[data-id="weapon"]').click();
  assert.equal(await page.locator('#costs .cost').count(), 5);
  await page.screenshot({ path: path.join(out, 'all-costs-1920.png'), fullPage: true });
  assert.ok(await page.locator('#enhance').evaluate(e => e.getBoundingClientRect().bottom <= innerHeight), 'All five costs and action fit 1080p');
  await page.locator('#enhance').click();
  assert.equal(await page.locator('#confirm-costs .cost').count(), 5);
  await page.screenshot({ path: path.join(out, 'confirmation-1920.png') });
  await page.locator('#confirm').click();
  assert.equal(await page.locator('#enhance').isDisabled(), true);
  await page.waitForFunction(() => document.getElementById('receipt').classList.contains('show'));
  assert.equal(await page.locator('#receipt-title').innerText(), '強化成功');
  assert.equal(await page.locator('[data-id="weapon"] .gear-level').innerText(), '+7');
  assert.equal(await page.locator('[data-id="chest"] .gear-level').innerText(), '+6');
  assert.deepEqual(await page.evaluate(() => ({ ...stock })), { ingot:22, board:13, leather:18, cloth:11, cut_stone:17, affix_dust:6 }, 'Confirmed costs deducted exactly once');
  await page.locator('#dismiss-receipt').click();
  await page.locator('#reset').click();
  await page.locator('#scenario').selectOption('short');
  assert.equal(await page.locator('#enhance').isDisabled(), true);
  assert.equal(await page.locator('#costs .missing').count(), 1);
  await page.screenshot({ path: path.join(out, 'shortage-1920.png'), fullPage: true });
  await page.locator('#scenario').selectOption('high');
  assert.equal(await page.locator('#chance-value').innerText(), '26.0%');
  assert.ok((await page.locator('#risk').innerText()).includes('失敗した場合の破損率 8.0%'));
  await page.locator('#demo-outcome').selectOption('failure');
  await page.locator('#enhance').click();
  await page.locator('#confirm').click();
  await page.waitForFunction(() => document.getElementById('receipt').classList.contains('show'));
  assert.equal(await page.locator('#receipt-title').innerText(), '強化失敗');
  assert.equal(await page.locator('[data-id="chest"] .gear-level').innerText(), '+16');
  assert.ok((await page.locator('#guarantee').innerText()).includes('1 / 6'));
  await page.locator('#dismiss-receipt').click();
  for (const scenario of ['pity', 'max', 'broken']) {
    await page.locator('#scenario').selectOption(scenario);
    assert.equal(await page.locator('#enhance').isDisabled(), scenario !== 'pity');
    if (scenario === 'pity') assert.ok((await page.locator('#guarantee').innerText()).includes('成功確定'));
    if (scenario === 'max') assert.equal(await page.locator('#after-level').innerText(), 'MAX');
  }
  await page.locator('#reset').click();
  await page.locator('#close-forge').click();
  assert.equal(await page.locator('#forge').isVisible(), false);
  await page.locator('#reopen').click();
  assert.equal(await page.locator('#forge').isVisible(), true);
  for (const [width, height] of [[1920,1080], [1440,900], [390,844]]) {
    await page.setViewportSize({ width, height });
    assert.equal(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth), false, 'No horizontal overflow');
    const unloaded = await page.evaluate(() => [...document.images].filter(i => !i.complete || !i.naturalWidth).map(i => i.getAttribute('src')));
    assert.deepEqual(unloaded, [], 'All displayed image references load');
    await page.screenshot({ path: path.join(out, `responsive-${width}.png`), fullPage: true });
  }
  await browser.close();
  assert.deepEqual(errors, [], 'No browser errors');
  console.log('PASS: five equipment slots, model views, all costs, confirmation/focus, busy, success/failure, shortage, risk, pity, max, broken, close/reopen, responsive images.');
  console.log('Screenshots:', out);
})().catch(e => { console.error(e); process.exitCode = 1; });
