"""Input contracts for the proposal; no combat effects or player data are applied."""
STAT_INPUTS = {
 'hp':[], 'armor':[], 'resist':[], 'move':[], 'regen':[],
 'damage':['DAMAGE'], 'physical':['DAMAGE'], 'attackSpeed':['DAMAGE'],
 'crit':['DAMAGE'], 'critMulti':['DAMAGE'], 'penetration':['DAMAGE'], 'aoe':['DAMAGE'],
 'magic':['AP'], 'mana':['MP_CONSUMER'], 'efficiency':['MP_CONSUMER'],
 'resource':['RESOURCE'], 'haste':['RESOURCE'], 'barrier':['SHIELD'], 'healing':['HEAL'],
 'parry':['GUARD'], 'bleed':['BLEED_SOURCE'], 'leech':['LIFESTEAL'],
 'fire':['FIRE_SOURCE'], 'ice':['ICE'], 'lightning':['LIGHTNING_SOURCE'], 'weakpoint':['WEAKPOINT'],
}
FLAG_KINDS = {'HEAL':'unsupported_source', 'BLEED_SOURCE':'unsupported_source',
              'CROSS_WEAPON_SKILL':'unimplemented_connection', 'WEAKPOINT':'unwired_context'}
LOST_STATS = {'noCrit':{'crit','critMulti'}, 'bloodCost':{'regen','mana','efficiency'}}

def flags_for(profile, learned, by, extra=()):
 flags=set(profile['flags'])|set(extra)
 if not profile['weaponUsable'] and 'CROSS_WEAPON_SKILL' not in flags:
  flags-=set(f for fs in STAT_INPUTS.values() for f in fs)-{'HEAL','LIFESTEAL'}
 if any(by[i].get('rule')=='noDodge' for i in learned):flags.discard('DODGE')
 return flags

def node_input(d, node, profile, learned=(), extra=(), road_choice='hp', by=None):
 by=by if by is not None else {n['id']:n for n in d['nodes']};flags=flags_for(profile,learned,by,extra)
 if node['type']=='start':return {'present':True,'usable':{},'missing':{},'gate':True}
 if node['type']=='keystone':
  if node.get('rule')=='noDodge' and 'DODGE' in profile['flags']:flags.add('DODGE')
  required=list(node.get('requirements',[]))+(['DODGE'] if node['id']=='key_10' else [])
  missing=[f for f in required if f not in flags]
  job=next(c['job'] for c in d['currentCatalog']['profiles'] if c['start']==profile['start'] and c['kit']==profile['kit'])
  gate=not node.get('referenceJobs') or job in node['referenceJobs']
  legal=profile['weaponUsable'] or 'CROSS_WEAPON_SKILL' in flags
  return {'present':not missing and legal and gate,'usable':{},'missing':missing,'gate':gate}
 stats=node.get('stats',{}) if node['type']!='road' else next(c['stats'] for c in d['travelChoices'] if c['id']==road_choice)
 lost=set().union(*(LOST_STATS.get(by[i].get('rule'),set()) for i in learned)) if learned else set()
 usable={};missing={}
 for stat,value in stats.items():
  required=STAT_INPUTS[stat];absent=[f for f in required if f not in flags]
  if stat in lost:absent.append('RULE_LOSS')
  if absent:missing[stat]=absent
  else:usable[stat]=value
 return {'present':bool(usable),'usable':usable,'missing':missing,'gate':True}

def profile_key(p):return {k:p[k] for k in ('start','kit','element','weaponMode')}
