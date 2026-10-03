"""One moving first-step transition into unchanged approved walk26 phase0.
No new walk, rig, geometry, texture, atlas or action. Root travel belongs to owner.
Deliberate support/step poses; finite lightweight checks, not visual acceptance.
"""
from pathlib import Path
import json, hashlib, math, uuid, copy
import numpy as np
import author_native_plain_grounded_walk as p
import audit_native_plain_delivery as common

R=Path(__file__).resolve().parents[1]
O=R/'outputs/reentry_direct_first_step';O.mkdir(exist_ok=True)
NAME='transition_ready_moving_first_step_to_approved_walk26_phase000_native_rigid'
L=2.10;ACCEL=1.35;SPEED=8.;D=11.4
W=json.loads((R/'outputs/grounded_stride_rebuild/NEW_GROUNDED_STRIDE_GAME_CLIP.json').read_text())
A=json.loads((R/'outputs/foreclaw_hook_rake/NEW_HOOK_RAKE_FORECLAW_GAME_CLIP.json').read_text())
WT=common.tables(W);AT=common.tables(A);E=common.fk(AT,0.);F=common.fk(WT,0.)
EL=p.local(E);FL=p.local(F)
TIP={n:np.array(p.b.a.REF['feet'][n]['toe']) for n in p.OFF}
AIR={'hind_l':(.60,.96),'front_l':(.98,1.48),'hind_r':(1.78,L)}
BIAS=[(0.,0.,0.),(.40,3.25,-1.6),(.60,3.25,-1.6),(.96,3.25,-1.4),
      (1.20,0.,-1.),(1.48,0.,-.8),(1.78,-2.3,-.3),(1.94,-1.4,-.16),(L,0.,0.)]
def sha(path):
    with path.open('rb') as f:return hashlib.file_digest(f,'sha256').hexdigest()
PROTECTED=json.loads((R/'outputs/locomotion_reentry_isolation/PROTECTED_SOURCES.json').read_text())
assert all(sha(R/path)==v for path,v in PROTECTED.items())
def travel(t):
    return SPEED*(t*t/(2*ACCEL) if t<ACCEL else t-ACCEL/2)
assert abs(travel(L)-D)<1e-12
def uid(s):return str(uuid.uuid5(uuid.NAMESPACE_URL,'projects:reentry-direct-first-step/'+s))
def h(t,t0,t1,v0,v1,d0=0.,d1=0.):
    if t<=t0:return np.array(v0)
    if t>=t1:return np.array(v1)
    u=(t-t0)/(t1-t0);dt=t1-t0
    return (2*u**3-3*u*u+1)*np.array(v0)+(u**3-2*u*u+u)*dt*np.array(d0)+(-2*u**3+3*u*u)*np.array(v1)+(u**3-u*u)*dt*np.array(d1)
