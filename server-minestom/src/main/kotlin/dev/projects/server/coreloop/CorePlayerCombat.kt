package dev.projects.server.coreloop

import dev.projects.server.mob.QuestEncounterCombat
import net.kyori.adventure.sound.Sound
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.minestom.server.coordinate.Pos
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.Player
import net.minestom.server.entity.Entity
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.attribute.Attribute
import net.minestom.server.entity.metadata.display.TextDisplayMeta
import net.minestom.server.entity.metadata.display.AbstractDisplayMeta
import net.minestom.server.particle.Particle
import net.minestom.server.sound.SoundEvent
import java.util.UUID
import java.util.concurrent.ThreadLocalRandom
import kotlin.math.*

/** Vanilla inputs feed a bounded, server-owned greatsword combo; clients never declare hits. */
internal class CorePlayerCombat(
    val player: Player,
    private val weaponTier: () -> Int,
    private val armorTier: () -> Int,
    private val encounter: () -> QuestEncounterCombat?,
    private val statSource: () -> CoreAffixStats = { CoreAffixStats() },
    private val criticalRoll: () -> Double = { ThreadLocalRandom.current().nextDouble() },
    private val weaponEnhancement: () -> Int = { 0 },
    private val armorEnhancement: () -> Int = { 0 },
    private val weaponBroken: () -> Boolean = { false },
    private val armorBroken: () -> Boolean = { false },
    private val weaponQuality: () -> Int = { 0 },
    private val armorQuality: () -> Int = { 0 },
    private val onDefeated: () -> Unit,
) {
    private val normal = GreatswordCombo()
    private val vfx = GreatswordVfx(player)
    private var normalDirection = Vec(0.0, 0.0, 1.0)
    private var actionEpoch = 0L
    internal val activeVisualEffects: Int get() = vfx.activeEffects
    private var tickNumber = 0L
    private var nextDodge = 0L
    private var lastCombat = -400L
    private val readyAt = LongArray(3)
    private var pending: PendingSkill? = null
    private var queuedSkill: Int? = null
    private var queuedDodge = false
    private var whetstoneUntil = 0L
    var defeated = false
        private set
    private var manaValue = 100.0
    val mana: Int get() = manaValue.toInt()
    var health = 100.0
        private set
    val maxMana: Int get() = 100 + statSource().maxManaFlat.toInt()
    val maxHealth: Int get() = (if (armorBroken()) 100 else ((100 + (armorTier() - 1) * 30) * (1.0 + armorQuality().coerceIn(0, 30) / 100.0) * (1.0 + .02 * armorEnhancement().coerceIn(0, 30))).toInt()) + statSource().healthFlat.toInt()
    val attackSpeed: Double get() = if (weaponBroken()) 1.0 else 1.0 + (statSource().attackSpeedPercent.coerceIn(0.0, 60.0) + .8 * weaponEnhancement().coerceIn(0, 30)) / 100.0
    val attackDamage: Double get() = if (weaponBroken()) 0.0 else 12.0 * 1.65.pow(weaponTier() - 1) * (1.0 + weaponQuality().coerceIn(0, 30) / 100.0) * (1.0 + .04 * weaponEnhancement().coerceIn(0, 30)) * (1.0 + statSource().damagePercent / 100.0) * if (tickNumber < whetstoneUntil) 1.2 else 1.0
    private data class Burn(val damage: Double, var nextTick: Long, var remaining: Int)
    private val burns = mutableMapOf<UUID, Burn>()
    private val damageLabels = mutableListOf<Pair<Entity, Long>>()

    private data class PendingSkill(val id: Int, val origin: Pos, val direction: Vec, val startup: Int, var elapsed: Int = 0)

    fun attack() {
        if (weaponBroken()) { notice("武器が破損しています。装備庫で修理してください"); return }
        if (defeated || pending != null || encounter() == null || player.openInventory != null) return
        normal.press(attackSpeed)?.let { swing ->
            normalDirection = flatFacing()
            vfx.play(GreatswordVisual.WINDUP, player.position, normalDirection)
            vfx.startSound(swing.step)
            lastCombat = tickNumber
        }
    }

    fun skill(id: Int) {
        if (weaponBroken()) { notice("武器が破損しています。装備庫で修理してください"); return }
        if (id !in 0..2 || defeated || encounter() == null || player.openInventory != null) return
        if (normal.isAttacking) { normal.clearBuffer(); queuedSkill = id; return }
        if (pending != null) return
        if (tickNumber < readyAt[id]) { notice("${SKILL_NAMES[id]}：あと${cooldownSeconds(id)}秒"); return }
        val cost = intArrayOf(15, 25, 35)[id]
        if (mana < cost) { notice("マナが足りません（必要 $cost）"); return }
        manaValue -= cost
        readyAt[id] = tickNumber + cooldownTicks(id)
        val castMultiplier = 1.0 - statSource().castReductionPercent.coerceIn(0.0, 40.0) / 100.0
        val startup = ceil(intArrayOf(5, 12, 6)[id] * castMultiplier).toInt().coerceAtLeast(2)
        pending = PendingSkill(id, player.position, flatFacing(), startup)
        lastCombat = tickNumber
        player.setHeldItemSlot(0)
        player.swingMainHand()
        sound(SoundEvent.ITEM_TRIDENT_THROW, 0.65f, 1.25f)
        vfx.play(GreatswordVisual.WINDUP, player.position, flatFacing())
    }

    fun dodge() {
        if (defeated || encounter() == null || tickNumber < nextDodge) return
        if (normal.isAttacking || pending != null) { normal.clearBuffer(); queuedDodge = true; return }
        val input = player.inputs()
        val forward = (if (input.forward()) 1.0 else 0.0) - (if (input.backward()) 1.0 else 0.0)
        val side = (if (input.right()) 1.0 else 0.0) - (if (input.left()) 1.0 else 0.0)
        val facing = flatFacing()
        val right = Vec(-facing.z(), 0.0, facing.x())
        val direction = if (forward == 0.0 && side == 0.0) facing else facing.mul(forward).add(right.mul(side)).normalize()
        moveSafely(direction, 2.6)
        nextDodge = tickNumber + 24
        sound(SoundEvent.ENTITY_PLAYER_ATTACK_SWEEP, 0.35f, 1.7f)
    }

    fun tick() {
        tickNumber++
        damageLabels.removeAll { (entity, expiry) -> if (tickNumber >= expiry) { entity.remove(); true } else false }
        if (defeated) return
        if (tickNumber % 20 == 0L) {
            val recovery = if (tickNumber - lastCombat > 100) 12.0 else 5.0
            manaValue = (manaValue + recovery * (1.0 + statSource().manaRegenPercent / 100.0)).coerceAtMost(maxMana.toDouble())
            player.getAttribute(Attribute.MOVEMENT_SPEED).baseValue = 0.1 * (1.0 + statSource().moveSpeedPercent.coerceIn(0.0, 25.0) / 100.0)
        }
        val enemies = encounter() ?: run { resetActions(); return }
        if (weaponBroken()) { resetActions(); return }
        val epoch = actionEpoch
        // A kill callback may synchronously return/reset the actor, including clearing burns.
        for ((id, burn) in burns.toMap()) {
            if (!enemies.isAlive(id)) { burns.remove(id); continue }
            if (tickNumber >= burn.nextTick) {
                enemies.applyEffectDamage(id, player, burn.damage)
                if (!actionsValid(enemies, epoch)) return
                burn.nextTick += 20; burn.remaining--
            }
            if (burn.remaining <= 0) burns.remove(id)
        }
        normal.tick()?.let { swing ->
            vfx.swingSound(swing.step)
            player.swingMainHand()
            vfx.play(arrayOf(GreatswordVisual.SWEEP, GreatswordVisual.REVERSE, GreatswordVisual.FINISHER)[swing.step - 1], player.position, normalDirection)
            for (target in enemies.combatTargets()) {
                if (greatswordInRange(player.position, normalDirection, target) && visibleTo(target.id, enemies)) {
                    hit(enemies, target.id, swing.multiplier, skill = false, heavy = swing.step == 3)
                    if (!actionsValid(enemies, epoch)) return
                }
            }
        }
        pending?.let { action ->
            action.elapsed++
            when (action.id) {
                0 -> if (action.elapsed == action.startup) {
                    // Stop in front of a nearby enemy instead of dashing through it and missing behind us.
                    val distance = enemies.combatTargets().mapNotNull { target ->
                        val offset = target.position.sub(player.position)
                        val forward = offset.x() * action.direction.x() + offset.z() * action.direction.z()
                        val lateral = abs(offset.x() * action.direction.z() - offset.z() * action.direction.x())
                        if (forward >= 0 && lateral <= 0.85 && abs(offset.y()) <= 2.5) (forward - 1.0).coerceAtLeast(0.0) else null
                    }.minOrNull()?.coerceAtMost(2.2) ?: 2.2
                    moveSafely(action.direction, distance)
                    vfx.play(GreatswordVisual.LUNGE, player.position, action.direction)
                    strike(enemies, player.position, action.direction, 4.5, 0.45, 1.7)
                }
                1 -> {
                    if (action.elapsed == action.startup) {
                        vfx.play(GreatswordVisual.SLAM, action.origin, action.direction)
                    vfx.play(GreatswordVisual.SLAM_BLADE, action.origin, action.direction)
                        strike(enemies, action.origin, action.direction, 5.0, cos(0.85), 2.6)
                        if (!actionsValid(enemies, epoch)) return
                        sound(SoundEvent.ENTITY_GENERIC_EXPLODE, 0.45f, 1.2f)
                    }
                }
                2 -> if (action.elapsed in listOf(action.startup, action.startup + 8, action.startup + 16)) {
                    vfx.play(GreatswordVisual.WHIRL, player.position, action.direction)
                    vfx.swingSound(2)
                    strike(enemies, player.position, action.direction, 3.3, -1.0, 1.15)
                }
            }
            if (!actionsValid(enemies, epoch)) return
            if (action.elapsed >= action.startup + intArrayOf(11, 13, 25)[action.id]) pending = null
        }
        if (!normal.isAttacking && pending == null) {
            if (queuedDodge) { normal.clearBuffer(); queuedDodge = false; dodge() }
            else if (queuedSkill != null) { normal.clearBuffer(); val id = queuedSkill!!; queuedSkill = null; skill(id) }
            else if (normal.takeBuffered()) attack()
        }
        vfx.tick()
    }

    private fun strike(enemies: QuestEncounterCombat, origin: Pos, direction: Vec, range: Double, minDot: Double, multiplier: Double) {
        val epoch = actionEpoch
        for (target in enemies.combatTargets()) {
            if (greatswordInRange(origin, direction, target, range, minDot) && visibleTo(target.id, enemies)) {
                hit(enemies, target.id, multiplier, skill = true, heavy = multiplier >= 2.0)
                if (!actionsValid(enemies, epoch)) return
            }
        }
    }

    private fun hit(enemies: QuestEncounterCombat, id: UUID, multiplier: Double, skill: Boolean, heavy: Boolean = false) {
        val epoch = actionEpoch
        val stats = statSource()
        val position = enemies.positionOf(id) ?: return
        val tagBonus = if (skill) stats.skillDamagePercent else stats.normalDamagePercent
        val element = (stats.fireFlat + stats.iceFlat + stats.lightningFlat) * multiplier * 0.65
        val critical = criticalRoll() < (0.05 * (1.0 + stats.critChanceIncreasedPercent / 100.0)).coerceIn(0.0, 0.75)
        val criticalMultiplier = if (critical) (1.5 + stats.critMultiplierBonusPercent / 100.0).coerceAtMost(4.0) else 1.0
        val weak = when (enemies.weaknessOf(id)) {
            "fire" -> stats.fireFlat > 0; "ice" -> stats.iceFlat > 0; "lightning" -> stats.lightningFlat > 0; else -> false
        }
        val damage = (attackDamage * multiplier * (1 + tagBonus / 100.0) + element) * criticalMultiplier * if (weak) 1.25 else 1.0
        val applied = enemies.applyDamageAmount(id, player, damage) ?: return
        if (!actionsValid(enemies, epoch)) return
        vfx.impactSound(heavy)
        vfx.play(GreatswordVisual.HIT, position, normalDirection)
        vfx.holdContact(if (heavy) 3 else 2)
        showDamage(position, applied, critical, weak)
        if (stats.fireFlat > 0 && enemies.isAlive(id)) {
            burns[id] = Burn(maxOf(burns[id]?.damage ?: 0.0, stats.fireFlat * 0.3), tickNumber + 20, 3)
            vfx.particles(Particle.SMALL_FLAME, position.add(0.0, 1.0, 0.0), 7, Vec(0.2, 0.4, 0.2), 0.01f)
        }
        if (stats.iceFlat > 0) {
            enemies.applySlow(id, (0.15 + stats.iceFlat / 100.0).coerceAtMost(0.45), 2000)
            vfx.particles(Particle.SNOWFLAKE, position.add(0.0, 0.8, 0.0), 7, Vec(0.3, 0.3, 0.3), 0.03f)
        }
        if (stats.lightningFlat > 0) {
            enemies.combatTargets().filter { it.id != id && it.position.distance(position) <= 4.0 && visibleTo(it.id, enemies) }
                .minByOrNull { it.position.distance(position) }?.let { chained ->
                    if (enemies.applyEffectDamage(chained.id, player, stats.lightningFlat * 0.8)) {
                        if (!actionsValid(enemies, epoch)) return
                        val end = enemies.positionOf(chained.id) ?: position
                        for (step in 0..8) vfx.particles(Particle.ELECTRIC_SPARK,
                            position.add(end.sub(position).mul(step / 8.0)).add(0.0, 0.8, 0.0), 1)
                    }
                }
        }
    }

    private fun showDamage(position: Pos, amount: Double, critical: Boolean, weak: Boolean) {
        // Bounded, short-lived combat feedback; never a client damage declaration.
        if (damageLabels.size >= 24) damageLabels.removeAt(0).first.remove()
        val entity = Entity(EntityType.TEXT_DISPLAY).apply {
            setNoGravity(true); setHasPhysics(false)
            editEntityMeta(TextDisplayMeta::class.java) { meta ->
                meta.setText(Component.text("${if (critical) "✦ " else ""}${amount.roundToInt()}${if (weak) " 弱点" else ""}",
                    if (critical) NamedTextColor.GOLD else if (weak) NamedTextColor.AQUA else NamedTextColor.WHITE))
                meta.setBillboardRenderConstraints(AbstractDisplayMeta.BillboardConstraints.CENTER)
                meta.setScale(Vec(0.7, 0.7, 0.7)); meta.setShadow(true); meta.setBackgroundColor(0)
            }
            setInstance(player.instance, position.add(0.0, 2.35, 0.0))
        }
        damageLabels += entity to tickNumber + 18
    }

    private fun visibleTo(targetId: java.util.UUID, enemies: QuestEncounterCombat): Boolean {
        val target = enemies.positionOf(targetId) ?: return false
        val start = player.position.add(0.0, 1.0, 0.0)
        val end = target.add(0.0, 1.0, 0.0)
        val steps = ceil(start.distance(end) * 4).toInt().coerceAtLeast(1)
        return (1 until steps).all { step ->
            val point = start.add(end.sub(start).mul(step.toDouble() / steps))
            !player.instance.getBlock(point).isSolid
        }
    }

    fun hurt(amount: Double) {
        if (defeated || encounter() == null) return
        val adjusted = amount * (if (armorBroken()) 1.0 else (1.0 - (armorTier() - 1) * 0.10)) * (1.0 - statSource().mitigationPercent.coerceIn(0.0, 45.0) / 100.0)
        lastCombat = tickNumber
        if (health - adjusted <= 0.0) {
            health = 0.0
            defeated = true
            resetActions()
            onDefeated()
            return
        }
        health -= adjusted
        syncVanillaHealth()
        sound(SoundEvent.ENTITY_PLAYER_HURT, 0.65f, 1f)
    }

    fun healPotion() {
        if (!defeated) {
            health = (health + maxHealth * 0.45).coerceAtMost(maxHealth.toDouble())
            syncVanillaHealth()
            sound(SoundEvent.ENTITY_GENERIC_DRINK, 0.75f, 1f)
        }
    }

    fun sharpen() { whetstoneUntil = tickNumber + 20 * 180 }
    fun cooldownSeconds(id: Int): Long = ((readyAt[id] - tickNumber).coerceAtLeast(0) + 19) / 20
    fun cooldownTicks(id: Int): Int = ceil(intArrayOf(80, 140, 220)[id] * (1.0 - statSource().cooldownReductionPercent.coerceIn(0.0, 45.0) / 100.0)).toInt()
    fun cooldownRemaining(id: Int): Int = (readyAt[id] - tickNumber).coerceAtLeast(0).toInt()
    fun reset() { resetActions(); defeated = false; manaValue = maxMana.toDouble(); health = maxHealth.toDouble(); readyAt.fill(0L); nextDodge = 0L; syncVanillaHealth() }
    fun revive(fraction: Double) { require(fraction in .1..1.0); reset(); health = maxHealth * fraction; syncVanillaHealth() }
    fun resetActions() {
        actionEpoch++
        normal.reset(); pending = null; queuedSkill = null; queuedDodge = false; burns.clear()
        vfx.cancel()
        damageLabels.forEach { it.first.remove() }; damageLabels.clear()
    }
    private fun syncVanillaHealth() {
        player.getAttribute(Attribute.MAX_HEALTH).baseValue = 20.0
        player.health = (20.0 * health / maxHealth).toFloat().coerceIn(0.1f, 20f)
    }

    private fun moveSafely(direction: Vec, distance: Double) {
        val start = player.position
        var destination = start
        for (step in 1..ceil(distance / 0.15).toInt()) {
            val next = start.add(direction.mul(min(distance, step * 0.15)))
            if (!listOf(-0.3, 0.3).all { x -> listOf(-0.3, 0.3).all { z ->
                listOf(0.1, 0.9, 1.7).all { y -> !player.instance.getBlock(next.add(x, y, z)).isSolid }
            } }) break
            destination = next
        }
        player.teleport(destination)
    }

    private fun flatFacing(): Vec = Vec(player.position.direction().x(), 0.0, player.position.direction().z()).let {
        if (it.lengthSquared() < 0.001) Vec(0.0, 0.0, 1.0) else it.normalize()
    }
    private fun actionsValid(enemies: QuestEncounterCombat, epoch: Long): Boolean =
        epoch == actionEpoch && !defeated && encounter() === enemies && player.instance != null
    private fun notice(message: String) = player.sendActionBar(Component.text(message, NamedTextColor.YELLOW))
    private fun sound(event: SoundEvent, volume: Float, pitch: Float) = player.playSound(Sound.sound(event, Sound.Source.PLAYER, volume, pitch))

    companion object { val SKILL_NAMES = listOf("踏み込み斬り", "地砕き", "旋風斬り") }
}
