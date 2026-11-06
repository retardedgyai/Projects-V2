package dev.projects.server.coreloop

import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Pos
import net.minestom.server.coordinate.Vec
import net.minestom.server.dialog.Dialog
import net.minestom.server.dialog.DialogAction
import net.minestom.server.dialog.DialogActionButton
import net.minestom.server.dialog.DialogAfterAction
import net.minestom.server.dialog.DialogBody
import net.minestom.server.dialog.DialogMetadata
import net.minestom.server.entity.Entity
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.EquipmentSlot
import net.minestom.server.entity.LivingEntity
import net.minestom.server.entity.Player
import net.minestom.server.entity.metadata.display.AbstractDisplayMeta
import net.minestom.server.entity.metadata.display.ItemDisplayMeta
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
import net.kyori.adventure.key.Key
import net.kyori.adventure.nbt.BinaryTag
import net.kyori.adventure.nbt.StringBinaryTag
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextColor
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Class selection as a small place, after RuneForge's (藍 2026-11-06 「あんな感じにしたい」): six class figures sit on log
 * benches in a forest clearing (scripts/buildkit/towns/class_camp.py, shipped as resources/class-camp), the camera is
 * held in front of one of them, and A / D slides it to the next. Beside each figure its weapon floats and turns, ringed
 * by its skill plates; its name hangs above and a card of names and numbers stands on the other side. Space asks to
 * choose (戦士 and メイジ only for now, the rest say 開発中); Shift leaves.
 *
 * The camera is a text display the client spectates (as UiSessions does); A / D / Space / Shift arrive as the player's
 * input packet. The words use the menu's MaruMinya dots (polish05; this file is one of its text sources).
 */
internal class ClassSelectScene {
    /** One figure per class, west to east, on the benches the camp script lays out. */
    private val order = listOf(CoreClass.WARRIOR, CoreClass.MAGE, CoreClass.TEMPLAR, CoreClass.HEALER, CoreClass.ASSASSIN, CoreClass.RANGER)
    private val world: InstanceContainer by lazy(::load)
    private val figures = mutableListOf<Entity>()
    /** Per class: the floating weapon and the plates circling it, turned by [animate]. */
    private val weapons = mutableListOf<Entity>()
    private val plates = mutableListOf<List<Entity>>()
    private var phase = 0

    private class Session(val player: Player, val back: Pos, val camera: Entity) {
        var index = 0
        var from = 0.0
        var at = 0.0
        var step = SLIDE
        var held = false
    }
    private val sessions = mutableMapOf<UUID, Session>()

    /** Where the player goes when they leave (back to [Pos] in the harbor); set by the server. */
    var onLeave: (Player, Pos) -> Unit = { _, _ -> }
    /** The player confirmed a class; the server records it and then calls [close]. */
    var onChoose: (Player, CoreClass) -> Unit = { _, _ -> }

