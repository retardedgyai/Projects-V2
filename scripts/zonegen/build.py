import sys, time, pickle
import numpy as np
from core import Zone
from blocks import Blocks
import validate
import zone1
t0 = time.time()
z = Zone(zone1.SPEC)
for st in ("macro", "lakes", "streams", "places", "roads", "solve", "detail", "quantize"):
    getattr(z, st)(); print(st, round(time.time() - t0, 1), flush=True)
bl = Blocks(z); bl.build(); print("blocks", len(bl.b), round(time.time() - t0, 1), flush=True)
res = validate.run(z, bl, zone1.SPEC["places"])
ok = validate.report(res)
print("点検", "合格" if ok else "不合格")
pickle.dump((bl.b, z.hi, []), open("field500.pkl", "wb"))
np.save("upper.npy", np.full_like(z.hi, -1))
pickle.dump(z, open("zone.pkl", "wb"))
