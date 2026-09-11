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
        val ownerStart: Pos,val baseYaw: Double,var age: Int=-2,var model: String="",val cancelled: AtomicBoolean=AtomicBoolean(),
        var lastUpdateNanos: Long=0,var maxUpdateGapNanos: Long=0,var updateCount: Int=0)
    private val live=mutableListOf<Live>()
    internal val size get()=live.size

    fun play(effect: CoreSkillEffect) {
        val instance=owner.instance ?: return
        val immediateContour=effect.job==CoreClass.WARRIOR &&
            (effect.sceneId in CoreApprovedNormalV3.sceneIds || effect.sceneId in CoreWarriorBladeChoreography.sceneIds && effect.sceneId!="dash")
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
            val record=Live(entity,instance,part,owner.position,atan2(effect.direction.x(),effect.direction.z()),
                age=if(immediateContour) 0 else -2)
            live+=record
            entity.editEntityMeta(ItemDisplayMeta::class.java) { meta ->
                record.model=CoreSkillChoreography.pose(part,0.0).model
                meta.setItemStack(ItemStack.of(Material.PAPER).withItemModel("projects:${record.model}"))
                meta.setDisplayContext(ItemDisplayMeta.DisplayContext.FIXED)
                meta.setBrightness(15,15);meta.setViewRange(1.0f)
                meta.setTransformationInterpolationDuration(interpolationTicks(part))
                // Authored warrior contours start at their real phase, not a hidden extra
                // two-tick delay after damage. Other scene clocks remain unchanged.
                meta.setScale(if(immediateContour) CoreSkillChoreography.pose(part,0.0).scale else Vec.ZERO);meta.setTranslation(part.offset)
                meta.setLeftRotation(CoreCombatMeshArt.rotation(part.yaw,part.pitch,part.roll))
                meta.setRightRotation(CoreCombatMeshArt.vanillaItemCorrection)
            }
            entity.setInstance(instance,Pos(effect.origin.x(),effect.origin.y(),effect.origin.z())).whenComplete { _,failure ->
                if(failure!=null || record.cancelled.get() || entity.isRemoved || owner.instance!==instance) entity.remove()
                else if(immediateContour && CoreCombatPresentation.packed(owner) &&
                    CoreCombatPresentation.detail(owner)!=CoreCombatPresentation.Detail.MINIMAL &&
                    (!part.secondary || CoreCombatPresentation.detail(owner)==CoreCombatPresentation.Detail.FULL) &&
                    owner.position.distanceSquared(effect.origin)<1600) {
                    // Send the authored phase's first frame as soon as registration completes.
                    // Do not tick other effects or bypass the normal observer budgets/LOD.
                    entity.addViewer(owner)
                }
            }
        }
    }

    fun tick() {
        // Select independently for each observer: old/delayed/off-screen parts
        // must not consume the eight slots before a fresh nearby damage beat.
        val observerParts=owner.instance?.players?.filter { it!==owner }?.associateWith { viewer ->
            if(!CoreCombatPresentation.packed(viewer) || CoreCombatPresentation.detail(viewer)==CoreCombatPresentation.Detail.MINIMAL)
                emptySet<Entity>()
            else live.asReversed().asSequence().filter { v ->
                !v.part.secondary && !v.entity.isRemoved && v.entity.instance===owner.instance &&
                    v.age>=v.part.delayTicks && v.age<removalAge(v.part) &&
                    viewer.position.distanceSquared(v.entity.position)<=256.0
            }.take(8).map { it.entity }.toSet()
        } ?: emptyMap()
        val iterator=live.iterator()
        while(iterator.hasNext()) {
            val v=iterator.next();val p=v.part
            if(owner.isRemoved || owner.instance!==v.instance || v.entity.isRemoved || v.age>=removalAge(p)) {
                if(traced(p)) println("CORE_VFX_TIMING shape=${p.shape} updates=${v.updateCount} maxServerGapMs=${v.maxUpdateGapNanos/1_000_000.0} interpolationTicks=${interpolationTicks(p)} endAge=${v.age}")
                v.cancelled.set(true);v.entity.remove();release(v.instance);iterator.remove();continue
            }
            if(v.entity.instance!==v.instance) continue
            val pose=CoreSkillChoreography.pose(p,v.age.toDouble())
            val facing=if(p.followOwner) atan2(owner.position.direction().x(),owner.position.direction().z())-v.baseYaw else 0.0
            val at=pose.offset
            val offset=if(p.followOwner) Vec(cos(facing)*at.x()+sin(facing)*at.z(),at.y(),-sin(facing)*at.x()+cos(facing)*at.z())
                .add(owner.position.sub(v.ownerStart).asVec()) else at
            // Let the final zero-width target finish. Restarting its interpolation
            // during drain ticks or removing at the authored endpoint cuts off the fade.
            if(v.age<p.delayTicks+p.durationTicks) v.entity.editEntityMeta(ItemDisplayMeta::class.java) { meta ->
                if(traced(p)) {
                    val now=System.nanoTime()
                    if(v.lastUpdateNanos!=0L) v.maxUpdateGapNanos=maxOf(v.maxUpdateGapNanos,now-v.lastUpdateNanos)
                    v.lastUpdateNanos=now;v.updateCount++
                }
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
                        (viewer===owner || v.entity in observerParts[viewer].orEmpty())
                }.toSet()
                v.entity.viewers.toList().filter { it !in allowed }.forEach { v.entity.removeViewer(it) }
                allowed.filter { it !in v.entity.viewers }.forEach { v.entity.addViewer(it) }
            }
            v.age++
        }
    }

    fun cancel() { live.forEach { it.cancelled.set(true);it.entity.remove();release(it.instance) };live.clear() }
    companion object {
        // Opt-in server-side cadence evidence, not client FPS or packet-arrival telemetry.
        private val traceTiming=java.lang.Boolean.getBoolean("projects.vfx.traceTiming")
        private fun traced(part: CoreCombatMeshPart)=traceTiming &&
            (part.shape.contains(":cut:0:") || part.shape=="warrior_trace:tail")
        // One client tick can contain zero or two server updates. A one-tick
        // transform runs out during a single missed delivery and visibly holds.
        // Only persistent flow surfaces get this extra tick of presentation slack.
        internal fun interpolationTicks(part: CoreCombatMeshPart)=
            // Approved v3 is an authored 20 Hz model sequence with a fixed transform.
            // Interpolating its initial zero scale would shrink/distort its first frames.
            if(part.shape.startsWith("approved_dash_") || CoreWarriorBladeChoreography.owns(part)) 0
            else if(part.shape.startsWith("warrior_trace:") ||
                part.shape.startsWith("flow:") && !part.shape.contains(":prepare:")) 2 else 1
        internal fun removalAge(part: CoreCombatMeshPart)=part.delayTicks+part.durationTicks+
            if(interpolationTicks(part)==2) 2 else 0
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