    fun install() {
        MinecraftServer.getGlobalEventHandler().addListener(PlayerPacketEvent::class.java) { event ->
            val s = sessions[event.player.uuid] ?: return@addListener
            val packet = event.packet as? ClientInputPacket ?: return@addListener
            event.isCancelled = true
            if (packet.shift()) { close(event.player); return@addListener }
            val dir = (if (packet.right()) 1 else 0) - (if (packet.left()) 1 else 0)
            // One step per press: holding a key does not keep sliding.
            if (dir == 0 && !packet.jump()) { s.held = false; return@addListener }
            if (s.held) return@addListener
            s.held = true
            if (packet.jump()) ask(event.player, order[s.index]) else step(event.player, dir)
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
        MinecraftServer.getSchedulerManager().buildTask { if (sessions.isNotEmpty()) animate() }.repeat(TaskSchedule.tick(TURN)).schedule()
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
        if (figures.isEmpty()) { relight(); seat() }
        player.setInstance(world, Pos(SEATS_X.first() + 0.5, SEAT_TOP, 6.5)).thenRun {
            player.isInvisible = true
            camera.setInstance(world, camera(0.0)).thenRun {
                camera.addViewer(player)
                player.sendPacket(ChangeGameStatePacket(ChangeGameStatePacket.Reason.CHANGE_GAMEMODE, 3f))
                player.sendPacket(CameraPacket(camera.entityId))
            }
        }
    }

    fun isOpen(player: Player) = player.uuid in sessions

    /** Slide the camera one figure to the east (+1) or west (-1). */
    fun step(player: Player, dir: Int) {
        val s = sessions[player.uuid] ?: return
        val next = (s.index + dir).coerceIn(order.indices)
        if (next != s.index) { s.from = s.at; s.index = next; s.step = 0 }
    }

    /** [leave] false when the player is disconnecting (nowhere to send them). */
    fun close(player: Player, leave: Boolean = true) {
        val s = sessions.remove(player.uuid) ?: return
        player.closeDialog()
        player.sendPacket(CameraPacket(player.entityId))
        player.sendPacket(ChangeGameStatePacket(ChangeGameStatePacket.Reason.CHANGE_GAMEMODE, player.gameMode.ordinal.toFloat()))
        player.isInvisible = false
        s.camera.remove()
        if (leave) onLeave(player, s.back)
    }

    /** The keys under the view, every few ticks while the camp is open (the HUD stands down meanwhile). */
    fun hint(): Component = Component.text().append(word("A D", GOLD), word("  見る   ", MUTED), word("Space", GOLD),
        word("  選ぶ   ", MUTED), word("Shift", GOLD), word("  戻る", MUTED)).build()

    /** Space on a figure: a short confirm for the playable classes, nothing for the rest. */
    private fun ask(player: Player, job: CoreClass) {
        if (job !in PLAYABLE) return
        val yes = DialogActionButton(dialogText("決める", GOLD), null, 120, DialogAction.Custom(Key.key(NS, "choose"), StringBinaryTag.stringBinaryTag(job.name)))
        val no = DialogActionButton(dialogText("やめる", MUTED), null, 120, DialogAction.Custom(Key.key(NS, "cancel"), null))
        val meta = DialogMetadata(dialogText("${job.displayName}にする？", INK), dialogText(job.displayName, INK), true, false,
            DialogAfterAction.WAIT_FOR_RESPONSE, listOf(DialogBody.PlainMessage(dialogText("港でいつでも選び直せます", MUTED), 240)), emptyList())
        player.showDialog(Dialog.Confirmation(meta, yes, no))
    }

    /** Clicks from [ask]'s dialog; false when the click is not this scene's. */
    fun click(player: Player, key: Key, payload: BinaryTag?): Boolean {
        if (key.namespace() != NS) return false
        player.closeDialog()
        if (key.value() == "choose" && isOpen(player)) {
            val job = (payload as? StringBinaryTag)?.value()?.let { name -> PLAYABLE.firstOrNull { it.name == name } } ?: return true
            onChoose(player, job)
        }
        return true
    }

    /** The camera for a (fractional) seat: south of the bench, a little to the east and above, looking at the figure's chest. */
    private fun camera(seat: Double): Pos {
        val x = SEATS_X.first() + 0.5 + seat * SEAT_GAP
        val eye = Vec(x + 1.0, SEAT_TOP + 1.85, 0.5 + 2.4)
        val look = Vec(x + 0.4, SEAT_TOP + 0.75, 0.5)
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
            props(job, x)
        }
    }

    /** The weapon, its skill plates, the name above and the card beside one figure at block centre [x]. */
    private fun props(job: CoreClass, x: Double) {
        val playable = job in PLAYABLE
        val weapon = Entity(EntityType.ITEM_DISPLAY).apply {
            setNoGravity(true)
            (entityMeta as ItemDisplayMeta).apply {
                itemStack = weapon(job); displayContext = ItemDisplayMeta.DisplayContext.FIXED
                scale = Vec(WEAPON_SCALE); rightRotation = quat(0.0, 0.0, 1.0, -PI / 4)
            }
        }
        weapon.setInstance(world, Pos(x + WEAPON.x(), SEAT_TOP + WEAPON.y(), WEAPON.z()))
        weapons += weapon
        plates += CoreSkillCatalog.normalChoices(job).map { CoreSkillCatalog.skills(job)[it] }.map { skill ->
            Entity(EntityType.ITEM_DISPLAY).apply {
                setNoGravity(true)
                (entityMeta as ItemDisplayMeta).apply {
                    itemStack = ItemStack.of(Material.PAPER).withItemModel("projects:core_ui/${skill.icon}")
                    displayContext = ItemDisplayMeta.DisplayContext.FIXED
                    billboardRenderConstraints = AbstractDisplayMeta.BillboardConstraints.CENTER
                    scale = Vec(PLATE_SCALE)
                }
                setInstance(world, Pos(x + WEAPON.x(), SEAT_TOP + WEAPON.y(), WEAPON.z()))
            }
        }
        val name = Component.text().append(word(job.displayName, if (playable) GOLD else MUTED, "b8"))
        if (!playable) name.append(Component.newline(), word("開発中", MUTED))
        text(name.build(), Pos(x + NAME.x(), SEAT_TOP + NAME.y(), NAME.z()), NAME_SCALE, 0)
        text(card(job), Pos(x + CARD.x(), SEAT_TOP + CARD.y(), CARD.z()), CARD_SCALE, CARD_BACK)
    }

    /** Names and numbers only (藍 2026-11-03: no sentences in the UI). */
    internal fun card(job: CoreClass): Component {
        val skills = CoreSkillCatalog.normalChoices(job).map { CoreSkillCatalog.skills(job)[it] }
        val ults = CoreSkillCatalog.ultimateChoices(job)
        val nl = Component.newline()
        return Component.text().append(
            word(job.displayName, INK, "b8"), nl,
            word("資源 ", MUTED), word(job.resourceName, INK), nl,
            word("体力 ", MUTED), word("×%.1f".format(java.util.Locale.ROOT, job.healthFactor), INK),
            word("  防御 ", MUTED), word("${job.baseArmor.toInt()}", INK),
            word("  魔防 ", MUTED), word("${job.baseResist.toInt()}", INK), nl,
            word("スキル ", MUTED), word("${skills.size}", INK), word("  ウルト ", MUTED), word("${ults.size}", INK), nl,
            word(skills.joinToString(" ・ ") { it.name }, FAINT), nl, nl,
            if (job in PLAYABLE) Component.text().append(word("Space", GOLD), word("  選ぶ", MUTED)).build() else word("開発中", MUTED),
        ).build()
    }

