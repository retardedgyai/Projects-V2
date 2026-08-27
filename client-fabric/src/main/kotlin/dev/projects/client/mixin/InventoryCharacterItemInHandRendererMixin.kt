package dev.projects.client.mixin

import dev.projects.client.InventoryCharacterScreen
import net.minecraft.client.Minecraft
import net.minecraft.client.player.LocalPlayer
import net.minecraft.client.renderer.ItemInHandRenderer
import net.minecraft.client.renderer.SubmitNodeCollector
import com.mojang.blaze3d.vertex.PoseStack
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

@Mixin(ItemInHandRenderer::class)
class InventoryCharacterItemInHandRendererMixin {
    @Inject(method = ["submitHandsWithItems"], at = [At("HEAD")], cancellable = true)
    private fun hideHandsWhileInventoryIsOpen(
        tickDelta: Float,
        poseStack: PoseStack,
        submitNodeCollector: SubmitNodeCollector,
        player: LocalPlayer,
        packedLight: Int,
        callbackInfo: CallbackInfo,
    ) {
        if (Minecraft.getInstance().gui.screen() is InventoryCharacterScreen) callbackInfo.cancel()
    }
}
