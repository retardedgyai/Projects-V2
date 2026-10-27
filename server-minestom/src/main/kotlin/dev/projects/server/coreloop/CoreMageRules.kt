package dev.projects.server.coreloop

import java.util.UUID

/**
 * The mage (Creator 2026-10-27, docs/development/mage-rework.md): fire, ice, lightning and wind. Every spell is one
 * button; what makes it a mage is what happens after: the states spells leave on enemies mix by themselves, and every cast
 * carves its element into the sigil circle under the mage's feet for the grand chant (大詠唱) to release.
 */
internal enum class MageElement(val label: String, val color: Int) {
    FIRE("火", 0xff9438), ICE("氷", 0x82daff), LIGHTNING("雷", 0xffe066), WIND("風", 0xc8f0c0)
}

/** What a spell's element did on contact. Several may happen at once (lightning on a burning enemy on ice). */
internal enum class MageReaction(val label: String) {
    /** 砕け: fire on a frozen (or, for a boss, chilled) enemy. */
    SHATTER("砕け"),
    /** 伝導: lightning on a frozen enemy runs to every frozen enemy. */
    CONDUCT("伝導"),
    /** 過負荷: lightning on a burning enemy explodes and throws back. */
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
 * The enemies as the mage's elements left them: frozen and burning. Nothing is left on the ground (Creator
 * 2026-10-28: states sit on enemies only). Server state only, in ticks. Bosses never freeze: ice only chills them,
 * which still lets fire shatter (weaker).
 */
internal class CoreMageField {
    private data class Mark(val until: Long, val boss: Boolean)

    private val frozen = HashMap<UUID, Mark>()
    private val burning = HashMap<UUID, Long>()

    fun tick(now: Long) {
        frozen.values.removeAll { it.until <= now }
        burning.values.removeAll { it <= now }
    }

    fun isFrozen(id: UUID, now: Long) = (frozen[id]?.until ?: 0) > now
    fun isBurning(id: UUID, now: Long) = (burning[id] ?: 0) > now
    fun frozenIds(now: Long) = frozen.filterValues { it.until > now }.keys
    fun burningIds(now: Long) = burning.filterValues { it > now }.keys
    /** Ticks this enemy still burns (0 when it does not). */
    fun burnLeft(id: UUID, now: Long) = ((burning[id] ?: 0) - now).coerceAtLeast(0)

    /**
     * [element] lands on enemy [id]. Applies the element's own state (fire burns, ice freezes or chills) and returns
     * the mixes it set off. A shatter uses up the freeze.
     */
    fun land(element: MageElement, id: UUID, boss: Boolean, now: Long): Set<MageReaction> {
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
                if (isFrozen(id, now)) out += MageReaction.CONDUCT
                if (isBurning(id, now)) out += MageReaction.OVERLOAD
            }
            MageElement.WIND -> Unit
        }
        return out
    }

    /**
     * 巻き込み: wind blowing over enemies [ids] picks up the state they carry and spreads it to everything it hits:
     * the element on the most of them (burning = fire, frozen = ice; a tie goes to fire), or null. The states stay.
     */
    fun carry(ids: Collection<UUID>, now: Long): MageElement? {
        val fire = ids.count { isBurning(it, now) }; val ice = ids.count { isFrozen(it, now) }
        return when { fire == 0 && ice == 0 -> null; fire >= ice -> MageElement.FIRE; else -> MageElement.ICE }
    }

    fun shatterMultiplier(boss: Boolean) = if (boss) BOSS_SHATTER else SHATTER

    fun clear() { frozen.clear(); burning.clear() }

    companion object {
        const val FREEZE_TICKS = 40     // 2 s frozen solid (a stun on regular enemies)
        const val CHILL_TICKS = 60      // a boss is only chilled, a little longer
        const val BURN_TICKS = 80       // 4 s burning, a hit every second
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
    const val CHAIN_JUMPS = 3
    const val CHAIN_REACH = 5.0
    const val OVERLOAD_RADIUS = 3.0
    /** 元素の極み (lightning): a conduct also reaches what stands this near a frozen enemy. */
    const val PEAK_CONDUCT_REACH = 3.0

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
