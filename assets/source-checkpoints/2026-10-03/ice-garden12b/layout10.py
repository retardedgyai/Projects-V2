"""Ten short rigid boundary clusters and three bounded root sweeps."""
import math
import numpy as np
from field_math09 import edges
OUTLINE=sorted(edges(),key=lambda e:math.atan2((e['a'][1]+e['b'][1])/2,(e['a'][0]+e['b'][0])/2))
MARKERS=[]
for index,edge in enumerate(OUTLINE[::2]):
 rng=np.random.default_rng(570+index*31);normal=np.array(edge['normal'],float);tangent=np.array([-normal[1],normal[0]])
 midpoint=(np.array(edge['a'])+np.array(edge['b']))/2
 anchor=midpoint-normal*.24+tangent*rng.uniform(-.10,.10)
 length=float(rng.uniform(.30,.45));depth=float(rng.uniform(.16,.23));height=float(rng.uniform(.13,.20))
 MARKERS.append({'index':index,'group':index%2,'anchor':[float(anchor[0]),.023,float(anchor[1])],'normal':normal.tolist(),'tangent':tangent.tolist(),'length':length,'depth':depth,'height':height,'born':.66+index*.018})
SWEEPS=(1.50,3.50,5.50)
SECONDARY=[{'anchor':[1.30,0,-1.60],'yaw':-22,'scale':.17,'delay':0},
 {'anchor':[2.38,0,.82],'yaw':-42,'scale':.20,'delay':.32},
 {'anchor':[.72,0,2.54],'yaw':25,'scale':.19,'delay':.64}]
SECONDARY_LIFE=.36
def live_events(t):
 if not 1.50<=t<6.80:return []
 return [(sweep+i['delay'],i) for sweep in SWEEPS for i in SECONDARY if 0<=t-sweep-i['delay']<SECONDARY_LIFE]
def planned_displays(t,contact_count=0):
 if t<.5 or t>=7.56:return 0
 if t<.8:return 2+sum(t>=m['born'] for m in MARKERS)
 if t<6.8:return 19+(2 if t<1.275 else 0)+len(live_events(t))+min(4,contact_count)*3
 return 16+sum(t<6.8+.18+m['index']*.014 for m in MARKERS)
