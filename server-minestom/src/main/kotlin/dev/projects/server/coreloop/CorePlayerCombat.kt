package dev.projects.server.coreloop

import dev.projects.server.mob.rayHit
import dev.projects.server.puppet.PlayerPuppet
import dev.projects.server.mob.distanceTo

import dev.projects.server.mob.CombatTarget
import dev.projects.server.mob.CrowdControl
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
    private val journey: () -> CoreJourney = { CoreJourney.fresh() },
    private val weaponBase: () -> CoreWeaponBase = { CoreWeaponBase.STANDARD },
    private val weaponLevelPower: () -> Double = { 1.0 },
    private val armorLevelPower: () -> Double = { 1.0 },
    private val onLesson: (Int) -> Unit = {},
    private val allies: () -> List<CorePlayerCombat> = { emptyList() },
    private val gearSource: () -> CoreCombatGear? = { null },
    /** True once the UI pack is loaded: damage numbers then use the pixel damage font. */
    private val packed: () -> Boolean = { false },
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
    private var dodgeCooldown = 24L
    /** 回避の無敵: hits (except TRUE damage) are evaded until this tick. */
    private var evadeUntil = -1L
    /** An F pressed while committed (swing before impact, heavy startup) fires as soon as it is allowed. */
    private var dodgeQueuedUntil = -1L
    /** Dodges and knockbacks travel over a few ticks instead of teleporting. */
    private class Slide(val direction: Vec, val step: Double, var remaining: Int)
    private var slide: Slide? = null
    /** 怯み / 吹き飛び: no input and no walking until this tick. */
    private var staggerUntil = -1L
    val staggered get() = tickNumber < staggerUntil
    /** In the dodge's invulnerable window: the boss counts a hit on this player as a whiff. */
    val evading get() = tickNumber < evadeUntil
    private var baseSpeed = 0.1
    private var appliedSpeed = -1.0
    private var lastCombat = -400L
    private val readyAt = LongArray(5)
    internal val classState = CoreClassState()
    private var previousPosition: Pos? = null
    private var moveHasteUntil = -1L
    private var pending: PendingSkill? = null
    private data class PlantedGarden(val field: CoreIceGarden, val action: PendingSkill,
        val encounter: QuestEncounterCombat, val instance: net.minestom.server.instance.Instance)
    private var garden: PlantedGarden? = null
    internal val activeGardens get() = if(garden == null) 0 else 1
    /** Latest buffered intent (skill slot or [CoreCombatFeel.NORMAL]); same rules for every class. */
    private var queued: Int? = null
    private var queueUntil = -1L
    private var nextNormalAt = -1L
    private val feel get() = CoreCombatFeel.profile(classId)
    /** Warrior: a connected third normal hit opens a short window for a fast, heavy slam. */
    private var finisherUntil = -1L
    /** Warrior: beast built in [beastTick]; one action never builds more than [CoreWarriorRules.BEAST_PER_ACTION]. */
    private var beastTick = -1L
    private var beastThisTick = 0.0
    private var lastNormalBeast = -1L
    /** Warrior ultimates: 狂戦士の甲冑 and 獣の目覚め last until these ticks. */
    private var armorUntil = -1L
    private var awakenUntil = -1L
    val armored get() = classId == CoreClass.WARRIOR && tickNumber < armorUntil
    val awakened get() = classId == CoreClass.WARRIOR && tickNumber < awakenUntil
    /** Warrior A + B: the building skill that last landed, and until when a spender turns into their variant. */
    private var comboFrom: String? = null
    private var comboUntil = -1L
    private var whetstoneUntil = 0L
    private var shot: Pair<Long, Vec>? = null
    private var skillBoost = 1.0
    private var lastConduitGain = -1L
    private val lessonAttempts = mutableMapOf<Int, Long>()
    /** Crowd control this player is under (enemy attacks now, other players in PvP fields). */
    internal val crowd = CorePlayerCrowdControl()
    private var movementHeld = false
    val classId get() = journey().job
    val skillDefinitions get() = CoreSkillCatalog.equipped(journey(), statSource())
    val skillNames get() = skillDefinitions.map { it.name }
    fun skillAvailable(index: Int) = index in 0..4 && (journey().legacy || journey().level >= CoreSkillCatalog.unlockLevels[index])
    fun resourceAvailable(index: Int) = classState.canCast(skillDefinitions[index], journey().build)
    val weaponHint get() = "${classId.resourceName} ${classState.resource.toInt()}/${classState.cap(classId).toInt()}"
    val combatCue get() = crowd.cue(tickNumber) ?: CoreWarriorSkillPresentation.combatCue(classId,tickNumber,classState.counterUntil,classState.guardUntil)
    val resource get() = classState.resource
    val resourceMax get() = classState.cap(classId)
    val shield get() = classState.shield
    var defeated = false
        private set
    private var manaValue = 100.0
    val mana: Int get() = manaValue.toInt()
    var health = 100.0
        private set
    val sheet: CoreCombatSheet get() = CoreCombatSheet.from(gearSource() ?: CoreCombatGear(weaponTier(), armorTier(), weaponBase(),
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
    /** [shrunk]: a newer hit on the same target has pushed this one down; its own settle steps then stop. */
    private class DamageLabel(val entity: Entity, val expiry: Long, val target: java.util.UUID, val size: Double) { var shrunk = false }
    private val damageLabels = mutableListOf<DamageLabel>()

    private data class PendingSkill(val id: Int, val definition: CoreSkillDefinition, val origin: Pos, val direction: Vec, val startup: Int,
        var elapsed: Int = 0, var gained: Boolean = false, val counter: Boolean = false,
        /** Warrior: how many beast stacks the spender took at the cast. */
        val empowered: Double = 0.0,
        val boost: Double = 1.0, val gardenCells: List<Pos>? = null,
        /** Break gauge damage each hit on a boss does (CoreBreak), fixed at the cast. */
        val breakAmount: Double = 0.0,
        /** 跳び叩き / 天落とし: the tick the body was launched; the action lasts until it lands. */
        var leapTick: Long = -1L,
        /** 天落とし: where it landed and when; the rings go out from there (CoreWarriorRules.SKYFALL_RINGS). */
        var landAt: Pos? = null, var landTick: Long = -1L,
        /** 体当たり: when the charge began (-1 when not charging) and whether it caught a blow; 跳び叩き: how far it leapt. */
        var chargeTick: Long = -1L, var caught: Boolean = false, var leapReach: Double = 0.0,
        /** Warrior A + B: the variant this spender turned into, if any. */
        val combo: CoreWarriorRules.Combo? = null,
        /** 地割り with a full beast: 全力の一振り (CoreWarriorRules.fullSwing). */
        val full: Boolean = false,
        /** The current pulse's damage and break, against the cast's (狂乱's heavy first and last swings). */
        var hitScale: Double = 1.0, var breakScale: Double = 1.0)

    fun attack() {
        if (weaponBroken()) { notice("武器が破損しています。装備庫で修理してください"); return }
        if (defeated || player.openInventory != null || staggered) return
        val enemies = encounter() ?: return
        if (!crowd.canAttack(tickNumber)) return
        if (pending != null || shot != null || tickNumber < nextNormalAt) { queueAction(CoreCombatFeel.NORMAL); return }
        queued = null
        if (feel.attackBreaksGuard) { classState.guardUntil = -1; classState.perfectUntil = -1 }
        if (!classId.melee) {
            shot = tickNumber + 4 to player.position.direction()
            nextNormalAt = tickNumber + ceil(17.0 / attackSpeed).toLong().coerceAtLeast(8)
            player.swingMainHand(); lastCombat = tickNumber
            sound(if (classId == CoreClass.RANGER) SoundEvent.ENTITY_ARROW_SHOOT else SoundEvent.BLOCK_AMETHYST_BLOCK_CHIME, .65f, 1.2f)
            return
        }
        val weapon = CoreWeaponCombat.of(weaponBase())
        // The greatsword swings in the Better Combat cadence: the blade lands a quarter of the way into the swing.
        val greatsword = classId == CoreClass.WARRIOR && weaponBase().family == "greatsword"
        normal.press(attackSpeed * if (armored) CoreWarriorRules.ARMOR_HASTE else 1.0, quick = classId == CoreClass.ASSASSIN,
            immediate = feel.immediateNormal && !greatsword, attacks = weapon?.attacks ?: GreatswordCombo.DEFAULT,
            upswing = if (greatsword) GREATSWORD_UPSWING else null)?.let { swing ->
            nextNormalAt = tickNumber + swing.totalTicks
            normalEmpowerment = if(classId == CoreClass.TEMPLAR && classState.counterUntil >= tickNumber) 1.35 else 1.0
            if(normalEmpowerment > 1) classState.counterUntil = -1
            normalDirection = flatFacing()
            lastCombat = tickNumber
            if (greatsword) CoreArmamentPresentation.shaderSwing(player, swing.beat, swing.totalTicks)
            if (swing.impactTick == 0) releaseNormal(enemies, swing)
            else vfx.startSound(swing.beat)
        }
    }

    /** Dev only (/vfx): cast a skill with cooldowns, mana and the class resource refilled, to film its visuals. */
    fun devCast(id: Int) {
        readyAt.fill(0L); manaValue = maxMana.toDouble(); classState.fill(classId)
        skill(id, unlocked = true)
    }

    fun skill(id: Int, unlocked: Boolean = false) {
        if (weaponBroken()) { notice("武器が破損しています。装備庫で修理してください"); return }
        if (id !in 0..4 || defeated || encounter() == null || player.openInventory != null || staggered && !breaksOut(id)) return
        if (!unlocked && !skillAvailable(id)) { notice("${skillNames[id]}はLv${CoreSkillCatalog.unlockLevels[id]}で解放されます"); return }
        if (tickNumber < readyAt[id]) { notice("${skillNames[id]}：あと${cooldownSeconds(id)}秒"); return }
        if (!crowd.canCast(tickNumber)) { notice("${crowd.cue(tickNumber) ?: "行動不能"}：技を使えません"); return }
        val definition = skillDefinitions[id]
        if (classId == CoreClass.WARRIOR && definition.icon == CoreWarriorRules.AWAKEN && classState.resource < CoreWarriorRules.BEAST_MAX) {
            notice("獣が3つ必要です"); return
        }
        if (!classState.canCast(definition, journey().build)) { notice("${classId.resourceName}が足りません（必要 ${definition.spend} / 現在 ${resource.toInt()}）"); return }
        val cost = definition.mana
        if (mana < cost) { notice("マナが足りません（必要 $cost）"); return }
        val gardenCells = if(definition.icon == "mage_garden") {
            if(garden != null) { notice("氷の庭は一つまで。今の庭が解けるまで待ってください"); return }
            val cellSize = CoreIceGarden.cellSize(definition.radius)
            val ground = gardenSurface(aimedGround(definition, encounter()!!),cellSize)
            if(ground == null || !clearLine(player.position, ground)) { notice("氷の庭を置ける床を狙ってください"); return }
            val cells = CoreIceGarden.offsets.mapNotNull { (x,z) ->
                val cell = gardenSurface(ground.add(x*cellSize,0.0,z*cellSize),cellSize)
                cell?.takeIf { abs(it.y()-ground.y()) <= .55 && clearLine(ground,it) }
            }
            if(cells.isEmpty()) { notice("氷の庭を置ける床を狙ってください"); return }
            cells
        } else null
        val guard = CoreCombatFeel.cancelsIntoGuard(definition)
        val action = pending
        val canLeaveSkill = action == null || guard && action.elapsed >= CoreCombatFeel.lastImpact(action.definition, action.startup)
        if (!canLeaveSkill || !(guard || normalLinked()) || shot != null) { queueAction(id); return }
        queued = null
        if (normal.isAttacking) normal.endRecovery()
        PlayerPuppet.stop(player)
        if (action != null) { pending = null; vfx.cancel(preserveGarden = garden != null) }
        if (feel.attackBreaksGuard && definition.motion !in setOf(CoreSkillMotion.GUARD, CoreSkillMotion.SHIELD)) {
            classState.guardUntil = -1; classState.perfectUntil = -1
        }
        manaValue -= cost
        classState.spend(definition, classId, journey().build)
        if (classId == CoreClass.WARRIOR && definition.icon == CoreWarriorRules.SHATTER) {
            // 大地崩し breaks out of a stagger, and nothing touches the warrior while the sword goes in.
            if (staggered) { staggerUntil = -1; slide = null }
            evadeUntil = maxOf(evadeUntil, tickNumber + CoreWarriorRules.SHATTER_EVADE_TICKS)
        }
        skillBoost = 1.0
        if (weaponBase() == CoreWeaponBase.CONDUIT && definition.spend > 0) skillBoost *= 1.12
        // Only a templar guard read sets counterUntil (CoreClassState.guarded); the warrior has no parry counter (D06).
        val counter = classState.counterUntil >= tickNumber && definition.formula.ad > 0 &&
            definition.motion !in setOf(CoreSkillMotion.SHIELD,CoreSkillMotion.HEAL,CoreSkillMotion.GUARD)
        if (counter) { skillBoost *= 1.35; classState.counterUntil = -1 }
        var castDefinition = definition
        var breakAmount = CoreBreak.skill(classId, definition.icon)
        var beastSpent = 0
        var combo: CoreWarriorRules.Combo? = null
        var full = false
        if (classId == CoreClass.WARRIOR && definition.icon in CoreWarriorRules.spenders) {
            // The beast never gates a spender; whatever whole stacks are there, it takes them all for a heavier hit.
            // 獣の目覚め / 狂戦士の甲冑: the beast stays full, every spender takes all three and keeps them.
            beastSpent = if (awakened || armored) CoreWarriorRules.BEAST_MAX.toInt() else classState.takeStacks()
            skillBoost *= 1 + beastSpent * CoreWarriorRules.BEAST_DAMAGE_PERCENT_PER_STACK / 100.0
            breakAmount += beastSpent * CoreWarriorRules.BEAST_BREAK_PER_STACK_SPENT
            castDefinition = CoreWarriorRules.shape(castDefinition, beastSpent)
            // 全力の一振り breaks like the old ultimate did.
            full = CoreWarriorRules.fullSwing(definition.icon, beastSpent)
            if (full) breakAmount += CoreBreak.skill(classId, CoreWarriorRules.FULL_SWING) - CoreBreak.skill(classId, definition.icon)
            if (definition.icon == "slam" && tickNumber <= finisherUntil) {
                skillBoost *= 1 + CoreWarriorRules.FINISHER_BONUS_PERCENT / 100.0; finisherUntil = -1
                breakAmount *= CoreBreak.FINISHER
                // 全力の一振り keeps its whole wind-up: the shouldered charge is the blow.
                if (!full) castDefinition = castDefinition.copy(startup = minOf(CoreWarriorRules.FINISHER_STARTUP, castDefinition.startup))
            }
            if (tickNumber <= comboUntil) combo = CoreWarriorRules.combo(comboFrom, definition.icon)
            comboFrom = null; comboUntil = -1
            if (combo != null) { notice(combo.label); sound(SoundEvent.ENTITY_WARDEN_ROAR, .4f, 1.2f) }
        }
        readyAt[id] = tickNumber + if (awakened && definition.icon in CoreWarriorRules.spenders && !definition.ultimate) cooldownTicks(id) / 2 else cooldownTicks(id)
        if (classId == CoreClass.WARRIOR && definition.icon == CoreWarriorRules.ARMOR) {
            armorUntil = tickNumber + definition.duration; classState.fill(classId); crowd.clear(); staggerUntil = -1
            notice("狂戦士の甲冑"); sound(SoundEvent.ENTITY_WITHER_SPAWN, .5f, .6f)
        }
        if (classId == CoreClass.WARRIOR && definition.icon == CoreWarriorRules.AWAKEN) {
            awakenUntil = tickNumber + definition.duration; notice("獣の目覚め"); sound(SoundEvent.ENTITY_WARDEN_ROAR, .6f, .8f)
        }
        val startup = castDefinition.startupTicks(sheet)
        pending = PendingSkill(id, castDefinition, if(definition.motion == CoreSkillMotion.FIELD) aimedGround(definition, encounter()!!) else player.position,
            if (definition.motion == CoreSkillMotion.RAY) player.position.direction() else flatFacing(), startup,
            empowered = beastSpent.toDouble(), counter = counter, boost = skillBoost,
            gardenCells = gardenCells, breakAmount = breakAmount, combo = combo, full = full)
        lastCombat = tickNumber
        player.setHeldItemSlot(0)
        player.swingMainHand()
        if (startup > 1) vfx.playSkill(CoreSkillEffect(classId, castDefinition, pending!!.origin, pending!!.direction,
            CoreSkillVisualPhase.PREPARE, prepareTicks = startup - 1, gardenCells = gardenCells))
        if (classId == CoreClass.WARRIOR && definition.icon == "slam") slamWindup(pending!!)
        if (classId == CoreClass.WARRIOR && definition.icon == CoreWarriorRules.PUSH && startup <= CoreArmamentPresentation.PUSH_LEAD_TICKS) pushHand()
        if (classId == CoreClass.WARRIOR && definition.icon == CoreWarriorRules.TIP) tipWindup(pending!!)
    }

    /** 押し払い in the hand: a one-tick cock to the right, then the flat low across, through the middle at the impact. */
    private fun pushHand() {
        CoreArmamentPresentation.shaderSwing(player, CoreArmamentPresentation.PUSH_BEAT, CoreArmamentPresentation.PUSH_TICKS)
        pushSwingSound()
    }
    /**
     * 切っ先薙ぎ's cast: the sword drawn back to the right and held (the wind-up beat), until the cut starts
     * [CoreArmamentPresentation.TIP_LEAD_TICKS] before the impact; a startup too short for that goes straight into the cut.
     */
    private fun tipWindup(action: PendingSkill) {
        if (action.startup <= CoreArmamentPresentation.TIP_LEAD_TICKS) { tipCutHand(); return }
        CoreArmamentPresentation.shaderSwing(player, CoreArmamentPresentation.TIP_WIND_BEAT, CoreArmamentPresentation.TIP_WIND_TICKS)
        tipWindupSound()
    }
    private fun tipCutHand() {
        CoreArmamentPresentation.shaderSwing(player, CoreArmamentPresentation.TIP_CUT_BEAT, CoreArmamentPresentation.TIP_CUT_TICKS)
        tipCutSound()
    }

    /**
     * 地割り's cast. In the hand (after the PREPARE's own pose): the sword hauled up over the right shoulder and held
     * there until the slam beat takes it down ([slamHand]); a startup too short for that goes straight into the slam.
     * 全力の一振り also takes a breath, and the ground under the feet starts to give (the world gets no swing).
     */
    private fun slamWindup(action: PendingSkill) {
        val lead = CoreArmamentPresentation.SLAM_LEAD_TICKS
        if (action.startup <= lead) slamHand()
        else CoreArmamentPresentation.shaderSwing(player, CoreArmamentPresentation.RISE_BEAT, CoreArmamentPresentation.slamRiseTicks(action.startup - lead))
        if (!action.full) return
        look(action, CoreWarriorRefChoreography.FULL_WINDUP_PULSE)
        fullSwingBreathSound()
    }
    /** The slam beat: down through the middle a tick in, on the ground half a tick later (CoreArmamentPresentation.SLAM_LEAD_TICKS). */
    private fun slamHand() = CoreArmamentPresentation.shaderSwing(player, CoreArmamentPresentation.SLAM_BEAT, CoreArmamentPresentation.LEAP_SLAM_TICKS)

    /** 大地崩し may be cast out of a stagger, as long as there is a whole stack to throw (or the beast is full anyway). */
    private fun breaksOut(id: Int) = classId == CoreClass.WARRIOR && skillDefinitions[id].icon == CoreWarriorRules.SHATTER &&
        (awakened || armored || classState.resource >= 1.0)

    fun dodge() {
        if (defeated || encounter() == null || tickNumber < nextDodge || staggered) return
        // MH×MMO commitment: F cuts follow-through (after impact) and light skills, never a swing before its impact
        // or the startup of a heavy skill / ultimate. Pressed then, it fires the moment the commitment ends.
        if (committed()) { dodgeQueuedUntil = tickNumber + DODGE_BUFFER_TICKS; return }
        dodgeQueuedUntil = -1
        // Rule 3 of player CC: the dodge breaks every movement lock (it still costs the dodge cooldown).
        if (crowd.escape(tickNumber)) { refreshMovement(); sound(SoundEvent.ITEM_ARMOR_EQUIP_CHAIN, .7f, 1.4f) }
        queued = null
        if (normal.isAttacking) normal.endRecovery()
        PlayerPuppet.stop(player)
        classState.guardUntil = -1; classState.perfectUntil = -1
        if (pending != null) { pending = null; vfx.cancel(preserveGarden = garden != null) }
        val input = player.inputs()
        val forward = (if (input.forward()) 1.0 else 0.0) - (if (input.backward()) 1.0 else 0.0)
        val side = (if (input.right()) 1.0 else 0.0) - (if (input.left()) 1.0 else 0.0)
        val facing = flatFacing()
        val right = Vec(-facing.z(), 0.0, facing.x())
        val direction = if (forward == 0.0 && side == 0.0) facing else facing.mul(forward).add(right.mul(side)).normalize()
        startSlide(direction, DODGE_DISTANCE, DODGE_SLIDE_TICKS)
        evadeUntil = tickNumber + DODGE_EVADE_TICKS
        if (weaponBase() == CoreWeaponBase.FLOW) normal.holdSequence()
        dodgeCooldown = if (journey().build.has(4)) 20L else 24L
        nextDodge = tickNumber + dodgeCooldown
        classState.dodgeUntil = tickNumber + 60
        sound(SoundEvent.ENTITY_PLAYER_ATTACK_SWEEP, 0.35f, 1.7f)
    }

    fun tick() {
        tickNumber++
        if (armorUntil >= 0 && !armored) armorUntil = -1
        if (armored) {
            // 狂戦士の甲冑: the beast held full; the body burns for it, never below 1.
            classState.fill(classId)
            if (tickNumber % 10 == 0L && health > 1.0) {
                health = (health - maxHealth * CoreWarriorRules.ARMOR_BURN_PERCENT_PER_SECOND / 200.0).coerceAtLeast(1.0); syncVanillaHealth()
            }
            // The look: blood runs off the body and red scale-dust rises (every 4 ticks), and on each heartbeat
            // (once a second, with the burn) blood sprays out of the body.
            skillDefinitions.firstOrNull { it.icon == CoreWarriorRules.ARMOR }?.let { d ->
                if (tickNumber % 4 == 0L) vfx.playSkill(CoreSkillEffect(classId, d, player.position, flatFacing(),
                    pulse = CoreWarriorRules.ARMOR_BLEED_PULSE + (tickNumber / 4 % 97).toInt()))
                if ((armorUntil - tickNumber) % 20 == 0L) vfx.playSkill(CoreSkillEffect(classId, d, player.position, flatFacing(),
                    pulse = CoreWarriorRules.ARMOR_BEAT_PULSE + (tickNumber / 20 % 97).toInt()))
            }
        }
        if (awakened && tickNumber % 2 == 0L) {
            // 獣の目覚め: the beast is worn: smoke clings to the body and streams off it the whole time.
            skillDefinitions.firstOrNull { it.icon == CoreWarriorRules.AWAKEN }?.let { d ->
                vfx.playSkill(CoreSkillEffect(classId, d, player.position, flatFacing(), pulse = CoreWarriorRules.AWAKEN_CLING_PULSE + (tickNumber / 2 % 97).toInt()))
            }
        }
        geyser?.let { g ->
            vfx.look(CoreSkillEffect(g.job, g.skill, g.origin, g.direction, pulse = CoreWarriorRules.FULL_SWING_GEYSER_PULSE + geyserStep))
            if (++geyserStep >= CoreWarriorRules.FULL_SWING_GEYSER_STEPS) { geyser = null; geyserStep = 0 }
        }
        visualContactsThisTick = 0
        classState.tick(tickNumber)
        previousPosition?.let { if (it.distance(player.position) > .035) classState.stationarySince = tickNumber }
        previousPosition = player.position
        damageLabels.removeAll { if (tickNumber >= it.expiry) { it.entity.remove(); true } else false }
        if (defeated) return
        if(player.isRemoved) { resetActions(); return }
        if (tickNumber % 20 == 0L) {
            manaValue = (manaValue + CoreCombatMath.manaRegeneration(statSource(), tickNumber - lastCombat > 100)).coerceAtMost(maxMana.toDouble())
            baseSpeed = 0.1 * (1.0 + statSource().moveSpeedPercent.coerceIn(0.0, 25.0) / 100.0 + if (tickNumber < moveHasteUntil) .2 else 0.0)
        }
        applySpeed(speedFactor())
        crowdTick()
        val enemies = encounter() ?: run { resetActions(); return }
        if (weaponBroken()) { resetActions(); return }
        slide?.let { s ->
            val travelled = moveSafely(s.direction, s.step)
            if (--s.remaining <= 0 || travelled < s.step - 1e-6) slide = null
        }
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
            if(action.definition.icon == "mage_garden") {
                if(action.elapsed >= action.startup) {
                    val cellSize = CoreIceGarden.cellSize(action.definition.radius)
                    val cells = action.gardenCells.orEmpty().filter { cell -> gardenSurface(cell,cellSize) == cell }
                    if(cells.isNotEmpty()) {
                        val field = CoreIceGarden(cells,tickNumber,action.definition.duration,cellSize)
                        if (vfx.playSkill(CoreSkillEffect(CoreClass.MAGE,action.definition,action.origin,action.direction,gardenCells=cells))) {
                            garden = PlantedGarden(field,action,enemies,player.instance)
                            notice("氷の庭：6秒。敵を誘い、他の術を重ねる")
                        } else {
                            manaValue = min(maxMana.toDouble(), manaValue + action.definition.mana)
                            readyAt[action.id] = tickNumber
                            notice("氷の庭を展開できませんでした。マナと再使用を戻します")
                        }
                    }
                    pending = null
                }
                return@let
            }
            if (action.leapTick >= 0) {
                // In the air: the client flies the arc; the slam comes down when the body is back on the ground.
                val air = tickNumber - action.leapTick
                if (air >= CoreWarriorLeap.MIN_AIR_TICKS && grounded() || air >= CoreWarriorLeap.MAX_AIR_TICKS) {
                    action.leapTick = -1
                    if (action.definition.icon == CoreWarriorRules.SKYFALL) {
                        action.landAt = player.position; action.landTick = tickNumber
                        skyfallRing(enemies, action, 0)
                        if (!actionsValid(enemies, epoch)) return
                        if (action.definition.pulses <= 1) pending = null
                        return@let
                    }
                    emitSkillPulse(player.position, CoreWarriorLeap.LANDING_RADIUS)
                    look(action, if (action.leapReach >= CoreWarriorRules.LEAP_FAR) CoreWarriorRefChoreography.LEAP_FAR_LAND_PULSE
                        else CoreWarriorRefChoreography.LEAP_LAND_PULSE)
                    // In the hand (after the pulse's own pose): the raised sword comes straight down and jolts on the ground.
                    CoreArmamentPresentation.shaderSwing(player, CoreArmamentPresentation.SLAM_BEAT, CoreArmamentPresentation.LEAP_SLAM_TICKS)
                    leapLandingSound(action.leapReach >= CoreWarriorRules.LEAP_FAR)
                    hitThisStrike.clear()
                    strike(enemies, player.position, action.direction, CoreWarriorLeap.LANDING_RADIUS, -1.0, 1.0)
                    if (!actionsValid(enemies, epoch)) return
                    // Hit-stop: what the slam met stands frozen a moment (bosses refuse it), longer after a far leap.
                    val stop = CoreWarriorRules.leapHitStopTicks(action.leapReach)
                    for (id in hitThisStrike.toList()) enemies.hitStop(id, stop * 50L)
                    pending = null
                }
                return@let
            }
            if (action.chargeTick >= 0) {
                tackleStep(enemies, action)
                if (!actionsValid(enemies, epoch)) return
                return@let
            }
            if (action.landTick >= 0) {
                // 天落とし: each ring goes out a little later than the one inside it.
                val since = tickNumber - action.landTick
                val every = if (action.combo == CoreWarriorRules.Combo.DOUBLE_DROP) CoreWarriorRules.DOUBLE_DROP_RING_TICKS else CoreWarriorRules.SKYFALL_RING_TICKS
                val ring = (since / every).toInt()
                if (since % every == 0L && ring in 1 until action.definition.pulses) {
                    skyfallRing(enemies, action, ring)
                    if (!actionsValid(enemies, epoch)) return
                }
                if (ring >= action.definition.pulses - 1) pending = null
                return@let
            }
            if (classId == CoreClass.WARRIOR && action.definition.icon == "slam") {
                // 地割り: the blade comes down so it crosses the middle just before the impact (cause first).
                if (action.elapsed == action.startup - CoreArmamentPresentation.SLAM_LEAD_TICKS) slamHand()
                if (action.full && action.elapsed == CoreWarriorRules.FULL_SWING_RUMBLE_TICK && action.elapsed < action.startup) fullSwingRumbleSound()
            }
            // 押し払い / 切っ先薙ぎ: the blade is through the middle of the view at the impact (cause first).
            if (classId == CoreClass.WARRIOR && action.definition.icon == CoreWarriorRules.PUSH &&
                action.elapsed == action.startup - CoreArmamentPresentation.PUSH_LEAD_TICKS) pushHand()
            if (classId == CoreClass.WARRIOR && action.definition.icon == CoreWarriorRules.TIP &&
                action.elapsed == action.startup - CoreArmamentPresentation.TIP_LEAD_TICKS) tipCutHand()
            val pulse = action.elapsed - action.startup
            val every = CoreCombatFeel.pulseTicks(action.definition)
            if (pulse >= 0 && pulse % every == 0 && pulse / every < action.definition.pulses) {
                executeSkill(enemies, action)
                if (!actionsValid(enemies, epoch)) return
                if (action.leapTick >= 0) return@let
            }
            if (pulse >= 0 && pulse % 8 == 4 && pulse / 8 + 1 < action.definition.pulses && action.definition.motion == CoreSkillMotion.FIELD) {
                vfx.playSkill(CoreSkillEffect(classId, action.definition, action.origin, action.direction,
                    CoreSkillVisualPhase.PREPARE, pulse = pulse / 8 + 1, prepareTicks = 4))
            }
            if (action.elapsed >= CoreCombatFeel.finish(classId, action.definition, action.startup)) pending = null
        }
        tickGarden(enemies)
        if (!actionsValid(enemies, epoch)) return
        if (tickNumber <= dodgeQueuedUntil && !committed()) dodge()
        drainInput()
        if(classId==CoreClass.WARRIOR) {
            warriorMarks.update(enemies.combatTargets().filter { classState.marked(it.id,tickNumber) && visibleTo(it.id,enemies) },classState,tickNumber)
        } else warriorMarks.clear()
        vfx.tick()
    }

    /** A swing before its impact, or the startup of a heavy skill / ultimate, cannot be cut by F. */
    private fun committed(): Boolean {
        val swing = normal.swing
        if (swing != null && normal.elapsed < swing.impactTick) return true
        val action = pending ?: return false
        return action.elapsed < action.startup && (action.startup >= HEAVY_STARTUP || action.definition.ultimate)
    }

    /** Walking while acting: rooted for heavy startups and while staggered, slowed while casting or swinging. */
    private fun speedFactor(): Double {
        if (defeated || staggered || !crowd.canMove(tickNumber)) return 0.0
        val action = pending
        if (armored && (action == null || action.elapsed >= action.startup)) return CoreWarriorRules.ARMOR_HASTE
        if (action != null && action.chargeTick >= 0) return 0.0     // 体当たり carries the body itself
        if (action != null && action.elapsed < action.startup)
            return if (action.startup >= HEAVY_STARTUP || action.definition.ultimate) 0.0 else CAST_SPEED
        // Swinging: the weapon's own slowdown (Better Combat manner, CoreWeaponCombat), else the general one.
        return if (normal.isAttacking && classId.melee) CoreWeaponCombat.of(weaponBase())?.let { 1 - it.attackSlow } ?: SWING_SPEED else 1.0
    }

    private fun applySpeed(factor: Double) {
        val speed = baseSpeed * factor
        if (speed == appliedSpeed) return
        appliedSpeed = speed
        player.getAttribute(Attribute.MOVEMENT_SPEED).baseValue = speed
    }

    /** The first step lands now (an F press feels instant); the rest follows on the next ticks. */
    private fun startSlide(direction: Vec, distance: Double, ticks: Int) {
        val step = distance / ticks
        slide = null
        if (moveSafely(direction, step) < step - 1e-6 || ticks <= 1) return
        slide = Slide(direction, step, ticks - 1)
    }

    /** A heavy unguarded hit staggers (≥20% of max HP) or knocks down (≥45%): the action is lost, the body is thrown. */
    private fun react(share: Double) {
        if (share < STAGGER_SHARE || staggered || armored) return
        // A warrior spender does not stop for a blow (Destroyer super armor); the damage still lands.
        if (classId == CoreClass.WARRIOR && pending?.definition?.icon?.let { it in CoreWarriorRules.spenders } == true) return
        val down = share >= KNOCKDOWN_SHARE
        queued = null; dodgeQueuedUntil = -1; shot = null
        if (normal.isAttacking) normal.endRecovery()
        PlayerPuppet.stop(player)
        if (pending != null) { pending = null; vfx.cancel(preserveGarden = garden != null) }
        staggerUntil = tickNumber + if (down) KNOCKDOWN_TICKS else STAGGER_TICKS
        // Getting up is protected, as in MH: no juggling a downed hunter.
        if (down) evadeUntil = staggerUntil + WAKE_EVADE_TICKS
        val here = player.position
        val away = encounter()?.combatTargets()?.minByOrNull { it.position.distanceSquared(here) }
            ?.let { Vec(here.x() - it.position.x(), 0.0, here.z() - it.position.z()) }
            ?.takeIf { it.lengthSquared() > 1e-6 }?.normalize() ?: flatFacing().mul(-1.0)
        startSlide(away, if (down) KNOCKDOWN_DISTANCE else STAGGER_DISTANCE, 3)
        applySpeed(0.0)
        notice(if (down) "吹き飛ばされた" else "怯んだ")
        sound(if (down) SoundEvent.ENTITY_PLAYER_ATTACK_KNOCKBACK else SoundEvent.ENTITY_PLAYER_ATTACK_WEAK, .8f, if (down) .7f else 1f)
    }

    /** A skill pressed during a slow swing is kept until that swing can be linked, not only for the buffer. */
    private fun queueAction(action: Int) {
        queued = action
        val swing = normal.swing
        val untilLink = if (action >= 0 && swing != null) (swing.impactTick + feel.linkTicks - normal.elapsed).toLong() else 0L
        queueUntil = tickNumber + maxOf(feel.bufferTicks, untilLink)
    }

    /** A skill may cut a normal swing once [CoreCombatFeel.Profile.linkTicks] have passed since its impact. */
    private fun normalLinked(): Boolean {
        val swing = normal.swing ?: return true
        return normal.elapsed >= swing.impactTick + feel.linkTicks
    }

    private fun drainInput() {
        val input = queued ?: return
        if (tickNumber > queueUntil || player.openInventory != null) { queued = null; return }
        val action = pending
        val guarding = input >= 0 && CoreCombatFeel.cancelsIntoGuard(skillDefinitions[input])
        if (action != null && !(guarding && action.elapsed >= CoreCombatFeel.lastImpact(action.definition, action.startup))) return
        if (shot != null) return
        if (normal.isAttacking && (input == CoreCombatFeel.NORMAL || !guarding && !normalLinked())) return
        if (input == CoreCombatFeel.NORMAL && tickNumber < nextNormalAt) return
        queued = null
        if (input == CoreCombatFeel.NORMAL) attack() else skill(input)
    }

    /** Same server-owned strike for input-time warrior AA and delayed other melee jobs. */
    /** The weapon's normal-attack reach (Better Combat attack_range); 4.5 for melee without a table. */
    private fun reach() = CoreWeaponCombat.of(weaponBase())?.range ?: if (classId == CoreClass.ASSASSIN) 3.0 else 4.5

    private fun releaseNormal(enemies: QuestEncounterCombat, swing: GreatswordCombo.Swing): Boolean {
        val epoch = actionEpoch
        vfx.swingSound(swing.beat)
        // Better Combat manner: the off hand swings the off-hand attacks of a dual weapon.
        if (swing.attack.hand == CoreAttackHand.OFF) player.swingOffHand() else player.swingMainHand()
        if (classId == CoreClass.WARRIOR) {
            // A sweep, a rising cut back, an overhead slam.
            vfx.play(arrayOf(GreatswordVisual.SWEEP, GreatswordVisual.REVERSE, GreatswordVisual.FINISHER)[swing.beat - 1], player.position, normalDirection)
        }
        else vfx.play(CoreClassEffect(classId, CoreSkillMotion.CONE, player.position, normalDirection,
            reach(), swing.finisher, reverse = swing.beat == 2))
        var connected = false
        for (target in enemies.combatTargets()) {
            if (swing.attack.reaches(player.position, normalDirection, target, reach()) && visibleTo(target.id, enemies)) {
                hit(enemies, target.id, swing.multiplier * normalEmpowerment, skill = false, heavy = swing.finisher)
                connected = true
                if (!actionsValid(enemies, epoch)) return false
            }
        }
        if (connected && swing.finisher && weaponBase() == CoreWeaponBase.FLOW) manaValue = min(maxMana.toDouble(), manaValue + 8)
        if (connected && swing.finisher && classId == CoreClass.WARRIOR) {
            finisherUntil = tickNumber + CoreWarriorRules.FINISHER_TICKS
            if (journey().build.keystone == 1) skillDefinitions.forEachIndexed { i, s ->
                if (s.icon in CoreWarriorRules.spenders) readyAt[i] = maxOf(tickNumber, readyAt[i] - CoreWarriorRules.SAINT_REFUND_TICKS)
            }
        }
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
        if (classId == CoreClass.WARRIOR && s.icon == CoreWarriorLeap.ICON) { leap(action); return }
        if (classId == CoreClass.WARRIOR && s.icon == CoreWarriorRules.SKYFALL) { leap(action, high = true); return }
        if (classId == CoreClass.WARRIOR && s.icon == CoreWarriorRules.PUSH) { pushSweep(enemies, action); return }
        if (classId == CoreClass.WARRIOR && s.icon == CoreWarriorRules.TIP) { tipSweep(enemies, action); return }
        if (classId == CoreClass.WARRIOR && s.icon == "dash") {
            // 体当たり: the shoulder goes in now (its look plays here), the body follows over the next ticks.
            action.chargeTick = tickNumber
            emitSkillPulse(player.position, CoreWarriorRules.TACKLE_RADIUS)
            // In the hand (after the pulse's own pose): the sword hauled down to the lower right, the shoulder leads.
            CoreArmamentPresentation.shaderSwing(player, CoreArmamentPresentation.CHARGE_BEAT, CoreArmamentPresentation.TACKLE_CHARGE_TICKS)
            tackleChargeSound()
            tackleStep(enemies, action)
            return
        }
        if (classId == CoreClass.WARRIOR && s.icon == "slam") {
            emitSkillPulse(player.position, s.radius)
            // What the blade leaves in the world (the swing itself is the hand's): the crack forward, or after the first
            // pulse (重ね撃ち) the head splitting again; 全力の一振り's blood bursts up the rift over the next ticks.
            val again = action.elapsed - action.startup >= CoreCombatFeel.pulseTicks(s)
            look(action, when { again -> CoreWarriorRefChoreography.SLAM_SPLIT_PULSE
                action.full -> CoreWarriorRefChoreography.FULL_SLAM_PULSE; else -> CoreWarriorRefChoreography.SLAM_PULSE })
            if (action.full && !again) { geyser = CoreSkillEffect(classId, s, player.position, action.direction); geyserStep = 0 }
            if (action.full) fullSwingImpactSound()
            hitThisStrike.clear()
            strike(enemies, player.position, action.direction, s.radius, CoreCombatFeel.minDot(s.icon), 1.0)
            if (!actionsValid(enemies, epoch)) return
            // Hit-stop: what the blow met stands frozen a moment (bosses refuse it), longer under 全力の一振り.
            val stop = if (action.full) CoreWarriorRules.FULL_SWING_HITSTOP_TICKS else CoreWarriorRules.SLAM_HITSTOP_TICKS
            for (id in hitThisStrike.toList()) enemies.hitStop(id, stop * 50L)
            return
        }
        if (classId == CoreClass.WARRIOR && (s.icon == CoreWarriorRules.ARMOR || s.icon == CoreWarriorRules.AWAKEN)) {
            // The two ultimates that change the warrior (set at the cast); here only the beast breaking loose shows.
            emitSkillPulse(player.position, 2.0)
            return
        }
        if (classId == CoreClass.WARRIOR && s.icon == CoreWarriorRules.FRENZY) {
            // 狂乱: each swing steps in (a push the client moves, not a carried move) and reaches a little further;
            // the last is the overhead blow.
            // It keeps to the one in front (a narrow fan) and breaks harder; a full beast's last blow breaks far more.
            // 轟進＋狂乱: the first step goes further and the first swing lands heavy.
            val swing = (action.elapsed - action.startup) / CoreCombatFeel.pulseTicks(s)
            val last = swing == s.pulses - 1
            val rush = swing == 0 && action.combo == CoreWarriorRules.Combo.RUSH
            val push = flatFacing().mul(if (last) 7.0 else if (rush) CoreWarriorRules.RUSH_FIRST_PUSH else 5.0)
            player.velocity = Vec(push.x(), player.velocity.y(), push.z())
            action.hitScale = if (rush) CoreWarriorRules.RUSH_FIRST_SCALE else 1.0
            action.breakScale = CoreWarriorRules.FRENZY_BREAK_SCALE * action.hitScale *
                if (last && action.empowered >= CoreWarriorRules.BEAST_MAX) CoreWarriorRules.FRENZY_FULL_LAST_BREAK else 1.0
            val reach = s.radius + .3 * swing + if (last) .6 else 0.0
            emitSkillPulse(player.position, reach)
            strike(enemies, player.position, action.direction, reach,
                if (last) CoreWarriorRules.FRENZY_LAST_MIN_DOT else CoreWarriorRules.FRENZY_MIN_DOT, if (last || rush) 2.0 else 1.0)
            return
        }
        if (classId == CoreClass.WARRIOR && s.icon == CoreWarriorRules.SHATTER) {
            // 大地崩し: with no beast it is the quick way out, throwing the regulars round the warrior outward.
            hitThisStrike.clear()
            pulse(player.position)
            if (!actionsValid(enemies, epoch)) return
            if (action.empowered < 1.0) for (id in hitThisStrike.toList()) enemies.shove(id, player.position, CoreWarriorRules.SHATTER_SHOVE)
            return
        }
        when (s.motion) {
            CoreSkillMotion.RAY -> rangedStrike(enemies, action.direction, s.range, .65,
                if (s.icon == "hunt_pierce") 3 else 1, 1.0, true)
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
                strike(enemies, player.position, action.direction, s.radius, CoreCombatFeel.minDot(s.icon), 1.0)
            }
            CoreSkillMotion.CONE -> {
                emitSkillPulse(player.position, s.radius)
                strike(enemies, player.position, action.direction, s.radius, CoreCombatFeel.minDot(s.icon), 1.0)
            }
            CoreSkillMotion.SPIN -> pulse(player.position)
            CoreSkillMotion.NOVA -> pulse(player.position)
            CoreSkillMotion.FIELD -> pulse(action.origin)
            CoreSkillMotion.EVADE -> {
                val departure = player.position
                moveSafely(action.direction.mul(if (s.range < 0) -1.0 else 1.0), abs(s.range))
                classState.dodgeUntil = tickNumber + 60
                if (classId == CoreClass.HEALER) grantNearbyShields(8 + abilityPower * .3, 4.0, 60)
                if (s.formula.ad > 0 || s.formula.ap > 0) rangedStrike(enemies, action.direction, 18.0, .7, 1, 1.0, true)
                else if (s.status == CoreSkillStatus.SLOW) enemies.combatTargets().filter { it.distanceTo(action.origin) < 4 && visibleTo(it.id, enemies) }
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
                val targets = enemies.combatTargets().filter { it.distanceTo(player.position) <= s.radius && visibleTo(it.id, enemies) }
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
        return (1 until steps).all { !player.instance.getBlock(start.add(end.sub(start).mul(it.toDouble() / steps))).solid() }
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

    private fun hit(enemies: QuestEncounterCombat, id: UUID, multiplier: Double, skill: Boolean, heavy: Boolean = false, piercing: Boolean = false,
        skillAction: PendingSkill? = pending, contact: Boolean = true, fieldOrigin: Pos? = null, accepted: (() -> Unit)? = null) {
        val epoch = actionEpoch
        val stats = sheet.mods
        val build = journey().build
        val position = enemies.positionOf(id) ?: return
        val definition = if (skill) skillAction?.definition ?: return else null
        val formula = definition?.formula ?: CoreSkillCatalog.basicFormula
        val type = definition?.type ?: CoreSkillCatalog.basicType(classId)
        val tags = definition?.tags ?: CoreSkillCatalog.basicTags(classId)
        val element = (stats.fireFlat + stats.iceFlat + stats.lightningFlat) * (if (skill) 1.0 else multiplier) * 0.65
        val critical = criticalRoll() < stats.criticalChance
        // A skill of the weak element counts as much as an affix of it (element 1 fire / 2 ice / 3 lightning).
        val skillElement = definition?.element ?: 0
        val weak = when (enemies.weaknessOf(id)) {
            "fire" -> stats.fireFlat > 0 || skillElement == 1; "ice" -> stats.iceFlat > 0 || skillElement == 2
            "lightning" -> stats.lightningFlat > 0 || skillElement == 3; else -> false
        }
        val baseDamage = definition?.baseDamage(sheet) ?: (formula.evaluate(sheet) * multiplier + element)
        var classMultiplier = 1.0
        if (classId == CoreClass.RANGER) {
            if (classState.focus == id) classMultiplier *= 1 + classState.focusHits * .04
            if (build.keystone == 0 && tickNumber - classState.stationarySince >= 40) classMultiplier *= 1.25
        }
        if (classId == CoreClass.WARRIOR && build.keystone == 0 && resource >= 2) classMultiplier *= 1.2
        val beastSpender = skill && classId == CoreClass.WARRIOR && definition!!.icon in CoreWarriorRules.spenders
        if (skill) hitThisStrike += id
        // The beast let loose on a downed enemy: the window the warrior builds toward.
        if (beastSpender && enemies.isBroken(id)) classMultiplier *= 1 + skillAction!!.empowered * CoreWarriorRules.BEAST_DOWN_PERCENT_PER_STACK / 100.0
        if (classId == CoreClass.HEALER && build.keystone == 0) classMultiplier *= 1.25
        if (classId == CoreClass.TEMPLAR && build.keystone == 2) classMultiplier *= .9
        val consumeMark = skill && classId != CoreClass.WARRIOR && classState.marked(id, tickNumber) && definition!!.spend > 0
        if (consumeMark) {
            classMultiplier *= 1.35
            val info = enemies.mobInfo(id)
            if (classId == CoreClass.ASSASSIN && build.keystone == 0 && info != null && info.health / info.maximumHealth <= .35) classMultiplier *= 1.4
        }
        val damage = CoreCombatMath.outgoing(baseDamage, type, tags, stats, critical) *
            (if (skill) skillAction!!.boost * skillAction.hitScale else 1.0) * classMultiplier * (if (tickNumber < whetstoneUntil) 1.2 else 1.0) * (if (weak) 1.25 else 1.0)
        val applied = (if (fieldOrigin != null) enemies.applyCalculatedFieldDamage(id, player, fieldOrigin, damage, type, stats)
            else enemies.applyCalculatedDamage(id, player, damage, type, stats,
                projectile = !classId.melee || definition?.motion == CoreSkillMotion.RAY || definition?.motion == CoreSkillMotion.FIELD || (definition?.radius ?: 0.0) > 7.0)) ?: return
        accepted?.invoke()
        val weakSpot = enemies.takeWeakSpot(player)
        if (weakSpot) sound(SoundEvent.ENTITY_PLAYER_ATTACK_CRIT, .7f, 1.25f)
        val lesson = if (skill) 1 else 0
        if (!journey().knows(lesson) && tickNumber - (lessonAttempts[lesson] ?: -100L) >= 20) {
            lessonAttempts[lesson] = tickNumber; onLesson(lesson)
        }
        if (!actionsValid(enemies, epoch)) return
        if (!skill) {
            classState.normalHit(classId, id, tickNumber, build)
            // Once per swing, however many it cleaves.
            if (classId == CoreClass.WARRIOR && lastNormalBeast != tickNumber) {
                lastNormalBeast = tickNumber; feedBeast(CoreWarriorRules.BEAST_PER_NORMAL * if (heavy) 2 else 1)
            }
            if(weaponBase() == CoreWeaponBase.CONDUIT && lastConduitGain != tickNumber) {
                if (classId == CoreClass.WARRIOR) feedBeast(CoreWarriorRules.BEAST_PER_NORMAL) else classState.gain(4.0, classId, build)
                lastConduitGain = tickNumber
            }
        }
        else skillAction?.takeUnless { it.gained }?.let { action ->
            action.gained = true; classState.skillHit(classId, action.definition, build)
            // 押し払い / 切っ先薙ぎ count theirs after the whole strike (how many it met, whether the tip met one).
            if (classId == CoreClass.WARRIOR && action.definition.icon !in CoreWarriorRules.spenders &&
                action.definition.icon !in CoreWarriorRules.countedBuilders) feedBuilder(action)
        }
        if (consumeMark) { classState.consumeMark(id, tickNumber); if (classId == CoreClass.ASSASSIN && build.keystone == 1) nextDodge = 0 }
        if (classId == CoreClass.TEMPLAR) enemies.taunt(id, player)
        if (definition != null) when (definition.status) {
            CoreSkillStatus.MARK -> classState.mark(id, tickNumber)
            CoreSkillStatus.SLOW -> {
                // Garden slow is refreshed by actual occupancy, never a three-second tail on exit.
                if(definition.icon == "mage_garden") {
                    if(classId == CoreClass.MAGE && build.keystone == 2 && !enemies.isBoss(id))
                        receiveShield((8 + abilityPower * .3) * shieldScale, 80)
                } else {
                val slow = if (classId == CoreClass.MAGE && build.keystone == 2) .6 else .4
                enemies.applySlow(id, slow, if (classId == CoreClass.RANGER && build.keystone == 2) 4500 else 3000)
                if (classId == CoreClass.MAGE && build.keystone == 2) receiveShield((8 + abilityPower * .3) * shieldScale, 80)
                }
            }
            CoreSkillStatus.EXPOSE -> enemies.expose(id)
            CoreSkillStatus.POISON -> {
                burns[DotKey(id,true)] = Burn(CoreCombatMath.outgoing(formula.evaluate(sheet) * .3,type,tags,stats), type, stats, tickNumber + 20, 3)
                if (classId == CoreClass.RANGER) enemies.applySlow(id, .45, 3500)
            }
            CoreSkillStatus.NONE -> Unit
        }
        // Warrior weight: the third normal hit makes the target flinch.
        val crowd = definition?.crowd?.let { it to definition.crowdMillis }
            ?: if (classId == CoreClass.WARRIOR && !skill && heavy) CrowdControl.FLINCH to CoreWarriorRules.FLINCH_MILLIS else null
        if (enemies.isBoss(id)) {
            // Bosses take break, not crowd control: the warrior breaks the most, every class a little (CoreBreak).
            val amount = if (skill) skillAction?.let { it.breakAmount * it.breakScale } ?: 0.0 else CoreBreak.normal(classId, heavy)
            enemies.applyBreak(id, amount, player)
        } else if (crowd != null) {
            // 地割り: the stun is put on first so it outlasts the launch; the launch then throws the target up.
            if ((definition?.stunAfterMillis ?: 0) > 0) enemies.applyCrowdControl(id, CrowdControl.STUN, definition!!.stunAfterMillis, player)
            enemies.applyCrowdControl(id, crowd.first, crowd.second, player)
        }
        val venom = stats.bonus(CoreAffixStat.ASS_VENOM)
        if (classId == CoreClass.ASSASSIN && ((venom > 0 && definition?.status == CoreSkillStatus.MARK) ||
                (!skill && build.keystone == 2 && classState.marked(id, tickNumber)))) {
            burns[DotKey(id,true)] = Burn(CoreCombatMath.outgoing(attackDamage * (.1 + venom * .02),type,tags,stats), type, stats, tickNumber + 20, 3)
        }
        val stolen = CoreCombatMath.lifeSteal(applied, stats, piercing || CoreAttackTag.AREA in tags || classId.melee)
        if (stolen > 0) heal(stolen)
        if (definition != null) {
            if (contact && visualContactsThisTick++ < 3) vfx.playSkill(CoreSkillEffect(classId, definition, position,
                skillAction!!.direction, CoreSkillVisualPhase.CONTACT, pulse = (skillAction.elapsed - skillAction.startup) / 8))
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
        showDamage(id, position, applied, critical, type, if (weak) enemies.weaknessOf(id) else null, weakSpot)
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
            if (target.parts.isNotEmpty()) return@mapNotNull target.parts.rayHit(origin, direction, range, width)
                ?.takeIf { visibleTo(target.id, enemies) }?.let { target.id to it }
            val position = enemies.positionOf(target.id)?.add(0.0, 1.0, 0.0) ?: return@mapNotNull null
            val offset = position.sub(origin)
            val along = offset.x() * direction.x() + offset.y() * direction.y() + offset.z() * direction.z()
            if (along !in 0.0..range || origin.add(direction.mul(along)).distance(position) > width + target.halfExtent.x() || !visibleTo(target.id, enemies)) null else target.id to along
        }.sortedBy { it.second }.take(count)
        val distance = candidates.lastOrNull()?.second ?: range
        var visibleDistance = 0.0
        for (step in 1..floor(distance * 4).toInt()) {
            val p = origin.add(direction.mul(step / 4.0))
            if (player.instance.getBlock(p).solid()) break
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

    /** 跳び叩き: launch toward the aimed point with vanilla physics (CoreWarriorLeap); never a teleport. */
    private fun leap(action: PendingSkill, high: Boolean = false) {
        val instance = player.instance ?: return
        val eye = player.position.add(0.0, player.eyeHeight, 0.0)
        // 天落とし: few stacks hop low and short, a full beast jumps high and far (CoreWarriorRules.SKYFALL_REACH).
        val n = action.empowered.toInt().coerceIn(0, 3)
        val target = CoreWarriorLeap.aim(eye, player.position.direction(), solid = { instance.getBlock(it).solid() },
            reach = if (high) CoreWarriorRules.SKYFALL_REACH[n] else CoreWarriorLeap.MAX_REACH)
        val velocity = CoreWarriorLeap.launch(player.position, target, if (high) CoreWarriorRules.SKYFALL_APEX[n] else null)
        player.velocity = velocity.mul(20.0)  // Minestom velocity is blocks per second
        action.leapTick = tickNumber
        action.leapReach = kotlin.math.hypot(target.x() - player.position.x(), target.z() - player.position.z())
        if (high) { sound(SoundEvent.ENTITY_PLAYER_ATTACK_KNOCKBACK, .8f, .6f); return }
        // 跳び叩き in the hand: the sword hauled up over the right shoulder and held there for the expected flight.
        val air = CoreWarriorLeap.path(player.position, velocity, ground = target.y()).size
        CoreArmamentPresentation.shaderSwing(player, CoreArmamentPresentation.RISE_BEAT, CoreArmamentPresentation.leapRiseTicks(air))
        leapLaunchSound()
        look(action, CoreWarriorRefChoreography.LEAP_TAKEOFF_PULSE)
    }
    /** What 体当たり / 跳び叩き leave in the world at this moment (pieces only: the sound and the hand are their own). */
    private fun look(action: PendingSkill, pulse: Int) =
        vfx.look(CoreSkillEffect(classId, action.definition, player.position, action.direction, pulse = pulse))
    /** 跳び叩き's launch: a heavy push-off from the ground and a low whoosh of the body and blade going up. */
    private fun leapLaunchSound() {
        sound(SoundEvent.ENTITY_PLAYER_ATTACK_KNOCKBACK, .8f, varied(.6f))
        sound(SoundEvent.BLOCK_ROOTED_DIRT_BREAK, .7f, varied(.6f))
        sound(SoundEvent.ENTITY_RAVAGER_STEP, .5f, varied(.75f))
        sound(SoundEvent.ENTITY_PLAYER_ATTACK_SWEEP, .5f, varied(.55f))
        sound(SoundEvent.ITEM_TRIDENT_RIPTIDE_1, .35f, varied(.6f))
    }
    /** 跳び叩き's landing: a deep thud into the ground and earth and stone breaking; heavier and lower after a far leap. */
    private fun leapLandingSound(far: Boolean) {
        val low = if (far) .82f else 1f
        sound(SoundEvent.ENTITY_GENERIC_EXPLODE, if (far) .45f else .3f, varied(.55f * low))
        sound(SoundEvent.ENTITY_IRON_GOLEM_ATTACK, .9f, varied(.6f * low))
        sound(SoundEvent.BLOCK_ROOTED_DIRT_BREAK, 1f, varied(.6f * low))
        sound(SoundEvent.BLOCK_STONE_BREAK, .8f, varied(.55f * low))
        sound(SoundEvent.BLOCK_GRAVEL_BREAK, .7f, varied(.7f * low))
        if (far) sound(SoundEvent.BLOCK_ANVIL_LAND, .3f, varied(.5f))
    }
    /** 体当たり: one tick of the charge; it stops on the first enemy in front, a wall, or the end of the charge. */
    private fun tackleStep(enemies: QuestEncounterCombat, action: PendingSkill) {
        val t = tickNumber - action.chargeTick
        val ahead = enemies.combatTargets().any { greatswordInRange(player.position, action.direction, it, CoreWarriorRules.TACKLE_CONTACT, .5) }
        val moved = if (ahead || t >= CoreWarriorRules.TACKLE_TICKS) 0.0 else moveSafely(action.direction, CoreWarriorRules.TACKLE_STEP)
        if (moved >= CoreWarriorRules.TACKLE_STEP * .5) look(action, CoreWarriorRefChoreography.TACKLE_STEP_PULSE + t.toInt())
        if (ahead || t >= CoreWarriorRules.TACKLE_TICKS || moved < CoreWarriorRules.TACKLE_STEP * .5) tackleImpact(enemies, action)
    }
    /** 体当たり's impact: everything just in front, harder and stunned after a catch. */
    private fun tackleImpact(enemies: QuestEncounterCombat, action: PendingSkill) {
        action.chargeTick = -1
        val epoch = actionEpoch
        action.hitScale = if (action.caught) CoreWarriorRules.TACKLE_CATCH_SCALE else 1.0
        action.breakScale = action.hitScale
        hitThisStrike.clear()
        strike(enemies, player.position, action.direction, CoreWarriorRules.TACKLE_RADIUS, .3, if (action.caught) 2.0 else 1.0)
        if (!actionsValid(enemies, epoch)) return
        val struck = hitThisStrike.toList()
        look(action, when { struck.isEmpty() -> CoreWarriorRefChoreography.TACKLE_STOP_PULSE
            action.caught -> CoreWarriorRefChoreography.TACKLE_CAUGHT_PULSE; else -> CoreWarriorRefChoreography.TACKLE_HIT_PULSE })
        if (action.caught) for (id in struck) if (!enemies.isBoss(id))
            enemies.applyCrowdControl(id, CrowdControl.STUN, CoreWarriorRules.TACKLE_CATCH_STUN_MILLIS, player)
        // Hit-stop: what the shoulder met stands frozen a moment (bosses refuse it), and the hand jolts back.
        val stop = if (action.caught) CoreWarriorRules.TACKLE_CATCH_HITSTOP_TICKS else CoreWarriorRules.TACKLE_HITSTOP_TICKS
        for (id in struck) enemies.hitStop(id, stop * 50L)
        if (struck.isNotEmpty()) CoreArmamentPresentation.shaderSwing(player, CoreArmamentPresentation.IMPACT_BEAT, CoreArmamentPresentation.TACKLE_IMPACT_TICKS)
        tackleImpactSound(struck.isNotEmpty(), action.caught)
    }
    /** A little different every time (vanilla sounds only). */
    private fun varied(pitch: Float) = pitch * (.94f + kotlin.random.Random.nextFloat() * .12f)
    /** 体当たり's charge: the armour shifting and heavy steps. */
    private fun tackleChargeSound() {
        sound(SoundEvent.ITEM_ARMOR_EQUIP_IRON, .6f, varied(.75f))
        sound(SoundEvent.ITEM_ARMOR_EQUIP_CHAIN, .45f, varied(.85f))
        sound(SoundEvent.ENTITY_RAVAGER_STEP, .55f, varied(.9f))
        sound(SoundEvent.BLOCK_STONE_STEP, .8f, varied(.6f))
    }
    /** 体当たり's impact: a body thud and a metal knock, heavier and lower with an anvil's ring after a catch. */
    private fun tackleImpactSound(hit: Boolean, caught: Boolean) {
        if (!hit) { sound(SoundEvent.BLOCK_GRAVEL_STEP, .7f, varied(.7f)); return }   // ran out: only the feet
        sound(SoundEvent.ENTITY_PLAYER_ATTACK_KNOCKBACK, 1f, varied(if (caught) .55f else .7f))
        sound(SoundEvent.ENTITY_IRON_GOLEM_ATTACK, .9f, varied(if (caught) .55f else .75f))
        sound(SoundEvent.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, if (caught) .5f else .35f, varied(if (caught) .6f else .9f))
        if (caught) sound(SoundEvent.BLOCK_ANVIL_LAND, .45f, varied(.5f))
    }
    /** A building skill landed: the beast it builds (at most that much this tick), and the A + B window it opens. */
    private fun feedBuilder(action: PendingSkill, struck: Int = 1, tip: Boolean = false) {
        val gain = CoreWarriorRules.builderGain(action.definition.icon, action.caught, action.leapReach, struck, tip)
        feedBeast(gain, cap = maxOf(gain, CoreWarriorRules.BEAST_PER_ACTION))
        comboFrom = action.definition.icon; comboUntil = tickNumber + CoreWarriorRules.COMBO_TICKS
    }
    /**
     * 押し払い's impact: the flat goes across a wide fan in front; every regular enemy it meets is thrown outward
     * ([CoreWarriorRules.PUSH_SHOVE]) and flinches (the catalog's crowd control), a boss takes the hit and its break
     * where it stands. The beast is counted by how many it met.
     */
    private fun pushSweep(enemies: QuestEncounterCombat, action: PendingSkill) {
        val epoch = actionEpoch
        look(action, CoreWarriorRefChoreography.PUSH_SWEEP_PULSE)
        hitThisStrike.clear()
        strike(enemies, player.position, action.direction, action.definition.radius, CoreWarriorRules.PUSH_MIN_DOT, 1.0)
        if (!actionsValid(enemies, epoch)) return
        val struck = hitThisStrike.toList()
        var skids = 0
        for (id in struck) {
            if (enemies.isBoss(id)) continue
            val from = enemies.positionOf(id) ?: continue
            if (!enemies.shove(id, player.position, CoreWarriorRules.PUSH_SHOVE)) continue
            val away = Vec(from.x() - player.position.x(), 0.0, from.z() - player.position.z())
            if (skids++ < 4 && away.lengthSquared() > 1e-6) vfx.look(CoreSkillEffect(classId, action.definition, from, away.normalize(),
                pulse = CoreWarriorRefChoreography.PUSH_SKID_PULSE))
        }
        if (struck.isNotEmpty()) feedBuilder(action, struck = struck.size)
        pushImpactSound(struck.isNotEmpty())
    }
    /**
     * 切っ先薙ぎ's impact: one wide cut. Each enemy met is a tip hit when the near surface of its body is in the tip band
     * ([CoreWarriorRules.tipBand]): 1.5 times the damage and break, a long hit-stop, the ring and the spark on it;
     * nearer, the middle of the blade meets it: an ordinary hit, a short hit-stop, a thud. The hand catches on a tip hit.
     */
    private fun tipSweep(enemies: QuestEncounterCombat, action: PendingSkill) {
        val epoch = actionEpoch
        hitThisStrike.clear()
        var tips = 0; var closes = 0
        for (target in enemies.combatTargets()) {
            if (!greatswordInRange(player.position, action.direction, target, action.definition.radius, CoreWarriorRules.TIP_MIN_DOT) ||
                !visibleTo(target.id, enemies)) continue
            val distance = tipDistance(target)
            val tip = CoreWarriorRules.tipBand(distance)
            action.hitScale = if (tip) CoreWarriorRules.TIP_SCALE else 1.0
            action.breakScale = action.hitScale
            val before = hitThisStrike.size
            hit(enemies, target.id, 1.0, skill = true, contact = false)
            if (!actionsValid(enemies, epoch)) return
            if (hitThisStrike.size == before) continue
            enemies.hitStop(target.id, CoreWarriorRules.tipHitStopTicks(tip) * 50L)
            val feet = enemies.positionOf(target.id) ?: target.position.let { Pos(it.x(), it.y(), it.z()) }
            if (tip) {
                // Where the tip went in: just off the near side of the body (so the body does not hide it), at the
                // height of the cut.
                val to = Vec(feet.x() - player.position.x(), 0.0, feet.z() - player.position.z())
                val at = if (to.lengthSquared() > 1e-6) player.position.add(to.normalize().mul((distance - .3).coerceAtLeast(.5))) else feet
                if (tips++ < 3) vfx.look(CoreSkillEffect(classId, action.definition, Pos(at.x(), player.position.y() + 1.15, at.z()),
                    action.direction, pulse = CoreWarriorRefChoreography.TIP_SPARK_PULSE))
            } else if (closes++ < 3) vfx.look(CoreSkillEffect(classId, action.definition, feet, action.direction,
                pulse = CoreWarriorRefChoreography.TIP_CLOSE_PULSE))
        }
        action.hitScale = 1.0; action.breakScale = 1.0
        // The bite takes over from the cut where it stands (the cut started TIP_LEAD_TICKS before this).
        if (tips > 0 && action.startup >= CoreArmamentPresentation.TIP_LEAD_TICKS)
            CoreArmamentPresentation.shaderSwing(player, CoreArmamentPresentation.TIP_BITE_BEAT, CoreArmamentPresentation.TIP_BITE_TICKS)
        if (tips + closes > 0) feedBuilder(action, struck = tips + closes, tip = tips > 0)
        tipImpactSound(tips > 0, closes > 0)
    }
    /** How far from the warrior the blade meets [target]: the near surface of its body (its nearest part for a boss). */
    private fun tipDistance(target: CombatTarget): Double {
        val o = player.position
        if (target.parts.isNotEmpty()) return target.parts.minOf { kotlin.math.hypot(it.center.x() - o.x(), it.center.z() - o.z()) - it.radius }
            .coerceAtLeast(0.0)
        return (kotlin.math.hypot(target.position.x() - o.x(), target.position.z() - o.z()) - target.halfExtent.x()).coerceAtLeast(0.0)
    }
    /** 押し払い's swing: the heavy blade hauled round flat, air shoved. */
    private fun pushSwingSound() {
        sound(SoundEvent.ENTITY_PLAYER_ATTACK_SWEEP, .8f, varied(.55f))
        sound(SoundEvent.ITEM_ARMOR_EQUIP_IRON, .5f, varied(.7f))
        sound(SoundEvent.ITEM_TRIDENT_THROW, .4f, varied(.5f))
    }
    /** 押し払い's impact: the flat slapping into bodies, the shove, grit scraped along the ground; only the grit on a miss. */
    private fun pushImpactSound(hit: Boolean) {
        sound(SoundEvent.BLOCK_GRAVEL_BREAK, .55f, varied(.75f))
        if (!hit) return
        sound(SoundEvent.ENTITY_PLAYER_ATTACK_KNOCKBACK, 1f, varied(.62f))
        sound(SoundEvent.ITEM_SHIELD_BLOCK, .6f, varied(.6f))
        sound(SoundEvent.ENTITY_IRON_GOLEM_ATTACK, .55f, varied(.85f))
        sound(SoundEvent.BLOCK_ROOTED_DIRT_BREAK, .6f, varied(.7f))
    }
    /** 切っ先薙ぎ's wind-up: the weight going back, the blade drawn. */
    private fun tipWindupSound() {
        sound(SoundEvent.ITEM_ARMOR_EQUIP_IRON, .55f, varied(.75f))
        sound(SoundEvent.ITEM_TRIDENT_RETURN, .45f, varied(.55f))
        sound(SoundEvent.BLOCK_GRAVEL_STEP, .6f, varied(.7f))
    }
    /** 切っ先薙ぎ's cut: a long low whoosh. */
    private fun tipCutSound() {
        sound(SoundEvent.ENTITY_PLAYER_ATTACK_SWEEP, 1f, varied(.62f))
        sound(SoundEvent.ITEM_TRIDENT_THROW, .7f, varied(.6f))
        sound(SoundEvent.ITEM_TRIDENT_RIPTIDE_1, .3f, varied(.75f))
    }
    /** 切っ先薙ぎ's impact: the tip rings sharp and bright off what it met; the middle of the blade only thuds dully. */
    private fun tipImpactSound(tip: Boolean, close: Boolean) {
        if (tip) {
            sound(SoundEvent.ITEM_TRIDENT_HIT, 1f, varied(1.3f))
            sound(SoundEvent.BLOCK_ANVIL_LAND, .3f, varied(1.85f))
            sound(SoundEvent.BLOCK_BELL_USE, .4f, varied(1.7f))
            sound(SoundEvent.ENTITY_PLAYER_ATTACK_CRIT, .9f, varied(1.15f))
            sound(SoundEvent.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, .3f, varied(1.4f))
        }
        if (close) {
            sound(SoundEvent.ENTITY_PLAYER_ATTACK_STRONG, .9f, varied(.6f))
            sound(SoundEvent.ENTITY_PLAYER_ATTACK_KNOCKBACK, .6f, varied(.7f))
            sound(SoundEvent.BLOCK_WOOD_HIT, .5f, varied(.6f))
        }
    }
    /** 全力の一振り's wind-up: a heavy breath drawn in as the sword goes onto the shoulder. */
    private fun fullSwingBreathSound() {
        sound(SoundEvent.ENTITY_PLAYER_BREATH, 1f, varied(.6f))
        sound(SoundEvent.ENTITY_RAVAGER_STUNNED, .3f, varied(.7f))
        sound(SoundEvent.ITEM_ARMOR_EQUIP_IRON, .55f, varied(.7f))
    }
    /** ...then a low rumble under the feet while it is held. */
    private fun fullSwingRumbleSound() {
        sound(SoundEvent.ENTITY_RAVAGER_ROAR, .35f, varied(.5f))
        sound(SoundEvent.ENTITY_WARDEN_STEP, .9f, varied(.5f))
        sound(SoundEvent.BLOCK_ROOTED_DIRT_BREAK, .6f, varied(.5f))
    }
    /** 全力の一振り's impact: a deep crack of the ground and a meaty, wet blow (the blood). */
    private fun fullSwingImpactSound() {
        sound(SoundEvent.ITEM_MACE_SMASH_GROUND_HEAVY, 1f, varied(.6f))
        sound(SoundEvent.ENTITY_GENERIC_EXPLODE, .45f, varied(.5f))
        sound(SoundEvent.BLOCK_DEEPSLATE_BREAK, .9f, varied(.5f))
        sound(SoundEvent.ENTITY_WARDEN_ATTACK_IMPACT, .9f, varied(.6f))
        sound(SoundEvent.ENTITY_SLIME_SQUISH, .8f, varied(.55f))
        sound(SoundEvent.BLOCK_HONEY_BLOCK_BREAK, .6f, varied(.6f))
    }
    /** 天落とし: ring [k] hits what stands between the ring inside it and its own radius, from the landing point. */
    private fun skyfallRing(enemies: QuestEncounterCombat, action: PendingSkill, k: Int) {
        val centre = action.landAt ?: return
        val outer = CoreWarriorRules.SKYFALL_RINGS[k]
        val inner = if (k == 0) -1.0 else CoreWarriorRules.SKYFALL_RINGS[k - 1]
        vfx.playSkill(CoreSkillEffect(classId, action.definition.copy(radius = outer), centre, action.direction, pulse = k))
        val epoch = actionEpoch
        for (target in enemies.combatTargets()) {
            val p = enemies.positionOf(target.id) ?: continue
            val d = kotlin.math.hypot(p.x() - centre.x(), p.z() - centre.z())
            if (d > inner && d <= outer && abs(p.y() - centre.y()) < 3.0 && visibleTo(target.id, enemies)) {
                hit(enemies, target.id, if (k == 0) 1.0 else .7, skill = true, skillAction = action)
                if (!actionsValid(enemies, epoch)) return
            }
        }
    }
    /** Who the current strike has hit (hit-stop, shoves, the beast counted per strike). */
    private val hitThisStrike = mutableSetOf<UUID>()
    private fun grounded(): Boolean = player.isOnGround ||
        player.instance?.getBlock(player.position.sub(0.0, .05, 0.0))?.solid() == true

    private fun emitSkillPulse(centre: Pos, radius: Double, endpoint: CoreSkillEndpoint = CoreSkillEndpoint.NONE) {
        val action = pending ?: return
        val pulse = (action.elapsed - action.startup) / CoreCombatFeel.pulseTicks(action.definition)
        vfx.playSkill(CoreSkillEffect(classId, action.definition.copy(radius = radius), centre, action.direction,
            pulse = pulse, endpoint = endpoint))
    }
    /** 全力の一振り's blood burst, played out along the rift a step a tick after the blow (see tick()). */
    private var geyser: CoreSkillEffect? = null
    private var geyserStep = 0

    /** Lock a ground cast to the visible aimed enemy/block, not a fixed point seven blocks ahead. */
    private fun aimedGround(s: CoreSkillDefinition, enemies: QuestEncounterCombat): Pos {
        val from=player.position.add(0.0,1.0,0.0);val direction=player.position.direction()
        val maximum=minOf(s.range,24.0-s.radius).coerceAtLeast(1.0)
        val aimed=enemies.combatTargets().mapNotNull { target ->
            val position=enemies.positionOf(target.id) ?: return@mapNotNull null
            if(target.parts.isNotEmpty()) return@mapNotNull target.parts.rayHit(from,direction,maximum,.8)
                ?.takeIf { visibleTo(target.id,enemies) }?.let { position to it }
            val offset=position.add(0.0,1.0,0.0).sub(from)
            val along=offset.x()*direction.x()+offset.y()*direction.y()+offset.z()*direction.z()
            if(along in 0.0..maximum && from.add(direction.mul(along)).distance(position.add(0.0,1.0,0.0)) < target.halfExtent.x()+.8 && visibleTo(target.id,enemies)) position to along else null
        }.minByOrNull { it.second }
        if(aimed!=null) return aimed.first
        var previous=from
        for(step in 1..ceil(maximum*4).toInt()) {
            val point=from.add(direction.mul(step*.25))
            if(player.instance.getBlock(point).solid()) return previous.sub(0.0,if(direction.y()<-.1) 0.0 else 1.0,0.0)
            previous=point
        }
        return player.position.add(flatFacing().mul(minOf(maximum,7.0)))
    }

    /**
     * Big floating number over the hit (2026-10-20): the newest is the largest and highest; the target's older
     * numbers shrink and sink below it, so a burst of hits reads as a stack.
     */
    private fun showDamage(target: java.util.UUID, position: Pos, amount: Double, critical: Boolean, type: CoreDamageType,
                           element: String?, spot: Boolean) {
        // Bounded, short-lived combat feedback; never a client damage declaration.
        if (damageLabels.size >= 24) damageLabels.removeAt(0).entity.remove()
        val pixel = packed()
        if (pixel) for (old in damageLabels) if (old.target == target && !old.entity.isRemoved) {
            old.shrunk = true
            old.entity.editEntityMeta(TextDisplayMeta::class.java) { meta ->
                meta.setTransformationInterpolationStartDelta(0); meta.setTransformationInterpolationDuration(4)
                meta.setScale(Vec(old.size * .5, old.size * .5, old.size * .5)); meta.setTranslation(Vec(0.0, .1, 0.0))
            }
        }
        val size = if (critical) 1.25 else 1.0
        val random = ThreadLocalRandom.current()
        val entity = Entity(EntityType.TEXT_DISPLAY).apply {
            setNoGravity(true); setHasPhysics(false)
            editEntityMeta(TextDisplayMeta::class.java) { meta ->
                meta.setText(if (pixel) dev.projects.server.coreloop.ui.CoreDamageNumbers.render(amount, critical, type, element, spot)
                    else Component.text("${if (critical) "✦ " else ""}-${amount.roundToInt()}${if (element != null || spot) " 弱点" else ""}",
                        if (critical) NamedTextColor.GOLD else if (type == CoreDamageType.MAGICAL) NamedTextColor.BLUE else NamedTextColor.YELLOW))
                meta.setBillboardRenderConstraints(AbstractDisplayMeta.BillboardConstraints.CENTER)
                // Pop in large, then settle and rise (client-side interpolation, two meta updates).
                meta.setScale(Vec(size * 1.6, size * 1.6, size * 1.6)); meta.setShadow(!pixel); meta.setBackgroundColor(0)
            }
            setInstance(player.instance, position.add(random.nextDouble(-.3, .3), 2.3 + random.nextDouble(0.0, .2), random.nextDouble(-.3, .3)))
        }
        val label = DamageLabel(entity, tickNumber + 18, target, size)
        if (pixel) {
            entity.scheduler().buildTask {
                if (label.shrunk) return@buildTask
                entity.editEntityMeta(TextDisplayMeta::class.java) { meta ->
                    meta.setTransformationInterpolationStartDelta(0); meta.setTransformationInterpolationDuration(3)
                    meta.setScale(Vec(size, size, size)); meta.setTranslation(Vec(0.0, .2, 0.0))
                }
            }.delay(net.minestom.server.timer.TaskSchedule.tick(1)).schedule()
            entity.scheduler().buildTask {
                if (label.shrunk) return@buildTask
                entity.editEntityMeta(TextDisplayMeta::class.java) { meta ->
                    meta.setTransformationInterpolationStartDelta(0); meta.setTransformationInterpolationDuration(12)
                    meta.setScale(Vec(size * .85, size * .85, size * .85)); meta.setTranslation(Vec(0.0, .8, 0.0))
                }
            }.delay(net.minestom.server.timer.TaskSchedule.tick(5)).schedule()
        }
        damageLabels += label
    }

    private fun visibleTo(targetId: java.util.UUID, enemies: QuestEncounterCombat): Boolean {
        val target = enemies.positionOf(targetId) ?: return false
        val start = player.position.add(0.0, 1.0, 0.0)
        val end = target.add(0.0, 1.0, 0.0)
        val steps = ceil(start.distance(end) * 4).toInt().coerceAtLeast(1)
        return (1 until steps).all { step ->
            val point = start.add(end.sub(start).mul(step.toDouble() / steps))
            !player.instance.getBlock(point).solid()
        }
    }

    /**
     * Crowd control landing on this player. [pvp] marks another player as the source (half duration).
     * Diminishing returns and the dodge escape live in [CorePlayerCrowdControl]. Returns the ticks applied.
     */
    fun receiveCrowdControl(kind: CrowdControl, millis: Long, from: Pos?, pvp: Boolean = false): Int {
        if (defeated || armored) return 0  // 狂戦士の甲冑: no crowd control takes hold
        // 全力の一振り's wind-up cannot be stopped: nothing takes hold until the blow has landed.
        if (classId == CoreClass.WARRIOR && pending?.let { it.full && it.elapsed <= it.startup } == true) return 0
        if (kind == CrowdControl.FLINCH || CorePlayerCrowdControl.INCAPACITATING.contains(kind)) {
            // A flinch or anything incapacitating cancels the windup in progress.
            queued = null
            if (pending != null) { pending = null; vfx.cancel(preserveGarden = garden != null) }
        }
        val ticks = crowd.apply(kind, millis, tickNumber, pvp, from, sheet.mods.bonus(CoreAffixStat.CC_RESIST))
        if (ticks <= 0) return 0
        val away = from?.let { player.position.sub(it).let { d -> Vec(d.x(), 0.0, d.z()) } }
            ?.takeIf { it.length() > 1e-6 }?.normalize() ?: flatFacing().mul(-1.0)
        when (kind) {
            CrowdControl.KNOCKBACK -> player.velocity = away.mul(11.0).withY(4.5)
            CrowdControl.AIRBORNE -> player.velocity = Vec(0.0, 10.0, 0.0)
            CrowdControl.BLIND -> player.addEffect(net.minestom.server.potion.Potion(net.minestom.server.potion.PotionEffect.BLINDNESS, 0, ticks))
            else -> Unit
        }
        refreshMovement()
        sound(when (kind) {
            CrowdControl.STUN -> SoundEvent.BLOCK_ANVIL_LAND
            CrowdControl.ROOT -> SoundEvent.BLOCK_CHAIN_PLACE
            else -> SoundEvent.ENTITY_PLAYER_ATTACK_KNOCKBACK
        }, .5f, if (kind == CrowdControl.STUN) 1.6f else .8f)
        notice(crowd.cue(tickNumber) ?: kind.label)
        return ticks
    }

    /** Holds the player in place while a movement lock lasts; charm and fear walk the player for them. */
    private fun crowdTick() {
        if (crowd.canMove(tickNumber) == movementHeld) refreshMovement() // a lock began or ended this tick
        if (crowd.forced(tickNumber) && tickNumber % 4 == 0L) crowd.source?.let { from ->
            val toward = Vec(from.x() - player.position.x(), 0.0, from.z() - player.position.z())
            if (toward.length() > .6) {
                val step = toward.normalize().mul(if (crowd.active(CrowdControl.FEAR, tickNumber)) -4.0 else 4.0)
                player.velocity = step.withY(player.velocity.y())
            }
        }
    }

    /** Applies the walking speed now and locks/unlocks jumping with the movement lock. */
    private fun refreshMovement() {
        val held = !crowd.canMove(tickNumber)
        movementHeld = held
        applySpeed(speedFactor())
        player.getAttribute(Attribute.JUMP_STRENGTH).baseValue = if (held) 0.0 else JUMP_STRENGTH
    }

    /** [blow] false for damage that is not a strike (ticking effects, setup): it never staggers. */
    fun hurt(amount: Double, type: CoreDamageType = CoreDamageType.PHYSICAL, blow: Boolean = true) {
        if (defeated || encounter() == null || !amount.isFinite() || amount <= 0.0) return
        // 回避の無敵. TRUE damage (failed gimmicks) cannot be rolled through.
        if (tickNumber < evadeUntil && type != CoreDamageType.TRUE) {
            sound(SoundEvent.ENTITY_BREEZE_WIND_BURST, .45f, 1.6f)
            return
        }
        // 体当たり: from the press to the impact, the first blow is caught whole; only one, ever, per charge.
        pending?.takeIf { classId == CoreClass.WARRIOR && it.definition.icon == "dash" && !it.caught && type != CoreDamageType.TRUE &&
            (it.elapsed < it.startup || it.chargeTick >= 0) }?.let { charge ->
            charge.caught = true
            look(charge, CoreWarriorRefChoreography.TACKLE_CATCH_PULSE)
            feedBeast(CoreWarriorRules.TACKLE_CATCH_BEAST, cap = CoreWarriorRules.TACKLE_CATCH_BEAST)
            notice("体当たり：受け止めた")
            // A sharp metal block, a low anvil under it.
            sound(SoundEvent.ITEM_SHIELD_BLOCK, .9f, varied(.9f))
            sound(SoundEvent.BLOCK_ANVIL_LAND, .55f, varied(.55f))
            lastCombat = tickNumber
            return
        }
        val snapshot = sheet
        var adjusted = CoreCombatMath.mitigate(amount, type, snapshot.ar, snapshot.mr, damageReduction = snapshot.mods.mitigationPercent)
        if (classId == CoreClass.WARRIOR && journey().build.keystone == 0 && resource >= 2) adjusted *= 1.1
        val guarded = tickNumber < classState.guardUntil
        if (guarded) {
            val perfect = tickNumber <= classState.perfectUntil
            adjusted *= if (perfect) .2 else .45
            classState.guarded(classId, tickNumber, journey().build)
            if (classId == CoreClass.WARRIOR && perfect) {
                // One read per stance; it shows the catch but refreshes nothing (the parry/slam reset was removed, D06).
                classState.perfectUntil = -1
                val guard = CoreSkillCatalog.skills(CoreClass.WARRIOR).first { it.icon == "war_guard" }
                vfx.playSkill(CoreSkillEffect(classId, guard, player.position, flatFacing(), CoreSkillVisualPhase.CONTACT))
            }
            sound(SoundEvent.ITEM_SHIELD_BLOCK, .8f, if (perfect) 1.4f else .8f)
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
        if (!guarded && blow) react(adjusted / maxHealth)
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

    fun sharpen() { whetstoneUntil = tickNumber + WHETSTONE_TICKS }
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
        garden?.let { it.encounter.clearSlowSource(it.field.slowSource) }
        garden = null
        normal.reset(); pending = null; shot = null; lastConduitGain = -1; skillBoost = 1.0; burns.clear()
        normalEmpowerment = 1.0
        queued = null; queueUntil = -1; nextNormalAt = -1; finisherUntil = -1; beastTick = -1; beastThisTick = 0.0; comboFrom = null; comboUntil = -1; armorUntil = -1; awakenUntil = -1; geyser = null; geyserStep = 0
        classState.reset(); moveHasteUntil = -1; previousPosition = null
        crowd.clear()
        evadeUntil = -1; dodgeQueuedUntil = -1; slide = null; staggerUntil = -1
        if (movementHeld) { movementHeld = false; player.getAttribute(Attribute.JUMP_STRENGTH).baseValue = JUMP_STRENGTH }
        if (appliedSpeed >= 0 && appliedSpeed != baseSpeed) applySpeed(1.0)
        vfx.cancel()
        warriorMarks.clear()
        damageLabels.forEach { it.entity.remove() }; damageLabels.clear()
    }
    private fun syncVanillaHealth() {
        player.getAttribute(Attribute.MAX_HEALTH).baseValue = 20.0
        player.health = (20.0 * health / maxHealth).toFloat().coerceIn(0.1f, 20f)
    }

    /** Returns how far the player actually moved. */
    private fun moveSafely(direction: Vec, distance: Double): Double {
        val start = player.position
        var destination = start
        val grounded=player.instance.getBlock(start.sub(0.0,.15,0.0)).solid()
        for (step in 1..ceil(distance / 0.15).toInt()) {
            val next = start.add(direction.mul(min(distance, step * 0.15)))
            if(grounded && !player.instance.getBlock(next.sub(0.0,.15,0.0)).solid()) break
            if (!listOf(-0.3, 0.3).all { x -> listOf(-0.3, 0.3).all { z ->
                listOf(0.1, 0.9, 1.7).all { y -> !player.instance.getBlock(next.add(x, y, z)).solid() }
            } }) break
            destination = next
        }
        player.teleport(destination)
        return start.distance(destination)
    }

    private fun flatFacing(): Vec = Vec(player.position.direction().x(), 0.0, player.position.direction().z()).let {
        if (it.lengthSquared() < 0.001) Vec(0.0, 0.0, 1.0) else it.normalize()
    }
    /** A plate must have four supported corners, headroom, and the same level as its centre. */
    private fun gardenSurface(at: Pos, size: Double = CoreIceGarden.CELL): Pos? {
        val instance = player.instance ?: return null
        val base = floor(at.y()).toInt()
        val surface = (base+1 downTo base-3).firstOrNull { y ->
            listOf(-size/2+.04,0.0,size/2-.04).all { x -> listOf(-size/2+.04,0.0,size/2-.04).all { z ->
                instance.getBlock(Pos(at.x()+x,y.toDouble(),at.z()+z)).solid() &&
                    !instance.getBlock(Pos(at.x()+x,y+1.0,at.z()+z)).solid() &&
                    !instance.getBlock(Pos(at.x()+x,y+2.0,at.z()+z)).solid()
            } }
        } ?: return null
        return Pos(at.x(),surface+1.0,at.z())
    }
    private fun tickGarden(enemies: QuestEncounterCombat) {
        val planted = garden ?: return
        val field = planted.field
        if(planted.encounter !== enemies || planted.instance !== player.instance || classId != CoreClass.MAGE ||
            player.position.distance(planted.action.origin) > 24) {
            enemies.clearSlowSource(field.slowSource); planted.encounter.clearSlowSource(field.slowSource)
            garden = null; vfx.clearGarden(); return
        }
        if(!field.active(tickNumber)) {
            enemies.clearSlowSource(field.slowSource)
            garden = null; sound(SoundEvent.BLOCK_GLASS_BREAK,.3f,1.6f); return
        }
        if(tickNumber == field.endsAt-20) sound(SoundEvent.BLOCK_AMETHYST_BLOCK_CHIME,.35f,.65f)
        if((tickNumber-field.openedAt) % 10 == 0L) vfx.gardenBoundary(field.cells,field.cellSize)
        if((tickNumber-field.openedAt) % CoreIceGarden.CHECK_INTERVAL != 0L) return
        val epoch = actionEpoch
        val supported = field.cells.filter { gardenSurface(it,field.cellSize) == it }.toSet()
        for(target in enemies.combatTargets()) {
            val feet = enemies.positionOf(target.id) ?: continue
            if(field.cellAt(feet,supported) == null || !clearLine(planted.action.origin,feet)) continue
            // QuestEncounterCombat halves boss slow: .20 here yields only 10% and never an interrupt.
            val slow = if(enemies.isBoss(target.id)) .2 else if(journey().build.keystone == 2) .5 else .4
            enemies.applySlow(target.id,slow,CoreIceGarden.SLOW_MILLIS,field.slowSource)
            if(field.canHit(target.id,tickNumber)) hit(enemies,target.id,1.0,skill=true,skillAction=planted.action,
                contact=field.hitCount(target.id)==0,fieldOrigin=planted.action.origin,accepted={ field.claimHit(target.id,tickNumber) })
            if(!actionsValid(enemies,epoch)) return
        }
    }
    private fun actionsValid(enemies: QuestEncounterCombat, epoch: Long): Boolean =
        epoch == actionEpoch && !defeated && encounter() === enemies && player.instance != null
    /** Feeds the beast, at most [CoreWarriorRules.BEAST_PER_ACTION] per tick (one action's hits land together). */
    private fun feedBeast(amount: Double, cap: Double = CoreWarriorRules.BEAST_PER_ACTION) {
        if (beastTick != tickNumber) { beastTick = tickNumber; beastThisTick = 0.0 }
        val add = amount.coerceAtMost(cap - beastThisTick)
        if (add <= 0.0) return
        beastThisTick += add
        val before = classState.resource.toInt()
        classState.gain(add, classId, journey().build)
        if (classState.resource.toInt() > before) sound(SoundEvent.ENTITY_WARDEN_HEARTBEAT, .8f, .7f + .15f * classState.resource.toInt())
    }
    /** Feedback goes into the HUD caption slot; a separate action-bar message would blank the HUD. */
    fun notice(message: String) {
        noticeText = message
        noticeUntil = System.currentTimeMillis() + NOTICE_MILLIS
        hudDirty = true
    }
    private var noticeText = ""
    private var noticeUntil = 0L
    private var hudDirty = false
    val activeNotice: String get() = if (System.currentTimeMillis() < noticeUntil) noticeText else ""
    /** True once after a notice: the server refreshes the HUD now instead of on its 5-tick cadence. */
    fun consumeHudDirty(): Boolean = hudDirty.also { hudDirty = false }
    fun hudBuffs(): List<dev.projects.server.coreloop.ui.CoreHudBuff> = buildList {
        val whetstone = whetstoneUntil - tickNumber
        if (whetstone > 0) add(dev.projects.server.coreloop.ui.CoreHudBuff(dev.projects.server.coreloop.ui.CoreHudBuffKind.WHETSTONE,
            whetstone / 20.0, WHETSTONE_TICKS / 20.0, cooldown = false))
        val dodge = nextDodge - tickNumber
        if (dodge > 0) add(dev.projects.server.coreloop.ui.CoreHudBuff(dev.projects.server.coreloop.ui.CoreHudBuffKind.DODGE,
            dodge / 20.0, dodgeCooldown / 20.0, cooldown = true))
    }
    private fun sound(event: SoundEvent, volume: Float, pitch: Float) = player.playSound(Sound.sound(event, Sound.Source.PLAYER, volume, pitch))

    companion object {
        val SKILL_NAMES = listOf("轟進", "地割り", "押し薙ぎ")
        const val WHETSTONE_TICKS = 20L * 180
        const val NOTICE_MILLIS = 1_600L
        /** Vanilla player jump strength, restored when a movement lock ends. */
        const val JUMP_STRENGTH = 0.41999998688697815
        const val DODGE_DISTANCE = 2.6
        const val DODGE_SLIDE_TICKS = 4
        /** About 0.35 s of evasion from the press, a little longer than the slide. */
        const val DODGE_EVADE_TICKS = 7L
        const val DODGE_BUFFER_TICKS = 8L
        /** Skills with a startup this long (or ultimates) root the caster and cannot be dodge-cancelled before they fire. */
        const val HEAVY_STARTUP = 12
        const val CAST_SPEED = .35
        const val SWING_SPEED = .6
        /** Better Combat's claymore: upswing .5 x the server's upswing_multiplier .5. */
        const val GREATSWORD_UPSWING = .25
        const val STAGGER_SHARE = .2
        const val KNOCKDOWN_SHARE = .45
        const val STAGGER_TICKS = 10L
        const val KNOCKDOWN_TICKS = 20L
        const val WAKE_EVADE_TICKS = 8L
        const val STAGGER_DISTANCE = 2.5
        const val KNOCKDOWN_DISTANCE = 4.0
    }
}
