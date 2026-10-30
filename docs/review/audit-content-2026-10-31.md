# 設計書と実装のずれ：ボス・装備の段・ドロップ・刻印石（2026-10-31）

読んだ設計書：`docs/development/` の shade-wyrm-boss / sakura-fang-boss / core-encounter-content-v0 / adventure-events-and-trials-v0 / mob-hud-grades / core-affix-loot-v0 / core-orb-crafting-v0 / class-combat-formula-recovery / core-enhancement-v0 / enhancement-break-repair-v0 / armor-four-slots / harbor-ascension-mmo-v0 / expedition-map-v0 / progression-design、`docs/game/combat-principles.md`。
突き合わせた実装：`server-minestom/.../mob/`（QuestMobContent・QuestEncounterCombat・ShadeWyrmGimmicks・MobGrade）と `coreloop/`（CoreCombatMath・CoreAffixCatalog・CoreCraftingCurrency・CoreEnhancementCatalog・CoreEconomy・CoreAccountService・CoreLoopServer・CoreFrontierZones・adventure/）。
数値は一時的な検証コード `AuditScratchTest`（コミットしていない）と既存テストで確かめた。コードは直していない。武器・職の技は別セッションの担当なので触れていない。

略記：QMC = `mob/QuestMobContent.kt`、QEC = `mob/QuestEncounterCombat.kt`、SWG = `mob/ShadeWyrmGimmicks.kt`、CCM = `coreloop/CoreCombatMath.kt`、CAC = `coreloop/CoreAffixCatalog.kt`、CAS = `coreloop/CoreAccountService.kt`。

## 1. 直さないとまずい

### 1-1. 黒翳竜の大詠唱・殻の失敗ダメージが「防御無視（TRUE）」になっていない
- 設計：shade-wyrm-boss.md「大詠唱85・殻80はTRUEなので回避の無敵では抜けられない」、combat-principles.md:105 も同じ。
- 実装：QMC:134-137。`shade-channel`/`shade-shell` は先に MAGICAL の集合に入っているため、後ろの `else if (...) TRUE` に届かない。結果 MAGICAL になり、回避の無敵で抜けられ（`CorePlayerCombat.kt:1145` は TRUE だけ素通り）、MR と防御の構え（×0.2〜0.45）でも減る。
- 確かめ方：`AuditScratchTest` で `definition(1, SHADE_WYRM)` の `shade-channel`/`shade-shell` の `damageType` を出力 → MAGICAL。
- 直し方：TRUE の判定を MAGICAL の集合より先に書く（または集合から2つを外す）。`ShadeWyrmBossTest` に種別の検査を1行足す。

### 1-2. 堅牢・抗魔（AR%・MR%）は T1 防具では 0 のまま、職の基礎防御にも掛からない
- 設計：class-combat-formula-recovery.md「AD = (武器AD + 固定AD) × (1 + AD増加/100)。…AR/MR も同形」。刻印石 `projects:bulwark`/`aegis` は +10〜20%（+6/段）。
- 実装：CCM:62-70 の段の防御は `300×(1/(1−0.1(T−1))−1)` → T1=0、T2=33.3、T3=75、T4=128.6。CCM:72-73 で `power(defense, AR_FLAT, AR_PERCENT)` を掛けた後、CCM:25-26 `specialize` で職の基礎 AR/MR（戦士40/25、テンプラー70/50 など）を**足す**。よって % は職の基礎に掛からず、T1 では完全に 0。
- 確かめ方：`AuditScratchTest`：armorTier=1 で AR+20% → ar=0、戦士で ar=40（+20% でも 40）。T2 で 33.3→40.0。
- 直し方：`specialize` の基礎 AR/MR を `power()` の base 側に入れる（CCM:72-73 を職込みで計算）か、段の防御に T1 の土台値を置く。

