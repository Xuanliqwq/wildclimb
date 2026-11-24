package com.xuanli.wildclimb.compat;

import com.xuanli.wildclimb.Wildclimb;

import net.minecraft.world.entity.player.Player;
import tictim.paraglider.api.stamina.Stamina;

/** Reads and spends Paraglider stamina, including temporary extra stamina. */
public final class ParagliderCompat {
    private static boolean warned;

    private ParagliderCompat() {}

    public static double totalStamina(Player player) {
        try {
            Stamina stamina = Stamina.get(player);
            return stamina.stamina() + stamina.extraStamina();
        } catch (RuntimeException e) {
            warn(e);
            return 0.0D;
        }
    }

    public static boolean isDepleted(Player player) {
        try {
            return Stamina.get(player).isDepleted();
        } catch (RuntimeException e) {
            warn(e);
            return false;
        }
    }

    public static double take(Player player, double amount) {
        try {
            return Stamina.get(player).takeStamina(amount, false, false);
        } catch (RuntimeException e) {
            warn(e);
            return 0.0D;
        }
    }

    public static boolean hasStamina(Player player) {
        try {
            Stamina stamina = Stamina.get(player);
            return !stamina.isDepleted() && stamina.stamina() + stamina.extraStamina() > 0.0D;
        } catch (RuntimeException e) {
            warn(e);
            return false;
        }
    }

    private static void warn(RuntimeException e) {
        if (!warned) {
            warned = true;
            Wildclimb.LOGGER.error("Paraglider stamina API is not available yet", e);
        }
    }
}
