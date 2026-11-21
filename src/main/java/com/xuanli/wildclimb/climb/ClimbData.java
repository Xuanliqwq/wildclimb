package com.xuanli.wildclimb.climb;

import net.minecraft.core.Direction;

/**
 * 每个玩家身上的攀爬状态。运行期状态，不做持久化。
 */
public final class ClimbData {
    /** 双手够到墙顶时，脚底距离墙顶表面的高度差。 */
    public static final double HANG_OFFSET = 1.55D;

    /** 是否正在攀爬。 */
    public boolean climbing;

    /** 服务端：是否用“背包里的滑翔伞”维持滑翔状态（不移动物品）。 */
    public boolean gliding;

    /** 所抓墙面的朝向（从玩家指向墙面的方向）。 */
    public Direction wallDir = Direction.NORTH;
    /** 当前所在的墙面方块列。 */
    public int wallX;
    public int wallZ;
    /** 墙面所在方块列最下方 / 最上方实心方块的 Y。 */
    public int bottomSolidY;
    public int topSolidY;

    /** 沿着墙面方向的自由坐标，用于横向攀爬。 */
    public double lateral;

    /** 身体允许的最低 / 最高脚部坐标。 */
    public double minY;
    public double maxY;
    /** 墙顶表面的 Y（翻上去以后站立的高度）。 */
    public double topSurfaceY;

    /** 是否正在移动（上下左右都算）：决定消耗耐力还是挂在墙上休息。 */
    public boolean moving;
    /** 攀爬跳跃的剩余向上速度，> 0 表示正在跳跃中。 */
    public double jumpVelocity;
    /** 攀爬跳跃的横向方向：{@code -1}/{@code 0}/{@code +1}，侧向跳跃时用。 */
    public int jumpLateralDir;
    /** 攀爬跳跃的内置冷却（刻），防止一边跳一边爬导致动作不连贯。 */
    public int jumpCooldown;
    /** 绕角 / 换面后的宽限期，宽限期内不因为贴墙判定失败而脱手。 */
    public int graceTicks;
    /** 已经攀爬的刻数，用于动画。 */
    public int ticks;
    /** 重新抓墙的冷却。 */
    public int cooldown;
    /** 客户端：跳起后仍可抓墙的剩余窗口。 */
    public int grabWindow;
    /** 客户端：上一刻跳跃键是否按下（用于检测按下瞬间）。 */
    public boolean wasJumpDown;
    /** 客户端：双手是否已经够到墙顶边缘。 */
    public boolean atTop;

    /** 服务端：上一刻的耐力值，用于兜底结算消耗。 */
    public double lastStamina = -1.0D;
    /** 服务端：攀爬跳跃还没扣完的耐力。 */
    public double pendingJumpCost;
    /** 服务端：连续判定“贴墙失效”的刻数。 */
    public int invalidTicks;

    /** 服务端：上一刻的坐标，用来判断玩家是否真的在移动。 */
    public double lastX;
    public double lastY;
    public double lastZ;

    /** 客户端：翻上墙顶的动画进度，0 表示没有在翻越。 */
    public int mantleTicks;
    public double mantleStartX;
    public double mantleStartY;
    public double mantleStartZ;
    public double mantleTargetX;
    public double mantleTargetY;
    public double mantleTargetZ;

    /** 客户端：绕过墙角时的动画进度，0 表示没有在绕角。 */
    public int cornerTicks;
    public double cornerStartX;
    public double cornerStartY;
    public double cornerStartZ;
    public double cornerTargetX;
    public double cornerTargetY;
    public double cornerTargetZ;
    public double cornerPivotX;
    public double cornerPivotZ;

    /** 根据墙面重算所有派生坐标。 */
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

    /** 切换到同一面墙的另一列（横向攀爬）。 */
    public void moveToColumn(int x, int z, int bottomSolidY, int topSolidY) {
        setWall(this.wallDir, x, z, bottomSolidY, topSolidY);
    }

    public void clearClimbing() {
        this.climbing = false;
        this.moving = false;
        this.jumpVelocity = 0.0D;
        this.jumpLateralDir = 0;
        this.jumpCooldown = 0;
        this.graceTicks = 0;
        this.atTop = false;
        this.ticks = 0;
    }
}
