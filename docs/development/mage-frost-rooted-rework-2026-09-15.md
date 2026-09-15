# 霜の波紋：根付きの放射氷へ再制作（未達・継続）

## 今回変えたもの

以前の `frost_wave` 3個の移動＋薄い床線を、低い根／内側3束／外側3束へ置換。
素材追加だけでなく、足元の予備動作、形の構成、表示方式も変更した。
Mage全10技の完成やMatE／Wynncraft水準到達を意味しない。

- 主担当がR04の作者本編36.60–39.10秒を再観察。根付きで外を向く氷群を基準にした。
- 内側は短い氷、外側は幅広い主片と短い側片。中心を空けた全周構成。
- 固定された7モデルを使い、内側から外側へ時間をずらして立ち上げる。
- 位置は固定し、高さが立ち上がる。毎tick別の完成画像やモデルへ交換しない。
- 通常のVanilla 26.2 ItemDisplay・リソースパックのみ。1tickの標準補間。
- PREPAREは手元の氷塊から足元の低い根へ変更。CONTACTの小さい既存ice_hitは変更していない。
- NOVAの攻撃判定、半径、スロー、詠唱、MP、CD、入力、他職業、UIは変更しない。

## 実モデル比較で修正した点

初回 `.tools/rime-v1-*` は、旧孤立塊からは改善したが、槍を並べた星形であり未達。
根元が一点へ集まり、面に細かい横縞が出ていた。読み取り専用レビューでも同じ指摘。

水平薄片で斜めの氷を積層すると、明るい面全体に段ができることを確認した。
続く `.tools/rime-v3-*` は **斜めの同一平面内** で輪郭だけを段状にした。
標準26.2の `CuboidRotation.EulerXYZRotation` とそのJSONデシリアライザをローカルで確認し、
RP要素の `rotation.x/y/z` を使用。クライアントソースやバイナリは変更していない。
プレビューも同じ標準回転順序に対応させ、独自の見栄え補正はしていない。

再レビューではまだ単純な三角錐の束という指摘があったため、最終の今回版 `rime-v4-*` で
主片3本に幅のある非対称な肩を追加。低い本体と長い先端で面の角度を変え、作画は長軸でつなげた。
これだけで参照の複雑な分岐を再現したとはしない。

## 作画

imagegenスキルの built-in image_gen を使用。CLI/APIキー利用なし。
入力はR11の保存観察画像を画風参照として使用し、作者の画像自体をRPへ入れていない。

- 原画: `assets/combat-vfx/mage-v5/sources/rime-faces-v01.png`
- 全文プロンプト: 同ディレクトリ `rime-faces-v01.prompt.txt`
- 配布テクスチャ: `server-minestom/src/main/resources/core-ui-pack/assets/projects/textures/combat_vfx/mage_material/rime_faces_v01.png`
- 原画と配布画像はSHA-256一致。画像の描き直し・加工をPythonで行っていない。
- 太い白水色の主面、青い中間面、暗い側面、低い根の4領域。

## 処理の流れと主なファイル

`CoreMageChoreography` → `CoreMageFrostChoreography.parts/pose` → 既存 `CoreCombatMeshes`。
主役の位置は変えず、各束のdelayと高さを補間。最後は高さを下げ、ゼロを送ってから削除。
最重要ファイルは `server-minestom/src/main/kotlin/dev/projects/server/coreloop/CoreMageFrostChoreography.kt`。
形・UVは `scripts/build_mage_rime.py`。7モデルはpack indexへ明示登録。

壊れた場合は最初に、実際に配布されたpackのrimeアイテム／モデル／テクスチャとindex、
続いて上記Mageローカル振付、最後に実表示metadataを調べる。
共通レンダラー・protocolへ推測で修正を広げない。

## 検証

- Kotlin対象テスト: CoreMageChoreographyTest 11、CoreMageFrostChoreographyTest 3、CoreMageFireboltChoreographyTest 6、CoreCombatMeshTest 20、計40件、失敗0。
- Python `test_mage_rime.py`: 7件成功。各モデル1000elements以下、UV、原画一致、再生成一致、面の方向、主片の肩。
- Vanilla 26.2のCuboidModelが7モデルを受理。invalid-axisの負例は拒否。
- `CheckNativeRimePlanes.java`: 実際に生成した7モデルの40,704頂点について、
  標準のパース済み回転行列とプレビュー計算の一致を確認。
- `ExportMageDisplayTimeline`: 実際のサーバー表示metadataの8表示（予備1＋本体7）を
  改造していないVanilla Displayへ投入。削除前ゼロ・有限変換・残存entityなしを確認。
- ネイティブatlas: 150参照combat sprite検出。stock-directory負例は検出0。

## プレビューと残り

`.tools/rime-v4-motion.gif`、`rime-v4-iso-{36,45,60,81}.png`、`rime-v4-eye-{45,60}.png`。
実モデルと実metadataからの簡易投影。ゲーム映像ではなく、GPU・地形遮蔽・ネットワークは検証しない。
60fpsの表示データを作ったことだけで、滑らかさや手触りの合格とはしない。

旧版のノイズの多い岩塊と移動は解消したが、放射槍のような輪郭はまだ残る。
参照の「厚い束から枝分かれする」形と、青い根の厚さには差がある。
テクスチャにも生成由来の微細な階調が残り、要求された完全に整理されたピクセル面の達成とはしない。
CONTACTの既存素材、斜面での接地、消散の見え方も継続対象。
全31参照との最終比較／全10技の品質判定は未完了。ゲームの起動・操作はしていない。

## 既存作業の保全

この改修対象に重なる旧未commit差分は、frost_waveの高さ変更1行とそのテスト。
`.tools/frost-prior-local.patch` に保全してから、新しい根付き構成のテストへ更新。
他の旧生成物・dirtyファイルは残し、今回のcommitへ含めない。mainは変更しない。
