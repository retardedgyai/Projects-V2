package dev.projects.server.coreloop

/** Presentation only: values still come from the modified skill and live combat sheet. */
internal object CoreWarriorSkillPresentation {
    data class Identity(val shortName: String, val role: String, val use: String)
    val skills = mapOf(
        "dash" to Identity("体当たり", "獣を溜める・突進", "殴られそうな時に突っ込み、1発受け止めると獣が2つ"),
        "slam" to Identity("地割り", "獣を使う・重撃", "獣を全部使って地を割る。獣3つなら担いで溜める全力の一振り、裂け目から血が噴く"),
        "whirl" to Identity("狂乱", "獣を使う・連撃", "踏み込みながら振り続け、最後に振り下ろす"),
        "war_guard" to Identity("天落とし", "獣を使う・跳躍", "高く跳んで叩き落とし、衝撃の輪を広げる"),
        "war_wound" to Identity("跳び叩き", "移動・着地", "狙った地点へ跳び、着地の周りを叩いて止める"),
        "war_counter" to Identity("大地崩し", "獣を使う・範囲", "突き立てて周り一面を砕き、まとめて打ち上げる"),
        "war_cry" to Identity("引き摺り", "獣を溜める・巻き込み", "大剣を引きずって歩き、引っかけた敵をまとめて叩きつける"),
        "war_breach" to Identity("斬り上げ", "獣を溜める・打ち上げ", "真上へ振り上げて打ち上げ、地割りで叩き落とす"),
        // Retired as an ultimate (2026-10-27): never offered; the name stays for old art and saves.
        "war_ult" to Identity("全力一振", "地割りに統合", "獣3つの地割りが全力の一振り"),
        "war_banner" to Identity("狂戦士甲冑", "奥義・暴走", "10秒、CCも怯みも効かず速く、獣が満ちたまま"),
        "war_awaken" to Identity("獣の目覚め", "奥義・解放", "獣3つで撃つ。8秒、獣が減らず使う技の再使用が半分")
    )
    // Rows: enter or heavy / crowd or reach / hold. Retired catalog entries (5, 6, 7) are not offered.
    val candidateOrder = listOf(0, 1, 4, 2, 3, 5)   // 引き摺り (6) and 斬り上げ (7) are retired
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
