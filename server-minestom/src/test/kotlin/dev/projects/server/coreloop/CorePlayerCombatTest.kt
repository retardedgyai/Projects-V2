package dev.projects.server.coreloop

import dev.projects.server.mob.QuestEncounterCombat
import net.minestom.server.Auth
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Pos
import net.minestom.server.component.DataComponents
import net.minestom.server.entity.GameMode
import net.minestom.server.entity.Player
import net.minestom.server.entity.attribute.Attribute
import net.minestom.server.instance.block.Block
import net.minestom.server.network.ConnectionState
import net.minestom.server.network.packet.server.SendablePacket
import net.minestom.server.network.player.GameProfile
import net.minestom.server.network.player.PlayerConnection
import java.net.InetSocketAddress
import java.net.SocketAddress
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Real Minestom entities, but no sockets, external client, native input, or live server port. */
class CorePlayerCombatTest {
    @Test fun `switching held weapons or disabling pack cancels stale action poses`() = arena { h ->
        CoreCombatPresentation.pack(h.player,true)
        try {
            val item=CoreLoopItems.gear(CoreAccount(h.player.uuid),CoreGearSlot.WEAPON,true)
            h.player.setItemInMainHand(item)
            val definition=CoreSkillCatalog.skills(CoreClass.WARRIOR).first { it.icon=="war_wound" }
            val effect=CoreSkillEffect(CoreClass.WARRIOR,definition,h.player.position,h.player.position.direction(),CoreSkillVisualPhase.PREPARE)
            CoreArmamentPresentation.skill(h.player,effect)
            assertEquals(12f,h.player.itemInMainHand.get(DataComponents.CUSTOM_MODEL_DATA)!!.floats().first())
            h.player.setItemInMainHand(net.minestom.server.item.ItemStack.AIR)
            CoreArmamentPresentation.tick(h.player,true)
            h.player.setItemInMainHand(item);CoreArmamentPresentation.tick(h.player,true)
            assertEquals(0f,h.player.itemInMainHand.get(DataComponents.CUSTOM_MODEL_DATA)!!.floats().first())
            CoreArmamentPresentation.skill(h.player,effect)
            CoreArmamentPresentation.tick(h.player,false)
            CoreArmamentPresentation.tick(h.player,true)
            assertEquals(0f,h.player.itemInMainHand.get(DataComponents.CUSTOM_MODEL_DATA)!!.floats().first())
        } finally { CoreCombatPresentation.forget(h.player) }
    }

    @Test fun `accepted class casts animate equipped models and cancellation returns them to idle`() = arena { h ->
        CoreCombatPresentation.pack(h.player,true)
        try {
            for(job in CoreClass.entries) {
                h.journey=CoreJourney(job=job);h.base=CoreWeaponBase.entries.first { it.usable(job) };h.actor.reset()
                val a=CoreAccount(h.player.uuid,journey=h.journey,
                    weaponIdentity=CoreGearIdentity(UUID.randomUUID(),h.player.uuid,base=h.base))
                h.player.setItemInMainHand(CoreLoopItems.gear(a,CoreGearSlot.WEAPON,true))
                h.actor.refillTraining()
                h.actor.skill(0)
                fun pose()=h.player.itemInMainHand.get(DataComponents.CUSTOM_MODEL_DATA)?.floats()?.firstOrNull()
                assertEquals(12f,pose(),"$job accepted anticipation")
                h.ticks(h.actor.skillDefinitions[0].startupTicks(h.actor.sheet))
                assertEquals(18f,pose(),"$job first authoritative pulse")
                h.actor.resetActions();CoreArmamentPresentation.tick(h.player,true)
                assertEquals(0f,pose(),"$job cancelled visual clip")
            }
        } finally { CoreCombatPresentation.forget(h.player) }
    }

    @Test fun `rejected casts and target contact do not invent or restart weapon release`() = arena { h ->
        CoreCombatPresentation.pack(h.player,true)
        try {
            h.journey=CoreJourney(job=CoreClass.MAGE);h.base=CoreWeaponBase.STAFF;h.actor.reset()
            val a=CoreAccount(h.player.uuid,journey=h.journey,
                weaponIdentity=CoreGearIdentity(UUID.randomUUID(),h.player.uuid,base=h.base))
            h.player.setItemInMainHand(CoreLoopItems.gear(a,CoreGearSlot.WEAPON,true))
            h.weaponBroken=true;h.actor.skill(0)
            assertEquals(null,h.player.itemInMainHand.get(DataComponents.CUSTOM_MODEL_DATA))
            h.weaponBroken=false;h.actor.skill(0)
            val prepared=h.player.itemInMainHand
            val definition=h.actor.skillDefinitions[0]
            CoreArmamentPresentation.skill(h.player,CoreSkillEffect(CoreClass.MAGE,definition,h.player.position,h.player.position.direction(),CoreSkillVisualPhase.CONTACT))
            CoreArmamentPresentation.skill(h.player,CoreSkillEffect(CoreClass.MAGE,definition,h.player.position,h.player.position.direction(),pulse=1))
            assertEquals(prepared,h.player.itemInMainHand)
            h.actor.skill(0) // Pending cast rejects the repeated command.
            assertEquals(prepared,h.player.itemInMainHand)
        } finally { CoreCombatPresentation.forget(h.player) }
    }

    @Test fun `training refill clears costs but preserves all in-flight whirlwind hit timings`() = arena(bossDistance = 2.0) { h ->
        h.actor.skill(2)
        repeat(5) { h.actor.refillTraining(); h.ticks(1) }
        assertEquals(300.0, h.combat.bossHealth())
        h.actor.refillTraining(); h.ticks(1)
        assertEquals(286.8, h.combat.bossHealth(), .00001)
        repeat(16) { h.actor.refillTraining(); h.ticks(1) }
        assertEquals(260.4, h.combat.bossHealth(), .00001)
        assertEquals(0, h.actor.cooldownRemaining(2))
        assertEquals(h.actor.maxMana, h.actor.mana)
    }

