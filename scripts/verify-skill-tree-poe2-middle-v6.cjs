/* Run the full existing 120-case / 94-view suite with equivalent cached
 * clearance arithmetic. Cross-check once against the exhaustive browser audit.
 * The cache is generated independently from all actual world-space segments.
 * Only QA is accelerated; the shipped UI and allocation rules are untouched. */
const fs=require('node:fs'),assert=require('node:assert/strict'),Module=require('node:module');
let suite=fs.readFileSync(__dirname+'/verify-skill-tree-poe2-central-v6.cjs','utf8');
const needle=' const frames=[],audits=[],cases=[];';assert.equal(suite.split(needle).length,2);
const inject=`
 await run(\`window.boundedAudit=()=>{let minGlyph=Infinity,minHit=Infinity,minPair=Infinity,nearest=null;for(const n of G.nodes){const d=P.paintBounds[n.id].wire*cam.z,gap=d-radius(n)-halo()-nodeStroke()/2-wireWidth()/2;if(gap<minGlyph){minGlyph=gap;nearest={node:n.id}}minHit=Math.min(minHit,d-radius(n)-hitPad()-wireWidth()/2)}for(let i=0;i<G.nodes.length;i++)for(let j=i+1;j<G.nodes.length;j++){const a=G.nodes[i],b=G.nodes[j];minPair=Math.min(minPair,Math.hypot(a.x-b.x,a.y-b.y)*cam.z-radius(a)-radius(b)-2*halo()-nodeStroke())}const segments=screenSegments(),labelLines=[];for(const c of captionBoxes){const q={l:c.box.l-4,r:c.box.r+4,t:c.box.t-4,b:c.box.b+4};for(const s of segments)if(lineBox(q,s.a,s.b))labelLines.push({label:c.text,edge:[s.e.a,s.e.b]})}const cp=point({x:P.centralCenter[0],y:P.centralCenter[1]}),cr=858*cam.z,centralLabelIntrusions=captionBoxes.filter(c=>Math.hypot(cp.x-Math.max(c.box.l-4,Math.min(c.box.r+4,cp.x)),cp.y-Math.max(c.box.t-4,Math.min(c.box.b+4,cp.y)))<cr+5).map(c=>c.text);return {centralLabelIntrusions,minGlyphGap:minGlyph,minClickGap:minHit,minNodePairPaintGap:minPair,nearest,labelLines,captions:captionBoxes.length,zoom:cam.z,spent:spent(),usable:[...learned].every(id=>usable(by.get(id))),connected:connected(learned)}}\`);
 const exhaustive=await run('wholeTree.audit()'),cached=await run('boundedAudit()');for(const field of ['minGlyphGap','minClickGap','minNodePairPaintGap'])assert(Math.abs(exhaustive[field]-cached[field])<1e-6,field);for(const field of ['labelLines','centralLabelIntrusions','captions','spent','usable','connected'])assert.deepEqual(exhaustive[field],cached[field]);await run('wholeTree.audit=boundedAudit');
 console.log('Actual-segment clearance cache agrees with exhaustive browser audit');
`;
suite=suite.replace(needle,inject+needle).replace("realMouseAndTouch:true,networkRequests:0","realMouseAndTouch:true,clearanceCacheCrossCheckedAgainstExhaustive:true,middleBandRevision:true,networkRequests:0");
const m=new Module(__dirname+'/verify-skill-tree-poe2-central-v6.cjs',module);m.filename=__dirname+'/verify-skill-tree-poe2-central-v6.cjs';m.paths=module.paths;m._compile(suite,m.filename);
