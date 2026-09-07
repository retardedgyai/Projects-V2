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
    private val journey: () -> CoreJourney = { CoreJourney() },
    private val weaponBase: () -> CoreWeaponBase = { CoreWeaponBase.STANDARD },
    private val weaponLevelPower: () -> Double = { 1.0 },
    private val armorLevelPower: () -> Double = { 1.0 },
    private val onLesson: (Int) -> Unit = {},
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
    private var nextShot = 0L
    private var shot: Pair<Long, Vec>? = null
    private var charges = 0
    private var skillBoost = 1.0
    private var castCharges = 0
    private var lastChargeTick = -1L
    private val lessonAttempts = mutableMapOf<Int, Long>()
    val classId get() = journey().job
    val skillDefinitions get() = CoreSkillCatalog.skills(classId)
    val skillNames get() = skillDefinitions.map { it.name }
    fun skillAvailable(index: Int) = journey().legacy || journey().level >= listOf(1, 4, 8)[index]
    val weaponHint get() = if (weaponBase() == CoreWeaponBase.CONDUIT || classId == CoreClass.STARWEAVER) "蓄積 $charges/3" else weaponBase().displayName
    val chargeCount: Int? get() = if (weaponBase() == CoreWeaponBase.CONDUIT || classId == CoreClass.STARWEAVER) charges else null
    var defeated = false
        private set
    private var manaValue = 100.0
    val mana: Int get() = manaValue.toInt()
    var health = 100.0
        private set
    val sheet: CoreCombatSheet get() = CoreCombatSheet.from(CoreCombatGear(weaponTier(), armorTier(), weaponBase(),
        weaponEnhancement(), armorEnhancement(), weaponQuality(), armorQuality(), weaponLevelPower(), armorLevelPower(),
        weaponBroken(), armorBroken()), statSource())
    val maxMana: Int get() = sheet.mana.toInt().coerceAtLeast(1)
    val maxHealth: Int get() = sheet.health.toInt().coerceAtLeast(1)
    val attackSpeed: Double get() = sheet.attackSpeed
    val attackDamage: Double get() = sheet.ad
    val abilityPower: Double get() = sheet.ap
    private data class Burn(val damage: Double, val type: CoreDamageType, val stats: CoreAffixStats, var nextTick: Long, var remaining: Int)
    private val burns = mutableMapOf<UUID, Burn>()
    private val damageLabels = mutableListOf<Pair<Entity, Long>>()

    private data class PendingSkill(val id: Int, val definition: CoreSkillDefinition, val origin: Pos, val direction: Vec, val startup: Int, var elapsed: Int = 0)

    fun attack() {
        if (weaponBroken()) { notice("武器が破損しています。装備庫で修理してください"); return }
        if (defeated || pending != null || encounter() == null || player.openInventory != null) return
        if (classId != CoreClass.WARRIOR) {
            if (tickNumber < nextShot || shot != null) return
            shot = tickNumber + 4 to player.position.direction()
            nextShot = tickNumber + ceil(17.0 / attackSpeed).toLong().coerceAtLeast(8)
            player.swingMainHand(); lastCombat = tickNumber
            sound(if (classId == CoreClass.RANGER) SoundEvent.ENTITY_ARROW_SHOOT else SoundEvent.BLOCK_AMETHYST_BLOCK_CHIME, .65f, 1.2f)
            return
        }
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
        if (!skillAvailable(id)) { notice("${skillNames[id]}はLv${listOf(1, 4, 8)[id]}で解放されます"); return }
        if (normal.isAttacking) { normal.clearBuffer(); queuedSkill = id; return }
        if (pending != null || shot != null) return
        if (tickNumber < readyAt[id]) { notice("${skillNames[id]}：あと${cooldownSeconds(id)}秒"); return }
        val definition = skillDefinitions[id]
        val cost = definition.mana
        if (mana < cost) { notice("マナが足りません（必要 $cost）"); return }
        manaValue -= cost
        castCharges = charges; skillBoost = 1.0 + charges * .15; charges = 0
        readyAt[id] = tickNumber + cooldownTicks(id)
        val startup = definition.startupTicks(sheet)
        pending = PendingSkill(id, definition, player.position, if (id == 0 && classId != CoreClass.WARRIOR) player.position.direction() else flatFacing(), startup)
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
        if (weaponBase() == CoreWeaponBase.FLOW) normal.holdSequence()
        nextDodge = tickNumber + 24
        sound(SoundEvent.ENTITY_PLAYER_ATTACK_SWEEP, 0.35f, 1.7f)
    }

    fun tick() {
        tickNumber++
        damageLabels.removeAll { (entity, expiry) -> if (tickNumber >= expiry) { entity.remove(); true } else false }
        if (defeated) return
        if (tickNumber % 20 == 0L) {
            manaValue = (manaValue + CoreCombatMath.manaRegeneration(statSource(), tickNumber - lastCombat > 100)).coerceAtMost(maxMana.toDouble())
            player.getAttribute(Attribute.MOVEMENT_SPEED).baseValue = 0.1 * (1.0 + statSource().moveSpeedPercent.coerceIn(0.0, 25.0) / 100.0)
        }
        val enemies = encounter() ?: run { resetActions(); return }
        if (weaponBroken()) { resetActions(); return }
        val epoch = actionEpoch
        shot?.takeIf { tickNumber >= it.first }?.let { (_, direction) ->
            shot = null
            rangedStrike(enemies, direction, 18.0, .65, 1, 1.0, false)
        }
        if (!actionsValid(enemies, epoch)) return
        // A kill callback may synchronously return/reset the actor, including clearing burns.
        for ((id, burn) in burns.toMap()) {
            if (!enemies.isAlive(id)) { burns.remove(id); continue }
            if (tickNumber >= burn.nextTick) {
                enemies.applyCalculatedDamage(id, player, burn.damage, burn.type, burn.stats, effect = true)
                if (!actionsValid(enemies, epoch)) return
                burn.nextTick += 20; burn.remaining--
            }
            if (burn.remaining <= 0) burns.remove(id)
        }
        normal.tick()?.let { swing ->
            vfx.swingSound(swing.step)
            player.swingMainHand()
            vfx.play(arrayOf(GreatswordVisual.SWEEP, GreatswordVisual.REVERSE, GreatswordVisual.FINISHER)[swing.step - 1], player.position, normalDirection)
            var connected = false
            val heavyFinish = swing.step == 3 && weaponBase() == CoreWeaponBase.CLEAVER
            for (target in enemies.combatTargets()) {
                if (greatswordInRange(player.position, normalDirection, target, minDot = if (heavyFinish) .9 else .4) && visibleTo(target.id, enemies)) {
                    hit(enemies, target.id, swing.multiplier * if (heavyFinish) 1.65 else 1.0, skill = false, heavy = swing.step == 3)
                    connected = true
                    if (!actionsValid(enemies, epoch)) return
                }
            }
            if (connected && swing.step == 3 && weaponBase() == CoreWeaponBase.FLOW) manaValue = min(maxMana.toDouble(), manaValue + 8)
        }
        pending?.let { action ->
            action.elapsed++
            if (classId != CoreClass.WARRIOR) {
                val woven = classId == CoreClass.STARWEAVER && castCharges == 3
                val pulseCount = action.definition.pulses + if (woven && action.id == 2) 1 else 0
                val pulseTick = action.elapsed - action.startup
                if (pulseTick >= 0 && pulseTick % 8 == 0 && pulseTick / 8 < pulseCount) {
                    if (action.id == 0) rangedStrike(enemies, action.direction, 20.0, .9, if (classId == CoreClass.RANGER || woven) 3 else 1, 2.0, true)
                    else if (action.id == 1) {
                        strike(enemies, player.position, action.direction, 6.5, if (classId == CoreClass.RANGER) .35 else -1.0, 1.8)
                        if (!actionsValid(enemies, epoch)) return
                        enemies.combatTargets().filter { greatswordInRange(player.position, action.direction, it, 6.5, if (classId == CoreClass.RANGER) .35 else -1.0) && visibleTo(it.id, enemies) }.forEach { enemies.applySlow(it.id, .4, if (woven) 4500 else 2500) }
                        if (woven) manaValue = min(maxMana.toDouble(), manaValue + 12)
                        spellRing(player.position, 5.0)
                    } else {
                        val centre = action.origin.add(action.direction.mul(7.0))
                        strike(enemies, centre, action.direction, 4.5, -1.0, 1.35)
                        if (!actionsValid(enemies, epoch)) return
                        spellRing(centre, 4.0, rain = true)
                    }
                }
                if (!actionsValid(enemies, epoch)) return
                if (action.elapsed >= action.startup + pulseCount * 8) pending = null
                return@let
            }
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
                2 -> if (action.elapsed >= action.startup && (action.elapsed - action.startup) % 8 == 0 &&
                    (action.elapsed - action.startup) / 8 < action.definition.pulses) {
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

    private fun hit(enemies: QuestEncounterCombat, id: UUID, multiplier: Double, skill: Boolean, heavy: Boolean = false, piercing: Boolean = false) {
        val epoch = actionEpoch
        val stats = statSource()
        val position = enemies.positionOf(id) ?: return
        val definition = if (skill) pending?.definition ?: return else null
        val formula = definition?.formula ?: CoreSkillCatalog.basicFormula
        val type = definition?.type ?: CoreSkillCatalog.basicType(classId)
        val tags = definition?.tags ?: CoreSkillCatalog.basicTags(classId)
        val element = (stats.fireFlat + stats.iceFlat + stats.lightningFlat) * (if (skill) 1.0 else multiplier) * 0.65
        val critical = criticalRoll() < stats.criticalChance
        val weak = when (enemies.weaknessOf(id)) {
            "fire" -> stats.fireFlat > 0; "ice" -> stats.iceFlat > 0; "lightning" -> stats.lightningFlat > 0; else -> false
        }
        val baseDamage = definition?.baseDamage(sheet) ?: (formula.evaluate(sheet) * multiplier + element)
        val damage = CoreCombatMath.outgoing(baseDamage, type, tags, stats, critical) *
            (if (skill) skillBoost else 1.0) * (if (tickNumber < whetstoneUntil) 1.2 else 1.0) * (if (weak) 1.25 else 1.0)
        val applied = enemies.applyCalculatedDamage(id, player, damage, type, stats,
            projectile = classId != CoreClass.WARRIOR) ?: return
        val lesson = if (skill) 1 else 0
        if (!journey().knows(lesson) && tickNumber - (lessonAttempts[lesson] ?: -100L) >= 20) {
            lessonAttempts[lesson] = tickNumber; onLesson(lesson)
        }
        if (!skill && lastChargeTick != tickNumber && (weaponBase() == CoreWeaponBase.CONDUIT || classId == CoreClass.STARWEAVER)) {
            charges = (charges + 1).coerceAtMost(3); lastChargeTick = tickNumber
        }
        if (!actionsValid(enemies, epoch)) return
        val stolen = CoreCombatMath.lifeSteal(applied, stats, piercing || CoreAttackTag.AREA in tags || classId == CoreClass.WARRIOR)
        if (stolen > 0) heal(stolen)
        if (classId == CoreClass.WARRIOR) {
            vfx.impactSound(heavy)
            vfx.play(GreatswordVisual.HIT, position, normalDirection)
            vfx.holdContact(if (heavy) 3 else 2)
        } else {
            sound(if (classId == CoreClass.RANGER) SoundEvent.ENTITY_ARROW_HIT else SoundEvent.BLOCK_AMETHYST_BLOCK_CHIME, .4f, 1.4f)
            vfx.particles(if (classId == CoreClass.MAGE) Particle.FLAME else Particle.END_ROD, position.add(0.0, 1.0, 0.0), 6, Vec(.2, .3, .2), .03f)
        }
        showDamage(position, applied, critical, weak)
        if (stats.fireFlat > 0 && enemies.isAlive(id)) {
            burns[id] = Burn(maxOf(burns[id]?.damage ?: 0.0, CoreCombatMath.outgoing(stats.fireFlat * .3, type, tags, stats)), type, stats, tickNumber + 20, 3)
            vfx.particles(Particle.SMALL_FLAME, position.add(0.0, 1.0, 0.0), 7, Vec(0.2, 0.4, 0.2), 0.01f)
        }
        if (stats.iceFlat > 0) {
            enemies.applySlow(id, (0.15 + stats.iceFlat / 100.0).coerceAtMost(0.45), 2000)
            vfx.particles(Particle.SNOWFLAKE, position.add(0.0, 0.8, 0.0), 7, Vec(0.3, 0.3, 0.3), 0.03f)
        }
        if (stats.lightningFlat > 0) {
            enemies.combatTargets().filter { it.id != id && it.position.distance(position) <= 4.0 && visibleTo(it.id, enemies) }
                .minByOrNull { it.position.distance(position) }?.let { chained ->
                    if (enemies.applyCalculatedDamage(chained.id, player, CoreCombatMath.outgoing(stats.lightningFlat * .8, type, tags, stats), type, stats, effect = true) != null) {
                        if (!actionsValid(enemies, epoch)) return
                        val end = enemies.positionOf(chained.id) ?: position
                        for (step in 0..8) vfx.particles(Particle.ELECTRIC_SPARK,
                            position.add(end.sub(position).mul(step / 8.0)).add(0.0, 0.8, 0.0), 1)
                    }
                }
        }
    }

    /** Narrow server ray with block occlusion. No client-selected target or through-wall projectile. */
    private fun rangedStrike(enemies: QuestEncounterCombat, direction: Vec, range: Double, width: Double, count: Int, multiplier: Double, skill: Boolean) {
        val origin = player.position.add(0.0, 1.0, 0.0)
        val candidates = enemies.combatTargets().mapNotNull { target ->
            val position = enemies.positionOf(target.id)?.add(0.0, 1.0, 0.0) ?: return@mapNotNull null
            val offset = position.sub(origin)
            val along = offset.x() * direction.x() + offset.y() * direction.y() + offset.z() * direction.z()
            if (along !in 0.0..range || origin.add(direction.mul(along)).distance(position) > width + target.halfExtent.x() || !visibleTo(target.id, enemies)) null else target.id to along
        }.sortedBy { it.second }.take(count)
        val distance = candidates.lastOrNull()?.second ?: range
        for (step in 1..ceil(distance * 2).toInt()) {
            val p = origin.add(direction.mul(step / 2.0))
            if (player.instance.getBlock(p).isSolid) break
            vfx.particles(if (classId == CoreClass.MAGE) Particle.FLAME else Particle.END_ROD, p, 1)
        }
        val epoch = actionEpoch
        for ((id, _) in candidates) {
            hit(enemies, id, multiplier, skill, piercing = count > 1)
            if (!actionsValid(enemies, epoch)) return
        }
    }

    private fun spellRing(centre: Pos, radius: Double, rain: Boolean = false) {
        for (i in 0 until 28) {
            val angle = i * Math.PI * 2 / 28
            val particle = if (classId == CoreClass.STARWEAVER || (rain && classId == CoreClass.RANGER)) Particle.END_ROD else if (rain) Particle.FLAME else Particle.SNOWFLAKE
            vfx.particles(particle,
                centre.add(cos(angle) * radius, .25, sin(angle) * radius), 1)
            if (rain && i % 4 == 0) for (height in 1..5) vfx.particles(particle, centre.add(cos(angle) * radius * .7, height.toDouble(), sin(angle) * radius * .7), 1)
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

    fun hurt(amount: Double, type: CoreDamageType = CoreDamageType.PHYSICAL) {
        if (defeated || encounter() == null || !amount.isFinite() || amount <= 0.0) return
        val snapshot = sheet
        val adjusted = CoreCombatMath.mitigate(amount, type, snapshot.ar, snapshot.mr, damageReduction = snapshot.mods.mitigationPercent)
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
            heal(CoreCombatMath.healing(maxHealth * .45, 1.0, sheet))
            sound(SoundEvent.ENTITY_GENERIC_DRINK, 0.75f, 1f)
        }
    }

    private fun heal(amount: Double) {
        if (defeated || !amount.isFinite() || amount <= 0) return
        health = min(maxHealth.toDouble(), health + amount)
        syncVanillaHealth()
    }

    fun sharpen() { whetstoneUntil = tickNumber + 20 * 180 }
    fun cooldownSeconds(id: Int): Long = ((readyAt[id] - tickNumber).coerceAtLeast(0) + 19) / 20
    fun cooldownTicks(id: Int): Int = skillDefinitions[id].cooldownTicks(statSource())
    fun cooldownRemaining(id: Int): Int = (readyAt[id] - tickNumber).coerceAtLeast(0).toInt()
    fun reset() { resetActions(); defeated = false; manaValue = maxMana.toDouble(); health = maxHealth.toDouble(); readyAt.fill(0L); nextDodge = 0L; syncVanillaHealth() }
    fun revive(fraction: Double) { require(fraction in .1..1.0); reset(); health = maxHealth * fraction; syncVanillaHealth() }
    fun resetActions() {
        actionEpoch++
        normal.reset(); pending = null; shot = null; charges = 0; castCharges = 0; skillBoost = 1.0; queuedSkill = null; queuedDodge = false; burns.clear()
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
