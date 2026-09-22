package dev.projects.modellab

import net.kyori.adventure.key.Key
import net.kyori.adventure.sound.Sound
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.EntityCreature
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.Player
import net.minestom.server.entity.PlayerHand
import net.minestom.server.entity.attribute.Attribute
import net.minestom.server.entity.damage.DamageType
import net.minestom.server.instance.Instance
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.tag.Tag
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.floor

/** Opt-in training only. Input is marshalled to one instance tick; never loads production saves. */
class IceFangTraining(private val bundle: ModelBundle, private val instance: Instance) : AutoCloseable {
    private val tag = Tag.Boolean("projects:ice_fang_training")
    private val sessions = mutableMapOf<UUID, Session>()
    private val input = ConcurrentLinkedQueue<() -> Unit>()
    private var tick = 0L
    private data class Session(val player: Player, var mana: Int = 100, var ready: Long = 0,
                               val targets: MutableList<EntityCreature> = mutableListOf(), var cast: Cast? = null)
    private data class Cast(val started: Long, val origin: Pos, val teeth: List<IceFangPlan.Tooth>,
                            val actors: MutableMap<Int, BossModelActor> = mutableMapOf(), val hit: MutableSet<Int> = mutableSetOf())
    fun equip(player: Player) { input.add {
        if (player.instance === instance && !player.isRemoved) {
            clear(player.uuid)
            val session = Session(player)
            sessions[player.uuid] = session
            val wand = ItemStack.builder(Material.BLAZE_ROD).customName(Component.text("氷牙の連鎖", NamedTextColor.AQUA))
                .lore(listOf("右クリック：正面7mへ氷牙を連ねる", "威力 40 + AP80% = 88（訓練AP60）",
                    "敵1体につき1回命中 / マナ20 / 再使用4秒", "壁で停止 / 空中使用不可", "コンパス／Shift＋F：リセット・終了")
                    .map { Component.text(it, NamedTextColor.GRAY) }).set(tag, true).build()
            player.inventory.setItemStack(0,wand)
            player.setHeldItemSlot(0)
            spawnTargets(session)
            player.sendMessage(Component.text("氷牙の連鎖：杖を右クリック。手前から奥へ発生し、標的を1回ずつ攻撃します。"))
        }
    } }
    fun remove(player: Player) { input.add { clear(player.uuid) } }
    internal fun mana(player: Player): Int? = sessions[player.uuid]?.mana
    fun uses(player: Player, hand: PlayerHand) = hand == PlayerHand.MAIN && player.itemInMainHand.getTag(tag) == true
    fun requestCast(player: Player) { input.add { sessions[player.uuid]?.let(::cast) } }

