# ProjectS V12 — 五始点で12ptから24ptへ育てる

[動く実データHTML](ProjectS_Five_Growth_V12.html)。V11を保持した別候補。本人がLibrary保存を承認したものはV11 HTML 1点で、V12はローカルのみ。新しい本編/save/packetは作っていない。

## 問題と変更

V11の固定入力ではRanger/Assassinに安い外側目標がある一方、各職の所有入力では既存共通92目標の単独最安が両職0だった。障壁だけのSmall g51n5/g51n8が、障壁を持たない職業の入口を遮り、大きな迂回を要求していた。前者に魔法防御2、後者にHP2を加え、旧障壁6は所有者だけの追加値として保持した。障壁能力や強制魔術は与えない。

同時に既存点で育成目標を配置した。Mageの二つのNotableはMP容量/消費と、資源/再使用。Rangerは既存Small g51n6を会心目標、既存Road r39_40_3を位置取り/再使用の目標に変更。Assassinは既存Road r1_51_2とr1_29_2を直撃/会心倍率と手数/資源の目標に変更した。装備は全職自由混合。追矢/毒印/吸命/残響や職業技能を二重に供給しない。

850点/67群/1011辺。全座標・全辺・中央の内容はV11と同一。新しい点・Road・線0。変更した報酬8点、表示種別4点（Small→Notable1、Road→Notable3）。規則的な外側群の複製を行っていない。直線の追加候補は既存の線と干渉し、有用な短縮も得られなかったため使用しない。[差分・比較人口](five-growth-authoring.json) / [候補](candidate-graph.json)。

## 同じ92目標で五始点を比較

同じID集合92点に固定。単独最安とは五始点のうちその始点だけが最小費用であること。同率最安は別列。全職所有入力の表は入力も職業で異なり、幾何だけの比較は固定入力で行う。新しくNotableになった4点をこの人口へ混ぜていない。

| 入力 / 版 | Warrior | Tank | Mage | Ranger | Assassin | 同率最安目標 |
|---|---:|---:|---:|---:|---:|---:|
| 固定Warrior-support / V11 |35|8|12|13|15|9|
| 固定Warrior-support / V12 |35|8|12|13|15|9|
| 各職supportの所有入力 / V11 |50|8|26|0|0|8|
| 各職supportの所有入力 / V12 |35|8|13|12|15|9|

新しい報酬目標の固定費用：Mage二目標はM9/W15/T14/R12/A15。射撃はR4/W10/T12/M7/A7、位置取りはR5/W9/T13/M8/A8。決め手はA4/W7/T12/M12/R9、回収はA5/W6/T11/M11/R8。全五職から横移動でき、職業専用の取得制限はない。

これは特定の能力源を入口で要求していた問題を修正する案。Warriorはなお既存92目標中35の単独最安で、装甲g6n0など全体の報酬分布は未調整。全始点バランスが完成したとは判定しない。

## 同じ使用24ptで選ぶ

五職各二例、計10例。元の二方向12ptの全取得を保持し、実際に追加12ptを購入する。すべて使用24/残り0、無効点0。越境で他職地域のパッシブを取る例も職業技能を新規に与えない。比較画面で12pt/24ptを開ける。

| 始点 | 24ptの育成 | 追加パッシブ値（単位はHTMLの元定義に従う） |
|---|---|---|
| warrior | 主力と再使用を伸ばす | {"hp": 25, "armor": 14, "physical": 45, "haste": 20, "attackSpeed": 13, "resource": 24, "efficiency": 4} |
| warrior | 通常命中から資源を回収 | {"resource": 40, "attackSpeed": 22, "hp": 29, "armor": 14, "physical": 33, "haste": 3} |
| tank | 容量と装甲で前に残る | {"armor": 53, "hp": 65, "resist": 31, "regen": 16, "mana": 6} |
| tank | 守りを回し、立て直す | {"hp": 51, "armor": 34, "regen": 14, "efficiency": 9, "resist": 16, "haste": 26, "barrier": 16, "resource": 1} |
| mage | MPの器と消費を積む | {"regen": 8, "hp": 14, "barrier": 3, "mana": 78, "resist": 2, "efficiency": 24, "resource": 4, "magic": 27} |
| mage | 資源から技へ、守りも戻す | {"regen": 14, "hp": 13, "resource": 35, "mana": 25, "magic": 14, "haste": 23, "resist": 10, "efficiency": 15} |
| ranger | 射撃の会心と直撃を育てる | {"crit": 60, "critMulti": 31, "move": 5, "resource": 8, "physical": 32, "damage": 20, "haste": 7, "hp": 11, "resist": 6} |
| ranger | 位置取りと所有技能を回す | {"move": 17, "resource": 33, "damage": 14, "crit": 24, "hp": 11, "haste": 50, "critMulti": 5, "resist": 4, "efficiency": 14, "mana": 6} |
| assassin | 決め手に会心を重ねる | {"crit": 43, "critMulti": 35, "physical": 50, "hp": 10, "damage": 12, "attackSpeed": 7, "resource": 12, "move": 5, "haste": 4, "resist": 4} |
| assassin | 手数と資源を越境して積む | {"attackSpeed": 39, "resource": 50, "physical": 32, "hp": 17, "move": 10, "resist": 2, "armor": 6, "critMulti": 9, "haste": 3} |

両24pt例を全取得する消費はWarrior28/Tank37/Mage37/Ranger40/Assassin41pt。24ptでは全取りできず、重点を選ぶ。追加パッシブの差は実戦DPS・操作感の測定値ではない。[全取得・入力・手順](route-examples.json)。元の全10配分12ptの取得と値は同じ。V11の旧24ptや融合例は変更点を経由すると値が変わるため、今回グラフで再計算した。

## 保存・検証・未完了

preview保存はfive-growth-v12。V11以前の配分コードは拒否する。本編保存は変更しない。canonical source、入力定義、pixel spriteと字体はV11のまま。

[実ブラウザ記録](browser-verification.json)に最終HTML SHA256を記録。実JSで22配分を目標から順に購入し、spent/取得/値・保存復元/旧版と不正コード拒否を照合。固定/所有入力の全atlasと五始点最安数を独立計算してPython結果と比較。五職の10例で12pt保持/使用24/union費用を確認。障壁を持たないRangerのg51n4、Assassinのv7g09n5も実際に購入し、無効点がないことを確認する。120入力ケース、22焦点のglyph/線/クリック/ラベルと中央侵入、PC1920×1200/mobile412×915の横overflowと例外を確認。

技術検証・目視確認・本人採用・本編完成は別判定。残る全体報酬分布、7職の本編統合、実戦効果、ゲーム操作感は未完了。native compile/ゲーム起動/install/pushは行わない。

生成：scripts/build-skill-tree-five-growth-v12.py → scripts/publish-skill-tree-five-growth-v12.py。実ブラウザ：専用一時Chrome port18132とscripts/verify-skill-tree-five-growth-v12.cjs。[参照記録](reference-review.md)。
