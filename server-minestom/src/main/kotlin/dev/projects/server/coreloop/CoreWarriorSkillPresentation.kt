package dev.projects.server.coreloop

/** Presentation only: values still come from the modified skill and live combat sheet. */
internal object CoreWarriorSkillPresentation {
    data class Identity(val shortName: String, val role: String, val use: String)
    val skills = mapOf(
        "dash" to Identity("踏込斬り", "状況技・接近", "間合いを詰めて防御を崩し、闘気を得る"),
        "slam" to Identity("叩きつけ", "主力・重撃", "闘気で狭い正面へ重撃。単体と大物へ"),
        "whirl" to Identity("薙ぎ払い", "主力・集団", "闘気で前方の集団を一度に薙ぐ。背後には届かない"),
        "war_guard" to Identity("踏ん張り", "状況技・耐える", "避けられない攻撃を構えて受け、闘気に変える"),
        "war_wound" to Identity("剣風", "状況技・距離", "近づけない敵へ細い斬撃を飛ばし、闘気を得る"),
        "war_counter" to Identity("返し刃", "反撃・重撃", "受け流し直後の好機を攻撃に変える"),
        "war_cry" to Identity("雄叫び", "仲間・障壁", "交戦前に仲間を守る"),
        "war_breach" to Identity("破城突き", "接近・崩し", "踏み込んで敵の防御を崩す"),
        "war_ult" to Identity("天断", "奥義・三連", "斬り上げ、返し、断撃へつなぐ"),
        "war_banner" to Identity("不屈の旗", "奥義・守護", "仲間への大きな障壁と自分の防御")
    )
    // Rows: enter or heavy / crowd or reach / hold. Retired catalog entries (5, 6, 7) are not offered.
    val candidateOrder = listOf(0, 1, 2, 4, 3)
    val candidateSlots = listOf(9, 14, 18, 23, 27, 32, 36, 41)
    val loadoutSlots = listOf(0, 2, 4, 6, 8)

    fun combatCue(job: CoreClass, tick: Long, counterUntil: Long, guardUntil: Long): String {
        if(job!=CoreClass.WARRIOR) return ""
        if(guardUntil>=tick) return "踏ん張り ${((guardUntil-tick+19)/20).coerceAtLeast(1)}秒"
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
