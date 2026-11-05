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
    /** 凍結: the ice built up on an enemy filled it; it freezes (a boss once, for a short down). */
    FREEZE("凍結"),
    /** 破裂: the charge built up on an enemy filled it; it bursts for a big hit. */
    BURST("破裂"),
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
 * The enemies as the mage's elements left them (Creator 2026-11-03, docs/development/mage-rework.md): the element
 * itself only strikes a weakness, the same for a weapon's element; what ice and lightning do is the mage's spells'.
 * Ice builds up on an enemy (slower the fuller) and freezes it when full; lightning builds a charge that bursts when
 * full. A boss freezes or bursts once and then needs [GROW] times as much the next time. Fire burns (4 s) and
 * finishes what the others set up. Nothing is left on the ground. Server state only, in ticks.
 */
internal class CoreMageField {
    /** An element building up on one enemy: [value] of [need]; it starts to fade [FADE_AFTER] ticks after the last. */
    private class Gauge(var value: Double = 0.0, var need: Double = FULL, var lastAt: Long = 0)

    private val frozen = HashMap<UUID, Long>()
    private val burning = HashMap<UUID, Long>()
    private val ice = HashMap<UUID, Gauge>()
    private val charge = HashMap<UUID, Gauge>()

    fun tick(now: Long) {
        frozen.values.removeAll { it <= now }
        burning.values.removeAll { it <= now }
        for (gauges in listOf(ice, charge)) gauges.values.removeAll { g ->
            if (now - g.lastAt > FADE_AFTER) g.value = (g.value - FADE_PER_TICK).coerceAtLeast(0.0)
            g.value <= 0.0 && g.need <= FULL
        }
    }

    fun isFrozen(id: UUID, now: Long) = (frozen[id] ?: 0) > now
    fun isBurning(id: UUID, now: Long) = (burning[id] ?: 0) > now
    fun frozenIds(now: Long) = frozen.filterValues { it > now }.keys
    fun burningIds(now: Long) = burning.filterValues { it > now }.keys
    /** Ticks this enemy still burns (0 when it does not). */
    fun burnLeft(id: UUID, now: Long) = ((burning[id] ?: 0) - now).coerceAtLeast(0)
    /** How full the ice on this enemy is, 0..1 (its slow). */
    fun chill(id: UUID) = ice[id]?.let { it.value / it.need } ?: 0.0
    /** How full the charge on this enemy is, 0..1. */
    fun charged(id: UUID) = charge[id]?.let { it.value / it.need } ?: 0.0

    /**
     * [element] lands on enemy [id] with [amount] of build-up (ice and lightning; a grand chant's ice brings [FILL]).
     * Applies the element's own state and returns what it set off. A shatter uses up the freeze.
     */
    fun land(element: MageElement, id: UUID, boss: Boolean, now: Long, amount: Double = 0.0): Set<MageReaction> {
        val out = mutableSetOf<MageReaction>()
        when (element) {
            MageElement.FIRE -> {
                if (isFrozen(id, now)) { out += MageReaction.SHATTER; frozen.remove(id) }
                burning[id] = now + BURN_TICKS
            }
            MageElement.ICE -> {
                burning.remove(id)
                if (!isFrozen(id, now) && fill(ice, id, boss, now, amount)) { freeze(id, boss, now); out += MageReaction.FREEZE }
            }
            MageElement.LIGHTNING -> {
                if (isFrozen(id, now)) out += MageReaction.CONDUCT
                if (isBurning(id, now)) out += MageReaction.OVERLOAD
                if (fill(charge, id, boss, now, amount)) out += MageReaction.BURST
            }
            MageElement.WIND -> Unit
        }
        return out
    }

    /** Frozen now, whatever the ice (元素の極み's ice after a shatter). */
    fun freeze(id: UUID, boss: Boolean, now: Long) { frozen[id] = now + if (boss) BOSS_FREEZE_TICKS else FREEZE_TICKS }

