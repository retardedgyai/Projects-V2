"""A UI-only successor to preserved V9: compare the very same target across starts."""
import hashlib
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
BASE = ROOT / 'assets/core-ui/large-tree-preview/proposals'
SOURCE = BASE / 'workshop-specialization-v9/ProjectS_Specialization_V9.html'
OUT = BASE / 'workshop-affinity-v10'
OUT.mkdir(exist_ok=True)
html = SOURCE.read_text(encoding='utf8')

def replace(old, new):
    global html
    assert html.count(old) == 1, (old[:100], html.count(old))
    html = html.replace(old, new)

replace('<button id="compareStarts">始点と2方向</button>', '<button id="compareStarts">始点と2方向</button><button id="affinityToggle" aria-pressed="false">最安の始点</button>')
replace('<div class="map-key">', '<div id="affinityLegend" class="affinity-legend" hidden></div><div class="map-key">')
replace('<div id="effectContract"></div>', '<section id="targetCosts" aria-live="polite"></section><div id="effectContract"></div>')
replace('<h3>専門帯の深掘りまで、何ptか</h3>', '<details id="sameTargetAudit"><summary>既存外側の同じ目標を、5始点で比べる</summary><p class="compare-note" id="sameTargetNote"></p><div class="cost-wrap"><table class="cost-table" id="sameTargetMatrix"></table></div></details><h3>専門帯の深掘りまで、何ptか</h3>')
replace('<div class="kicker">全体配置案 / 実戦未反映</div>', '<div class="kicker">V10 · 始点の費用比較 / 実戦未反映</div>')

# Put the selected point first; keep input configuration and large totals within reach.
detail = re.search(r'<div class="detail">.*?</div><button class="buttonwide" id="reset">', html, re.S)
assert detail
detail_markup = detail[0].removesuffix('<button class="buttonwide" id="reset">')
html = html[:detail.start()] + '<button class="buttonwide" id="reset">' + html[detail.end():]
replace('<div id="directionIntent" class="intent"></div>', detail_markup + '<div id="directionIntent" class="intent"></div>')
replace('<label>職業<select id="origin">', '<details class="input-settings"><summary>編成・MOD・比較予算を変更</summary><label>職業<select id="origin">')
replace('</select></label><div class="budget" id="spent">', '</select></label></details><div class="budget" id="spent">')

css = '''
[hidden]{display:none!important}.toolbar{flex-wrap:wrap}.map-key{top:78px}.affinity-legend{position:absolute;top:78px;left:18px;max-width:calc(100% - 36px);display:flex;gap:7px 14px;flex-wrap:wrap;background:#18221af5;border:1px solid #687352;padding:8px 12px;font-size:11px;pointer-events:none}.affinity-legend span{white-space:nowrap}.affinity-legend i{display:inline-block;width:8px;height:8px;margin-right:5px;background:var(--job-color)}.affinity-legend~.map-key{display:none}.affinity-legend[hidden]~.map-key{display:flex}.input-settings{margin-top:12px}.detail{margin-top:12px;padding-top:12px}#targetCosts{border:1px solid #687352;background:#222e22;padding:10px 12px;margin:12px 0}#targetCosts h3{margin:0 0 6px;font-size:15px}#targetCosts p{margin:0 0 9px;font-size:10px}.start-price{display:grid;grid-template-columns:90px minmax(20px,1fr) 42px;align-items:center;gap:9px;margin:8px 0;font-size:11px}.start-price b{text-align:right;font-weight:normal}.price-track{height:5px;background:#3b4837}.price-track i{display:block;height:5px;background:var(--job-color)}.start-price.best{color:#f2d38f}.target-outcome{font-size:11px;color:#dccc9b;line-height:1.7}#sameTargetAudit{padding:12px;border:1px solid #596347}.tiny{overflow-wrap:anywhere}button[aria-pressed=true]{border-color:#d7ba7e;background:#4b4a31}@media(max-width:800px){header>.kicker{max-width:85px;text-align:right;font-size:8px}.map-key,.affinity-legend{top:112px;font-size:9px;padding:6px 8px}.affinity-legend{left:8px;max-width:calc(100% - 16px);gap:6px 9px}.toolbar{align-items:center}.toolbar select{max-width:none}.input-settings{margin:8px 0}.start-price{grid-template-columns:98px minmax(20px,1fr) 45px}.stage{height:65vh}.route-note{font-size:10px}}
'''
replace('</style>', css + '</style>')

