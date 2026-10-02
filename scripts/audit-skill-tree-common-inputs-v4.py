"""Every equipped, current loadout must reach shared travel without specialist inputs."""
import json,math
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
helper=ROOT/'scripts/analyze-skill-tree-build-budgets-v4.py'
scope={'__file__':str(helper)}
exec(compile(helper.read_text(encoding='utf-8').split('policies=')[0],str(helper),'exec'),scope)
source=scope['source'];by=scope['by'];graph=scope['graphs']['proposal'];adj=scope['model'](graph)
kinds={'district-ring','opening-ring','district-bridge'}
common={i for e in graph['edges'] if e['proposalKind'] in kinds for i in [e['a'],e['b']]}
specialists=[{'node':i,'stats':by[i]['stats']} for i in sorted(common) if by[i]['type']=='small' and any(k in {'magic','fire','ice','lightning','barrier','healing','weakpoint','parry','bleed','leech'} for k in by[i]['stats'])]
assert not specialists,specialists
checks=[];failures=[]
jobs={(p['start'],p['kit']):p['job'] for p in source['currentCatalog']['profiles']}
for p in source['profiles']:
    if not p.get('weaponUsable'):continue
    flags=set(p['flags']);root=next(o['root'] for o in source['origins'] if o['id']==p['start'])
    usable={i for i in by if scope['enabled'](i,flags,jobs[p['start'],p['kit']],'hp')}
    distances,prev=scope['search'](adj,[root],root,usable=usable)
    missing=sorted(i for i in common if not math.isfinite(distances[i]))
    row={'profile':p['id'],'missingCommonNodes':missing};checks.append(row)
    if missing:failures.append(row)
assert not failures,failures
exchanges=graph['structureProposal']['optionalInputBranches']
assert len(exchanges)==8
result={'status':'PASS','currentUsableLoadoutsChecked':len(checks),'commonNodes':len(common),'specialistNodesInCommonTravel':specialists,'ordinaryTravelChoice':'hp','otherStartsCannotBeTransit':True,'keyPassageUsed':False,'eightSpecialistsKeptInMatchingRegions':exchanges,'checks':checks,'runtimeApplied':False}
out=ROOT/'assets/core-ui/large-tree-preview/proposals/workshop-clear-wiring-v4/common-input-verification.json'
out.write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps({k:v for k,v in result.items() if k not in {'checks','eightSpecialistsKeptInMatchingRegions'}},ensure_ascii=False))
