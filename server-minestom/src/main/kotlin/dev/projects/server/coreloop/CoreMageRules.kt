package dev.projects.server.coreloop

import net.minestom.server.coordinate.Point
import java.util.UUID

/**
 * The mage (Creator 2026-10-27, docs/development/mage-rework.md): fire, ice, lightning and wind. Every spell is one
 * button; what makes it a mage is what happens after: spells left on the ground mix by themselves, and every cast
 * carves its element into the sigil circle under the mage's feet for the grand chant (大詠唱) to release.
 */
internal enum class MageElement(val label: String, val color: Int) {
    FIRE("火", 0xff9438), ICE("氷", 0x82daff), LIGHTNING("雷", 0xffe066), WIND("風", 0xc8f0c0)
}

/** What a spell's element did on contact. Several may happen at once (lightning on a burning enemy on ice). */
internal enum class MageReaction(val label: String) {
    /** 砕け: fire on a frozen (or, for a boss, chilled) enemy. */
    SHATTER("砕け"),
    /** 伝導: lightning on ice runs to every enemy frozen or standing on that ice. */
    CONDUCT("伝導"),
    /** 過負荷: lightning on a burning enemy or fire floor explodes and throws back. */
    OVERLOAD("過負荷"),
}

/**
 * 陣: the elements of the last [MAX] casts, oldest first. It never gates a spell; only the grand chant spends it
 * and reads its [dominant] element (the most marks; with a tie the latest; none when all three differ).
 */
internal class CoreMageSigils {
    private val marks = ArrayDeque<MageElement>()
    val list: List<MageElement> get() = marks.toList()
    val size get() = marks.size

    fun carve(element: MageElement) {
        if (marks.size == MAX) marks.removeFirst()
        marks.addLast(element)
    }

    val allDifferent get() = marks.size == MAX && marks.distinct().size == MAX

    fun dominant(): MageElement? {
        if (marks.isEmpty() || allDifferent) return null
        val counts = marks.groupingBy { it }.eachCount()
        val most = counts.values.max()
        return marks.last { counts[it] == most }
    }

    /** Spends every mark: what the grand chant was made of. */
    fun spend(): List<MageElement> = list.also { marks.clear() }

    fun clear() = marks.clear()

    companion object { const val MAX = 3 }
}

/**
 * The ground and the enemies as the mage's elements left them: fire and ice floors, frozen and burning enemies.
 * Server state only, in ticks. Bosses never freeze: ice only chills them, which still lets fire shatter (weaker).
 */
internal class CoreMageField {
    data class Floor(val element: MageElement, val centre: Point, val radius: Double, val until: Long)
    private data class Mark(val until: Long, val boss: Boolean)

    private val floors = mutableListOf<Floor>()
    private val frozen = HashMap<UUID, Mark>()
    private val burning = HashMap<UUID, Long>()
    val floorList: List<Floor> get() = floors.toList()

    fun tick(now: Long) {
        floors.removeAll { it.until <= now }
        frozen.values.removeAll { it.until <= now }
        burning.values.removeAll { it <= now }
    }

    /** Standing in fire: burning, without the freeze-shattering a fire spell does. */
    fun ignite(id: UUID, now: Long) { burning[id] = maxOf(burning[id] ?: 0, now + 30) }

    fun lay(element: MageElement, centre: Point, radius: Double, now: Long, ticks: Int) {
        floors += Floor(element, centre, radius, now + ticks)
    }

    fun floorUnder(at: Point, element: MageElement): Floor? = floors.lastOrNull { it.element == element && flatDistance(it.centre, at) <= it.radius }

    fun isFrozen(id: UUID, now: Long) = (frozen[id]?.until ?: 0) > now
    fun isBurning(id: UUID, now: Long) = (burning[id] ?: 0) > now
    fun frozenIds(now: Long) = frozen.filterValues { it.until > now }.keys

