# ProjectS v2

Minecraft Java Edition の上で動く、オンラインの Action MMO です。プレイヤーはバニラの Minecraft 26.3 でサーバーに入るだけで遊べます。見た目はサーバーが配るリソースパックで出します。

**目標：2026年11月中旬に、3〜5人で小さなテストプレイをする。** 新しいアイデアは「テストに要るか？」で振り分けています。

## どんなゲームか

基本の一周：

`港町 → 遠征 → 戦闘・採取 → ボス → 素材 → 市場・製造・強化 → もっと難しい遠征`

戦闘だけのゲームにはしません。採取・製造・魔術も、それぞれ自分の目標とお金の使い道を持つ「道」です。各自が自分の得のために動いた結果、ほかの人の目標が生まれる。そういう「エゴイストの連合」を目指しています。

設計の軸の全文は [現在の基準書](docs/decisions/2026-10-14-current-state.md) にあります。

## 今入っているもの（2026-10-17）

| 分野 | 中身 |
|---|---|
| 戦闘 | 7職。戦士はクールダウン管理型に作り直し済み（CC 9種、ボスの崩しゲージ） |
| ボス | 黒翳竜（技6つ・待機・死亡のアニメ）。今は開発用の `/bosstest` で出す |
| 遠征 | 遠征マップ（ゾーンの実データ、出発の判定、踏破記録の保存） |
| 魔術 | 相の研究、魔術書UI、研究と経済のつながり |
| 経済 | 市場（出品・買い注文・オフライン売買）、精製・製造の職業Lv、強化+30、オーブ・MOD |
| 見た目 | 戦闘HUD、職業ごとの初期武器、魔術書・工房のUI |

まだ足りないもの・次の優先順位は、[基準書](docs/decisions/2026-10-14-current-state.md) の3〜4章を見てください。

## 動かし方

### 必要なもの

- Git
- JDK 25
- Python 3 と Pillow・numpy（リソースパックをビルド時に生成するため）：`pip install -r scripts/requirements.txt`
- Minecraft Java Edition 26.3（遊ぶ側）

### ビルドとテスト

```bash
./gradlew build
```

Windows の PowerShell では `./gradlew.bat build` です。

Linux など `python` コマンドが無い環境では、Python の場所を Gradle に教えます。`~/.gradle/gradle.properties` に1行足してください。

```properties
pythonExe=python3
```

### サーバーを起動して遊ぶ

```bash
./gradlew :server-minestom:run
```

Minecraft 26.3 で `localhost` に接続します。リソースパックの確認が出たら受け入れてください。

`run` で起動すると、開発用のコマンドも使えます。

| コマンド | 内容 |
|---|---|
| `/bosstest` | 黒翳竜と戦う。戦いの中で `/bosstest rage` を打つと、すぐ怒った状態にできる |
| `/researchtest` | 研究素材とインクを補充する |
| `/grimoire` | 魔術書を開く |
| `/colony` | 最初の魔術（コロニー・相研究） |

## まず読む文書

- [`docs/decisions/2026-10-14-current-state.md`](docs/decisions/2026-10-14-current-state.md)：**今の基準書**（構成・設計の軸・次の優先順位）
- [`docs/decisions/2026-09-19-combat-build-direction.md`](docs/decisions/2026-09-19-combat-build-direction.md)：戦闘・職業・ビルドの決定記録
- [`docs/development/onboarding.md`](docs/development/onboarding.md)：clone から開発を始めるまで
- [`docs/development/creator-workflow.md`](docs/development/creator-workflow.md)：ブランチの作り方から main への統合まで
- `docs/development/*.md`：機能ごとの設計メモと再開メモ（例：[職業CD](docs/development/class-cd-rework.md)、[遠征マップ](docs/development/expedition-map-v0.md)、[黒翳竜](docs/development/shade-wyrm-boss.md)、[魔術書](docs/development/grimoire-ui.md)）
- `docs/archive/`：終わった計画と、当時の制作メモ

## 構成

| 場所 | 役割 |
|---|---|
| `server-minestom/` | ゲーム本体（Kotlin / Minestom）。入口は `ProjectSServer.kt` → `coreloop/CoreLoopServer` |
| `web-ui-lab/` | 名前は lab だが、サーバーが使うUI部品（工房など）を含む |
| `scripts/` | アセット生成と検証のスクリプト（Python） |
| `assets/` | 元画像・参考資料・見本 |
| `docs/` | 基準書・設計メモ・研究 |

## 開発の約束

- 進め方は [`AGENTS.md`](AGENTS.md) と [creator-workflow.md](docs/development/creator-workflow.md) に従う。作業は `play/<creator>/<slug>` のブランチで行い、main へは PR で入れる。
- **Framework を Feature より先に作らない。** まず遊べる機能を1つ作り、本当に必要になった共通部分だけを後から抜き出す。
- 生成されたアセットはコミットしない。元データと生成スクリプトをコミットし、ビルド時に生成する。
- サーバーがダメージ・命中・報酬・成長を最終判定する。
