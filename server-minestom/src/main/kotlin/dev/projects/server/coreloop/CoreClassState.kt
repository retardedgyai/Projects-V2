package dev.projects.server.coreloop

import java.util.UUID
import kotlin.math.ceil

/** Per-player combat decisions, reset at every instance exit/death; no packet supplied targets. */
internal class CoreClassState {
    var resource = 0.0
        private set
    var focus: UUID? = null
        private set
    var focusHits = 0
        private set
    var lastElement = 0
        private set
    var counterUntil = -1L
    var guardUntil = -1L
    var perfectUntil = -1L
    var dodgeUntil = -1L
    var shield = 0.0
    var shieldUntil = -1L
    var stationarySince = 0L
    private var lastGuardGain = -100L
    private var lastNormalGain = -1L
    private val marks = mutableMapOf<UUID, Long>()
    fun cap(job: CoreClass) = when (job) { CoreClass.WARRIOR -> CoreWarriorRules.BEAST_MAX; else -> 100.0 }
    fun gain(value: Double, job: CoreClass, build: CoreClassBuild) {
        val multiplier = if (build.has(3)) 1.2 else 1.0
        resource = (resource + value * multiplier).coerceIn(0.0, cap(job))
    }
    /** Warrior spender: takes every whole beast stack (a part-built one stays); returns how many. */
    fun takeStacks(): Int = resource.toInt().also { resource -= it }
    /** Dev cast and labs: everything full. */
    fun fill(job: CoreClass) { resource = cap(job) }
    /** Takes [amount] only when it is all there; never leaves a partial spend. */
    fun consume(amount: Double): Boolean = (resource >= amount).also { if (it) resource -= amount }
    fun cost(skill: CoreSkillDefinition, build: CoreClassBuild): Int = skill.spend
    fun canCast(skill: CoreSkillDefinition, build: CoreClassBuild) = resource >= cost(skill, build)
    fun spend(skill: CoreSkillDefinition, job: CoreClass, build: CoreClassBuild): Double {
        check(canCast(skill, build))
        val before = resource
        resource -= cost(skill, build)
        return before
    }
    fun normalHit(job: CoreClass, target: UUID, tick: Long, build: CoreClassBuild) {
        if (lastNormalGain == tick) return
        lastNormalGain = tick
        if (job == CoreClass.RANGER) {
            if (focus != null && focus != target) resource *= .5
            focusHits = if (focus == target) (focusHits + 1).coerceAtMost(5) else 1
            focus = target
        }
        gain(when (job) { CoreClass.WARRIOR -> 0.0; CoreClass.RANGER -> 10.0 + focusHits;
            CoreClass.ASSASSIN -> 12.0; CoreClass.TEMPLAR -> 8.0; CoreClass.HEALER -> 8.0; CoreClass.MAGE -> 5.0 }, job, build)
    }
    fun skillHit(job: CoreClass, skill: CoreSkillDefinition, build: CoreClassBuild) {
        if (job == CoreClass.WARRIOR) return  // the beast is built by crowd control and break (CorePlayerCombat)
        var amount = skill.gain.toDouble()
        if (job == CoreClass.MAGE && skill.gain > 0 && skill.element != 0) {
            if (lastElement != 0 && skill.element != lastElement) amount += if (build.keystone == 1) 30 else 15
            lastElement = skill.element
        }
        gain(amount, job, build)
    }
    fun guarded(job: CoreClass, tick: Long, build: CoreClassBuild) {
        if (tick - lastGuardGain < 10) return
        lastGuardGain = tick
        if (job != CoreClass.WARRIOR) gain(if (tick <= perfectUntil) 30.0 else 15.0, job, build)
        // Only the templar turns a read into a retaliation; the warrior guard just holds the line (D06).
        if (tick <= perfectUntil && job == CoreClass.TEMPLAR) counterUntil = tick + 60
    }
    fun mark(id: UUID, tick: Long) { marks[id] = tick + 120 }
    fun marked(id: UUID, tick: Long) = (marks[id] ?: -1) >= tick
    fun markRemaining(id: UUID, tick: Long): Long = marks[id]?.let { if(it>=tick)(it-tick).coerceAtLeast(1) else 0 } ?: 0
    fun consumeMark(id: UUID, tick: Long): Boolean = marked(id, tick).also { if (it) marks.remove(id) }
    fun tick(tick: Long) { marks.entries.removeIf { it.value < tick }; if (tick >= shieldUntil) shield = 0.0 }
    fun reset() {
        resource = 0.0; focus = null; focusHits = 0; lastElement = 0; counterUntil = -1; guardUntil = -1
        perfectUntil = -1; dodgeUntil = -1; shield = 0.0; shieldUntil = -1; stationarySince = 0
        lastGuardGain = -100; lastNormalGain = -1; marks.clear()
    }
}
