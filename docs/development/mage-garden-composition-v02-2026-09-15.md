# 氷の庭：足元・副柱・形成と材質の統一（v02、未達）

前段: [氷柱の初回再制作](mage-cryopillar-review-2026-09-15.md)。
Mage全10技をMatE／Wynncraftの参照水準へ再制作する作業の途中。
**今回も完成／参照以上の品質とは判定していない。**

## 参照と判断

R01 [MatE Cryomancer](https://www.youtube.com/watch?v=LztyUoK2KGk) の
低い青い根から高い柱への形成、長い白青の主面、短い副柱、斜めの足元を根拠にする。
実見記録と時間は前段文書。今回はその不足箇所へ戻って同じR01を比較した。
既存31件の件数を増やす変更ではない。

参考資料に存在を確認できなかった「大きな枝分かれする発光亀裂」は採用しない。
低い根が先行する表現を予備動作に使い、古い段状の台座は発動経路から外した。
持続中の別系統の `garden_beat` も出さない。サーバーの4回のダメージpulse、
命中時CONTACT、音、MP、CD、範囲は変更しない。

## 実装変更

- 主柱 `cryo_pillar` と通常の根 `cryo_root` に加え、
  低く厚く張り出す `cryo_buttress`、高低二つの尖りの `cryo_crown`、
  低い根の予兆 `cryo_seed` を追加。副柱は主柱の縮小コピーから別の輪郭になった。
- 2つの根を低い破片へ交換。主柱・副柱の付け根から外へ伸ばし、
  初回版の細長いV字／柵状の足元を解消する方向。
- 正面・裏面のnative face windingに合わせ、north/east側のUVを反転。
  断面の中心が変わるたびに原画を局所反転していた問題を修正した。
- `CoreMageChoreography` の氷専用の高さ定義を各nativeモデルの最高点に合わせた。
  形成後は同じモデルを保持し、末尾で地面下へ退く。種は終端ゼロscale。
- 共有ground解決が平面用に付ける0.12の隙間を、氷の立体だけposeで補正。
  根は地面へ0.02入り、空中に底が浮くのを防ぐ。共有Rendererには変更しない。
- UI、Warrior、クライアント、ゲーム入力、戦闘ルールは変更していない。

主経路: 原画v02 → `scripts/build_mage_cryopillar.py` → 5種のnativeモデルとitems/index
→ `CoreMageChoreography.parts/pose` → 既存Display補間／地形のdepth test。

## 原画v02と生成方法

組み込みimagegen、編集モード。CLI/APIへの切替なし。
Image 1は原画v01、Image 2は `.tools/reference-cryo-sequence-01.png` の左上のR01を画風参照。
色だけを明るくせず、明るい長い面が中腹と根元へ入り込むように再作画した。
背景を含むRGB出力をそのまま保存し、描画UVだけを塗りの内側へ制限する。
画像の加工・透過処理はしていない。v01は比較と来歴のため保持。

保存先:
`assets/combat-vfx/mage-v5/sources/cryopillar-faces-v02.png`

元の生成ファイル:
`C:/Users/xgaiz/.codex/generated_images/01a060a6-6e32-7e13-a4cd-2730ddc429e9/exec-bb16e385-543c-45a7-93cb-0fb13263b43d.png`

使用した最終プロンプト:

> Use case: precise-object-edit. Asset type: production pixel-art painted FRONT and SIDE surfaces of a Minecraft ice crystal, not a rendered 3D object. Image 1 is the edit target. Image 2 is style reference ONLY: the top-left ice-pillar sculpture. Change only the interior painting of the two silhouettes in Image 1; preserve their exact outer shapes, placement, relative size and upright front/side layout. Repaint the ice with the long interlocking facets and luminous milky-cyan material of the top-left reference. The current smooth pale top above an almost navy-black base is wrong. Instead, 3 to 5 long irregular pale cyan slivers descend from the top into the middle/lower region while blue angled facets climb between them. Keep one broad quiet milky-cyan upper plane, but add two thin long fractures interrupting its edge; no uniform neon strip. The bottom third remains blue, with sizable lighter blue facets and pale blue broken edges, not dark flat black/navy stalks. The side face is more muted blue than the front but is visibly icy, with a thin pale rim. Use around 8 deliberate icy blue/blue-gray/cyan/off-white tones, crisp pixel clusters and stepped diagonals on a roughly 32 by 96 logical grid per painted face. No blurred gradients, tiny speckled noise, dithering, glints, ornamental runes, icons, added objects or perspective shading. Keep background and layout unchanged. Do not include any part of the reference screenshot or its text. The result will be mapped to real faceted geometry and evaluated in Minecraft; it must not look like a glossy neon gem.

指示は不変条件を求めたが、出力の外形がピクセル単位で不変とは証明していない。
新しい出力の塗り領域を読み直してUVの背景混入をテストしている。

## 実装確認とレビュー

- Python5件: 座標・native回転、出荷画像同一性、UVの塗り内制約、
  opposite face winding、モデル再現、登録、独立した副柱の構造。
- Kotlin対象3クラス・作業ツリー33件合格（既存の未commit frostテスト1件を含む）。形の高さ定義、48tick寿命、形成順、保持、
  種の終端ゼロ、後続pulseで別の大形を再生しないことを確認。
- 未変更Vanilla 26.2のnative parserで5モデルを受理。
  atlas列挙で148参照を解決。これらはGPUの出力品質の証明ではない。
- 実際のサーバーmetadataをVanilla Displayへ再生し、予兆2＋本体6の補間を確認。
  `.tools/garden-cryopillar-footing-native.json` は60fpsの補間データ。
- `.tools/garden-cryopillar-interlock-54.png` は目線投影。
  `-interlock-iso-54.png` は斜め上からの形の確認。
  `-interlock-side-54.png` は画面端で主柱が切れるため、全体品質の証拠として不十分。
- ground隙間補正後の最終静止確認は `.tools/garden-cryopillar-interlock-final-54.png`。
  `.tools/garden-cryopillar-interlock-motion.gif` は実装metadataの連続投影用。
  これらもゲーム画面ではなく、地形遮蔽を再現しない。

読み取り専用の独立レビュー:

1. 明暗の上下の分断が弱まり、接続は改善。ただし太いジグザグの筋が稲妻模様に寄る。
2. 副柱の高低二つの尖りが読め、縮小コピー感は減少。この形の差は保持する。
3. 足元の横幅・接地感は改善。一方で右へ出る一部が薄い水平の舌に見え、
   参照の厚い破片の重なりはまだ不足。細長い台座へ戻さないこと。

ゲーム内照明・地形遮蔽・段差・実プレイ視界は未確認。
全10技と全参照を合わせた最終レビューも残る。テストの成功でこれらを置き換えない。

## 不具合時の入口

色／輪郭は `build_mage_cryopillar.py` と原画v02。
浮き／終端は `CoreMageChoreography.rootedIceHeight/pose`。
欠損画像はpack indexとtexture参照を先に確認する。
ゲーム・サーバーは起動せず、実験ブランチだけで継続する。
