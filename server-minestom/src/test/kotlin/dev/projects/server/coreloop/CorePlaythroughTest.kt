package dev.projects.server.coreloop

import dev.projects.server.coreloop.adventure.BossArenaFactory
import dev.projects.server.coreloop.adventure.DungeonRun
import dev.projects.server.coreloop.adventure.InstanceDungeonFactory
import dev.projects.webui.UiInputPlayer
import net.kyori.adventure.key.Key
import net.kyori.adventure.nbt.BinaryTag
import net.kyori.adventure.nbt.CompoundBinaryTag
import net.kyori.adventure.nbt.StringBinaryTag
import net.kyori.adventure.resource.ResourcePackStatus
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import net.minestom.server.Auth
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Pos
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.Player
import net.minestom.server.entity.PlayerHand
import net.minestom.server.event.EventDispatcher
import net.minestom.server.event.player.PlayerHandAnimationEvent
import net.minestom.server.event.player.PlayerUseItemEvent
import net.minestom.server.instance.Instance
import net.minestom.server.instance.InstanceContainer
import net.minestom.server.instance.block.BlockFace
import net.minestom.server.network.ConnectionState
import net.minestom.server.network.packet.client.ClientPacket
import net.minestom.server.network.packet.client.common.ClientCustomClickActionPacket
import net.minestom.server.network.packet.client.common.ClientKeepAlivePacket
import net.minestom.server.network.packet.client.common.ClientResourcePackStatusPacket
import net.minestom.server.network.packet.client.play.ClientPlayerBlockPlacementPacket
import net.minestom.server.network.packet.server.SendablePacket
import net.minestom.server.network.packet.server.common.KeepAlivePacket
import net.minestom.server.network.packet.server.common.ResourcePackPushPacket
import net.minestom.server.network.packet.server.play.SystemChatPacket
import net.minestom.server.network.player.GameProfile
import net.minestom.server.network.player.PlayerConnection
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.io.File
import java.io.OutputStream
import java.io.PrintStream
import java.net.InetSocketAddress
import java.net.SocketAddress
import java.util.ArrayDeque
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Night playthrough (QUEUE.md「通しの自動プレイ」): bot players play the real game, start to end, in one server —
 * first join (画面の設定 → クラス選び), 開拓者の町 → ダンジョンの門 → 散り桜の社's rooms → 桜牙獣 → back in town,
 * [RUNS] times in a row, a warrior and a mage side by side. Every input goes through the game's own handlers (client
 * packets and the input events); the bots only read the game state to decide where to walk and whom to hit.
 *
 * Walking is a step a tick along the walkable blocks (InstanceDungeonTest's walk check), so a closed gate or a missing
 * floor stops a bot as it would a player. In a fight the bot steps beside its target and swings / casts.
 *
 * The bots are not players: a bot whose health falls under a third is set back to full ("heal"), and a room a bot
 * cannot clear in [ROOM_BUDGET] ticks is finished for it ("assist", as /dungeontest clear). Both are counted and
 * reported, never hidden. Stops (a step that never came) and exceptions fail the test.
 *
 * Run with `./gradlew :server-minestom:playthrough` (build/playthrough holds the save folder, the log and the report).
 */
class CorePlaythroughTest {
    private val runs = System.getProperty("projects.playthrough.runs", "3").toInt()
    private val tickMs = System.getProperty("projects.playthrough.tickMs", "30").toLong()

    private class Report {
        val lines = CopyOnWriteArrayList<String>()
        val stops = CopyOnWriteArrayList<String>()
        val exceptions = CopyOnWriteArrayList<Throwable>()
        var tick = 0L
        fun note(text: String) { val line = "[t=$tick] $text"; lines += line; REAL_OUT.println("PLAYTHROUGH $line") }
        fun stop(text: String) { stops += "[t=$tick] $text"; note("STOP $text") }
    }

    /** The bot's side of the connection: answers keep-alives and the UI pack, keeps what the game said. */
    private class BotConnection(val name: String, val report: Report, val inbox: ConcurrentLinkedQueue<Pair<PlayerConnection, ClientPacket>>) : PlayerConnection() {
        @Volatile var dialogs = 0
        @Volatile var kicked: String? = null
        override fun sendPacket(packet: SendablePacket) {
            val server = runCatching { SendablePacket.extractServerPacket(serverState, packet) }.getOrNull() ?: return
            when (server) {
                is net.minestom.server.network.packet.server.configuration.SelectKnownPacksPacket ->
                    Thread { Thread.sleep(50); receiveKnownPacksResponse(server.entries()) }.start()
                is KeepAlivePacket -> inbox += this to ClientKeepAlivePacket(server.id())
                is ResourcePackPushPacket -> Thread {
                    Thread.sleep(50)
                    for (status in listOf(ResourcePackStatus.ACCEPTED, ResourcePackStatus.DOWNLOADED, ResourcePackStatus.SUCCESSFULLY_LOADED))
                        MinecraftServer.getPacketListenerManager().processClientPacket(ClientResourcePackStatusPacket(server.id(), status), this)
                }.start()
                is SystemChatPacket -> if (!server.overlay()) report.note("$name chat: ${plain(server.message())}")
                else -> if (server.javaClass.simpleName.contains("ShowDialog")) dialogs++
            }
        }
        override fun getRemoteAddress(): SocketAddress = InetSocketAddress("127.0.0.1", 0)
        override fun kick(component: Component) { kicked = plain(component); report.stop("$name kicked: $kicked"); super.kick(component) }
    }

    /** Where a player can walk (InstanceDungeonTest's check): up one block, down three, two free blocks to stand in. */
    private class Walk(val stand: Map<Pair<Int, Int>, Int>, val from: Map<Pair<Int, Int>, Pair<Int, Int>>) {
        fun nearest(target: Pos): Pair<Int, Int>? = stand.keys.minByOrNull { (x, z) -> (x + .5 - target.x()).let { it * it } + (z + .5 - target.z()).let { it * it } }
        fun path(to: Pair<Int, Int>): List<Pos> {
            val steps = mutableListOf<Pos>(); var at: Pair<Int, Int>? = to
            while (at != null) { steps += Pos(at.first + .5, stand.getValue(at).toDouble(), at.second + .5); at = from[at] }
            return steps.reversed()
        }
    }

    private fun walk(instance: Instance, start: Pos, reach: Int): Walk {
        fun open(x: Int, y: Int, z: Int) = !instance.getBlock(x, y, z).solid() && !instance.getBlock(x, y, z).liquid()
        fun stands(x: Int, y: Int, z: Int) = instance.getBlock(x, y - 1, z).let { it.solid() && it.registry().collisionShape().relativeEnd().y() <= 1.0 } &&
            open(x, y, z) && open(x, y + 1, z)
        val sx = start.blockX(); val sz = start.blockZ()
        val stand = hashMapOf((sx to sz) to start.blockY())
        val from = hashMapOf<Pair<Int, Int>, Pair<Int, Int>>()
        val queue = ArrayDeque(listOf(Triple(sx, start.blockY(), sz)))
        while (queue.isNotEmpty()) {
            val (x, y, z) = queue.removeFirst()
            for ((dx, dz) in listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)) {
                val nx = x + dx; val nz = z + dz
                if (kotlin.math.abs(nx - sx) > reach || kotlin.math.abs(nz - sz) > reach || (nx to nz) in stand) continue
                if (instance.getChunkAt(nx.toDouble(), nz.toDouble()) == null) continue
                val ny = (1 downTo -3).map { y + it }.firstOrNull { stands(nx, it, nz) && (it <= y || open(x, y + 2, z)) } ?: continue
                stand[nx to nz] = ny; from[nx to nz] = x to z; queue += Triple(nx, ny, nz)
            }
        }
        return Walk(stand, from)
    }

    private inner class Bot(val name: String, val job: CoreClass, val game: CoreLoopGame, val harbor: HarborScene.Result,
                            val hub: InstanceContainer, val report: Report, val inbox: ConcurrentLinkedQueue<Pair<PlayerConnection, ClientPacket>>) {
        val connection = BotConnection(name, report, inbox)
        lateinit var player: Player
        @Volatile var configured = false
        var completed = 0
        var streak = 0
        var heals = 0
        val assists = mutableListOf<String>()
        var done = false
        val script: Iterator<Unit> = iterator { play() }

        private fun click(namespace: String, value: String, payload: BinaryTag) =
            MinecraftServer.getPacketListenerManager().processClientPacket(ClientCustomClickActionPacket(Key.key(namespace, value), payload), connection)

        private suspend fun SequenceScope<Unit>.wait(ticks: Int) = repeat(ticks) { yield(Unit) }

        /** Ticks until [ok]; false (and a stop in the report) when it never came. */
        private suspend fun SequenceScope<Unit>.until(ticks: Int, what: String, ok: () -> Boolean): Boolean {
            repeat(ticks) { if (ok()) return true; yield(Unit) }
            if (ok()) return true
            report.stop("$name: $what (waited $ticks ticks)")
            return false
        }

        private suspend fun SequenceScope<Unit>.play() {
            // First join: the configuration (with the UI pack) on its own thread, as the network thread would.
            val profile = GameProfile(UUID.nameUUIDFromBytes("playthrough:$name".toByteArray()), name)
            connection.setServerState(ConnectionState.CONFIGURATION); connection.setClientState(ConnectionState.CONFIGURATION)
            // Minestom creates and configures players on the network's virtual threads.
            val joining = Thread.ofVirtual().name("bot-config-$name").start {
                player = MinecraftServer.getConnectionManager().createPlayer(connection, profile)
                MinecraftServer.getConnectionManager().doConfiguration(player, true); configured = true
            }
            if (!until(600, "configuration never finished") { configured }) {
                report.note("$name configuration stack:\n  " + joining.stackTrace.joinToString("\n  ")); return }
            MinecraftServer.getConnectionManager().transitionConfigToPlay(player)
            connection.setClientState(ConnectionState.PLAY)   // the client's acknowledgement of the configuration's end
            if (!until(400, "never spawned in town") { player.instance === hub }) return
            report.note("$name joined town at ${player.position}")
            // 画面の設定 (required on the first join), then the class camp.
            if (!until(100, "the screen settings dialog never opened") { connection.dialogs > 0 }) return
            click(DisplaySettingsDialog.NS, "save", CompoundBinaryTag.builder().putString("gui", "4").putFloat("fov", 70f).build())
            if (!until(200, "the class camp never opened") { player.instance !== hub && player.instance != null }) return
            wait(20)
            click("projects_class", "choose", StringBinaryTag.stringBinaryTag(job.name))
            if (!until(400, "choosing ${job.name} never brought the player back to town") {
                    game.account(player)?.journey?.chosen == true && game.account(player)?.journey?.job == job && player.instance === hub }) return
            report.note("$name chose ${job.name}")
            wait(20)
            for (n in 1..runs) {
                val ok = runOnce(n)
                if (ok) { completed++; streak++ } else streak = 0
                if (!ok) {
                    // Back to town whatever happened, so the next run starts where a player would.
                    if (player.instance !== hub) game.returnToHarbor(player)
                    until(1200, "run $n: never came back to town after the stop") { player.instance === hub && !game.botInRun(player) }
                }
                wait(40)
            }
            done = true
        }

        private suspend fun SequenceScope<Unit>.walkTo(instance: Instance, target: Pos, what: String, near: Double, stopWhen: () -> Boolean): Boolean {
            var guard = 0
            while (!stopWhen()) {
                val map = walk(instance, player.position, 140)
                val goal = map.nearest(target) ?: run { report.stop("$name: $what — nowhere to stand"); return false }
                val gap = Math.hypot(goal.first + .5 - target.x(), goal.second + .5 - target.z())
                if (gap > near) { report.stop("$name: $what — cannot walk there (closest ${"%.1f".format(gap)} blocks away, at ${goal.first},${goal.second})"); return false }
                val path = map.path(goal)
                if (path.size <= 1) return true
                for (step in path.drop(1).take(48)) {
                    val ahead = step.sub(player.position).withY(0.0)
                    player.teleport(if (ahead.lengthSquared() > 1e-4) step.withDirection(ahead) else step)
                    yield(Unit); guard++
                    if (stopWhen()) return true
                    if (guard > 6000) { report.stop("$name: $what — still walking after 6000 steps"); return false }
                }
            }
            return true
        }

        private suspend fun SequenceScope<Unit>.runOnce(n: Int): Boolean {
            val actor = game.botCombat(player) ?: run { report.stop("$name: run $n — no combat actor"); return false }
            actor.reset()
            // 開拓者の町 → ダンジョンの門: walk to the gate's click block and use it.
            val gate = harbor.facilities.first { it.kind == HarborFacilityKind.DUNGEON }
            val block = gate.blockPositions.minBy { it.distanceSquared(gate.position) }
            if (!walkTo(hub, Pos(block.x() + .5, block.y(), block.z() + .5), "run $n: town → ダンジョンの門", 4.0) { false }) return false
            player.teleport(player.position.withDirection(Vec(block.x() + .5, block.y() + .5, block.z() + .5).sub(player.position.add(0.0, 1.6, 0.0))))
            yield(Unit)
            val before = connection.dialogs
            MinecraftServer.getPacketListenerManager().processClientPacket(
                ClientPlayerBlockPlacementPacket(PlayerHand.MAIN, block, BlockFace.TOP, .5f, 1f, .5f, false, false, n), connection)
            if (!until(60, "run $n: using ダンジョンの門 opened nothing") { connection.dialogs > before }) return false
            wait(10)
            click(DungeonGateDialog.NS, "enter", CompoundBinaryTag.empty())
            if (!until(2400, "run $n: 散り桜の社 never opened after 入る") { game.botDungeon(player) != null && player.instance !== hub }) return false
            val (run, combat) = game.botDungeon(player)!!
            val instance = player.instance!!
            val layout = run.layout
            report.note("$name run $n: entered ${layout.name} (段階${run.step})")
            val started = report.tick
            for ((i, room) in layout.rooms.withIndex()) {
                if (!walkTo(instance, room.center, "run $n: to ${room.name}", room.radius.toDouble()) { run.stage == DungeonRun.Stage.FIGHTING || run.room != i || dead() }) return false
                if (dead()) { report.stop("$name run $n: fell on the way to ${room.name}"); return false }
                if (run.stage != DungeonRun.Stage.FIGHTING) {
                    // Standing in the round should start it.
                    if (!until(100, "run $n: ${room.name} never started (stage ${run.stage}, room ${run.room})") { run.stage == DungeonRun.Stage.FIGHTING }) return false
                }
                if (!fight(n, room.name, ROOM_BUDGET, done = { run.stage == DungeonRun.Stage.WALKING && run.room == i + 1 },
                        assist = { combat.entities().filter { !combat.isBoss(it.uuid) }.forEach { combat.applyEffectDamage(it.uuid, player, 1e6) }; run.skipToLast() },
                        targets = { combat.entities().filter { !combat.isBoss(it.uuid) && room.holds(it.position, -4.0) } })) return false
                report.note("$name run $n: ${room.name} cleared")
            }
            if (!walkTo(instance, layout.arenaCenter, "run $n: to 散り桜の庭", layout.arenaRadius) { run.bossEngaged || dead() }) return false
            if (!until(100, "run $n: walking into 散り桜の庭 never woke the boss") { run.bossEngaged }) return false
            if (!fight(n, "桜牙獣", BOSS_BUDGET, done = { combat.bossDefeated },
                    assist = { combat.entities().filter { combat.isBoss(it.uuid) }.forEach { combat.applyEffectDamage(it.uuid, player, 1e6) } },
                    targets = { combat.entities() })) return false
            report.note("$name run $n: 桜牙獣 defeated after ${report.tick - started} ticks in the dungeon")
            if (!until(2000, "run $n: never sent back to town after the boss") { player.instance === hub && !game.botInRun(player) }) return false
            val account = game.account(player)
            if (account?.activeRun != null) { report.stop("$name run $n: back in town but the run is still open"); return false }
            report.note("$name run $n: back in town; clears ${account?.dungeonClears?.get(CoreDungeons.SAKURA)}, level ${account?.journey?.level}")
            return true
        }

        private fun dead() = game.botCombat(player)?.defeated == true || player.instance === hub

        private suspend fun SequenceScope<Unit>.fight(n: Int, what: String, budget: Int, done: () -> Boolean, assist: () -> Unit,
                                                       targets: () -> List<net.minestom.server.entity.Entity>): Boolean {
            var ticks = 0; var idle = 0; var assisted = false
            while (!done()) {
                val actor = game.botCombat(player)
                if (actor == null || actor.defeated || player.instance === hub) { report.stop("$name run $n: fell in $what"); return false }
                if (actor.health < actor.maxHealth / 3.0) { actor.reset(); heals++ }
                if (ticks >= budget && (ticks - budget) % 100 == 0) {
                    if (!assisted) { assists += "run $n $what"; report.note("$name run $n: $what not cleared in $budget ticks — assisted") }
                    assisted = true; assist()
                }
                val foes = targets().filter { it.instance === player.instance }
                val target = foes.minByOrNull { it.position.distanceSquared(player.position) }
                if (target == null) idle++ else { idle = 0; strike(target, ticks) }
                if (idle > 1200) { report.stop("$name run $n: $what — no enemy to fight for 1200 ticks and not cleared"); return false }
                if (ticks > budget + 4000) { report.stop("$name run $n: $what — not cleared even with help"); return false }
                ticks++
                yield(Unit)
            }
            return true
        }

        /** Step beside [target] (melee) or a few blocks off (mage), face it, swing; now and then a skill. */
        private fun strike(target: net.minestom.server.entity.Entity, ticks: Int) {
            val instance = player.instance ?: return
            val half = target.boundingBox.width() / 2
            val range = if (job == CoreClass.MAGE) 6.0 + half else 1.8 + half
            val t = target.position
            val away = player.position.sub(t).withY(0.0).asVec().let { if (it.lengthSquared() < .01) Vec(1.0, 0.0, 0.0) else it.normalize() }
            if (player.position.distance(t.withY(player.position.y())) > range + 1.0 || player.position.distance(t.withY(player.position.y())) < range - 1.5) {
                val base = atan2(away.z(), away.x())
                val stand = (0 until 12).asSequence().map { k -> base + (if (k % 2 == 0) 1 else -1) * (k + 1) / 2 * Math.PI / 6 }.map { a ->
                    val x = t.x() + cos(a) * range; val z = t.z() + sin(a) * range
                    Pos(x, InstanceDungeonFactory.floorAt(instance as InstanceContainer, floor(x).toInt(), floor(z).toInt(), t.blockY()).toDouble(), z)
                }.firstOrNull { QuestCombatPlacement.clear(instance, it) && kotlin.math.abs(it.y() - t.y()) < 4 }
                if (stand != null) player.teleport(stand)
            }
            val eye = player.position.add(0.0, 1.62, 0.0)
            player.teleport(player.position.withDirection(t.add(0.0, target.boundingBox.height() / 2, 0.0).sub(eye)))
            player.setHeldItemSlot(0)
            EventDispatcher.call(PlayerHandAnimationEvent(player, PlayerHand.MAIN))
            if (ticks % 8 == 4) {
                val slot = 1 + (ticks / 8) % 5
                val item = player.inventory.getItemStack(slot)
                if (item.getTag(CoreLoopItems.actionTag)?.startsWith("skill:") == true) EventDispatcher.call(PlayerUseItemEvent(player, PlayerHand.MAIN, item, 0))
            }
        }
    }

    @Test
    fun `warrior and mage play town, gate, 散り桜の社 and 桜牙獣 back to town`() {
        assumeTrue(System.getProperty("projects.playthrough") == "true", "./gradlew :server-minestom:playthrough runs this")
        val logFile = File("playthrough.log")
        val log = logFile.outputStream().buffered()
        System.setOut(PrintStream(Tee(REAL_OUT, log), true, Charsets.UTF_8))
        System.setErr(PrintStream(Tee(REAL_ERR, log), true, Charsets.UTF_8))
        val report = Report()
        try {
            CoreMmoTuning.balance = CoreMmoBalance.load(java.nio.file.Path.of("config", "projects", "mmo-balance.properties"))
            MinecraftServer.init(Auth.Offline())
            MinecraftServer.getExceptionManager().setExceptionHandler { failure ->
                report.exceptions += failure
                report.note("EXCEPTION ${failure.javaClass.name}: ${failure.message}")
                failure.printStackTrace()
            }
            BossArenaFactory.registerDimensions()
            // No network here: the tick threads start as server.start would start them.
            MinecraftServer.process().dispatcher().start()
            val hub = MinecraftServer.getInstanceManager().createInstanceContainer()
            val harbor = HarborScene.build(hub)
            val game = CoreLoopGame(hub, harbor)
            MinecraftServer.getConnectionManager().setPlayerProvider { connection, profile -> UiInputPlayer(connection, profile, game::consumeImmediateUiPacket) }
            game.register()
            val inbox = ConcurrentLinkedQueue<Pair<PlayerConnection, ClientPacket>>()
            val bots = listOf(Bot("BotWarrior", CoreClass.WARRIOR, game, harbor, hub, report, inbox),
                Bot("BotMage", CoreClass.MAGE, game, harbor, hub, report, inbox))
            val ticker = MinecraftServer.process().ticker()
            val deadline = System.currentTimeMillis() + 50 * 60_000L
            while (bots.any { !it.done } && System.currentTimeMillis() < deadline) {
                val started = System.currentTimeMillis()
                while (true) { val (connection, packet) = inbox.poll() ?: break; MinecraftServer.getPacketListenerManager().processClientPacket(packet, connection) }
                ticker.tick(System.nanoTime())
                report.tick++
                for (bot in bots) if (!bot.done) try {
                    if (bot.script.hasNext()) bot.script.next() else bot.done = true
                } catch (failure: Throwable) {
                    bot.done = true
                    report.stop("${bot.name}: the bot itself failed: $failure")
                    failure.printStackTrace()
                }
                Thread.sleep((tickMs - (System.currentTimeMillis() - started)).coerceAtLeast(1))
            }
            bots.filterNot { it.done }.forEach { report.stop("${it.name}: out of time") }
            System.out.flush(); System.err.flush(); log.flush()
            val text = logFile.readLines()
            val errorLines = text.filter { ERROR.containsMatchIn(it) }
            val exceptionLines = text.filter { EXCEPTION.containsMatchIn(it) }
            val summary = buildString {
                appendLine("PLAYTHROUGH SUMMARY runs=$runs tickMs=$tickMs ticks=${report.tick}")
                for (bot in bots) appendLine("  ${bot.name} (${bot.job}): completed ${bot.completed}/$runs, streak ${bot.streak}, heals ${bot.heals}, assists ${bot.assists.size} ${bot.assists}")
                appendLine("  stops ${report.stops.size}"); report.stops.forEach { appendLine("    $it") }
                appendLine("  exception handler ${report.exceptions.size}, log ERROR lines ${errorLines.size}, log exception lines ${exceptionLines.size}")
                (errorLines + exceptionLines).distinct().take(40).forEach { appendLine("    $it") }
            }
            File("playthrough-report.txt").writeText(summary + "\n" + report.lines.joinToString("\n"))
            REAL_OUT.println(summary)
            assertTrue(report.stops.isEmpty() && report.exceptions.isEmpty() && errorLines.isEmpty() && exceptionLines.isEmpty() &&
                bots.all { it.streak >= runs }, summary)
        } finally {
            runCatching { MinecraftServer.stopCleanly() }
            System.setOut(REAL_OUT); System.setErr(REAL_ERR); log.close()
        }
    }

    private class Tee(val a: OutputStream, val b: OutputStream) : OutputStream() {
        override fun write(byte: Int) { a.write(byte); synchronized(b) { b.write(byte) } }
        override fun write(bytes: ByteArray, off: Int, len: Int) { a.write(bytes, off, len); synchronized(b) { b.write(bytes, off, len) } }
        override fun flush() { a.flush(); synchronized(b) { b.flush() } }
    }

    companion object {
        private const val ROOM_BUDGET = 3600
        private const val BOSS_BUDGET = 7200
        private val REAL_OUT: PrintStream = System.out
        private val REAL_ERR: PrintStream = System.err
        private val ERROR = Regex("ERROR|_FAILURE|SEVERE")
        private val EXCEPTION = Regex("Exception|Throwable|\\bError\\b|^\\s+at [a-z]")
        private fun plain(component: Component) = PlainTextComponentSerializer.plainText().serialize(component)
    }
}
