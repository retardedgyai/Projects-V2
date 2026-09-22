"""Publish the existing approved T1 artwork into the normal pack; no new art.

The canonical generator and source digest are shared with the former opt-in
preview. Only its 32 assets, T1 item routing and index are written.
"""
from build_material_playtest_pack import ROOT, TARGET, MODEL_NAME, candidate_resources

PACK = ROOT / 'server-minestom/src/main/resources/core-ui-pack'


def promote():
    resources = candidate_resources()
    resources[TARGET] = resources[f'assets/projects/items/weapons/{MODEL_NAME}.json']
    for relative, data in resources.items():
        target = PACK / relative
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(data)
    index = PACK / 'index.txt'
    paths = set(index.read_text(encoding='utf-8').splitlines()) | resources.keys()
    index.write_text('\n'.join(sorted(paths)) + '\n', encoding='utf-8')
    print(f'Approved T1 sword: {len(resources)} assets in the default pack; no launch flag needed.')


if __name__ == '__main__':
    promote()
