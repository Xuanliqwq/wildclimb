package com.xuanli.wildclimb.climb;

import com.xuanli.wildclimb.Config;
import com.xuanli.wildclimb.compat.ParagliderCompat;
import com.xuanli.wildclimb.net.ClimbStatePayload;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 服务端权威逻辑：验证抓墙、跟随横向攀爬所在的方块列、结算攀爬耐力消耗与耐力耗尽。
 * 具体位移由客户端预测完成（和爬梯等原版机制类似，服务端只做校验）。
 */
public final class ClimbServer {
    /** 攀爬跳跃的耐力分几刻扣完，让耐力轮有一个平滑的过渡而不是瞬间掉一大截。 */
    private static final int JUMP_COST_TICKS = 6;
    /** 允许连续几刻判定为“贴墙失效”才真正脱手，避免服务端坐标慢一刻导致误判。 */
    private static final int INVALID_GRACE = 6;

    private ClimbServer() {}

    public static void tick(ServerPlayer player) {
        ClimbData data = ClimbAttachments.get(player);
        if (data.cooldown > 0) data.cooldown--;

        if (!data.climbing) return;

        player.setNoGravity(true);
        player.setDeltaMovement(Vec3.ZERO);
        player.fallDistance = 0.0F;
        data.ticks++;

        // 服务端根据真实位移判断玩家是否在移动（不依赖客户端输入包，避免状态机拿不到“正在移动”）
        double dx = player.getX() - data.lastX;
        double dy = player.getY() - data.lastY;
        double dz = player.getZ() - data.lastZ;
        double threshold = Math.max(0.008D, Config.climbSpeedPerTick() * 0.5D);
        data.moving = Math.abs(dy) > threshold || Math.sqrt(dx * dx + dz * dz) > threshold;
        data.lastX = player.getX();
        data.lastY = player.getY();
        data.lastZ = player.getZ();

        // 横向攀爬会让玩家进入新的一列方块，服务端跟着更新墙面范围并广播给其他玩家
        BlockPos column = ClimbHelper.columnInFront(player, data.wallDir);
        if (column.getX() != data.wallX || column.getZ() != data.wallZ) {
            ClimbHelper.Wall wall = ClimbHelper.scanColumn(player.level(),
                    column.getX(), column.getZ(), column.getY(), data.wallDir);
            if (wall != null) {
                data.moveToColumn(wall.x(), wall.z(), wall.bottomSolidY(), wall.topSolidY());
                broadcast(player, data, true);
            }
        }

        if (ClimbHelper.canStillClimb(player, data)) {
            data.invalidTicks = 0;
            data.graceTicks = 0;
        } else if (data.graceTicks > 0) {
            data.graceTicks--;
        } else if (++data.invalidTicks >= INVALID_GRACE) {
            end(player, data);
            return;
        }

        if (ParagliderCompat.isDepleted(player) || ParagliderCompat.totalStamina(player) <= 0.0D) {
            end(player, data);
            return;
        }

        payJumpCost(player, data);
        drainStamina(player, data);
    }

    /**
     * 攀爬耐力的最终结算。
     *
     * <p>耐力消耗主要由 Paraglider 的玩家状态（{@code wildclimb:climbing}）承担，
     * 这里每刻再核算一次实际掉了多少：如果状态机这一帧没有扣（例如状态还没切换过来），
     * 就把差额补上，保证耐力一定按配置的速率下降，且不会和状态机重复扣除。</p>
     */
    private static void drainStamina(ServerPlayer player, ClimbData data) {
        double current = ParagliderCompat.totalStamina(player);

        if (data.moving) {
            double expected = Config.climbStaminaPerTick();
            double drop = data.lastStamina >= 0.0D ? data.lastStamina - current : expected;
            if (drop < expected) {
                ParagliderCompat.take(player, expected - drop);
            }
        }

        data.lastStamina = ParagliderCompat.totalStamina(player);
    }

    /** 攀爬跳跃的耐力分摊到几刻里扣完，耐力轮因此有平滑的下降动画。 */
    private static void payJumpCost(ServerPlayer player, ClimbData data) {
        if (data.pendingJumpCost <= 0.0D) return;
        double step = Math.min(data.pendingJumpCost, Config.jumpStamina() / JUMP_COST_TICKS);
        data.pendingJumpCost -= step;
        ParagliderCompat.take(player, step);
    }

