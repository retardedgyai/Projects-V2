package dev.projects.client.mixin

import dev.projects.client.ItemTooltipLayout
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner
import net.minecraft.resources.Identifier
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.Unique
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.ModifyArg
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

@Mixin(GuiGraphicsExtractor::class)
class ItemTooltipLayoutMixin {
    @Unique
    private var projectSTitlePending = false

    @Unique
    private var projectSTitleX = 0

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
        projectSTitlePending = ItemTooltipLayout.shouldCenterTitle(style?.namespace, style?.path) && components.isNotEmpty()
        if (!projectSTitlePending) return

        val tooltipWidth = components.maxOf { component -> component.getWidth(font) }
        val titleWidth = components.first().getWidth(font)
        projectSTitleX = ItemTooltipLayout.centeredTitleX(0, tooltipWidth, titleWidth)
    }

    @ModifyArg(
        method = ["tooltip"],
        at = At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/screens/inventory/tooltip/ClientTooltipComponent;extractText(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/gui/Font;II)V",
        ),
        index = 2,
    )
    private fun centerProjectSTitle(originalX: Int): Int {
        if (!projectSTitlePending) return originalX
        projectSTitlePending = false
        return originalX + projectSTitleX
    }
}
