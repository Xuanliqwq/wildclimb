package com.xuanli.wildclimb;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Gameplay configuration and built-in climbing constants. */
public final class Config {
    private static final double JUMP_VELOCITY = 0.55D;
    private static final int GRAB_COOLDOWN = 8;
    private static final int AIRBORNE_GRAB_WINDOW = 12;
    private static final int MIN_WALL_HEIGHT = 2;

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.DoubleValue CLIMB_SPEED = BUILDER
            .comment("Climbing speed in blocks per second; used for up, down and sideways movement.")
            .defineInRange("climbSpeed", 1.6D, 0.0D, 20.0D);

    public static final ModConfigSpec.DoubleValue CLIMB_STAMINA = BUILDER
            .comment("Stamina spent per second while climbing (Paraglider units, one wheel = 1000).")
            .defineInRange("climbStamina", 80.0D, 0.0D, 10000.0D);

    public static final ModConfigSpec.DoubleValue CLIMB_JUMP_STAMINA = BUILDER
            .comment("Stamina cost of a climb jump.")
            .defineInRange("climbJumpStamina", 250.0D, 0.0D, 10000.0D);

    public static final ModConfigSpec.BooleanValue AUTO_DEPLOY_PARAGLIDER = BUILDER
            .comment("Deploy the paraglider from the inventory by pressing jump in mid-air; press again to close.")
            .define("autoDeployParaglider", true);

    static final ModConfigSpec SPEC = BUILDER.build();

    private Config() {}

    public static double climbStaminaPerTick() {
        return get(CLIMB_STAMINA, 80.0D) / 20.0D;
    }

    public static double climbSpeedPerTick() {
        return get(CLIMB_SPEED, 1.6D) / 20.0D;
    }

    public static double jumpStamina() {
        return get(CLIMB_JUMP_STAMINA, 250.0D);
    }

    public static double jumpVelocity() {
        return JUMP_VELOCITY;
    }

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

}
