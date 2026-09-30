# 中央台・材料台の紫灰パレット

本人の「台座の色も揃えたい」に対応する、石材の色だけを変えた比較版。中央台 `center` と周辺材料台 `offering` の白灰/黄味の石を、核・支柱と共通する暗い紫灰へ揃える。主面・稜線・暗部の3段階以上の明暗を残し、受け皿の縁と物を載せるくぼみを読みやすくする。

native要素の形状・大きさ・配置・face・UVをv3と完全一致させる。16px原画のpixel clusterとalphaもそのまま。変更する材質は `limestone` / `well` の2つ。金属接点の控えめな金、暗石の足、刻印は元のPNGを保持。台の発光は追加しない。核はv5、鋭い支柱はv4、Jarはv3のまま使用する。

旧v3/v4/v5の全ファイルをhash照合で保護する。旧パレットや過去比較を上書きしない。変更はこの独立ディレクトリだけであり、active game pack・ゲームruntime・Jar・研究・装備・保存へ未接続。実clientの描画とFPSは未検証。

再現：`python scripts/build-infusion-pedestal-palette.py`（Pillow/NumPy、既存cached26.2 assetsをread-only参照）。検証：`python scripts/verify-infusion-pedestal-palette.py`。同角度/同縮尺の一式旧新と、中央台・材料台の正面/斜め旧新PNGを `.tools/world-model-preview/` に出力する。画像は実JSON/UV/PNGのoffline描画で、ゲーム画面ではない。