    /**
     * [element] lands on enemy [id] standing at [at]. Applies the element's own state (fire burns, ice freezes or
     * chills) and returns the mixes it set off. A shatter uses up the freeze.
     */
    fun land(element: MageElement, id: UUID, at: Point, boss: Boolean, now: Long): Set<MageReaction> {
        val out = mutableSetOf<MageReaction>()
        when (element) {
            MageElement.FIRE -> {
                if (isFrozen(id, now)) { out += MageReaction.SHATTER; frozen.remove(id) }
                burning[id] = now + BURN_TICKS
            }
            MageElement.ICE -> {
                frozen[id] = Mark(now + if (boss) CHILL_TICKS else FREEZE_TICKS, boss)
                burning.remove(id)
            }
            MageElement.LIGHTNING -> {
                if (isFrozen(id, now) || floorUnder(at, MageElement.ICE) != null) out += MageReaction.CONDUCT
                if (isBurning(id, now) || floorUnder(at, MageElement.FIRE) != null) out += MageReaction.OVERLOAD
            }
            MageElement.WIND -> Unit
        }
        return out
    }

    /**
     * 巻き込み: wind blowing over [at] within [radius] picks up the floors it crosses (they are carried off, not
     * left behind) and returns the element it now carries: the one covering the most ground, or null.
     */
    fun carry(at: Point, radius: Double): MageElement? {
        val crossed = floors.filter { it.element != MageElement.WIND && flatDistance(it.centre, at) <= it.radius + radius }
        if (crossed.isEmpty()) return null
        floors.removeAll(crossed.toSet())
        return crossed.groupBy { it.element }.maxBy { (_, f) -> f.sumOf { it.radius * it.radius } }.key
    }

    fun shatterMultiplier(boss: Boolean) = if (boss) BOSS_SHATTER else SHATTER

    fun clear() { floors.clear(); frozen.clear(); burning.clear() }

    private fun flatDistance(a: Point, b: Point) = kotlin.math.hypot(a.x() - b.x(), a.z() - b.z())

    companion object {
        const val FREEZE_TICKS = 40     // 2 s frozen solid (a stun on regular enemies)
        const val CHILL_TICKS = 60      // a boss is only chilled, a little longer
        const val BURN_TICKS = 80
        const val SHATTER = 2.0         // the hit that shatters deals this many times its damage
        const val BOSS_SHATTER = 1.5
    }
}

internal object CoreMageRules {
    /** The extra skill index the staff casts (right-click on the weapon): the grand chant, beside the five slots. */
    const val GRAND = 5
    const val ECHO = "mage_ult"
    const val PEAK = "mage_zero"
    const val ECHO_DELAY = 40
    const val ECHO_POWER = .6
    const val PEAK_WIND_REACH = 1.5
    const val FIRE_FLOOR_TICKS = 80
    const val ICE_FLOOR_TICKS = 100
    const val CHAIN_JUMPS = 3
    const val CHAIN_REACH = 5.0
    const val OVERLOAD_RADIUS = 3.0

    fun element(skill: CoreSkillDefinition): MageElement? = when (skill.element) {
        1 -> MageElement.FIRE; 2 -> MageElement.ICE; 3 -> MageElement.LIGHTNING; 4 -> MageElement.WIND; else -> null
    }
    fun code(element: MageElement) = element.ordinal + 1

    /**
     * 大詠唱 made of the marks it spends: one wave a mark (a small one with none), its element the circle's most
     * common mark (all three different: every element at once). Art reuses the old mage scenes for now.
     */
    fun grand(spent: List<MageElement>, dominant: MageElement?): CoreSkillDefinition {
        val mixed = spent.size == CoreMageSigils.MAX && dominant == null
        val name = when {
            mixed -> "大詠唱：四元の爆発"
            dominant == MageElement.FIRE -> "大詠唱：火柱の雨"
            dominant == MageElement.ICE -> "大詠唱：氷の棺"
            dominant == MageElement.LIGHTNING -> "大詠唱：雷の檻"
            dominant == MageElement.WIND -> "大詠唱：大竜巻"
            else -> "大詠唱"
        }
        val scene = when (dominant) {
            MageElement.FIRE -> "mage_ult"; MageElement.ICE -> "mage_zero"; MageElement.LIGHTNING -> "mage_mark"
            MageElement.WIND -> "mage_ward"; null -> "mage_burst"
        }
        val power = 1.0 + .5 * spent.size
        return CoreSkillDefinition(name, scene, "陣の紋を全部使って解き放つ。一番多い紋で中身が変わり、紋が多いほど大きい",
            CoreDamageFormula(10.0 * power, ap = 1.6 * power), CoreDamageType.MAGICAL, mana = 20, cooldown = 240, startup = 20,
            pulses = spent.size.coerceAtLeast(1), area = true, motion = CoreSkillMotion.NOVA, radius = 4.0 + spent.size,
            element = dominant?.let(::code) ?: 0)
    }
}
