---
type: vision
design_status: NEEDS_REVIEW
approved_by:
approved_at:
priority: NOW
last_reviewed: 2026-08-29
---

# Beta Goal

> [!warning] Director Review待ち
> 既存資料をAIが整理した候補。君が全文を確認するまでFIXED仕様ではない。

## 最初の完全な遊び

`ProjectS起動 → 港町 → 1クエスト受注 → 1討伐Map → 1Boss撃破 → 1素材入手 → 1武器Craft/強化 → 永続保存 → 再挑戦`

これより広いSystemを、この一周より先に完成させない。

## Player Journey（Beta全体の設計Bank）

`Lv1 → 戦闘基礎 → Mob/Quest/探索 → XP/素材/報酬 → 装備/Build → Elite/Boss → Gathering/Crafting/MOD/Party → Lv45 → First Island Final Boss → Endgame`

上はIssue #112のDesign Bank。各sliceで現行コードと再調整してからFIXEDにする。

## Done When

- [ ] 港町から説明なしで討伐を始められる
- [ ] Bossまでの失敗/復帰ルールがある
- [ ] Boss報酬が装備更新へつながる
- [ ] 更新後の強さを再挑戦で確認できる
- [ ] 切断/再起動後も永続状態が壊れない
- [ ] 初見Playerが次に何をすべきか理解できる

## 現在のGap

- Combat/Boss prototypeはある。
- Progression/Persistence v0はある。
- Equipment/MODはCombat接続中。
- 港町、Quest、HuntSession実行、素材、Craft/強化、報酬→再挑戦の一本化が未完。

## Sources

[[00-product-vision|Product Vision]] · [Legacy Design Bank #112](https://github.com/retardedgyai/Projects-V2/issues/112)

## Director Review

- [ ] 最初に完成させたい一周と一致する
- [ ] Betaへ含めるものと後回しにするものが明確
- [ ] このGoalを達成した時に何を評価するか分かる
- [ ] 承認後、frontmatterを `FIXED / Director / 承認日` に更新した
