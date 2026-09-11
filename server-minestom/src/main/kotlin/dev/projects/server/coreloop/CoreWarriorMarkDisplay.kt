package dev.projects.server.coreloop

import dev.projects.server.CombatTarget
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
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
            val at=Pos(target.position.x(),target.position.y()+target.halfExtent.y()+.5,target.position.z())
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
            entity.editEntityMeta(TextDisplayMeta::class.java) { it.setText(Component.text("印 ${seconds}秒",NamedTextColor.RED)) }
            if(entity.instance===scene && entity.position.distanceSquared(at)>.0025) entity.teleport(at)
        }
    }
    fun clear() { labels.values.forEach { it.remove() };labels.clear() }
}
