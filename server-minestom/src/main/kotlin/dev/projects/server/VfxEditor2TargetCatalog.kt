package dev.projects.server

import dev.projects.protocol.VfxEditor2TargetCatalog as VfxEditor2TargetCatalogMessage
import dev.projects.protocol.VfxEditor2TargetDescriptor

/** The server-owned, deliberately small list of skill presentation targets exposed by Editor 2. */
object VfxEditor2TargetCatalog {
    const val RONIN_AA = "ronin.aa.main"
    const val RONIN_Q = "ronin.q.main"
    const val RONIN_W1 = "ronin.w1.main"
    const val RONIN_W2 = "ronin.w2.main"
    const val RONIN_W3 = "ronin.w3.main"
    const val RONIN_E = "ronin.e.main"
    const val RONIN_R = "ronin.r.main"

    const val STARWEAVER_Q_SUN = "starweaver.q.sun.impact"
    const val STARWEAVER_Q_MOON = "starweaver.q.moon.impact"
    const val STARWEAVER_Q_STAR = "starweaver.q.star.impact"
    const val STARWEAVER_Q_SOLAR = "starweaver.q.solar.impact"
    const val STARWEAVER_W_SUN = "starweaver.w.sun.zone"
    const val STARWEAVER_W_MOON = "starweaver.w.moon.zone"
    const val STARWEAVER_W_STAR = "starweaver.w.star.zone"
    const val STARWEAVER_W_LUNAR = "starweaver.w.lunar.zone"
    const val STARWEAVER_E_SUN = "starweaver.e.sun.zone"
    const val STARWEAVER_E_MOON = "starweaver.e.moon.zone"
    const val STARWEAVER_E_STAR = "starweaver.e.star.zone"
    const val STARWEAVER_E_STELLAR = "starweaver.e.stellar.zone"

    val targets: List<VfxEditor2TargetDescriptor> = listOf(
        target(RONIN_AA, "ronin", "Ronin", "AA"),
        target(RONIN_Q, "ronin", "Ronin", "Q"),
        target(RONIN_W1, "ronin", "Ronin", "W1 - Wound"),
        target(RONIN_W2, "ronin", "Ronin", "W2 - Crosscut"),
        target(RONIN_W3, "ronin", "Ronin", "W3 - Tempest"),
        target(RONIN_E, "ronin", "Ronin", "E - Blink"),
        target(RONIN_R, "ronin", "Ronin", "R"),
        target(STARWEAVER_Q_SUN, "starweaver", "Starweaver", "Q Sun - Impact"),
        target(STARWEAVER_Q_MOON, "starweaver", "Starweaver", "Q Moon - Impact"),
        target(STARWEAVER_Q_STAR, "starweaver", "Starweaver", "Q Star - Impact"),
        target(STARWEAVER_Q_SOLAR, "starweaver", "Starweaver", "Q Solar Conjunction - Impact"),
        target(STARWEAVER_W_SUN, "starweaver", "Starweaver", "W Sun"),
        target(STARWEAVER_W_MOON, "starweaver", "Starweaver", "W Moon"),
        target(STARWEAVER_W_STAR, "starweaver", "Starweaver", "W Star"),
        target(STARWEAVER_W_LUNAR, "starweaver", "Starweaver", "W Lunar Conjunction"),
        target(STARWEAVER_E_SUN, "starweaver", "Starweaver", "E Sun"),
        target(STARWEAVER_E_MOON, "starweaver", "Starweaver", "E Moon"),
        target(STARWEAVER_E_STAR, "starweaver", "Starweaver", "E Star"),
        target(STARWEAVER_E_STELLAR, "starweaver", "Starweaver", "E Stellar Conjunction"),
    )

    val message: VfxEditor2TargetCatalogMessage = VfxEditor2TargetCatalogMessage(targets)

    private val byId = targets.associateBy { it.id }

    init {
        require(byId.size == targets.size) { "VFX Editor 2 target ids must be unique" }
    }

    fun contains(targetId: String): Boolean = targetId in byId

    fun descriptor(targetId: String): VfxEditor2TargetDescriptor? = byId[targetId]

    fun classIds(): List<String> = targets.map { it.classId }.distinct()

    fun targetsForClass(classId: String): List<VfxEditor2TargetDescriptor> =
        targets.filter { it.classId == classId }

    private fun target(id: String, classId: String, classLabel: String, skillLabel: String) =
        VfxEditor2TargetDescriptor(id, classId, classLabel, skillLabel)
}
