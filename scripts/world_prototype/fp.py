"""First-person voxel raycaster: what a player standing somewhere actually sees.

python3 fp.py name x y z tx ty tz [fov] [w]
  y / ty may be 'g' (eye 1.6 above the ground there) or 'g+N'.
Writes fp-<name>.png. Textures come from iso.tex (same tints). Leaves and plants are cut out by their alpha,
slabs and carpets have their real height, water is a translucent tint.
"""
import pickle, sys, math
import numpy as np
from PIL import Image
import iso

CUT = {"plant", "ceil", "shut_n", "shut_s", "shut_e", "shut_w", "lantern", "post", "fence", "chain", "fire", "pane_x", "pane_z",
       "thinlog", "crop", "wall", "cloth_e", "cloth_w"}
BOXES = {"slab": (0, 0, 0, 1, .5, 1), "slab_t": (0, .5, 0, 1, 1, 1), "carpet": (0, 0, 0, 1, .0625, 1), "ceil": (0, .9375, 0, 1, 1, 1),
         "shut_s": (0, 0, 0, 1, 1, .1875), "shut_n": (0, 0, .8125, 1, 1, 1), "shut_w": (.8125, 0, 0, 1, 1, 1), "shut_e": (0, 0, 0, .1875, 1, 1),
         "post": (.375, 0, .375, .625, 1, .625), "fence": (.375, 0, .375, .625, 1, .625), "wall": (.25, 0, .25, .75, 1, .75),
         "lantern": (.3, 0, .3, .7, .55, .7), "chain": (.44, 0, .44, .56, 1, .56), "thinlog": (.3, 0, .3, .7, 1, .7)}
BOXA = None
MOB = {"deer", "beetle", "sprite", "boss", "rabbit", "boar", "frog", "wolf", "sheep_x", "sheep_z", "npc_low", "npc_high", "arm_x", "arm_z"}


def load(path="field500.pkl"):
    b, hi, L = pickle.load(open(path, "rb"))
    return b, hi


def build(b):
    keys = {}
    ys = [k[1] for k in b]; y0, y1 = min(ys), max(ys)
    X = max(k[0] for k in b) + 1; Z = max(k[2] for k in b) + 1
    G = np.zeros((X, y1 - y0 + 2, Z), np.uint16)
    cls = [0]; tops = [None]; sides = [None]; boxes = [(0, 0, 0, 1, 1, 1)]
    for (x, y, z), (shape, top, side) in b.items():
        if shape in MOB: continue
        bx = BOXES.get("slab" if shape.startswith("stair") and not shape.endswith("_t") else shape)
        c = 4 if shape == "water" or top == "water_still" else 2 if bx else 5 if shape in CUT else 1
        k = (c, top, side, bx)
        if k not in keys:
            keys[k] = len(cls); cls.append(c); tops.append(top); sides.append(side); boxes.append(bx or (0, 0, 0, 1, 1, 1))
        if x >= 0 and z >= 0: G[x, y - y0, z] = keys[k]
    n = len(cls)
    TT = np.zeros((n, 16, 16, 4), np.float32); TS = np.zeros((n, 16, 16, 4), np.float32)
    for i in range(1, n):
        for arr, name in ((TT, tops[i]), (TS, sides[i])):
            try:
                im = iso.tex(name).resize((16, 16), Image.NEAREST)
            except Exception:
                im = Image.new("RGBA", (16, 16), (255, 0, 255, 255))
            arr[i] = np.asarray(im, np.float32) / 255
        if cls[i] == 1:                                         # full blocks: leaves keep holes, everything else opaque
            if not ("leaves" in tops[i] or tops[i] in ("glass", "iron_bars", "mangrove_roots_top")):
                TT[i, ..., 3] = 1; TS[i, ..., 3] = 1
    global BOXA
    BOXA = np.array(boxes, np.float64)
    return G, y0, np.array(cls, np.int8), TT, TS


