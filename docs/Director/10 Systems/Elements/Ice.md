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

# Ice

> [!warning] Director Review待ち
> Issue #127から取り込んだ仕様候補。君が承認するまではProjectSのFIXED仕様ではない。

## 頭の中にあるもの

- Coldを蓄積して敵をFrozenにし、その後の一撃でShatterする。
- Fireの連続蓄積と違い、状態を作ってから次Hitを選ぶBuild。

## 絶対に実現したい感覚

- Freezeを作ったことと、砕くべき瞬間が明確に分かる。
- Shatterの一撃に手応えがある。
- Fireとは異なる攻撃リズムになる。

## こうはしたくない

- Freezeを作った同じHitで自動的にShatterして判断が消える。
- Bossを連続Freezeして行動不能にし続ける。
- 単なる常時Damage倍率になる。

## 完成仕様候補

### State

- Coldはtarget-shared gauge。
- `Cold I → Cold II → Frozen` と進む。
- Shatter後はColdを40%残す。

### Frozen

- Frozen中のeligible direct damageは1.08倍。
- Frozenを作った同じHitではShatterしない。
- Frozen後の次のvalid hitだけがShatterできる。
- Shatterは1 freezeにつき1回。

### Shatter

- impact = pre-crit direct damage ×1.25。
- Ice core = frozen core ×0.5。
- Shatter bonusはnon-crit / single-target。

### Refreeze Immunity

| Target | Immunity |
| --- | --- |
| Normal | 3s |
| Elite | 4s |
| Miniboss | 5s |
| Boss | 8s |

- Immunity中もCold capには到達できる。
- Immunity終了後、次のvalid Ice hitでFreezeする。

### Feedback

- Cold段階、Frozen、Shatter、immunityを専用UIなしでも識別できること。

## 例外・境界条件

- 同じHitでFreezeとShatterを同時成立させない。
- 1 freezeから複数回Shatterを発生させない。
- target classificationがない場合のfallbackを実装Contractで明示する。

## 未決定

- 体験上、Shatterを狙っていることが自然に理解できるか。
- Frozen中の行動制限をtarget分類ごとにどう見せるか。

## Director Review

- [ ] Frost Buildを実際に触った
- [ ] FreezeとShatterを説明なしで認識できた
- [ ] Fireと違う判断が生まれた
- [ ] 数値・例外を承認した

## Implementation

- Delivery Contract: [Issue #127](https://github.com/retardedgyai/Projects-V2/issues/127)
- 実装branchでruntime/testsを作業中。

## Related

[[Director/10 Systems/Elements|Elements]] · [[Director/10 Systems/Elements/Fire|Fire]] · [[Director/10 Systems/MOD|MOD]]
