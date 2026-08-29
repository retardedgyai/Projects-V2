---
type: system
design_status: NEEDS_REVIEW
implementation_status: PLAYTEST
approved_by:
approved_at:
priority: NOW
design_clarity: green
backend_complexity: yellow
minecraft_limitation: yellow
ui_dependency: yellow
content_requirement: yellow
testing: green
user_decision_needed: false
last_reviewed: 2026-08-29
---

# Combat

> [!warning] Director Review待ち
> このページはSystem Map兼・取り込み候補。個別仕様はDirector承認後にFIXEDになる。

## 目的

一人称で攻撃を自分で狙い、敵の明示的な攻撃範囲だけを最小移動で避け、武器/Build固有のテンポを維持するAction Combat。

## 取り込み済み仕様候補

- 一人称を正式基準。三人称は許可するが初期調整対象外。
- ロックオン/自動向き補正なし。照準方向へ攻撃。
- 通常攻撃は武器固有の最も基本的な戦闘動作。
- 近接通常攻撃は長押し継続を試す。Clientは押下/解放だけ送る。
- 一撃は `startup → active hit window → recovery`。
- Skill/回避入力は現在の一撃をcancelせず、一撃終了後に実行。
- Attack Speedは高速click要求ではなく武器tempoを変える。
- Vanilla meleeを主判定にせず、扇/突き/円/突進/projectile等の明示的空間Hit。
- 同一attack executionで意図しない多重Hitを防ぐ。
- Player/Mobは同じ攻撃文法。接触Damageは原則なし。
- ServerがHit/Damage/Rewardを最終決定。
- 初期は高度な巻き戻し判定なし。Client presentationは即時。
- Skill 1〜3 + Ult + Dodge。

## 仮決定 / Playtest対象

- Dodge: WASD 8方向、約3 blocks、地上、初期i-frame/staminaなし。
- Heavy Blade/Twin BladesへのAttack Speed配分。
- Combat feelは現在のRift Executioner prototypeで評価中。

## 実装済み

- `CombatState`、attack execution ID、weapon profile、hold input。
- Heavy Blade / Twin Blades(TWIN_RODS internal) normal attacks。
- Dodge。
- Twin Blades aerial jump/dodge loop。
- Skill 1〜3、Mana/Cooldown、VFX/Sound。
- Weakpoint、Boss damage、server-authoritative hit。
- Combat HUD、debug shapes、tests。

## 実装中

- Equipment/MOD resolved snapshot → Combat。
- Fire/Ice stateとDamage Core接続。
- current attack中のweapon swapでsnapshotを変えない保証。

## 未決定

- SkillとDodgeが同時queueされた時の優先順位。
- 武器ごとの最終Attack Speed式。
- 被弾時の怯み/吹き飛び/よろめき。
- Dodgeのi-frame/staminaを正式採用するか。
- 高Ping補正が必要になる閾値。

## Implementation Health

| 観点 | Health | 理由 / 次の検証 |
| --- | --- | --- |
| Design clarity | 🟢 | 基本戦闘文法は詳細に固定済み |
| Backend complexity | 🟡 | Equipment/Elements/Damageのsnapshot順を#127で接続中 |
| Minecraft limitation | 🟡 | 入力、first-person可読性、Client optional範囲を実機確認 |
| UI dependency | 🟡 | HUDはある。Element feedbackは新UIなしで読ませる必要 |
| Content requirement | 🟡 | Rift Executioner以外の実戦例がない |
| Testing | 🟢 | Combat/Dodge/Boss/skillsのunit testsあり |

## User Decision Needed

今はなし。#127完成後、Build差が本当に面白いかManual Smokeで判定する。

## Related

[[game/combat-principles|Canonical Combat Principles]] · [[Director/10 Systems/Damage|Damage]] · [[Director/10 Systems/Elements|Elements]] · [[Director/10 Systems/Mana and Classes|Mana & Classes]] · [[Director/20 Content/Bosses|Bosses]]