    public static void handleStart(ServerPlayer player, Direction dir,
                                   int wallX, int wallZ, int bottomSolidY, int topSolidY) {
        ClimbData data = ClimbAttachments.get(player);
        if (data.climbing) return;
        if (data.cooldown > 0 || !ClimbHelper.canGrab(player) || !ParagliderCompat.hasStamina(player)) {
            // 告诉客户端取消它那边的预测
            deny(player);
            return;
        }

        ClimbHelper.Wall wall = ClimbHelper.verifyWall(player, dir, wallX, wallZ);
        if (wall == null) wall = ClimbHelper.findWall(player.level(), player, dir);
        if (wall == null) {
            deny(player);
            return;
        }

        data.climbing = true;
        data.setWall(wall.dir(), wall.x(), wall.z(), wall.bottomSolidY(), wall.topSolidY());
        data.moving = false;
        data.ticks = 0;
        data.jumpVelocity = 0.0D;
        data.mantleTicks = 0;
        data.invalidTicks = 0;
        data.graceTicks = 0;
        data.jumpLateralDir = 0;
        data.pendingJumpCost = 0.0D;
        data.lastStamina = ParagliderCompat.totalStamina(player);
        data.lastX = player.getX();
        data.lastY = player.getY();
        data.lastZ = player.getZ();

        player.setNoGravity(true);
        player.setDeltaMovement(Vec3.ZERO);
        player.fallDistance = 0.0F;
        // 只把 Y 夹到墙面范围内，横向坐标由客户端保持（支持横向攀爬）
        player.setPos(player.getX(), Mth.clamp(player.getY(), wall.minY(), wall.maxY()), player.getZ());

        broadcast(player, data, true);
        ClimbHelper.playWallSound(player, data, true, 0.35F, 1.6F);
    }

    public static void handleJump(ServerPlayer player) {
        ClimbData data = ClimbAttachments.get(player);
        if (!data.climbing) return;
        // 攀爬跳跃固定消耗一笔耐力，分几刻扣完（和旷野之息一样，不够就扣到 0）
        data.pendingJumpCost = Config.jumpStamina();
    }

    /** 横向攀爬绕过墙角后，客户端通知服务端新的墙面。 */
    public static void handleTurn(ServerPlayer player, Direction dir,
                                  int wallX, int wallZ, int bottomSolidY, int topSolidY) {
        ClimbData data = ClimbAttachments.get(player);
        if (!data.climbing) return;
        ClimbHelper.Wall wall = ClimbHelper.verifyWall(player, dir, wallX, wallZ);
        if (wall == null) wall = ClimbHelper.findWall(player.level(), player, dir);
        if (wall == null) return;

        data.setWall(dir, wall.x(), wall.z(), wall.bottomSolidY(), wall.topSolidY());
        data.invalidTicks = 0;
        data.graceTicks = 8;
        data.jumpLateralDir = 0;
        data.lastX = player.getX();
        data.lastY = player.getY();
        data.lastZ = player.getZ();
        broadcast(player, data, true);
    }

    public static void handleStop(ServerPlayer player) {
        ClimbData data = ClimbAttachments.get(player);
        if (!data.climbing) return;
        end(player, data);
    }

    /** 结束攀爬并通知所有能看到该玩家的客户端。 */
    public static void end(ServerPlayer player, ClimbData data) {
        boolean wasClimbing = data.climbing;
        // 跳跃耐力还没扣完的部分补齐
        if (data.pendingJumpCost > 0.0D) {
            ParagliderCompat.take(player, data.pendingJumpCost);
            data.pendingJumpCost = 0.0D;
        }
        data.clearClimbing();
        data.cooldown = Config.grabCooldown();
        data.lastStamina = -1.0D;
        data.invalidTicks = 0;
        player.setNoGravity(false);
        player.setDeltaMovement(Vec3.ZERO);
        if (!wasClimbing) return;

        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player,
                new ClimbStatePayload(player.getId(), false, (byte) 0, 0, 0, 0, 0));
    }

    /** 玩家下线时清理状态。 */
    public static void onLogout(ServerPlayer player) {
        ClimbData data = ClimbAttachments.get(player);
        data.clearClimbing();
        data.cooldown = 0;
        data.lastStamina = -1.0D;
        data.pendingJumpCost = 0.0D;
        data.invalidTicks = 0;
    }

    private static void broadcast(ServerPlayer player, ClimbData data, boolean climbing) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, new ClimbStatePayload(
                player.getId(), climbing, (byte) data.wallDir.ordinal(),
                data.wallX, data.wallZ, data.bottomSolidY, data.topSolidY));
    }

    /** 拒绝一次抓墙，让客户端停止预测。 */
    private static void deny(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player,
                new ClimbStatePayload(player.getId(), false, (byte) 0, 0, 0, 0, 0));
    }
}
