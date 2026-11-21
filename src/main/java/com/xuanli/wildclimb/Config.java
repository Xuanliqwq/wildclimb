package com.xuanli.wildclimb;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * 旷野攀爬 / Wildclimb 配置。
 *
 * <p>只保留四个玩家可调的数值：攀爬速度、最小墙面高度、攀爬耐力消耗、攀爬跳跃耐力消耗。
 * 其余手感参数都是内置常量，不给玩家额外负担。</p>
 */
public final class Config {
    /** 攀爬跳跃初速度（刻/格），按原版重力与阻力积分约 2 格高度。 */
    private static final double JUMP_VELOCITY = 0.55D;
    /** 松手后重新抓墙的冷却（刻）。 */
    private static final int GRAB_COOLDOWN = 8;
    /** 起跳后仍能抓住墙面的窗口（刻）。 */
    private static final int AIRBORNE_GRAB_WINDOW = 12;
    /** 允许攀爬的最小墙面高度（格）。 */
    private static final int MIN_WALL_HEIGHT = 2;

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.DoubleValue CLIMB_SPEED = BUILDER
            .comment("攀爬速度（格/秒），上下左右移动都使用这个速度。",
                    "Climbing speed in blocks per second; used for up, down and sideways movement.")
            .defineInRange("climbSpeed", 1.6D, 0.0D, 20.0D);

    public static final ModConfigSpec.DoubleValue CLIMB_STAMINA = BUILDER
            .comment("攀爬时每秒消耗的耐力值（Paraglider 耐力单位，一圈 = 1000）。",
                    "Stamina spent per second while climbing (Paraglider units, one wheel = 1000).")
            .defineInRange("climbStamina", 80.0D, 0.0D, 10000.0D);

    public static final ModConfigSpec.DoubleValue CLIMB_JUMP_STAMINA = BUILDER
            .comment("攀爬跳跃消耗的耐力值。",
                    "Stamina cost of a climb jump.")
            .defineInRange("climbJumpStamina", 250.0D, 0.0D, 10000.0D);

    public static final ModConfigSpec.BooleanValue AUTO_DEPLOY_PARAGLIDER = BUILDER
            .comment("空中按跳跃键时自动展开背包里的滑翔伞；再次按下可收起。",
                    "Deploy the paraglider from the inventory by pressing jump in mid-air; press again to close.")
            .define("autoDeployParaglider", true);

    static final ModConfigSpec SPEC = BUILDER.build();

    private Config() {}

    /** 攀爬时每刻消耗的耐力值。 */
    public static double climbStaminaPerTick() {
        return get(CLIMB_STAMINA, 80.0D) / 20.0D;
    }

    public static double climbSpeedPerTick() {
        return get(CLIMB_SPEED, 1.6D) / 20.0D;
    }

    /** 向下攀爬与向上攀爬同速。 */
    public static double descendSpeedPerTick() {
        return climbSpeedPerTick();
    }

    /** 横向攀爬与上下攀爬同速。 */
    public static double lateralSpeedPerTick() {
        return climbSpeedPerTick();
    }

    public static double jumpStamina() {
        return get(CLIMB_JUMP_STAMINA, 250.0D);
    }

    public static double jumpVelocity() {
        return JUMP_VELOCITY;
    }

    /** 允许攀爬的最小墙面高度（格），固定值，不再对外暴露配置。 */
    public static int minWallHeight() {
        return MIN_WALL_HEIGHT;
    }

    public static int grabCooldown() {
        return GRAB_COOLDOWN;
    }

    public static int airborneGrabWindow() {
        return AIRBORNE_GRAB_WINDOW;
    }

    public static boolean autoDeployParaglider() {
        try {
            return AUTO_DEPLOY_PARAGLIDER.get();
        } catch (IllegalStateException notLoadedYet) {
            return true;
        }
    }

    private static double get(ModConfigSpec.DoubleValue value, double fallback) {
        try {
            return value.get();
        } catch (IllegalStateException notLoadedYet) {
            return fallback;
        }
    }

    private static int getInt(ModConfigSpec.IntValue value, int fallback) {
        try {
            return value.get();
        } catch (IllegalStateException notLoadedYet) {
            return fallback;
        }
    }
}
