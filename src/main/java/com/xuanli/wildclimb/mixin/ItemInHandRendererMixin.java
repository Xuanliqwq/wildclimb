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

/** Renders the inventory paraglider canopy in first person. */
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
