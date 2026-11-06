/* ブロックの形を箱に分解して、1つのメッシュに焼く。テクスチャは 26.3 の jar から抜いた atlas.png */
(function () {
  const H = window.HOUSE;
  const S = 1 / 16;
  const CLEAR = new Set(['iron_bars', 'oak_leaves', 'azalea_leaves', 'flowering_azalea_leaves', 'spruce_leaves', 'dark_oak_leaves', 'birch_leaves', 'cherry_leaves', 'fire_0']);
  const GLOW = new Set(['shroomlight', 'lantern', 'sea_lantern', 'soul_lantern', 'magma', 'lava_still', 'campfire_fire', 'fire_0']);
  const map = new Map();
  for (const [x, y, z, p] of H.blocks) map.set(x + ',' + y + ',' + z, H.pal[p]);
  const get = (x, y, z) => map.get(x + ',' + y + ',' + z);
  const opaque = (b) => b && b.s === 'full' && !CLEAR.has(b.t);
  // ---- 夜の光：マイクラと同じ「明るさのレベル」。光源から1マスごとに1段暗くなる
  const EMIT = { lantern: 15, soul_lantern: 10, sea_lantern: 15, shroomlight: 15, magma: 3, lava_still: 15, campfire_fire: 15, fire_0: 15 };
  const emitOf = (b) => (!b ? 0 : EMIT[b.t] || (b.fr && /_on$/.test(b.fr) ? 13 : 0));
  const passes = (b) => !b || !(b.s === 'full' && !CLEAR.has(b.t) && !EMIT[b.t]);
  const DIRS6 = [[1, 0, 0], [-1, 0, 0], [0, 1, 0], [0, -1, 0], [0, 0, 1], [0, 0, -1]];
  const LIGHT = new Map(); const lq = [];
  for (const [x, y, z, p] of H.blocks) { const e = emitOf(H.pal[p]); if (e) { LIGHT.set(x + ',' + y + ',' + z, e); lq.push([x, y, z, e]); } }
  for (let qi = 0; qi < lq.length; qi++) {
    const [x, y, z, l] = lq[qi]; if (l <= 1) continue;
    for (const d of DIRS6) {
      const nx = x + d[0], ny = y + d[1], nz = z + d[2], b = get(nx, ny, nz);
      if (!passes(b)) continue;
      const nl = l - 1 - (b && b.s === 'water' ? 1 : 0), k = nx + ',' + ny + ',' + nz;
      if ((LIGHT.get(k) || 0) >= nl) continue;
      LIGHT.set(k, nl); lq.push([nx, ny, nz, nl]);
    }
  }
  const lightAt = (x, y, z) => LIGHT.get(x + ',' + y + ',' + z) || 0;
  // 空の光：上から 15 で降り、不透明な物で止まる。葉と水は1段ずつ減る。そこから横へ1マスごとに1段暗く
  let bx0 = 1e9, bx1 = -1e9, by1 = -1e9, bz0 = 1e9, bz1 = -1e9, by0 = 1e9;
  for (const [x, y, z] of H.blocks) { bx0 = Math.min(bx0, x); bx1 = Math.max(bx1, x); by0 = Math.min(by0, y); by1 = Math.max(by1, y); bz0 = Math.min(bz0, z); bz1 = Math.max(bz1, z); }
  const BIG = (bx1 - bx0 + 3) * (bz1 - bz0 + 3) * (by1 - by0 + 2) > 6e6;   // 手本の地図（数百マス四方）は空の光を1マスずつ覚えると固まるので、柱の高さで決める
  let skyAt;
  if (BIG) {
    const TOP = new Map();
    for (const [x, y, z, p] of H.blocks) { if (!opaque(H.pal[p])) continue; const k = x + ',' + z; if ((TOP.get(k) ?? -1e9) < y) TOP.set(k, y); }
    skyAt = (x, y, z) => { const t = TOP.get(x + ',' + z); return t === undefined || y > t ? 15 : y >= t - 2 ? 11 : 7; };
  } else {
    const SKY = new Map(); const sq = [];
    const DIM = (b) => b && (CLEAR.has(b.t) || b.s === 'water') ? 1 : 0;
    for (let x = bx0 - 1; x <= bx1 + 1; x++) for (let z = bz0 - 1; z <= bz1 + 1; z++) {
      let l = 15;
      for (let y = by1 + 1; y >= by0; y--) {
        const b = get(x, y, z);
        if (!passes(b) || EMIT[b && b.t] && b.s === 'full') break;
        l -= DIM(b); if (l <= 0) break;
        SKY.set(x + ',' + y + ',' + z, l); sq.push([x, y, z, l]);
      }
    }
    for (let qi = 0; qi < sq.length; qi++) {
      const [x, y, z, l] = sq[qi]; if (l <= 1) continue;
      for (const d of DIRS6) {
        const nx = x + d[0], ny = y + d[1], nz = z + d[2];
        if (nx < bx0 - 1 || nx > bx1 + 1 || nz < bz0 - 1 || nz > bz1 + 1 || ny < by0 || ny > by1 + 1) continue;
        const b = get(nx, ny, nz); if (!passes(b) || EMIT[b && b.t] && b.s === 'full') continue;
        const nl = l - 1 - DIM(b), k = nx + ',' + ny + ',' + nz;
        if ((SKY.get(k) || 0) >= nl) continue;
        SKY.set(k, nl); sq.push([nx, ny, nz, nl]);
      }
    }
    skyAt = (x, y, z) => (y > by1 ? 15 : (SKY.get(x + ',' + y + ',' + z) || 0));
  }
  // 面の向きの明るさ（バニラと同じ：上1.0、下0.5、南北0.8、東西0.6）
  const FACE_SHADE = (n) => n[1] > 0 ? 1.0 : n[1] < 0 ? 0.5 : n[2] !== 0 ? 0.8 : 0.6;
  const MOON = [0.35, 0.75, -0.55];
  function nightCol(a, L, n) {
    const m = 0.06 + 0.09 * Math.max(0, n[0] * MOON[0] + n[1] * MOON[1] + n[2] * MOON[2]);
    const f = L <= 0 ? 0 : Math.pow(L / 15, 1.6) * 1.15;
    return [a * (m * 0.75 + f * 1.0), a * (m * 0.85 + f * 0.72), a * (m * 1.2 + f * 0.45)];
  }
  const solidForAO = (b) => b && (b.s === 'full' || b.s === 'stair' || b.s === 'path') && !CLEAR.has(b.t);

  // 面の定義：法線, 4頂点（箱の min/max の選び方）, uv 軸
  const FACES = {
    px: { n: [1, 0, 0], c: [[1, 0, 1], [1, 0, 0], [1, 1, 0], [1, 1, 1]], u: [2, -1], v: 1 },
    nx: { n: [-1, 0, 0], c: [[0, 0, 0], [0, 0, 1], [0, 1, 1], [0, 1, 0]], u: [2, 1], v: 1 },
    py: { n: [0, 1, 0], c: [[0, 1, 1], [1, 1, 1], [1, 1, 0], [0, 1, 0]], u: [0, 1], v: 2, vflip: true },
    ny: { n: [0, -1, 0], c: [[0, 0, 0], [1, 0, 0], [1, 0, 1], [0, 0, 1]], u: [0, 1], v: 2 },
    pz: { n: [0, 0, 1], c: [[0, 0, 1], [1, 0, 1], [1, 1, 1], [0, 1, 1]], u: [0, 1], v: 1 },
    nz: { n: [0, 0, -1], c: [[1, 0, 0], [0, 0, 0], [0, 1, 0], [1, 1, 0]], u: [0, -1], v: 1 },
  };

  function buf() { return { p: [], n: [], uv: [], c: [], nc: [], lv: [], i: [] }; }
  const lit = buf(), glow = buf(), cut = buf(), wat = buf();

  function texRect(name) {
    const i = H.tex[name];
    if (i === undefined) return [0, 0];
    return [(i % H.cols) / H.cols, 1 - (Math.floor(i / H.cols) + 1) / H.rows];
  }
  const TW = 1 / H.cols, TH = 1 / H.rows;

  function ao(bx, by, bz, f, corner) {
    // 面の外側の3つの近傍で頂点を暗くする
    const n = FACES[f].n;
    const ox = bx + n[0], oy = by + n[1], oz = bz + n[2];
    const axes = [0, 1, 2].filter((a) => n[a] === 0);
    const d = axes.map((a) => (corner[a] > 0.5 ? 1 : -1));
    const off = (a, s) => { const v = [0, 0, 0]; v[a] = s; return v; };
    const a0 = off(axes[0], d[0]), a1 = off(axes[1], d[1]);
    const s1 = solidForAO(get(ox + a0[0], oy + a0[1], oz + a0[2])) ? 1 : 0;
    const s2 = solidForAO(get(ox + a1[0], oy + a1[1], oz + a1[2])) ? 1 : 0;
    const cc = solidForAO(get(ox + a0[0] + a1[0], oy + a0[1] + a1[1], oz + a0[2] + a1[2])) ? 1 : 0;
    const k = s1 && s2 ? 3 : s1 + s2 + cc;
    return 1 - k * 0.17;
  }

  function quad(B, verts, norm, uvs, cols, L, Sk) {
    const base = B.p.length / 3;
    for (let k = 0; k < 4; k++) {
      B.p.push(...verts[k]); B.n.push(...norm); B.uv.push(...uvs[k]); B.c.push(cols[k], cols[k], cols[k]);
      B.nc.push(...nightCol(cols[k], L || 0, norm));
      B.lv.push(L || 0, Sk == null ? 15 : Sk, cols[k] * FACE_SHADE(norm));   // 明るさの表を引くための値
    }
    B.i.push(base, base + 1, base + 2, base, base + 2, base + 3);
  }

  // box: [x0,y0,z0,x1,y1,z1] は 0..1。tex: 面ごとの名前。rot: 面ごとに uv を90度回す
  function box(B, bx, by, bz, bb, tex, opts) {
    opts = opts || {};
    for (const f of Object.keys(FACES)) {
      if (opts.only && !opts.only.includes(f)) continue;
      const F = FACES[f];
      const atEdge = (f === 'px' && bb[3] === 1) || (f === 'nx' && bb[0] === 0) || (f === 'py' && bb[4] === 1) ||
        (f === 'ny' && bb[1] === 0) || (f === 'pz' && bb[5] === 1) || (f === 'nz' && bb[2] === 0);
      if (opts.cull && atEdge && opaque(get(bx + F.n[0], by + F.n[1], bz + F.n[2]))) continue;
      const tn = typeof tex === 'string' ? tex : tex[f];
      const [tu, tv] = texRect(tn);
      const verts = [], uvs = [], cols = [];
      for (const c of F.c) {
        const lp = [c[0] ? bb[3] : bb[0], c[1] ? bb[4] : bb[1], c[2] ? bb[5] : bb[2]];
        verts.push([bx + lp[0], by + lp[1], bz + lp[2]]);
        let u = F.u[1] > 0 ? lp[F.u[0]] : 1 - lp[F.u[0]];
        let v = lp[F.v];
        if (F.vflip) v = 1 - v;
        if (opts.rot && opts.rot.includes(f)) { const t = u; u = v; v = 1 - t; }
        if (opts.px) { // テクスチャの一部（ピクセル座標 [u0,v0,u1,v1]、v は上から）
          const r = (f === 'py' || f === 'ny') && opts.pxTop ? opts.pxTop : opts.px;
          u = (r[0] + u * (r[2] - r[0])) / 16; v = 1 - (r[1] + (1 - v) * (r[3] - r[1])) / 16;
        }
        uvs.push([tu + u * TW, tv + v * TH]);
        cols.push(opts.noAO || !atEdge ? 1 : ao(bx, by, bz, f, c));
      }
      const L = atEdge ? lightAt(bx + F.n[0], by + F.n[1], bz + F.n[2]) : Math.max(lightAt(bx, by, bz), lightAt(bx + F.n[0], by + F.n[1], bz + F.n[2]));
      const Sk = atEdge ? skyAt(bx + F.n[0], by + F.n[1], bz + F.n[2]) : Math.max(skyAt(bx, by, bz), skyAt(bx + F.n[0], by + F.n[1], bz + F.n[2]));
      quad(B, verts, F.n, uvs, cols, L, Sk);
    }
  }

  function planeCross(B, bx, by, bz, name, w, h0, h1) {
    const [tu, tv] = texRect(name);
    const a = 0.5 - w / 2, b = 0.5 + w / 2;
    const sets = w < 1
      ? [[[a, 0.5], [b, 0.5]], [[0.5, a], [0.5, b]]]
      : [[[0.15, 0.15], [0.85, 0.85]], [[0.15, 0.85], [0.85, 0.15]]];
    for (const [p0, p1] of sets) {
      const v = [[bx + p0[0], by + h0, bz + p0[1]], [bx + p1[0], by + h0, bz + p1[1]], [bx + p1[0], by + h1, bz + p1[1]], [bx + p0[0], by + h1, bz + p0[1]]];
      const uw = w < 1 ? 3 / 16 : 1;
      const uv = [[tu, tv], [tu + uw * TW, tv], [tu + uw * TW, tv + TH], [tu, tv + TH]];
      const Lc = lightAt(bx, by, bz), Sc = skyAt(bx, by, bz);
      quad(B, v, [0, 1, 0], uv, [1, 1, 1, 1], Lc, Sc);
      quad(B, [v[1], v[0], v[3], v[2]], [0, 1, 0], [uv[1], uv[0], uv[3], uv[2]], [1, 1, 1, 1], Lc, Sc);
    }
  }

  const sides = (b) => {
    const o = { px: b.t, nx: b.t, pz: b.t, nz: b.t, py: b.top || b.t, ny: b.bot || b.t };
    if (b.fr) o[{ n: 'nz', s: 'pz', e: 'px', w: 'nx' }[b.ff]] = b.fr;   // 1つの面だけ別の絵（炉の焚き口など）
    return o;
  };

  for (const [x, y, z, pi] of H.blocks) {
    const b = H.pal[pi];
    const T = b.t;
    const target = GLOW.has(T) ? glow : CLEAR.has(T) ? cut : lit;
    if (b.s === 'full') {
      if (b.axis === 'x') {
        box(target, x, y, z, [0, 0, 0, 1, 1, 1], { px: b.top, nx: b.top, py: T, ny: T, pz: T, nz: T }, { cull: true, rot: ['py', 'ny', 'pz', 'nz'] });
      } else if (b.axis === 'z') {
        box(target, x, y, z, [0, 0, 0, 1, 1, 1], { pz: b.top, nz: b.top, py: T, ny: T, px: T, nx: T }, { cull: true, rot: ['px', 'nx'] });
      } else {
        box(target, x, y, z, [0, 0, 0, 1, 1, 1], sides(b), { cull: !CLEAR.has(T), noAO: GLOW.has(T) });
      }
    } else if (b.s === 'water') {
      // 水：空気に面した面だけ。上面は 14/16 の高さ
      const faces = [];
      for (const f of Object.keys(FACES)) {
        const n = FACES[f].n; const nb = get(x + n[0], y + n[1], z + n[2]);
        if (!nb || (nb.s !== 'water' && nb.s !== 'full' && f !== 'ny')) { if (f !== 'ny') faces.push(f); }
      }
      const above = get(x, y + 1, z);
      box(wat, x, y, z, [0, 0, 0, 1, above && above.s === 'water' ? 1 : 14 / 16, 1], T, { only: faces, noAO: true });
    } else if (b.s === 'anvil') {
      // 金床：台・くびれ・首・天板。天板は向き（ax）に長い
      const z = b.ax === 'x';
      const R = (x0, y0, z0, x1, y1, z1) => z ? [z0 * S, y0 * S, x0 * S, z1 * S, y1 * S, x1 * S] : [x0 * S, y0 * S, z0 * S, x1 * S, y1 * S, z1 * S];
      const tx = { px: 'anvil', nx: 'anvil', pz: 'anvil', nz: 'anvil', py: 'anvil', ny: 'anvil' };
      box(lit, x, y, z, R(2, 0, 2, 14, 4, 14), tx, {});
      box(lit, x, y, z, R(4, 4, 3, 12, 5, 13), tx, {});
      box(lit, x, y, z, R(6, 5, 4, 10, 10, 12), tx, {});
      box(lit, x, y, z, R(3, 10, 0, 13, 16, 16), Object.assign({}, tx, { py: 'anvil_top' }), {});
    } else if (b.s === 'grindstone') {
      // 砥石：丸い石を2本の脚で挟む
      box(lit, x, y, z, [4 * S, 4 * S, 2 * S, 12 * S, 16 * S, 14 * S], { px: 'grindstone_round', nx: 'grindstone_round', pz: 'grindstone_side', nz: 'grindstone_side', py: 'grindstone_side', ny: 'grindstone_side' }, {});
      box(lit, x, y, z, [1 * S, 0, 6 * S, 4 * S, 12 * S, 10 * S], 'dark_oak_log', {});
      box(lit, x, y, z, [12 * S, 0, 6 * S, 15 * S, 12 * S, 10 * S], 'dark_oak_log', {});
    } else if (b.s === 'pane') {
      // 鉄格子など：向き（ax）に沿った薄い板
      const bb = b.ax === 'x' ? [0, 0, 7 * S, 1, 1, 9 * S] : [7 * S, 0, 0, 9 * S, 1, 1];
      box(cut, x, y, z, bb, T, {});
    } else if (b.s === 'pot') {
      // 植木鉢：小さな鉢と、上に植物
      box(lit, x, y, z, [5 * S, 0, 5 * S, 11 * S, 6 * S, 11 * S], 'flower_pot', {});
      if (b.plant) { const sv = (k) => k; planeCross(cut, x, y, z, b.plant, 1, 6 * S, 1); }
    } else if (b.s === 'leaves') {
      box(cut, x, y, z, [0, 0, 0, 1, 1, 1], T, { cull: true, noAO: true });
    } else if (b.s === 'path') {
      box(lit, x, y, z, [0, 0, 0, 1, 15 / 16, 1], sides(b), { cull: true });
    } else if (b.s === 'slab') {
      const bb = b.h === 'top' ? [0, 0.5, 0, 1, 1, 1] : [0, 0, 0, 1, 0.5, 1];
      box(lit, x, y, z, bb, T, { cull: true });
    } else if (b.s === 'stair') {
      const top = b.h === 'top';
      box(lit, x, y, z, top ? [0, 0.5, 0, 1, 1, 1] : [0, 0, 0, 1, 0.5, 1], T, { cull: true });
      const yr = top ? [0, 0.5] : [0.5, 1];
      const f = b.f;
      const r = f === 'e' ? [0.5, 0, 1, 1] : f === 'w' ? [0, 0, 0.5, 1] : f === 's' ? [0, 0.5, 1, 1] : [0, 0, 1, 0.5];
      box(lit, x, y, z, [r[0], yr[0], r[1], r[2], yr[1], r[3]], T, { cull: true });
    } else if (b.s === 'trap' || b.s === 'door' || b.s === 'vine') {
      const t = b.s === 'vine' ? 0.02 : 3 / 16;
      const f = b.f;
      const bb = f === 'n' ? [0, 0, 0, 1, 1, t] : f === 's' ? [0, 0, 1 - t, 1, 1, 1] : f === 'e' ? [1 - t, 0, 0, 1, 1, 1]
        : f === 'w' ? [0, 0, 0, t, 1, 1] : f === 'top' ? [0, 1 - t, 0, 1, 1, 1] : [0, 0, 0, 1, t, 1];
      box(b.s === 'vine' ? cut : lit, x, y, z, bb, T, { noAO: true });
    } else if (b.s === 'fence' || b.s === 'wall') {
      // 柵と塀：柱と、隣の柵・塀・満ブロックへ伸びる横木（マイクラと同じつながり方）
      const wl = b.s === 'wall';
      const p0 = wl ? 4 : 6, p1 = wl ? 12 : 10;
      box(lit, x, y, z, [p0 * S, 0, p0 * S, p1 * S, 1, p1 * S], T, {});
      for (const [dx, dz] of [[1, 0], [-1, 0], [0, 1], [0, -1]]) {
        const nb = get(x + dx, y, z + dz);
        if (!nb || !(nb.s === b.s || nb.s === 'gate' || (nb.s === 'full' && !CLEAR.has(nb.t)))) continue;
        const ys = wl ? [[0, 14]] : [[6, 9], [12, 15]];
        const w0 = wl ? 5 : 7, w1 = wl ? 11 : 9;
        for (const [y0, y1] of ys) {
          const bb = dx === 1 ? [p1 * S, y0 * S, w0 * S, 1, y1 * S, w1 * S] : dx === -1 ? [0, y0 * S, w0 * S, p0 * S, y1 * S, w1 * S]
            : dz === 1 ? [w0 * S, y0 * S, p1 * S, w1 * S, y1 * S, 1] : [w0 * S, y0 * S, 0, w1 * S, y1 * S, p0 * S];
          box(lit, x, y, z, bb, T, {});
        }
      }
    } else if (b.s === 'carpet') {
      box(lit, x, y, z, [0, 0, 0, 1, 1 * S, 1], T, {});
    } else if (b.s === 'lantern') {
      // 吊るす灯り（hang）は上に短い鎖を付けて少し上へ
      const o = b.hang ? 1 : 0;
      box(glow, x, y, z, [5 * S, (1 + o) * S, 5 * S, 11 * S, (8 + o) * S, 11 * S], T, { noAO: true, px: [0, 2, 6, 9], pxTop: [0, 9, 6, 15] });
      box(lit, x, y, z, [6 * S, (8 + o) * S, 6 * S, 10 * S, (10 + o) * S, 10 * S], T, { noAO: true, px: [0, 2, 4, 4], pxTop: [0, 9, 4, 13] });
      if (b.hang) planeCross(cut, x, y, z, 'iron_chain', 3 / 16, 11 * S, 1);
    } else if (b.s === 'chain') {
      planeCross(cut, x, y, z, T, 3 / 16, 0, 1);
    } else if (b.s === 'cross' && T.startsWith('fire')) {
      // 火：ゲームと同じく、マスの4つの側面の内側に立つ板＋十字。背は1.4マス、光る材質で描く（小さな十字だけだと焦げた山に見えた）
      planeCross(glow, x, y, z, T, 1, 0, 1.4);
      const [tu, tv] = texRect(T), e = 0.06, H1 = 1.4, Lc = 15, Sc = skyAt(x, y, z);
      const uv = [[tu, tv], [tu + TW, tv], [tu + TW, tv + TH], [tu, tv + TH]];
      for (const [p0, p1] of [[[e, e], [1 - e, e]], [[1 - e, e], [1 - e, 1 - e]], [[1 - e, 1 - e], [e, 1 - e]], [[e, 1 - e], [e, e]]]) {
        const v = [[x + p0[0], y, z + p0[1]], [x + p1[0], y, z + p1[1]], [x + p1[0], y + H1, z + p1[1]], [x + p0[0], y + H1, z + p0[1]]];
        quad(glow, v, [0, 1, 0], uv, [1, 1, 1, 1], Lc, Sc);
        quad(glow, [v[1], v[0], v[3], v[2]], [0, 1, 0], [uv[1], uv[0], uv[3], uv[2]], [1, 1, 1, 1], Lc, Sc);
      }
    } else if (b.s === 'cross') {
      planeCross(cut, x, y, z, T, 1, 0, 1);
    }
  }

  function mesh(B, mat) {
    const g = new THREE.BufferGeometry();
    g.setAttribute('position', new THREE.Float32BufferAttribute(B.p, 3));
    g.setAttribute('normal', new THREE.Float32BufferAttribute(B.n, 3));
    g.setAttribute('uv', new THREE.Float32BufferAttribute(B.uv, 2));
    g.setAttribute('color', new THREE.Float32BufferAttribute(B.c, 3));
    g.userData.day = new Float32Array(B.c); g.userData.night = new Float32Array(B.nc); g.userData.lv = B.lv;
    // 手本と同じ向きにするため x を鏡写しにする（巻き方向も反転）
    const pos = g.attributes.position.array, nor = g.attributes.normal.array;
    for (let k = 0; k < pos.length; k += 3) { pos[k] = -pos[k]; nor[k] = -nor[k]; }
    const idx = B.i.slice();
    for (let k = 0; k < idx.length; k += 3) { const t = idx[k + 1]; idx[k + 1] = idx[k + 2]; idx[k + 2] = t; }
    g.setIndex(idx);
    const m = new THREE.Mesh(g, mat);
    return m;
  }

  // ---- 明るさの表（lightmap）：ゲームのシェーダーの文をそのまま WebGL2 で動かす
  // 直すのは GLSL ES に無い書き方だけ（#version と #extension、入力の location、1.0f の f）。計算は同じ文。
  const LM_NIGHT = {   // 26.3 が夜に渡す値の見積もり（実機の値は未確認）：空 0.24、灯り 1.5、明るさ設定 0.5
    SkyFactor: 0.24, BlockFactor: 1.5, NightVisionFactor: 0, DarknessScale: 0, Boss: 0, Brightness: 0.5,
    BlockLightTint: [1.0, 0.875, 0.75], SkyLightColor: [0.506, 0.506, 1.0], AmbientColor: [0, 0, 0], NightVisionColor: [1, 1, 1] };
  function runLightmap(src, u) {
    const c = document.createElement('canvas'); c.width = 16; c.height = 16;
    const gl = c.getContext('webgl2'); if (!gl) throw new Error('WebGL2 がない');
    const fs = src.replace(/#version[^\n]*\n/, '').replace(/#extension[^\n]*\n/g, '')
      .replace(/layout\(location = 0\) in vec2 texCoord;/, 'in vec2 texCoord;')
      .replace(/(\d)\.(\d*)f\b/g, '$1.$2')
      .replace(/(?<![eE\w])([*\/+\-]\s*)(\d+)(?![\d.\w])/g, '$1$2.0');   // GLSL 330 は int を float に直すが ES は直さない
    const vs = '#version 300 es\nout vec2 texCoord;\nvoid main(){vec2 uv=vec2((gl_VertexID<<1)&2,gl_VertexID&2);gl_Position=vec4(uv*2.0-1.0,0,1);texCoord=uv;}';
    const sh = (t, s) => { const o = gl.createShader(t); gl.shaderSource(o, s); gl.compileShader(o); if (!gl.getShaderParameter(o, gl.COMPILE_STATUS)) throw new Error(gl.getShaderInfoLog(o)); return o; };
    const pr = gl.createProgram();
    gl.attachShader(pr, sh(gl.VERTEX_SHADER, vs)); gl.attachShader(pr, sh(gl.FRAGMENT_SHADER, '#version 300 es\nprecision highp float;\n' + fs));
    gl.linkProgram(pr); if (!gl.getProgramParameter(pr, gl.LINK_STATUS)) throw new Error(gl.getProgramInfoLog(pr));
    gl.useProgram(pr);
    const data = new Float32Array(24);   // std140：float 6つ、vec3 は 16 バイトずつ
    data.set([u.SkyFactor, u.BlockFactor, u.NightVisionFactor, u.DarknessScale, u.Boss, u.Brightness], 0);
    data.set(u.BlockLightTint, 8); data.set(u.SkyLightColor, 12); data.set(u.AmbientColor, 16); data.set(u.NightVisionColor, 20);
    const ub = gl.createBuffer(); gl.bindBuffer(gl.UNIFORM_BUFFER, ub); gl.bufferData(gl.UNIFORM_BUFFER, data, gl.STATIC_DRAW);
    gl.uniformBlockBinding(pr, gl.getUniformBlockIndex(pr, 'LightmapInfo'), 0); gl.bindBufferBase(gl.UNIFORM_BUFFER, 0, ub);
    gl.viewport(0, 0, 16, 16); gl.drawArrays(gl.TRIANGLES, 0, 3);
    const px = new Uint8Array(16 * 16 * 4); gl.readPixels(0, 0, 16, 16, gl.RGBA, gl.UNSIGNED_BYTE, px);
    return (bl, sl) => { const i = (sl * 16 + bl) * 4; return [px[i] / 255, px[i + 1] / 255, px[i + 2] / 255]; };   // 行 = 空、列 = 灯り
  }
  window.HOUSE_LIGHTMAP = runLightmap;

  window.buildHouseScene = function (canvas, atlasUrl, onReady) {
    const renderer = new THREE.WebGLRenderer({ canvas, antialias: true, alpha: true, preserveDrawingBuffer: true });
    renderer.setClearColor(0x000000, 0);
    renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 2));
    renderer.outputEncoding = THREE.sRGBEncoding;
    renderer.shadowMap.enabled = true;
    renderer.shadowMap.type = THREE.PCFSoftShadowMap;
    renderer.toneMapping = THREE.ACESFilmicToneMapping;
    renderer.toneMappingExposure = 1.05;
    const scene = new THREE.Scene();
    scene.fog = new THREE.Fog(0xb9d3ee, 60, 140);
    const cam = new THREE.PerspectiveCamera(42, 1, 0.5, 900);
    const tex = new THREE.TextureLoader().load(atlasUrl, () => { draw(); onReady && onReady(); });
    tex.magFilter = THREE.NearestFilter; tex.minFilter = THREE.NearestFilter;
    tex.encoding = THREE.sRGBEncoding; tex.generateMipmaps = false;
    const matLit = new THREE.MeshLambertMaterial({ map: tex, vertexColors: true, alphaTest: 0.5 });
    const matCut = new THREE.MeshLambertMaterial({ map: tex, vertexColors: true, alphaTest: 0.5, side: THREE.DoubleSide });
    const matGlow = new THREE.MeshBasicMaterial({ map: tex, alphaTest: 0.5, side: THREE.DoubleSide });   // 火の板の透けを抜く
    const depthCut = new THREE.MeshDepthMaterial({ depthPacking: THREE.RGBADepthPacking, map: tex, alphaTest: 0.5 });
    const matWat = new THREE.MeshLambertMaterial({ map: tex, vertexColors: true, transparent: true, opacity: 0.82, side: THREE.DoubleSide });
    // 夜は光を使わず、頂点の色（月明かり＋光源のレベル）だけで描く
    const matLitN = new THREE.MeshBasicMaterial({ map: tex, vertexColors: true, alphaTest: 0.5 });
    const matCutN = new THREE.MeshBasicMaterial({ map: tex, vertexColors: true, alphaTest: 0.5, side: THREE.DoubleSide });
    const matWatN = new THREE.MeshBasicMaterial({ map: tex, vertexColors: true, transparent: true, opacity: 0.85, side: THREE.DoubleSide });
    const MESHES = [];
    for (const [B, m, mn] of [[lit, matLit, matLitN], [cut, matCut, matCutN], [glow, matGlow, matGlow], [wat, matWat, matWatN]]) {
      const me = mesh(B, m); me.castShadow = m !== matWat; me.receiveShadow = m !== matGlow;
      if (m === matCut) me.customDepthMaterial = depthCut;
      me.userData.matDay = m; me.userData.matNight = mn;
      scene.add(me); MESHES.push(me);
    }
    const ALLMATS = [matLit, matCut, matGlow, matWat, matLitN, matCutN, matWatN];
    renderer.localClippingEnabled = true;
    const hemi = new THREE.HemisphereLight(0xcfe3ff, 0x6b5a3e, 0.62);
    scene.add(hemi);
    const sun = new THREE.DirectionalLight(0xfff1d6, 0.95);
    const T0 = H.target || [-4, 6, 5]; sun.position.set(T0[0] + 42, 34, T0[2] - 38); sun.target.position.set(T0[0], 4, T0[2]);
    sun.shadow.radius = 3;
    sun.castShadow = true; sun.shadow.mapSize.set(4096, 4096);
    const sc = sun.shadow.camera; const R = H.shadowR || 34; sc.left = -R; sc.right = R; sc.top = R; sc.bottom = -R; sc.far = 200; sc.near = 1;
    sun.shadow.bias = -0.0006; sun.shadow.normalBias = 0.02;
    scene.add(sun); scene.add(sun.target);
    const controls = new THREE.OrbitControls(cam, canvas);
    controls.target.set(...(H.target || [-4.5, 10, 5.5]));
    controls.enableDamping = true; controls.maxPolarAngle = Math.PI * 0.495;
    controls.minDistance = 6; controls.maxDistance = 280;
    const VIEWS = H.views || {
      front: [[-3, 6.5, -48], [-4.5, 11.5, 5]],
      gable: [[-4.5, 14, -16], [-4.5, 14.5, 0]],
      angle: [[17, 9.5, -32], [-4.5, 11, 5]],
      side: [[-46, 15, -6], [-4.5, 11, 5.5]],
      back: [[-22, 12, 32], [-4.5, 9, 5]],
      top: [[14, 44, -14], [-4.5, 6, 5.5]],
      eye: [[-9, 2.6, -14], [-5, 8, 4]],
    };
    function setView(k) {
      const v = VIEWS[k] || VIEWS.angle;
      cam.position.set(...v[0]); controls.target.set(...v[1]); controls.update();
      const o = v[2] || {};
      setNight(!!o.night, true, o.lm || 'ours');       // 夜は視点ごと。指定がなければ昼。lm：'ours' か 'vanilla'
      setCut('cut' in o ? o.cut : null, true);          // 輪切りは視点ごと。指定がなければ外す
      draw();
    }
    let night = false, lmMode = 'ours';
    const LMS = {};   // 'ours'（このシェーダー）/ 'vanilla'（素のマイクラ）-> 夜の頂点色
    function bakeLightmap(mode, lm) {
      LMS[mode] = MESHES.map((me) => {
        const lv = me.geometry.userData.lv, out = new Float32Array(lv.length);
        for (let i = 0; i < lv.length; i += 3) {
          const c = lm(lv[i], lv[i + 1]); out[i] = c[0] * lv[i + 2]; out[i + 1] = c[1] * lv[i + 2]; out[i + 2] = c[2] * lv[i + 2];
        }
        return out;
      });
    }
    const lmReady = Promise.all(['lightmap.fsh', 'lightmap_vanilla.fsh'].map((f) => fetch(f).then((r) => r.ok ? r.text() : null).catch(() => null)))
      .then(([ours, van]) => {
        try {
          if (ours) bakeLightmap('ours', runLightmap(ours, LM_NIGHT));
          if (van) bakeLightmap('vanilla', runLightmap(van, LM_NIGHT));
        } catch (e) { console.error('lightmap', e); window.LIGHTMAP_ERROR = String(e); }
        if (night) setNight(true);
        window.LIGHTMAP_READY = Object.keys(LMS).join(',');
      });
    function setNight(on, quiet, mode) {
      night = !!on; if (mode) lmMode = mode;
      MESHES.forEach((me, k) => {
        me.material = night ? me.userData.matNight : me.userData.matDay;
        const baked = LMS[lmMode];
        const glowMesh = me.userData.matNight === me.userData.matDay;
        const src = !night ? me.geometry.userData.day : (baked && !glowMesh ? baked[k] : me.geometry.userData.night);
        const a = me.geometry.attributes.color; a.array.set(src); a.needsUpdate = true;
      });
      sun.visible = !night; hemi.visible = !night; renderer.shadowMap.enabled = !night;
      if (typeof applyLook === 'function') applyLook();
      scene.fog.color.set(night ? 0x0d1430 : 0xb9d3ee);
      renderer.toneMappingExposure = night ? 1.0 : 1.05;
      const bg = night ? 'linear-gradient(#060a1c, #1b2547)' : 'linear-gradient(#7fb2ec, #d6e7f7)';
      if (canvas.parentElement) canvas.parentElement.style.background = bg;
      if (!quiet) draw();
    }
    function setCut(y, quiet) {
      const planes = y == null ? [] : [new THREE.Plane(new THREE.Vector3(0, -1, 0), y)];
      for (const m of ALLMATS) { m.clippingPlanes = planes; m.needsUpdate = true; }
      if (!quiet) draw();
    }
    // ---- シェーダーの見た目（手本の動画の影 MOD に寄せる）：HDR で描き、隙間の影・光のにじみ・色の仕上げを足す
    const FX = { on: !!THREE.EffectComposer };
    function skyTexture(night) {
      const c = document.createElement('canvas'); c.width = 4; c.height = 256; const g = c.getContext('2d');
      const gr = g.createLinearGradient(0, 0, 0, 256);
      if (night) { gr.addColorStop(0, '#050816'); gr.addColorStop(0.55, '#0f1a3a'); gr.addColorStop(1, '#2a3560'); }
      else { gr.addColorStop(0, '#6f9fe0'); gr.addColorStop(0.55, '#a9c8ec'); gr.addColorStop(0.85, '#dbe7f1'); gr.addColorStop(1, '#efe6d6'); }
      g.fillStyle = gr; g.fillRect(0, 0, 4, 256);
      if (!night) {   // 手本のような柔らかい雲（空の上半分）
        const c2 = document.createElement('canvas'); c2.width = 1024; c2.height = 512; const g2 = c2.getContext('2d');
        g2.drawImage(c, 0, 0, 1024, 512); g2.filter = 'blur(5px)';
        let seed = 7; const rnd = () => ((seed = (seed * 16807) % 2147483647) / 2147483647);
        for (let i = 0; i < 16; i++) {
          const x = rnd() * 1024, y = 25 + rnd() * 170, w = 40 + rnd() * 70, h = 10 + rnd() * 14;
          for (let k = 0; k < 6; k++) {
            g2.fillStyle = 'rgba(255,255,255,' + (0.35 + rnd() * 0.35) + ')';
            g2.beginPath(); g2.ellipse(x + (rnd() - 0.5) * w * 1.4, y + (rnd() - 0.6) * h, w * (0.25 + rnd() * 0.25), h * (0.5 + rnd() * 0.5), 0, 0, 7); g2.fill();
          }
        }
        const t2 = new THREE.CanvasTexture(c2); t2.encoding = THREE.sRGBEncoding; return t2;
      }
      const t = new THREE.CanvasTexture(c); t.encoding = THREE.sRGBEncoding; return t;
    }
    const SKY = { day: skyTexture(false), night: skyTexture(true) };
    let composer = null, ssao = null, bloom = null, grade = null;
    const GRADE = {
      uniforms: { tDiffuse: { value: null }, exposure: { value: 1.0 }, sat: { value: 1.12 }, warm: { value: 0.06 }, vig: { value: 0.35 }, night: { value: 0.0 } },
      vertexShader: 'varying vec2 vUv; void main(){ vUv = uv; gl_Position = projectionMatrix * modelViewMatrix * vec4(position, 1.0); }',
      fragmentShader: [
        'uniform sampler2D tDiffuse; uniform float exposure, sat, warm, vig, night; varying vec2 vUv;',
        'vec3 aces(vec3 x){ return clamp((x*(2.51*x+0.03))/(x*(2.43*x+0.59)+0.14), 0.0, 1.0); }',
        'void main(){',
        '  vec3 c = texture2D(tDiffuse, vUv).rgb * exposure;',
        '  float l = dot(c, vec3(0.2126, 0.7152, 0.0722));',
        '  c = mix(vec3(l), c, sat);',
        // 暗い所は青く、明るい所は暖かく（影 MOD の色の分かれ方）
        '  c *= mix(vec3(1.0 - warm*0.6, 1.0 - warm*0.1, 1.0 + warm), vec3(1.0 + warm, 1.0 + warm*0.25, 1.0 - warm*0.7), smoothstep(0.05, 0.6, l));',
        '  c = aces(c);',
        '  vec2 d = vUv - 0.5; c *= 1.0 - vig * dot(d, d) * 1.6;',
        '  gl_FragColor = vec4(pow(c, vec3(1.0/2.2)), 1.0);',
        '}'].join('\n'),
    };
    function buildFX() {
      if (!FX.on || composer) return;
      const w = Math.max(2, canvas.clientWidth), h = Math.max(2, canvas.clientHeight), pr = renderer.getPixelRatio();
      const rt = new THREE.WebGLRenderTarget(w * pr, h * pr, { type: THREE.HalfFloatType });
      composer = new THREE.EffectComposer(renderer, rt);
      composer.addPass(new THREE.RenderPass(scene, cam));
      if (THREE.SSAOPass) {
        ssao = new THREE.SSAOPass(scene, cam, w, h);
        ssao.kernelRadius = 6; ssao.minDistance = 0.0008; ssao.maxDistance = 0.06;
        composer.addPass(ssao);
      }
      bloom = new THREE.UnrealBloomPass(new THREE.Vector2(w, h), 0.55, 0.6, 1.05);
      composer.addPass(bloom);
      grade = new THREE.ShaderPass(GRADE);
      composer.addPass(grade);
    }
    function applyLook() {
      const shader = FX.on && !!composer;
      renderer.toneMapping = shader ? THREE.NoToneMapping : THREE.ACESFilmicToneMapping;
      renderer.outputEncoding = shader ? THREE.LinearEncoding : THREE.sRGBEncoding;
      scene.background = shader ? (night ? SKY.night : SKY.day) : null;
      if (shader && !night) {
        sun.color.set(0xffdcb0); sun.intensity = 1.75; hemi.color.set(0xa9c6ef); hemi.groundColor.set(0x51463a); hemi.intensity = 0.5;
        scene.fog.color.set(0xd3dde6); scene.fog.near = (H.fog || [50, 230])[0]; scene.fog.far = (H.fog || [50, 230])[1];
      } else if (!night) {
        sun.color.set(0xfff1d6); sun.intensity = 0.95; hemi.color.set(0xcfe3ff); hemi.groundColor.set(0x6b5a3e); hemi.intensity = 0.62;
        scene.fog.color.set(0xb9d3ee); scene.fog.near = 60; scene.fog.far = 140;
      }
      matGlow.color.setScalar(shader ? (night ? 1.8 : 1.3) : 1.0);   // 灯り・窓の芯は HDR で明るく、にじませる
      if (grade) {
        grade.uniforms.exposure.value = night ? 0.9 : 0.95;
        grade.uniforms.warm.value = night ? 0.03 : 0.07;
        grade.uniforms.sat.value = night ? 1.05 : 1.04;
        grade.uniforms.vig.value = night ? 0.5 : 0.32;
      }
      if (bloom) { bloom.strength = night ? 0.55 : 0.25; bloom.threshold = night ? 1.0 : 1.2; bloom.radius = night ? 0.45 : 0.4; }
      if (ssao) ssao.enabled = !night;
    }
    function setShader(on) { FX.on = !!on && !!THREE.EffectComposer; if (FX.on) buildFX(); applyLook(); draw(); }
    function resize() {
      const w = canvas.clientWidth, h = canvas.clientHeight;
      renderer.setSize(w, h, false); cam.aspect = w / h; cam.updateProjectionMatrix();
      if (composer) { composer.setSize(w, h); if (ssao) ssao.setSize(w, h); }
      draw();
    }
    function draw() { if (FX.on && composer) composer.render(); else renderer.render(scene, cam); }
    controls.addEventListener('change', draw);
    let raf = 0;
    (function loop() { raf = requestAnimationFrame(loop); if (controls.update()) draw(); })();
    window.addEventListener('resize', resize);
    resize();
    if (FX.on) { buildFX(); applyLook(); }
    return { setView, setNight, setCut, setShader, draw, resize, cam, scene, lmReady, get night() { return night; }, get shader() { return FX.on; } };
  };
})();
