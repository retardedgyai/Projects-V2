# 強化画面 v3（Playground）

港の鍛冶屋で開く強化画面を、Creator が承認したデザイン案 v3（`assets/ui/forge-v3/Forge3.dc.html`）と同じ見た目で Vanilla に出す。

## 仕組み

- **動かない部分**は v3 の HTML を headless Chrome で描いた `assets/ui/forge-v3/chrome.png`（背景・パネル・固定ラベル）。256px タイルに分けて `v3_chrome/*` スプライトとして並べる。
- **動く部分**（選択行、素材行、ボタン、スイッチ、数値、MOD、段階表示）は `ForgeV3Scene` が HTML と同じ 1920×1080 の座標で組み立てる。座標は `assets/ui/forge-v3/measure.json`（Chrome で測った全要素の箱）から取った。
- **角丸**は panel の十字＋白い四分円スプライト（`v3_round_*` / `v3_ring10/14_*`）を色付けして作る。オーラ・輪・閃光も白いスプライト（`v3_glow`, `v3_ring`, `v3_sword_glow_*`）に色と透明度を付ける。`UiRenderer` は style の `sprite-color` と `opacity` を見る。
- **文字**は M PLUS 2（400/500/700）と DotGothic16（数字）。Vanilla はフォントにミップマップを使わないので、HTML が使う各サイズ（11〜84px）ごとにちょうどのサイズで描いたページ（`v3r13` など）を持つ。各グリフの右端に透明度1の点を置き、Vanilla の字送りをブラウザの字送りに合わせている。
- 表示倍率は `ForgeV3Scene.ZOOM`。1080p で HTML の 1px が画面の 1px になる。

## 作り直し

```powershell
python scripts/build_forge_v3_assets.py fonts --source <MPLUS2.ttf と DotGothic16-Regular.ttf のフォルダ>
python scripts/build_forge_v3_assets.py render   # Chrome が必要。chrome.png と measure.json を更新
python scripts/build_forge_v3_assets.py pack     # pack.zip・font-map.json・フォント幅表を更新
```

フォントは Google Fonts（OFL）。ライセンスは `web-ui-lab/ui/forge-v3-fonts/` とパック内 `licenses/` にある。

## HTML と違うところ

- キー操作の表示は Minecraft に合わせて「左クリック 選択 / Shift 閉じる」。Enter キーの表示は出さない。
- 破損の危険がある強化だけ、v3 と同じ見た目の確認画面を出す。
- 精錬・製作などのタブは見た目だけ。
