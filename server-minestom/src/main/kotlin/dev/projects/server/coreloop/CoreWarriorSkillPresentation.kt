package dev.projects.server.coreloop

/** Presentation only: values still come from the modified skill and live combat sheet. */
internal object CoreWarriorSkillPresentation {
    data class Identity(val shortName: String, val role: String, val use: String)
    val skills = mapOf(
        "dash" to Identity("轟進", "つなぎ・突進", "突っ込んで敵を怯ませ、闘気を得る"),
        "slam" to Identity("地割り", "大技・重撃", "闘気を注いで地を割る。3段目の直後なら速い"),
        "whirl" to Identity("押し薙ぎ", "大技・集団", "前方の集団を薙いで押し出す。背後には届かない"),
        "war_guard" to Identity("根張り", "状況・耐える", "避けられない攻撃を踏みとどまって受け、闘気に変える"),
        "war_wound" to Identity("地走り", "状況・足止め", "亀裂を走らせて近づけない敵の足を止める"),
        "war_counter" to Identity("返し刃", "反撃・重撃", "受け流し直後の好機を攻撃に変える"),
        "war_cry" to Identity("雄叫び", "仲間・障壁", "交戦前に仲間を守る"),
        "war_breach" to Identity("破城突き", "接近・崩し", "踏み込んで敵の防御を崩す"),
        "war_ult" to Identity("鉄槌", "奥義・三連", "赤熱の大剣を三度振り下ろし、敵を打ち上げる"),
        "war_banner" to Identity("不屈の旗", "奥義・守護", "仲間への大きな障壁と自分の防御")
    )
    // Rows: enter or heavy / crowd or reach / hold. Retired catalog entries (5, 6, 7) are not offered.
    val candidateOrder = listOf(0, 1, 2, 4, 3)
    val candidateSlots = listOf(9, 14, 18, 23, 27, 32, 36, 41)
    val loadoutSlots = listOf(0, 2, 4, 6, 8)

    fun combatCue(job: CoreClass, tick: Long, counterUntil: Long, guardUntil: Long, rampageUntil: Long = -1): String {
        if(job!=CoreClass.WARRIOR) return ""
        if(rampageUntil>tick) return "赤熱 ${((rampageUntil-tick+19)/20).coerceAtLeast(1)}秒"
        if(guardUntil>=tick) return "根張り ${((guardUntil-tick+19)/20).coerceAtLeast(1)}秒"
        return ""
    }

    fun tooltip(s: CoreSkillDefinition, sheet: CoreCombatSheet, j: CoreJourney): List<String> {
        val identity = skills.getValue(s.icon)
        val mechanical = s.tooltip(sheet, j)
        return buildList {
            add(identity.role)
            add(identity.use)
            add("")
            add(if(s.motion == CoreSkillMotion.GUARD) "防御" else if(s.motion == CoreSkillMotion.SHIELD) "障壁" else "物理ダメージ")
            // Keep formula, actual modified values, defence caveats and all status details.
            addAll(mechanical.drop(2).take(3))
            add("")
            addAll(mechanical.drop(5))
            if(s.description.contains("適用")) { add(""); add(s.description.substringAfter(" / ")) }
        }
    }
}
