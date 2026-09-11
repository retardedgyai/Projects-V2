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
    private val allies: () -> List<CorePlayerCombat> = { emptyList() },
    private val onDefeated: () -> Unit,
) {
    private val normal = GreatswordCombo()
    private val vfx = GreatswordVfx(player)
    private val warriorMarks = CoreWarriorMarkDisplay(player)
    internal val activeMarkLabels get() = warriorMarks.size
    private var normalDirection = Vec(0.0, 0.0, 1.0)
    private var normalEmpowerment = 1.0
    private var actionEpoch = 0L
    internal val activeVisualEffects: Int get() = vfx.activeEffects
    private var tickNumber = 0L
    private var nextDodge = 0L
    private var lastCombat = -400L
    private val readyAt = LongArray(5)
    internal val classState = CoreClassState()
    private var previousPosition: Pos? = null
    private var moveHasteUntil = -1L
    private var pending: PendingSkill? = null
    private var queuedSkill: Int? = null
    private var queuedDodge = false
    private var whetstoneUntil = 0L
    private var nextShot = 0L
    private var shot: Pair<Long, Vec>? = null
    private var skillBoost = 1.0
    private var castCharges = 0
    private var lastConduitGain = -1L
    private val lessonAttempts = mutableMapOf<Int, Long>()
    val classId get() = journey().job
    val skillDefinitions get() = CoreSkillCatalog.equipped(journey(), statSource())
    val skillNames get() = skillDefinitions.map { it.name }
    fun skillAvailable(index: Int) = index in 0..4 && (journey().legacy || journey().level >= CoreSkillCatalog.unlockLevels[index])
    fun resourceAvailable(index: Int) = classState.canCast(skillDefinitions[index], journey().build)
    val weaponHint get() = "${classId.resourceName} ${classState.resource.toInt()}/${classState.cap(classId).toInt()}"
    val combatCue get() = CoreWarriorSkillPresentation.combatCue(classId,tickNumber,classState.counterUntil,classState.guardUntil)
    val chargeCount: Int? get() = if (classId == CoreClass.STARWEAVER) classState.resource.toInt() else null
    val resource get() = classState.resource
    val resourceMax get() = classState.cap(classId)
    val shield get() = classState.shield
    var defeated = false
        private set
    private var manaValue = 100.0
    val mana: Int get() = manaValue.toInt()
    var health = 100.0
        private set
    val sheet: CoreCombatSheet get() = CoreCombatSheet.from(CoreCombatGear(weaponTier(), armorTier(), weaponBase(),
        weaponEnhancement(), armorEnhancement(), weaponQuality(), armorQuality(), weaponLevelPower(), armorLevelPower(),
        weaponBroken(), armorBroken()), statSource()).specialize(journey())
    val maxMana: Int get() = sheet.mana.toInt().coerceAtLeast(1)
    val maxHealth: Int get() = sheet.health.toInt().coerceAtLeast(1)
    val attackSpeed: Double get() = sheet.attackSpeed
    val attackDamage: Double get() = sheet.ad
    val abilityPower: Double get() = sheet.ap
    private data class Burn(val damage: Double, val type: CoreDamageType, val stats: CoreAffixStats, var nextTick: Long, var remaining: Int)
    private data class DotKey(val id:UUID,val poison:Boolean = false)
    private val burns = mutableMapOf<DotKey, Burn>()
    private var visualContactsThisTick = 0
    private val damageLabels = mutableListOf<Pair<Entity, Long>>()

    private data class PendingSkill(val id: Int, val definition: CoreSkillDefinition, val origin: Pos, val direction: Vec, val startup: Int,
        var elapsed: Int = 0, var gained: Boolean = false, val empowered: Double = 0.0, val counter: Boolean = false)

    fun attack() {
        if (weaponBroken()) { notice("武器が破損しています。装備庫で修理してください"); return }
        if (defeated || pending != null || player.openInventory != null) return
        val enemies = encounter() ?: return
        if (!classId.melee) {
            if (tickNumber < nextShot || shot != null) return
            shot = tickNumber + 4 to player.position.direction()
            nextShot = tickNumber + ceil(17.0 / attackSpeed).toLong().coerceAtLeast(8)
            player.swingMainHand(); lastCombat = tickNumber
            sound(if (classId == CoreClass.RANGER) SoundEvent.ENTITY_ARROW_SHOOT else SoundEvent.BLOCK_AMETHYST_BLOCK_CHIME, .65f, 1.2f)
            return
        }
        normal.press(attackSpeed, quick = classId == CoreClass.ASSASSIN, immediate = classId == CoreClass.WARRIOR)?.let { swing ->
            normalEmpowerment = if(classId == CoreClass.TEMPLAR && classState.counterUntil >= tickNumber) 1.35 else 1.0
            if(normalEmpowerment > 1) classState.counterUntil = -1
            normalDirection = flatFacing()
            lastCombat = tickNumber
            if (swing.impactTick == 0) releaseNormal(enemies, swing)
            else vfx.startSound(swing.step)
        }
    }

    fun skill(id: Int) {
        if (weaponBroken()) { notice("武器が破損しています。装備庫で修理してください"); return }
        if (id !in 0..4 || defeated || encounter() == null || player.openInventory != null) return
        if (!skillAvailable(id)) { notice("${skillNames[id]}はLv${CoreSkillCatalog.unlockLevels[id]}で解放されます"); return }
        if (normal.isAttacking) { normal.clearBuffer(); queuedSkill = id; return }
        if (pending != null || shot != null) return
        if (tickNumber < readyAt[id]) { notice("${skillNames[id]}：あと${cooldownSeconds(id)}秒"); return }
        val definition = skillDefinitions[id]
        if (!classState.canCast(definition, journey().build)) { notice("${classId.resourceName}が足りません（必要 ${definition.spend} / 現在 ${resource.toInt()}）"); return }
        val cost = definition.mana
        if (mana < cost) { notice("マナが足りません（必要 $cost）"); return }
        manaValue -= cost
        val spentFrom = classState.spend(definition, classId, journey().build)
        castCharges = if (classId == CoreClass.STARWEAVER) spentFrom.toInt() else 0
        skillBoost = if (classId == CoreClass.STARWEAVER && definition.gain == 0 && definition.motion != CoreSkillMotion.EVADE) 1 + spentFrom * .15 else 1.0
        if (weaponBase() == CoreWeaponBase.CONDUIT && definition.spend > 0) skillBoost *= 1.12
        val counter = classState.counterUntil >= tickNumber && definition.formula.ad > 0 &&
            definition.motion !in setOf(CoreSkillMotion.SHIELD,CoreSkillMotion.HEAL,CoreSkillMotion.GUARD)
        if (counter) { skillBoost *= if (journey().build.keystone == 1 && classId == CoreClass.WARRIOR) 1.6 else 1.35; classState.counterUntil = -1 }
        var castDefinition = definition
        if (classId == CoreClass.STARWEAVER && spentFrom == 3.0 && definition.motion !in setOf(CoreSkillMotion.EVADE, CoreSkillMotion.HEAL, CoreSkillMotion.SHIELD)) {
            var extra = if (definition.motion == CoreSkillMotion.FIELD) 1 else 0
            if (journey().build.keystone == 0) { extra++; skillBoost *= .85 }
            val orbit = statSource().bonus(CoreAffixStat.STAR_ORBIT)
            if (orbit > 0) { extra++; skillBoost *= .65 + orbit.coerceAtMost(20.0) / 100 }
            castDefinition = definition.copy(pulses = (definition.pulses + extra).coerceAtMost(8))
        }
        readyAt[id] = tickNumber + cooldownTicks(id)
        val startup = definition.startupTicks(sheet)
        pending = PendingSkill(id, castDefinition, if(definition.motion == CoreSkillMotion.FIELD) aimedGround(definition, encounter()!!) else player.position,
            if (definition.motion == CoreSkillMotion.RAY) player.position.direction() else flatFacing(), startup,
            empowered = spentFrom, counter = counter)
        lastCombat = tickNumber
        player.setHeldItemSlot(0)
        player.swingMainHand()
        if (startup > 1) vfx.playSkill(CoreSkillEffect(classId, castDefinition, pending!!.origin, pending!!.direction,
            CoreSkillVisualPhase.PREPARE, prepareTicks = startup - 1))
    }

    fun dodge() {
        if (defeated || encounter() == null || tickNumber < nextDodge) return
        if (pending != null) { pending = null; vfx.cancel(); queuedSkill = null }
        if (normal.isAttacking) { normal.clearBuffer(); queuedDodge = true; return }
        val input = player.inputs()
        val forward = (if (input.forward()) 1.0 else 0.0) - (if (input.backward()) 1.0 else 0.0)
        val side = (if (input.right()) 1.0 else 0.0) - (if (input.left()) 1.0 else 0.0)
        val facing = flatFacing()
        val right = Vec(-facing.z(), 0.0, facing.x())
        val direction = if (forward == 0.0 && side == 0.0) facing else facing.mul(forward).add(right.mul(side)).normalize()
        moveSafely(direction, 2.6)
        if (weaponBase() == CoreWeaponBase.FLOW) normal.holdSequence()
        nextDodge = tickNumber + if (journey().build.has(4)) 20 else 24
        classState.dodgeUntil = tickNumber + 60
        sound(SoundEvent.ENTITY_PLAYER_ATTACK_SWEEP, 0.35f, 1.7f)
    }

    fun tick() {
        tickNumber++
        visualContactsThisTick = 0
        classState.tick(tickNumber)
        previousPosition?.let { if (it.distance(player.position) > .035) classState.stationarySince = tickNumber }
        previousPosition = player.position
        damageLabels.removeAll { (entity, expiry) -> if (tickNumber >= expiry) { entity.remove(); true } else false }
        if (defeated) return
        if (tickNumber % 20 == 0L) {
            manaValue = (manaValue + CoreCombatMath.manaRegeneration(statSource(), tickNumber - lastCombat > 100)).coerceAtMost(maxMana.toDouble())
            player.getAttribute(Attribute.MOVEMENT_SPEED).baseValue = 0.1 * (1.0 + statSource().moveSpeedPercent.coerceIn(0.0, 25.0) / 100.0 + if (tickNumber < moveHasteUntil) .2 else 0.0)
        }
        val enemies = encounter() ?: run { resetActions(); return }
        if (weaponBroken()) { resetActions(); return }
        val epoch = actionEpoch
        shot?.takeIf { tickNumber >= it.first }?.let { (_, direction) ->
            shot = null
            val mobile = classId == CoreClass.RANGER && journey().build.keystone == 1 && tickNumber < classState.dodgeUntil
            rangedStrike(enemies, direction, 18.0, .65, 1, if (mobile) .65 else 1.0, false)
            if (mobile && actionsValid(enemies, epoch)) rangedStrike(enemies, direction, 18.0, .65, 1, .65, false)
        }
        if (!actionsValid(enemies, epoch)) return
        // A kill callback may synchronously return/reset the actor, including clearing burns.
        for ((key, burn) in burns.toMap()) {
            val id=key.id
            if (!enemies.isAlive(id)) { burns.remove(key); continue }
            if (tickNumber >= burn.nextTick) {
                val applied = enemies.applyCalculatedDamage(id, player, burn.damage, burn.type, burn.stats, effect = true)
                if (!actionsValid(enemies, epoch)) return
                if (key.poison && applied != null) enemies.positionOf(id)?.let { vfx.status(CorePoisonEffect(it, tickNumber, true)) }
                burn.nextTick += 20; burn.remaining--
            } else if (key.poison && tickNumber % 4 == 0L) {
                enemies.positionOf(id)?.let { vfx.status(CorePoisonEffect(it, tickNumber, false)) }
            }
            if (burn.remaining <= 0) burns.remove(key)
        }
        normal.tick()?.let { swing -> if (!releaseNormal(enemies, swing)) return }
        pending?.let { action ->
            action.elapsed++
            val pulse = action.elapsed - action.startup
            if (pulse >= 0 && pulse % 8 == 0 && pulse / 8 < action.definition.pulses) {
                executeSkill(enemies, action)
                if (!actionsValid(enemies, epoch)) return
            }
            if (pulse >= 0 && pulse % 8 == 4 && pulse / 8 + 1 < action.definition.pulses && action.definition.motion == CoreSkillMotion.FIELD) {
                vfx.playSkill(CoreSkillEffect(classId, action.definition, action.origin, action.direction,
                    CoreSkillVisualPhase.PREPARE, pulse = pulse / 8 + 1, prepareTicks = 4))
            }
            if (action.elapsed >= action.startup + action.definition.pulses * 8 + 3) pending = null
        }
        if (!normal.isAttacking && pending == null) {
            if (queuedDodge) { normal.clearBuffer(); queuedDodge = false; dodge() }
            else if (queuedSkill != null) { normal.clearBuffer(); val id = queuedSkill!!; queuedSkill = null; skill(id) }
            else if (normal.takeBuffered()) attack()
        }
        if(classId==CoreClass.WARRIOR) {
            warriorMarks.update(enemies.combatTargets().filter { classState.marked(it.id,tickNumber) && visibleTo(it.id,enemies) },classState,tickNumber)
        } else warriorMarks.clear()
        vfx.tick()
    }

    /** Same server-owned strike for input-time warrior AA and delayed other melee jobs. */
    private fun releaseNormal(enemies: QuestEncounterCombat, swing: GreatswordCombo.Swing): Boolean {
        val epoch = actionEpoch
        vfx.swingSound(swing.step)
        player.swingMainHand()
        if (classId == CoreClass.WARRIOR) vfx.play(arrayOf(GreatswordVisual.SWEEP, GreatswordVisual.REVERSE, GreatswordVisual.FINISHER)[swing.step - 1], player.position, normalDirection)
        else vfx.play(CoreClassEffect(classId, CoreSkillMotion.CONE, player.position, normalDirection,
            if (classId == CoreClass.ASSASSIN) 3.0 else 4.5, swing.step == 3, reverse = swing.step == 2))
        var connected = false
        val heavyFinish = swing.step == 3 && weaponBase() == CoreWeaponBase.CLEAVER
        for (target in enemies.combatTargets()) {
            if (greatswordInRange(player.position, normalDirection, target, range = if (classId == CoreClass.ASSASSIN) 3.0 else 4.5,
                    minDot = if (heavyFinish) .9 else .4) && visibleTo(target.id, enemies)) {
                hit(enemies, target.id, swing.multiplier * normalEmpowerment * if (heavyFinish) 1.65 else 1.0, skill = false, heavy = swing.step == 3)
                connected = true
                if (!actionsValid(enemies, epoch)) return false
            }
        }
        if (connected && swing.step == 3 && weaponBase() == CoreWeaponBase.FLOW) manaValue = min(maxMana.toDouble(), manaValue + 8)
        return actionsValid(enemies, epoch)
    }

    private fun executeSkill(enemies: QuestEncounterCombat, action: PendingSkill) {
        val s = action.definition
        val epoch = actionEpoch
        val build = journey().build
        fun pulse(at: Pos, radius: Double = s.radius) {
            emitSkillPulse(at, radius)
            strike(enemies, at, action.direction, radius, -1.0, 1.0)
        }
        when (s.motion) {
            CoreSkillMotion.RAY -> rangedStrike(enemies, action.direction, s.range, .65,
                if ((classId == CoreClass.STARWEAVER && action.empowered >= 3) || s.icon == "hunt_pierce") 3 else 1, 1.0, true)
            CoreSkillMotion.LUNGE -> {
                val maximum = if (s.range == 18.0) 2.2 else s.range
                val distance = enemies.combatTargets().mapNotNull { target ->
                    val offset = target.position.sub(player.position)
                    val forward = offset.x() * action.direction.x() + offset.z() * action.direction.z()
                    val lateral = abs(offset.x() * action.direction.z() - offset.z() * action.direction.x())
                    if (forward >= 0 && lateral < .9 && abs(offset.y()) < 2.5) (forward - 1).coerceAtLeast(0.0) else null
                }.minOrNull()?.coerceAtMost(maximum) ?: maximum
                moveSafely(action.direction, distance)
                emitSkillPulse(player.position, s.radius)
                strike(enemies, player.position, action.direction, s.radius, .35, 1.0)
            }
            CoreSkillMotion.CONE -> {
                emitSkillPulse(player.position, s.radius)
                strike(enemies, player.position, action.direction, s.radius, .35, 1.0)
            }
            CoreSkillMotion.SPIN -> pulse(player.position)
            CoreSkillMotion.NOVA -> pulse(player.position)
            CoreSkillMotion.FIELD -> pulse(action.origin)
            CoreSkillMotion.EVADE -> {
                val departure = player.position
                moveSafely(action.direction.mul(if (s.range < 0) -1.0 else 1.0), abs(s.range))
                classState.dodgeUntil = tickNumber + 60
                if (classId == CoreClass.STARWEAVER && build.keystone == 1) classState.gain(1.0, classId, build)
                if (classId == CoreClass.HEALER) grantNearbyShields(8 + abilityPower * .3, 4.0, 60)
                if (s.formula.ad > 0 || s.formula.ap > 0) rangedStrike(enemies, action.direction, 18.0, .7, 1, 1.0, true)
                else if (s.status == CoreSkillStatus.SLOW) enemies.combatTargets().filter { it.position.distance(action.origin) < 4 && visibleTo(it.id, enemies) }
                    .forEach { enemies.applySlow(it.id, .5, 2500) }
                if (actionsValid(enemies, epoch) && s.formula.ad == 0.0 && s.formula.ap == 0.0) {
                    emitSkillPulse(departure, 2.0, CoreSkillEndpoint.DEPARTURE)
                    emitSkillPulse(player.position, 2.0, CoreSkillEndpoint.ARRIVAL)
                }
            }
            CoreSkillMotion.GUARD -> {
                classState.guardUntil = tickNumber + s.duration; classState.perfectUntil = tickNumber + 12
                if (classId == CoreClass.WARRIOR && build.keystone == 2) grantNearbyShields(12 + attackDamage * .4, 6.0, 80)
                emitSkillPulse(player.position, 1.0)
                sound(SoundEvent.ITEM_SHIELD_BLOCK, .7f, 1.0f)
            }
            CoreSkillMotion.PULL -> {
                emitSkillPulse(player.position, s.radius)
                val targets = enemies.combatTargets().filter { it.position.distance(player.position) <= s.radius && visibleTo(it.id, enemies) }
                for (target in targets) {
                    hit(enemies, target.id, 1.0, true)
                    if (!actionsValid(enemies, epoch)) return
                    enemies.pull(target.id, player, 3 + statSource().bonus(CoreAffixStat.TEMP_GRAVITY).coerceAtMost(10.0) * .2)
                    enemies.taunt(target.id, player)
                    if (build.keystone == 1) enemies.expose(target.id)
                }
            }
            CoreSkillMotion.HEAL -> {
                val conversion = CoreSkillCatalog.healConversion(journey(), statSource())
                var recovered = 0.0
                for (ally in nearbyAllies(s.radius)) {
                    val amount = CoreCombatMath.healing(s.formula.evaluate(sheet), 1.0, sheet, ally.sheet.mods) * skillBoost
                    val before = ally.health
                    ally.heal(amount * (1 - conversion))
                    recovered += ally.health - before
                    if (conversion > 0) ally.receiveShield(amount * conversion * shieldScale, 100)
                    if (build.keystone == 2 && classId == CoreClass.HEALER) ally.moveHasteUntil = ally.tickNumber + 60
                }
                if (classId == CoreClass.HEALER && build.keystone == 1) classState.gain((recovered * .15).coerceAtMost(15.0), classId, build)
                if (s.icon == "heal_wind") nextDodge = 0
                emitSkillPulse(player.position, s.radius)
            }
            CoreSkillMotion.SHIELD -> {
                grantNearbyShields((s.formula.evaluate(sheet) + sheet.healingPower) * skillBoost, s.radius, s.duration)
                if (classId == CoreClass.TEMPLAR || s.icon == "war_banner") {
                    classState.guardUntil = tickNumber + minOf(s.duration, 60); classState.perfectUntil = tickNumber + 12
                }
                if (classId == CoreClass.TEMPLAR && build.keystone == 0) classState.counterUntil = tickNumber + 80
                emitSkillPulse(player.position, s.radius.coerceAtLeast(1.0))
            }
        }
        if (!actionsValid(enemies, epoch)) return
        if (classId == CoreClass.STARWEAVER && action.empowered >= 3 && action.elapsed == action.startup && build.keystone == 2) {
            grantNearbyShields(10 + abilityPower * .5, 7.0, 100)
        }
    }

    private fun nearbyAllies(radius: Double): List<CorePlayerCombat> = (allies() + this).distinctBy { it.player.uuid }.filter {
        !it.defeated && it.player.instance === player.instance && it.encounter() === encounter() &&
            it.player.position.distance(player.position) <= radius.coerceAtLeast(.1) && clearLine(player.position, it.player.position)
    }
    private val shieldScale get() = sheet.shieldMultiplier
    private fun grantNearbyShields(amount: Double, radius: Double, duration: Int) {
        val scale = shieldScale
        for (ally in nearbyAllies(radius)) ally.receiveShield(amount * scale *
            if (classId == CoreClass.TEMPLAR && journey().build.keystone == 2 && ally !== this) 1.4 else 1.0, duration)
    }
    private fun receiveShield(amount: Double, duration: Int) {
        if (defeated || !amount.isFinite() || amount <= 0) return
        // Strongest shield wins: overlapping support players cannot stack unlimited effective HP.
        val next = amount.coerceAtMost(maxHealth * .75)
        if (next >= classState.shield) {
            classState.shield = next
            classState.shieldUntil = tickNumber + duration.coerceIn(1, 200)
        }
    }
    private fun clearLine(from: Pos, to: Pos): Boolean {
        val start = from.add(0.0, 1.0, 0.0); val end = to.add(0.0, 1.0, 0.0)
        val steps = ceil(start.distance(end) * 4).toInt().coerceAtLeast(1)
        return (1 until steps).all { !player.instance.getBlock(start.add(end.sub(start).mul(it.toDouble() / steps))).isSolid }
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
        val stats = sheet.mods
        val build = journey().build
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
        var classMultiplier = 1.0
        if (classId == CoreClass.RANGER) {
            if (classState.focus == id) classMultiplier *= 1 + classState.focusHits * .04
            if (build.keystone == 0 && tickNumber - classState.stationarySince >= 40) classMultiplier *= 1.25
        }
        if (classId == CoreClass.WARRIOR && build.keystone == 0 && resource >= 60) classMultiplier *= 1.2
        if (classId == CoreClass.HEALER && build.keystone == 0) classMultiplier *= 1.25
        if (classId == CoreClass.TEMPLAR && build.keystone == 2) classMultiplier *= .9
        val consumeMark = skill && classState.marked(id, tickNumber) && (definition!!.spend > 0 || (classId == CoreClass.STARWEAVER && castCharges > 0))
        if (consumeMark) {
            classMultiplier *= 1.35
            val info = enemies.mobInfo(id)
            if (classId == CoreClass.ASSASSIN && build.keystone == 0 && info != null && info.health / info.maximumHealth <= .35) classMultiplier *= 1.4
        }
        val damage = CoreCombatMath.outgoing(baseDamage, type, tags, stats, critical) *
            (if (skill) skillBoost else 1.0) * classMultiplier * (if (tickNumber < whetstoneUntil) 1.2 else 1.0) * (if (weak) 1.25 else 1.0)
        val applied = enemies.applyCalculatedDamage(id, player, damage, type, stats,
            projectile = !classId.melee || definition?.motion == CoreSkillMotion.RAY || definition?.motion == CoreSkillMotion.FIELD || (definition?.radius ?: 0.0) > 7.0) ?: return
        val lesson = if (skill) 1 else 0
        if (!journey().knows(lesson) && tickNumber - (lessonAttempts[lesson] ?: -100L) >= 20) {
            lessonAttempts[lesson] = tickNumber; onLesson(lesson)
        }
        if (!actionsValid(enemies, epoch)) return
        if (!skill) {
            classState.normalHit(classId, id, tickNumber, build)
            if(weaponBase() == CoreWeaponBase.CONDUIT && lastConduitGain != tickNumber) {
                classState.gain(4.0, classId, build); lastConduitGain = tickNumber
            }
        }
        else pending?.takeUnless { it.gained }?.let { action ->
            action.gained = true; classState.skillHit(classId, action.definition, build)
            if (classId == CoreClass.STARWEAVER && action.empowered >= 3 && action.definition.icon == "star_ring") manaValue = min(maxMana.toDouble(), manaValue + 12)
        }
        if (consumeMark) { classState.consumeMark(id, tickNumber); if (classId == CoreClass.ASSASSIN && build.keystone == 1) nextDodge = 0 }
        if (classId == CoreClass.TEMPLAR) enemies.taunt(id, player)
        if (definition != null) when (definition.status) {
            CoreSkillStatus.MARK -> classState.mark(id, tickNumber)
            CoreSkillStatus.SLOW -> {
                val slow = if (classId == CoreClass.MAGE && build.keystone == 2) .6 else .4
                enemies.applySlow(id, slow, if (classId == CoreClass.RANGER && build.keystone == 2) 4500 else 3000)
                if (classId == CoreClass.MAGE && build.keystone == 2) receiveShield((8 + abilityPower * .3) * shieldScale, 80)
            }
            CoreSkillStatus.EXPOSE -> enemies.expose(id)
            CoreSkillStatus.POISON -> {
                burns[DotKey(id,true)] = Burn(CoreCombatMath.outgoing(formula.evaluate(sheet) * .3,type,tags,stats), type, stats, tickNumber + 20, 3)
                if (classId == CoreClass.RANGER) enemies.applySlow(id, .45, 3500)
            }
            CoreSkillStatus.NONE -> Unit
        }
        val venom = stats.bonus(CoreAffixStat.ASS_VENOM)
        if (classId == CoreClass.ASSASSIN && ((venom > 0 && definition?.status == CoreSkillStatus.MARK) ||
                (!skill && build.keystone == 2 && classState.marked(id, tickNumber)))) {
            burns[DotKey(id,true)] = Burn(CoreCombatMath.outgoing(attackDamage * (.1 + venom * .02),type,tags,stats), type, stats, tickNumber + 20, 3)
        }
        val stolen = CoreCombatMath.lifeSteal(applied, stats, piercing || CoreAttackTag.AREA in tags || classId.melee)
        if (stolen > 0) heal(stolen)
        if (definition != null) {
            if (visualContactsThisTick++ < 3) vfx.playSkill(CoreSkillEffect(classId, definition, position,
                pending!!.direction, CoreSkillVisualPhase.CONTACT, pulse = (pending!!.elapsed - pending!!.startup) / 8))
            if (burns.containsKey(DotKey(id, true))) vfx.status(CorePoisonEffect(position, tickNumber, false))
        } else if (classId.melee) {
            vfx.impactSound(heavy)
            if (classId == CoreClass.WARRIOR) {
                if (visualContactsThisTick++ < 3) vfx.normalContact(position, normalDirection)
            } else vfx.play(GreatswordVisual.HIT, position, normalDirection)
            vfx.holdContact(if (heavy) 3 else 2)
        } else {
            sound(if (classId == CoreClass.RANGER) SoundEvent.ENTITY_ARROW_HIT else SoundEvent.BLOCK_AMETHYST_BLOCK_CHIME, .4f, 1.4f)
            vfx.particles(if (classId == CoreClass.MAGE) Particle.FLAME else Particle.END_ROD, position.add(0.0, 1.0, 0.0), 6, Vec(.2, .3, .2), .03f)
        }
        showDamage(position, applied, critical, weak)
        if (stats.fireFlat > 0 && enemies.isAlive(id)) {
            burns[DotKey(id)] = Burn(maxOf(burns[DotKey(id)]?.damage ?: 0.0, CoreCombatMath.outgoing(stats.fireFlat * .3, type, tags, stats)), type, stats, tickNumber + 20, 3)
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
        var visibleDistance = 0.0
        for (step in 1..floor(distance * 4).toInt()) {
            val p = origin.add(direction.mul(step / 4.0))
            if (player.instance.getBlock(p).isSolid) break
            visibleDistance = step / 4.0
            if (!skill && step % 2 == 0) vfx.particles(if (classId == CoreClass.MAGE) Particle.FLAME else Particle.END_ROD, p, 1)
        }
        if (skill) pending?.let { action ->
            vfx.playSkill(CoreSkillEffect(classId, action.definition, origin, direction,
                pulse = (action.elapsed - action.startup) / 8, rayLength = visibleDistance, clippedRay = true))
        }
        val epoch = actionEpoch
        for ((id, _) in candidates) {
            hit(enemies, id, multiplier, skill, piercing = count > 1)
            if (!actionsValid(enemies, epoch)) return
        }
    }

    private fun emitSkillPulse(centre: Pos, radius: Double, endpoint: CoreSkillEndpoint = CoreSkillEndpoint.NONE) {
        val action = pending ?: return
        vfx.playSkill(CoreSkillEffect(classId, action.definition.copy(radius = radius), centre, action.direction,
            pulse = (action.elapsed - action.startup) / 8, endpoint = endpoint))
    }

    /** Lock a ground cast to the visible aimed enemy/block, not a fixed point seven blocks ahead. */
    private fun aimedGround(s: CoreSkillDefinition, enemies: QuestEncounterCombat): Pos {
        val from=player.position.add(0.0,1.0,0.0);val direction=player.position.direction()
        val maximum=minOf(s.range,24.0-s.radius).coerceAtLeast(1.0)
        val aimed=enemies.combatTargets().mapNotNull { target ->
            val position=enemies.positionOf(target.id) ?: return@mapNotNull null
            val offset=position.add(0.0,1.0,0.0).sub(from)
            val along=offset.x()*direction.x()+offset.y()*direction.y()+offset.z()*direction.z()
            if(along in 0.0..maximum && from.add(direction.mul(along)).distance(position.add(0.0,1.0,0.0)) < target.halfExtent.x()+.8 && visibleTo(target.id,enemies)) position to along else null
        }.minByOrNull { it.second }
        if(aimed!=null) return aimed.first
        var previous=from
        for(step in 1..ceil(maximum*4).toInt()) {
            val point=from.add(direction.mul(step*.25))
            if(player.instance.getBlock(point).isSolid) return previous.sub(0.0,if(direction.y()<-.1) 0.0 else 1.0,0.0)
            previous=point
        }
        return player.position.add(flatFacing().mul(minOf(maximum,7.0)))
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
        var adjusted = CoreCombatMath.mitigate(amount, type, snapshot.ar, snapshot.mr, damageReduction = snapshot.mods.mitigationPercent)
        if (classId == CoreClass.WARRIOR && journey().build.keystone == 0 && resource >= 60) adjusted *= 1.1
        if (tickNumber < classState.guardUntil) {
            adjusted *= if (tickNumber <= classState.perfectUntil) .2 else .45
            classState.guarded(classId, tickNumber, journey().build)
            sound(SoundEvent.ITEM_SHIELD_BLOCK, .8f, if (tickNumber <= classState.perfectUntil) 1.4f else .8f)
        }
        val absorbed = min(adjusted, classState.shield)
        classState.shield -= absorbed; adjusted -= absorbed
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
    /** Explicit laboratory control. Does not cancel the skill currently being observed. */
    internal fun refillTraining() {
        manaValue = maxMana.toDouble(); health = maxHealth.toDouble(); readyAt.fill(0L); nextDodge = 0L
        classState.gain(classState.cap(classId), classId, journey().build)
        syncVanillaHealth()
    }
    fun revive(fraction: Double) { require(fraction in .1..1.0); reset(); health = maxHealth * fraction; syncVanillaHealth() }
    fun resetActions() {
        actionEpoch++
        normal.reset(); pending = null; shot = null; lastConduitGain = -1; castCharges = 0; skillBoost = 1.0; queuedSkill = null; queuedDodge = false; burns.clear()
        normalEmpowerment = 1.0
        classState.reset(); moveHasteUntil = -1; previousPosition = null
        vfx.cancel()
        warriorMarks.clear()
        damageLabels.forEach { it.first.remove() }; damageLabels.clear()
    }
    private fun syncVanillaHealth() {
        player.getAttribute(Attribute.MAX_HEALTH).baseValue = 20.0
        player.health = (20.0 * health / maxHealth).toFloat().coerceIn(0.1f, 20f)
    }

    private fun moveSafely(direction: Vec, distance: Double) {
        val start = player.position
        var destination = start
        val grounded=player.instance.getBlock(start.sub(0.0,.15,0.0)).isSolid
        for (step in 1..ceil(distance / 0.15).toInt()) {
            val next = start.add(direction.mul(min(distance, step * 0.15)))
            if(grounded && !player.instance.getBlock(next.sub(0.0,.15,0.0)).isSolid) break
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
