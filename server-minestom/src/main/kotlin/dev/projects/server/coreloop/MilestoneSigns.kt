package dev.projects.server.coreloop

import net.kyori.adventure.key.Key
import net.kyori.adventure.sound.Sound
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextColor
import net.kyori.adventure.title.Title
import net.minestom.server.entity.Player
import net.minestom.server.network.packet.server.play.ParticlePacket
import net.minestom.server.particle.Particle
import net.minestom.server.sound.SoundEvent
import java.time.Duration

/**
 * The sign that pops up on a milestone, after RuneForge's LEVEL UP (藍 2026-11-07 「RuneForge みたいなのがあるといいね」):
 * a chunky gold sign in the title (scripts/build_milestone_signs.py), the milestone under it in the 手帳's dots, a burst
 * around the player and a chime. Which milestone an account change is decides [of]; the server shows it.
 */
internal object MilestoneSigns {
    enum class Kind(val glyph: String) { LEVEL(""), SKILL(""), CLEAR("") }
    data class Milestone(val kind: Kind, val line: String)

    /** The one milestone between two snapshots of an account, if any: a first clear first, then 冒険 Lv, then スキル Lv. */
    fun of(before: CoreAccount, after: CoreAccount): Milestone? {
        (after.dungeonClears.keys - before.dungeonClears.keys).firstOrNull()?.let {
            return Milestone(Kind.CLEAR, CoreDungeons.names[it] ?: "ダンジョン")
        }
        if (after.journey.level > before.journey.level) return Milestone(Kind.LEVEL, "冒険 Lv ${after.journey.level}")
        if (after.journey.job == before.journey.job) {
            val was = before.journey.build.skillLevels
            after.journey.build.skillLevels.entries.firstOrNull { (icon, level) -> level > (was[icon] ?: 1) }?.let { (icon, level) ->
                val name = CoreSkillCatalog.skills(after.journey.job).firstOrNull { it.icon == icon }?.name ?: return null
                return Milestone(Kind.SKILL, "$name Lv $level")
            }
        }
        return null
    }

    /** [guiScale] picks the sign baked for it, so a dot is four screen pixels at 1080p at every scale. */
    fun show(player: Player, milestone: Milestone, guiScale: Int?, packed: Boolean) {
        val size = when (guiScale) { 2 -> 2; 3 -> 3; else -> 4 }
        val sign = if (packed) Component.text(milestone.kind.glyph).font(Key.key("projects_milestone", "sign$size"))
            else Component.text(if (milestone.kind == Kind.CLEAR) "初踏破！" else "アップ！", NamedTextColor.GOLD)
        val line = if (packed) Component.text(milestone.line, INK).font(Key.key("projects_ui_polish05", "menu${size}_m6_0"))
            else Component.text(milestone.line, INK)
        player.showTitle(Title.title(sign, line, Title.Times.times(Duration.ofMillis(100), Duration.ofMillis(1800), Duration.ofMillis(500))))
        // The gems flying off: green for a level, gold for a first clear, thrown around the head.
        val eye = player.position.add(0.0, player.eyeHeight, 0.0)
        val burst = if (milestone.kind == Kind.CLEAR) Particle.TOTEM_OF_UNDYING else Particle.HAPPY_VILLAGER
        player.sendPacket(ParticlePacket(burst, false, true, eye.x(), eye.y(), eye.z(), 1.2f, 0.8f, 1.2f, 0.4f, 40))
        player.playSound(Sound.sound(if (milestone.kind == Kind.CLEAR) SoundEvent.UI_TOAST_CHALLENGE_COMPLETE else SoundEvent.ENTITY_PLAYER_LEVELUP,
            Sound.Source.MASTER, 0.7f, 1f))
    }

    private val INK = TextColor.color(0xECE6DC)
}
