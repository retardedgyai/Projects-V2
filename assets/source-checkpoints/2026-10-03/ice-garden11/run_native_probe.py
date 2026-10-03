"""Read installed 26.2 parser; no downloads or game launch. Heap capped at 256MB."""
from pathlib import Path
import json, os, subprocess, sys
ROOT=Path(__file__).resolve().parent
sys.stdout.reconfigure(encoding='utf-8')
MC=Path(os.environ['APPDATA'])/'.minecraft'
JDK=Path(r'C:\Program Files\Eclipse Adoptium\jdk-25.0.4.101-hotspot\bin')
PRIVATE=ROOT/'reference-private';CLASSES=PRIVATE/'probe-classes';CLASSES.mkdir(parents=True,exist_ok=True)
version=json.loads((MC/'versions/26.2/26.2.json').read_text(encoding='utf-8'))
jars=[MC/'versions/26.2/26.2.jar'];missing=[]
for lib in version['libraries']:
 artifact=lib.get('downloads',{}).get('artifact')
 if artifact:
  p=MC/'libraries'/artifact['path']
  if p.is_file():jars.append(p)
  else:missing.append(str(p))
classpath=';'.join(p.as_posix() for p in jars)
args=PRIVATE/'javac.args'
args.write_text('-classpath\n"'+classpath+'"\n-d\n"'+CLASSES.as_posix()+'"\n"'+(ROOT/'NativeModelProbe.java').as_posix()+'"\n')
subprocess.run([str(JDK/'javac.exe'),'-J-Xmx256m','@'+str(args)],check=True)
probe='--probe' in sys.argv[1:]
names=[name for name in sys.argv[1:] if name!='--probe']
source_dir=ROOT/('probe-source' if probe else 'native-study')
sources=[source_dir/(name+'.model.json') for name in names] if names else sorted(source_dir.glob('*.model.json'))
for source in sources:
 out=source.with_name(source.name.replace('.model.json','.native-faces.json'))
 args=PRIVATE/('java-'+source.stem+'.args')
 args.write_text('-Xmx256m\n-classpath\n"'+CLASSES.as_posix()+';'+classpath+'"\ndev.projects.artprobe.NativeModelProbe\n"'+source.as_posix()+'"\n"'+out.as_posix()+'"\n')
 subprocess.run([str(JDK/'java.exe'),'@'+str(args)],check=True)
print(json.dumps({'existing_library_jars':len(jars)-1,'unused_missing_libraries':len(missing),'downloaded':0,'game_started':False,'max_heap_mb':256}))
