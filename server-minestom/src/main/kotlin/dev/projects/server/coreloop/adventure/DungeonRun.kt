package dev.projects.server.coreloop.adventure

import dev.projects.server.coreloop.*
import dev.projects.server.mob.*
import net.kyori.adventure.bossbar.BossBar
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.Entity
import net.minestom.server.entity.GameMode
import net.minestom.server.entity.Player
import net.minestom.server.instance.block.Block
import java.util.UUID
import java.util.concurrent.CompletableFuture

internal interface DungeonRunHost {
    fun nowMillis(): Long = System.currentTimeMillis()
    fun connected(player: Player): Boolean
    fun hurt(player: Player, damage: Double)
    fun resetActions(player: Player)
    fun revive(player: Player, fraction: Double)
    fun reward(player: Player, action: CoreAction.DungeonReward): CompletableFuture<CoreTransactionResult>
    fun showRunMenu(player: Player)
    fun schedule(action: () -> Unit) { MinecraftServer.getSchedulerManager().scheduleNextTick { action() } }
}

enum class DungeonRunPhase { TRANSFERRING, FIGHTING, SAVING, CHOOSING, COMPLETE, FAILED, CLOSED }
data class DungeonRunView(val runId: UUID, val leader: UUID, val room: DungeonRoom, val stages: Int, val ascension: Int,
    val phase: DungeonRunPhase, val objective: String, val revives: Int, val choices: List<DungeonRoom>,
    val boonOffers: List<DungeonBoon>, val chosen: Boolean, val blessings: Map<DungeonBoon, Int>, val waitingFor: Int)

/** One dungeon instance, one tick owner, many participants. No per-player duplicate mob ticks or public loot. */
internal class DungeonRun(val id: UUID, val world: DungeonWorld, participants: List<Player>, private val host: DungeonRunHost) {
    private val members = participants.associateByTo(linkedMapOf()) { it.uuid }
    private val downed = mutableMapOf<UUID, DungeonMarker>()
    private val reviving = mutableMapOf<UUID, Pair<UUID, Long>>()
    private val markers = mutableListOf<DungeonMarker>()
    private val seals = mutableMapOf<Int, Pair<UUID, Long>>()
    private val completedSeals = mutableSetOf<Int>()
    private val blessings = DungeonBlessings(world.plan.seed)
    private var mechanic: DungeonBossMechanics? = null
    private var enemies = emptySet<UUID>()
    private var wave = 0
    private var nextWaveAt = 0L
    private var wipeAt: Long? = null
    private var lastTick = Long.MIN_VALUE
    private var epoch = 0
    private var savePending = false
    private var retryAt = 0L
    private val rewarded = mutableSetOf<UUID>()
    private val bar = BossBar.bossBar(Component.text("星環の深殿"), 1f, BossBar.Color.PURPLE, BossBar.Overlay.PROGRESS)
    @Volatile var phase = DungeonRunPhase.TRANSFERRING
        private set
    @Volatile var room = world.plan.choices(1).first()
        private set
    @Volatile var combat: QuestEncounterCombat? = null
        private set
    @Volatile var revives = CoreMmoTuning.balance.dungeonRevives
        private set
    @get:Synchronized val leader get() = members.keys.firstOrNull()
    @get:Synchronized val playerIds get() = members.keys.toSet()
    val instance get() = world.instance
    val terminal get() = phase in setOf(DungeonRunPhase.FAILED, DungeonRunPhase.CLOSED, DungeonRunPhase.COMPLETE)
    @Synchronized fun owns(player: Player) = members[player.uuid] === player
    @Synchronized fun canFight(player: Player) = phase == DungeonRunPhase.FIGHTING && owns(player) && host.connected(player) && player.uuid !in downed && player.instance === instance
    private fun living() = members.values.filter { canFight(it) }
    @Synchronized fun stats(player: UUID, base: CoreAffixStats) = blessings.stats(player, base)
    @Synchronized fun view(player: Player): DungeonRunView? {
        if (!owns(player)) return null
        val chosen = blessings.hasChosen(player.uuid, room.stage)
        return DungeonRunView(id, leader ?: player.uuid, room, world.plan.stages, world.plan.ascension, phase, objective(), revives,
            if (phase == DungeonRunPhase.CHOOSING) world.plan.next(room) else emptyList(),
            if (phase == DungeonRunPhase.CHOOSING && !chosen) blessings.offers(player.uuid, room.stage) else emptyList(), chosen, blessings.bonuses(player.uuid),
            members.keys.count { !blessings.hasChosen(it, room.stage) })
    }
    internal fun interactionMarkers(): List<Entity> = markers.map { it.interaction } + downed.values.map { it.interaction } + (mechanic?.markerEntities().orEmpty())
    internal fun bossMechanics() = mechanic
    @Synchronized fun start(now: Long) { check(phase == DungeonRunPhase.TRANSFERRING); beginCombat(now) }

