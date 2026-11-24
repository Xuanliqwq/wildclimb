package com.xuanli.wildclimb.climb;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

/** Non-persistent climbing and gliding state attached to each player. */
public final class ClimbData {
    /** Vertical distance from the feet to the ledge while hanging. */
    public static final double HANG_OFFSET = 1.55D;
    /** Mantle duration in ticks, matching the 0.5-second animation. */
    public static final int MANTLE_TICKS = 10;
    /** Fraction of the mantle spent lifting before moving onto the platform. */
    public static final double MANTLE_RISE = 0.55D;

    public boolean climbing;

    public boolean gliding;

    public Direction wallDir = Direction.NORTH;
    public int wallX;
    public int wallZ;
    public int bottomSolidY;
    public int topSolidY;

    public double lateral;

    public double minY;
    public double maxY;
    public double topSurfaceY;

    public boolean moving;
    public double jumpVelocity;
    public int jumpLateralDir;
    public int jumpCooldown;
    /** Remaining ticks of temporary wall-contact tolerance after a corner or grab. */
    public int graceTicks;
    public int ticks;
    public int cooldown;
    public int grabWindow;
    public boolean wasJumpDown;
    public boolean atTop;

    /** Previously settled stamina total, used to avoid duplicate charges. */
    public double lastStamina = -1.0D;
    /** Unpaid stamina cost of a climb jump. */
    public double pendingJumpCost;
    public int invalidTicks;

    public double lastX;
    public double lastY;
    public double lastZ;

    public int mantleTicks;
    public Vec3 mantleStart = Vec3.ZERO;
    public Vec3 mantleCrest = Vec3.ZERO;
    public Vec3 mantleTarget = Vec3.ZERO;

    public int cornerTicks;
    public Vec3 cornerStart = Vec3.ZERO;
    public Vec3 cornerBend = Vec3.ZERO;
    public Vec3 cornerTarget = Vec3.ZERO;
    public float cornerStartYaw;
    public double climbVelocity;
    public double lateralVelocity;
    public int jumpAnimationTicks;
    public int traverseDirection;
    public ClimbHelper.Wall cornerWall;
    public float renderBodyYaw;
    public float previousRenderBodyYaw;
    public float renderHeadYaw;
    public float previousRenderHeadYaw;
    public boolean hasClimbRotation;
    public boolean descending;
    public float animationSpeed = 1.0F;
    public static final int CORNER_TICKS = 8;
    public static final int JUMP_COOLDOWN_TICKS = 8;

    public void setWall(Direction dir, int x, int z, int bottomSolidY, int topSolidY) {
        this.wallDir = dir;
        this.wallX = x;
        this.wallZ = z;
        this.bottomSolidY = bottomSolidY;
        this.topSolidY = topSolidY;
        this.topSurfaceY = topSolidY + 1.0D;
        this.minY = bottomSolidY;
        this.maxY = Math.max(bottomSolidY, topSurfaceY - HANG_OFFSET);
    }

    public void moveToColumn(int x, int z, int bottomSolidY, int topSolidY) {
        setWall(this.wallDir, x, z, bottomSolidY, topSolidY);
    }

    public void clearClimbing() {
        this.climbing = false;
        this.moving = false;
        this.climbVelocity = 0.0D;
        this.lateralVelocity = 0.0D;
        this.cornerTicks = 0;
        this.cornerWall = null;
        this.descending = false;
        this.jumpAnimationTicks = 0;
        this.traverseDirection = 0;
        this.jumpVelocity = 0.0D;
        this.jumpLateralDir = 0;
        this.jumpCooldown = 0;
        this.graceTicks = 0;
        this.atTop = false;
        this.ticks = 0;
    }
}
