package dev.projects.server.coreloop

import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Pos
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.Entity
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.EquipmentSlot
import net.minestom.server.entity.LivingEntity
import net.minestom.server.entity.Player
import net.minestom.server.entity.metadata.display.TextDisplayMeta
import net.minestom.server.entity.metadata.other.ArmorStandMeta
import net.minestom.server.event.player.PlayerPacketEvent
import net.minestom.server.instance.InstanceContainer
import net.minestom.server.instance.LightingChunk
import net.minestom.server.instance.anvil.AnvilLoader
import net.minestom.server.instance.block.Block
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.network.packet.client.play.ClientInputPacket
import net.minestom.server.network.packet.server.play.CameraPacket
import net.minestom.server.network.packet.server.play.ChangeGameStatePacket
import net.minestom.server.timer.TaskSchedule
import net.kyori.adventure.text.Component
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.UUID
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.PI
import kotlin.math.cos

/**
 * Class selection as a small place, after RuneForge's (藍 2026-11-06 「あんな感じにしたい」): six class figures sit on log
 * benches in a forest clearing (scripts/buildkit/towns/class_camp.py, shipped as resources/class-camp), the camera is
 * held in front of one of them, and A / D slides it to the next. Choosing comes later; Shift leaves for now.
 *
 * The camera is a text display the client spectates (as UiSessions does); A / D arrive as the player's input packet.
 */
internal class ClassSelectScene {
    /** One figure per class, west to east, on the benches the camp script lays out. */
    private val order = listOf(CoreClass.WARRIOR, CoreClass.MAGE, CoreClass.TEMPLAR, CoreClass.HEALER, CoreClass.ASSASSIN, CoreClass.RANGER)
    private val world: InstanceContainer by lazy(::load)
    private val figures = mutableListOf<Entity>()

    private class Session(val player: Player, val back: Pos, val camera: Entity) {
        var index = 0
        var from = 0.0
        var at = 0.0
        var step = SLIDE
        var held = false
    }
    private val sessions = mutableMapOf<UUID, Session>()

    fun install() {
        MinecraftServer.getGlobalEventHandler().addListener(PlayerPacketEvent::class.java) { event ->
            val s = sessions[event.player.uuid] ?: return@addListener
            val packet = event.packet as? ClientInputPacket ?: return@addListener
            event.isCancelled = true
            if (packet.shift()) { close(event.player); return@addListener }
            val dir = (if (packet.right()) 1 else 0) - (if (packet.left()) 1 else 0)
            // One step per press: holding a key does not keep sliding.
            if (dir == 0) { s.held = false; return@addListener }
            if (s.held) return@addListener
            s.held = true
            step(event.player, dir)
        }
        MinecraftServer.getSchedulerManager().buildTask {
            for (s in sessions.values.toList()) {
                if (s.step >= SLIDE) continue
                s.step++
                val t = s.step.toDouble() / SLIDE
                s.at = s.from + (s.index - s.from) * (1 - cos(t * PI)) / 2
                s.camera.teleport(camera(s.at))
            }
        }.repeat(TaskSchedule.tick(1)).schedule()
    }

    fun open(player: Player) {
        if (sessions.containsKey(player.uuid)) return
        val back = player.position
        val camera = Entity(EntityType.TEXT_DISPLAY).apply {
            setNoGravity(true); setAutoViewable(false)
            (entityMeta as TextDisplayMeta).apply { text = Component.empty(); backgroundColor = 0 }
        }
        val s = Session(player, back, camera)
        sessions[player.uuid] = s
        if (figures.isEmpty()) seat()
        player.setInstance(world, Pos(SEATS_X.first() + 0.5, SEAT_TOP, 6.5)).thenRun {
            player.isInvisible = true
            camera.setInstance(world, camera(0.0)).thenRun {
                camera.addViewer(player)
                player.sendPacket(ChangeGameStatePacket(ChangeGameStatePacket.Reason.CHANGE_GAMEMODE, 3f))
                player.sendPacket(CameraPacket(camera.entityId))
            }
        }
    }

    /** Slide the camera one figure to the east (+1) or west (-1). */
    fun step(player: Player, dir: Int) {
        val s = sessions[player.uuid] ?: return
        val next = (s.index + dir).coerceIn(order.indices)
        if (next != s.index) { s.from = s.at; s.index = next; s.step = 0 }
    }

