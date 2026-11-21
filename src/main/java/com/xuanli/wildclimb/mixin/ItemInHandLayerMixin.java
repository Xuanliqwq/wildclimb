package com.xuanli.wildclimb.mixin;

import com.xuanli.wildclimb.client.ParagliderVisual;

import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 第三人称下，把正在“背包滑翔”的玩家主手渲染成滑翔伞，于是伞盖模型正常显示。
 *
 * <p>只替换渲染用的 {@link ItemStack}，玩家背包里的物品不会发生任何变化。</p>
 */
@Mixin(ItemInHandLayer.class)
public abstract class ItemInHandLayerMixin {
    @Redirect(
            method = "render",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/LivingEntity;getMainHandItem()Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack wildclimb$renderParagliderCanopy(LivingEntity entity) {
        if (entity instanceof Player player) {
            return ParagliderVisual.handStackForRender(player);
        }
        return entity.getMainHandItem();
    }
}
