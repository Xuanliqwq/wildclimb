package com.xuanli.wildclimb.client;

import com.xuanli.wildclimb.Wildclimb;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import tictim.paraglider.api.ParagliderAPI;
import tictim.paraglider.api.ParagliderItemCapability;
import tictim.paraglider.api.movement.Movement;
import tictim.paraglider.api.movement.PlayerState;

/**
 * 让“背包里有滑翔伞但不拿在手上”的滑翔也有伞盖模型。
 *
 * <p>Paraglider 的伞盖是手持物品模型，所以这里在渲染玩家主手物品时，如果玩家正处于滑翔状态、
 * 主手又不是滑翔伞，就把要渲染的模型临时换成滑翔伞——只影响渲染，不动背包。</p>
 *
 * <p>滑翔伞物品由 Paraglider 的 {@code paraglider:paragliders} 标签解析，避免硬依赖具体物品类。</p>
 */
public final class ParagliderVisual {
    private static ItemStack canopy = ItemStack.EMPTY;
    private static boolean canopyResolved;

    private ParagliderVisual() {}

    /** 玩家当前是否处于滑翔状态。 */
    public static boolean isParagliding(Player player) {
        try {
            PlayerState state = Movement.get(player).state();
            return state != null && state.paragliding();
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** 是否应该用伞盖模型替换主手渲染。 */
    public static boolean shouldRenderCanopy(Player player) {
        if (player.isSpectator() || player.isPassenger()) return false;
        return isParagliding(player);
    }

    /**
     * 用于渲染的滑翔伞物品（取标签里的第一个）。
     *
     * <p>Paraglider 的“展开”模型由物品堆上的 paragliding 组件决定，所以这里必须把这个标记也打上，
     * 否则渲染出来的是收起状态的小模型，而不是展开的伞盖。</p>
     */
    public static ItemStack canopy() {
        if (canopyResolved) return canopy;
        canopyResolved = true;

        ItemStack stack = BuiltInRegistries.ITEM.getTag(ParagliderAPI.PARAGLIDERS)
                .flatMap(tag -> tag.stream().findFirst())
                .map(holder -> new ItemStack(holder.value()))
                .orElse(ItemStack.EMPTY);

        if (stack.isEmpty()) {
            Wildclimb.LOGGER.warn("[wildclimb] 找不到任何带有 paraglider:paragliders 标签的物品，伞盖模型不可用");
            canopy = stack;
            return canopy;
        }

        try {
            ParagliderItemCapability.defaultImpl().setParagliding(stack, true);
        } catch (RuntimeException e) {
            Wildclimb.LOGGER.warn("[wildclimb] 无法把渲染用的滑翔伞标记为展开状态", e);
        }
        canopy = stack;
        return canopy;
    }

    /**
     * 渲染主手时实际应该用的物品：滑翔中且主手不是滑翔伞时换成伞盖，否则保持原样。
     */
    public static ItemStack handStackForRender(Player player) {
        ItemStack original = player.getMainHandItem();
        if (original.is(ParagliderAPI.PARAGLIDERS)) return original;
        if (!shouldRenderCanopy(player)) return original;

        ItemStack canopy = canopy();
        return canopy.isEmpty() ? original : canopy;
    }
}
