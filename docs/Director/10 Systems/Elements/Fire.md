---
type: system-spec
design_status: NEEDS_REVIEW
implementation_status: IMPLEMENTING
priority: NOW
user_decision_needed: true
approved_by:
approved_at:
owner: Director
source:
  - https://github.com/retardedgyai/Projects-V2/issues/127
last_reviewed: 2026-08-29
---

# Fire

> [!warning] Director Review待ち
> Issue #127から取り込んだ仕様候補。君が承認するまではProjectSのFIXED仕様ではない。

## 頭の中にあるもの

- 攻撃を重ね、Burnを十分に溜めた瞬間に爆発させる。
- 一発の色付きDamageではなく、蓄積とDetonateを狙うBuild。

## 絶対に実現したい感覚

- 攻撃を続ける理由と、爆発が近づく期待感がある。
- Detonateが起きた瞬間を説明なしで認識できる。
- 単体への蓄積が周囲への結果につながる。

## こうはしたくない

- 常時つく小さな追加Damageだけになる。
- Burnが無制限に広がり、Playerの狙いがなくなる。
- 誰のBuildが結果を作ったか分からなくなる。

## 完成仕様候補

### State

- Burnはtarget-shared。
- fractional remainderを保持する。
- 最大10 stacks。10到達でDetonate。
- Detonate後は7 stacksを消費し、3 stacksを残す。
- Element input停止後5秒holdし、その後remainder/stackが減衰する。

### Contribution

- effective Fire contributionは基礎Contributionの2.5倍。
- 1 valid server hitにつきElement contributionは1回だけ。
- contributorとPhysical/Magical lineage attributionを保持する。

### Detonate

- 半径4 blocks。
- 中心targetの倍率は1.0。
- 周囲targetの倍率は0.6。
- Burnは周囲へspreadしない。

### Feedback

- Detonateの発生、範囲、中心targetをparticle/sound/health responseから理解できること。
- 専用UIを成立条件にしない。

## 例外・境界条件

- 同じHitで複数回Contributionしない。
- Detonate自身からBurnを再帰的に付与しない。
- target消滅時のstate cleanupをserverが所有する。

## 未決定

- この数値と体験で本当に「溜めて爆発を狙うBuild」に感じるか。
- Boss/Eliteで蓄積率やDetonate結果を変えるか。

## Director Review

- [ ] Ember Buildを実際に触った
- [ ] Detonateを説明なしで認識できた
- [ ] Iceと違う判断が生まれた
- [ ] 数値・例外を承認した

## Implementation

- Delivery Contract: [Issue #127](https://github.com/retardedgyai/Projects-V2/issues/127)
- 実装branchでruntime/testsを作業中。

## Related

[[Director/10 Systems/Elements|Elements]] · [[Director/10 Systems/Elements/Ice|Ice]] · [[Director/10 Systems/MOD|MOD]]
