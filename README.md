# ProjectS v2

ProjectS v2 は、旧ProjectSを引き継ぎつつコードはゼロから作り直す、Minecraft Java基盤のオンラインAction MMOです。

このrepoをv2の新しい基準書・実装場所として扱います。旧repoは資料/歴史/失敗例/テスト観点として残し、新アーキテクチャへそのまま移植しません。

## 現在のゲーム方向

基本ループ:

`港町 → クエスト選択 → 1〜4人専用討伐空間 → Boss → 個人素材 → Craft/強化 → より難しい討伐`

巨大オープンワールドは、この一周が面白く安定してから後続で検討します。

## 技術方向

- Minecraft Java Edition 26.2。
- Java 25。
- Kotlin-first。
- Gradle Kotlin DSL。
- Server: Minestom 26.2系。
- Client: Fabric 26.2を内部基盤とした専用ProjectS Client。
- ServerがDamage/Hit/Reward/Progressionを最終判定。
- 人間向けEditorはMob Editorのみ。
- Skill/VFX/Studio/World Builder/Balance Editorはv2では作らない。
- VFXはコード/データ/AIで制作し、Clientで描画する。
- 統合版対応は初期Scopeに入れない。

## 最初の完成単位

`起動 → 港町 → 1クエスト受注 → 1討伐Map → 1Boss撃破 → 1素材入手 → 1武器Craft/強化 → 再挑戦、永続保存あり`

ただしその前に、一日のCombat Spikeで「戦闘そのものが本当に面白いか」を確認します。

## まず読む文書

- [`docs/decisions/2026-08-19-current-decisions.md`](docs/decisions/2026-08-19-current-decisions.md) — **現在決まっていること全部の基準書**。
- [`docs/game/combat-principles.md`](docs/game/combat-principles.md) — 一人称、通常攻撃、攻撃判定、Mob攻撃、回避等。
- [`docs/game/mana-and-class-resources.md`](docs/game/mana-and-class-resources.md) — マナ/Cooldown/固有ゲージ。
- [`docs/development/environment.md`](docs/development/environment.md) — Minecraft/Java/Minestom/Fabric/Gradle環境。
- [`docs/architecture/hunt-session-lifecycle.md`](docs/architecture/hunt-session-lifecycle.md) — 討伐空間の寿命。
- [`docs/research/minestom-case-studies.md`](docs/research/minestom-case-studies.md) — Minestom事例から持ってくる設計。
- [`docs/research/ai-boss-model-pipeline.md`](docs/research/ai-boss-model-pipeline.md) — AI大型Mob制作の研究方針。
- [`docs/future/gvg-principles.md`](docs/future/gvg-principles.md) — 将来のAlbion型大規模GvG方向。
- [`docs/development/day-1-combat-spike.md`](docs/development/day-1-combat-spike.md) — 初日の戦闘実験。
- [`docs/development/rewrite-rules.md`](docs/development/rewrite-rules.md) — リライト規則。
- [`legacy/README.md`](legacy/README.md) — 旧ProjectSの扱い。

## 最重要原則

**FrameworkをFeatureより先に作らない。**

まず実際に遊べる一つの機能を作り、ゲームが本当に必要とした共通部分だけを後から抽出します。

## 開発起動

- Server: `./gradlew :server-minestom:run`
- Client: `./gradlew :client-fabric:runClient`
- Test / Build: `./gradlew build`
