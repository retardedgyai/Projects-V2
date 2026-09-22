package dev.projects.server.coreloop

enum class CoreSkillMotion { LUNGE, CONE, SPIN, RAY, NOVA, FIELD, EVADE, GUARD, HEAL, SHIELD, PULL }
enum class CoreSkillStatus { NONE, SLOW, MARK, EXPOSE, POISON }

/** This catalog is the authority for both casts and their Japanese tooltips. */
data class CoreSkillDefinition(
    val name: String, val icon: String, val description: String,
    val formula: CoreDamageFormula, val type: CoreDamageType,
    val mana: Int, val cooldown: Int, val startup: Int,
    val pulses: Int = 1, val projectile: Boolean = false, val area: Boolean = false,
    val motion: CoreSkillMotion = CoreSkillMotion.RAY, val range: Double = 18.0, val radius: Double = 4.5,
    val gain: Int = 0, val spend: Int = 0, val status: CoreSkillStatus = CoreSkillStatus.NONE,
    val element: Int = 0, val duration: Int = 60, val ultimate: Boolean = false,
) {
    val tags get() = buildSet {
        add(CoreAttackTag.SKILL)
        if (projectile) add(CoreAttackTag.PROJECTILE)
        else if (type == CoreDamageType.PHYSICAL) add(CoreAttackTag.MELEE)
        if (area) add(CoreAttackTag.AREA)
    }
    fun cooldownTicks(stats: CoreAffixStats) = CoreCombatMath.cooldownTicks(cooldown, stats.cooldownReductionPercent)
    fun startupTicks(sheet: CoreCombatSheet) = CoreCombatMath.castTicks(startup,
        if (type == CoreDamageType.MAGICAL) sheet.mods.castReductionPercent else (sheet.attackSpeed - 1) * 100)
    fun baseDamage(sheet: CoreCombatSheet) = formula.evaluate(sheet) +
        (sheet.mods.fireFlat + sheet.mods.iceFlat + sheet.mods.lightningFlat) * .65
    fun preview(sheet: CoreCombatSheet) = when (motion) {
        CoreSkillMotion.HEAL -> CoreCombatMath.healing(formula.evaluate(sheet), 1.0, sheet)
        CoreSkillMotion.SHIELD -> (formula.evaluate(sheet) + sheet.healingPower) * sheet.shieldMultiplier
        CoreSkillMotion.GUARD -> 0.0
        else -> if (formula.base == 0.0 && formula.ad == 0.0 && formula.ap == 0.0) 0.0 else CoreCombatMath.outgoing(baseDamage(sheet), type, tags, sheet.mods)
    }
    fun tooltip(sheet: CoreCombatSheet, journey: CoreJourney? = null): List<String> = buildList {
        add(description)
        add(when(motion) { CoreSkillMotion.HEAL -> "周囲の仲間を回復"; CoreSkillMotion.SHIELD -> "周囲の仲間へ障壁"; CoreSkillMotion.GUARD -> "防御姿勢 / ${type.label}"; else -> type.label })
        add("${formula.label()}${if(motion in setOf(CoreSkillMotion.HEAL, CoreSkillMotion.SHIELD)) " + 回復力100%" else ""}${if (pulses > 1) " × $pulses 回（1回ごと）" else ""}")
        val conversion = CoreSkillCatalog.healConversion(journey, sheet.mods)
        if(motion == CoreSkillMotion.HEAL && conversion > 0) {
            add("現在 回復 ${CoreCombatMath.number(preview(sheet)*(1-conversion))} / 障壁 ${CoreCombatMath.number(preview(sheet)*conversion*sheet.shieldMultiplier)}")
        } else add("現在 ${CoreCombatMath.number(preview(sheet))}${if (pulses > 1) " / 全命中 ${CoreCombatMath.number(preview(sheet) * pulses)}" else ""}（装備・技能補正込み）")
        add(if(motion in setOf(CoreSkillMotion.HEAL,CoreSkillMotion.SHIELD)) "対象の被回復・星・守護聖人は別 / 障壁は最大HPの75%まで" else "敵防御・会心・弱点・固有効果・砥石は別計算")
        add("${journey?.job?.resourceName ?: "固有資源"}：必要 $spend / 命中で +$gain")
        add("マナ $mana / 再使用 ${CoreCombatMath.number(cooldownTicks(sheet.mods) / 20.0)}秒")
        add("発生 ${CoreCombatMath.number(startupTicks(sheet) / 20.0)}秒 / ${if (type == CoreDamageType.MAGICAL) "詠唱速度" else "攻撃速度"}で短縮")
        if(motion == CoreSkillMotion.GUARD) add("最初の0.6秒：被害80%軽減 / 以降55%軽減 / ${duration/20.0}秒")
        if(status != CoreSkillStatus.NONE) add(when(status) { CoreSkillStatus.MARK -> "印6秒：消費技の最初の命中が35%強化"; CoreSkillStatus.EXPOSE -> "防御崩し4秒：敵のAR・MRを20%低下"; CoreSkillStatus.POISON -> "毒3秒：1秒ごとに係数の30%（重複しない）"; else -> "減速：敵の移動を40%低下" })
        if (journey?.job == CoreClass.WARRIOR) {
            if (icon == "war_guard") add("成功時：叩きつけの再使用を回復 / 3秒以内の叩きつけ・返し刃を反撃化")
            if (CoreWarriorCombatRules.counterSkill(icon)) {
                val boost = if(journey.build.keystone == 1) 1.6 else 1.35
                val counterTicks = copy(startup = minOf(3, startup)).startupTicks(sheet)
                add("反撃時：発生${CoreCombatMath.number(counterTicks/20.0)}秒 / 威力+${((boost-1)*100).toInt()}%")
            }
            add("後隙 ${CoreWarriorCombatRules.recovery(icon)/20.0}秒 / 次の入力は0.3秒先行受付")
        }
    }
}

