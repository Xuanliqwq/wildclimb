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

/**
 * 让“背包滑翔”的玩家也摆出握着滑翔伞的手臂姿势。
 *
 * <p>Paraglider 的手臂姿势是从主手物品上算出来的，而本模组只是替换渲染、不动物品，
 * 所以在姿势算完后补一手：滑翔中且主手不是滑翔伞时，把主手那边的 {@code ArmPose}
 * 换成 Paraglider 的滑翔姿势。</p>
 */
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
