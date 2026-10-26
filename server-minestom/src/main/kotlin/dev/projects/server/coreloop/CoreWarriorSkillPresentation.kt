package dev.projects.server.coreloop

/** Presentation only: values still come from the modified skill and live combat sheet. */
internal object CoreWarriorSkillPresentation {
    /** [gridName]: the label on the three-wide candidate tile (34 px), the okurigana dropped where four kana do not fit. */
    data class Identity(val shortName: String, val role: String, val use: String, val gridName: String = shortName)
    val skills = mapOf(
        "dash" to Identity("轟進", "つなぎ・突進", "突っ込んで敵を怯ませ、獣を溜める"),
        "slam" to Identity("地割り", "獣を使う・重撃", "獣を全部使って地を割る。3段目の直後なら速い"),
        "whirl" to Identity("狂乱", "獣を使う・連撃", "踏み込みながら振り続け、最後に振り下ろす"),
        "war_guard" to Identity("天落とし", "獣を使う・跳躍", "高く跳んで叩き落とし、衝撃の輪を広げる", "天落し"),
        "war_wound" to Identity("跳び叩き", "移動・着地", "狙った地点へ跳び、着地の周りを叩いて止める", "跳叩き"),
        "war_counter" to Identity("大地崩し", "獣を使う・範囲", "突き立てて周り一面を砕き、まとめて打ち上げる", "大地崩"),
        "war_cry" to Identity("引き摺り", "獣を溜める・巻き込み", "大剣を引きずって歩き、引っかけた敵をまとめて叩きつける", "引摺り"),
        "war_breach" to Identity("斬り上げ", "獣を溜める・打ち上げ", "真上へ振り上げて打ち上げ、地割りで叩き落とす", "斬上げ"),
        "war_ult" to Identity("全力一振", "奥義・一撃", "担いで溜め、渾身の一振りで地面を大きく割る"),
        "war_banner" to Identity("狂戦士甲冑", "奥義・暴走", "10秒、CCも怯みも効かず速く、獣が満ちたまま"),
        "war_awaken" to Identity("獣の目覚め", "奥義・解放", "獣3つで撃つ。8秒、獣が減らず使う技の再使用が半分"),
        CoreWarriorRules.KIZAMI to Identity("刻み打ち", "獣を溜める・連打", "振りの直後に押し直して刻む。4振り刻めば獣がもう1つ", "刻打ち")
    )
    // Three across: the building skills first (enter, leap, launch / drag, rhythm), then the four spenders.
    val candidateOrder = listOf(0, 4, 7, 6, 11, 1, 2, 3, 5)
    val candidateSlots = listOf(9, 12, 15, 18, 21, 24, 27, 30, 33)
    const val CANDIDATE_SPAN = 3
    /** The three ultimates keep the wide tiles their long names need. */
    val ultimateSlots = listOf(9, 14, 18)
    const val ULTIMATE_SPAN = 4
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
