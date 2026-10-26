package dev.projects.server.puppet

import dev.projects.server.coreloop.CoreCombatPresentation
import net.minestom.server.Auth
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.Player
import net.minestom.server.instance.block.Block
import net.minestom.server.network.ConnectionState
import net.minestom.server.network.packet.server.SendablePacket
import net.minestom.server.network.player.GameProfile
import net.minestom.server.network.player.PlayerConnection
import java.awt.image.BufferedImage
import java.net.InetSocketAddress
import java.net.SocketAddress
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.math.sqrt
import kotlin.test.*

class PlayerPuppetTest {
    @Test fun `both motions load from their Blockbench models and match scripts player_rig pose`() {
        val gs = assertNotNull(PlayerMotion.load("greatsword_charge"))
        val db = assertNotNull(PlayerMotion.load("dual_blades_dance"))
        assertFalse(gs.weapon.dual); assertTrue(db.weapon.dual)
        assertEquals(4.1, gs.length, 1e-9); assertEquals(1.57, db.length, 1e-9)
        assertNull(PlayerMotion.load("../secrets")); assertNull(PlayerMotion.load("missing_motion"))
        // Same numbers as `scripts/player_rig.py` samples (origin, then the +z axis) for this saved model.
        fun check(t: Double, bone: String, origin: DoubleArray, z: DoubleArray) {
            val m = gs.pose(t).getValue(bone)
            for (i in 0..2) assertEquals(origin[i], m.origin[i], 1e-3, "$bone origin $t")
            for (i in 0..2) assertEquals(z[i], m.rotation[i * 3 + 2], 1e-3, "$bone z $t")
        }
        check(1.0, "weapon_r", doubleArrayOf(-0.2872, 1.07384, -0.03114), doubleArrayOf(0.0564, 0.0262, -0.99806))
        check(2.86, "arm_r_low", doubleArrayOf(-0.26364, 0.6259, 0.5667), doubleArrayOf(0.13173, 0.9447, 0.30032))
        check(2.86, "head", doubleArrayOf(0.16288, 0.91156, 0.58195), doubleArrayOf(0.17583, -0.16138, 0.9711))
    }

    @Test fun `feet stay on the ground, hands hold the weapons and the puppet ends where the player stands`() {
        for (name in listOf("greatsword_charge", "dual_blades_dance", "greatsword_combo")) {
            val motion = PlayerMotion.load(name)!!
            var t = 0.0
            while (t <= motion.length) {
                val pose = motion.pose(t)
                for (leg in listOf("leg_r_low", "leg_l_low")) {
                    val foot = pose.getValue(leg).apply(doubleArrayOf(0.0, -6 * PlayerMotion.K, 0.0))
                    assertTrue(foot[1] in -.08..0.25, "$name $leg foot y ${foot[1]} at $t")
                }
                val hand = pose.getValue("arm_r_low").apply(doubleArrayOf(0.0, -5.5 * PlayerMotion.K, 0.0))
                val grip = pose.getValue("weapon_r").origin
                assertTrue(sqrt((0..2).sumOf { (hand[it] - grip[it]).let { d -> d * d } }) < .05, "$name grip at $t")
                t += .05
            }
            val end = motion.pose(motion.length).getValue("root").origin
            assertEquals(0.0, end[0], 1e-6); assertEquals(12 * PlayerMotion.K, end[1], 1e-3); assertEquals(0.0, end[2], 1e-3)
        }
    }