    @Test fun `all lab classes can fill resources and return to zero without changing their build`() = arena { h ->
        for (job in CoreClass.entries) {
            val loadout = CoreLabLoadout(h.player.uuid, job)
            h.journey = loadout.journey; h.base = loadout.weaponBase
            h.actor.reset(); h.actor.refillTraining()
            assertEquals(h.actor.resourceMax, h.actor.resource)
            assertTrue((0..4).all { h.actor.resourceAvailable(it) && h.actor.skillAvailable(it) })
            h.actor.reset()
            assertEquals(0.0, h.actor.resource)
            assertEquals(CoreClassBuild(), h.journey.build)
        }
    }

    @Test fun `Starweaver weaves three normal hits then consumes them in four enhanced starfall pulses`() = arena(bossDistance = 7.0) { h ->
        h.journey = CoreJourney(job=CoreClass.STARWEAVER); h.base=CoreWeaponBase.STAFF
        repeat(3) { h.actor.attack(); h.ticks(20) }
        assertEquals(3,h.actor.chargeCount)
        val before=h.combat.bossHealth(); h.actor.skill(2); assertEquals(0,h.actor.chargeCount)
        h.ticks(40)
        assertEquals(before - (4 + 12 * .92 * 1.1) * 1.45 * 4,h.combat.bossHealth(),.00001)
    }
    @Test fun `bow and mage projectile hit at twelve blocks but not through a wall`() {
        for (job in listOf(CoreClass.RANGER, CoreClass.MAGE, CoreClass.STARWEAVER)) arena(bossDistance = 12.0) { h ->
            h.journey = CoreJourney(job = job); h.base = if (job == CoreClass.RANGER) CoreWeaponBase.LONGBOW else CoreWeaponBase.STAFF
            h.actor.attack(); h.ticks(3); assertEquals(300.0, h.combat.bossHealth())
            h.ticks(1); assertTrue(h.combat.bossHealth() < 300.0, "$job cannot hit at range")
            val hp = h.combat.bossHealth(); h.ticks(20)
            for (y in 40..45) h.instance.setBlock(8,y,14,Block.STONE)
            h.actor.attack(); h.ticks(20); assertEquals(hp, h.combat.bossHealth())
        }
    }
    @Test fun `early class cannot cast level4 or level8 skill`() = arena { h ->
        h.journey = CoreJourney.fresh().copy(chosen = true, job = CoreClass.MAGE); h.base = CoreWeaponBase.STAFF
        h.actor.classState.gain(100.0, h.journey.job, h.journey.build); h.actor.skill(1); h.actor.skill(2); assertEquals(100, h.actor.mana)
        h.ticks(30); assertEquals(300.0, h.combat.bossHealth())
        h.actor.skill(0); h.ticks(5); assertTrue(h.combat.bossHealth() < 300.0)
    }
    @Test fun `weapon sheet attack speed and power match all actual bases`() = arena { h ->
        for (base in CoreWeaponBase.entries) {
            h.base = base
            val a = CoreAccount(UUID.randomUUID(), weaponIdentity = CoreGearIdentity(UUID.randomUUID(), UUID.randomUUID(), base = base))
            assertEquals(h.actor.attackSpeed, 1 + CoreWeaponPresentation.attackSpeedPercent(a) / 100, .000001)
            assertEquals(kotlin.math.round(h.actor.attackDamage).toInt(), CoreWeaponPresentation.damage(a))
        }
    }
    @Test
    fun `duplicate vanilla swing signals start one normal attack and hit once during active frames`() = arena { h ->
        h.actor.attack()
        h.actor.attack()
        h.ticks(7)
        assertEquals(300.0, h.combat.bossHealth())
        h.ticks(1)
        assertEquals(288.0, h.combat.bossHealth())
        h.ticks(20)
        assertEquals(288.0, h.combat.bossHealth())
    }

    @Test
    fun `normal attack cannot hit a target behind the server facing`() = arena { h ->
        h.player.setView(180f, 0f)
        h.actor.attack()
        h.ticks(20)
        assertEquals(300.0, h.combat.bossHealth())
        h.player.setView(0f, 0f)
        h.actor.attack()
        h.ticks(20)
        assertEquals(286.2, h.combat.bossHealth(), 0.00001)
    }

    @Test
    fun `wall blocks a spatially eligible normal hit`() = arena { h ->
        for (y in 40..43) h.instance.setBlock(8, y, 10, Block.STONE_BRICKS)
        h.actor.attack()
        h.ticks(20)
        assertEquals(300.0, h.combat.bossHealth())
    }

    @Test
    fun `lunge spends mana once hits after startup and respects its cooldown`() = arena { h ->
        h.actor.skill(0)
        h.actor.skill(0)
        assertEquals(88, h.actor.mana)
        assertEquals(4L, h.actor.cooldownSeconds(0))
        h.ticks(4)
        assertEquals(300.0, h.combat.bossHealth())
        h.ticks(1)
        assertEquals(279.6, h.combat.bossHealth(), 0.00001)
        assertTrue(h.player.position.z() > 10.6)
        h.ticks(11)
        val manaDuringCooldown = h.actor.mana
        h.actor.skill(0)
        assertEquals(manaDuringCooldown, h.actor.mana)
        h.ticks(64)
        assertEquals(0L, h.actor.cooldownSeconds(0))
        val readyMana = h.actor.mana
        h.actor.skill(0)
        assertEquals(readyMana - 12, h.actor.mana)
    }

    @Test
    fun `lunge stops before a close enemy and still connects instead of passing through`() = arena(bossDistance = 1.5) { h ->
        h.actor.skill(0)
        h.ticks(5)
        assertEquals(9.0, h.player.position.z(), 0.00001)
        assertEquals(279.6, h.combat.bossHealth(), 0.00001)
        h.ticks(15)
        assertEquals(279.6, h.combat.bossHealth(), 0.00001)
    }

