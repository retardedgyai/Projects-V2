---
type: content-spec
design_status: NEEDS_REVIEW
implementation_status: PLAYTEST
priority: NEXT
user_decision_needed: true
approved_by:
approved_at:
owner: Director
source:
  - server-minestom boss prototype
last_reviewed: 2026-08-29
---

# Rift Executioner

> [!warning] Director Review待ち
> 動いているprototypeから仕様候補を逆算したページ。実装されていること自体はDirector承認を意味しない。

## 頭の中にあるもの

- Duelから空間圧力、処刑へ強度が上がるBoss。
- 正面から殴り続けるだけではなく、予兆を読み、安全な位置を選ぶ。
- WeakpointとBreakで攻撃機会を作る。

## 絶対に実現したい感覚

- first-personでも「次に何が来るか」「どこへ逃げるか」が分かる。
- Phaseが進むほど空間の圧力が増す。
- Breakへ成功した時に、Playerが自分で攻撃機会を作ったと感じる。

## こうはしたくない

- 接触しているだけで継続Damageを受ける。
- chat文字を読まないと避けられない。
- 攻撃範囲とDamage timingが見た目と一致しない。
- HPだけが多い訓練人形になる。

## Prototype Contract

### Core

- HP 3000。
- Phases: Duel / Rift Pressure / Execution。
- Attacks: Sector Cleave / Forward Slam / Chain Dash。
- Rift hazard、Break、Final Struggle、Victory/Defeatを持つ。
- geometryとdamage timingはserverが所有する。
- Weakpoint/Break multipliersを持つ。

### Feedback

- Boss bar、telegraph、particle、sound、chat feedback。
- phase force/reset用development commands。

### Safety

- duplicate damage execution guard。
- state/controller tests。

## 正式Content化に必要な仕様

- Hunt内での登場理由と物語上の役割。
- Entry、Victory、Defeat、再挑戦。
- Solo/Party scaling。
- Boss素材、固有報酬、次のCraft/Equipment更新。
- 正式model/animationとtelegraph表現。

## 未決定

- Rift Executionerを正式First Bossへ昇格するか。
- mechanic test targetとしてArchiveするか。
- 正式採用する場合、Player Journeyのどの能力を最終試験にするか。

## Director Review

- [ ] 説明なしで主要攻撃を回避できた
- [ ] Phase変化を体感できた
- [ ] Breakを自分で作ったと感じた
- [ ] 正式First Bossにするか判断した

## Implementation

- Server prototype、controller、testsは存在する。
- 正式Hunt/Reward/Craft loopには未接続。

## Related

[[Director/20 Content/Bosses|Bosses]] · [[Director/20 Content/First Hunt and Harbor|First Hunt & Harbor]] · [[Director/10 Systems/Combat|Combat]]
