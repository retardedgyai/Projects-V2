#!/usr/bin/env python3
"""組んだ地図の 3D ページ（視点のボタン付き）を out/<名前>/index.html に書く。Artifact で公開する時の本体。
  python3 kit/page.py <名前> "<題>" "<一言>" 視点:字 視点:字 ...   （視点は save() に書いた名前）
"""
import os, sys, json
name, title, note, *views = sys.argv[1:]
pairs = [v.split(':', 1) for v in views]
html = '''<!doctype html><html lang="ja"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover">
<title>%(title)s</title>
<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=M+PLUS+2:wght@500;700&display=swap">
<style>
:root { --bg:#0F1113; --fg:#F1EEE7; --chip:#1B1F23; --line:#2C3137; --accent:#E9A15A; color-scheme: dark; }
html,body { margin:0; height:100%%; background:var(--bg); color:var(--fg); font-family:"M PLUS 2","Noto Sans JP",sans-serif; }
#c { position:fixed; inset:0; width:100%%; height:100%%; display:block; background:linear-gradient(#7fb2ec,#cfe3f7); }
.bar { position:fixed; left:0; right:0; bottom:0; display:flex; flex-wrap:wrap; gap:6px; padding:10px max(16px, env(safe-area-inset-right,0px)) calc(10px + env(safe-area-inset-bottom,0px)) max(16px, env(safe-area-inset-left,0px)); background:linear-gradient(transparent, rgba(15,17,19,.85) 40%%); }
button { font:inherit; font-weight:700; font-size:13px; color:var(--fg); background:var(--chip); border:1px solid var(--line); border-radius:6px; padding:6px 11px; cursor:pointer; }
button.on { border-color:var(--accent); color:var(--accent); }
.hint { position:fixed; top:calc(10px + env(safe-area-inset-top,0px)); left:max(16px, env(safe-area-inset-left,0px)); right:max(16px, env(safe-area-inset-right,0px)); font-size:12px; line-height:1.6; color:var(--fg); background:rgba(15,17,19,.75); padding:6px 10px; border-radius:6px; max-width:60ch; }
</style></head><body>
<canvas id="c"></canvas>
<div class="hint" id="hint">読み込み中…（大きいので少し待つ）</div>
<div class="bar" id="bar"></div>
<script src="https://cdnjs.cloudflare.com/ajax/libs/three.js/r128/three.min.js"></script>
<script src="https://cdn.jsdelivr.net/npm/three@0.128.0/examples/js/controls/OrbitControls.js"></script>
<script src="https://cdn.jsdelivr.net/npm/three@0.128.0/examples/js/shaders/CopyShader.js"></script>
<script src="https://cdn.jsdelivr.net/npm/three@0.128.0/examples/js/shaders/LuminosityHighPassShader.js"></script>
<script src="https://cdn.jsdelivr.net/npm/three@0.128.0/examples/js/shaders/SSAOShader.js"></script>
<script src="https://cdn.jsdelivr.net/npm/three@0.128.0/examples/js/math/SimplexNoise.js"></script>
<script src="https://cdn.jsdelivr.net/npm/three@0.128.0/examples/js/postprocessing/EffectComposer.js"></script>
<script src="https://cdn.jsdelivr.net/npm/three@0.128.0/examples/js/postprocessing/RenderPass.js"></script>
<script src="https://cdn.jsdelivr.net/npm/three@0.128.0/examples/js/postprocessing/ShaderPass.js"></script>
<script src="https://cdn.jsdelivr.net/npm/three@0.128.0/examples/js/postprocessing/UnrealBloomPass.js"></script>
<script src="https://cdn.jsdelivr.net/npm/three@0.128.0/examples/js/postprocessing/SSAOPass.js"></script>
<script src="house.js"></script><script src="viewer.js"></script>
<script>
const NOTE = %(note)s;
const VIEWS = %(views)s;
const bar = document.getElementById('bar');
const v = buildHouseScene(document.getElementById('c'), 'atlas.png', () => {
  v.lmReady.then(() => { show(VIEWS[0][0]); document.getElementById('hint').textContent = NOTE + '　ドラッグで回す・ホイールで寄る・右ドラッグで動かす'; });
});
function show(k) { v.setView(k); [...bar.querySelectorAll('[data-k]')].forEach(b => b.classList.toggle('on', b.dataset.k === k)); }
for (const [k, label] of VIEWS) { const b = document.createElement('button'); b.textContent = label; b.dataset.k = k; b.onclick = () => show(k); bar.appendChild(b); }
</script>
</body></html>
''' % {'title': title, 'note': json.dumps(note, ensure_ascii=False), 'views': json.dumps(pairs, ensure_ascii=False)}
out = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'out', name, 'index.html')
open(out, 'w').write(html); print(out)
