# スキル別・立体エフェクト設計表

2026-09-08。Playground `play/gyai/class-combat-rework`。前版の見た目テストを受けた演出リワーク。ダメージ、係数、射程、クールダウン、スキル性能は変更しない。

## 変更の要点

- 7形状の共用から、現行で使う **99形状・128モデル／配色組**へ拡張。70スキルと通常大剣3段を個別定義した。互換用の旧モデルは残っているが、この表の現行シーンには割り当てない。
- Vanilla 26.2 の ItemDisplayRenderer はアイテムに追加のY軸180度回転を掛ける。これを表示エンティティの右回転で相殺し、制作側の +Z を前方として扱う。クライアントを改造する修正ではない。
- 斬撃本体の寸法とサーバーの攻撃範囲を分離。近接の本体が移動距離／広域判定に引きずられて巨大化しないようにした。攻撃範囲は低い疎な地上目印で補足する。
- Starweaver の糸／針／環／雨／雲／衣／転移門を別の形と構図にした。槌・盾・弓矢・毒・回復にも専用の形を割り当てた。
- 本体を使う場合、従来の巨大な粒子輪郭を重ねず、小さな色片・余韻を添える。パック未適用時の粒子フォールバックは残す。
- UIアイコン、日本語フォント、スキル選択UIは変更しない。新規画像の外部取得、クライアントMOD、独自パケット、Particle Framework coreの変更はない。

## 配置・動作の共通契約

モデルは中心 (8,8,8)、XZ面、+Zが前方。立てる絵はX軸 -90度、前方を維持した縦斬りはZ軸90度。
Vanillaの内部回転 → 相殺する右回転 → モデル倍率 → 制作側の左回転 → 配置、の順になる。

| 構成 | 配置・動作 | 禁止する誤用 |
| --- | --- | --- |
| CUT / CLEAVE | 胸の前方に短い横刃／前方を保った縦刃。通常攻撃は横・返し・縦の3段 | 移動距離まで刀身を伸ばす、背中側に刃先を出す |
| SPIN | 腰周りを一周。実際の攻撃波ごとに独立した回転、交互の向き | 多段を一回の静止した輪で済ませる |
| THRUST / HAMMER | 接近後の正面に刺突／槌頭。盾突進だけは縦の盾面 | 槌を剣の三日月で代用する |
| RAY | 3Dの実照準、壁で切った可視線に細い軌跡と短い弾頭。命中は既存の即時判定 | 飛翔時間を新しく作る、空振り終点に命中印を出す、射程全体を太い板にする |
| RAIN | 上空から本体が降下し、同じ着弾地点で攻撃波。本体の横ずれは波ごとに固定 | 予備動作と着弾の位置を別々に抽選する |
| NOVA / FIELD | 足元／指定地点に水平の波、陣、罠。低い境界片を添える | 防護板を巨大な床として寝かせる |
| FAN | 正面に5本の矢尻と短い軌跡 | 全周の氷柱で扇形射撃を表す |
| TELEPORT | 出発点では門が閉じ、到着点では開く。門は約1.6幅×2.15高、8tick | 転移に攻撃刃を出す、出発と到着で同じ拡大をする |
| GUARD / AFTERIMAGE | 構えは本人の胸と向きに追従。残像は発動時の背後に人型を残す | 残像を盾面にする |
| SHIELD / BANNER | 胸前の防護、星衣は胴の横、星座は頭の外周。旗は発動地点に40tick残る | 星の防護を攻撃の爆発や床模様にする |
| PULL | 外周の鉤が中心へ移動し、環も収縮 | 吸引なのに外へ爆発させる |
| PILLAR | 灯火は浮遊灯＋下の縦光柱。審判の剣は切先を下へ | 柱を地面に寝かせる、裁きの剣を上に突き上げる |
| HEAL | 地上の回復波から花弁・羽根・翼が上昇 | 斬撃や攻撃爆発を回復に流用する |

### タイミングと表示

