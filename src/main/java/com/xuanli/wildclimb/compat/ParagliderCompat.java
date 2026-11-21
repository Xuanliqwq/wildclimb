package com.xuanli.wildclimb.compat;

import com.xuanli.wildclimb.Wildclimb;

import net.minecraft.world.entity.player.Player;
import tictim.paraglider.api.stamina.Stamina;

/**
 * 与 Paraglider 耐力系统的对接。
 *
 * <p>Paraglider 的耐力由“主体力 + 黄色额外体力（耐力容器）”组成，两者加起来才是可用总量。
 * 这里统一用总量判断，避免主体力耗尽、额外体力还有剩余时就被当成没体力。</p>
 */
public final class ParagliderCompat {
    private static boolean warned;

    private ParagliderCompat() {}

    /** 当前主体力值。 */
    public static double stamina(Player player) {
        try {
            return Stamina.get(player).stamina();
        } catch (RuntimeException e) {
            warn(e);
            return 0.0D;
        }
    }

    /** 主体力 + 额外（黄色）体力的总量。 */
    public static double totalStamina(Player player) {
        try {
            Stamina stamina = Stamina.get(player);
            return stamina.stamina() + stamina.extraStamina();
        } catch (RuntimeException e) {
            warn(e);
            return 0.0D;
        }
    }

    /** 耐力是否处于“耗尽”状态（此时会像滑翔伞耗尽一样缓慢恢复）。 */
    public static boolean isDepleted(Player player) {
        try {
            return Stamina.get(player).isDepleted();
        } catch (RuntimeException e) {
            warn(e);
            return false;
        }
    }

    /** 扣除耐力（优先扣主体力，再扣额外体力），返回实际扣除的量。 */
    public static double take(Player player, double amount) {
        try {
            return Stamina.get(player).takeStamina(amount, false, false);
        } catch (RuntimeException e) {
            warn(e);
            return 0.0D;
        }
    }

    /** 是否还有可以攀爬的耐力（含额外体力）。 */
    public static boolean hasStamina(Player player) {
        return !isDepleted(player) && totalStamina(player) > 0.0D;
    }

    private static void warn(RuntimeException e) {
        if (!warned) {
            warned = true;
            Wildclimb.LOGGER.error("Paraglider stamina API is not available yet", e);
        }
    }
}
