package dev.projects.server.coreloop

import net.kyori.adventure.sound.Sound
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Pos
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.Entity
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.Player
import net.minestom.server.entity.metadata.display.AbstractDisplayMeta
import net.minestom.server.entity.metadata.display.ItemDisplayMeta
import net.minestom.server.entity.metadata.display.TextDisplayMeta
import net.minestom.server.instance.InstanceContainer
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.network.packet.server.play.ParticlePacket
import net.minestom.server.particle.Particle
import net.minestom.server.sound.SoundEvent
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/** Real visible, proximity-collected loot. The display is never the ownership record. */
internal class CoreWorldLoot(
    private val owner: Player,
    private val instance: InstanceContainer,
    private val run: CoreActiveRun,
    private val reward: (CoreAction.AffixLoot) -> CompletableFuture<CoreTransactionResult>,
    private val inventoryChanged: () -> Unit,
) {
    private class Drop(val source: String, val kind: CoreLootKind, val position: Pos,
        val currencies: Map<CoreCraftingCurrency, Long>, val entities: List<Entity>) {
        val collecting = AtomicBoolean()
        @Volatile var future: CompletableFuture<CoreTransactionResult>? = null
    }
    private val drops = ConcurrentHashMap<String, Drop>()
    private val seenSources = ConcurrentHashMap.newKeySet<String>()
    private var ticks = 0L
    private var disposed = false

    fun spawn(source: String, kind: CoreLootKind, deathPosition: Pos) {
        if (disposed || !seenSources.add(source)) return
        val currencies = CoreCraftingCatalog.rollLoot(run, source, kind)
        val surface = (0..7).map { deathPosition.sub(0.0, it.toDouble(), 0.0) }
            .firstOrNull { instance.getBlock(it.sub(0.0, 0.2, 0.0)).isSolid } ?: deathPosition
        val position = Pos(surface.x(), surface.blockY() + 0.35, surface.z())
        val item = Entity(EntityType.ITEM_DISPLAY).apply {
            setNoGravity(true); setHasPhysics(false)
            editEntityMeta(ItemDisplayMeta::class.java) { meta ->
                meta.setItemStack(ItemStack.of(currencies.keys.firstOrNull()?.let(CoreLoopItems::currencyMaterial) ?: Material.GLOWSTONE_DUST))
                meta.setDisplayContext(ItemDisplayMeta.DisplayContext.GROUND)
                meta.setScale(Vec(1.2, 1.2, 1.2)); meta.setBrightness(15, 15)
                meta.setBillboardRenderConstraints(AbstractDisplayMeta.BillboardConstraints.CENTER)
            }
            setInstance(this@CoreWorldLoot.instance, position)
        }
        val summary = if (currencies.isEmpty()) "刻印粉 +${CoreAffixCatalog.lootDust(kind)}  / 戦利品券 +${CoreAffixCatalog.lootTokens(kind)}"
            else currencies.entries.joinToString(" / ") { "${it.key.displayName} ×${it.value}" }
        val label = Entity(EntityType.TEXT_DISPLAY).apply {
            setNoGravity(true); setHasPhysics(false)
            editEntityMeta(TextDisplayMeta::class.java) { meta ->
                meta.setText(CoreLoopItems.text(summary, if (currencies.isEmpty()) NamedTextColor.GOLD else NamedTextColor.LIGHT_PURPLE)
                    .append(Component.newline()).append(CoreLoopItems.text("近づいて回収", NamedTextColor.GRAY)))
                meta.setBillboardRenderConstraints(AbstractDisplayMeta.BillboardConstraints.CENTER)
                meta.setScale(Vec(0.65, 0.65, 0.65)); meta.setShadow(true); meta.setBackgroundColor(0x880e1020.toInt())
                meta.setViewRange(0.5f)
            }
            setInstance(this@CoreWorldLoot.instance, position.add(0.0, 0.75, 0.0))
        }
        val drop = Drop(source, kind, position, currencies, listOf(item, label))
        if (drops.putIfAbsent(source, drop) != null) drop.entities.forEach { it.remove() }
        else owner.playSound(Sound.sound(if (currencies.isEmpty()) SoundEvent.ENTITY_ITEM_PICKUP else SoundEvent.BLOCK_AMETHYST_BLOCK_CHIME,
            Sound.Source.PLAYER, 0.65f, if (kind == CoreLootKind.BOSS) 0.8f else 1.2f), position.x(), position.y(), position.z())
    }

    fun tick() {
        if (disposed) return
        ticks++
        drops.values.forEach { drop ->
            if (owner.isOnline && owner.instance === instance && owner.position.distanceSquared(drop.position) < 2.6 * 2.6) collect(drop)
            if (ticks % 16 == 0L && drop.currencies.isNotEmpty() && !drop.collecting.get()) {
                instance.sendGroupedPacket(ParticlePacket(Particle.END_ROD, drop.position.add(0.0, 0.5, 0.0), Vec(0.05, 0.5, 0.05), 0.001f, 3))
            }
        }
    }

    @Synchronized
    private fun collect(drop: Drop): CompletableFuture<CoreTransactionResult> {
        drop.future?.let { return it }
        drop.collecting.set(true)
        val future = try { reward(CoreAction.AffixLoot(run.id, drop.source, drop.kind)) }
            catch (failure: Exception) { CompletableFuture.failedFuture(failure) }
        drop.future = future
        future.whenComplete { result, error ->
            MinecraftServer.getSchedulerManager().scheduleNextTick {
                if (error == null && result?.successful == true) {
                    drops.remove(drop.source, drop)
                    drop.entities.forEach { it.remove() }
                    if (!disposed && owner.isOnline && owner.instance === instance) {
                        owner.playSound(Sound.sound(SoundEvent.ENTITY_EXPERIENCE_ORB_PICKUP, Sound.Source.PLAYER, 0.6f, 1.25f))
                        owner.sendMessage(CoreLoopItems.text(if (drop.currencies.isEmpty())
                            "戦利品回収：刻印粉 +${CoreAffixCatalog.lootDust(drop.kind)} / 戦利品券 +${CoreAffixCatalog.lootTokens(drop.kind)}"
                            else "回収：${drop.currencies.entries.joinToString(" / ") { "${it.key.displayName} ×${it.value}" }}。刻印工房で使用できます。", NamedTextColor.GOLD))
                        inventoryChanged()
                    }
                } else { drop.collecting.set(false); drop.future = null }
            }
        }
        return future
    }

    /** Stop combat producers first. Return/disconnect auto-recover visible, uncollected drops. */
    fun collectAll(): CompletableFuture<Void> = CompletableFuture.allOf(*drops.values.map(::collect).toTypedArray())
    fun remainingCount(): Int = drops.size
    fun dispose() { disposed = true; drops.values.forEach { d -> d.entities.forEach { it.remove() } }; drops.clear(); seenSources.clear() }
}