- PREPARE：既存の発生待ち時間。発射前は手元、指定地点系は地面／上空で示す。
- PULSE：サーバーの実攻撃波または実補助効果の発動。追加の偽ヒットは作らない。
- CONTACT：サーバーが受理した命中点のみ。印・破片はこの段階に割り当てる。命中段階を持たない補助スキルで無理に再生しない。
- 基本の本体は6tick。転移8tick、防護の発動9tick、旗40tick、構えは既存持続時間（最大100tick）、残像は最大60tick。長いバフ全期間を表すHUDではない。
- 地面要素は周辺±2ブロックで地表を探して配置する。広い陣全体を斜面へ変形する機能ではない。
- 槌には打撃と金属音、転移の出発は低い詠唱、到着は高い共鳴音。その他は既存の予備動作／発動／命中の音層を維持する。
- `/effects` の華やか／控えめ／最小を維持。自分の設定のみ。半透明度ではなくモデル・補助層・粒子量の切替。
- 1人20表示、インスタンス120表示の上限。他人は16ブロック以内・主要3表示まで。パック未適用者にはモデルを送らない。

## 全割り当て

正本は [skill-scenes.psv](../../server-minestom/src/main/resources/combat-art/skill-scenes.psv)。モデルIDは「形状_配色」。
下表の寸法は本体の最大長／半径の設計パラメーター（ブロック）で、ダメージ半径ではない。最終的な幅・高さ・配置・アニメーションは `CoreSkillScenes.kt` の構成別処理で決まる。
予備動作／命中が実際に発生するかは既存のスキル処理に従う。

### 通常攻撃

| 技／ID | 構成・配色・寸法 | 本体＋補助 | 予備動作 → 確定命中 | 用途・区別 |
| --- | --- | --- | --- | --- |
| 通常攻撃一段目 `normal_sweep` | CUT / steel / 2.1 | `slash_short` + `speed_streak` | `edge_glint` → `cut_spark` | 大剣の一段目。短い右から左への振り |
| 通常攻撃二段目 `normal_reverse` | CUT / steel / 2.2 | `slash_reverse` + `speed_streak` | `edge_glint` → `cut_spark` | 大剣の二段目。前方の左から右へ返す |
| 通常攻撃三段目 `normal_finish` | CLEAVE / gold / 2.7 | `slash_heavy` + `ground_crack` | `edge_glint` → `stone_impact` | 大剣の三段目。前方へ縦に下ろす |

### ウォーリアー

| 技／ID | 構成・配色・寸法 | 本体＋補助 | 予備動作 → 確定命中 | 用途・区別 |
| --- | --- | --- | --- | --- |
| 踏み込み斬り `dash` | CUT / steel / 2.1 | `slash_short` + `speed_streak` | `edge_glint` → `cut_spark` | 移動後の胸元前方を短く横断する刃。突進距離を刃の長さにしない |
| 地砕き `slam` | CLEAVE / gold / 2.7 | `slash_heavy` + `ground_crack` | `edge_glint` → `stone_impact` | 正面の縦斬りと前方地割れ。背後へ振らない |
| 旋風斬り `whirl` | SPIN / steel / 2.4 | `spin_blade` + `speed_streak` | `edge_glint` → `cut_spark` | 一回の判定に一周の回転刃。三回は三つの独立した斬撃 |
| 受け流し `war_guard` | GUARD / steel / 0.9 | `parry_blade` + `edge_glint` | `edge_glint` → `cut_spark` | 胸前の交差した刃。構えの実時間だけ追従 |
| 裂傷斬り `war_wound` | CUT / steel / 1.8 | `slash_short` + `blood_notch` | `edge_glint` → `cut_spark` | 素早い斜め斬り。小さな傷跡を確定命中時に表示 |
| 返し刃 `war_counter` | CUT / gold / 2.3 | `slash_reverse` + `speed_streak` | `edge_glint` → `cut_spark` | 横斬りと逆向きの返し。全身を囲う輪にしない |
| 雄叫び `war_cry` | SHIELD / gold / 1.2 | `roar_wave` + `shield_arch` | `cast_spark` → `light_spark` | 口元から前後へ広がる声の波と短い防護弧。斬撃なし |
| 破城突き `war_breach` | THRUST / steel / 2.6 | `thrust_tip` + `speed_streak` | `edge_glint` → `armor_break` | 移動後の正面への細い刺突。横の刀身や回転は禁止 |
| 天断 `war_ult` | CLEAVE / gold / 3.4 | `slash_heavy` + `ground_crack` | `edge_glint` → `stone_impact` | 三回の垂直叩きつけ。広い当たり判定は薄い地上表示で補足 |
| 不屈の旗 `war_banner` | BANNER / gold / 1.5 | `battle_banner` + `shield_arch` | `cast_spark` → `light_spark` | 足元に立つ旗と仲間へ広がる防護の弧。旗は発動地点に残る |

### メイジ

