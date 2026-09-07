package dev.projects.server.coreloop

import net.minestom.server.coordinate.Pos
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.Entity
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.Player
import net.minestom.server.entity.metadata.display.ItemDisplayMeta
import net.minestom.server.instance.Instance
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import java.util.UUID
import java.util.WeakHashMap
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.*

/** Per-viewer, session-only preference. Never changes hits or another player's setting. */
internal object CoreCombatPresentation {
    enum class Detail(val label: String,val particleStride: Int) { FULL("華やか",1), SUBDUED("控えめ",2), MINIMAL("最小",4) }
    private val loaded=ConcurrentHashMap.newKeySet<UUID>()
    private val details=ConcurrentHashMap<UUID,Detail>()
    fun pack(player: Player,ready: Boolean) { if(ready) loaded+=player.uuid else loaded-=player.uuid }
    fun packed(player: Player)=player.uuid in loaded
    fun detail(player: Player)=details[player.uuid] ?: Detail.FULL
    fun cycle(player: Player): Detail=Detail.entries[(detail(player).ordinal+1)%Detail.entries.size].also { details[player.uuid]=it }
    fun forget(player: Player) { loaded-=player.uuid; details-=player.uuid }
}

/** Concrete scene consumer. Each display has an owner, a hard lifetime and a scene budget. */
internal class CoreCombatMeshes(private val owner: Player) {
    private data class Live(val entity: Entity,val instance: Instance,val part: CoreCombatMeshPart,
        val ownerStart: Pos,val baseYaw: Double,var age: Int=-2,var model: String="",val cancelled: AtomicBoolean=AtomicBoolean())
    private val live=mutableListOf<Live>()
    internal val size get()=live.size

    fun play(effect: CoreSkillEffect) {
        val instance=owner.instance ?: return
        if(!instance.players.any { CoreCombatPresentation.packed(it) && CoreCombatPresentation.detail(it)!=CoreCombatPresentation.Detail.MINIMAL && it.position.distanceSquared(effect.origin)<1600 }) return
        for(authored in CoreSkillChoreography.parts(effect).sortedBy { it.secondary }) {
            if(live.size>=OWNER_LIMIT) {
                // A new strike must not silently vanish behind old secondary afterglow.
                val tail=live.firstOrNull { it.part.secondary || it.age>it.part.durationTicks*.65 }
                if(tail!=null && !authored.secondary) {
                    tail.cancelled.set(true);tail.entity.remove();release(tail.instance);live.remove(tail)
                } else continue
            }
            if(!reserve(instance)) continue
            var part=authored
            if(part.ground) {
                val x=effect.origin.x()+part.offset.x(); val z=effect.origin.z()+part.offset.z()
                val base=floor(effect.origin.y()).toInt()
                val surface=(base+1 downTo base-2).firstOrNull { y ->
                    instance.getBlock(Pos(x,y.toDouble(),z)).isSolid && !instance.getBlock(Pos(x,y+1.0,z)).isSolid
                }
                if(surface!=null) part=part.copy(offset=Vec(part.offset.x(),surface+1.12-effect.origin.y(),part.offset.z()))
            }
            val entity=Entity(EntityType.ITEM_DISPLAY)
            entity.setHasPhysics(false);entity.setNoGravity(true);entity.setAutoViewable(false)
            val record=Live(entity,instance,part,owner.position,atan2(effect.direction.x(),effect.direction.z()))
            live+=record
            entity.editEntityMeta(ItemDisplayMeta::class.java) { meta ->
                record.model=CoreSkillChoreography.pose(part,0.0).model
                meta.setItemStack(ItemStack.of(Material.PAPER).withItemModel("projects:${record.model}"))
                meta.setDisplayContext(ItemDisplayMeta.DisplayContext.FIXED)
                meta.setBrightness(15,15);meta.setViewRange(1.0f)
                meta.setTransformationInterpolationDuration(1)
                // Send spawn before reveal; the animation clock waits for setInstance to finish.
                meta.setScale(Vec.ZERO);meta.setTranslation(part.offset)
                meta.setLeftRotation(CoreCombatMeshArt.rotation(part.yaw,part.pitch,part.roll))
                meta.setRightRotation(CoreCombatMeshArt.vanillaItemCorrection)
            }
            entity.setInstance(instance,Pos(effect.origin.x(),effect.origin.y(),effect.origin.z())).whenComplete { _,failure ->
                if(failure!=null || record.cancelled.get() || entity.isRemoved || owner.instance!==instance) entity.remove()
            }
        }
    }

    fun tick() {
        val iterator=live.iterator()
        var otherVisible=0
        while(iterator.hasNext()) {
            val v=iterator.next();val p=v.part
            if(owner.isRemoved || owner.instance!==v.instance || v.entity.isRemoved || v.age>=p.delayTicks+p.durationTicks) {
                v.cancelled.set(true);v.entity.remove();release(v.instance);iterator.remove();continue
            }
            if(v.entity.instance!==v.instance) continue
            val pose=CoreSkillChoreography.pose(p,v.age.toDouble())
            val facing=if(p.followOwner) atan2(owner.position.direction().x(),owner.position.direction().z())-v.baseYaw else 0.0
            val at=pose.offset
            val offset=if(p.followOwner) Vec(cos(facing)*at.x()+sin(facing)*at.z(),at.y(),-sin(facing)*at.x()+cos(facing)*at.z())
                .add(owner.position.sub(v.ownerStart).asVec()) else at
            v.entity.editEntityMeta(ItemDisplayMeta::class.java) { meta ->
                meta.setTransformationInterpolationStartDelta(0)
                meta.setScale(if(pose.visible) pose.scale else Vec.ZERO)
                meta.setTranslation(offset)
                meta.setLeftRotation(CoreCombatMeshArt.rotation(pose.yaw+facing,pose.pitch,pose.roll))
                if(v.model!=pose.model) {
                    meta.setItemStack(ItemStack.of(Material.PAPER).withItemModel("projects:${pose.model}"));v.model=pose.model
                }
            }
            if(v.entity.instance===v.instance) {
                val allowed=v.instance.players.filter { viewer ->
                    val detail=CoreCombatPresentation.detail(viewer)
                    CoreCombatPresentation.packed(viewer) && detail!=CoreCombatPresentation.Detail.MINIMAL &&
                        (!p.secondary || detail==CoreCombatPresentation.Detail.FULL && viewer===owner) &&
                        viewer.position.distanceSquared(v.entity.position)<=(if(viewer===owner) 1600.0 else 256.0) &&
                        (viewer===owner || otherVisible<8)
                }.toSet()
                v.entity.viewers.toList().filter { it !in allowed }.forEach { v.entity.removeViewer(it) }
                allowed.filter { it !in v.entity.viewers }.forEach { v.entity.addViewer(it) }
                if(!p.secondary) otherVisible++
            }
            v.age++
        }
    }

    fun cancel() { live.forEach { it.cancelled.set(true);it.entity.remove();release(it.instance) };live.clear() }
    companion object {
        const val OWNER_LIMIT=48
        const val SCENE_LIMIT=384
        private val sceneCounts=WeakHashMap<Instance,Int>()
        private fun reserve(instance: Instance): Boolean=synchronized(sceneCounts) {
            val n=sceneCounts[instance] ?: 0
            if(n>=SCENE_LIMIT) false else { sceneCounts[instance]=n+1;true }
        }
        private fun release(instance: Instance)=synchronized(sceneCounts) {
            val n=(sceneCounts[instance] ?: 1)-1
            if(n<=0) sceneCounts.remove(instance) else sceneCounts[instance]=n
        }
    }
}
