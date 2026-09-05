package dev.projects.server.coreloop.adventure

import dev.projects.server.coreloop.CoreMmoTuning
import dev.projects.server.mob.*
import net.kyori.adventure.sound.Sound
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.minestom.server.coordinate.Pos
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.Entity
import net.minestom.server.entity.Player
import net.minestom.server.instance.InstanceContainer
import net.minestom.server.instance.block.Block
import java.util.UUID
import kotlin.math.pow
import kotlin.random.Random

/** Three authored boss encounters, with mandatory HP gates and explicit server-shaped floor mechanics. */
internal class DungeonBossMechanics(
    private val instance: InstanceContainer, private val room: DungeonRoom, private val tier: Int, private val ascension: Int,
    private val combat: QuestEncounterCombat, private val players: () -> List<Player>, private val hurt: (Player, Double) -> Unit,
) {
    private data class Pulse(val id: UUID, val frame: MobAbilityFrame, val hitsAt: Long)
    private val ground = MobGroundTelegraph(instance)
    private val markers = mutableListOf<DungeonMarker>()
    private val pulses = mutableListOf<Pulse>()
    private var guardians = emptySet<UUID>()
    private var sequence = emptyList<Int>()
    private val activated = mutableSetOf<Int>()
    private var nextPulseAt = 0L
    private var deadline = 0L
    private var cycles = 0
    private var disposed = false
    private var intermission = false
    var gate = 0
        private set
    val active get() = intermission
    val title get() = if (!intermission) "第${gate + 1}形態" else when (room.theme) {
        DungeonTheme.EMBER -> "機構停止 ${activated.size}/${markers.size}・護衛${guardians.count(combat::isAlive)}"
        DungeonTheme.TIDE -> if (markers.isEmpty()) "満潮と干潮・赤い床を避ける" else "碑文 ${sequence.joinToString("→") { (it + 1).toString() }}・${activated.size}/${markers.size}"
        DungeonTheme.ASTRAL -> if (markers.isEmpty()) "星落とし→光の下へ集合" else "星核停止 ${activated.size}/${markers.size}・護衛${guardians.count(combat::isAlive)}"
    }
    internal fun markerEntities() = markers.map { it.interaction }
    internal val groundCount get() = ground.displayCount
    init { combat.gateBossHealth(GATES[0]) }

    fun tick(now: Long) {
        if (disposed || combat.bossDefeated) return
        ground.tick(now)
        val activePlayers = players()
        if (activePlayers.isEmpty()) return
        if (!intermission && gate < GATES.size && combat.bossHealth() <= combat.bossMaxHealth() * GATES[gate] + .001) begin(now)
        if (!intermission) return
        check(markers.none { it.failed }) { "深殿の機構を表示できません" }
        pulses.toList().forEach { pulse ->
            if (!ground.show(pulse.id, pulse.frame, now >= pulse.hitsAt, now)) {
                // No visible surface means NO damage. Retry a fresh, full warning later.
                ground.clear(pulse.id); pulses.remove(pulse); cycles = (cycles - 1).coerceAtLeast(0); nextPulseAt = now + 500
            } else if (now >= pulse.hitsAt) {
                pulses.remove(pulse) // terminal before damage may re-enter defeat/exit callbacks
                activePlayers.filter { pulse.frame.ability.shape.contains(pulse.frame.origin, pulse.frame.facing, it.position) }
                    .forEach { if (!disposed) hurt(it, pulse.frame.ability.damage) }
            }
        }
        if (disposed) return
        if (now >= nextPulseAt && pulses.isEmpty() && (cycles < 3 || markers.isNotEmpty())) {
            val before = pulses.size
            emitCycle(now, activePlayers)
            if (pulses.size > before) cycles++
            nextPulseAt = now + if (pulses.size > before) 3300L else 500L
        }
        val sealsDone = markers.isEmpty() || activated.size == markers.size
        val patternsDone = markers.isNotEmpty() || cycles >= 3
        if (sealsDone && patternsDone && guardians.none(combat::isAlive) && pulses.isEmpty()) { finish(now); return }
        if (now >= deadline && pulses.isEmpty()) {
            announce("機構が暴走！赤い床の外へ。機構停止を続けよう")
            pulse(MobAttackShape.Ring(12.0), room.center, now, 2000, 45.0)
            deadline = now + CoreMmoTuning.balance.bossMechanicSeconds * 1000L
        }
    }

    private fun begin(now: Long) {
        intermission = true; cycles = 0; activated.clear()
        combat.sealBoss(true)
        nextPulseAt = now + 1800
        deadline = now + CoreMmoTuning.balance.bossMechanicSeconds * 1000L
        val needsSeals = room.theme == DungeonTheme.EMBER || (room.theme == DungeonTheme.TIDE && gate != 1) || (room.theme == DungeonTheme.ASTRAL && gate == 2)
        if (needsSeals) {
            val positions = listOf(room.center.add(-8.0, 0.0, -4.0), room.center.add(8.0, 0.0, -4.0), room.center.add(0.0, 0.0, 9.0))
            positions.forEachIndexed { i, p -> markers += DungeonMarker(instance, p, "${i + 1} 封印・右クリック", if (room.theme == DungeonTheme.EMBER) Block.COPPER_BLOCK else Block.AMETHYST_BLOCK) }
            sequence = if (room.theme == DungeonTheme.TIDE) markers.indices.shuffled(Random(room.encounterSeed xor gate.toLong())) else emptyList()
        }
        if ((room.theme == DungeonTheme.EMBER && gate >= 1) || (room.theme == DungeonTheme.ASTRAL && gate == 2)) {
            guardians = combat.spawnEncounter(QuestCombatEncounter(listOf(room.center.add(-5.0, 0.0, 4.0), room.center.add(5.0, 0.0, 4.0)),
                listOf(QuestMobArchetype.SHIELD_GUARD, QuestMobArchetype.RIFT_CASTER)))
        }
        announce("障壁展開：$title。ボスへの攻撃は通らない")
    }

    fun interact(player: Player, entity: Entity, now: Long): Boolean {
        val index = markers.indexOfFirst { it.interaction === entity }
        if (index < 0) return false
        if (disposed || !intermission || player !in players() || player.instance !== instance || player.position.distanceSquared(markers[index].position) > 4.5 * 4.5) return true
        if (index in activated) return true
        if (sequence.isNotEmpty() && sequence[activated.size] != index) {
            activated.clear(); markers.forEachIndexed { i, m -> m.label("${i + 1} 碑文・順番に起動") }
            announce("順序が違う！${sequence.joinToString("→") { (it + 1).toString() }} の順で起動")
            return true
        }
        activated += index; markers[index].label("${index + 1} 停止済み", NamedTextColor.GREEN)
        player.playSound(Sound.sound(net.minestom.server.sound.SoundEvent.BLOCK_RESPAWN_ANCHOR_CHARGE, Sound.Source.HOSTILE, .6f, 1.4f))
        return true
    }

    private fun emitCycle(now: Long, alive: List<Player>) {
        when (room.theme) {
            DungeonTheme.EMBER -> pulse(if (gate == 0) MobAttackShape.Slam(32.0, 1.6) else MobAttackShape.Cross(19.0, 1.5),
                if (gate == 0) room.center.add(0.0, 0.0, -16.0) else room.center, now, 1700, 25.0,
                if (cycles % 2 == 0) Vec(0.0, 0.0, 1.0) else Vec(.707, 0.0, .707))
            DungeonTheme.TIDE -> {
                val outside = cycles % 2 == 0
                announce(if (outside) "満潮・中央の明るい円へ" else "干潮・中央から離れろ")
                pulse(if (outside) MobAttackShape.Ring(30.0, 5.5) else MobAttackShape.Ring(5.5), room.center, now, 1900, 28.0)
            }
            DungeonTheme.ASTRAL -> {
                if (cycles % 2 == 0) {
                    announce("星落とし・仲間と離れて赤い床から退避")
                    alive.take(4).forEach { pulse(MobAttackShape.Ring(if (ascension >= 8) 4.0 else 3.2), it.position.withY(40.0), now, 2100, 28.0) }
                } else {
                    val refuge = room.center.add(if (cycles % 4 == 1) -7.0 else 7.0, 0.0, 0.0)
                    announce("星の庇護・赤くない小さな円へ集合")
                    pulse(MobAttackShape.Ring(40.0, 3.5), refuge, now, 2300, 35.0)
                }
            }
        }
        // Ascension adds a second timed spatial problem, not an invisible stat-only increase.
        if (ascension >= 12 && room.theme == DungeonTheme.TIDE) pulse(MobAttackShape.Slam(32.0, .9), room.center.add(-16.0, 0.0, 0.0), now, 2500, 18.0, Vec(1.0, 0.0, 0.0))
    }

    private fun pulse(shape: MobAttackShape, at: Pos, now: Long, warning: Long, damage: Double, facing: Vec = Vec(0.0, 0.0, 1.0)) {
        if (disposed || pulses.size >= 5) return
        val ability = MobAbility("dungeon-mechanic", "赤い床から退避", shape, 50.0,
            damage * 1.65.pow(tier - 1) * (1 + ascension * CoreMmoTuning.balance.dungeonDamagePerAscension / 100.0), warning, 0, 0, 0)
        val frame = MobAbilityFrame(ability, at, facing, MobAbilityPhase.LOCKED, now)
        val id = UUID.randomUUID()
        if (ground.show(id, frame, false, now)) pulses += Pulse(id, frame, now + warning)
    }
    private fun finish(now: Long) {
        markers.forEach { it.dispose() }; markers.clear(); activated.clear()
        combat.removeEncounter(guardians); guardians = emptySet()
        gate++; intermission = false
        combat.gateBossHealth(GATES.getOrElse(gate) { 0.0 }); combat.sealBoss(false); combat.staggerBoss(now + 3000)
        announce("障壁崩壊！3秒間の攻撃機会・第${gate + 1}形態へ")
    }
    private fun announce(text: String) { players().forEach { it.sendMessage(Component.text(text, NamedTextColor.GOLD)) } }
    fun dispose() {
        disposed = true; ground.dispose(); pulses.clear(); markers.forEach { it.dispose() }; markers.clear()
        combat.removeEncounter(guardians); guardians = emptySet()
    }
    companion object { val GATES = listOf(.70, .40, .15) }
}