### 1-3. 慈愛（回復力%）は癒力（回復力の固定値）がないと 0
- 設計：刻印石 `projects:grace` 回復力 +8〜14%。回復薬は「最大HP45% + 回復力100%」、回復技・障壁も回復力を足す。
- 実装：CCM:98 `power(0.0, HEALING_FLAT, HEALING_PERCENT)`。回復力の土台が 0 なので、%だけでは 0×1.14=0。`CorePlayerCombat.kt:1183`（回復薬）、`CoreSkillCatalog.kt:36-37`（回復・障壁）がこの値を使う。
- 確かめ方：`AuditScratchTest`：HEALING_PERCENT 14 のみ → healingPower 0.0、HEALING_FLAT 6 → 6.0、両方 → 6.84。
- 直し方：回復力の土台を決める（例：Lv や最大HP の一部、ヒーラーの聖典に固有値）。土台を置かないなら慈愛を抽選から外す。

### 1-4. 職業専用 MOD と AP 系・回復系 MOD が、使えない職の装備にも付く
- 設計：core-affix-loot-v0「まだ使う攻撃や耐久モデルがない効果を、無効なMODとしてドロッププールへ入れない」。class-combat-formula-recovery.md「死んだMODを先にdrop poolへ入れない」。
- 実装：`CoreCraftingCurrency.kt:95-99` の `eligible` は 部位・重複・接頭接尾だけで絞り、職も武器系統も見ない。CAC:138-143 の 余波（戦士）/残響（メイジ）/追矢（狩人）/毒印（暗殺者）/重力（聖騎士）/予防（治療師）、叡智・魔術（AP）、癒力・慈愛・施療（回復）が戦士の大剣にも抽選される。戦士・狩人・暗殺者・テンプラーの技は AP を使わない（`CoreSkillCatalog`：AP を使う技は メイジ 9/10・ヒーラー 9/10・他職 0）ので、AP 系と職業専用 MOD は効果 0 のまま枠を1つ潰す。回復系は障壁を持つ戦士・テンプラーには少し効く。
- 確かめ方：`AuditScratchTest`：戦士（開拓の大剣）の口座で錬金→混沌を400回 → 46 種が出て、職業専用 6 種（7〜16 回）と叡智 49・魔術 51 回を含む。胴でも同じ。
- 直し方：`eligible` に「職業専用 MOD はその職のときだけ」「AP 系は杖・聖典のときだけ」「回復系は治療師（と障壁を持つ職）だけ」のフィルタを足す。既に付いている石は効果 0 のまま保全。

### 1-5. 討伐証（BOSS_SIGIL）の使い道が Tier 昇格だけで、その昇格を呼ぶ UI がない
- 設計：core-orb-crafting-v0「通常ボス報酬に討伐証2…専用ボスにも討伐証2」。progression-design.md 棚卸し1「Tier昇格がUIにつながっていない」（2026-10-19 時点）。
- 実装：討伐証を消費するレシピは `CoreAccount.kt:311-320`（weaponUpgrade/armorUpgrade：素材4+2+討伐証1）だけ。これを実行する `CoreAction.UpgradeWeapon`/`UpgradeArmorPart`（CAS:348-365）を呼ぶコードは main にない（`grep` で CoreLoopMenus・CorePolish05ForgeFlow・web-ui-lab に 0 件）。討伐証は増える一方で、売買もできない（`CoreEconomy.tradeable`）。
- 確かめ方：`grep -rn "UpgradeWeapon\|UpgradeArmorPart" server-minestom/src/main web-ui-lab` → 定義と Service のみ。
- 直し方：工房（CorePolish05ForgeFlow）に「昇格」タブを足して UpgradeWeapon/UpgradeArmorPart を呼ぶ。+値と MOD を保つ昇格は Service 側で既にできている（`CoreCraftingCurrencyTest` が確認済み）。

