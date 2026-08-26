package dev.projects.client

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProgressionTreeLayoutTest {
    @Test
    fun `compact 480 by 270 layout keeps all regions inside without overlap`() {
        val layout = progressionTreeLayout(480, 270)

        assertTrue(layout.compact)
        assertInside(layout.panel, layout.detail)
        assertInside(layout.panel, layout.purchase)
        assertInside(layout.panel, layout.notice)
        assertInside(layout.panel, layout.footer)
        assertTrue(layout.detail.bottom <= layout.purchase.y)
        assertTrue(layout.purchase.bottom <= layout.notice.y)
        assertTrue(layout.notice.bottom <= layout.footer.y)
        assertTrue(layout.footer.bottom <= layout.panel.bottom)
        assertNodesInside(layout)
    }

    @Test
    fun `640 by 360 layout keeps desktop regions inside without overlap`() {
        val layout = progressionTreeLayout(640, 360)

        assertTrue(!layout.compact)
        assertInside(layout.panel, layout.detail)
        assertInside(layout.panel, layout.purchase)
        assertInside(layout.panel, layout.notice)
        assertInside(layout.panel, layout.footer)
        assertTrue(layout.purchase.bottom <= layout.notice.y)
        assertTrue(layout.notice.bottom <= layout.footer.y)
        assertTrue(layout.footer.bottom <= layout.panel.bottom)
        assertNodesInside(layout)
    }

    private fun assertNodesInside(layout: ProgressionTreeLayout) {
        val positions = progressionTreeNodePositions(layout.panel)
        assertEquals(6, positions.size)
        positions.values.forEach { position ->
            assertTrue(position.x - NODE_RADIUS >= layout.panel.x)
            assertTrue(position.x + NODE_RADIUS < layout.panel.right)
            assertTrue(position.y - NODE_RADIUS >= layout.panel.y)
            assertTrue(position.y + NODE_RADIUS < layout.panel.bottom)
        }
    }

    private fun assertInside(container: HudRect, child: HudRect) {
        assertTrue(child.x >= container.x)
        assertTrue(child.y >= container.y)
        assertTrue(child.right <= container.right)
        assertTrue(child.bottom <= container.bottom)
    }

    private companion object {
        const val NODE_RADIUS = 14
    }
}

private val HudRect.right: Int
    get() = x + width

private val HudRect.bottom: Int
    get() = y + height
