package dev.projects.server.coreloop

/** Presentation only: values still come from the modified skill and live combat sheet. */
internal object CoreWarriorSkillPresentation {
    data class Identity(val shortName: String, val role: String, val use: String)
    val skills = mapOf(
        "dash" to Identity("轟進", "獣を溜める・突進", "一気に走り抜けて、すれ違う敵を斬る"),
        "slam" to Identity("地割り", "獣を使う・重撃", "獣を全部使って地を割る。獣3つで全力の一振り"),
        "whirl" to Identity("狂乱", "獣を使う・連撃", "踏み込みながら振り続け、最後に振り下ろす"),
        "war_guard" to Identity("衝撃", "獣を溜める・連打", "地面に打ち込み、次の8振りに2発目の衝撃が乗る"),
        "war_wound" to Identity("跳び叩き", "移動・着地", "狙った地点へ跳び、着地の周りを叩いて止める"),
        "war_counter" to Identity("大地崩し", "使えない技", "（引退）"),
        "war_cry" to Identity("受け止め", "獣を溜める・守り", "シールドで受けるほど大剣が熱く、殴りが強くなる"),
        "war_breach" to Identity("斬り上げ", "獣を溜める・打ち上げ", "真上へ振り上げて打ち上げ、地割りで叩き落とす"),
        "war_ult" to Identity("全力一振", "使えない技", "（地割りの獣3つになった）"),
        "war_banner" to Identity("狂戦士甲冑", "ウルト・暴走", "10秒、CCも怯みも効かず速く、獣が2倍で溜まる"),
        "war_awaken" to Identity("獣の目覚め", "ウルト・超重力", "獣3つで8秒。障壁と硬さ、周りの敵を遅くして引き寄せる")
    )
    // Rows: enter or heavy / crowd or reach / hold. The retired 大地崩し (5) is not offered.
    val candidateOrder = listOf(0, 1, 4, 7, 6, 2, 3)
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
            if (s.icon == CoreWarriorRules.ARMOR || s.icon == CoreWarriorRules.AWAKEN || s.icon == CoreWarriorRules.BRACE) {
                // Self buffs: their catalog tooltip is already short (no damage, no shield for allies).
                add("自分の強化")
                addAll(mechanical.drop(1))
                return@buildList
            }
            add(if(s.motion == CoreSkillMotion.GUARD) "防御" else if(s.motion == CoreSkillMotion.SHIELD) "障壁" else "物理ダメージ")
            // Keep formula, actual modified values, defence caveats and all status details.
            addAll(mechanical.drop(2).take(3))
            add("")
            addAll(mechanical.drop(5))
            if(s.description.contains("適用")) { add(""); add(s.description.substringAfter(" / ")) }
        }
    }
}