    /** Adds [amount]; true when that filled it (then it empties, and a boss's next fill needs more). */
    private fun fill(gauges: HashMap<UUID, Gauge>, id: UUID, boss: Boolean, now: Long, amount: Double): Boolean {
        if (amount <= 0) return false
        val g = gauges.getOrPut(id) { Gauge() }
        g.value += amount; g.lastAt = now
        if (g.value < g.need) return false
        g.value = 0.0
        if (boss) g.need *= GROW
        return true
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

    fun clear() { frozen.clear(); burning.clear(); ice.clear(); charge.clear() }

    companion object {
        const val FULL = 100.0
        /** A grand chant's ice: full at once, whatever is still needed. */
        const val FILL = 1e6
        const val GROW = 1.5            // a boss needs this much more each time it froze or burst
        const val FADE_AFTER = 80L      // 4 s without that element and its build-up starts to fade
        const val FADE_PER_TICK = 1.25  // ... emptying a full gauge in 4 s more
        const val FREEZE_TICKS = 40     // 2 s frozen solid (a stun on regular enemies)
        const val BOSS_FREEZE_TICKS = 30 // 1.5 s, a short down for a boss
        const val BURN_TICKS = 80       // 4 s burning, a hit every second
        const val SHATTER = 3.0         // the hit that shatters deals this many times its damage
        const val BOSS_SHATTER = 2.0
        /** The most a full ice slows (a boss resists half, QuestEncounterCombat.applySlow). */
        const val CHILL_SLOW = .4
        /** A burst: this many times the hit that set it off. */
        const val BURST_POWER = 2.5
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

    /** Ice or charge a spell builds on each enemy it hits (CoreMageField.FULL is full). 落雷's jumps: [JUMP_BUILDUP]. */
    fun buildup(icon: String, grand: Boolean) = when {
        grand && icon == GRAND_ICE -> CoreMageField.FILL
        grand -> STORM_BOLT
        else -> when (icon) { "frost_nova" -> 60.0; "mage_burst" -> 50.0; "mage_mark" -> 40.0; "mage_blink" -> 20.0; else -> 30.0 }
    }
    const val JUMP_BUILDUP = 25.0
    /** One bolt of the grand chant's thunderstorm. */
    const val STORM_BOLT = 35.0
    const val OVERLOAD_POWER = 1.2
    /** The grand chant's ice draws the frost nova's own picture where it lands. */
    const val GRAND_ICE = "frost_nova"
    /** How far a wind field draws a regular enemy in each pulse (竜巻, the typhoon). */
    const val WIND_DRAW = 1.2

    fun element(skill: CoreSkillDefinition): MageElement? = when (skill.element) {
        1 -> MageElement.FIRE; 2 -> MageElement.ICE; 3 -> MageElement.LIGHTNING; 4 -> MageElement.WIND; else -> null
    }
    fun code(element: MageElement) = element.ordinal + 1

    /**
     * 大詠唱 made of the marks it spends (Creator 2026-11-03): cast where the mage aims, about three times a spell's
     * damage, and each element does its own big thing. Its element is the circle's most common mark; all three
     * different is every element at once. More marks make it a little stronger. Art reuses the spells' for now.
     * - fire, 大流星: one huge meteor. - ice, 氷河: fills every enemy's ice at once (a boss freezes once).
     * - lightning, 雷雨: bolts fall again and again, building charge until enemies burst.
     * - wind, 大台風: a long storm that draws enemies in. - mixed, 四元の爆発: three blasts of fire, ice and lightning.
     */
    fun grand(spent: List<MageElement>, dominant: MageElement?): CoreSkillDefinition {
        val mixed = spent.size == CoreMageSigils.MAX && dominant == null
        val scale = .7 + .1 * spent.size
        fun g(name: String, icon: String, ad: Double, ap: Double, pulses: Int, radius: Double, startup: Int) =
            CoreSkillDefinition(name, icon, "陣の紋を全部使って、狙った所に解き放つ。一番多い紋で中身が変わり、紋が多いほど強い",
                CoreDamageFormula(ad * scale, ap = ap * scale), CoreDamageType.MAGICAL, mana = 20, cooldown = 240, startup = startup,
                pulses = pulses, area = true, motion = CoreSkillMotion.FIELD, range = 18.0, radius = radius,
                element = dominant?.let(::code) ?: 0)
        return when {
            mixed -> g("大詠唱：四元の爆発", "mage_burst", 18.0, 2.6, 3, 5.0, 20)
            dominant == MageElement.FIRE -> g("大詠唱：大流星", "meteor", 54.0, 7.8, 1, 5.5, 24)
            dominant == MageElement.ICE -> g("大詠唱：氷河", GRAND_ICE, 40.0, 6.0, 1, 5.0, 18)
            dominant == MageElement.LIGHTNING -> g("大詠唱：雷雨", "mage_mark", 9.0, 1.3, 6, 5.5, 16)
            dominant == MageElement.WIND -> g("大詠唱：大台風", "mage_gale", 7.0, 1.0, 6, 6.5, 16)
            else -> g("大詠唱", "mage_burst", 10.0, 1.6, 1, 3.0, 20)
        }
    }
}
