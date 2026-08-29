---
type: decision-index
last_reviewed: 2026-08-29
---

# Decision Index

## NOW — 現在必要な判断

| Timing | Decision | 判断基準 | Spec |
| --- | --- | --- | --- |
| Fire/Ice Manual Smoke時 | Ember/Frostが本当に別Buildに感じるか | 説明なしでDetonate / Freeze / Shatterと狙いが分かる | [[Director/10 Systems/Elements/Fire|Fire]] / [[Director/10 Systems/Elements/Ice|Ice]] |
| #127通過後 | 次のquality laneを1件選ぶ | 完全なHunt Loopへ最短で近づくもの | [[Director/30 Current/NEXT|NEXT]] |

今この瞬間、Lightningや全Craftingを決める必要はない。

## DESIGN REVIEW — 自分の頭と一致するか

AIと既存資料から取り込んだ候補。ここでの確認は実装判断ではなく、ProjectSの正式仕様にしてよいかの判断。

- [ ] [[Director/01 Vision/Project Identity|Project Identity]]
- [ ] [[Director/01 Vision/Beta Goal|Beta Goal]]
- [ ] [[Director/01 Vision/Player Journey|Player Journey]]
- [ ] [[Director/10 Systems/Elements/Fire|Fire]]
- [ ] [[Director/10 Systems/Elements/Ice|Ice]]
- [ ] [[Director/20 Content/Bosses/Rift Executioner|Rift Executioner]]

## NEXT — 実装前に必要

| Priority | Decision | 現在の状態 |
| --- | --- | --- |
| 🔴 | First Huntの死亡/復活/失敗/再挑戦 | 未決定 |
| 🔴 | Boss報酬から最初の装備更新までのContract | 未決定 |
| 🟡 | Twin Blades正式identityとclass/weaponの責任 | prototypeはあるが正式採用は未承認 |
| 🟡 | Global Passive Treeと武器/クラスspecializationの分離 | 方針候補はある。最終構造は未承認 |
| 🟡 | Twin Blades secondary visualの実装表現 | ownership要件と具体方式を分けて確認する |

## LATER — 今は止めない

- Lightningの固有挙動
- Ultの獲得/消費方式
- 各クラス固有ゲージの最終仕様
- 装備Quality、強化、Tier promotion
- Market/Trade/大規模Economy
- AI 3D Boss pipeline正式採用
- Open World / GvG / Minestom Fork

## Imported Decision Candidates

次は既存資料に書かれているが、Director Console上ではまだ再承認していない。

- ProjectS v2の初期構造とBeta最小ループ
- 一人称基準、ロックオンなし
- Server authority
- 武器固有通常攻撃と明示的空間Hit
- Player/Mob共通の攻撃文法、接触Damage原則なし
- Skill 1〜3 + Ult、共通Mana、固有ゲージの役割
- Lv45、Passive Point、Global Passive Tree方向
- Equipment slots / Tier / rarityとMOD capacity
- Fire / Iceの数値Contract
- UI Art Direction v1

承認した項目は箇条書きのままFIXED扱いにせず、対象の仕様ページまたはDecisionへ記録する。
