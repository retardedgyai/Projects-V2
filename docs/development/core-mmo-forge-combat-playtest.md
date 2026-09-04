# 採取・鍛冶・大剣の接続 v0 — 手動テスト案内

実験branch: `play/gyai/mmo-forge-combat`。基点 `cbee1a2` の既存コアループへ追加。
mainを変更せず、Vanilla 26.2 + Minestom + サーバー配布resource packだけで動く。
`client-fabric/`・`protocol/`・Particle Framework coreには変更を加えない。

## 今回のプレイ体験

採取密集地域を狙って地図MODを選ぶ → 素材を精製 → 装備Tier・強化・消耗品へ使う →
魔物／ボスからオーブと刻印粉を取る → MODで戦い方を作る → 強い地域と専用ボスへ挑む。

| 軸 | 主な成果 | 役割 |
|---|---|---|
| 採取・精製 | 各Tierの板材、インゴット、加工石材、なめし革、布 | 確実に残る素材投資 |
| Tier制作 | T1〜4の基礎性能 | 地域攻略の段階 |
| ＋30強化 | 基礎威力・AS／防具基礎HP | 同じ装備を継続して育てる |
| オーブ加工 | 接頭／接尾MOD・数値 | ランダムなビルド作り |
| 鍛冶熟練 | 精製・制作で上がる成功率 | 抽選に外れても残る確定成長 |

### ＋30強化

- 冒険の手帳 → 工房 → 強化。武器／防具を選択し、費用・成功率・天井・結果予告を見る。
- 失敗しても装備破損・降級・MOD消失なし。素材は消費。4／6／9回の失敗後、次回は確定。
- 素材Tierは目標＋値で共通: ＋1〜8=T1、＋9〜16=T2、＋17〜24=T3、＋25〜30=T4。
  T1で安く強化してT4へ持ち越す抜け道を作らない。Tier更新でも強化・天井・MODを保持する。
- 精錬触媒は追加素材で成功率＋15ポイント。既に100%ならUIでは消費させない。
- 精製／制作1batchで1XP、Tier制作5XP、強化試行1XP。20XPごとに熟練rank、最大10。
- 武器＋1は基礎威力＋4%、AS＋0.8ポイント。防具＋1は基礎HP＋2%。MODのflatHPには掛けない。
- 仕様・確率表・移行検証は [強化仕様](core-enhancement-v0.md)。保存形式v3は完全バックアップ後v4へ。

### 大剣・戦闘表示

- 通常攻撃を横薙ぎ → 返し斬り → 重い縦斬りへ。倍率1 / 1.15 / 1.85。
- 各段に溜めと後隙。連打は残り6tickで次の一撃を1つだけ予約し、現在の攻撃をリセットしない。
- 太い多層の刃・短い残光・火花・打撃音。接触時に演出時計だけを2〜3tick止める。
  プレイヤーの移動やサーバーの時刻は止めない。
- 上下3mの斜面判定。サーバーの見通し判定を通すため、壁・床の向こうには当たらない。
- 踏み込み／地砕き／旋風それぞれに固有形状。縦斬りの扇形衝撃波は実判定の広がりを表す。
- 盾の軽減後・overkillを除いた実減少HPでダメージ数字を表示する。
- 大剣T1〜4に独自の厚い3Dモデル。既存Minecraftの素材textureを使用し、全Vanilla剣を置換しない。
- 32m LOD・一人／同Instanceの粒子予算・演出数上限を付け、帰還時は旧Instance参照まで解放。

### 工房UI

左でカテゴリ／レシピ、中央で対象／結果、右で費用、下で個数／実行を選ぶ構成。
精製・制作・強化・MOD加工を同じ操作規則に揃える。
主操作の「右クリックだけ5個」は廃止し、1／5／最大を明示する。
費用には必要／所持を表示。使えないオーブを主要リストへ大量に並べない。
費用素材から精製等へ辿れ、成功後は同じ対象に戻って反復できる。