| 技／ID | 構成・配色・寸法 | 本体＋補助 | 予備動作 → 確定命中 | 用途・区別 |
| --- | --- | --- | --- | --- |
| 火炎弾 `firebolt` | RAY / fire / 0.6 | `fire_orb` + `flame_tail` | `cast_spark` → `fire_burst` | 視線方向の火球と短い炎尾。画面全長を巨大な槍にしない |
| 霜の波紋 `frost_nova` | NOVA / ice / 3.6 | `ice_wave` + `ice_spike` | `cast_spark` → `ice_shatter` | 足元から全周へ広がる氷の波と低い霜。正面扇ではない |
| 流星雨 `meteor` | RAIN / fire / 0.9 | `meteor_rock` + `fire_crater` | `cast_spark` → `fire_burst` | 狙った地点の上空から岩塊が落下。各判定に一組の着弾痕 |
| 閃光歩 `mage_blink` | TELEPORT / lightning / 1.0 | `lightning_gate` + `electric_shard` | `cast_spark` → `electric_spark` | 出発点で縮む裂け目と到着点で開く裂け目。斬撃は使わない |
| 雷の刻印 `mage_mark` | RAY / lightning / 0.8 | `lightning_fork` + `electric_thread` | `cast_spark` → `lightning_seal` | 細い分岐雷と命中した敵の雷印。空振りに印を出さない |
| 氷の庭 `mage_garden` | FIELD / ice / 3.0 | `snowflake` + `ice_spike` | `cast_spark` → `ice_shatter` | 地面の雪結晶と低い氷柱。攻撃判定の各波に合わせて更新 |
| 術式起爆 `mage_burst` | NOVA / lightning / 3.6 | `lightning_burst` + `electric_shard` | `lightning_seal` → `electric_spark` | 中心の術式が破裂し四方向へ雷片。一本の光線にしない |
| 魔力障壁 `mage_ward` | SHIELD / lightning / 1.0 | `arcane_shield` + `shield_arch` | `cast_spark` → `light_spark` | 本人の胴を守る魔力板。radiusゼロでも巨大な地面輪を作らない |
| 天火の大術式 `mage_ult` | RAIN / fire / 1.4 | `meteor_crown` + `fire_crater` | `fire_seal` → `fire_burst` | 大術式の中心へ火冠を落とし放射状の火口。各爆発と同期 |
| 絶対零界 `mage_zero` | NOVA / ice / 5.0 | `ice_wave` + `snowflake` | `snowflake` → `ice_shatter` | 五回の同心霜波。巨大な正面氷槍にしない |

### ハンター

| 技／ID | 構成・配色・寸法 | 本体＋補助 | 予備動作 → 確定命中 | 用途・区別 |
| --- | --- | --- | --- | --- |
| 狙い射ち `pierce` | RAY / hunter / 1.0 | `arrow_head` + `arrow_trail` | `aim_bracket` → `arrow_hit` | 命中線に小さな矢尻と細い弾道。矢全体を射程分伸ばさない |
| 霜矢の扇 `frost_fan` | FAN / ice / 3.0 | `frost_arrow` + `arrow_trail` | `aim_bracket` → `ice_shatter` | 正面の五本の氷矢。全周の氷柱と区別する |
| 矢の嵐 `arrow_rain` | RAIN / hunter / 1.3 | `rain_arrow` + `target_reticle` | `aim_bracket` → `arrow_hit` | 指定地点へ複数の下向き矢。魔法の柱にしない |
| 後退射撃 `hunt_retreat` | RAY / hunter / 0.9 | `arrow_head` + `arrow_trail` | `aim_bracket` → `arrow_hit` | 後退後も発射方向は前方の照準。転移門を出さない |
| 貫き矢 `hunt_pierce` | RAY / hunter / 1.4 | `barbed_arrow` + `arrow_trail` | `aim_bracket` → `arrow_hit` | 貫通矢尻と最後の対象までの細い線。横の巨大な面は禁止 |
| 狩猟罠 `hunt_trap` | FIELD / venom / 2.0 | `trap_jaws` + `poison_drop` | `target_reticle` → `poison_splash` | 地面の開閉する罠と内側の毒。盾や星の輪を流用しない |
| 獲物の印 `hunt_mark` | RAY / hunter / 0.8 | `mark_arrow` + `arrow_trail` | `aim_bracket` → `hunter_mark` | 細い印矢。標的マークは確定命中した敵だけ |
| 速射 `hunt_volley` | RAY / hunter / 0.8 | `arrow_head` + `arrow_trail` | `aim_bracket` → `arrow_hit` | 一回の判定に一発の短い矢。三連射を一本にまとめない |
| 終の一矢 `hunt_ult` | RAY / hunter / 1.8 | `great_arrow` + `arrow_trail` | `aim_bracket` → `arrow_hit` | 長い構えの照準から一発の強い矢。砲弾や魔法球にしない |
| 狩場の支配者 `hunt_storm` | RAIN / hunter / 1.5 | `barbed_arrow` + `target_reticle` | `aim_bracket` → `arrow_hit` | 六回の広域矢雨。発生回数はサーバーの攻撃波そのもの |

