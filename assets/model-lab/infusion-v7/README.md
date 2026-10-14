# 儀式連動の核（造形とパレットはv5を保持）

核の45要素はv5と同一。発光面にだけ `tintindex:0` を追加し、既存アニメーションの最大光フレームを固定16px原画として取り出す。紫の画素を変えず、実儀式の共有timingがCustomModelDataの中立色（RGB同値）で明るさを調節する。石・真鍮はtintしない。native `light_emission:15` と透過spriteを使い、Bloomや色付きの周辺照明を追加しない。

石殻・拘束具・光は同一ItemDisplayのleft rotationで一緒に回る。旧v3-v6は全ファイルのhashを保護する。最新モデルと台座は独立world playgroundのpack/表示コードへ接続した。main、本番world、公開サーバーへの適用は行っていない。

タイミングと制約は `docs/development/world-infusion-ritual-animation.md`。GUIclientの画質/透明ソート/補間/FPSは未検証。独立previewは実Kotlin traceとJSON/PNGの描画。核の位置・回転・光量、Jar残量、もや、素材消費の時間を共有し、投影/照明/液体表面/装備markerは近似。装備そのものの見た目を新制作した成果ではない。

再現export：`scripts/build-infusion-ritual-art.py`。古いactive packモデルを削除/上書きせず、新ID `projects:infusion-v7/core_ritual` と既存スタディの専用IDを配置する。新clientやshaderpack不要。