    private fun beginCombat(now: Long) {
        if (members.isEmpty() || phase == DungeonRunPhase.CLOSED) return
        phase = DungeonRunPhase.FIGHTING; nextWaveAt = now + 1400; wave = 0; wipeAt = null; rewarded.clear()
        completedSeals.clear(); seals.clear()
        val b = CoreMmoTuning.balance
        val c = QuestEncounterCombat(instance, world.plan.tier, emptyList(), room.center,
            onMobDefeated = { _, _ -> }, damagePlayer = host::hurt, canTarget = ::canFight, contentSeed = room.encounterSeed,
            explicitBossArchetype = when (room.theme) { DungeonTheme.EMBER -> QuestMobArchetype.FORGE_SENTINEL; DungeonTheme.TIDE -> QuestMobArchetype.TIDE_ARCHIVIST; DungeonTheme.ASTRAL -> QuestMobArchetype.ECLIPSE_REGENT },
            spawnBoss = room.kind == DungeonRoomKind.BOSS,
            healthMultiplier = 1 + (members.size - 1) * b.dungeonHealthPerPlayer / 100.0 + world.plan.ascension * b.dungeonHealthPerAscension / 100.0,
            damageMultiplier = 1 + world.plan.ascension * b.dungeonDamagePerAscension / 100.0)
        combat = c
        if (room.kind == DungeonRoomKind.BOSS) mechanic = DungeonBossMechanics(instance, room, world.plan.tier, world.plan.ascension, c, ::living, host::hurt)
        if (room.kind == DungeonRoomKind.SEALS) listOf(room.center.add(-9.0, 0.0, 2.0), room.center.add(9.0, 0.0, 2.0), room.center.add(0.0, 0.0, 10.0)).forEachIndexed { i, p ->
            markers += DungeonMarker(instance, p, "封印${i + 1}・右クリック後5秒近くに留まる", Block.CRYING_OBSIDIAN)
        }
        if (room.kind == DungeonRoomKind.REST) {
            members.values.forEach { host.revive(it, 1.0) }
            markers += DungeonMarker(instance, room.center.add(0.0, 0.0, 2.0), "憩いの泉・回復完了", Block.SEA_LANTERN)
        }
        announce("${room.theme.displayName} / ${room.stage}の間：${room.kind.description}")
    }

