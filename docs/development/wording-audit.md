# 文言の一覧（自動生成）

`python3 scripts/wording_audit.py --md docs/development/wording-audit.md` で作り直せます。幅は px。全角幅はバニラ文字なら px÷9、メニュー文字（core_menu）でも漢字・かなは 9 px なので同じく px÷9 を目安に書いています。

- 集めた文字列：3955（日本語を含む 3801、英字だけ 154）
- 英語が残っているもの：283
- 書き方だけが違う名前の組（自動）：36
- 誤字の候補（自動の型）：2
- はみ出し（コードに上限がある所）：0／測った 175
- メニュー文字の幅表：`server-minestom/src/main/resources/core-ui-pack/assets/projects/menu`

## はみ出し（上限がコードにある所）

| 場所 | 表示先 | 文字列 | 幅 | 上限 |
|---|---|---|---|---|
| （なし） | | | | |

### 上限に近いもの（上限の 90% 以上）

| 場所 | 表示先 | 文字列 | 幅 | 上限 |
|---|---|---|---|---|
| coreloop/FirstMagicWorkshop.kt:127 | メニュー左右の行（PANEL_WIDTH 88） | 紙上の小さな星図は | 81 | 88 |
| coreloop/FirstMagicWorkshop.kt:138 | メニュー左右の行（PANEL_WIDTH 88） | 研究済み素材を選ぶ | 81 | 88 |
| coreloop/FirstMagicWorkshop.kt:153 | メニュー左右の行（PANEL_WIDTH 88） | 石は沈殿として残る | 81 | 88 |
| coreloop/FirstMagicWorkshop.kt:176 | メニュー左右の行（PANEL_WIDTH 88） | 未知から性質を知る | 81 | 88 |
| coreloop/FirstMagicWorkshop.kt:180 | メニュー左右の行（PANEL_WIDTH 88） | 封印区画の円形台座 | 81 | 88 |
| coreloop/FirstMagicWorkshop.kt:181 | メニュー左右の行（PANEL_WIDTH 88） | まだ共鳴が足りない | 81 | 88 |
| coreloop/FirstMagicWorkshop.kt:193 | メニュー左右の行（PANEL_WIDTH 88） | 六角盤の空欄に記す | 81 | 88 |
| coreloop/FirstMagicWorkshop.kt:200 | メニュー左右の行（PANEL_WIDTH 88） | 解明で調合品が開く | 81 | 88 |
| coreloop/FirstMagicWorkshop.kt:205 | メニュー左右の行（PANEL_WIDTH 88） | 外せばインクは戻る | 81 | 88 |
| coreloop/FirstMagicWorkshop.kt:216 | メニュー左右の行（PANEL_WIDTH 88） | 各インクを一つ消費 | 81 | 88 |
| coreloop/FirstMagicWorkshop.kt:226 | メニュー左右の行（PANEL_WIDTH 88） | 研究盤で接続できる | 81 | 88 |
| coreloop/FirstMagicWorkshop.kt:233 | メニュー左右の行（PANEL_WIDTH 88） | 解明した研究ごとに | 81 | 88 |
| coreloop/FirstMagicWorkshop.kt:162 | メニュー左右の行（PANEL_WIDTH 88） | 棚の瓶をクリックで | 80 | 88 |
| coreloop/FirstMagicWorkshop.kt:246 | メニュー左右の行（PANEL_WIDTH 88） |   鍛冶の強化成功率 | 80 | 88 |

## 上限がコードにない表示先（測っただけ）

バニラの文字で測った幅（px、全角＝px÷9）。長い順に 15 件ずつ。

### チャット（128 件）

| 場所 | 文字列 | 幅 | 全角 |
|---|---|---|---|
| coreloop/CoreLoopServer.kt:753 | /motion greatsword_charge \| dual_blades_dance [self] \| stop \| watch（self：自分にも人形を見せる。watch：通常攻撃の人形も自分に見せるのを切り替え。F5で確認用） | 768 | 85.3 |
| coreloop/WorldInfusionServer.kt:78 | ワールド祭壇試作：祭壇キットを床へ右クリック → 外側台座とJarを設置。装備と素材を手に持って台座へ。 | 449 | 49.9 |
| coreloop/CoreLoopServer.kt:359 | 所持品が満杯です。従来の保存品 {…} 種類は消えていません。空きを作ると所持品に表示されます | 405 | 45.0 |
| coreloop/WorldInfusionServer.kt:79 | Matrixを筆記杖で起動。しゃがみ＋空手で素材回収／空台座・Jar撤去、しゃがみ＋杖で取消し。 | 399 | 44.3 |
| coreloop/CoreLoopServer.kt:1637 | 採取素材を精製 → 工房で装備を制作 → 装備庫から使う・売る。市場で素材の売買もできます。 | 394 | 43.8 |
| coreloop/WorldInfusionServer.kt:80 | これは独立した試験ワールド。初期装備・素材・充填Jar・範囲6・霜転レシピは仮設定です。 | 384 | 42.7 |
| coreloop/PlayerDisplaySettings.kt:101 | 画面の設定を保存しました（GUIの大きさ {…}・FOV {…}）。/settings で変えられます。 | 382 | 42.4 |
| questmap/VerdantRoadQuestService.kt:207 | 素材を受け取れませんでした。インベントリに空きを作って再度採取してください。 | 342 | 38.0 |
| coreloop/CoreLoopServer.kt:367 | ホットバー9番の「冒険の手帳」を右クリックすると、一周の流れを確認できます。 | 339 | 37.7 |
| coreloop/CoreLoopServer.kt:670 | 千本桜が来る。庭の縁でやり過ごし、終わったらもう一度 /dungeontest boss | 334 | 37.1 |
| coreloop/CoreLoopServer.kt:1024 | 設備を手に持って右クリックで配置。遠征で異質素材を拾い、この庭へ持ち帰ろう | 333 | 37.0 |
| coreloop/CoreLoopServer.kt:1545 | 羅針盤から港へ帰還し、工房で装備を更新しよう。探索は続けても構いません。 | 324 | 36.0 |
| coreloop/adventure/AdventureRuntime.kt:179 | 祭儀の報酬 {…}個：祭壇を右クリックで続行／しゃがみ右クリックで確定。 | 311 | 34.6 |
| coreloop/CoreCombatLab.kt:119 | テスト用マップを生成できませんでした。保存データは変更していません。 | 306 | 34.0 |
| coreloop/CoreLoopServer.kt:1052 | 研究机の自動設置場所がありません。手持ちの研究机を庭に置いてください | 306 | 34.0 |

### タイトル（8 件）

| 場所 | 文字列 | 幅 | 全角 |
|---|---|---|---|
| coreloop/CoreLoopServer.kt:1118 | 置いたJarに、最初の性質が宿った | 144 | 16.0 |
| coreloop/CoreLoopServer.kt:342 | 獲得素材を持って港へ戻ります | 126 | 14.0 |
| coreloop/CoreLoopServer.kt:1109 | 古い工房の目覚め | 72 | 8.0 |
| coreloop/CoreLoopServer.kt:1118 | 最初のEssentia | 69 | 7.7 |
| coreloop/CoreLoopServer.kt:1018 | 自分のコロニー | 63 | 7.0 |
| mob/QuestEncounterCombat.kt:1489 | {…}を討った | 55 | 6.1 |
| coreloop/CoreLoopServer.kt:342 | 力尽きた… | 45 | 5.0 |
| coreloop/CoreLoopServer.kt:1541 | 討伐達成 | 36 | 4.0 |

### アクションバー（11 件）

| 場所 | 文字列 | 幅 | 全角 |
|---|---|---|---|
| coreloop/WorldInfusionServer.kt:213 | 中心に装備、Matrixには筆記杖。取消しはしゃがみ＋杖。 | 237 | 26.3 |
| coreloop/WorldInfusionServer.kt:227 | 通常素材を手に持って右クリック。空手で回収。 | 198 | 22.0 |
| coreloop/WorldInfusionServer.kt:219 | {…} Jar {…}/{…} / しゃがみ＋空手で撤去 | 193 | 21.4 |
| coreloop/adventure/InstanceDungeon.kt:312 | {…}を制圧・庭への道が開いた | 127 | 14.1 |
| coreloop/adventure/InstanceDungeon.kt:312 | {…}を制圧・次の扉が開いた | 118 | 13.1 |
| questmap/VerdantRoadQuestService.kt:90 | この採取物は再生中です。 | 108 | 12.0 |
| coreloop/adventure/InstanceDungeon.kt:380 | {…}・第{…}/{…}波 | 90 | 10.0 |
| coreloop/adventure/InstanceDungeon.kt:322 | {…}・あと{…}体 | 74 | 8.2 |
| coreloop/adventure/InstanceDungeon.kt:322 | {…}・最後の穢れ | 73 | 8.1 |
| coreloop/FirstMagicWorkshop.kt:354 | 蒸留中  {…}… | 63 | 7.0 |
| coreloop/WorldInfusionServer.kt:190 | 操作できません | 63 | 7.0 |

### ボスバー（8 件）

| 場所 | 文字列 | 幅 | 全角 |
|---|---|---|---|
| mob/QuestEncounterCombat.kt:1228 | {…}  {…}秒 | 55 | 6.1 |
| mob/QuestEncounterCombat.kt:1211 | 翳の大詠唱 | 45 | 5.0 |
| mob/QuestEncounterCombat.kt:1228 | 翳の大詠唱 | 45 | 5.0 |
| mob/QuestEncounterCombat.kt:1212 | 止める量 | 36 | 4.0 |
| mob/QuestEncounterCombat.kt:1228 | 鱗粉の殻 | 36 | 4.0 |
| mob/QuestEncounterCombat.kt:1230 | 殻の耐久 | 36 | 4.0 |
| mob/QuestEncounterCombat.kt:1230 | 止める量 | 36 | 4.0 |
| mob/ShadeWyrmGimmicks.kt:31 | 侵食 | 18 | 2.0 |

## 英語が残っているもの

語ごとの数：MOD 86、Tier 49、Jar 32、Aspect 14、Essentia 13、AD 8、AR 8、MR 8、CC 8、AP 7、tick 5、ProjectS 5、Matrix 5、GUI 4、Lux 3、hub 3、Aer 2、Ignis 2、Space 2、skilltest 2、researchtest 2、lighttest 2、off 2、FOV 2、Rank 2、Aqua 1、Terra 1、Ordo 1、Perditio 1、Tempestas 1、Motus 1、Vacuos 1、Victus 1、Gelum 1、Venenum 1、Potentia 1、Mortuus 1、Metallum 1、pt 1、HUD 1、creative 1、frontier 1、bosstest 1、minibosstest 1、boss 1、dungeontest 1、blades 1、charge 1、dance 1、dual 1、greatsword 1、motion 1、self 1、stop 1、watch 1、ClaudeTest 1、effects 1、orbit 1、Ember 1、Tide 1、Gale 1、Stone 1、settings 1、seed 1、ms 1

| 場所 | 英語 | 文字列 |
|---|---|---|
| coreloop/AspectResearch.kt:13 | Aer | Aer / 風 |
| coreloop/AspectResearch.kt:14 | Aqua | Aqua / 水 |
| coreloop/AspectResearch.kt:15 | Ignis | Ignis / 火 |
| coreloop/AspectResearch.kt:16 | Terra | Terra / 地 |
| coreloop/AspectResearch.kt:17 | Ordo | Ordo / 秩序 |
| coreloop/AspectResearch.kt:18 | Perditio | Perditio / 混沌 |
| coreloop/AspectResearch.kt:19 | Lux | Lux / 光 |
| coreloop/AspectResearch.kt:20 | Tempestas | Tempestas / 嵐 |
| coreloop/AspectResearch.kt:21 | Motus | Motus / 運動 |
| coreloop/AspectResearch.kt:22 | Vacuos | Vacuos / 虚空 |
| coreloop/AspectResearch.kt:23 | Victus | Victus / 生命 |
| coreloop/AspectResearch.kt:24 | Gelum | Gelum / 氷 |
| coreloop/AspectResearch.kt:25 | Venenum | Venenum / 毒 |
| coreloop/AspectResearch.kt:26 | Potentia | Potentia / 力 |
| coreloop/AspectResearch.kt:27 | Mortuus | Mortuus / 死 |
| coreloop/AspectResearch.kt:28 | Metallum | Metallum / 金属 |
| coreloop/ClassSelectScene.kt:146 | Space | Space |
| coreloop/ClassSelectScene.kt:244 | Space | Space |
| coreloop/CoreAccountService.kt:182 | MOD | 装備Lv{…}。MOD・品質・強化は維持しています |
| coreloop/CoreAccountService.kt:260 | MOD | 修理材料の装備1個を消費して修理しました。対象のMOD・強化値・天井・識別番号は維持しています |
| coreloop/CoreAccountService.kt:434 | MOD | 採取MODを地図に刻みました |
| coreloop/CoreAccountService.kt:515 | MOD | 修復の封蝋{…}個で修理しました。MOD・強化値・成功保証は維持しています |
| coreloop/CoreAffixCatalog.kt:29 | AD | 物理攻撃力 AD |
| coreloop/CoreAffixCatalog.kt:29 | AD | 物理攻撃力 AD |
| coreloop/CoreAffixCatalog.kt:30 | AP | 魔法攻撃力 AP |
| coreloop/CoreAffixCatalog.kt:30 | AP | 魔法攻撃力 AP |
| coreloop/CoreAffixCatalog.kt:31 | AR | 物理防御 AR |
| coreloop/CoreAffixCatalog.kt:31 | AR | 物理防御 AR |
| coreloop/CoreAffixCatalog.kt:32 | MR | 魔法防御 MR |
| coreloop/CoreAffixCatalog.kt:32 | MR | 魔法防御 MR |
| coreloop/CoreAffixCatalog.kt:42 | CC | CC耐性 |
| coreloop/CoreAffixCatalog.kt:43 | CC | CC時間 |
| coreloop/CoreAffixCatalog.kt:242 | MOD | 未対応のMOD（効果停止） |
| coreloop/CoreAffixCatalog.kt:243 | MOD | 不正なMOD値（効果停止） |
| coreloop/CoreAffixCatalog.kt:250 | AD | 暗殺者：印を付けた敵に{…}% ADの毒を3回 |
| coreloop/CoreAffixCatalog.kt:279 | MOD | MODを石に抽出 |
| coreloop/CoreCombatLab.kt:150 | Tier hub | {…}に到着（Tier{…}）。{…}が待っています。ポーションは{…}個・再使用10秒。手帳 [9] で職業・スキル・再配置、帰還は /hub。 |
| coreloop/CoreCombatLab.kt:151 | hub skilltest | 試験場に到着。手帳 [9] または /skilltest で職業・スキル・敵を変更できます。帰還は /hub。 |
| coreloop/CoreCombatLab.kt:286 | tick | 制限なし＝毎tick補充・無敵。通常条件＝実際の資源と待ち時間 |
| coreloop/CoreCombatMath.kt:8 | AR | 物理 / ARで軽減 |
| coreloop/CoreCombatMath.kt:8 | MR | 魔法 / MRで軽減 |
| coreloop/CoreCraftingCurrency.kt:44 | MOD | ノーマルをマジックに変え、ランダムなMODを1〜2個付与 |
| coreloop/CoreCraftingCurrency.kt:45 | MOD | マジックの空き枠へランダムなMODを1個追加 |
| coreloop/CoreCraftingCurrency.kt:46 | MOD | マジックのMODをすべて消し、1〜2個を再抽選 |
| coreloop/CoreCraftingCurrency.kt:47 | MOD | ノーマルをレアに変え、ランダムなMODを4〜6個付与 |
| coreloop/CoreCraftingCurrency.kt:48 | MOD | レアのMODをすべて消し、4〜6個を再抽選 |
| coreloop/CoreCraftingCurrency.kt:49 | MOD | マジックのMODを残してレアに変え、1個追加 |
| coreloop/CoreCraftingCurrency.kt:50 | MOD | レアの空き枠へランダムなMODを1個追加 |
| coreloop/CoreCraftingCurrency.kt:51 | MOD | MODをすべて消し、ノーマルに戻す（取り消し不可） |
| coreloop/CoreCraftingCurrency.kt:52 | MOD Tier | MODの種類とTierを保ち、数値だけ再抽選（低下する場合あり） |
| coreloop/CoreCraftingCurrency.kt:53 | MOD | 全MODを再抽選し、元素MODを1個以上含むレア4〜6個へ変える |
| coreloop/CoreCraftingCurrency.kt:54 | MOD | MODの数値をそれぞれ2回抽選し、高い方を採用（元の値を保証しない） |
| coreloop/CoreCraftingCurrency.kt:55 | MOD | レアの空き枠へ、数値範囲の上位25％からMODを1個追加 |
| coreloop/CoreCraftingCurrency.kt:75 | MOD | 旧形式のMOD構成です。改変で新形式へ移してください |
| coreloop/CoreCraftingCurrency.kt:77 | MOD | 再抽選するMODがありません |
| coreloop/CoreCraftingCurrency.kt:78 | MOD | 未対応のMODは数値を変更せず保管してください |
| coreloop/CoreCraftingCurrency.kt:86 | MOD | 旧形式のMOD構成です。再鋳造で新形式へ移してください |
| coreloop/CoreCraftingCurrency.kt:87 | MOD | MOD枠が満杯です |
| coreloop/CoreEnhancementCatalog.kt:136 | MOD Tier | 強化失敗で破損しました。装備庫で同Tier・同系統・+0の装備1個を使い修理できます。+{…}・MOD・天井 {…}/{…} は維持 |
| coreloop/CoreEnhancementCatalog.kt:137 | MOD | 強化失敗。破損はありません。+{…}・MODは維持。天井 {…}/{…}（鍛冶経験 +1） |
| coreloop/CoreJourney.kt:103 | MOD | 刻印工房でオーブを使い、MODを付けよう |
| coreloop/CoreJourney.kt:104 | Tier | ボスを討伐、または採取実績を積んで次のTierへ |
| coreloop/CoreJourney.kt:105 | MOD | T4遠征と工房で、装備のMOD・強化・製造品質を追求しよう |
| coreloop/CoreKeystone.kt:10 | CC | CC中・減速中の敵に対して |
| coreloop/CoreKeystone.kt:38 | CC | 回避できなくなる。被ダメージ-20%、CCを受けない。 |
| coreloop/CoreLoopItems.kt:135 | AD | 物理攻撃 AD |
| coreloop/CoreLoopItems.kt:136 | AP | 魔法攻撃 AP |
| coreloop/CoreLoopItems.kt:141 | AR | 物理防御 AR |
| coreloop/CoreLoopItems.kt:142 | MR | 魔法防御 MR |
| coreloop/CoreLoopItems.kt:167 | Tier | 装備Lv鍛錬で同Tier内の基礎性能が成長 |
| coreloop/CoreLoopItems.kt:167 | MOD | 破損中：この装備の性能・MODは無効 |
| coreloop/CoreLoopItems.kt:168 | Tier | 装備庫で修理：同Tier・同系統・+0・未破損を1個 |
| coreloop/CoreLoopItems.kt:169 | MOD | 修理対象のMOD・強化値・製作者は維持 |
| coreloop/CoreLoopItems.kt:170 | MOD | 能力欄は装備Lv・強化・MODを反映 |
| coreloop/CoreLoopItems.kt:172 | MOD | 防具MODはセット全体に1回適用 |
| coreloop/CoreLoopItems.kt:179 | MOD | 未対応MOD |
| coreloop/CoreLoopItems.kt:245 | MOD | 石板をつかんで重ねるとMODを付与 |
| coreloop/CoreLoopItems.kt:245 | MOD | 付与MOD {…}/3 |
| coreloop/CoreLoopItems.kt:315 | MOD | 与回復・被回復MODが適用 / 再使用10秒 |
| coreloop/CoreLoopItems.kt:317 | ProjectS | ProjectS — 冒険の手帳 |
| coreloop/CoreLoopItems.kt:323 | MOD | つかんで地図に重ねるとMODを付与 |
| coreloop/CoreLoopItems.kt:328 | AD AP | AD・AP自体は変化しません |
| coreloop/CoreLoopMenus.kt:144 | Tier | 素材・地図 Tier {…} |
| coreloop/CoreLoopMenus.kt:173 | MOD | 強化・精製・制作・MOD加工 |
| coreloop/CoreLoopMenus.kt:184 | MOD | 武器・防具のMODを確認 |
| coreloop/CoreLoopMenus.kt:205 | Tier | 同TierのLv上限で |
| coreloop/CoreLoopMenus.kt:205 | Tier | ボス討伐→次Tier |
| coreloop/CoreLoopMenus.kt:205 | Tier | このTierは未解放 |
| coreloop/CoreLoopMenus.kt:228 | MOD | 採取MOD |
| coreloop/CoreLoopMenus.kt:232 | MOD | 採取量・品質・密集地域のMODを追加 |
| coreloop/CoreLoopMenus.kt:266 | AD AP | 今のAD・AP・防御を確認 |
| coreloop/CoreLoopMenus.kt:278 | Tier | 同じTierの選択 |
| coreloop/CoreLoopMenus.kt:278 | MOD | MODは後から抽選 |
| coreloop/CoreLoopMenus.kt:279 | Tier | 上位製造に低Tier材 |
| coreloop/CoreLoopMenus.kt:483 | AD | 通常はADで伸びる |
| coreloop/CoreLoopMenus.kt:483 | AP | 魔法スキルはAPで伸びる |
| coreloop/CoreLoopMenus.kt:483 | AR | 物理はARで軽減 |
| coreloop/CoreLoopMenus.kt:483 | MR | 魔法はMRで軽減 |
| coreloop/CoreLoopMenus.kt:485 | AD | 物理 AD |
| coreloop/CoreLoopMenus.kt:485 | AP | 魔法 AP |
| coreloop/CoreLoopMenus.kt:485 | AR | 物防 AR |
| coreloop/CoreLoopMenus.kt:485 | MR | 魔防 MR |
| coreloop/CoreLoopMenus.kt:496 | Tier | 同Tier内で最大+9段階 |
| coreloop/CoreLoopMenus.kt:496 | MOD | MODと強化はそのまま |
| coreloop/CoreLoopMenus.kt:497 | Tier | このTierは完成 |
| coreloop/CoreLoopMenus.kt:497 | Tier | 次のTierでも好きな型を |
| coreloop/CoreLoopMenus.kt:584 | MOD | 強化値・MODは維持 / 破損時は装備1個で修理 |
| coreloop/CoreLoopMenus.kt:637 | MOD | 強化値・MODは維持 |
| coreloop/CoreLoopMenus.kt:641 | Tier | 素材Tierは装備Tierではなく強化段階によって決まります |
| coreloop/CoreLoopMenus.kt:668 | MOD | 破損しても対象は消失せず、強化値・MOD・成功保証を維持 |
| coreloop/CoreLoopMenus.kt:669 | Tier | 修理には同Tier・同系統・+0・未破損の装備1個を消費 |
| coreloop/CoreLoopMenus.kt:711 | Tier | 下の素材Tier |
| coreloop/CoreLoopMenus.kt:713 | Tier | 上の素材Tier |
| coreloop/CoreLoopMenus.kt:714 | Tier | 現在の装備Tierの素材で制作 |
| coreloop/CoreLoopMenus.kt:769 | MOD | 全MODを消去 |
| coreloop/CoreLoopMenus.kt:771 | MOD | 全MODを消去 |
| coreloop/CoreLoopMenus.kt:772 | MOD | 既存MODは保持 |
| coreloop/CoreLoopMenus.kt:774 | MOD | 全MODを消去 |
| coreloop/CoreLoopMenus.kt:775 | MOD Tier | MOD・Tier保持 |
| coreloop/CoreLoopMenus.kt:776 | MOD | 全MODを消去 |
| coreloop/CoreLoopMenus.kt:776 | MOD | 元素MODを含む |
| coreloop/CoreLoopMenus.kt:777 | MOD Tier | MOD・Tier保持 |
| coreloop/CoreLoopMenus.kt:811 | Tier | 同Tierのボスを討伐 |
| coreloop/CoreLoopMenus.kt:811 | Tier | 同Tierの地域で採取 |
| coreloop/CoreLoopMenus.kt:872 | Tier | クリック：このTierの素材を表示 |
| coreloop/CoreLoopMenus.kt:911 | Tier | このTierの素材はまだありません |
| coreloop/CoreLoopMenus.kt:912 | Tier | 上段から他のTierも確認できます |
| coreloop/CoreLoopMenus.kt:985 | MOD | MOD・強化値も装備ごとに保持します |
| coreloop/CoreLoopMenus.kt:996 | MOD | 自作・未強化・MODなし・未破損の装備のみ |
| coreloop/CoreLoopMenus.kt:1013 | Tier | 同Tier・同系統・+0・未破損 / 初期装備・出品中は不可 |
| coreloop/CoreLoopMenus.kt:1013 | MOD | 対象の装備は消えません / MOD・強化値・成功保証を維持 |
| coreloop/CoreLoopMenus.kt:1014 | MOD | 修理材料のレアリティ・MODは問いません |
| coreloop/CoreLoopMenus.kt:1014 | MOD | 材料に付いているMODも消失します / 選択後に確認 |
| coreloop/CoreLoopMenus.kt:1019 | MOD | 材料装備のMODも消失 / 取り消せません |
| coreloop/CoreLoopMenus.kt:1020 | MOD | 修理対象の強化値・MOD・成功保証はそのまま |
| coreloop/CoreLoopMenus.kt:1043 | Tier | 所持 {…} / 必要 {…}（Tier×{…}） |
| coreloop/CoreLoopMenus.kt:1080 | Tier | 同Tier・同系統・+0・未破損のみ表示 |
| coreloop/CoreLoopMenus.kt:1087 | Tier | クリック：同Tierの採取遠征へ |
| coreloop/CoreLoopMenus.kt:1101 | Tier | 高Tierの地図入手にも使います |
| coreloop/CoreLoopMenus.kt:1186 | Tier | 名前とTierが目印 |
| coreloop/CoreLoopMenus.kt:1201 | Tier | 採取対象1個につき、そのTier分の実績 |
| coreloop/CoreLoopMenus.kt:1252 | MOD Tier | 同Tier・同系統の+0・未破損装備（MOD・品質不問） |
| coreloop/CoreLoopMenus.kt:1256 | Tier | このTierには注文がありません |
| coreloop/CoreLoopMenus.kt:1324 | Tier | 同Tier・同系統・+0・未破損 / 初期・出品中の装備は不可 |
| coreloop/CoreLoopMenus.kt:1328 | MOD | 装備のMOD・品質もそのまま相手へ渡ります |
| coreloop/CoreLoopMenus.kt:1338 | MOD | MOD・製造品質も相手へ移ります |
| coreloop/CoreLoopMenus.kt:1350 | pt | 未使用 {…}pt |
| coreloop/CoreLoopMenus.kt:1352 | tick | 手つき：時間-4tick |
| coreloop/CoreLoopMenus.kt:1352 | tick | 豊穣は時間+8tick |
| coreloop/CoreLoopMenus.kt:1391 | MOD | 装備 / MOD詳細 |
| coreloop/CoreLoopMenus.kt:1396 | Tier | 内部Tier T{…} |
| coreloop/CoreLoopMenus.kt:1397 | MOD | 選択したMOD |
| coreloop/CoreLoopMenus.kt:1399 | Tier | 内部Tier T{…} |
| coreloop/CoreLoopMenus.kt:1400 | MOD | MODがありません |
| coreloop/CoreLoopMenus.kt:1404 | Tier | 品質 {…}% / 内部Tier T{…} |
| coreloop/CoreLoopMenus.kt:1410 | MOD | この装備のMODを加工する |
| coreloop/CoreLoopMenus.kt:1424 | MOD | 旧石のMODは消え |
| coreloop/CoreLoopMenus.kt:1424 | Tier | 改変は石のTier分 |
| coreloop/CoreLoopMenus.kt:1438 | MOD | 元のMODは失われる |
| coreloop/CoreLoopMenus.kt:1452 | GUI | GUI倍率を1段下げる |
| coreloop/CoreLoopMenus.kt:1453 | MOD | クライアントMODは不要 |
| coreloop/CoreLoopMenus.kt:1469 | HUD | マナ・再使用はHUDへ |
| coreloop/CoreLoopMenus.kt:1471 | MOD | マップの石板MODで |
| coreloop/CoreLoopMenus.kt:1471 | Tier | 高Tierの素材は |
| coreloop/CoreLoopMenus.kt:1471 | Tier | 同Tierのマップから |
| coreloop/CoreLoopMenus.kt:1472 | MOD | オーブでランダムMOD |
| coreloop/CoreLoopMenus.kt:1472 | MOD | 強化値とMODは保持 |
| coreloop/CoreLoopMenus.kt:1507 | Tier | Tierが未解放 |
| coreloop/CoreLoopServer.kt:585 | ProjectS | 魔術書を開くには ProjectS のリソースパックが必要です |
| coreloop/CoreLoopServer.kt:611 | creative frontier | /creative は /frontier の中で使ってください |
| coreloop/CoreLoopServer.kt:621 | hub researchtest | 先に /hub で港へ戻ってから /researchtest を使ってください |
| coreloop/CoreLoopServer.kt:642 | bosstest | /bosstest のボス戦の中で使ってください。 |
| coreloop/CoreLoopServer.kt:650 | minibosstest | /minibosstest 1 か 2（散り桜の社のミニボス1・2） |
| coreloop/CoreLoopServer.kt:670 | boss dungeontest | 千本桜が来る。庭の縁でやり過ごし、終わったらもう一度 /dungeontest boss |
| coreloop/CoreLoopServer.kt:753 | blades charge dance dual greatsword motion self stop watch | /motion greatsword_charge \| dual_blades_dance [self] \| stop \| watch（self：自分にも人形を見せる。watch： |
| coreloop/CoreLoopServer.kt:775 | ClaudeTest | ClaudeTest |
| coreloop/CoreLoopServer.kt:805 | skilltest | /skilltest の中で使ってください |
| coreloop/CoreLoopServer.kt:810 | effects | 戦闘演出：{…}（/effects で切替。自分の表示だけ変更） |
| coreloop/CoreLoopServer.kt:821 | lighttest off | 光は8つまで。/lighttest off で消す。 |
| coreloop/CoreLoopServer.kt:847 | lighttest off orbit | 桜の光 {…}/8（/lighttest orbit で回る光、/lighttest off で消す） |
| coreloop/CoreLoopServer.kt:942 | Jar | 上か棚の中に設備がある。先にJarを取り出そう |
| coreloop/CoreLoopServer.kt:957 | Jar | このJarはすでに設置されている |
| coreloop/CoreLoopServer.kt:964 | Jar | Jarの配置を保存できませんでした |
| coreloop/CoreLoopServer.kt:972 | Jar | インベントリに空きを作ってからJarを取り出してください |
| coreloop/CoreLoopServer.kt:979 | Jar | {…}のJarを棚から取り出した |
| coreloop/CoreLoopServer.kt:983 | Jar | Jarの回収を保存できませんでした |
| coreloop/CoreLoopServer.kt:1050 | researchtest | /researchtest：{…}。再実行で補充できます |
| coreloop/CoreLoopServer.kt:1084 | Essentia | 星図にはまだ描かれていない輪がある。Essentiaはその入口にすぎない |
| coreloop/CoreLoopServer.kt:1086 | Essentia | 封印の向こうに大きな円形装置が見える。今のEssentiaではまだ共鳴が足りない |
| coreloop/CoreLoopServer.kt:1087 | Jar | 共鳴が不足している。まず研究机とJarを満たそう |
| coreloop/CoreLoopServer.kt:1118 | Essentia | 最初のEssentia |
| coreloop/CoreLoopServer.kt:1118 | Jar | 置いたJarに、最初の性質が宿った |
| coreloop/CoreLoopServer.kt:1202 | MOD | MODは最大3個です。 |
| coreloop/CoreLoopServer.kt:1718 | MOD | 刻印工房でオーブを使い、MODを抽選 |
| coreloop/CoreLoopServer.kt:1721 | Tier | 密集地域や高Tierの資源を狙って周回しよう |
| coreloop/CoreLoopServer.kt:1722 | Tier | ボス討伐で次Tierの地図を解放 |
| coreloop/CoreSkillCatalog.kt:62 | CC | 出している間：CCが効かず、受けるダメージ-{…}% |
| coreloop/CoreSkillCatalog.kt:74 | AR MR | 防御崩し4秒：敵のAR・MRを20%低下 |
| coreloop/CoreSkillCatalog.kt:146 | MOD | 残響MOD |
| coreloop/CoreSkillCatalog.kt:150 | MOD | 余波MOD |
| coreloop/CoreSkillCatalog.kt:154 | MOD | 追矢MOD |
| coreloop/CoreSkillCatalog.kt:158 | MOD | 重力MOD |
| coreloop/CoreSkillCatalog.kt:200 | CC | 10秒間、体から血が流れ、鼓動のたびに血しぶきが散る。CCも怯みも効かず、動きと振りが速くなり、獣が2倍の速さで溜まる。代わりに体力が毎秒2%ずつ減る（1で止まる） |
| coreloop/CoreSkillCatalog.kt:201 | CC | 獣3つで超重力に入る（再使用なし）。獣を毎秒0.5ずつ燃やし、0で切れる。殴れば獣は溜まるので、溜まる速さが上回れば続く。入った時に最大HP20%の障壁（8秒）、受けるダメージ-1 |
| coreloop/CoreSkillCatalog.kt:247 | AR MR | 敵のARとMRを短時間低下させる |
| coreloop/CoreWarriorSkillPresentation.kt:16 | CC | 10秒、CCも怯みも効かず速く、獣が2倍で溜まる |
| coreloop/FirstMagicColonyLayout.kt:17 | Jar | Jar棚 |
| coreloop/FirstMagicState.kt:4 | Ember | Ember / 熾火 |
| coreloop/FirstMagicState.kt:4 | Tide | Tide / 潮 |
| coreloop/FirstMagicState.kt:4 | Gale | Gale / 風 |
| coreloop/FirstMagicState.kt:4 | Stone | Stone / 石 |
| coreloop/FirstMagicState.kt:115 | Jar | 対応するJarがいっぱい。蒸留を中止した |
| coreloop/FirstMagicState.kt:121 | Essentia Jar | {…}からEssentiaを抽出。Jarに保存した |
| coreloop/FirstMagicState.kt:127 | Aspect | この二つから生まれるAspectはまだ見つからない |
| coreloop/FirstMagicState.kt:129 | Aspect | 両方のAspectを先に発見しよう |
| coreloop/FirstMagicState.kt:151 | Aspect | そのAspectの研究インクが足りない |
| coreloop/FirstMagicState.kt:152 | Aspect | 先に置いたAspectを外そう |
| coreloop/FirstMagicState.kt:186 | Essentia | Essentiaが足りない（{…}） |
| coreloop/FirstMagicState.kt:198 | Aspect | 外せるAspectがない |
| coreloop/FirstMagicState.kt:202 | Aspect | Aspectを外し、研究インクを戻した |
| coreloop/FirstMagicWorkshop.kt:140 | Jar | 冷却管からJarへ |
| coreloop/FirstMagicWorkshop.kt:143 | Jar | 対応するJarを |
| coreloop/FirstMagicWorkshop.kt:145 | Jar | Jar上限 16 / 性質 |
| coreloop/FirstMagicWorkshop.kt:160 | Jar | Jarを持って |
| coreloop/FirstMagicWorkshop.kt:295 | Essentia | 分析済み素材からEssentiaを抽出 |
| coreloop/FirstMagicWorkshop.kt:308 | Jar | 沸騰 → 冷却 → Jarへ保存 |
| coreloop/FirstMagicWorkshop.kt:319 | Jar | Jarに {…} / {…} |
| coreloop/FirstMagicWorkshop.kt:319 | Jar | Jarを庭に置くと受け取れる |
| coreloop/FirstMagicWorkshop.kt:321 | Jar | Jar棚を見る |
| coreloop/FirstMagicWorkshop.kt:321 | Essentia | 保存されたEssentiaを確認 |
| coreloop/FirstMagicWorkshop.kt:337 | Jar | {…}に対応するJarを先に配置しよう |
| coreloop/FirstMagicWorkshop.kt:359 | Jar | Jarが外されたため蒸留を中止した。素材は残っている |
| coreloop/FirstMagicWorkshop.kt:369 | Essentia Jar | Essentia Jar棚 |
| coreloop/FirstMagicWorkshop.kt:377 | Jar | Jarを持って棚を右クリック |
| coreloop/FirstMagicWorkshop.kt:384 | Jar | 観測工房・Jar棚 |
| coreloop/FirstMagicWorkshop.kt:391 | Aspect | 研究Aspect {…} / {…} |
| coreloop/FirstMagicWorkshop.kt:398 | Jar | Jarが満たされる日を待つ |
| coreloop/FirstMagicWorkshop.kt:421 | Aspect | 未発見のAspect |
| coreloop/FirstMagicWorkshop.kt:446 | Aspect | Aspectを合成 |
| coreloop/FirstMagicWorkshop.kt:459 | Aspect | Aspectの合成 |
| coreloop/FirstMagicWorkshop.kt:476 | Aspect | Aspect合成 {…}/2 |
| coreloop/FirstMagicWorkshop.kt:491 | Aspect | クリックしてAspectを選ぶ |
| coreloop/FirstMagicWorkshop.kt:514 | Aspect | Aspect合成へ |
| coreloop/FirstMagicWorkshop.kt:523 | Essentia |   Essentia {…} + {…} |
| coreloop/FirstMagicWorkshop.kt:562 | Aspect | 六角 {…} に記すAspect |
| coreloop/GrimoireBook.kt:38 | Aer Ignis Lux | 相は2つ合わさって新しい相になる。松明の相は、Aer と Ignis が合わさった Lux だった。 |
| coreloop/GrimoireBook.kt:39 | Lux | 研究盤で Lux を解く |
| coreloop/GrimoireBook.kt:40 | Essentia | 相は取り出して液体（Essentia）にできる。素材を窯で溶かすと、中の相が分かれて出てくる。 |
| coreloop/GrimoireBook.kt:42 | Essentia | Essentia は瓶に溜まる。瓶ひとつにつき相はひとつ。 |
| coreloop/GrimoireBook.kt:45 | Essentia | ひとつの瓶に Essentia を16溜める |
| coreloop/GrimoireBook.kt:45 | Essentia | 瓶詰めの Essentia |
| coreloop/PlayerDisplaySettings.kt:74 | GUI | GUIの大きさが「自動」なら、1920×1080 の画面では 4 です。 |
| coreloop/PlayerDisplaySettings.kt:78 | GUI | GUIの大きさ |
| coreloop/PlayerDisplaySettings.kt:79 | FOV | 視野角（FOV） |
| coreloop/PlayerDisplaySettings.kt:101 | FOV GUI settings | 画面の設定を保存しました（GUIの大きさ {…}・FOV {…}）。/settings で変えられます。 |
| coreloop/PlayerMenuDialog.kt:217 | Rank | Rank は上限 |
| coreloop/PlayerMenuDialog.kt:218 | Rank | 次の Rank まで {…} |
| coreloop/ResearchEconomy.kt:17 | Tier | 破損装備をTier×{…}個で修理（予備装備不要） |
| coreloop/WorldInfusionServer.kt:78 | Jar | ワールド祭壇試作：祭壇キットを床へ右クリック → 外側台座とJarを設置。装備と素材を手に持って台座へ。 |
| coreloop/WorldInfusionServer.kt:79 | Jar Matrix | Matrixを筆記杖で起動。しゃがみ＋空手で素材回収／空台座・Jar撤去、しゃがみ＋杖で取消し。 |
| coreloop/WorldInfusionServer.kt:80 | Jar | これは独立した試験ワールド。初期装備・素材・充填Jar・範囲6・霜転レシピは仮設定です。 |
| coreloop/WorldInfusionServer.kt:159 | Matrix | 筆記杖：Matrixを右クリックで起動 |
| coreloop/WorldInfusionServer.kt:162 | Jar | {…} Jar {…}/{…} |
| coreloop/WorldInfusionServer.kt:213 | Matrix | 中心に装備、Matrixには筆記杖。取消しはしゃがみ＋杖。 |
| coreloop/WorldInfusionServer.kt:219 | Jar | {…} Jar {…}/{…} / しゃがみ＋空手で撤去 |
| coreloop/WorldInfusionServer.kt:406 | Jar Matrix | 供給停止：Jarを設置し直しMatrixへ筆記杖で再開 / しゃがみ＋杖で取消し |
| coreloop/WorldInfusionServer.kt:410 | Matrix | 準備完了：頭上のMatrixへ筆記杖で右クリック |
| coreloop/WorldInfusionState.kt:120 | MOD | 固定0枠に火属性MODが必要です（試作レシピ） |
| coreloop/WorldInfusionState.kt:126 | Jar | 近くの設置Jar：{…}が{…}不足（試作範囲6ブロック） |
| coreloop/ui/CoreForgeLayout.kt:8 | MOD | MOD加工 |
| coreloop/ui/CoreForgeLayout.kt:11 | MOD | MODを追加 |
| coreloop/ui/CoreForgeLayout.kt:11 | MOD | MODを引き直す |
| coreloop/ui/CoreForgeSummary.kt:62 | MOD | 最大強化・MODと強化値を維持 |
| coreloop/ui/CoreUiPackServer.kt:63 | ProjectS | ProjectSのUI・アイコンを適用します。拒否しても通常表示で遊べます。 |
| coreloop/ui/CoreUiPackServer.kt:87 | ProjectS | ProjectSのUIを適用しました。 |
| coreloop/ui/CoreUiTooltip.kt:31 | Tier | アイテムレベル {…}  /  内部Tier T{…} |
| coreloop/ui/CoreUiTooltip.kt:73 | Tier | アイテムレベル {…}  /  内部Tier T{…} |
| questmap/QuestGathering.kt:155 | tick | 採取時間が4tick短くなる。 |
| questmap/QuestGathering.kt:177 | tick | 通常素材が2個増えるが、採取時間が8tick長くなる。 |
| questmap/VerdantRoadQuestService.kt:440 | seed | クエストマップ seed={…} 景観={…} 経路={…}  |
| questmap/VerdantRoadQuestService.kt:443 | ms | 生成={…}ms 転送={…}ms |
| questmap/VerdantRoadQuestService.kt:450 | MOD | マップMOD |
| questmap/VerdantRoadQuestService.kt:477 | ProjectS | ProjectSの拠点へ戻りました。 |

## 書き方だけが違う名前（自動で拾った組）

「の」・長音・中黒・送り仮名・熔/溶 を無視すると同じになる語。動詞の形の違いだけのもの（帰還／帰還する など）も混ざっています。

| 揃えた形 | 出てくる書き方（回数） | 場所の例 |
|---|---|---|
| Aspect合成 | Aspectの合成（1） / Aspect合成へ（1） | FirstMagicWorkshop.kt:459 FirstMagicWorkshop.kt:514 |
| 交換 | 交換について（1） / 交換する（1） | CoreLoopMenus.kt:1424 CoreLoopMenus.kt:1442 |
| 修理 | 修理へ（2） / 修理（1） | CoreLoopMenus.kt:648 CoreLoopMenus.kt:1408 |
| 全力一振 | 全力の一振り（2） / 全力一振（1） | CorePlayerCombat.kt:745 CoreWarriorSkillPresentation.kt:15 |
| 制作 | 制作できます（1） / 制作する（1） / 制作（1） | CoreLoopMenus.kt:732 CoreLoopMenus.kt:736 CoreForgeLayout.kt:8 |
| 刻印 | 刻印（1） / 刻印する（1） | CoreLoopMenus.kt:537 CoreLoopMenus.kt:585 |
| 刻印石 | の刻印石（3） / 刻印石（1） | CoreLoopItems.kt:179 CoreLoopItems.kt:190 |
| 加工 | 加工する（2） / 加工へ（1） | CoreLoopMenus.kt:585 CoreLoopMenus.kt:1410 |
| 報酬 | 報酬あり（1） / 報酬（1） | GrimoireFlow.kt:437 GrimoireFlow.kt:540 |
| 報酬を受け取 | 報酬を受け取れる（1） / 報酬を受け取る（1） | GrimoireFlow.kt:411 GrimoireFlow.kt:552 |
| 工房 | 工房（1） / 工房へ（1） | CoreLoopMenus.kt:173 CoreLoopMenus.kt:886 |
| 市場 | 市場（2） / 市場へ（4） | CoreLoopMenus.kt:177 CoreLoopMenus.kt:962 |
| 帰還 | 帰還（1） / 帰還する（2） | CoreLoopMenus.kt:182 CoreLoopMenus.kt:1377 |
| 強化 | 強化する（3） / 強化できません（1） / 強化（1） | CoreLoopMenus.kt:584 CoreLoopMenus.kt:672 CoreForgeLayout.kt:8 |
| 手帳 | 手帳（4） / 手帳へ（4） | CoreLoopMenus.kt:210 CoreLoopMenus.kt:958 |
| 挑戦 | 挑戦できます（1） / 挑戦（1） | CoreLoopMenus.kt:1507 CoreLoopMenus.kt:1510 |
| 採取石板 | 採取の石板（2） / 採取石板（1） | CoreAccount.kt:11 CoreLoopMenus.kt:131 |
| 探索 | 探索（1） / 探索へ（1） | CoreLoopMenus.kt:181 CoreLoopMenus.kt:269 |
| 操作 | 操作できません（2） / 操作（1） | CoreAccountService.kt:77 CoreLoopMenus.kt:1448 |
| 星鉄触媒 | 星鉄の触媒（2） / 星鉄触媒（1） | CoreAccount.kt:16 CoreLoopMenus.kt:849 |
| 木岩草死体 | 木・岩・草・死体（1） / 木・岩・草・死体へ（1） | CoreLoopMenus.kt:1186 CoreLoopMenus.kt:1470 |
| 注文へ納品 | 注文へ納品（1） / 注文へ納品する（1） | CoreAccountService.kt:619 CoreLoopMenus.kt:1203 |
| 消費 | 消費なし（1） / 消費するもの（1） | CoreLoopMenus.kt:575 CoreLoopMenus.kt:1438 |
| 港で使用 | 港で使用（1） / 港で使用できます（1） | CoreLoopMenus.kt:595 CoreLoopMenus.kt:1410 |
| 狂戦士甲冑 | 狂戦士の甲冑（2） / 狂戦士甲冑（1） | CorePlayerCombat.kt:326 CoreWarriorSkillPresentation.kt:16 |
| 研究星図 | 研究の星図（3） / 研究の星図へ（1） | FirstMagicWorkshop.kt:291 FirstMagicWorkshop.kt:551 |
| 研究机 | 研究机（3） / 研究机へ（3） | FirstMagicColonyLayout.kt:15 FirstMagicWorkshop.kt:323 |
| 破損 | 破損（3） / 破損していません（1） | CoreLoopMenus.kt:590 CoreLoopMenus.kt:1016 |
| 精製 | 精製する（1） / 精製（1） | CoreLoopMenus.kt:736 CoreForgeLayout.kt:8 |
| 素材を出品 | 素材を出品（1） / 素材を出品する（1） | CoreAccountService.kt:271 CoreLoopMenus.kt:1092 |
| 蒸留器 | 蒸留器へ（2） / 蒸留器（2） | FirstMagicWorkshop.kt:295 GrimoireBook.kt:40 |
| 装備庫 | 装備庫（2） / 装備庫へ（1） | CoreAccountService.kt:566 CoreLoopMenus.kt:980 |
| 解明 | 解明そのものは（1） / 解明すると（1） | FirstMagicWorkshop.kt:241 FirstMagicWorkshop.kt:522 |
| 起点 | 起点へ（1） / 起点（1） | PassiveTreeFlow.kt:500 PassiveTreeFlow.kt:593 |
| 遠征 | 遠征（2） / 遠征へ（1） | CoreLoopMenus.kt:172 CoreLoopMenus.kt:269 |
| 魔導記録帳第一頁 | 魔導記録帳 第一頁（1） / 魔導記録帳・第一頁（1） | FirstMagicWorkshop.kt:391 FirstMagicWorkshop.kt:403 |

## 誤字の候補（自動の型）

| 場所 | 型 | 該当 | 文字列 |
|---|---|---|---|
| coreloop/AtlasMapFlow.kt:320 | 助詞の重複 | でで | このゾーンでできること |
| coreloop/GrimoireBook.kt:32 | 助詞の重複 | でで | 光る草に挟まっていた手帳。最初のページには一文だけ。「すべての物は、目に見えない性質（相）でできている」 |

## 全部の文字列

| 場所 | 幅 | 文字列 |
|---|---|---|
| coreloop/AspectResearch.kt:13 | 41 | Aer / 風 |
| coreloop/AspectResearch.kt:14 | 47 | Aqua / 水 |
| coreloop/AspectResearch.kt:15 | 47 | Ignis / 火 |
| coreloop/AspectResearch.kt:16 | 53 | Terra / 地 |
| coreloop/AspectResearch.kt:17 | 56 | Ordo / 秩序 |
| coreloop/AspectResearch.kt:18 | 70 | Perditio / 混沌 |
| coreloop/AspectResearch.kt:19 | 41 | Lux / 光 |
| coreloop/AspectResearch.kt:20 | 75 | Tempestas / 嵐 |
| coreloop/AspectResearch.kt:21 | 60 | Motus / 運動 |
| coreloop/AspectResearch.kt:22 | 68 | Vacuos / 虚空 |
| coreloop/AspectResearch.kt:23 | 62 | Victus / 生命 |
| coreloop/AspectResearch.kt:24 | 50 | Gelum / 氷 |
| coreloop/AspectResearch.kt:25 | 65 | Venenum / 毒 |
| coreloop/AspectResearch.kt:26 | 63 | Potentia / 力 |
| coreloop/AspectResearch.kt:27 | 63 | Mortuus / 死 |
| coreloop/AspectResearch.kt:28 | 72 | Metallum / 金属 |
| coreloop/AspectResearch.kt:101 | 18 | 星灯 |
| coreloop/AspectResearch.kt:101 | 63 | 光を器に留める |
| coreloop/AspectResearch.kt:102 | 54 | 生きたインク |
| coreloop/AspectResearch.kt:102 | 81 | 観測を記録へ変える |
| coreloop/AspectResearch.kt:103 | 36 | 嵐ガラス |
| coreloop/AspectResearch.kt:103 | 81 | 空模様を閉じ込める |
| coreloop/AspectResearch.kt:104 | 27 | 鉱脈視 |
| coreloop/AspectResearch.kt:104 | 72 | 金属の眠りを聴く |
| coreloop/AspectResearch.kt:105 | 27 | 氷の封 |
| coreloop/AspectResearch.kt:105 | 72 | 水の動きを止める |
| coreloop/AspectResearch.kt:106 | 45 | 空洞レンズ |
| coreloop/AspectResearch.kt:106 | 81 | 見えない間隙を覗く |
| coreloop/AspectResearch.kt:107 | 36 | 動力刻印 |
| coreloop/AspectResearch.kt:107 | 63 | 動きを符へ移す |
| coreloop/AspectResearch.kt:108 | 36 | 毒の分離 |
| coreloop/AspectResearch.kt:108 | 90 | 水に混じる異物を知る |
| coreloop/AspectResearch.kt:109 | 36 | 熾火装置 |
| coreloop/AspectResearch.kt:109 | 63 | 熱を秩序へ導く |
| coreloop/AspectResearch.kt:110 | 18 | 終花 |
| coreloop/AspectResearch.kt:110 | 81 | 生命と終わりを結ぶ |
| coreloop/AtlasMapFlow.kt:246 | 36 | 開拓大陸 |
| coreloop/AtlasMapFlow.kt:289 | 18 | 拠点 |
| coreloop/AtlasMapFlow.kt:290 | 27 | 未解放 |
| coreloop/AtlasMapFlow.kt:291 | 36 | 踏破済み |
| coreloop/AtlasMapFlow.kt:292 | 27 | 遠征地 |
| coreloop/AtlasMapFlow.kt:298 | 44 | 敵Lv {…} |
| coreloop/AtlasMapFlow.kt:308 | 77 | {…} · 特産 {…} |
| coreloop/AtlasMapFlow.kt:308 | 54 | 遠征の出発点 |
| coreloop/AtlasMapFlow.kt:313 | 72 | 道の終わりのボス |
| coreloop/AtlasMapFlow.kt:313 | 9 | 港 |
| coreloop/AtlasMapFlow.kt:315 | 48 | p-card-n |
| coreloop/AtlasMapFlow.kt:315 | 27 | 開拓港 |
| coreloop/AtlasMapFlow.kt:315 | 40 | #f4eedf |
| coreloop/AtlasMapFlow.kt:315 | 18 | left |
| coreloop/AtlasMapFlow.kt:317 | 103 | 弱点 {…} · 倒すと踏破 |
| coreloop/AtlasMapFlow.kt:317 | 90 | 遠征から戻るのもここ |
| coreloop/AtlasMapFlow.kt:320 | 99 | このゾーンでできること |
| coreloop/AtlasMapFlow.kt:322 | 36 | 討伐遠征 |
| coreloop/AtlasMapFlow.kt:322 | 169 | 毎回ちがう島 · 敵の群れ · 最後にボス |
| coreloop/AtlasMapFlow.kt:323 | 18 | 採取 |
| coreloop/AtlasMapFlow.kt:323 | 118 | {…}の採取点が半分を占める |
| coreloop/AtlasMapFlow.kt:324 | 27 | 寄り道 |
| coreloop/AtlasMapFlow.kt:324 | 115 | 隠し宝箱 · 裂け目 · 儀式 |
| coreloop/AtlasMapFlow.kt:326 | 36 | 港の施設 |
| coreloop/AtlasMapFlow.kt:326 | 151 | 鍛冶工房 · 交易市場 · 所持品目録 |
| coreloop/AtlasMapFlow.kt:327 | 36 | 遠征受付 |
| coreloop/AtlasMapFlow.kt:327 | 126 | この地図で行き先を選んで出発 |
| coreloop/AtlasMapFlow.kt:342 | 18 | 解放 |
| coreloop/AtlasMapFlow.kt:342 | 47 | T{…} まで |
| coreloop/AtlasMapFlow.kt:343 | 18 | 踏破 |
| coreloop/AtlasMapFlow.kt:343 | 74 | {…} / {…} 地域 |
| coreloop/AtlasMapFlow.kt:344 | 18 | 記録 |
| coreloop/AtlasMapFlow.kt:344 | 94 | ボス討伐 合計 {…} 回 |
| coreloop/AtlasMapFlow.kt:346 | 18 | 解放 |
| coreloop/AtlasMapFlow.kt:347 | 18 | 費用 |
| coreloop/AtlasMapFlow.kt:347 | 97 | {…} ×1（所持 {…}） |
| coreloop/AtlasMapFlow.kt:347 | 18 | 無料 |
| coreloop/AtlasMapFlow.kt:349 | 18 | 装備 |
| coreloop/AtlasMapFlow.kt:349 | 111 | 武器 T{…} · 防具 T{…} |
| coreloop/AtlasMapFlow.kt:351 | 18 | 記録 |
| coreloop/AtlasMapFlow.kt:351 | 137 | 踏破 {…} 回 · 最高 敵Lv {…} |
| coreloop/AtlasMapFlow.kt:351 | 81 | まだ踏破していない |
| coreloop/AtlasMapFlow.kt:362 | 105 | 採取できる素材 · T{…} |
| coreloop/AtlasMapFlow.kt:363 | 45 | 採取点の数 |
| coreloop/AtlasMapFlow.kt:377 | 45 | 読み込み中 |
| coreloop/AtlasMapFlow.kt:378 | 36 | 港へ戻る |
| coreloop/AtlasMapFlow.kt:378 | 45 | 遠征に出る |
| coreloop/AtlasMapFlow.kt:395 | 18 | 緑野 |
| coreloop/AtlasMapFlow.kt:395 | 27 | 桜の森 |
| coreloop/AtlasMapFlow.kt:396 | 18 | 塩沼 |
| coreloop/AtlasMapFlow.kt:396 | 18 | 断崖 |
| coreloop/AtlasMapFlow.kt:396 | 18 | 高地 |
| coreloop/AtlasMapFlow.kt:397 | 36 | 獄炎の地 |
| coreloop/AtlasMapFlow.kt:398 | 9 | 氷 |
| coreloop/AtlasMapFlow.kt:398 | 9 | 雷 |
| coreloop/AtlasMapFlow.kt:398 | 9 | 炎 |
| coreloop/ClassSelectScene.kt:146 | 38 |   見る    |
| coreloop/ClassSelectScene.kt:146 | 30 | Space |
| coreloop/ClassSelectScene.kt:147 | 38 |   選ぶ    |
| coreloop/ClassSelectScene.kt:147 | 26 |   戻る |
| coreloop/ClassSelectScene.kt:152 | 27 | 決める |
| coreloop/ClassSelectScene.kt:152 | 36 | choose |
| coreloop/ClassSelectScene.kt:153 | 27 | やめる |
| coreloop/ClassSelectScene.kt:153 | 33 | cancel |
| coreloop/ClassSelectScene.kt:154 | 55 | {…}にする？ |
| coreloop/ClassSelectScene.kt:155 | 108 | 港でいつでも選び直せます |
| coreloop/ClassSelectScene.kt:226 | 27 | 開発中 |
| coreloop/ClassSelectScene.kt:238 | 22 | 資源  |
| coreloop/ClassSelectScene.kt:239 | 22 | 体力  |
| coreloop/ClassSelectScene.kt:240 | 30 |   防御  |
| coreloop/ClassSelectScene.kt:241 | 30 |   魔防  |
| coreloop/ClassSelectScene.kt:242 | 31 | スキル  |
| coreloop/ClassSelectScene.kt:242 | 39 |   ウルト  |
| coreloop/ClassSelectScene.kt:244 | 30 | Space |
| coreloop/ClassSelectScene.kt:244 | 26 |   選ぶ |
| coreloop/ClassSelectScene.kt:244 | 27 | 開発中 |
| coreloop/ClassSelectScene.kt:353 | 107 | projects_ui_polish05 |
| coreloop/ClassSelectScene.kt:353 | 67 | menu4_{…}_0 |
| coreloop/CoreAccount.kt:9 | 18 | 木材 |
| coreloop/CoreAccount.kt:9 | 18 | 鉱石 |
| coreloop/CoreAccount.kt:9 | 18 | 石材 |
| coreloop/CoreAccount.kt:9 | 18 | 獣皮 |
| coreloop/CoreAccount.kt:9 | 36 | 植物繊維 |
| coreloop/CoreAccount.kt:10 | 18 | 板材 |
| coreloop/CoreAccount.kt:10 | 45 | インゴット |
| coreloop/CoreAccount.kt:10 | 36 | 加工石材 |
| coreloop/CoreAccount.kt:10 | 36 | なめし革 |
| coreloop/CoreAccount.kt:10 | 9 | 布 |
| coreloop/CoreAccount.kt:11 | 27 | 討伐証 |
| coreloop/CoreAccount.kt:11 | 36 | 戦利品券 |
| coreloop/CoreAccount.kt:11 | 27 | 回復薬 |
| coreloop/CoreAccount.kt:11 | 45 | 採取の石板 |
| coreloop/CoreAccount.kt:11 | 18 | 砥石 |
| coreloop/CoreAccount.kt:12 | 27 | 刻印粉 |
| coreloop/CoreAccount.kt:14 | 27 | 月鈴草 |
| coreloop/CoreAccount.kt:14 | 27 | 熾火苔 |
| coreloop/CoreAccount.kt:14 | 36 | 空洞水晶 |
| coreloop/CoreAccount.kt:15 | 36 | 微熱鉱片 |
| coreloop/CoreAccount.kt:15 | 18 | 潮羽 |
| coreloop/CoreAccount.kt:15 | 36 | 枯れた核 |
| coreloop/CoreAccount.kt:16 | 45 | 星鉄の触媒 |
| coreloop/CoreAccount.kt:16 | 45 | 修復の封蝋 |
| coreloop/CoreAccount.kt:165 | 137 | スキル Lv の割り振りが不正です |
| coreloop/CoreAccount.kt:353 | 74 | T{…} 武器へ鍛造 |
| coreloop/CoreAccount.kt:358 | 83 | T{…} 防具へ仕立て |
| coreloop/CoreAccount.kt:364 | 46 | {…}を精製 |
| coreloop/CoreAccount.kt:369 | 91 | 戦利品券で{…}を交換 |
| coreloop/CoreAccount.kt:376 | 54 | 回復薬を調合 |
| coreloop/CoreAccount.kt:377 | 72 | 採取の石板を彫刻 |
| coreloop/CoreAccount.kt:379 | 45 | 砥石を加工 |
| coreloop/CoreAccountRepository.kt:58 | 198 | 取引の復旧待ちです。管理者に連絡してください |
| coreloop/CoreAccountRepository.kt:59 | 115 | 取引の復旧が必要です: {…} |
| coreloop/CoreAccountRepository.kt:68 | 171 | このデータは破損または未対応の形式です |
| coreloop/CoreAccountRepository.kt:83 | 117 | 保存データを読み込めません |
| coreloop/CoreAccountRepository.kt:89 | 81 | 取引の復旧待ちです |
| coreloop/CoreAccountRepository.kt:90 | 63 | 保存できません |
| coreloop/CoreAccountRepository.kt:93 | 126 | 破損データを上書きできません |
| coreloop/CoreAccountRepository.kt:134 | 81 | 保存に失敗しました |
| coreloop/CoreAccountRepository.kt:160 | 180 | 取引結果を復旧中です。再接続してください |
| coreloop/CoreAccountRepository.kt:268 | 207 | 旧形式のバックアップが既存データと一致しません |
| coreloop/CoreAccountRepository.kt:635 | 12 | all |
| coreloop/CoreAccountService.kt:35 | 117 | 市場の取引を復旧できません |
| coreloop/CoreAccountService.kt:61 | 153 | データの読み込みが完了していません |
| coreloop/CoreAccountService.kt:66 | 171 | 同じ操作番号に異なる内容が送られました |
| coreloop/CoreAccountService.kt:69 | 207 | 内容が更新されました。もう一度選択してください |
| coreloop/CoreAccountService.kt:72 | 234 | 保存履歴の上限に達しました。管理者に連絡してください |
| coreloop/CoreAccountService.kt:77 | 63 | 操作できません |
| coreloop/CoreAccountService.kt:90 | 243 | 保存内容が別の処理で更新されました。再接続してください |
| coreloop/CoreAccountService.kt:93 | 198 | 保存に失敗しました。素材は消費されていません |
| coreloop/CoreAccountService.kt:102 | 108 | スキル構成を保存しました |
| coreloop/CoreAccountService.kt:108 | 82 | {…}を取得しました |
| coreloop/CoreAccountService.kt:116 | 118 | 極意「{…}」を取得しました |
| coreloop/CoreAccountService.kt:124 | 91 | {…}点を取得しました |
| coreloop/CoreAccountService.kt:130 | 73 | {…}を戻しました |
| coreloop/CoreAccountService.kt:135 | 117 | パッシブをすべて戻しました |
| coreloop/CoreAccountService.kt:141 | 116 | {…}を Lv {…} にしました |
| coreloop/CoreAccountService.kt:151 | 208 | スキルポイントをすべて戻しました（銀貨{…}枚） |
| coreloop/CoreAccountService.kt:165 | 280 | {…}を選びました。手帳の「成長と職業」で次の目標を確認できます |
| coreloop/CoreAccountService.kt:169 | 108 | 戦闘の基本を習得しました |
| coreloop/CoreAccountService.kt:182 | 202 | 装備Lv{…}。MOD・品質・強化は維持しています |
| coreloop/CoreAccountService.kt:191 | 189 | 購入注文を掲示しました。代金は預託済みです |
| coreloop/CoreAccountService.kt:196 | 126 | 未成立分の銀貨を返却しました |
| coreloop/CoreAccountService.kt:204 | 232 | 採取実績でT{…}の地図を用意しました。討伐は不要です |
| coreloop/CoreAccountService.kt:222 | 247 | T{…} {…}を{…}個制作。製造品質は装備庫で確認できます |
| coreloop/CoreAccountService.kt:229 | 235 | {…}を装備しました。以前の装備は装備庫へ保管しました |
| coreloop/CoreAccountService.kt:243 | 221 | 港へ納品しました。銀貨{…}枚を獲得（本日{…}/3） |
| coreloop/CoreAccountService.kt:247 | 63 | 戦利品券を換金 |
| coreloop/CoreAccountService.kt:249 | 253 | 銀貨{…}枚を獲得しました。市場で素材や装備を購入できます |
| coreloop/CoreAccountService.kt:260 | 411 | 修理材料の装備1個を消費して修理しました。対象のMOD・強化値・天井・識別番号は維持しています |
| coreloop/CoreAccountService.kt:271 | 45 | 素材を出品 |
| coreloop/CoreAccountService.kt:278 | 144 | 出品を取り下げ素材を返却しました |
| coreloop/CoreAccountService.kt:279 | 198 | 出品を取り下げました。装備庫から使用できます |
| coreloop/CoreAccountService.kt:289 | 110 | {…}を{…}個保管しました |
| coreloop/CoreAccountService.kt:296 | 127 | 戦利品券を{…}枚獲得しました |
| coreloop/CoreAccountService.kt:319 | 182 | 戦利品を回収：オーブ{…}個・刻印粉{…}個 |
| coreloop/CoreAccountService.kt:328 | 218 | {…}に{…}を使用しました。結果を確認してください |
| coreloop/CoreAccountService.kt:343 | 178 | 旧石を改変{…}個・錬金1個へ交換しました |
| coreloop/CoreAccountService.kt:349 | 221 | {…}を制覇！ 専用オーブと入場の欠片を獲得しました |
| coreloop/CoreAccountService.kt:360 | 68 | {…}を初踏破！  |
| coreloop/CoreAccountService.kt:360 | 258 | 討伐完了！ T{…} の地図・討伐証・試練の欠片を獲得しました |
| coreloop/CoreAccountService.kt:361 | 277 | ダンジョン踏破！ 高揚1個・試練の欠片1個・討伐証を獲得しました |
| coreloop/CoreAccountService.kt:362 | 268 | 専用ボス討伐！ 専用オーブ2個・高揚1個・討伐証を獲得しました |
| coreloop/CoreAccountService.kt:374 | 48 | boss-map |
| coreloop/CoreAccountService.kt:424 | 146 | T{…} の遠征地図を受け取りました |
| coreloop/CoreAccountService.kt:434 | 117 | 採取MODを地図に刻みました |
| coreloop/CoreAccountService.kt:443 | 90 | 遠征を準備しています |
| coreloop/CoreAccountService.kt:451 | 45 | trial-map |
| coreloop/CoreAccountService.kt:454 | 145 | {…}の専用ボス戦を準備しています |
| coreloop/CoreAccountService.kt:462 | 66 | dungeon-map |
| coreloop/CoreAccountService.kt:464 | 135 | ダンジョンへの扉を開いています |
| coreloop/CoreAccountService.kt:473 | 189 | 遠征の準備に失敗したため地図を返却しました |
| coreloop/CoreAccountService.kt:475 | 216 | ダンジョンの準備に失敗しました。もう一度入れます |
| coreloop/CoreAccountService.kt:478 | 204 | 準備に失敗したため入場の欠片3個を返却しました |
| coreloop/CoreAccountService.kt:484 | 180 | 拠点へ帰還しました。獲得品は保管済みです |
| coreloop/CoreAccountService.kt:490 | 46 | {…}を蒸留 |
| coreloop/CoreAccountService.kt:491 | 168 | {…}を蒸留器へ投入（燃料 銀貨{…}枚） |
| coreloop/CoreAccountService.kt:497 | 82 | {…}でインクを調合 |
| coreloop/CoreAccountService.kt:498 | 186 | {…}で研究インクを調合（溶剤 銀貨{…}枚） |
| coreloop/CoreAccountService.kt:505 | 277 | {…}を{…}個調合しました（調合料 銀貨{…}枚）。市場で売れます |
| coreloop/CoreAccountService.kt:512 | 72 | 修復の封蝋で修理 |
| coreloop/CoreAccountService.kt:515 | 316 | 修復の封蝋{…}個で修理しました。MOD・強化値・成功保証は維持しています |
| coreloop/CoreAccountService.kt:520 | 122 | Lv{…}・銀貨{…}にしました |
| coreloop/CoreAccountService.kt:525 | 99 | 研究素材を保管しました |
| coreloop/CoreAccountService.kt:527 | 99 | 研究素材を保管しました |
| coreloop/CoreAccountService.kt:532 | 82 | {…}を使用しました |
| coreloop/CoreAccountService.kt:542 | 281 | 銀貨{…}枚で出品しました。成約時に手数料{…}枚が差し引かれます |
| coreloop/CoreAccountService.kt:566 | 27 | 装備庫 |
| coreloop/CoreAccountService.kt:566 | 54 | インベントリ |
| coreloop/CoreAccountService.kt:566 | 200 | 銀貨{…}枚で購入しました。{…}で確認できます |
| coreloop/CoreAccountService.kt:570 | 63 | 購入できません |
| coreloop/CoreAccountService.kt:578 | 288 | 取引結果を確認できません。再接続すると保存済みの取引を復旧します |
| coreloop/CoreAccountService.kt:592 | 252 | 取引を保存できませんでした。銀貨と品物は移動していません |
| coreloop/CoreAccountService.kt:619 | 45 | 注文へ納品 |
| coreloop/CoreAccountService.kt:620 | 54 | 注文品を保管 |
| coreloop/CoreAccountService.kt:624 | 262 | 注文へ納品しました。手数料を引き銀貨{…}枚を受け取りました |
| coreloop/CoreAccountService.kt:627 | 63 | 納品できません |
| coreloop/CoreAccountService.kt:635 | 198 | 取引結果を確認できません。再接続で復旧します |
| coreloop/CoreAccountService.kt:682 | 83 | {…}（銀貨{…}枚） |
| coreloop/CoreAffixCatalog.kt:10 | 18 | 武器 |
| coreloop/CoreAffixCatalog.kt:10 | 18 | 防具 |
| coreloop/CoreAffixCatalog.kt:10 | 9 | 頭 |
| coreloop/CoreAffixCatalog.kt:10 | 9 | 胴 |
| coreloop/CoreAffixCatalog.kt:10 | 9 | 脚 |
| coreloop/CoreAffixCatalog.kt:10 | 9 | 足 |
| coreloop/CoreAffixCatalog.kt:20 | 18 | 接頭 |
| coreloop/CoreAffixCatalog.kt:20 | 18 | 接尾 |
| coreloop/CoreAffixCatalog.kt:21 | 18 | 攻撃 |
| coreloop/CoreAffixCatalog.kt:21 | 54 | スキル・資源 |
| coreloop/CoreAffixCatalog.kt:21 | 18 | 防御 |
| coreloop/CoreAffixCatalog.kt:21 | 18 | 機動 |
| coreloop/CoreAffixCatalog.kt:23 | 63 | 与ダメージ増加 |
| coreloop/CoreAffixCatalog.kt:23 | 36 | 攻撃速度 |
| coreloop/CoreAffixCatalog.kt:23 | 63 | スキルダメージ |
| coreloop/CoreAffixCatalog.kt:24 | 36 | 最大マナ |
| coreloop/CoreAffixCatalog.kt:24 | 54 | マナ回復速度 |
| coreloop/CoreAffixCatalog.kt:24 | 90 | クールダウン回復速度 |
| coreloop/CoreAffixCatalog.kt:25 | 30 | 最大HP |
| coreloop/CoreAffixCatalog.kt:25 | 63 | 被ダメージ軽減 |
| coreloop/CoreAffixCatalog.kt:25 | 36 | 移動速度 |
| coreloop/CoreAffixCatalog.kt:26 | 81 | クリティカル率増加 |
| coreloop/CoreAffixCatalog.kt:26 | 72 | クリティカル倍率 |
| coreloop/CoreAffixCatalog.kt:27 | 72 | 通常攻撃ダメージ |
| coreloop/CoreAffixCatalog.kt:27 | 36 | 詠唱速度 |
| coreloop/CoreAffixCatalog.kt:28 | 36 | 火属性値 |
| coreloop/CoreAffixCatalog.kt:28 | 36 | 氷属性値 |
| coreloop/CoreAffixCatalog.kt:28 | 36 | 雷属性値 |
| coreloop/CoreAffixCatalog.kt:29 | 61 | 物理攻撃力 AD |
| coreloop/CoreAffixCatalog.kt:29 | 61 | 物理攻撃力 AD |
| coreloop/CoreAffixCatalog.kt:30 | 61 | 魔法攻撃力 AP |
| coreloop/CoreAffixCatalog.kt:30 | 61 | 魔法攻撃力 AP |
| coreloop/CoreAffixCatalog.kt:31 | 52 | 物理防御 AR |
| coreloop/CoreAffixCatalog.kt:31 | 52 | 物理防御 AR |
| coreloop/CoreAffixCatalog.kt:32 | 52 | 魔法防御 MR |
| coreloop/CoreAffixCatalog.kt:32 | 52 | 魔法防御 MR |
| coreloop/CoreAffixCatalog.kt:33 | 54 | 物理ダメージ |
| coreloop/CoreAffixCatalog.kt:33 | 54 | 魔法ダメージ |
| coreloop/CoreAffixCatalog.kt:34 | 54 | 近接ダメージ |
| coreloop/CoreAffixCatalog.kt:34 | 63 | 投射物ダメージ |
| coreloop/CoreAffixCatalog.kt:35 | 36 | 物理貫通 |
| coreloop/CoreAffixCatalog.kt:35 | 36 | 物理貫通 |
| coreloop/CoreAffixCatalog.kt:36 | 36 | 魔法貫通 |
| coreloop/CoreAffixCatalog.kt:36 | 36 | 魔法貫通 |
| coreloop/CoreAffixCatalog.kt:37 | 30 | 最大HP |
| coreloop/CoreAffixCatalog.kt:37 | 36 | 最大マナ |
| coreloop/CoreAffixCatalog.kt:37 | 54 | 毎秒マナ回復 |
| coreloop/CoreAffixCatalog.kt:38 | 27 | 回復力 |
| coreloop/CoreAffixCatalog.kt:38 | 27 | 回復力 |
| coreloop/CoreAffixCatalog.kt:39 | 36 | 与回復量 |
| coreloop/CoreAffixCatalog.kt:39 | 36 | 被回復量 |
| coreloop/CoreAffixCatalog.kt:40 | 72 | ライフスティール |
| coreloop/CoreAffixCatalog.kt:41 | 45 | シールド量 |
| coreloop/CoreAffixCatalog.kt:42 | 30 | CC耐性 |
| coreloop/CoreAffixCatalog.kt:43 | 30 | CC時間 |
| coreloop/CoreAffixCatalog.kt:43 | 45 | ブレイク力 |
| coreloop/CoreAffixCatalog.kt:43 | 54 | 継続ダメージ |
| coreloop/CoreAffixCatalog.kt:43 | 72 | 回避の再使用速度 |
| coreloop/CoreAffixCatalog.kt:43 | 36 | 属性効果 |
| coreloop/CoreAffixCatalog.kt:44 | 57 | 撃破時HP回復 |
| coreloop/CoreAffixCatalog.kt:44 | 99 | 溢れた回復をシールドに |
| coreloop/CoreAffixCatalog.kt:45 | 45 | 戦士・余波 |
| coreloop/CoreAffixCatalog.kt:45 | 36 | 戦士・獣 |
| coreloop/CoreAffixCatalog.kt:45 | 54 | メイジ・残響 |
| coreloop/CoreAffixCatalog.kt:45 | 45 | 狩人・追矢 |
| coreloop/CoreAffixCatalog.kt:46 | 54 | 暗殺者・毒印 |
| coreloop/CoreAffixCatalog.kt:46 | 54 | 聖騎士・重力 |
| coreloop/CoreAffixCatalog.kt:46 | 54 | 治療師・予防 |
| coreloop/CoreAffixCatalog.kt:48 | 70 | 全部のスキル Lv |
| coreloop/CoreAffixCatalog.kt:50 | 43 | スキル Lv |
| coreloop/CoreAffixCatalog.kt:135 | 54 | 剛力の刻印石 |
| coreloop/CoreAffixCatalog.kt:136 | 54 | 疾撃の刻印石 |
| coreloop/CoreAffixCatalog.kt:137 | 81 | スキル威力の刻印石 |
| coreloop/CoreAffixCatalog.kt:138 | 54 | 魔力の刻印石 |
| coreloop/CoreAffixCatalog.kt:139 | 54 | 循環の刻印石 |
| coreloop/CoreAffixCatalog.kt:140 | 54 | 集中の刻印石 |
| coreloop/CoreAffixCatalog.kt:141 | 54 | 生命の刻印石 |
| coreloop/CoreAffixCatalog.kt:142 | 54 | 守護の刻印石 |
| coreloop/CoreAffixCatalog.kt:144 | 54 | 軽歩の刻印石 |
| coreloop/CoreAffixCatalog.kt:146 | 54 | 会心の刻印石 |
| coreloop/CoreAffixCatalog.kt:147 | 54 | 痛撃の刻印石 |
| coreloop/CoreAffixCatalog.kt:148 | 54 | 連撃の刻印石 |
| coreloop/CoreAffixCatalog.kt:149 | 54 | 速詠の刻印石 |
| coreloop/CoreAffixCatalog.kt:150 | 54 | 火炎の刻印石 |
| coreloop/CoreAffixCatalog.kt:151 | 54 | 氷結の刻印石 |
| coreloop/CoreAffixCatalog.kt:152 | 54 | 雷鳴の刻印石 |
| coreloop/CoreAffixCatalog.kt:153 | 45 | 刃の刻印石 |
| coreloop/CoreAffixCatalog.kt:154 | 54 | 武威の刻印石 |
| coreloop/CoreAffixCatalog.kt:155 | 54 | 叡智の刻印石 |
| coreloop/CoreAffixCatalog.kt:156 | 54 | 魔術の刻印石 |
| coreloop/CoreAffixCatalog.kt:157 | 54 | 鋼壁の刻印石 |
| coreloop/CoreAffixCatalog.kt:158 | 54 | 堅牢の刻印石 |
| coreloop/CoreAffixCatalog.kt:159 | 54 | 結界の刻印石 |
| coreloop/CoreAffixCatalog.kt:160 | 54 | 抗魔の刻印石 |
| coreloop/CoreAffixCatalog.kt:161 | 54 | 武力の刻印石 |
| coreloop/CoreAffixCatalog.kt:162 | 54 | 秘術の刻印石 |
| coreloop/CoreAffixCatalog.kt:163 | 54 | 接戦の刻印石 |
| coreloop/CoreAffixCatalog.kt:164 | 54 | 弾道の刻印石 |
| coreloop/CoreAffixCatalog.kt:165 | 54 | 穿甲の刻印石 |
| coreloop/CoreAffixCatalog.kt:166 | 54 | 破甲の刻印石 |
| coreloop/CoreAffixCatalog.kt:167 | 54 | 破魔の刻印石 |
| coreloop/CoreAffixCatalog.kt:168 | 54 | 解呪の刻印石 |
| coreloop/CoreAffixCatalog.kt:169 | 54 | 活力の刻印石 |
| coreloop/CoreAffixCatalog.kt:170 | 54 | 深泉の刻印石 |
| coreloop/CoreAffixCatalog.kt:171 | 54 | 湧泉の刻印石 |
| coreloop/CoreAffixCatalog.kt:172 | 54 | 癒力の刻印石 |
| coreloop/CoreAffixCatalog.kt:173 | 54 | 慈愛の刻印石 |
| coreloop/CoreAffixCatalog.kt:174 | 54 | 施療の刻印石 |
| coreloop/CoreAffixCatalog.kt:175 | 54 | 受容の刻印石 |
| coreloop/CoreAffixCatalog.kt:176 | 54 | 吸命の刻印石 |
| coreloop/CoreAffixCatalog.kt:177 | 54 | 庇護の刻印石 |
| coreloop/CoreAffixCatalog.kt:178 | 54 | 不動の刻印石 |
| coreloop/CoreAffixCatalog.kt:179 | 54 | 余波の刻印石 |
| coreloop/CoreAffixCatalog.kt:180 | 45 | 獣の刻印石 |
| coreloop/CoreAffixCatalog.kt:181 | 54 | 残響の刻印石 |
| coreloop/CoreAffixCatalog.kt:182 | 54 | 追矢の刻印石 |
| coreloop/CoreAffixCatalog.kt:183 | 54 | 毒印の刻印石 |
| coreloop/CoreAffixCatalog.kt:184 | 54 | 重力の刻印石 |
| coreloop/CoreAffixCatalog.kt:185 | 54 | 予防の刻印石 |
| coreloop/CoreAffixCatalog.kt:193 | 72 | 全スキルの刻印石 |
| coreloop/CoreAffixCatalog.kt:196 | 55 | {…}の刻印石 |
| coreloop/CoreAffixCatalog.kt:209 | 81 | 霜転の調律（試作） |
| coreloop/CoreAffixCatalog.kt:242 | 108 | 未対応のMOD（効果停止） |
| coreloop/CoreAffixCatalog.kt:243 | 108 | 不正なMOD値（効果停止） |
| coreloop/CoreAffixCatalog.kt:246 | 180 | 戦士：範囲+{…}% / 範囲スキルの威力85% |
| coreloop/CoreAffixCatalog.kt:247 | 103 | 戦士：獣の溜まり+{…}% |
| coreloop/CoreAffixCatalog.kt:248 | 141 | メイジ：起爆+1回 / 各{…}%威力 |
| coreloop/CoreAffixCatalog.kt:249 | 159 | 狩人：スキル射撃+1回 / 各{…}%威力 |
| coreloop/CoreAffixCatalog.kt:250 | 182 | 暗殺者：印を付けた敵に{…}% ADの毒を3回 |
| coreloop/CoreAffixCatalog.kt:251 | 135 | 聖騎士：吸引+{…}% / 威力80% |
| coreloop/CoreAffixCatalog.kt:252 | 169 | 治療師：回復の{…}%を同量の障壁に変換 |
| coreloop/CoreAffixCatalog.kt:253 | 99 | 全部のスキル Lv +{…} |
| coreloop/CoreAffixCatalog.kt:279 | 63 | MODを石に抽出 |
| coreloop/CoreAffixCatalog.kt:280 | 90 | 刻印石の数値を再抽選 |
| coreloop/CoreBuyOrders.kt:19 | 58 | greatsword |
| coreloop/CoreBuyOrders.kt:19 | 18 | 大剣 |
| coreloop/CoreBuyOrders.kt:19 | 18 | bow |
| coreloop/CoreBuyOrders.kt:19 | 18 | 長弓 |
| coreloop/CoreBuyOrders.kt:19 | 26 | staff |
| coreloop/CoreBuyOrders.kt:19 | 9 | 杖 |
| coreloop/CoreBuyOrders.kt:19 | 36 | dagger |
| coreloop/CoreBuyOrders.kt:19 | 18 | 短剣 |
| coreloop/CoreBuyOrders.kt:19 | 24 | mace |
| coreloop/CoreBuyOrders.kt:19 | 18 | 騎槍 |
| coreloop/CoreBuyOrders.kt:19 | 22 | tome |
| coreloop/CoreBuyOrders.kt:19 | 18 | 聖典 |
| coreloop/CoreCombatLab.kt:91 | 108 | テスト用マップを準備中… |
| coreloop/CoreCombatLab.kt:92 | 45 | ボス試験場 |
| coreloop/CoreCombatLab.kt:92 | 54 | スキル試験場 |
| coreloop/CoreCombatLab.kt:92 | 36 | 制限なし |
| coreloop/CoreCombatLab.kt:92 | 36 | 通常条件 |
| coreloop/CoreCombatLab.kt:92 | 129 | {…} / {…} / 設定は手帳 [9] |
| coreloop/CoreCombatLab.kt:100 | 189 | 港へ帰還してから {…} を実行してください。 |
| coreloop/CoreCombatLab.kt:105 | 288 | ボス試験場を準備しています。報酬・成長・装備の保存はありません。 |
| coreloop/CoreCombatLab.kt:106 | 360 | テスト専用のランダムマップを生成しています。報酬・成長・装備の保存はありません。 |
| coreloop/CoreCombatLab.kt:119 | 306 | テスト用マップを生成できませんでした。保存データは変更していません。 |
| coreloop/CoreCombatLab.kt:130 | 235 | 倒れた。ボスも最初からやり直し（ポーション{…}個）。 |
| coreloop/CoreCombatLab.kt:150 | 563 | {…}に到着（Tier{…}）。{…}が待っています。ポーションは{…}個・再使用10秒。手帳 [9] で職業・スキル・再配置、帰還は /hub。 |
| coreloop/CoreCombatLab.kt:151 | 400 | 試験場に到着。手帳 [9] または /skilltest で職業・スキル・敵を変更できます。帰還は /hub。 |
| coreloop/CoreCombatLab.kt:168 | 302 | {…}の主を討伐（テストのため報酬なし）。手帳 [9] で再配置できます。 |
| coreloop/CoreCombatLab.kt:222 | 81 | スキル試験場の設定 |
| coreloop/CoreCombatLab.kt:223 | 207 | 右クリック：職業・スキル・試験条件・敵の再配置 |
| coreloop/CoreCombatLab.kt:226 | 118 | 回復ポーション（残り{…}） |
| coreloop/CoreCombatLab.kt:227 | 263 | 右クリック：最大HPの45%を回復 / 再使用10秒（本番と同じ） |
| coreloop/CoreCombatLab.kt:229 | 72 | テスト用の全補充 |
| coreloop/CoreCombatLab.kt:230 | 237 | 右クリック：HP・マナ・固有ゲージ・クールダウンを補充 |
| coreloop/CoreCombatLab.kt:242 | 99 | ポーションがもうない。 |
| coreloop/CoreCombatLab.kt:243 | 145 | ポーションはあと{…}秒で使える。 |
| coreloop/CoreCombatLab.kt:257 | 180 | マップを準備中です。少しお待ちください。 |
| coreloop/CoreCombatLab.kt:258 | 180 | マップを準備中です。少しお待ちください。 |
| coreloop/CoreCombatLab.kt:268 | 36 | 選択中： |
| coreloop/CoreCombatLab.kt:268 | 27 | 職業： |
| coreloop/CoreCombatLab.kt:269 | 180 | テスト専用。元の職業・装備は変更しません |
| coreloop/CoreCombatLab.kt:273 | 36 | 変更先： |
| coreloop/CoreCombatLab.kt:273 | 18 | 枠： |
| coreloop/CoreCombatLab.kt:274 | 171 | この枠を選び、その下のスキルをクリック |
| coreloop/CoreCombatLab.kt:282 | 100 | クリック：枠{…}へ設定 |
| coreloop/CoreCombatLab.kt:282 | 99 | クリック：ウルトへ設定 |
| coreloop/CoreCombatLab.kt:285 | 90 | 全回復・再使用を補充 |
| coreloop/CoreCombatLab.kt:285 | 183 | HP・マナ・固有ゲージ・クールダウンを補充 |
| coreloop/CoreCombatLab.kt:286 | 36 | 制限なし |
| coreloop/CoreCombatLab.kt:286 | 36 | 通常条件 |
| coreloop/CoreCombatLab.kt:286 | 46 | 条件：{…} |
| coreloop/CoreCombatLab.kt:286 | 260 | 制限なし＝毎tick補充・無敵。通常条件＝実際の資源と待ち時間 |
| coreloop/CoreCombatLab.kt:289 | 54 | ボスを再配置 |
| coreloop/CoreCombatLab.kt:289 | 237 | HP満タンで初期位置に戻す。ポーションも戻す。報酬なし |
| coreloop/CoreCombatLab.kt:291 | 81 | 目の前に敵を再配置 |
| coreloop/CoreCombatLab.kt:291 | 219 | HP20倍の敵3体。古い敵と攻撃状態は消去。報酬なし |
| coreloop/CoreCombatLab.kt:293 | 288 | 敵を置ける広さがありません。道など広い場所で再試行してください。 |
| coreloop/CoreCombatLab.kt:295 | 18 | あり |
| coreloop/CoreCombatLab.kt:295 | 18 | なし |
| coreloop/CoreCombatLab.kt:295 | 64 | 敵の反撃：{…} |
| coreloop/CoreCombatLab.kt:295 | 243 | 反撃なしは演出確認用。戦闘も試すなら通常条件と反撃あり |
| coreloop/CoreCombatLab.kt:297 | 288 | 敵を置ける広さがありません。道など広い場所で再試行してください。 |
| coreloop/CoreCombatLab.kt:299 | 108 | 初期化・固有ゲージをゼロ |
| coreloop/CoreCombatLab.kt:299 | 198 | 通常条件へ切替。スタックを貯める動作から試す |
| coreloop/CoreCombatLab.kt:302 | 36 | 港へ帰還 |
| coreloop/CoreCombatLab.kt:302 | 189 | テスト場を破棄し、元の装備・職業に戻ります |
| coreloop/CoreCombatLab.kt:303 | 64 | 戦闘演出：{…} |
| coreloop/CoreCombatLab.kt:303 | 313 | 華やか → 控えめ → 最小。自分の表示だけ変更、再接続で華やかに戻ります |
| coreloop/CoreCombatLab.kt:306 | 45 | 閉じて試す |
| coreloop/CoreCombatLab.kt:306 | 216 | ホットバー2～6のスキルを右クリック。設定は手帳9 |
| coreloop/CoreCombatLab.kt:307 | 151 | スキル試験場：職業 → 枠 → スキル |
| coreloop/CoreCombatLab.kt:328 | 360 | 帰還先へ移動できませんでした。再接続してください。保存データは変更していません。 |
| coreloop/CoreCombatMath.kt:8 | 71 | 物理 / ARで軽減 |
| coreloop/CoreCombatMath.kt:8 | 71 | 魔法 / MRで軽減 |
| coreloop/CoreCombatMath.kt:8 | 68 | 確定 / 防御無視 |
| coreloop/CoreCombatMeshes.kt:20 | 27 | 華やか |
| coreloop/CoreCombatMeshes.kt:20 | 27 | 控えめ |
| coreloop/CoreCombatMeshes.kt:20 | 18 | 最小 |
| coreloop/CoreCraftingCurrency.kt:9 | 36 | ノーマル |
| coreloop/CoreCraftingCurrency.kt:9 | 36 | マジック |
| coreloop/CoreCraftingCurrency.kt:9 | 18 | レア |
| coreloop/CoreCraftingCurrency.kt:13 | 54 | 変成のオーブ |
| coreloop/CoreCraftingCurrency.kt:13 | 54 | 増強のオーブ |
| coreloop/CoreCraftingCurrency.kt:13 | 54 | 改変のオーブ |
| coreloop/CoreCraftingCurrency.kt:14 | 54 | 錬金のオーブ |
| coreloop/CoreCraftingCurrency.kt:14 | 54 | 混沌のオーブ |
| coreloop/CoreCraftingCurrency.kt:14 | 54 | 富豪のオーブ |
| coreloop/CoreCraftingCurrency.kt:15 | 54 | 高揚のオーブ |
| coreloop/CoreCraftingCurrency.kt:15 | 54 | 洗浄のオーブ |
| coreloop/CoreCraftingCurrency.kt:15 | 54 | 神聖のオーブ |
| coreloop/CoreCraftingCurrency.kt:16 | 63 | 裂け目のオーブ |
| coreloop/CoreCraftingCurrency.kt:16 | 54 | 儀式のオーブ |
| coreloop/CoreCraftingCurrency.kt:16 | 54 | 試練のオーブ |
| coreloop/CoreCraftingCurrency.kt:16 | 54 | 星環のオーブ |
| coreloop/CoreCraftingCurrency.kt:21 | 234 | 星環のオーブは終了しました。旧残高は保管されています |
| coreloop/CoreCraftingCurrency.kt:26 | 27 | 裂け目 |
| coreloop/CoreCraftingCurrency.kt:27 | 18 | 儀式 |
| coreloop/CoreCraftingCurrency.kt:28 | 18 | 試練 |
| coreloop/CoreCraftingCurrency.kt:44 | 237 | ノーマルをマジックに変え、ランダムなMODを1〜2個付与 |
| coreloop/CoreCraftingCurrency.kt:45 | 186 | マジックの空き枠へランダムなMODを1個追加 |
| coreloop/CoreCraftingCurrency.kt:46 | 192 | マジックのMODをすべて消し、1〜2個を再抽選 |
| coreloop/CoreCraftingCurrency.kt:47 | 219 | ノーマルをレアに変え、ランダムなMODを4〜6個付与 |
| coreloop/CoreCraftingCurrency.kt:48 | 174 | レアのMODをすべて消し、4〜6個を再抽選 |
| coreloop/CoreCraftingCurrency.kt:49 | 186 | マジックのMODを残してレアに変え、1個追加 |
| coreloop/CoreCraftingCurrency.kt:50 | 168 | レアの空き枠へランダムなMODを1個追加 |
| coreloop/CoreCraftingCurrency.kt:51 | 216 | MODをすべて消し、ノーマルに戻す（取り消し不可） |
| coreloop/CoreCraftingCurrency.kt:52 | 263 | MODの種類とTierを保ち、数値だけ再抽選（低下する場合あり） |
| coreloop/CoreCraftingCurrency.kt:53 | 261 | 全MODを再抽選し、元素MODを1個以上含むレア4〜6個へ変える |
| coreloop/CoreCraftingCurrency.kt:54 | 294 | MODの数値をそれぞれ2回抽選し、高い方を採用（元の値を保証しない） |
| coreloop/CoreCraftingCurrency.kt:55 | 234 | レアの空き枠へ、数値範囲の上位25％からMODを1個追加 |
| coreloop/CoreCraftingCurrency.kt:62 | 99 | 拠点で操作してください |
| coreloop/CoreCraftingCurrency.kt:63 | 198 | 破損しています。先に装備庫で修理してください |
| coreloop/CoreCraftingCurrency.kt:64 | 73 | {…}が足りません |
| coreloop/CoreCraftingCurrency.kt:69 | 117 | ノーマル装備に使用できます |
| coreloop/CoreCraftingCurrency.kt:70 | 117 | マジック装備に使用できます |
| coreloop/CoreCraftingCurrency.kt:71 | 99 | レア装備に使用できます |
| coreloop/CoreCraftingCurrency.kt:74 | 117 | マジック装備に使用できます |
| coreloop/CoreCraftingCurrency.kt:75 | 225 | 旧形式のMOD構成です。改変で新形式へ移してください |
| coreloop/CoreCraftingCurrency.kt:76 | 81 | すでにノーマルです |
| coreloop/CoreCraftingCurrency.kt:77 | 117 | 再抽選するMODがありません |
| coreloop/CoreCraftingCurrency.kt:78 | 198 | 未対応のMODは数値を変更せず保管してください |
| coreloop/CoreCraftingCurrency.kt:85 | 100 | {…}装備に使用できます |
| coreloop/CoreCraftingCurrency.kt:86 | 234 | 旧形式のMOD構成です。再鋳造で新形式へ移してください |
| coreloop/CoreCraftingCurrency.kt:87 | 72 | MOD枠が満杯です |
| coreloop/CoreCraftingCurrency.kt:172 | 173 | projects/legacy-craft-seed/{…} |
| coreloop/CoreEconomy.kt:33 | 36 | 【破損】 |
| coreloop/CoreEconomy.kt:33 | 112 | T{…} Lv{…} {…} +{…} |
| coreloop/CoreEconomy.kt:77 | 75 | T{…} {…}を制作 |
| coreloop/CoreEnhancementCatalog.kt:8 | 36 | 通常強化 |
| coreloop/CoreEnhancementCatalog.kt:8 | 63 | 精錬触媒を使う |
| coreloop/CoreEnhancementCatalog.kt:104 | 84 | {…}を+{…} へ強化 |
| coreloop/CoreEnhancementCatalog.kt:106 | 198 | 破損しています。先に装備庫で修理してください |
| coreloop/CoreEnhancementCatalog.kt:107 | 114 | すでに最大強化 +{…} です |
| coreloop/CoreEnhancementCatalog.kt:108 | 99 | 拠点で操作してください |
| coreloop/CoreEnhancementCatalog.kt:109 | 90 | 強化素材が足りません |
| coreloop/CoreEnhancementCatalog.kt:135 | 234 | 強化成功！ {…}が +{…} になりました（鍛冶経験 +1） |
| coreloop/CoreEnhancementCatalog.kt:136 | 520 | 強化失敗で破損しました。装備庫で同Tier・同系統・+0の装備1個を使い修理できます。+{…}・MOD・天井 {…}/{…} は維持 |
| coreloop/CoreEnhancementCatalog.kt:137 | 350 | 強化失敗。破損はありません。+{…}・MODは維持。天井 {…}/{…}（鍛冶経験 +1） |
| coreloop/CoreFrontierZones.kt:52 | 27 | 開拓港 |
| coreloop/CoreFrontierZones.kt:54 | 36 | 花筏の淵 |
| coreloop/CoreFrontierZones.kt:55 | 45 | 泥炭の入江 |
| coreloop/CoreFrontierZones.kt:56 | 36 | 春雷の社 |
| coreloop/CoreFrontierZones.kt:58 | 27 | 狼の背 |
| coreloop/CoreFrontierZones.kt:59 | 36 | 鷹の巣岩 |
| coreloop/CoreFrontierZones.kt:60 | 18 | 霧沼 |
| coreloop/CoreFrontierZones.kt:61 | 36 | 塩の干潟 |
| coreloop/CoreFrontierZones.kt:62 | 36 | 氷柱の谷 |
| coreloop/CoreFrontierZones.kt:63 | 36 | 黒鉄の砦 |
| coreloop/CoreFrontierZones.kt:64 | 36 | 風穴の崖 |
| coreloop/CoreFrontierZones.kt:65 | 45 | 夜桜の参道 |
| coreloop/CoreFrontierZones.kt:66 | 45 | 吹雪の関所 |
| coreloop/CoreFrontierZones.kt:67 | 45 | 羊飼いの原 |
| coreloop/CoreFrontierZones.kt:68 | 36 | 緑風の谷 |
| coreloop/CoreFrontierZones.kt:70 | 36 | 霧氷の城 |
| coreloop/CoreFrontierZones.kt:71 | 45 | 凍て牙の峰 |
| coreloop/CoreFrontierZones.kt:72 | 36 | 白樫の森 |
| coreloop/CoreFrontierZones.kt:73 | 36 | 雪原の砦 |
| coreloop/CoreFrontierZones.kt:74 | 45 | 蛙鳴の湿原 |
| coreloop/CoreFrontierZones.kt:75 | 45 | 段々の白壁 |
| coreloop/CoreFrontierZones.kt:77 | 45 | 熾火の火口 |
| coreloop/CoreFrontierZones.kt:78 | 63 | 白崖の見張り塔 |
| coreloop/CoreFrontierZones.kt:79 | 45 | 花影の塔寺 |
| coreloop/CoreFrontierZones.kt:80 | 54 | 石切りの段丘 |
| coreloop/CoreFrontierZones.kt:81 | 36 | 風見の丘 |
| coreloop/CoreFrontierZones.kt:82 | 54 | 沈み葦の集落 |
| coreloop/CoreFrontierZones.kt:83 | 36 | 麦穂の野 |
| coreloop/CoreFrontierZones.kt:84 | 45 | 桜吹雪の峠 |
| coreloop/CoreFrontierZones.kt:85 | 36 | 紅霞の庭 |
| coreloop/CoreFrontierZones.kt:103 | 108 | 港は遠征先ではありません |
| coreloop/CoreFrontierZones.kt:104 | 65 | T{…} は未解放 |
| coreloop/CoreFrontierZones.kt:105 | 81 | 戦利品券が足りない |
| coreloop/CoreFrontierZones.kt:114 | 140 | T{…} のボスを敵Lv {…} で倒す |
| coreloop/CoreJourney.kt:5 | 18 | 戦士 |
| coreloop/CoreJourney.kt:5 | 153 | 鉄塊の大剣で地を割り、敵を押し出す |
| coreloop/CoreJourney.kt:6 | 45 | レンジャー |
| coreloop/CoreJourney.kt:6 | 144 | 射線を通し、距離を保って狙い撃つ |
| coreloop/CoreJourney.kt:7 | 27 | メイジ |
| coreloop/CoreJourney.kt:7 | 171 | 炎と氷の術式。マナを使って集団を制する |
| coreloop/CoreJourney.kt:8 | 36 | アサシン |
| coreloop/CoreJourney.kt:8 | 153 | 印を仕込み、接近と離脱を使い分ける |
| coreloop/CoreJourney.kt:9 | 45 | テンプラー |
| coreloop/CoreJourney.kt:9 | 162 | 敵を集め、守った力を反撃と支援に回す |
| coreloop/CoreJourney.kt:10 | 36 | ヒーラー |
| coreloop/CoreJourney.kt:10 | 153 | 攻撃で信仰を得て、救護か裁きに使う |
| coreloop/CoreJourney.kt:13 | 9 | 獣 |
| coreloop/CoreJourney.kt:13 | 18 | 集中 |
| coreloop/CoreJourney.kt:13 | 18 | 術式 |
| coreloop/CoreJourney.kt:13 | 18 | 機会 |
| coreloop/CoreJourney.kt:13 | 18 | 決意 |
| coreloop/CoreJourney.kt:13 | 18 | 信仰 |
| coreloop/CoreJourney.kt:22 | 630 | 溜めるスキルが当たると獣が1つ、通常攻撃でも少し溜まる（最大3）。地割りは獣を全部使い、1つごとに重くなる。使うスキルは振っている間怯まない。 |
| coreloop/CoreJourney.kt:23 | 333 | 同じ獲物への通常攻撃で集中が育ち、威力が最大20%増す。獲物変更で集中半減。 |
| coreloop/CoreJourney.kt:24 | 363 | 属性を変えて生成スキルを当てると術式を追加15獲得。集めた術式を消費スキルに使う。 |
| coreloop/CoreJourney.kt:25 | 324 | 通常攻撃で機会を得る。印を付けた敵への消費スキルは最初の命中が35%強化。 |
| coreloop/CoreJourney.kt:26 | 303 | 攻撃と防御で決意を得る。命中した敵の狙いを2秒引きつけ、味方を守る。 |
| coreloop/CoreJourney.kt:27 | 333 | 通常攻撃と光のスキルで信仰を得る。回復・障壁・裁きに同じ信仰を振り分ける。 |
| coreloop/CoreJourney.kt:35 | 45 | 開拓の大剣 |
| coreloop/CoreJourney.kt:35 | 126 | 癖のない基準型。三連撃で戦う |
| coreloop/CoreJourney.kt:36 | 36 | 連撃大剣 |
| coreloop/CoreJourney.kt:36 | 162 | 回避後も連撃を保持。三段目でマナ回復 |
| coreloop/CoreJourney.kt:37 | 36 | 重断大剣 |
| coreloop/CoreJourney.kt:37 | 162 | 三段目は狭い縦斬り。締めの一撃が強い |
| coreloop/CoreJourney.kt:38 | 36 | 導脈大剣 |
| coreloop/CoreJourney.kt:38 | 255 | 通常命中で固有資源を追加4獲得。資源消費スキルの威力+12% |
| coreloop/CoreJourney.kt:39 | 45 | 潮風の長弓 |
| coreloop/CoreJourney.kt:39 | 90 | 狙った射線へ矢を放つ |
| coreloop/CoreJourney.kt:40 | 36 | 灯火の杖 |
| coreloop/CoreJourney.kt:40 | 99 | 遠距離の魔弾と属性術式 |
| coreloop/CoreJourney.kt:41 | 45 | 影縫い短剣 |
| coreloop/CoreJourney.kt:41 | 171 | 素早く印を仕込む。接近戦用の短い間合い |
| coreloop/CoreJourney.kt:42 | 45 | 誓いの騎槍 |
| coreloop/CoreJourney.kt:42 | 144 | 長い騎槍と守護の術。集団を制する |
| coreloop/CoreJourney.kt:43 | 45 | 灯守の聖典 |
| coreloop/CoreJourney.kt:43 | 171 | 裁きと救護。攻撃で得た信仰を振り分ける |
| coreloop/CoreJourney.kt:97 | 117 | 案内所で最初の職業を選ぼう |
| coreloop/CoreJourney.kt:98 | 138 | T1遠征で通常攻撃を敵に当てよう |
| coreloop/CoreJourney.kt:99 | 186 | ホットバー2番を選び、最初のスキルを使おう |
| coreloop/CoreJourney.kt:100 | 189 | 敵の戦利品を回収しよう。近づくと保管される |
| coreloop/CoreJourney.kt:101 | 171 | 手帳から港へ帰還し、戦利品を確認しよう |
| coreloop/CoreJourney.kt:102 | 180 | 工房で制作するか、市場で装備を購入しよう |
| coreloop/CoreJourney.kt:103 | 171 | 刻印工房でオーブを使い、MODを付けよう |
| coreloop/CoreJourney.kt:104 | 200 | ボスを討伐、または採取実績を積んで次のTierへ |
| coreloop/CoreJourney.kt:105 | 246 | T4遠征と工房で、装備のMOD・強化・製造品質を追求しよう |
| coreloop/CoreJourney.kt:113 | 87 | 装備レベルを1上げる |
| coreloop/CoreKeystone.kt:8 | 78 | 敵を倒して4秒以内 |
| coreloop/CoreKeystone.kt:8 | 84 | HPが半分以下のとき |
| coreloop/CoreKeystone.kt:8 | 75 | HPが満タンのとき |
| coreloop/CoreKeystone.kt:9 | 69 | 回避して2秒以内 |
| coreloop/CoreKeystone.kt:9 | 87 | 2秒動いていないとき |
| coreloop/CoreKeystone.kt:9 | 27 | 移動中 |
| coreloop/CoreKeystone.kt:10 | 54 | ボスに対して |
| coreloop/CoreKeystone.kt:10 | 111 | CC中・減速中の敵に対して |
| coreloop/CoreKeystone.kt:37 | 285 | HPが減るほど与ダメージが増える（最大+40%）。受ける回復-30%。 |
| coreloop/CoreKeystone.kt:38 | 225 | 回避できなくなる。被ダメージ-20%、CCを受けない。 |
| coreloop/CoreKeystone.kt:39 | 312 | 自分が得たシールドの半分が近くの味方にも付く。自分の与ダメージ-15%。 |
| coreloop/CoreKeystone.kt:40 | 228 | 受けるダメージの30%を、HPの代わりにマナで受ける。 |
| coreloop/CoreKeystone.kt:41 | 195 | 継続ダメージも会心する。直接ダメージ-20%。 |
| coreloop/CoreKeystone.kt:42 | 141 | 最大HP-30%。与ダメージ+35%。 |
| coreloop/CoreKeystone.kt:43 | 285 | 味方を回復すると、その半分だけ自分も回復する。与ダメージ-10%。 |
| coreloop/CoreKeystone.kt:44 | 228 | スキルがマナの代わりにHPを使う。マナは回復しない。 |
| coreloop/CoreKeystone.kt:45 | 174 | 2秒動かないと会心率が2倍。動くと解除。 |
| coreloop/CoreKeystone.kt:46 | 243 | 敵を倒すたび全てのスキルのCDが1秒縮む。最大HP-15%。 |
| coreloop/CoreKeystone.kt:47 | 216 | 回避の後2秒、与ダメージ+30%。被ダメージ+10%。 |
| coreloop/CoreKeystone.kt:48 | 186 | 会心しなくなる。代わりに与ダメージ+30%。 |
| coreloop/CoreKeystone.kt:49 | 303 | ボスのブレイクゲージが50%多く溜まる。ボス以外への与ダメージ-15%。 |
| coreloop/CoreKeystone.kt:50 | 246 | パーティにいないとき与ダメージ+20%・被ダメージ-10%。 |
| coreloop/CoreKeystone.kt:51 | 150 | CD回復速度+30%。最大マナ-40%。 |
| coreloop/CoreKeystone.kt:52 | 225 | 火・氷・雷の属性値を、一番高い属性に全部まとめる。 |
| coreloop/CoreLoopItems.kt:77 | 59 | 所持：{…} 個 |
| coreloop/CoreLoopItems.kt:77 | 135 | 採取、または市場で購入して入手 |
| coreloop/CoreLoopItems.kt:77 | 144 | 採取素材を精製、または市場で購入 |
| coreloop/CoreLoopItems.kt:78 | 72 | 遠征や工房で入手 |
| coreloop/CoreLoopItems.kt:82 | 108 | インベントリ所持 {…} 個 |
| coreloop/CoreLoopItems.kt:82 | 77 | 素材束：{…} 個分 |
| coreloop/CoreLoopItems.kt:83 | 117 | 探索・製造で得た実物の素材 |
| coreloop/CoreLoopItems.kt:84 | 90 | 港の市場で売買できる |
| coreloop/CoreLoopItems.kt:110 | 83 | T{…} 開拓者の大剣 |
| coreloop/CoreLoopItems.kt:110 | 50 | 攻撃力 {…} |
| coreloop/CoreLoopItems.kt:111 | 99 | 左クリック：前方を斬る |
| coreloop/CoreLoopItems.kt:111 | 72 | 右クリック：轟進 |
| coreloop/CoreLoopItems.kt:111 | 78 | F：移動方向へ回避 |
| coreloop/CoreLoopItems.kt:131 | 55 | 開拓者の{…} |
| coreloop/CoreLoopItems.kt:135 | 52 | 物理攻撃 AD |
| coreloop/CoreLoopItems.kt:136 | 52 | 魔法攻撃 AP |
| coreloop/CoreLoopItems.kt:137 | 36 | 攻撃速度 |
| coreloop/CoreLoopItems.kt:138 | 59 | 会心率 / 倍率 |
| coreloop/CoreLoopItems.kt:139 | 55 | 炎 / 氷 / 雷 |
| coreloop/CoreLoopItems.kt:140 | 30 | 最大HP |
| coreloop/CoreLoopItems.kt:141 | 52 | 物理防御 AR |
| coreloop/CoreLoopItems.kt:142 | 52 | 魔法防御 MR |
| coreloop/CoreLoopItems.kt:143 | 36 | 追加軽減 |
| coreloop/CoreLoopItems.kt:144 | 36 | 最大マナ |
| coreloop/CoreLoopItems.kt:157 | 36 | 【破損】 |
| coreloop/CoreLoopItems.kt:157 | 55 | 開拓者の{…} |
| coreloop/CoreLoopItems.kt:164 | 46 | 防具・{…} |
| coreloop/CoreLoopItems.kt:164 | 82 |  · 強化 +{…}/30 |
| coreloop/CoreLoopItems.kt:167 | 167 | 装備Lv鍛錬で同Tier内の基礎性能が成長 |
| coreloop/CoreLoopItems.kt:167 | 125 | 製造品質 +{…}%（基礎性能） |
| coreloop/CoreLoopItems.kt:167 | 153 | 破損中：この装備の性能・MODは無効 |
| coreloop/CoreLoopItems.kt:167 | 149 | 未破損 / 遠征・戦闘では壊れません |
| coreloop/CoreLoopItems.kt:168 | 209 | 装備庫で修理：同Tier・同系統・+0・未破損を1個 |
| coreloop/CoreLoopItems.kt:168 | 189 | +15以降の強化失敗で破損する場合があります |
| coreloop/CoreLoopItems.kt:169 | 162 | 修理対象のMOD・強化値・製作者は維持 |
| coreloop/CoreLoopItems.kt:170 | 147 | 能力欄は装備Lv・強化・MODを反映 |
| coreloop/CoreLoopItems.kt:170 | 200 | マジック：接頭1＋接尾1 / レア：接頭3＋接尾3 |
| coreloop/CoreLoopItems.kt:171 | 97 | 通常攻撃：{…} / {…} |
| coreloop/CoreLoopItems.kt:171 | 155 | 防御軽減：300 / (300 + 有効防御) |
| coreloop/CoreLoopItems.kt:172 | 157 | 左：通常 / 右：第1スキル / F：回避 |
| coreloop/CoreLoopItems.kt:172 | 132 | 防具MODはセット全体に1回適用 |
| coreloop/CoreLoopItems.kt:179 | 36 | の刻印石 |
| coreloop/CoreLoopItems.kt:179 | 45 | 未対応MOD |
| coreloop/CoreLoopItems.kt:180 | 18 | 不明 |
| coreloop/CoreLoopItems.kt:190 | 27 | 刻印石 |
| coreloop/CoreLoopItems.kt:190 | 37 | 旧・{…} |
| coreloop/CoreLoopItems.kt:191 | 108 | 旧仕様の保管品・交換専用 |
| coreloop/CoreLoopItems.kt:191 | 135 | 所持品は消さずに保持しています |
| coreloop/CoreLoopItems.kt:192 | 207 | 改変のオーブ ×{…} ＋ 錬金のオーブ ×1 と交換 |
| coreloop/CoreLoopItems.kt:192 | 167 | 直接装着は終了 / 港の刻印工房から交換 |
| coreloop/CoreLoopItems.kt:216 | 18 | 所持 |
| coreloop/CoreLoopItems.kt:221 | 90 | 旧通貨・残高保管のみ |
| coreloop/CoreLoopItems.kt:221 | 72 | 装備加工用の通貨 |
| coreloop/CoreLoopItems.kt:221 | 64 | {…}の専用報酬 |
| coreloop/CoreLoopItems.kt:222 | 32 | {…} 個 |
| coreloop/CoreLoopItems.kt:223 | 117 | 消費と加工は確認画面で確定 |
| coreloop/CoreLoopItems.kt:223 | 176 | 港で装備に重ねる / 右クリックで刻印工房 |
| coreloop/CoreLoopItems.kt:226 | 46 | {…}の欠片 |
| coreloop/CoreLoopItems.kt:227 | 155 | 所持 {…} 個 / 3個で専用ボスへ挑戦 |
| coreloop/CoreLoopItems.kt:228 | 162 | 入手：マップ内の裂け目を最後まで追う |
| coreloop/CoreLoopItems.kt:229 | 162 | 入手：儀式の波を倒し、報酬を確定する |
| coreloop/CoreLoopItems.kt:230 | 144 | 入手：通常マップのボスを討伐する |
| coreloop/CoreLoopItems.kt:231 | 116 | 手帳 → 境界の試練から出発 |
| coreloop/CoreLoopItems.kt:238 | 18 | 銀貨 |
| coreloop/CoreLoopItems.kt:239 | 54 | 所持 {…} 枚 |
| coreloop/CoreLoopItems.kt:239 | 77 | 銀貨束：{…} 枚分 |
| coreloop/CoreLoopItems.kt:239 | 63 | 港の市場で使用 |
| coreloop/CoreLoopItems.kt:244 | 109 | T{…} Lv{…} 未踏の地図 |
| coreloop/CoreLoopItems.kt:245 | 117 | 右クリック：この地図で出発 |
| coreloop/CoreLoopItems.kt:245 | 144 | 石板をつかんで重ねるとMODを付与 |
| coreloop/CoreLoopItems.kt:245 | 71 | 付与MOD {…}/3 |
| coreloop/CoreLoopItems.kt:299 | 270 | 所持品がいっぱいです。アイテムを移動してから操作してください |
| coreloop/CoreLoopItems.kt:314 | 27 | 回復薬 |
| coreloop/CoreLoopItems.kt:315 | 54 | 所持 {…} 個 |
| coreloop/CoreLoopItems.kt:315 | 176 | 右クリック：最大HPの45% + 回復力100% |
| coreloop/CoreLoopItems.kt:315 | 170 | 与回復・被回復MODが適用 / 再使用10秒 |
| coreloop/CoreLoopItems.kt:317 | 108 | ProjectS — 冒険の手帳 |
| coreloop/CoreLoopItems.kt:317 | 153 | 右クリック：地図・刻印工房・所持品 |
| coreloop/CoreLoopItems.kt:317 | 144 | 港の施設からも同じ操作ができます |
| coreloop/CoreLoopItems.kt:322 | 45 | 採取の石板 |
| coreloop/CoreLoopItems.kt:323 | 54 | 所持 {…} 個 |
| coreloop/CoreLoopItems.kt:323 | 144 | つかんで地図に重ねるとMODを付与 |
| coreloop/CoreLoopItems.kt:323 | 126 | 地図台でも同じ操作ができます |
| coreloop/CoreLoopItems.kt:327 | 18 | 砥石 |
| coreloop/CoreLoopItems.kt:328 | 54 | 所持 {…} 個 |
| coreloop/CoreLoopItems.kt:328 | 197 | 右クリック：直接攻撃の与ダメージ+20% / 3分 |
| coreloop/CoreLoopItems.kt:328 | 114 | AD・AP自体は変化しません |
| coreloop/CoreLoopMenus.kt:55 | 86 | 画面の詳細 / ヘルプ |
| coreloop/CoreLoopMenus.kt:121 | 18 | 戻る |
| coreloop/CoreLoopMenus.kt:124 | 45 | 画面の見方 |
| coreloop/CoreLoopMenus.kt:124 | 149 | 左右：選択内容と費用 / 中央：操作 |
| coreloop/CoreLoopMenus.kt:124 | 99 | 表示サイズ・操作の説明 |
| coreloop/CoreLoopMenus.kt:130 | 27 | 金属材 |
| coreloop/CoreLoopMenus.kt:131 | 36 | 採取石板 |
| coreloop/CoreLoopMenus.kt:134 | 36 | のオーブ |
| coreloop/CoreLoopMenus.kt:139 | 50 | 攻撃力 {…} |
| coreloop/CoreLoopMenus.kt:139 | 41 | 速度 {…} |
| coreloop/CoreLoopMenus.kt:140 | 53 | 最大HP {…} |
| coreloop/CoreLoopMenus.kt:144 | 92 | 素材・地図 Tier {…} |
| coreloop/CoreLoopMenus.kt:148 | 45 | 前のページ |
| coreloop/CoreLoopMenus.kt:149 | 83 | {…} / {…} ページ |
| coreloop/CoreLoopMenus.kt:150 | 45 | 次のページ |
| coreloop/CoreLoopMenus.kt:161 | 207 | 遠征を準備しています。しばらくお待ちください。 |
| coreloop/CoreLoopMenus.kt:164 | 59 | 開拓港 / 手帳 |
| coreloop/CoreLoopMenus.kt:164 | 50 | 遠征 / 手帳 |
| coreloop/CoreLoopMenus.kt:166 | 113 | {…} Lv{…} / 成長と職業 |
| coreloop/CoreLoopMenus.kt:167 | 36 | 旅の装備 |
| coreloop/CoreLoopMenus.kt:171 | 36 | 次の遠征 |
| coreloop/CoreLoopMenus.kt:171 | 63 | 挑戦できる地域 |
| coreloop/CoreLoopMenus.kt:171 | 50 | 地図 {…}枚 |
| coreloop/CoreLoopMenus.kt:171 | 72 | 道の先に待つボス |
| coreloop/CoreLoopMenus.kt:171 | 90 | 寄り道で見つかる素材 |
| coreloop/CoreLoopMenus.kt:172 | 18 | 遠征 |
| coreloop/CoreLoopMenus.kt:172 | 63 | 地図台から遠征 |
| coreloop/CoreLoopMenus.kt:172 | 115 | 地図を選ぶ → 調整 → 出発 |
| coreloop/CoreLoopMenus.kt:172 | 165 | T1の地図は無料で何度でも入手できます |
| coreloop/CoreLoopMenus.kt:173 | 18 | 工房 |
| coreloop/CoreLoopMenus.kt:173 | 36 | 装備工房 |
| coreloop/CoreLoopMenus.kt:173 | 117 | 強化・精製・制作・MOD加工 |
| coreloop/CoreLoopMenus.kt:174 | 27 | 所持品 |
| coreloop/CoreLoopMenus.kt:174 | 72 | 素材の所持品目録 |
| coreloop/CoreLoopMenus.kt:174 | 126 | 持っている素材と正確な所持数 |
| coreloop/CoreLoopMenus.kt:175 | 27 | 装備庫 |
| coreloop/CoreLoopMenus.kt:175 | 162 | 作った装備を使う・出品する・納品する |
| coreloop/CoreLoopMenus.kt:176 | 18 | 採取 |
| coreloop/CoreLoopMenus.kt:176 | 72 | 採取の心得・道具 |
| coreloop/CoreLoopMenus.kt:177 | 18 | 市場 |
| coreloop/CoreLoopMenus.kt:177 | 126 | 素材・装備をプレイヤーと売買 |
| coreloop/CoreLoopMenus.kt:177 | 50 | 銀貨 {…}枚 |
| coreloop/CoreLoopMenus.kt:178 | 18 | 目標 |
| coreloop/CoreLoopMenus.kt:180 | 45 | 遠征の記録 |
| coreloop/CoreLoopMenus.kt:180 | 45 | ボス討伐！ |
| coreloop/CoreLoopMenus.kt:180 | 65 | T{…} を探索中 |
| coreloop/CoreLoopMenus.kt:180 | 54 | 寄り道は自由 |
| coreloop/CoreLoopMenus.kt:181 | 18 | 探索 |
| coreloop/CoreLoopMenus.kt:181 | 108 | 画面を閉じて探索を続ける |
| coreloop/CoreLoopMenus.kt:182 | 18 | 帰還 |
| coreloop/CoreLoopMenus.kt:182 | 54 | 帰還の確認へ |
| coreloop/CoreLoopMenus.kt:182 | 135 | 今のマップには戻れなくなります |
| coreloop/CoreLoopMenus.kt:183 | 27 | 獲得品 |
| coreloop/CoreLoopMenus.kt:183 | 90 | インベントリの獲得品 |
| coreloop/CoreLoopMenus.kt:183 | 180 | 採取・戦利品は自分で回収して持ち帰ります |
| coreloop/CoreLoopMenus.kt:184 | 18 | 装備 |
| coreloop/CoreLoopMenus.kt:184 | 99 | 武器・防具のMODを確認 |
| coreloop/CoreLoopMenus.kt:185 | 18 | 道具 |
| coreloop/CoreLoopMenus.kt:185 | 63 | 採取道具を持つ |
| coreloop/CoreLoopMenus.kt:186 | 27 | 遊び方 |
| coreloop/CoreLoopMenus.kt:186 | 45 | 操作ガイド |
| coreloop/CoreLoopMenus.kt:188 | 27 | 閉じる |
| coreloop/CoreLoopMenus.kt:188 | 54 | 画面を閉じる |
| coreloop/CoreLoopMenus.kt:201 | 66 | 地図台 / T{…} |
| coreloop/CoreLoopMenus.kt:203 | 9 | 採 |
| coreloop/CoreLoopMenus.kt:203 | 81 | 討伐不要の採取地図 |
| coreloop/CoreLoopMenus.kt:204 | 56 | 遠征先 T{…} |
| coreloop/CoreLoopMenus.kt:204 | 50 | 所持 {…}枚 |
| coreloop/CoreLoopMenus.kt:204 | 62 | 解放 T1〜{…} |
| coreloop/CoreLoopMenus.kt:204 | 65 | 目安装備 T{…} |
| coreloop/CoreLoopMenus.kt:204 | 47 | 武器 T{…} |
| coreloop/CoreLoopMenus.kt:204 | 53 | 選ぶ → 調整 |
| coreloop/CoreLoopMenus.kt:204 | 63 | 選択では未消費 |
| coreloop/CoreLoopMenus.kt:205 | 45 | 地図の入手 |
| coreloop/CoreLoopMenus.kt:205 | 39 | T1は無料 |
| coreloop/CoreLoopMenus.kt:205 | 80 | T{…} 戦利品券1枚 |
| coreloop/CoreLoopMenus.kt:205 | 72 | 何度でも入手可能 |
| coreloop/CoreLoopMenus.kt:205 | 41 | 所持 {…} |
| coreloop/CoreLoopMenus.kt:205 | 77 | 同TierのLv上限で |
| coreloop/CoreLoopMenus.kt:205 | 74 | ボス討伐→次Tier |
| coreloop/CoreLoopMenus.kt:205 | 74 | このTierは未解放 |
| coreloop/CoreLoopMenus.kt:205 | 63 | 地図の保管上限 |
| coreloop/CoreLoopMenus.kt:205 | 63 | 戦利品券が不足 |
| coreloop/CoreLoopMenus.kt:205 | 72 | 右下から受け取る |
| coreloop/CoreLoopMenus.kt:207 | 53 | Lv{…} 地図 |
| coreloop/CoreLoopMenus.kt:209 | 98 | 地図なし → 右下で入手 |
| coreloop/CoreLoopMenus.kt:210 | 18 | 手帳 |
| coreloop/CoreLoopMenus.kt:210 | 18 | 元へ |
| coreloop/CoreLoopMenus.kt:214 | 18 | 入手 |
| coreloop/CoreLoopMenus.kt:214 | 92 | T{…} 地図を受け取る |
| coreloop/CoreLoopMenus.kt:214 | 18 | 無料 |
| coreloop/CoreLoopMenus.kt:214 | 80 | T{…} 戦利品券1枚 |
| coreloop/CoreLoopMenus.kt:225 | 77 | 地図台 / 出発準備 |
| coreloop/CoreLoopMenus.kt:227 | 45 | 遠征の内容 |
| coreloop/CoreLoopMenus.kt:227 | 79 | T{…} / 敵Lv{…} |
| coreloop/CoreLoopMenus.kt:227 | 49 | 冒険Lv{…} |
| coreloop/CoreLoopMenus.kt:227 | 47 | 武器 T{…} |
| coreloop/CoreLoopMenus.kt:227 | 60 | 地図1枚を消費 |
| coreloop/CoreLoopMenus.kt:227 | 54 | 道の先にボス |
| coreloop/CoreLoopMenus.kt:227 | 54 | 寄り道は自由 |
| coreloop/CoreLoopMenus.kt:227 | 63 | 地形の準備完了 |
| coreloop/CoreLoopMenus.kt:227 | 54 | 地形を準備中 |
| coreloop/CoreLoopMenus.kt:227 | 72 | 出発後は返却なし |
| coreloop/CoreLoopMenus.kt:228 | 36 | 採取MOD |
| coreloop/CoreLoopMenus.kt:228 | 52 | {…} / 3 個 |
| coreloop/CoreLoopMenus.kt:229 | 81 | まだ付いていません |
| coreloop/CoreLoopMenus.kt:230 | 59 | 石板 所持{…} |
| coreloop/CoreLoopMenus.kt:230 | 75 | 石板1枚で1個付与 |
| coreloop/CoreLoopMenus.kt:231 | 18 | 地図 |
| coreloop/CoreLoopMenus.kt:232 | 36 | 石板付与 |
| coreloop/CoreLoopMenus.kt:232 | 87 | 採取の石板を1枚使う |
| coreloop/CoreLoopMenus.kt:232 | 153 | 採取量・品質・密集地域のMODを追加 |
| coreloop/CoreLoopMenus.kt:238 | 27 | 手元へ |
| coreloop/CoreLoopMenus.kt:238 | 105 | 地図をホットバー8へ用意 |
| coreloop/CoreLoopMenus.kt:238 | 180 | インベントリで石板を重ねる操作も使えます |
| coreloop/CoreLoopMenus.kt:242 | 18 | 出発 |
| coreloop/CoreLoopMenus.kt:242 | 87 | 地図1枚を使って出発 |
| coreloop/CoreLoopMenus.kt:248 | 104 | 旅の始まり / 成長と職業 |
| coreloop/CoreLoopMenus.kt:249 | 54 | {…} Lv{…} |
| coreloop/CoreLoopMenus.kt:250 | 41 | 経験 {…} |
| coreloop/CoreLoopMenus.kt:250 | 50 | 次まで {…} |
| coreloop/CoreLoopMenus.kt:250 | 48 | 冒険Lv最大 |
| coreloop/CoreLoopMenus.kt:250 | 91 | スキル Lv1/4/8/12 |
| coreloop/CoreLoopMenus.kt:250 | 55 | ウルト Lv16 |
| coreloop/CoreLoopMenus.kt:251 | 36 | 次の一歩 |
| coreloop/CoreLoopMenus.kt:251 | 53 | 導入 {…}/6 |
| coreloop/CoreLoopMenus.kt:251 | 81 | 港で職業を変更可能 |
| coreloop/CoreLoopMenus.kt:251 | 72 | 装備と経験は保持 |
| coreloop/CoreLoopMenus.kt:251 | 81 | 採取でも経験を獲得 |
| coreloop/CoreLoopMenus.kt:257 | 108 | 港でいつでも選び直せます |
| coreloop/CoreLoopMenus.kt:262 | 27 | 武器型 |
| coreloop/CoreLoopMenus.kt:263 | 18 | 鍛錬 |
| coreloop/CoreLoopMenus.kt:264 | 27 | スキル |
| coreloop/CoreLoopMenus.kt:264 | 117 | 四つのスキルとウルトを選ぶ |
| coreloop/CoreLoopMenus.kt:265 | 27 | 成長樹 |
| coreloop/CoreLoopMenus.kt:265 | 72 | 成長の分岐を選ぶ |
| coreloop/CoreLoopMenus.kt:266 | 27 | 能力値 |
| coreloop/CoreLoopMenus.kt:266 | 105 | 今のAD・AP・防御を確認 |
| coreloop/CoreLoopMenus.kt:267 | 18 | 手帳 |
| coreloop/CoreLoopMenus.kt:268 | 45 | 操作ガイド |
| coreloop/CoreLoopMenus.kt:268 | 223 | 左クリック：通常攻撃 / 右：最初のスキル / F：回避 |
| coreloop/CoreLoopMenus.kt:269 | 27 | 遠征へ |
| coreloop/CoreLoopMenus.kt:269 | 27 | 探索へ |
| coreloop/CoreLoopMenus.kt:277 | 68 | 工房 / 武器の型 |
| coreloop/CoreLoopMenus.kt:278 | 65 | 同じTierの選択 |
| coreloop/CoreLoopMenus.kt:278 | 75 | T1から全型を制作 |
| coreloop/CoreLoopMenus.kt:278 | 63 | 型の特徴は固定 |
| coreloop/CoreLoopMenus.kt:278 | 72 | MODは後から抽選 |
| coreloop/CoreLoopMenus.kt:278 | 72 | 強化と品質は保持 |
| coreloop/CoreLoopMenus.kt:278 | 99 | 職業で使える系統が変化 |
| coreloop/CoreLoopMenus.kt:279 | 45 | 制作と購入 |
| coreloop/CoreLoopMenus.kt:279 | 72 | 採取素材から制作 |
| coreloop/CoreLoopMenus.kt:279 | 83 | 上位製造に低Tier材 |
| coreloop/CoreLoopMenus.kt:279 | 81 | 良い型を選んで厳選 |
| coreloop/CoreLoopMenus.kt:279 | 36 | 今の職業 |
| coreloop/CoreLoopMenus.kt:282 | 55 | 使用可：{…} |
| coreloop/CoreLoopMenus.kt:286 | 36 | 次の武器 |
| coreloop/CoreLoopMenus.kt:286 | 36 | 前の武器 |
| coreloop/CoreLoopMenus.kt:303 | 27 | ウルト |
| coreloop/CoreLoopMenus.kt:303 | 46 | スキル{…} |
| coreloop/CoreLoopMenus.kt:306 | 78 | {…} / スキル編成 |
| coreloop/CoreLoopMenus.kt:307 | 55 | 変更先：{…} |
| coreloop/CoreLoopMenus.kt:307 | 75 | 1. 上段で枠を選ぶ |
| coreloop/CoreLoopMenus.kt:307 | 84 | 2. 候補で詳細を見る |
| coreloop/CoreLoopMenus.kt:307 | 75 | 3. 右下で装備する |
| coreloop/CoreLoopMenus.kt:307 | 54 | 現在のスキル |
| coreloop/CoreLoopMenus.kt:307 | 72 | 遠征中は変更不可 |
| coreloop/CoreLoopMenus.kt:307 | 54 | 港で無料変更 |
| coreloop/CoreLoopMenus.kt:307 | 90 | 重複するスキルは入替 |
| coreloop/CoreLoopMenus.kt:308 | 63 | 装備中のスキル |
| coreloop/CoreLoopMenus.kt:308 | 45 | 候補の詳細 |
| coreloop/CoreLoopMenus.kt:309 | 41 | マナ {…} |
| coreloop/CoreLoopMenus.kt:309 | 88 | 必要{…} / 獲得{…} |
| coreloop/CoreLoopMenus.kt:309 | 59 | 再使用 {…}秒 |
| coreloop/CoreLoopMenus.kt:310 | 81 | 説明と補正の詳細は |
| coreloop/CoreLoopMenus.kt:310 | 72 | アイコンに重ねる |
| coreloop/CoreLoopMenus.kt:320 | 56 | hunt_storm |
| coreloop/CoreLoopMenus.kt:320 | 36 | 狩場支配 |
| coreloop/CoreLoopMenus.kt:320 | 43 | mage_ult |
| coreloop/CoreLoopMenus.kt:320 | 36 | 天火術式 |
| coreloop/CoreLoopMenus.kt:327 | 36 | 前の候補 |
| coreloop/CoreLoopMenus.kt:328 | 36 | 次の候補 |
| coreloop/CoreLoopMenus.kt:332 | 50 | {…} に装備 |
| coreloop/CoreLoopMenus.kt:332 | 54 | 港で変更可能 |
| coreloop/CoreLoopMenus.kt:332 | 45 | 成長で解放 |
| coreloop/CoreLoopMenus.kt:332 | 45 | 候補を選ぶ |
| coreloop/CoreLoopMenus.kt:348 | 37 | キー{…} |
| coreloop/CoreLoopMenus.kt:359 | 54 | 港で変更可能 |
| coreloop/CoreLoopMenus.kt:360 | 45 | 成長で解放 |
| coreloop/CoreLoopMenus.kt:361 | 45 | 候補を選ぶ |
| coreloop/CoreLoopMenus.kt:362 | 36 | 装備済み |
| coreloop/CoreLoopMenus.kt:363 | 92 | キー{…}と{…}を入替 |
| coreloop/CoreLoopMenus.kt:364 | 50 | {…} に装備 |
| coreloop/CoreLoopMenus.kt:366 | 113 | 戦士 / スキルの組み合わせ |
| coreloop/CoreLoopMenus.kt:367 | 60 | ウルト / {…} |
| coreloop/CoreLoopMenus.kt:367 | 59 | {…} のスキル |
| coreloop/CoreLoopMenus.kt:368 | 27 | 装備中 |
| coreloop/CoreLoopMenus.kt:369 | 63 | 上段で枠を選び |
| coreloop/CoreLoopMenus.kt:369 | 90 | 中央からスキルを選ぶ |
| coreloop/CoreLoopMenus.kt:369 | 57 | 2〜5：スキル |
| coreloop/CoreLoopMenus.kt:369 | 42 | 6：ウルト |
| coreloop/CoreLoopMenus.kt:370 | 72 | この枠に装備済み |
| coreloop/CoreLoopMenus.kt:372 | 27 | 変更後 |
| coreloop/CoreLoopMenus.kt:373 | 64 | キー{…}へ移動 |
| coreloop/CoreLoopMenus.kt:375 | 72 | 遠征中は変更不可 |
| coreloop/CoreLoopMenus.kt:375 | 63 | この枠は未解放 |
| coreloop/CoreLoopMenus.kt:375 | 54 | 港で無料変更 |
| coreloop/CoreLoopMenus.kt:378 | 41 | マナ {…} |
| coreloop/CoreLoopMenus.kt:378 | 36 | 全部使う |
| coreloop/CoreLoopMenus.kt:378 | 27 | 溜める |
| coreloop/CoreLoopMenus.kt:378 | 32 | 獣 {…} |
| coreloop/CoreLoopMenus.kt:379 | 59 | 再使用 {…}秒 |
| coreloop/CoreLoopMenus.kt:380 | 81 | 詳しくは絵に重ねる |
| coreloop/CoreLoopMenus.kt:384 | 141 | キー{…} / この枠のスキルを変更 |
| coreloop/CoreLoopMenus.kt:392 | 59 | {…} に装備中 |
| coreloop/CoreLoopMenus.kt:393 | 150 | キー{…}に装備中 / 選択すると入替 |
| coreloop/CoreLoopMenus.kt:393 | 81 | 選択して右下で装備 |
| coreloop/CoreLoopMenus.kt:397 | 27 | ツリー |
| coreloop/CoreLoopMenus.kt:432 | 73 | 選んだ極意：{…} |
| coreloop/CoreLoopMenus.kt:435 | 72 | 戻すノードを選ぶ |
| coreloop/CoreLoopMenus.kt:435 | 72 | 取るノードを選ぶ |
| coreloop/CoreLoopMenus.kt:436 | 72 | 港で変更できます |
| coreloop/CoreLoopMenus.kt:437 | 73 | 銀貨{…}枚で戻す |
| coreloop/CoreLoopMenus.kt:437 | 73 | （先の{…}つも） |
| coreloop/CoreLoopMenus.kt:438 | 90 | ポイントが足りません |
| coreloop/CoreLoopMenus.kt:439 | 244 | 「{…}」はほかの輪で取得済み。もう一度押すと次の選択肢 |
| coreloop/CoreLoopMenus.kt:440 | 232 | 「{…}」を1ポイントで取る。もう一度押すと次の選択肢 |
| coreloop/CoreLoopMenus.kt:441 | 87 | 1ポイントで取れます |
| coreloop/CoreLoopMenus.kt:443 | 69 | {…} / パッシブ |
| coreloop/CoreLoopMenus.kt:444 | 81 | 残り {…} ポイント |
| coreloop/CoreLoopMenus.kt:444 | 96 | Lvが1上がるごとに1点 |
| coreloop/CoreLoopMenus.kt:444 | 93 | ボス初討伐ごとに+1点 |
| coreloop/CoreLoopMenus.kt:444 | 63 | 全職で一枚の木 |
| coreloop/CoreLoopMenus.kt:444 | 91 | {…}の起点から伸ばす |
| coreloop/CoreLoopMenus.kt:444 | 81 | 戻すには銀貨が要る |
| coreloop/CoreLoopMenus.kt:444 | 88 | 1点ごとに銀貨{…}枚 |
| coreloop/CoreLoopMenus.kt:445 | 36 | 取得済み |
| coreloop/CoreLoopMenus.kt:445 | 54 | 取れるノード |
| coreloop/CoreLoopMenus.kt:454 | 63 | クリック：選ぶ |
| coreloop/CoreLoopMenus.kt:460 | 27 | 取れる |
| coreloop/CoreLoopMenus.kt:460 | 27 | 取った |
| coreloop/CoreLoopMenus.kt:461 | 9 | 左 |
| coreloop/CoreLoopMenus.kt:462 | 9 | 右 |
| coreloop/CoreLoopMenus.kt:465 | 18 | 戻す |
| coreloop/CoreLoopMenus.kt:465 | 18 | 取る |
| coreloop/CoreLoopMenus.kt:473 | 27 | 全戻し |
| coreloop/CoreLoopMenus.kt:482 | 95 | 戦闘能力 / 計算の内訳 |
| coreloop/CoreLoopMenus.kt:483 | 75 | 通常はADで伸びる |
| coreloop/CoreLoopMenus.kt:483 | 102 | 魔法スキルはAPで伸びる |
| coreloop/CoreLoopMenus.kt:483 | 66 | 物理はARで軽減 |
| coreloop/CoreLoopMenus.kt:483 | 66 | 魔法はMRで軽減 |
| coreloop/CoreLoopMenus.kt:483 | 81 | 攻撃ごとに係数あり |
| coreloop/CoreLoopMenus.kt:483 | 81 | スキルの説明で確認 |
| coreloop/CoreLoopMenus.kt:484 | 54 | 固有の戦い方 |
| coreloop/CoreLoopMenus.kt:485 | 34 | 物理 AD |
| coreloop/CoreLoopMenus.kt:485 | 34 | 魔法 AP |
| coreloop/CoreLoopMenus.kt:485 | 34 | 物防 AR |
| coreloop/CoreLoopMenus.kt:485 | 34 | 魔防 MR |
| coreloop/CoreLoopMenus.kt:485 | 30 | 最大HP |
| coreloop/CoreLoopMenus.kt:485 | 30 | 最大MP |
| coreloop/CoreLoopMenus.kt:494 | 95 | 工房 / 装備レベル鍛錬 |
| coreloop/CoreLoopMenus.kt:496 | 54 | {…} Lv{…} |
| coreloop/CoreLoopMenus.kt:496 | 95 | 同Tier内で最大+9段階 |
| coreloop/CoreLoopMenus.kt:496 | 90 | MODと強化はそのまま |
| coreloop/CoreLoopMenus.kt:496 | 99 | 基礎性能を少しずつ向上 |
| coreloop/CoreLoopMenus.kt:496 | 96 | 冒険Lv+2まで鍛錬可能 |
| coreloop/CoreLoopMenus.kt:497 | 65 | このTierは完成 |
| coreloop/CoreLoopMenus.kt:497 | 101 | 次のTierでも好きな型を |
| coreloop/CoreLoopMenus.kt:497 | 81 | 制作・購入できます |
| coreloop/CoreLoopMenus.kt:498 | 18 | 武器 |
| coreloop/CoreLoopMenus.kt:499 | 18 | 防具 |
| coreloop/CoreLoopMenus.kt:501 | 48 | Lvを上げる |
| coreloop/CoreLoopMenus.kt:501 | 36 | 鍛錬不可 |
| coreloop/CoreLoopMenus.kt:514 | 72 | 防具の部位を選ぶ |
| coreloop/CoreLoopMenus.kt:521 | 80 | {…} / 品質 {…}% |
| coreloop/CoreLoopMenus.kt:522 | 27 | 破損中 |
| coreloop/CoreLoopMenus.kt:522 | 27 | 未破損 |
| coreloop/CoreLoopMenus.kt:522 | 108 | クリックでこの部位を選択 |
| coreloop/CoreLoopMenus.kt:525 | 18 | 戻る |
| coreloop/CoreLoopMenus.kt:535 | 69 | 開拓工房 / {…} |
| coreloop/CoreLoopMenus.kt:537 | 18 | 刻印 |
| coreloop/CoreLoopMenus.kt:538 | 135 | 選ぶだけでは素材を消費しません |
| coreloop/CoreLoopMenus.kt:544 | 18 | 武器 |
| coreloop/CoreLoopMenus.kt:545 | 45 | 武器を選択 |
| coreloop/CoreLoopMenus.kt:550 | 18 | 防具 |
| coreloop/CoreLoopMenus.kt:551 | 126 | 頭・胴・脚・足から対象を選ぶ |
| coreloop/CoreLoopMenus.kt:557 | 18 | 手帳 |
| coreloop/CoreLoopMenus.kt:557 | 18 | 元へ |
| coreloop/CoreLoopMenus.kt:570 | 36 | 必要素材 |
| coreloop/CoreLoopMenus.kt:573 | 68 | 足りる 所持{…} |
| coreloop/CoreLoopMenus.kt:573 | 87 | あと{…}個 所持{…} |
| coreloop/CoreLoopMenus.kt:575 | 36 | 消費なし |
| coreloop/CoreLoopMenus.kt:579 | 27 | 入手先 |
| coreloop/CoreLoopMenus.kt:579 | 72 | 必要素材の入手先 |
| coreloop/CoreLoopMenus.kt:579 | 180 | 精製・補給へ寄り道し、元の操作へ戻れます |
| coreloop/CoreLoopMenus.kt:583 | 121 | {…}: 所持 {…} / 必要 {…} |
| coreloop/CoreLoopMenus.kt:584 | 36 | 強化する |
| coreloop/CoreLoopMenus.kt:584 | 189 | +15からは失敗時に破損する可能性があります |
| coreloop/CoreLoopMenus.kt:584 | 191 | 強化値・MODは維持 / 破損時は装備1個で修理 |
| coreloop/CoreLoopMenus.kt:584 | 189 | 素材は成功・失敗にかかわらず毎回消費します |
| coreloop/CoreLoopMenus.kt:585 | 36 | 加工する |
| coreloop/CoreLoopMenus.kt:585 | 36 | 刻印する |
| coreloop/CoreLoopMenus.kt:590 | 18 | 破損 |
| coreloop/CoreLoopMenus.kt:590 | 27 | 要修理 |
| coreloop/CoreLoopMenus.kt:591 | 36 | 最大強化 |
| coreloop/CoreLoopMenus.kt:591 | 36 | 最大強化 |
| coreloop/CoreLoopMenus.kt:592 | 36 | 保管上限 |
| coreloop/CoreLoopMenus.kt:592 | 36 | 保管上限 |
| coreloop/CoreLoopMenus.kt:593 | 18 | 不足 |
| coreloop/CoreLoopMenus.kt:593 | 45 | 足りません |
| coreloop/CoreLoopMenus.kt:593 | 36 | 素材不足 |
| coreloop/CoreLoopMenus.kt:594 | 27 | 選んで |
| coreloop/CoreLoopMenus.kt:594 | 36 | 選択待ち |
| coreloop/CoreLoopMenus.kt:595 | 18 | 拠点 |
| coreloop/CoreLoopMenus.kt:595 | 36 | 港で使用 |
| coreloop/CoreLoopMenus.kt:596 | 36 | 条件未達 |
| coreloop/CoreLoopMenus.kt:598 | 36 | 最大強化 |
| coreloop/CoreLoopMenus.kt:599 | 18 | 破損 |
| coreloop/CoreLoopMenus.kt:599 | 27 | 要修理 |
| coreloop/CoreLoopMenus.kt:599 | 36 | 強化する |
| coreloop/CoreLoopMenus.kt:599 | 36 | 強化不可 |
| coreloop/CoreLoopMenus.kt:600 | 47 | {…}：{…} |
| coreloop/CoreLoopMenus.kt:607 | 9 | 全 |
| coreloop/CoreLoopMenus.kt:608 | 54 | 最大 {…} 回 |
| coreloop/CoreLoopMenus.kt:608 | 32 | {…} 回 |
| coreloop/CoreLoopMenus.kt:609 | 135 | 回数を選ぶだけでは消費しません |
| coreloop/CoreLoopMenus.kt:609 | 81 | 完成品数は左に表示 |
| coreloop/CoreLoopMenus.kt:621 | 27 | 攻撃力 |
| coreloop/CoreLoopMenus.kt:623 | 63 | 攻撃速度の補正 |
| coreloop/CoreLoopMenus.kt:626 | 30 | 最大HP |
| coreloop/CoreLoopMenus.kt:628 | 63 | 鍛え抜いた装備 |
| coreloop/CoreLoopMenus.kt:628 | 54 | 強化後の性能 |
| coreloop/CoreLoopMenus.kt:630 | 58 | 最大強化 +30 |
| coreloop/CoreLoopMenus.kt:630 | 56 | 成功率 {…}% |
| coreloop/CoreLoopMenus.kt:631 | 81 | 制作後も強化を維持 |
| coreloop/CoreLoopMenus.kt:631 | 77 | 鍛冶熟練 {…}/10 |
| coreloop/CoreLoopMenus.kt:632 | 81 | 次の強化は成功確定 |
| coreloop/CoreLoopMenus.kt:632 | 84 | 成功保証 {…}/{…} |
| coreloop/CoreLoopMenus.kt:633 | 77 | 鍛冶熟練 {…}/10 |
| coreloop/CoreLoopMenus.kt:634 | 81 | 破損中・修理が必要 |
| coreloop/CoreLoopMenus.kt:635 | 74 | 失敗時破損 {…}% |
| coreloop/CoreLoopMenus.kt:635 | 63 | 今回の破損なし |
| coreloop/CoreLoopMenus.kt:637 | 81 | 強化値・MODは維持 |
| coreloop/CoreLoopMenus.kt:637 | 63 | 素材は毎回消費 |
| coreloop/CoreLoopMenus.kt:640 | 18 | 詳細 |
| coreloop/CoreLoopMenus.kt:641 | 18 | 通常 |
| coreloop/CoreLoopMenus.kt:641 | 36 | 通常強化 |
| coreloop/CoreLoopMenus.kt:641 | 126 | 追加の精錬触媒を消費しません |
| coreloop/CoreLoopMenus.kt:641 | 238 | 素材Tierは装備Tierではなく強化段階によって決まります |
| coreloop/CoreLoopMenus.kt:644 | 18 | 触媒 |
| coreloop/CoreLoopMenus.kt:644 | 63 | 精錬触媒を追加 |
| coreloop/CoreLoopMenus.kt:644 | 153 | 追加素材を消費し成功率+15ポイント |
| coreloop/CoreLoopMenus.kt:645 | 341 | 星鉄の触媒を所持していればそれを1個使い +{…}（魔術の研究品・市場で購入可） |
| coreloop/CoreLoopMenus.kt:645 | 171 | 費用・成功率を表示してから実行できます |
| coreloop/CoreLoopMenus.kt:648 | 27 | 修理へ |
| coreloop/CoreLoopMenus.kt:648 | 99 | 破損した装備を修理する |
| coreloop/CoreLoopMenus.kt:650 | 36 | 強化する |
| coreloop/CoreLoopMenus.kt:662 | 122 | 強化の確認 / 破損リスクあり |
| coreloop/CoreLoopMenus.kt:665 | 56 | 成功率 {…}% |
| coreloop/CoreLoopMenus.kt:666 | 119 | 失敗した場合の破損率 {…}% |
| coreloop/CoreLoopMenus.kt:667 | 134 | 今回1回あたりの破損確率 {…}% |
| coreloop/CoreLoopMenus.kt:668 | 243 | 破損しても対象は消失せず、強化値・MOD・成功保証を維持 |
| coreloop/CoreLoopMenus.kt:669 | 227 | 修理には同Tier・同系統・+0・未破損の装備1個を消費 |
| coreloop/CoreLoopMenus.kt:670 | 121 | {…}: 所持 {…} / 必要 {…} |
| coreloop/CoreLoopMenus.kt:672 | 108 | リスクを確認して強化する |
| coreloop/CoreLoopMenus.kt:672 | 63 | 強化できません |
| coreloop/CoreLoopMenus.kt:673 | 212 | 素材は成功・失敗どちらでも消費 / 取り消せません |
| coreloop/CoreLoopMenus.kt:680 | 86 | やめる / 強化へ戻る |
| coreloop/CoreLoopMenus.kt:711 | 56 | 下の素材Tier |
| coreloop/CoreLoopMenus.kt:712 | 88 | 使用する素材：T{…} |
| coreloop/CoreLoopMenus.kt:713 | 56 | 上の素材Tier |
| coreloop/CoreLoopMenus.kt:714 | 43 | T{…}素材 |
| coreloop/CoreLoopMenus.kt:714 | 119 | 現在の装備Tierの素材で制作 |
| coreloop/CoreLoopMenus.kt:721 | 162 | クリック：選択。まだ素材は使いません |
| coreloop/CoreLoopMenus.kt:729 | 41 | 所持 {…} |
| coreloop/CoreLoopMenus.kt:730 | 46 | 防具・{…} |
| coreloop/CoreLoopMenus.kt:730 | 81 | 新品を装備庫へ保管 |
| coreloop/CoreLoopMenus.kt:730 | 81 | 製造品質を個別抽選 |
| coreloop/CoreLoopMenus.kt:730 | 81 | 今の装備は変更なし |
| coreloop/CoreLoopMenus.kt:731 | 54 | 完成するもの |
| coreloop/CoreLoopMenus.kt:731 | 54 | 今回 {…} 回 |
| coreloop/CoreLoopMenus.kt:731 | 72 | 制作可能 {…} 回 |
| coreloop/CoreLoopMenus.kt:732 | 54 | 制作できます |
| coreloop/CoreLoopMenus.kt:736 | 36 | 精製する |
| coreloop/CoreLoopMenus.kt:736 | 36 | 制作する |
| coreloop/CoreLoopMenus.kt:737 | 81 | 装備の保管上限です |
| coreloop/CoreLoopMenus.kt:745 | 36 | 全オーブ |
| coreloop/CoreLoopMenus.kt:745 | 90 | 全オーブの効果・条件 |
| coreloop/CoreLoopMenus.kt:749 | 54 | 所持 {…} 個 |
| coreloop/CoreLoopMenus.kt:749 | 140 | クリック：選ぶ / 効果は右に表示 |
| coreloop/CoreLoopMenus.kt:753 | 54 | 加工する装備 |
| coreloop/CoreLoopMenus.kt:753 | 63 | 選ぶだけは無料 |
| coreloop/CoreLoopMenus.kt:753 | 54 | 使用時に抽選 |
| coreloop/CoreLoopMenus.kt:754 | 45 | 加工の内容 |
| coreloop/CoreLoopMenus.kt:754 | 54 | オーブを選ぶ |
| coreloop/CoreLoopMenus.kt:754 | 72 | 右下で加工を実行 |
| coreloop/CoreLoopMenus.kt:754 | 72 | 使える所持品なし |
| coreloop/CoreLoopMenus.kt:754 | 63 | 左の装備が対象 |
| coreloop/CoreLoopMenus.kt:754 | 72 | 条件は全オーブへ |
| coreloop/CoreLoopMenus.kt:756 | 41 | 所持 {…} |
| coreloop/CoreLoopMenus.kt:756 | 37 | 必要 1個 |
| coreloop/CoreLoopMenus.kt:756 | 54 | 取り消し不可 |
| coreloop/CoreLoopMenus.kt:757 | 108 | 使えるオーブがありません |
| coreloop/CoreLoopMenus.kt:758 | 18 | 詳細 |
| coreloop/CoreLoopMenus.kt:759 | 36 | 旧石交換 |
| coreloop/CoreLoopMenus.kt:759 | 81 | 旧刻印石をオーブへ |
| coreloop/CoreLoopMenus.kt:759 | 54 | 所持 {…} 個 |
| coreloop/CoreLoopMenus.kt:760 | 36 | 加工する |
| coreloop/CoreLoopMenus.kt:760 | 99 | オーブを選んでください |
| coreloop/CoreLoopMenus.kt:767 | 54 | ノーマル専用 |
| coreloop/CoreLoopMenus.kt:767 | 63 | マジックへ昇格 |
| coreloop/CoreLoopMenus.kt:767 | 57 | 1〜2個を抽選 |
| coreloop/CoreLoopMenus.kt:768 | 54 | マジック専用 |
| coreloop/CoreLoopMenus.kt:768 | 69 | 空き枠へ1個追加 |
| coreloop/CoreLoopMenus.kt:769 | 54 | マジック専用 |
| coreloop/CoreLoopMenus.kt:769 | 54 | 全MODを消去 |
| coreloop/CoreLoopMenus.kt:769 | 66 | 1〜2個を再抽選 |
| coreloop/CoreLoopMenus.kt:770 | 54 | ノーマル専用 |
| coreloop/CoreLoopMenus.kt:770 | 45 | レアへ昇格 |
| coreloop/CoreLoopMenus.kt:770 | 57 | 4〜6個を抽選 |
| coreloop/CoreLoopMenus.kt:771 | 36 | レア専用 |
| coreloop/CoreLoopMenus.kt:771 | 54 | 全MODを消去 |
| coreloop/CoreLoopMenus.kt:771 | 66 | 4〜6個を再抽選 |
| coreloop/CoreLoopMenus.kt:772 | 54 | マジック専用 |
| coreloop/CoreLoopMenus.kt:772 | 63 | 既存MODは保持 |
| coreloop/CoreLoopMenus.kt:772 | 45 | レアへ昇格 |
| coreloop/CoreLoopMenus.kt:772 | 60 | 1個を追加抽選 |
| coreloop/CoreLoopMenus.kt:773 | 36 | レア専用 |
| coreloop/CoreLoopMenus.kt:773 | 69 | 空き枠へ1個追加 |
| coreloop/CoreLoopMenus.kt:774 | 54 | 全MODを消去 |
| coreloop/CoreLoopMenus.kt:774 | 63 | ノーマルに戻す |
| coreloop/CoreLoopMenus.kt:774 | 54 | 取り消し不可 |
| coreloop/CoreLoopMenus.kt:775 | 65 | MOD・Tier保持 |
| coreloop/CoreLoopMenus.kt:775 | 54 | 数値を再抽選 |
| coreloop/CoreLoopMenus.kt:775 | 63 | 低下する場合も |
| coreloop/CoreLoopMenus.kt:776 | 54 | レアへ再構成 |
| coreloop/CoreLoopMenus.kt:776 | 54 | 全MODを消去 |
| coreloop/CoreLoopMenus.kt:776 | 66 | 4〜6個を再抽選 |
| coreloop/CoreLoopMenus.kt:776 | 63 | 元素MODを含む |
| coreloop/CoreLoopMenus.kt:777 | 65 | MOD・Tier保持 |
| coreloop/CoreLoopMenus.kt:777 | 69 | 数値を各2回抽選 |
| coreloop/CoreLoopMenus.kt:777 | 54 | 高い方を採用 |
| coreloop/CoreLoopMenus.kt:777 | 63 | 低下する場合も |
| coreloop/CoreLoopMenus.kt:778 | 36 | レア専用 |
| coreloop/CoreLoopMenus.kt:778 | 69 | 空き枠へ1個追加 |
| coreloop/CoreLoopMenus.kt:778 | 63 | 数値の上位25% |
| coreloop/CoreLoopMenus.kt:779 | 72 | 旧通貨・使用終了 |
| coreloop/CoreLoopMenus.kt:779 | 81 | 残高は保存されます |
| coreloop/CoreLoopMenus.kt:789 | 77 | 工房 / 素材の調達 |
| coreloop/CoreLoopMenus.kt:791 | 36 | 元の操作 |
| coreloop/CoreLoopMenus.kt:791 | 72 | 素材を選んで調達 |
| coreloop/CoreLoopMenus.kt:791 | 72 | 元へ：操作を復元 |
| coreloop/CoreLoopMenus.kt:791 | 54 | 自動実行なし |
| coreloop/CoreLoopMenus.kt:796 | 96 | 所持 {…} / 必要 {…} |
| coreloop/CoreLoopMenus.kt:797 | 54 | 精製して作る |
| coreloop/CoreLoopMenus.kt:797 | 90 | 採取または市場で入手 |
| coreloop/CoreLoopMenus.kt:797 | 63 | 遠征で入手する |
| coreloop/CoreLoopMenus.kt:810 | 86 | 工房 / 素材の入手先 |
| coreloop/CoreLoopMenus.kt:811 | 36 | 探すもの |
| coreloop/CoreLoopMenus.kt:811 | 83 | 同Tierのボスを討伐 |
| coreloop/CoreLoopMenus.kt:811 | 83 | 同Tierの地域で採取 |
| coreloop/CoreLoopMenus.kt:811 | 81 | 魔物を討伐して入手 |
| coreloop/CoreLoopMenus.kt:811 | 99 | 獲得品はインベントリへ |
| coreloop/CoreLoopMenus.kt:812 | 45 | 操作を保存 |
| coreloop/CoreLoopMenus.kt:812 | 72 | 戻ると工房を復元 |
| coreloop/CoreLoopMenus.kt:812 | 72 | 地図台へ進んでも |
| coreloop/CoreLoopMenus.kt:812 | 81 | 制作は実行しません |
| coreloop/CoreLoopMenus.kt:813 | 70 | T{…}の地図台へ |
| coreloop/CoreLoopMenus.kt:813 | 99 | 素材を集める遠征を選ぶ |
| coreloop/CoreLoopMenus.kt:823 | 77 | 工房 / オーブ図鑑 |
| coreloop/CoreLoopMenus.kt:825 | 45 | 対象の装備 |
| coreloop/CoreLoopMenus.kt:825 | 81 | 図鑑は未所持も表示 |
| coreloop/CoreLoopMenus.kt:825 | 72 | ここでは消費なし |
| coreloop/CoreLoopMenus.kt:826 | 63 | 効果と使用条件 |
| coreloop/CoreLoopMenus.kt:827 | 54 | 所持 {…} 個 |
| coreloop/CoreLoopMenus.kt:827 | 81 | この装備に使用可能 |
| coreloop/CoreLoopMenus.kt:827 | 81 | オーブを選んで詳細 |
| coreloop/CoreLoopMenus.kt:827 | 63 | 使用条件を確認 |
| coreloop/CoreLoopMenus.kt:830 | 54 | 所持 {…} 個 |
| coreloop/CoreLoopMenus.kt:830 | 135 | クリック：効果と使用条件を見る |
| coreloop/CoreLoopMenus.kt:835 | 18 | 選ぶ |
| coreloop/CoreLoopMenus.kt:835 | 90 | 選んだオーブを工房へ |
| coreloop/CoreLoopMenus.kt:835 | 108 | まだオーブは消費しません |
| coreloop/CoreLoopMenus.kt:842 | 47 | {…}万{…} |
| coreloop/CoreLoopMenus.kt:844 | 18 | 切石 |
| coreloop/CoreLoopMenus.kt:845 | 18 | 繊維 |
| coreloop/CoreLoopMenus.kt:846 | 9 | 革 |
| coreloop/CoreLoopMenus.kt:847 | 18 | 石板 |
| coreloop/CoreLoopMenus.kt:848 | 27 | 戦利品 |
| coreloop/CoreLoopMenus.kt:849 | 36 | 星鉄触媒 |
| coreloop/CoreLoopMenus.kt:850 | 18 | 封蝋 |
| coreloop/CoreLoopMenus.kt:862 | 84 | 所持品目録 / T{…} |
| coreloop/CoreLoopMenus.kt:871 | 101 | T{…} の素材（表示中） |
| coreloop/CoreLoopMenus.kt:871 | 56 | T{…} の素材 |
| coreloop/CoreLoopMenus.kt:872 | 137 | クリック：このTierの素材を表示 |
| coreloop/CoreLoopMenus.kt:872 | 162 | オーブ・欠片などの共通品も表示します |
| coreloop/CoreLoopMenus.kt:875 | 81 | 所持品目録の使い方 |
| coreloop/CoreLoopMenus.kt:876 | 246 | 1枠に1種類。カーソルを合わせると名前・正確な個数を表示 |
| coreloop/CoreLoopMenus.kt:877 | 219 | 右下の数字は最大64。合計数は説明欄で確認できます |
| coreloop/CoreLoopMenus.kt:878 | 189 | 素材はインベントリに入り、工房で消費します |
| coreloop/CoreLoopMenus.kt:879 | 162 | 遠征中は自分で採取・回収してください |
| coreloop/CoreLoopMenus.kt:880 | 243 | クリック：その素材を使う画面へ（ここでは消費しません） |
| coreloop/CoreLoopMenus.kt:884 | 45 | 刻印画面へ |
| coreloop/CoreLoopMenus.kt:885 | 54 | ボスの選択へ |
| coreloop/CoreLoopMenus.kt:886 | 45 | 精製画面へ |
| coreloop/CoreLoopMenus.kt:886 | 27 | 工房へ |
| coreloop/CoreLoopMenus.kt:893 | 64 | クリック：{…} |
| coreloop/CoreLoopMenus.kt:893 | 117 | 港へ帰還すると使用できます |
| coreloop/CoreLoopMenus.kt:895 | 54 | 所持 {…} 個 |
| coreloop/CoreLoopMenus.kt:898 | 176 | 所持品の確認 / この画面では消費しません |
| coreloop/CoreLoopMenus.kt:911 | 137 | このTierの素材はまだありません |
| coreloop/CoreLoopMenus.kt:912 | 189 | 採取・討伐すると、ここに自動で保管されます |
| coreloop/CoreLoopMenus.kt:912 | 137 | 上段から他のTierも確認できます |
| coreloop/CoreLoopMenus.kt:913 | 72 | 冒険の手帳へ戻る |
| coreloop/CoreLoopMenus.kt:916 | 104 | 市場へ / 素材を売買する |
| coreloop/CoreLoopMenus.kt:920 | 45 | 前のページ |
| coreloop/CoreLoopMenus.kt:923 | 138 | {…} 種類 / {…} / {…} ページ |
| coreloop/CoreLoopMenus.kt:924 | 225 | 所持している素材・オーブ・欠片だけを表示しています |
| coreloop/CoreLoopMenus.kt:926 | 45 | 次のページ |
| coreloop/CoreLoopMenus.kt:930 | 45 | 工房を開く |
| coreloop/CoreLoopMenus.kt:930 | 99 | 工房は港で利用できます |
| coreloop/CoreLoopMenus.kt:941 | 78 | 装備庫 / 銀貨{…} |
| coreloop/CoreLoopMenus.kt:947 | 45 | 武器の修理 |
| coreloop/CoreLoopMenus.kt:949 | 45 | 装備の修理 |
| coreloop/CoreLoopMenus.kt:949 | 108 | 部位を選んで修理できます |
| coreloop/CoreLoopMenus.kt:951 | 63 | 新しく制作する |
| coreloop/CoreLoopMenus.kt:951 | 142 | 採取 → 精製 → 武器・防具を制作 |
| coreloop/CoreLoopMenus.kt:951 | 171 | 完成品は自動装備せず、この装備庫へ保管 |
| coreloop/CoreLoopMenus.kt:957 | 126 | 保管している装備はありません |
| coreloop/CoreLoopMenus.kt:957 | 117 | 右上の金床から制作できます |
| coreloop/CoreLoopMenus.kt:957 | 99 | 今の装備は左上から確認 |
| coreloop/CoreLoopMenus.kt:958 | 27 | 手帳へ |
| coreloop/CoreLoopMenus.kt:959 | 71 | {…}/{…}ページ |
| coreloop/CoreLoopMenus.kt:959 | 66 | 装備 {…}/{…} |
| coreloop/CoreLoopMenus.kt:959 | 183 | 港の納品：自作・未加工の装備を1日3個まで |
| coreloop/CoreLoopMenus.kt:959 | 186 | 更新は日本時間9時。市場への出品は別枠です |
| coreloop/CoreLoopMenus.kt:960 | 18 | 前へ |
| coreloop/CoreLoopMenus.kt:961 | 18 | 次へ |
| coreloop/CoreLoopMenus.kt:962 | 27 | 市場へ |
| coreloop/CoreLoopMenus.kt:969 | 122 | 初期・引継ぎ装備 / 売却不可 |
| coreloop/CoreLoopMenus.kt:969 | 50 | 製作者 {…} |
| coreloop/CoreLoopMenus.kt:970 | 140 | 出品中 / 取り下げてから装備可能 |
| coreloop/CoreLoopMenus.kt:970 | 117 | クリック：装備・出品・納品 |
| coreloop/CoreLoopMenus.kt:978 | 60 | 装備庫 / {…} |
| coreloop/CoreLoopMenus.kt:980 | 36 | 装備庫へ |
| coreloop/CoreLoopMenus.kt:982 | 72 | 出品を取り下げる |
| coreloop/CoreLoopMenus.kt:982 | 68 | 価格 銀貨{…}枚 |
| coreloop/CoreLoopMenus.kt:985 | 81 | この装備に変更する |
| coreloop/CoreLoopMenus.kt:985 | 117 | 今の装備は装備庫へ戻します |
| coreloop/CoreLoopMenus.kt:985 | 153 | MOD・強化値も装備ごとに保持します |
| coreloop/CoreLoopMenus.kt:988 | 72 | 売却できない装備 |
| coreloop/CoreLoopMenus.kt:988 | 90 | 価格を決めて出品する |
| coreloop/CoreLoopMenus.kt:988 | 153 | 出品画面ではまだ装備を消費しません |
| coreloop/CoreLoopMenus.kt:995 | 104 | 港の依頼へ納品 / 確認へ |
| coreloop/CoreLoopMenus.kt:995 | 108 | この装備は納品できません |
| coreloop/CoreLoopMenus.kt:996 | 180 | 自作・未強化・MODなし・未破損の装備のみ |
| coreloop/CoreLoopMenus.kt:996 | 166 | 本日 {…}/3個 / 完成品は消費されます |
| coreloop/CoreLoopMenus.kt:997 | 68 | 報酬 銀貨{…}枚 |
| coreloop/CoreLoopMenus.kt:1011 | 27 | 未破損 |
| coreloop/CoreLoopMenus.kt:1011 | 45 | 材料を確認 |
| coreloop/CoreLoopMenus.kt:1011 | 45 | 材料を選択 |
| coreloop/CoreLoopMenus.kt:1011 | 79 | {…}の修理 / {…} |
| coreloop/CoreLoopMenus.kt:1013 | 105 | 修理材料の装備1個を消費 |
| coreloop/CoreLoopMenus.kt:1013 | 235 | 同Tier・同系統・+0・未破損 / 初期装備・出品中は不可 |
| coreloop/CoreLoopMenus.kt:1013 | 239 | 対象の装備は消えません / MOD・強化値・成功保証を維持 |
| coreloop/CoreLoopMenus.kt:1014 | 171 | 修理材料のレアリティ・MODは問いません |
| coreloop/CoreLoopMenus.kt:1014 | 212 | 材料に付いているMODも消失します / 選択後に確認 |
| coreloop/CoreLoopMenus.kt:1016 | 72 | 破損していません |
| coreloop/CoreLoopMenus.kt:1016 | 81 | 素材は消費しません |
| coreloop/CoreLoopMenus.kt:1019 | 135 | この材料装備を消費して修理する |
| coreloop/CoreLoopMenus.kt:1019 | 141 | 消費するのは上の材料装備1個です |
| coreloop/CoreLoopMenus.kt:1019 | 167 | 材料装備のMODも消失 / 取り消せません |
| coreloop/CoreLoopMenus.kt:1020 | 189 | 修理対象の強化値・MOD・成功保証はそのまま |
| coreloop/CoreLoopMenus.kt:1024 | 104 | 予備装備が不足 / 制作へ |
| coreloop/CoreLoopMenus.kt:1024 | 147 | T{…} {…}の+0・未破損が1個必要 |
| coreloop/CoreLoopMenus.kt:1026 | 90 | 市場で予備装備を探す |
| coreloop/CoreLoopMenus.kt:1035 | 18 | 前へ |
| coreloop/CoreLoopMenus.kt:1036 | 18 | 次へ |
| coreloop/CoreLoopMenus.kt:1042 | 118 | 修復の封蝋{…}個で修理する |
| coreloop/CoreLoopMenus.kt:1042 | 100 | 修復の封蝋が{…}個不足 |
| coreloop/CoreLoopMenus.kt:1043 | 180 | 予備装備を消費しない修理（魔術の研究品） |
| coreloop/CoreLoopMenus.kt:1043 | 162 | 所持 {…} / 必要 {…}（Tier×{…}） |
| coreloop/CoreLoopMenus.kt:1043 | 126 | 市場で研究者から購入できます |
| coreloop/CoreLoopMenus.kt:1046 | 18 | 戻る |
| coreloop/CoreLoopMenus.kt:1054 | 104 | 納品 / 装備を消費します |
| coreloop/CoreLoopMenus.kt:1056 | 81 | この装備を納品する |
| coreloop/CoreLoopMenus.kt:1056 | 146 | 取り消せません / 完成品1個を消費 |
| coreloop/CoreLoopMenus.kt:1056 | 109 | 銀貨{…}枚を受け取ります |
| coreloop/CoreLoopMenus.kt:1058 | 27 | やめる |
| coreloop/CoreLoopMenus.kt:1073 | 63 | 修理材料の市場 |
| coreloop/CoreLoopMenus.kt:1073 | 18 | 市場 |
| coreloop/CoreLoopMenus.kt:1073 | 109 | {…} / T{…} / 銀貨{…} |
| coreloop/CoreLoopMenus.kt:1076 | 88 | 修理材料はT{…}のみ |
| coreloop/CoreLoopMenus.kt:1076 | 83 | T{…} の出品を見る |
| coreloop/CoreLoopMenus.kt:1079 | 117 | 採取者・職人・戦闘者の市場 |
| coreloop/CoreLoopMenus.kt:1079 | 162 | 素材は採取者が集め、職人が装備を制作 |
| coreloop/CoreLoopMenus.kt:1079 | 160 | 戦利品券 → 銀貨 → 素材や装備を購入 |
| coreloop/CoreLoopMenus.kt:1079 | 210 | 魔術：研究素材と研究品（触媒・封蝋）はT1で売買 |
| coreloop/CoreLoopMenus.kt:1079 | 143 | 成約手数料5% / 出品時の消費なし |
| coreloop/CoreLoopMenus.kt:1080 | 158 | 同Tier・同系統・+0・未破損のみ表示 |
| coreloop/CoreLoopMenus.kt:1080 | 135 | クリックで素材フィルターを解除 |
| coreloop/CoreLoopMenus.kt:1080 | 108 | クリックで市場の全商品へ |
| coreloop/CoreLoopMenus.kt:1087 | 117 | この条件の出品はありません |
| coreloop/CoreLoopMenus.kt:1087 | 144 | NPCから採取素材は購入できません |
| coreloop/CoreLoopMenus.kt:1087 | 185 | 採取して出品する / 装備を制作して出品する |
| coreloop/CoreLoopMenus.kt:1087 | 128 | クリック：同Tierの採取遠征へ |
| coreloop/CoreLoopMenus.kt:1090 | 27 | 修理へ |
| coreloop/CoreLoopMenus.kt:1090 | 27 | 手帳へ |
| coreloop/CoreLoopMenus.kt:1090 | 45 | 元の工房へ |
| coreloop/CoreLoopMenus.kt:1092 | 63 | 素材を出品する |
| coreloop/CoreLoopMenus.kt:1093 | 104 | 購入注文 / 買い手を探す |
| coreloop/CoreLoopMenus.kt:1093 | 207 | 注文は銀貨を預託済み。持ち物を納品して即時売却 |
| coreloop/CoreLoopMenus.kt:1094 | 86 | 装備庫 / 装備を出品 |
| coreloop/CoreLoopMenus.kt:1095 | 148 | {…}/{…}ページ / クリックで更新 |
| coreloop/CoreLoopMenus.kt:1096 | 18 | 前へ |
| coreloop/CoreLoopMenus.kt:1097 | 18 | 次へ |
| coreloop/CoreLoopMenus.kt:1100 | 107 | T{…} 戦利品券を1枚換金 |
| coreloop/CoreLoopMenus.kt:1100 | 119 | T{…} 戦利品券がありません |
| coreloop/CoreLoopMenus.kt:1101 | 142 | 所持 {…}枚 / 1枚 → 銀貨{…}枚 |
| coreloop/CoreLoopMenus.kt:1101 | 128 | 高Tierの地図入手にも使います |
| coreloop/CoreLoopMenus.kt:1112 | 132 | 合計 銀貨{…}枚 / 所持 {…}枚 |
| coreloop/CoreLoopMenus.kt:1113 | 158 | 自分の出品 / クリックで取り下げ確認 |
| coreloop/CoreLoopMenus.kt:1113 | 117 | クリック：商品と価格を確認 |
| coreloop/CoreLoopMenus.kt:1121 | 45 | 出品の管理 |
| coreloop/CoreLoopMenus.kt:1121 | 45 | 購入の確認 |
| coreloop/CoreLoopMenus.kt:1121 | 51 | 市場 / {…} |
| coreloop/CoreLoopMenus.kt:1125 | 72 | 出品を取り下げる |
| coreloop/CoreLoopMenus.kt:1125 | 91 | 銀貨{…}枚で購入する |
| coreloop/CoreLoopMenus.kt:1125 | 73 | 銀貨が{…}枚不足 |
| coreloop/CoreLoopMenus.kt:1126 | 126 | 商品は自分の所持品へ戻ります |
| coreloop/CoreLoopMenus.kt:1126 | 212 | 購入品はインベントリへ / 装備は自動装備しません |
| coreloop/CoreLoopMenus.kt:1127 | 240 | 注意：破損品です。別の装備1個で修理するまで性能は無効 |
| coreloop/CoreLoopMenus.kt:1127 | 72 | 未破損の商品です |
| coreloop/CoreLoopMenus.kt:1133 | 27 | 市場へ |
| coreloop/CoreLoopMenus.kt:1140 | 93 | 出品する素材 / T{…} |
| coreloop/CoreLoopMenus.kt:1142 | 50 | 所持 {…}個 |
| coreloop/CoreLoopMenus.kt:1142 | 126 | クリック：数量と価格を決める |
| coreloop/CoreLoopMenus.kt:1145 | 27 | 市場へ |
| coreloop/CoreLoopMenus.kt:1157 | 95 | 出品 / 数量と合計価格 |
| coreloop/CoreLoopMenus.kt:1161 | 78 | 合計価格 {…}{…} |
| coreloop/CoreLoopMenus.kt:1164 | 68 | 合計 銀貨{…}枚 |
| coreloop/CoreLoopMenus.kt:1164 | 122 | 商品 {…}個分の合計価格です |
| coreloop/CoreLoopMenus.kt:1164 | 77 | 成約手数料 {…}枚 |
| coreloop/CoreLoopMenus.kt:1164 | 68 | 売上受取 {…}枚 |
| coreloop/CoreLoopMenus.kt:1164 | 167 | 売れなければ手数料なし / 取り下げ可能 |
| coreloop/CoreLoopMenus.kt:1168 | 60 | 数量 {…}{…} |
| coreloop/CoreLoopMenus.kt:1171 | 114 | 出品 {…}個 / 所持 {…}個 |
| coreloop/CoreLoopMenus.kt:1171 | 162 | 数量を変えても合計価格は変わりません |
| coreloop/CoreLoopMenus.kt:1174 | 108 | この数量・価格で出品する |
| coreloop/CoreLoopMenus.kt:1174 | 63 | 出品できません |
| coreloop/CoreLoopMenus.kt:1174 | 215 | 最大24出品 / 素材は一時的に所持品から預かります |
| coreloop/CoreLoopMenus.kt:1177 | 27 | やめる |
| coreloop/CoreLoopMenus.kt:1183 | 77 | 道具箱 / 採取道具 |
| coreloop/CoreLoopMenus.kt:1185 | 45 | 道具を持つ |
| coreloop/CoreLoopMenus.kt:1185 | 81 | 集めたい素材を選ぶ |
| coreloop/CoreLoopMenus.kt:1185 | 72 | 道具を無料で用意 |
| coreloop/CoreLoopMenus.kt:1185 | 69 | ホットバー7番へ |
| coreloop/CoreLoopMenus.kt:1185 | 72 | 右クリック長押し |
| coreloop/CoreLoopMenus.kt:1185 | 72 | 離すと採取を中断 |
| coreloop/CoreLoopMenus.kt:1186 | 45 | 採取の目印 |
| coreloop/CoreLoopMenus.kt:1186 | 74 | 名前とTierが目印 |
| coreloop/CoreLoopMenus.kt:1186 | 72 | 木・岩・草・死体 |
| coreloop/CoreLoopMenus.kt:1186 | 72 | それぞれ対応道具 |
| coreloop/CoreLoopMenus.kt:1186 | 81 | 完了時に素材を保存 |
| coreloop/CoreLoopMenus.kt:1188 | 55 | {…}の採取用 |
| coreloop/CoreLoopMenus.kt:1188 | 159 | ホットバー7番に持ち、画面を閉じます |
| coreloop/CoreLoopMenus.kt:1198 | 113 | 職業 / 好きな仕事を育てる |
| coreloop/CoreLoopMenus.kt:1199 | 45 | 採取スキル |
| coreloop/CoreLoopMenus.kt:1199 | 234 | 採取時間と収量を育てる。現在のスキルを保持しています |
| coreloop/CoreLoopMenus.kt:1201 | 59 | 採取実績 {…} |
| coreloop/CoreLoopMenus.kt:1201 | 112 | 討伐不要の地図：T1〜{…} |
| coreloop/CoreLoopMenus.kt:1201 | 161 | 採取対象1個につき、そのTier分の実績 |
| coreloop/CoreLoopMenus.kt:1203 | 63 | 注文へ納品する |
| coreloop/CoreLoopMenus.kt:1203 | 198 | 買い手が求める素材・装備を確認してから作れる |
| coreloop/CoreLoopMenus.kt:1209 | 86 | {…} Lv{…} / 100 |
| coreloop/CoreLoopMenus.kt:1210 | 50 | 経験値 {…} |
| coreloop/CoreLoopMenus.kt:1210 | 128 | 原料還元 {…}%（端数は保持） |
| coreloop/CoreLoopMenus.kt:1210 | 192 | Lvが上がると製造品質の下限・抽選回数が向上 |
| coreloop/CoreLoopMenus.kt:1211 | 162 | 購入した素材でも経験値を獲得できます |
| coreloop/CoreLoopMenus.kt:1211 | 72 | クリック：工房へ |
| coreloop/CoreLoopMenus.kt:1215 | 27 | 手帳へ |
| coreloop/CoreLoopMenus.kt:1223 | 75 | 採取地図 / T{…} |
| coreloop/CoreLoopMenus.kt:1224 | 65 | T{…} 採取地図 |
| coreloop/CoreLoopMenus.kt:1225 | 59 | 採取実績 {…} |
| coreloop/CoreLoopMenus.kt:1225 | 65 | 現在 T{…}まで |
| coreloop/CoreLoopMenus.kt:1225 | 243 | 地図の内容は通常遠征と同じ。討伐せず帰っても素材は保持 |
| coreloop/CoreLoopMenus.kt:1231 | 73 | {…}で地図を用意 |
| coreloop/CoreLoopMenus.kt:1232 | 111 | {…} 必要{…} / 所持{…} |
| coreloop/CoreLoopMenus.kt:1232 | 39 | T1は無料 |
| coreloop/CoreLoopMenus.kt:1233 | 90 | 採取実績が足りません |
| coreloop/CoreLoopMenus.kt:1233 | 36 | 素材不足 |
| coreloop/CoreLoopMenus.kt:1233 | 63 | 地図の保管上限 |
| coreloop/CoreLoopMenus.kt:1233 | 123 | クリック：地図を1枚受け取る |
| coreloop/CoreLoopMenus.kt:1236 | 36 | 地図台へ |
| coreloop/CoreLoopMenus.kt:1246 | 126 | 購入注文 / T{…} / 銀貨{…} |
| coreloop/CoreLoopMenus.kt:1247 | 47 | T{…} 注文 |
| coreloop/CoreLoopMenus.kt:1251 | 125 | 残り{…}個 / 1個 銀貨{…}枚 |
| coreloop/CoreLoopMenus.kt:1251 | 134 | 代金は預託済み / 納品手数料5% |
| coreloop/CoreLoopMenus.kt:1252 | 126 | 持っている素材を納品できます |
| coreloop/CoreLoopMenus.kt:1252 | 221 | 同Tier・同系統の+0・未破損装備（MOD・品質不問） |
| coreloop/CoreLoopMenus.kt:1253 | 153 | 自分の注文：クリックで取り下げ確認 |
| coreloop/CoreLoopMenus.kt:1253 | 99 | クリック：納品の確認へ |
| coreloop/CoreLoopMenus.kt:1256 | 128 | このTierには注文がありません |
| coreloop/CoreLoopMenus.kt:1256 | 162 | 自分で銀貨を預けて購入注文を出せます |
| coreloop/CoreLoopMenus.kt:1257 | 27 | 市場へ |
| coreloop/CoreLoopMenus.kt:1258 | 66 | {…}/{…} 更新 |
| coreloop/CoreLoopMenus.kt:1259 | 63 | 新しい購入注文 |
| coreloop/CoreLoopMenus.kt:1260 | 18 | 前へ |
| coreloop/CoreLoopMenus.kt:1261 | 18 | 次へ |
| coreloop/CoreLoopMenus.kt:1266 | 120 | 購入する品物を選ぶ / T{…} |
| coreloop/CoreLoopMenus.kt:1278 | 45 | 注文一覧へ |
| coreloop/CoreLoopMenus.kt:1286 | 104 | 購入注文 / 代金を預ける |
| coreloop/CoreLoopMenus.kt:1288 | 96 | 数量 {…} / 単価 {…} |
| coreloop/CoreLoopMenus.kt:1288 | 110 | 預託合計 {…} / 所持{…} |
| coreloop/CoreLoopMenus.kt:1288 | 117 | 注文の取消で未成立分を返却 |
| coreloop/CoreLoopMenus.kt:1288 | 144 | 購入後は素材をインベントリで確認 |
| coreloop/CoreLoopMenus.kt:1290 | 60 | 数量 {…}{…} |
| coreloop/CoreLoopMenus.kt:1291 | 60 | 単価 {…}{…} |
| coreloop/CoreLoopMenus.kt:1294 | 118 | 銀貨{…}枚を預けて注文する |
| coreloop/CoreLoopMenus.kt:1294 | 108 | 銀貨不足、または注文上限 |
| coreloop/CoreLoopMenus.kt:1296 | 18 | 戻る |
| coreloop/CoreLoopMenus.kt:1305 | 87 | 注文への納品 / {…} |
| coreloop/CoreLoopMenus.kt:1307 | 72 | 注文を取り下げる |
| coreloop/CoreLoopMenus.kt:1307 | 217 | 未成立の{…}枚を返却（確定済み取引は戻りません） |
| coreloop/CoreLoopMenus.kt:1314 | 77 | {…} 個を納品する |
| coreloop/CoreLoopMenus.kt:1315 | 110 | 所持 {…} / 手取り{…}枚 |
| coreloop/CoreLoopMenus.kt:1315 | 144 | クリックで素材を消費し納品します |
| coreloop/CoreLoopMenus.kt:1324 | 117 | 納品できる装備がありません |
| coreloop/CoreLoopMenus.kt:1324 | 244 | 同Tier・同系統・+0・未破損 / 初期・出品中の装備は不可 |
| coreloop/CoreLoopMenus.kt:1325 | 18 | 前へ |
| coreloop/CoreLoopMenus.kt:1326 | 18 | 次へ |
| coreloop/CoreLoopMenus.kt:1328 | 106 | 単価{…}枚 / 残り{…}個 |
| coreloop/CoreLoopMenus.kt:1328 | 180 | 装備のMOD・品質もそのまま相手へ渡ります |
| coreloop/CoreLoopMenus.kt:1329 | 45 | 注文一覧へ |
| coreloop/CoreLoopMenus.kt:1336 | 99 | この装備を納品しますか |
| coreloop/CoreLoopMenus.kt:1338 | 135 | MOD・製造品質も相手へ移ります |
| coreloop/CoreLoopMenus.kt:1338 | 55 | 手取り{…}枚 |
| coreloop/CoreLoopMenus.kt:1338 | 41 | 個体 {…} |
| coreloop/CoreLoopMenus.kt:1339 | 135 | この装備を渡して銀貨を受け取る |
| coreloop/CoreLoopMenus.kt:1341 | 18 | 戻る |
| coreloop/CoreLoopMenus.kt:1348 | 50 | 採取 / 育成 |
| coreloop/CoreLoopMenus.kt:1350 | 46 | {…}の成長 |
| coreloop/CoreLoopMenus.kt:1350 | 35 | Lv {…} |
| coreloop/CoreLoopMenus.kt:1350 | 50 | 経験値 {…} |
| coreloop/CoreLoopMenus.kt:1350 | 60 | 未使用 {…}pt |
| coreloop/CoreLoopMenus.kt:1350 | 81 | Lv3ごとにポイント |
| coreloop/CoreLoopMenus.kt:1350 | 81 | 右の能力に使えます |
| coreloop/CoreLoopMenus.kt:1352 | 45 | 能力の効果 |
| coreloop/CoreLoopMenus.kt:1352 | 83 | 手つき：時間-4tick |
| coreloop/CoreLoopMenus.kt:1352 | 66 | 深掘り：素材+1 |
| coreloop/CoreLoopMenus.kt:1352 | 57 | 豊穣：素材+2 |
| coreloop/CoreLoopMenus.kt:1352 | 74 | 豊穣は時間+8tick |
| coreloop/CoreLoopMenus.kt:1352 | 72 | 左から順番に取得 |
| coreloop/CoreLoopMenus.kt:1352 | 71 | 手つき → 深掘り |
| coreloop/CoreLoopMenus.kt:1352 | 31 | → 豊穣 |
| coreloop/CoreLoopMenus.kt:1354 | 18 | 採草 |
| coreloop/CoreLoopMenus.kt:1354 | 54 | {…} Lv{…} |
| coreloop/CoreLoopMenus.kt:1360 | 27 | 手つき |
| coreloop/CoreLoopMenus.kt:1360 | 27 | 深掘り |
| coreloop/CoreLoopMenus.kt:1360 | 18 | 豊穣 |
| coreloop/CoreLoopMenus.kt:1361 | 32 | {…} 済 |
| coreloop/CoreLoopMenus.kt:1361 | 81 | 必要 {…} ポイント |
| coreloop/CoreLoopMenus.kt:1361 | 90 | 前提を達成しています |
| coreloop/CoreLoopMenus.kt:1361 | 118 | 先に{…}を取得してください |
| coreloop/CoreLoopMenus.kt:1367 | 27 | 道具箱 |
| coreloop/CoreLoopMenus.kt:1367 | 90 | 無料で採取道具を用意 |
| coreloop/CoreLoopMenus.kt:1372 | 77 | 遠征 / 帰還の確認 |
| coreloop/CoreLoopMenus.kt:1373 | 36 | 港へ帰還 |
| coreloop/CoreLoopMenus.kt:1373 | 63 | 今の遠征を終了 |
| coreloop/CoreLoopMenus.kt:1373 | 72 | このマップは閉鎖 |
| coreloop/CoreLoopMenus.kt:1373 | 90 | 同じ地図へ戻れません |
| coreloop/CoreLoopMenus.kt:1374 | 54 | 獲得物は保持 |
| coreloop/CoreLoopMenus.kt:1374 | 81 | 採取素材は保存済み |
| coreloop/CoreLoopMenus.kt:1374 | 72 | 未回収の戦利品も |
| coreloop/CoreLoopMenus.kt:1374 | 81 | まとめて保管します |
| coreloop/CoreLoopMenus.kt:1375 | 36 | 探索再開 |
| coreloop/CoreLoopMenus.kt:1375 | 81 | 閉じて探索を続ける |
| coreloop/CoreLoopMenus.kt:1377 | 36 | 帰還する |
| coreloop/CoreLoopMenus.kt:1377 | 90 | 遠征を終了し港へ帰還 |
| coreloop/CoreLoopMenus.kt:1377 | 108 | このマップへは戻れません |
| coreloop/CoreLoopMenus.kt:1391 | 68 | 装備 / MOD詳細 |
| coreloop/CoreLoopMenus.kt:1392 | 9 | 剣 |
| coreloop/CoreLoopMenus.kt:1394 | 46 | {…}を確認 |
| coreloop/CoreLoopMenus.kt:1396 | 45 | 現在の装備 |
| coreloop/CoreLoopMenus.kt:1396 | 71 | アイテムLv {…} |
| coreloop/CoreLoopMenus.kt:1396 | 67 | 内部Tier T{…} |
| coreloop/CoreLoopMenus.kt:1396 | 72 | 下の装備に全情報 |
| coreloop/CoreLoopMenus.kt:1397 | 54 | 選択したMOD |
| coreloop/CoreLoopMenus.kt:1399 | 45 | ロール範囲 |
| coreloop/CoreLoopMenus.kt:1399 | 47 | 品質 {…}% |
| coreloop/CoreLoopMenus.kt:1399 | 67 | 内部Tier T{…} |
| coreloop/CoreLoopMenus.kt:1400 | 72 | MODがありません |
| coreloop/CoreLoopMenus.kt:1400 | 81 | オーブで昇格・追加 |
| coreloop/CoreLoopMenus.kt:1400 | 55 | ノーマル 0枠 |
| coreloop/CoreLoopMenus.kt:1400 | 55 | マジック 2枠 |
| coreloop/CoreLoopMenus.kt:1400 | 37 | レア 6枠 |
| coreloop/CoreLoopMenus.kt:1403 | 36 | の刻印石 |
| coreloop/CoreLoopMenus.kt:1403 | 41 | MOD {…} |
| coreloop/CoreLoopMenus.kt:1404 | 41 | 範囲 {…} |
| coreloop/CoreLoopMenus.kt:1404 | 128 | 品質 {…}% / 内部Tier T{…} |
| coreloop/CoreLoopMenus.kt:1408 | 18 | 修理 |
| coreloop/CoreLoopMenus.kt:1408 | 64 | {…}を修理する |
| coreloop/CoreLoopMenus.kt:1410 | 27 | 加工へ |
| coreloop/CoreLoopMenus.kt:1410 | 108 | この装備のMODを加工する |
| coreloop/CoreLoopMenus.kt:1410 | 72 | 港で使用できます |
| coreloop/CoreLoopMenus.kt:1422 | 68 | 旧刻印石 / 交換 |
| coreloop/CoreLoopMenus.kt:1423 | 63 | 旧形式の所持品 |
| coreloop/CoreLoopMenus.kt:1423 | 54 | 所持 {…} 個 |
| coreloop/CoreLoopMenus.kt:1423 | 72 | 選んで交換内容へ |
| coreloop/CoreLoopMenus.kt:1423 | 81 | 選ぶだけでは未消費 |
| coreloop/CoreLoopMenus.kt:1424 | 54 | 交換について |
| coreloop/CoreLoopMenus.kt:1424 | 72 | 旧石のMODは消え |
| coreloop/CoreLoopMenus.kt:1424 | 81 | オーブに変わります |
| coreloop/CoreLoopMenus.kt:1424 | 74 | 改変は石のTier分 |
| coreloop/CoreLoopMenus.kt:1424 | 42 | 錬金は1個 |
| coreloop/CoreLoopMenus.kt:1424 | 81 | 交換は取り消し不可 |
| coreloop/CoreLoopMenus.kt:1426 | 37 | 旧石{…} |
| coreloop/CoreLoopMenus.kt:1437 | 86 | 旧刻印石 / 交換確認 |
| coreloop/CoreLoopMenus.kt:1438 | 54 | 消費するもの |
| coreloop/CoreLoopMenus.kt:1438 | 80 | T{…} 旧刻印石1個 |
| coreloop/CoreLoopMenus.kt:1438 | 81 | 元のMODは失われる |
| coreloop/CoreLoopMenus.kt:1438 | 81 | 交換は取り消せない |
| coreloop/CoreLoopMenus.kt:1439 | 54 | 受け取るもの |
| coreloop/CoreLoopMenus.kt:1439 | 54 | 改変のオーブ |
| coreloop/CoreLoopMenus.kt:1439 | 32 | {…} 個 |
| coreloop/CoreLoopMenus.kt:1439 | 54 | 錬金のオーブ |
| coreloop/CoreLoopMenus.kt:1439 | 19 | 1 個 |
| coreloop/CoreLoopMenus.kt:1442 | 36 | 交換する |
| coreloop/CoreLoopMenus.kt:1447 | 45 | 画面の見方 |
| coreloop/CoreLoopMenus.kt:1448 | 18 | 操作 |
| coreloop/CoreLoopMenus.kt:1448 | 81 | 絵や名前をクリック |
| coreloop/CoreLoopMenus.kt:1448 | 72 | 明るい縁は選択中 |
| coreloop/CoreLoopMenus.kt:1448 | 72 | 金色は実行ボタン |
| coreloop/CoreLoopMenus.kt:1448 | 81 | 赤・暗色は使用不可 |
| coreloop/CoreLoopMenus.kt:1448 | 81 | 選択だけでは未消費 |
| coreloop/CoreLoopMenus.kt:1448 | 72 | 右下で確定します |
| coreloop/CoreLoopMenus.kt:1448 | 81 | ESCで閉じられます |
| coreloop/CoreLoopMenus.kt:1449 | 54 | 所持品と表示 |
| coreloop/CoreLoopMenus.kt:1449 | 90 | 素材はアイテムで所持 |
| coreloop/CoreLoopMenus.kt:1449 | 66 | 表示数は最大64 |
| coreloop/CoreLoopMenus.kt:1449 | 72 | 正確な数は説明欄 |
| coreloop/CoreLoopMenus.kt:1449 | 72 | 採取は道具で操作 |
| coreloop/CoreLoopMenus.kt:1449 | 81 | 戦利品は右クリック |
| coreloop/CoreLoopMenus.kt:1449 | 81 | 工房で直接使えます |
| coreloop/CoreLoopMenus.kt:1449 | 80 | 工房の × は必要数 |
| coreloop/CoreLoopMenus.kt:1449 | 81 | 足りる：素材は充足 |
| coreloop/CoreLoopMenus.kt:1449 | 72 | あと何個：不足数 |
| coreloop/CoreLoopMenus.kt:1449 | 63 | 入手先から調達 |
| coreloop/CoreLoopMenus.kt:1449 | 81 | 装備の全情報は詳細 |
| coreloop/CoreLoopMenus.kt:1450 | 81 | 左右が見切れるとき |
| coreloop/CoreLoopMenus.kt:1451 | 80 | 設定 → ビデオ設定 |
| coreloop/CoreLoopMenus.kt:1452 | 85 | GUI倍率を1段下げる |
| coreloop/CoreLoopMenus.kt:1453 | 99 | クライアントMODは不要 |
| coreloop/CoreLoopMenus.kt:1460 | 86 | 冒険の手帳 / 遊び方 |
| coreloop/CoreLoopMenus.kt:1461 | 18 | 一周 |
| coreloop/CoreLoopMenus.kt:1461 | 18 | 戦闘 |
| coreloop/CoreLoopMenus.kt:1461 | 18 | 採取 |
| coreloop/CoreLoopMenus.kt:1461 | 18 | 育成 |
| coreloop/CoreLoopMenus.kt:1466 | 75 | 地図台でT1を入手 |
| coreloop/CoreLoopMenus.kt:1466 | 72 | 地図を選んで出発 |
| coreloop/CoreLoopMenus.kt:1466 | 81 | 道の先のボスを討伐 |
| coreloop/CoreLoopMenus.kt:1466 | 72 | 素材と地図を獲得 |
| coreloop/CoreLoopMenus.kt:1466 | 81 | 港へ帰還して工房へ |
| coreloop/CoreLoopMenus.kt:1466 | 81 | 装備を更新して次へ |
| coreloop/CoreLoopMenus.kt:1467 | 81 | 採取と雑魚戦は自由 |
| coreloop/CoreLoopMenus.kt:1467 | 81 | 寄り道で特別な報酬 |
| coreloop/CoreLoopMenus.kt:1467 | 72 | 素材は自分で採取 |
| coreloop/CoreLoopMenus.kt:1467 | 81 | 戦利品は右クリック |
| coreloop/CoreLoopMenus.kt:1467 | 81 | 途中帰還もできます |
| coreloop/CoreLoopMenus.kt:1468 | 90 | 左クリックで通常攻撃 |
| coreloop/CoreLoopMenus.kt:1468 | 78 | 3段階の大剣コンボ |
| coreloop/CoreLoopMenus.kt:1468 | 90 | 右クリックで踏み込み |
| coreloop/CoreLoopMenus.kt:1468 | 51 | Fキーで回避 |
| coreloop/CoreLoopMenus.kt:1468 | 75 | 2〜4番のスキルを |
| coreloop/CoreLoopMenus.kt:1468 | 72 | 右クリックで発動 |
| coreloop/CoreLoopMenus.kt:1469 | 81 | 赤い予兆から離れる |
| coreloop/CoreLoopMenus.kt:1469 | 51 | 5番：回復薬 |
| coreloop/CoreLoopMenus.kt:1469 | 87 | 砥石：3分間攻撃強化 |
| coreloop/CoreLoopMenus.kt:1469 | 90 | マナ・再使用はHUDへ |
| coreloop/CoreLoopMenus.kt:1469 | 90 | 敵を倒してオーブ入手 |
| coreloop/CoreLoopMenus.kt:1469 | 81 | 戦利品を右クリック |
| coreloop/CoreLoopMenus.kt:1470 | 81 | 道具箱で道具を選ぶ |
| coreloop/CoreLoopMenus.kt:1470 | 81 | 木・岩・草・死体へ |
| coreloop/CoreLoopMenus.kt:1470 | 72 | 右クリック長押し |
| coreloop/CoreLoopMenus.kt:1470 | 63 | 離すと採取中断 |
| coreloop/CoreLoopMenus.kt:1470 | 90 | 対象の表示で進行確認 |
| coreloop/CoreLoopMenus.kt:1470 | 90 | 素材はインベントリへ |
| coreloop/CoreLoopMenus.kt:1471 | 81 | マップの石板MODで |
| coreloop/CoreLoopMenus.kt:1471 | 90 | 狙う素材を増やせます |
| coreloop/CoreLoopMenus.kt:1471 | 72 | 密集地域を探そう |
| coreloop/CoreLoopMenus.kt:1471 | 90 | 経験で採取育成を解放 |
| coreloop/CoreLoopMenus.kt:1471 | 65 | 高Tierの素材は |
| coreloop/CoreLoopMenus.kt:1471 | 83 | 同Tierのマップから |
| coreloop/CoreLoopMenus.kt:1472 | 81 | 採取素材を精製する |
| coreloop/CoreLoopMenus.kt:1472 | 81 | 精製素材で装備制作 |
| coreloop/CoreLoopMenus.kt:1472 | 81 | 武器と防具はT1〜4 |
| coreloop/CoreLoopMenus.kt:1472 | 60 | +強化は0〜30 |
| coreloop/CoreLoopMenus.kt:1472 | 90 | オーブでランダムMOD |
| coreloop/CoreLoopMenus.kt:1472 | 81 | 強化値とMODは保持 |
| coreloop/CoreLoopMenus.kt:1473 | 72 | 所持素材から使用 |
| coreloop/CoreLoopMenus.kt:1473 | 88 | 採取 → 精製 → 制作 |
| coreloop/CoreLoopMenus.kt:1473 | 81 | 素材・装備は市場へ |
| coreloop/CoreLoopMenus.kt:1473 | 72 | 戦利品券は銀貨へ |
| coreloop/CoreLoopMenus.kt:1473 | 81 | 装備庫から装備変更 |
| coreloop/CoreLoopMenus.kt:1473 | 87 | 欠片3個で専用ボスへ |
| coreloop/CoreLoopMenus.kt:1475 | 45 | 基本の流れ |
| coreloop/CoreLoopMenus.kt:1475 | 63 | 覚えておくこと |
| coreloop/CoreLoopMenus.kt:1476 | 27 | 道具箱 |
| coreloop/CoreLoopMenus.kt:1476 | 63 | 採取道具を選ぶ |
| coreloop/CoreLoopMenus.kt:1477 | 27 | 手帳へ |
| coreloop/CoreLoopMenus.kt:1477 | 72 | 冒険の手帳へ戻る |
| coreloop/CoreLoopMenus.kt:1483 | 36 | 灰燼の王 |
| coreloop/CoreLoopMenus.kt:1484 | 45 | 氷獄の巨兵 |
| coreloop/CoreLoopMenus.kt:1485 | 36 | 嵐の司祭 |
| coreloop/CoreLoopMenus.kt:1490 | 84 | 境界の試練 / T{…} |
| coreloop/CoreLoopMenus.kt:1492 | 54 | ボスへの挑戦 |
| coreloop/CoreLoopMenus.kt:1492 | 60 | 欠片3個で入場 |
| coreloop/CoreLoopMenus.kt:1492 | 72 | 通常マップとは別 |
| coreloop/CoreLoopMenus.kt:1492 | 62 | 解放 T1〜{…} |
| coreloop/CoreLoopMenus.kt:1492 | 72 | 選ぶと確認画面へ |
| coreloop/CoreLoopMenus.kt:1492 | 72 | まだ消費しません |
| coreloop/CoreLoopMenus.kt:1493 | 54 | 欠片の入手先 |
| coreloop/CoreLoopMenus.kt:1493 | 72 | 遠征中の寄り道で |
| coreloop/CoreLoopMenus.kt:1493 | 78 | 同じ欠片を3個収集 |
| coreloop/CoreLoopMenus.kt:1493 | 72 | 報酬は専用オーブ |
| coreloop/CoreLoopMenus.kt:1493 | 81 | 高揚と討伐証も入手 |
| coreloop/CoreLoopMenus.kt:1493 | 90 | 死亡・帰還で返却なし |
| coreloop/CoreLoopMenus.kt:1496 | 138 | {…}の欠片 所持 {…} / 必要3個 |
| coreloop/CoreLoopMenus.kt:1496 | 108 | クリック：挑戦内容の確認 |
| coreloop/CoreLoopMenus.kt:1505 | 68 | 試練 / 出発確認 |
| coreloop/CoreLoopMenus.kt:1506 | 74 | T{…} の専用ボス |
| coreloop/CoreLoopMenus.kt:1506 | 46 | {…}の欠片 |
| coreloop/CoreLoopMenus.kt:1506 | 41 | 所持 {…} |
| coreloop/CoreLoopMenus.kt:1506 | 33 | 必要3個 |
| coreloop/CoreLoopMenus.kt:1506 | 63 | 一度きりの挑戦 |
| coreloop/CoreLoopMenus.kt:1506 | 90 | 敗北・帰還で返却なし |
| coreloop/CoreLoopMenus.kt:1506 | 81 | 準備失敗時だけ返却 |
| coreloop/CoreLoopMenus.kt:1507 | 45 | 討伐の報酬 |
| coreloop/CoreLoopMenus.kt:1507 | 15 | 2個 |
| coreloop/CoreLoopMenus.kt:1507 | 73 | 高揚のオーブ 1個 |
| coreloop/CoreLoopMenus.kt:1507 | 27 | 討伐証 |
| coreloop/CoreLoopMenus.kt:1507 | 54 | 挑戦できます |
| coreloop/CoreLoopMenus.kt:1507 | 56 | Tierが未解放 |
| coreloop/CoreLoopMenus.kt:1507 | 90 | 欠片が不足しています |
| coreloop/CoreLoopMenus.kt:1510 | 18 | 挑戦 |
| coreloop/CoreLoopMenus.kt:1510 | 105 | 欠片3個を使って挑戦する |
| coreloop/CoreLoopMenus.kt:1519 | 63 | ダンジョンの門 |
| coreloop/CoreLoopMenus.kt:1520 | 134 | T{…}・段階{…}・ひとりで挑戦 |
| coreloop/CoreLoopMenus.kt:1520 | 93 | 部屋7つ・ミニボス2体 |
| coreloop/CoreLoopMenus.kt:1520 | 54 | 最奥に桜牙獣 |
| coreloop/CoreLoopMenus.kt:1520 | 45 | 入場は無料 |
| coreloop/CoreLoopMenus.kt:1520 | 54 | 倒れたら港へ |
| coreloop/CoreLoopMenus.kt:1520 | 63 | 何度でも挑める |
| coreloop/CoreLoopMenus.kt:1521 | 45 | 踏破の報酬 |
| coreloop/CoreLoopMenus.kt:1521 | 72 | 討伐証・戦利品券 |
| coreloop/CoreLoopMenus.kt:1521 | 73 | 高揚のオーブ 1個 |
| coreloop/CoreLoopMenus.kt:1521 | 64 | 試練の欠片 1個 |
| coreloop/CoreLoopMenus.kt:1521 | 72 | 部屋の敵の戦利品 |
| coreloop/CoreLoopMenus.kt:1521 | 84 | 倒すと10秒後に港へ |
| coreloop/CoreLoopMenus.kt:1522 | 45 | 散り桜の社 |
| coreloop/CoreLoopMenus.kt:1523 | 160 | 花守の回廊 → 散華の間 → 散り桜の庭 |
| coreloop/CoreLoopMenus.kt:1523 | 54 | ボス：桜牙獣 |
| coreloop/CoreLoopMenus.kt:1523 | 63 | クリック：入る |
| coreloop/CoreLoopMenus.kt:1525 | 18 | 入る |
| coreloop/CoreLoopMenus.kt:1525 | 72 | 散り桜の社へ入る |
| coreloop/CoreLoopServer.kt:180 | 18 | 遠征 |
| coreloop/CoreLoopServer.kt:209 | 270 | インベントリがいっぱいです。空きを作ってから採取してください |
| coreloop/CoreLoopServer.kt:265 | 135 | 別の接続からログインしました。 |
| coreloop/CoreLoopServer.kt:272 | 261 | 前回の報酬を保存中です。少し待ってから再接続してください。 |
| coreloop/CoreLoopServer.kt:277 | 133 | 魔術記録を読み込めません: {…} |
| coreloop/CoreLoopServer.kt:295 | 133 | 魔術素材を移行できません: {…} |
| coreloop/CoreLoopServer.kt:308 | 280 | 保存データを読み込めません。既存データは保持されています。{…} |
| coreloop/CoreLoopServer.kt:342 | 45 | 力尽きた… |
| coreloop/CoreLoopServer.kt:342 | 126 | 獲得素材を持って港へ戻ります |
| coreloop/CoreLoopServer.kt:355 | 189 | コロニー設備の所持品を読み込めませんでした |
| coreloop/CoreLoopServer.kt:359 | 405 | 所持品が満杯です。従来の保存品 {…} 種類は消えていません。空きを作ると所持品に表示されます |
| coreloop/CoreLoopServer.kt:366 | 99 | 開拓者の町へようこそ。 |
| coreloop/CoreLoopServer.kt:367 | 339 | ホットバー9番の「冒険の手帳」を右クリックすると、一周の流れを確認できます。 |
| coreloop/CoreLoopServer.kt:585 | 243 | 魔術書を開くには ProjectS のリソースパックが必要です |
| coreloop/CoreLoopServer.kt:594 | 117 | 魔術書を最初からにしました |
| coreloop/CoreLoopServer.kt:604 | 198 | 開拓大陸のワールドがまだ書き出されていません |
| coreloop/CoreLoopServer.kt:611 | 206 | /creative は /frontier の中で使ってください |
| coreloop/CoreLoopServer.kt:621 | 276 | 先に /hub で港へ戻ってから /researchtest を使ってください |
| coreloop/CoreLoopServer.kt:629 | 259 | Lv{…}・銀貨50000にしました。手帳の「成長樹」で開けます |
| coreloop/CoreLoopServer.kt:629 | 72 | できませんでした |
| coreloop/CoreLoopServer.kt:642 | 207 | ボスを怒らせた（体力を怒りの境目の少し下へ）。 |
| coreloop/CoreLoopServer.kt:642 | 189 | /bosstest のボス戦の中で使ってください。 |
| coreloop/CoreLoopServer.kt:650 | 228 | /minibosstest 1 か 2（散り桜の社のミニボス1・2） |
| coreloop/CoreLoopServer.kt:670 | 334 | 千本桜が来る。庭の縁でやり過ごし、終わったらもう一度 /dungeontest boss |
| coreloop/CoreLoopServer.kt:751 | 24 | name |
| coreloop/CoreLoopServer.kt:753 | 768 | /motion greatsword_charge \| dual_blades_dance [self] \| stop \| watch（self：自分にも人形を見せる。watch：通常攻撃の人形も自分に見せるのを切り替え。F5で確認用） |
| coreloop/CoreLoopServer.kt:759 | 111 | オン（F5で見てください） |
| coreloop/CoreLoopServer.kt:759 | 18 | オフ |
| coreloop/CoreLoopServer.kt:759 | 163 | 通常攻撃の人形を自分にも見せる：{…} |
| coreloop/CoreLoopServer.kt:762 | 142 | そのモーションはありません: {…} |
| coreloop/CoreLoopServer.kt:766 | 142 | そのモーションはありません: {…} |
| coreloop/CoreLoopServer.kt:775 | 55 | ClaudeTest |
| coreloop/CoreLoopServer.kt:798 | 90 | 大剣を持っていません |
| coreloop/CoreLoopServer.kt:805 | 139 | /skilltest の中で使ってください |
| coreloop/CoreLoopServer.kt:810 | 247 | 戦闘演出：{…}（/effects で切替。自分の表示だけ変更） |
| coreloop/CoreLoopServer.kt:821 | 167 | 光は8つまで。/lighttest off で消す。 |
| coreloop/CoreLoopServer.kt:847 | 302 | 桜の光 {…}/8（/lighttest orbit で回る光、/lighttest off で消す） |
| coreloop/CoreLoopServer.kt:890 | 40 | grimoire |
| coreloop/CoreLoopServer.kt:917 | 126 | 自分と重なる場所には置けない |
| coreloop/CoreLoopServer.kt:922 | 207 | ここには置けない。床と空きスペースを確かめよう |
| coreloop/CoreLoopServer.kt:927 | 235 | {…}を設置した。左クリックかしゃがみ右クリックで回収 |
| coreloop/CoreLoopServer.kt:929 | 189 | 配置を保存できなかったため設置していません |
| coreloop/CoreLoopServer.kt:937 | 234 | インベントリに空きを作ってから設備を回収してください |
| coreloop/CoreLoopServer.kt:942 | 198 | 上か棚の中に設備がある。先にJarを取り出そう |
| coreloop/CoreLoopServer.kt:947 | 100 | {…}をアイテムに戻した |
| coreloop/CoreLoopServer.kt:949 | 189 | 配置を保存できなかったため回収していません |
| coreloop/CoreLoopServer.kt:957 | 135 | このJarはすでに設置されている |
| coreloop/CoreLoopServer.kt:962 | 190 | {…}を棚に収めた。棚を開くと瓶を取り出せる |
| coreloop/CoreLoopServer.kt:964 | 144 | Jarの配置を保存できませんでした |
| coreloop/CoreLoopServer.kt:972 | 243 | インベントリに空きを作ってからJarを取り出してください |
| coreloop/CoreLoopServer.kt:979 | 127 | {…}のJarを棚から取り出した |
| coreloop/CoreLoopServer.kt:983 | 144 | Jarの回収を保存できませんでした |
| coreloop/CoreLoopServer.kt:996 | 160 | コロニーを準備できませんでした: {…} |
| coreloop/CoreLoopServer.kt:1007 | 135 | コロニーへ移動できませんでした |
| coreloop/CoreLoopServer.kt:1018 | 63 | 自分のコロニー |
| coreloop/CoreLoopServer.kt:1019 | 144 | 古い観測工房が庭の奥に眠っている |
| coreloop/CoreLoopServer.kt:1022 | 297 | 持ち帰った素材に古い区画が反応した。研究机を手に持って、庭へ置こう |
| coreloop/CoreLoopServer.kt:1024 | 216 | 手持ちの設備を庭へ置き、研究机から未知を調べよう |
| coreloop/CoreLoopServer.kt:1024 | 333 | 設備を手に持って右クリックで配置。遠征で異質素材を拾い、この庭へ持ち帰ろう |
| coreloop/CoreLoopServer.kt:1036 | 171 | 研究テストの準備を保存できませんでした |
| coreloop/CoreLoopServer.kt:1050 | 201 | /researchtest：{…}。再実行で補充できます |
| coreloop/CoreLoopServer.kt:1052 | 306 | 研究机の自動設置場所がありません。手持ちの研究机を庭に置いてください |
| coreloop/CoreLoopServer.kt:1066 | 108 | 港への帰還に失敗しました |
| coreloop/CoreLoopServer.kt:1074 | 180 | 港へ戻った。遠征路の未知をまた持ち帰ろう |
| coreloop/CoreLoopServer.kt:1084 | 294 | 星図にはまだ描かれていない輪がある。Essentiaはその入口にすぎない |
| coreloop/CoreLoopServer.kt:1086 | 330 | 封印の向こうに大きな円形装置が見える。今のEssentiaではまだ共鳴が足りない |
| coreloop/CoreLoopServer.kt:1087 | 207 | 共鳴が不足している。まず研究机とJarを満たそう |
| coreloop/CoreLoopServer.kt:1097 | 135 | 魔術記録を保存できませんでした |
| coreloop/CoreLoopServer.kt:1109 | 72 | 古い工房の目覚め |
| coreloop/CoreLoopServer.kt:1110 | 144 | 持ち帰った未知に、紙と石が応えた |
| coreloop/CoreLoopServer.kt:1118 | 69 | 最初のEssentia |
| coreloop/CoreLoopServer.kt:1118 | 144 | 置いたJarに、最初の性質が宿った |
| coreloop/CoreLoopServer.kt:1130 | 153 | 港へ帰還してから操作してください。 |
| coreloop/CoreLoopServer.kt:1137 | 99 | データを読み込めません |
| coreloop/CoreLoopServer.kt:1151 | 242 | 冒険Lv{…}！ 成長と職業の手帳で解放内容を確認できます |
| coreloop/CoreLoopServer.kt:1152 | 64 | 次の目標：{…} |
| coreloop/CoreLoopServer.kt:1174 | 216 | 保存できませんでした。操作をやり直してください。 |
| coreloop/CoreLoopServer.kt:1202 | 87 | MODは最大3個です。 |
| coreloop/CoreLoopServer.kt:1249 | 193 | 地図を広げています… 地形と遠征先を準備中。 |
| coreloop/CoreLoopServer.kt:1255 | 126 | 遠征を開始できませんでした。 |
| coreloop/CoreLoopServer.kt:1283 | 221 | T{…} {…}へ遠征開始。道の先に{…}。特産は{…}。 |
| coreloop/CoreLoopServer.kt:1284 | 371 | T{…} 遠征開始。道の先にボス。裂け目・儀式の寄り道には専用オーブと欠片があります。 |
| coreloop/CoreLoopServer.kt:1314 | 118 | {…}の境界を開いています… |
| coreloop/CoreLoopServer.kt:1319 | 109 | {…}の扉を開いています… |
| coreloop/CoreLoopServer.kt:1338 | 126 | 試練を開始できませんでした。 |
| coreloop/CoreLoopServer.kt:1373 | 218 | {…}へ。部屋の敵を退け、奥の庭で{…}を討伐せよ！ |
| coreloop/CoreLoopServer.kt:1374 | 128 | {…}へ到着。{…}を討伐せよ！ |
| coreloop/CoreLoopServer.kt:1389 | 207 | 試練の準備に失敗しました。再接続してください。 |
| coreloop/CoreLoopServer.kt:1441 | 243 | 遠征の準備に失敗しました。再接続時に状態を確認します。 |
| coreloop/CoreLoopServer.kt:1523 | 293 | {…}報酬を保存：{…} ×{…} / 欠片 ×{…}。欠片3個で専用ボスへ！ |
| coreloop/CoreLoopServer.kt:1536 | 153 | 討伐報酬の保存を再試行しています。 |
| coreloop/CoreLoopServer.kt:1541 | 36 | 討伐達成 |
| coreloop/CoreLoopServer.kt:1542 | 153 | 討伐証・次の地図・試練の欠片を保存 |
| coreloop/CoreLoopServer.kt:1542 | 165 | 高揚1個・試練の欠片1個・討伐証を保存 |
| coreloop/CoreLoopServer.kt:1543 | 165 | 専用オーブ2個・高揚1個・討伐証を保存 |
| coreloop/CoreLoopServer.kt:1545 | 324 | 羅針盤から港へ帰還し、工房で装備を更新しよう。探索は続けても構いません。 |
| coreloop/CoreLoopServer.kt:1573 | 235 | {…}秒後に港へ戻ります。落ちた戦利品を拾っておこう。 |
| coreloop/CoreLoopServer.kt:1619 | 216 | 帰還を保存できません。もう一度操作してください。 |
| coreloop/CoreLoopServer.kt:1637 | 394 | 採取素材を精製 → 工房で装備を制作 → 装備庫から使う・売る。市場で素材の売買もできます。 |
| coreloop/CoreLoopServer.kt:1642 | 288 | 帰還先を読み直すため再接続してください。獲得素材は保存済みです。 |
| coreloop/CoreLoopServer.kt:1655 | 183 | HPが満タン、または回復薬の再使用待ちです |
| coreloop/CoreLoopServer.kt:1666 | 99 | 使用できませんでした。 |
| coreloop/CoreLoopServer.kt:1692 | 72 | 遠征先を準備中… |
| coreloop/CoreLoopServer.kt:1693 | 179 | 開拓港  武器T{…} / 防具T{…}  手帳 [9] |
| coreloop/CoreLoopServer.kt:1694 | 112 | 討伐達成・手帳 [9] で帰還 |
| coreloop/CoreLoopServer.kt:1696 | 121 | 道の先のボスへ  戦利品 {…} |
| coreloop/CoreLoopServer.kt:1713 | 82 | 倒した敵 {…}体 /  |
| coreloop/CoreLoopServer.kt:1713 | 143 | 発見 {…}か所・寄り道 {…}/{…} |
| coreloop/CoreLoopServer.kt:1718 | 153 | 刻印工房でオーブを使い、MODを抽選 |
| coreloop/CoreLoopServer.kt:1718 | 149 | 変成でマジック / 錬金でレア装備へ |
| coreloop/CoreLoopServer.kt:1719 | 144 | 欠片が集まった！境界の試練に挑戦 |
| coreloop/CoreLoopServer.kt:1719 | 162 | 専用ボスから特別な加工オーブを狙おう |
| coreloop/CoreLoopServer.kt:1720 | 171 | 工房で装備を作り、装備庫から装備しよう |
| coreloop/CoreLoopServer.kt:1720 | 162 | 足りない素材は採取、または市場で購入 |
| coreloop/CoreLoopServer.kt:1721 | 129 | T4装備完成！石板で地図を調整 |
| coreloop/CoreLoopServer.kt:1721 | 182 | 密集地域や高Tierの資源を狙って周回しよう |
| coreloop/CoreLoopServer.kt:1722 | 117 | 地図台で地図を選び、遠征へ |
| coreloop/CoreLoopServer.kt:1722 | 128 | ボス討伐で次Tierの地図を解放 |
| coreloop/CoreMageRules.kt:11 | 9 | 火 |
| coreloop/CoreMageRules.kt:11 | 9 | 氷 |
| coreloop/CoreMageRules.kt:11 | 9 | 雷 |
| coreloop/CoreMageRules.kt:11 | 9 | 風 |
| coreloop/CoreMageRules.kt:17 | 18 | 砕け |
| coreloop/CoreMageRules.kt:19 | 18 | 伝導 |
| coreloop/CoreMageRules.kt:21 | 27 | 過負荷 |
| coreloop/CoreMageRules.kt:23 | 18 | 凍結 |
| coreloop/CoreMageRules.kt:25 | 18 | 破裂 |
| coreloop/CoreMageRules.kt:212 | 369 | 陣の紋を全部使って、狙った所に解き放つ。一番多い紋で中身が変わり、紋が多いほど強い |
| coreloop/CoreMageRules.kt:217 | 81 | 大詠唱：四元の爆発 |
| coreloop/CoreMageRules.kt:218 | 63 | 大詠唱：大流星 |
| coreloop/CoreMageRules.kt:219 | 54 | 大詠唱：氷河 |
| coreloop/CoreMageRules.kt:220 | 54 | 大詠唱：雷雨 |
| coreloop/CoreMageRules.kt:221 | 63 | 大詠唱：大台風 |
| coreloop/CoreMageRules.kt:222 | 27 | 大詠唱 |
| coreloop/CoreMapCaches.kt:48 | 72 | 遺された宝箱⏎右クリックで開く |
| coreloop/CoreMapCaches.kt:74 | 225 | 近くの敵が宝箱を守っています。先に倒してください。 |
| coreloop/CorePartyDialogs.kt:52 | 27 | close |
| coreloop/CorePartyDialogs.kt:64 | 72 | できませんでした |
| coreloop/CorePartyDialogs.kt:106 | 104 | {…} に招待を送りました |
| coreloop/CorePartyDialogs.kt:109 | 93 |  からパーティの招待   |
| coreloop/CorePartyDialogs.kt:110 | 44 | [参加する] |
| coreloop/CorePartyDialogs.kt:112 | 95 | {…} のパーティに入る |
| coreloop/CorePartyDialogs.kt:114 | 62 | [パーティ画面] |
| coreloop/CorePartyDialogs.kt:114 | 34 | /party |
| coreloop/CorePartyDialogs.kt:125 | 86 | {…} が参加しました |
| coreloop/CorePartyDialogs.kt:139 | 117 | パーティからはずされました |
| coreloop/CorePartyDialogs.kt:149 | 77 | {…} が抜けました |
| coreloop/CorePartyDialogs.kt:166 | 36 | （不在） |
| coreloop/CorePartyDialogs.kt:178 | 126 | まだパーティに入っていません |
| coreloop/CorePartyDialogs.kt:179 | 234 | 作って仲間を招待するか、届いた招待に参加しましょう。 |
| coreloop/CorePartyDialogs.kt:183 | 63 | 届いている招待 |
| coreloop/CorePartyDialogs.kt:186 | 114 |   のパーティ · {…}/{…} |
| coreloop/CorePartyDialogs.kt:187 | 50 | {…} に参加 |
| coreloop/CorePartyDialogs.kt:187 | 20 | join |
| coreloop/CorePartyDialogs.kt:189 | 72 | 招待をすべて断る |
| coreloop/CorePartyDialogs.kt:189 | 35 | decline |
| coreloop/CorePartyDialogs.kt:191 | 63 | パーティを作る |
| coreloop/CorePartyDialogs.kt:191 | 34 | create |
| coreloop/CorePartyDialogs.kt:194 | 40 | メンバー  |
| coreloop/CorePartyDialogs.kt:195 | 52 | 　　準備OK  |
| coreloop/CorePartyDialogs.kt:202 | 22 |  試し |
| coreloop/CorePartyDialogs.kt:204 | 43 | ✔ 準備OK |
| coreloop/CorePartyDialogs.kt:204 | 36 | 準備中… |
| coreloop/CorePartyDialogs.kt:207 | 26 |   空き |
| coreloop/CorePartyDialogs.kt:209 | 246 | 全員が準備OKになったら、リーダーが地図から出発します。 |
| coreloop/CorePartyDialogs.kt:210 | 135 | （一緒に出発は次の段階で実装） |
| coreloop/CorePartyDialogs.kt:212 | 24 | name |
| coreloop/CorePartyDialogs.kt:212 | 90 | 招待するプレイヤー名 |
| coreloop/CorePartyDialogs.kt:213 | 45 | 招待を送る |
| coreloop/CorePartyDialogs.kt:213 | 126 | 入力した名前の人に招待を送る |
| coreloop/CorePartyDialogs.kt:214 | 26 | invite |
| coreloop/CorePartyDialogs.kt:217 | 63 | 準備を取り消す |
| coreloop/CorePartyDialogs.kt:217 | 30 | 準備OK |
| coreloop/CorePartyDialogs.kt:217 | 30 | ready |
| coreloop/CorePartyDialogs.kt:218 | 59 | {…} をはずす |
| coreloop/CorePartyDialogs.kt:218 | 18 | kick |
| coreloop/CorePartyDialogs.kt:219 | 72 | 試しの仲間を追加 |
| coreloop/CorePartyDialogs.kt:219 | 22 | mate |
| coreloop/CorePartyDialogs.kt:220 | 72 | パーティを抜ける |
| coreloop/CorePartyDialogs.kt:220 | 27 | leave |
| coreloop/CorePartyDialogs.kt:222 | 36 | パーティ |
| coreloop/CorePartyDialogs.kt:222 | 36 | パーティ |
| coreloop/CorePartyDialogs.kt:224 | 27 | 閉じる |
| coreloop/CorePartyDialogs.kt:224 | 27 | close |
| coreloop/CorePartyDialogs.kt:260 | 42 | 練習相手A |
| coreloop/CorePartyDialogs.kt:260 | 42 | 練習相手B |
| coreloop/CorePartyDialogs.kt:260 | 42 | 練習相手C |
| coreloop/CorePassiveTree.kt:47 | 63 | ここから伸ばす |
| coreloop/CorePassiveTree.kt:49 | 228 | この輪の主要ノードを取ると、1点で次から1つ選べる： |
| coreloop/CorePassiveTree.kt:49 | 9 | ／ |
| coreloop/CorePassiveTree.kt:217 | 144 | 極意の輪に主要ノードがありません |
| coreloop/CorePlayerCombat.kt:160 | 18 | なし |
| coreloop/CorePlayerCombat.kt:160 | 32 | 陣 {…} |
| coreloop/CorePlayerCombat.kt:216 | 207 | 武器が破損しています。装備庫で修理してください |
| coreloop/CorePlayerCombat.kt:257 | 207 | 武器が破損しています。装備庫で修理してください |
| coreloop/CorePlayerCombat.kt:259 | 122 | {…}はLv{…}で解放されます |
| coreloop/CorePlayerCombat.kt:260 | 74 | {…}：あと{…}秒 |
| coreloop/CorePlayerCombat.kt:261 | 36 | 行動不能 |
| coreloop/CorePlayerCombat.kt:261 | 109 | {…}：スキルを使えません |
| coreloop/CorePlayerCombat.kt:263 | 90 | 獣の目覚めの最中です |
| coreloop/CorePlayerCombat.kt:265 | 69 | 獣が3つ必要です |
| coreloop/CorePlayerCombat.kt:267 | 187 | {…}が足りません（必要 {…} / 現在 {…}） |
| coreloop/CorePlayerCombat.kt:270 | 131 | マナが足りません（必要 {…}） |
| coreloop/CorePlayerCombat.kt:272 | 225 | 氷の庭は一つまで。今の庭が解けるまで待ってください |
| coreloop/CorePlayerCombat.kt:275 | 144 | 氷の庭を置ける床を狙ってください |
| coreloop/CorePlayerCombat.kt:280 | 144 | 氷の庭を置ける床を狙ってください |
| coreloop/CorePlayerCombat.kt:326 | 54 | 狂戦士の甲冑 |
| coreloop/CorePlayerCombat.kt:333 | 45 | 獣の目覚め |
| coreloop/CorePlayerCombat.kt:350 | 99 | 鉄の意志：回避できない |
| coreloop/CorePlayerCombat.kt:382 | 81 | 獣の目覚めが切れた |
| coreloop/CorePlayerCombat.kt:487 | 168 | 氷の庭：6秒。敵を誘い、他の術を重ねる |
| coreloop/CorePlayerCombat.kt:491 | 234 | 氷の庭を展開できませんでした。マナと再使用を戻します |
| coreloop/CorePlayerCombat.kt:614 | 63 | 吹き飛ばされた |
| coreloop/CorePlayerCombat.kt:614 | 27 | 怯んだ |
| coreloop/CorePlayerCombat.kt:745 | 54 | 全力の一振り |
| coreloop/CorePlayerCombat.kt:1169 | 45 | 叩き落とし |
| coreloop/CorePlayerCombat.kt:1237 | 22 |  弱点 |
| coreloop/CorePlayerCombat.kt:1621 | 145 | 反響：スキルが{…}回繰り返される |
| coreloop/CorePlayerCombat.kt:1627 | 73 | 元素の極み：{…} |
| coreloop/CorePlayerCombat.kt:1842 | 18 | 轟進 |
| coreloop/CorePlayerCombat.kt:1842 | 27 | 地割り |
| coreloop/CorePlayerCombat.kt:1842 | 36 | 押し薙ぎ |
| coreloop/CorePlayerCrowdControl.kt:67 | 51 | {…} {…}秒 |
| coreloop/CorePolish05ForgeFlow.kt:77 | 36 | の刻印石 |
| coreloop/CorePolish05ForgeFlow.kt:81 | 55 | 開拓者の{…} |
| coreloop/CorePolish05ForgeFlow.kt:89 | 36 | 物理攻撃 |
| coreloop/CorePolish05ForgeFlow.kt:89 | 30 | 最大HP |
| coreloop/CorePolish05ForgeFlow.kt:96 | 18 | 大剣 |
| coreloop/CorePolish05ForgeFlow.kt:96 | 18 | 防具 |
| coreloop/CorePolish05ForgeFlow.kt:107 | 97 | {…} {…}があと{…}個 |
| coreloop/CorePolish05ForgeFlow.kt:127 | 27 | 破損中 |
| coreloop/CorePolish05ForgeFlow.kt:129 | 63 | 素材が足りない |
| coreloop/CorePolish05ForgeFlow.kt:133 | 68 | {…} 足りません |
| coreloop/CorePolish05ForgeFlow.kt:134 | 179 | 手持ちの素材で あと {…} 回 強化できます |
| coreloop/CorePolish05ForgeFlow.kt:147 | 36 | 鍛造中… |
| coreloop/CorePolish05ForgeFlow.kt:171 | 216 | 状態が変わりました。費用を確認し直してください。 |
| coreloop/CorePolish05ForgeFlow.kt:184 | 147 | 集中鍛造：成功率が15上がります。 |
| coreloop/CorePolish05ForgeFlow.kt:184 | 108 | 通常の強化に戻しました。 |
| coreloop/CorePolish05ForgeFlow.kt:207 | 252 | 保存できませんでした。状態を確認してやり直してください。 |
| coreloop/CorePolish05ForgeFlow.kt:217 | 47 | 成功 +{…} |
| coreloop/CorePolish05ForgeFlow.kt:218 | 18 | 破損 |
| coreloop/CorePolish05ForgeFlow.kt:219 | 18 | 失敗 |
| coreloop/CorePolish05ForgeFlow.kt:222 | 120 | {…}  +{…} → +{…}  成功 |
| coreloop/CorePolish05ForgeFlow.kt:223 | 105 | {…}  +{…}  失敗・破損 |
| coreloop/CorePolish05ForgeFlow.kt:224 | 150 | {…}  +{…}  失敗（強化値は維持） |
| coreloop/CorePolish05ForgeFlow.kt:226 | 162 | 強化成功。次の強化を表示しています。 |
| coreloop/CorePolish05ForgeFlow.kt:226 | 162 | 破損しました。装備庫で修理できます。 |
| coreloop/CorePolish05ForgeFlow.kt:226 | 198 | 強化失敗。素材を消費し、段階を維持しました。 |
| coreloop/CoreProfessions.kt:7 | 36 | 木工精製 |
| coreloop/CoreProfessions.kt:7 | 36 | 鉱石精製 |
| coreloop/CoreProfessions.kt:7 | 36 | 石材精製 |
| coreloop/CoreProfessions.kt:7 | 36 | 皮革精製 |
| coreloop/CoreProfessions.kt:7 | 36 | 繊維精製 |
| coreloop/CoreProfessions.kt:8 | 36 | 武器製造 |
| coreloop/CoreProfessions.kt:8 | 36 | 防具製造 |
| coreloop/CoreProfessions.kt:30 | 92 | T{…} 採取遠征の地図 |
| coreloop/CoreRewardQueue.kt:36 | 135 | 報酬以外の操作は保留できません |
| coreloop/CoreRewardQueue.kt:41 | 162 | 同じ報酬元に異なる内容が送られました |
| coreloop/CoreRewardQueue.kt:72 | 223 | 報酬の保存待ちです。保存処理が停止しています: {…} |
| coreloop/CoreRewardQueue.kt:82 | 99 | この報酬は保存済みです |
| coreloop/CoreRewardQueue.kt:87 | 90 | 報酬を保存できません |
| coreloop/CoreRewardQueue.kt:99 | 151 | 報酬の保存を再試行しています: {…} |
| coreloop/CoreSkillCatalog.kt:45 | 147 | 獣：3つ使う（3つの時だけ使える） |
| coreloop/CoreSkillCatalog.kt:45 | 141 | 獣：止めた量で溜まる（最大3つ） |
| coreloop/CoreSkillCatalog.kt:45 | 54 | 獣：使わない |
| coreloop/CoreSkillCatalog.kt:46 | 114 | マナ {…} / 再使用 {…}秒 |
| coreloop/CoreSkillCatalog.kt:49 | 72 | 周囲の仲間を回復 |
| coreloop/CoreSkillCatalog.kt:49 | 72 | 周囲の仲間へ障壁 |
| coreloop/CoreSkillCatalog.kt:49 | 69 | 防御姿勢 / {…} |
| coreloop/CoreSkillCatalog.kt:50 | 65 |  + 回復力100% |
| coreloop/CoreSkillCatalog.kt:50 | 100 |  × {…} 回（1回ごと） |
| coreloop/CoreSkillCatalog.kt:53 | 118 | 現在 回復 {…} / 障壁 {…} |
| coreloop/CoreSkillCatalog.kt:54 | 64 |  / 全命中 {…} |
| coreloop/CoreSkillCatalog.kt:54 | 168 | 現在 {…}{…}（装備・スキル補正込み） |
| coreloop/CoreSkillCatalog.kt:55 | 251 | 対象の被回復・星・守護聖人は別 / 障壁は最大HPの75%まで |
| coreloop/CoreSkillCatalog.kt:55 | 189 | 敵防御・会心・弱点・固有効果・砥石は別計算 |
| coreloop/CoreSkillCatalog.kt:57 | 256 | 獣：全部使う（0でも撃てる）。1つごとに最大HP{…}%の障壁 |
| coreloop/CoreSkillCatalog.kt:58 | 96 | 獣：当たると1つ溜まる |
| coreloop/CoreSkillCatalog.kt:60 | 36 | 固有資源 |
| coreloop/CoreSkillCatalog.kt:60 | 139 | {…}：必要 {…} / 命中で +{…} |
| coreloop/CoreSkillCatalog.kt:62 | 214 | 出している間：CCが効かず、受けるダメージ-{…}% |
| coreloop/CoreSkillCatalog.kt:63 | 114 | マナ {…} / 再使用 {…}秒 |
| coreloop/CoreSkillCatalog.kt:64 | 36 | 詠唱速度 |
| coreloop/CoreSkillCatalog.kt:64 | 36 | 攻撃速度 |
| coreloop/CoreSkillCatalog.kt:64 | 110 | 発生 {…}秒 / {…}で短縮 |
| coreloop/CoreSkillCatalog.kt:65 | 51 | {…} {…}秒 |
| coreloop/CoreSkillCatalog.kt:66 | 68 | →スタン {…}秒 |
| coreloop/CoreSkillCatalog.kt:66 | 90 | （ボスには効かない） |
| coreloop/CoreSkillCatalog.kt:70 | 241 | ボスの崩し {…}＋獣1つごとに+{…}・3段目の直後{…}倍 |
| coreloop/CoreSkillCatalog.kt:71 | 51 | （1回ごと） |
| coreloop/CoreSkillCatalog.kt:71 | 87 | ボスの崩し {…}{…} |
| coreloop/CoreSkillCatalog.kt:73 | 223 | 最初の0.6秒：被害80%軽減 / 以降55%軽減 / {…}秒 |
| coreloop/CoreSkillCatalog.kt:74 | 177 | 印6秒：消費スキルの最初の命中が35%強化 |
| coreloop/CoreSkillCatalog.kt:74 | 156 | 防御崩し4秒：敵のAR・MRを20%低下 |
| coreloop/CoreSkillCatalog.kt:74 | 183 | 毒3秒：1秒ごとに係数の30%（重複しない） |
| coreloop/CoreSkillCatalog.kt:74 | 108 | 減速：敵の移動を40%低下 |
| coreloop/CoreSkillCatalog.kt:76 | 259 | 床板が有効範囲 / 壁・段差・穴へは広がらない / 同時設置1つ |
| coreloop/CoreSkillCatalog.kt:77 | 221 | 1体へ1秒間隔・最大4打 / 術式獲得は最初の命中だけ |
| coreloop/CoreSkillCatalog.kt:78 | 264 | 在域中40%減速（氷大成50%） / ボス10% / 退出後最大0.35秒 |
| coreloop/CoreSkillCatalog.kt:81 | 239 | 獣1つごとに威力+{…}%・ダウン中の敵にはさらに+{…}% |
| coreloop/CoreSkillCatalog.kt:82 | 576 | 獣0：約4m・怯み / 1：約6m・低く打ち上げ / 2：約8m・打ち上げ→スタン / 3：全力の一振り（12m・2倍・高く打ち上げ→長いスタン） |
| coreloop/CoreSkillCatalog.kt:83 | 99 | 振っている間は怯まない |
| coreloop/CoreSkillCatalog.kt:84 | 246 | 通常3段目の命中から{…}秒以内：発生{…}秒・威力+{…}% |
| coreloop/CoreSkillCatalog.kt:86 | 168 | 後隙 {…}秒 / 次の入力は0.3秒先行受付 |
| coreloop/CoreSkillCatalog.kt:146 | 36 | 残響MOD |
| coreloop/CoreSkillCatalog.kt:150 | 36 | 余波MOD |
| coreloop/CoreSkillCatalog.kt:154 | 36 | 追矢MOD |
| coreloop/CoreSkillCatalog.kt:158 | 36 | 重力MOD |
| coreloop/CoreSkillCatalog.kt:166 | 9 | ・ |
| coreloop/CoreSkillCatalog.kt:166 | 51 |  / {…}適用 |
| coreloop/CoreSkillCatalog.kt:183 | 18 | 轟進 |
| coreloop/CoreSkillCatalog.kt:183 | 497 | 見ている方へ一気に走り抜ける（0.5秒）。すれ違う敵を1回ずつ斬り、走っている間は受けるダメージが半分。獣を溜める |
| coreloop/CoreSkillCatalog.kt:184 | 27 | 地割り |
| coreloop/CoreSkillCatalog.kt:184 | 792 | 溜めた獣を全部叩き込んで地を割る。獣が多いほど亀裂が長く、敵は高く打ち上がり長くスタン。獣3つなら全力の一振り：大地が前へ12m割れ、2倍の重さで叩く。通常3段目の直後なら速く重い |
| coreloop/CoreSkillCatalog.kt:187 | 18 | 狂乱 |
| coreloop/CoreSkillCatalog.kt:187 | 375 | 踏み込みながら続けて振り、最後は真上から2倍の力で振り下ろす。獣の数だけ振りが増える |
| coreloop/CoreSkillCatalog.kt:188 | 18 | 衝撃 |
| coreloop/CoreSkillCatalog.kt:188 | 982 | 大剣を目の前の地面へ打ち込む（周り2.5m）。そのあと8振りのあいだ、通常攻撃が当たった所へ少し遅れて2発目の衝撃が落ちる（振りの40%・周り1.5m、3段目は周り3mで怯ませる）。空振りでも1回減る。衝撃も1ヒットとして獣が溜まる |
| coreloop/CoreSkillCatalog.kt:190 | 36 | 跳び叩き |
| coreloop/CoreSkillCatalog.kt:190 | 405 | 狙った地点へ弧を描いて跳び、着地の周りを叩いて短くスタン。空中で少し曲げられる。獣を溜める |
| coreloop/CoreSkillCatalog.kt:192 | 36 | 大地崩し |
| coreloop/CoreSkillCatalog.kt:192 | 162 | 大剣を地面に叩きつけて周り一面を砕く |
| coreloop/CoreSkillCatalog.kt:194 | 36 | 受け止め |
| coreloop/CoreSkillCatalog.kt:194 | 807 | 3秒間、最大HPの20%のシールドを張る（動けるし殴れる）。止めた分だけ大剣が熱くなり、通常攻撃が最大+50%（6秒で冷める）。シールドの1/3を止めるごとに獣を1つ溜める（割り切れば3つ） |
| coreloop/CoreSkillCatalog.kt:196 | 36 | 斬り上げ |
| coreloop/CoreSkillCatalog.kt:196 | 675 | 大剣を引きずって真上へ振り上げ、正面の敵を高く打ち上げる。ジャンプを押していれば自分も追って跳ぶ。打ち上げた敵に地割りを当てると叩き落とし。獣を溜める |
| coreloop/CoreSkillCatalog.kt:199 | 54 | 全力の一振り |
| coreloop/CoreSkillCatalog.kt:199 | 240 | 地割りを獣3つで撃つと出る、大地を前へ大きく割る一振り |
| coreloop/CoreSkillCatalog.kt:200 | 54 | 狂戦士の甲冑 |
| coreloop/CoreSkillCatalog.kt:200 | 714 | 10秒間、体から血が流れ、鼓動のたびに血しぶきが散る。CCも怯みも効かず、動きと振りが速くなり、獣が2倍の速さで溜まる。代わりに体力が毎秒2%ずつ減る（1で止まる） |
| coreloop/CoreSkillCatalog.kt:201 | 45 | 獣の目覚め |
| coreloop/CoreSkillCatalog.kt:201 | 1259 | 獣3つで超重力に入る（再使用なし）。獣を毎秒0.5ずつ燃やし、0で切れる。殴れば獣は溜まるので、溜まる速さが上回れば続く。入った時に最大HP20%の障壁（8秒）、受けるダメージ-15%、CCが効かない。周り6mの敵は遅くなり、少しずつ引き寄せられる。通常攻撃は当てるたびに速くなる（最大+50%） |
| coreloop/CoreSkillCatalog.kt:207 | 27 | 火炎弾 |
| coreloop/CoreSkillCatalog.kt:207 | 330 | 大きな火の玉。当たった所で爆発し、敵を4秒燃やす。凍った敵に当てると砕ける |
| coreloop/CoreSkillCatalog.kt:208 | 36 | 霜の波紋 |
| coreloop/CoreSkillCatalog.kt:208 | 423 | 周りの敵に氷を大きく溜める。氷が溜まるほど敵は遅くなり、満タンで凍る（ボスは一度だけ短く凍る） |
| coreloop/CoreSkillCatalog.kt:209 | 18 | 流星 |
| coreloop/CoreSkillCatalog.kt:209 | 276 | 狙った地点へ空から大きな流星を落とす。巻き込んだ敵を4秒燃やす |
| coreloop/CoreSkillCatalog.kt:210 | 27 | 雷走り |
| coreloop/CoreSkillCatalog.kt:210 | 252 | 稲妻になって見ている先へ走る。通り道の敵に雷を少し溜める |
| coreloop/CoreSkillCatalog.kt:211 | 18 | 落雷 |
| coreloop/CoreSkillCatalog.kt:211 | 366 | 狙った敵に雷を落とし、近くの敵へ3回跳ねる。雷が溜まりきった敵は破裂して大ダメージ |
| coreloop/CoreSkillCatalog.kt:212 | 27 | 氷の庭 |
| coreloop/CoreSkillCatalog.kt:212 | 468 | 狙った床に6秒の氷庭。設置後は自由に動ける。敵を誘い減速中に他の術を重ねる（1体4打まで・ボスは減速耐性） |
| coreloop/CoreSkillCatalog.kt:213 | 36 | 氷槍の列 |
| coreloop/CoreSkillCatalog.kt:213 | 270 | 正面の地面から氷の槍が列で突き上がる。打ち上げて、氷を溜める |
| coreloop/CoreSkillCatalog.kt:214 | 18 | 突風 |
| coreloop/CoreSkillCatalog.kt:214 | 414 | 前へ風を吹いて敵を押し返す。燃えた敵・凍った敵の火や氷を巻き込んで、当たった敵みんなに広げる |
| coreloop/CoreSkillCatalog.kt:215 | 18 | 竜巻 |
| coreloop/CoreSkillCatalog.kt:215 | 450 | 狙った所に竜巻を置き、周りの敵を吸い寄せる。燃えた敵・凍った敵を巻き込むと、その火や氷を周りに広げる |
| coreloop/CoreSkillCatalog.kt:216 | 18 | 反響 |
| coreloop/CoreSkillCatalog.kt:216 | 435 | 10秒間、撃ったスキルが2秒後に同じ場所でもう一度唱えられる（陣の紋の数だけ、最大3回・6割の威力） |
| coreloop/CoreSkillCatalog.kt:217 | 45 | 元素の極み |
| coreloop/CoreSkillCatalog.kt:217 | 363 | 10秒間、陣に一番多い紋の元素を極める。体は変わらず、魔法の威力と性質だけが上がる |
| coreloop/CoreSkillCatalog.kt:219 | 36 | 狙い射ち |
| coreloop/CoreSkillCatalog.kt:219 | 153 | 同じ獲物を狙い続けるほど集中を得る |
| coreloop/CoreSkillCatalog.kt:220 | 36 | 霜矢の扇 |
| coreloop/CoreSkillCatalog.kt:220 | 189 | 前方の扇を減速。距離を作り、射線を確保する |
| coreloop/CoreSkillCatalog.kt:221 | 27 | 矢の嵐 |
| coreloop/CoreSkillCatalog.kt:221 | 138 | 集中40を消費。地点へ四度の矢雨 |
| coreloop/CoreSkillCatalog.kt:222 | 36 | 後退射撃 |
| coreloop/CoreSkillCatalog.kt:222 | 189 | 後ろへ退きながら矢を放つ。逃げと攻撃を両立 |
| coreloop/CoreSkillCatalog.kt:223 | 27 | 貫き矢 |
| coreloop/CoreSkillCatalog.kt:223 | 129 | 集中30を消費して三体まで貫通 |
| coreloop/CoreSkillCatalog.kt:224 | 27 | 狩猟罠 |
| coreloop/CoreSkillCatalog.kt:224 | 171 | 足元前方に減速と毒の罠。誘い込んで撃つ |
| coreloop/CoreSkillCatalog.kt:225 | 36 | 獲物の印 |
| coreloop/CoreSkillCatalog.kt:225 | 207 | 遠くの獲物に印。集中消費スキルが印を使って強化 |
| coreloop/CoreSkillCatalog.kt:226 | 18 | 速射 |
| coreloop/CoreSkillCatalog.kt:226 | 183 | 集中20を使う三連射。照準を保つ必要がある |
| coreloop/CoreSkillCatalog.kt:227 | 36 | 終の一矢 |
| coreloop/CoreSkillCatalog.kt:227 | 201 | 集中80を消費する精密射撃。長い構えを守り抜く |
| coreloop/CoreSkillCatalog.kt:228 | 54 | 狩場の支配者 |
| coreloop/CoreSkillCatalog.kt:228 | 201 | 集中70を消費。広い範囲を六度撃ち、減速させる |
| coreloop/CoreSkillCatalog.kt:230 | 27 | 影刺し |
| coreloop/CoreSkillCatalog.kt:230 | 135 | 敵へ接近し印を刻む。機会を得る |
| coreloop/CoreSkillCatalog.kt:231 | 18 | 断命 |
| coreloop/CoreSkillCatalog.kt:231 | 192 | 機会30を消費。印を消して威力を増す短い一撃 |
| coreloop/CoreSkillCatalog.kt:232 | 27 | 刃の輪 |
| coreloop/CoreSkillCatalog.kt:232 | 180 | 周囲に二度斬りつけ、複数の敵へ印を仕込む |
| coreloop/CoreSkillCatalog.kt:233 | 27 | 影抜け |
| coreloop/CoreSkillCatalog.kt:233 | 225 | 後方へ離脱し、周囲を減速。移動を攻撃に使い切らない |
| coreloop/CoreSkillCatalog.kt:234 | 18 | 毒牙 |
| coreloop/CoreSkillCatalog.kt:234 | 153 | 毒を仕込み、接近できない時間も削る |
| coreloop/CoreSkillCatalog.kt:235 | 18 | 追影 |
| coreloop/CoreSkillCatalog.kt:235 | 180 | 遠めの敵へ飛び込む。離脱と同じ機会を消費 |
| coreloop/CoreSkillCatalog.kt:236 | 27 | 影の針 |
| coreloop/CoreSkillCatalog.kt:236 | 225 | 遠くから印を付ける。正面しか狙えないボスにも使える |
| coreloop/CoreSkillCatalog.kt:237 | 18 | 残像 |
| coreloop/CoreSkillCatalog.kt:237 | 234 | 短時間だけ身を守る。早いタイミングで受けると機会獲得 |
| coreloop/CoreSkillCatalog.kt:238 | 36 | 無明連刃 |
| coreloop/CoreSkillCatalog.kt:238 | 192 | 機会80を消費し五連撃。印のある敵を先に狙う |
| coreloop/CoreSkillCatalog.kt:239 | 36 | 死の契約 |
| coreloop/CoreSkillCatalog.kt:239 | 210 | 機会70を消費する刺突。防御を崩し次の追撃も通す |
| coreloop/CoreSkillCatalog.kt:241 | 36 | 裁きの槌 |
| coreloop/CoreSkillCatalog.kt:241 | 135 | 重い一撃で印を付け、決意を得る |
| coreloop/CoreSkillCatalog.kt:242 | 18 | 集束 |
| coreloop/CoreSkillCatalog.kt:242 | 216 | 近くの敵を引き寄せる。ボスは移動せず短く行動阻害 |
| coreloop/CoreSkillCatalog.kt:243 | 45 | 護りの誓い |
| coreloop/CoreSkillCatalog.kt:243 | 171 | 周囲の味方へ障壁。自分も防御姿勢を取る |
| coreloop/CoreSkillCatalog.kt:244 | 45 | 守護の構え |
| coreloop/CoreSkillCatalog.kt:244 | 158 | 2.5秒防御。受けた攻撃から決意を得る |
| coreloop/CoreSkillCatalog.kt:245 | 18 | 報復 |
| coreloop/CoreSkillCatalog.kt:245 | 192 | 決意40を使う反撃。集めた敵にまとめて当てる |
| coreloop/CoreSkillCatalog.kt:246 | 27 | 封鎖線 |
| coreloop/CoreSkillCatalog.kt:246 | 153 | 敵を遅らせる陣。味方の射線を支える |
| coreloop/CoreSkillCatalog.kt:247 | 27 | 破魔槌 |
| coreloop/CoreSkillCatalog.kt:247 | 132 | 敵のARとMRを短時間低下させる |
| coreloop/CoreSkillCatalog.kt:248 | 36 | 援護突進 |
| coreloop/CoreSkillCatalog.kt:248 | 189 | 敵へ踏み込み、周囲の敵の狙いを自分に向ける |
| coreloop/CoreSkillCatalog.kt:249 | 36 | 空間圧縮 |
| coreloop/CoreSkillCatalog.kt:249 | 192 | 決意80を消費。広範囲を三度吸引してまとめる |
| coreloop/CoreSkillCatalog.kt:250 | 45 | 不落の聖域 |
| coreloop/CoreSkillCatalog.kt:250 | 210 | 決意70を消費。仲間へ大きな障壁と自分へ防御姿勢 |
| coreloop/CoreSkillCatalog.kt:252 | 36 | 裁きの光 |
| coreloop/CoreSkillCatalog.kt:252 | 171 | 敵を撃ち信仰を得る。ソロでも主力になる |
| coreloop/CoreSkillCatalog.kt:253 | 36 | 救いの輪 |
| coreloop/CoreSkillCatalog.kt:253 | 201 | 信仰30を回復に回す。周囲の傷ついた仲間を救う |
| coreloop/CoreSkillCatalog.kt:254 | 36 | 断罪の柱 |
| coreloop/CoreSkillCatalog.kt:254 | 201 | 信仰40を攻撃に回す。回復と同じ資源を取り合う |
| coreloop/CoreSkillCatalog.kt:255 | 36 | 光の歩み |
| coreloop/CoreSkillCatalog.kt:255 | 153 | 前へ移動し、周囲に小さな障壁を渡す |
| coreloop/CoreSkillCatalog.kt:256 | 18 | 灯火 |
| coreloop/CoreSkillCatalog.kt:256 | 153 | 地点に三度の光。敵を削り信仰を得る |
| coreloop/CoreSkillCatalog.kt:257 | 36 | 祈りの盾 |
| coreloop/CoreSkillCatalog.kt:257 | 228 | 信仰25を予防に回す。回復できない大きな攻撃に備える |
| coreloop/CoreSkillCatalog.kt:258 | 36 | 導きの印 |
| coreloop/CoreSkillCatalog.kt:258 | 189 | 敵へ印を刻み、信仰を得る。裁きの起点になる |
| coreloop/CoreSkillCatalog.kt:259 | 36 | 清めの風 |
| coreloop/CoreSkillCatalog.kt:259 | 201 | 信仰20で軽く回復し、自分の回避を再使用可能に |
| coreloop/CoreSkillCatalog.kt:260 | 18 | 救済 |
| coreloop/CoreSkillCatalog.kt:260 | 165 | 信仰80を使い、周囲の仲間を大きく回復 |
| coreloop/CoreSkillCatalog.kt:261 | 45 | 最後の審判 |
| coreloop/CoreSkillCatalog.kt:261 | 174 | 信仰80を攻撃へ全投入。四度の広域の裁き |
| coreloop/CoreSkillTooltip.kt:12 | 117 | 成長するとこの枠を使えます |
| coreloop/CoreSkillTooltip.kt:12 | 152 | 港の成長と職業 → スキル選びで変更 |
| coreloop/CoreSkillTooltip.kt:16 | 45 | 【未解放】 |
| coreloop/CoreSkillTooltip.kt:27 | 27 | ウルト |
| coreloop/CoreSkillTooltip.kt:27 | 45 | 選択スキル |
| coreloop/CoreWarriorMarkDisplay.kt:42 | 46 | projects |
| coreloop/CoreWarriorMarkDisplay.kt:42 | 67 | warrior_mark |
| coreloop/CoreWarriorMarkDisplay.kt:43 | 32 |  {…}秒 |
| coreloop/CoreWarriorRefChoreography.kt:444 | 37 | fissure |
| coreloop/CoreWarriorRefChoreography.kt:448 | 22 | field |
| coreloop/CoreWarriorRefChoreography.kt:448 | 47 | {…}/field |
| coreloop/CoreWarriorRefChoreography.kt:449 | 20 | wind |
| coreloop/CoreWarriorRefChoreography.kt:449 | 70 | {…}/wind_{…} |
| coreloop/CoreWarriorRefChoreography.kt:456 | 30 | sweep |
| coreloop/CoreWarriorRefChoreography.kt:456 | 80 | {…}/sweep_{…} |
| coreloop/CoreWarriorRefChoreography.kt:457 | 36 | scrape |
| coreloop/CoreWarriorRefChoreography.kt:459 | 35 | furrow |
| coreloop/CoreWarriorRefChoreography.kt:461 | 34 | crater |
| coreloop/CoreWarriorRefChoreography.kt:463 | 35 | dcrack |
| coreloop/CoreWarriorRefChoreography.kt:487 | 29 | smoke |
| coreloop/CoreWarriorRefChoreography.kt:491 | 30 | bdrop |
| coreloop/CoreWarriorRefChoreography.kt:493 | 28 | bfling |
| coreloop/CoreWarriorRefChoreography.kt:497 | 31 | bsplat |
| coreloop/CoreWarriorRefChoreography.kt:497 | 81 | {…}/bsplat_{…} |
| coreloop/CoreWarriorRefChoreography.kt:498 | 24 | bmist |
| coreloop/CoreWarriorRefChoreography.kt:500 | 35 | bsmoke |
| coreloop/CoreWarriorRefChoreography.kt:510 | 36 | bmistup |
| coreloop/CoreWarriorRefChoreography.kt:512 | 24 | beye |
| coreloop/CoreWarriorRefChoreography.kt:512 | 67 | {…}/ember_0 |
| coreloop/CoreWarriorRefChoreography.kt:513 | 27 | rhalo |
| coreloop/CoreWarriorRefChoreography.kt:555 | 35 | gcrack |
| coreloop/CoreWarriorRefChoreography.kt:621 | 29 | crack |
| coreloop/CoreWarriorRefChoreography.kt:641 | 29 | frame |
| coreloop/CoreWarriorRefChoreography.kt:641 | 54 | {…}/frame |
| coreloop/CoreWarriorRefChoreography.kt:642 | 20 | ring |
| coreloop/CoreWarriorRefChoreography.kt:642 | 70 | {…}/ring_{…} |
| coreloop/CoreWarriorSkillPresentation.kt:7 | 18 | 轟進 |
| coreloop/CoreWarriorSkillPresentation.kt:7 | 72 | 獣を溜める・突進 |
| coreloop/CoreWarriorSkillPresentation.kt:7 | 153 | 一気に走り抜けて、すれ違う敵を斬る |
| coreloop/CoreWarriorSkillPresentation.kt:8 | 27 | 地割り |
| coreloop/CoreWarriorSkillPresentation.kt:8 | 63 | 獣を使う・重撃 |
| coreloop/CoreWarriorSkillPresentation.kt:8 | 195 | 獣を全部使って地を割る。獣3つで全力の一振り |
| coreloop/CoreWarriorSkillPresentation.kt:9 | 18 | 狂乱 |
| coreloop/CoreWarriorSkillPresentation.kt:9 | 63 | 獣を使う・連撃 |
| coreloop/CoreWarriorSkillPresentation.kt:9 | 180 | 踏み込みながら振り続け、最後に振り下ろす |
| coreloop/CoreWarriorSkillPresentation.kt:10 | 18 | 衝撃 |
| coreloop/CoreWarriorSkillPresentation.kt:10 | 72 | 獣を溜める・連打 |
| coreloop/CoreWarriorSkillPresentation.kt:10 | 201 | 地面に打ち込み、次の8振りに2発目の衝撃が乗る |
| coreloop/CoreWarriorSkillPresentation.kt:11 | 36 | 跳び叩き |
| coreloop/CoreWarriorSkillPresentation.kt:11 | 45 | 移動・着地 |
| coreloop/CoreWarriorSkillPresentation.kt:11 | 189 | 狙った地点へ跳び、着地の周りを叩いて止める |
| coreloop/CoreWarriorSkillPresentation.kt:12 | 36 | 大地崩し |
| coreloop/CoreWarriorSkillPresentation.kt:12 | 63 | 使えないスキル |
| coreloop/CoreWarriorSkillPresentation.kt:12 | 36 | （引退） |
| coreloop/CoreWarriorSkillPresentation.kt:13 | 36 | 受け止め |
| coreloop/CoreWarriorSkillPresentation.kt:13 | 72 | 獣を溜める・守り |
| coreloop/CoreWarriorSkillPresentation.kt:13 | 207 | シールドで受けるほど大剣が熱く、殴りが強くなる |
| coreloop/CoreWarriorSkillPresentation.kt:14 | 36 | 斬り上げ |
| coreloop/CoreWarriorSkillPresentation.kt:14 | 90 | 獣を溜める・打ち上げ |
| coreloop/CoreWarriorSkillPresentation.kt:14 | 198 | 真上へ振り上げて打ち上げ、地割りで叩き落とす |
| coreloop/CoreWarriorSkillPresentation.kt:15 | 36 | 全力一振 |
| coreloop/CoreWarriorSkillPresentation.kt:15 | 63 | 使えないスキル |
| coreloop/CoreWarriorSkillPresentation.kt:15 | 114 | （地割りの獣3つになった） |
| coreloop/CoreWarriorSkillPresentation.kt:16 | 45 | 狂戦士甲冑 |
| coreloop/CoreWarriorSkillPresentation.kt:16 | 54 | ウルト・暴走 |
| coreloop/CoreWarriorSkillPresentation.kt:16 | 201 | 10秒、CCも怯みも効かず速く、獣が2倍で溜まる |
| coreloop/CoreWarriorSkillPresentation.kt:17 | 45 | 獣の目覚め |
| coreloop/CoreWarriorSkillPresentation.kt:17 | 63 | ウルト・超重力 |
| coreloop/CoreWarriorSkillPresentation.kt:17 | 198 | 獣を燃やして続く。殴って溜め続ければ切れない |
| coreloop/CoreWarriorSkillPresentation.kt:26 | 59 | 根張り {…}秒 |
| coreloop/CoreWarriorSkillPresentation.kt:39 | 45 | 自分の強化 |
| coreloop/CoreWarriorSkillPresentation.kt:43 | 18 | 防御 |
| coreloop/CoreWarriorSkillPresentation.kt:43 | 18 | 障壁 |
| coreloop/CoreWarriorSkillPresentation.kt:43 | 54 | 物理ダメージ |
| coreloop/CoreWarriorSkillPresentation.kt:48 | 18 | 適用 |
| coreloop/CoreWorldLoot.kt:60 | 139 | 刻印粉 +{…}  / 戦利品券 +{…} |
| coreloop/CoreWorldLoot.kt:66 | 72 | 右クリックで回収 |
| coreloop/CoreWorldLoot.kt:100 | 270 | インベントリがいっぱいです。空きを作ってから回収してください |
| coreloop/CoreWorldLoot.kt:130 | 189 | 戦利品回収：刻印粉 +{…} / 戦利品券 +{…} |
| coreloop/CoreWorldLoot.kt:131 | 163 | 回収：{…}。刻印工房で使用できます。 |
| coreloop/DungeonGateDialog.kt:43 | 27 | close |
| coreloop/DungeonGateDialog.kt:54 | 63 | ダンジョンの門 |
| coreloop/DungeonGateDialog.kt:57 | 27 | close |
| coreloop/DungeonGateDialog.kt:58 | 27 | close |
| coreloop/DungeonGateDialog.kt:72 | 48 | gateLines |
| coreloop/DungeonGateDialog.kt:76 | 71 | T{…}・段階{…} |
| coreloop/DungeonGateDialog.kt:80 | 54 | hit_reward |
| coreloop/DungeonGateDialog.kt:83 | 46 | hit_enter |
| coreloop/DungeonGateDialog.kt:83 | 28 | enter |
| coreloop/DungeonGateDialog.kt:83 | 72 | 散り桜の社へ入る |
| coreloop/FirstMagicColony.kt:315 | 68 | 帰還の帳 / 港へ |
| coreloop/FirstMagicColony.kt:347 | 131 | 封印区画 / 共鳴が不足している |
| coreloop/FirstMagicColonyItems.kt:29 | 162 | 手に持ってブロックへ右クリック：設置 |
| coreloop/FirstMagicColonyItems.kt:30 | 135 | しゃがみながら右クリック：回収 |
| coreloop/FirstMagicColonyItems.kt:31 | 45 | 壁面に設置 |
| coreloop/FirstMagicColonyItems.kt:31 | 126 | コロニー内の好きな場所に設置 |
| coreloop/FirstMagicColonyItems.kt:46 | 217 | インベントリに空きがないため{…}を受け取れません |
| coreloop/FirstMagicColonyLayout.kt:15 | 27 | 研究机 |
| coreloop/FirstMagicColonyLayout.kt:16 | 54 | 粗末な蒸留器 |
| coreloop/FirstMagicColonyLayout.kt:17 | 27 | Jar棚 |
| coreloop/FirstMagicColonyLayout.kt:22 | 36 | 古い星図 |
| coreloop/FirstMagicField.kt:71 | 72 | 右クリックで採取 |
| coreloop/FirstMagicField.kt:94 | 270 | インベントリがいっぱいです。空きを作ってから採取してください |
| coreloop/FirstMagicField.kt:102 | 208 | {…}を採取した。研究に使うか、港の市場で売れる |
| coreloop/FirstMagicField.kt:108 | 117 | 採取を保存できませんでした |
| coreloop/FirstMagicState.kt:4 | 62 | Ember / 熾火 |
| coreloop/FirstMagicState.kt:4 | 43 | Tide / 潮 |
| coreloop/FirstMagicState.kt:4 | 44 | Gale / 風 |
| coreloop/FirstMagicState.kt:4 | 51 | Stone / 石 |
| coreloop/FirstMagicState.kt:11 | 27 | 月鈴草 |
| coreloop/FirstMagicState.kt:12 | 27 | 熾火苔 |
| coreloop/FirstMagicState.kt:13 | 36 | 空洞水晶 |
| coreloop/FirstMagicState.kt:14 | 36 | 微熱鉱片 |
| coreloop/FirstMagicState.kt:15 | 18 | 潮羽 |
| coreloop/FirstMagicState.kt:16 | 36 | 枯れた核 |
| coreloop/FirstMagicState.kt:78 | 180 | 研究テスト用の素材と研究インクを補充した |
| coreloop/FirstMagicState.kt:82 | 90 | 素材袋がいっぱいです |
| coreloop/FirstMagicState.kt:84 | 217 | {…}を拾った。コロニーの古い研究区画に持ち帰ろう |
| coreloop/FirstMagicState.kt:87 | 135 | 研究区画の紙が静かに光っている |
| coreloop/FirstMagicState.kt:88 | 216 | 古い研究区画は眠ったまま。遠征で異質素材を探そう |
| coreloop/FirstMagicState.kt:89 | 261 | 持ち帰った素材に古い装置が反応した。研究机を修復できそうだ |
| coreloop/FirstMagicState.kt:92 | 90 | 研究机は起動している |
| coreloop/FirstMagicState.kt:93 | 144 | この机を起こす手がかりがまだない |
| coreloop/FirstMagicState.kt:94 | 171 | 研究机が起動した。素材を観測盤に置こう |
| coreloop/FirstMagicState.kt:97 | 99 | 先に研究机を修復しよう |
| coreloop/FirstMagicState.kt:98 | 82 | {…}を持っていない |
| coreloop/FirstMagicState.kt:99 | 64 | {…}は記録済み |
| coreloop/FirstMagicState.kt:107 | 78 | {…}：{…} を記録 |
| coreloop/FirstMagicState.kt:111 | 153 | 研究机を起動してから蒸留器を使おう |
| coreloop/FirstMagicState.kt:112 | 91 | 先に{…}を分析しよう |
| coreloop/FirstMagicState.kt:113 | 64 | {…}が足りない |
| coreloop/FirstMagicState.kt:115 | 171 | 対応するJarがいっぱい。蒸留を中止した |
| coreloop/FirstMagicState.kt:121 | 178 | {…}からEssentiaを抽出。Jarに保存した |
| coreloop/FirstMagicState.kt:126 | 99 | 研究机を先に修復しよう |
| coreloop/FirstMagicState.kt:127 | 205 | この二つから生まれるAspectはまだ見つからない |
| coreloop/FirstMagicState.kt:129 | 133 | 両方のAspectを先に発見しよう |
| coreloop/FirstMagicState.kt:131 | 144 | 組み合わせる研究インクが足りない |
| coreloop/FirstMagicState.kt:139 | 137 | {…} を発見。研究インクを4得た |
| coreloop/FirstMagicState.kt:146 | 99 | 研究机を先に修復しよう |
| coreloop/FirstMagicState.kt:148 | 72 | 置けない場所です |
| coreloop/FirstMagicState.kt:149 | 81 | 研究は完了している |
| coreloop/FirstMagicState.kt:151 | 151 | そのAspectの研究インクが足りない |
| coreloop/FirstMagicState.kt:152 | 115 | 先に置いたAspectを外そう |
| coreloop/FirstMagicState.kt:160 | 100 | 研究「{…}」を解明した |
| coreloop/FirstMagicState.kt:160 | 59 | {…} を記した |
| coreloop/FirstMagicState.kt:168 | 99 | 先に研究机を修復しよう |
| coreloop/FirstMagicState.kt:169 | 91 | 先に{…}を分析しよう |
| coreloop/FirstMagicState.kt:170 | 145 | {…}を持っていない。市場で買える |
| coreloop/FirstMagicState.kt:171 | 126 | 研究インクの瓶がいっぱいです |
| coreloop/FirstMagicState.kt:176 | 9 | ・ |
| coreloop/FirstMagicState.kt:176 | 201 | {…}を溶かし、研究インクを{…}に{…}ずつ得た |
| coreloop/FirstMagicState.kt:182 | 135 | この研究から作れる品はまだない |
| coreloop/FirstMagicState.kt:184 | 127 | 研究「{…}」を先に解明しよう |
| coreloop/FirstMagicState.kt:186 | 124 | Essentiaが足りない（{…}） |
| coreloop/FirstMagicState.kt:189 | 64 | {…}を調合した |
| coreloop/FirstMagicState.kt:198 | 88 | 外せるAspectがない |
| coreloop/FirstMagicState.kt:202 | 151 | Aspectを外し、研究インクを戻した |
| coreloop/FirstMagicWorkshop.kt:100 | 36 | 港へ帰還 |
| coreloop/FirstMagicWorkshop.kt:115 | 36 | 観測手順 |
| coreloop/FirstMagicWorkshop.kt:116 | 79 | 01 素材を持ち帰る |
| coreloop/FirstMagicWorkshop.kt:117 | 70 | 02 観測盤を修復 |
| coreloop/FirstMagicWorkshop.kt:118 | 61 | 03 素材を分析 |
| coreloop/FirstMagicWorkshop.kt:119 | 70 | 04 記録帳へ残す |
| coreloop/FirstMagicWorkshop.kt:121 | 61 | 素材 {…} / 6 |
| coreloop/FirstMagicWorkshop.kt:122 | 61 | 性質 {…} / 4 |
| coreloop/FirstMagicWorkshop.kt:124 | 54 | 古い観測工房 |
| coreloop/FirstMagicWorkshop.kt:125 | 63 | 観測盤は起動中 |
| coreloop/FirstMagicWorkshop.kt:125 | 63 | 観測盤は休眠中 |
| coreloop/FirstMagicWorkshop.kt:127 | 81 | 紙上の小さな星図は |
| coreloop/FirstMagicWorkshop.kt:128 | 72 | 持ち帰った未知に |
| coreloop/FirstMagicWorkshop.kt:129 | 27 | 応える |
| coreloop/FirstMagicWorkshop.kt:131 | 81 | 未分析：分析は無料 |
| coreloop/FirstMagicWorkshop.kt:132 | 81 | 分析済：インク調合 |
| coreloop/FirstMagicWorkshop.kt:133 | 70 | 素材1・銀貨{…} |
| coreloop/FirstMagicWorkshop.kt:137 | 36 | 抽出工程 |
| coreloop/FirstMagicWorkshop.kt:138 | 81 | 研究済み素材を選ぶ |
| coreloop/FirstMagicWorkshop.kt:139 | 69 | 炉が約3秒で加熱 |
| coreloop/FirstMagicWorkshop.kt:140 | 72 | 冷却管からJarへ |
| coreloop/FirstMagicWorkshop.kt:142 | 92 | 素材1・燃料 銀貨{…} |
| coreloop/FirstMagicWorkshop.kt:143 | 63 | 対応するJarを |
| coreloop/FirstMagicWorkshop.kt:144 | 36 | 先に置く |
| coreloop/FirstMagicWorkshop.kt:145 | 84 | Jar上限 16 / 性質 |
| coreloop/FirstMagicWorkshop.kt:147 | 36 | 炉の状態 |
| coreloop/FirstMagicWorkshop.kt:148 | 50 | 加熱中 {…} |
| coreloop/FirstMagicWorkshop.kt:148 | 27 | 待機中 |
| coreloop/FirstMagicWorkshop.kt:150 | 63 | 熾火は熱として |
| coreloop/FirstMagicWorkshop.kt:151 | 63 | 潮は循環として |
| coreloop/FirstMagicWorkshop.kt:152 | 63 | 風は流れとして |
| coreloop/FirstMagicWorkshop.kt:153 | 81 | 石は沈殿として残る |
| coreloop/FirstMagicWorkshop.kt:157 | 54 | 四つの保存瓶 |
| coreloop/FirstMagicWorkshop.kt:158 | 63 | 瓶ごとに一性質 |
| coreloop/FirstMagicWorkshop.kt:159 | 72 | 中身と残量を見る |
| coreloop/FirstMagicWorkshop.kt:160 | 54 | Jarを持って |
| coreloop/FirstMagicWorkshop.kt:161 | 63 | 棚を右クリック |
| coreloop/FirstMagicWorkshop.kt:162 | 81 | 棚の瓶をクリックで |
| coreloop/FirstMagicWorkshop.kt:163 | 36 | 取り出す |
| coreloop/FirstMagicWorkshop.kt:165 | 9 | 済 |
| coreloop/FirstMagicWorkshop.kt:165 | 9 | 未 |
| coreloop/FirstMagicWorkshop.kt:165 | 59 | 初回抽出 {…} |
| coreloop/FirstMagicWorkshop.kt:167 | 36 | 保管記録 |
| coreloop/FirstMagicWorkshop.kt:169 | 81 | 瓶の色は性質に対応 |
| coreloop/FirstMagicWorkshop.kt:172 | 27 | 第一頁 |
| coreloop/FirstMagicWorkshop.kt:173 | 79 | 観測済み {…} / 6 |
| coreloop/FirstMagicWorkshop.kt:174 | 97 | 判明した性質 {…} / 4 |
| coreloop/FirstMagicWorkshop.kt:176 | 81 | 未知から性質を知る |
| coreloop/FirstMagicWorkshop.kt:177 | 72 | 抽出して瓶へ残す |
| coreloop/FirstMagicWorkshop.kt:179 | 54 | その先の余白 |
| coreloop/FirstMagicWorkshop.kt:180 | 81 | 封印区画の円形台座 |
| coreloop/FirstMagicWorkshop.kt:181 | 81 | まだ共鳴が足りない |
| coreloop/FirstMagicWorkshop.kt:183 | 63 | 四性質は入口に |
| coreloop/FirstMagicWorkshop.kt:184 | 36 | すぎない |
| coreloop/FirstMagicWorkshop.kt:188 | 45 | 研究の手順 |
| coreloop/FirstMagicWorkshop.kt:189 | 54 | 素材を分析し |
| coreloop/FirstMagicWorkshop.kt:190 | 45 | 性質を発見 |
| coreloop/FirstMagicWorkshop.kt:191 | 72 | 二性質を合成して |
| coreloop/FirstMagicWorkshop.kt:192 | 27 | 新発見 |
| coreloop/FirstMagicWorkshop.kt:193 | 81 | 六角盤の空欄に記す |
| coreloop/FirstMagicWorkshop.kt:194 | 63 | となりの性質を |
| coreloop/FirstMagicWorkshop.kt:195 | 27 | つなぐ |
| coreloop/FirstMagicWorkshop.kt:196 | 54 | 固定点が全て |
| coreloop/FirstMagicWorkshop.kt:197 | 63 | つながれば解明 |
| coreloop/FirstMagicWorkshop.kt:199 | 74 | 解明 {…} / {…} |
| coreloop/FirstMagicWorkshop.kt:200 | 81 | 解明で調合品が開く |
| coreloop/FirstMagicWorkshop.kt:202 | 45 | 研究インク |
| coreloop/FirstMagicWorkshop.kt:203 | 63 | 所持した性質を |
| coreloop/FirstMagicWorkshop.kt:204 | 36 | 一つ消費 |
| coreloop/FirstMagicWorkshop.kt:205 | 81 | 外せばインクは戻る |
| coreloop/FirstMagicWorkshop.kt:206 | 72 | 合成で複合性質を |
| coreloop/FirstMagicWorkshop.kt:207 | 24 | 4得る |
| coreloop/FirstMagicWorkshop.kt:209 | 74 | 発見 {…} / {…} |
| coreloop/FirstMagicWorkshop.kt:213 | 45 | 性質の合成 |
| coreloop/FirstMagicWorkshop.kt:214 | 45 | 発見済みの |
| coreloop/FirstMagicWorkshop.kt:215 | 54 | 二性質を使う |
| coreloop/FirstMagicWorkshop.kt:216 | 81 | 各インクを一つ消費 |
| coreloop/FirstMagicWorkshop.kt:217 | 54 | 新しい性質の |
| coreloop/FirstMagicWorkshop.kt:218 | 60 | インクを4得る |
| coreloop/FirstMagicWorkshop.kt:220 | 45 | 素材分析で |
| coreloop/FirstMagicWorkshop.kt:221 | 63 | 基礎性質を補充 |
| coreloop/FirstMagicWorkshop.kt:223 | 45 | 構成の結び |
| coreloop/FirstMagicWorkshop.kt:224 | 45 | 複合性質と |
| coreloop/FirstMagicWorkshop.kt:225 | 45 | その材料は |
| coreloop/FirstMagicWorkshop.kt:226 | 81 | 研究盤で接続できる |
| coreloop/FirstMagicWorkshop.kt:228 | 58 | 火 + 風 → 光 |
| coreloop/FirstMagicWorkshop.kt:232 | 54 | 研究の調合台 |
| coreloop/FirstMagicWorkshop.kt:233 | 81 | 解明した研究ごとに |
| coreloop/FirstMagicWorkshop.kt:234 | 18 | 一品 |
| coreloop/FirstMagicWorkshop.kt:236 | 36 | 港の素材 |
| coreloop/FirstMagicWorkshop.kt:237 | 68 | 調合料 銀貨{…} |
| coreloop/FirstMagicWorkshop.kt:239 | 63 | 完成品は市場で |
| coreloop/FirstMagicWorkshop.kt:240 | 27 | 売れる |
| coreloop/FirstMagicWorkshop.kt:241 | 63 | 解明そのものは |
| coreloop/FirstMagicWorkshop.kt:242 | 36 | 売れない |
| coreloop/FirstMagicWorkshop.kt:244 | 45 | 誰が買うか |
| coreloop/FirstMagicWorkshop.kt:245 | 45 | 星鉄の触媒 |
| coreloop/FirstMagicWorkshop.kt:246 | 80 |   鍛冶の強化成功率 |
| coreloop/FirstMagicWorkshop.kt:247 | 45 | 修復の封蝋 |
| coreloop/FirstMagicWorkshop.kt:248 | 71 |   破損装備の修理 |
| coreloop/FirstMagicWorkshop.kt:250 | 63 | 所持 銀貨 {…} |
| coreloop/FirstMagicWorkshop.kt:262 | 144 | 研究机を庭に配置してから調べよう |
| coreloop/FirstMagicWorkshop.kt:268 | 36 | 観測記録 |
| coreloop/FirstMagicWorkshop.kt:268 | 72 | 机は起動している |
| coreloop/FirstMagicWorkshop.kt:268 | 99 | 古い観測盤は眠っている |
| coreloop/FirstMagicWorkshop.kt:270 | 68 | 観測盤 / 稼働中 |
| coreloop/FirstMagicWorkshop.kt:270 | 54 | 研究机を修復 |
| coreloop/FirstMagicWorkshop.kt:270 | 72 | クリックして修復 |
| coreloop/FirstMagicWorkshop.kt:270 | 126 | 異質素材を持ち帰ると反応する |
| coreloop/FirstMagicWorkshop.kt:274 | 63 | 性質はまだ不明 |
| coreloop/FirstMagicWorkshop.kt:277 | 154 | クリックで分析（無料）/ 素材は残る |
| coreloop/FirstMagicWorkshop.kt:278 | 182 | クリック：研究インク調合 素材1・銀貨{…} |
| coreloop/FirstMagicWorkshop.kt:278 | 122 | 遠征で拾う / 港の市場で買う |
| coreloop/FirstMagicWorkshop.kt:279 | 50 | インク {…} |
| coreloop/FirstMagicWorkshop.kt:286 | 54 | 未解明の性質 |
| coreloop/FirstMagicWorkshop.kt:287 | 81 | 分析により判明した |
| coreloop/FirstMagicWorkshop.kt:287 | 99 | 未知の素材に眠っている |
| coreloop/FirstMagicWorkshop.kt:289 | 45 | 魔導記録帳 |
| coreloop/FirstMagicWorkshop.kt:289 | 18 | なし |
| coreloop/FirstMagicWorkshop.kt:289 | 82 | 判明した性質：{…} |
| coreloop/FirstMagicWorkshop.kt:289 | 90 | クリックで記録を読む |
| coreloop/FirstMagicWorkshop.kt:291 | 45 | 研究の星図 |
| coreloop/FirstMagicWorkshop.kt:291 | 99 | 六角盤で研究を解明する |
| coreloop/FirstMagicWorkshop.kt:293 | 54 | 研究の調合台 |
| coreloop/FirstMagicWorkshop.kt:293 | 135 | 解明した研究から売れる品を作る |
| coreloop/FirstMagicWorkshop.kt:293 | 41 | 銀貨 {…} |
| coreloop/FirstMagicWorkshop.kt:295 | 36 | 蒸留器へ |
| coreloop/FirstMagicWorkshop.kt:295 | 141 | 分析済み素材からEssentiaを抽出 |
| coreloop/FirstMagicWorkshop.kt:297 | 72 | 観測工房・研究机 |
| coreloop/FirstMagicWorkshop.kt:302 | 135 | 蒸留器を庭に配置してから使おう |
| coreloop/FirstMagicWorkshop.kt:308 | 54 | 粗末な蒸留器 |
| coreloop/FirstMagicWorkshop.kt:308 | 96 | 研究済み素材を1個消費 |
| coreloop/FirstMagicWorkshop.kt:308 | 115 | 沸騰 → 冷却 → Jarへ保存 |
| coreloop/FirstMagicWorkshop.kt:309 | 38 | distiller |
| coreloop/FirstMagicWorkshop.kt:309 | 55 | 蒸留中：{…} |
| coreloop/FirstMagicWorkshop.kt:309 | 27 | 蒸留槽 |
| coreloop/FirstMagicWorkshop.kt:309 | 66 | 1回につき約3秒 |
| coreloop/FirstMagicWorkshop.kt:312 | 72 | 研究机で先に分析 |
| coreloop/FirstMagicWorkshop.kt:314 | 169 | クリックで蒸留 / 素材1・燃料 銀貨{…} |
| coreloop/FirstMagicWorkshop.kt:314 | 117 | 足りなければ港の市場で買う |
| coreloop/FirstMagicWorkshop.kt:319 | 83 | Jarに {…} / {…} |
| coreloop/FirstMagicWorkshop.kt:319 | 117 | Jarを庭に置くと受け取れる |
| coreloop/FirstMagicWorkshop.kt:321 | 54 | Jar棚を見る |
| coreloop/FirstMagicWorkshop.kt:321 | 114 | 保存されたEssentiaを確認 |
| coreloop/FirstMagicWorkshop.kt:323 | 36 | 研究机へ |
| coreloop/FirstMagicWorkshop.kt:325 | 72 | 観測工房・蒸留器 |
| coreloop/FirstMagicWorkshop.kt:330 | 45 | 蒸留中です |
| coreloop/FirstMagicWorkshop.kt:337 | 154 | {…}に対応するJarを先に配置しよう |
| coreloop/FirstMagicWorkshop.kt:341 | 118 | 燃料の銀貨{…}枚が必要です |
| coreloop/FirstMagicWorkshop.kt:345 | 199 | {…}を加熱中…色のついた蒸気がガラス管を昇る |
| coreloop/FirstMagicWorkshop.kt:354 | 63 | 蒸留中  {…}… |
| coreloop/FirstMagicWorkshop.kt:359 | 225 | Jarが外されたため蒸留を中止した。素材は残っている |
| coreloop/FirstMagicWorkshop.kt:369 | 73 | Essentia Jar棚 |
| coreloop/FirstMagicWorkshop.kt:369 | 117 | 1瓶につき1性質 / 容量{…} |
| coreloop/FirstMagicWorkshop.kt:376 | 167 | 棚に収納中 / 瓶をクリックして取り出す |
| coreloop/FirstMagicWorkshop.kt:377 | 45 | 庭に設置中 |
| coreloop/FirstMagicWorkshop.kt:377 | 117 | Jarを持って棚を右クリック |
| coreloop/FirstMagicWorkshop.kt:380 | 45 | 魔導記録帳 |
| coreloop/FirstMagicWorkshop.kt:380 | 90 | 初回の観測結果を確認 |
| coreloop/FirstMagicWorkshop.kt:382 | 36 | 蒸留器へ |
| coreloop/FirstMagicWorkshop.kt:384 | 72 | 観測工房・Jar棚 |
| coreloop/FirstMagicWorkshop.kt:391 | 76 | 魔導記録帳 第一頁 |
| coreloop/FirstMagicWorkshop.kt:391 | 108 | 研究Aspect {…} / {…} |
| coreloop/FirstMagicWorkshop.kt:394 | 45 | 未分析の頁 |
| coreloop/FirstMagicWorkshop.kt:395 | 117 | 未知を持ち帰り研究机に置く |
| coreloop/FirstMagicWorkshop.kt:397 | 72 | 最初の蒸留を記録 |
| coreloop/FirstMagicWorkshop.kt:397 | 63 | まだ白紙の余白 |
| coreloop/FirstMagicWorkshop.kt:398 | 153 | 封印区画にはさらに大きな装置が眠る |
| coreloop/FirstMagicWorkshop.kt:398 | 108 | Jarが満たされる日を待つ |
| coreloop/FirstMagicWorkshop.kt:399 | 45 | 研究の星図 |
| coreloop/FirstMagicWorkshop.kt:399 | 74 | 研究 {…} / {…} |
| coreloop/FirstMagicWorkshop.kt:399 | 99 | クリックで六角盤を開く |
| coreloop/FirstMagicWorkshop.kt:401 | 36 | 研究机へ |
| coreloop/FirstMagicWorkshop.kt:403 | 81 | 魔導記録帳・第一頁 |
| coreloop/FirstMagicWorkshop.kt:421 | 70 | 未発見のAspect |
| coreloop/FirstMagicWorkshop.kt:422 | 18 | 基礎 |
| coreloop/FirstMagicWorkshop.kt:422 | 43 | 構成: {…} |
| coreloop/FirstMagicWorkshop.kt:422 | 81 | 素材を分析して発見 |
| coreloop/FirstMagicWorkshop.kt:423 | 68 | 研究インク {…} |
| coreloop/FirstMagicWorkshop.kt:423 | 81 | 固定された手がかり |
| coreloop/FirstMagicWorkshop.kt:431 | 45 | 研究の星図 |
| coreloop/FirstMagicWorkshop.kt:431 | 74 | 解明 {…} / {…} |
| coreloop/FirstMagicWorkshop.kt:439 | 140 | 解明済み / クリックで盤面を見る |
| coreloop/FirstMagicWorkshop.kt:439 | 90 | クリックで研究を開く |
| coreloop/FirstMagicWorkshop.kt:442 | 27 | 前の頁 |
| coreloop/FirstMagicWorkshop.kt:444 | 27 | 次の頁 |
| coreloop/FirstMagicWorkshop.kt:446 | 61 | Aspectを合成 |
| coreloop/FirstMagicWorkshop.kt:446 | 90 | 新しい性質を発見する |
| coreloop/FirstMagicWorkshop.kt:448 | 54 | 研究の調合台 |
| coreloop/FirstMagicWorkshop.kt:448 | 135 | 解明した研究から売れる品を作る |
| coreloop/FirstMagicWorkshop.kt:450 | 36 | 記録帳へ |
| coreloop/FirstMagicWorkshop.kt:452 | 80 | 研究の星図 {…}/2 |
| coreloop/FirstMagicWorkshop.kt:459 | 61 | Aspectの合成 |
| coreloop/FirstMagicWorkshop.kt:459 | 117 | 基礎から複合性質を発見する |
| coreloop/FirstMagicWorkshop.kt:464 | 72 | 未知の組み合わせ |
| coreloop/FirstMagicWorkshop.kt:466 | 63 | クリックで合成 |
| coreloop/FirstMagicWorkshop.kt:466 | 45 | 材料が不足 |
| coreloop/FirstMagicWorkshop.kt:466 | 83 | インク {…} / {…} |
| coreloop/FirstMagicWorkshop.kt:470 | 27 | 前の頁 |
| coreloop/FirstMagicWorkshop.kt:472 | 27 | 次の頁 |
| coreloop/FirstMagicWorkshop.kt:474 | 45 | 研究一覧へ |
| coreloop/FirstMagicWorkshop.kt:476 | 87 | Aspect合成 {…}/2 |
| coreloop/FirstMagicWorkshop.kt:491 | 36 | 空の六角 |
| coreloop/FirstMagicWorkshop.kt:491 | 115 | クリックしてAspectを選ぶ |
| coreloop/FirstMagicWorkshop.kt:496 | 68 | 有効な接続 {…} |
| coreloop/FirstMagicWorkshop.kt:496 | 126 | クリックで外し、インクを戻す |
| coreloop/FirstMagicWorkshop.kt:500 | 81 | 固定された手がかり |
| coreloop/FirstMagicWorkshop.kt:500 | 54 | 起点と接続中 |
| coreloop/FirstMagicWorkshop.kt:500 | 117 | まだ起点とつながっていない |
| coreloop/FirstMagicWorkshop.kt:508 | 45 | 研究一覧へ |
| coreloop/FirstMagicWorkshop.kt:512 | 54 | 研究解明済み |
| coreloop/FirstMagicWorkshop.kt:512 | 114 | 手がかり {…} / {…} 接続 |
| coreloop/FirstMagicWorkshop.kt:514 | 61 | Aspect合成へ |
| coreloop/FirstMagicWorkshop.kt:516 | 55 | 研究盤・{…} |
| coreloop/FirstMagicWorkshop.kt:522 | 36 | 解放済み |
| coreloop/FirstMagicWorkshop.kt:522 | 45 | 解明すると |
| coreloop/FirstMagicWorkshop.kt:522 | 101 | {…}：{…}を調合できる |
| coreloop/FirstMagicWorkshop.kt:523 | 106 |   Essentia {…} + {…} |
| coreloop/FirstMagicWorkshop.kt:524 | 112 |   調合料 銀貨{…}（消滅） |
| coreloop/FirstMagicWorkshop.kt:534 | 54 | 研究の調合台 |
| coreloop/FirstMagicWorkshop.kt:535 | 74 | 解明 {…} / {…} |
| coreloop/FirstMagicWorkshop.kt:535 | 41 | 銀貨 {…} |
| coreloop/FirstMagicWorkshop.kt:543 | 79 | {…} {…}（{…}） |
| coreloop/FirstMagicWorkshop.kt:544 | 18 | 不足 |
| coreloop/FirstMagicWorkshop.kt:545 | 109 | {…}（港の市場で購入可） |
| coreloop/FirstMagicWorkshop.kt:546 | 18 | 不足 |
| coreloop/FirstMagicWorkshop.kt:546 | 95 | 調合料 銀貨{…}  {…} |
| coreloop/FirstMagicWorkshop.kt:548 | 127 | 研究「{…}」を解明すると開く |
| coreloop/FirstMagicWorkshop.kt:548 | 182 | クリックで1個調合 / 完成品は市場で売れる |
| coreloop/FirstMagicWorkshop.kt:551 | 54 | 研究の星図へ |
| coreloop/FirstMagicWorkshop.kt:553 | 36 | 研究机へ |
| coreloop/FirstMagicWorkshop.kt:555 | 54 | 研究の調合台 |
| coreloop/FirstMagicWorkshop.kt:562 | 106 | 六角 {…} に記すAspect |
| coreloop/FirstMagicWorkshop.kt:562 | 90 | 所持インクを一つ使う |
| coreloop/FirstMagicWorkshop.kt:572 | 27 | 前の頁 |
| coreloop/FirstMagicWorkshop.kt:574 | 27 | 次の頁 |
| coreloop/FirstMagicWorkshop.kt:576 | 45 | 盤面へ戻る |
| coreloop/FirstMagicWorkshop.kt:578 | 80 | 研究インク {…}/2 |
| coreloop/FirstStepsGuide.kt:24 | 66 | 導入 {…}/{…} |
| coreloop/FirstStepsGuide.kt:50 | 107 | projects_ui_polish05 |
| coreloop/FirstStepsGuide.kt:50 | 80 | menu{…}_{…}_0 |
| coreloop/GrimoireBook.kt:9 | 41 | 銀貨 {…} |
| coreloop/GrimoireBook.kt:9 | 59 | インク ×{…} |
| coreloop/GrimoireBook.kt:21 | 9 | 幹 |
| coreloop/GrimoireBook.kt:21 | 27 | 刻印術 |
| coreloop/GrimoireBook.kt:21 | 18 | 注入 |
| coreloop/GrimoireBook.kt:21 | 18 | 召喚 |
| coreloop/GrimoireBook.kt:22 | 18 | 錬金 |
| coreloop/GrimoireBook.kt:22 | 27 | 地脈術 |
| coreloop/GrimoireBook.kt:22 | 36 | 魔導工学 |
| coreloop/GrimoireBook.kt:24 | 279 | 帰還・加速・転移などの巻物。使えば消える、魔術の効果そのもの。 |
| coreloop/GrimoireBook.kt:25 | 243 | 武器や防具に、魔術ならではの性質を与える・付け替える。 |
| coreloop/GrimoireBook.kt:26 | 252 | ボスを呼ぶ封印の鍵を作る。戦闘職がボスに挑むための入口。 |
| coreloop/GrimoireBook.kt:27 | 126 | 薬や食べ物。体に入れる魔術。 |
| coreloop/GrimoireBook.kt:28 | 234 | 領地の結界、天候、転移の網。世界そのものに手を出す。 |
| coreloop/GrimoireBook.kt:29 | 207 | 仕分けや自動化の装置。工房を工場のように組む。 |
| coreloop/GrimoireBook.kt:32 | 36 | 古い手帳 |
| coreloop/GrimoireBook.kt:32 | 40 | 序章 観測 |
| coreloop/GrimoireBook.kt:32 | 468 | 光る草に挟まっていた手帳。最初のページには一文だけ。「すべての物は、目に見えない性質（相）でできている」 |
| coreloop/GrimoireBook.kt:33 | 63 | 光る素材を拾う |
| coreloop/GrimoireBook.kt:33 | 18 | 開く |
| coreloop/GrimoireBook.kt:33 | 27 | 魔術書 |
| coreloop/GrimoireBook.kt:34 | 45 | 観測の眼鏡 |
| coreloop/GrimoireBook.kt:34 | 40 | 序章 観測 |
| coreloop/GrimoireBook.kt:34 | 198 | かぶっている間だけ、見ている物の相が見える。 |
| coreloop/GrimoireBook.kt:35 | 120 | 眼鏡を作る（ガラス2・銅1） |
| coreloop/GrimoireBook.kt:35 | 27 | 見える |
| coreloop/GrimoireBook.kt:35 | 54 | 照準の先の相 |
| coreloop/GrimoireBook.kt:36 | 27 | 研究机 |
| coreloop/GrimoireBook.kt:36 | 40 | 序章 観測 |
| coreloop/GrimoireBook.kt:36 | 297 | 読めない相は、机の上で考えると分かる。最初の「自分の魔術の場所」。 |
| coreloop/GrimoireBook.kt:37 | 54 | 研究机を置く |
| coreloop/GrimoireBook.kt:37 | 27 | 置ける |
| coreloop/GrimoireBook.kt:37 | 27 | 研究机 |
| coreloop/GrimoireBook.kt:38 | 72 | 最初の研究「光」 |
| coreloop/GrimoireBook.kt:38 | 40 | 序章 観測 |
| coreloop/GrimoireBook.kt:38 | 383 | 相は2つ合わさって新しい相になる。松明の相は、Aer と Ignis が合わさった Lux だった。 |
| coreloop/GrimoireBook.kt:39 | 89 | 研究盤で Lux を解く |
| coreloop/GrimoireBook.kt:39 | 27 | 見える |
| coreloop/GrimoireBook.kt:39 | 54 | 珍しい物の光 |
| coreloop/GrimoireBook.kt:40 | 27 | 蒸留器 |
| coreloop/GrimoireBook.kt:40 | 37 | 1章 抽出 |
| coreloop/GrimoireBook.kt:40 | 393 | 相は取り出して液体（Essentia）にできる。素材を窯で溶かすと、中の相が分かれて出てくる。 |
| coreloop/GrimoireBook.kt:41 | 108 | 蒸留器を作って工房に置く |
| coreloop/GrimoireBook.kt:41 | 27 | 置ける |
| coreloop/GrimoireBook.kt:41 | 27 | 蒸留器 |
| coreloop/GrimoireBook.kt:42 | 9 | 瓶 |
| coreloop/GrimoireBook.kt:42 | 37 | 1章 抽出 |
| coreloop/GrimoireBook.kt:42 | 226 | Essentia は瓶に溜まる。瓶ひとつにつき相はひとつ。 |
| coreloop/GrimoireBook.kt:43 | 51 | 瓶を3つ置く |
| coreloop/GrimoireBook.kt:43 | 27 | 置ける |
| coreloop/GrimoireBook.kt:43 | 9 | 瓶 |
| coreloop/GrimoireBook.kt:44 | 45 | 相を溜める |
| coreloop/GrimoireBook.kt:44 | 37 | 1章 抽出 |
| coreloop/GrimoireBook.kt:44 | 180 | 蒸留器と瓶がそろうと、相が溜まっていく。 |
| coreloop/GrimoireBook.kt:45 | 152 | ひとつの瓶に Essentia を16溜める |
| coreloop/GrimoireBook.kt:45 | 27 | 作れる |
| coreloop/GrimoireBook.kt:45 | 82 | 瓶詰めの Essentia |
| coreloop/GrimoireBook.kt:46 | 72 | 寄り道：相の図鑑 |
| coreloop/GrimoireBook.kt:46 | 37 | 1章 抽出 |
| coreloop/GrimoireBook.kt:46 | 216 | 本筋には要らない。いろんな物を見て、相を集める。 |
| coreloop/GrimoireBook.kt:47 | 84 | 相を12種類観察する |
| coreloop/GrimoireBook.kt:47 | 36 | もらえる |
| coreloop/GrimoireBook.kt:47 | 54 | 眼鏡の見た目 |
| coreloop/GrimoireBook.kt:48 | 9 | 杖 |
| coreloop/GrimoireBook.kt:48 | 37 | 2章 流れ |
| coreloop/GrimoireBook.kt:48 | 261 | 杖は工房をつなぐレンチ。顎で管をはさみ、弁の向きを決める。 |
| coreloop/GrimoireBook.kt:49 | 36 | 杖を作る |
| coreloop/GrimoireBook.kt:49 | 27 | 作れる |
| coreloop/GrimoireBook.kt:49 | 9 | 杖 |
| coreloop/GrimoireBook.kt:50 | 27 | 管と弁 |
| coreloop/GrimoireBook.kt:50 | 37 | 2章 流れ |
| coreloop/GrimoireBook.kt:50 | 162 | 相は、引っ張る力の強いほうへ流れる。 |
| coreloop/GrimoireBook.kt:51 | 108 | 蒸留器から瓶へ管でつなぐ |
| coreloop/GrimoireBook.kt:51 | 27 | 置ける |
| coreloop/GrimoireBook.kt:51 | 27 | 管、弁 |
| coreloop/GrimoireBook.kt:52 | 18 | 地脈 |
| coreloop/GrimoireBook.kt:52 | 37 | 3章 地脈 |
| coreloop/GrimoireBook.kt:52 | 153 | 足もとを流れる、見えない川がある。 |
| coreloop/GrimoireBook.kt:53 | 99 | 地脈の上に研究机を置く |
| coreloop/GrimoireBook.kt:53 | 27 | 見える |
| coreloop/GrimoireBook.kt:53 | 45 | 地脈の流れ |
| coreloop/GrimoireBook.kt:54 | 45 | 帰還の巻物 |
| coreloop/GrimoireBook.kt:54 | 67 | 終章 最初の儀式 |
| coreloop/GrimoireBook.kt:54 | 351 | 幹のゴール。遠征先から、魔術で帰ってくる。ここまで来ると専門の枝の封が解ける。 |
| coreloop/GrimoireBook.kt:55 | 126 | 小さな祭壇で帰還の巻物を作る |
| coreloop/GrimoireBook.kt:55 | 18 | 開く |
| coreloop/GrimoireBook.kt:55 | 63 | 専門の枝すべて |
| coreloop/GrimoireFlow.kt:92 | 27 | 」と「 |
| coreloop/GrimoireFlow.kt:92 | 72 | 」を終えると開く |
| coreloop/GrimoireFlow.kt:92 | 36 | 寄り道： |
| coreloop/GrimoireFlow.kt:264 | 27 | 魔術書 |
| coreloop/GrimoireFlow.kt:265 | 71 | 帰還港 · 研究室 |
| coreloop/GrimoireFlow.kt:281 | 27 | インク |
| coreloop/GrimoireFlow.kt:285 | 18 | 銀貨 |
| coreloop/GrimoireFlow.kt:326 | 62 | 幹 · 基礎魔術 |
| coreloop/GrimoireFlow.kt:327 | 62 | 幹 · 基礎魔術 |
| coreloop/GrimoireFlow.kt:329 | 86 | 受け取れる報酬 {…} |
| coreloop/GrimoireFlow.kt:333 | 45 | m-claim-t |
| coreloop/GrimoireFlow.kt:388 | 36 | 寄り道： |
| coreloop/GrimoireFlow.kt:390 | 34 | center |
| coreloop/GrimoireFlow.kt:407 | 54 | まだ見えない |
| coreloop/GrimoireFlow.kt:409 | 108 | 前の項目を終えると分かる |
| coreloop/GrimoireFlow.kt:411 | 72 | 報酬を受け取れる |
| coreloop/GrimoireFlow.kt:423 | 24 | tip-s |
| coreloop/GrimoireFlow.kt:424 | 24 | tip-h |
| coreloop/GrimoireFlow.kt:430 | 44 | lg-{…}-t |
| coreloop/GrimoireFlow.kt:431 | 18 | 完了 |
| coreloop/GrimoireFlow.kt:431 | 45 | 進められる |
| coreloop/GrimoireFlow.kt:431 | 27 | 寄り道 |
| coreloop/GrimoireFlow.kt:431 | 45 | 幹のゴール |
| coreloop/GrimoireFlow.kt:431 | 27 | 未解放 |
| coreloop/GrimoireFlow.kt:435 | 21 | lg-q |
| coreloop/GrimoireFlow.kt:435 | 34 | center |
| coreloop/GrimoireFlow.kt:435 | 54 | まだ見えない |
| coreloop/GrimoireFlow.kt:437 | 33 | lg-c-x |
| coreloop/GrimoireFlow.kt:437 | 42 | #FFF1DE |
| coreloop/GrimoireFlow.kt:437 | 34 | center |
| coreloop/GrimoireFlow.kt:437 | 36 | 報酬あり |
| coreloop/GrimoireFlow.kt:439 | 33 | lg-p-d |
| coreloop/GrimoireFlow.kt:439 | 42 | #7FE3F0 |
| coreloop/GrimoireFlow.kt:439 | 36 | ピン留め |
| coreloop/GrimoireFlow.kt:461 | 162 | 幹の最後に「帰還の巻物」を作ると開く |
| coreloop/GrimoireFlow.kt:471 | 48 | e-state-t |
| coreloop/GrimoireFlow.kt:471 | 34 | center |
| coreloop/GrimoireFlow.kt:486 | 54 | まだ見えない |
| coreloop/GrimoireFlow.kt:488 | 198 | 前の項目を終えると、ここに何があるか分かる。 |
| coreloop/GrimoireFlow.kt:498 | 18 | 目標 |
| coreloop/GrimoireFlow.kt:515 | 90 | できるようになること |
| coreloop/GrimoireFlow.kt:524 | 27 | この先 |
| coreloop/GrimoireFlow.kt:528 | 36 | 寄り道： |
| coreloop/GrimoireFlow.kt:540 | 18 | 報酬 |
| coreloop/GrimoireFlow.kt:545 | 42 | #ECE6DC |
| coreloop/GrimoireFlow.kt:552 | 63 | 報酬を受け取る |
| coreloop/GrimoireFlow.kt:556 | 54 | 受け取り済み |
| coreloop/GrimoireFlow.kt:563 | 72 | 画面にピン留め中 |
| coreloop/GrimoireFlow.kt:563 | 81 | 画面にピン留めする |
| coreloop/GrimoireFlow.kt:565 | 117 | 試し：ワールドで達成させる |
| coreloop/GrimoireFlow.kt:611 | 27 | 封印中 |
| coreloop/GrimoireFlow.kt:618 | 243 | 幹の最後に「帰還の巻物」を作ると、この枝の地図が開く。 |
| coreloop/GrimoireFlow.kt:625 | 27 | 閉じる |
| coreloop/GrimoireFlow.kt:625 | 45 | 左クリック |
| coreloop/GrimoireFlow.kt:625 | 18 | 選択 |
| coreloop/GrimoireFlow.kt:642 | 36 | 、。」） |
| coreloop/GrimoireFlow.kt:672 | 18 | 開く |
| coreloop/GrimoireFlow.kt:672 | 27 | 見える |
| coreloop/GrimoireFlow.kt:672 | 27 | 置ける |
| coreloop/GrimoireFlow.kt:672 | 27 | 作れる |
| coreloop/GrimoireFlow.kt:672 | 36 | もらえる |
| coreloop/GrimoireFlow.kt:674 | 18 | 完了 |
| coreloop/GrimoireFlow.kt:674 | 45 | 進められる |
| coreloop/GrimoireFlow.kt:674 | 45 | 進められる |
| coreloop/GrimoireFlow.kt:675 | 27 | 寄り道 |
| coreloop/GrimoireFlow.kt:675 | 27 | 未解放 |
| coreloop/GrimoireFlow.kt:675 | 54 | まだ見えない |
| coreloop/GrimoireFlow.kt:676 | 45 | 幹のゴール |
| coreloop/HarborScene.kt:89 | 97 | 鍛冶師  │  話しかける |
| coreloop/MilestoneSigns.kt:28 | 45 | ダンジョン |
| coreloop/MilestoneSigns.kt:30 | 57 | 冒険 Lv {…} |
| coreloop/MilestoneSigns.kt:44 | 97 | projects_milestone |
| coreloop/MilestoneSigns.kt:44 | 39 | sign{…} |
| coreloop/MilestoneSigns.kt:45 | 36 | 初踏破！ |
| coreloop/MilestoneSigns.kt:45 | 36 | アップ！ |
| coreloop/MilestoneSigns.kt:46 | 107 | projects_ui_polish05 |
| coreloop/MilestoneSigns.kt:46 | 73 | menu{…}_m6_0 |
| coreloop/MilestoneSigns.kt:58 | 45 | 散り桜の社 |
| coreloop/PassiveTreeFlow.kt:370 | 36 | パッシブ |
| coreloop/PassiveTreeFlow.kt:376 | 18 | 銀貨 |
| coreloop/PassiveTreeFlow.kt:377 | 36 | ポイント |
| coreloop/PassiveTreeFlow.kt:398 | 18 | 合計 |
| coreloop/PassiveTreeFlow.kt:399 | 32 | {…} 点 |
| coreloop/PassiveTreeFlow.kt:410 | 54 | キーストーン |
| coreloop/PassiveTreeFlow.kt:442 | 54 | 港でのみ変更 |
| coreloop/PassiveTreeFlow.kt:443 | 45 | 自分の起点 |
| coreloop/PassiveTreeFlow.kt:443 | 63 | ほかの職の起点 |
| coreloop/PassiveTreeFlow.kt:444 | 36 | 取得済み |
| coreloop/PassiveTreeFlow.kt:445 | 45 | 仮に取った |
| coreloop/PassiveTreeFlow.kt:446 | 63 | 主要ノードが先 |
| coreloop/PassiveTreeFlow.kt:447 | 54 | ポイント不足 |
| coreloop/PassiveTreeFlow.kt:448 | 33 | 1つ選ぶ |
| coreloop/PassiveTreeFlow.kt:449 | 27 | 準備中 |
| coreloop/PassiveTreeFlow.kt:450 | 36 | 届かない |
| coreloop/PassiveTreeFlow.kt:451 | 68 | {…} 点足りない |
| coreloop/PassiveTreeFlow.kt:452 | 54 | あと {…} 点 |
| coreloop/PassiveTreeFlow.kt:477 | 18 | 代金 |
| coreloop/PassiveTreeFlow.kt:478 | 36 | %,d 銀貨 |
| coreloop/PassiveTreeFlow.kt:480 | 54 | できなかった |
| coreloop/PassiveTreeFlow.kt:486 | 54 | 確定のあとで |
| coreloop/PassiveTreeFlow.kt:487 | 68 | 戻す（{…} 個） |
| coreloop/PassiveTreeFlow.kt:487 | 18 | 戻す |
| coreloop/PassiveTreeFlow.kt:488 | 68 | 外す（{…} 個） |
| coreloop/PassiveTreeFlow.kt:488 | 18 | 外す |
| coreloop/PassiveTreeFlow.kt:489 | 86 | 仮に取る（{…} 点） |
| coreloop/PassiveTreeFlow.kt:490 | 18 | 取る |
| coreloop/PassiveTreeFlow.kt:497 | 51 | 確定 ({…}) |
| coreloop/PassiveTreeFlow.kt:497 | 36 | 取り消し |
| coreloop/PassiveTreeFlow.kt:497 | 27 | 閉じる |
| coreloop/PassiveTreeFlow.kt:500 | 36 | 全部戻す |
| coreloop/PassiveTreeFlow.kt:500 | 27 | 起点へ |
| coreloop/PassiveTreeFlow.kt:500 | 27 | 閉じる |
| coreloop/PassiveTreeFlow.kt:545 | 36 | 選び済み |
| coreloop/PassiveTreeFlow.kt:592 | 27 | ノード |
| coreloop/PassiveTreeFlow.kt:592 | 45 | 主要ノード |
| coreloop/PassiveTreeFlow.kt:593 | 54 | キーストーン |
| coreloop/PassiveTreeFlow.kt:593 | 18 | 起点 |
| coreloop/PassiveTreeFlow.kt:593 | 18 | 極意 |
| coreloop/PlayerDisplaySettings.kt:71 | 177 | 最初に、画面の設定を2つ教えてください。 |
| coreloop/PlayerDisplaySettings.kt:72 | 306 | メニューとカメラの画面を、あなたの画面に合わせて描くために使います。 |
| coreloop/PlayerDisplaySettings.kt:73 | 197 | どちらも「設定 → ビデオ設定」で見られます。 |
| coreloop/PlayerDisplaySettings.kt:74 | 271 | GUIの大きさが「自動」なら、1920×1080 の画面では 4 です。 |
| coreloop/PlayerDisplaySettings.kt:76 | 14 | gui |
| coreloop/PlayerDisplaySettings.kt:78 | 52 | GUIの大きさ |
| coreloop/PlayerDisplaySettings.kt:79 | 17 | fov |
| coreloop/PlayerDisplaySettings.kt:79 | 63 | 視野角（FOV） |
| coreloop/PlayerDisplaySettings.kt:79 | 109 | options.generic_value |
| coreloop/PlayerDisplaySettings.kt:82 | 45 | 画面の設定 |
| coreloop/PlayerDisplaySettings.kt:82 | 45 | 画面の設定 |
| coreloop/PlayerDisplaySettings.kt:84 | 18 | 決定 |
| coreloop/PlayerDisplaySettings.kt:85 | 24 | save |
| coreloop/PlayerDisplaySettings.kt:95 | 234 | 設定を読み取れませんでした。もう一度選んでください。 |
| coreloop/PlayerDisplaySettings.kt:101 | 382 | 画面の設定を保存しました（GUIの大きさ {…}・FOV {…}）。/settings で変えられます。 |
| coreloop/PlayerMenuDialog.kt:42 | 18 | 戦闘 |
| coreloop/PlayerMenuDialog.kt:42 | 18 | 採取 |
| coreloop/PlayerMenuDialog.kt:42 | 18 | 鍛冶 |
| coreloop/PlayerMenuDialog.kt:42 | 18 | 魔術 |
| coreloop/PlayerMenuDialog.kt:64 | 27 | close |
| coreloop/PlayerMenuDialog.kt:69 | 72 | 魔術は準備中です |
| coreloop/PlayerMenuDialog.kt:71 | 126 | 名刺を整える画面は準備中です |
| coreloop/PlayerMenuDialog.kt:108 | 45 | 冒険の手帳 |
| coreloop/PlayerMenuDialog.kt:113 | 27 | close |
| coreloop/PlayerMenuDialog.kt:119 | 27 | close |
| coreloop/PlayerMenuDialog.kt:134 | 30 | notice |
| coreloop/PlayerMenuDialog.kt:153 | 24 | name |
| coreloop/PlayerMenuDialog.kt:156 | 28 | dot16 |
| coreloop/PlayerMenuDialog.kt:156 | 28 | dot16 |
| coreloop/PlayerMenuDialog.kt:181 | 36 | hit_row |
| coreloop/PlayerMenuDialog.kt:181 | 47 | path_{…} |
| coreloop/PlayerMenuDialog.kt:206 | 65 | 冒険 Lv は上限 |
| coreloop/PlayerMenuDialog.kt:206 | 79 | 次の Lv まで {…} |
| coreloop/PlayerMenuDialog.kt:213 | 35 | Lv {…} |
| coreloop/PlayerMenuDialog.kt:217 | 54 | Rank は上限 |
| coreloop/PlayerMenuDialog.kt:218 | 90 | 次の Rank まで {…} |
| coreloop/PlayerMenuDialog.kt:221 | 27 | 準備中 |
| coreloop/PlayerMenuDialog.kt:264 | 31 | slot_x |
| coreloop/PlayerMenuDialog.kt:264 | 106 | 冒険 Lv {…} で使えます |
| coreloop/PlayerMenuDialog.kt:273 | 37 | hit_slot |
| coreloop/PlayerMenuDialog.kt:273 | 42 | key_{…} |
| coreloop/PlayerMenuDialog.kt:273 | 65 | キー{…}　{…} |
| coreloop/PlayerMenuDialog.kt:290 | 35 | key_tri |
| coreloop/PlayerMenuDialog.kt:294 | 37 | hit_slot |
| coreloop/PlayerMenuDialog.kt:294 | 43 | cell_{…} |
| coreloop/PlayerMenuDialog.kt:307 | 35 | Lv {…} |
| coreloop/PlayerMenuDialog.kt:310 | 28 | dot16 |
| coreloop/PlayerMenuDialog.kt:310 | 28 | dot16 |
| coreloop/PlayerMenuDialog.kt:324 | 116 | ×%.2f　次の Lv で ×%.2f |
| coreloop/PlayerMenuDialog.kt:333 | 71 | Lv {…}（上限） |
| coreloop/PlayerMenuDialog.kt:334 | 117 | スキルポイントがありません |
| coreloop/PlayerMenuDialog.kt:335 | 116 | Lv を上げる（1 ポイント） |
| coreloop/PlayerMenuDialog.kt:338 | 25 | Lv を |
| coreloop/PlayerMenuDialog.kt:342 | 77 | キー{…} に入れる |
| coreloop/PlayerMenuDialog.kt:342 | 77 | キー{…} のスキル |
| coreloop/PlayerMenuDialog.kt:343 | 27 | 入れる |
| coreloop/PlayerMenuDialog.kt:349 | 167 | スキルポイントを全部戻す（銀貨 {…}） |
| coreloop/PlayerMenuDialog.kt:397 | 139 | polish05/menu-metrics.json |
| coreloop/ResearchEconomy.kt:16 | 241 | 強化の「触媒」で使用：成功率+{…}（通常の触媒は+15） |
| coreloop/ResearchEconomy.kt:17 | 201 | 破損装備をTier×{…}個で修理（予備装備不要） |
| coreloop/ResearchEconomy.kt:18 | 171 | 研究素材。分析・研究インク・蒸留に使う |
| coreloop/ResearchEconomy.kt:31 | 46 | {…}を調合 |
| coreloop/ResearchEconomy.kt:84 | 99 | データを読み込めません |
| coreloop/ResearchEconomy.kt:97 | 99 | データを読み込めません |
| coreloop/ResearchEconomy.kt:104 | 267 | 研究テスト用に素材4個ずつ・銀貨300枚・研究インクを補充した |
| coreloop/ReturnDialog.kt:28 | 36 | 討伐済み |
| coreloop/ReturnDialog.kt:28 | 27 | 未討伐 |
| coreloop/ReturnDialog.kt:29 | 108 | このマップには戻れません |
| coreloop/ReturnDialog.kt:31 | 36 | 港へ帰還 |
| coreloop/ReturnDialog.kt:31 | 36 | 港へ帰還 |
| coreloop/ReturnDialog.kt:32 | 36 | 帰還する |
| coreloop/ReturnDialog.kt:32 | 24 | home |
| coreloop/ReturnDialog.kt:32 | 18 | 手帳 |
| coreloop/ReturnDialog.kt:32 | 23 | book |
| coreloop/ReturnDialog.kt:33 | 27 | やめる |
| coreloop/ReturnDialog.kt:33 | 27 | close |
| coreloop/WorldInfusionServer.kt:71 | 163 | 試作保存データは保護されました：{…} |
| coreloop/WorldInfusionServer.kt:78 | 449 | ワールド祭壇試作：祭壇キットを床へ右クリック → 外側台座とJarを設置。装備と素材を手に持って台座へ。 |
| coreloop/WorldInfusionServer.kt:79 | 399 | Matrixを筆記杖で起動。しゃがみ＋空手で素材回収／空台座・Jar撤去、しゃがみ＋杖で取消し。 |
| coreloop/WorldInfusionServer.kt:80 | 384 | これは独立した試験ワールド。初期装備・素材・充填Jar・範囲6・霜転レシピは仮設定です。 |
| coreloop/WorldInfusionServer.kt:159 | 147 | 筆記杖：Matrixを右クリックで起動 |
| coreloop/WorldInfusionServer.kt:160 | 81 | 祭壇キット（試作） |
| coreloop/WorldInfusionServer.kt:161 | 36 | 外側台座 |
| coreloop/WorldInfusionServer.kt:162 | 81 | infusion-v3/jar |
| coreloop/WorldInfusionServer.kt:162 | 89 | {…} Jar {…}/{…} |
| coreloop/WorldInfusionServer.kt:162 | 39 | jar:{…} |
| coreloop/WorldInfusionServer.kt:173 | 45 | UUID {…} |
| coreloop/WorldInfusionServer.kt:173 | 50 | 製作者 {…} |
| coreloop/WorldInfusionServer.kt:174 | 140 | 品質{…} / T{…} Lv{…} +{…} |
| coreloop/WorldInfusionServer.kt:175 | 74 | 枠{…} {…} {…} |
| coreloop/WorldInfusionServer.kt:190 | 63 | 操作できません |
| coreloop/WorldInfusionServer.kt:213 | 237 | 中心に装備、Matrixには筆記杖。取消しはしゃがみ＋杖。 |
| coreloop/WorldInfusionServer.kt:219 | 193 | {…} Jar {…}/{…} / しゃがみ＋空手で撤去 |
| coreloop/WorldInfusionServer.kt:227 | 198 | 通常素材を手に持って右クリック。空手で回収。 |
| coreloop/WorldInfusionServer.kt:268 | 36 | 中心台座 |
| coreloop/WorldInfusionServer.kt:280 | 36 | 外側台座 |
| coreloop/WorldInfusionServer.kt:406 | 314 | 供給停止：Jarを設置し直しMatrixへ筆記杖で再開 / しゃがみ＋杖で取消し |
| coreloop/WorldInfusionServer.kt:407 | 205 | 元素吸収 {…}/7 — 周囲素材はまだ残っています |
| coreloop/WorldInfusionServer.kt:408 | 80 | 副素材吸収 {…}/4 |
| coreloop/WorldInfusionServer.kt:409 | 237 | 霜転の調律 完了 — 中心台座へ空手で右クリックして回収 |
| coreloop/WorldInfusionServer.kt:410 | 192 | 準備完了：頭上のMatrixへ筆記杖で右クリック |
| coreloop/WorldInfusionServer.kt:412 | 199 | E {…}/{…} 残り{…} / P={…}E/s（仮） /  |
| coreloop/WorldInfusionServer.kt:412 | 134 | E=120 / P={…}E/s（仮） /  |
| coreloop/WorldInfusionState.kt:7 | 9 | 火 |
| coreloop/WorldInfusionState.kt:7 | 9 | 水 |
| coreloop/WorldInfusionState.kt:7 | 9 | 風 |
| coreloop/WorldInfusionState.kt:118 | 135 | 中心台座に装備を載せてください |
| coreloop/WorldInfusionState.kt:120 | 195 | 固定0枠に火属性MODが必要です（試作レシピ） |
| coreloop/WorldInfusionState.kt:122 | 219 | 台座にT2鉄×2・布×1・加工石×1を載せてください |
| coreloop/WorldInfusionState.kt:126 | 233 | 近くの設置Jar：{…}が{…}不足（試作範囲6ブロック） |
| coreloop/adventure/AdventureRuntime.kt:113 | 198 | 進行中の寄り道を終えてから起動してください。 |
| coreloop/adventure/AdventureRuntime.kt:135 | 114 | {…} 第{…}波・敵を倒せ！ |
| coreloop/adventure/AdventureRuntime.kt:138 | 243 | 敵を安全に配置できないため、この寄り道を中止しました。 |
| coreloop/adventure/AdventureRuntime.kt:150 | 153 | 寄り道失敗・探索者が離脱しました。 |
| coreloop/adventure/AdventureRuntime.kt:154 | 153 | 寄り道失敗・制限時間を超えました。 |
| coreloop/adventure/AdventureRuntime.kt:162 | 18 | 祭域 |
| coreloop/adventure/AdventureRuntime.kt:162 | 18 | 亀裂 |
| coreloop/adventure/AdventureRuntime.kt:162 | 142 | 5秒以内に{…}へ戻ってください！ |
| coreloop/adventure/AdventureRuntime.kt:164 | 162 | 寄り道失敗・戦闘地域から離れました。 |
| coreloop/adventure/AdventureRuntime.kt:178 | 216 | 次の亀裂へ：X {…} / Z {…}。右クリックで追撃。 |
| coreloop/adventure/AdventureRuntime.kt:179 | 311 | 祭儀の報酬 {…}個：祭壇を右クリックで続行／しゃがみ右クリックで確定。 |
| coreloop/adventure/AdventureRuntime.kt:190 | 99 | 寄り道は終了しました。 |
| coreloop/adventure/AdventureRuntime.kt:206 | 189 | {…} 達成！ 専用オーブ {…} 個の報酬確定。 |
| coreloop/adventure/AdventureRuntime.kt:219 | 45 | 裂界の亀裂 |
| coreloop/adventure/AdventureRuntime.kt:219 | 45 | 血誓の祭儀 |
| coreloop/adventure/AdventureRuntime.kt:263 | 105 | 右クリックで起動・全3波 |
| coreloop/adventure/AdventureRuntime.kt:264 | 108 | 第{…}/3波・残り {…}体 |
| coreloop/adventure/AdventureRuntime.kt:264 | 58 | ⏎地域外！ 戻れ |
| coreloop/adventure/AdventureRuntime.kt:265 | 152 | 次の亀裂 {…}/3・右クリックで追撃 |
| coreloop/adventure/AdventureRuntime.kt:266 | 189 | 確定 {…}個⏎右クリック：続行／しゃがみ右クリック：確定 |
| coreloop/adventure/AdventureRuntime.kt:267 | 81 | 達成・報酬確定済み |
| coreloop/adventure/AdventureRuntime.kt:268 | 99 | 失敗・再起動できません |
| coreloop/adventure/AdventureRuntime.kt:269 | 18 | 終了 |
| coreloop/adventure/BossArenaFactory.kt:127 | 45 | 灰燼の溶炉 |
| coreloop/adventure/BossArenaFactory.kt:127 | 45 | 氷獄の円庭 |
| coreloop/adventure/BossArenaFactory.kt:127 | 27 | 翳の巣 |
| coreloop/adventure/BossArenaFactory.kt:127 | 45 | 散り桜の庭 |
| coreloop/adventure/BossArenaFactory.kt:128 | 54 | 花守の試し場 |
| coreloop/adventure/BossArenaFactory.kt:128 | 45 | 天雷の祭壇 |
| coreloop/adventure/InstanceDungeon.kt:89 | 45 | 散り桜の社 |
| coreloop/adventure/InstanceDungeon.kt:112 | 18 | 人数 |
| coreloop/adventure/InstanceDungeon.kt:113 | 18 | 人数 |
| coreloop/adventure/InstanceDungeon.kt:153 | 24 | name |
| coreloop/adventure/InstanceDungeon.kt:260 | 65 | {…}・段階{…} |
| coreloop/adventure/InstanceDungeon.kt:260 | 126 | 敵は強く、部屋は手強くなった |
| coreloop/adventure/InstanceDungeon.kt:269 | 82 | {…}つの波を退けよ |
| coreloop/adventure/InstanceDungeon.kt:270 | 46 | {…}を討て |
| coreloop/adventure/InstanceDungeon.kt:272 | 90 | 湧き続ける穢れを祓え |
| coreloop/adventure/InstanceDungeon.kt:280 | 82 | {…}が目を覚ました |
| coreloop/adventure/InstanceDungeon.kt:312 | 118 | {…}を制圧・次の扉が開いた |
| coreloop/adventure/InstanceDungeon.kt:312 | 127 | {…}を制圧・庭への道が開いた |
| coreloop/adventure/InstanceDungeon.kt:322 | 73 | {…}・最後の穢れ |
| coreloop/adventure/InstanceDungeon.kt:322 | 74 | {…}・あと{…}体 |
| coreloop/adventure/InstanceDungeon.kt:380 | 90 | {…}・第{…}/{…}波 |
| coreloop/ui/CoreForgeLayout.kt:8 | 18 | 強化 |
| coreloop/ui/CoreForgeLayout.kt:8 | 18 | 精製 |
| coreloop/ui/CoreForgeLayout.kt:8 | 18 | 制作 |
| coreloop/ui/CoreForgeLayout.kt:8 | 36 | MOD加工 |
| coreloop/ui/CoreForgeLayout.kt:11 | 27 | すべて |
| coreloop/ui/CoreForgeLayout.kt:11 | 63 | レアリティ昇格 |
| coreloop/ui/CoreForgeLayout.kt:11 | 45 | MODを追加 |
| coreloop/ui/CoreForgeLayout.kt:11 | 63 | MODを引き直す |
| coreloop/ui/CoreForgeLayout.kt:11 | 54 | 数値・初期化 |
| coreloop/ui/CoreForgeSummary.kt:43 | 99 | 拠点で操作してください |
| coreloop/ui/CoreForgeSummary.kt:44 | 74 | {…}が{…}個不足 |
| coreloop/ui/CoreForgeSummary.kt:45 | 109 | {…}の保管上限を超えます |
| coreloop/ui/CoreForgeSummary.kt:54 | 61 | +{…}（最大） |
| coreloop/ui/CoreForgeSummary.kt:56 | 72 | 最大まで強化済み |
| coreloop/ui/CoreForgeSummary.kt:57 | 82 | 成功率 100%・確定 |
| coreloop/ui/CoreForgeSummary.kt:58 | 56 | 成功率 {…}% |
| coreloop/ui/CoreForgeSummary.kt:61 | 90 | 破損中・装備庫で修理 |
| coreloop/ui/CoreForgeSummary.kt:62 | 126 | 最大強化・MODと強化値を維持 |
| coreloop/ui/CoreForgeSummary.kt:63 | 81 | 成功確定・破損なし |
| coreloop/ui/CoreForgeSummary.kt:64 | 137 | 失敗時の破損率 {…}%・修理可能 |
| coreloop/ui/CoreForgeSummary.kt:65 | 129 | 破損なし・成功保証 {…}/{…} |
| coreloop/ui/CoreHudOverlays.kt:72 | 36 | ミニボス |
| coreloop/ui/CoreHudOverlays.kt:175 | 18 | 侵食 |
| coreloop/ui/CoreHudOverlays.kt:175 | 18 | 侵食 |
| coreloop/ui/CoreHudOverlays.kt:227 | 9 | ・ |
| coreloop/ui/CoreMenuCanvas.kt:206 | 46 | projects |
| coreloop/ui/CoreMenuCanvas.kt:256 | 46 | projects |
| coreloop/ui/CoreMenuCanvas.kt:256 | 123 | core_menu_buttons_{…} |
| coreloop/ui/CoreUiComponents.kt:81 | 9 | 可 |
| coreloop/ui/CoreUiComponents.kt:82 | 36 | マナ不足 |
| coreloop/ui/CoreUiComponents.kt:82 | 36 | 資源不足 |
| coreloop/ui/CoreUiComponents.kt:83 | 27 | 未解放 |
| coreloop/ui/CoreUiComponents.kt:84 | 28 | {…}秒 |
| coreloop/ui/CoreUiComponents.kt:88 | 134 | HP {…}/{…}  マナ {…}/{…} |
| coreloop/ui/CoreUiComponents.kt:89 | 74 |   資源 {…}/{…} |
| coreloop/ui/CoreUiComponents.kt:89 | 49 |   障壁 {…} |
| coreloop/ui/CoreUiComponents.kt:92 | 18 | 待ち |
| coreloop/ui/CoreUiComponents.kt:92 | 9 | 回 |
| coreloop/ui/CoreUiComponents.kt:92 | 9 | 秒 |
| coreloop/ui/CoreUiModel.kt:6 | 27 | コモン |
| coreloop/ui/CoreUiModel.kt:7 | 45 | アンコモン |
| coreloop/ui/CoreUiModel.kt:8 | 18 | レア |
| coreloop/ui/CoreUiModel.kt:9 | 36 | エピック |
| coreloop/ui/CoreUiModel.kt:15 | 9 | 攻 |
| coreloop/ui/CoreUiModel.kt:15 | 9 | 速 |
| coreloop/ui/CoreUiModel.kt:16 | 9 | 会 |
| coreloop/ui/CoreUiModel.kt:16 | 9 | 防 |
| coreloop/ui/CoreUiModel.kt:17 | 9 | 魔 |
| coreloop/ui/CoreUiModel.kt:18 | 9 | 報 |
| coreloop/ui/CoreUiModel.kt:20 | 18 | 踏込 |
| coreloop/ui/CoreUiModel.kt:20 | 18 | 叩付 |
| coreloop/ui/CoreUiModel.kt:20 | 18 | 薙払 |
| coreloop/ui/CoreUiModel.kt:36 | 18 | 装備 |
| coreloop/ui/CoreUiModel.kt:80 | 18 | 砥石 |
| coreloop/ui/CoreUiModel.kt:80 | 27 | 回復薬 |
| coreloop/ui/CoreUiModel.kt:80 | 18 | 回避 |
| coreloop/ui/CoreUiModel.kt:80 | 9 | 熱 |
| coreloop/ui/CoreUiModel.kt:80 | 18 | 衝撃 |
| coreloop/ui/CoreUiPackPolicy.kt:36 | 32 | button |
| coreloop/ui/CoreUiPackPolicy.kt:36 | 91 | button_highlighted |
| coreloop/ui/CoreUiPackPolicy.kt:36 | 79 | button_disabled |
| coreloop/ui/CoreUiPackPolicy.kt:36 | 48 | text_field |
| coreloop/ui/CoreUiPackPolicy.kt:36 | 107 | text_field_highlighted |
| coreloop/ui/CoreUiPackServer.kt:63 | 308 | ProjectSのUI・アイコンを適用します。拒否しても通常表示で遊べます。 |
| coreloop/ui/CoreUiPackServer.kt:87 | 137 | ProjectSのUIを適用しました。 |
| coreloop/ui/CoreUiPackServer.kt:87 | 243 | 通常表示で続けます。ゲーム機能はそのまま利用できます。 |
| coreloop/ui/CoreUiTooltip.kt:25 | 63 | {…}  ・  {…} |
| coreloop/ui/CoreUiTooltip.kt:29 | 96 | 範囲 {…}  品質 {…}% |
| coreloop/ui/CoreUiTooltip.kt:31 | 175 | アイテムレベル {…}  /  内部Tier T{…} |
| coreloop/ui/CoreUiTooltip.kt:41 | 63 | {…}  ・  {…} |
| coreloop/ui/CoreUiTooltip.kt:45 | 36 | 基本性能 |
| coreloop/ui/CoreUiTooltip.kt:54 | 26 | MOD   |
| coreloop/ui/CoreUiTooltip.kt:56 | 133 |   未装着 — 工房で刻印できます |
| coreloop/ui/CoreUiTooltip.kt:67 | 112 |     範囲 {…}  品質 {…}% |
| coreloop/ui/CoreUiTooltip.kt:72 | 36 | 詳細情報 |
| coreloop/ui/CoreUiTooltip.kt:73 | 175 | アイテムレベル {…}  /  内部Tier T{…} |
| coreloop/ui/CoreUiTooltip.kt:80 | 123 | 範囲 {…}  品質 {…}%  {…} |
| mob/CrowdControl.kt:10 | 27 | スタン |
| mob/CrowdControl.kt:12 | 36 | 打ち上げ |
| mob/CrowdControl.kt:14 | 54 | ノックバック |
| mob/CrowdControl.kt:16 | 36 | チャーム |
| mob/CrowdControl.kt:18 | 36 | フィアー |
| mob/CrowdControl.kt:20 | 18 | 怯み |
| mob/CrowdControl.kt:22 | 18 | 拘束 |
| mob/CrowdControl.kt:24 | 18 | 盲目 |
| mob/CrowdControl.kt:26 | 18 | 沈黙 |
| mob/MobGrade.kt:10 | 18 | 一般 |
| mob/MobGrade.kt:11 | 18 | 精鋭 |
| mob/MobGrade.kt:12 | 18 | 希少 |
| mob/MobGrade.kt:13 | 18 | 特異 |
| mob/MobGrade.kt:14 | 18 | 厄災 |
| mob/MobGrade.kt:22 | 18 | 俊足 |
| mob/MobGrade.kt:24 | 18 | 鉄皮 |
| mob/MobGrade.kt:26 | 18 | 剛力 |
| mob/MobGrade.kt:28 | 18 | 吸血 |
| mob/MobGrade.kt:30 | 18 | 狂化 |
| mob/MobGrade.kt:32 | 18 | 再生 |
| mob/MobGrade.kt:94 | 66 | SAKURA_FANG |
| mob/MobGroundTelegraph.kt:155 | 85 | minecraft:default |
| mob/QuestEncounterCombat.kt:413 | 63 | 凍結・殴り放題 |
| mob/QuestEncounterCombat.kt:501 | 90 | カウンター・殴り放題 |
| mob/QuestEncounterCombat.kt:515 | 99 | 詠唱を止めた・殴り放題 |
| mob/QuestEncounterCombat.kt:518 | 90 | 殻が割れた・殴り放題 |
| mob/QuestEncounterCombat.kt:702 | 90 | 殻が割れた・殴り放題 |
| mob/QuestEncounterCombat.kt:711 | 99 | 詠唱を止めた・殴り放題 |
| mob/QuestEncounterCombat.kt:1110 | 72 | 障壁・護衛を倒せ |
| mob/QuestEncounterCombat.kt:1124 | 72 | 障壁・護衛を倒せ |
| mob/QuestEncounterCombat.kt:1175 | 87 | 第2形態・鱗粉が光る |
| mob/QuestEncounterCombat.kt:1211 | 45 | 翳の大詠唱 |
| mob/QuestEncounterCombat.kt:1212 | 36 | 止める量 |
| mob/QuestEncounterCombat.kt:1228 | 36 | 鱗粉の殻 |
| mob/QuestEncounterCombat.kt:1228 | 45 | 翳の大詠唱 |
| mob/QuestEncounterCombat.kt:1228 | 55 | {…}  {…}秒 |
| mob/QuestEncounterCombat.kt:1230 | 36 | 殻の耐久 |
| mob/QuestEncounterCombat.kt:1230 | 36 | 止める量 |
| mob/QuestEncounterCombat.kt:1269 | 27 | 空振り |
| mob/QuestEncounterCombat.kt:1368 | 72 | 怒り・花が開いた |
| mob/QuestEncounterCombat.kt:1368 | 72 | 怒り・翼を広げた |
| mob/QuestEncounterCombat.kt:1489 | 55 | {…}を討った |
| mob/QuestEncounterCombat.kt:1490 | 54 | ミニボス撃破 |
| mob/QuestEncounterCombat.kt:1774 | 45 | 角が折れた |
| mob/QuestEncounterCombat.kt:1785 | 45 | 扇が裂けた |
| mob/QuestEncounterCombat.kt:1973 | 90 | 灯が満ちる・柱の陰へ |
| mob/QuestEncounterCombat.kt:2095 | 9 | 炎 |
| mob/QuestEncounterCombat.kt:2095 | 9 | 氷 |
| mob/QuestEncounterCombat.kt:2095 | 9 | 雷 |
| mob/QuestEncounterCombat.kt:2096 | 147 | T{…} {…}  {…}/{…}  弱点:{…} |
| mob/QuestEncounterCombat.kt:2099 | 44 |   ［崩れ］ |
| mob/QuestEncounterCombat.kt:2100 | 49 |   崩し {…} |
| mob/QuestEncounterCombat.kt:2101 | 45 |   ［{…}］ |
| mob/QuestEncounterCombat.kt:2104 | 35 |   帰還中 |
| mob/QuestEncounterCombat.kt:2113 | 54 | ［ミニボス］ |
| mob/QuestEncounterCombat.kt:2113 | 37 | ［{…}］ |
| mob/QuestEncounterCombat.kt:2114 | 54 | Lv{…} {…} |
| mob/QuestEncounterCombat.kt:2141 | 27 | 帰還中 |
| mob/QuestEncounterCombat.kt:2218 | 121 | 花輪が回った！ 正面から打て |
| mob/QuestEncounterCombat.kt:2263 | 163 | 桜紋の陣・一人ひとつ紋に立て（{…}） |
| mob/QuestEncounterCombat.kt:2281 | 85 | 陣が成った！ 崩れた |
| mob/QuestEncounterCombat.kt:2291 | 122 | 陣が崩れた（空いた紋 {…}） |
| mob/QuestEncounterCombat.kt:2334 | 109 | 千本桜・桜の陰へ（{…}） |
| mob/QuestEncounterCombat.kt:2353 | 117 | 千本桜が散った…（崩れた） |
| mob/QuestEncounterCombat.kt:2384 | 85 | カウンター！ 崩れた |
| mob/QuestEncounterCombat.kt:2398 | 54 | 枝角が折れた |
| mob/QuestEncounterCombat.kt:2408 | 81 | 尾の花びらが散った |
| mob/QuestMobContent.kt:14 | 27 | 追放兵 |
| mob/QuestMobContent.kt:15 | 63 | 盾持ちの追放兵 |
| mob/QuestMobContent.kt:16 | 54 | 裂け目の術師 |
| mob/QuestMobContent.kt:17 | 90 | 精鋭・裂け目の狂戦士 |
| mob/QuestMobContent.kt:18 | 63 | 裂け目の執行官 |
| mob/QuestMobContent.kt:19 | 54 | 鉄壁の守護者 |
| mob/QuestMobContent.kt:20 | 63 | 裂け目の預言者 |
| mob/QuestMobContent.kt:21 | 36 | 灰燼の王 |
| mob/QuestMobContent.kt:22 | 45 | 氷獄の巨兵 |
| mob/QuestMobContent.kt:23 | 36 | 嵐の司祭 |
| mob/QuestMobContent.kt:24 | 45 | 熔星の番人 |
| mob/QuestMobContent.kt:25 | 54 | 蒼潮の記録者 |
| mob/QuestMobContent.kt:26 | 36 | 星蝕の王 |
| mob/QuestMobContent.kt:27 | 27 | 黒翳竜 |
| mob/QuestMobContent.kt:28 | 27 | 桜牙獣 |
| mob/QuestMobContent.kt:32 | 54 | 花喰いの白蛇 |
| mob/QuestMobContent.kt:33 | 54 | 石灯籠の番人 |
| mob/QuestMobContent.kt:151 | 27 | 横薙ぎ |
| mob/QuestMobContent.kt:154 | 54 | 前方叩きつけ |
| mob/QuestMobContent.kt:169 | 36 | 翼脚薙ぎ |
| mob/QuestMobContent.kt:171 | 45 | 飛びかかり |
| mob/QuestMobContent.kt:173 | 36 | 鱗粉炸裂 |
| mob/QuestMobContent.kt:175 | 45 | 触角の共鳴 |
| mob/QuestMobContent.kt:177 | 36 | 翳の吐息 |
| mob/QuestMobContent.kt:179 | 27 | 鱗粉嵐 |
| mob/QuestMobContent.kt:182 | 36 | 噛みつき |
| mob/QuestMobContent.kt:185 | 45 | しっぽ回転 |
| mob/QuestMobContent.kt:187 | 54 | 角の突き上げ |
| mob/QuestMobContent.kt:189 | 27 | 鱗粉弾 |
| mob/QuestMobContent.kt:192 | 18 | 突進 |
| mob/QuestMobContent.kt:197 | 108 | 翳の大詠唱・殴って止めろ |
| mob/QuestMobContent.kt:199 | 81 | 鱗粉の殻・殻を割れ |
| mob/QuestMobContent.kt:202 | 27 | 尾打ち |
| mob/QuestMobContent.kt:205 | 54 | 振り向き薙ぎ |
| mob/QuestMobContent.kt:207 | 27 | 急降下 |
| mob/QuestMobContent.kt:216 | 90 | 桜爪の叩きつけ・横へ |
| mob/QuestMobContent.kt:218 | 99 | 花尾の振り下ろし・横へ |
| mob/QuestMobContent.kt:220 | 63 | 散華・扇の外へ |
| mob/QuestMobContent.kt:222 | 81 | 花嵐・獣から離れろ |
| mob/QuestMobContent.kt:224 | 90 | 枝角の突き上げ・横へ |
| mob/QuestMobContent.kt:226 | 81 | 花尾の旋回・離れろ |
| mob/QuestMobContent.kt:228 | 99 | 散り際の遠吠え・離れろ |
| mob/QuestMobContent.kt:230 | 153 | 花冠の飛びかかり・着地点から離れろ |
| mob/QuestMobContent.kt:232 | 99 | 花冠落とし・印から出ろ |
| mob/QuestMobContent.kt:234 | 81 | 花吹雪の突進・横へ |
| mob/QuestMobContent.kt:238 | 72 | 踏み込み爪・横へ |
| mob/QuestMobContent.kt:240 | 90 | 花肩の体当たり・横へ |
| mob/QuestMobContent.kt:242 | 81 | 跳ね尾払い・離れろ |
| mob/QuestMobContent.kt:245 | 54 | 桜爪・二の手 |
| mob/QuestMobContent.kt:247 | 45 | 桜爪・止め |
| mob/QuestMobContent.kt:256 | 81 | 花の吐息・扇の外へ |
| mob/QuestMobContent.kt:258 | 63 | 突き噛み・横へ |
| mob/QuestMobContent.kt:260 | 72 | 尾払い・前へ出ろ |
| mob/QuestMobContent.kt:262 | 81 | 締めの輪・内か外へ |
| mob/QuestMobContent.kt:269 | 81 | 踏み鳴らし・離れろ |
| mob/QuestMobContent.kt:271 | 81 | 灯の弾・印から出ろ |
| mob/QuestMobContent.kt:273 | 63 | 倒れ込み・横へ |
| mob/QuestMobContent.kt:275 | 63 | 灯明・柱の陰へ |
| mob/QuestMobContent.kt:282 | 36 | 盾の強打 |
| mob/QuestMobContent.kt:286 | 45 | 裂け目の槍 |
| mob/QuestMobContent.kt:288 | 36 | 拒絶の波 |
| mob/QuestMobContent.kt:294 | 45 | 憤怒の震撃 |
| mob/QuestMobContent.kt:299 | 36 | 処刑の環 |
| mob/QuestMobContent.kt:304 | 45 | 城壁の震動 |
| mob/QuestMobContent.kt:306 | 18 | 鉄槌 |
| mob/QuestMobContent.kt:310 | 45 | 崩壊の刻印 |
| mob/QuestMobContent.kt:312 | 36 | 断絶の光 |
| mob/QuestMobContent.kt:314 | 36 | 拒絶の波 |
| mob/QuestMobContent.kt:320 | 63 | 熔断・対角線へ |
| mob/QuestMobContent.kt:321 | 90 | 火種・足元から離れろ |
| mob/QuestMobContent.kt:324 | 63 | 潮流・横へ回避 |
| mob/QuestMobContent.kt:325 | 54 | 満潮・中心へ |
| mob/QuestMobContent.kt:326 | 45 | 干潮・外へ |
| mob/QuestMobContent.kt:330 | 108 | 星落とし・足元から離れろ |
| mob/QuestMobContent.kt:331 | 72 | 星蝕十字・斜めへ |
| mob/QuestMobContent.kt:334 | 99 | 焼印・赤い床から離れろ |
| mob/QuestMobContent.kt:337 | 90 | 熔炉十字・斜めへ回避 |
| mob/QuestMobContent.kt:342 | 99 | 外周凍結・巨兵の近くへ |
| mob/QuestMobContent.kt:344 | 108 | 中心砕氷・巨兵から離れろ |
| mob/QuestMobContent.kt:349 | 36 | 天雷の道 |
| mob/QuestMobContent.kt:351 | 45 | 落雷の刻印 |
| mob/QuestMobContent.kt:353 | 36 | 嵐の十字 |
| mob/ShadeWyrmGimmicks.kt:31 | 18 | 侵食 |
| questmap/QuestGathering.kt:41 | 27 | 皮剥ぎ |
| questmap/QuestGathering.kt:42 | 54 | 皮剥ぎナイフ |
| questmap/QuestGathering.kt:44 | 36 | 獣の墓場 |
| questmap/QuestGathering.kt:47 | 18 | 獣皮 |
| questmap/QuestGathering.kt:49 | 45 | 上質な獣皮 |
| questmap/QuestGathering.kt:53 | 18 | 伐採 |
| questmap/QuestGathering.kt:54 | 45 | 木こりの斧 |
| questmap/QuestGathering.kt:56 | 36 | 古樹の森 |
| questmap/QuestGathering.kt:59 | 18 | 木材 |
| questmap/QuestGathering.kt:61 | 45 | 古樹の樹脂 |
| questmap/QuestGathering.kt:65 | 18 | 採石 |
| questmap/QuestGathering.kt:66 | 36 | 石工の槌 |
| questmap/QuestGathering.kt:68 | 36 | 露岩地帯 |
| questmap/QuestGathering.kt:71 | 18 | 石材 |
| questmap/QuestGathering.kt:73 | 36 | 共鳴水晶 |
| questmap/QuestGathering.kt:77 | 18 | 採鉱 |
| questmap/QuestGathering.kt:78 | 63 | 鉱夫のツルハシ |
| questmap/QuestGathering.kt:80 | 18 | 鉱脈 |
| questmap/QuestGathering.kt:83 | 18 | 鉱石 |
| questmap/QuestGathering.kt:85 | 36 | 希少宝石 |
| questmap/QuestGathering.kt:89 | 36 | 植物採取 |
| questmap/QuestGathering.kt:90 | 27 | 採集鎌 |
| questmap/QuestGathering.kt:92 | 45 | 薬草群生地 |
| questmap/QuestGathering.kt:95 | 36 | 植物繊維 |
| questmap/QuestGathering.kt:97 | 36 | 発光繊維 |
| questmap/QuestGathering.kt:139 | 18 | 通常 |
| questmap/QuestGathering.kt:140 | 18 | 豊富 |
| questmap/QuestGathering.kt:141 | 18 | 希少 |
| questmap/QuestGathering.kt:154 | 54 | 熟練の手つき |
| questmap/QuestGathering.kt:155 | 113 | 採取時間が4tick短くなる。 |
| questmap/QuestGathering.kt:159 | 27 | 深掘り |
| questmap/QuestGathering.kt:160 | 159 | すべての採取で通常素材が1個増える。 |
| questmap/QuestGathering.kt:165 | 36 | 鋭い感覚 |
| questmap/QuestGathering.kt:166 | 129 | 希少素材の発見率が8%増える。 |
| questmap/QuestGathering.kt:170 | 54 | 幸運の探究者 |
| questmap/QuestGathering.kt:171 | 162 | 希少素材の発見率がさらに12%増える。 |
| questmap/QuestGathering.kt:176 | 18 | 豊穣 |
| questmap/QuestGathering.kt:177 | 218 | 通常素材が2個増えるが、採取時間が8tick長くなる。 |
| questmap/QuestGathering.kt:184 | 18 | 発見 |
| questmap/QuestGathering.kt:185 | 231 | 希少素材の発見率が20%増えるが、通常素材が1個減る。 |
| questmap/QuestMapCustomization.kt:4 | 27 | 生成量 |
| questmap/QuestMapCustomization.kt:5 | 18 | 品質 |
| questmap/QuestMapCustomization.kt:6 | 63 | 密集地域出現率 |
| questmap/QuestMapCustomization.kt:22 | 27 | 全資源 |
| questmap/QuestMapCustomization.kt:24 | 36 | 全採取物 |
| questmap/QuestMapCustomization.kt:26 | 82 | {…}の{…} +{…}% |
| questmap/QuestMapSchematicCatalog.kt:303 | 113 | minecraft:short_grass |
| questmap/QuestMapSchematicCatalog.kt:303 | 105 | minecraft:large_fern |
| questmap/QuestMapSchematicCatalog.kt:303 | 121 | minecraft:crimson_roots |
| questmap/QuestMapSchematicCatalog.kt:304 | 105 | minecraft:moss_block |
| questmap/QuestMapSchematicCatalog.kt:304 | 76 | minecraft:melon |
| questmap/QuestMapSchematicCatalog.kt:304 | 143 | minecraft:nether_wart_block |
| questmap/VerdantRoadQuestService.kt:90 | 108 | この採取物は再生中です。 |
| questmap/VerdantRoadQuestService.kt:96 | 73 | {…}が必要です。 |
| questmap/VerdantRoadQuestService.kt:176 | 216 | 採取系統またはマスタリーノードが見つかりません。 |
| questmap/VerdantRoadQuestService.kt:186 | 116 | {…}: {…}を取得しました。 |
| questmap/VerdantRoadQuestService.kt:188 | 117 | そのノードは取得済みです。 |
| questmap/VerdantRoadQuestService.kt:189 | 127 | 先に{…}を取得してください。 |
| questmap/VerdantRoadQuestService.kt:190 | 204 | 各採取系統で選べるキーストーンは1つだけです。 |
| questmap/VerdantRoadQuestService.kt:191 | 171 | マスタリーツリーポイントが足りません。 |
| questmap/VerdantRoadQuestService.kt:207 | 342 | 素材を受け取れませんでした。インベントリに空きを作って再度採取してください。 |
| questmap/VerdantRoadQuestService.kt:219 | 148 | T{…} {…} +{…} → インベントリ |
| questmap/VerdantRoadQuestService.kt:221 | 103 | {…}マスタリー Lv {…} |
| questmap/VerdantRoadQuestService.kt:262 | 193 | +{…} {…}  •  +{…} {…}マスタリー経験値 |
| questmap/VerdantRoadQuestService.kt:269 | 171 | {…}マスタリーがLv {…}になりました。 |
| questmap/VerdantRoadQuestService.kt:419 | 187 | クエストマップへの転送に失敗しました: {…} |
| questmap/VerdantRoadQuestService.kt:440 | 214 | クエストマップ seed={…} 景観={…} 経路={…}  |
| questmap/VerdantRoadQuestService.kt:441 | 112 | 地形={…} チャンク={…}  |
| questmap/VerdantRoadQuestService.kt:442 | 103 | 候補数={…} 評価={…}  |
| questmap/VerdantRoadQuestService.kt:443 | 114 | 生成={…}ms 転送={…}ms |
| questmap/VerdantRoadQuestService.kt:447 | 297 | 道を進むとボス地点へ到達できます。脇道には採取物や発見があります。 |
| questmap/VerdantRoadQuestService.kt:448 | 276 | 採取: 対応する道具を持ち、対象を右クリック長押ししてください。 |
| questmap/VerdantRoadQuestService.kt:450 | 45 | マップMOD |
| questmap/VerdantRoadQuestService.kt:452 | 28 | ・{…} |
| questmap/VerdantRoadQuestService.kt:459 | 208 | このマップには資源密集地域が{…}か所あります。 |
| questmap/VerdantRoadQuestService.kt:477 | 136 | ProjectSの拠点へ戻りました。 |
| questmap/VerdantRoadQuestService.kt:481 | 142 | 拠点への転送に失敗しました: {…} |
| res/passive-tree.tsv:2 | 45 | 与ダメージ |
| res/passive-tree.tsv:3 | 30 | 最大HP |
| res/passive-tree.tsv:4 | 45 | 与ダメージ |
| res/passive-tree.tsv:5 | 30 | 最大HP |
| res/passive-tree.tsv:6 | 45 | 与ダメージ |
| res/passive-tree.tsv:7 | 30 | 最大HP |
| res/passive-tree.tsv:8 | 45 | 与ダメージ |
| res/passive-tree.tsv:9 | 30 | 最大HP |
| res/passive-tree.tsv:10 | 36 | 原点の力 |
| res/passive-tree.tsv:11 | 27 | 巡る力 |
| res/passive-tree.tsv:12 | 18 | 均衡 |
| res/passive-tree.tsv:13 | 45 | 戦士の起点 |
| res/passive-tree.tsv:14 | 45 | 与ダメージ |
| res/passive-tree.tsv:15 | 45 | 与ダメージ |
| res/passive-tree.tsv:16 | 72 | テンプラーの起点 |
| res/passive-tree.tsv:17 | 45 | 与ダメージ |
| res/passive-tree.tsv:18 | 45 | 与ダメージ |
| res/passive-tree.tsv:19 | 63 | ヒーラーの起点 |
| res/passive-tree.tsv:20 | 45 | 与ダメージ |
| res/passive-tree.tsv:21 | 45 | 与ダメージ |
| res/passive-tree.tsv:22 | 54 | メイジの起点 |
| res/passive-tree.tsv:23 | 45 | 与ダメージ |
| res/passive-tree.tsv:24 | 45 | 与ダメージ |
| res/passive-tree.tsv:25 | 63 | アサシンの起点 |
| res/passive-tree.tsv:26 | 45 | 与ダメージ |
| res/passive-tree.tsv:27 | 45 | 与ダメージ |
| res/passive-tree.tsv:28 | 72 | レンジャーの起点 |
| res/passive-tree.tsv:29 | 45 | 与ダメージ |
| res/passive-tree.tsv:30 | 45 | 与ダメージ |
| res/passive-tree.tsv:31 | 45 | 武人の心得 |
| res/passive-tree.tsv:32 | 54 | 近接ダメージ |
| res/passive-tree.tsv:33 | 36 | 物理防御 |
| res/passive-tree.tsv:34 | 54 | 近接ダメージ |
| res/passive-tree.tsv:35 | 36 | 物理防御 |
| res/passive-tree.tsv:36 | 27 | 祈る盾 |
| res/passive-tree.tsv:37 | 36 | 物理防御 |
| res/passive-tree.tsv:38 | 27 | 回復力 |
| res/passive-tree.tsv:39 | 36 | 物理防御 |
| res/passive-tree.tsv:40 | 27 | 回復力 |
| res/passive-tree.tsv:41 | 36 | 命の術式 |
| res/passive-tree.tsv:42 | 27 | 回復力 |
| res/passive-tree.tsv:43 | 45 | 魔法攻撃力 |
| res/passive-tree.tsv:44 | 27 | 回復力 |
| res/passive-tree.tsv:45 | 45 | 魔法攻撃力 |
| res/passive-tree.tsv:46 | 36 | 呪いの刃 |
| res/passive-tree.tsv:47 | 63 | スキルダメージ |
| res/passive-tree.tsv:48 | 27 | 会心率 |
| res/passive-tree.tsv:49 | 63 | スキルダメージ |
| res/passive-tree.tsv:50 | 27 | 会心率 |
| res/passive-tree.tsv:51 | 36 | 影の狩人 |
| res/passive-tree.tsv:52 | 27 | 会心率 |
| res/passive-tree.tsv:53 | 63 | 投射物ダメージ |
| res/passive-tree.tsv:54 | 27 | 会心率 |
| res/passive-tree.tsv:55 | 63 | 投射物ダメージ |
| res/passive-tree.tsv:56 | 36 | 斬り込み |
| res/passive-tree.tsv:57 | 63 | 投射物ダメージ |
| res/passive-tree.tsv:58 | 54 | 近接ダメージ |
| res/passive-tree.tsv:59 | 63 | 投射物ダメージ |
| res/passive-tree.tsv:60 | 54 | 近接ダメージ |
| res/passive-tree.tsv:61 | 63 | 投射物ダメージ |
| res/passive-tree.tsv:62 | 54 | 近接ダメージ |
| res/passive-tree.tsv:63 | 36 | 武芸の才 |
| res/passive-tree.tsv:64 | 63 | 投射物ダメージ |
| res/passive-tree.tsv:65 | 54 | 近接ダメージ |
| res/passive-tree.tsv:66 | 36 | 戦場の目 |
| res/passive-tree.tsv:67 | 63 | 投射物ダメージ |
| res/passive-tree.tsv:68 | 54 | 近接ダメージ |
| res/passive-tree.tsv:69 | 54 | 斬り込み・改 |
| res/passive-tree.tsv:70 | 54 | 近接ダメージ |
| res/passive-tree.tsv:71 | 63 | 投射物ダメージ |
| res/passive-tree.tsv:72 | 63 | 投射物ダメージ |
| res/passive-tree.tsv:73 | 54 | 武芸の才・改 |
| res/passive-tree.tsv:74 | 63 | 投射物ダメージ |
| res/passive-tree.tsv:75 | 54 | 戦場の目・改 |
| res/passive-tree.tsv:76 | 63 | 投射物ダメージ |
| res/passive-tree.tsv:77 | 54 | 斬り込み・極 |
| res/passive-tree.tsv:78 | 63 | 投射物ダメージ |
| res/passive-tree.tsv:79 | 54 | 近接ダメージ |
| res/passive-tree.tsv:80 | 18 | 極意 |
| res/passive-tree.tsv:81 | 54 | 近接ダメージ |
| res/passive-tree.tsv:82 | 30 | 最大HP |
| res/passive-tree.tsv:83 | 18 | 剛力 |
| res/passive-tree.tsv:84 | 54 | 近接ダメージ |
| res/passive-tree.tsv:85 | 27 | 鋼の体 |
| res/passive-tree.tsv:86 | 45 | 物理攻撃力 |
| res/passive-tree.tsv:87 | 27 | 大振り |
| res/passive-tree.tsv:88 | 54 | 近接ダメージ |
| res/passive-tree.tsv:89 | 18 | 不屈 |
| res/passive-tree.tsv:90 | 45 | 物理攻撃力 |
| res/passive-tree.tsv:91 | 54 | 物理ダメージ |
| res/passive-tree.tsv:92 | 18 | 極意 |
| res/passive-tree.tsv:93 | 54 | 近接ダメージ |
| res/passive-tree.tsv:94 | 36 | 血の渇き |
| res/passive-tree.tsv:95 | 45 | 物理攻撃力 |
| res/passive-tree.tsv:96 | 36 | 重い一撃 |
| res/passive-tree.tsv:97 | 54 | 近接ダメージ |
| res/passive-tree.tsv:98 | 45 | 戦場の心得 |
| res/passive-tree.tsv:99 | 45 | 物理攻撃力 |
| res/passive-tree.tsv:100 | 54 | 物理ダメージ |
| res/passive-tree.tsv:101 | 18 | 極意 |
| res/passive-tree.tsv:102 | 54 | 近接ダメージ |
| res/passive-tree.tsv:103 | 30 | 最大HP |
| res/passive-tree.tsv:104 | 27 | 宿る力 |
| res/passive-tree.tsv:105 | 54 | 近接ダメージ |
| res/passive-tree.tsv:106 | 36 | 剛力・改 |
| res/passive-tree.tsv:107 | 45 | 物理攻撃力 |
| res/passive-tree.tsv:108 | 45 | 鋼の体・改 |
| res/passive-tree.tsv:109 | 54 | 近接ダメージ |
| res/passive-tree.tsv:110 | 45 | 大振り・改 |
| res/passive-tree.tsv:111 | 45 | 物理攻撃力 |
| res/passive-tree.tsv:112 | 54 | 物理ダメージ |
| res/passive-tree.tsv:113 | 18 | 極意 |
| res/passive-tree.tsv:114 | 54 | 近接ダメージ |
| res/passive-tree.tsv:115 | 54 | 近接ダメージ |
| res/passive-tree.tsv:116 | 36 | 物理防御 |
| res/passive-tree.tsv:117 | 36 | 戦う聖者 |
| res/passive-tree.tsv:118 | 54 | 近接ダメージ |
| res/passive-tree.tsv:119 | 54 | 近接ダメージ |
| res/passive-tree.tsv:120 | 45 | 鉄壁の構え |
| res/passive-tree.tsv:121 | 54 | 近接ダメージ |
| res/passive-tree.tsv:122 | 63 | 武人の心得・改 |
| res/passive-tree.tsv:123 | 54 | 近接ダメージ |
| res/passive-tree.tsv:124 | 54 | 戦う聖者・改 |
| res/passive-tree.tsv:125 | 54 | 近接ダメージ |
| res/passive-tree.tsv:126 | 36 | 物理防御 |
| res/passive-tree.tsv:127 | 18 | 極意 |
| res/passive-tree.tsv:128 | 54 | 近接ダメージ |
| res/passive-tree.tsv:129 | 36 | 物理防御 |
| res/passive-tree.tsv:130 | 63 | 鉄壁の構え・改 |
| res/passive-tree.tsv:131 | 54 | 近接ダメージ |
| res/passive-tree.tsv:132 | 36 | 物理防御 |
| res/passive-tree.tsv:133 | 63 | 武人の心得・極 |
| res/passive-tree.tsv:134 | 36 | 物理防御 |
| res/passive-tree.tsv:135 | 54 | 近接ダメージ |
| res/passive-tree.tsv:136 | 36 | 物理防御 |
| res/passive-tree.tsv:137 | 54 | 戦う聖者・極 |
| res/passive-tree.tsv:138 | 36 | 物理防御 |
| res/passive-tree.tsv:139 | 54 | 近接ダメージ |
| res/passive-tree.tsv:140 | 54 | 近接ダメージ |
| res/passive-tree.tsv:141 | 63 | 鉄壁の構え・極 |
| res/passive-tree.tsv:142 | 54 | 近接ダメージ |
| res/passive-tree.tsv:143 | 63 | 武人の心得・極 |
| res/passive-tree.tsv:144 | 54 | 近接ダメージ |
| res/passive-tree.tsv:145 | 54 | 戦う聖者・極 |
| res/passive-tree.tsv:146 | 54 | 近接ダメージ |
| res/passive-tree.tsv:147 | 36 | 物理防御 |
| res/passive-tree.tsv:148 | 18 | 極意 |
| res/passive-tree.tsv:149 | 54 | 近接ダメージ |
| res/passive-tree.tsv:150 | 36 | 物理防御 |
| res/passive-tree.tsv:151 | 63 | 鉄壁の構え・極 |
| res/passive-tree.tsv:152 | 54 | 近接ダメージ |
| res/passive-tree.tsv:153 | 36 | 物理防御 |
| res/passive-tree.tsv:154 | 63 | 武人の心得・極 |
| res/passive-tree.tsv:155 | 36 | 物理防御 |
| res/passive-tree.tsv:156 | 45 | シールド量 |
| res/passive-tree.tsv:157 | 18 | 城壁 |
| res/passive-tree.tsv:158 | 36 | 物理防御 |
| res/passive-tree.tsv:159 | 45 | シールド量 |
| res/passive-tree.tsv:160 | 36 | 誓いの盾 |
| res/passive-tree.tsv:161 | 36 | 物理防御 |
| res/passive-tree.tsv:162 | 45 | 聖なる守り |
| res/passive-tree.tsv:163 | 36 | 魔法防御 |
| res/passive-tree.tsv:164 | 36 | 不動の心 |
| res/passive-tree.tsv:165 | 36 | 物理防御 |
| res/passive-tree.tsv:166 | 36 | 守りの光 |
| res/passive-tree.tsv:167 | 36 | 魔法防御 |
| res/passive-tree.tsv:168 | 30 | 最大HP |
| res/passive-tree.tsv:169 | 18 | 極意 |
| res/passive-tree.tsv:170 | 30 | 最大HP |
| res/passive-tree.tsv:171 | 36 | 物理防御 |
| res/passive-tree.tsv:172 | 18 | 堅牢 |
| res/passive-tree.tsv:173 | 36 | 魔法防御 |
| res/passive-tree.tsv:174 | 36 | 裁きの槍 |
| res/passive-tree.tsv:175 | 36 | 物理防御 |
| res/passive-tree.tsv:176 | 54 | 揺るがぬ誓い |
| res/passive-tree.tsv:177 | 36 | 魔法防御 |
| res/passive-tree.tsv:178 | 30 | 最大HP |
| res/passive-tree.tsv:179 | 18 | 極意 |
| res/passive-tree.tsv:180 | 36 | 物理防御 |
| res/passive-tree.tsv:181 | 45 | シールド量 |
| res/passive-tree.tsv:182 | 36 | 城壁・改 |
| res/passive-tree.tsv:183 | 36 | 物理防御 |
| res/passive-tree.tsv:184 | 27 | 回復力 |
| res/passive-tree.tsv:185 | 54 | 聖なる守り手 |
| res/passive-tree.tsv:186 | 36 | 物理防御 |
| res/passive-tree.tsv:187 | 27 | 回復力 |
| res/passive-tree.tsv:188 | 54 | 揺るがぬ祈り |
| res/passive-tree.tsv:189 | 36 | 物理防御 |
| res/passive-tree.tsv:190 | 27 | 回復力 |
| res/passive-tree.tsv:191 | 45 | 祈る盾・改 |
| res/passive-tree.tsv:192 | 27 | 回復力 |
| res/passive-tree.tsv:193 | 27 | 回復力 |
| res/passive-tree.tsv:194 | 36 | 物理防御 |
| res/passive-tree.tsv:195 | 72 | 聖なる守り手・改 |
| res/passive-tree.tsv:196 | 36 | 物理防御 |
| res/passive-tree.tsv:197 | 72 | 揺るがぬ祈り・改 |
| res/passive-tree.tsv:198 | 36 | 物理防御 |
| res/passive-tree.tsv:199 | 45 | 祈る盾・極 |
| res/passive-tree.tsv:200 | 36 | 物理防御 |
| res/passive-tree.tsv:201 | 27 | 回復力 |
| res/passive-tree.tsv:202 | 18 | 極意 |
| res/passive-tree.tsv:203 | 36 | 物理防御 |
| res/passive-tree.tsv:204 | 27 | 回復力 |
| res/passive-tree.tsv:205 | 72 | 聖なる守り手・極 |
| res/passive-tree.tsv:206 | 36 | 物理防御 |
| res/passive-tree.tsv:207 | 72 | 揺るがぬ祈り・極 |
| res/passive-tree.tsv:208 | 36 | 物理防御 |
| res/passive-tree.tsv:209 | 45 | 祈る盾・極 |
| res/passive-tree.tsv:210 | 36 | 物理防御 |
| res/passive-tree.tsv:211 | 72 | 聖なる守り手・極 |
| res/passive-tree.tsv:212 | 36 | 物理防御 |
| res/passive-tree.tsv:213 | 27 | 回復力 |
| res/passive-tree.tsv:214 | 18 | 極意 |
| res/passive-tree.tsv:215 | 36 | 物理防御 |
| res/passive-tree.tsv:216 | 27 | 回復力 |
| res/passive-tree.tsv:217 | 72 | 揺るがぬ祈り・極 |
| res/passive-tree.tsv:218 | 36 | 物理防御 |
| res/passive-tree.tsv:219 | 27 | 回復力 |
| res/passive-tree.tsv:220 | 45 | 祈る盾・極 |
| res/passive-tree.tsv:221 | 27 | 回復力 |
| res/passive-tree.tsv:222 | 27 | 回復力 |
| res/passive-tree.tsv:223 | 27 | 回復力 |
| res/passive-tree.tsv:224 | 36 | 与回復量 |
| res/passive-tree.tsv:225 | 18 | 慈雨 |
| res/passive-tree.tsv:226 | 36 | 与回復量 |
| res/passive-tree.tsv:227 | 27 | 回復力 |
| res/passive-tree.tsv:228 | 36 | 祈りの泉 |
| res/passive-tree.tsv:229 | 54 | マナ回復速度 |
| res/passive-tree.tsv:230 | 27 | 命の灯 |
| res/passive-tree.tsv:231 | 27 | 回復力 |
| res/passive-tree.tsv:232 | 36 | 巡る祈り |
| res/passive-tree.tsv:233 | 54 | マナ回復速度 |
| res/passive-tree.tsv:234 | 36 | 魔法防御 |
| res/passive-tree.tsv:235 | 18 | 極意 |
| res/passive-tree.tsv:236 | 27 | 回復力 |
| res/passive-tree.tsv:237 | 36 | 与回復量 |
| res/passive-tree.tsv:238 | 45 | 癒しの術理 |
| res/passive-tree.tsv:239 | 27 | 回復力 |
| res/passive-tree.tsv:240 | 36 | 光の歩み |
| res/passive-tree.tsv:241 | 54 | マナ回復速度 |
| res/passive-tree.tsv:242 | 36 | 救いの手 |
| res/passive-tree.tsv:243 | 27 | 回復力 |
| res/passive-tree.tsv:244 | 36 | 聖なる泉 |
| res/passive-tree.tsv:245 | 54 | マナ回復速度 |
| res/passive-tree.tsv:246 | 36 | 魔法防御 |
| res/passive-tree.tsv:247 | 18 | 極意 |
| res/passive-tree.tsv:248 | 27 | 回復力 |
| res/passive-tree.tsv:249 | 27 | 回復力 |
| res/passive-tree.tsv:250 | 45 | 魔法攻撃力 |
| res/passive-tree.tsv:251 | 45 | 静かな水面 |
| res/passive-tree.tsv:252 | 27 | 回復力 |
| res/passive-tree.tsv:253 | 27 | 回復力 |
| res/passive-tree.tsv:254 | 27 | 回復力 |
| res/passive-tree.tsv:255 | 36 | 雨の詠唱 |
| res/passive-tree.tsv:256 | 27 | 回復力 |
| res/passive-tree.tsv:257 | 54 | 命の術式・改 |
| res/passive-tree.tsv:258 | 27 | 回復力 |
| res/passive-tree.tsv:259 | 63 | 静かな水面・改 |
| res/passive-tree.tsv:260 | 27 | 回復力 |
| res/passive-tree.tsv:261 | 45 | 魔法攻撃力 |
| res/passive-tree.tsv:262 | 18 | 極意 |
| res/passive-tree.tsv:263 | 27 | 回復力 |
| res/passive-tree.tsv:264 | 45 | 魔法攻撃力 |
| res/passive-tree.tsv:265 | 54 | 雨の詠唱・改 |
| res/passive-tree.tsv:266 | 27 | 回復力 |
| res/passive-tree.tsv:267 | 45 | 魔法攻撃力 |
| res/passive-tree.tsv:268 | 54 | 命の術式・極 |
| res/passive-tree.tsv:269 | 27 | 回復力 |
| res/passive-tree.tsv:270 | 45 | 魔法攻撃力 |
| res/passive-tree.tsv:271 | 63 | 静かな水面・極 |
| res/passive-tree.tsv:272 | 27 | 回復力 |
| res/passive-tree.tsv:273 | 45 | 魔法攻撃力 |
| res/passive-tree.tsv:274 | 54 | 雨の詠唱・極 |
| res/passive-tree.tsv:275 | 45 | 魔法攻撃力 |
| res/passive-tree.tsv:276 | 27 | 回復力 |
| res/passive-tree.tsv:277 | 27 | 回復力 |
| res/passive-tree.tsv:278 | 54 | 命の術式・極 |
| res/passive-tree.tsv:279 | 27 | 回復力 |
| res/passive-tree.tsv:280 | 63 | 静かな水面・極 |
| res/passive-tree.tsv:281 | 27 | 回復力 |
| res/passive-tree.tsv:282 | 54 | 雨の詠唱・極 |
| res/passive-tree.tsv:283 | 27 | 回復力 |
| res/passive-tree.tsv:284 | 45 | 魔法攻撃力 |
| res/passive-tree.tsv:285 | 18 | 極意 |
| res/passive-tree.tsv:286 | 27 | 回復力 |
| res/passive-tree.tsv:287 | 45 | 魔法攻撃力 |
| res/passive-tree.tsv:288 | 54 | 命の術式・極 |
| res/passive-tree.tsv:289 | 45 | 魔法攻撃力 |
| res/passive-tree.tsv:290 | 54 | 魔法ダメージ |
| res/passive-tree.tsv:291 | 36 | 深淵の知 |
| res/passive-tree.tsv:292 | 45 | 魔法攻撃力 |
| res/passive-tree.tsv:293 | 54 | 魔法ダメージ |
| res/passive-tree.tsv:294 | 36 | 術の急所 |
| res/passive-tree.tsv:295 | 45 | 魔法攻撃力 |
| res/passive-tree.tsv:296 | 45 | 魔力の奔流 |
| res/passive-tree.tsv:297 | 63 | スキルダメージ |
| res/passive-tree.tsv:298 | 36 | 貫く魔弾 |
| res/passive-tree.tsv:299 | 45 | 魔法攻撃力 |
| res/passive-tree.tsv:300 | 45 | 燃え残る術 |
| res/passive-tree.tsv:301 | 63 | スキルダメージ |
| res/passive-tree.tsv:302 | 27 | 会心率 |
| res/passive-tree.tsv:303 | 18 | 極意 |
| res/passive-tree.tsv:304 | 45 | 魔法攻撃力 |
| res/passive-tree.tsv:305 | 45 | 魔法攻撃力 |
| res/passive-tree.tsv:306 | 45 | 早口の詠唱 |
| res/passive-tree.tsv:307 | 63 | スキルダメージ |
| res/passive-tree.tsv:308 | 45 | 魔弾の奥義 |
| res/passive-tree.tsv:309 | 45 | 魔法攻撃力 |
| res/passive-tree.tsv:310 | 36 | 元素の理 |
| res/passive-tree.tsv:311 | 63 | スキルダメージ |
| res/passive-tree.tsv:312 | 27 | 会心率 |
| res/passive-tree.tsv:313 | 18 | 極意 |
| res/passive-tree.tsv:314 | 45 | 魔法攻撃力 |
| res/passive-tree.tsv:315 | 54 | 深淵の知・改 |
| res/passive-tree.tsv:316 | 63 | スキルダメージ |
| res/passive-tree.tsv:317 | 54 | 術の急所・改 |
| res/passive-tree.tsv:318 | 45 | 魔法攻撃力 |
| res/passive-tree.tsv:319 | 63 | 魔力の奔流・改 |
| res/passive-tree.tsv:320 | 63 | スキルダメージ |
| res/passive-tree.tsv:321 | 27 | 会心率 |
| res/passive-tree.tsv:322 | 18 | 極意 |
| res/passive-tree.tsv:323 | 63 | スキルダメージ |
| res/passive-tree.tsv:324 | 27 | 会心率 |
| res/passive-tree.tsv:325 | 36 | 影の術式 |
| res/passive-tree.tsv:326 | 63 | スキルダメージ |
| res/passive-tree.tsv:327 | 36 | 闇の詠唱 |
| res/passive-tree.tsv:328 | 63 | スキルダメージ |
| res/passive-tree.tsv:329 | 54 | 呪いの刃・改 |
| res/passive-tree.tsv:330 | 63 | スキルダメージ |
| res/passive-tree.tsv:331 | 54 | 影の術式・改 |
| res/passive-tree.tsv:332 | 63 | スキルダメージ |
| res/passive-tree.tsv:333 | 27 | 会心率 |
| res/passive-tree.tsv:334 | 18 | 極意 |
| res/passive-tree.tsv:335 | 63 | スキルダメージ |
| res/passive-tree.tsv:336 | 27 | 会心率 |
| res/passive-tree.tsv:337 | 54 | 闇の詠唱・改 |
| res/passive-tree.tsv:338 | 63 | スキルダメージ |
| res/passive-tree.tsv:339 | 27 | 会心率 |
| res/passive-tree.tsv:340 | 54 | 呪いの刃・極 |
| res/passive-tree.tsv:341 | 63 | スキルダメージ |
| res/passive-tree.tsv:342 | 27 | 会心率 |
| res/passive-tree.tsv:343 | 63 | スキルダメージ |
| res/passive-tree.tsv:344 | 27 | 会心率 |
| res/passive-tree.tsv:345 | 54 | 影の術式・極 |
| res/passive-tree.tsv:346 | 27 | 会心率 |
| res/passive-tree.tsv:347 | 63 | スキルダメージ |
| res/passive-tree.tsv:348 | 54 | 闇の詠唱・極 |
| res/passive-tree.tsv:349 | 63 | スキルダメージ |
| res/passive-tree.tsv:350 | 54 | 呪いの刃・極 |
| res/passive-tree.tsv:351 | 63 | スキルダメージ |
| res/passive-tree.tsv:352 | 54 | 影の術式・極 |
| res/passive-tree.tsv:353 | 63 | スキルダメージ |
| res/passive-tree.tsv:354 | 27 | 会心率 |
| res/passive-tree.tsv:355 | 18 | 極意 |
| res/passive-tree.tsv:356 | 63 | スキルダメージ |
| res/passive-tree.tsv:357 | 27 | 会心率 |
| res/passive-tree.tsv:358 | 54 | 闇の詠唱・極 |
| res/passive-tree.tsv:359 | 63 | スキルダメージ |
| res/passive-tree.tsv:360 | 27 | 会心率 |
| res/passive-tree.tsv:361 | 54 | 呪いの刃・極 |
| res/passive-tree.tsv:362 | 27 | 会心率 |
| res/passive-tree.tsv:363 | 27 | 会心率 |
| res/passive-tree.tsv:364 | 36 | 移動速度 |
| res/passive-tree.tsv:365 | 36 | 急所狙い |
| res/passive-tree.tsv:366 | 36 | 移動速度 |
| res/passive-tree.tsv:367 | 27 | 会心率 |
| res/passive-tree.tsv:368 | 27 | 影の足 |
| res/passive-tree.tsv:369 | 72 | ライフスティール |
| res/passive-tree.tsv:370 | 27 | 刃の嵐 |
| res/passive-tree.tsv:371 | 27 | 会心率 |
| res/passive-tree.tsv:372 | 27 | 毒の刃 |
| res/passive-tree.tsv:373 | 72 | ライフスティール |
| res/passive-tree.tsv:374 | 72 | 通常攻撃ダメージ |
| res/passive-tree.tsv:375 | 18 | 極意 |
| res/passive-tree.tsv:376 | 27 | 会心率 |
| res/passive-tree.tsv:377 | 36 | 一瞬の隙 |
| res/passive-tree.tsv:378 | 72 | ライフスティール |
| res/passive-tree.tsv:379 | 27 | 夜の衣 |
| res/passive-tree.tsv:380 | 27 | 会心率 |
| res/passive-tree.tsv:381 | 45 | 血を吸う刃 |
| res/passive-tree.tsv:382 | 72 | ライフスティール |
| res/passive-tree.tsv:383 | 72 | 通常攻撃ダメージ |
| res/passive-tree.tsv:384 | 18 | 極意 |
| res/passive-tree.tsv:385 | 27 | 会心率 |
| res/passive-tree.tsv:386 | 45 | 処刑の作法 |
| res/passive-tree.tsv:387 | 72 | ライフスティール |
| res/passive-tree.tsv:388 | 54 | 急所狙い・改 |
| res/passive-tree.tsv:389 | 27 | 会心率 |
| res/passive-tree.tsv:390 | 45 | 影の足・改 |
| res/passive-tree.tsv:391 | 72 | ライフスティール |
| res/passive-tree.tsv:392 | 72 | 通常攻撃ダメージ |
| res/passive-tree.tsv:393 | 18 | 極意 |
| res/passive-tree.tsv:394 | 27 | 会心率 |
| res/passive-tree.tsv:395 | 36 | 移動速度 |
| res/passive-tree.tsv:396 | 45 | 刃の嵐・改 |
| res/passive-tree.tsv:397 | 27 | 会心率 |
| res/passive-tree.tsv:398 | 63 | 投射物ダメージ |
| res/passive-tree.tsv:399 | 36 | 属性の矢 |
| res/passive-tree.tsv:400 | 27 | 会心率 |
| res/passive-tree.tsv:401 | 63 | 投射物ダメージ |
| res/passive-tree.tsv:402 | 36 | 素早い手 |
| res/passive-tree.tsv:403 | 27 | 会心率 |
| res/passive-tree.tsv:404 | 27 | 会心率 |
| res/passive-tree.tsv:405 | 27 | 会心率 |
| res/passive-tree.tsv:406 | 54 | 影の狩人・改 |
| res/passive-tree.tsv:407 | 27 | 会心率 |
| res/passive-tree.tsv:408 | 54 | 属性の矢・改 |
| res/passive-tree.tsv:409 | 27 | 会心率 |
| res/passive-tree.tsv:410 | 54 | 素早い手・改 |
| res/passive-tree.tsv:411 | 27 | 会心率 |
| res/passive-tree.tsv:412 | 63 | 投射物ダメージ |
| res/passive-tree.tsv:413 | 18 | 極意 |
| res/passive-tree.tsv:414 | 27 | 会心率 |
| res/passive-tree.tsv:415 | 63 | 投射物ダメージ |
| res/passive-tree.tsv:416 | 54 | 影の狩人・極 |
| res/passive-tree.tsv:417 | 27 | 会心率 |
| res/passive-tree.tsv:418 | 63 | 投射物ダメージ |
| res/passive-tree.tsv:419 | 54 | 属性の矢・極 |
| res/passive-tree.tsv:420 | 27 | 会心率 |
| res/passive-tree.tsv:421 | 63 | 投射物ダメージ |
| res/passive-tree.tsv:422 | 54 | 素早い手・極 |
| res/passive-tree.tsv:423 | 27 | 会心率 |
| res/passive-tree.tsv:424 | 63 | 投射物ダメージ |
| res/passive-tree.tsv:425 | 54 | 影の狩人・極 |
| res/passive-tree.tsv:426 | 27 | 会心率 |
| res/passive-tree.tsv:427 | 63 | 投射物ダメージ |
| res/passive-tree.tsv:428 | 27 | 会心率 |
| res/passive-tree.tsv:429 | 54 | 属性の矢・極 |
| res/passive-tree.tsv:430 | 27 | 会心率 |
| res/passive-tree.tsv:431 | 54 | 素早い手・極 |
| res/passive-tree.tsv:432 | 27 | 会心率 |
| res/passive-tree.tsv:433 | 54 | 影の狩人・極 |
| res/passive-tree.tsv:434 | 27 | 会心率 |
| res/passive-tree.tsv:435 | 63 | 投射物ダメージ |
| res/passive-tree.tsv:436 | 18 | 極意 |
| res/passive-tree.tsv:437 | 63 | 投射物ダメージ |
| res/passive-tree.tsv:438 | 36 | 攻撃速度 |
| res/passive-tree.tsv:439 | 27 | 鷹の目 |
| res/passive-tree.tsv:440 | 63 | 投射物ダメージ |
| res/passive-tree.tsv:441 | 18 | 速射 |
| res/passive-tree.tsv:442 | 36 | 会心倍率 |
| res/passive-tree.tsv:443 | 45 | 狩人の構え |
| res/passive-tree.tsv:444 | 63 | 投射物ダメージ |
| res/passive-tree.tsv:445 | 27 | 風読み |
| res/passive-tree.tsv:446 | 36 | 会心倍率 |
| res/passive-tree.tsv:447 | 36 | 移動速度 |
| res/passive-tree.tsv:448 | 18 | 極意 |
| res/passive-tree.tsv:449 | 63 | 投射物ダメージ |
| res/passive-tree.tsv:450 | 36 | 急所射ち |
| res/passive-tree.tsv:451 | 36 | 会心倍率 |
| res/passive-tree.tsv:452 | 36 | 連なる矢 |
| res/passive-tree.tsv:453 | 63 | 投射物ダメージ |
| res/passive-tree.tsv:454 | 27 | 遠い的 |
| res/passive-tree.tsv:455 | 36 | 会心倍率 |
| res/passive-tree.tsv:456 | 36 | 移動速度 |
| res/passive-tree.tsv:457 | 18 | 極意 |
| res/passive-tree.tsv:458 | 63 | 投射物ダメージ |
| res/passive-tree.tsv:459 | 36 | 攻撃速度 |
| res/passive-tree.tsv:460 | 36 | 野の知恵 |
| res/passive-tree.tsv:461 | 63 | 投射物ダメージ |
| res/passive-tree.tsv:462 | 45 | 鷹の目・改 |
| res/passive-tree.tsv:463 | 36 | 会心倍率 |
| res/passive-tree.tsv:464 | 36 | 速射・改 |
| res/passive-tree.tsv:465 | 63 | 投射物ダメージ |
| res/passive-tree.tsv:466 | 63 | 狩人の構え・改 |
| res/passive-tree.tsv:467 | 36 | 会心倍率 |
| res/passive-tree.tsv:468 | 36 | 移動速度 |
| res/passive-tree.tsv:469 | 18 | 極意 |
| res/passive-tree.tsv:470 | 36 | 攻撃速度 |
| res/passive-tree.tsv:471 | 63 | 投射物ダメージ |
| res/passive-tree.tsv:472 | 54 | 近接ダメージ |
| res/passive-tree.tsv:473 | 54 | 武芸の才・極 |
| res/passive-tree.tsv:474 | 54 | 近接ダメージ |
| res/passive-tree.tsv:475 | 63 | 投射物ダメージ |
| res/passive-tree.tsv:476 | 54 | 戦場の目・極 |
| res/passive-tree.tsv:477 | 63 | 投射物ダメージ |
| res/passive-tree.tsv:478 | 54 | 斬り込み・極 |
| res/passive-tree.tsv:479 | 63 | 投射物ダメージ |
| res/passive-tree.tsv:480 | 54 | 武芸の才・極 |
| res/passive-tree.tsv:481 | 63 | 投射物ダメージ |
| res/passive-tree.tsv:482 | 54 | 近接ダメージ |
| res/passive-tree.tsv:483 | 18 | 極意 |
| res/passive-tree.tsv:484 | 63 | 投射物ダメージ |
| res/passive-tree.tsv:485 | 54 | 近接ダメージ |
| res/passive-tree.tsv:486 | 54 | 戦場の目・極 |
| res/passive-tree.tsv:487 | 63 | 投射物ダメージ |
| res/passive-tree.tsv:488 | 54 | 近接ダメージ |
| res/passive-tree.tsv:489 | 54 | 斬り込み・極 |
| res/passive-tree.tsv:490 | 54 | 近接ダメージ |
| res/passive-tree.tsv:491 | 54 | 近接ダメージ |
| res/passive-tree.tsv:492 | 54 | 近接ダメージ |
| res/passive-tree.tsv:493 | 30 | 最大HP |
| res/passive-tree.tsv:494 | 54 | 近接ダメージ |
| res/passive-tree.tsv:495 | 36 | 物理防御 |
| res/passive-tree.tsv:496 | 45 | シールド量 |
| res/passive-tree.tsv:497 | 36 | 物理防御 |
| res/passive-tree.tsv:498 | 36 | 物理防御 |
| res/passive-tree.tsv:499 | 45 | シールド量 |
| res/passive-tree.tsv:500 | 27 | 回復力 |
| res/passive-tree.tsv:501 | 27 | 回復力 |
| res/passive-tree.tsv:502 | 27 | 回復力 |
| res/passive-tree.tsv:503 | 45 | 魔法攻撃力 |
| res/passive-tree.tsv:504 | 54 | 魔法ダメージ |
| res/passive-tree.tsv:505 | 45 | 魔法攻撃力 |
| res/passive-tree.tsv:506 | 54 | 魔法ダメージ |
| res/passive-tree.tsv:507 | 27 | 会心率 |
| res/passive-tree.tsv:508 | 27 | 会心率 |
| res/passive-tree.tsv:509 | 36 | 移動速度 |
| res/passive-tree.tsv:510 | 27 | 会心率 |
| res/passive-tree.tsv:511 | 63 | 投射物ダメージ |
| res/passive-tree.tsv:512 | 36 | 攻撃速度 |
| res/passive-tree.tsv:513 | 63 | 投射物ダメージ |
| res/passive-tree.tsv:514 | 36 | 攻撃速度 |
| res/passive-tree.tsv:515 | 63 | 投射物ダメージ |
| res/passive-tree.tsv:516 | 63 | 投射物ダメージ |
| res/passive-tree.tsv:517 | 63 | 投射物ダメージ |
| res/passive-tree.tsv:518 | 54 | 近接ダメージ |
| res/passive-tree.tsv:519 | 54 | 近接ダメージ |
| res/passive-tree.tsv:520 | 54 | 近接ダメージ |
| res/passive-tree.tsv:521 | 54 | 近接ダメージ |
| res/passive-tree.tsv:522 | 36 | 物理防御 |
| res/passive-tree.tsv:523 | 36 | 物理防御 |
| res/passive-tree.tsv:524 | 36 | 物理防御 |
| res/passive-tree.tsv:525 | 36 | 物理防御 |
| res/passive-tree.tsv:526 | 36 | 物理防御 |
| res/passive-tree.tsv:527 | 36 | 物理防御 |
| res/passive-tree.tsv:528 | 27 | 回復力 |
| res/passive-tree.tsv:529 | 27 | 回復力 |
| res/passive-tree.tsv:530 | 27 | 回復力 |
| res/passive-tree.tsv:531 | 27 | 回復力 |
| res/passive-tree.tsv:532 | 27 | 回復力 |
| res/passive-tree.tsv:533 | 27 | 回復力 |
| res/passive-tree.tsv:534 | 45 | 魔法攻撃力 |
| res/passive-tree.tsv:535 | 45 | 魔法攻撃力 |
| res/passive-tree.tsv:536 | 63 | スキルダメージ |
| res/passive-tree.tsv:537 | 63 | スキルダメージ |
| res/passive-tree.tsv:538 | 63 | スキルダメージ |
| res/passive-tree.tsv:539 | 63 | スキルダメージ |
| res/passive-tree.tsv:540 | 63 | スキルダメージ |
| res/passive-tree.tsv:541 | 27 | 会心率 |
| res/passive-tree.tsv:542 | 27 | 会心率 |
| res/passive-tree.tsv:543 | 27 | 会心率 |
| res/passive-tree.tsv:544 | 27 | 会心率 |
| res/passive-tree.tsv:545 | 27 | 会心率 |
| res/passive-tree.tsv:546 | 27 | 会心率 |
| res/passive-tree.tsv:547 | 27 | 会心率 |
| res/passive-tree.tsv:548 | 27 | 会心率 |
| res/passive-tree.tsv:549 | 27 | 会心率 |
| res/passive-tree.tsv:550 | 63 | 投射物ダメージ |
| res/passive-tree.tsv:551 | 63 | 投射物ダメージ |
| res/passive-tree.tsv:552 | 63 | 投射物ダメージ |
| res/passive-tree.tsv:553 | 54 | 近接ダメージ |
| res/passive-tree.tsv:554 | 30 | 最大HP |
| res/passive-tree.tsv:555 | 54 | 近接ダメージ |
| res/passive-tree.tsv:556 | 30 | 最大HP |
| res/passive-tree.tsv:557 | 45 | 物理攻撃力 |
| res/passive-tree.tsv:558 | 54 | 近接ダメージ |
| res/passive-tree.tsv:559 | 54 | 近接ダメージ |
| res/passive-tree.tsv:560 | 30 | 最大HP |
| res/passive-tree.tsv:561 | 54 | 近接ダメージ |
| res/passive-tree.tsv:562 | 30 | 最大HP |
| res/passive-tree.tsv:563 | 45 | 物理攻撃力 |
| res/passive-tree.tsv:564 | 54 | 物理ダメージ |
| res/passive-tree.tsv:565 | 54 | 近接ダメージ |
| res/passive-tree.tsv:566 | 54 | 近接ダメージ |
| res/passive-tree.tsv:567 | 54 | 近接ダメージ |
| res/passive-tree.tsv:568 | 36 | 物理防御 |
| res/passive-tree.tsv:569 | 54 | 近接ダメージ |
| res/passive-tree.tsv:570 | 36 | 物理防御 |
| res/passive-tree.tsv:571 | 54 | 近接ダメージ |
| res/passive-tree.tsv:572 | 36 | 物理防御 |
| res/passive-tree.tsv:573 | 36 | 物理防御 |
| res/passive-tree.tsv:574 | 45 | シールド量 |
| res/passive-tree.tsv:575 | 36 | 物理防御 |
| res/passive-tree.tsv:576 | 45 | シールド量 |
| res/passive-tree.tsv:577 | 36 | 物理防御 |
| res/passive-tree.tsv:578 | 45 | シールド量 |
| res/passive-tree.tsv:579 | 36 | 魔法防御 |
| res/passive-tree.tsv:580 | 30 | 最大HP |
| res/passive-tree.tsv:581 | 36 | 物理防御 |
| res/passive-tree.tsv:582 | 36 | 物理防御 |
| res/passive-tree.tsv:583 | 45 | シールド量 |
| res/passive-tree.tsv:584 | 36 | 物理防御 |
| res/passive-tree.tsv:585 | 45 | シールド量 |
| res/passive-tree.tsv:586 | 36 | 魔法防御 |
| res/passive-tree.tsv:587 | 36 | 物理防御 |
| res/passive-tree.tsv:588 | 27 | 回復力 |
| res/passive-tree.tsv:589 | 36 | 物理防御 |
| res/passive-tree.tsv:590 | 36 | 物理防御 |
| res/passive-tree.tsv:591 | 27 | 回復力 |
| res/passive-tree.tsv:592 | 36 | 物理防御 |
| res/passive-tree.tsv:593 | 27 | 回復力 |
| res/passive-tree.tsv:594 | 27 | 回復力 |
| res/passive-tree.tsv:595 | 36 | 与回復量 |
| res/passive-tree.tsv:596 | 27 | 回復力 |
| res/passive-tree.tsv:597 | 36 | 与回復量 |
| res/passive-tree.tsv:598 | 54 | マナ回復速度 |
| res/passive-tree.tsv:599 | 27 | 回復力 |
| res/passive-tree.tsv:600 | 36 | 与回復量 |
| res/passive-tree.tsv:601 | 27 | 回復力 |
| res/passive-tree.tsv:602 | 36 | 与回復量 |
| res/passive-tree.tsv:603 | 27 | 回復力 |
| res/passive-tree.tsv:604 | 36 | 与回復量 |
| res/passive-tree.tsv:605 | 54 | マナ回復速度 |
| res/passive-tree.tsv:606 | 36 | 魔法防御 |
| res/passive-tree.tsv:607 | 27 | 回復力 |
| res/passive-tree.tsv:608 | 27 | 回復力 |
| res/passive-tree.tsv:609 | 45 | 魔法攻撃力 |
| res/passive-tree.tsv:610 | 27 | 回復力 |
| res/passive-tree.tsv:611 | 27 | 回復力 |
| res/passive-tree.tsv:612 | 45 | 魔法攻撃力 |
| res/passive-tree.tsv:613 | 27 | 回復力 |
| res/passive-tree.tsv:614 | 45 | 魔法攻撃力 |
| res/passive-tree.tsv:615 | 45 | 魔法攻撃力 |
| res/passive-tree.tsv:616 | 54 | 魔法ダメージ |
| res/passive-tree.tsv:617 | 63 | スキルダメージ |
| res/passive-tree.tsv:618 | 27 | 会心率 |
| res/passive-tree.tsv:619 | 45 | 魔法攻撃力 |
| res/passive-tree.tsv:620 | 54 | 魔法ダメージ |
| res/passive-tree.tsv:621 | 63 | スキルダメージ |
| res/passive-tree.tsv:622 | 27 | 会心率 |
| res/passive-tree.tsv:623 | 45 | 魔法攻撃力 |
| res/passive-tree.tsv:624 | 45 | 魔法攻撃力 |
| res/passive-tree.tsv:625 | 45 | 魔法攻撃力 |
| res/passive-tree.tsv:626 | 54 | 魔法ダメージ |
| res/passive-tree.tsv:627 | 63 | スキルダメージ |
| res/passive-tree.tsv:628 | 63 | スキルダメージ |
| res/passive-tree.tsv:629 | 27 | 会心率 |
| res/passive-tree.tsv:630 | 63 | スキルダメージ |
| res/passive-tree.tsv:631 | 63 | スキルダメージ |
| res/passive-tree.tsv:632 | 27 | 会心率 |
| res/passive-tree.tsv:633 | 63 | スキルダメージ |
| res/passive-tree.tsv:634 | 27 | 会心率 |
| res/passive-tree.tsv:635 | 63 | スキルダメージ |
| res/passive-tree.tsv:636 | 27 | 会心率 |
| res/passive-tree.tsv:637 | 27 | 会心率 |
| res/passive-tree.tsv:638 | 27 | 会心率 |
| res/passive-tree.tsv:639 | 27 | 会心率 |
| res/passive-tree.tsv:640 | 36 | 移動速度 |
| res/passive-tree.tsv:641 | 72 | ライフスティール |
| res/passive-tree.tsv:642 | 27 | 会心率 |
| res/passive-tree.tsv:643 | 36 | 移動速度 |
| res/passive-tree.tsv:644 | 27 | 会心率 |
| res/passive-tree.tsv:645 | 36 | 移動速度 |
| res/passive-tree.tsv:646 | 27 | 会心率 |
| res/passive-tree.tsv:647 | 36 | 移動速度 |
| res/passive-tree.tsv:648 | 72 | ライフスティール |
| res/passive-tree.tsv:649 | 72 | 通常攻撃ダメージ |
| res/passive-tree.tsv:650 | 27 | 会心率 |
| res/passive-tree.tsv:651 | 63 | 投射物ダメージ |
| res/passive-tree.tsv:652 | 27 | 会心率 |
| res/passive-tree.tsv:653 | 63 | 投射物ダメージ |
| res/passive-tree.tsv:654 | 27 | 会心率 |
| res/passive-tree.tsv:655 | 63 | 投射物ダメージ |
| res/passive-tree.tsv:656 | 63 | 投射物ダメージ |
| res/passive-tree.tsv:657 | 63 | 投射物ダメージ |
| res/passive-tree.tsv:658 | 36 | 攻撃速度 |
| res/passive-tree.tsv:659 | 36 | 会心倍率 |
| res/passive-tree.tsv:660 | 63 | 投射物ダメージ |
| res/passive-tree.tsv:661 | 36 | 攻撃速度 |
| res/passive-tree.tsv:662 | 36 | 会心倍率 |
| res/passive-tree.tsv:663 | 36 | 移動速度 |
| res/passive-tree.tsv:664 | 63 | 投射物ダメージ |
| res/passive-tree.tsv:665 | 63 | 投射物ダメージ |
| res/passive-tree.tsv:666 | 63 | 投射物ダメージ |
| res/passive-tree.tsv:667 | 36 | 攻撃速度 |
| res/passive-tree.tsv:668 | 63 | 投射物ダメージ |
| res/passive-tree.tsv:669 | 54 | 近接ダメージ |
| res/passive-tree.tsv:670 | 63 | 投射物ダメージ |
| res/passive-tree.tsv:671 | 54 | 近接ダメージ |
| res/passive-tree.tsv:672 | 63 | 投射物ダメージ |
| res/passive-tree.tsv:673 | 63 | 投射物ダメージ |
| res/passive-tree.tsv:674 | 54 | 近接ダメージ |
| res/passive-tree.tsv:675 | 63 | 投射物ダメージ |
| res/passive-tree.tsv:676 | 54 | 近接ダメージ |
| res/passive-tree.tsv:677 | 45 | 狂戦士の血 |
| res/passive-tree.tsv:678 | 45 | 与ダメージ |
| res/passive-tree.tsv:679 | 36 | 鉄の意志 |
| res/passive-tree.tsv:680 | 45 | 与ダメージ |
| res/passive-tree.tsv:681 | 30 | 最大HP |
| res/passive-tree.tsv:682 | 45 | 与ダメージ |
| res/passive-tree.tsv:683 | 18 | 聖域 |
| res/passive-tree.tsv:684 | 45 | 与ダメージ |
| res/passive-tree.tsv:685 | 30 | 最大HP |
| res/passive-tree.tsv:686 | 36 | 魔力の盾 |
| res/passive-tree.tsv:687 | 36 | 蝕む術理 |
| res/passive-tree.tsv:688 | 45 | 与ダメージ |
| res/passive-tree.tsv:689 | 45 | 硝子の大砲 |
| res/passive-tree.tsv:690 | 45 | 与ダメージ |
| res/passive-tree.tsv:691 | 30 | 最大HP |
| res/passive-tree.tsv:692 | 36 | 命の循環 |
| res/passive-tree.tsv:693 | 45 | 与ダメージ |
| res/passive-tree.tsv:694 | 30 | 最大HP |
| res/passive-tree.tsv:695 | 45 | 与ダメージ |
| res/passive-tree.tsv:696 | 36 | 血の契約 |
| res/passive-tree.tsv:697 | 45 | 与ダメージ |
| res/passive-tree.tsv:698 | 30 | 最大HP |
| res/passive-tree.tsv:699 | 45 | 狩人の忍耐 |
| res/passive-tree.tsv:700 | 63 | 終わりなき狩り |
| res/passive-tree.tsv:701 | 45 | 与ダメージ |
| res/passive-tree.tsv:702 | 30 | 最大HP |
| res/passive-tree.tsv:703 | 36 | 影の歩み |
| res/passive-tree.tsv:704 | 45 | 与ダメージ |
| res/passive-tree.tsv:705 | 30 | 最大HP |
| res/passive-tree.tsv:706 | 45 | 与ダメージ |
| res/passive-tree.tsv:707 | 45 | 一撃の美学 |
| res/passive-tree.tsv:708 | 45 | 与ダメージ |
| res/passive-tree.tsv:709 | 30 | 最大HP |
| res/passive-tree.tsv:710 | 45 | 崩しの達人 |
| res/passive-tree.tsv:711 | 45 | 与ダメージ |
| res/passive-tree.tsv:712 | 30 | 最大HP |
| res/passive-tree.tsv:713 | 45 | 与ダメージ |
| res/passive-tree.tsv:714 | 30 | 最大HP |
| res/passive-tree.tsv:715 | 45 | 与ダメージ |
| res/passive-tree.tsv:716 | 18 | 孤高 |
| res/passive-tree.tsv:717 | 45 | 与ダメージ |
| res/passive-tree.tsv:718 | 30 | 最大HP |
| res/passive-tree.tsv:719 | 45 | 与ダメージ |
| res/passive-tree.tsv:720 | 30 | 最大HP |
| res/passive-tree.tsv:721 | 45 | 与ダメージ |
| res/passive-tree.tsv:722 | 54 | 循環する魔力 |
| res/passive-tree.tsv:723 | 45 | 与ダメージ |
| res/passive-tree.tsv:724 | 30 | 最大HP |
| res/passive-tree.tsv:725 | 45 | 与ダメージ |
| res/passive-tree.tsv:726 | 30 | 最大HP |
| res/passive-tree.tsv:727 | 45 | 与ダメージ |
| res/passive-tree.tsv:728 | 45 | 元素の同盟 |
| res/passive-tree.tsv:729 | 45 | 与ダメージ |
| res/passive-tree.tsv:730 | 30 | 最大HP |
| res/passive-tree.tsv:731 | 45 | 与ダメージ |
| res/passive-tree.tsv:732 | 30 | 最大HP |
| res/passive-tree.tsv:733 | 45 | 与ダメージ |
| res/dungeons/sakura_dungeon/rooms.json:30 | 45 | 花守の回廊 |
| res/dungeons/sakura_dungeon/rooms.json:35 | 60 | ミニボス1の間 |
| res/dungeons/sakura_dungeon/rooms.json:40 | 36 | 散華の間 |
| res/dungeons/sakura_dungeon/rooms.json:45 | 54 | 崖道の穢れ場 |
| res/dungeons/sakura_dungeon/rooms.json:50 | 45 | 古い祠の森 |
| res/dungeons/sakura_dungeon/rooms.json:55 | 54 | 岩棚の穢れ場 |
| res/dungeons/sakura_dungeon/rooms.json:60 | 60 | ミニボス2の間 |
| res/dungeons/sakura_dungeon/rooms.json:65 | 9 | 庭 |
| res/dungeons/sakura_dungeon/rooms.json:70 | 27 | 苔の窪 |
| res/dungeons/sakura_dungeon/rooms.json:75 | 36 | 滝裏の洞 |
| res/dungeons/sakura_dungeon/rooms.json:82 | 45 | 花守の回廊 |
| res/dungeons/sakura_dungeon/rooms.json:86 | 60 | ミニボス1の間 |
| res/dungeons/sakura_dungeon/rooms.json:90 | 36 | 散華の間 |
| res/dungeons/sakura_dungeon/rooms.json:94 | 54 | 崖道の穢れ場 |
| res/dungeons/sakura_dungeon/rooms.json:98 | 45 | 古い祠の森 |
| res/dungeons/sakura_dungeon/rooms.json:102 | 54 | 岩棚の穢れ場 |
| res/dungeons/sakura_dungeon/rooms.json:106 | 60 | ミニボス2の間 |
| res/dungeons/sakura_dungeon/rooms.json:110 | 9 | 庭 |
| res/dungeons/sakura_dungeon/rooms.json:114 | 27 | 苔の窪 |
| res/dungeons/sakura_dungeon/rooms.json:118 | 36 | 滝裏の洞 |
| res/dungeons/sakura_dungeon/rooms.json:130 | 36 | 枡形の北 |
| res/dungeons/sakura_dungeon/rooms.json:135 | 36 | 枡形の南 |
| res/dungeons/sakura_dungeon/rooms.json:140 | 9 | 北 |
| res/dungeons/sakura_dungeon/rooms.json:145 | 9 | 西 |
| res/dungeons/sakura_dungeon/rooms.json:218 | 1162 | 入口の野（慣らしの群れ）→（沢道｜尾根道）→ 花守の回廊（敵の波）→ 坂 → ミニボス1の間 → 散華の間（敵の波）→ 崖道の穢れ場（隠れた空き地）→ 古い祠の森（穢れ場）→ 岩棚の穢れ場 → ミニボス2の間（滝の前、奥に滝裏の洞）→ 谷の石橋（休み）→ 散り桜の庭（ボス） |
| res/dungeons/sakura_dungeon/rooms.json:222 | 45 | 花守の回廊 |
| res/dungeons/sakura_dungeon/rooms.json:224 | 45 | 花守の回廊 |
| res/dungeons/sakura_dungeon/rooms.json:276 | 60 | ミニボス1の間 |
| res/dungeons/sakura_dungeon/rooms.json:278 | 60 | ミニボス1の間 |
| res/dungeons/sakura_dungeon/rooms.json:295 | 36 | 枡形の北 |
| res/dungeons/sakura_dungeon/rooms.json:305 | 36 | 散華の間 |
| res/dungeons/sakura_dungeon/rooms.json:307 | 36 | 散華の間 |
| res/dungeons/sakura_dungeon/rooms.json:359 | 54 | 崖道の穢れ場 |
| res/dungeons/sakura_dungeon/rooms.json:361 | 54 | 崖道の穢れ場 |
| res/dungeons/sakura_dungeon/rooms.json:411 | 58 | 8 + 4 * 人数 |
| res/dungeons/sakura_dungeon/rooms.json:412 | 38 | 3 + 人数 |
| res/dungeons/sakura_dungeon/rooms.json:414 | 33 | 精鋭1体 |
| res/dungeons/sakura_dungeon/rooms.json:420 | 45 | 古い祠の森 |
| res/dungeons/sakura_dungeon/rooms.json:422 | 45 | 古い祠の森 |
| res/dungeons/sakura_dungeon/rooms.json:439 | 9 | 北 |
| res/dungeons/sakura_dungeon/rooms.json:474 | 58 | 8 + 4 * 人数 |
| res/dungeons/sakura_dungeon/rooms.json:475 | 38 | 3 + 人数 |
| res/dungeons/sakura_dungeon/rooms.json:477 | 33 | 精鋭1体 |
| res/dungeons/sakura_dungeon/rooms.json:483 | 54 | 岩棚の穢れ場 |
| res/dungeons/sakura_dungeon/rooms.json:485 | 54 | 岩棚の穢れ場 |
| res/dungeons/sakura_dungeon/rooms.json:535 | 58 | 8 + 4 * 人数 |
| res/dungeons/sakura_dungeon/rooms.json:536 | 38 | 3 + 人数 |
| res/dungeons/sakura_dungeon/rooms.json:538 | 33 | 精鋭1体 |
| res/dungeons/sakura_dungeon/rooms.json:544 | 60 | ミニボス2の間 |
| res/dungeons/sakura_dungeon/rooms.json:546 | 60 | ミニボス2の間 |
| res/dungeons/sakura_dungeon/rooms.json:563 | 9 | 西 |
| res/dungeons/sakura_dungeon/rooms.json:564 | 36 | 枡形の南 |
| res/dungeons/sakura_dungeon/rooms.json:574 | 45 | 散り桜の庭 |
| res/dungeons/sakura_dungeon/rooms.json:576 | 9 | 庭 |
| res/dungeons/sakura_dungeon/rooms.json:597 | 63 | 入口の野の群れ |
| res/dungeons/sakura_dungeon/rooms.json:603 | 38 | 3 + 人数 |
| res/dungeons/sakura_dungeon/rooms.json:606 | 342 | 慣らし。門は閉じない。追ってくるので、走り抜けると回廊の門の中まで付いてくる |
| res/lobby/first_town/rooms.json:10 | 63 | ダンジョンの門 |
| res/lobby/first_town/rooms.json:140 | 36 | 鍛冶工房 |
| res/lobby/first_town/rooms.json:165 | 54 | 熟練の手引き |
| res/lobby/first_town/rooms.json:205 | 36 | 交易市場 |
| res/lobby/first_town/rooms.json:235 | 45 | 所持品目録 |
