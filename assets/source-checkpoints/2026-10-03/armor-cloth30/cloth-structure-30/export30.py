grouped=defaultdict(list)
for i,f in enumerate(M['faces']):grouped[f['piece']].append((i,f))
elements=[]
for piece,faces in grouped.items():
 ids=sorted({i for _,f in faces for i in f['vertices']});uid=str(uuid.uuid5(uuid.NAMESPACE_URL,'ProjectS30/'+piece))
 elements.append({'name':piece,'type':'mesh','uuid':uid,'origin':[0,0,0],'rotation':[0,0,0],
  'vertices':{'v'+str(i):M['vertices'][i] for i in ids},'faces':{'f'+str(i):{'vertices':['v'+str(k) for k in f['vertices']],'uv':{'v'+str(k):uv for k,uv in zip(f['vertices'],f['uv'])},'texture':f['texture_index']} for i,f in faces},'visibility':True,'export':True,'autouv':0,'locked':False})
textures=[]
for i,name in enumerate(['native.png','new-cloth-boots.png','structure-paint.png']):
 textures.append({'path':'','name':name,'folder':'','namespace':'','id':str(i),'uuid':str(uuid.uuid5(uuid.NAMESPACE_URL,'ProjectS30/texture'+str(i))), 'width':512,'height':512,'uv_width':512,'uv_height':512,'mode':'bitmap','source':'data:image/png;base64,'+base64.b64encode((D/name).read_bytes()).decode(),'render_mode':'default','render_sides':'front','visible':True})
write(D/'model.bbmodel',{'meta':{'format_version':'4.10','model_format':'free','box_uv':False},'name':M['name'],'model_identifier':'model','visible_box':[4,3,0],'resolution':{'width':512,'height':512},'elements':elements,'outliner':[e['uuid'] for e in elements],'textures':textures,'animations':[]})
obj=['mtllib material.mtl','s off']+['v '+' '.join(f'{x:.8f}' for x in p) for p in M['vertices']]
last=None
for i,f in enumerate(M['faces']):
 if f['texture_index']!=last:last=f['texture_index'];obj.append('usemtl material'+str(last))
 obj+=['vt '+f'{uv[0]/512:.8f} {1-uv[1]/512:.8f}' for uv in f['uv']];obj.append('f '+' '.join(f'{v+1}/{3*i+j+1}' for j,v in enumerate(f['vertices'])))
(D/'model.obj').write_text('\n'.join(obj)+'\n')
(D/'material.mtl').write_text('\n'.join('newmtl material'+str(i)+'\nKd 1 1 1\nKa 0 0 0\nKs 0 0 0\nillum 1\nmap_Kd '+n+'\n' for i,n in enumerate(['native.png','new-cloth-boots.png','structure-paint.png'])))