### 1-6. 貫通の刻印石（穿甲・破甲・破魔・解呪）は T1 の敵に効かない
- 設計：class-combat-formula-recovery.md「有効防御 = max(0, 防御 × (1−防御低下率) × (1−割合貫通率) − 固定貫通)」。
- 実装：敵の防御は QEC:612 `(tier−1)×30 + (敵Lv − Tier最低Lv)×2`。T1 Lv1 の敵は AR=MR=0 なので貫通は何も引けない。T2 の敵でも防御30（軽減 9%）に穿甲 +10〜20 を引いて +3% 程度。
- 確かめ方：QEC:612-617 と CCM:117-121（`effectiveDefense` の `safe()` で 0 止め）。
- 直し方：敵に T1 から土台の防御を持たせる（例：30 + 30×(T−1)）か、貫通石を T2 以上の段でだけ抽選する。

## 2. 説明と違う

### 2-1. 黒翳竜の怒りの咆哮：2.6秒 → 5秒
- shade-wyrm-boss.md:37「2.6秒その場で咆哮」。実装 QEC:70 `ENRAGE_HOLD_MILLIS = 5000`（桜牙獣と共用）。同じ設計書の再開メモは「`enrage` 5秒」。紫の波は咆哮の2.5秒後（QEC:1083）で一致。
- 直し方：設計書を 5 秒に直す（実装の値が演出と合っている）。

### 2-2. 鱗粉の侵食：殴るたび −6 → −2
- shade-wyrm-boss.md「竜を殴るたび−6」。実装 SWG:158 `HIT_RELIEF = 2.0`（1秒上限 10 は一致、SWG:159）。`ShadeWyrmBossTest.kt:313-314` も 80→78 を正としている。
- 直し方：どちらを正にするか決める。−2 のままなら設計書を直す。

### 2-3. 設計書の中で古い値が残っている（実装はすべて後の節の値）
| 項目 | 古い記述 | 実装 | 場所 |
|---|---|---|---|
| 第2段階の解禁 | HP50% | 70%（怒り35%） | QMC:102-103 |
| 大詠唱を止める量 | 2.5% | 5% | QEC:2358 |
| 大詠唱失敗 | 半径22に35×Tier、侵食+40 | 85×Tier、侵食+60 | QMC:189、SWG:156 |
| 殻 | 12秒・耐久4%・失敗 半径10に40×Tier | 9秒・7%・半径22に80×Tier | QMC:191-192、QEC:2359 |
| 侵食の発症 | 25×Tier | 40×Tier | SWG:162 |
| 飛び移り後（第1段階） | 炸裂 | 飛びかかり | QEC:1484 |
| 桜牙獣 横跳びの後 | 「突き上げ」と「突き上げか体当たり」の両方 | 50/50 | QEC:1476 |
- 直し方：設計書の古い行を消す（実装は意図どおり）。

### 2-4. 名前欄に技名を出す → 出さない
- shade-wyrm-boss.md「名前欄は技名だけ」。実装 QEC:1797-1798（2026-10-20 藍「出さない」）。設計書の更新漏れ。

### 2-5. 帰還（リーシュ外）で怒りの「効果」も残る
- 設計書は「見た目は戻さない（未決）」。実装は見た目だけでなく 速さ×1.2（QEC:1362）・後隙×0.6（QEC:1208）・部位破壊の可否（QEC:1601）も `enraged` のまま。体力は全快（QEC:62-66, 877）。吐息・嵐・ギミックは HP 比で再判定されるので全快中は出ない。
- 直し方：未決を決める。全快で怒りを解くなら `enraged`/`awakened` を `finishReturn` で戻す。