    @Test
    fun `ground slam resolves once at its startup boundary and has six second cooldown`() = arena { h ->
        h.actor.classState.gain(100.0, h.journey.job, h.journey.build); h.actor.skill(1)
        assertEquals(80, h.actor.mana)
        assertEquals(6L, h.actor.cooldownSeconds(1))
        h.ticks(11)
        assertEquals(300.0, h.combat.bossHealth())
        h.ticks(1)
        assertEquals(259.6, h.combat.bossHealth(), 0.00001)
        h.ticks(20)
        assertEquals(259.6, h.combat.bossHealth(), 0.00001)
        val mana = h.actor.mana
        h.actor.classState.gain(100.0, h.journey.job, h.journey.build); h.actor.skill(1)
        assertEquals(mana, h.actor.mana)
    }

    @Test
    fun `whirlwind damages surrounding target in exactly three timed pulses`() = arena(bossDistance = 2.0) { h ->
        h.player.setView(180f, 0f)
        h.actor.skill(2)
        assertEquals(76, h.actor.mana)
        assertEquals(9L, h.actor.cooldownSeconds(2))
        h.ticks(5)
        assertEquals(300.0, h.combat.bossHealth())
        h.ticks(1)
        assertEquals(286.8, h.combat.bossHealth(), 0.00001)
        h.ticks(8)
        assertEquals(273.6, h.combat.bossHealth(), 0.00001)
        h.ticks(8)
        assertEquals(260.4, h.combat.bossHealth(), 0.00001)
        h.ticks(15)
        assertEquals(260.4, h.combat.bossHealth(), 0.00001)
    }

    @Test
    fun `skill requested during normal swing waits for normal recovery`() = arena { h ->
        h.actor.attack()
        h.actor.classState.gain(100.0, h.journey.job, h.journey.build); h.actor.skill(1)
        assertEquals(100, h.actor.mana)
        h.ticks(19)
        assertEquals(100, h.actor.mana)
        h.ticks(1)
        assertEquals(80, h.actor.mana)
        assertEquals(288.0, h.combat.bossHealth())
        h.ticks(12)
        assertEquals(247.6, h.combat.bossHealth(), 0.00001)
    }

    @Test
    fun `F dodge follows Minecraft strafe direction and cooldown then forward movement`() = arena { h ->
        h.player.refreshInput(false, false, false, true, false, false, false)
        h.actor.dodge()
        assertEquals(5.9, h.player.position.x(), 0.00001)
        assertEquals(8.5, h.player.position.z(), 0.00001)
        val afterFirst = h.player.position
        h.actor.dodge()
        assertEquals(afterFirst, h.player.position)
        h.ticks(24)
        h.player.refreshInput(true, false, false, false, false, false, false)
        h.actor.dodge()
        assertEquals(5.9, h.player.position.x(), 0.00001)
        assertEquals(11.1, h.player.position.z(), 0.00001)
    }

    @Test
    fun `diagonal dodge is normalized and cannot teleport through solid wall`() {
        arena { h ->
            h.player.refreshInput(true, false, false, true, false, false, false)
            h.actor.dodge()
            assertEquals(8.5 - 2.6 / sqrt(2.0), h.player.position.x(), 0.00001)
            assertEquals(8.5 + 2.6 / sqrt(2.0), h.player.position.z(), 0.00001)
        }
        arena { h ->
            for (y in 40..42) h.instance.setBlock(8, y, 10, Block.STONE)
            h.actor.dodge()
            assertTrue(h.player.position.z() in 8.5..9.7)
            assertTrue(h.player.position.z() + 0.3 < 10.000001)
        }
    }

    @Test
    fun `death cancels pending and queued actions and callback fires once until reset`() = arena { h ->
        h.actor.classState.gain(100.0, h.journey.job, h.journey.build); h.actor.skill(1)
        h.actor.dodge()
        val deathPosition = h.player.position
        h.actor.hurt(1000.0)
        h.actor.hurt(1000.0)
        h.ticks(40)
        h.actor.attack()
        h.actor.skill(0)
        h.actor.dodge()
        assertTrue(h.actor.defeated)
        assertEquals(1, h.deaths)
        assertEquals(deathPosition, h.player.position)
        assertEquals(300.0, h.combat.bossHealth())
        h.actor.reset()
        assertFalse(h.actor.defeated)
        assertEquals(100.0, h.actor.health)
        assertEquals(20f, h.player.health)
        assertEquals(100, h.actor.mana)
        assertTrue((0..2).all { h.actor.cooldownSeconds(it) == 0L })
        h.ticks(20)
        assertEquals(300.0, h.combat.bossHealth())
        h.actor.attack()
        h.ticks(20)
        assertEquals(288.0, h.combat.bossHealth())
    }

    @Test
    fun `dying during enemy warning prevents delayed enemy damage`() = arena { h ->
        // Four blocks away permits only the boss's forward slam, so timing is deterministic.
        h.combat.tick(0L)
        h.combat.tick(550L)
        h.actor.hurt(1000.0)
        h.combat.tick(1450L)
        h.combat.tick(3000L)
        assertEquals(1, h.deaths)
        assertTrue(h.incomingHits.isEmpty())
    }

    @Test
    fun `armor damage reduction and potion healing use logical max health`() = arena(armorTier = 2) { h ->
        assertEquals(130, h.actor.maxHealth)
        h.actor.hurt(20.0)
        assertEquals(112.0, h.actor.health)
        assertEquals(20f * 112f / 130f, h.player.health, 0.0001f)
        h.actor.healPotion()
        assertEquals(130.0, h.actor.health)
        assertEquals(20f, h.player.health)
    }

    @Test
    fun `resetActions on map exit prevents a delayed skill from leaking into the next map`() = arena { h ->
        h.actor.classState.gain(100.0, h.journey.job, h.journey.build); h.actor.skill(1)
        h.ticks(5)
        h.actor.resetActions()
        h.activeEncounter = null
        val exitPosition = h.player.position
        h.actor.skill(0)
        h.actor.attack()
        h.actor.dodge()
        h.ticks(30)
        assertEquals(exitPosition, h.player.position)
        h.activeEncounter = h.combat
        h.ticks(30)
        assertEquals(300.0, h.combat.bossHealth())
    }

