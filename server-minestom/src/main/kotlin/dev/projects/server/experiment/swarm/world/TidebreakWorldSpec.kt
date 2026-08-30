package dev.projects.server.experiment.swarm.world

import net.minestom.server.coordinate.Pos

data class BlockBounds(
    val minX: Int,
    val minY: Int,
    val minZ: Int,
    val maxX: Int,
    val maxY: Int,
    val maxZ: Int,
) {
    init {
        require(minX <= maxX && minY <= maxY && minZ <= maxZ) { "Invalid block bounds" }
    }

    val width: Int get() = maxX - minX + 1
    val height: Int get() = maxY - minY + 1
    val depth: Int get() = maxZ - minZ + 1

    fun contains(x: Int, y: Int, z: Int): Boolean =
        x in minX..maxX && y in minY..maxY && z in minZ..maxZ

    fun contains(pos: Pos): Boolean = contains(pos.blockX(), pos.blockY(), pos.blockZ())
}

enum class TidebreakZoneId {
    HARBOR_MARKET,
    TIDAL_FLAT,
    QUARRY_CAVE,
    BREAKWATER_STAGING,
    BREAKWATER_ARENA,
}

enum class TidebreakActorId(val targetId: String) {
    WARDEN("tidebreak:npc/warden"),
    BROKER_SMITH("tidebreak:npc/broker-smith"),
    LOOKOUT("tidebreak:npc/lookout"),
}

data class TidebreakActorSpec(
    val id: TidebreakActorId,
    val displayName: String,
    val role: String,
    val position: Pos,
)

enum class TidebreakTargetKind {
    ORE_NODE,
    SUPPLIER_RECORD,
    COUPLER_RACK,
    PRACTICE_POST,
    CRASH_PILLAR,
}

data class TidebreakTargetSpec(
    val targetId: String,
    val kind: TidebreakTargetKind,
    val position: Pos,
    val maxInteractionDistance: Double,
    val zone: TidebreakZoneId,
)

data class TidebreakCombatSpawnSpec(
    val spawnId: String,
    val position: Pos,
    val zone: TidebreakZoneId,
)

/** Frozen spatial and integration identifiers for the Tidebreak slice. */
object TidebreakWorldSpec {
    const val GROUND_Y = 40
    const val MIN_GENERATED_Y = 36
    const val MAX_GENERATED_Y = 49

    val worldBounds = BlockBounds(-48, MIN_GENERATED_Y, -48, 47, MAX_GENERATED_Y, 47)
    val harborMarketBounds = BlockBounds(-44, GROUND_Y, -18, -14, 47, 18)
    val tidalFlatBounds = BlockBounds(-13, 38, 12, 21, 43, 44)
    val quarryCaveBounds = BlockBounds(-8, 38, -44, 24, 47, -12)
    val stagingBounds = BlockBounds(17, GROUND_Y, -8, 26, 45, 8)
    val arenaBounds = BlockBounds(27, 39, -16, 46, 48, 16)

    val spawn = Pos(-36.5, GROUND_Y + 1.0, 0.5, -90f, 0f)
    val respawn = spawn
    val stagingSpawn = Pos(21.5, GROUND_Y + 1.0, 0.5, -90f, 0f)
    val bossSpawn = Pos(39.5, GROUND_Y + 1.0, 0.5, 90f, 0f)

    val actors = listOf(
        TidebreakActorSpec(
            TidebreakActorId.WARDEN,
            "Warden Iona [Route / Report]",
            "Route choice, objective recap, final report",
            Pos(-33.5, GROUND_Y + 1.0, -5.5, 0f, 0f),
        ),
        TidebreakActorSpec(
            TidebreakActorId.BROKER_SMITH,
            "Broker-Smith Brann [Exchange / Forge]",
            "Fixed exchange, Coupler craft, Tidehook MOD fitting",
            Pos(-26.5, GROUND_Y + 1.0, 4.5, 180f, 0f),
        ),
        TidebreakActorSpec(
            TidebreakActorId.LOOKOUT,
            "Lookout Tern [Hunt / Retry]",
            "Enemy teaching, prepared-player encounter entry, retry",
            Pos(20.5, GROUND_Y + 1.0, -5.5, 0f, 0f),
        ),
    )

