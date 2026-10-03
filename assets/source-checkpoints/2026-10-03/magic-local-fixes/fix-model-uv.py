from pathlib import Path
import json,copy,hashlib
root=Path(__file__).parent
source=Path(r'C:\Users\xgaiz\Documents\Codex\2026-09-30\task\world-infusion-altar\server-minestom\src\main\resources\core-ui-pack')
report=[]
for model in ['infusion-v4/support','infusion-v7/core_ritual']:
    rel=Path('assets/projects/models')/(model+'.json')
    before=json.loads((source/rel).read_text(encoding='utf-8-sig'))
    after=copy.deepcopy(before)
    changes=[]
    for i,element in enumerate(after['elements']):
        for direction,face in element['faces'].items():
            uv=face.get('uv')
            if not uv: continue
            previous=uv.copy()
            for a,b in [(0,2),(1,3)]:
                lo,hi=sorted([uv[a],uv[b]])
                span=hi-lo
                if lo>=0 and hi<=16: continue
                if span>16:
                    uv[a]=(uv[a]-lo)*16/span
                    uv[b]=(uv[b]-lo)*16/span
                else:
                    offset=-lo if lo<0 else 16-hi
                    uv[a]+=offset;uv[b]+=offset
            assert all(0<=v<=16 for v in uv)
            if previous!=uv: changes.append({'element':i,'face':direction,'before':previous,'after':uv})
    def without_uv(data):
        clone=copy.deepcopy(data)
        for element in clone['elements']:
            for face in element['faces'].values(): face.pop('uv',None)
        return clone
    assert without_uv(before)==without_uv(after), 'Geometry, rotation, material, face winding must stay unchanged'
    dest=root/'classes/core-ui-pack'/rel
    dest.parent.mkdir(parents=True,exist_ok=True)
    dest.write_text(json.dumps(after,indent=2)+'\n',encoding='utf-8')
    report.append({'model':model,'faces_corrected':len(changes),'geometry_rotation_materials_unchanged':True,'original_sha256':hashlib.sha256((source/rel).read_bytes()).hexdigest(),'changes':changes})
(root/'uv-repair-audit.json').write_text(json.dumps(report,indent=2),encoding='utf-8')
print(json.dumps([{k:v for k,v in r.items() if k!='changes'} for r in report],indent=2))
