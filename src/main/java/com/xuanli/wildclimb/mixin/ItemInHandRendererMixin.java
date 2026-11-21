package com.xuanli.wildclimb.mixin;

import com.xuanli.wildclimb.client.ParagliderVisual;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.world.item.ItemStack;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 第一人称下，把“背包滑翔”时的手持物品渲染替换成滑翔伞，于是第一人称也能看到伞盖。
 *
 * <p>只改渲染时读到的物品，背包物品本身不变。</p>
 */
@Mixin(ItemInHandRenderer.class)
public abstract class ItemInHandRendererMixin {
    @Redirect(
            method = "renderHandsWithItems",
            at = @At(value = "FIELD",
                    target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;mainHandItem:Lnet/minecraft/world/item/ItemStack;",
                    opcode = Opcodes.GETFIELD))
    private ItemStack wildclimb$firstPersonCanopy(ItemInHandRenderer renderer) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return ItemStack.EMPTY;
        return ParagliderVisual.handStackForRender(player);
    }
}