    /** [leave] false when the player is disconnecting (nowhere to send them). */
    fun close(player: Player, leave: Boolean = true) {
        val s = sessions.remove(player.uuid) ?: return
        player.sendPacket(CameraPacket(player.entityId))
        player.sendPacket(ChangeGameStatePacket(ChangeGameStatePacket.Reason.CHANGE_GAMEMODE, player.gameMode.ordinal.toFloat()))
        player.isInvisible = false
        s.camera.remove()
        if (leave) onLeave(player)
    }

    /** Where the player goes when they leave (the harbor); set by the server. */
    var onLeave: (Player) -> Unit = {}

    /** The camera for a (fractional) seat: south of the bench, a little to the east and above, looking at the figure's chest. */
    private fun camera(seat: Double): Pos {
        val x = SEATS_X.first() + 0.5 + seat * SEAT_GAP
        val eye = Vec(x + 0.9, SEAT_TOP + 1.25, 0.5 + 3.6)
        val look = Vec(x, SEAT_TOP + 0.55, 0.5)
        val d = look.sub(eye)
        val yaw = Math.toDegrees(atan2(-d.x(), d.z())).toFloat()
        val pitch = Math.toDegrees(-atan2(d.y(), hypot(d.x(), d.z()))).toFloat()
        return Pos(eye.x(), eye.y(), eye.z(), yaw, pitch)
    }

    /** Each figure rides an invisible marker on its bench, which seats it; it faces the camera side (south). */
    private fun seat() {
        order.forEachIndexed { i, job ->
            val x = SEATS_X[i] + 0.5
            val seat = Entity(EntityType.ARMOR_STAND).apply {
                setNoGravity(true)
                (entityMeta as ArmorStandMeta).apply { isMarker = true; isInvisible = true }
            }
            val figure = LivingEntity(EntityType.MANNEQUIN).apply {
                setNoGravity(true)
                setEquipment(EquipmentSlot.MAIN_HAND, weapon(job))
            }
            seat.setInstance(world, Pos(x, SEAT_TOP + RIDE, 0.5, 0f, 0f)).thenRun {
                figure.setInstance(world, Pos(x, SEAT_TOP, 0.5, 0f, 0f)).thenRun { seat.addPassenger(figure) }
            }
            figures += seat; figures += figure
        }
    }

    private fun weapon(job: CoreClass): ItemStack {
        val family = when (job) {
            CoreClass.WARRIOR -> "greatsword"; CoreClass.MAGE -> "staff"; CoreClass.TEMPLAR -> "mace"
            CoreClass.HEALER -> "tome"; CoreClass.ASSASSIN -> "dagger"; CoreClass.RANGER -> "bow"
        }
        return ItemStack.of(Material.IRON_SWORD).withItemModel("projects:weapons/${family}_t1")
    }

    /** The camp ships as region files in the jar; AnvilLoader reads from disk, so they are copied out once. */
    private fun load(): InstanceContainer {
        val dir = Files.createTempDirectory("projects-class-camp")
        Files.createDirectories(dir.resolve("region"))
        for (name in listOf("level.dat", "region/r.-1.-1.mca", "region/r.-1.0.mca", "region/r.0.-1.mca", "region/r.0.0.mca")) {
            requireNotNull(javaClass.classLoader.getResourceAsStream("class-camp/$name")) { "class-camp/$name がありません" }
                .use { Files.copy(it, dir.resolve(name), StandardCopyOption.REPLACE_EXISTING) }
        }
        dir.toFile().deleteOnExit()
        return MinecraftServer.getInstanceManager().createInstanceContainer().apply {
            setChunkSupplier(::LightingChunk)
            chunkLoader = AnvilLoader(dir)
            setGenerator { unit -> unit.modifier().fillHeight(-64, 0, Block.DEEPSLATE) }
            time = 1_500L
            defaultClock()?.pause()
        }
    }

    companion object {
        /** Bench centres (block x) and the top of the logs; matches class_camp.py's SEATS and Y0. */
        private val SEATS_X = List(6) { -20 + 8 * it }
        private const val SEAT_GAP = 8.0
        private const val SEAT_TOP = 66.0
        /** How far below the log top the marker sits so the rider's seat lands on the log. */
        private const val RIDE = -0.6
        private const val SLIDE = 14
    }
}
