# スキルアイコン代表案 v3

制作担当は Agent。Creator に画像制作や素材準備を依頼しない。

## 状態

- 斬撃・火球・防御の代表3点。採用前の提案であり、全70個の差し替え完了ではない。
- `assets/core-ui/AGENTS.md` の代表案確認後に全種類へ展開するルールに従う。
- 稼働中のサーバー、既存のスキル画像、承認済み日本語フォントは変更していない。
- 以前却下された2枚と旧70個の図形アイコンは制作参照に使用していない。

## 作成と検証

内蔵 image_gen で各1枚を生成。完全な生成プロンプトは `prompts.json`。
Monumenta の実際の能力スプライトを画風の参照に使用し、既存画像の直接転載はしていない。
参照元: https://github.com/Njol/UnofficialMonumentaMod
参照画像: warrior/riposte.png、mage/magma_shield.png、cleric/sanctified_armor.png。

`*-source.png` → `preview.py` で最近傍縮小 → 16px / 32px PNG → 既存の
`build_core_hud_assets.skill_frame` による使用可能・半分・全量クールダウン表示 → `comparison.png`。
3枚のサイズ検証と比較画像の目視確認を実施。ゲーム画面のスクリーンショットではない。
実装コードに変更がないため Gradle テスト・ゲーム内手動テストは今回実施していない。

## 残る確認

形は縮小後も識別可能。斬撃はアクションより装備品に見える懸念がある。
見た目の採用判定は未完了。採用後も背景のアルファ処理と実際のメニュー表示を検証してから
live master に入れる。黒い背景を含む現時点の提案を完成品と扱わない。
再現・不具合確認の入口は `preview.py` と `comparison.png`。
