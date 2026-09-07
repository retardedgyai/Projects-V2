package dev.projects.server.coreloop

import net.minestom.server.Auth
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Pos
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.Player
import net.minestom.server.instance.block.Block
import net.minestom.server.network.ConnectionState
import net.minestom.server.network.packet.server.SendablePacket
import net.minestom.server.network.player.GameProfile
import net.minestom.server.network.player.PlayerConnection
import java.net.InetSocketAddress
import java.net.SocketAddress
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.test.*

class CoreCombatMeshTest {
    private fun effect(job: CoreClass, id: String, phase: CoreSkillVisualPhase = CoreSkillVisualPhase.PULSE, pulse: Int = 0, length: Double = 0.0) =
        CoreSkillEffect(job, CoreSkillCatalog.skills(job).first { it.icon == id }, Vec(8.0, 41.0, 8.0), Vec(0.0, 0.0, 1.0), phase, pulse, rayLength = length)

    @Test fun `all seventy skills have bounded pack-backed solid silhouettes`() {
        val index = javaClass.getResourceAsStream("/core-ui-pack/index.txt")!!.bufferedReader().use { it.readLines().toSet() }
        for (job in CoreClass.entries) for (skill in CoreSkillCatalog.skills(job)) for (phase in CoreSkillVisualPhase.entries) {
            val parts = CoreCombatMeshArt.parts(effect(job, skill.icon, phase, length = if (skill.motion == CoreSkillMotion.RAY) 18.0 else 0.0))
            assertTrue(parts.size in 1..7, "${skill.icon} $phase")
            for (p in parts) {
                val name = "${p.shape}_${p.palette}"
                for (kind in listOf("models", "items")) {
                    val path = "assets/projects/$kind/combat_vfx/$name.json"
                    assertTrue(path in index, path)
                    assertNotNull(javaClass.getResource("/core-ui-pack/$path"))
                }
                assertTrue(listOf(p.scale.x(), p.scale.y(), p.scale.z()).all { it.isFinite() && it > 0 && it <= 24 })
                val q = CoreCombatMeshArt.rotation(p.yaw, p.pitch, p.roll)
                assertTrue(q.all(Float::isFinite))
                assertTrue(abs(q.sumOf { (it * it).toDouble() } - 1.0) < 1e-5)
            }
        }
    }

    @Test fun `starfall forms a falling nucleus then releases rings not another ray`() {
        val prepare = CoreCombatMeshArt.parts(effect(CoreClass.STARWEAVER, "starfall", CoreSkillVisualPhase.PREPARE))
        assertTrue(prepare.any { it.shape == "star" && it.offset.y() >= 6 && it.travel.y() < -4 })
        val impact = CoreCombatMeshArt.parts(effect(CoreClass.STARWEAVER, "starfall"))
        assertEquals(2, impact.count { it.shape == "orbit" })
        assertTrue(impact.any { it.shape == "star" && it.scale.x() >= 2.5 })
        assertTrue(impact.any { it.shape == "burst" })
        assertEquals(3, impact.count { it.shape == "lance" && it.travel.lengthSquared() > 1.0 })
        assertTrue(impact.all { it.palette == "astral" })
    }

    @Test fun `blade pulses alternate and clipped ray never becomes a twenty four block beam`() {
        val a = CoreCombatMeshArt.parts(effect(CoreClass.ASSASSIN, "ass_ult", pulse = 0)).first()
        val b = CoreCombatMeshArt.parts(effect(CoreClass.ASSASSIN, "ass_ult", pulse = 1)).first()
        assertTrue(a.spin * b.spin < 0)
        val beam = CoreCombatMeshArt.parts(effect(CoreClass.STARWEAVER, "star_needle", length = 3.25)).first()
        assertEquals(3.25, beam.scale.z())
        assertEquals(1.625, beam.offset.z())
        assertTrue(CoreCombatMeshArt.parts(effect(CoreClass.STARWEAVER, "star_needle", length = 0.0)).isEmpty())
        val invalid = CoreSkillEffect(CoreClass.WARRIOR, CoreSkillCatalog.skills(CoreClass.WARRIOR).first(), Vec.ZERO, Vec(Double.NaN, 0.0, 0.0))
        assertTrue(CoreCombatMeshArt.parts(invalid).isEmpty())
    }

    @Test fun `native displays require pack obey viewer setting expire and cancel without leaks`() = player { p ->
        val meshes = CoreCombatMeshes(p)
        val scene = p.instance
        val original = scene.entities.size
        val effect = effect(CoreClass.STARWEAVER, "starfall")
        try {
            meshes.play(effect)
            assertEquals(0, meshes.size, "No PAPER items may appear for a missing pack")
            CoreCombatPresentation.pack(p, true)
            meshes.play(effect); meshes.tick()
            assertEquals(7, meshes.size)
            assertEquals(7, scene.entities.count { it !== p && p in it.viewers })
            assertEquals(CoreCombatPresentation.Detail.SUBDUED, CoreCombatPresentation.cycle(p))
            meshes.tick()
            assertEquals(2, scene.entities.count { it !== p && p in it.viewers })
            assertEquals(CoreCombatPresentation.Detail.MINIMAL, CoreCombatPresentation.cycle(p))
            meshes.tick()
            assertEquals(0, scene.entities.count { it !== p && p in it.viewers })
            repeat(8) { meshes.tick() }
            assertEquals(0, meshes.size)
            assertEquals(original, scene.entities.size)
            assertEquals(CoreCombatPresentation.Detail.FULL, CoreCombatPresentation.cycle(p))
            repeat(20) { meshes.play(effect) }
            assertEquals(20, meshes.size)
            meshes.cancel(); meshes.cancel()
            assertEquals(original, scene.entities.size)
            val party = List(7) { CoreCombatMeshes(p) }
            try {
                party.forEach { renderer -> repeat(20) { renderer.play(effect) } }
                assertEquals(120, party.sumOf { it.size }, "The shared scene cap must apply across actors")
                assertEquals(0, party.last().size)
            } finally { party.forEach { it.cancel() } }
            assertEquals(original, scene.entities.size)
            CoreCombatPresentation.forget(p)
            assertEquals(CoreCombatPresentation.Detail.FULL, CoreCombatPresentation.detail(p))
            assertFalse(CoreCombatPresentation.packed(p))
        } finally { meshes.cancel(); CoreCombatPresentation.forget(p) }
    }

    private fun player(action: (Player) -> Unit) {
        MinecraftServer.init(Auth.Offline())
        val map = MinecraftServer.getInstanceManager().createInstanceContainer()
        map.viewDistance(2)
        map.setGenerator { it.modifier().fillHeight(0, 40, Block.STONE) }
        for (x in -3..3) for (z in -3..3) map.loadChunk(x, z).get(10, TimeUnit.SECONDS)
        val connection = object : PlayerConnection() {
            override fun sendPacket(packet: SendablePacket) = Unit
            override fun getRemoteAddress(): SocketAddress = InetSocketAddress("127.0.0.1", 0)
        }
        connection.setClientState(ConnectionState.PLAY); connection.setServerState(ConnectionState.PLAY)
        val p = Player(connection, GameProfile(UUID.randomUUID(), "CombatMeshTest"))
        connection.player = p
        p.setInstance(map, Pos(8.0, 40.0, 8.0)).get(10, TimeUnit.SECONDS)
        try { action(p) } finally { p.remove(); MinecraftServer.getInstanceManager().unregisterInstance(map) }
    }
}