    private fun spawnTargets(session: Session) {
        val origin = session.player.position
        val d = IceFangPlan.direction(origin.yaw())
        for ((i, distance) in listOf(2.0, 4.5, 7.0).withIndex()) {
            val pos = Pos(origin.x()+d.x*distance, 1.0, origin.z()+d.z*distance)
            // Do not place training targets inside an existing obstruction.
            if (!corridorClear(IceFangPlan.Point(pos.x(), 1.0, pos.z()))) continue
            val target = EntityCreature(EntityType.HUSK)
            target.getAttribute(Attribute.MAX_HEALTH).baseValue = 528.0
            target.health = 528f
            target.setNoGravity(true)
            target.setHasPhysics(false)
            target.isCustomNameVisible = true
            target.customName = Component.text("訓練標的${i+1} 528 / 528", NamedTextColor.WHITE)
            target.setInstance(instance, pos)
            session.targets += target
        }
    }
    private fun corridorClear(p: IceFangPlan.Point): Boolean {
        // Laboratory is deliberately flat: require loaded solid floor, check the full fang width/height.
        for (x in listOf(-1.0, 0.0, 1.0)) for (z in listOf(-1.0, 0.0, 1.0)) {
            val bx = floor(p.x+x).toInt(); val bz = floor(p.z+z).toInt()
            if (instance.getChunk(bx shr 4, bz shr 4) == null) return false
            if (!instance.getBlock(bx, 0, bz).isSolid) return false
            for (y in 1..4) if (instance.getBlock(bx, y, bz).isSolid) return false
        }
        return true
    }
    private fun cast(session: Session) {
        val player = session.player
        if (player.instance !== instance || player.isRemoved || !uses(player, PlayerHand.MAIN)) return
        if (tick < session.ready || session.cast != null) return
        if (session.mana < IceFangPlan.COST) {
            player.sendMessage(Component.text("マナが足りません。", NamedTextColor.RED)); return
        }
        if (!player.isOnGround || player.isFlying || kotlin.math.abs(player.position.y()-1.0) > .02) {
            player.sendMessage(Component.text("地面に降りてから使用してください。", NamedTextColor.YELLOW)); return
        }
        val origin = player.position
        val teeth = IceFangPlan.path(IceFangPlan.Point(origin.x(), 1.0, origin.z()), origin.yaw(), ::corridorClear)
        if (teeth.isEmpty()) {
            player.sendMessage(Component.text("前方に氷牙を出せる空間がありません。", NamedTextColor.YELLOW)); return
        }
        session.cast = Cast(tick, origin, teeth)
        session.mana -= IceFangPlan.COST
        session.ready = tick + IceFangPlan.COOLDOWN
        player.swingMainHand()
        sound(origin, "block.amethyst_block.resonate", .65f, .85f)
    }
    @Synchronized fun tick() {
        tick++
        while (true) (input.poll() ?: break).invoke()
        sessions.values.toList().forEach { session ->
            val player = session.player
            if (player.instance !== instance || player.isRemoved) { clear(player.uuid); return@forEach }
            if (tick % 10L == 0L) {
                session.mana = (session.mana+2).coerceAtMost(100)
                val remaining = (session.ready-tick).coerceAtLeast(0)/20.0
                player.sendActionBar(Component.text("氷牙の連鎖  |  マナ ${session.mana}/100  |  " +
                    if (remaining == 0.0) "使用可能" else "あと %.1f秒".format(remaining), NamedTextColor.AQUA))
            }
            session.cast?.let { cast ->
                val age = (tick-cast.started).toInt()
                cast.teeth.forEachIndexed { index, tooth ->
                    val local = age-tooth.start
                    val pos = Pos(tooth.point.x, tooth.point.y, tooth.point.z, cast.origin.yaw(), 0f)
                    if (local == 0) {
                        // If the world changed after the cast, cancel this and all subsequent sections.
                        val available = IceFangPlan.path(IceFangPlan.Point(cast.origin.x(), 1.0, cast.origin.z()),
                            cast.origin.yaw(), ::corridorClear)
                        if (index >= available.size) { finish(session); return@let }
                        val actor = BossModelActor(bundle.definition(IceFangPlan.MODELS[index]), instance, pos, tooth.size)
                        actor.move(pos)
                        actor.play("erupt")
                        cast.actors[index] = actor
                    }
                    if (local == IceFangPlan.HIT_DELAY && cast.actors.containsKey(index)) {
                        sound(pos, "block.glass.break", .8f, 1.25f-index*.12f)
                        sound(pos, "block.deepslate.break", .4f, .8f)
                        session.targets.filterNot { it.isRemoved || it.isDead || it.entityId in cast.hit }.forEach { target ->
                            val p = target.position
                            if (IceFangPlan.hits(tooth, IceFangPlan.Point(p.x(), p.y(), p.z())) && corridorClear(tooth.point)) {
                                cast.hit += target.entityId
                                target.damage(DamageType.MAGIC, IceFangPlan.DAMAGE.toFloat())
                                target.customName = Component.text("訓練標的 ${target.health.toInt().coerceAtLeast(0)} / 528  −88", NamedTextColor.AQUA)
                            }
                        }
                    }
                    if (local == 22 && cast.actors.containsKey(index)) sound(pos, "block.amethyst_cluster.break", .6f, 1.4f)
                    if (local >= IceFangPlan.LIFETIME) cast.actors.remove(index)?.close()
                }
                cast.actors.values.forEach(BossModelActor::syncViewers)
                if (age >= cast.teeth.last().start + IceFangPlan.LIFETIME) finish(session)
            }
        }
    }
    private fun sound(pos: Pos, name: String, volume: Float, pitch: Float) {
        val sound = Sound.sound(Key.key("minecraft:$name"), Sound.Source.PLAYER, volume, pitch)
        instance.players.filter { it.position.distanceSquared(pos) < 32*32 }.forEach { it.playSound(sound, pos) }
    }
    private fun finish(session: Session) {
        session.cast?.actors?.values?.forEach(BossModelActor::close)
        session.cast = null
    }
    private fun clear(id: UUID) {
        sessions.remove(id)?.let { session ->
            finish(session)
            session.targets.forEach { it.remove() }
            if (!session.player.isRemoved) {
                for (slot in 0 until session.player.inventory.size) {
                    if (session.player.inventory.getItemStack(slot).getTag(tag) == true)
                        session.player.inventory.setItemStack(slot,ItemStack.AIR)
                }
                session.player.sendActionBar(Component.empty())
            }
        }
    }
    @Synchronized override fun close() {
        input.clear()
        sessions.keys.toList().forEach(::clear)
    }
}
