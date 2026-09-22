package dev.projects.server.coreloop

import dev.projects.protocol.AttackInputState
import dev.projects.server.CombatEvent
import dev.projects.server.CombatState
import dev.projects.server.WeaponType
import dev.projects.server.mob.QuestEncounterCombat
import net.kyori.adventure.sound.Sound
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.minestom.server.coordinate.Pos
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.Player
import net.minestom.server.network.packet.server.play.ParticlePacket
import net.minestom.server.particle.Particle
import net.minestom.server.sound.SoundEvent
import kotlin.math.*

/** Vanilla inputs feed the same server-owned attack geometry used by the combat spike. */
internal class CorePlayerCombat(
    val player: Player,
    private val weaponTier: () -> Int,
    private val armorTier: () -> Int,
    private val encounter: () -> QuestEncounterCombat?,
    private val onDefeated: () -> Unit,
) {
    private val normal = CombatState(weaponSource = { WeaponType.HEAVY_BLADE })
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
    var mana = 100
        private set
    val maxHealth: Int get() = 100 + (armorTier() - 1) * 30
    val attackDamage: Double get() = 12.0 * 1.65.pow(weaponTier() - 1) * if (tickNumber < whetstoneUntil) 1.2 else 1.0

    private data class PendingSkill(val id: Int, val origin: Pos, val direction: Vec, var elapsed: Int = 0)

    fun attack() {
        if (defeated || pending != null || encounter() == null || player.openInventory != null) return
        normal.input(AttackInputState.PRESS)
        normal.input(AttackInputState.RELEASE)
        lastCombat = tickNumber
    }

    fun skill(id: Int) {
        if (id !in 0..2 || defeated || encounter() == null || player.openInventory != null) return
        if (normal.isAttacking) { queuedSkill = id; return }
        if (pending != null) return
        if (tickNumber < readyAt[id]) { notice("${SKILL_NAMES[id]}：あと${cooldownSeconds(id)}秒"); return }
        val cost = intArrayOf(15, 25, 35)[id]
        if (mana < cost) { notice("マナが足りません（必要 $cost）"); return }
        mana -= cost
        readyAt[id] = tickNumber + intArrayOf(80, 140, 220)[id]
        pending = PendingSkill(id, player.position, flatFacing())
        lastCombat = tickNumber
        player.setHeldItemSlot(0)
        player.swingMainHand()
        sound(SoundEvent.ITEM_TRIDENT_THROW, 0.65f, 1.25f)
    }

    fun dodge() {
        if (defeated || encounter() == null || tickNumber < nextDodge) return
        if (normal.isAttacking || pending != null) { queuedDodge = true; return }
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
        if (defeated) return
        if (tickNumber % 20 == 0L) {
            mana = (mana + if (tickNumber - lastCombat > 100) 12 else 5).coerceAtMost(100)
        }
        val enemies = encounter() ?: return
        normal.tick(player.position, player.position.direction(), enemies.combatTargets()).forEach { event ->
            when (event) {
                is CombatEvent.Active -> arc(player.position, flatFacing(), 4.5, 1.15, Particle.SWEEP_ATTACK)
                is CombatEvent.HitConfirmed -> if (visibleTo(event.targetId, enemies)) {
                    if (enemies.applyDamage(event.targetId, player, attackDamage)) hitFeedback()
                }
                is CombatEvent.Started -> Unit
            }
        }
        pending?.let { action ->
            action.elapsed++
            when (action.id) {
                0 -> if (action.elapsed == 5) {
                    // Stop in front of a nearby enemy instead of dashing through it and missing behind us.
                    val distance = enemies.combatTargets().mapNotNull { target ->
                        val offset = target.position.sub(player.position)
                        val forward = offset.x() * action.direction.x() + offset.z() * action.direction.z()
                        val lateral = abs(offset.x() * action.direction.z() - offset.z() * action.direction.x())
                        if (forward >= 0 && lateral <= 0.85 && abs(offset.y()) <= 2.5) (forward - 1.0).coerceAtLeast(0.0) else null
                    }.minOrNull()?.coerceAtMost(2.2) ?: 2.2
                    moveSafely(action.direction, distance)
                    strike(enemies, player.position, action.direction, 4.5, 0.45, 1.7)
                }
                1 -> {
                    if (action.elapsed < 12 && action.elapsed % 3 == 0) arc(action.origin, action.direction, 5.0, 0.85, Particle.CRIT)
                    if (action.elapsed == 12) {
                        strike(enemies, action.origin, action.direction, 5.0, cos(0.85), 2.6)
                        arc(action.origin, action.direction, 5.0, 0.85, Particle.SWEEP_ATTACK)
                        sound(SoundEvent.ENTITY_GENERIC_EXPLODE, 0.45f, 1.2f)
                    }
                }
                2 -> if (action.elapsed in listOf(6, 14, 22)) {
                    strike(enemies, player.position, action.direction, 3.3, -1.0, 1.15)
                    arc(player.position, action.direction, 3.3, PI, Particle.SWEEP_ATTACK)
                }
            }
            if (action.elapsed >= intArrayOf(16, 25, 31)[action.id]) pending = null
        }
        if (!normal.isAttacking && pending == null) {
            if (queuedDodge) { queuedDodge = false; dodge() }
            else queuedSkill?.let { queuedSkill = null; skill(it) }
        }
    }

    private fun strike(enemies: QuestEncounterCombat, origin: Pos, direction: Vec, range: Double, minDot: Double, multiplier: Double) {
        enemies.combatTargets().forEach { target ->
            val offset = Vec(target.position.x() - origin.x(), 0.0, target.position.z() - origin.z())
            val length = offset.length()
            if (length <= range && abs(target.position.y() - origin.y()) <= 2.5 &&
                (length < 0.1 || offset.normalize().dot(direction) >= minDot) && visibleTo(target.id, enemies)) {
                if (enemies.applyDamage(target.id, player, attackDamage * multiplier)) hitFeedback()
            }
        }
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
        val adjusted = amount * (1.0 - (armorTier() - 1) * 0.10)
        lastCombat = tickNumber
        if (player.health - adjusted <= 0.0) {
            defeated = true
            resetActions()
            onDefeated()
            return
        }
        player.health = (player.health - adjusted).toFloat()
        sound(SoundEvent.ENTITY_PLAYER_HURT, 0.65f, 1f)
    }

    fun healPotion() {
        if (!defeated) {
            player.health = (player.health + maxHealth * 0.45f).coerceAtMost(maxHealth.toFloat())
            sound(SoundEvent.ENTITY_GENERIC_DRINK, 0.75f, 1f)
        }
    }

    fun sharpen() { whetstoneUntil = tickNumber + 20 * 180 }
    fun cooldownSeconds(id: Int): Long = ((readyAt[id] - tickNumber).coerceAtLeast(0) + 19) / 20
    fun reset() { resetActions(); defeated = false; mana = 100; readyAt.fill(0L); nextDodge = 0L; player.health = maxHealth.toFloat() }
    fun resetActions() { normal.reset(); pending = null; queuedSkill = null; queuedDodge = false }

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
    private fun hitFeedback() { sound(SoundEvent.ENTITY_PLAYER_ATTACK_STRONG, 0.4f, 1f) }
    private fun notice(message: String) = player.sendActionBar(Component.text(message, NamedTextColor.YELLOW))
    private fun sound(event: SoundEvent, volume: Float, pitch: Float) = player.playSound(Sound.sound(event, Sound.Source.PLAYER, volume, pitch))

    private fun arc(origin: Pos, facing: Vec, radius: Double, halfAngle: Double, particle: Particle) {
        val yaw = atan2(facing.z(), facing.x())
        val points = if (particle == Particle.SWEEP_ATTACK) 5 else 17
        repeat(points) { index ->
            val angle = yaw - halfAngle + 2 * halfAngle * index / (points - 1)
            player.instance.sendGroupedPacket(ParticlePacket(particle,
                origin.add(cos(angle) * radius, 0.6, sin(angle) * radius), Vec.ZERO, 0f, 1))
        }
    }

    companion object { val SKILL_NAMES = listOf("踏み込み斬り", "地砕き", "旋風斬り") }
}
