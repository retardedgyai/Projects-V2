package dev.projects.server.coreloop

import net.kyori.adventure.sound.Sound
import net.minestom.server.entity.Player
import net.minestom.server.sound.SoundEvent

/** Immediate cues on the same authoritative phase as the visual. No delayed sound tasks. */
internal object CoreSkillAudio {
    fun play(player: Player, effect: CoreSkillEffect) {
        val detail = when (CoreCombatPresentation.detail(player)) {
            CoreCombatPresentation.Detail.FULL -> 1f
            CoreCombatPresentation.Detail.SUBDUED -> .65f
            CoreCombatPresentation.Detail.MINIMAL -> .4f
        }
        fun cue(event: SoundEvent, volume: Float, pitch: Float) =
            player.playSound(Sound.sound(event, Sound.Source.PLAYER, volume * detail, pitch))
        val astral = effect.job == CoreClass.STARWEAVER
        val blade = effect.motif in setOf(CoreSkillMotif.SLASH, CoreSkillMotif.CLEAVE, CoreSkillMotif.WHIRL, CoreSkillMotif.THRUST)
        when (effect.phase) {
            CoreSkillVisualPhase.PREPARE -> {
                cue(if (blade) SoundEvent.ITEM_TRIDENT_RETURN else SoundEvent.BLOCK_BEACON_ACTIVATE, .50f, if (astral) 1.6f else .8f)
            }
            CoreSkillVisualPhase.CONTACT -> {
                cue(if (blade) SoundEvent.ITEM_TRIDENT_HIT else SoundEvent.BLOCK_GLASS_BREAK, .65f, if (blade) .65f else 1.35f)
                if (effect.skill.ultimate) cue(SoundEvent.ENTITY_GENERIC_EXPLODE, .38f, .65f)
            }
            CoreSkillVisualPhase.PULSE -> {
                val pitch = (.85 + (effect.pulse % 3) * .13).toFloat()
                when {
                    blade -> {
                        cue(SoundEvent.ENTITY_PLAYER_ATTACK_SWEEP, 1.0f, pitch)
                        cue(SoundEvent.ITEM_TRIDENT_THROW, .85f, if (effect.motif == CoreSkillMotif.CLEAVE) .55f else pitch)
                    }
                    astral -> {
                        cue(SoundEvent.BLOCK_AMETHYST_BLOCK_RESONATE, .95f, pitch)
                        cue(SoundEvent.ENTITY_ILLUSIONER_CAST_SPELL, .8f, .8f)
                    }
                    effect.motif == CoreSkillMotif.FIRE || effect.motif == CoreSkillMotif.METEOR -> {
                        cue(SoundEvent.ITEM_FIRECHARGE_USE, 1f, .7f)
                        cue(SoundEvent.ENTITY_BLAZE_SHOOT, .6f, .85f)
                    }
                    effect.motif == CoreSkillMotif.FROST || effect.motif == CoreSkillMotif.FROST_FAN -> {
                        cue(SoundEvent.BLOCK_GLASS_BREAK, .9f, .65f)
                        cue(SoundEvent.ITEM_TRIDENT_THROW, .55f, 1.5f)
                    }
                    effect.motif == CoreSkillMotif.VENOM || effect.motif == CoreSkillMotif.TRAP -> {
                        cue(SoundEvent.BLOCK_BREWING_STAND_BREW, .9f, .65f)
                        cue(SoundEvent.ENTITY_PLAYER_ATTACK_SWEEP, .7f, 1.3f)
                    }
                    effect.motif == CoreSkillMotif.ARROW || effect.motif == CoreSkillMotif.RAIN || effect.motif == CoreSkillMotif.NEEDLE -> {
                        cue(SoundEvent.ENTITY_ARROW_SHOOT, 1f, pitch)
                        cue(SoundEvent.ITEM_TRIDENT_THROW, .55f, 1.45f)
                    }
                    else -> {
                        cue(SoundEvent.ENTITY_ILLUSIONER_CAST_SPELL, .85f, pitch)
                        cue(SoundEvent.BLOCK_AMETHYST_BLOCK_CHIME, .65f, 1.35f)
                    }
                }
                if (effect.skill.ultimate || effect.motif == CoreSkillMotif.CLEAVE || effect.motif == CoreSkillMotif.PULL)
                    cue(SoundEvent.ENTITY_GENERIC_EXPLODE, if (effect.skill.ultimate) .60f else .3f, .6f)
            }
        }
    }
}