    private fun arena(bossDistance: Double = 4.0, armorTier: Int = 1, stats: CoreAffixStats = CoreAffixStats(), roll: Double = 1.0,
        weaponEnhancement: Int = 0, armorEnhancement: Int = 0, test: (Harness) -> Unit) {
        MinecraftServer.init(Auth.Offline())
        Harness(bossDistance, armorTier, stats, roll, weaponEnhancement, armorEnhancement).use(test)
    }

    @Test
    fun `increased damage and normal modifiers add in the same layer while haste shortens windup`() = arena(
        stats = CoreAffixStats(damagePercent = 20.0, normalDamagePercent = 10.0, attackSpeedPercent = 50.0)) { h ->
        assertEquals(1.5, h.actor.attackSpeed)
        h.actor.attack()
        h.ticks(7)
        assertEquals(300.0 - 12.0 * 1.3, h.combat.bossHealth(), 0.00001)
    }

    @Test
    fun `critical chance increase and multiplier modify a real server hit`() {
        arena(roll = 0.075) { h ->
            h.actor.attack(); h.ticks(8)
            assertEquals(288.0, h.combat.bossHealth())
        }
        arena(stats = CoreAffixStats(critChanceIncreasedPercent = 100.0, critMultiplierBonusPercent = 30.0), roll = 0.075) { h ->
            h.actor.attack(); h.ticks(8)
            assertEquals(300.0 - 12.0 * 1.8, h.combat.bossHealth(), 0.00001)
        }
    }

    @Test
    fun `physical skill startup uses attack speed and cooldown uses recovery speed`() = arena(
        stats = CoreAffixStats(skillDamagePercent = 50.0, attackSpeedPercent = 50.0, cooldownReductionPercent = 25.0)) { h ->
        h.actor.classState.gain(100.0, h.journey.job, h.journey.build); h.actor.skill(1)
        assertEquals(96, h.actor.cooldownTicks(1))
        h.ticks(7)
        assertEquals(300.0, h.combat.bossHealth())
        h.ticks(1)
        assertEquals(300.0 - (14.0 + 12.0 * 2.2) * 1.5, h.combat.bossHealth(), 0.00001)
        assertEquals(88, h.actor.cooldownRemaining(1))
    }

    @Test
    fun `health mitigation and mobility mods apply once to the armor set`() = arena(armorTier = 2,
        stats = CoreAffixStats(healthFlat = 20.0, mitigationPercent = 20.0, moveSpeedPercent = 20.0)) { h ->
        assertEquals(150, h.actor.maxHealth)
        h.actor.hurt(20.0)
        assertEquals(135.6, h.actor.health, 0.00001)
        assertEquals(20f, h.player.getAttribute(Attribute.MAX_HEALTH).baseValue.toFloat())
        h.ticks(20)
        assertEquals(0.12, h.player.getAttribute(Attribute.MOVEMENT_SPEED).baseValue, 0.00001)
        h.actor.healPotion()
        assertEquals(150.0, h.actor.health)
    }

    @Test
    fun `maximum mana and fractional regeneration are used by the skill pool`() = arena(
        stats = CoreAffixStats(maxManaFlat = 50.0, manaRegenPercent = 40.0)) { h ->
        assertEquals(150, h.actor.maxMana)
        h.actor.skill(2)
        assertEquals(126, h.actor.mana)
        h.ticks(20)
        assertEquals(133, h.actor.mana)
    }

    @Test
    fun `fire mod adds direct damage and three nonrecursive burn ticks`() = arena(stats = CoreAffixStats(fireFlat = 10.0)) { h ->
        h.actor.attack(); h.ticks(8)
        assertEquals(281.5, h.combat.bossHealth(), 0.00001)
        h.ticks(59)
        assertEquals(275.5, h.combat.bossHealth(), 0.00001)
        h.ticks(1)
        assertEquals(272.5, h.combat.bossHealth(), 0.00001)
        h.ticks(60)
        assertEquals(272.5, h.combat.bossHealth(), 0.00001)
    }

    @Test
    fun `ice mod exploits enemy elemental weakness with actual damage`() = arena(stats = CoreAffixStats(iceFlat = 8.0)) { h ->
        assertEquals("ice", h.combat.weaknessOf(h.combat.combatTargets().single().id))
        h.actor.attack(); h.ticks(8)
        assertEquals(300.0 - (12.0 + 8.0 * 0.65) * 1.25, h.combat.bossHealth(), 0.00001)
    }

    @Test
    fun `lightning mod chains once to a nearby second enemy`() = arena { h ->
        h.combat.dispose()
        val encounter = QuestEncounterCombat(h.instance, 1,
            listOf(dev.projects.server.mob.QuestCombatEncounter(listOf(Pos(8.5, 40.0, 12.5), Pos(11.5, 40.0, 12.5)),
                listOf(dev.projects.server.mob.QuestMobArchetype.SOLDIER, dev.projects.server.mob.QuestMobArchetype.SOLDIER))),
            Pos(25.0, 40.0, 25.0), onMobDefeated = { _, _ -> }, damagePlayer = { _, _ -> })
        try {
            val actor = CorePlayerCombat(h.player, { 1 }, { 1 }, { encounter },
                statSource = { CoreAffixStats(lightningFlat = 10.0) }, criticalRoll = { 1.0 }) {}
            actor.reset(); actor.attack(); repeat(8) { actor.tick() }
            val soldiers = encounter.combatTargets().mapNotNull { encounter.mobInfo(it.id) }.filter { it.archetype == dev.projects.server.mob.QuestMobArchetype.SOLDIER }
            assertEquals(listOf(25.5, 36.0), soldiers.map { it.health }.sorted())
            actor.resetActions()
        } finally { encounter.dispose() }
    }

