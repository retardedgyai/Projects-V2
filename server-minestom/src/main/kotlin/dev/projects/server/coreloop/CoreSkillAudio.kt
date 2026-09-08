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
        val scene = CoreSkillScenes.get(effect.sceneId)
        val restoration=scene.kind==CoreSceneKind.HEAL || effect.sceneId=="heal_shield"
        val blade = scene.kind in setOf(CoreSceneKind.CUT,CoreSceneKind.CLEAVE,CoreSceneKind.SPIN,CoreSceneKind.THRUST) && scene.body!="shield_bash"
        if(scene.kind==CoreSceneKind.TELEPORT && effect.phase==CoreSkillVisualPhase.PULSE) {
            cue(if(effect.endpoint==CoreSkillEndpoint.DEPARTURE) SoundEvent.ENTITY_ILLUSIONER_CAST_SPELL else SoundEvent.BLOCK_AMETHYST_BLOCK_CHIME,
                .65f,if(effect.endpoint==CoreSkillEndpoint.DEPARTURE) .7f else 1.4f)
            return
        }
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
                    restoration -> {
                        cue(SoundEvent.BLOCK_AMETHYST_BLOCK_CHIME,.9f,1.15f+effect.pulse%3*.12f)
                        cue(SoundEvent.BLOCK_ENCHANTMENT_TABLE_USE,.6f,1.25f)
                    }
                    scene.kind == CoreSceneKind.PULL -> {
                        cue(SoundEvent.BLOCK_CHAIN_BREAK,.85f,.7f)
                        cue(SoundEvent.ITEM_TRIDENT_RETURN,.9f,.55f)
                    }
                    effect.sceneId == "mage_ward" -> {
                        cue(SoundEvent.BLOCK_AMETHYST_BLOCK_RESONATE,.85f,1.25f)
                        cue(SoundEvent.ITEM_SHIELD_BLOCK,.6f,1.4f)
                    }
                    scene.kind == CoreSceneKind.HAMMER -> {
                        cue(SoundEvent.ENTITY_PLAYER_ATTACK_STRONG,.9f,.65f)
                        cue(SoundEvent.BLOCK_ANVIL_LAND,.28f,1.35f)
                    }
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
                if (!restoration && (effect.skill.ultimate || effect.motif == CoreSkillMotif.CLEAVE || effect.motif == CoreSkillMotif.PULL))
                    cue(SoundEvent.ENTITY_GENERIC_EXPLODE, if (effect.skill.ultimate) .60f else .3f, .6f)
            }
        }
    }
}
