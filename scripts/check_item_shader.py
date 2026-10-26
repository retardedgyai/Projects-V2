"""Build the pack's item shader with this machine's own OpenGL driver, headless, before any client loads it
(2026-10-25: NVIDIA refused the greatsword swing's key arrays, "Possibly large array", and the whole pack failed).

Resolves the includes the way the game does (minecraft: from the client's own shaders, ours from scripts/shaders),
adds the defines the item pipelines compile with, and compiles and links each variant (scripts/tools/glcheck.c).
The game goes through SPIR-V first, which this does not: it catches mistakes and what the driver itself refuses, not
everything the translation may add (the arrays that failed only failed after it; the swing keys are now code).

    python3 scripts/check_item_shader.py      # exit 1 if any variant fails
"""
import re
import subprocess
import sys
import tempfile
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OURS = ROOT / 'scripts/shaders'
TOOL = ROOT / '.tools/glcheck/glcheck'
CLIENT = Path.home() / '.minecraft/versions/26.3/26.3.jar'
DEFINES = ('GLINT', 'GLINT_SPECIAL', 'OIT_ALPHA_ONLY', 'ALPHA_CUTOUT')


def vanilla(name):
    with zipfile.ZipFile(CLIENT) as jar:
        return jar.read(f'assets/minecraft/shaders/include/{name}').decode()


def resolve(src, seen=None):
    seen = set() if seen is None else seen
    def include(m):
        name = m.group(1)
        if name in seen: return ''
        seen.add(name)
        ours = OURS / name
        return resolve(ours.read_text() if ours.exists() else vanilla(name), seen)
    return re.sub(r'#(?:include|moj_import)\s*<minecraft:([^>]+)>', include, src)


def variant(src, defines):
    lines = src.split('\n')
    at = next(i for i, l in enumerate(lines) if l.startswith('#extension') or l.startswith('#version')) + 1
    while lines[at].startswith('#extension'): at += 1
    values = {'ALPHA_CUTOUT': ' 0.1'}
    out = '\n'.join(lines[:at] + [f'#define {d}{values.get(d, "")}' for d in defines] + lines[at:])
    # The game compiles through SPIR-V (Vulkan GLSL); straight to OpenGL the vertex index has its GL name.
    return out.replace('gl_VertexIndex', 'gl_VertexID')


def main():
    if not TOOL.exists():
        TOOL.parent.mkdir(parents=True, exist_ok=True)
        subprocess.run(['cc', '-O1', '-o', str(TOOL), str(ROOT / 'scripts/tools/glcheck.c'), '-lEGL', '-lGL'], check=True)
    vsh, fsh = (resolve((OURS / f'item.{k}').read_text()) for k in ('vsh', 'fsh'))
    failed = 0
    combos = [()] + [(d,) for d in DEFINES] + [('GLINT', 'OIT_ALPHA_ONLY'), ('GLINT', 'GLINT_SPECIAL', 'OIT_ALPHA_ONLY')]
    with tempfile.TemporaryDirectory() as tmp:
        for defines in combos:
            v, f = Path(tmp) / 'item.vsh', Path(tmp) / 'item.fsh'
            v.write_text(variant(vsh, defines)); f.write_text(variant(fsh, defines))
            r = subprocess.run([str(TOOL), str(v), str(f)], capture_output=True, text=True)
            ok = r.returncode == 0
            failed += not ok
            print(('OK  ' if ok else 'FAIL'), ' '.join(defines) or '(none)')
            if not ok: print(r.stdout[-2000:])
    print(r.stdout.splitlines()[0] if r.stdout else '')
    return 1 if failed else 0


if __name__ == '__main__':
    sys.exit(main())
