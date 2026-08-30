package dev.projects.server.experiment.swarm

import dev.projects.server.experiment.swarm.combat.CairnbackAttack
import dev.projects.server.experiment.swarm.combat.CairnbackConfig
import dev.projects.server.experiment.swarm.combat.CairnbackEncounter
import dev.projects.server.experiment.swarm.combat.CairnbackEvent
import dev.projects.server.experiment.swarm.combat.CairnbackLifecycle
import dev.projects.server.experiment.swarm.combat.CairnbackRewardSink
import dev.projects.server.experiment.swarm.loop.FileSwarmSnapshotStore
import dev.projects.server.experiment.swarm.loop.FittingState
import dev.projects.server.experiment.swarm.loop.PlayerLoadResult
import dev.projects.server.experiment.swarm.loop.ProcurementRoute
import dev.projects.server.experiment.swarm.loop.QuestStage
import dev.projects.server.experiment.swarm.loop.SupplierRecord
import dev.projects.server.experiment.swarm.loop.SwarmLoopService
import dev.projects.server.experiment.swarm.loop.SwarmResource
import dev.projects.server.experiment.swarm.loop.TidehookModChoice
import java.nio.file.Files
import java.util.UUID
import kotlin.math.ceil
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SwarmCompleteJourneyIntegrationTest {
    @Test
    fun `every route reaches complete and survives restart with mandatory training`() {
        ProcurementRoute.entries.forEach { route ->
            val directory = Files.createTempDirectory("swarm-complete-${route.name.lowercase()}")
            val playerId = UUID.randomUUID()
            var loop = SwarmLoopService(FileSwarmSnapshotStore(directory))
            loop.loadPlayer(playerId)
            loop.selectRoute(playerId, route)

            when (route) {
                ProcurementRoute.HUNTER -> {
                    repeat(4) { loop.grantBrineclawCord(playerId) }
                    loop.sell(playerId, SwarmResource.CORD, 2)
                    loop.buy(playerId, SwarmResource.ORE, 2)
                }
                ProcurementRoute.GATHERER -> {
                    repeat(4) { nodeIndex -> loop.grantHarvestedOre(playerId, nodeIndex) }
                    loop.sell(playerId, SwarmResource.ORE, 2)
                    repeat(3) { loop.grantBrineclawCord(playerId) }
                }
                ProcurementRoute.SUPPLIER -> {
                    loop.inspectSupplierRecord(playerId, SupplierRecord.TIDAL_FLAT)
                    loop.inspectSupplierRecord(playerId, SupplierRecord.QUARRY)
                    loop.claimSupplierCommission(playerId)
                    repeat(3) { loop.grantBrineclawCord(playerId) }
                    loop.buy(playerId, SwarmResource.ORE, 2)
                }
            }

            assertTrue(loop.snapshot(playerId)!!.routeObjectiveComplete)
            assertTrue(loop.snapshot(playerId)!!.brineclawTrainingComplete)
            assertTrue(loop.craftAndInstallCoupler(playerId).accepted)
            assertTrue(loop.preparedForEncounter(playerId))
            assertTrue(loop.claimBossVictory(playerId).accepted)
            assertTrue(loop.installMod(playerId, TidehookModChoice.BARBED_POINT).accepted)
            assertTrue(loop.reportCompletion(playerId).accepted)

            loop.unloadPlayer(playerId)
            loop = SwarmLoopService(FileSwarmSnapshotStore(directory))
            val reloaded = assertIs<PlayerLoadResult.Ready>(loop.loadPlayer(playerId)).snapshot
            assertEquals(QuestStage.COMPLETE, reloaded.questStage)
            assertEquals(FittingState.MOD_BARBED, reloaded.fittingState)
            assertTrue(reloaded.brineclawTrainingComplete)
        }
    }

    @Test
    fun `cairnback pillar exposure supports an eligible authoritative victory`() {
        val playerId = UUID.randomUUID()
        var victory: CairnbackEvent.Victory? = null
        val encounter = CairnbackEncounter(
            config = CairnbackConfig(baseMaxHealth = SwarmSliceBalance.CAIRNBACK_BASE_HEALTH),
            rewardSink = CairnbackRewardSink { victory = it },
        )
        assertTrue(encounter.start(listOf(playerId), tick = 0).accepted)

        val sweep = encounter.beginNextAttack(tick = 0, targetId = playerId)!!
        assertEquals(CairnbackAttack.SWEEP, sweep.telegraph.attack)
        sweep.scheduled.sortedBy { it.dueTick }.forEach { encounter.executeScheduled(it, it.dueTick) }

        val charge = encounter.beginNextAttack(tick = 60, targetId = playerId)!!
        assertEquals(CairnbackAttack.CHARGE, charge.telegraph.attack)
        val activation = charge.scheduled.first()
        encounter.executeScheduled(activation, activation.dueTick)
        encounter.collideChargeWithPillar(powered = true, tick = activation.dueTick)
        assertTrue(encounter.exposed)

        val exposedDamage = SwarmSliceBalance.cairnbackThrustDamage(exposed = true, modMultiplier = 1.0)
        val hits = ceil(encounter.maxHealth.toDouble() / exposedDamage).toInt()
        repeat(hits) { index -> encounter.applyTidehookHit(playerId, index + 1L, exposedDamage) }

        assertEquals(CairnbackLifecycle.VICTORY, encounter.lifecycle)
        assertEquals(setOf(playerId), victory?.eligiblePlayers)
        assertTrue(hits >= 3)
    }
}
