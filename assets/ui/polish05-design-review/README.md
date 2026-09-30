# Polish05 デザイン比較案

[HTMLを開く](index.html) / [レビュー](../../../docs/development/polish05-design-review.md)

[ゲーム用の共有素材プレビュー](shared-preview/index.html) /
[素材共有の方式と確認範囲](../../../docs/development/polish05-shared-preview.md)

`index.html` と `art/` を一緒に保存してブラウザで開ける。
外部サービスやMinecraftへの接続はない。上の状態切替は比較用の見本設定。

## 元画像

- 防具: `scripts/plate_armor_ui.py` の承認済み戦士用プレート。
  同じモデルを256px、16方向で描画。アイコンは出荷中の64px画像。
- 素材: 出荷中の `core-ui-pack/assets/projects/textures/item/forge_materials/`。
- 剣・背景・鍛冶マーク: `assets/ui/polish05-import/assets/images/`。

Isles等の参照パックの画像・モデルは使用していない。
画像を新しく描き直す目的の変更ではない。

## 再生成・検証

repo rootで実行する。

```text
python assets/ui/polish05-design-review/rebuild_art.py
node assets/ui/polish05-design-review/verify.cjs
```

アート生成には既存スクリプトと同じPython、Pillow、numpyが必要。
検証にはPlaywrightとインストール済みEdgeが必要。
必要なら `NODE_PATH` をバンドル済みNodeの `node_modules` へ設定する。
スクリーンショットは `.tools/polish05-design-review/` に出力される。

HTML中の強化の見本は現行Catalogの熟練度0で計算。
実際の保存・抽選・破損判定は行わない。