### 2-6. 進行の設計案（progression-design.md）と実装
- 「ボスを倒すと次の場所が開く」→ 実装は **Tier の上限 Lv（10/20/30）のボス**を倒したときだけ次 Tier 解放（CAS:316）。それ以外は同 Tier で Lv+2 の地図（CAS:331-333）。harbor-ascension-mmo-v0 とは一致。
- 「T2＝根の洞（黒翳竜）、T3＝桜牙獣」→ 両ボスは遠征に出ない。`CoreFrontierZones.BOSSES` は執行官・守護者・預言者の3種（CoreFrontierZones.kt:30）、黒翳竜・桜牙獣は `/bosstest` だけ（両設計書とも「未決」）。
- 「ボスの部位破壊で素材」「枝角＝桜牙の武器の型・装身具、黒翳竜＝翳の外套＋怒りの角〈特性〉」（共有ノート progression-design.md の 2026-10-20/21 の決定）→ 未実装。`QuestMobDefeat`（QMC:41-50）に部位の情報がなく、報酬に渡らない。
- 「防具を軽くする案（頭・足 革2布1）」→ 未着手。`CoreEconomy.manufacture`（CoreEconomy.kt:60-70）は全部位 革4・布2・石材1。
- 直し方：設計案のままなら progression-design.md に「未実装」と明記。実装するなら各項を別タスクに。

### 2-7. 刻印石の種類数：16種・weight 全部10 → 48種・6種は weight 3
- core-affix-loot-v0 と core-orb-crafting-v0「現時点では全16種類weight10」。実装 CAC:97-146 は 48 定義（うち 24 種は class-combat-formula-recovery.md にあり、残り 8 種は設計書なし。3-1 参照）。元素3種の出やすさは 30/438 ≒ 6.8%（16種のときの 18.8%から下がった）。

### 2-8. ボス抽選「seedから3種」→ ゾーン固定
- core-encounter-content-v0「ボスもseedから3種を選ぶ」。実装はゾーン付き地図ならゾーンのボス（`CoreLoopServer.kt:1202`、expedition-map-v0 のとおり）。ゾーンなし地図（ボス報酬の次地図・採取地図）だけ seed 抽選（QEC:312）。設計書の更新漏れ。

### 2-9. 桜牙獣のカウンターの合図「角が青く光る」→ 花輪が回る
- sakura-fang-boss.md「角が青く光る」。実装は頭の周りの花輪（`SakuraPetalFx.wreath`）と角先の花（`SakuraStrikeFx.kt:246-251`）、名前欄「花輪が回った！ 正面から打て」（QEC:2043）。藍が 10/19 に変えた後、設計書が古い。

## 3. 説明がない実装

### 3-1. 設計書にない刻印石 8 種と、接頭／接尾の割り当て
- 庇護（シールド量 +6〜12%）・不動（CC耐性 +5〜10%）と職業専用 6 種（余波・残響・追矢・毒印・重力・予防、+1〜4、weight 3）：CAC:138-146。効果の式は `CoreSkillCatalog.kt:82-136`・`CorePlayerCombat.kt:711, 878, 1101` にあるが、設計書は class-combat-rework.md に名前が出るだけ。
- 接頭／接尾：追加 32 種の振り分けは CAC:66-74（AD/AP/AR/MR の固定・AD/AP の%・HP%・MP%・癒力が接頭、他は接尾）。orb 設計書の表は 16 種分だけ。
- 霜転の調律（`projects:glacial-attunement`、CAC:148-149）は抽選外の儀式専用。world-infusion 系の文書にだけある。
- 直し方：core-orb-crafting-v0 の表に 48 種と接頭／接尾を書き足す。

### 3-2. 敵の防御値と職種別の補正
- QEC:612-617：敵 AR=MR=`(T−1)×30 + (Lv−最低Lv)×2`。盾持ち・守護者は AR×1.5、術師・預言者は MR×1.5。core-encounter-content-v0 には「軽減」だけで防御値の記述がない。1-6 の原因でもある。

