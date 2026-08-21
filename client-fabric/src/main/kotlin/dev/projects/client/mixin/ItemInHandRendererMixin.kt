package dev.projects.client.mixin

import com.mojang.blaze3d.vertex.PoseStack
import dev.projects.client.TwinRodFirstPersonAnimationState
import net.minecraft.client.player.AbstractClientPlayer
import net.minecraft.client.player.LocalPlayer
import net.minecraft.client.renderer.ItemInHandRenderer
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.HumanoidArm
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.Shadow
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

@Mixin(ItemInHandRenderer::class)
abstract class ItemInHandRendererMixin {
    @Shadow
    abstract fun submitArmWithItem(
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
    )

    @Inject(method = ["submitHandsWithItems"], at = [At("TAIL")])
    private fun renderTwinRodOppositeArm(
        frameInterp: Float,
        poseStack: PoseStack,
        submitNodeCollector: SubmitNodeCollector,
        player: LocalPlayer,
        lightCoords: Int,
        callbackInfo: CallbackInfo,
    ) {
        if (player.mainHandItem.item != Items.BLAZE_ROD || !player.offhandItem.isEmpty()) return

        val activeArm = if (TwinRodFirstPersonAnimationState.activeArmIsRight()) {
            HumanoidArm.RIGHT
        } else {
            HumanoidArm.LEFT
        }
        if (activeArm == player.mainArm) return

        // Vanilla skips an arm when its hand is empty. Reuse its private path so
        // the opposite arm and rod receive the same normal first-person setup.
        submitArmWithItem(
            player,
            frameInterp,
            player.getXRot(frameInterp),
            InteractionHand.OFF_HAND,
            0f,
            player.mainHandItem,
            1f,
            poseStack,
            submitNodeCollector,
            lightCoords,
        )
    }

    @Inject(method = ["submitArmWithItem"], at = [At("HEAD")], cancellable = true)
    private fun hideInactiveTwinRodArm(
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
        val activeArm = if (TwinRodFirstPersonAnimationState.activeArmIsRight()) HumanoidArm.RIGHT else HumanoidArm.LEFT
        if (activeArm != player.mainArm) callbackInfo.cancel()
    }

    @Inject(
        method = ["submitArmWithItem"],
        at = [At(
            value = "INVOKE",
            target = "Lcom/mojang/blaze3d/vertex/PoseStack;pushPose()V",
            shift = At.Shift.AFTER,
        )],
    )
    private fun applyTwinRodPunchPose(
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
        if (itemStack.item != Items.BLAZE_ROD) return
        val renderedArm = if (hand == InteractionHand.MAIN_HAND) player.mainArm else player.mainArm.opposite
        val activeArm = if (TwinRodFirstPersonAnimationState.activeArmIsRight()) HumanoidArm.RIGHT else HumanoidArm.LEFT
        if (renderedArm != activeArm) return

        val pose = TwinRodFirstPersonAnimationState.pose(frameInterp)
        val mirror = if (renderedArm == HumanoidArm.RIGHT) 1f else -1f
        // Apply only the shared punch motion before vanilla submits the arm and item.
        poseStack.translate(pose.translateX * mirror, pose.translateY, pose.translateZ)
        poseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(pose.rotateX))
        poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(pose.rotateY * mirror))
        poseStack.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(pose.rotateZ * mirror))
    }

    @Inject(
        method = ["submitArmWithItem"],
        at = [At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;renderItem(" +
                "Lnet/minecraft/world/entity/LivingEntity;" +
                "Lnet/minecraft/world/item/ItemStack;" +
                "Lnet/minecraft/world/item/ItemDisplayContext;" +
                "Lcom/mojang/blaze3d/vertex/PoseStack;" +
                "Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V",
        )],
    )
    private fun applyTwinRodBasePose(
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
        if (itemStack.item != Items.BLAZE_ROD) return
        val renderedArm = if (hand == InteractionHand.MAIN_HAND) player.mainArm else player.mainArm.opposite
        val activeArm = if (TwinRodFirstPersonAnimationState.activeArmIsRight()) HumanoidArm.RIGHT else HumanoidArm.LEFT
        if (renderedArm != activeArm) return

        val mirror = if (renderedArm == HumanoidArm.RIGHT) 1f else -1f
        // Keep the rod's depth-axis alignment item-only, after vanilla arm placement.
        poseStack.translate(-0.08f * mirror, 0.04f, -0.10f)
        poseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(78f))
        poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(8f * mirror))
    }
}
