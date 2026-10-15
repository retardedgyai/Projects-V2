"""Export an mcrender model (elements + {name: 16px PIL texture}) to a Blockbench .bbmodel
(Generic/free format), one embedded texture per distinct texture name, so it can be viewed
in the real Blockbench instead of the home-made renderer."""
import io
import json
import base64
import uuid
import importlib
import sys
from mcrender import default_uv

AX = {'x': 0, 'y': 1, 'z': 2}


def _uv(el, face, fd):
    if 'uv' in fd:
        return list(fd['uv'])
    u = default_uv(el['from'], el['to'], face)
    w, h = u[2] - u[0], u[3] - u[1]
    if abs(w) <= 16 and abs(h) <= 16:
        ox = (min(u[0], u[2]) // 16) * 16
        oy = (min(u[1], u[3]) // 16) * 16
        return [u[0] - ox, u[1] - oy, u[2] - ox, u[3] - oy]
    return [0, 0, 16, 16]


def export(model, textures, title, path):
    names, texs = {}, []

    def tex_id(name, emissive):
        key = (name, emissive)
        if key not in names:
            img = textures[name]
            buf = io.BytesIO()
            img.save(buf, 'PNG')
            names[key] = len(texs)
            texs.append({
                "name": f"{name}{'_glow' if emissive else ''}.png", "folder": "", "namespace": "",
                "id": str(len(texs)), "width": img.size[0], "height": img.size[1],
                "uv_width": 16, "uv_height": 16, "particle": len(texs) == 0,
                "render_mode": "emissive" if emissive else "default", "render_sides": "auto",
                "visible": True, "internal": True, "saved": True, "uuid": str(uuid.uuid4()),
                "source": "data:image/png;base64," + base64.b64encode(buf.getvalue()).decode()})
        return names[key]

    elements = []
    for i, el in enumerate(model['elements']):
        rot, origin = [0, 0, 0], [8, 8, 8]
        if el.get('rotation'):
            r = el['rotation']
            rot[AX[r['axis']]] = r['angle']
            origin = r['origin']
        faces = {}
        emis = False
        for face in ('north', 'east', 'south', 'west', 'up', 'down'):
            fd = el['faces'].get(face)
            if not fd:
                continue
            emis = emis or fd.get('emissive', False)
            faces[face] = {"uv": _uv(el, face, fd), "texture": tex_id(fd['texture'].lstrip('#'), fd.get('emissive', False))}
        elements.append({
            "name": f"part{i}", "box_uv": False, "rescale": False, "locked": False,
            "light_emission": 15 if emis else 0, "render_order": "default",
            "from": [round(v, 4) for v in el['from']], "to": [round(v, 4) for v in el['to']],
            "autouv": 0, "color": i % 8, "origin": [round(v, 4) for v in origin],
            "rotation": [round(v, 4) for v in rot], "faces": faces, "type": "cube", "uuid": str(uuid.uuid4())})
    bb = {
        "meta": {"format_version": "4.10", "model_format": "free", "box_uv": False},
        "name": title, "model_identifier": "", "visible_box": [1, 1, 0],
        "resolution": {"width": 16, "height": 16},
        "elements": elements, "outliner": [e["uuid"] for e in elements], "textures": texs,
    }
    with open(path, 'w', encoding='utf-8') as fp:
        json.dump(bb, fp, ensure_ascii=False)
    return len(elements), len(texs)


if __name__ == '__main__':
    mod = importlib.import_module(sys.argv[1])
    title = sys.argv[2]
    print(export(mod.M(), mod.T, title, f'{title}.bbmodel'))
