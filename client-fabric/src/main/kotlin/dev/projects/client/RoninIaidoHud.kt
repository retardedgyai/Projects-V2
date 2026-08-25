package dev.projects.client

import dev.projects.protocol.RoninHudSnapshot
import net.minecraft.client.Minecraft
import net.minecraft.client.DeltaTracker
import net.minecraft.client.gui.GuiGraphicsExtractor

object RoninIaidoHud {
    fun render(context: GuiGraphicsExtractor, snapshot: RoninHudSnapshot?) {
        val ronin = snapshot?.takeIf { it.selected } ?: return
        val markWidth = 20
        val gap = 4
        val totalWidth = markWidth * 3 + gap * 2
        val startX = (context.guiWidth() - totalWidth) / 2
        val y = context.guiHeight() - 105
        context.text(Minecraft.getInstance().font, "IAIDO", startX - 28, y + 4, 0xFFB9C2CF.toInt(), true)
        for (index in 0 until 3) {
            drawSlashMark(
                context,
                startX + index * (markWidth + gap),
                y,
                lit = index < ronin.iaido,
            )
        }
    }

    private fun drawSlashMark(context: GuiGraphicsExtractor, x: Int, y: Int, lit: Boolean) {
        val color = if (lit) 0xFFF7F7FF.toInt() else 0xFF303743.toInt()
        val glow = if (lit) 0x66E34B58 else 0x00000000
        if (glow != 0) {
            context.fill(x + 5, y - 2, x + 17, y + 24, glow)
        }
        for (step in 0 until 16) {
            val px = x + 2 + step
            val py = y + 20 - step
            context.fill(px, py, px + 3, py + 4, color)
        }
    }
}