def render(G, y0, CLS, TT, TS, eye, target, fov=75, W=800, H=450, maxd=260):
    ex, ey, ez = eye; ey -= y0
    tx, ty, tz = target; ty -= y0
    f = np.array([tx - ex, ty - ey, tz - ez], np.float64); f /= np.linalg.norm(f)
    r = np.cross(f, [0, 1, 0]); r /= np.linalg.norm(r); u = np.cross(r, f)
    a = math.tan(math.radians(fov) / 2)
    px = (np.arange(W) + .5) / W * 2 - 1; py = 1 - (np.arange(H) + .5) / H * 2
    PX, PY = np.meshgrid(px * a, py * a * H / W)
    D = f[None, None] + PX[..., None] * r + PY[..., None] * u
    D /= np.linalg.norm(D, axis=-1, keepdims=True)
    D = D.reshape(-1, 3); N = len(D)
    O = np.array([ex, ey, ez])
    pos = np.floor(O).astype(np.int64)[None].repeat(N, 0)
    step = np.where(D > 0, 1, -1)
    with np.errstate(divide="ignore", invalid="ignore"):
        tD = np.abs(1 / D)
        tM = np.where(D > 0, (pos + 1 - O) / D, (O - pos) / -D)
    tD = np.nan_to_num(tD, posinf=1e9); tM = np.nan_to_num(tM, nan=1e9, posinf=1e9)
    tcur = np.zeros(N); axis = np.full(N, 1)
    col = np.zeros((N, 3)); trans = np.ones(N); alive = np.ones(N, bool); inwater = np.zeros(N, bool)
    hit_t = np.full(N, np.inf)
    SX, SY, SZ = G.shape
    LIGHT = np.array([.62, 1.0, .78])                     # x faces, top, z faces
    for it in range(int(maxd * 1.8)):
        idx = np.nonzero(alive)[0]
        if len(idx) == 0: break
        p = pos[idx]
        inb = (p[:, 0] >= 0) & (p[:, 0] < SX) & (p[:, 1] >= 0) & (p[:, 1] < SY) & (p[:, 2] >= 0) & (p[:, 2] < SZ)
        m = np.zeros(len(idx), np.int64)
        m[inb] = G[p[inb, 0], p[inb, 1], p[inb, 2]]
        c = CLS[m]
        out = (~inb & ((p[:, 1] < 0) | (p[:, 1] >= SY + 40))) | (tcur[idx] > maxd)
        alive[idx[out]] = False
        # where does the ray enter / leave this voxel
        t0 = tcur[idx]; t1 = tM[idx].min(1)
        hitmask = np.zeros(len(idx), bool); nrm = axis[idx].copy(); th = t0.copy()
        P0 = O + D[idx] * t0[:, None]
        fr = P0 - p
        # water: tint once and continue
        wmask = (c == 4)
        new_w = wmask & ~inwater[idx]
        if new_w.any():
            k = idx[new_w]
            col[k] += trans[k, None] * .5 * np.array([.20, .36, .62]); trans[k] *= .5
        inwater[idx] = wmask
        solid = (c == 1) | (c == 5)
        hitmask |= solid
        boxm = (c == 2)
        if boxm.any():
            bi = np.nonzero(boxm)[0]
            bb = BOXA[m[bi]]
            lo = p[bi] + bb[:, :3]; hi_ = p[bi] + bb[:, 3:]
            Dd = D[idx[bi]]
            with np.errstate(divide="ignore", invalid="ignore"):
                ta = (lo - O) / Dd; tb = (hi_ - O) / Dd
            ta = np.nan_to_num(ta, nan=-1e9, posinf=1e9, neginf=-1e9); tb = np.nan_to_num(tb, nan=1e9, posinf=1e9, neginf=-1e9)
            tn3 = np.minimum(ta, tb); tf3 = np.maximum(ta, tb)
            tn = tn3.max(1); tf = tf3.min(1)
            ok_ = (tn <= tf) & (tf >= t0[bi]) & (tn <= t1[bi] + 1e-9)
            tn = np.maximum(tn, t0[bi])
            hb = bi[ok_]
            th[hb] = tn[ok_]; nrm[hb] = np.argmax(tn3[ok_], 1)
            hitmask[hb] = True
        if hitmask.any():
            hk = np.nonzero(hitmask)[0]
            k = idx[hk]
            PP = O + D[k] * th[hk, None]
            q = PP - pos[k]
            q = np.clip(q, 0, .9999)
            ax = nrm[hk]
            u_ = np.where(ax == 0, q[:, 2], q[:, 0]); v_ = np.where(ax == 1, q[:, 2], 1 - q[:, 1])
            ui = (u_ * 16).astype(int); vi = (v_ * 16).astype(int)
            mm = m[hk]
            texel = np.where((ax == 1)[:, None], TT[mm, vi, ui], TS[mm, vi, ui])
            opaque = texel[:, 3] > .4
            # alpha-cut texels let the ray through (leaves, plants)
            ok = opaque
            kk = k[ok]
            if len(kk):
                lit = LIGHT[ax[ok]] * np.where((ax[ok] == 1) & (D[kk, 1] > 0), .5, 1)
                dist = th[hk][ok]
                fog = np.clip((dist - maxd * .35) / (maxd * .65), 0, 1)[:, None]
                sky = np.array([.62, .74, .88])
                cc = texel[ok, :3] * lit[:, None]
                cc = cc * (1 - fog) + sky * fog
                col[kk] += trans[kk, None] * cc; trans[kk] = 0; alive[kk] = False; hit_t[kk] = dist
        # advance
        a_ = np.argmin(tM[idx], 1)
        rows = np.arange(len(idx))
        tcur[idx] = tM[idx, a_] if True else 0
        pos[idx, a_] += step[idx, a_]
        tM[idx, a_] += tD[idx, a_]
        axis[idx] = a_
    # sky for whatever is left
    dy = D[:, 1]
    sky = np.stack([.55 + .2 * (1 - dy), .68 + .15 * (1 - dy), .86 + .08 * (1 - dy)], 1)
    col += trans[:, None] * np.clip(sky, 0, 1)
    img = (np.clip(col, 0, 1).reshape(H, W, 3) ** (1 / 1.1) * 255).astype(np.uint8)
    return Image.fromarray(img)


UP = None
def parse_y(s, hi, x, z, base):
    global UP
    if s.startswith("u"):
        if UP is None: UP = np.load("upper.npy")
        return float(UP[int(z), int(x)]) + 1 + (float(s[1:]) if len(s) > 1 else 1.6)
    if s.startswith("g"):
        return float(hi[min(499, max(0, int(z))), min(499, max(0, int(x)))]) + 1 + (float(s[1:]) if len(s) > 1 else 1.6)
    return float(s)


if __name__ == "__main__":
    a = sys.argv[1:]
    b, hi = load()
    G, y0, CLS, TT, TS = build(b)
    jobs = [a[i:i + 7] for i in range(0, len(a), 7)] if len(a) % 7 == 0 else [a]
    for j in jobs:
        nm, x, y, z, tx, ty, tz = j[:7]
        x, z, tx, tz = float(x), float(z), float(tx), float(tz)
        eye = (x + .5, parse_y(y, hi, x, z, 0), z + .5); tgt = (tx + .5, parse_y(ty, hi, tx, tz, 0), tz + .5)
        render(G, y0, CLS, TT, TS, eye, tgt).save(f"fp-{nm}.png"); print(nm, flush=True)
