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
        if(effect.sceneId in CoreHealerChoreography.sceneIds) {
            when(effect.phase) {
                CoreSkillVisualPhase.PREPARE -> {
                    if(effect.sceneId!="heal_lamp" || effect.pulse==0)
                        cue(if(effect.sceneId=="heal_judgment") SoundEvent.ITEM_TRIDENT_RETURN else SoundEvent.BLOCK_ENCHANTMENT_TABLE_USE,.65f,1.2f)
                }
                CoreSkillVisualPhase.CONTACT -> cue(SoundEvent.BLOCK_AMETHYST_BLOCK_HIT,.75f,if(effect.sceneId=="heal_mark") 1.6f else 1.15f)
                CoreSkillVisualPhase.PULSE -> when(effect.sceneId) {
                    "heal_step" -> {
                        cue(SoundEvent.ENTITY_ENDERMAN_TELEPORT,.5f,if(effect.endpoint==CoreSkillEndpoint.DEPARTURE) .9f else 1.6f)
                        cue(SoundEvent.ITEM_BOOK_PAGE_TURN,.7f,1.2f)
                    }
                    "heal_judgment" -> {
                        cue(SoundEvent.ITEM_TRIDENT_HIT_GROUND,.9f,.75f)
                        cue(SoundEvent.BLOCK_BELL_USE,.4f,.85f+effect.pulse*.1f)
                    }
                    "heal_lamp" -> {
                        cue(SoundEvent.BLOCK_AMETHYST_BLOCK_CHIME,.85f,1.0f+effect.pulse*.2f)
                        cue(SoundEvent.BLOCK_ENCHANTMENT_TABLE_USE,.5f,1.3f)
                    }
                    else -> {
                        cue(SoundEvent.BLOCK_BEACON_ACTIVATE,.6f,1.65f)
                        cue(SoundEvent.BLOCK_AMETHYST_BLOCK_RESONATE,.8f,1.35f)
                    }
                }
            }
            return
        }
        if(effect.job==CoreClass.TEMPLAR && scene.kind!=CoreSceneKind.PULL) {
            when(effect.phase) {
                CoreSkillVisualPhase.PREPARE -> {
                    if(effect.sceneId!="temp_field" || effect.pulse==0)
                        cue(if(scene.kind==CoreSceneKind.HAMMER) SoundEvent.ITEM_TRIDENT_RETURN else SoundEvent.BLOCK_CHAIN_PLACE,.65f,.65f)
                }
                CoreSkillVisualPhase.CONTACT -> {
                    cue(SoundEvent.ENTITY_PLAYER_ATTACK_STRONG,.75f,.65f)
                    if(effect.sceneId=="temp_break") cue(SoundEvent.ENTITY_ITEM_BREAK,.7f,.75f)
                    else cue(SoundEvent.BLOCK_STONE_BREAK,.45f,1.1f)
                }
                CoreSkillVisualPhase.PULSE -> when(effect.sceneId) {
                    "temp_mace","temp_break","temp_rebuke" -> {
                        cue(SoundEvent.BLOCK_ANVIL_LAND,.4f,if(effect.sceneId=="temp_break") .6f else .85f)
                        cue(SoundEvent.BLOCK_STONE_BREAK,.9f,.6f)
                    }
                    "temp_field" -> {
                        cue(SoundEvent.BLOCK_CHAIN_PLACE,.85f,.75f+effect.pulse*.12f)
                        cue(SoundEvent.BLOCK_AMETHYST_BLOCK_RESONATE,.45f,.85f)
                    }
                    "temp_guard","temp_dash" -> cue(SoundEvent.ITEM_SHIELD_BLOCK,1f,if(effect.sceneId=="temp_dash") .6f else .85f)
                    else -> {
                        cue(SoundEvent.BLOCK_BELL_USE,.65f,if(effect.skill.ultimate) .6f else 1.1f)
                        cue(SoundEvent.BLOCK_AMETHYST_BLOCK_RESONATE,.75f,.8f)
                    }
                }
            }
            return
        }
        if(effect.job==CoreClass.RANGER) {
            val trap=effect.sceneId=="hunt_trap"
            when(effect.phase) {
                CoreSkillVisualPhase.PREPARE -> {
                    if(!trap || effect.pulse==0) cue(if(trap) SoundEvent.BLOCK_CHAIN_PLACE else SoundEvent.ITEM_CROSSBOW_LOADING_MIDDLE,
                        .6f,if(effect.skill.ultimate) .65f else 1.0f)
                }
                CoreSkillVisualPhase.CONTACT -> {
                    cue(if(trap) SoundEvent.BLOCK_BREWING_STAND_BREW else SoundEvent.ENTITY_ARROW_HIT,
                        .75f,if(effect.skill.ultimate) .65f else 1.2f)
                    if(effect.sceneId=="hunt_mark") cue(SoundEvent.BLOCK_NOTE_BLOCK_PLING,.35f,1.6f)
                }
                CoreSkillVisualPhase.PULSE -> {
                    if(trap) {
                        cue(SoundEvent.BLOCK_IRON_TRAPDOOR_CLOSE,.9f,.65f)
                        cue(SoundEvent.BLOCK_BREWING_STAND_BREW,.7f,.9f)
                    } else {
                        cue(SoundEvent.ENTITY_ARROW_SHOOT,1.0f,if(effect.skill.ultimate) .6f else 1.0f+effect.pulse%3*.12f)
                        cue(SoundEvent.ITEM_CROSSBOW_SHOOT,.65f,if(effect.skill.ultimate) .65f else 1.25f)
                        if(effect.sceneId=="frost_fan") cue(SoundEvent.BLOCK_GLASS_BREAK,.5f,1.45f)
                    }
                }
            }
            return
        }
        val restoration=scene.kind==CoreSceneKind.HEAL || effect.sceneId=="heal_shield"
        val blade = scene.kind in setOf(CoreSceneKind.CUT,CoreSceneKind.CLEAVE,CoreSceneKind.SPIN,CoreSceneKind.THRUST) && scene.body!="shield_bash"
        if(scene.kind==CoreSceneKind.TELEPORT && effect.phase==CoreSkillVisualPhase.PULSE) {
            if(effect.sceneId=="ass_escape") {
                cue(SoundEvent.ENTITY_ENDERMAN_TELEPORT,.55f,if(effect.endpoint==CoreSkillEndpoint.DEPARTURE) .65f else 1.45f)
                cue(SoundEvent.BLOCK_FIRE_EXTINGUISH,.55f,.75f)
                return
            }
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
                    effect.sceneId == "mage_burst" -> {
                        cue(SoundEvent.ENTITY_LIGHTNING_BOLT_IMPACT,.7f,1.65f)
                        cue(SoundEvent.BLOCK_RESPAWN_ANCHOR_DEPLETE,.55f,1.8f)
                    }
                    effect.sceneId == "ass_poison" -> {
                        cue(SoundEvent.ENTITY_PLAYER_ATTACK_SWEEP,.8f,1.25f)
                        cue(SoundEvent.BLOCK_BREWING_STAND_BREW,.9f,.7f)
                    }
                    effect.sceneId in setOf("ass_stab","ass_chase","ass_contract") -> {
                        cue(SoundEvent.ITEM_TRIDENT_THROW,.9f,if(effect.skill.ultimate) .65f else 1.45f)
                        cue(SoundEvent.ENTITY_PLAYER_ATTACK_CRIT,.7f,if(effect.skill.ultimate) .7f else 1.2f)
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
                if (!restoration && effect.sceneId!="ass_contract" && (effect.skill.ultimate || effect.motif == CoreSkillMotif.CLEAVE || effect.motif == CoreSkillMotif.PULL))
                    cue(SoundEvent.ENTITY_GENERIC_EXPLODE, if (effect.skill.ultimate) .60f else .3f, .6f)
            }
        }
    }
}
