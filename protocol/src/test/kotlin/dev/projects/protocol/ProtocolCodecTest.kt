package dev.projects.protocol

import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ProtocolCodecTest {
    @Test
    fun `current protocol version is accepted`() {
        ProtocolVersion.requireCompatible(ProtocolVersion.CURRENT)
    }

    @Test
    fun `mismatched protocol version is rejected`() {
        assertFailsWith<ProtocolVersionMismatchException> {
            ProtocolVersion.requireCompatible(ProtocolVersion.CURRENT + 1)
        }
    }

    @Test
    fun `handshake messages round trip`() {
        assertRoundTrip(ProtocolHello(ProtocolVersion.CURRENT))
        assertRoundTrip(ProtocolHelloAck(ProtocolVersion.CURRENT))
    }

    @Test
    fun `unknown protocol data fails closed`() {
        assertFailsWith<IllegalArgumentException> {
            ProtocolCodec.decode(byteArrayOf(127))
        }
    }

    @Test
    fun `truncated protocol data fails closed`() {
        assertFailsWith<IllegalArgumentException> {
            ProtocolCodec.decode(byteArrayOf(1))
        }
    }

    @Test
    fun `attack input round trips`() {
        assertRoundTrip(AttackInput(AttackInputState.PRESS, 42))
        assertRoundTrip(AttackInput(AttackInputState.RELEASE, 43))
    }

    @Test
    fun `attack input represents press and release state changes`() {
        assertEquals(listOf(AttackInputState.PRESS, AttackInputState.RELEASE), AttackInputState.entries)
        val transitions = listOf(
            AttackInput(AttackInputState.PRESS, 42),
            AttackInput(AttackInputState.RELEASE, 43),
        )
        assertEquals(AttackInputState.PRESS, transitions.first().state)
        assertEquals(AttackInputState.RELEASE, transitions.last().state)
        assertEquals(listOf(42L, 43L), transitions.map { it.sequence })
    }

    @Test
    fun `attack started round trips`() {
        assertRoundTrip(AttackStarted(9001))
    }

    @Test
    fun `attack hit confirmed round trips`() {
        assertRoundTrip(AttackHitConfirmed(9001, UUID.fromString("58e6f12d-cf60-4cb2-9147-6f503fe24098")))
    }

    @Test
    fun `attack debug shape round trips server supplied shape parameters`() {
        assertRoundTrip(
            AttackDebugShape(
                AttackDebugShapeKind.TWIN_RODS,
                1.0,
                2.0,
                3.0,
                0.5,
                0.7071067811865476,
                0.5,
                3.5,
                0.65,
                1.75,
            ),
        )
        assertRoundTrip(
            AttackDebugShape(
                AttackDebugShapeKind.HEAVY_BLADE,
                -1.0,
                0.0,
                4.0,
                0.0,
                0.0,
                1.0,
                4.5,
                0.40,
                2.0,
            ),
        )
    }

    @Test
    fun `dodge input round trips`() {
        assertRoundTrip(DodgeInput(-1.0, 1.0))
        assertRoundTrip(DodgeInput(0.0, 0.0))
    }

    @Test
    fun `invalid dodge direction is rejected`() {
        assertFailsWith<IllegalArgumentException> { DodgeInput(Double.NaN, 0.0) }
        assertFailsWith<IllegalArgumentException> { DodgeInput(1.1, 0.0) }

        val malformed = ProtocolCodec.encode(DodgeInput(0.0, 0.0)).copyOf().also { bytes ->
            java.nio.ByteBuffer.wrap(bytes, 1, Double.SIZE_BYTES).putDouble(Double.POSITIVE_INFINITY)
        }
        assertFailsWith<IllegalArgumentException> { ProtocolCodec.decode(malformed) }
    }

    @Test
    fun `air jump input round trips`() {
        assertRoundTrip(AirJumpInput(-1.0, 1.0))
        assertRoundTrip(AirJumpInput(0.0, 0.0))
    }

    @Test
    fun `invalid air jump direction is rejected`() {
        assertFailsWith<IllegalArgumentException> { AirJumpInput(Double.NaN, 0.0) }
        assertFailsWith<IllegalArgumentException> { AirJumpInput(1.1, 0.0) }

        val malformed = ProtocolCodec.encode(AirJumpInput(0.0, 0.0)).copyOf().also { bytes ->
            java.nio.ByteBuffer.wrap(bytes, 1, Double.SIZE_BYTES).putDouble(Double.POSITIVE_INFINITY)
        }
        assertFailsWith<IllegalArgumentException> { ProtocolCodec.decode(malformed) }
    }

    @Test
    fun `class skill input round trips`() {
        assertRoundTrip(ClassSkillInput(ClassSkillSlot.SKILL_1, -1.0, 1.0))
        assertRoundTrip(ClassSkillInput(ClassSkillSlot.ULTIMATE, 0.0, 0.0))
    }

    @Test
    fun `invalid class skill input fails closed`() {
        assertFailsWith<IllegalArgumentException> { ClassSkillInput(ClassSkillSlot.SKILL_1, Double.NaN, 0.0) }
        assertFailsWith<IllegalArgumentException> { ClassSkillInput(ClassSkillSlot.SKILL_1, 1.1, 0.0) }

        val unknownSlot = ProtocolCodec.encode(ClassSkillInput(ClassSkillSlot.SKILL_1, 0.0, 0.0)).also { bytes ->
            bytes[1] = 99
        }
        assertFailsWith<IllegalArgumentException> { ProtocolCodec.decode(unknownSlot) }

        val malformed = ProtocolCodec.encode(ClassSkillInput(ClassSkillSlot.SKILL_1, 0.0, 0.0)).copyOf().also { bytes ->
            java.nio.ByteBuffer.wrap(bytes, 2, Double.SIZE_BYTES).putDouble(Double.POSITIVE_INFINITY)
        }
        assertFailsWith<IllegalArgumentException> { ProtocolCodec.decode(malformed) }
    }

    @Test
    fun `resource snapshot round trips mana and all skill cooldowns`() {
        assertRoundTrip(ClassResourceSnapshot(75, 100, 20, 80, 50, 100, 40, 60))
    }

    @Test
    fun `progression messages round trip`() {
        assertRoundTrip(
            ProgressionSnapshot(
                revision = 7L,
                level = 2,
                xp = 25,
                xpRequiredForNextLevel = 150,
                grantedPassivePoints = 1,
                spentPassivePoints = 1,
                allocatedPassiveNodeIds = listOf("projects:passive/force"),
            ),
        )
        assertRoundTrip(ProgressionXpGained(100, 2, 1, 1))
        assertRoundTrip(PassiveNodeSpendRequest(7L, "projects:passive/tempo"))
        assertRoundTrip(
            PassiveNodeSpendResponse(
                "projects:passive/tempo",
                PassiveNodeSpendResult.STALE_REVISION,
                7L,
            ),
        )
    }

    @Test
    fun `invalid progression messages fail closed`() {
        assertFailsWith<IllegalArgumentException> {
            ProgressionSnapshot(0L, 46, 0, 100, 0, 0, emptyList())
        }
        assertFailsWith<IllegalArgumentException> {
            PassiveNodeSpendRequest(0L, "projects:passive/unknown node")
        }

        val malformed = ProtocolCodec.encode(
            ProgressionSnapshot(0L, 1, 0, 100, 0, 0, emptyList()),
        ).copyOf().also { bytes ->
            bytes[1 + Long.SIZE_BYTES + Int.SIZE_BYTES * 5] = 7
        }
        assertFailsWith<IllegalArgumentException> { ProtocolCodec.decode(malformed) }
    }

    @Test
    fun `malformed resource snapshot fails closed`() {
        val malformed = ProtocolCodec.encode(ClassResourceSnapshot(75, 100, 20, 80, 50, 100, 40, 60)).copyOf().also { bytes ->
            java.nio.ByteBuffer.wrap(bytes, 25, Int.SIZE_BYTES).putInt(61)
        }
        assertFailsWith<IllegalArgumentException> { ProtocolCodec.decode(malformed) }
    }

    @Test
    fun `ground telegraph messages round trip`() {
        assertRoundTrip(
            GroundTelegraphStart(
                telegraphId = 17,
                centerX = 1.0,
                centerY = 40.0,
                centerZ = -2.0,
                facingX = 0.6,
                facingZ = 0.8,
                radius = 6.0,
                angleDegrees = 90.0,
                durationTicks = 140,
            ),
        )
        assertRoundTrip(GroundTelegraphRemove(17))
    }

    @Test
    fun `ground telegraph rejects non finite values and clamps wire bounds`() {
        assertFailsWith<IllegalArgumentException> {
            GroundTelegraphStart(1, Double.NaN, 0.0, 0.0, 0.0, 1.0, 1.0, 90.0, 20)
        }
        val malformed = ProtocolCodec.encode(
            GroundTelegraphStart(1, 0.0, 0.0, 0.0, 0.0, 1.0, 1.0, 90.0, 20),
        ).copyOf().also { bytes ->
            java.nio.ByteBuffer.wrap(bytes, 1 + Long.SIZE_BYTES + Double.SIZE_BYTES * 5, Double.SIZE_BYTES)
                .putDouble(1000.0)
        }
        val decoded = ProtocolCodec.decode(malformed) as GroundTelegraphStart
        assertEquals(64.0, decoded.radius)
    }

    @Test
    fun `slash editor parameters reject non finite values and clamp bounds`() {
        assertFailsWith<IllegalArgumentException> {
            SlashEditorParameters.clamped(
                Double.NaN, 0.0, 1.0, 90.0, 0.0, 0.0, 0.0, 0.1, 1, 0.1, 0.1, 0.1, 8, 0xffffff, 1.0,
            )
        }
        val parameters = SlashEditorParameters.clamped(
            -100.0, 100.0, 100.0, -100.0, 100.0, -100.0, 100.0, 100.0, 9, 100.0, 100.0, -100.0, 1000, -1, 100.0,
        )
        assertEquals(-4.0, parameters.originY)
        assertEquals(8.0, parameters.forwardOffset)
        assertEquals(12.0, parameters.length)
        assertEquals(10.0, parameters.arcSpan)
        assertEquals(4.0, parameters.curvature)
        assertEquals(3, parameters.laneCount)
        assertEquals(2.0, parameters.laneSpacing)
        assertEquals(0.05, parameters.spacing)
        assertEquals(80, parameters.durationTicks)
        assertEquals(0, parameters.color)
        assertEquals(32.0, parameters.targetDistance)
    }

    @Test
    fun `vfx editor messages round trip`() {
        val parameters = SlashEditorParameters()
        listOf<ProtocolMessage>(
            VfxEditorOpen(parameters),
            VfxSlashPreviewRequest(42L, parameters),
            VfxSlashApplySkill3(Skill3VfxTarget.PULSE, parameters),
            VfxSlashApplySkill3(Skill3VfxTarget.FINISHER, parameters),
            VfxSlashPreviewCancel,
            VfxSlashSaveRequest("blue slash", parameters),
            VfxSlashDraftList(listOf("blue slash")),
            VfxSlashDraftLoadRequest("blue slash"),
            VfxSlashDraft("blue slash", parameters),
            VfxEditorNotice("saved"),
        ).forEach(::assertRoundTrip)
    }

    @Test
    fun `vfx editor parameter decode rejects non finite values`() {
        val malformed = ProtocolCodec.encode(VfxSlashPreviewRequest(1L, SlashEditorParameters())).copyOf().also { bytes ->
            java.nio.ByteBuffer.wrap(bytes, 1 + Long.SIZE_BYTES, Double.SIZE_BYTES).putDouble(Double.NaN)
        }
        assertFailsWith<IllegalArgumentException> { ProtocolCodec.decode(malformed) }
    }

    private fun assertRoundTrip(message: ProtocolMessage) {
        assertEquals(message, ProtocolCodec.decode(ProtocolCodec.encode(message)))
    }
}
