package dev.projects.client

import dev.projects.protocol.StarweaverHudCelestial
import dev.projects.protocol.StarweaverHudSnapshot
import net.minecraft.client.gui.GuiGraphicsExtractor

/**
 * Starweaver-only celestial HUD. It renders server-provided marks directly as
 * compact bitmap-style icons; it does not predict or rotate the queue locally.
 */
object StarweaverCelestialHud {
    private const val TILE_SIZE = 20
    private const val TILE_GAP = 2
    private const val ARROW_WIDTH = 12
    private const val RELOAD_TICKS = 60
    private const val HUD_CENTER_OFFSET_Y = 42

    private val sunIcon = arrayOf(
        "   #   ",
        "  ###  ",
        " # # # ",
        "#######",
        " # # # ",
        "  ###  ",
        "   #   ",
    )
    private val moonIcon = arrayOf(
        "  ###  ",
        " ####  ",
        "#####  ",
        "####   ",
        "#####  ",
        " ####  ",
        "  ###  ",
    )
    private val starIcon = arrayOf(
        "   #   ",
        "  ###  ",
        " ##### ",
        "#######",
        " ##### ",
        " # # # ",
        "#  #  #",
    )

    fun render(context: GuiGraphicsExtractor, snapshot: StarweaverHudSnapshot?) {
        if (snapshot?.selected != true) return

        val queueWidth = TILE_SIZE * 5 + TILE_GAP * 4
        val totalWidth = TILE_SIZE + TILE_GAP + ARROW_WIDTH + TILE_GAP + queueWidth
        val left = (context.guiWidth() - totalWidth) / 2
        val centerY = context.guiHeight() / 2 + HUD_CENTER_OFFSET_Y
        val tileY = centerY - TILE_SIZE / 2
        val arrowX = left + TILE_SIZE + TILE_GAP
        val queueX = arrowX + ARROW_WIDTH + TILE_GAP

        drawTile(context, left, tileY, TILE_SIZE, snapshot.stored, highlighted = false)
        drawSwapArrow(context, arrowX, centerY)

        for (index in 0 until 5) {
            val x = queueX + index * (TILE_SIZE + TILE_GAP)
            drawTile(
                context = context,
                x = x,
                y = tileY,
                size = TILE_SIZE,
                celestial = snapshot.queue.getOrNull(index),
                highlighted = index == 0 && snapshot.queue.isNotEmpty(),
            )
        }

        if (snapshot.conjunctionAvailable && snapshot.queue.size >= 2) {
            drawOutline(
                context,
                queueX - 2,
                tileY - 2,
                TILE_SIZE * 2 + TILE_GAP + 4,
                TILE_SIZE + 4,
                0xFFF3D36A.toInt(),
            )
        }

        if (snapshot.reloadTicksRemaining > 0) {
            val ratio = (snapshot.reloadTicksRemaining.toDouble() / RELOAD_TICKS).coerceIn(0.0, 1.0)
            val progressWidth = (queueWidth * ratio).toInt()
            context.fill(queueX, tileY + TILE_SIZE + 3, queueX + progressWidth, tileY + TILE_SIZE + 4, 0xFF8A6BC4.toInt())
        }
    }

    private fun drawTile(
        context: GuiGraphicsExtractor,
        x: Int,
        y: Int,
        size: Int,
        celestial: StarweaverHudCelestial?,
        highlighted: Boolean,
    ) {
        val background = when {
            highlighted -> 0xDD2A3A4D.toInt()
            celestial != null -> 0xAA10151C.toInt()
            else -> 0x5510151C
        }
        context.fill(x, y, x + size, y + size, background)
        if (highlighted) {
            drawOutline(context, x, y, size, size, 0xFFDDEBFF.toInt())
        } else if (celestial != null) {
            drawOutline(context, x, y, size, size, 0x884A5B70.toInt())
        }
        if (celestial != null) {
            drawIcon(context, celestial, x + 2, y + 2, size - 4)
        }
    }

    private fun drawSwapArrow(context: GuiGraphicsExtractor, x: Int, centerY: Int) {
        val color = 0xFFDDEBFF.toInt()
        val lineY = centerY - 1
        val start = x + 1
        val end = x + ARROW_WIDTH - 1
        context.fill(start, lineY, end, lineY + 2, color)
        context.fill(start, lineY, start + 3, lineY - 2, color)
        context.fill(start, lineY + 1, start + 3, lineY + 4, color)
        context.fill(end - 3, lineY - 2, end, lineY, color)
        context.fill(end - 3, lineY + 2, end, lineY + 4, color)
    }

    private fun drawOutline(
        context: GuiGraphicsExtractor,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        color: Int,
    ) {
        context.fill(x, y, x + width, y + 1, color)
        context.fill(x, y + height - 1, x + width, y + height, color)
        context.fill(x, y, x + 1, y + height, color)
        context.fill(x + width - 1, y, x + width, y + height, color)
    }

    private fun drawIcon(
        context: GuiGraphicsExtractor,
        celestial: StarweaverHudCelestial,
        x: Int,
        y: Int,
        size: Int,
    ) {
        val pattern = when (celestial) {
            StarweaverHudCelestial.SUN -> sunIcon
            StarweaverHudCelestial.MOON -> moonIcon
            StarweaverHudCelestial.STAR -> starIcon
        }
        val color = when (celestial) {
            StarweaverHudCelestial.SUN -> 0xFFFFD66B.toInt()
            StarweaverHudCelestial.MOON -> 0xFFB8D6F2.toInt()
            StarweaverHudCelestial.STAR -> 0xFFE3B8FF.toInt()
        }
        val pixelSize = (size / pattern.size).coerceAtLeast(1)
        val iconWidth = pattern.maxOf { it.length } * pixelSize
        val iconHeight = pattern.size * pixelSize
        val startX = x + (size - iconWidth) / 2
        val startY = y + (size - iconHeight) / 2
        for ((row, line) in pattern.withIndex()) {
            for ((column, value) in line.withIndex()) {
                if (value != '#') continue
                val pixelX = startX + column * pixelSize
                val pixelY = startY + row * pixelSize
                context.fill(pixelX, pixelY, pixelX + pixelSize, pixelY + pixelSize, color)
            }
        }
    }
}
