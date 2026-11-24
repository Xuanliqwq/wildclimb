package com.xuanli.wildclimb.client;

import com.xuanli.wildclimb.Wildclimb;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import tictim.paraglider.api.ParagliderAPI;
import tictim.paraglider.api.ParagliderItemCapability;
import tictim.paraglider.api.movement.Movement;
import tictim.paraglider.api.movement.PlayerState;

/** Supplies the deployed canopy model for inventory gliding. */
public final class ParagliderVisual {
    private static ItemStack canopy = ItemStack.EMPTY;
    private static boolean canopyResolved;

    private ParagliderVisual() {}

    public static boolean isParagliding(Player player) {
        try {
            PlayerState state = Movement.get(player).state();
            return state != null && state.paragliding();
        } catch (RuntimeException e) {
            return false;
        }
    }

    public static boolean shouldRenderCanopy(Player player) {
        if (player.isSpectator() || player.isPassenger()) return false;
        return isParagliding(player);
    }

    /** Creates a visual-only stack with Paraglider’s deployed model component. */
    public static ItemStack canopy() {
        if (canopyResolved) return canopy;
        canopyResolved = true;

        ItemStack stack = BuiltInRegistries.ITEM.getTag(ParagliderAPI.PARAGLIDERS)
                .flatMap(tag -> tag.stream().findFirst())
                .map(holder -> new ItemStack(holder.value()))
                .orElse(ItemStack.EMPTY);

        if (stack.isEmpty()) {
            Wildclimb.LOGGER.warn("[wildclimb] No items carry the paraglider:paragliders tag; canopy rendering is unavailable");
            canopy = stack;
            return canopy;
        }

        try {
            ParagliderItemCapability.defaultImpl().setParagliding(stack, true);
        } catch (RuntimeException e) {
            Wildclimb.LOGGER.warn("[wildclimb] Could not mark the render-only paraglider stack as deployed", e);
        }
        canopy = stack;
        return canopy;
    }

    public static ItemStack handStackForRender(Player player) {
        ItemStack original = player.getMainHandItem();
        if (original.is(ParagliderAPI.PARAGLIDERS)) return original;
        if (!shouldRenderCanopy(player)) return original;

        ItemStack canopy = canopy();
        return canopy.isEmpty() ? original : canopy;
    }
}