    private fun text(content: Component, at: Pos, size: Double, background: Int) =
        Entity(EntityType.TEXT_DISPLAY).apply {
            setNoGravity(true)
            (entityMeta as TextDisplayMeta).apply {
                text = content; lineWidth = 230; backgroundColor = background; isShadow = background == 0
                billboardRenderConstraints = AbstractDisplayMeta.BillboardConstraints.CENTER
                scale = Vec(size)
            }
            setInstance(world, at)
            figures += this
        }

    /** One turn step: the weapons spin, the plates go round them; the client tweens between steps. */
    private fun animate() {
        phase += TURN
        val spin = phase * 2 * PI / WEAPON_TURN
        weapons.forEach { w ->
            (w.entityMeta as ItemDisplayMeta).apply {
                setNotifyAboutChanges(false)
                transformationInterpolationStartDelta = 0; transformationInterpolationDuration = TURN
                leftRotation = quat(0.0, 1.0, 0.0, spin)
                translation = Vec(0.0, 0.06 * sin(spin * 2), 0.0)
                setNotifyAboutChanges(true)
            }
        }
        val orbit = phase * 2 * PI / PLATE_TURN
        plates.forEach { ring ->
            ring.forEachIndexed { k, plate ->
                val a = orbit + 2 * PI * k / ring.size
                (plate.entityMeta as ItemDisplayMeta).apply {
                    setNotifyAboutChanges(false)
                    transformationInterpolationStartDelta = 0; transformationInterpolationDuration = TURN
                    translation = Vec(PLATE_RING * cos(a), PLATE_RING * sin(a), 0.0)
                    setNotifyAboutChanges(true)
                }
            }
        }
    }

    /** The exported regions carry no light: load the camp's chunks and light them once, or the clearing is black. */
    private fun relight() {
        val chunks = (-4..3).flatMap { cx -> (-4..3).map { cz -> world.loadChunk(cx, cz).join() } }
        LightingChunk.relight(world, chunks)
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
        const val NS = "projects_class"
        /** 2026-11-06 (司令塔): only these can be chosen for now; the others stand in the camp as 開発中. */
        val PLAYABLE = setOf(CoreClass.WARRIOR, CoreClass.MAGE)
        /** Bench centres (block x) and the top of the logs; matches class_camp.py's SEATS and Y0. */
        private val SEATS_X = List(6) { -20 + 8 * it }
        private const val SEAT_GAP = 8.0
        private const val SEAT_TOP = 66.0
        /** How far below the log top the marker sits so the rider's seat lands on the log. */
        private const val RIDE = 0.05
        private const val SLIDE = 14
        /** Offsets from a figure (x from its block centre, y from the log top, z absolute): east of it, west of it, above. */
        private val WEAPON = Vec(1.45, 0.95, 1.1)
        private val CARD = Vec(-0.85, 0.35, 1.3)
        private val NAME = Vec(0.25, 1.55, 0.6)
        private const val WEAPON_SCALE = 1.3
        private const val PLATE_SCALE = 0.32
        private const val PLATE_RING = 0.62
        private const val NAME_SCALE = 0.55
        private const val CARD_SCALE = 0.32
        private const val CARD_BACK = 0xD8101014.toInt()
        /** Ticks per animation step, and per full weapon turn / plate orbit. */
        private const val TURN = 4
        private const val WEAPON_TURN = 160
        private const val PLATE_TURN = 400

        private val INK = TextColor.color(0xECE6DC)
        private val MUTED = TextColor.color(0x9A948C)
        private val FAINT = TextColor.color(0x6E6A64)
        private val GOLD = TextColor.color(0xF2C84B)
        private val DIALOG = Key.key("projects_ui_polish05", "dialog")

        /** MaruMinya at the menu's GUI-scale-4 size (m6: words, b8: headings), unshifted. */
        private fun word(text: String, color: TextColor, style: String = "m6"): Component =
            Component.text(text, color).font(Key.key("projects_ui_polish05", "menu4_${style}_0"))
        private fun dialogText(text: String, color: TextColor): Component = Component.text(text, color).font(DIALOG)

        /** Quaternion (x, y, z, w) for [angle] radians about the unit axis (ax, ay, az). */
        private fun quat(ax: Double, ay: Double, az: Double, angle: Double): FloatArray {
            val s = sin(angle / 2)
            return floatArrayOf((ax * s).toFloat(), (ay * s).toFloat(), (az * s).toFloat(), cos(angle / 2).toFloat())
        }
    }
}