    val oreNodes = listOf(
        target("ore/tidal-1", TidebreakTargetKind.ORE_NODE, -7, 41, 21, TidebreakZoneId.TIDAL_FLAT),
        target("ore/tidal-2", TidebreakTargetKind.ORE_NODE, 4, 41, 31, TidebreakZoneId.TIDAL_FLAT),
        target("ore/tidal-3", TidebreakTargetKind.ORE_NODE, 15, 41, 39, TidebreakZoneId.TIDAL_FLAT),
        target("ore/quarry-1", TidebreakTargetKind.ORE_NODE, 0, 41, -21, TidebreakZoneId.QUARRY_CAVE),
        target("ore/quarry-2", TidebreakTargetKind.ORE_NODE, 11, 41, -31, TidebreakZoneId.QUARRY_CAVE),
        target("ore/quarry-3", TidebreakTargetKind.ORE_NODE, 20, 41, -39, TidebreakZoneId.QUARRY_CAVE),
    )

    val supplierRecords = listOf(
        target(
            "supply-record/tidal-flat",
            TidebreakTargetKind.SUPPLIER_RECORD,
            -3,
            41,
            17,
            TidebreakZoneId.TIDAL_FLAT,
        ),
        target(
            "supply-record/quarry",
            TidebreakTargetKind.SUPPLIER_RECORD,
            5,
            41,
            -17,
            TidebreakZoneId.QUARRY_CAVE,
        ),
    )

    val couplerRack = target(
        "market/coupler-rack",
        TidebreakTargetKind.COUPLER_RACK,
        -16,
        41,
        0,
        TidebreakZoneId.HARBOR_MARKET,
    )

    val practicePost = target(
        "breakwater/practice-post",
        TidebreakTargetKind.PRACTICE_POST,
        24,
        41,
        0,
        TidebreakZoneId.BREAKWATER_STAGING,
        maxDistance = 6.0,
    )

    val crashPillars = listOf(
        target(
            "breakwater/crash-pillar-north",
            TidebreakTargetKind.CRASH_PILLAR,
            39,
            41,
            -12,
            TidebreakZoneId.BREAKWATER_ARENA,
            maxDistance = 8.0,
        ),
        target(
            "breakwater/crash-pillar-south",
            TidebreakTargetKind.CRASH_PILLAR,
            39,
            41,
            12,
            TidebreakZoneId.BREAKWATER_ARENA,
            maxDistance = 8.0,
        ),
    )

    val targets: List<TidebreakTargetSpec> = oreNodes + supplierRecords + couplerRack + practicePost + crashPillars
    val targetsById: Map<String, TidebreakTargetSpec> = targets.associateBy(TidebreakTargetSpec::targetId)

    val brineclawSpawns = listOf(
        TidebreakCombatSpawnSpec("brineclaw/quarry-1", Pos(7.5, 41.0, -22.5), TidebreakZoneId.QUARRY_CAVE),
        TidebreakCombatSpawnSpec("brineclaw/quarry-2", Pos(16.5, 41.0, -34.5), TidebreakZoneId.QUARRY_CAVE),
        TidebreakCombatSpawnSpec("brineclaw/staging", Pos(24.5, 41.0, 4.5), TidebreakZoneId.BREAKWATER_STAGING),
    )

    init {
        require(actors.map { it.id }.distinct().size == actors.size) { "Duplicate actor ID" }
        require(targetsById.size == targets.size) { "Duplicate Tidebreak target ID" }
        require(actors.all { worldBounds.contains(it.position) }) { "Actor outside Tidebreak world" }
        require(targets.all { worldBounds.contains(it.position) }) { "Target outside Tidebreak world" }
    }

    private fun target(
        suffix: String,
        kind: TidebreakTargetKind,
        x: Int,
        y: Int,
        z: Int,
        zone: TidebreakZoneId,
        maxDistance: Double = 4.5,
    ) = TidebreakTargetSpec(
        targetId = "tidebreak:$suffix",
        kind = kind,
        position = Pos(x + 0.5, y.toDouble(), z + 0.5),
        maxInteractionDistance = maxDistance,
        zone = zone,
    )
}
