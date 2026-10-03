"""Test independent forearm twist with the existing edge-closure solver."""
import numpy as np
import noctveil_v42_pose_tools as p
def closed_pose(q,roll=0,twist=0,outer_seed=None,membrane_rotation=None):
 values,report=p.body_pose(q,0);r1,R,A,E=p.closure['arm'](q)
 # Twist about local B->C leaves the anatomical wrist endpoint unchanged.
 R=R@p.poseenv['axis_rotation'](p.closure['C']-p.closure['B'],twist)
 arm_R=R.copy();mem_delta=np.eye(3) if membrane_rotation is None else p.skeleton.rot(membrane_rotation);R=R@mem_delta
 pts=p.closure['X'].copy();steps=[]
 for f in np.linspace(0,1,9):
  pts,error,it=p.closure['solve'](p.closure['rotation_fraction'](R,float(f)),pts);steps.append({'fraction':float(f),'error':error,'iterations':it})
  if error>1e-6:return None,{'closure_succeeded':False,'steps':steps,'forearm_twist':twist}
 if outer_seed is not None:
  seeded=outer_seed(pts.copy());pts,error,it=p.closure['solve'](R,seeded);steps.append({'alternative_outer_seed':True,'error':error,'iterations':it})
  if error>1e-6:return None,{'closure_succeeded':False,'steps':steps,'forearm_twist':twist}
 xfs={i:(p.rig.affine(R,p.closure['B']) if i in p.closure['lead'] else p.closure['face_xf'](p.closure['X'][t],pts[t])) for i,t in enumerate(p.closure['tri'])};rotations={};pivot_errors=[]
 for i in p.closure['queue']:
  if i in p.closure['lead']:continue
  local=np.linalg.inv(xfs[p.closure['parent'][i]])@xfs[i];pivot=p.closure['X'][p.closure['hinges'][i][0]];pivot_errors.append(float(np.linalg.norm(pivot-local[:3,:3]@pivot-local[:3,3])))
  rotations['right_mantle_hinge_'+str(i).zfill(2)]=p.rig.native_euler(local[:3,:3])
 core=p.skeleton.rot(q['body']);W=p.poseenv['axis_rotation'](np.asarray(q['wrist'])-A,roll);r1=core.T@W@core@r1;H=p.hand_matrix(q)
 overrides={'right_wing_shoulder':p.rig.native_euler(r1),'right_wing_forearm':p.rig.native_euler(arm_R),'right_wing_hand':p.rig.native_euler((core@r1@arm_R).T@H),**rotations}
 if membrane_rotation is not None:overrides['right_folded_membrane']=p.rig.native_euler(mem_delta)
 for n,v in overrides.items():values[(n,'rotation')]=v
 report.update(closure_succeeded=True,hinge_pivot_error=max(pivot_errors),closure_steps=steps,closure_coordinates=pts.tolist(),actual_shoulder=A.tolist(),actual_wrist=q['wrist'],actual_elbow=(A+core@r1@(p.closure['B']-p.closure['A'])).tolist(),forearm_rotation=p.rig.native_euler(arm_R),membrane_rotation=membrane_rotation,whole_limb_roll=roll,forearm_twist=twist)
 return values,report
