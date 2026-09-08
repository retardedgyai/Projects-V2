package dev.projects.server.coreloop

import net.minestom.server.component.DataComponents
import net.minestom.server.entity.Player
import net.minestom.server.item.ItemStack
import net.minestom.server.item.component.CustomModelData
import net.minestom.server.tag.Tag
import java.util.UUID

/** Cosmetic model frames only. Never touches the account, damage, use state or skill runtime. */
internal object CoreArmamentPresentation {
    const val FRAMES = 12
    const val ACTION_FRAMES = 6
    internal enum class Stage(val offset: Int) { PREPARE(12), RELEASE(18) }
    internal data class Clip(val stage: Stage, val started: Long, val duration: Int,
        val weapon: ItemStack, val instanceId: UUID) {
        fun poseAt(tick: Long): Int? {
            val elapsed = tick - started
            if (elapsed !in 0 until duration.toLong()) return null
            return stage.offset + (elapsed * (ACTION_FRAMES - 1) / (duration - 1).coerceAtLeast(1)).toInt()
        }
    }
    // Transient, player-owned and never serialized into an item/account/save.
    private val clipTag = Tag.Transient<Clip>("projects_armament_visual_clip")
    private val families = setOf("greatsword", "staff", "bow", "dagger", "mace", "tome", "astrolabe")
    private val models = families.flatMap { family -> (1..4).map { "projects:weapons/${family}_t$it" } }.toSet()

    fun model(base: CoreWeaponBase, job: CoreClass, tier: Int): String {
        val family = if (base == CoreWeaponBase.STAFF && job == CoreClass.STARWEAVER) "astrolabe" else base.family
        return "projects:weapons/${family}_t${tier.coerceIn(1, 4)}"
    }

    fun frame(item: ItemStack, frame: Int): ItemStack {
        return pose(item, Math.floorMod(frame, FRAMES))
    }

    internal fun pose(item: ItemStack, pose: Int): ItemStack {
        if (item.get(DataComponents.ITEM_MODEL) !in models ||
            CoreLoopItems.gearSlot(item) != CoreGearSlot.WEAPON) return item
        require(pose in 0 until FRAMES + ACTION_FRAMES * 2)
        val old = item.get(DataComponents.CUSTOM_MODEL_DATA) ?: CustomModelData(emptyList(), emptyList(), emptyList(), emptyList())
        val value = pose.toFloat()
        if (old.floats().firstOrNull() == value) return item
        return item.with(DataComponents.CUSTOM_MODEL_DATA,
            CustomModelData(listOf(value) + old.floats().drop(1), old.flags(), old.strings(), old.colors()))
    }

    fun skill(player: Player, effect: CoreSkillEffect) {
        // Contacts and delayed field pulses do not mean the player cast again.
        if (!effect.valid || effect.pulse != 0 || effect.phase == CoreSkillVisualPhase.CONTACT) return
        // A shout or planting a standard is not a sword release.
        if(effect.job==CoreClass.WARRIOR && effect.sceneId in CoreWarriorSupportChoreography.sceneIds) return
        if (!usesWeapon(player.itemInMainHand, effect.motif)) return
        if (effect.phase == CoreSkillVisualPhase.PREPARE)
            begin(player, Stage.PREPARE, effect.prepareDuration + 1)
        else begin(player, Stage.RELEASE, 12)
    }

    internal fun usesWeapon(item: ItemStack, motif: CoreSkillMotif): Boolean {
        if (motif in setOf(CoreSkillMotif.BLINK, CoreSkillMotif.TRAP, CoreSkillMotif.GUARD)) return false
        val model = item.get(DataComponents.ITEM_MODEL) ?: return false
        // Laying a snare or escaping must not display a nocked/released arrow.
        return model in models && (!model.startsWith("projects:weapons/bow_") ||
            motif in setOf(CoreSkillMotif.ARROW, CoreSkillMotif.RAIN))
    }

    fun meleeRelease(player: Player) = begin(player, Stage.RELEASE, 12)

    private fun begin(player: Player, stage: Stage, duration: Int) {
        val instance = player.instance ?: return
        if (!CoreCombatPresentation.packed(player) || player.openInventory != null || player.isRemoved) return
        val item = player.itemInMainHand
        if (item.get(DataComponents.ITEM_MODEL) !in models || CoreLoopItems.gearSlot(item) != CoreGearSlot.WEAPON) return
        val previous = player.getTag(clipTag)
        val weapon = frame(item, 0)
        if (previous?.stage == stage && previous.started == player.aliveTicks &&
            previous.weapon == weapon && previous.instanceId == instance.uuid) return
        player.setTag(clipTag, Clip(stage, player.aliveTicks, duration.coerceIn(2, 61), weapon, instance.uuid))
        // Deliver the accepted release pose immediately, including on odd ticks.
        player.setItemInMainHand(pose(item, stage.offset))
    }

    fun cancel(player: Player) { player.removeTag(clipTag) }

    fun tick(player: Player, packed: Boolean) {
        // Hand-held equipment only, 10 fps. No background timers or entities.
        // Do not churn container/cursor projections while a menu is open.
        if (!packed || player.instance == null || player.openInventory != null || player.isRemoved) { cancel(player); return }
        val old = player.itemInMainHand
        val clip = player.getTag(clipTag)
        val actionPose = clip?.takeIf { it.instanceId == player.instance.uuid && it.weapon == frame(old, 0) }
            ?.poseAt(player.aliveTicks)
        if (clip != null && actionPose == null) cancel(player)
        if (player.aliveTicks % 2L != 0L) return
        val next = if (actionPose != null) pose(old, actionPose) else frame(old, ((player.aliveTicks / 2) % FRAMES).toInt())
        if (next !== old) player.setItemInMainHand(next)
    }
}
