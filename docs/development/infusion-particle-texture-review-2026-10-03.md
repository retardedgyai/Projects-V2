# 魔術祭壇の小粒テクスチャ調査 — 2026-10-03

状態: 読み取り調査と、参照実見後の小粒画像2案の保存点。TC6/TC7の具体的な祭壇演出を参照するという本人の追加指示を受け、研究前の作画は保留した。TC6の指定静止画を実見してから、独立folderだけに小試作を作成した。実環境への適用はしていない。

本人の指摘は「パーティクルがダサい」「核の派手なのはいい」「出てくるパーティクルの…テクスチャっていうのかな、がダサい」。量・方向・リズムへの指摘と読み替えない。主核の派手さを削ることを目的にしない。

## 現行の描画経路

稼働パック SHA1: `a5c09be291cc6ceb7d64002645c88ece757ee1a2`。パック読込成功済みのネイティブ Vanilla 26.2 クライアントである。パック読込失敗時の DUST フォールバックと、常時 DUST を使う素材吸収軌跡を区別する。

| 場面 | 使用画像・描画 | 根拠 |
|---|---|---|
| Jar口から核の移動する吸入口へ運ぶ元素 | `projects:infusion/smoke`、CENTER billboard ItemDisplay | `WorldInfusionSmoke.Sample.model` の既定値、`sample()`、`WorldInfusionServer.tick()` の Jar drain 分岐 |
| 核の周囲を回る小粒 | 同じ `infusion/smoke` | `WorldInfusionRadiance.Track.samples()` の `radiance:orbit` がモデルを指定せず既定値を使用 |
| 核から中央の装備へ降りる小粒 | 同じ `infusion/smoke` | 同関数の `radiance:flow` が同じ既定値を使用 |
| 集束・完成時の装備周囲の小火花・台座から上昇する小粒 | 同じ `infusion/smoke` | `WorldInfusionConfluence.Track.finishSamples()` の `convergence` / `weapon-success` / `pedestal-rise` |
| 外周の素材が吸収される短い軌跡 | Vanilla `Particle.DUST`、scale `.55` | `WorldInfusionServer.tick()` の `trails.forEach` はパック読込状態に関係なく ParticlePacket を送る |
| 主核の大きなhalo、下降する柱、受け皿のwave、完成flash | `infusion-radiance/halo`, `/column`, `/wave`, `/burst` | `WorldInfusionRadiance.Track.samples()` が個別モデルを明示 |

## 粒そのものの観察

`infusion/smoke.png` は16×16 RGBAの静止画像。SHA256は `4902aa3ea76b4ca30b45f7a8f879ac29f4429bedebd0dd12658d39f5593fed53`。不透明画素は0、最大alphaは180、alpha値は0を含め24種類、256画素のうち197画素にalphaがある。明るい灰色5段階を使う。

生成元 `scripts/build_world_infusion_smoke.cjs` は3つの Gaussian puff の最大値と正弦/余弦の濃淡を合成している。画像の拡大確認でも輪郭を持つ固有形ではなく、淡い雲状の塊に見える。モデルは奥行き0の両面板で、全面UV 0..16、色は `custom_model_data` による元素色/紫のtint。flipbook・画像の時間変化はない。位置とscaleの変化を、画像のアニメーションと混同しない。

Jarの1単位の受領は6つのtuft、それぞれ3つのlobeを生成する。この数や軌道、更新周期、最大64サンプルは今回変更していない。核周囲 `.32/.48`、下降流 `.38` などの小粒にも煙と同じ画像が使われるため、「小粒の見た目」を直す際に単に全体の煙画像を差し替えると複数役割へ同時に波及する。

Vanilla 26.2の実JAR内 `assets/minecraft/particles/dust.json` は `minecraft:generic_7` から `generic_0` を参照し、実画像は `assets/minecraft/textures/particle/generic_*.png`。確認した先頭2枚は8×8で、白い斑点が複数ある輪郭の硬い画像。独自煙画像とは別の指摘候補である。

