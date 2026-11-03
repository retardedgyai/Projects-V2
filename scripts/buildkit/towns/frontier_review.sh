#!/bin/bash
# 全区域を組んで、斜め上と目の高さで撮り、1枚に並べる。使い方：towns/frontier_review.sh <出力フォルダ>
cd "$(dirname "$0")/.."
O=$1; mkdir -p "$O"
for k in harbor hanaikada deitan shunrai hitsuji ryokufu kirinuma fuketsu ookami shirakashi setsugen okibi; do
  python3 towns/frontier_section.py $k 200 >/dev/null 2>&1
  kit/shot.sh frontier_$k "$O/$k.jpg" over eye >/dev/null 2>&1
  echo "撮った $k"
done
