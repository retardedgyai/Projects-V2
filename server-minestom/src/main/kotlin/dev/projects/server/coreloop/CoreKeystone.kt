package dev.projects.server.coreloop

/**
 * When a conditional passive value (`STAT@CONDITION=v` in passive-tree.tsv) counts. Self conditions are tracked by
 * [CorePlayerCombat] every tick; [targeted] ones are decided per hit for the struck enemy.
 */
enum class CorePassiveCondition(val label: String, val targeted: Boolean = false) {
    RECENT_KILL("敵を倒して4秒以内"), LOW_LIFE("HPが半分以下のとき"), FULL_LIFE("HPが満タンのとき"),
    AFTER_DODGE("回避して2秒以内"), STILL("2秒動いていないとき"), MOVING("移動中"),
    VS_BOSS("ボスに対して", true), VS_CONTROLLED("CC中・減速中の敵に対して", true);
    val bit get() = 1 shl ordinal

    companion object {
        const val RECENT_KILL_TICKS = 80L
        const val AFTER_DODGE_TICKS = 40L
        const val STILL_TICKS = 40L
        /** Movement counts as "moving" for this many ticks after the last step (no flicker between steps). */
        const val MOVING_TICKS = 5L
        fun mask(conditions: Collection<CorePassiveCondition>) = conditions.fold(0) { m, c -> m or c.bit }
        /** The self conditions that hold now; all inputs are cheap fields the combat actor already keeps. */
        fun selfMask(tick: Long, lastKill: Long, lastDodge: Long, stillSince: Long, lastMoved: Long, healthFraction: Double): Int {
            var m = 0
            if (lastKill >= 0 && tick - lastKill <= RECENT_KILL_TICKS) m = m or RECENT_KILL.bit
            if (healthFraction <= .5) m = m or LOW_LIFE.bit
            if (healthFraction >= .99) m = m or FULL_LIFE.bit
            if (lastDodge >= 0 && tick - lastDodge <= AFTER_DODGE_TICKS) m = m or AFTER_DODGE.bit
            if (tick - stillSince >= STILL_TICKS) m = m or STILL.bit
            if (lastMoved >= 0 && tick - lastMoved <= MOVING_TICKS) m = m or MOVING.bit
            return m
        }
        fun targetMask(boss: Boolean, controlled: Boolean) = (if (boss) VS_BOSS.bit else 0) or (if (controlled) VS_CONTROLLED.bit else 0)
    }
}

/** Keystones: one point, a strong rule with its price. Downsides are multipliers here, never negative stats. */
enum class CoreKeystone(val text: String) {
    BERSERKER_BLOOD("HPが減るほど与ダメージが増える（最大+40%）。受ける回復-30%。"),
    IRON_WILL("回避できなくなる。被ダメージ-20%、CCを受けない。"),
    SANCTUARY("自分が得たシールドの半分が近くの味方にも付く。自分の与ダメージ-15%。"),
    MANA_SHIELD("受けるダメージの30%を、HPの代わりにマナで受ける。"),
    CREEPING_ROT("継続ダメージも会心する。直接ダメージ-20%。"),
    GLASS_CANNON("最大HP-30%。与ダメージ+35%。"),
    LIFE_CYCLE("味方を回復すると、その半分だけ自分も回復する。与ダメージ-10%。"),
    BLOOD_PACT("技がマナの代わりにHPを使う。マナは回復しない。"),
    HUNTER_PATIENCE("2秒動かないと会心率が2倍。動くと解除。"),
    ENDLESS_HUNT("敵を倒すたび全ての技のCDが1秒縮む。最大HP-15%。"),
    SHADOW_STEP("回避の後2秒、与ダメージ+30%。被ダメージ+10%。"),
    ONE_STRIKE("会心しなくなる。代わりに与ダメージ+30%。"),
    BREAKER("ボスのブレイクゲージが50%多く溜まる。ボス以外への与ダメージ-15%。"),
    LONE_WOLF("パーティにいないとき与ダメージ+20%・被ダメージ-10%。"),
    MANA_CYCLE("CD回復速度+30%。最大マナ-40%。");

    companion object {
        const val SANCTUARY_RADIUS = 6.0
        const val MANA_SHIELD_SHARE = .3
        const val ENDLESS_HUNT_TICKS = 20L
        const val MANA_CYCLE_COOLDOWN = 30.0

        /** Outgoing damage of every hit (and the damage-over-time it leaves) from keystones; [direct] is false for DoT ticks. */
        fun damage(keys: Set<CoreKeystone>, missingHealth: Double = 0.0, afterDodge: Boolean = false, boss: Boolean = false,
            alone: Boolean = false, direct: Boolean = true): Double {
            if (keys.isEmpty()) return 1.0
            var m = 1.0
            if (BERSERKER_BLOOD in keys) m *= 1 + .4 * missingHealth.coerceIn(0.0, 1.0)
            if (SANCTUARY in keys) m *= .85
            if (CREEPING_ROT in keys && direct) m *= .8
            if (GLASS_CANNON in keys) m *= 1.35
            if (LIFE_CYCLE in keys) m *= .9
            if (SHADOW_STEP in keys && afterDodge) m *= 1.3
            if (ONE_STRIKE in keys) m *= 1.3
            if (BREAKER in keys && !boss) m *= .85
            if (LONE_WOLF in keys && alone) m *= 1.2
            return m
        }
        fun taken(keys: Set<CoreKeystone>, alone: Boolean = false): Double {
            if (keys.isEmpty()) return 1.0
            var m = 1.0
            if (IRON_WILL in keys) m *= .8
            if (SHADOW_STEP in keys) m *= 1.1
            if (LONE_WOLF in keys && alone) m *= .9
            return m
        }
        fun incomingHealing(keys: Set<CoreKeystone>) = if (BERSERKER_BLOOD in keys) .7 else 1.0
        fun maxHealth(keys: Set<CoreKeystone>) = (if (GLASS_CANNON in keys) .7 else 1.0) * (if (ENDLESS_HUNT in keys) .85 else 1.0)
        fun maxMana(keys: Set<CoreKeystone>) = if (MANA_CYCLE in keys) .6 else 1.0
        fun breakAmount(keys: Set<CoreKeystone>) = if (BREAKER in keys) 1.5 else 1.0
        /** ONE_STRIKE never crits; HUNTER_PATIENCE doubles the chance while still (up to certain). */
        fun critChance(stats: CoreAffixStats, still: Boolean): Double = when {
            ONE_STRIKE in stats.keystones -> 0.0
            HUNTER_PATIENCE in stats.keystones && still -> (stats.criticalChance * 2).coerceAtMost(1.0)
            else -> stats.criticalChance
        }
        /** CREEPING_ROT: a damage-over-time tick may crit with the caster's chance and multiplier. */
        fun dotCritical(stats: CoreAffixStats, roll: Double): Double =
            if (CREEPING_ROT in stats.keystones && roll < critChance(stats, false)) stats.criticalMultiplier else 1.0
        /** BLOOD_PACT: the HP left after paying [cost] (never below 1). */
        fun bloodPrice(health: Double, cost: Int) = (health - cost).coerceAtLeast(1.0)
        /** MANA_SHIELD: how much of [damage] mana pays, and the mana left. */
        fun manaShield(damage: Double, mana: Double): Double = minOf(damage * MANA_SHIELD_SHARE, mana.coerceAtLeast(0.0))
    }
}
