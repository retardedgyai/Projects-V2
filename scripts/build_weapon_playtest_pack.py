"""Opt-in playable resource snapshot; never edits the installed or running server.

Keep the installed server's complete pack, including approved UI and frozen armor,
byte-for-byte except the 28 weapon item definitions. Existing equipment IDs and
pose channels then select the seven pixel weapons without touching gameplay code.
Greatswords/daggers have four original Tier appearances; other families share art.
"""
import hashlib
import json
from pathlib import Path, PurePosixPath
import zipfile
from build_pixel_armament_pack import build as build_blades, PACK as BLADES, definition
from build_specialist_armament_pack import build as build_specialists, PACK as SPECIALISTS
from build_greatsword_tiers import build as build_tiers, PACK as TIERS
from build_dagger_tiers import build as build_daggers, PACK as DAGGERS

ROOT = Path(__file__).resolve().parents[1]
SERVER_JAR = ROOT/'server-minestom/build/install/server-minestom/lib/server-minestom-0.1.0-SNAPSHOT.jar'
OUT = ROOT/'.tools/weapon-playtest-resources'
PACK = OUT/'core-ui-pack'
FAMILIES = ('greatsword','staff','bow','dagger','mace','tome','astrolabe')


def digest(data): return hashlib.sha256(data).hexdigest()


def valid_path(path):
    return (path and not path.startswith('/') and '\\' not in path and
            '..' not in path and not PurePosixPath(path).is_absolute())


def installed_pack():
    with zipfile.ZipFile(SERVER_JAR) as jar:
        index = jar.read('core-ui-pack/index.txt').decode('utf-8')
        paths = [p for p in index.splitlines() if p.strip() and not p.startswith('#')]
        if len(paths)!=len(set(paths)) or not all(valid_path(p) for p in paths):
            raise ValueError('Invalid installed resource index')
        return {p:jar.read('core-ui-pack/'+p) for p in paths}


def assemble(base):
    files = dict(base)
    additions = []
    for pack in (BLADES,SPECIALISTS,TIERS,DAGGERS):
        for path in sorted((pack/'assets/projects').rglob('*')):
            if not path.is_file(): continue
            name = path.relative_to(pack).as_posix()
            # Fail closed if a generator starts exporting anything other than
            # these isolated weapon models, item graphs and texture resources.
            if not any(name.startswith('assets/projects/'+prefix) for prefix in (
                'models/item/weapons/pixel_', 'items/weapons/pixel_', 'textures/item/weapons/pixel_')):
                raise ValueError('Unexpected review asset: '+name)
            if name in files: raise ValueError('Review asset collides with installed resource: '+name)
            files[name] = path.read_bytes(); additions.append(name)
    replacements = []
    for family in FAMILIES:
        pixel = f'assets/projects/items/weapons/pixel_{family}.json'
        if pixel not in files: raise ValueError('Missing generated weapon graph: '+family)
        parsed = json.loads(files[pixel])
        if parsed != definition(family): raise ValueError('Unexpected pose contract: '+family)
        for tier in range(1,5):
            selected_key=f'{family}_t{tier}' if family in ('greatsword','dagger') and tier>1 else family
            selected=f'assets/projects/items/weapons/pixel_{selected_key}.json'
            if selected not in files or json.loads(files[selected])!=definition(selected_key):
                raise ValueError('Missing/invalid Tier graph: '+selected_key)
            path = f'assets/projects/items/weapons/{family}_t{tier}.json'
            if path not in base: raise ValueError('Installed server lacks expected equipment ID: '+path)
            files[path] = files[selected]
            replacements.append(path)
    return files,additions,replacements


def build():
    if not SERVER_JAR.is_file(): raise ValueError('InstallDist is required before creating a review snapshot')
    # Regenerate from authored scripts every time, never package stale .tools art.
    build_blades(); build_specialists(); build_tiers(); build_daggers()
    base = installed_pack()
    files,additions,replacements = assemble(base)
    OUT.mkdir(parents=True,exist_ok=True)
    for name,data in files.items():
        target = PACK/name; target.parent.mkdir(parents=True,exist_ok=True); target.write_bytes(data)
    index = '\n'.join(sorted(files))+'\n'
    (PACK/'index.txt').write_text(index,encoding='utf-8')
    with zipfile.ZipFile(OUT/'projects-weapon-playtest.zip','w',zipfile.ZIP_DEFLATED) as archive:
        for name,data in sorted(files.items()):
            entry = zipfile.ZipInfo(name,date_time=(1980,1,1,0,0,0))
            entry.compress_type = zipfile.ZIP_DEFLATED
            archive.writestr(entry,data)
    report = {'status':'opt-in weapon art playtest snapshot; not automatically active',
        'runtime_applied':False,'armor_changes':0,'tier_art_distinct':False,
        'distinct_tier_families':['greatsword','dagger'],
        'server_jar':SERVER_JAR.relative_to(ROOT).as_posix(),
        'server_jar_sha256':digest(SERVER_JAR.read_bytes()),
        'replaced_item_definitions':replacements,'added_files':additions,
        'unchanged_installed_files':len(base)-len(replacements),
        'indexed_files':len(files),'files_sha256':{p:digest(data) for p,data in sorted(files.items())}}
    (OUT/'report.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8')
    print(f'Playtest resources: {len(files)} files; 28 weapon definitions replaced; armor/UI unchanged; NOT running.')


if __name__=='__main__': build()
