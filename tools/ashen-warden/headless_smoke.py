"""Starts the actual packaged Minestom server, verifies status + pack HTTP, then stops.
No game login/input: the human Creator retains the final gameplay smoke.
Usage: python headless_smoke.py <java> <server distribution> <report.json>
"""
import sys,pathlib,subprocess,urllib.request,hashlib,json,socket,struct,time
java,dist,report=sys.argv[1:];dist=pathlib.Path(dist).resolve()
p=subprocess.Popen([java,'-Dprojects.warden=true','-Dprojects.warden.smokeTicks=240','-cp',str(dist/'lib/*'),'dev.projects.server.ProjectSServerKt'],stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True,encoding='utf8',errors='replace')
lines=[];result={}
def varint(v):
    v &= 0xffffffff;b=bytearray()
    while True:
        x=v&127;v>>=7;b.append(x|(128 if v else 0))
        if not v:return bytes(b)
def packet(s,data):s.sendall(varint(len(data))+data)
def readvi(s):
    v=0
    for n in range(5):
        b=s.recv(1)
        if not b:raise EOFError()
        x=b[0];v|=(x&127)<<(7*n)
        if not x&128:return v
    raise ValueError('bad varint')
try:
    for line in p.stdout:
        lines.append(line.rstrip())
        if line.startswith('WARDEN_PACK_READY'):
            url=line.split('url=')[1].split()[0];expected=line.split('sha1=')[1].strip()
        if line.startswith('WARDEN_READY'):
            raw=urllib.request.urlopen(url,timeout=5).read();actual=hashlib.sha1(raw).hexdigest();assert actual==expected
            result['pack']={'bytes':len(raw),'sha1':actual,'http':'200 OK'}
            with socket.create_connection(('127.0.0.1',25575),5) as s:
                host=b'localhost';packet(s,b'\x00'+varint(-1)+varint(len(host))+host+struct.pack('>H',25575)+b'\x01');packet(s,b'\x00')
                readvi(s);assert readvi(s)==0;n=readvi(s);data=b''
                while len(data)<n:data+=s.recv(n-len(data))
                status=json.loads(data);assert '26.2' in status['version']['name'];result['server_status']=status['version']
    code=p.wait(timeout=30);assert code==0
    assert any('WARDEN_SERVER_SMOKE_PASS' in l for l in lines)
    result.update(status='PASS',exit_code=code,log=lines)
except BaseException:
    p.terminate();p.wait(timeout=10);raise
finally:
    pathlib.Path(report).write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf8')
print(json.dumps(result,ensure_ascii=False))
