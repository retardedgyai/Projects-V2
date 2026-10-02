/* Actual tested snapshots: optional preview examples, not native game presets. */
(()=>{
 const cases=JSON.parse(document.getElementById('buildExampleData').textContent);
 const details=document.createElement('details');details.className='atelier-provenance';details.id='buildExamples';
 details.innerHTML='<summary>同じ予算の配分作例</summary><p>戦士・補助編成 / HP吸収あり。48・64・80ptで比べる。</p><div style="display:flex;gap:5px;flex-wrap:wrap"><button data-build-example="weapon">武器育成</button><button data-build-example="survival">生存育成</button><button data-build-example="two_key">2Key混合</button></div><p id="buildExampleSummary">作例を選ぶと配分を読み込む。実戦効果は未反映。</p>';
 document.getElementById('structureCost').closest('details').after(details);
 const labels={weapon:'武器育成',survival:'生存育成',two_key:'2Key混合'};
 let loaded=null;
 function showSummary(){
  const target=document.getElementById('buildExampleSummary');if(!loaded){target.textContent='作例を選ぶと配分を読み込む。実戦効果は未反映。';return}
  const state=treeTest.getState(),ids=new Set(state.learned),same=state.origin==='warrior'&&DATA.budget===loaded.budget&&ids.size===loaded.buildCode.learned.length&&loaded.buildCode.learned.every(id=>ids.has(id))&&Object.entries(loaded.buildCode.travel).every(([id,v])=>state.travel[id]===v)&&largeTreeTest.getProfile().id==='warrior:support:plain:standard'&&document.getElementById('siphon').checked;
  if(!same){target.textContent='配分・装備条件を変更中。作例は選び直せる。実戦効果は未反映。';return}
  const stats=loaded.stats,parts=[['physical','物理'],['attackSpeed','攻速'],['hp','HP'],['armor','物防'],['resist','耐性'],['barrier','障壁'],['leech','HP吸収']].filter(([k])=>stats[k]).map(([k,label])=>label+' +'+Number(stats[k].toFixed(2))+'%');
  target.textContent=labels[loaded.strategy]+' / '+loaded.spent+'pt：'+parts.join(' / ')+(loaded.keys.length?'。最大HP15%減・回避不可。吸収は障壁へ。':'')+' 仮値・実戦未反映。';
 }
 function load(strategy,budget=DATA.budget){
  const sample=cases.find(b=>b.strategy===strategy&&b.budget===budget);if(!sample)throw Error('この予算の作例はありません');
  structureProposal.setMode('proposal',true);treeTest.changeOrigin('warrior');largeTreeTest.setFixture('support','plain','standard',true);largeTreeTest.setBudget(budget);treeTest.importData(sample.buildCode);
  treeTest.selectNode(sample.keys[0]||sample.notables[0].id);treeTest.fit();
  details.open=true;
  loaded=sample;showSummary();
  window.loadedSkillTreeBuildExample={strategy,budget,spent:sample.spent};drawSoon();return sample;
 }
 for(const button of details.querySelectorAll('button'))button.onclick=()=>load(button.dataset.buildExample);
 const previousSide=renderSide;renderSide=()=>{previousSide();showSummary()};
 window.skillTreeBuildExamples={cases,load};
})();
