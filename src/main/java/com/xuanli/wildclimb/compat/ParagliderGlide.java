package com.xuanli.wildclimb.compat;

import com.xuanli.wildclimb.Config;
import com.xuanli.wildclimb.climb.ClimbAttachments;
import com.xuanli.wildclimb.climb.ClimbData;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import tictim.paraglider.api.ParagliderAPI;

/** Tracks whether Paraglider may use a glider from the inventory without moving items. */
public final class ParagliderGlide {
    private static final int MAIN_INVENTORY_SIZE = 36;
    private static final int OFFHAND_SLOT = 40;

    private ParagliderGlide() {}

    public static boolean hasParaglider(Player player) {
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < MAIN_INVENTORY_SIZE; slot++) {
            if (inventory.getItem(slot).is(ParagliderAPI.PARAGLIDERS)) return true;
        }
        return inventory.getItem(OFFHAND_SLOT).is(ParagliderAPI.PARAGLIDERS);
    }

    public static void request(ServerPlayer player, boolean glide) {
        ClimbData data = ClimbAttachments.get(player);

        if (!glide) {
            data.gliding = false;
            return;
        }

        if (!Config.autoDeployParaglider()) return;
        if (player.onGround() || player.isSpectator() || player.isPassenger()) return;
        if (player.isInWater() || player.isInLava() || player.isFallFlying()) return;
        if (!ParagliderCompat.hasStamina(player)) return;
        if (!hasParaglider(player)) return;
        if (data.climbing) return;
        data.gliding = true;
    }

    public static void tick(ServerPlayer player, ClimbData data) {
        if (!data.gliding) return;

        if (data.climbing || player.onGround() || player.isDeadOrDying() || player.isPassenger()
                || player.isInWater() || player.isInLava() || player.isFallFlying()
                || !ParagliderCompat.hasStamina(player)
                || !hasParaglider(player)) {
            data.gliding = false;
        }
    }

    public static boolean isGliding(Player player) {
        ClimbData data = ClimbAttachments.get(player);
        if (!data.gliding || data.climbing) return false;
        if (player.onGround() || player.isInWater() || player.isInLava() || player.isFallFlying()) return false;
        return ParagliderCompat.hasStamina(player);
    }

    public static void clear(Player player) {
        ClimbAttachments.get(player).gliding = false;
    }
}
