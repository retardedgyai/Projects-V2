---
ページ種類: システム
仕様状態: 要確認
実装状態: 実装中
確認した人:
確認日:
優先度: 今
仕様の分かりやすさ: 問題なし
実装の難しさ: 注意
Minecraftの制約: 問題なし
画面への依存: 注意
素材の必要量: 注意
テスト: 問題なし
自分の判断が必要: false
最終確認日: 2026-08-29
---

# MOD

> [!warning] 自分の確認待ち
> このページはSystem Map兼・取り込み候補。個別仕様は自分の確認後に仕様確定になる。

## 目的

装備へ付ける小さな変更で、同じ武器でもstatだけでなくCombat behaviorを変える。

## 取り込み済み仕様候補

- data-driven definition。
- stable ID、Rank 1〜3、allowed slots。
- required/excluded AttackTag。
- finite roll range、definition revision。
- Stacking layers: Base Flat / Base Percent / Increased / Conditional / Final。
- static numeric modifierとreactive/element/proc behaviorは分離可能にする。
- display weapon nameではなくattack tagsでapplicabilityを判定。
- unknown/invalid MODは安全にreject/preserveし、別statへ読み替えない。
- Generic scripting/effect engineを先に作らない。

## 実装済み

- `ModDefinition` / `ModEntry` / validation。
- RankとEquipment Tier整合。
- slot/tag/revision/rolled value validation。
- EquipmentStatContribution。
- tests。

## 実装中（#127）

- Keen Edge等のnumeric offensive MOD。
- Ember/Frost typed effect。
- Equipment snapshotからCombatへ解決。
- valid hitでFire/Ice contributionを1回だけ適用。

## 未決定

- Betaの正式MOD poolとdrop source。
- MODの装着/取り外しcost。
- 同一MOD重複、上限、rare behavior。
- reactive MODの最小typed modelをどこまで一般化するか。
- Crafting/Marketとの取引単位。

## 実装で困りそうなこと

| 観点 | 状態 | 理由・次に確かめること |
| --- | --- | --- |
| 仕様の分かりやすさ | 🟢 | Core contractと#127の最小effectは明確 |
| 実装の難しさ | 🟡 | static/reactive resolution順とattack snapshot |
| Minecraftの制約 | 🟢 | pure domain中心 |
| 画面への依存 | 🟡 | #127はUIなし。後でtooltip/compareが必要 |
| 必要な素材の量 | 🟡 | prototype数件のみ。正式poolは未設計 |
| テスト | 🟢 | foundation + #127 branch tests |

## 自分が決めること

今はなし。#127ではEmber/Frostと少数numeric MODだけ。正式poolはCraft/Economy sliceで決める。

## 関係する仕様

[[制作ノート/10 ゲームの仕組み/装備|装備]] · [[制作ノート/10 ゲームの仕組み/属性|属性]] · [[制作ノート/10 ゲームの仕組み/クラフト|クラフト]] · [Issue #127](https://github.com/retardedgyai/Projects-V2/issues/127)
