// Minimal renderer for the Forge3 design artboard, used only by build_forge_v3_assets.py.
// Supports the subset the artboard uses: {{holes}}, <sc-for>, <sc-if> and the Component class.
// Hash modes: #full, #chrome (static parts only, for the game's background plate), #measure (dump boxes).
const [mode, override] = (location.hash.slice(1) || 'full').split('&');
const component = new Component({});
if (override) Object.assign(component.state, JSON.parse(decodeURIComponent(override)));
const values = component.renderVals();
const lookup = (ctx, path) => path.split('.').reduce((o, k) => o == null ? undefined : o[k], ctx);
const HOLE = /\{\{\s*([\w.$]+)\s*\}\}/g;
const hole = s => { const m = /^\{\{\s*([\w.$]+)\s*\}\}$/.exec(s.trim()); return m && m[1]; };
const fill = (s, ctx) => s.replace(HOLE, (_, p) => { const v = lookup(ctx, p); return v == null ? '' : String(v); });

function render(node, ctx, out) {
  for (const child of node.childNodes) {
    if (child.nodeType === 3) { out.appendChild(document.createTextNode(fill(child.textContent, ctx))); continue; }
    if (child.nodeType !== 1) continue;
    if (child.localName === 'sc-for') {
      const list = lookup(ctx, hole(child.getAttribute('list'))) || [];
      const as = child.getAttribute('as');
      list.forEach((item, i) => render(child, Object.assign({}, ctx, { [as]: item, $index: i }), out));
      continue;
    }
    if (child.localName === 'sc-if') { if (lookup(ctx, hole(child.getAttribute('value')))) render(child, ctx, out); continue; }
    const el = child.cloneNode(false);
    for (const a of [...child.attributes]) {
      if (a.name.startsWith('on')) { el.removeAttribute(a.name); continue; }
      const h = hole(a.value);
      if (h) { const v = lookup(ctx, h); if (v === false || v == null) el.removeAttribute(a.name); else el.setAttribute(a.name, String(v)); }
      else if (a.value.includes('{{')) el.setAttribute(a.name, fill(a.value, ctx));
    }
    out.appendChild(el);
    render(child, ctx, el);
  }
}
render(document.getElementById('tpl').content, values, document.getElementById('root'));

const all = selector => [...document.querySelectorAll(selector)];
const ownText = el => [...el.childNodes].filter(n => n.nodeType === 3).map(n => n.textContent.trim()).join('');
const byText = text => all('#root *').find(el => ownText(el) === text);

if (mode === 'chrome') {
  // Everything the game redraws from live data is hidden; the rest becomes one background plate.
  const css = document.createElement('style');
  css.textContent = '#root * { visibility: hidden; } #root > div > svg:first-child, #root > div > svg:first-child * { visibility: visible; }' +
    '#root .box { visibility: visible; } #root .keep, #root .keep * { visibility: visible; }' +
    '#root .keep .drop, #root .keep .drop * { visibility: hidden; }';
  document.head.appendChild(css);
  const keep = el => el && el.classList.add('keep');
  const box = el => el && el.classList.add('box');
  const drop = el => el && el.classList.add('drop');
  const header = all('header')[0];
  keep(header);
  header.querySelectorAll('.num').forEach(drop);
  drop(byText('鍛冶熟練').nextElementSibling.firstElementChild);
  const [left, center, right] = all('section');
  box(left);
  keep(left.firstElementChild);
  keep(left.lastElementChild);
  all('section span').filter(s => s.style.borderRadius === '50%').forEach(keep);
  const track = byText('強化段階 +0 〜 +30').closest('.panel');
  box(track);
  keep(track.firstElementChild);
  box(right);
  keep(byText('成功率'));
  const pity = byText('天井（連続失敗）');
  box(pity.parentElement);
  keep(pity);
  keep(byText('銀貨は使いません'));
  keep(byText('鍛造記録'));
}

if (mode === 'measure') {
  const items = [];
  all('#root *').forEach((el, i) => {
    const r = el.getBoundingClientRect();
    if (!r.width || !r.height) return;
    const cs = getComputedStyle(el);
    const text = ownText(el);
    let textBox = null;
    if (text) { const range = document.createRange(); range.selectNodeContents(el); const b = range.getBoundingClientRect(); textBox = [b.x, b.y, b.width, b.height]; }
    items.push({ i, tag: el.localName, cls: el.getAttribute('class') || '', box: [r.x, r.y, r.width, r.height], text: text || null, textBox,
      font: cs.fontFamily, size: parseFloat(cs.fontSize), weight: cs.fontWeight, color: cs.color, bg: cs.backgroundColor,
      radius: cs.borderTopLeftRadius, align: cs.textAlign, src: el.getAttribute('src') });
  });
  document.getElementById('out').textContent = JSON.stringify(items);
}