### アサシン

| 技／ID | 構成・配色・寸法 | 本体＋補助 | 予備動作 → 確定命中 | 用途・区別 |
| --- | --- | --- | --- | --- |
| 影刺し `ass_stab` | THRUST / shadow / 1.6 | `dagger_tip` + `speed_streak` | `edge_glint` → `blood_notch` | 接近後の短剣の刺突。長い大剣にはしない |
| 断命 `ass_execute` | CUT / shadow / 1.8 | `cross_cut` + `blood_notch` | `edge_glint` → `cut_spark` | 前方の短い二枚刃の切断。一判定に一つの交差斬痕 |
| 刃の輪 `ass_fan` | SPIN / shadow / 2.1 | `dagger_fan` + `speed_streak` | `edge_glint` → `cut_spark` | 腰周りの短剣列が一周。二連撃は二つの回転 |
| 影抜け `ass_escape` | TELEPORT / shadow / 0.9 | `shadow_gate` + `shadow_wisp` | `cast_spark` → `shadow_spark` | 後方離脱の二地点に裂ける影。振り向きや斬撃は表さない |
| 毒牙 `ass_poison` | CUT / venom / 1.6 | `poison_fang` + `poison_drop` | `edge_glint` → `poison_splash` | 短い二本の毒刃と緑の滴。毒の継続表示は実DOTに追従 |
| 追影 `ass_chase` | THRUST / shadow / 2.0 | `dagger_tip` + `shadow_wisp` | `edge_glint` → `cut_spark` | 接近先から前方への短い追い突き。移動距離で巨大化しない |
| 影の針 `ass_needle` | RAY / shadow / 1.1 | `shadow_needle` + `arrow_trail` | `edge_glint` → `blood_notch` | 視線に沿う細い黒紫の針。星核や輪を付けない |
| 残像 `ass_guard` | AFTERIMAGE / shadow / 0.8 | `afterimage` + `shadow_wisp` | `cast_spark` → `shadow_spark` | 人型の影を本人の背後へずらす。盾として描かない |
| 無明連刃 `ass_ult` | SPIN / shadow / 2.5 | `shadow_claw` + `dagger_fan` | `edge_glint` → `cut_spark` | 五判定に五回の短い周回斬撃。刃の傾きを交互に変える |
| 死の契約 `ass_contract` | THRUST / shadow / 2.6 | `contract_blade` + `shadow_wisp` | `edge_glint` → `armor_break` | 接近後の一本の強い刺突と命中点の装甲割れ |

### テンプラー

