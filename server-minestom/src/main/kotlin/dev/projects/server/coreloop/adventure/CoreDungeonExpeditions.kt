package dev.projects.server.coreloop.adventure

import dev.projects.server.coreloop.*
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.minestom.server.entity.Entity
import net.minestom.server.entity.GameMode
import net.minestom.server.entity.Player
import net.minestom.server.instance.Instance
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor

sealed interface DungeonLobbyAction {
    data class Create(val tier: Int, val ascension: Int) : DungeonLobbyAction
    data class Solo(val tier: Int, val ascension: Int) : DungeonLobbyAction
    data class Join(val id: UUID) : DungeonLobbyAction
    data object Ready : DungeonLobbyAction
    data object Leave : DungeonLobbyAction
    data object Start : DungeonLobbyAction
}

internal interface CoreDungeonHost : DungeonRunHost {
    fun account(player: Player): CoreAccount?
    fun player(id: UUID): Player?
    fun eligible(player: Player): Boolean
    fun transact(player: Player, action: CoreAction, revision: Long? = null): CompletableFuture<CoreTransactionResult>
    fun drain(player: Player): CompletableFuture<Void>
    fun harbor(player: Player): CompletableFuture<Boolean>
    fun refreshed(player: Player)
}

/** Launch/return coordinator owns asynchronous worlds even before the first successful player transfer. */
internal class CoreDungeonExpeditions(private val host: CoreDungeonHost, private val builder: Executor) {
    val parties = DungeonParties()
    private val runs = ConcurrentHashMap<UUID, DungeonRun>()
    private val pending = ConcurrentHashMap<UUID, UUID>()
    private val returning = ConcurrentHashMap<UUID, DungeonRun>()
    private val retired = ConcurrentHashMap.newKeySet<DungeonWorld>()
    @Volatile private var closed = false
    fun isDeparting(player: Player) = pending.containsKey(player.uuid)
    fun hasRun(player: Player) = runs[player.uuid]?.owns(player) == true || returning.containsKey(player.uuid)
    fun run(player: Player) = runs[player.uuid]?.takeIf { it.owns(player) }
    fun combat(player: Player) = run(player)?.takeIf { it.canFight(player) }?.combat
    fun view(player: Player) = run(player)?.view(player)
    fun stats(player: Player, base: CoreAffixStats) = run(player)?.stats(player.uuid, base) ?: base

