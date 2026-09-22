package dev.projects.server.coreloop

/** Presentation only: values still come from the modified skill and live combat sheet. */
internal object CoreWarriorSkillPresentation {
    data class Identity(val shortName: String, val role: String, val use: String)
    val skills = mapOf(
        "dash" to Identity("踏込斬り", "接近・始動", "間合いを詰めて闘気を得る"),
        "slam" to Identity("叩きつけ", "重撃・反撃", "狭い正面へ重撃。受け流し成功で素早く返す"),
        "whirl" to Identity("薙ぎ払い", "集団・横薙ぎ", "前方の集団を一度で薙ぐ。背後には届かない"),
        "war_guard" to Identity("受け流し", "防御・好機", "敵の攻撃直前に構え、反撃へつなぐ"),
        "war_wound" to Identity("牽制斬り", "減速・間合い", "追ってくる敵を減速し、間合いを取り直す"),
        "war_counter" to Identity("返し刃", "反撃・重撃", "受け流し直後の好機を攻撃に変える"),
        "war_cry" to Identity("雄叫び", "仲間・障壁", "交戦前に仲間を守る"),
        "war_breach" to Identity("破城突き", "接近・崩し", "踏み込んで敵の防御を崩す"),
        "war_ult" to Identity("天断", "奥義・三連", "斬り上げ、返し、断撃へつなぐ"),
        "war_banner" to Identity("不屈の旗", "奥義・守護", "仲間への大きな障壁と自分の防御")
    )
    // Each row contrasts a way to enter a fight with a payoff or protection.
    val candidateOrder = listOf(0, 2, 3, 1, 4, 5, 7, 6)
    val candidateSlots = listOf(9, 14, 18, 23, 27, 32, 36, 41)
    val loadoutSlots = listOf(0, 2, 4, 6, 8)

    fun combatCue(job: CoreClass, tick: Long, counterUntil: Long, guardUntil: Long): String {
        if(job!=CoreClass.WARRIOR) return ""
        if(counterUntil>=tick) return "反撃の好機 ${((counterUntil-tick+19)/20).coerceAtLeast(1)}秒"
        if(guardUntil>=tick) return "防御 ${((guardUntil-tick+19)/20).coerceAtLeast(1)}秒"
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
