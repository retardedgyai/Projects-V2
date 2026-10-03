/* Reuse the complete inspected UI suite, with the added-node contract and
 * actual-segment clearance cache independently cross-checked once. */
const fs=require('node:fs'),assert=require('node:assert/strict'),Module=require('node:module');
let suite=fs.readFileSync(__dirname+'/verify-skill-tree-poe2-central-v6.cjs','utf8');
suite=suite.replaceAll('workshop-poe2-central-v6','workshop-middle-choice-v7').replaceAll('ProjectS_PoE2_Central_V6.html','ProjectS_Middle_Choices_V7.html').replaceAll('ProjectS_PoE2_V6_','ProjectS_Middle_V7_').replaceAll('poe2-central-v6','middle-choice-v7');
suite=suite.replace('assert.equal(graph.nodes.length,645);assert.equal(graph.groups.length,47);','assert(graph.nodes.length>645);assert(graph.groups.length>47);').replace('size,645);for(const n of graph.nodes)assert.deepEqual(strip(n),strip(original.get(n.id)));','size,graph.nodes.length);for(const n of graph.nodes)if(original.has(n.id))assert.deepEqual(strip(n),strip(original.get(n.id)));');
suite=suite.replace("[['critical','quiet'],['tank-life','tank-shield'],['mage-mp','mage-ice']]","[['critical','quiet'],['tank-life','tank-shield'],['mage-mp','mage-ice'],['v7g07-a','v7g07-b'],['v7g04-a','v7g04-b'],['critical','legacy-critical'],['tank-life','legacy-tank-life']]");
const previous=fs.readFileSync(__dirname+'/verify-skill-tree-poe2-middle-v6.cjs','utf8');
const start=previous.indexOf('const inject=')+'const inject='.length,end=previous.indexOf('\nsuite=suite.replace');
const literal=previous.slice(start,end).trim().replace(/;$/,'');
const inject=Function('return ('+literal+')')();
const needle=' const frames=[],audits=[],cases=[];';assert.equal(suite.split(needle).length,2);suite=suite.replace(needle,inject+needle);
suite=suite.replace('realMouseAndTouch:true,networkRequests:0','realMouseAndTouch:true,middleChoiceRevision:true,clearanceCacheCrossCheckedAgainstExhaustive:true,networkRequests:0');
const m=new Module(__dirname+'/verify-skill-tree-middle-choice-v7.cjs',module);m.filename=__dirname+'/verify-skill-tree-middle-choice-v7.cjs';m.paths=module.paths;m._compile(suite,m.filename);