EPS=.00001
F1=common.fk(WT,EPS);FL1=p.local(F1)
FD={n:{ch:(np.array(FL1[n][ch])-np.array(FL[n][ch]))/EPS for ch in ['rotation','position']} for n in FL}
PA={n:p.b.a.apply(E[n+'_paw'],TIP[n]) for n in TIP}
PB={n:p.b.a.apply(F[n+'_paw'],TIP[n])-[0,0,D] for n in TIP}
VD={n:(p.b.a.apply(F1[n+'_paw'],TIP[n])-p.b.a.apply(F[n+'_paw'],TIP[n]))/EPS-[0,0,SPEED] for n in TIP}
EA={n:p.w.euler(E[n+'_paw']) for n in TIP}
FA={n:p.w.euler(F[n+'_paw']) for n in TIP}
AD={n:(p.w.euler(F1[n+'_paw'])-FA[n])/EPS for n in TIP}
assert np.linalg.norm(PB['front_r']-PA['front_r'])<1e-10
def foot(n,t):
    if n=='front_r':return PA[n].copy(),EA[n].copy(),True
    a,b=AIR[n]
    if t<=a:
        angles=EA[n].copy()
        if n=='hind_r':angles[0]=float(h(t,1.6,a,EA[n][0],-14.))
        return PA[n].copy(),angles,True
    if n!='hind_r' and t>=b:return PB[n].copy(),FA[n].copy(),True
    if n=='hind_r':
        # Join an already recovering hind foot: keep terminal velocity, no
        # artificial landing and immediate relift at the clip switch.
        folded=PA[n]+[0,2.67,-.10];mid=2.0
        if t<mid:
            at=h(t,a,mid,PA[n],folded,np.zeros(3),[0,4,-8])
            angles=h(t,a,mid,[-14,0,0],[-30,0,0])
        else:
            at=h(t,mid,b,folded,PB[n],[0,4,-8],VD[n])
            angles=h(t,mid,b,[-30,0,0],FA[n],np.zeros(3),AD[n])
    else:
        u=(t-a)/(b-a);at=h(t,a,b,PA[n],PB[n])
        at[1]+=3.4*math.sin(math.pi*u)**2
        angles=h(t,a,b,EA[n],FA[n]);angles[0]-=22*math.sin(math.pi*u)**2
    return at,angles,False
def pose(t):
    locals={}
    for n in p.b.G:
        locals[n]={ch:h(t,0,L,EL[n][ch],FL[n][ch],np.zeros(3),FD[n][ch]) for ch in ['rotation','position']}
    dx,dz=[p.w.hermite(t,[(r[0],r[i],0.) for r in BIAS]) for i in [1,2]]
    locals['body']['position']+=np.array([dx,0,dz])
    ms={}
    for n,g in p.b.G.items():
        origin=np.array(g['origin']);m=p.w.attached(p.b.a.rot(locals[n]['rotation']),origin,origin+locals[n]['position'])
        par=p.b.PARENTS[n];ms[n]=m if par is None else ms[par]@m
    carrier=ms['body'].copy()
    for n in p.OFF:
        at,angles,_=foot(n,t);at+=np.array([0,0,travel(t)])
        paw=p.w.attached(p.b.a.rot(angles),TIP[n],at)
        H,K,_=p.w.RIG[n];atH=p.b.a.apply(carrier,H)
        pole=carrier[:3,:3]@np.array([0,0,1 if n.startswith('front') else -1.])
        refs={n:p.w.attached(np.eye(3),H,atH),n+'_shin':p.w.attached(np.eye(3),K,atH+pole)}
        ms[n+'_support']=carrier.copy();ms.update(p.w.leg(n,carrier,paw,refs))
    return ms
def hull(points):
    pts=sorted(set(tuple(x) for x in points))
    def cross(a,b,c):
        u=np.array(b)-a;v=np.array(c)-a;return u[0]*v[1]-u[1]*v[0]
    lo=[];hi=[]
    for x in pts:
        while len(lo)>=2 and cross(lo[-2],lo[-1],x)<=0:lo.pop()
        lo.append(x)
    for x in reversed(pts):
        while len(hi)>=2 and cross(hi[-2],hi[-1],x)<=0:hi.pop()
        hi.append(x)
    return np.array(lo[:-1]+hi[:-1])
def margin(poly,point):
    return min(float(((b-a)[0]*(point-a)[1]-(b-a)[1]*(point-a)[0])/np.linalg.norm(b-a)) for a,b in zip(poly,np.roll(poly,-1,axis=0)))
