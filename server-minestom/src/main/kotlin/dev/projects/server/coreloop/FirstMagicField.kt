package dev.projects.server.coreloop

import dev.projects.server.questmap.VerdantRoadQuestRuntime
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
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import java.util.concurrent.CompletableFuture

/** Distinct, unobstructing anomalies along the quest road. A pickup is saved before it vanishes. */
internal class FirstMagicField(
    private val player: Player,
    private val runtime: VerdantRoadQuestRuntime,
    private val collect: (AnomalousMaterial) -> CompletableFuture<FirstMagicChange>,
) {
    private class Find(val material: AnomalousMaterial, val position: Pos, val displays: List<Entity>) {
        var pending = false
        var claimed = false
    }
    private val finds: List<Find>
    private var closed = false

    init {
        val road = runtime.plan.mainRoute
        finds = AnomalousMaterial.entries.mapIndexed { index, material ->
            val point = road[(road.lastIndex * (index + 1) / 8).coerceIn(1, road.lastIndex)]
            val position = Pos(point.x + 0.5, runtime.plan.heightAt(point) + 1.15, point.z + 0.5)
            val icon = Entity(EntityType.ITEM_DISPLAY).apply {
                setNoGravity(true); setHasPhysics(false)
                editEntityMeta(ItemDisplayMeta::class.java) { meta ->
                    meta.setItemStack(ItemStack.of(when (material) {
                        AnomalousMaterial.MOONBELL -> Material.BLUE_ORCHID
                        AnomalousMaterial.EMBER_MOSS -> Material.RED_MUSHROOM
                        AnomalousMaterial.HOLLOW_CRYSTAL -> Material.AMETHYST_SHARD
                        AnomalousMaterial.WARM_ORE -> Material.RAW_COPPER
                        AnomalousMaterial.TIDEWING_FEATHER -> Material.FEATHER
                        AnomalousMaterial.WITHERED_CORE -> Material.ECHO_SHARD
                    }))
                    meta.setDisplayContext(ItemDisplayMeta.DisplayContext.GROUND)
                    meta.setScale(Vec(1.3, 1.3, 1.3))
                    meta.setBillboardRenderConstraints(AbstractDisplayMeta.BillboardConstraints.CENTER)
                    meta.setBrightness(15, 15)
                }
                setInstance(runtime.instance, position)
            }
            val label = Entity(EntityType.TEXT_DISPLAY).apply {
                setNoGravity(true); setHasPhysics(false)
                editEntityMeta(TextDisplayMeta::class.java) { meta ->
                    meta.setText(Component.text("?  ${material.label}", NamedTextColor.AQUA)
                        .append(Component.newline()).append(Component.text("近づいて採取", NamedTextColor.GRAY)))
                    meta.setBillboardRenderConstraints(AbstractDisplayMeta.BillboardConstraints.CENTER)
                    meta.setScale(Vec(0.6, 0.6, 0.6)); meta.setShadow(true)
                    meta.setBackgroundColor(0x780d171b); meta.setViewRange(0.5f)
                }
                setInstance(runtime.instance, position.add(0.0, 0.95, 0.0))
            }
            Find(material, position, listOf(icon, label))
        }
    }

    fun tick() {
        if (closed || player.instance !== runtime.instance || !player.isOnline) return
        finds.firstOrNull { !it.pending && !it.claimed && player.position.distanceSquared(it.position) < 2.4 * 2.4 }?.let { find ->
            find.pending = true
            collect(find.material).whenComplete { change, failure ->
                MinecraftServer.getSchedulerManager().scheduleNextTick {
                    if (closed) return@scheduleNextTick
                    if (failure == null && change?.changed == true) {
                        find.claimed = true
                        find.displays.forEach(Entity::remove)
                        if (player.isOnline) player.sendMessage(CoreLoopItems.text(change.message, NamedTextColor.AQUA))
                    } else {
                        find.pending = false
                        if (player.isOnline) player.sendMessage(CoreLoopItems.text(change?.message ?: "採取を保存できませんでした", NamedTextColor.RED))
                    }
                }
            }
        }
    }

    fun dispose() { closed = true; finds.forEach { it.displays.forEach(Entity::remove) } }
}
