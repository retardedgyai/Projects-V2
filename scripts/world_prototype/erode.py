"""Hydraulic (droplet) erosion and thermal relaxation on a float heightmap, vectorised over droplets with numpy.

Terraformers run an erosion pass (World Machine / Gaea) before painting: it cuts branching gullies down slopes and
leaves soft deposition fans at the feet, which is what makes a heightmap stop looking like smooth noise blobs.
"""
import numpy as np


def _bilinear(H, x, z):
    x0 = np.floor(x).astype(np.int64); z0 = np.floor(z).astype(np.int64)
    fx = x - x0; fz = z - z0
    h00 = H[z0, x0]; h10 = H[z0, x0 + 1]; h01 = H[z0 + 1, x0]; h11 = H[z0 + 1, x0 + 1]
    h = h00 * (1 - fx) * (1 - fz) + h10 * fx * (1 - fz) + h01 * (1 - fx) * fz + h11 * fx * fz
    gx = (h10 - h00) * (1 - fz) + (h11 - h01) * fz
    gz = (h01 - h00) * (1 - fx) + (h11 - h10) * fx
    return h, gx, gz, x0, z0, fx, fz


def hydraulic(H, n=120000, steps=60, seed=1, inertia=.08, capacity=5.0, deposit=.25, erode=.35, evap=.02,
              min_slope=.01, gravity=4.0, radius=2, batch=20000, mask=None):
    """Classic droplet erosion (Hans Beyer style), many droplets at once. mask (0..1) scales how much a cell may change."""
    H = H.astype(np.float64).copy()
    B = H.shape[0]
    rng = np.random.default_rng(seed)
    # erosion brush: spread the removal over a small disc so gullies are a few blocks wide, not 1-cell slots
    offs = [(dx, dz) for dx in range(-radius, radius + 1) for dz in range(-radius, radius + 1) if dx * dx + dz * dz <= radius * radius]
    wts = np.array([max(0, radius - np.hypot(dx, dz)) + 1e-3 for dx, dz in offs]); wts /= wts.sum()
    M = np.ones_like(H) if mask is None else mask.astype(np.float64)
    for b0 in range(0, n, batch):
        m = min(batch, n - b0)
        x = rng.uniform(radius + 1, B - radius - 2, m); z = rng.uniform(radius + 1, B - radius - 2, m)
        dx = np.zeros(m); dz = np.zeros(m); v = np.ones(m); w = np.ones(m); sed = np.zeros(m)
        alive = np.ones(m, bool)
        for _ in range(steps):
            idx = np.nonzero(alive)[0]
            if len(idx) == 0: break
            h, gx, gz, x0, z0, fx, fz = _bilinear(H, x[idx], z[idx])
            dx[idx] = dx[idx] * inertia - gx * (1 - inertia); dz[idx] = dz[idx] * inertia - gz * (1 - inertia)
            L = np.hypot(dx[idx], dz[idx]); L[L == 0] = 1
            dx[idx] /= L; dz[idx] /= L
            nx = x[idx] + dx[idx]; nz = z[idx] + dz[idx]
            out = (nx < radius + 1) | (nx > B - radius - 2) | (nz < radius + 1) | (nz > B - radius - 2)
            nx = np.clip(nx, radius + 1, B - radius - 2.001); nz = np.clip(nz, radius + 1, B - radius - 2.001)
            nh = _bilinear(H, nx, nz)[0]
            dh = nh - h
            cap = np.maximum(-dh, min_slope) * v[idx] * w[idx] * capacity
            dep_mask = (sed[idx] > cap) | (dh > 0)
            # deposit: fill the pit when going uphill, else drop the excess
            amt_dep = np.where(dh > 0, np.minimum(dh, sed[idx]), (sed[idx] - cap) * deposit)
            amt_dep = np.clip(np.where(dep_mask, amt_dep, 0), 0, 1.0)
            amt_ero = np.where(~dep_mask, np.clip(np.minimum((cap - sed[idx]) * erode, -dh * .5), 0, .35), 0)
            mloc = M[z0, x0]
            amt_ero *= mloc; amt_dep *= np.maximum(mloc, .3)
            sed[idx] += amt_ero - amt_dep
            # deposit bilinearly at the old cell
            for (ox, oz, ww) in ((0, 0, (1 - fx) * (1 - fz)), (1, 0, fx * (1 - fz)), (0, 1, (1 - fx) * fz), (1, 1, fx * fz)):
                np.add.at(H, (z0 + oz, x0 + ox), amt_dep * ww)
            for (ox, oz), ww in zip(offs, wts):
                np.add.at(H, (z0 + oz, x0 + ox), -amt_ero * ww)
            v[idx] = np.clip(np.sqrt(np.maximum(0, v[idx] ** 2 - dh * gravity)), 0, 3)
            w[idx] *= (1 - evap)
            x[idx] = nx; z[idx] = nz
            alive[idx[out | (w[idx] < .05)]] = False
    return H.astype(np.float32)


def thermal(H, talus=1.2, rate=.25, iters=20, mask=None):
    """Material slumps from any cell steeper than the talus slope to its lowest neighbour: softens knife edges and builds skirts."""
    H = H.astype(np.float64).copy()
    M = np.ones_like(H) if mask is None else mask
    for _ in range(iters):
        P = np.pad(H, 1, mode="edge")
        nb = [P[1 + dz:1 + dz + H.shape[0], 1 + dx:1 + dx + H.shape[1]] for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1))]
        lo = np.minimum.reduce(nb)
        d = H - lo
        move = np.where(d > talus, (d - talus) * rate * M, 0)
        H -= move
        for n_ in nb:
            pass
        # give the material to the lowest neighbour
        for k, (dx, dz) in enumerate(((1, 0), (-1, 0), (0, 1), (0, -1))):
            sel = (nb[k] == lo) & (move > 0)
            add = np.zeros_like(H)
            add[sel] = move[sel]
            add = np.roll(np.roll(add, dz, 0), dx, 1)
            H += add
            move = np.where(sel, 0, move)
    return H.astype(np.float32)