    @Test
    fun `three consecutive greatsword swings each hit once and finish with a heavy strike`() = arena { h ->
        for (duration in listOf(20, 22, 30)) { h.actor.attack(); h.ticks(duration) }
        assertEquals(300.0 - 12.0 * (1.0 + 1.15 + 1.85), h.combat.bossHealth(), .00001)
        h.ticks(40)
        assertEquals(300.0 - 48.0, h.combat.bossHealth(), .00001)
    }

    @Test
    fun `end recovery input buffer chains one swing without a held attack loop`() = arena { h ->
        h.actor.attack(); h.ticks(15)
        repeat(20) { h.actor.attack() }
        h.ticks(60)
        assertEquals(300.0 - 12.0 * 2.15, h.combat.bossHealth(), .00001)
    }

    @Test
    fun `server LOS permits open uphill and downhill body hits`() {
        arena { h ->
            h.combat.entities().single().teleport(Pos(8.5, 42.5, 12.5)).get(10, TimeUnit.SECONDS)
            h.actor.attack(); h.ticks(8)
            assertEquals(288.0, h.combat.bossHealth(), .00001)
        }
        arena { h ->
            h.player.teleport(Pos(8.5, 42.5, 8.5)).get(10, TimeUnit.SECONDS)
            h.actor.attack(); h.ticks(8)
            assertEquals(288.0, h.combat.bossHealth(), .00001)
        }
    }

    @Test
    fun `increased vertical range still cannot hit through a floor`() = arena { h ->
        h.combat.entities().single().teleport(Pos(8.5, 43.0, 12.5)).get(10, TimeUnit.SECONDS)
        for (x in 6..10) for (z in 7..14) h.instance.setBlock(x, 42, z, Block.STONE)
        h.actor.attack(); h.ticks(20)
        assertEquals(300.0, h.combat.bossHealth())
    }

    @Test
    fun `enhancement multiplies weapon base and armor base separately from affix values`() = arena(armorTier = 2,
        stats = CoreAffixStats(damagePercent = 20.0, attackSpeedPercent = 60.0, healthFlat = 20.0),
        weaponEnhancement = 30, armorEnhancement = 30) { h ->
        assertEquals(1.84, h.actor.attackSpeed, .00001)
        assertEquals(12.0 * 2.2, h.actor.attackDamage, .00001)
        assertEquals(228, h.actor.maxHealth)
        h.actor.attack(); h.ticks(8)
        assertEquals(300.0 - 12.0 * 2.2 * 1.2, h.combat.bossHealth(), .00001)
    }

    @Test
    fun `kill callback can return actor during direct hit without late VFX or burn`() = arena(stats = CoreAffixStats(fireFlat = 10.0)) { h ->
        h.combat.applyEffectDamage(h.combat.combatTargets().single().id, h.player, 290.0)
        h.afterKill = { h.actor.resetActions(); h.activeEncounter = null }
        h.actor.attack(); h.ticks(8)
        assertTrue(h.combat.bossDefeated)
        assertEquals(0, h.actor.activeVisualEffects)
        h.ticks(60)
        assertEquals(0, h.actor.activeVisualEffects)
    }

    @Test
    fun `burn kill callback can clear effect map reentrantly without concurrent modification`() = arena(stats = CoreAffixStats(fireFlat = 10.0)) { h ->
        h.combat.applyEffectDamage(h.combat.combatTargets().single().id, h.player, 280.0)
        h.afterKill = { h.actor.resetActions(); h.activeEncounter = null }
        h.actor.attack(); h.ticks(28)
        assertTrue(h.combat.bossDefeated)
        assertEquals(0, h.actor.activeVisualEffects)
    }

    @Test
    fun `return cancels running sword animation and does not resume it on the next map`() = arena { h ->
        h.actor.attack(); h.ticks(8)
        assertTrue(h.actor.activeVisualEffects > 0)
        h.actor.resetActions(); h.activeEncounter = null
        assertEquals(0, h.actor.activeVisualEffects)
        h.ticks(5); h.activeEncounter = h.combat; h.ticks(5)
        assertEquals(0, h.actor.activeVisualEffects)
        assertEquals(288.0, h.combat.bossHealth())
    }

    @Test
    fun `broken weapon blocks every offensive input without mana cooldown or damage but permits dodge`() = arena { h ->
        h.weaponBroken = true
        h.actor.attack()
        repeat(3) { h.actor.skill(it) }
        assertEquals(100, h.actor.mana)
        repeat(3) { assertEquals(0L, h.actor.cooldownSeconds(it)) }
        h.ticks(40)
        assertEquals(300.0, h.combat.bossHealth())
        assertEquals(0, h.actor.activeVisualEffects)
        val origin = h.player.position
        h.actor.dodge()
        assertTrue(origin.distance(h.player.position) > 0)
        h.weaponBroken = false
        h.actor.classState.gain(100.0, h.journey.job, h.journey.build); h.actor.skill(1)
        assertEquals(80, h.actor.mana)
    }

    @Test
    fun `broken armor removes tier mitigation and health bonus`() = arena(armorTier = 4, armorEnhancement = 30) { h ->
        h.armorBroken = true
        h.actor.reset()
        assertEquals(100, h.actor.maxHealth)
        h.actor.hurt(20.0)
        assertEquals(80.0, h.actor.health, .00001)
        h.armorBroken = false
        h.actor.reset()
        assertEquals(304, h.actor.maxHealth)
        h.actor.hurt(20.0)
        assertEquals(290.0, h.actor.health, .00001)
    }

    @Test fun `real Mage spell gains AP not AD and matches the tooltip preview`() {
        for (mod in listOf(CoreAffixStat.AD_FLAT, CoreAffixStat.AP_FLAT)) arena(
            stats = CoreAffixStats(additional = mapOf(mod to 20.0))) { h ->
            h.journey = CoreJourney(job = CoreClass.MAGE); h.base = CoreWeaponBase.STAFF
            val expected = 8.0 + (12 * .92 + if (mod == CoreAffixStat.AP_FLAT) 20 else 0) * 1.4
            assertEquals(expected, h.actor.skillDefinitions[0].preview(h.actor.sheet), .000001)
            h.actor.skill(0); h.ticks(5)
            assertEquals(300 - expected, h.combat.bossHealth(), .000001)
        }
    }

