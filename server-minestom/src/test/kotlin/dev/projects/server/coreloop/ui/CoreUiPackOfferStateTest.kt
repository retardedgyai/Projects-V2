package dev.projects.server.coreloop.ui

import net.kyori.adventure.resource.ResourcePackStatus
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CoreUiPackOfferStateTest {
    @Test fun `intermediate statuses never enable glyphs`() {
        val state = CoreUiPackOfferState()
        assertNull(state.accept(state.packId, ResourcePackStatus.ACCEPTED))
        assertNull(state.accept(state.packId, ResourcePackStatus.DOWNLOADED))
        assertFalse(state.loaded)
        assertTrue(assertNotNull(state.accept(state.packId, ResourcePackStatus.SUCCESSFULLY_LOADED)).loaded)
    }

    @Test fun `discard after successful application restores plain rendering`() {
        val state = CoreUiPackOfferState()
        val success = assertNotNull(state.accept(state.packId, ResourcePackStatus.SUCCESSFULLY_LOADED))
        val discard = assertNotNull(state.accept(state.packId, ResourcePackStatus.DISCARDED))
        assertFalse(state.loaded)
        assertFalse(discard.loaded)
        assertTrue(state.isCurrent(discard))
        assertFalse(state.isCurrent(success), "Already queued success must not overwrite a newer fallback")
    }

    @Test fun `failed reload after successful application restores plain rendering`() {
        val state = CoreUiPackOfferState()
        state.accept(state.packId, ResourcePackStatus.SUCCESSFULLY_LOADED)
        assertFalse(assertNotNull(state.accept(state.packId, ResourcePackStatus.FAILED_RELOAD)).loaded)
        assertFalse(state.loaded)
    }

    @Test fun `global event and request callback publish a terminal status only once`() {
        val state = CoreUiPackOfferState()
        val success = assertNotNull(state.accept(state.packId, ResourcePackStatus.SUCCESSFULLY_LOADED))
        assertNull(state.accept(state.packId, ResourcePackStatus.SUCCESSFULLY_LOADED))
        assertTrue(state.isCurrent(success))
        val decline = assertNotNull(state.accept(state.packId, ResourcePackStatus.DECLINED))
        assertNull(state.accept(state.packId, ResourcePackStatus.DECLINED))
        assertTrue(state.isCurrent(decline))
    }

    @Test fun `another pack status cannot change this offer`() {
        val state = CoreUiPackOfferState()
        assertNull(state.accept(UUID.randomUUID(), ResourcePackStatus.SUCCESSFULLY_LOADED))
        assertFalse(state.loaded)
        val success = assertNotNull(state.accept(state.packId, ResourcePackStatus.SUCCESSFULLY_LOADED))
        assertNull(state.accept(UUID.randomUUID(), ResourcePackStatus.DISCARDED))
        assertTrue(state.isCurrent(success))
    }

    @Test fun `forget or close invalidation rejects late events and queued rendering`() {
        val state = CoreUiPackOfferState()
        val success = assertNotNull(state.accept(state.packId, ResourcePackStatus.SUCCESSFULLY_LOADED))
        state.invalidate()
        assertFalse(state.loaded)
        assertFalse(state.isCurrent(success))
        assertNull(state.accept(state.packId, ResourcePackStatus.DISCARDED))
        assertNull(state.accept(state.packId, ResourcePackStatus.SUCCESSFULLY_LOADED))
        state.invalidate()
        assertFalse(state.loaded)
    }

    @Test fun `reoffer has a fresh protocol identity and ignores the removed pack events`() {
        val previous = CoreUiPackOfferState()
        val previousSuccess = assertNotNull(previous.accept(previous.packId, ResourcePackStatus.SUCCESSFULLY_LOADED))
        previous.invalidate()
        val replacement = CoreUiPackOfferState()
        assertNotEquals(previous.packId, replacement.packId)
        assertNull(replacement.accept(previous.packId, ResourcePackStatus.SUCCESSFULLY_LOADED))
        assertFalse(replacement.loaded)
        val replacementSuccess = assertNotNull(replacement.accept(replacement.packId, ResourcePackStatus.SUCCESSFULLY_LOADED))
        assertNull(replacement.accept(previous.packId, ResourcePackStatus.DISCARDED))
        assertTrue(replacement.isCurrent(replacementSuccess))
        assertFalse(replacement.isCurrent(previousSuccess))
        assertFalse(previous.isCurrent(previousSuccess))
    }
}