def build():
    times=sorted(set(round(float(t),10) for t in np.arange(0,L+.001,.01))|{t for r in BIAS for t in [r[0]]}|{v for ab in AIR.values() for v in ab}|{1.6,2.,L,L-.00001})
    rows={n:{ch:[] for ch in ['rotation','position']} for n in p.b.G}
    extensions=[];proxies=[];poses=[];lowest=[]
    for t in times:
        ms=pose(t);loc=p.local(ms);contact=[]
        for n in rows:
            for ch in rows[n]:rows[n][ch].append(loc[n][ch])
        for n in p.OFF:
            H,K,ankle=p.w.RIG[n]
            extensions.append(float(np.linalg.norm(p.b.a.apply(ms[n],H)-p.b.a.apply(ms[n+'_paw'],ankle))/(np.linalg.norm(K-H)+np.linalg.norm(ankle-K))))
            toe=p.b.a.apply(ms[n+'_paw'],TIP[n])-[0,0,travel(t)]
            at,_,stance=foot(n,t)
            if stance:contact.append(toe[[0,2]])
            pts=p.b.a.PAWS[n]@ms[n+'_paw'][:3,:3].T+ms[n+'_paw'][:3,3]
            lowest.append(float(pts[:,1].min()))
        chest=p.b.a.apply(ms['body'],p.C)-[0,0,travel(t)]
        pelvis=p.b.a.apply(ms['body'],p.P)-[0,0,travel(t)]
        proxy=(chest+pelvis)/2
        m=margin(hull(contact),proxy[[0,2]])
        proxies.append({'time':t,'supports':len(contact),'torso_midpoint_proxy_margin_BB':m})
        if any(abs(t-k)<1e-9 for k in [0,.4,.6,.8,.96,1.2,1.58,1.75,L]):
            poses.append({'time':t,'actor_travel_BB':travel(t),'chest_world_BB':chest.tolist(),'pelvis_world_BB':pelvis.tolist(),
                          'feet':{n:{'toe_world_BB':foot(n,t)[0].tolist(),'stance':foot(n,t)[2]} for n in p.OFF},'torso_support_proxy_margin_BB':m})
    clip={'uuid':uid('clip'),'name':NAME,'loop':'once','override':False,'length':L,'snapping':100,'animators':{}}
    for n,channels in rows.items():
        channels['rotation']=np.degrees(np.unwrap(np.radians(channels['rotation']),axis=0));keys=[]
        for ch,arr in channels.items():
            # A tiny serialized boundary interval adopts the approved local
            # channel tangent; body/foot source clocks retain C1 handoff.
            arr[-2]=np.array(arr[-1])-FD[n][ch]*(times[-1]-times[-2])
            for i,(t,v) in enumerate(zip(times,arr)):
                keys.append({'uuid':uid(f'{n}/{ch}/{i}'),'channel':ch,'time':t,'interpolation':'linear','color':-1,'data_points':dict(zip('xyz',[p.w.number(x) for x in v]))})
        for k in keys:k['data_points']=[k['data_points']]
        clip['animators'][p.b.G[n]['uuid']]={'name':n,'type':'bone','keyframes':keys}
    table=common.tables(clip);contacts=[];interpolated_floor=[]
    for t in np.arange(0,L+.0001,.005):
        ms=common.fk(table,float(t));d=travel(t)
        for n in p.OFF:
            want,_,stance=foot(n,float(t));toe=p.b.a.apply(ms[n+'_paw'],TIP[n])-[0,0,d]
            if stance:contacts.append(float(np.linalg.norm(toe-want)))
            pts=p.b.a.PAWS[n]@ms[n+'_paw'][:3,:3].T+ms[n+'_paw'][:3,3]
            interpolated_floor.append(float(pts[:,1].min()))
    errors=[max(float(np.max(abs(pose(t)[n]-want[n]))) for n in want) for t,want in [(0,E),(L,F)]]
    # Endpoint velocity probe compares the new serialized keys to untouched walk.
    veps=.000001
    before=common.fk(table,L-veps);after=common.fk(WT,veps)
    vrows=[]
    for n in p.OFF:
        end=foot(n,L)[0]
        vb=(end-(p.b.a.apply(before[n+'_paw'],TIP[n])-[0,0,travel(L-veps)]))/veps
        va=(p.b.a.apply(after[n+'_paw'],TIP[n])-[0,0,D+SPEED*veps]-end)/veps
        vrows.append({'leg':n,'world_toe_velocity_jump_BBsec':float(np.linalg.norm(va-vb)),'before':vb.tolist(),'after':va.tolist()})
    report={'transition_only_approved_walk26_claw33_unchanged':True,'new_clip':NAME,'duration_source_seconds':L,
            'end_walk26_phase':0.,'source_actor_travel_BB':D,'source_acceleration_seconds':ACCEL,'actor_travel_owner_only_not_baked_in_game_clip':True,
            'moving_start_duration_world_seconds_by_speed_scale3':{str(s):L*1.5/s for s in [.75,1.5,2.25]},
            'authored_air_intervals_not_measured_from_reference':AIR,'authored_body_support_translation_BB':BIAS,
            'reference_limit':'Public tiger footage informed planted-body-passing and folding recovery; start timing is authored, not MH measurement.',
            'start_ready_end_walk26_all34_pose_errors_BB':errors,'max_leg_extension_ratio':max(extensions),
            'serialized_support_toe_error_BB':max(contacts),'minimum_paw_vertex_Y_BB':min(interpolated_floor),
            'min_support_count':min(x['supports'] for x in proxies),'torso_midpoint_proxy_min_margin_BB':min(x['torso_midpoint_proxy_margin_BB'] for x in proxies),
            'proxy_is_not_mass_COM_or_visual_pass':True,'world_toe_endpoint_velocity':vrows,'main_poses':poses,
            'native_GUI_full_visual_and_real_client_not_tested':True,'visual_natural_reentry_pass':False}
    (O/'FIRST_STEP_BUILD.json').write_text(json.dumps(report,indent=2),encoding='utf8')
    assert max(errors)<1e-7 and max(extensions)<.995 and max(contacts)<.01,(errors,max(extensions),max(contacts))
    assert min(x['supports'] for x in proxies)>=3
    # Keep any diagnostic proxy shortfall visible; it is not a hidden PASS gate.
    (O/'NEW_MOVING_FIRST_STEP_TRANSITION_CLIP.json').write_text(json.dumps(clip,separators=(',',':')),encoding='utf8')
    M=json.loads((R/'outputs/foreclaw_hook_rake/dragon_v8_FORECLAW_HOOK_RAKE_NATIVE_REVIEW.bbmodel').read_text())
    M['animations']=[clip];M['name']=M['model_identifier']='dragon_v8_MOVING_FIRST_STEP_TRANSITION_ONLY'
    (O/'dragon_v8_MOVING_FIRST_STEP_TRANSITION_ONLY.bbmodel').write_text(json.dumps(M,separators=(',',':')),encoding='utf8')
    old=json.loads((R/'outputs/locomotion_reentry_isolation/NEW_READY_TO_WALK26_TRANSITION_CLIP.json').read_text())
    view=copy.deepcopy(M);view['animations']=[old,clip,W];view['name']=view['model_identifier']='dragon_v8_OLD_STATIC_NEW_MOVING_REENTRY_REVIEW'
    (O/'dragon_v8_REENTRY_OLD_NEW_WALK26_3CLIPS.bbmodel').write_text(json.dumps(view,separators=(',',':')),encoding='utf8')
    (O/'PROTECTED_SOURCES.json').write_text(json.dumps(PROTECTED,indent=2),encoding='utf8')
    export=R/'work/reentry_direct_first_step_export_inputs';export.mkdir(exist_ok=True)
    (export/'00.json').write_text(json.dumps({'meta':M['meta'],'animations':[clip]},separators=(',',':')),encoding='utf8')
    assert all(sha(R/path)==v for path,v in PROTECTED.items())
    print(json.dumps({k:v for k,v in report.items() if k!='main_poses'},indent=2),flush=True)
if __name__=='__main__':build()