### 3-3. ボスの数値で設計書に値がないもの
- 桜紋の陣のダメージ 18×1.65^(T−1)×等級倍率／空き紋1つ（QEC:2292, 2099-2113、魔法）、紋の位置 半径7・判定1.8（QEC:2290-2291）。
- 千本桜 70×1.65^(T−1)×等級倍率、魔法（QEC:2297, 2161-2166）。
- カウンター「正面」＝80°以内（QEC:2194-2199）。部位倍率の角度 頭50°・尾140°（QEC:2250-2251）。
- 大詠唱・殻への CC の寄与 2%／0.8%（QEC:2255-2256）。
- 後隙：振り向き薙ぎ200・角300・しっぽ回転200・鱗粉弾500ms（QEC:2322-2326）。技選びの重み：側面でしっぽ回転×2、止まっている相手に飛びかかり・鱗粉弾・突進×2（QEC:1287-1289）。
- 桜牙獣の怒り後の連携 3 種：宙返り→旋回、突き上げ→桜爪、飛びかかり→突き上げ（QEC:1897-1899）。
- 目覚め直後 4 秒で飛び移り（QEC:1119）、鱗粉弾の着弾にだまり1つ（SWG:202 付近）、引き寄せはボスに 6 秒に 1 回（QEC:388-392）、連携は最大 1.5 秒待つ（QEC:2352）。
- 直し方：各ボスの設計書の表に写す。

### 3-4. 精鋭以上の通常モブの崩しゲージは回復しない
- QEC:858 の回復（6秒後から毎秒5）は `mob.boss` 限定。mob-hud-grades.md は回復に触れていない。ボスとの差が意図かどうかを決めて書く。

### 3-5. 納品（NPC が完成装備を買い取る）
- `CoreAction.Deliver`（CAS:190-202）：ノーマル・+0・MOD なしの装備を港に納めると 銀貨 80×T²（T1=80、T4=1280）、1 日 3 回。development/ の設計書に記述がない。銀貨の「蛇口」なので、経済の一周（2026-10-14 基準書の優先1）を見るときに表へ載せたい。

### 3-6. 初期武器の型
- 職業選択で渡す初期武器は 戦士＝**連撃大剣（FLOW、威力0.88・速度1.18）**（CAS:117）。設計書は「初期武器」「T1武器」としか書いておらず、開拓の大剣（STANDARD）でない理由が残っていない。

### 3-7. 残っている古い処理
- `CoreAffixCatalog.rollLoot/extractionRecipe/rerollRecipe/reroll`（CAC:181-205）は旧刻印石の抽選で、main からの呼び出しは `CoreAffixTest` だけ。`CoreAction.ApplyAffix` 等は終了メッセージを返す（CAS:282-283）。
- `BossBars.grade`/`dedicated`（MobGrade.kt:84-96）は `archetype.name == "SAKURA_FANG"` の文字列比較のまま（enum は main にある）。

## 4. 問題なしと確認した範囲