工房の金床・赤熱した剣の絵は今回OpenAI内蔵画像生成で作成した原画。
PixelLabではない。PixelLab設定はあるが指定tokenが見つからず、今回もAPI成功実績はない。
独自背景／武器モデルは名前空間を分け、日本語・英字フォントと既存HUD位置は変更しない。

## 設計参考と、まだ追加していないもの

- [Hypixel公式Ravengard紹介](https://hypixel.net/threads/dev-blog-13-a-look-at-ravengard.6023553/):
  resource packを活かしたコンテナUIの視覚的なまとまりを参考にする。PvP／全ロストを導入する意味ではない。
- [Albion公式・精製ガイド](https://albiononline.com/news/guide-refining):
  採取→精製→制作をつなぐ考え方を参考に、必要素材が辿れるUIへ。
- [Lost Ark公式・強化関連アップデート](https://www.playlostark.com/en-gb/game/releases/break-through-to-thaemine?language-picker=true):
  同じ装備への継続投資・強化補助という方向の参考。確率表やバランスの移植ではない。
- MH的な重量感は3段の間合い／後隙／接触演出に反映。公式ゲームの武器・画像素材を移植したものではない。

これだけでMMO全体の経済が完成したとは扱わない。現在は個人台帳の武器1本／防具セットであり、
個体別の装備製作・取引市場・パーティ共有報酬・役割分担レイドは未実装。
次は「パーティ参加と報酬の公平配分」「部位破壊／怯みと専用制作素材」「製作者名付き個体装備」の順が合う。
市場はアイテム個体の所有権と重複防止を用意してから。取引だけ先に作ると経済を維持できない。
消耗品／素材需要は今回作ったが、＋30到達後の継続需要・成長速度・敵HP調整はCreatorの試遊結果で決める。

## 実装と故障時の入口

1. `CoreLoopMenus` → `CoreForgeLayout` → 対象／数量／見積を表示。
2. `CoreLoopGame.mutate` → `CoreAccountService` → `CoreEnhancementCatalog` で費用・試行を再計算。
3. `CoreAccountRepository` の保存成功後に強化／MOD／素材／天井／XPをまとめて公開。
4. `CoreLoopItems` / `CoreWeaponPresentation` が表示、`CorePlayerCombat` が実戦効果を読む。
5. 入力 → `GreatswordCombo` → サーバー命中 → `GreatswordVfx` と実ダメージ数字。

最重要は `CoreEnhancementCatalog`（成長と費用）／`CorePlayerCombat`（実判定）。
消費や消失なら `CoreAccountService` と保存ログ、表示ずれは `CoreWeaponPresentation` と
`CoreForgeLayout`、斬撃／帰還後残留は `GreatswordVfx` から確認。

## 検証・手動確認

自動: 全server tests、配布ZIP、UI asset検証、4モデル／Vanilla26.2実JARのtexture照合、
全Tier×＋0〜30の実戦・tooltip計算一致、盾軽減表示、既存MOD・地図・保存データの回帰。
2026-09-05の統合結果: **56 suites / 451 tests、失敗0・error0・skip0**。
`test + distZip + installDist` 成功。パック143 assets / 195 private glyphs、4モデル検証成功。
初回統合で見つかったパック拒否時のモデル消失は、Vanilla既定ITEM_MODELを復元する処理で修正済み。
配布生成順は `build_core_weapon_assets.py` → `build_core_ui_assets.py`。
`verify_core_weapon_assets.py --vanilla-jar <26.2 client jar>` でモデルを検証できる。

手動: 大剣3段の迫力／テンポ、坂上と坂下への命中、工房の数量選択・素材への動線・連続強化、
実画面のtooltip／文字／アイテムモデルをCreatorが判断する。
Agentは起動と接続ログ／ウィンドウ状態まで準備し、キャラクター移動やメニューのゲーム操作は行わない。
