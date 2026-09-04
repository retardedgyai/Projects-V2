# 寄り道と専用試練 v0

Vanilla Minecraft 26.2 + Minestom。既存マップ地形・クライアント・プロトコル・永続データ形式は、このモジュールでは変更しない。

## 亀裂と祭儀

| イベント | 開始と進行 | 専用報酬 | 失敗 |
| --- | --- | --- | --- |
| 裂界の亀裂 `RIFT` | 広場の亀裂を右クリック→3体撃破→次の亀裂を追跡→再び起動。3地点目は精鋭入り4体。紫の光と座標が次の地点を示す。 | 最後の波を倒して3個確定。 | 各波90秒、追跡120秒。戦闘地域から5秒離れる、死亡、マップ離脱。 |
| 血誓の祭儀 `RITUAL` | 祭壇を右クリック、半径10の祭域で3波。各波の後は祭壇を右クリックで続行、しゃがみ右クリックで確定。 | 第1波で1個、第2波で3個、第3波で6個。続行後の失敗は未確定分を失う。 | 各波90秒、判断120秒。祭域外に5秒、死亡、マップ離脱。 |

操作対象は幅2.5・高さ2.8の専用 `INTERACTION` エンティティ。起動判定はサーバー上で対象UUID、同じinstance、4.5ブロック以内、有効な探索者を確認する。単なる空中右クリックでは起動しない。持ち込みスキルの操作と区別するため、ルート側は `PlayerEntityInteract` の実対象を渡す。

途中の敵も通常の `QuestEncounterCombat` に所属し、既存のサーバー確定攻撃・スキルで倒せる。波の敵UUIDだけを追跡して殲滅判定する。失敗・終了・disposeは生存敵を無報酬で削除し、時間経過後に波や報酬が復活しない。達成表示は地図を出るまで残し、マップdisposeでマーカーをすべて除く。

`AdventureProgress` は完了状態へ遷移してから一度だけ報酬通知する。経済への接続は `AdventureReward(kind, sourceId, rewardCount)`。rootは各個数を `sourceId:reward:N` で台帳へ送る。コントローラー自体は通貨を増やさない。専用通貨・断片・試練入場コストは経済モジュールの契約に従う。

## 専用ボス3種

| 入口ID | アリーナ | ボス／T1 HP／弱点 | 固有の戦い方 |
| --- | --- | --- | --- |
| `rift` | 灰燼の溶炉：黒い十字の赤煉瓦床 | 灰燼の王／460／氷 | 追尾して固定する火印と斧薙ぎ。HP50%以下から長さ13の十字熔断を解禁。斜めに空いた場所へ逃げる。 |
| `ritual` | 氷獄の円庭：半径5・16を示す氷輪 | 氷獄の巨兵／510／炎 | 半径5以内が安全な外周凍結と、半径5以内が危険な中心砕氷を交互に使用。内側へ走る／外側へ離れる判断を切り替える。 |
| `trial` | 天雷の祭壇：光を埋め込んだプリズマリン床 | 嵐の司祭／420／雷 | 雷の直線・足元刻印。HP66%と33%で盾兵と術師を召喚し、本体を障壁で守る。護衛2体を倒すと再開。HP33%以下は十字落雷も使用。 |

全種のHP・攻撃力は `1.65^(Tier-1)` 倍。新3種は専用試練の `explicitBossArchetype` からのみ指定し、既存の通常マップの3ボス抽選は変更しない。

赤い半透明面・方向固定・一度判定・硬直は既存 `MobAbilityManager` と同じ経路。新しい十字形も表示と判定で同じ `MobAttackShape.Cross` を使う。氷の輪は外側16／内側5までの明示判定。召喚障壁は護衛の論理生存を確認し、通常攻撃・持続属性ダメージ両方を遮断する。接触ダメージやVanilla AIの無予兆攻撃は使わない。

## ルート側の接続

