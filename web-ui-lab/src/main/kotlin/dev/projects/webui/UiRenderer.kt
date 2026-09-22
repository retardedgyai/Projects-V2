package dev.projects.webui

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextColor
import net.kyori.adventure.text.format.TextDecoration
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

/** Private fixed screen: 800 authored pixels = 3.2 world blocks. Camera looks +Z. */
class UiRenderer(private val player: Player, private val origin: Pos) : AutoCloseable {
    private val entities = mutableMapOf<String, Entity>()
    private var wanted = mutableSetOf<String>()
    private var closed = false
    var zoom = 1.0
    val size get() = entities.size
    private val unit get() = 0.004 * zoom
    private fun perspective(depth: Double) = (2.2-depth)/2.2
    private fun position(x: Double, y: Double, depth: Double) =
        Pos(origin.x() + (400-x)*unit*perspective(depth), origin.y() + (240-y)*unit*perspective(depth), origin.z()+2.2-depth, 180f, 0f)

    private fun entity(id: String, type: EntityType): Entity {
        wanted += id
        return entities.getOrPut(id) {
            Entity(type).apply {
                setHasPhysics(false); setNoGravity(true); setAutoViewable(false)
                (entityMeta as AbstractDisplayMeta).apply {
                    setBrightness(15,15); setViewRange(1f)
                    setTransformationInterpolationDuration(0); setPosRotInterpolationDuration(0)
                    setBillboardRenderConstraints(AbstractDisplayMeta.BillboardConstraints.FIXED)
                }
                setInstance(player.instance!!, origin).thenRun {
                    if(closed || isRemoved) remove() else addViewer(player)
                }
            }
        }
    }
    private fun panel(id: String, b: Box, color: Int, depth: Double) {
        val e=entity(id,EntityType.TEXT_DISPLAY)
        val m=e.entityMeta as TextDisplayMeta
        m.setText(Component.text(" ")); m.setLineWidth(8); m.setTextOpacity(0)
        m.setUseDefaultBackground(false); m.setBackgroundColor(color); m.setShadow(false)
        // Existing ProjectS 26.2 text-display geometry: space background is 5x10 pixels.
        val u=unit*perspective(depth)
        m.setScale(Vec(b.w*u*8, b.h*u*4, 1.0))
        m.setTranslation(Vec(-b.w*u*0.1, b.h*u*0.5, 0.0))
        e.teleport(position(b.x+b.w/2,b.y+b.h/2,depth))
    }
    fun render(scene: UiScene, hover: String?, pointer: UiPointer) {
        wanted=mutableSetOf()
        scene.nodes.forEach { node ->
            val z=node.depth*0.005
            if(node.background != null) {
                var rgb=node.background!!.removePrefix("#").toInt(16)
                if(!node.enabled) rgb=0x35383c
                else if(node.action!=null && node.id==hover) rgb=0x866744
                panel(node.id+":bg",node.box,rgb or (0xff shl 24),z)
            }
            val b=node.box
            if(node.item!=null) {
                val e=entity(node.id+":item",EntityType.ITEM_DISPLAY)
                val m=e.entityMeta as ItemDisplayMeta
                m.setItemStack(ItemStack.of(requireNotNull(Material.fromKey(node.item))))
                m.setDisplayContext(ItemDisplayMeta.DisplayContext.GUI)
                val size=minOf(b.w,b.h)*unit*perspective(z+0.05)*0.8
                m.setScale(Vec(size,size,size))
                e.teleport(position(b.x+b.w/2,b.y+b.h/2,z+0.05))
            } else if(node.text.isNotEmpty()) {
                val e=entity(node.id+":text",EntityType.TEXT_DISPLAY)
                val m=e.entityMeta as TextDisplayMeta
                var text=Component.text(node.text,TextColor.fromHexString(if(node.enabled) node.color else "#91969b"))
                if(node.style["font-weight"]=="bold") text=text.decorate(TextDecoration.BOLD)
                m.setText(text); m.setLineWidth(4000); m.setUseDefaultBackground(false); m.setBackgroundColor(0)
                m.setShadow(false); m.setTextOpacity((-1).toByte())
                val font=node.fontSize
                val scale=font/8.0*unit*perspective(z+0.025)/0.025
                m.setScale(Vec(scale,scale,scale))
                // Text is centered by the client. Approximate advance only for left/right alignment.
                val advance=node.text.codePoints().toArray().sumOf { if(it>255) 8.0 else if(it==32) 4.0 else 6.0 }*font/8.0
                val align=node.style["text-align"]?:if(node.action!=null) "center" else "left"
                val center=when(align){"center"->b.x+b.w/2;"right"->b.x+b.w-advance/2;else->b.x+advance/2}
                e.teleport(position(center,b.y+(b.h-font)/2,z+0.025))
            }
        }
        cursor(pointer)
        entities.keys.filter { it !in wanted }.toList().forEach { entities.remove(it)?.remove() }
    }
    fun cursor(pointer: UiPointer) {
        panel("cursor-shadow",Box(pointer.x-1,pointer.y-1,5.0,15.0),0xff14171b.toInt(),0.4)
        panel("cursor-v",Box(pointer.x,pointer.y,2.0,12.0),0xffffdf9f.toInt(),0.41)
        panel("cursor-h",Box(pointer.x,pointer.y,10.0,2.0),0xffffdf9f.toInt(),0.42)
    }
    override fun close() { closed=true; entities.values.forEach(Entity::remove); entities.clear() }
}
