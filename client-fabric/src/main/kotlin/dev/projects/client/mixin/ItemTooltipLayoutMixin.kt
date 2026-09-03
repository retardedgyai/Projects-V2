package dev.projects.client.mixin

import com.mojang.blaze3d.platform.InputConstants
import dev.projects.client.ItemTooltipHeaderPalette
import dev.projects.client.ItemTooltipDetails
import dev.projects.client.ItemTooltipLayout
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.world.item.ItemStack
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.Unique
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.Redirect
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

@Mixin(GuiGraphicsExtractor::class)
class ItemTooltipLayoutMixin {
    @Unique
    private var projectSTitlePending = false

    @Unique
    private var projectSTitleX = 0

    @Unique
    private var projectSTooltipWidth = 0

    @Unique
    private var projectSHeaderPalette: ItemTooltipHeaderPalette? = null

    @Redirect(
        method = ["setTooltipForNextFrame(Lnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;II)V"],
        at = At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/screens/Screen;getTooltipFromItem(Lnet/minecraft/client/Minecraft;Lnet/minecraft/world/item/ItemStack;)Ljava/util/List;",
        ),
    )
    private fun selectProjectSTooltipDetailLines(minecraft: Minecraft, stack: ItemStack): List<Component> {
        val shiftDown = InputConstants.isKeyDown(minecraft.window, InputConstants.KEY_LSHIFT) ||
            InputConstants.isKeyDown(minecraft.window, InputConstants.KEY_RSHIFT)
        return Screen.getTooltipFromItem(minecraft, stack)
            .filter { line -> ItemTooltipDetails.shouldDisplay(line.string, shiftDown) }
    }

    @Inject(
        method = ["tooltip"],
        at = [At("HEAD")],
    )
    private fun prepareProjectSTitleLayout(
        font: Font,
        components: List<ClientTooltipComponent>,
        mouseX: Int,
        mouseY: Int,
        positioner: ClientTooltipPositioner,
        style: Identifier?,
        callbackInfo: CallbackInfo,
    ) {
        projectSHeaderPalette = ItemTooltipLayout.headerPalette(style?.namespace, style?.path)
        projectSTitlePending = projectSHeaderPalette != null && components.isNotEmpty()
        if (!projectSTitlePending) return

        projectSTooltipWidth = components.maxOf { component -> component.getWidth(font) }
        val titleWidth = components.first().getWidth(font)
        projectSTitleX = ItemTooltipLayout.centeredTitleX(0, projectSTooltipWidth, titleWidth)
    }

    @Redirect(
        method = ["tooltip"],
        at = At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/screens/inventory/tooltip/ClientTooltipComponent;extractText(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/gui/Font;II)V",
        ),
    )
    private fun drawProjectSTooltipLine(
        component: ClientTooltipComponent,
        graphics: GuiGraphicsExtractor,
        font: Font,
        x: Int,
        y: Int,
    ) {
        if (!projectSTitlePending) {
            component.extractText(graphics, font, x, y)
            return
        }

        projectSTitlePending = false
        projectSHeaderPalette?.let { palette ->
            graphics.fill(x - 1, y - 1, x + projectSTooltipWidth + 1, y + font.lineHeight + 1, palette.plateColor)
            graphics.fill(x, y + font.lineHeight + 1, x + projectSTooltipWidth, y + font.lineHeight + 2, palette.dividerColor)
        }
        component.extractText(graphics, font, x + projectSTitleX, y)
    }
}
