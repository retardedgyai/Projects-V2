#!/bin/bash
# 撮る：kit/shot.sh <町の名前> <出力の画像> 視点...
#   視点は save() に書いた名前か、@x;y;z;tx;ty;tz（カメラと注視点、x はそのまま）。
#   out/ を http://127.0.0.1:8767 で配り（無ければ立てる）、まっさらなブラウザで1枚ずつ撮って並べる。
#   重い処理なので flock ~/ProjectS/.heavy.lock で包んで呼ぶ。
KIT=$(cd "$(dirname "$0")" && pwd); OUTROOT="$KIT/../out"; D=$1; OUT=$2; shift 2
ss -ltn | grep -q ':8767 ' || (cd "$OUTROOT" && nohup python3 -m http.server 8767 >/dev/null 2>&1 &) ; sleep 1
TMP=$(mktemp -d); files=()
for v in "$@"; do
  f="$TMP/shot-$(echo "$v" | tr ';@,' '___').png"
  prof=$(mktemp -d)
  chromium --headless=new --user-data-dir="$prof" --disk-cache-size=1 --no-sandbox --use-angle=swiftshader --enable-unsafe-swiftshader \
    --ignore-gpu-blocklist --hide-scrollbars --window-size=1000,750 --virtual-time-budget=60000 --screenshot="$f" \
    "http://127.0.0.1:8767/$D/test.html#$v" >/dev/null 2>&1
  rm -rf "$prof"; files+=("$f")
done
python3 - "$OUT" "${files[@]}" <<'PY'
import sys
from PIL import Image
out, fs = sys.argv[1], sys.argv[2:]
ims = [Image.open(f).resize((800, 600)) for f in fs]
cols = 2 if len(ims) > 3 else len(ims); rows = (len(ims) + cols - 1) // cols
s = Image.new('RGB', (800 * cols, 600 * rows))
for i, im in enumerate(ims): s.paste(im, ((i % cols) * 800, (i // cols) * 600))
s.save(out, quality=85); print(out)
PY
rm -rf "$TMP"
