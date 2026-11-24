package com.xuanli.wildclimb.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.xuanli.wildclimb.climb.ClimbAttachments;
import com.xuanli.wildclimb.climb.ClimbData;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {
    private static final String RENDER = "render(Lnet/minecraft/world/entity/LivingEntity;FF"
            + "Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V";
    private static final String ROT_LERP = "Lnet/minecraft/util/Mth;rotLerp(FFF)F";

    @ModifyExpressionValue(method = RENDER, at = @At(value = "INVOKE", target = ROT_LERP, ordinal = 0))
    private float wildclimb$bodyYaw(float vanilla, LivingEntity entity, float yaw, float partialTick) {
        if (!(entity instanceof AbstractClientPlayer player)) return vanilla;
        ClimbData climb = ClimbAttachments.get(player);
        return climb.hasClimbRotation
                ? Mth.rotLerp(partialTick, climb.previousRenderBodyYaw, climb.renderBodyYaw) : vanilla;
    }

    @ModifyExpressionValue(method = RENDER, at = @At(value = "INVOKE", target = ROT_LERP, ordinal = 1))
    private float wildclimb$headYaw(float vanilla, LivingEntity entity, float yaw, float partialTick) {
        if (!(entity instanceof AbstractClientPlayer player)) return vanilla;
        ClimbData climb = ClimbAttachments.get(player);
        return climb.hasClimbRotation
                ? Mth.rotLerp(partialTick, climb.previousRenderHeadYaw, climb.renderHeadYaw) : vanilla;
    }
}