    fun lobby(player: Player, action: DungeonLobbyAction) {
        if (closed || !host.eligible(player) || pending.containsKey(player.uuid) || runs.containsKey(player.uuid)) return
        runCatching {
            when (action) {
                is DungeonLobbyAction.Create -> { eligibleTier(player, action.tier, action.ascension); parties.create(player.uuid, action.tier, action.ascension) }
                is DungeonLobbyAction.Solo -> {
                    eligibleTier(player, action.tier, action.ascension)
                    check(parties.of(player.uuid) == null) { "先に今のパーティを抜けてください" }
                    parties.create(player.uuid, action.tier, action.ascension); parties.ready(player.uuid); start(player)
                }
                is DungeonLobbyAction.Join -> {
                    val party = parties.list().singleOrNull { it.id == action.id } ?: error("募集は終了しました")
                    eligibleTier(player, party.tier, party.ascension); parties.join(player.uuid, action.id)
                }
                DungeonLobbyAction.Ready -> parties.ready(player.uuid)
                DungeonLobbyAction.Leave -> parties.leave(player.uuid)
                DungeonLobbyAction.Start -> start(player)
            }
        }.onFailure { player.sendMessage(Component.text(it.message ?: "操作できません", NamedTextColor.YELLOW)) }
    }
    private fun eligibleTier(player: Player, tier: Int, ascension: Int) {
        val a = host.account(player) ?: error("データを読込中です")
        require(tier in 1..a.unlockedMapTier) { "そのTierは未解放です" }
        require(ascension in 0..minOf(CoreMmoTuning.balance.dungeonMaxAscension, (a.dungeonRecords[tier] ?: -1) + 1)) { "先に一つ前の深度を踏破してください" }
        require(!a.weaponBroken) { "先に武器を修理してください" }
    }
    private fun start(leader: Player) {
        val before = parties.of(leader.uuid) ?: error("パーティがありません")
        val players = before.members.map { id -> requireNotNull(host.player(id)) { "オフラインの仲間がいます" } }
        players.forEach { check(host.eligible(it) && !pending.containsKey(it.uuid) && !runs.containsKey(it.uuid)) { "全員が港で操作を終えてください" }; eligibleTier(it, before.tier, before.ascension) }
        val party = parties.launch(leader.uuid)
        val runId = UUID.randomUUID(); val seed = UUID.randomUUID().leastSignificantBits
        players.forEach { pending[it.uuid] = runId; it.closeInventory(); host.resetActions(it); it.sendMessage(Component.text("星環の深殿を生成しています…", NamedTextColor.GOLD)) }
        val reservations = players.map { p -> host.transact(p, CoreAction.StartDungeon(runId, party.tier, party.ascension, seed), host.account(p)!!.revision) }
        CompletableFuture.allOf(*reservations.toTypedArray()).whenComplete { _, reserveError -> host.schedule {
            if (reserveError != null || reservations.any { it.isCompletedExceptionally || it.getNow(null)?.successful != true } || !current(players, runId)) {
                abort(party, players, runId, null); return@schedule
            }
            CompletableFuture.supplyAsync({ DungeonWorld.create(DungeonPlan.generate(seed, party.tier, party.ascension)) }, builder).whenComplete { world, buildError -> host.schedule {
                if (buildError != null || world == null || !current(players, runId)) {
                    System.err.println("CORE_DUNGEON_BUILD_ABORT run=$runId error=$buildError")
                    abort(party, players, runId, world); return@schedule
                }
                val spawn = world.plan.choices(1).first().spawn
                val transfers = players.map { p -> try { p.setInstance(world.instance, spawn) } catch (e: Exception) { CompletableFuture.failedFuture<Void>(e) } }
                CompletableFuture.allOf(*transfers.toTypedArray()).whenComplete { _, transferError -> host.schedule {
                    if (transferError != null || !current(players, runId) || players.any { it.instance !== world.instance }) {
                        abort(party, players, runId, world); return@schedule
                    }
                    val run = DungeonRun(runId, world, players, host)
                    try {
                        players.forEach { runs[it.uuid] = run; pending.remove(it.uuid, runId); host.revive(it, 1.0); host.refreshed(it) }
                        run.start(host.nowMillis())
                        parties.remove(party.id)
                        println("CORE_DUNGEON_STARTED run=$runId players=${players.joinToString { it.username }} seed=$seed tier=${party.tier} ascension=${party.ascension} rooms=${world.plan.rooms.size} transfer=complete")
                    } catch (error: Exception) {
                        run.close(); players.forEach { runs.remove(it.uuid, run) }; abort(party, players, runId, world)
                    }
                } }
            } }
        } }
    }
    private fun current(players: List<Player>, runId: UUID) = !closed && players.all { host.connected(it) && pending[it.uuid] == runId && host.account(it)?.activeRun?.id == runId }

    private fun abort(party: DungeonParty, players: List<Player>, runId: UUID, world: DungeonWorld?) {
        parties.remove(party.id)
        world?.let { retired += it }
        players.forEach { p ->
            // Never teleport or finish a replacement login. Its login recovery owns the previous ledger run.
            if (host.connected(p)) {
                val moved = if (world != null && p.instance === world.instance) host.harbor(p) else CompletableFuture.completedFuture(true)
                moved.whenComplete { success, failure -> host.schedule {
                    if (!host.connected(p)) { if (p.instance === world?.instance) p.remove(); return@schedule }
                    if (failure != null || success != true) p.kick(Component.text("深殿の転送に失敗しました。再接続してください"))
                    if (host.account(p)?.activeRun?.id == runId) host.transact(p, CoreAction.AbortRun(runId)).whenComplete { result, _ -> host.schedule {
                        pending.remove(p.uuid, runId)
                        if (host.connected(p)) { host.refreshed(p); p.sendMessage(Component.text(result?.message ?: "出発できませんでした。再接続で復旧します", NamedTextColor.YELLOW)) }
                    } } else pending.remove(p.uuid, runId)
                } }
            } else {
                pending.remove(p.uuid, runId)
                if (world != null && p.instance === world.instance) p.remove()
            }
        }
    }

