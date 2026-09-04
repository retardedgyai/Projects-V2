package dev.projects.server.coreloop

import net.kyori.adventure.sound.Sound
import net.kyori.adventure.text.format.NamedTextColor
import net.minestom.server.coordinate.Pos
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.Entity
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.Player
import net.minestom.server.entity.metadata.display.AbstractDisplayMeta
import net.minestom.server.entity.metadata.display.ItemDisplayMeta
import net.minestom.server.entity.metadata.display.TextDisplayMeta
import net.minestom.server.entity.metadata.other.InteractionMeta
import net.minestom.server.instance.InstanceContainer
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.sound.SoundEvent
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.ceil

/** Optional discovery sites have something to open, not an invisible radius-only payment. */
internal class CoreMapCaches(
    private val owner: Player,
    private val instance: InstanceContainer,
    positions: List<Pos>,
    private val guarded: (Pos) -> Boolean,
    private val opened: (Int, Pos) -> Unit,
) {
    private data class Cache(val index: Int, val position: Pos, val entities: List<Entity>)
    private val byEntity = ConcurrentHashMap<Int, Cache>()
    private val caches = ConcurrentHashMap<Int, Cache>()

    init {
        // Resolve the whole set before spawning anything: one invalid site cannot leak half a set.
        val resolved = positions.map { QuestCombatPlacement.resolve(instance, it) }
        resolved.forEachIndexed { index, position ->
            val chest = Entity(EntityType.ITEM_DISPLAY).apply {
                setHasPhysics(false); setNoGravity(true)
                editEntityMeta(ItemDisplayMeta::class.java) { meta ->
                    meta.setItemStack(ItemStack.of(Material.CHEST)); meta.setScale(Vec(1.1, 1.1, 1.1))
                    meta.setDisplayContext(ItemDisplayMeta.DisplayContext.FIXED)
                }
                setInstance(this@CoreMapCaches.instance, position.add(0.0, 0.5, 0.0))
            }
            val label = Entity(EntityType.TEXT_DISPLAY).apply {
                setHasPhysics(false); setNoGravity(true)
                editEntityMeta(TextDisplayMeta::class.java) { meta ->
                    meta.setText(CoreLoopItems.text("遺された宝箱\n右クリックで開く", NamedTextColor.GOLD))
                    meta.setBillboardRenderConstraints(AbstractDisplayMeta.BillboardConstraints.CENTER)
                    meta.setScale(Vec(0.7, 0.7, 0.7)); meta.setShadow(true); meta.setBackgroundColor(0x880e1020.toInt())
                    meta.setViewRange(0.6f)
                }
                setInstance(this@CoreMapCaches.instance, position.add(0.0, 1.6, 0.0))
            }
            val hitbox = Entity(EntityType.INTERACTION).apply {
                setHasPhysics(false); setNoGravity(true)
                editEntityMeta(InteractionMeta::class.java) { meta -> meta.setWidth(1.5f); meta.setHeight(1.5f); meta.setResponse(true) }
                setInstance(this@CoreMapCaches.instance, position)
            }
            val cache = Cache(index, position, listOf(chest, label, hitbox))
            caches[index] = cache
            cache.entities.forEach { byEntity[it.entityId] = cache }
        }
    }

    fun interact(player: Player, target: Entity): Boolean {
        val cache = byEntity[target.entityId] ?: return false
        if (player !== owner || player.instance !== instance || player.position.distanceSquared(cache.position) > 5.5 * 5.5) return true
        val from = player.position.add(0.0, player.eyeHeight, 0.0)
        val to = cache.position.add(0.0, 0.6, 0.0)
        val steps = ceil(from.distance(to) * 4).toInt().coerceAtLeast(1)
        if ((1 until steps).any { instance.getBlock(from.add(to.sub(from).mul(it.toDouble() / steps))).isSolid }) return true
        if (guarded(cache.position)) {
            player.sendMessage(CoreLoopItems.text("近くの敵が宝箱を守っています。先に倒してください。", NamedTextColor.YELLOW))
            return true
        }
        if (!caches.remove(cache.index, cache)) return true
        cache.entities.forEach { byEntity.remove(it.entityId); it.remove() }
        player.playSound(Sound.sound(SoundEvent.BLOCK_CHEST_OPEN, Sound.Source.BLOCK, 0.9f, 1f))
        opened(cache.index, cache.position)
        return true
    }

    fun dispose() { caches.values.forEach { c -> c.entities.forEach { it.remove() } }; caches.clear(); byEntity.clear() }
}
