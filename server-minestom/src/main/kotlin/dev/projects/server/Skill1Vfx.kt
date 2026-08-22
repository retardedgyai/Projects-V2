package dev.projects.server

internal const val SKILL1_TRAVEL_VFX = "projects:class/twin_blades/skill1_travel"
internal const val SKILL1_STOMP_VFX = "projects:class/twin_blades/skill1_stomp"
internal const val SKILL1_ESCAPE_VFX = "projects:class/twin_blades/skill1_escape"

internal data class Skill1VfxPlan(
    val travel: String,
    val stomp: String?,
    val escape: String?,
)

internal data class Skill1SoundCue(val key: String, val volume: Float, val pitch: Float)

internal data class Skill1SoundPlan(
    val travel: List<Skill1SoundCue>,
    val stomp: List<Skill1SoundCue>,
    val escape: List<Skill1SoundCue>,
)

internal fun skill1VfxPlan(confirmedHit: Boolean): Skill1VfxPlan = Skill1VfxPlan(
    travel = SKILL1_TRAVEL_VFX,
    stomp = SKILL1_STOMP_VFX.takeIf { confirmedHit },
    escape = SKILL1_ESCAPE_VFX.takeIf { confirmedHit },
)

internal fun skill1SoundPlan(confirmedHit: Boolean): Skill1SoundPlan = Skill1SoundPlan(
    travel = listOf(
        Skill1SoundCue("item.trident.riptide_1", 0.58f, 0.82f),
        Skill1SoundCue("item.trident.throw", 0.24f, 1.12f),
    ),
    stomp = if (confirmedHit) {
        listOf(
            Skill1SoundCue("item.trident.hit", 0.82f, 0.74f),
            Skill1SoundCue("entity.player.attack.strong", 0.72f, 0.82f),
            Skill1SoundCue("item.axe.scrape", 0.30f, 0.58f),
        )
    } else {
        emptyList()
    },
    escape = if (confirmedHit) {
        listOf(
            Skill1SoundCue("item.trident.throw", 0.42f, 1.78f),
            Skill1SoundCue("item.trident.riptide_1", 0.28f, 1.42f),
        )
    } else {
        emptyList()
    },
)