| 技／ID | 構成・配色・寸法 | 本体＋補助 | 予備動作 → 確定命中 | 用途・区別 |
| --- | --- | --- | --- | --- |
| 裁きの槌 `temp_mace` | HAMMER / gold / 1.8 | `hammer_head` + `ground_crack` | `edge_glint` → `stone_impact` | 槌頭が正面へ落ちる。剣の三日月は使用しない |
| 集束 `temp_pull` | PULL / gold / 3.0 | `chain_hook` + `gravity_funnel` | `cast_spark` → `light_spark` | 外周の鉤が本人へ収束。爆発のように外へ飛ばさない |
| 護りの誓い `temp_ward` | SHIELD / gold / 1.2 | `shield_face` + `shield_arch` | `holy_seal` → `light_spark` | 本人の胸前の盾面と味方側へ開く防護弧。広い保護範囲は地上の目印で示す |
| 守護の構え `temp_guard` | GUARD / gold / 1.1 | `shield_face` + `edge_glint` | `holy_seal` → `light_spark` | 胸の前の縦盾。構え時間だけ本人に追従 |
| 報復 `temp_rebuke` | NOVA / gold / 3.0 | `shield_wave` + `ground_crack` | `holy_seal` → `stone_impact` | 足元から四分割の衝撃波が放射。地面へ巨大な盾の板を寝かせない |
| 封鎖線 `temp_field` | FIELD / gold / 3.2 | `holy_seal` + `seal_pillar` | `holy_seal` → `light_spark` | 指定地点の地上封印と四本の低い境界柱 |
| 破魔槌 `temp_break` | HAMMER / gold / 2.1 | `hammer_break` + `ground_crack` | `edge_glint` → `armor_break` | 割れた刃縁の槌を振り下ろし確定命中で装甲破壊片 |
| 援護突進 `temp_dash` | THRUST / gold / 1.8 | `shield_bash` + `speed_streak` | `holy_seal` → `stone_impact` | 移動後の前方へ盾を押し出す。刺す槍と区別 |
| 空間圧縮 `temp_ult` | PULL / gold / 4.5 | `chain_hook` + `gravity_funnel` | `holy_seal` → `light_spark` | 三回の内向き圧縮。外周から中心へ実際に動く鉤と収縮環 |
| 不落の聖域 `temp_sanctuary` | SHIELD / gold / 1.6 | `sanctum_gate` + `shield_arch` | `holy_seal` → `light_spark` | 頭上を塞がない聖堂状の防護壁と外へ広がる弧 |

### ヒーラー

| 技／ID | 構成・配色・寸法 | 本体＋補助 | 予備動作 → 確定命中 | 用途・区別 |
| --- | --- | --- | --- | --- |
| 裁きの光 `heal_light` | RAY / holy / 1.1 | `holy_ray` + `light_thread` | `cast_spark` → `light_spark` | 細い聖光と命中点の光片。星の針とは形も色も別 |
| 救いの輪 `heal_ring` | HEAL / life / 2.4 | `healing_petal` + `healing_wave` | `holy_seal` → `light_spark` | 地面から本人と周辺へ立ち上がる花弁の回復波 |
| 断罪の柱 `heal_pillar` | PILLAR / holy / 1.0 | `light_column` + `holy_seal` | `cast_spark` → `light_spark` | 指定地点に立ち上がる聖光の柱。落下矢にしない |
| 光の歩み `heal_step` | TELEPORT / life / 0.9 | `feather_gate` + `feather` | `cast_spark` → `light_spark` | 出発点の羽根と到着点の開く光門。足元から保護を示す |
| 灯火 `heal_lamp` | PILLAR / holy / 0.7 | `lantern` + `light_column` | `holy_seal` → `light_spark` | 指定地点の浮遊灯から短い光柱。流星とは区別 |
| 祈りの盾 `heal_shield` | SHIELD / life / 1.2 | `prayer_wing` + `shield_arch` | `holy_seal` → `light_spark` | 左右の祈りの翼が胴を包む。攻撃刃は使わない |
| 導きの印 `heal_mark` | RAY / holy / 0.8 | `holy_ray` + `light_thread` | `cast_spark` → `guidance_seal` | 命中した敵に導きの印。空中の終点へは印を出さない |
| 清めの風 `heal_wind` | HEAL / life / 2.0 | `feather` + `healing_wave` | `holy_seal` → `light_spark` | 低い風の輪から羽根が上昇。回復を外向き爆発に見せない |
| 救済 `heal_ult` | HEAL / life / 3.2 | `prayer_wing` + `healing_wave` | `holy_seal` → `light_spark` | 大きな回復波と上向きの翼。画面中央を埋める板にしない |
| 最後の審判 `heal_judgment` | PILLAR / holy / 1.8 | `judgment_sword` + `holy_seal` | `holy_seal` → `light_spark` | 四判定ごとに天からの裁きの剣。地面の印と同期 |

### Starweaver