    @Synchronized fun tick(now: Long) {
        if (phase == DungeonRunPhase.CLOSED || now <= lastTick) return
        lastTick = now
        if (phase == DungeonRunPhase.SAVING) { if (!savePending && now >= retryAt) saveRoom(); return }
        if (phase != DungeonRunPhase.FIGHTING) return
        check(markers.none { it.failed }) { "封印の機構を表示できません" }
        // Spectators cannot scout future stages or become valid targets; everybody stays in this generated room.
        members.values.filter { it.instance === instance && !DungeonWorld.safe(room, it.position) }.forEach { it.teleport(room.spawn) }
        tickRevives(now)
        if (living().isEmpty()) {
            combat?.stopActionsForReturn(now)
            if (wipeAt == null) { wipeAt = now + 6000; announce(if (revives > 0) "全滅…6秒後にこの部屋を再挑戦（復活残り$revives）" else "全滅しました。獲得済み報酬を持って港へ帰れます") }
            if (now >= wipeAt!! && phase == DungeonRunPhase.FIGHTING) {
                if (revives > 0) { revives--; retryRoom() } else { phase = DungeonRunPhase.FAILED; clearRoom(); members.values.forEach(host::showRunMenu) }
            }
            return
        }
        wipeAt = null
        val c = combat ?: return
        c.tick(now)
        if (phase != DungeonRunPhase.FIGHTING) return
        mechanic?.tick(now)
        if (phase != DungeonRunPhase.FIGHTING) return
        tickSeals(now)
        val requiredWaves = when (room.kind) { DungeonRoomKind.BOSS, DungeonRoomKind.REST -> 0; DungeonRoomKind.SEALS -> 1; DungeonRoomKind.TREASURE -> 3; else -> 2 }
        if (wave < requiredWaves && enemies.none(c::isAlive) && now >= nextWaveAt) {
            wave++
            val types = if (room.kind == DungeonRoomKind.ELITE || (world.plan.ascension >= 4 && wave == requiredWaves))
                listOf(QuestMobArchetype.ELITE_BRUTE, QuestMobArchetype.SHIELD_GUARD, QuestMobArchetype.RIFT_CASTER)
            else QuestMobContent.composition(room.encounterSeed, wave, 3)
            enemies = c.spawnEncounter(QuestCombatEncounter(listOf(room.center.add(-6.0, 0.0, 0.0), room.center.add(6.0, 0.0, 0.0), room.center.add(0.0, 0.0, 6.0)), types))
            nextWaveAt = now + 2200
            announce("第${wave}波・${room.kind.displayName}")
        }
        val complete = when (room.kind) {
            DungeonRoomKind.BOSS -> c.bossDefeated
            DungeonRoomKind.REST -> now >= nextWaveAt
            else -> wave >= requiredWaves && enemies.none(c::isAlive) && (room.kind != DungeonRoomKind.SEALS || completedSeals.size == 3)
        }
        if (complete && living().isNotEmpty()) { phase = DungeonRunPhase.SAVING; c.stopActionsForReturn(now); saveRoom() }
        members.values.forEach { p ->
            bar.name(Component.text(objective(), NamedTextColor.GOLD))
            bar.progress(if (room.kind == DungeonRoomKind.BOSS) (c.bossHealth() / c.bossMaxHealth()).toFloat().coerceIn(0f, 1f) else room.stage.toFloat() / world.plan.stages)
            p.showBossBar(bar)
        }
    }

    private fun tickSeals(now: Long) {
        if (room.kind != DungeonRoomKind.SEALS) return
        seals.toMap().forEach { (index, reading) ->
            val p = members[reading.first]
            if (p == null || !canFight(p) || p.position.distanceSquared(markers[index].position) > 3.5 * 3.5) {
                seals.remove(index); markers[index].label("封印${index + 1}・右クリックで再開")
            } else if (now - reading.second >= 5000) {
                completedSeals += index; seals.remove(index); markers[index].label("封印${index + 1}・解除済み", NamedTextColor.GREEN)
            } else markers[index].label("封印${index + 1}・解読 ${(now - reading.second) / 1000 + 1}/5")
        }
    }