- **通常敵**：T1 HP 44/66/36/105/300/380/280、弱点、盾持ち 55%・守護者 50% の正面軽減と「予兆・硬直中は軽減なし」（QEC:1326-1327）、Tier 倍率 1.65^(T−1)（HP・攻撃力とも）、減速上限 65%・ボス半分、8/24 ブロックの再確認、予兆 64/6/384・180ms。
- **黒翳竜**：技 16 の形（帯の半幅・扇の半角）・予兆・硬直・CD（QMC:155-200）、3 段階 70/35%、速さ ×1.1/×1.2、噛みつき 3 連、尾打ち 0.35 秒→振り向き薙ぎ 0.6 秒、部位破壊（角 正面60°・6 ブロック・12%／扇 45〜135°・左右 10%／薙ぎ半径 4.5／嵐封印／3 秒・2 秒怯み／20 秒ぶれ）、だまり（4/3 個・半径 1.8・25 秒・最大 8・共鳴 0.8 秒後に半径 2.5 で 15×Tier）、侵食（+20／+8 毎秒／100 で発症／5 秒で毎秒 −2／1 秒上限 10）、大詠唱 12 秒→40 秒ごと・6 秒・down 4 秒・被ダメ 1.3 倍、殻 15 秒→45 秒ごと、後隙と ×0.85/×0.6、空振り +0.8/+1.5、張り付き反撃 3 割、光る溜め 0.55 秒→2.5 秒、部位倍率 1.25/0.8、振り向き 90°/秒、situationWeight 3/0.35、飛び移り 15/26/18 秒、ADVANCE 8 秒。
- **桜牙獣**：技 15 の形・予兆・硬直、HP 560・弱点 炎・移動 0.14、怒り 50%・5 秒、休み 2.2〜3.8／1.4〜2.6 秒、大技 CD 6〜14 秒、休み中は小技だけ、カウンター 8 技・0.6〜0.15 秒前・3.5 秒、部位破壊 正面 12%／背後 10% と技封印、桜紋の陣 10 秒→40 秒ごと・人数分・6 秒・4.5 秒崩れ・空き紋ごとの計算、千本桜 25% で止まる・8 秒・柱の陰・5 秒崩れ、連携 桜爪→二の手→止め、飛び退き・横跳び・回り込みの後の小技。
- **ドロップ**：通常 35% で 1／精鋭 2／ボス 3、粉 1/3/6、券 2/6/0、通貨の重み 9 種の合計 100、裂け目・儀式・試練は通常から出ない、宝箱＝精鋭相当（CoreLoopServer.kt:1225）、初回の変成 +1、活動報酬 1 単位＝通貨 1・欠片 1・券 3（亀裂 3 単位、儀式 1/3/6）、通常ボス＝討伐証 2・券 12・石板 1・薬 2・試練の欠片 1・次の地図（上限 Lv なら次 Tier、そうでなければ Lv+2）、専用ボス＝通貨 2・高揚 1・討伐証 2・券 12、入場 欠片 3・中断時だけ返却、ボスの追加石は討伐報酬の確定後、同 source の二重受領なし。等級で報酬は変わらない。
- **刻印石（16 種）**：範囲と段ごとの増分がすべて設計書どおり（CAC:97-112）、守護・軽歩は防具限定、同装備内の重複なし、上限（攻撃速度 60・CDR 45・詠唱 40・軽減 45・移動 25・会心率 75%・倍率 4 倍）、レア度の枠 0/2/6・接頭接尾 1/3、通貨 12 種の条件と効果（変成 1〜2・錬金 4〜6・裂け目は元素 1 個保証・試練は上位 25%・神聖は低下あり）、段を上げても既存 MOD はそのまま、石の段が装備の段より上なら効果 0。
- **強化**：成功率表 30 段、天井 0/4/6/6/9/9、破損 `min(50, 5+(L−15)×3)`、素材の段 (+値+7)/8、費用 u=ceil(+値/5)、触媒 +15／星鉄 +25（研究設計書どおり）、+4%／+0.8／+2%、熟練 20XP で 1 rank・最大 10、精製 1・製作 1・昇格 5・強化 1 XP、修理は同系統・同 Tier・+0・未破損・未出品、封蝋は Tier×1 個。
- **装備の段**：武器 12×1.65^(T−1)（12／19.8／32.7／53.9）、防具 HP 100+30(T−1)、段の防御 0／33／75／129（軽減 10/20/30% に相当）、4 部位の重み 15/40/30/15、製作 4/2/1＋下位 Tier 素材 1 個ずつ、品質 +0〜30%、装備 Lv +4.5%/Lv。
- **等級**：HP ×1/1.5/2.1/2.9/4.0、与ダメ ×1/1.1/1.2/1.3/1.45、重み 70/18/8/3/1 と 0/60/25/11/4、MOD 6 種の値、崩れ 4 秒・+30%、ボスは等級の外（ボスバーの等級は見た目だけ）。
- **ウィザークイーン・アレス**：main に設計書も実装もない。`play/gyai/practice-wither-queen`（ローカルのみ・3 コミット）は Ares Bosses の模写練習で公開しない。監査の対象外。
- **気になるが設計書どおり**：敵の攻撃力は段ごとに ×1.65（T4 で ×4.49）だが、プレイヤー側は HP ×1.9・軽減 30% で実効 ×2.7。T3〜T4 は段が上がるほど被弾が重くなる設計になっている（progression-design.md の「見本の数値」も同じ式）。
