package dev.projects.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class VfxEditor2ProtocolTest {
    @Test
    fun `checkpoint A messages round trip`() {
        listOf<ProtocolMessage>(
            VfxEditor2Open("Ronin Q"),
            VfxEditor2PreviewStart(17L),
            VfxEditor2PreviewStop,
            VfxEditor2Status(VfxEditor2StatusKind.PLAYING, "Playing"),
            VfxEditor2Status(VfxEditor2StatusKind.ERROR, "Preview failed"),
        ).forEach { message ->
            assertEquals(message, ProtocolCodec.decode(ProtocolCodec.encode(message)))
        }
    }

    @Test
    fun `checkpoint A protocol validates bounded fields`() {
        assertFailsWith<IllegalArgumentException> { VfxEditor2Open(" ") }
        assertFailsWith<IllegalArgumentException> { VfxEditor2PreviewStart(-1L) }
        assertFailsWith<IllegalArgumentException> {
            VfxEditor2Status(VfxEditor2StatusKind.ERROR, "x".repeat(161))
        }
    }

    @Test
    fun `old slash editor messages still round trip`() {
        listOf<ProtocolMessage>(
            VfxEditorOpen(),
            VfxSlashPreviewRequest(3L, SlashEditorParameters()),
            VfxSlashPreviewCancel,
        ).forEach { message ->
            assertEquals(message, ProtocolCodec.decode(ProtocolCodec.encode(message)))
        }
    }
}