    @Test fun `real Mage auto still scales only with AD`() {
        for (mod in listOf(CoreAffixStat.AD_FLAT, CoreAffixStat.AP_FLAT)) arena(
            stats = CoreAffixStats(additional = mapOf(mod to 20.0))) { h ->
            h.journey = CoreJourney(job = CoreClass.MAGE); h.base = CoreWeaponBase.STAFF
            h.actor.attack(); h.ticks(4)
            assertEquals(300 - 12 * .92 - if (mod == CoreAffixStat.AD_FLAT) 20 else 0, h.combat.bossHealth(), .000001)
        }
    }

    @Test fun `player AR and MR mitigate different incoming types`() = arena(
        stats = CoreAffixStats(additional = mapOf(CoreAffixStat.AR_FLAT to 300.0))) { h ->
        h.actor.hurt(20.0, CoreDamageType.PHYSICAL)
        assertEquals(90.0, h.actor.health)
        h.actor.hurt(20.0, CoreDamageType.MAGICAL)
        assertEquals(70.0, h.actor.health)
        h.actor.hurt(Double.NaN)
        assertEquals(70.0, h.actor.health)
    }

    @Test fun `lifesteal uses overkill-capped health loss and cannot trigger recursively from burn`() = arena(
        stats = CoreAffixStats(fireFlat = 10.0, additional = mapOf(CoreAffixStat.LIFESTEAL to 50.0))) { h ->
        h.actor.hurt(60.0)
        h.combat.applyEffectDamage(h.combat.combatTargets().single().id, h.player, 296.0)
        h.actor.attack(); h.ticks(8)
        assertEquals(40.0 + 4 * .5 * .33, h.actor.health, .000001)
        h.ticks(80)
        assertEquals(40.0 + 4 * .5 * .33, h.actor.health, .000001)
    }

    @Test fun `only cast speed shortens a Mage skill while physical attack speed does not`() {
        arena(stats = CoreAffixStats(castReductionPercent = 100.0)) { h ->
            h.journey = CoreJourney(job = CoreClass.MAGE); h.base = CoreWeaponBase.STAFF
            h.actor.classState.gain(100.0, h.journey.job, h.journey.build); h.actor.skill(1); h.ticks(4); assertEquals(300.0, h.combat.bossHealth())
            h.ticks(1); assertTrue(h.combat.bossHealth() < 300)
        }
        arena(stats = CoreAffixStats(attackSpeedPercent = 100.0)) { h ->
            h.journey = CoreJourney(job = CoreClass.MAGE); h.base = CoreWeaponBase.STAFF
            h.actor.classState.gain(100.0, h.journey.job, h.journey.build); h.actor.skill(1); h.ticks(9); assertEquals(300.0, h.combat.bossHealth())
            h.ticks(1); assertTrue(h.combat.bossHealth() < 300)
        }
    }