    fun tick(instance: Instance, now: Long) {
        runs.values.toSet().filter { it.instance === instance }.forEach { run ->
            runCatching { run.tick(now) }.onFailure { error ->
                System.err.println("CORE_DUNGEON_RUNTIME_FAILURE run=${run.id}: $error")
                run.playerIds.mapNotNull(host::player).forEach { p -> p.sendMessage(Component.text("深殿を安全に終了します。獲得済み報酬は保持されます", NamedTextColor.RED)); returnToHarbor(p) }
            }
        }
        retired.filter { it.instance === instance && it.instance.players.isEmpty() }.forEach { it.dispose(); retired.remove(it) }
    }
    fun interact(player: Player, entity: Entity, now: Long) = run(player)?.interact(player, entity, now) == true
    fun defeated(player: Player): Boolean { val r = run(player) ?: return false; r.defeated(player); return true }
    fun boon(player: Player, boon: DungeonBoon) { if (run(player)?.chooseBoon(player, boon) == true) host.showRunMenu(player) }
    fun route(player: Player, room: Int) { if (run(player)?.chooseRoom(player, room) != true) player.sendMessage(Component.text("全員が加護を選んだ後、リーダーが進路を決めます", NamedTextColor.YELLOW)) }

    fun returnToHarbor(player: Player): Boolean {
        val run = runs[player.uuid] ?: return false
        if (!host.connected(player) || returning.putIfAbsent(player.uuid, run) != null) return true
        run.remove(player); host.resetActions(player); player.closeInventory()
        if (run.playerIds.isEmpty()) retired += run.world
        host.drain(player).thenCompose { host.transact(player, CoreAction.FinishRun(run.id)) }.whenComplete { result, error -> host.schedule {
            if (!host.connected(player)) { returning.remove(player.uuid, run); return@schedule }
            if (error != null || result?.successful != true) {
                returning.remove(player.uuid, run)
                player.sendMessage(Component.text("帰還を保存できません。/hub で再試行してください", NamedTextColor.RED)); return@schedule
            }
            host.harbor(player).whenComplete { returned, failure -> host.schedule {
                if (host.connected(player)) {
                    if (returned == true && failure == null) {
                        player.gameMode = GameMode.ADVENTURE; host.revive(player, 1.0); host.refreshed(player)
                        player.sendMessage(Component.text("深殿から帰還。オーブで装備を仕上げるか、戦利品券を換金して市場へ", NamedTextColor.GOLD))
                    } else player.kick(Component.text("帰還先を読み直すため再接続してください。報酬は保存済みです"))
                } else if (player.instance === run.instance) player.remove()
                runs.remove(player.uuid, run); returning.remove(player.uuid, run)
                if (run.playerIds.isEmpty()) retired += run.world
            } }
        } }
        return true
    }
    fun disconnect(player: Player) {
        pending.remove(player.uuid)
        parties.of(player.uuid)?.let { if (!it.starting) parties.leave(player.uuid) }
        runs.remove(player.uuid)?.let { run -> run.remove(player); if (run.playerIds.isEmpty()) retired += run.world }
        returning.remove(player.uuid)
    }
    fun close() { closed = true; runs.values.toSet().forEach { it.close() }; runs.clear(); pending.clear(); parties.list().forEach { parties.remove(it.id) } }
}
