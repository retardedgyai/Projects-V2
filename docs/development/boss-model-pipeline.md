# Blockbenchボスの制作パイプライン

決定D12の実装。最初の1体は [黒翳竜](shade-wyrm-boss.md)。2体目以降もこの手順で作る。

```text
assets/bosses/<id>.bbmodel        ← 正本（コミットする。Blockbenchで直接編集してよい）
   │  ./gradlew :server-minestom:generateBossModels   （processResourcesの前に自動で走る）
   ├─ core-ui-pack/assets/worldseed/**                 ← 骨ごとのitem model・テクスチャ（生成物、git無視）
   └─ src/main/resources/boss-models/**                ← サーバー用のgeo/animation/mappings（生成物、git無視）
サーバー：BossModelLibrary が読み込み → BossModelView が不可視の体（ModelCarrier）に追従して再生
```

## 方式：WSEE（WorldSeed Entity Engine）

- 入手元：作者の公式Maven `https://reposilite.atlasengine.ca/public`（`net.worldseed.multipart` だけをこのrepoから取るよう `exclusiveContent` で限定）。ソースは GitHub `AtlasEngineCa/WorldSeedEntityEngine`。
- ライセンス：Apache-2.0。版：13.0.4（2026-08-28公開、Minestom `2026.07.22-26.2` 向け）。こちらの `2026.08.16-26.2` で、骨の生成・アニメ再生・破棄まで自動テストが通ることを確認済み。
- 旧版にあった `--add-opens` は不要（13.x の Molang は依存なしの自前実装）。
- 自前実装（ItemDisplay＋補間）より採用した理由：D12でCreatorがWSEEを第一候補に決めている。Blockbenchの骨・回転・補間・Molang・テクスチャ状態（被弾の赤）をそのまま使え、2体目以降の作業が「bbmodelを置く＋技の定義」だけになる。

### パックの方針に合わせた変更点

WSEEは本来、運び役アイテム（magma_cream）の **バニラのitem定義を上書き** する。ProjectSのパックはバニラ上書きを禁止しているので、
生成時にその定義を `assets/worldseed/items/boss_bones.json` へ移し、サーバーが骨のアイテムに `item_model=worldseed:boss_bones` を付ける（`BossModelPack` / `BossModelLibrary`）。バニラのマグマクリームは変わらない。

## 約束事（bbmodel側）

`python scripts/boss_models.py validate` が確認する。

- 形式は Bedrock Model。前は **+Z**、16px＝1ブロック、地面は y=0。テクスチャはbbmodelに埋め込みのPNG。
- 骨の名前は一意。`head` と、`hitbox`/`seat`/`nametag` を含む名前は使わない（WSEEが特別扱いする。当たり判定はサーバーが持つ）。
- アニメ名がサーバーとの契約：
  - `idle`・`walk`（loop）。怒りのあるボスは `idle_enraged`・`walk_enraged`・`enrage`（once）。`death`（once、最後の姿勢で止まる）。
  - 怒った後も使う技と `death` は、怒りの構えで作った `<名前>_enraged` を置くと、怒り中はそちらが再生される（無ければ通常版）。
  - **技1つにつき、技idと同名の once アニメ**。長さ＝予兆＋硬直（ms/1000）、当たる瞬間の姿勢＝予兆の時刻。サーバーは技開始時に再生するだけなので、ここがずれると見た目と当たり判定がずれる（`ShadeWyrmBossTest` が長さを検査）。
  - 怒り後だけ見せる骨（角など）は `QuestMobDefinition.enragedBones` に名前を書くと、最初は非表示になる。
  - 怒ると消える骨（畳んだ翼など）は `foldedBones` に書くと、`enrage` の途中でその骨と下の骨がまとめて非表示になる。`enrage` が拡大縮小キーで角・扇を0から生やし畳んだ翼を縮めるので、表示の切り替え時刻（`BossModelView` の `ENRAGE_REVEAL_MILLIS`・`ENRAGE_FOLD_MILLIS`）はクリップに合わせる。
- 回転の向き（WSEE実測、`ShadeWyrmBossTest` で検査）：scripts が書く 4.x 形式では、X回転の正は前（+Z側）を **上げる**。WSEEはBlockbenchのXを鏡映するので、`_l` の骨はゲーム内では右側に出る（左右対称なら気にしなくてよい）。Blockbench 5で保存すると 5.0 形式になり、WSEEが自動で変換する（この経路はまだ実機未確認）。

## 2体目の作り方

1. **モデル**：`scripts/build_boss_<id>.py` を `build_boss_shade_wyrm.py` を写して作る（`boss_models.Model` の cube/bone/anim を使う）か、Blockbenchで直接作って `assets/bosses/<id>.bbmodel` に保存する。
2. `python scripts/boss_models.py validate` と `python scripts/boss_models.py preview <id>`（`build/boss-previews/<id>.png`、静止姿勢の確認用でMinecraftの画面ではない）。
3. **内容**：`QuestMobArchetype` に1行、`QuestMobContent.definition` に技（4〜6個、`health=` で段階）と `.copy(model = "<id>", enrageHealthRatio=…, enragedBones=…)`。運び役のEntityTypeは体格の近いバニラMob（当たり判定・経路探索・音に使う）。
4. **アリーナ**：`BossArenaFactory` に bossId を1つ足す（床・時間・明かり）。
5. **テスト**：`ShadeWyrmBossTest` を写し、アニメ長さの契約・回転向き・怒り/死亡の流れを確認。`./gradlew :server-minestom:test`。
6. **確認**：`/bosstest` の bossId を差し替えるか引数化して実機で見る（今は黒翳竜固定）。
7. 本編の入口・報酬は保存形式に関わるので、Creatorと決めてから。

## ゲーム内での確認

1. `./gradlew :server-minestom:run`（`projects.devCommands=true` が付く）。
2. Vanilla 26.2 で `localhost` に接続し、リソースパックを **受け入れる**。
3. 港で `/bosstest` → 「翳の巣」へ移動し、黒翳竜が出る。手帳 [9] で職業・技・条件・ボスの再配置、`/hub` で帰還。
4. 見るところ：歩き/待機の切替、各技の予兆と動きの一致、HP50%の咆哮（翼が開き角が生える）、被弾時の赤、死亡アニメ。

## 壊れた時に最初に見る場所

- 起動ログ `BOSS_MODELS_MISSING` → 生成が走っていない（`generateBossModels`）。この時ボスは素のRavagerで出る。
- 体が後ろ向き → `BossModelView.modelYaw`。
- アニメが変 → bbmodelのアニメ名・長さ、`scripts/build_boss_*.py` の `PITCH/YAW/ROLL`。
- 骨が紫黒の四角／マグマクリーム → パック未適用、または `assets/worldseed/items/boss_bones.json` が index.txt に無い。
