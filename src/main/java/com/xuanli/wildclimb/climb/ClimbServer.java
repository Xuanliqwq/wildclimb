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

/** Validates client-predicted climbing and settles stamina on the server. */
public final class ClimbServer {
    private static final int JUMP_COST_TICKS = 6;
    /** Allows a short interruption in wall contact while movement packets arrive. */
    private static final int INVALID_GRACE = 6;

    private ClimbServer() {}

    static void tick(ServerPlayer player, ClimbData data) {
        if (data.cooldown > 0) data.cooldown--;
        if (data.jumpCooldown > 0) data.jumpCooldown--;

        if (!data.climbing) return;

        player.setNoGravity(true);
        player.setDeltaMovement(Vec3.ZERO);
        player.fallDistance = 0.0F;
        data.ticks++;

        double dx = player.getX() - data.lastX;
        double dy = player.getY() - data.lastY;
        double dz = player.getZ() - data.lastZ;
        double threshold = 0.004D;
        data.moving = Math.abs(dy) > threshold || dx * dx + dz * dz > threshold * threshold;
        data.lastX = player.getX();
        data.lastY = player.getY();
        data.lastZ = player.getZ();

        BlockPos column = ClimbHelper.columnInFront(player, data.wallDir);
        if (data.graceTicks == 0 && ClimbHelper.sameWallPlane(data, column)
                && (column.getX() != data.wallX || column.getZ() != data.wallZ)) {
            ClimbHelper.Wall wall = ClimbHelper.scanColumn(player.level(),
                    column.getX(), column.getZ(), column.getY(), data.wallDir);
            if (wall != null) {
                data.moveToColumn(wall.x(), wall.z(), wall.bottomSolidY(), wall.topSolidY());
                broadcast(player, data, true);
            }
        }

        if (data.graceTicks > 0) data.graceTicks--;
        if (ClimbHelper.canStillClimb(player, data, column)) {
            data.invalidTicks = 0;
        } else if (data.graceTicks > 0) {
            data.invalidTicks = 0;
        } else if (++data.invalidTicks >= INVALID_GRACE) {
            end(player, data);
            return;
        }

        double stamina = ParagliderCompat.totalStamina(player);
        if (stamina <= 0.0D || ParagliderCompat.isDepleted(player)) {
            end(player, data);
            return;
        }

        boolean chargedMovement = drainStamina(player, data, stamina);
        boolean chargedJump = payJumpCost(player, data);
        if (chargedMovement || chargedJump) {
            stamina = ParagliderCompat.totalStamina(player);
            if (stamina <= 0.0D || ParagliderCompat.isDepleted(player)) {
                end(player, data);
                return;
            }
        }
        data.lastStamina = stamina;
    }

    /** Charges any movement cost that Paraglider has not already charged this tick. */
    private static boolean drainStamina(ServerPlayer player, ClimbData data, double current) {
        if (!data.moving) return false;
        double expected = Config.climbStaminaPerTick();
        double drop = data.lastStamina >= 0.0D ? data.lastStamina - current : expected;
        if (drop >= expected) return false;
        ParagliderCompat.take(player, expected - drop);
        return true;
    }

    /** Spreads the jump cost across several ticks for the stamina display. */
    private static boolean payJumpCost(ServerPlayer player, ClimbData data) {
        if (data.pendingJumpCost <= 0.0D) return false;
        double step = Math.min(data.pendingJumpCost, Config.jumpStamina() / JUMP_COST_TICKS);
        data.pendingJumpCost -= step;
        ParagliderCompat.take(player, step);
        return true;
    }

    public static void handleStart(ServerPlayer player, Direction dir,
                                   int wallX, int wallZ, int bottomSolidY, int topSolidY) {
        ClimbData data = ClimbAttachments.get(player);
        if (data.climbing) return;
        if (data.cooldown > 0 || !ClimbHelper.canGrab(player) || !ParagliderCompat.hasStamina(player)) {
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
        player.setPos(player.getX(), Mth.clamp(player.getY(), wall.minY(), wall.maxY()), player.getZ());

        broadcast(player, data, true);
        ClimbHelper.playWallSound(player, data, true, 0.35F, 1.6F);
    }

    public static void handleJump(ServerPlayer player) {
        ClimbData data = ClimbAttachments.get(player);
        if (!data.climbing) return;
        if (data.jumpCooldown > 0 || !ParagliderCompat.hasStamina(player)) return;
        data.jumpCooldown = ClimbData.JUMP_COOLDOWN_TICKS;
        data.pendingJumpCost += Config.jumpStamina();
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player,
                new ClimbStatePayload(player.getId(), true, (byte) 0, 0, 0, 0, 0, ClimbStatePayload.JUMP));
    }

    /** Registers the destination face before the player leaves the previous wall. */
    public static void handleTurn(ServerPlayer player, Direction dir,
                                  int wallX, int wallZ, int bottomSolidY, int topSolidY) {
        ClimbData data = ClimbAttachments.get(player);
        if (!data.climbing) return;
        ClimbHelper.Wall wall = ClimbHelper.verifyTurn(player, data, dir, wallX, wallZ);
        if (wall == null) return;
        boolean changingFace = dir != data.wallDir;
        data.setWall(dir, wall.x(), wall.z(), wall.bottomSolidY(), wall.topSolidY());
        data.invalidTicks = 0;
        if (changingFace) data.graceTicks = ClimbData.CORNER_TICKS + 5;
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

    public static void handleMantle(ServerPlayer player) {
        ClimbData data = ClimbAttachments.get(player);
        if (!data.climbing) return;
        byte animation = player.getY() >= data.maxY - 0.65D && ClimbHelper.canMantle(player.level(), data)
                ? ClimbStatePayload.MANTLE : ClimbStatePayload.NONE;
        end(player, data, animation);
    }

    public static void end(ServerPlayer player, ClimbData data) {
        end(player, data, ClimbStatePayload.NONE);
    }

    private static void end(ServerPlayer player, ClimbData data, byte animation) {
        boolean wasClimbing = data.climbing;
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
                new ClimbStatePayload(player.getId(), false, (byte) 0, 0, 0, 0, 0, animation));
    }

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

    private static void deny(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player,
                new ClimbStatePayload(player.getId(), false, (byte) 0, 0, 0, 0, 0));
    }
}