object CoreSkillCatalog {
    fun healConversion(j: CoreJourney?, stats: CoreAffixStats): Double {
        if(j?.job != CoreClass.HEALER) return 0.0
        val mod = stats.bonus(CoreAffixStat.HEAL_CONVERSION)
        return maxOf(if(j.build.keystone == 0) .5 else 0.0, if(mod>0) (.25+mod*.03).coerceAtMost(.8) else 0.0)
    }
    val unlockLevels = listOf(1, 4, 8, 12, 16)
    val basicFormula = CoreDamageFormula(ad = 1.0)
    fun basicType(job: CoreClass) = if (job.magic) CoreDamageType.MAGICAL else CoreDamageType.PHYSICAL
    fun basicTags(job: CoreClass) = setOf(CoreAttackTag.NORMAL,
        if (job.melee) CoreAttackTag.MELEE else CoreAttackTag.PROJECTILE)
    private fun skill(name: String, icon: String, description: String, magic: Boolean, motion: CoreSkillMotion,
        base: Double, coefficient: Double, mana: Int, cooldown: Int, startup: Int, pulses: Int = 1,
        gain: Int = 0, spend: Int = 0, status: CoreSkillStatus = CoreSkillStatus.NONE,
        range: Double = 18.0, radius: Double = 4.5, element: Int = 0, duration: Int = 60, ultimate: Boolean = false) =
        CoreSkillDefinition(name, icon, description, CoreDamageFormula(base, ad = if (magic) 0.0 else coefficient, ap = if (magic) coefficient else 0.0),
            if (magic) CoreDamageType.MAGICAL else CoreDamageType.PHYSICAL, mana, cooldown, startup, pulses,
            motion == CoreSkillMotion.RAY, motion != CoreSkillMotion.RAY, motion, range, radius, gain, spend, status, element, duration, ultimate)
    private val byClass: Map<CoreClass, List<CoreSkillDefinition>> = CoreClass.entries.associateWith(::buildSkills)
    fun skills(job: CoreClass): List<CoreSkillDefinition> = byClass.getValue(job)
    fun equipped(j: CoreJourney, stats: CoreAffixStats = CoreAffixStats()) = (j.build.skills.map { skills(j.job)[it] } + skills(j.job)[8 + j.build.ultimate]).map { modify(it, j, stats) }
    fun modify(s: CoreSkillDefinition, j: CoreJourney, stats: CoreAffixStats): CoreSkillDefinition {
        var pulses = s.pulses
        var power = 1.0
        var radius = s.radius
        val notes = mutableListOf<String>()
        if (j.job == CoreClass.MAGE && s.spend > 0) {
            if (j.build.keystone == 0) { pulses++; power *= .85; notes += "連鎖術式" }
            val echo = stats.bonus(CoreAffixStat.MAGE_ECHO)
            if (echo > 0) { pulses++; power *= (.65 + echo.coerceAtMost(20.0) / 100); notes += "残響MOD" }
        }
        if (j.job == CoreClass.WARRIOR && s.motion in setOf(CoreSkillMotion.CONE, CoreSkillMotion.SPIN, CoreSkillMotion.LUNGE)) {
            val wave = stats.bonus(CoreAffixStat.WAR_AFTERSHOCK)
            if (wave > 0) { radius *= 1 + wave.coerceAtMost(12.0) * .08; power *= .85; notes += "余波MOD" }
        }
        if (j.job == CoreClass.RANGER && s.motion == CoreSkillMotion.RAY) {
            val arrow = stats.bonus(CoreAffixStat.HUNT_RETURN)
            if (arrow > 0) { pulses++; power *= .6 + arrow.coerceAtMost(20.0) / 100; notes += "追矢MOD" }
        }
        if (j.job == CoreClass.RANGER && j.build.keystone == 2 && s.motion == CoreSkillMotion.FIELD) { pulses++; notes += "罠師" }
        if (j.job == CoreClass.TEMPLAR && s.motion == CoreSkillMotion.PULL) {
            val gravity = stats.bonus(CoreAffixStat.TEMP_GRAVITY)
            if (gravity > 0) { radius *= 1 + gravity.coerceAtMost(10.0) * .1; power *= .8; notes += "重力MOD" }
            if (j.build.keystone == 1) radius += 1.5
        }
        var cooldown = s.cooldown
        var startup = s.startup
        var mana = s.mana
        var gain = s.gain
        var spend = s.spend
        val index = skills(j.job).indexOf(s)
        if (index == CoreClassTrees.signature(j.job)) {
            if (j.build.has(9)) { radius *= 1.3; power *= .9; notes += "広域化" }
            if (j.build.has(10)) { pulses++; power *= .75; notes += "追加発動" }
            if (j.build.has(14)) { cooldown = (cooldown * .8).toInt(); power *= .9; notes += "循環" }
        }
        if (index == 0) {
            if (j.build.has(11)) { startup = (startup * .7).toInt().coerceAtLeast(1); gain = kotlin.math.ceil(gain * 1.25).toInt(); notes += "高速始動" }
            if (j.build.has(13)) { mana = (mana * .6).toInt(); gain = kotlin.math.ceil(gain * 1.25).toInt(); notes += "始動の備え" }
        }
        if (index == 3 && j.build.has(12)) { cooldown = (cooldown * .75).toInt(); notes += "軽やかな構え" }
        if (index == 5 && j.build.has(15)) { radius += 1.5; mana = (mana * .6).toInt(); notes += "広い備え" }
        if (s.ultimate) {
            if (j.build.has(16)) { spend = kotlin.math.ceil(spend * .8).toInt(); notes += "奥義の備え" }
            if (j.build.has(17)) { cooldown = (cooldown * .85).toInt(); power *= .9; notes += "奥義の循環" }
        }
        return s.copy(formula = CoreDamageFormula(s.formula.base * power, s.formula.ad * power, s.formula.ap * power),
            cooldown = cooldown, startup = startup, mana = mana, gain = gain,
            pulses = pulses.coerceAtMost(8), radius = radius.coerceAtMost(10.5), spend = kotlin.math.ceil(spend * if (j.build.has(1)) .85 else 1.0).toInt(),
            description = s.description + if (notes.isEmpty()) "" else " / ${notes.joinToString("・")}適用")
    }
    val artNames: List<String> = CoreClass.entries.flatMap { skills(it).map(CoreSkillDefinition::icon) }
    fun artIndex(skill: CoreSkillDefinition) = artNames.indexOf(skill.icon)
    private fun buildSkills(job: CoreClass): List<CoreSkillDefinition> {
        fun s(name: String, icon: String, description: String, motion: CoreSkillMotion, base: Double, coefficient: Double,
            mana: Int = 12, cd: Int = 100, startup: Int = 6, pulses: Int = 1, gain: Int = 15, spend: Int = 0,
            status: CoreSkillStatus = CoreSkillStatus.NONE, range: Double = 18.0, radius: Double = 4.5,
            element: Int = 0, duration: Int = 60, ult: Boolean = false) = skill(name, icon, description, job.magic, motion,
                base, coefficient, mana, cd, startup, pulses, gain, spend, status, range, radius, element, duration, ult)
                .let { if (job == CoreClass.RANGER && coefficient > 0) it.copy(projectile = true) else it }
        return when (job) {
            CoreClass.WARRIOR -> listOf(
                s("踏み込み斬り", "dash", "敵の手前へ踏み込み、闘気を得る", CoreSkillMotion.LUNGE, 6.0, 1.2, cd=80, startup=5),
                s("叩きつけ", "slam", "正面の狭い範囲へ重撃。受け流し成功後は素早い反撃に変化", CoreSkillMotion.CONE, 14.0, 2.2, mana=20, cd=120, startup=12, gain=0, status=CoreSkillStatus.EXPOSE),
                s("薙ぎ払い", "whirl", "前方180度を一度で薙ぐ。正面の集団をまとめて捉える", CoreSkillMotion.CONE, 6.0, 1.4, mana=12, cd=70, startup=4, radius=4.2),
                s("受け流し", "war_guard", "攻撃直前に構えると叩きつけが再使用可能。攻撃すると構え解除", CoreSkillMotion.GUARD, 0.0, 0.0, mana=8, cd=120, startup=1, gain=0, duration=30),
                s("牽制斬り", "war_wound", "素早い一撃で敵を減速。間合いを取り直すための選択技", CoreSkillMotion.CONE, 3.0, .9, cd=65, startup=3, status=CoreSkillStatus.SLOW),
                s("返し刃", "war_counter", "闘気を使う強い返し。受け流し直後はさらに強化", CoreSkillMotion.CONE, 10.0, 2.8, cd=130, startup=4, gain=0, spend=30),
                s("雄叫び", "war_cry", "仲間に短い障壁。足を止めず次の交戦に備える", CoreSkillMotion.SHIELD, 12.0, .5, mana=18, cd=240, gain=0, radius=7.0, duration=100),
                s("破城突き", "war_breach", "長い踏み込み。横には狭いが防御を崩せる", CoreSkillMotion.LUNGE, 8.0, 1.5, mana=18, cd=160, startup=8, status=CoreSkillStatus.EXPOSE, range=4.0),
                s("天断", "war_ult", "闘気を解放し斬り上げ・返し・叩きつけ。途中の回避で中断可", CoreSkillMotion.CONE, 20.0, 2.4, mana=30, cd=600, startup=18, pulses=3, gain=0, spend=80, radius=7.0, ult=true),
                s("不屈の旗", "war_banner", "仲間全員に大きな障壁。自分は3秒防御姿勢", CoreSkillMotion.SHIELD, 50.0, 1.5, mana=25, cd=700, gain=0, spend=70, radius=10.0, duration=160, ult=true))
            CoreClass.MAGE -> listOf(
                s("火炎弾", "firebolt", "火の術式を刻む。直前と違う属性なら獲得量増加", CoreSkillMotion.RAY, 8.0, 1.4, cd=60, startup=5, element=1),
                s("霜の波紋", "frost_nova", "周囲を凍てつかせる。火や雷からつなぐと術式が育つ", CoreSkillMotion.NOVA, 10.0, 1.0, cd=110, startup=10, status=CoreSkillStatus.SLOW, element=2),
                s("流星雨", "meteor", "術式60を消費。狙った地点へ三度の流星", CoreSkillMotion.FIELD, 7.0, 1.3, mana=25, cd=180, startup=12, pulses=3, gain=0, spend=60, element=1),
                s("閃光歩", "mage_blink", "前方へ安全に転移。周囲の敵を遅らせる", CoreSkillMotion.EVADE, 0.0, 0.0, mana=15, cd=140, gain=0, status=CoreSkillStatus.SLOW, range=4.5, element=3),
                s("雷の刻印", "mage_mark", "射線に雷印を残す。起爆技は印を消費して強化", CoreSkillMotion.RAY, 5.0, 1.1, cd=80, status=CoreSkillStatus.MARK, element=3),
                s("氷の庭", "mage_garden", "狙った場所を凍結域にする。敵を留めて術式を組む", CoreSkillMotion.FIELD, 2.0, .45, mana=20, cd=180, pulses=4, status=CoreSkillStatus.SLOW, element=2, duration=120),
                s("術式起爆", "mage_burst", "術式を40消費。印のある敵なら威力+35%", CoreSkillMotion.NOVA, 12.0, 2.5, mana=20, cd=120, gain=0, spend=40, radius=6.0, element=3),
                s("魔力障壁", "mage_ward", "魔力で短い障壁を作る。攻撃に使うマナと競合する", CoreSkillMotion.SHIELD, 10.0, 1.2, mana=25, cd=200, gain=0, radius=0.0, duration=100, element=3),
                s("天火の大術式", "mage_ult", "術式80を消費。広い範囲に四度の大爆発", CoreSkillMotion.FIELD, 15.0, 2.0, mana=35, cd=640, startup=22, pulses=4, gain=0, spend=80, radius=7.0, element=1, ult=true),
                s("絶対零界", "mage_zero", "術式70を消費。広範囲を減速する五連波", CoreSkillMotion.NOVA, 7.0, 1.3, mana=30, cd=600, startup=12, pulses=5, gain=0, spend=70, status=CoreSkillStatus.SLOW, radius=8.0, element=2, duration=160, ult=true))
            CoreClass.RANGER -> listOf(
                s("狙い射ち", "pierce", "同じ獲物を狙い続けるほど集中を得る", CoreSkillMotion.RAY, 6.0, 1.7, cd=70, startup=8, radius=.5),
                s("霜矢の扇", "frost_fan", "前方の扇を減速。距離を作り、射線を確保する", CoreSkillMotion.CONE, 5.0, 1.0, cd=130, status=CoreSkillStatus.SLOW, radius=6.5, element=2),
                s("矢の嵐", "arrow_rain", "集中40を消費。地点へ四度の矢雨", CoreSkillMotion.FIELD, 3.0, 1.0, mana=22, cd=180, pulses=4, gain=0, spend=40),
                s("後退射撃", "hunt_retreat", "後ろへ退きながら矢を放つ。逃げと攻撃を両立", CoreSkillMotion.EVADE, 4.0, .8, cd=120, range=-3.5),
                s("貫き矢", "hunt_pierce", "集中30を消費して三体まで貫通", CoreSkillMotion.RAY, 8.0, 2.6, cd=110, startup=12, gain=0, spend=30, radius=.9),
                s("狩猟罠", "hunt_trap", "足元前方に減速と毒の罠。誘い込んで撃つ", CoreSkillMotion.FIELD, 3.0, .65, mana=18, cd=170, pulses=4, status=CoreSkillStatus.POISON, range=4.0, radius=3.5, duration=120),
                s("獲物の印", "hunt_mark", "遠くの獲物に印。集中消費技が印を使って強化", CoreSkillMotion.RAY, 3.0, .6, mana=8, cd=80, gain=25, status=CoreSkillStatus.MARK),
                s("速射", "hunt_volley", "集中20を使う三連射。照準を保つ必要がある", CoreSkillMotion.RAY, 2.0, .95, mana=14, cd=110, pulses=3, gain=0, spend=20),
                s("終の一矢", "hunt_ult", "集中80を消費する精密射撃。長い構えを守り抜く", CoreSkillMotion.RAY, 35.0, 8.0, mana=30, cd=600, startup=28, gain=0, spend=80, range=23.0, ult=true),
                s("狩場の支配者", "hunt_storm", "集中70を消費。広い範囲を六度撃ち、減速させる", CoreSkillMotion.FIELD, 5.0, 1.2, mana=32, cd=680, pulses=6, gain=0, spend=70, status=CoreSkillStatus.SLOW, radius=7.0, ult=true))
            CoreClass.ASSASSIN -> listOf(
                s("影刺し", "ass_stab", "敵へ接近し印を刻む。機会を得る", CoreSkillMotion.LUNGE, 4.0, 1.0, cd=65, startup=3, status=CoreSkillStatus.MARK, radius=3.0),
                s("断命", "ass_execute", "機会30を消費。印を消して威力を増す短い一撃", CoreSkillMotion.CONE, 10.0, 2.8, mana=16, cd=100, startup=4, gain=0, spend=30, radius=3.2),
                s("刃の輪", "ass_fan", "周囲に二度斬りつけ、複数の敵へ印を仕込む", CoreSkillMotion.SPIN, 3.0, .65, cd=130, pulses=2, status=CoreSkillStatus.MARK, radius=3.5),
                s("影抜け", "ass_escape", "後方へ離脱し、周囲を減速。移動を攻撃に使い切らない", CoreSkillMotion.EVADE, 0.0, 0.0, mana=10, cd=100, gain=0, status=CoreSkillStatus.SLOW, range=-4.0),
                s("毒牙", "ass_poison", "毒を仕込み、接近できない時間も削る", CoreSkillMotion.CONE, 3.0, .8, cd=80, status=CoreSkillStatus.POISON, radius=3.0),
                s("追影", "ass_chase", "遠めの敵へ飛び込む。離脱と同じ機会を消費", CoreSkillMotion.LUNGE, 8.0, 1.7, cd=130, gain=0, spend=20, range=5.0, radius=3.5),
                s("影の針", "ass_needle", "遠くから印を付ける。正面しか狙えないボスにも使える", CoreSkillMotion.RAY, 2.0, .75, cd=75, gain=12, status=CoreSkillStatus.MARK, range=12.0),
                s("残像", "ass_guard", "短時間だけ身を守る。早いタイミングで受けると機会獲得", CoreSkillMotion.GUARD, 0.0, 0.0, mana=12, cd=160, startup=1, gain=0, duration=20),
                s("無明連刃", "ass_ult", "機会80を消費し五連撃。印のある敵を先に狙う", CoreSkillMotion.SPIN, 8.0, 1.4, mana=25, cd=600, startup=8, pulses=5, gain=0, spend=80, radius=4.0, ult=true),
                s("死の契約", "ass_contract", "機会70を消費する刺突。防御を崩し次の追撃も通す", CoreSkillMotion.LUNGE, 30.0, 6.0, mana=25, cd=620, gain=0, spend=70, status=CoreSkillStatus.EXPOSE, range=5.0, ult=true))
            CoreClass.TEMPLAR -> listOf(
                s("裁きの槌", "temp_mace", "重い一撃で印を付け、決意を得る", CoreSkillMotion.CONE, 5.0, 1.0, cd=80, startup=7, status=CoreSkillStatus.MARK),
                s("集束", "temp_pull", "近くの敵を引き寄せる。ボスは移動せず短く行動阻害", CoreSkillMotion.PULL, 5.0, .6, mana=18, cd=170, gain=20, radius=6.0),
                s("護りの誓い", "temp_ward", "周囲の味方へ障壁。自分も防御姿勢を取る", CoreSkillMotion.SHIELD, 18.0, .7, mana=18, cd=200, gain=0, radius=7.0, duration=100),
                s("守護の構え", "temp_guard", "2.5秒防御。受けた攻撃から決意を得る", CoreSkillMotion.GUARD, 0.0, 0.0, mana=8, cd=130, startup=1, gain=0, duration=50),
                s("報復", "temp_rebuke", "決意40を使う反撃。集めた敵にまとめて当てる", CoreSkillMotion.NOVA, 12.0, 2.2, cd=120, gain=0, spend=40, radius=5.0),
                s("封鎖線", "temp_field", "敵を遅らせる陣。味方の射線を支える", CoreSkillMotion.FIELD, 2.0, .4, mana=18, cd=170, pulses=4, status=CoreSkillStatus.SLOW, range=6.0, radius=5.0),
                s("破魔槌", "temp_break", "敵のARとMRを短時間低下させる", CoreSkillMotion.CONE, 6.0, 1.3, cd=120, status=CoreSkillStatus.EXPOSE),
                s("援護突進", "temp_dash", "敵へ踏み込み、周囲の敵の狙いを自分に向ける", CoreSkillMotion.LUNGE, 4.0, .9, cd=120, range=4.0),
                s("空間圧縮", "temp_ult", "決意80を消費。広範囲を三度吸引してまとめる", CoreSkillMotion.PULL, 10.0, 1.4, mana=30, cd=640, startup=12, pulses=3, gain=0, spend=80, radius=9.0, ult=true),
                s("不落の聖域", "temp_sanctuary", "決意70を消費。仲間へ大きな障壁と自分へ防御姿勢", CoreSkillMotion.SHIELD, 70.0, 1.5, mana=30, cd=680, gain=0, spend=70, radius=10.0, duration=180, ult=true))
            CoreClass.HEALER -> listOf(
                s("裁きの光", "heal_light", "敵を撃ち信仰を得る。ソロでも主力になる", CoreSkillMotion.RAY, 8.0, 1.4, cd=60, startup=5, gain=20),
                s("救いの輪", "heal_ring", "信仰30を回復に回す。周囲の傷ついた仲間を救う", CoreSkillMotion.HEAL, 20.0, .9, mana=18, cd=120, gain=0, spend=30, radius=7.0),
                s("断罪の柱", "heal_pillar", "信仰40を攻撃に回す。回復と同じ資源を取り合う", CoreSkillMotion.FIELD, 8.0, 1.6, mana=18, cd=140, pulses=2, gain=0, spend=40, radius=4.0),
                s("光の歩み", "heal_step", "前へ移動し、周囲に小さな障壁を渡す", CoreSkillMotion.EVADE, 0.0, 0.0, mana=12, cd=140, gain=0, range=3.5),
                s("灯火", "heal_lamp", "地点に三度の光。敵を削り信仰を得る", CoreSkillMotion.FIELD, 3.0, .65, mana=16, cd=130, pulses=3, gain=20, radius=4.0),
                s("祈りの盾", "heal_shield", "信仰25を予防に回す。回復できない大技に備える", CoreSkillMotion.SHIELD, 20.0, 1.0, mana=16, cd=140, gain=0, spend=25, radius=7.0, duration=120),
                s("導きの印", "heal_mark", "敵へ印を刻み、信仰を得る。裁きの起点になる", CoreSkillMotion.RAY, 4.0, .8, mana=8, cd=70, gain=20, status=CoreSkillStatus.MARK),
                s("清めの風", "heal_wind", "信仰20で軽く回復し、自分の回避を再使用可能に", CoreSkillMotion.HEAL, 12.0, .5, mana=12, cd=120, gain=0, spend=20, radius=5.0),
                s("救済", "heal_ult", "信仰80を使い、周囲の仲間を大きく回復", CoreSkillMotion.HEAL, 70.0, 2.8, mana=30, cd=650, startup=16, gain=0, spend=80, radius=10.0, ult=true),
                s("最後の審判", "heal_judgment", "信仰80を攻撃へ全投入。四度の広域の裁き", CoreSkillMotion.FIELD, 12.0, 1.8, mana=30, cd=620, startup=18, pulses=4, gain=0, spend=80, radius=6.5, ult=true))
            CoreClass.STARWEAVER -> listOf(
                s("星糸", "star_thread", "星を解放。三蓄積なら三体まで貫通", CoreSkillMotion.RAY, 6.0, 1.4, cd=80, startup=5, gain=0),
                s("星環", "star_ring", "星を解放して減速。三蓄積ならマナ12回復", CoreSkillMotion.NOVA, 8.0, 1.1, cd=140, status=CoreSkillStatus.SLOW, gain=0),
                s("星降る夜", "starfall", "星を解放。三蓄積なら四回に増加", CoreSkillMotion.FIELD, 4.0, 1.1, mana=25, cd=200, pulses=3, gain=0),
                s("星渡り", "star_step", "前方へ星の道を渡る。攻撃と離脱の位置を作る", CoreSkillMotion.EVADE, 0.0, 0.0, mana=15, cd=140, gain=0, range=4.0),
                s("星の針", "star_needle", "星を消費せず印を付ける。次の解放を準備", CoreSkillMotion.RAY, 3.0, .65, mana=8, cd=65, gain=1, status=CoreSkillStatus.MARK),
                s("星雲", "star_cloud", "星を解放し、地点へ四度の減速波", CoreSkillMotion.FIELD, 2.0, .6, mana=20, cd=180, pulses=4, gain=0, status=CoreSkillStatus.SLOW),
                s("星衣", "star_shield", "星を解放して仲間を保護する", CoreSkillMotion.SHIELD, 15.0, 1.0, mana=18, cd=200, gain=0, radius=7.0, duration=120),
                s("星砕き", "star_break", "星を解放して印を起爆。集団より単体に強い", CoreSkillMotion.RAY, 10.0, 2.2, mana=20, cd=160, gain=0, spend=1),
                s("天球崩壊", "star_ult", "星3を使う五連星雨。星の残響ならさらに一回", CoreSkillMotion.FIELD, 12.0, 1.8, mana=30, cd=620, startup=16, pulses=5, gain=0, spend=3, radius=7.0, ult=true),
                s("星座の守り", "star_constellation", "星3を使い仲間へ大きな障壁を編む", CoreSkillMotion.SHIELD, 60.0, 2.0, mana=25, cd=680, gain=0, spend=3, radius=10.0, duration=160, ult=true))
        }
    }
}
