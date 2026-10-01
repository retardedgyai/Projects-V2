// Per-benefit availability, explicitly separate from unimplemented combat effects.
let assumption='none';
function inputFlags(){
 const p=profile(),flags=new Set(p.flags);if(siphon)flags.add('LIFESTEAL');
 for(const f of DATA.assumptions.find(a=>a.id===assumption)?.flags||[])flags.add(f);
 if(!p.weaponUsable&&!flags.has('CROSS_WEAPON_SKILL'))for(const f of new Set(Object.values(DATA.statInputs).flat()))if(!['HEAL','LIFESTEAL'].includes(f))flags.delete(f);
 if([...learned].some(i=>by.get(i).rule==='noDodge'))flags.delete('DODGE');
 return flags;
}
function flagState(f){return f==='RULE_LOSS'?'Keyで喪失する仮案':({unsupported_source:'休眠案・現行入力源なし',unimplemented_connection:'接続処理未実装',unwired_context:'戦闘条件が未接続'})[DATA.flagKinds[f]]||'現在の入力なし'}
inputFor=function(n){
 const p=profile(),flags=inputFlags();if(n.rule==='noDodge'&&p.flags.includes('DODGE'))flags.add('DODGE');
 const cls=DATA.currentCatalog.profiles.find(c=>c.start===origin&&c.kit===kit).job;
 const gate=!(n.referenceJobs||[]).length||n.referenceJobs.includes(cls);
 const required=n.type==='keystone'?[...(n.requirements||[])]:[];if(n.id==='key_10')required.push('DODGE');
 const absent=required.filter(f=>!flags.has(f)),missing=absent.map(f=>(DATA.requirements[f]||f)+'（'+flagState(f)+'）');
 if(!p.weaponUsable&&n.type==='keystone'&&!flags.has('CROSS_WEAPON_SKILL'))missing.push('選択職ではこの武器を装備できない');
 const lost=new Set([...learned].flatMap(i=>DATA.lostStats[by.get(i).rule]||[]));
 const usable={},unsupported={};
 for(const [k,v] of Object.entries(statFor(n))){const needs=(DATA.statInputs[k]||[]).filter(f=>!flags.has(f));if(lost.has(k))needs.push('RULE_LOSS');if(needs.length)unsupported[k]={value:v,flags:needs};else usable[k]=v;}
 if(n.type!=='keystone')for(const [k,v] of Object.entries(unsupported))missing.push(DATA.stats[k].label+'：'+v.flags.map(flagState).join(' / '));
 return {present:n.type==='start'||(n.type==='keystone'?!missing.length&&gate:Object.keys(usable).length>0),missing,gate,usable,unsupported};
};
const structuralShortest=shortest;
shortest=function(target,ban=null){
 if(learned.has(target))return [target];const dist=new Map(),prev=new Map(),open=new Set(),usable=new Set(DATA.nodes.filter(n=>inputFor(n).present).map(n=>n.id));
 for(const id of learned){dist.set(id,0);open.add(id)}
 while(open.size){let cur=null,best=Infinity;for(const i of open)if(dist.get(i)<best){best=dist.get(i);cur=i}open.delete(cur);
  if(cur===target){const p=[cur];while(prev.has(cur)){cur=prev.get(cur);p.push(cur)}return p.reverse()}
  for(const nx of adj.get(cur)){const n=by.get(nx);if(ban&&ekey(cur,nx)===ban||n.type==='start'&&nx!==rootId()||n.type==='keystone'&&nx!==target&&!learned.has(nx)||nx!==target&&!learned.has(nx)&&!usable.has(nx))continue;
   const cost=best+(learned.has(nx)?0:n.cost);if(cost<(dist.get(nx)??Infinity)){dist.set(nx,cost);prev.set(nx,cur);open.add(nx)}}
 }return null;
};
function usableSums(ids){const sums={};for(const i of ids)for(const [k,v] of Object.entries(inputFor(by.get(i)).usable))sums[k]=(sums[k]||0)+v;return sums}
function statText(stats){return Object.entries(stats).map(([k,v])=>escapeHTML(DATA.stats[k].label+' '+value(k,v))).join(' / ')||'なし'}
const previousInputSide=renderSide;
renderSide=function(){
 previousInputSide();const n=by.get(selected),input=inputFor(n);
 $('nodeMods').innerHTML=Object.entries(input.usable).map(([k,v])=>'<div class="mod">'+escapeHTML(DATA.stats[k].label+' '+value(k,v))+' <small>入力あり</small></div>').join('')+Object.entries(input.unsupported).map(([k,v])=>'<div class="inactive">'+escapeHTML(DATA.stats[k].label+' '+value(k,v.value))+' <small>'+v.flags.map(flagState).map(escapeHTML).join(' / ')+'</small></div>').join('');
 $('nodeCondition').innerHTML=(n.type==='keystone'?(input.present?'比較条件の入力を持つ。共通効果は未実装。':'入力不足：'+input.missing.map(escapeHTML).join(' / ')):'<b>使える入力を持つ仮値：</b>'+statText(input.usable)+(input.missing.length?'<details><summary>条件不足の恩恵</summary>'+input.missing.map(escapeHTML).join('<br>')+'</details>':''))+(input.gate?'':'<br>参考実装は別職限定。共通版は未実装。')+(n.type==='keystone'?'<br><b>失うもの：</b>'+escapeHTML(n.tradeoff)+'<br>'+n.risks.map(escapeHTML).join(' / '):'');
 $('routeNote').textContent=learned.has(n.id)?'他の取得点を孤立させる返還は拒否。先端から返還するか別経路をつなぐ。':'途中は使える恩恵がない点を避ける。目標自体は休眠案でも仮取得できる。経路は最大2例の費用サンプルで、最良・網羅・Pareto最適は保証しない。';
 const plan=plans[planIndex];if(plan){const ns=plan.fresh.map(i=>by.get(i)),inactive=ns.filter(x=>!inputFor(x).present);$('routeBenefit').innerHTML=ns.filter(x=>x.type==='notable').map(x=>'<b>'+escapeHTML(x.name)+'</b>').join(' / ')+'<br>使える入力の仮合計：'+statText(usableSums(plan.fresh))+'<br>Small '+ns.filter(x=>x.type==='small').length+' · Notable '+ns.filter(x=>x.type==='notable').length+' · Keystone '+ns.filter(x=>x.type==='keystone').length+' · 接続 '+ns.filter(x=>x.type==='road').length+'<br>'+(inactive.length?'全恩恵の入力がない目標 '+inactive.length+'点：'+inactive.map(x=>escapeHTML(x.name)).join(' / '):'途中に完全無効の点なし');}
 if(plan)$('routeMods').innerHTML=statText(usableSums(plan.fresh));
 const a=DATA.assumptions.find(a=>a.id===assumption);if(a)$('profileWarning').textContent+=' / 仮入力テスト：'+a.note;
 if(n.sourceState==='review_diff_proposal')$('nodeProvenance').textContent='独立レビューによる再接続・恩恵の変更案。原本保持・実戦未反映。';
};
const previousInputBuild=renderBuild;
renderBuild=function(){
 previousInputBuild();const nodes=[...learned].filter(i=>i!==rootId()).map(i=>by.get(i));
 $('buildStats').innerHTML='<div class="kind-help">使える入力を持つ仮値の合計（実戦未反映）</div>'+statText(usableSums(learned))+'<details><summary>一部の恩恵に入力がない点</summary>'+nodes.filter(n=>inputFor(n).present&&inputFor(n).missing.length).map(n=>'<p>'+escapeHTML(n.name)+'：'+inputFor(n).missing.map(escapeHTML).join(' / ')+'</p>').join('')+'</details>';
 $('demoSummary').textContent='基本編成・標準武器・追加属性なしに揃え、両例とも全予算を使う。各投資は使える入力を持つ。実戦強さ・最適性は未判定。';
};
refreshFixture=function(){plans=buildPlans(selected);planIndex=0;setPlan();renderSide();renderBuild();drawSoon();};
$('assumption').onchange=e=>{assumption=e.target.value;refreshFixture()};
for(const id of ['demoNoKey','demoKeys']){const previous=$(id).onclick;$(id).onclick=()=>{kit='basic';element='plain';weaponMode='standard';siphon=false;assumption='none';$('kit').value=kit;$('element').value=element;$('weaponMode').value=weaponMode;$('siphon').checked=false;$('assumption').value='none';previous();};}
const oldSetFixture=largeTreeTest.setFixture;
largeTreeTest.setFixture=(...args)=>{assumption='none';$('assumption').value='none';oldSetFixture(...args)};
largeTreeTest.inputFor=inputFor;largeTreeTest.usableSums=usableSums;largeTreeTest.sourceShortest=shortest;largeTreeTest.structuralShortest=structuralShortest;
largeTreeTest.setAssumption=id=>{assumption=id;$('assumption').value=id;refreshFixture()};
if(DATA.chainReview)$('diag').innerHTML+='<br><b>次数2の連続中間点：最大'+DATA.chainReview.before.maxDegree2Interiors+'点 → '+DATA.chainReview.after.maxDegree2Interiors+'点</b><br>終端報酬の枝も数えた値。寄り道を除く継続経路では、全体最大'+DATA.chainReview.continuing.maxDegree2Interiors+'点、入力が成立する経路で最大'+DATA.chainReview.sourceContinuingMaxDegree2Interiors+'点。次の継続先まで3pt以内とは断定しない。';
$('budgetEvidence').innerHTML='<table class="budget-table"><tr><th>仮の比較pt</th><th>Notable個別到達</th><th>Key個別到達</th></tr>'+DATA.budgetAudit.budgets.map(b=>'<tr><td>'+b.budget+'</td><td>'+b.notablesMin+'〜'+b.notablesMax+' / '+b.notablesTotal+'</td><td>'+b.keysMin+'〜'+b.keysMax+' / 15</td></tr>').join('')+'</table><p>構造上の最短で全Keyへ44pt以内に個別到達。入力制限を含まず、全取得や入力成立の保証ではない。48/64/80ptは同支出で途中の能力と交換条件を比較する仮設定。64は画面の初期値で本番未採用。</p><p>Keyなし／Keyありの例は全予算を使い、各投資が使える入力を持つ。係数・戦闘強さ・最良経路は未検証。</p>';
refreshFixture();
