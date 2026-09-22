package dev.projects.server.coreloop

import dev.projects.server.CombatTarget
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.key.Key
import net.minestom.server.coordinate.Pos
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.*
import net.minestom.server.entity.metadata.display.AbstractDisplayMeta
import net.minestom.server.entity.metadata.display.TextDisplayMeta
import java.util.UUID

/** Marks belong to the attacker, not globally to the mob. Only that attacker sees the label. */
internal class CoreWarriorMarkDisplay(private val owner:Player) {
    private val labels=mutableMapOf<UUID,Entity>()
    internal val size get()=labels.size
    fun update(targets:List<CombatTarget>,state:CoreClassState,tick:Long) {
        val scene=owner.instance ?: run { clear();return }
        val selected=targets.filter { state.markRemaining(it.id,tick)>0 && it.position.distanceSquared(owner.position)<=24*24 }
            .sortedBy { it.position.distanceSquared(owner.position) }.take(12)
        val ids=selected.map { it.id }.toSet()
        labels.entries.removeIf { (id,e) ->
            if(id !in ids || e.isRemoved || e.instance!=null && e.instance!==scene) { e.remove();true } else false
        }
        for(target in selected) {
            val at=position(target,owner.position.add(0.0,owner.eyeHeight,0.0))
            val entity=labels[target.id] ?: Entity(EntityType.TEXT_DISPLAY).also { e ->
                e.setHasPhysics(false);e.setNoGravity(true);e.setAutoViewable(false)
                e.editEntityMeta(TextDisplayMeta::class.java) { meta ->
                    meta.setBillboardRenderConstraints(AbstractDisplayMeta.BillboardConstraints.CENTER)
                    meta.setScale(Vec(.65,.65,.65));meta.setShadow(true);meta.setBackgroundColor(0)
                }
                labels[target.id]=e
                e.setInstance(scene,at).whenComplete { _,failure ->
                    if(failure!=null || owner.instance!==scene || labels[target.id]!==e || e.isRemoved) e.remove()
                    else e.addViewer(owner)
                }
            }
            val seconds=(state.markRemaining(target.id,tick)+19)/20
            val icon=if(CoreCombatPresentation.packed(owner)) Component.text('\uE001',NamedTextColor.WHITE)
                .font(Key.key("projects","warrior_mark")) else Component.text("◆",NamedTextColor.RED)
            entity.editEntityMeta(TextDisplayMeta::class.java) { it.setText(icon.append(Component.text(" ${seconds}秒",NamedTextColor.WHITE)
                .font(Key.key("minecraft","default")))) }
            if(entity.instance===scene && entity.position.distanceSquared(at)>.0025) entity.teleport(at)
        }
    }
    fun clear() { labels.values.forEach { it.remove() };labels.clear() }
    companion object {
        /** Separate from the vanilla name in the camera plane, including looking up at large mobs. */
        internal fun position(target:CombatTarget,eye:net.minestom.server.coordinate.Point):Pos {
            val name=Vec(target.position.x(),target.position.y()+target.halfExtent.y()+.5,target.position.z())
            val ray=name.sub(Vec(eye.x(),eye.y(),eye.z()))
            val horizontal=kotlin.math.hypot(ray.x(),ray.z())
            val up=if(horizontal<.001) Vec(0.0,1.0,0.0) else
                Vec(-ray.x()*ray.y()/horizontal,horizontal,-ray.z()*ray.y()/horizontal).normalize()
            val at=name.add(up.mul(.7))
            return Pos(at.x(),at.y(),at.z())
        }
    }
}