## 実画面と未確定事項

入力せず専用クライアントの所有ウィンドウを4回撮影したところ、いずれもインベントリ表示中だった。背景の祭壇や発生中の粒を確認できず、本人が「ダサい」と感じた瞬間の粒を画像から特定できていない。現在の技術的な候補は独自煙画像と素材軌跡のVanilla DUSTの2系統であり、前者への決め打ちはしない。

主核のhalo等は本人が好意的に評価している範囲として保護する。ただし下降柱や完成flashのどの部分まで含むかは発言だけでは断定できない。粒の形・絵柄・質感と、発生→核への集束→完成の役割を、親側で調査中のTC6/TC7一次映像に対応させてから試作する。版・公開状態の確認も親側調査に委ねる。

## TC6静止画との照合と小試作

親側調査から指定された [TC6 1.12.2チュートリアル54:24の静止画](https://jaguidesminecraft.com/wp-content/uploads/2026/07/Thaumcraft-6-Full-Tutorial-1.12.2_at_54-24.png.webp) をローカルに取得し実見した。台座付近の青い斑点には小さな明るい芯と青/シアンの不均一な淡い縁があり、点ごとに見かけの大きさと濃さが異なる。ただしこの1枚から吸入中・残留・構築等のどの粒かは判別できない。実sprite atlas、発生/集束/完成の時系列、TC7の完成サイクルは未確認である。画像素片を流用せず、芯と縁の明度/alpha関係だけを小試作の根拠にした。

`assets/model-lab/infusion-particle-texture-review` に2案を保存した。A `a-core-mote` は明るい小芯を中心にした不均一な外縁、B `b-lobed-mist` は芯を持つ複数の塊で煙のまとまりを残す。いずれも明示的な16×16ピクセル配置と段階alphaで制作し、Gaussian/noise生成やImageGenは使っていない。TC6の正確なsprite再現と称さない。Bの向きは粒の絵柄内だけの非対称性であり、移動方向を提案していない。

比較PNG `current-vs-texture-candidates.png` は現行・A・Bを8/16/24/48pxと7倍の細部表示で並べ、暗い石背景、昼の灰色背景、元のEMBER/TIDE/GALEと紫tintを共通条件で確認した。明るい芯の最大alphaは現行180に対し候補252。候補は透明余白が広いため見かけの面積とalpha総量も異なり、質感のみの案としてもゲーム内での見え方は未検証である。少量化や主核の派手さの削減を意図したものではなく、実適用前に投影サイズと他役割への波及を確認する必要がある。

候補ごとにPNGと同一billboard geometry/UV/tint定義のmodel/item JSONを独立した `infusion-texture-review/*` 識別子で保存した。稼働packのindex/atlasへ登録しておらず、serverから参照していない。モデル面の範囲とtint定義は元画像のものと一致することを生成時にassertし、元画像のhash不変も検証した。再生成は `scripts/build-infusion-particle-texture-candidates.py`。

## 次の検証範囲

候補は別フォルダーだけで比較し、本人が指摘した場面と粒を照合して対象役割を決める。特に共有 `infusion/smoke` の上書きは複数役割へ波及するので、画像の分離が必要か先に検討する。ピクセル調を維持し、画像生成で代用しない。主核の派手さ、数・方向・時間、消費確定に同期した発生、表示数予算を変える案は今回の指摘から導かない。プレイ中の適用許可はない。

プレイ中のserver/client再起動、live pack上書き、入力操作、元PNG・モデル・save・認証・通信制限・既存表示修正の変更は一切行っていない。元の作業ツリーには既存の `scripts/run-native-infusion-emission.ps1` の未commit変更があり、本調査は別checkout・別branchで保存した。

調査画像・抽出参照・機械可読manifestはローカル `magic-particle-candidates` に保存した。比較シートは画像素片の検査であり、ゲーム内レンダーの証拠として扱わない。Vanilla画像の再配布や新パックへの取り込みはしていない。
