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

/** Retained fixed screen. Local transforms change; no per-frame entity teleports. */
class UiRenderer(private val player: Player, private val origin: Pos) : AutoCloseable {
    private data class Panel(val box: Box, val color: Int, val depth: Double, val zoom: Double)
    private val entities=mutableMapOf<String,Entity>()
    private val panels=mutableMapOf<String,Panel>()
    private val content=mutableMapOf<String,Pair<UiNode,Double>>()
    private var wanted=mutableSetOf<String>()
    private val unspawned=mutableListOf<Pair<String,Entity>>()
    private var closed=false
    var zoom=1.0
    val size get()=entities.size
    private val geometry get()=UiGeometry(zoom)

    private fun entity(id: String,type: EntityType): Entity {
        wanted+=id
        return entities.getOrPut(id) {
            Entity(type).apply {
                setHasPhysics(false); setNoGravity(true); setAutoViewable(false)
                (entityMeta as AbstractDisplayMeta).apply {
                    setBrightness(15,15); setViewRange(1f)
                    setTransformationInterpolationDuration(0); setPosRotInterpolationDuration(0)
                    setBillboardRenderConstraints(AbstractDisplayMeta.BillboardConstraints.FIXED)
                }
                unspawned+=id to this
            }
        }
    }
    private fun spawnReady() {
        val pending=unspawned.toList(); unspawned.clear()
        pending.forEach { (id,e) ->
            // First packet contains complete transforms, never a flash at identity scale.
            e.setInstance(player.instance!!,origin.add(0.0,0.0,UiGeometry.DISTANCE).withView(180f,0f)).thenRun {
                if(closed || e.isRemoved || entities[id]!==e) e.remove() else e.addViewer(player)
            }
        }
    }
    private fun panel(id: String,b: Box,color: Int,depth: Double,interpolate: Boolean=false) {
        wanted+=id
        val next=Panel(b,color,depth,zoom)
        val previous=panels[id]
        if(previous==next) return
        entity(id,EntityType.TEXT_DISPLAY).editEntityMeta(TextDisplayMeta::class.java) { m ->
            if(previous==null) {
                m.setText(Component.text(" ")); m.setLineWidth(8); m.setTextOpacity(0)
                m.setUseDefaultBackground(false); m.setShadow(false)
            }
            m.setBackgroundColor(color)
            if(previous==null || previous.box!=b || previous.depth!=depth || previous.zoom!=zoom) {
                val transform=geometry.panel(b,depth)
                m.setTransformationInterpolationDuration(if(interpolate && previous!=null && previous.zoom==zoom) 1 else 0)
                m.setScale(transform.scale); m.setTranslation(transform.translation)
                m.setTransformationInterpolationStartDelta(0)
            }
        }
        panels[id]=next
    }
    private fun background(node: UiNode,hover: String?) {
        val color=node.background?:return
        val rgb=if(!node.enabled) 0x35383c else if(node.action!=null && node.id==hover) 0x866744 else color.removePrefix("#").toInt(16)
        panel(node.id+":bg",node.box,rgb or (0xff shl 24),node.depth*0.005)
    }
    fun render(scene: UiScene,hover: String?,pointer: UiPointer) {
        wanted=mutableSetOf()
        scene.nodes.forEach { node ->
            background(node,hover)
            val b=node.box
            val z=node.depth*0.005
            val id=node.id+if(node.item!=null) ":item" else ":text"
            if(node.item==null && node.text.isEmpty()) return@forEach
            wanted+=id
            if(content[id]==(node to zoom)) return@forEach
            if(node.item!=null) {
                entity(id,EntityType.ITEM_DISPLAY).editEntityMeta(ItemDisplayMeta::class.java) { m ->
                    m.setItemStack(ItemStack.of(requireNotNull(Material.fromKey(node.item))))
                    m.setDisplayContext(ItemDisplayMeta.DisplayContext.GUI)
                    val size=minOf(b.w,b.h)*geometry.unit(z+0.05)*0.8
                    m.setScale(Vec(size,size,size))
                    m.setTranslation(geometry.point(b.x+b.w/2,b.y+b.h/2,z+0.05))
                }
            } else {
                entity(id,EntityType.TEXT_DISPLAY).editEntityMeta(TextDisplayMeta::class.java) { m ->
                    var text=Component.text(node.text,TextColor.fromHexString(if(node.enabled) node.color else "#91969b"))
                    val bold=node.style["font-weight"]=="bold"
                    if(bold) text=text.decorate(TextDecoration.BOLD)
                    m.setText(text); m.setLineWidth(4000); m.setUseDefaultBackground(false); m.setBackgroundColor(0)
                    m.setShadow(false); m.setTextOpacity((-1).toByte())
                    val pixel=node.fontSize/8.0
                    val scale=pixel*geometry.unit(z+0.025)/0.025
                    m.setScale(Vec(scale,scale,scale))
                    val advance=UiGeometry.textAdvance(node.text,bold)*pixel
                    val align=node.style["text-align"]?:if(node.action!=null) "center" else "left"
                    val center=when(align){"center"->b.x+b.w/2;"right"->b.x+b.w-advance/2;else->b.x+advance/2}
                    // Vanilla translates text by (1-width/2,-9) before its .025 pixel transform.
                    m.setTranslation(geometry.point(center-pixel,b.y+(b.h-node.fontSize)/2+9*pixel,z+0.025))
                }
            }
            content[id]=node to zoom
        }
        cursor(pointer)
        entities.keys.filter { it !in wanted }.toList().forEach {
            entities.remove(it)?.remove(); panels.remove(it); content.remove(it)
        }
        spawnReady()
    }
    /** Hover changes at most two panel colors, not all text and model transforms. */
    fun hover(scene: UiScene,previous: String?,next: String?) {
        scene.nodes.filter { it.id==previous || it.id==next }.forEach { background(it,next) }
    }
    fun cursor(pointer: UiPointer) {
        panel("cursor-shadow",Box(pointer.x-1,pointer.y-1,5.0,15.0),0xff14171b.toInt(),0.4,true)
        panel("cursor-v",Box(pointer.x,pointer.y,2.0,12.0),0xffffdf9f.toInt(),0.41,true)
        panel("cursor-h",Box(pointer.x,pointer.y,10.0,2.0),0xffffdf9f.toInt(),0.42,true)
        spawnReady()
    }
    override fun close() {
        closed=true; entities.values.forEach(Entity::remove); entities.clear()
        panels.clear(); content.clear(); unspawned.clear()
    }
}
