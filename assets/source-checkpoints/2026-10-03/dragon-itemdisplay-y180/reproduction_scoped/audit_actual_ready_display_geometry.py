from pathlib import Path
import json,zipfile,math,sys
import numpy as np
R=Path(__file__).resolve().parents[1];O=R/'outputs/actual_game_local_autoplay'
data_name=sys.argv[1] if len(sys.argv)>1 else 'ACTUAL_READY_DISPLAY_METADATA.json'
data=json.loads((O/data_name).read_text())
reference=json.loads((R/'outputs/practical_state_transition_validation/ACTUAL_PRACTICAL_STATE_TRANSITION_TRACE.json').read_text())['cases'][0]['frames'][0]
M=json.loads((R/'outputs/polish_fullpack_shared_atlas/dragon_v8_polish_shared_atlas_study.bbmodel').read_text())
idx=json.loads((R/'outputs/polish_fullpack_shared_atlas/ITEM_SOURCE_INDEX.json').read_text())
by={e['uuid']:e for e in M['elements']}
z=zipfile.ZipFile(R/'outputs/reentry_direct_first_step/projects_bundle/pack.zip')
def euler(a):
 x,y,z=np.radians(a);cx,sx=np.cos(x),np.sin(x);cy,sy=np.cos(y),np.sin(y);cz,sz=np.cos(z),np.sin(z)
 return np.array([[cz,-sz,0],[sz,cz,0],[0,0,1]])@np.array([[cy,0,sy],[0,1,0],[-sy,0,cy]])@np.array([[1,0,0],[0,cx,-sx],[0,sx,cx]])
def quat(q):
 x,y,z,w=q
 return np.array([[1-2*(y*y+z*z),2*(x*y-z*w),2*(x*z+y*w)],[2*(x*y+z*w),1-2*(x*x+z*z),2*(y*z-x*w)],[2*(x*z-y*w),2*(y*z+x*w),1-2*(x*x+y*y)]])
def cube(e):
 lo,hi=np.array(e['from']),np.array(e['to']);p=np.array([[hi[j] if i&(1<<j) else lo[j] for j in range(3)] for i in range(8)])
 return p
root=np.array([0,1,-4]);results=[]
for part in data['parts']:
 name=part['bone'];path=f'assets/worldseed/models/mobs/dragon_v8_polish_shared_atlas_study.bbmodel/normal/{name}.json'
 item=json.loads(z.read(path));display=item['display'][part['context']];W=np.array(reference['all34_local_pose_bb'][name]).reshape(4,4)
 errors=[]
 for target,uid in zip(item['elements'],idx[path]):
  src=by[uid];p=cube(src);C=np.array(src.get('origin',[0,0,0]));p=(p-C)@euler(src.get('rotation',[0,0,0])).T+C
  expected=((p@W[:3,:3].T+W[:3,3])*3/16)*[-1,1,-1]+root
  actual=cube(target);er=target.get('rotation',{});C=np.array(er.get('origin',[0,0,0]));actual=(actual-C)@euler([er.get(k,0) for k in 'xyz']).T+C
  actual=((actual/16-.5)*display['scale'])@euler(display.get('rotation',[0,0,0])).T+np.array(display['translation'])/16
  actual=((actual@quat(part['right']).T)*part['scale'])@quat(part['left']).T+part['translation']
  actual=actual@euler([part['pitch'],-part['yaw'],0]).T+root
  dist=np.linalg.norm(actual[:,None,:]-expected[None,:,:],axis=2);errors.append(float(dist.min(1).max()))
 results.append({'bone':name,'cubes':len(errors),'max_corner_gap_blocks':max(errors),'actual_item_stack':part['actual_item_stack']})
report={'same_ready_claw0_reference':True,'parts':results,'max_corner_gap_blocks':max(r['max_corner_gap_blocks'] for r in results),'display_count':len(results),'decoded_cubes':sum(r['cubes'] for r in results),'actual_metadata_matches_native_FK':max(r['max_corner_gap_blocks'] for r in results)<1e-5,
 'technical_only_not_game_visual_pass':True,'server_start_called':data['server_start_called'],'players':data['players'],'closed_entities_zero':data['closed_entities_zero'],
 'client26_primary_euler':'Cuboid EulerXYZRotation calls rotationZYX(z,y,x), matching exported facet rotation convention',
 'client26_primary_fixed_context_id':8,
 'client26_primary_UV_corner_order':'Actual FaceInfo vertices reversed relative to CPU polygon order; their corresponding getU/getV agrees, no UV swap indicated',
 'reference_READY_toe_relative_root_y_blocks':[reference['toe_world_blocks'][k][1] for k in reference['toe_world_blocks']],
 'fixture_floor_top_y':1.0,'fixture_root_y':1.0,'visual_remaining':'Observed limb gaps persist in real captured client; actual client riding/culling/metadata application remains to isolate. No speculative animation/rig/UV patch applied.'}
report['metadata_source']=data_name
report['closed_entities_zero_at_capture']=data['closed_entities_zero']
final_file=O/'ACTUAL_READY_AFTER_NOOP_MOVE_DISPLAY_METADATA.json'
if final_file.exists():report['closed_entities_zero']=json.loads(final_file.read_text())['closed_entities_zero']
report['scope_correction']='This server transform/intended-FK check excludes Vanilla ItemDisplay submitInner Y180. Old pack does not reproduce intended client geometry; see PRIMARY_ITEMDISPLAY_Y180_CAUSE_AND_COMPENSATION_AUDIT.json.'
report['old_pack_complete_client_transform_matches_intended_FK']=False
outname='ACTUAL_READY_AFTER_NOOP_MOVE_GEOMETRY_AUDIT.json' if 'NOOP_MOVE' in data_name else 'ACTUAL_READY_DISPLAY_GEOMETRY_AUDIT.json'
(O/outname).write_text(json.dumps(report,indent=2))
print(json.dumps({k:v for k,v in report.items() if k!='parts'},indent=2))
