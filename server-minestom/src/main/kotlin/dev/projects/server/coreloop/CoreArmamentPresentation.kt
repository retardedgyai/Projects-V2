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
    private val families = setOf("greatsword", "staff", "bow", "dagger", "mace", "tome")
    private val models = families.flatMap { family -> (1..4).map { "projects:weapons/${family}_t$it" } }.toSet()

    fun model(base: CoreWeaponBase, job: CoreClass, tier: Int): String {
        return "projects:weapons/${base.family}_t${tier.coerceIn(1, 4)}"
    }

    fun frame(item: ItemStack, frame: Int): ItemStack {
        return pose(item, Math.floorMod(frame, FRAMES))
    }

    internal fun pose(item: ItemStack, pose: Int): ItemStack {
        if (item.get(DataComponents.ITEM_MODEL) !in models ||
            CoreLoopItems.gearSlot(item) != CoreGearSlot.WEAPON) return item
        require(pose in 0..ANIM_POSE + SHADER_BEATS)
        val old = item.get(DataComponents.CUSTOM_MODEL_DATA) ?: CustomModelData(emptyList(), emptyList(), emptyList(), emptyList())
        val value = pose.toFloat()
        if (old.floats().firstOrNull() == value) return item
        return item.with(DataComponents.CUSTOM_MODEL_DATA,
            CustomModelData(listOf(value) + old.floats().drop(1), old.flags(), old.strings(), old.colors()))
    }

    fun skill(player: Player, effect: CoreSkillEffect) {
        // Contacts and delayed field pulses do not mean the player cast again.
        // 鉄槌 falls three times (2026-10-19): every pulse is its own swing, short enough to finish before the next.
        val threefold = effect.job == CoreClass.WARRIOR && effect.sceneId == "war_ult" && effect.phase == CoreSkillVisualPhase.PULSE
        if (!effect.valid || (effect.pulse != 0 && !threefold) || effect.phase == CoreSkillVisualPhase.CONTACT) return
        // 地割り's, 押し払い's and 切っ先薙ぎ's hands are the shader's beats (CorePlayerCombat): their pulses must not put the
        // old pose over them.
        if (effect.job == CoreClass.WARRIOR && effect.sceneId in shaderHanded && player.getTag(shaderTag) != null) return
        // A shout or planting a standard is not a sword release.
        if(effect.job==CoreClass.WARRIOR && effect.sceneId in CoreWarriorSupportChoreography.sceneIds) return
        if (!usesWeapon(player.itemInMainHand, effect.motif)) return
        if (effect.phase == CoreSkillVisualPhase.PREPARE)
            begin(player, Stage.PREPARE, effect.prepareDuration + 1)
        else begin(player, Stage.RELEASE, if (threefold) 8 else 12)
    }

    internal fun usesWeapon(item: ItemStack, motif: CoreSkillMotif): Boolean {
        if (motif in setOf(CoreSkillMotif.BLINK, CoreSkillMotif.TRAP, CoreSkillMotif.GUARD)) return false
        val model = item.get(DataComponents.ITEM_MODEL) ?: return false
        // Laying a snare or escaping must not display a nocked/released arrow.
        return model in models && (!model.startsWith("projects:weapons/bow_") ||
            motif in setOf(CoreSkillMotif.ARROW, CoreSkillMotif.RAIN))
    }

    /**
     * The greatsword swing moved by the item shader (2026-10-25; scripts/shaders/item.vsh, scripts/class_armament_swing.py).
     * The held sword becomes its tinted model and the tint says which beat, how long and since which world tick; the
     * shader plays it in the first-person hand at the screen's frame rate. When the swing is over it is the plain held
     * sword again (the tick only has 8 bits and would come round again).
     */
    const val ANIM_POSE = 120
    /**
     * Beats 1..3 are the normal attacks; 4 and 5 are 体当たり's charge and impact (scripts/class_armament_swing.py
     * CHARGE_BEAT / IMPACT_BEAT, keyed over CHARGE_TICKS / IMPACT_TICKS, which these lengths must match); 6 and 7 are
     * 跳び叩き's rise and slam (RISE_BEAT / SLAM_BEAT, SLAM_TICKS), which 地割り and 全力の一振り play too; 8 is 押し払い's
     * sweep; 9, 10 and 11 are 切っ先薙ぎ's wind-up, cut and bite (PUSH_BEAT, TIP_WIND_BEAT, TIP_CUT_BEAT, TIP_BITE_BEAT).
     * Tint blue: 84 + beat - 1 for the big swords, + [SHADER_BEATS] for the T1 sprite (84..105), the arms
     * + [ARM_MARK_STEP] / twice it (106..127 / 128..149), so no two ranges meet; the light probes below (80..83) stay clear.
     */
    const val SHADER_BEATS = 11
    const val ARM_MARK_STEP = 2 * SHADER_BEATS
    const val CHARGE_BEAT = 4
    const val IMPACT_BEAT = 5
    const val RISE_BEAT = 6
    const val SLAM_BEAT = 7
    const val PUSH_BEAT = 8
    const val TIP_WIND_BEAT = 9
    const val TIP_CUT_BEAT = 10
    const val TIP_BITE_BEAT = 11
    /** 押し払い's sweep: the blade is through the middle [PUSH_LEAD_TICKS] after it starts (scripts/class_armament_swing.py). */
    const val PUSH_TICKS = 13
    const val PUSH_LEAD_TICKS = 3
    /**
     * 切っ先薙ぎ: the wind-up is played from the cast at its full length (drawn back in about three ticks, held to
     * TIP_WIND_HOLD, back to rest by its end if no cut comes); the cut starts [TIP_LEAD_TICKS] before the impact and is
     * through the middle at it; the bite takes over from the cut at the impact when the tip met someone.
     */
    const val TIP_WIND_TICKS = 14
    const val TIP_CUT_TICKS = 16
    const val TIP_LEAD_TICKS = 2
    const val TIP_BITE_TICKS = 17
    private val shaderHanded = setOf("slam", CoreWarriorRules.PUSH, CoreWarriorRules.TIP)
    const val TACKLE_CHARGE_TICKS = 14
    const val TACKLE_IMPACT_TICKS = 11
    const val LEAP_SLAM_TICKS = 12
    /** The rise's hold ends at this share of its clip (scripts/class_armament_swing.py LEAP_RISE_HOLD). */
    const val LEAP_RISE_HOLD = .8
    /**
     * 跳び叩き's rise is played over the expected air time: long enough that the hold lasts until the body is down
     * (two ticks to spare for the landing to be seen), never past the latest landing (CoreWarriorLeap.MAX_AIR_TICKS).
     */
    fun leapRiseTicks(airTicks: Int) = kotlin.math.ceil((airTicks + 2) / LEAP_RISE_HOLD).toInt().coerceIn(12, CoreWarriorLeap.MAX_AIR_TICKS)
    /**
     * 地割り: the slam beat starts this many ticks before the server's impact, so the blade crosses the middle a tick
     * before it and is on the ground half a tick before it (SLAM_TICKS keys: the middle at 1, the ground at 1.5).
     */
    const val SLAM_LEAD_TICKS = 2
    /**
     * 地割り's haul over the shoulder, played from the cast so its hold lasts until the slam beat at [slamAt] ticks
     * (two to spare). A short 地割り hauls up in about a tick, 全力の一振り's wind-up in about three and holds.
     */
    fun slamRiseTicks(slamAt: Int) = kotlin.math.ceil((slamAt + 2) / LEAP_RISE_HOLD).toInt().coerceIn(8, 255)
    internal fun shaderMarker(beat: Int, plain: Boolean) = 84 + (beat - 1).coerceIn(0, SHADER_BEATS - 1) + if (plain) SHADER_BEATS else 0
    private data class ShaderSwing(val until: Long, val weapon: ItemStack, val beat: Int = 1)
    private val shaderTag = Tag.Transient<ShaderSwing>("projects_armament_shader_swing")

    fun shaderSwing(player: Player, beat: Int, ticks: Int): Boolean {
        val instance = player.instance ?: return false
        if (!CoreCombatPresentation.packed(player) || player.openInventory != null || player.isRemoved) return false
        val item = player.itemInMainHand
        val model = item.get(DataComponents.ITEM_MODEL) ?: return false
        if (model !in greatswords || CoreLoopItems.gearSlot(item) != CoreGearSlot.WEAPON) return false
        cancel(player); player.removeTag(swingTag)
        val marker = shaderMarker(beat, model.endsWith("_t1"))
        // The shader's clock is GameTime, the client's game time modulo one day (24000): the start must be on that
        // clock, or the swing starts part-way through (or already over) and the sword jumps.
        val start = ((instance.worldAge % 24000L) and 255L).toInt()
        fun tint(mark: Int) = net.kyori.adventure.text.format.TextColor.color(ticks.coerceIn(1, 255), start, mark)
        // Colours 1 and 2 tint the arms that come with the swing (the shader places them by their own keys).
        // 120 + beat: Java draws every value from 120 with the shader-moved model (the beat is in the tint); Geyser maps
        // each beat to its own Bedrock item, whose attachable plays that beat (scripts/build_bedrock_greatsword.py).
        val posed = pose(item, ANIM_POSE + (beat - 1).coerceIn(0, SHADER_BEATS - 1) + 1)
        val old = posed.get(DataComponents.CUSTOM_MODEL_DATA)!!
        player.setTag(shaderTag, ShaderSwing(player.aliveTicks + ticks, frame(item, 0), beat))
        player.setItemInMainHand(posed.with(DataComponents.CUSTOM_MODEL_DATA,
            CustomModelData(old.floats(), old.flags(), old.strings(), listOf(tint(marker), tint(marker + ARM_MARK_STEP), tint(marker + 2 * ARM_MARK_STEP)) + old.colors().drop(3))))
        return true
    }

    fun meleeRelease(player: Player) {
        if (!GreatswordViewmodel.playing(player) && player.getTag(shaderTag) == null) begin(player, Stage.RELEASE, 12)
    }

    /**
     * First-person greatsword swings (2026-10-25, scripts/class_armament_swing.py): one model per tick moves the held
     * sword through the beat, Better Combat manner (no arm; the item's own attack animation is off). A beat's frames
     * are spread over the swing's real length; a beat the next press does not follow settles back to rest.
     */
    internal enum class SwingClip(val offset: Int, val frames: Int) {
        SWING1(24, 20), SWING2(44, 22), SWING3(66, 30), RETURN1(96, 8), RETURN2(104, 8)
    }
    internal data class Swing(val clip: SwingClip, val started: Long, val duration: Int, val weapon: ItemStack, val instanceId: UUID) {
        fun poseAt(tick: Long): Int? {
            val elapsed = tick - started
            if (elapsed !in 0 until duration.toLong()) return null
            return clip.offset + (elapsed * (clip.frames - 1) / (duration - 1).coerceAtLeast(1)).toInt()
        }
    }
    private val swingTag = Tag.Transient<Swing>("projects_armament_swing")
    private val greatswords = (1..4).map { "projects:weapons/greatsword_t$it" }.toSet()

    /** Plays [beat] (1, 2 or 3: the combo's swing art) over [ticks]; false if the held item is not a greatsword. */
    fun swing(player: Player, beat: Int, ticks: Int): Boolean {
        val instance = player.instance ?: return false
        if (!CoreCombatPresentation.packed(player) || player.openInventory != null || player.isRemoved) return false
        val item = player.itemInMainHand
        if (item.get(DataComponents.ITEM_MODEL) !in greatswords || CoreLoopItems.gearSlot(item) != CoreGearSlot.WEAPON) return false
        GreatswordViewmodel.stop(player)
        cancel(player)
        val clip = SwingClip.entries[(beat - 1).coerceIn(0, 2)]
        player.setTag(swingTag, Swing(clip, player.aliveTicks, ticks.coerceIn(2, 80), frame(item, 0), instance.uuid))
        player.setItemInMainHand(pose(item, clip.offset))
        return true
    }

    private fun begin(player: Player, stage: Stage, duration: Int) {
        val instance = player.instance ?: return
        if (!CoreCombatPresentation.packed(player) || player.openInventory != null || player.isRemoved) return
        val item = player.itemInMainHand
        if (item.get(DataComponents.ITEM_MODEL) !in models || CoreLoopItems.gearSlot(item) != CoreGearSlot.WEAPON) return
        val previous = player.getTag(clipTag)
        val weapon = frame(item, 0)
        if (previous?.stage == stage && previous.started == player.aliveTicks &&
            previous.weapon == weapon && previous.instanceId == instance.uuid) return
        player.removeTag(swingTag)
        GreatswordViewmodel.stop(player)
        player.setTag(clipTag, Clip(stage, player.aliveTicks, duration.coerceIn(2, 61), weapon, instance.uuid))
        // Deliver the accepted release pose immediately, including on odd ticks.
        player.setItemInMainHand(pose(item, stage.offset))
    }

    fun cancel(player: Player) {
        player.removeTag(clipTag)
        // 体当たり's charge, 跳び叩き's rise and 切っ先薙ぎ's wind-up are held in the hand only while the action lasts: a
        // cancelled one lets the sword go.
        player.getTag(shaderTag)?.takeIf { it.beat == CHARGE_BEAT || it.beat == RISE_BEAT || it.beat == TIP_WIND_BEAT }?.let { swing ->
            player.removeTag(shaderTag)
            val held = player.itemInMainHand
            if (frame(held, 0).without(DataComponents.CUSTOM_MODEL_DATA) == swing.weapon.without(DataComponents.CUSTOM_MODEL_DATA))
                player.setItemInMainHand(swing.weapon)
        }
    }

    fun tick(player: Player, packed: Boolean) {
        // Hand-held equipment only, 10 fps. No background timers or entities.
        // Do not churn container/cursor projections while a menu is open.
        if (!packed || player.instance == null || player.openInventory != null || player.isRemoved) {
            cancel(player); player.removeTag(swingTag); GreatswordViewmodel.stop(player); return
        }
        if (GreatswordViewmodel.playing(player)) { GreatswordViewmodel.tick(player); return }
        player.getTag(shaderTag)?.let { swing ->
            val held = player.itemInMainHand
            if (frame(held, 0).without(DataComponents.CUSTOM_MODEL_DATA) == swing.weapon.without(DataComponents.CUSTOM_MODEL_DATA) &&
                player.aliveTicks < swing.until) return
            player.removeTag(shaderTag)
            if (frame(held, 0).without(DataComponents.CUSTOM_MODEL_DATA) == swing.weapon.without(DataComponents.CUSTOM_MODEL_DATA))
                player.setItemInMainHand(swing.weapon)
        }
        val old = player.itemInMainHand
        // A swing moves every tick (the 10 fps idle frames would stutter it); a beat left unfollowed settles back.
        player.getTag(swingTag)?.let { swing ->
            val held = swing.instanceId == player.instance.uuid && swing.weapon == frame(old, 0)
            val pose = if (held) swing.poseAt(player.aliveTicks) else null
            if (pose != null) { pose(old, pose).let { if (it !== old) player.setItemInMainHand(it) }; return }
            player.removeTag(swingTag)
            val back = when (swing.clip) { SwingClip.SWING1 -> SwingClip.RETURN1; SwingClip.SWING2 -> SwingClip.RETURN2; else -> null }
            if (held && back != null) {
                player.setTag(swingTag, Swing(back, player.aliveTicks, back.frames, swing.weapon, swing.instanceId))
                player.setItemInMainHand(pose(old, back.offset)); return
            }
        }
        val clip = player.getTag(clipTag)
        val actionPose = clip?.takeIf { it.instanceId == player.instance.uuid && it.weapon == frame(old, 0) }
            ?.poseAt(player.aliveTicks)
        if (clip != null && actionPose == null) cancel(player)
        if (player.aliveTicks % 2L != 0L) return
        val next = if (actionPose != null) pose(old, actionPose) else frame(old, ((player.aliveTicks / 2) % FRAMES).toInt())
        if (next !== old) player.setItemInMainHand(next)
    }
}