    @Synchronized fun interact(player: Player, target: Entity, now: Long): Boolean {
        if (!owns(player) || player.instance !== instance) return false
        if (mechanic?.interact(player, target, now) == true) return true
        val corpse = downed.entries.firstOrNull { it.value.interaction === target }
        if (corpse != null) {
            if (canFight(player) && revives > 0 && player.position.distanceSquared(corpse.value.position) <= 4.5 * 4.5) {
                reviving.putIfAbsent(corpse.key, player.uuid to now); host.resetActions(player)
                player.sendMessage(Component.text("3秒間そばに留まって仲間を起こす。復活残り$revives", NamedTextColor.GREEN))
            }
            return true
        }
        val index = markers.indexOfFirst { it.interaction === target }
        if (index < 0) return false
        if (player.position.distanceSquared(markers[index].position) > 4.5 * 4.5) return true
        if (phase == DungeonRunPhase.CHOOSING || phase == DungeonRunPhase.COMPLETE) host.showRunMenu(player)
        else if (room.kind == DungeonRoomKind.SEALS && canFight(player) && index !in completedSeals && seals.values.none { it.first == player.uuid }) seals.putIfAbsent(index, player.uuid to now)
        return true
    }

    @Synchronized fun defeated(player: Player) {
        if (!owns(player) || phase != DungeonRunPhase.FIGHTING || player.uuid in downed) return
        host.resetActions(player)
        downed[player.uuid] = DungeonMarker(instance, player.position, "${player.username}・右クリックで救助", Block.SOUL_LANTERN)
        player.gameMode = GameMode.SPECTATOR
        announce("${player.username}が倒れた。仲間を右クリックして救助、またはこの部屋を突破しよう")
    }
    private fun tickRevives(now: Long) {
        reviving.toMap().forEach { (fallenId, channel) ->
            val p = members[channel.first]; val corpse = downed[fallenId]
            if (p == null || corpse == null || !canFight(p) || p.position.distanceSquared(corpse.position) > 3.5 * 3.5) reviving.remove(fallenId)
            else if (now - channel.second >= 3000) {
                reviving.remove(fallenId)
                if (revives > 0) {
                    revives--; downed.remove(fallenId)?.dispose()
                    members[fallenId]?.let { it.gameMode = GameMode.ADVENTURE; it.teleport(room.spawn); host.revive(it, .5) }
                }
            }
        }
    }

    private fun saveRoom() {
        if (phase != DungeonRunPhase.SAVING || savePending) return
        savePending = true
        val token = epoch
        val claims = members.values.filter { it.uuid !in rewarded }.associateWith { p ->
            host.reward(p, CoreAction.DungeonReward(id, room.stage, room.kind == DungeonRoomKind.BOSS, room.kind == DungeonRoomKind.TREASURE))
        }
        CompletableFuture.allOf(*claims.values.toTypedArray()).whenComplete { _, failure -> later {
            if (phase != DungeonRunPhase.SAVING || token != epoch) return@later
            savePending = false
            claims.forEach { (p, f) -> if (!f.isCompletedExceptionally && f.getNow(null)?.successful == true) rewarded += p.uuid }
            if (failure != null || members.keys.any { it !in rewarded }) { retryAt = lastTick + 2000; announce("報酬の保存を再試行しています。確定まで次の部屋へ進めません"); return@later }
            clearRoom()
            downed.values.forEach { it.dispose() }; downed.clear(); reviving.clear()
            members.values.forEach { p -> if (p.gameMode == GameMode.SPECTATOR) { p.gameMode = GameMode.ADVENTURE; p.teleport(room.spawn); host.revive(p, .5) } }
            phase = if (room.stage == world.plan.stages) DungeonRunPhase.COMPLETE else DungeonRunPhase.CHOOSING
            markers += DungeonMarker(instance, room.altar, if (phase == DungeonRunPhase.COMPLETE) "踏破・右クリックで帰還" else "加護と次の道・右クリック", Block.ENCHANTING_TABLE)
            announce(if (phase == DungeonRunPhase.COMPLETE) "星環の深殿を踏破！報酬と次の深度を保存しました" else "突破！自分の加護を選ぼう。全員が選んだらリーダーが次の道を決定")
            members.values.forEach { it.hideBossBar(bar); host.showRunMenu(it) }
        } }
    }

