"""Small authored pixel palettes and continuous panel UVs; no material noise."""
PALETTES={
 'iron':[(62,78,105),(88,112,149),(120,150,190),(157,184,218),(196,212,238)],
 'bronze':[(46,57,79),(63,82,113),(85,110,150),(112,138,176),(145,165,199)],
 'edge':[(90,115,155),(115,145,184),(143,174,211),(174,199,229),(204,220,243)],
 'dark':[(31,31,40),(44,45,58),(59,62,77),(77,80,96)],
 'cloth':[(39,24,61),(62,36,88),(92,52,119),(126,78,157)],
 'bone':[(92,86,148),(131,125,186),(167,167,221),(199,204,244),(227,235,255)],
 'ember':[(133,156,228),(185,203,251),(226,237,255),(250,251,255)]}
def pixel(name,x,y):
 p=PALETTES[name]
 if name=='ember':return p[3 if 8<x<24 and 6<y<26 else 2 if (x+y)%7>1 else 1]
 if name=='cloth':
  v=1 if x<8 or x>26 else 2
  if x in [9,10,24] and y%16<13:v=3
  if (x//4,y//4) in [(3,1),(4,5),(2,6)]:v=0
  return p[v]
 if name=='dark':return p[[1,1,2,1,0,2,1,3][(x//5+2*(y//6))%8]]
 if name=='bone':
  v=2
  if x<3 or x>28:v=4
  elif x in [3,4,14,15,16,27,28]:v=3
  if (x//3,y//4) in [(2,2),(7,3),(3,5),(6,6)]:v=1
  if (x+y//5)%19==9 and y%8<3:v=4
  return p[v]
 # A few large contiguous areas, a broken bevel, and a recessed central field.
 xx=x%16;yy=y%24;v=2
 if xx in [0,15] or yy in [0,23]:v=1
 if xx==1 or yy==1:v=3
 if xx==3 and yy in [4,5,13,14]:v=4
 if 8<=xx<=11 and 9<=yy<=17:v=1
 if xx in [12,13] and 7<=yy<=20:v=1
 if (xx//2,yy//3) in [(2,2),(4,1),(3,6)]:v=3
 if (xx//3,yy//4) in [(0,3),(3,0),(4,4)]:v=max(1,v-1)
 return p[v]

def region(center,size,axes,front_uv=None):
 if front_uv is not None:return front_uv
 # Bone-local projection keeps adjacent strips on the same pixels.
 density=16
 result=[]
 for axis in axes:
  lo=round((center[axis]-size[axis]/2)*density)+16
  hi=max(lo+1,round((center[axis]+size[axis]/2)*density)+16)
  span=min(32,hi-lo);lo=max(0,min(32-span,lo));result.append((lo,lo+span))
 return result[0][0],result[1][0],result[0][1],result[1][1]
