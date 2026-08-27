package dev.projects.client.mixin

import dev.projects.client.InventoryCharacterScreen
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.Hud
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

@Mixin(Hud::class)
class InventoryCharacterHudMixin {
    @Inject(method = ["extractHotbarAndDecorations"], at = [At("HEAD")], cancellable = true)
    private fun hideVanillaHotbarWhileInventoryIsOpen(
        graphics: GuiGraphicsExtractor,
        tickCounter: DeltaTracker,
        callbackInfo: CallbackInfo,
    ) {
        if (Minecraft.getInstance().gui.screen() is InventoryCharacterScreen) callbackInfo.cancel()
    }
}