| 技／ID | 構成・配色・寸法 | 本体＋補助 | 予備動作 → 確定命中 | 用途・区別 |
| --- | --- | --- | --- | --- |
| 星糸 `star_thread` | RAY / astral / 0.7 | `star_thread` + `light_thread` | `star_seed` → `star_spark` | 二本の細い星糸と小さな星節。巨大な光槍を使わない |
| 星環 `star_ring` | NOVA / astral / 3.1 | `star_orbit` + `star_shard` | `star_seed` → `star_spark` | 足元中心の水平星環が一度広がる。流星落下とは区別 |
| 星降る夜 `starfall` | RAIN / astral / 0.9 | `star_core` + `astral_crack` | `star_seed` → `star_spark` | 指定地点上空の星が落ちて星片へ。各判定で着弾点を小さくずらす |
| 星渡り `star_step` | TELEPORT / astral / 1.0 | `star_gate` + `star_shard` | `star_seed` → `star_spark` | 星形の門が二地点で閉じ開く。転移に星環攻撃を出さない |
| 星の針 `star_needle` | RAY / astral / 1.0 | `star_needle` + `light_thread` | `star_seed` → `star_mark` | 細長い星針。印は確定命中した敵のみに付ける |
| 星雲 `star_cloud` | FIELD / astral / 2.8 | `nebula_wisp` + `constellation` | `star_seed` → `star_spark` | 指定地点の低い雲筋と星座。毎回核が落ちる演出は禁止 |
| 星衣 `star_shield` | SHIELD / astral / 1.1 | `star_mantle` + `constellation` | `star_seed` → `star_spark` | 胴の外側に星の衣と短い星座弧。星雨の流用は禁止 |
| 星砕き `star_break` | RAY / astral / 1.2 | `star_shard` + `light_thread` | `star_seed` → `star_fracture` | 圧縮した星片を撃ち込み確定命中点で分裂。空振りに崩壊を出さない |
| 天球崩壊 `star_ult` | RAIN / astral / 1.5 | `celestial_core` + `astral_crack` | `constellation` → `star_fracture` | 大きな天球の落下と放射星片。五回以上の実判定に同期 |
| 星座の守り `star_constellation` | SHIELD / astral / 1.5 | `constellation_crown` + `star_mantle` | `constellation` → `star_spark` | 頭の外周と胴を結ぶ星座の守り。攻撃の破裂を出さない |

## 実装を追う場所

1. `CorePlayerCombat`：発生時刻、実命中、壁で短縮された線、実際の転移二地点。
2. `GreatswordVfx` → `CoreSkillEffect`：そのイベントから演出を作る。
3. **`CoreSkillScenes.kt` + `combat-art/skill-scenes.psv`**：技の個別契約と実配置を決める最重要箇所。
4. `CoreCombatMeshes`：Vanilla回転の補正、追従、倍率、寿命、viewer、上限を適用。
5. `CoreSceneParticles` / `CoreSkillAudio`：小さな補助粒子と音を重ねる。

形状の制作元は `scripts/combat_vfx_shapes.py`、JSONの生成は `scripts/build_core_combat_models.py`。
配布モデルは `core-ui-pack/assets/projects/{models,items}/combat_vfx/`。UIアイコン生成ではなく、Minecraft標準の直方体ジオメトリである。

壊れた時は最初にログの `CORE_UI_PACK ... SUCCESSFULLY_LOADED` と読み込んだビルドを確認する。
方向の問題は `vanillaItemCorrection`、技の取り違えは `sceneId` とPSV、消えない場合は `CoreCombatMeshes.tick/cancel` を確認する。

## 検証

最終結果：`:server-minestom:test :server-minestom:distZip --offline --no-daemon --max-workers=1 -Pkotlin.compiler.execution.strategy=in-process` 成功。全673テスト、失敗・エラー・スキップ0。128モデル／配色組、99形状のJSON検証も成功。

- `CoreSkillSceneGeometryTest`：73契約、全技の宣言形状の消費、実JSON頂点にVanillaと同じ変換を適用。近接8方向の前後・床下・最大寸法、上下を含む壁切り光線、転移開閉、吸引方向、落下と着弾の一致、星衣・灯火・審判の向き、粒子上限。
- `CoreCombatMeshTest`：全70技のモデル参照、実MinestomのテストPlayerでパック有無・viewer・寿命・キャンセル・個人／共有上限。ユーザーのゲームは操作しない。
- 生成JSONの全モデル検証と、実Kotlin配置を投影した全70技×3時点の7枚の描画シート。灯火の光柱、星の防護、盾の衝撃波、裁きの剣の誤用をここでも修正した。
- 描画シートはテストが `.tools/skill-scene-poses.json` を出力した後、`scripts/preview_core_combat_models.py` で作る。`.tools/skill-scenes-1.png` ～ `7.png` は**ゲーム画面ではなく幾何のQA**。ゲーム内の光、遮蔽、音、FPSの証明には使わない。
- 実機の派手さ・視界・複数人負荷はCreatorの手動確認待ち。サーバー再起動／Minecraft操作はこの変更作業では行わない。新しいビルドとパックの読み込みで反映する。