    @Test fun `resource rejection costs no mana cooldown or enemy health`() = arena { h ->
        h.actor.skill(1);h.ticks(30)
        assertEquals(100,h.actor.mana);assertEquals(0,h.actor.cooldownRemaining(1));assertEquals(300.0,h.combat.bossHealth())
        repeat(3) { h.actor.attack();h.ticks(24) }
        h.ticks(20)
        assertTrue(h.actor.resource>=30)
        val before=h.actor.resource;h.actor.skill(1)
        assertEquals(before-30,h.actor.resource);assertEquals(80,h.actor.mana)
    }
    @Test fun `warrior timed guard reduces real incoming damage and rewards a strong counter`() = arena { h ->
        h.journey=CoreJourney(build=CoreClassBuild(first=3,second=5,third=2,fourth=0,nodes=8248))
        h.actor.reset();h.actor.skill(0);h.ticks(h.actor.skillDefinitions[0].startupTicks(h.actor.sheet))
        h.actor.hurt(50.0);assertEquals(90.0,h.actor.health)
        assertEquals(36.0,h.actor.resource)
        h.ticks(15);val before=h.combat.bossHealth();h.actor.skill(1);h.ticks(4)
        assertEquals(before-(10+12*2.8)*1.6,h.combat.bossHealth(),.00001)
    }
    @Test fun `assassin mark consumption enhances one real hit and shadow keystone resets dodge`() = arena(bossDistance=2.0) { h ->
        h.journey=CoreJourney(job=CoreClass.ASSASSIN,build=CoreClassBuild(nodes=8248));h.base=CoreWeaponBase.DAGGERS;h.actor.reset()
        h.actor.skill(0);h.ticks(18)
        val id=h.combat.combatTargets().single().id;assertTrue(h.actor.classState.marked(id,18))
        h.actor.classState.gain(40.0,h.journey.job,h.journey.build)
        h.actor.dodge();h.player.teleport(Pos(8.5,40.0,8.5)).join();val at=h.player.position
        val before=h.combat.bossHealth();h.actor.skill(1);h.ticks(4)
        assertEquals(before-(10+12*.78*2.8)*1.35,h.combat.bossHealth(),.00001)
        assertFalse(h.actor.classState.marked(id,22));h.actor.dodge();assertTrue(h.player.position.distance(at)>0)
    }
    @Test fun `mage echo adds an actual pulse and full spell damage matches the modified preview`() = arena(bossDistance=7.0,
        stats=CoreAffixStats(additional=mapOf(CoreAffixStat.MAGE_ECHO to 4.0))) { h ->
        h.journey=CoreJourney(job=CoreClass.MAGE);h.base=CoreWeaponBase.STAFF;h.actor.reset()
        h.actor.classState.gain(100.0,h.journey.job,h.journey.build)
        val spell=h.actor.skillDefinitions[2];assertEquals(4,spell.pulses)
        h.actor.skill(2);h.ticks(42)
        assertEquals(300-spell.preview(h.actor.sheet)*4,h.combat.bossHealth(),.00001)
        assertEquals(40.0,h.actor.resource)
    }
    @Test fun `healer can damage alone then spend the same faith pool on actual self recovery`() = arena { h ->
        h.journey=CoreJourney(job=CoreClass.HEALER);h.base=CoreWeaponBase.TOME;h.actor.reset()
        h.actor.hurt(60.0)
        h.actor.skill(0);h.ticks(20);assertTrue(h.combat.bossHealth()<300);assertEquals(20.0,h.actor.resource)
        h.actor.attack();h.ticks(20);h.actor.attack();h.ticks(20);assertTrue(h.actor.resource>=30)
        val before=h.actor.health;val heal=h.actor.skillDefinitions[1]
        h.actor.skill(1);h.ticks(heal.startupTicks(h.actor.sheet))
        assertEquals(before+heal.preview(h.actor.sheet),h.actor.health,.00001)
    }
    @Test fun `healing conversion splits real recovery and shield and cannot extend a stronger shield`() = arena { h ->
        h.journey=CoreJourney(job=CoreClass.HEALER,build=CoreClassBuild(nodes=1031));h.base=CoreWeaponBase.TOME;h.actor.reset()
        h.actor.hurt(60.0);h.actor.classState.gain(100.0,h.journey.job,h.journey.build)
        val value=h.actor.skillDefinitions[1].preview(h.actor.sheet);val before=h.actor.health
        h.actor.skill(1);h.ticks(6)
        assertEquals(before+value*.5,h.actor.health,.00001);assertEquals(value*.5,h.actor.shield,.00001)
        h.actor.hurt(value*.25);assertEquals(before+value*.5,h.actor.health,.00001);assertEquals(value*.25,h.actor.shield,.00001)
        h.ticks(100);assertEquals(0.0,h.actor.shield)
    }
    @Test fun `templar shielding supports nearby allies but never passes walls`() = arena { h ->
        h.journey=CoreJourney(job=CoreClass.TEMPLAR,build=CoreClassBuild(nodes=65984));h.base=CoreWeaponBase.MACE;h.actor.reset()
        val ally=h.addAlly(Pos(10.5,40.0,8.5))
        val blocked=h.addAlly(Pos(5.5,40.0,8.5))
        for(y in 40..43) h.instance.setBlock(6,y,8,Block.STONE)
        val value=h.actor.skillDefinitions[2].preview(h.actor.sheet)
        h.actor.skill(2);h.ticks(h.actor.skillDefinitions[2].startupTicks(h.actor.sheet))
        assertEquals(value,h.actor.shield,.00001);assertEquals(value*1.4,ally.shield,.00001);assertEquals(0.0,blocked.shield)
        h.actor.resetActions();assertEquals(0.0,h.actor.shield)
    }
    @Test fun `star shield releases its charge and the added protection expires`() = arena { h ->
        h.journey=CoreJourney(job=CoreClass.STARWEAVER,build=CoreClassBuild(first=6,second=1,third=2,fourth=3));h.base=CoreWeaponBase.STAFF;h.actor.reset()
        h.actor.classState.gain(3.0,h.journey.job,h.journey.build)
        val value=h.actor.skillDefinitions[0].preview(h.actor.sheet)
        h.actor.skill(0);assertEquals(0.0,h.actor.resource);h.ticks(6)
        assertEquals(value*1.45,h.actor.shield,.00001);h.ticks(120);assertEquals(0.0,h.actor.shield)
    }
    @Test fun `dodging a channeled field cancels remaining pulses but never refunds its cost`() = arena(bossDistance=7.0) { h ->
        h.journey=CoreJourney(job=CoreClass.MAGE);h.base=CoreWeaponBase.STAFF;h.actor.reset()
        h.actor.classState.gain(100.0,h.journey.job,h.journey.build)
        h.actor.skill(2);h.ticks(12);val before=h.combat.bossHealth();assertTrue(before<300)
        h.actor.dodge();h.ticks(35);assertEquals(before,h.combat.bossHealth());assertEquals(40.0,h.actor.resource)
        assertTrue(h.actor.cooldownRemaining(2)>0)
    }
    @Test fun `fourth normal skill and ultimate have real runtime cooldowns and separate input slots`() = arena { h ->
        h.actor.skill(3);h.ticks(15);assertTrue(h.actor.cooldownRemaining(3)>0)
        h.actor.classState.gain(100.0,h.journey.job,h.journey.build)
        h.actor.skill(4);assertTrue(h.actor.cooldownRemaining(4)>0);assertEquals(20.0,h.actor.resource)
        h.ticks(40);assertTrue(h.combat.bossHealth()<300)
    }
    @Test fun `new melee classes have shorter dagger impact and full height mace contact`() {
        arena(bossDistance=2.5) { h ->
            h.journey=CoreJourney(job=CoreClass.ASSASSIN);h.base=CoreWeaponBase.DAGGERS
            h.actor.attack();h.ticks(4);assertTrue(h.combat.bossHealth()<300)
        }
        arena { h ->
            h.journey=CoreJourney(job=CoreClass.TEMPLAR);h.base=CoreWeaponBase.MACE
            h.actor.attack();h.ticks(10);assertTrue(h.combat.bossHealth()<300)
        }
    }

