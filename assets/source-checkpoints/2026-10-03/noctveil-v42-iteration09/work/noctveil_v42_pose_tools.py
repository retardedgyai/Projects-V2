"""Pure protected-shape pose helpers. Existing solver and rasterizer only."""
from pathlib import Path
import ast,copy,json,math,uuid
import numpy as np
import build_noctveil_v35_attack as rig
import preview_bbmodel as skeleton

ROOT=Path(__file__).resolve().parents[1]
def prefix(path,name):
    nodes=[]
    for n in ast.parse(path.read_text(encoding='utf8')).body:
        if isinstance(n,ast.Assign) and any(isinstance(t,ast.Name) and t.id==name for t in n.targets):break
        nodes.append(n)
    env={'__file__':str(path),'__name__':'definitions_only'}
    exec(compile(ast.fix_missing_locations(ast.Module(body=nodes,type_ignores=[])),str(path),'exec'),env)
    return env
closure=prefix(ROOT/'work/probe_v41_membrane_constraints.py','design');closure['CASE']='source_z'
review=prefix(ROOT/'work/review_v41_leading_surfaces.py','rows')
tree=ast.parse((ROOT/'work/build_noctveil_v40_wing_pose_study.py').read_text(encoding='utf8'))
defs=[n for n in tree.body if isinstance(n,ast.FunctionDef) and n.name in ['solve_chain','axis_rotation','build_pose']]
poseenv={'np':np,'math':math,'rig':rig,'skeleton':skeleton}
exec(compile(ast.fix_missing_locations(ast.Module(body=defs,type_ignores=[])),'existing_pose_ik_functions','exec'),poseenv)
target=review['target'];elements={e['name']:e for e in rig.source['elements']}
def descendants(g):
    out=[]
    for c in g['children']:
        out.extend(descendants(c) if isinstance(c,dict) else [rig.elements[c]['name']])
    return out
wing_names=descendants(rig.groups['right_wing_shoulder']);hand_names=descendants(rig.groups['right_wing_hand'])
uid=lambda s:str(uuid.uuid5(uuid.NAMESPACE_URL,'projects/noctveil/v42/'+s))
hook_axis=np.mean([elements['right_v2_folded_hook_0']['vertices']['v'+str(i)] for i in range(8,12)],0)-np.mean([elements['right_v2_folded_hook_0']['vertices']['v'+str(i)] for i in range(4)],0)
def hand_matrix(q):
    direction=np.asarray(q['claw_direction'],float)
    if q.get('hand_orientation')!='dorsal_up':return rig.rotation_between(hook_axis,direction)
    def frame(a):
        a=np.asarray(a,float);a/=np.linalg.norm(a);up=np.array([0.,1.,0.]);up-=a*(up@a);up/=np.linalg.norm(up)
        return np.stack((a,up,np.cross(a,up)),axis=1)
    return frame(direction)@frame(hook_axis).T

def frame_clip(values,slot=0):
    frames={}
    for (name,channel),v in values.items():
        frames.setdefault(name,[]).append({'uuid':uid(f'{slot}/{name}/{channel}'),'channel':channel,'time':slot,'interpolation':'linear','data_points':[dict(zip('xyz',map(rig.literal,v)))]})
    return {'uuid':uid('single-frame'),'name':'v42_static_probe','loop':'hold','length':max(1,slot),'animators':{rig.groups[n]['uuid']:{'name':n,'type':'bone','rotation_global':False,'keyframes':k} for n,k in frames.items()}}

def world(values,slot=0):
    clip=frame_clip(values,slot);m=skeleton.transforms(rig.source['outliner'][0],clip,slot,np.eye(4),{})
    return {e['name']:(m[e['uuid']][:3,:3]@np.asarray(list(e['vertices'].values())).T).T+m[e['uuid']][:3,3] for e in rig.source['elements']}

def body_pose(q,slot):
    """Solve actual foot goals before IK; a planted hind paw may yaw at its sole.

    No reach clamp, vertex edit, or shifting the opponent is used. A pivot is a
    fixed point of the existing paw geometry and world-Y yaw keeps its height.
    """
    core=skeleton.rot(q['body']);shift=np.asarray(q['shift'],float)
    M=rig.affine(core,np.asarray(rig.groups['thorax']['origin']),shift)
    values={('root','position'):[-shift[0],0,shift[2]],('thorax','position'):[0,shift[1],0],('thorax','rotation'):[-q['body'][0],-q['body'][1],q['body'][2]]}
    support=[];feet={};contact=[];step=q.get('foot_step')
    for leg,(a,b,c,l1,l2,pole) in rig.LEGS.items():
        foot_R=np.eye(3);goal=c.copy();pivot=None
        yaw=q.get('hind_foot_yaw',{}).get(leg,0)
        if leg.endswith('hind') and yaw:
            pivot=rig.SOLES[leg].mean(0)
            foot_R=skeleton.rot([0,yaw,0])
            goal=pivot+foot_R@(c-pivot)
        if step and leg==step['leg']:
            goal=c+np.asarray(step['delta']);foot_R=skeleton.rot([step.get('toe_pitch',0),0,0])
        goal[1]+=q.get('stance_clearance',0)
        r1,r2,aw,k,distance=poseenv['solve_chain'](a,b,c,goal,core,M,core@pole)
        r3=(core@r1@r2).T@foot_R
        for part,R in [('upper',r1),('lower',r2),('foot',r3)]:values[(leg+'_'+part,'rotation')]=rig.native_euler(R)
        feet[leg]={'ankle':goal.tolist(),'knee':k.tolist(),'reach_reserve':float(l1+l2-distance),'planted_yaw_pivot':None if pivot is None else pivot.tolist(),'absolute_foot_rotation':rig.native_euler(foot_R)}
        if not(step and leg==step['leg'] and step['delta'][1]>1e-7):
            sole=(foot_R@(rig.SOLES[leg]-c).T).T+goal;support.extend(sole[:,[0,2]]);contact.append(leg)
    values[('neck','rotation')]=[-2,q['body'][1]*.35,0]
    values[('head','rotation')]=[1,0,0]
    for i in range(4):values[(f'tail_{i}','rotation')]=[0,q['body'][1]*.12*(1+.15*i),0]
    chest=(M@np.array([0,19.82804214957833,2.1908450656139116,1]))[:3]
    return values,{'slot':slot,**q,'feet':feet,'chest_load_proxy':chest.tolist(),'support_proxy_margin':rig.margin(chest[[0,2]],rig.hull(support)),'supporting_feet':contact,'stepping_foot':step}