js = r'''
const startColors={warrior:'#d4ab66',tank:'#94c9c2',mage:'#c1a6db',ranger:'#b8ce83',assassin:'#dda59a'},affinityData=new Map();
let affinityOn=false;
function targetAffinity(id){
 if(affinityData.has(id))return affinityData.get(id);
 const n=by.get(id);if(!n||n.type==='start'||n.type==='keystone')return null;
 const atlas=cachedAtlas('fixed').current,costs=Object.fromEntries(S.origins.map(o=>[o.id,atlas[o.id][id]??null]));
 const reachable=Object.values(costs).filter(Number.isFinite),min=reachable.length?Math.min(...reachable):null;
 const winners=min===null?[]:S.origins.filter(o=>costs[o.id]===min).map(o=>o.id);
 const result={id,costs,min,winners};affinityData.set(id,result);return result;
}
function affinityColor(n){if(!affinityOn||n.type!=='notable')return null;const a=targetAffinity(n.id);return !a?.winners.length?null:a.winners.length===1?startColors[a.winners[0]]:'#d3d0bb'}
function renderTargetCosts(){
 const box=el('targetCosts'),n=by.get(selected);box.replaceChildren();
 const title=document.createElement('h3');title.textContent='同じ目標までの費用';box.append(title);const target=document.createElement('div');target.className='target-outcome';target.textContent=n.name;box.append(target);
 const note=document.createElement('p');note.textContent='固定Warrior-support入力 / 始点から未配分 / Keyなし / 通路HP';box.append(note);
 const a=targetAffinity(n.id);
 if(!a){const p=document.createElement('div');p.className='target-outcome';p.textContent=n.type==='keystone'?'Keyは代償と入力条件を伴います。現在の配分からの取得条件と費用を上で確認してください。':'職業の始点は取得目標の比較対象外です。普通の育成点を選ぶと5始点を比較できます。';box.append(p);return}
 const max=Math.max(1,...Object.values(a.costs).filter(Number.isFinite));
 for(const o of S.origins){const row=document.createElement('div');row.className='start-price'+(a.winners.includes(o.id)?' best':'');row.style.setProperty('--job-color',startColors[o.id]);const name=document.createElement('span');name.textContent=o.name;const track=document.createElement('div');track.className='price-track';const fill=document.createElement('i');fill.style.width=(a.costs[o.id]===null?0:a.costs[o.id]/max*100)+'%';track.append(fill);const cost=document.createElement('b');cost.textContent=a.costs[o.id]===null?'—':a.costs[o.id]+'pt';row.append(name,track,cost);box.append(row)}
 const result=document.createElement('div');result.className='target-outcome';result.textContent=a.winners.length?'最安：'+a.winners.map(j=>S.origins.find(o=>o.id===j).name).join('・')+' / '+a.min+'pt。各始点から同じ点へ別々に到達する費用です。':'固定入力で有効な経路がありません。';box.append(result);
}
function sameTargetAudit(){
 const {current,previous}=cachedAtlas('fixed'),ids=G.nodes.filter(n=>n.type==='notable'&&!n.opening&&!specializationIds.includes(n.id)&&S.origins.every(o=>Number.isFinite(current[o.id][n.id])&&Number.isFinite(previous[o.id][n.id]))).map(n=>n.id).sort();
 const pairs={};for(const a of S.origins){pairs[a.id]={};for(const b of S.origins){const r={a:0,b:0,tie:0};for(const id of ids)r[current[a.id][id]<current[b.id][id]?'a':current[b.id][id]<current[a.id][id]?'b':'tie']++;pairs[a.id][b.id]=r}}
 let hash=2166136261;for(const c of ids.join(',')){hash^=c.charCodeAt(0);hash=Math.imul(hash,16777619)}return {ids,idHash:(hash>>>0).toString(16).padStart(8,'0'),pairs};
}
function renderSameTargetAudit(){
 const r=sameTargetAudit();el('sameTargetNote').textContent='固定入力で、V8・V9の全5始点から届く同一の既存外側Notable '+r.ids.length+'点（ID集合 '+r.idHash+'）。行の始点が安い / 列の始点が安い / 同費用。中央・初期帯・新専門帯は除外。1目標ずつの比較で、同時取得費用ではありません。';
 el('sameTargetMatrix').innerHTML='<thead><tr><th>行 ＼ 列</th>'+S.origins.map(o=>'<th>'+o.name+'</th>').join('')+'</tr></thead><tbody>'+S.origins.map(a=>'<tr><th>'+a.name+'</th>'+S.origins.map(b=>{const p=r.pairs[a.id][b.id];return '<td>'+(a.id===b.id?'—':p.a+' / '+p.b+' / '+p.tie)+'</td>'}).join('')+'</tr>').join('')+'</tbody>';
}
for(const o of S.origins){const span=document.createElement('span');span.style.setProperty('--job-color',startColors[o.id]);const dot=document.createElement('i');span.append(dot,document.createTextNode(o.name));el('affinityLegend').append(span)}
const legendNote=document.createElement('span');legendNote.textContent='同費用は白 / 固定入力・同じ目標 / 職業制限なし';el('affinityLegend').append(legendNote);
el('affinityToggle').onclick=()=>{affinityOn=!affinityOn;el('affinityToggle').setAttribute('aria-pressed',String(affinityOn));el('affinityLegend').hidden=!affinityOn;draw()};
'''
replace('function render(){', js + '\nfunction render(){')
replace("major?ok?tone(n):'#88967d'", "major?affinityColor(n)||(ok?tone(n):'#88967d')")
replace('renderContract(n);renderDirection();draw()}', 'renderContract(n);renderDirection();renderTargetCosts();draw()}')
replace("const job=el('compareJob').value,mode=el('compareMode').value,cols=S.origins,pair=el('directionPair');", "renderSameTargetAudit();const job=el('compareJob').value,mode=el('compareMode').value,cols=S.origins,pair=el('directionPair');")
replace('window.wholeTree={source:S,', 'window.wholeTree={source:S,targetAffinity,sameTargetAudit,affinityColor,renderTargetCosts,')

