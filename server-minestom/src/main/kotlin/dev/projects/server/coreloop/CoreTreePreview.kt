package dev.projects.server.coreloop

/** Presentation data has stable IDs and explicit edges; the view does not depend on slots or bit masks. */
internal data class TreePreviewNode(
    val id: String, val name: String, val description: String, val parents: List<String>,
    val icon: String, val x: Double, val y: Double, val kind: String,
    val learned: Boolean, val original: Boolean, val available: Boolean,
)
internal data class TreePreviewMetric(val label: String, val current: Double, val after: Double, val unit: String)
internal data class TreePreviewSkill(val name: String, val icon: String, val equipped: Boolean, val metrics: List<TreePreviewMetric>)
internal data class TreePreviewSnapshot(
    val job: String, val level: Int, val budget: Int, val spent: Int, val originalSpent: Int,
    val nodes: List<TreePreviewNode>, val selected: String, val reason: String,
    val canToggle: Boolean, val changed: Boolean, val skills: List<TreePreviewSkill>,
    val health: TreePreviewMetric, val damage: TreePreviewMetric,
)

/** An immutable account snapshot plus an in-memory trial. There is deliberately no ledger/save callback. */
internal class CoreTreePreview(private val original: CoreAccount) {
    private val journey = original.journey
    private val budget = CoreClassTrees.budget(journey)
    private val catalog = CoreClassTrees.nodes(journey.job)
    private val ids = catalog.indices.map { "${journey.job.name.lowercase()}:$it" }
    private val rawSheet = CoreCombatSheet.from(CoreCombatGear.from(original), CoreAffixCatalog.stats(original))
    var trial: CoreClassBuild = journey.build
        private set
    var selected: String = ids.firstOrNull { index ->
        val i = ids.indexOf(index)
        !trial.has(i) && runCatching { trial.toggle(i, budget) }.isSuccess
    } ?: ids.first()
        private set

    fun select(id: String): Boolean {
        if (id !in ids) return false
        selected = id
        return true
    }

    fun toggle(): Boolean {
        val index = ids.indexOf(selected)
        val next = runCatching { trial.toggle(index, budget) }.getOrNull() ?: return false
        trial = next
        return true
    }

    fun reset() { trial = journey.build }

    fun snapshot(): TreePreviewSnapshot {
        val selectedIndex = ids.indexOf(selected)
        val change = runCatching { trial.toggle(selectedIndex, budget) }
        // Selection shows the proposed acquisition. After trial acquisition, keep that result visible.
        val comparison = if (!trial.has(selectedIndex)) change.getOrNull() ?: trial else trial
        val beforeJourney = journey
        val afterJourney = journey.copy(build = comparison)
        val beforeSheet = rawSheet.specialize(beforeJourney)
        val afterSheet = rawSheet.specialize(afterJourney)
        fun metric(label: String, before: Double, after: Double, unit: String = "") = TreePreviewMetric(label, before, after, unit)
        val skills = CoreSkillCatalog.skills(journey.job).mapIndexedNotNull { index, raw ->
            val before = CoreSkillCatalog.modify(raw, beforeJourney, beforeSheet.mods)
            val after = CoreSkillCatalog.modify(raw, afterJourney, afterSheet.mods)
            val metrics = listOf(
                metric("1回の基準値", before.preview(beforeSheet), after.preview(afterSheet)),
                metric("発動回数", before.pulses.toDouble(), after.pulses.toDouble(), "回"),
                metric("範囲", before.radius, after.radius, "m"),
                metric("再使用", before.cooldownTicks(beforeSheet.mods) / 20.0, after.cooldownTicks(afterSheet.mods) / 20.0, "秒"),
                metric("発生", before.startupTicks(beforeSheet) / 20.0, after.startupTicks(afterSheet) / 20.0, "秒"),
                metric("マナ", before.mana.toDouble(), after.mana.toDouble()),
                metric("必要資源", before.spend.toDouble(), after.spend.toDouble()),
                metric("命中時資源", before.gain.toDouble(), after.gain.toDouble()),
            )
            if (metrics.all { it.current == it.after }) null else TreePreviewSkill(raw.name, raw.icon,
                if (raw.ultimate) index == 8 + trial.ultimate else index in trial.skills, metrics.filter { it.current != it.after })
        }
        val sourceSkills = CoreSkillCatalog.skills(journey.job)
        val nodes = catalog.mapIndexed { i, node ->
            val branch = if (i < 9) i / 3 else (i - 9) / 3
            val position = if (i < 9) i % 3 else 3 + (i - 9) % 3
            val center = 250.0 + branch * 250
            val (x, y) = when (position) {
                0 -> center to 320.0
                1 -> center - 52 to 425.0
                2 -> center to 625.0
                3 -> center + 52 to 425.0
                4 -> center - 52 to 525.0
                else -> center + 52 to 525.0
            }
            val skillIndex = when (i) {
                2, 5, 8 -> 8 + i / 3 % 2
                9, 10, 14 -> CoreClassTrees.signature(journey.job)
                11, 13 -> 0
                12 -> 3
                15 -> 5
                16, 17 -> 9
                else -> i.coerceAtMost(7)
            }
            TreePreviewNode(ids[i], node.name, node.description, CoreClassTrees.parents(i).map(ids::get),
                sourceSkills[skillIndex].icon, x, y, if (i in listOf(2, 5, 8)) "keystone" else "passive",
                trial.has(i), journey.build.has(i), runCatching { trial.toggle(i, budget) }.isSuccess)
        }
        val reason = change.exceptionOrNull()?.message ?: if (trial.has(selectedIndex)) "返還すると、接続を失う先も戻ります" else "1ポイントで試せます"
        return TreePreviewSnapshot(journey.job.displayName, journey.level, budget, trial.points, journey.build.points,
            nodes, selected, reason, change.isSuccess, trial != journey.build, skills,
            metric("最大HP", beforeSheet.health, afterSheet.health),
            metric("直接威力補正", beforeSheet.mods.damagePercent, afterSheet.mods.damagePercent, "%"))
    }
}
