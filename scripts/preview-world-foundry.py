"""Render the actual foundry cuboids, UVs and pixels, then embed them in a portable HTML preview."""
from pathlib import Path
import importlib.util, json, base64, io, hashlib, os
from PIL import Image, ImageDraw

ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'.tools/world-foundry-preview';OUT.mkdir(parents=True,exist_ok=True)
spec=importlib.util.spec_from_file_location('model_renderer',ROOT/'scripts/render-world-models.py')
r=importlib.util.module_from_spec(spec);spec.loader.exec_module(r)
TRACE=ROOT/'server-minestom/build/reports/world-foundry/preview-trace.json'
trace=json.loads(TRACE.read_text(encoding='utf8'))
def model(name,at=(0,0,0),yaw=0):return r.boxes('projects:foundry/'+name,at=at,yaw=yaw)
def scene(stage,shape):
    # Exported by Kotlin from actual rules and WorldFoundryPresentation.frame; no copied browser state machine.
    parts=[]
    for v in trace[shape][stage]['visuals']:
        parts+=model(v['model'],(v['x']+.5,v['y']+.5,v['z']+.5),v['yaw'])
    return parts
bounds=((-0.2,-.2,-.2),(2.3,2.3,1.3))
frames={};gif=[]
for shape in ['rod','plate']:
    frames[shape]=[]
    for stage in range(14):
        im=r.render(scene(stage,shape),(1060,720),-34,26,frame_bounds=bounds,background='#20272b')
        data=io.BytesIO();im.save(data,format='PNG')
        frames[shape].append('data:image/png;base64,'+base64.b64encode(data.getvalue()).decode())
        if shape=='rod':gif.append(im.resize((742,504)))
    (r.render(scene(6,shape),(1200,800),-34,26,frame_bounds=bounds,background='#20272b')).save(OUT/f'ProjectS-Foundry-{shape}-pour.png')
