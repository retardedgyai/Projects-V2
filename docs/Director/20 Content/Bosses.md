---
type: content-map
design_status: NEEDS_REVIEW
implementation_status: PLAYTEST
approved_by:
approved_at:
priority: NEXT
last_reviewed: 2026-08-29
---

# Bosses

> [!warning] Director Review待ち
> これはBoss content全体の地図。個別Bossの正式仕様は専用ページで承認する。

## 頭の中へ戻すための要約

Bossは現在のCombatとBuildを試し、次の装備更新へつながる報酬を出すHuntの中心。

## Boss Design Rules候補

- Mob/BossもPlayerと同じ明示的な攻撃判定を使う。
- 接触だけでDamageを与えない。
- first-personでも予兆と安全地帯を読める。
- 初期Bossに専用3D modelを必須にしない。
- 一つのconcrete Bossから必要なruntimeだけを抽出する。

## Individual Specifications

| Boss | Design | Implementation | Role |
| --- | --- | --- | --- |
| [[Director/20 Content/Bosses/Rift Executioner|Rift Executioner]] | NEEDS_REVIEW | PLAYTEST | 現在のBoss prototype。正式First Bossにするか未決定 |

## Contentとして未決定

- First IslandのBoss lineupとFinal Boss。
- Solo/Party scalingの共通方針。
- wipe、revive、limited lives。
- Boss素材と固有報酬。
- 予兆をbody/VFX/floorへどう分担するか。

## Missing Connections

- 正式Hunt Map、Harbor/Quest entry、Party lifecycle。
- 個人Loot、Boss素材、Craft/Equipment update。
- 死亡、復活、再挑戦。
- persistence/reconnect中のencounter policy。

## Related

[[Director/20 Content/First Hunt and Harbor|First Hunt & Harbor]] · [[Director/20 Content/Boss Asset Pipeline|Boss Asset Pipeline]] · [[Director/10 Systems/Combat|Combat]]
