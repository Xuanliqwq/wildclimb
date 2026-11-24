package com.xuanli.wildclimb.mixin;

import com.xuanli.wildclimb.client.ParagliderVisual;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.world.entity.HumanoidArm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import tictim.paraglider.api.ParagliderAPI;
import tictim.paraglider.client.ParaglidingArmPose;

/** Applies the gliding arm pose when the canopy comes from the inventory. */
@Mixin(PlayerRenderer.class)
public abstract class PlayerRendererMixin {
    @Inject(method = "setModelProperties", at = @At("TAIL"))
    private void wildclimb$paraglidingArmPose(AbstractClientPlayer player, CallbackInfo ci) {
        if (player.getMainHandItem().is(ParagliderAPI.PARAGLIDERS)) return;
        if (!ParagliderVisual.shouldRenderCanopy(player)) return;

        PlayerModel<AbstractClientPlayer> model = ((PlayerRenderer) (Object) this).getModel();
        HumanoidModel.ArmPose pose = ParaglidingArmPose.ENUM.getValue();
        if (player.getMainArm() == HumanoidArm.RIGHT) {
            model.rightArmPose = pose;
        } else {
            model.leftArmPose = pose;
        }
    }
}