gif[0].save(OUT/'ProjectS-Foundry-Sequence.gif',save_all=True,append_images=gif[1:],duration=[900,1400,1100,900,900,900,2000,1000,1000,1000,1000,1000,1400,900],loop=0)
labels=['設備を組む','炭とインゴットを投入','溶湯をためる','砂床を敷く','木製原型を押す','空手で原型を外す','出湯口から注ぐ','冷却 5秒','冷却 4秒','冷却 3秒','冷却 2秒','冷却 1秒','部品が固まる','空手で取り出す']
notes=['炭床の上にるつぼ。横の出湯口、その真下に鋳造台。位置が合ってから鋳造できる。','右クリックで投入。熱源のない炉は溶けない。炉はインゴット3本分まで。','T2インゴット1本 = 工房湯4単位。液面は保存された金属量を表示。','砂型は1回消費。まだ注いでいなければ、しゃがみ＋空手で砂を回収できる。','芯棒か補強板の木製原型を選ぶ。型に押した原型は一時的に台が保持。','原型を外すまで注湯できない。原型は再利用できる。','炉の金属を鋳造台に一括で移して保存。その確定後に流れを表示。','熱い橙色が少しずつ金属色に変わる。冷却中は取り出しと解体を保護。','冷却はサーバー時間。クリックを繰り返しても速くならない。','離脱中は停止し、再接続時に保存した残り時間から再開。','描画は状態の表示。液体エンティティが消えても金属の正本は消えない。','残り1秒。部品回収はまだできない。','砂は使用済み。金属色に固まった部品が台に残る。','空手で1回回収。出力は保存に確定し、連打でも増えない。']
html='''<!doctype html><html lang="ja"><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1"><title>ProjectS 工房試作 Preview</title>
<style>*{box-sizing:border-box}body{margin:0;background:#151b1f;color:#e7e9e4;font:16px/1.7 system-ui,"Yu Gothic",sans-serif}main{max-width:1280px;margin:auto;padding:30px}h1{font-size:30px;letter-spacing:.05em;margin:0}small,.muted{color:#aeb8bc}header{display:flex;justify-content:space-between;align-items:center;gap:18px}.tag{border:1px solid #57626a;border-radius:4px;padding:4px 12px;color:#c9d3d5}.layout{display:grid;grid-template-columns:minmax(0,3fr) minmax(290px,1fr);gap:22px;margin-top:22px}figure{margin:0;background:#20272b;border:1px solid #3c474e;border-radius:8px;overflow:hidden}img{display:block;width:100%;height:auto}figcaption{padding:12px 22px;color:#afb9bd;background:#1b2227;font-size:13px}.info{background:#20272b;border:1px solid #3c474e;border-radius:8px;padding:22px}h2{font-size:22px;line-height:1.5;margin:12px 0}.step{color:#e3ba7c;font-size:13px;letter-spacing:.1em}button,select{font:inherit;color:#e7e9e4;background:#2c373e;border:1px solid #65717a;border-radius:5px;padding:9px 13px;cursor:pointer}button:hover{background:#42515a}button:focus-visible,select:focus-visible,input:focus-visible{outline:3px solid #e3ba7c;outline-offset:3px}.controls{display:flex;gap:9px;margin:16px 0;flex-wrap:wrap}input{width:100%;accent-color:#e3ba7c}dl{margin:20px 0}dt{color:#aeb8bc;font-size:13px}dd{margin:0 0 9px;color:#e3ba7c}footer{font-size:13px;color:#aeb8bc;padding:24px 0}a{color:#c7d9dc}.note{border-left:3px solid #ce9e60;padding-left:12px}.time{font-size:13px;color:#b7c4c7}@media(max-width:800px){main{padding:18px}.layout{grid-template-columns:1fr}header{display:block}.tag{display:inline-block;margin-top:10px}h1{font-size:24px}}</style>
<main><header><div><small>PROJECT S / WORLD WORKSHOP 01</small><h1>湯をつくる。型で形をつくる。</h1></div><span class="tag">実装モデル Preview</span></header>
<div class="layout"><figure><img id="scene" alt="炉・熱源・蛇口・砂床鋳造台の実装モデル"><figcaption>実装JSON・UV・16pxテクスチャを直接投影。Minecraft実機映像ではありません。</figcaption></figure><section class="info"><label for="shape">つくる形</label><select id="shape"><option value="rod">芯棒 / 湯2単位</option><option value="plate">補強板 / 湯4単位</option></select><p class="step" id="step"></p><h2 id="label"></h2><p id="description"></p><dl><dt>金属の選択</dt><dd>T2インゴット 1素材</dd><dt>選ぶ仕事</dt><dd>設備の位置 → 砂型の形 → 注ぐ量</dd></dl><p class="time">ゲーム側: 溶解6秒 / 注湯2秒 / 冷却5秒。初回の溶解開始は次のサーバー秒。</p></section></div>
<div class="controls"><button id="previous">← 前の工程</button><button id="play">自動再生</button><button id="next">次の工程 →</button><button id="reset">最初へ</button></div><label for="progress" class="muted">工程をスクラブ</label><input id="progress" type="range" min="0" max="13" value="0" aria-label="鋳造工程">
<p class="note">このPreviewは工程と見た目の確認用です。操作・保存・同時操作の検証はKotlinテストで実施します。共有工房・合金・巨大炉・職業ツリーは今回の範囲外。</p>
<footer>比較した参考: <a href="https://slimeknights.github.io/docs/books/1.20/puny-smelting/gallery/" target="_blank" rel="noopener">公式 Puny Smelting 1.20 の静止教本図</a> / <a href="https://slimeknights.github.io/docs/json/recipes/casting/" target="_blank" rel="noopener">Casting Recipes</a>。採用: 上の湯面、下の熱源、横の出湯口、直下の脚付き台、砂型と原型取り外し。モデル・名称・ピクセルは独自制作。動画は未視聴。Java用display/packを使用し、Bedrock描画経路は未実装。</footer></main>
<script>const frames=__FRAMES__,labels=__LABELS__,notes=__NOTES__;let stage=0,timer=null;const $=id=>document.getElementById(id);function render(){$('scene').src=frames[$('shape').value][stage];$('step').textContent=`工程 ${stage+1} / 14`;$('label').textContent=labels[stage];$('description').textContent=notes[stage];$('progress').value=stage}function stop(){clearInterval(timer);timer=null;$('play').textContent='自動再生'}function move(d){stop();stage=Math.max(0,Math.min(13,stage+d));render()}$('shape').onchange=render;$('previous').onclick=()=>move(-1);$('next').onclick=()=>move(1);$('reset').onclick=()=>{stop();stage=0;render()};$('progress').oninput=()=>{stop();stage=Number($('progress').value);render()};$('play').onclick=()=>{if(timer){stop();return}$('play').textContent='一時停止';timer=setInterval(()=>{stage=(stage+1)%14;render()},1200)};render();</script></html>'''
html=html.replace('__FRAMES__',json.dumps(frames)).replace('__LABELS__',json.dumps(labels,ensure_ascii=False)).replace('__NOTES__',json.dumps(notes,ensure_ascii=False))
(OUT/'ProjectS-World-Foundry-Preview.html').write_text(html,encoding='utf8')
(OUT/'model-render-provenance.json').write_text(json.dumps({'renderer':'offline exact cuboid UV texture render','minecraftScreenshot':False,'sourceAssets':r.provenance,'models':'Kotlin authoritative presentation trace','traceSHA256':hashlib.sha256(TRACE.read_bytes()).hexdigest(),'previewFixture':'two ingots, then one rod or plate cast; no game authority in browser'},indent=2),encoding='utf8')
sheet=Image.new('RGB',(1440,1000),'#151b1f')
for i,stage in enumerate([2,4,5,6,9,12]):
    tile=r.render(scene(stage,'rod'),(480,440),-34,26,frame_bounds=bounds,background='#20272b')
    x=(i%3)*480;y=60+(i//3)*460;sheet.paste(tile,(x,y));r.text(sheet,(x+18,y+10),labels[stage],22)
r.text(sheet,(24,12),'ProjectS 工房試作 / 実装モデルの鋳造工程',28)
r.text(sheet,(24,956),'実機映像ではありません。モデル・UV・PNGを直接描画。湯→砂型→注湯→固化。',19)
sheet.save(OUT/'ProjectS-World-Foundry-Overview.png')
print(json.dumps({'files':[{'name':p.name,'size':p.stat().st_size} for p in OUT.iterdir() if p.is_file()]},ensure_ascii=False))
