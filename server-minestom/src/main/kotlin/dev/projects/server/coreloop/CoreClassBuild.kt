package dev.projects.server.coreloop

/** Stable catalog indices; four distinct skills, one ultimate, and a prerequisite-checked tree. */
data class CoreClassBuild(val first: Int = 0, val second: Int = 1, val third: Int = 2, val fourth: Int = 3,
    val ultimate: Int = 0, val nodes: Int = 0) {
    val skills get() = listOf(first, second, third, fourth)
    init {
        require(skills.all { it in 0..7 } && skills.distinct().size == 4 && ultimate in 0..1)
        require(nodes in 0 until (1 shl 18))
        for (i in 0..17) if (has(i)) require(CoreClassTrees.parents(i).isEmpty() || CoreClassTrees.parents(i).any(::has)) { "接続する前の技能が必要です" }
        require(listOf(2, 5, 8).count(::has) <= 1) { "大成技能は一つだけ選べます" }
    }
    fun has(index: Int) = nodes and (1 shl index) != 0
    val points get() = Integer.bitCount(nodes)
    val keystone get() = listOf(2, 5, 8).indexOfFirst(::has)
    fun equip(slot: Int, choice: Int): CoreClassBuild {
        require(slot in 0..4)
        if (slot == 4) return copy(ultimate = choice)
        require(choice in 0..7)
        val next = skills.toMutableList()
        val oldSlot = next.indexOf(choice)
        if (oldSlot >= 0) next[oldSlot] = next[slot]
        next[slot] = choice
        return copy(first = next[0], second = next[1], third = next[2], fourth = next[3])
    }
    fun toggle(index: Int, budget: Int): CoreClassBuild {
        require(index in 0..17)
        val mask = if (has(index)) {
            var bits = nodes and (1 shl index).inv()
            // Fixed point: a merge remains connected while either learned route reaches it.
            do {
                val previous = bits
                for (i in 0..17) if (bits and (1 shl i) != 0 && CoreClassTrees.parents(i).isNotEmpty() &&
                    CoreClassTrees.parents(i).none { bits and (1 shl it) != 0 }) bits = bits and (1 shl i).inv()
            } while (previous != bits)
            bits
        } else nodes or (1 shl index)
        return copy(nodes = mask).also { require(it.points <= budget) { "技能ポイントが足りません" } }
    }
}

data class CoreTalentNode(val name: String, val description: String)

object CoreClassTrees {
    fun signature(job: CoreClass) = if(job==CoreClass.TEMPLAR)6 else 2
    fun budget(journey: CoreJourney) = if (journey.legacy) 12 else (2 + journey.level / 4).coerceAtMost(12)
    fun parents(i: Int): List<Int> {
        require(i in 0..17)
        val branch = if (i < 9) i / 3 else (i - 9) / 3
        val start = branch * 3
        val side = 9 + branch * 3
        return when(i) {
            start -> emptyList()
            start + 1, side -> listOf(start)
            side + 1 -> listOf(start + 1)
            side + 2 -> listOf(side)
            else -> listOf(side + 1, side + 2)
        }
    }
    fun slot(i: Int): Int {
        val b=if(i<9)i/3 else (i-9)/3
        return when(i) { b*3 -> 10+b*3; b*3+1 -> 18+b*3; b*3+2 -> 37+b*3;
            9+b*3 -> 20+b*3; 10+b*3 -> 27+b*3; else -> 29+b*3 }
    }
    /** Old nine-node choices are refunded; validate the original contract before migration. */
    fun refundLegacyMask(mask: Int): Int {
        require(mask in 0..511)
        for(i in 0..8) if(mask and (1 shl i)!=0 && i%3>0) require(mask and (1 shl (i-1))!=0)
        require(listOf(2,5,8).count { mask and (1 shl it)!=0 }<=1)
        return 0
    }
    fun nodes(job: CoreClass): List<CoreTalentNode> {
        val keys = when (job) {
            CoreClass.WARRIOR -> listOf("狂戦士" to "闘気60以上：威力+20%、被ダメージ+10%", "剣聖" to "受け流し成功後、叩きつけ・返し刃の反撃威力+60%", "旗手" to "防御技で周囲の仲間にも小さな障壁")
            CoreClass.MAGE -> listOf("連鎖術式" to "起爆技が1回増えるが、1回の威力-15%", "対位法" to "違う属性の技を当てると術式をさらに15獲得", "氷の賢者" to "氷術の減速を強化し、命中時に自分へ障壁")
            CoreClass.RANGER -> listOf("狙撃手" to "2秒静止すると威力+25%。動くと解除", "遊撃手" to "回避後3秒、通常射撃が二連射（各65%）", "罠師" to "罠と設置技が1回増え、減速時間も延長")
            CoreClass.ASSASSIN -> listOf("処刑人" to "印を消費する技はHP35%以下の敵へ威力+40%", "影渡り" to "印を消費すると回避が再使用可能", "毒刃" to "印のある敵への通常攻撃が3秒の毒を付与")
            CoreClass.TEMPLAR -> listOf("断罪者" to "障壁を得る技で次の攻撃を強化。威力+35%", "重力の主" to "吸引が広がる。ボスには防御低下を付与", "守護聖人" to "味方への障壁+40%、自分への威力-10%")
            CoreClass.HEALER -> listOf("審判者" to "回復の半分を障壁に変え、裁きの威力+25%", "命の循環" to "実際に味方を回復すると信仰を回収（1回上限15）", "灯守" to "救護技で味方の移動速度+20%を3秒付与")
            CoreClass.STARWEAVER -> listOf("星の残響" to "三蓄積で解放する技が1回増える（各85%）", "星巡り" to "移動技でも星を一つ編む", "守り星" to "三蓄積の解放で周囲の味方に障壁")
        }
        val core = keys.flatMapIndexed { branch, (name, text) -> listOf(
            CoreTalentNode(listOf("鍛えた一撃", "巧みな準備", "生存の心得")[branch], listOf("直接ダメージ+8%", "固有資源の獲得+20%", "最大HP+10%")[branch]),
            CoreTalentNode(listOf("力の制御", "身のこなし", "備えの技")[branch], listOf("固有資源の消費-15%", "回避の再使用時間-20%", "シールド量+20%")[branch]),
            CoreTalentNode(name, "$text / 大成技能は全体で一つ")) }
        val skills = CoreSkillCatalog.skills(job)
        val signature = skills[signature(job)].name
        val opener = skills[0].name
        val movement = skills[3].name
        val support = skills[5].name
        return core + listOf(
            CoreTalentNode("広域化", "$signature：範囲+30%、威力-10%"),
            CoreTalentNode("重ね撃ち", "$signature：追加発動1回、各回の威力75%"),
            CoreTalentNode("高速始動", "$opener：発生時間-30%、獲得資源+25%"),
            CoreTalentNode("軽やかな構え", "$movement：再使用時間-25%"),
            CoreTalentNode("始動の備え", "$opener：マナ消費-40%、獲得資源+25%"),
            CoreTalentNode("技の循環", "$signature：再使用時間-20%、威力-10%"),
            CoreTalentNode("広い備え", "$support：範囲+1.5m、マナ消費-40%"),
            CoreTalentNode("大技への備え", "奥義：固有資源の消費-20%"),
            CoreTalentNode("巡り来る奥義", "奥義：再使用時間-15%、威力-10%")
        )
    }
}
