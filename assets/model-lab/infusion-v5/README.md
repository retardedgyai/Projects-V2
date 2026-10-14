# 人工の封印核 / 紫の局所発光

本人の「もっと人為的な禍々しいもの」「紫の光」の指定に合わせた独立モデル比較。自然な割岩・ランダム破片をやめ、左右対称の加工石殻、正確に切られた貫通開口、深い刻印溝、四隅の拘束具、空隙に浮く規則的な封印体へ造形を変更した。核の大きな存在感と22.5度の浮遊角度を維持する。支柱はv4の一方向に傾く鋭い石刃、中央台・材料台・Jarはv3をそのまま参照する。

材質は既存の16px石・控えめな真鍮を継承。新しい石彫り、暗い凹面、発光画素、透過spriteはコードで手描きした実PNG。画像生成・外部モデル・TCのデコンパイル描画コードを使わない。v3/v4は全ファイルのSHA-256を照合し、編集しない。

## 実アセットの発光経路

- 対応版：現在のProjectSが指定するMinecraft Java 26.2。Fabric追加機能は不要。
- `projects:infusion-v5/core_idle` / `core_active` はnative item model。石の面は通常の環境光を受け、溝・内部の面だけ `light_emission:15` / `shade:false`。
- `seal_active.png` / `leak_active.png` は24枚の16pxフレーム。native `.png.mcmeta` の `frametime:2` / `interpolate:true` により48tick相当で脈動する。サーバーから各フレームを送らない。実時間はclient側のtexture animationに従う。
- 開口内の柔らかい光は `force_translucent:true` の実PNG透過sprite。モデルに含む1枚の両面quadであり、CPU後加工のぼかしではない。**これはBloomではない。床・壁を紫色に照らす光源でもない。**
- 核は状態ごとにItemDisplay1体で表現できる。現サーバーの `ItemStack.withItemModel(...)` でidle/activeを選べる構成。追加のclient・shaderpack・全体shader置換は行っていない。
- スタディはactive game packへ未導入、儀式状態との切替も未接続。モデルを採用してから設置表示・占有範囲・焦点・停止時切替をまとめて整合させる。現在の取引/消費/保存/霧cleanupロジックは変更しない。

## 制約とフォールバック

標準26.2は自発光面を描画する経路を持つが、既存clientにはBloomパスや色付きの周囲照明がない。第三者shaderpackは発光/透過/アニメーションを別扱いする可能性があり互換を未検証。新shaderpackをユーザーへ要求しない。発光無効化時は `core_unlit` が同じ形の石＋紫刻印を通常照明で描く。pack無しでは既存world playgroundのvanilla fallbackが残るが、この専用造形を表示できない。

native parserによるgeometry/発光値/不正値拒否とアセット検証を実施する。画像とGIFは実JSON・UV・PNGを読むoffline renderer。暗所の環境光係数は近似であり、実client/GPUの明るさ、透過ソート、shader互換、FPS、影、周囲照明を測った証拠ではない。GIFは実PNGを100ms刻みで表示し、nativeのフレーム間補間は含めない。

## 再現

`python scripts/build-infusion-sealed-core.py` と `python scripts/verify-infusion-sealed-core.py`。Pillow/NumPyと既存のcached 26.2 jarをread-only参照する。Java25を指定し `scripts/run-native-infusion-emission.ps1` で未改変vanillaのparser検証。GUIやゲーム起動不要。

旧新全景は同じ角度・縮尺。通常/暗所/作動の核拡大、単独全景、脈動GIF、画素アトラス、provenanceと検証記録を出力する。画像へBloomや床の紫色照明を焼き込まない。
