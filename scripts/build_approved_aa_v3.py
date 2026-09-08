"""Mirror only the approved dash's native geometry for AA2; all inks and lifetimes are identical."""
import json
from build_approved_dash_v3 import PACK, build as approved_build


def build(assets, write):
    def mirror(path, value):
        if 'impact_' in path.name:
            return  # Hit-local flash is the unchanged approved resource.
        target = path.parent.parent / 'approved_aa_reverse_v3' / path.name
        if 'elements' in value:
            for element in value['elements']:
                lo, hi = element['from'][0], element['to'][0]
                element['from'][0], element['to'][0] = 16-hi, 16-lo
                if 'rotation' in element:
                    element['rotation']['origin'][0] = 16-element['rotation']['origin'][0]
        else:
            value['model']['model'] = value['model']['model'].replace(
                'approved_dash_v3/', 'approved_aa_reverse_v3/')
        write(target, value)
    approved_build(assets, mirror)


if __name__ == '__main__':
    def write(path, value):
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps(value, separators=(',', ':'))+'\n', encoding='utf-8')
    build(PACK/'assets/projects', write)
    (PACK/'index.txt').write_text('\n'.join(sorted(p.relative_to(PACK).as_posix()
        for p in PACK.rglob('*') if p.is_file() and p.name != 'index.txt'))+'\n', encoding='utf-8')