def closed_pose(q,roll=0,slot=0):
    values,report=body_pose(q,slot)
    r1,R,A,E=closure['arm'](q);pts=closure['X'].copy();steps=[]
    for f in np.linspace(0,1,9):
        pts,error,it=closure['solve'](closure['rotation_fraction'](R,float(f)),pts);steps.append({'fraction':float(f),'error':error,'iterations':it})
        if error>1e-6:return None,{'closure_succeeded':False,'steps':steps,'q':q}
    xfs={i:(rig.affine(R,closure['B']) if i in closure['lead'] else closure['face_xf'](closure['X'][t],pts[t])) for i,t in enumerate(closure['tri'])}
    rotations={};pivot_errors=[]
    for i in closure['queue']:
        if i in closure['lead']:continue
        local=np.linalg.inv(xfs[closure['parent'][i]])@xfs[i];p=closure['X'][closure['hinges'][i][0]]
        pivot_errors.append(float(np.linalg.norm(p-local[:3,:3]@p-local[:3,3])))
        rotations['right_mantle_hinge_'+str(i).zfill(2)]=rig.native_euler(local[:3,:3])
    core=skeleton.rot(q['body']);W=poseenv['axis_rotation'](np.asarray(q['wrist'])-A,roll);r1=core.T@W@core@r1
    H=hand_matrix(q)
    overrides={'right_wing_shoulder':rig.native_euler(r1),'right_wing_forearm':rig.native_euler(R),'right_wing_hand':rig.native_euler((core@r1@R).T@H),**rotations}
    for n,v in overrides.items():values[(n,'rotation')]=v
    report.update(closure_succeeded=True,hinge_pivot_error=max(pivot_errors),closure_steps=steps,actual_shoulder=A.tolist(),actual_wrist=q['wrist'],actual_elbow=(A+core@r1@(closure['B']-closure['A'])).tolist(),forearm_rotation=rig.native_euler(R),whole_limb_roll=roll)
    return values,report

def audit(w):
    row=review['audit'](w)
    row['lowest_wing_mesh']=min(wing_names,key=lambda n:w[n][:,1].min())
    row['lowest_wing_vertex']=w[row['lowest_wing_mesh']][np.argmin(w[row['lowest_wing_mesh']][:,1])].tolist()
    row['minimum_wing_y']=float(min(w[n][:,1].min() for n in wing_names))
    row['palm_z_front_clearance']=float(w['right_v2_folded_knuckle'][:,2].min()+28)
    row['claw_tip_centres']={n:w[n][8:12].mean(0).tolist() for n in hand_names if '_hook_' in n}
    row['palm_centre']=w['right_v2_folded_knuckle'].mean(0).tolist()
    row['hook_overlap']=any(n.startswith('right_v2_folded_hook_') for n in row['proxy_triangle_overlap'])
    row['non_hook_overlap']=[n for n in row['proxy_triangle_overlap'] if not n.startswith('right_v2_folded_hook_')]
    row['palm_or_radius_overlap']=any(n in ['right_v2_folded_knuckle','right_v2_folded_radius'] for n in row['proxy_triangle_overlap'])
    return row

def roll_world(w,A,C,deg):
    W=poseenv['axis_rotation'](np.asarray(C)-np.asarray(A),deg);A=np.asarray(A)
    return {n:((W@(v-A).T).T+A if n in wing_names and n not in hand_names else v) for n,v in w.items()}

def rotate_values(values,q,deg):
    v=copy.deepcopy(values);core=skeleton.rot(q['body']);A=(rig.affine(core,np.array(rig.groups['thorax']['origin']),q['shift'])@np.r_[closure['A'],1])[:3]
    W=poseenv['axis_rotation'](np.asarray(q['wrist'])-A,deg)
    physical=lambda n:skeleton.rot([-v[(n,'rotation')][0],-v[(n,'rotation')][1],v[(n,'rotation')][2]])
    r1=core.T@W@core@physical('right_wing_shoulder');r2=physical('right_wing_forearm');H=hand_matrix(q)
    v[('right_wing_shoulder','rotation')]=rig.native_euler(r1);v[('right_wing_hand','rotation')]=rig.native_euler((core@r1@r2).T@H)
    return v