# This is UI-only: compatible V9 study codes retain their identity and validation.
payload_pattern = r'<script id="wholeTreeData" type="application/json">(.*?)</script>'
source_payload = json.loads(re.search(payload_pattern, SOURCE.read_text(encoding='utf8'), re.S)[1])
assert json.loads(re.search(payload_pattern, html, re.S)[1]) == source_payload
destination = OUT / 'ProjectS_Affinity_V10.html'
destination.write_text(html, encoding='utf8')
manifest = dict(status='UI_CANDIDATE_PENDING_BROWSER',runtimeApplied=False,graphChanged=False,nativeFilesChanged=False,saveStudy='specialization-v9',v9SaveCompatible=True,nodes=850,edges=1009,groups=67,sourceHtml=str(SOURCE.relative_to(ROOT)),sourceSha256=hashlib.sha256(SOURCE.read_bytes()).hexdigest(),htmlSha256=hashlib.sha256(destination.read_bytes()).hexdigest(),adoptedFromOpus=['same-target five-start cost presentation','optional start affinity view'],independentlyReimplemented=True,excludedFromOpus=['fixture commit ledger','automatic candidate edge','region minima across different target IDs','profile flag vocabulary in player-facing panel'])
(OUT / 'proposal-manifest.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2),encoding='utf8')
print(json.dumps(dict(html=str(destination.relative_to(ROOT)),bytes=destination.stat().st_size,payloadExact=True),ensure_ascii=False))
