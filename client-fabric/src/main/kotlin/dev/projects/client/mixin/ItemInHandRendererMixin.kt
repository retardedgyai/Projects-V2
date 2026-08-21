package dev.projects.client.mixin

import com.mojang.blaze3d.vertex.PoseStack
import dev.projects.client.TwinRodFirstPersonAnimationState
import net.minecraft.client.player.AbstractClientPlayer
import net.minecraft.client.renderer.ItemInHandRenderer
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.HumanoidArm
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

@Mixin(ItemInHandRenderer::class)
abstract class ItemInHandRendererMixin {
    @Inject(
        method = ["submitArmWithItem"],
        at = [At(
            value = "INVOKE",
            target = "Lcom/mojang/blaze3d/vertex/PoseStack;pushPose()V",
            shift = At.Shift.AFTER,
        )],
    )
    private fun applyTwinRodPose(
        player: AbstractClientPlayer,
        frameInterp: Float,
        xRot: Float,
        hand: InteractionHand,
        attack: Float,
        itemStack: ItemStack,
        inverseArmHeight: Float,
        poseStack: PoseStack,
        submitNodeCollector: SubmitNodeCollector,
        lightCoords: Int,
        callbackInfo: CallbackInfo,
    ) {
        if (hand != InteractionHand.MAIN_HAND || itemStack.item != Items.BLAZE_ROD) return

        val pose = TwinRodFirstPersonAnimationState.pose(frameInterp)
        val mirror = if (player.mainArm == HumanoidArm.RIGHT) 1f else -1f
        // This transform is applied before vanilla submits either the arm or item.
        poseStack.translate(-0.08f * mirror, 0.04f, -0.10f)
        poseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(78f))
        poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(8f * mirror))
        poseStack.translate(pose.translateX * mirror, pose.translateY, pose.translateZ)
        poseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(pose.rotateX))
        poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(pose.rotateY * mirror))
        poseStack.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(pose.rotateZ * mirror))
    }
}