    @Test fun `skin colours follow the part models' tint order`() {
        val parts = PuppetSkin.parts
        assertEquals(10, parts.size)
        assertEquals(384, parts.getValue("head").pixels.size / 2)
        assertEquals(352, parts.getValue("torso").pixels.size / 2)
        assertEquals(112, parts.getValue("arm_l_low").pixels.size / 2)
        // A skin whose every pixel encodes its own (x, y): each colour tells which pixel it came from.
        val skin = BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until 64) for (x in 0 until 64) skin.setRGB(x, y, (0xFF shl 24) or (x shl 8) or y)
        for ((name, part) in parts) {
            val colors = PuppetSkin.colors(skin, name)
            colors.forEachIndexed { i, c -> assertEquals(part.pixels[2 * i] shl 8 or part.pixels[2 * i + 1], c, name) }
        }
        // The face (front of the head) is the 8x8 at (8, 8): the head's front pixels all come from there.
        val head = parts.getValue("head").pixels
        val front = (0 until head.size / 2).map { head[2 * it] to head[2 * it + 1] }.filter { (x, y) -> x in 8..15 && y in 8..15 }
        assertEquals(64, front.size)
        // Legacy 64x32 skins: the left arm reads the right arm.
        val legacy = BufferedImage(64, 32, BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until 32) for (x in 0 until 64) legacy.setRGB(x, y, (0xFF shl 24) or (x shl 8) or y)
        assertEquals(PuppetSkin.colors(legacy, "arm_r_up"), PuppetSkin.colors(legacy, "arm_l_up"))
    }

    @Test fun `the player hides behind a puppet for the motion and comes back after`() = player { owner ->
        CoreCombatPresentation.pack(owner, true)
        try {
            val motion = PlayerMotion.load("dual_blades_dance")!!
            val puppet = PlayerPuppet(owner, motion)
            puppet.start()
            assertTrue(owner.isInvisible)
            val displays = owner.instance!!.entities.filter { it.entityType == EntityType.ITEM_DISPLAY }
            assertEquals(12, displays.size, "10 body parts and two blades")
            assertTrue(displays.none { owner in it.viewers }, "the owner never sees their own puppet")
            repeat((motion.length * 20).toInt() + 2) { puppet.tick() }
            assertTrue(puppet.finished)
            assertFalse(owner.isInvisible)
            assertTrue(owner.instance!!.entities.none { it.entityType == EntityType.ITEM_DISPLAY && !it.isRemoved })
        } finally { CoreCombatPresentation.forget(owner) }
    }

    @Test fun `a puppet whose player left its world is cleaned up`() = player { owner ->
        val puppet = PlayerPuppet(owner, PlayerMotion.load("greatsword_charge")!!)
        puppet.start()
        owner.remove()
        puppet.tick()
        assertTrue(puppet.finished)
    }

    /** Exports what the displays are sent, for scripts/preview_puppet_displays.py (the pack models drawn as sent). */
    @Test fun `export the sent display transforms for the pack-model preview`() = player { owner ->
        CoreCombatPresentation.pack(owner, true)
        try {
            val out = mutableListOf<Map<String, Any>>()
            for (name in listOf("greatsword_charge", "dual_blades_dance")) {
                val motion = PlayerMotion.load(name)!!
                val puppet = PlayerPuppet(owner, motion)
                puppet.start()
                val frames = mutableListOf<List<Map<String, Any>>>()
                repeat((motion.length * 20).toInt()) {
                    frames += owner.instance!!.entities.filter { it.entityType == EntityType.ITEM_DISPLAY && !it.isRemoved }.map { e ->
                        val m = e.entityMeta as net.minestom.server.entity.metadata.display.ItemDisplayMeta
                        val item = m.itemStack
                        mapOf("model" to item.get(net.minestom.server.component.DataComponents.ITEM_MODEL).toString(),
                            "pos" to listOf(e.position.x() - owner.position.x(), e.position.y() - owner.position.y(), e.position.z() - owner.position.z()),
                            "translation" to listOf(m.translation.x(), m.translation.y(), m.translation.z()),
                            "rotation" to m.leftRotation.map { it.toDouble() }, "scale" to m.scale.x(),
                            "colors" to (item.get(net.minestom.server.component.DataComponents.CUSTOM_MODEL_DATA)?.colors()?.map { c -> (c.red() shl 16) or (c.green() shl 8) or c.blue() } ?: emptyList<Int>()))
                    }
                    puppet.tick()
                }
                puppet.stop()
                out += mapOf("name" to name, "frames" to frames)
            }
            val cwd = java.nio.file.Path.of(System.getProperty("user.dir"))
            val root = if (cwd.fileName.toString() == "server-minestom") cwd.parent else cwd
            java.nio.file.Files.createDirectories(root.resolve(".tools"))
            java.nio.file.Files.writeString(root.resolve(".tools/puppet-displays.json"), com.google.gson.Gson().toJson(out))
        } finally { CoreCombatPresentation.forget(owner) }
    }

    private fun player(action: (Player) -> Unit) {
        MinecraftServer.init(Auth.Offline())
        val map = MinecraftServer.getInstanceManager().createInstanceContainer()
        map.viewDistance(2)
        map.setGenerator { it.modifier().fillHeight(0, 40, Block.STONE) }
        for (x in -3..3) for (z in -3..3) map.loadChunk(x, z).get(10, TimeUnit.SECONDS)
        val connection = object : PlayerConnection() {
            override fun sendPacket(packet: SendablePacket) {}
            override fun getRemoteAddress(): SocketAddress = InetSocketAddress("127.0.0.1", 0)
        }
        connection.setClientState(ConnectionState.PLAY); connection.setServerState(ConnectionState.PLAY)
        val p = Player(connection, GameProfile(UUID.randomUUID(), "PuppetTest"))
        connection.player = p
        p.setInstance(map, Pos(8.0, 40.0, 8.0)).get(10, TimeUnit.SECONDS)
        try { action(p) } finally {
            if (!p.isRemoved) p.remove()
            MinecraftServer.getInstanceManager().unregisterInstance(map)
        }
    }

}