- 既存マップ生成後、安全な広場3つを `AdventureSite(RIFT, sourceId, centers)`、別の広場1つを `AdventureSite(RITUAL, sourceId, centers)` へ渡す。
- `AdventureRuntime(instance, combat, sites, canParticipate, onReward)` をmapごとに所有し、tick・実エンティティ右クリック・退場時disposeを呼ぶ。
- `BossArenaFactory.create("rift" / "ritual" / "trial", tier)` は独立instance・playerSpawn・bossSpawn・archetypeを返す。rootの試練sessionが `QuestEncounterCombat(..., explicitBossArchetype = arena.archetype)` を1つ所有する。
- rootは攻撃hookのencounter参照を通常session／試練sessionで切り替える。試練撃破報酬・敗北帰還・断片消費はroot経済/UI側の責務。
- プレイヤーを退場させてcombatをdisposeした後、`BossArena.dispose()` で専用instanceを破棄する。

## 今回読み直した公式Monumentaコード

参照日: 2026-09-05。公式 `TeamMonumenta/monumenta-plugins-public` のmaster解決hashは `34aac4db9c3bcc7dc2a31988e07f47c9ebb29900`。以下を再取得し、実コードを読んだ。Bukkitコードやassetsは取り込まず、設計上の関係を参考に、既存Minestom実装を拡張した。

- [TealSpirit.java](https://github.com/TeamMonumenta/monumenta-plugins-public/blob/34aac4db9c3bcc7dc2a31988e07f47c9ebb29900/plugins/paper/src/main/java/com/playmonumenta/plugins/bosses/bosses/TealSpirit.java): HP閾値に応じた技群の変更・召喚・戦闘終了時の召喚物削除を確認。嵐の司祭では小さな具体的な2段階召喚障壁に落とし込んだ。
- [RingOfFrost.java](https://github.com/TeamMonumenta/monumenta-plugins-public/blob/34aac4db9c3bcc7dc2a31988e07f47c9ebb29900/plugins/paper/src/main/java/com/playmonumenta/plugins/bosses/spells/frostgiant/RingOfFrost.java): 予告中の円周表示、内側の安全域を除いた命中対象、死亡時cancelを確認。氷獄の巨兵では内外を交互に避ける別の技組にした。
- [SpellBaseAoE.java](https://github.com/TeamMonumenta/monumenta-plugins-public/blob/34aac4db9c3bcc7dc2a31988e07f47c9ebb29900/plugins/paper/src/main/java/com/playmonumenta/plugins/bosses/spells/SpellBaseAoE.java): 視線による開始条件、詠唱中の移動停止、発動時のダメージ、キャンセルを確認。本実装は既存のクロック駆動状態機械を維持し、遅延BukkitRunnableを移植していない。

## 検証で最初に見る場所

- イベントの順序・確定・失敗: `AdventureProgress` / `AdventureRuntimeTest`
- マーカーや敵が消えない: `AdventureRuntime.dispose` と `QuestEncounterCombat.removeEncounter`
- 試練の地形・開始位置: `BossArenaFactory` / `BossArenaFactoryTest`
- ボスの数値・新技: `QuestMobContent`
- 司祭の障壁: `QuestEncounterCombat.tickChallengeBarrier`

実画面の読みやすさ・難易度・遊び味はCreatorの手動テスト対象。自動テストは報酬一度性・誤起動防止・殲滅進行・失敗時清掃・安全なアリーナ配置・内外交互攻撃・十字の安全域・2段階召喚障壁を検証する。

2026-09-05: `:server-minestom:test --tests 'dev.projects.server.mob.*' --tests 'dev.projects.server.coreloop.adventure.*' --tests 'dev.projects.server.coreloop.CorePlayerCombatTest'` が成功。全68件・失敗0件。新規イベント7件と専用ボス／arena4件、既存のmob35件とプレイヤー戦闘22件を含む。マーカーの実instanceへの配置、実エンティティの3波、実プレイヤーを用いた障壁解除もテストした。ゲームの手動入力は行っていない。