    @Synchronized fun chooseBoon(player: Player, boon: DungeonBoon): Boolean = phase == DungeonRunPhase.CHOOSING && owns(player) &&
        blessings.choose(player.uuid, room.stage, boon, room.kind in setOf(DungeonRoomKind.ELITE, DungeonRoomKind.BOSS))

    @Synchronized fun chooseRoom(player: Player, roomId: Int): Boolean {
        if (phase != DungeonRunPhase.CHOOSING || !owns(player) || player.uuid != leader || members.keys.any { !blessings.hasChosen(it, room.stage) }) return false
        val next = world.plan.next(room).singleOrNull { it.id == roomId } ?: return false
        room = next; transferRoom(false); return true
    }
    private fun retryRoom() { clearRoom(); transferRoom(true) }
    private fun transferRoom(revive: Boolean) {
        phase = DungeonRunPhase.TRANSFERRING; epoch++; val token = epoch
        clearRoom(); downed.values.forEach { it.dispose() }; downed.clear(); reviving.clear()
        val moves = members.values.map { p ->
            p.closeInventory(); host.resetActions(p); p.gameMode = GameMode.ADVENTURE
            if (revive) host.revive(p, 1.0)
            try { p.teleport(room.spawn) } catch (e: Exception) { CompletableFuture.failedFuture<Void>(e) }
        }
        CompletableFuture.allOf(*moves.toTypedArray()).whenComplete { _, error -> later {
            if (token != epoch || phase != DungeonRunPhase.TRANSFERRING) return@later
            if (error != null) { phase = DungeonRunPhase.FAILED; announce("部屋の転送に失敗しました。手帳から帰還してください") }
            else beginCombat(host.nowMillis())
        } }
    }
    @Synchronized fun remove(player: Player) {
        if (!owns(player)) return
        members.remove(player.uuid); player.hideBossBar(bar); downed.remove(player.uuid)?.dispose(); reviving.remove(player.uuid)
        if (members.isEmpty()) close() else announce("${player.username}が帰還しました。残った仲間で継続できます")
    }
    private fun clearRoom() {
        mechanic?.dispose(); mechanic = null; combat?.dispose(); combat = null
        markers.forEach { it.dispose() }; markers.clear(); seals.clear(); enemies = emptySet()
    }
    @Synchronized fun close() { if (phase == DungeonRunPhase.CLOSED) return; phase = DungeonRunPhase.CLOSED; epoch++; clearRoom(); downed.values.forEach { it.dispose() }; downed.clear(); members.values.forEach { it.hideBossBar(bar) } }
    private fun later(action: () -> Unit) = host.schedule { synchronized(this) { action() } }
    private fun announce(text: String) { members.values.forEach { if (host.connected(it)) it.sendMessage(Component.text(text, NamedTextColor.GOLD)) } }
    @Synchronized fun objective(): String = "${room.stage}/${world.plan.stages} ${room.theme.displayName}・" + when {
        phase == DungeonRunPhase.COMPLETE -> "踏破・帰還できます"
        phase == DungeonRunPhase.FAILED -> "探索終了・帰還できます"
        phase == DungeonRunPhase.CHOOSING -> "加護と次の道を選ぼう"
        phase == DungeonRunPhase.SAVING -> "戦利品を保存中"
        room.kind == DungeonRoomKind.BOSS -> "${combat?.bossName().orEmpty()} ${mechanic?.title.orEmpty()}"
        room.kind == DungeonRoomKind.SEALS -> "封印 ${completedSeals.size}/3 / 残敵${enemies.count { combat?.isAlive(it) == true }}"
        else -> "${room.kind.displayName} 第${wave}波 / 残敵${enemies.count { combat?.isAlive(it) == true }}"
    }
}
