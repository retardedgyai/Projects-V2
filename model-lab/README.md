# ProjectS model laboratory — Scorpius integration

## 今回使えるようになったもの

Scorpius の AI 向けボス生成スクリプトを、ProjectS の Java 25 / Kotlin / Minestom `2026.08.16-26.2` で使う独立した制作環境へ取り込んだ。**本編のボス・戦闘・保存データ・既存リソースパックは変更していない。** Minecraft クライアントの変更は不要。

| ボス | 本体アニメーション数 | 付属物 |
|---|---:|---|
| Osirion / 不滅の王 | 5 | 本体 |
| Radix / 母樹 | 9 | 根の槍、腐果、予兆円、胞子床 |
| Vesper / 鐘の番人 | 27 | 鐘（ring 1本）、予兆4種 |
| Piglin Lord / 黄金卿 | 22 | 予兆円、扇形 |

合計15モデル、64アニメーション。友人のボスAI・戦利品・経済を本編へ追加したわけではない。モデルとアニメーションの制作経路を取り込んでいる。

## 通常の制作手順

repo root から実行する。Java 25、既存の Gradle 環境を使用。初回のみ WSEE 等の依存取得にネットワークが必要。

```powershell
# 生成。最初は全種、その後の形状変更は --boss vesper などに絞れる。
python scripts/boss_models.py generate --boss all

# .bbmodel の構造検証（ゲーム不要）
python scripts/boss_models.py validate --source model-lab/build/authored-models

# 実モデルを固定カメラ・同一縮尺でGIF化（Minecraftの映像ではない）
python scripts/boss_models.py preview --boss vesper --animation sweep

# 生成し直したモデルをパック化し、実際のエンジンで動作検証
.\gradlew.bat :model-lab:modelSmoke -PmodelSource=model-lab/build/authored-models --no-daemon "-Pkotlin.compiler.execution.strategy=in-process"

# 元の取り込み済みモデルでのビルド／テスト
.\gradlew.bat :model-lab:test :model-lab:modelSmoke :model-lab:compileLoadtestKotlin --no-daemon "-Pkotlin.compiler.execution.strategy=in-process"
python -m unittest discover -s scripts -p test_boss_models.py
python -m unittest discover -s scripts -p test_pack_components.py
```

`preview` の既定は取り込み済みモデル。再生成版を見る場合は同様に `--source model-lab/build/authored-models` を付ける。CPUプレビューは線形の位置・回転に対応し、未対応の補間／scale は明示的に拒否する。クライアントの補間・透過・照明を保証するものではない。

### 人間がゲームで確認するときだけ

```powershell
.\gradlew.bat :model-lab:run --no-daemon "-Pkotlin.compiler.execution.strategy=in-process"
```

- 接続先は **127.0.0.1:25566**。本編25565とは別。外部公開しない。
- パックは localhost:18086。初回に受け入れる。これはローカル確認専用で、別PCからの接続用ではない。
- `/modelmenu`：ボス選択→アニメーション選択。
- `/model vesper`、`/model piglin_lord` など：モデルを自分の6ブロック先（ワールド+Z）へ配置。
- `/anim sweep`：1回再生。`/loop walk`：ループ。`/model` が使用可能な名前を表示。
- `/bone crown false`：黄金卿の冠を非表示。`true`で復元。
- `/modelclear`：自分が配置したモデルを片づける。
- `/modelstats`：tick平均/p95/最大、Entity数、heap。処理時間であり描画FPSではない。
- GUIは確認用の標準チェスト表示。ProjectS本編のアート方向を置き換えない。

パック・mappings・geometry・animations は同一bundleのハッシュで検証する。モデル集合変更時にはIDが変わりうるため、**ライブでmappingsだけを差し替えない**。モデル変更後は再ビルドし、この確認用サーバーを再起動する。

## 負荷テスト（既定無効）

確認用サーバーを別途起動してから、明示的に以下を実行する。

```powershell
.\gradlew.bat :model-lab:loadTestBots -PmodelLabBots=4 -PmodelLabSeconds=30
```

最大32体、最大300秒、localhost:25566固定。本編や第三者サーバーへ向けない。1体ごとにVesperを配置する。ボットはパック適用の応答を模擬するだけで、ダウンロードや描画はしない。**接続成功を見た目の検証と報告してはいけない。** ボット依存はserver runtimeへ混入しない。

## その他の移植

- `LabMenu`：Scorpiusの宣言的GUI・セッション所有・古い画面を無効化する考え方を移植。確認用メニューで使用。アイテム投入型クラフト画面の原実装は `vendor/scorpius/reference/` に保持し、本編未接続。
- `TickMetrics`：固定件数の履歴とスナップショット。公開管理HTTPや個人情報収集は追加していない。
- `scripts/preview_pack_components.py`：実Component JSON配列とProjectSのbitmapフォントから文字を描き、欠けたグリフを検出。HUD専用シェーダーを移植したわけではない。Minecraft内スクリーンショットの代用にはしない。
- マップ俯瞰、建築プレビュー、再起動ドレイン、追従モデル、ピッチ補正、ギミック戦闘テストは元コードを参照用に保存。Scorpius固有状態に依存するので、そのまま本編で実行しない。

## 主なファイル／壊れたとき

1. 造形・アニメーション：`vendor/scorpius/bbmodel/gen_*.py` → `scripts/boss_models.py validate`。
2. 紫黒・間違ったモデル：`BuildBossPack.kt` の参照検証 → `ModelBundle.kt` の世代整合性 → パック適用確認。
3. 動かない・部位が残る：`BossModelActor.kt` → `modelSmoke`。smokeはソケットを開かず全アニメーションをtickし、Displayの変化とEntityの解放を確認する。
4. コマンド／確認用GUI：`ModelLab.kt` と `LabMenu.kt`。

モデル名・アニメーション名は実bbmodelから `WseeAssets` を生成し、Kotlinから参照する。モデルファイルの名前変更と呼び出し側の不一致はコンパイル時に検出できる。

## 境界

このモジュールは通常の `:server-minestom:run` では起動されない。将来のボスに `BossModelActor` を接続するときも、Damage/Hit/Rewardは既存のサーバー処理を維持し、ここには見た目だけを任せる。WSEE依存と既存RPの統合はShared Coreレビュー対象。本編マージは別の受け入れ作業。

出典・取り込み範囲は `vendor/scorpius/NOTICE.md`。`.tools/`、`.kotlin/`、`model-lab/build/` はcommit対象外。
