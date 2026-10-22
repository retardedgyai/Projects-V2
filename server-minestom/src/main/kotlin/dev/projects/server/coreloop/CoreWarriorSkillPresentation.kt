package dev.projects.server.coreloop

/** Presentation only: values still come from the modified skill and live combat sheet. */
internal object CoreWarriorSkillPresentation {
    data class Identity(val shortName: String, val role: String, val use: String)
    val skills = mapOf(
        "dash" to Identity("轟進", "つなぎ・突進", "突っ込んで敵を怯ませ、獣を溜める"),
        "slam" to Identity("地割り", "獣を使う・重撃", "獣を全部使って地を割る。3段目の直後なら速い"),
        "whirl" to Identity("狂乱", "獣を使う・連撃", "踏み込みながら振り続け、最後に振り下ろす"),
        "war_guard" to Identity("天落とし", "獣を使う・跳躍", "高く跳んで叩き落とし、衝撃の輪を広げる"),
        "war_wound" to Identity("跳び叩き", "移動・着地", "狙った地点へ跳び、着地の周りを叩いて止める"),
        "war_counter" to Identity("大地崩し", "獣を使う・範囲", "突き立てて周り一面を砕き、まとめて打ち上げる"),
        "war_cry" to Identity("雄叫び", "仲間・障壁", "交戦前に仲間を守る"),
        "war_breach" to Identity("破城突き", "接近・崩し", "踏み込んで敵の防御を崩す"),
        "war_ult" to Identity("鉄槌", "奥義・三連", "大剣を三度振り下ろし、敵を打ち上げる"),
        "war_banner" to Identity("不屈の旗", "奥義・守護", "仲間への大きな障壁と自分の防御")
    )
    // Rows: enter or heavy / crowd or reach / hold. Retired catalog entries (5, 6, 7) are not offered.
    val candidateOrder = listOf(0, 1, 4, 2, 3, 5)
    val candidateSlots = listOf(9, 14, 18, 23, 27, 32, 36, 41)
    val loadoutSlots = listOf(0, 2, 4, 6, 8)

    fun combatCue(job: CoreClass, tick: Long, counterUntil: Long, guardUntil: Long): String {
        if(job!=CoreClass.WARRIOR) return ""
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
