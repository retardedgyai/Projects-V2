# ProjectS v2

ProjectS v2 は、旧ProjectSを引き継ぎつつコードはゼロから作り直す、Minecraft Java基盤のオンラインAction MMOです。

このrepoをv2の新しい基準書・実装場所として扱います。旧repoは資料/歴史/失敗例/テスト観点として残し、新アーキテクチャへそのまま移植しません。

## 現在のゲーム方向

基本ループ:

`港町 → 遠征（クエストマップ） → 戦闘/採取 → Boss → 素材 → 市場/製造/強化 → より難しい遠征`

戦闘だけでなく、採取・製造・魔術がそれぞれ自分の目標とお金の使い道を持つ「道」になり、プレイヤー同士が市場でお互いの目標を作り合うことを目指します（詳細は2026-10の基準書）。

## 技術方向

- Minecraft Java Edition 26.2 / Java 25 / Kotlin-first / Gradle Kotlin DSL。
- Server: Minestom 26.2系（`server-minestom/`）。入口は `CoreLoopServer`。
- Client: Vanilla Minecraft 26.2 + Serverが配布するリソースパック。専用Fabric Clientとprotocolは2026-10に廃止。
- ServerがDamage/Hit/Reward/Progressionを最終判定。
- VFXはコード/データ/AIで制作し、ServerからParticle/Display Entity/リソースパックで描画する。
- `web-ui-lab/` は名前に反してServerが使うUI部品（工房v3等）を含む。

## まず読む文書

- [`docs/decisions/2026-10-14-current-state.md`](docs/decisions/2026-10-14-current-state.md) — **今の基準書**（構成・設計の軸・次の優先順位）。
- [`docs/decisions/2026-09-19-combat-build-direction.md`](docs/decisions/2026-09-19-combat-build-direction.md) — 戦闘・職業・ビルドの決定記録。
- [`docs/development/onboarding.md`](docs/development/onboarding.md) — cloneから開始まで。
- [`docs/development/creator-workflow.md`](docs/development/creator-workflow.md) — branchの作り方からmainへの統合まで。
- [`docs/game/combat-principles.md`](docs/game/combat-principles.md) — 一人称、通常攻撃、攻撃判定、Mob攻撃、回避等。
- [`docs/game/mana-and-class-resources.md`](docs/game/mana-and-class-resources.md) — マナ/Cooldown/固有ゲージ。
- [`docs/architecture/hunt-session-lifecycle.md`](docs/architecture/hunt-session-lifecycle.md) — 討伐空間の寿命。
- [`docs/research/minestom-case-studies.md`](docs/research/minestom-case-studies.md) — Minestom事例から持ってくる設計。
- [`docs/future/gvg-principles.md`](docs/future/gvg-principles.md) — 将来のAlbion型大規模GvG方向。
- `docs/archive/` — 終わった計画・日付付きの制作メモ（当時の記録）。

## 最重要原則

**FrameworkをFeatureより先に作らない。**

まず実際に遊べる一つの機能を作り、ゲームが本当に必要とした共通部分だけを後から抽出します。

## 開発起動

- Server: `./gradlew :server-minestom:run`
- Client: Vanilla Minecraft 26.2で `localhost` に接続
- Test / Build: `./gradlew build`