    @Test fun `templar taunt survives ally damage and expires instead of permanently stealing aggro`() = arena { h ->
        val ally=h.addAlly(Pos(10.5,40.0,8.5));val id=h.combat.combatTargets().single().id
        assertTrue(h.combat.taunt(id,h.player))
        h.combat.applyProjectileDamage(id,ally.player,1.0)
        assertEquals(h.player.uuid,h.combat.currentTargetId(id))
        h.combat.tick(System.currentTimeMillis()+2100)
        h.combat.applyProjectileDamage(id,ally.player,1.0)
        assertEquals(ally.player.uuid,h.combat.currentTargetId(id))
    }
    @Test fun `boss pull cannot bypass seals move a boss or interrupt repeatedly within immunity window`() = arena { h ->
        val id=h.combat.combatTargets().single().id;val at=h.combat.positionOf(id)
        h.combat.sealBoss(true);assertFalse(h.combat.pull(id,h.player));assertFalse(h.combat.taunt(id,h.player))
        h.combat.sealBoss(false);assertTrue(h.combat.pull(id,h.player));assertEquals(at,h.combat.positionOf(id))
        assertFalse(h.combat.pull(id,h.player));h.combat.tick(System.currentTimeMillis()+6100)
        assertTrue(h.combat.pull(id,h.player))
    }
    @Test fun `shield keystone enhances the next normal mace attack once`() = arena { h ->
        h.journey=CoreJourney(job=CoreClass.TEMPLAR,build=CoreClassBuild(nodes=1031));h.base=CoreWeaponBase.MACE;h.actor.reset()
        h.actor.skill(2);h.ticks(20);h.actor.attack();h.ticks(10)
        assertEquals(300-12*.95*1.08*1.35,h.combat.bossHealth(),.00001)
        assertEquals(-1,h.actor.classState.counterUntil)
    }
    @Test fun `poison and fire coexist as separate nonrecursive effects`() = arena(bossDistance=2.0,stats=CoreAffixStats(fireFlat=10.0)) { h ->
        h.journey=CoreJourney(job=CoreClass.ASSASSIN,build=CoreClassBuild(first=4,second=1,third=2,fourth=3));h.base=CoreWeaponBase.DAGGERS;h.actor.reset()
        h.actor.skill(0);h.ticks(h.actor.skillDefinitions[0].startupTicks(h.actor.sheet))
        val before=h.combat.bossHealth();h.ticks(20)
        assertEquals(before-(3+12*.78*.8)*.3-3.0,h.combat.bossHealth(),.00001)
    }
    @Test fun `ground field follows a visible aimed enemy beyond the old fixed seven block centre`() = arena(bossDistance=15.0) { h ->
        h.journey=CoreJourney(job=CoreClass.MAGE);h.base=CoreWeaponBase.STAFF;h.actor.reset()
        h.actor.classState.gain(100.0,h.journey.job,h.journey.build);h.actor.skill(2);h.ticks(12)
        assertTrue(h.combat.bossHealth()<300)
    }

    private class Harness(bossDistance: Double, armorTier: Int, stats: CoreAffixStats, roll: Double,
        weaponEnhancement: Int, armorEnhancement: Int) : AutoCloseable {
        val instance = MinecraftServer.getInstanceManager().createInstanceContainer()
        val player: Player
        val combat: QuestEncounterCombat
        var activeEncounter: QuestEncounterCombat? = null
        val actor: CorePlayerCombat
        var weaponBroken = false
        var armorBroken = false
        var journey = CoreJourney()
        var base = CoreWeaponBase.STANDARD
        var deaths = 0
        val incomingHits = mutableListOf<Double>()
        var afterKill: () -> Unit = {}
        val otherActors = mutableListOf<CorePlayerCombat>()
        fun addAlly(at:Pos):CorePlayerCombat {
            val c=MemoryConnection();c.setClientState(ConnectionState.PLAY);c.setServerState(ConnectionState.PLAY)
            val p=Player(c,GameProfile(UUID.randomUUID(),"AllyTest"));c.player=p;p.gameMode=GameMode.ADVENTURE
            p.setInstance(instance,at).get(10,TimeUnit.SECONDS)
            return CorePlayerCombat(p,{1},{1},{activeEncounter}) {}.also { it.reset();otherActors+=it }
        }

        init {
            instance.viewDistance(2)
            instance.setGenerator { unit -> unit.modifier().fillHeight(0, 40, Block.STONE) }
            // Player's effective range includes one chunk beyond the configured view distance.
            for (x in -3..3) for (z in -3..3) instance.loadChunk(x, z).get(10, TimeUnit.SECONDS)
            val connection = MemoryConnection()
            connection.setClientState(ConnectionState.PLAY)
            connection.setServerState(ConnectionState.PLAY)
            player = Player(connection, GameProfile(UUID.randomUUID(), "CombatTest"))
            connection.player = player
            player.gameMode = GameMode.ADVENTURE
            player.getAttribute(Attribute.MAX_HEALTH).baseValue = 100.0 + (armorTier - 1) * 30.0
            player.setInstance(instance, Pos(8.5, 40.0, 8.5)).get(10, TimeUnit.SECONDS)
            actor = CorePlayerCombat(player, { 1 }, { armorTier }, { activeEncounter }, statSource = { stats }, criticalRoll = { roll },
                weaponEnhancement = { weaponEnhancement }, armorEnhancement = { armorEnhancement },
                weaponBroken = { weaponBroken }, armorBroken = { armorBroken }, journey = { journey }, weaponBase = { base }, allies = {otherActors}) { deaths++ }
            combat = QuestEncounterCombat(instance, 1, emptyList(), Pos(8.5, 40.0, 8.5 + bossDistance),
                onMobDefeated = { _, _ -> afterKill() },
                damagePlayer = { _, amount -> incomingHits += amount; actor.hurt(amount) },
                canTarget = { (it === player && !actor.defeated) || otherActors.any { a -> a.player===it && !a.defeated } })
            activeEncounter = combat
            actor.reset()
        }

        fun ticks(count: Int) = repeat(count) { actor.tick() }

        override fun close() {
            actor.resetActions();otherActors.forEach { it.resetActions();it.player.remove() }
            combat.dispose()
            player.remove()
            MinecraftServer.getInstanceManager().unregisterInstance(instance)
        }
    }

    private class MemoryConnection : PlayerConnection() {
        override fun sendPacket(packet: SendablePacket) = Unit
        override fun getRemoteAddress(): SocketAddress = InetSocketAddress("127.0.0.1", 0)
    }
}
