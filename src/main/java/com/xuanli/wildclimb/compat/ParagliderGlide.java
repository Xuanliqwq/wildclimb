package com.xuanli.wildclimb.compat;

import com.xuanli.wildclimb.Config;
import com.xuanli.wildclimb.climb.ClimbAttachments;
import com.xuanli.wildclimb.climb.ClimbData;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import tictim.paraglider.api.ParagliderAPI;

/**
 * 不动物品的滑翔：玩家背包里有滑翔伞时，直接把 Paraglider 的玩家状态推到“滑翔”，
 * 由它自己处理缓降、耐力消耗和音效，不移动、不替换任何物品。
 */
public final class ParagliderGlide {
    /** 主背包（含快捷栏）0-35，副手 40。 */
    private static final int MAIN_INVENTORY_SIZE = 36;
    private static final int OFFHAND_SLOT = 40;

    private ParagliderGlide() {}

    /** 玩家背包（含副手）里是否有滑翔伞。 */
    public static boolean hasParaglider(Player player) {
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < MAIN_INVENTORY_SIZE; slot++) {
            if (inventory.getItem(slot).is(ParagliderAPI.PARAGLIDERS)) return true;
        }
        return inventory.getItem(OFFHAND_SLOT).is(ParagliderAPI.PARAGLIDERS);
    }

    /** 处理客户端的开伞 / 收伞请求。 */
    public static void request(ServerPlayer player, boolean glide) {
        ClimbData data = ClimbAttachments.get(player);

        // 收伞：无条件执行
        if (!glide) {
            data.gliding = false;
            return;
        }

        if (!Config.autoDeployParaglider()) return;
        if (player.onGround() || player.isSpectator() || player.isPassenger()) return;
        if (player.isInWater() || player.isInLava() || player.isFallFlying()) return;
        if (ParagliderCompat.isDepleted(player) || ParagliderCompat.totalStamina(player) <= 0.0D) return;
        if (!hasParaglider(player)) return;
        if (data.climbing) return;
        data.gliding = true;
    }

    /** 每刻维持滑翔标记：落地、进水中、耗尽、丢失滑翔伞等情况自动结束。 */
    public static void tick(ServerPlayer player) {
        ClimbData data = ClimbAttachments.get(player);
        if (!data.gliding) return;

        if (player.onGround() || player.isDeadOrDying() || player.isPassenger()
                || player.isInWater() || player.isInLava() || player.isFallFlying()
                || ParagliderCompat.isDepleted(player) || ParagliderCompat.totalStamina(player) <= 0.0D
                || !hasParaglider(player)) {
            data.gliding = false;
            return;
        }

        // 开始攀爬时收起滑翔伞
        if (data.climbing) data.gliding = false;
    }

    /** 是否可以停留在滑翔状态（供 Paraglider 状态机判定）。 */
    public static boolean isGliding(Player player) {
        ClimbData data = ClimbAttachments.get(player);
        if (!data.gliding || data.climbing) return false;
        if (player.onGround() || player.isInWater() || player.isInLava() || player.isFallFlying()) return false;
        return !ParagliderCompat.isDepleted(player) && ParagliderCompat.totalStamina(player) > 0.0D;
    }

    public static void clear(Player player) {
        ClimbAttachments.get(player).gliding = false;
    }
}
