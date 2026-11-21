package com.xuanli.wildclimb.climb;

import com.xuanli.wildclimb.Config;
import com.xuanli.wildclimb.compat.ParagliderCompat;
import com.xuanli.wildclimb.net.ClimbActionPayload;
import com.xuanli.wildclimb.net.ClimbStatePayload;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 客户端攀爬控制：输入、贴墙位移（含横向攀爬与绕墙角）、攀爬跳跃、翻上墙顶动画，
 * 以及对其他玩家攀爬状态的同步。
 */
public final class ClimbClient {
    /** 翻上墙顶的动画总时长（刻）与上升/前移分界点，定义在 {@link ClimbData} 里供动画层共用。 */
    private static final int MANTLE_TICKS = ClimbData.MANTLE_TICKS;
    private static final double MANTLE_RISE = ClimbData.MANTLE_RISE;
    /** 绕墙角的动画时长（刻）。 */
    private static final int CORNER_TICKS = 4;
    /** 绕角时向外鼓出的幅度，避免身体切进墙角。 */
    private static final double CORNER_BULGE = 0.3D;
    /** 视线与“正对墙面”的夹角超过约 120 度（也就是背对墙面）时，按跳跃会脱离墙面。 */
    private static final double LOOK_AWAY_DOT = -0.5D;
    /** 视线基本正对墙面（约 32 度以内）时是纯向上的攀爬跳跃，否则朝侧向做攀爬跳跃。 */
    private static final double FACING_DOT = 0.85D;
    /** 换面 / 绕角后的宽限刻数。 */
    private static final int GRACE_TICKS = 8;
    /** 侧向攀爬跳跃时，横向位移相对普通横移的倍率。 */
    private static final double JUMP_LATERAL_BOOST = 1.5D;
    /** 攀爬跳跃的内置冷却（刻）。 */
    private static final int JUMP_COOLDOWN_TICKS = 8;
    /** 攀爬移动时播放材质脚步声的间隔（刻）。 */
    private static final int CLIMB_SOUND_INTERVAL = 10;

    private ClimbClient() {}

    public static void onPlayerTick(Player player) {
        if (player instanceof LocalPlayer localPlayer) {
            if (Minecraft.getInstance().player == localPlayer) tickLocal(localPlayer);
        } else {
            tickOther(player);
        }
    }

    /**
     * 每刻在实体 tick 之前调用：清掉上一刻残留的速度，避免原版移动在贴墙时把玩家挤进方块。
     */
    public static void onPlayerTickPre(Player player) {
        if (!(player instanceof LocalPlayer)) return;
        ClimbData data = ClimbAttachments.get(player);
        if (!data.climbing && data.mantleTicks <= 0) return;
        player.setDeltaMovement(Vec3.ZERO);
        player.setNoGravity(true);
    }

    /** 其他玩家的攀爬状态：由服务端同步，这里只维持身体朝向与动画状态。 */
    private static void tickOther(Player player) {
        ClimbData data = ClimbAttachments.get(player);
        if (!data.climbing) return;
        data.ticks++;
        data.atTop = player.getY() >= data.maxY - 0.02D;
        data.moving = player.getDeltaMovement().y > 0.004D
                || Math.abs(player.getX() - player.xOld) > 0.004D
                || Math.abs(player.getZ() - player.zOld) > 0.004D;
        player.yBodyRot = data.wallDir.toYRot();
    }

    private static void tickLocal(LocalPlayer player) {
        Minecraft minecraft = Minecraft.getInstance();
        ClimbData data = ClimbAttachments.get(player);
        if (data.cooldown > 0) data.cooldown--;
        if (data.jumpCooldown > 0) data.jumpCooldown--;

        // 翻上墙顶 / 绕墙角的动画期间忽略一切输入
        if (data.mantleTicks > 0) {
            tickMantle(player, data);
            return;
        }
        if (data.cornerTicks > 0) {
            tickCorner(player, data);
            return;
        }

        boolean jumpDown = minecraft.options.keyJump.isDown();
        boolean jumpPressed = jumpDown && !data.wasJumpDown;
        data.wasJumpDown = jumpDown;

        boolean forward = minecraft.options.keyUp.isDown();
        boolean backward = minecraft.options.keyDown.isDown();
        boolean left = minecraft.options.keyLeft.isDown();
        boolean right = minecraft.options.keyRight.isDown();
        boolean sneak = minecraft.options.keyShift.isDown();

        if (!data.climbing) {
            if (jumpPressed) {
                if (tryStart(player, data)) return;
                // 跳向墙面后的一小段时间内仍然可以抓墙
                data.grabWindow = Config.airborneGrabWindow();
            } else if (data.grabWindow > 0) {
                data.grabWindow--;
                if (!player.onGround() && tryStart(player, data)) return;
            }
            return;
        }

        // 潜行键：松手
        if (sneak) {
            detach(player, data, null);
            return;
        }
        if (!ClimbHelper.canStillClimb(player, data)) {
            if (data.graceTicks > 0) {
                data.graceTicks--;
            } else {
                detach(player, data, null);
                return;
            }
        } else {
            data.graceTicks = 0;
        }
        if (ParagliderCompat.isDepleted(player)) {
            detach(player, data, null);
            return;
        }

        data.ticks++;
        data.atTop = player.getY() >= data.maxY - 0.02D;

        if (jumpPressed) {
            double facing = facingDot(player, data);
            // 背对墙面（或按住后退键）跳跃：向后蹬墙跳开
            if (backward || facing < LOOK_AWAY_DOT) {
                Direction dir = data.wallDir;
                detach(player, data, new Vec3(-dir.getStepX() * 0.18D, 0.42D, -dir.getStepZ() * 0.18D));
                return;
            }
            // 已经够到墙顶边缘时，空格是翻上墙顶
            if (data.atTop) {
                startMantle(player, data);
                return;
            }
            // 按住 A/D＝朝按键方向做侧向攀爬跳跃；没按平移键但视线朝左右时，按视线方向跳
            int lateralDir = 0;
            if (left != right) {
                double sign = ClimbHelper.lateralRightSign(data.wallDir);
                lateralDir = (int) (right ? sign : -sign);
            } else if (facing < FACING_DOT) {
                lateralDir = lateralDirectionFromLook(player, data);
            }
            climbJump(player, data, lateralDir);
            return;
        }

        // 跳跃过程中由跳跃自己的横向位移负责移动，避免和普通横移叠加
        boolean movedLaterally = false;
        if (data.jumpVelocity <= 0.0D) {
            movedLaterally = moveLaterally(player, data, left, right);
            // 刚触发换面：这一刻交给绕角动画
            if (data.cornerTicks > 0) {
                tickCorner(player, data);
                return;
            }
        }

        double y = player.getY();
        if (data.jumpVelocity > 0.0D) {
            y += data.jumpVelocity;
            data.jumpVelocity = (data.jumpVelocity - 0.08D) * 0.98D;
            if (data.jumpVelocity <= 0.0D) {
                data.jumpVelocity = 0.0D;
                data.jumpLateralDir = 0;
            } else if (data.jumpLateralDir != 0) {
                // 侧向攀爬跳跃：跳跃过程中同时向侧向移动
                double target = data.lateral
                        + data.jumpLateralDir * Config.lateralSpeedPerTick() * JUMP_LATERAL_BOOST;
                if (!tryMoveLateral(player, data, target)) data.jumpLateralDir = 0;
            }
        } else if (forward) {
            y += Config.climbSpeedPerTick();
        } else if (backward) {
            y -= Config.descendSpeedPerTick();
        }
        y = Mth.clamp(y, data.minY, data.maxY);
        // 不要钻进方块里（例如脚下就是平台时继续向下爬）
        if (!fitsAt(player, data, data.lateral, y)) {
            y = player.getY();
        }

        applyPose(player, data, y);

        // 上下左右任何移动都算“正在攀爬移动”，和旷野之息一样会消耗耐力
        data.moving = data.jumpVelocity > 0.0D || forward || backward || movedLaterally;

        // 攀爬过程中每隔一小段播放当前方块材质的脚步声
        if (data.moving && data.ticks % CLIMB_SOUND_INTERVAL == 0) {
            SoundEvent stepSound = ClimbHelper.wallStepSound(player.level(), data,
                    Mth.floor(player.getY() + 0.1D));
            player.level().playSound(player, player.blockPosition(), stepSound, SoundSource.PLAYERS, 0.16F, 1.4F);
        }
    }

    /** 玩家水平视线与“朝向墙面”方向的夹角余弦；低头/抬头看脚下时按正对处理。 */
    private static double facingDot(LocalPlayer player, ClimbData data) {
        Vec3 look = player.getLookAngle();
        double horizontal = Math.sqrt(look.x * look.x + look.z * look.z);
        if (horizontal < 0.2D) return 1.0D;
        Direction dir = data.wallDir;
        return (look.x * dir.getStepX() + look.z * dir.getStepZ()) / horizontal;
    }

    /** 侧向攀爬跳跃的方向：沿墙面、朝着玩家视线的那一侧。 */
    private static int lateralDirectionFromLook(LocalPlayer player, ClimbData data) {
        Vec3 look = player.getLookAngle();
        double alongWall = data.wallDir.getAxis() == Direction.Axis.X ? look.x : look.z;
        return alongWall >= 0.0D ? 1 : -1;
    }

    /**
     * 沿墙面左右横移；跨到新的方块列时重新扫描这一列的墙面高度，
     * 撞到方块就停下；走到墙面横向边缘时尝试绕到相邻的那一面。
     */
    private static boolean moveLaterally(LocalPlayer player, ClimbData data, boolean left, boolean right) {
        if (left == right) return false;

        boolean movingRight = right;
        double sign = ClimbHelper.lateralRightSign(data.wallDir) * (movingRight ? 1.0D : -1.0D);
        double step = sign * Config.lateralSpeedPerTick();

        double target = data.lateral + step;
        if (tryMoveLateral(player, data, target)) return true;
        // 撞墙时尝试走一小步，尽量贴到障碍物边缘
        if (tryMoveLateral(player, data, data.lateral + step * 0.5D)) return true;
        // 到了墙面横向边缘：如果旁边有可以抓的另一面，就绕过去
        return tryWrapCorner(player, data, movingRight);
    }

    private static boolean tryMoveLateral(LocalPlayer player, ClimbData data, double target) {
        double y = player.getY();
        if (!fitsAt(player, data, target, y)) return false;

        int columnX = ClimbHelper.columnX(data.wallDir, data.wallX, target);
        int columnZ = ClimbHelper.columnZ(data.wallDir, data.wallZ, target);
        if (columnX == data.wallX && columnZ == data.wallZ) {
            data.lateral = target;
            return true;
        }

        int feetBlockY = Mth.floor(y + 0.1D);
        ClimbHelper.Wall wall = ClimbHelper.scanColumn(player.level(), columnX, columnZ, feetBlockY, data.wallDir);
        if (wall == null) return false;
        // 新的一列墙面必须仍然覆盖玩家当前的高度
        if (y > wall.maxY() + 0.05D || y < wall.minY() - 0.05D) return false;

        data.moveToColumn(columnX, columnZ, wall.bottomSolidY(), wall.topSolidY());
        data.lateral = target;
        return true;
    }

    /** 走到墙面横向边缘时，绕当前方块转过 90 度继续攀爬相邻的那一面。 */
    private static boolean tryWrapCorner(LocalPlayer player, ClimbData data, boolean movingRight) {
        Direction newDir = ClimbHelper.wrapDirection(data.wallDir, movingRight);
        if (newDir == data.wallDir || newDir.getAxis().isVertical()) return false;

        int blockX = data.wallX;
        int blockZ = data.wallZ;
        int feetBlockY = Mth.floor(player.getY() + 0.1D);
        ClimbHelper.Wall wall = ClimbHelper.scanColumn(player.level(), blockX, blockZ, feetBlockY, newDir);
        if (wall == null) return false;

        double blockMin = newDir.getAxis() == Direction.Axis.X ? blockZ : blockX;
        double lateral = Mth.clamp(ClimbHelper.lateralOf(newDir, player),
                blockMin + ClimbHelper.PLAYER_HALF_WIDTH, blockMin + 1.0D - ClimbHelper.PLAYER_HALF_WIDTH);
        double targetX = ClimbHelper.onWallX(newDir, blockX, blockZ, lateral);
        double targetZ = ClimbHelper.onWallZ(newDir, blockX, blockZ, lateral);
        double y = Mth.clamp(player.getY(), wall.minY(), wall.maxY());
        if (!player.level().noCollision(player,
                player.getDimensions(player.getPose()).makeBoundingBox(targetX, y, targetZ))) {
            return false;
        }

        // 绕角的转轴：当前方块离玩家最近的那个角
        data.cornerPivotX = Math.abs(player.getX() - blockX) < Math.abs(player.getX() - (blockX + 1)) ? blockX : blockX + 1;
        data.cornerPivotZ = Math.abs(player.getZ() - blockZ) < Math.abs(player.getZ() - (blockZ + 1)) ? blockZ : blockZ + 1;

        data.cornerStartX = player.getX();
        data.cornerStartY = player.getY();
        data.cornerStartZ = player.getZ();
        data.cornerTargetX = targetX;
        data.cornerTargetY = y;
        data.cornerTargetZ = targetZ;
        data.cornerTicks = 1;

        data.setWall(newDir, blockX, blockZ, wall.bottomSolidY(), wall.topSolidY());
        data.lateral = lateral;
        data.graceTicks = GRACE_TICKS;
        data.jumpLateralDir = 0;

        ClimbHelper.playWallSound(player, data, false, 0.4F, 1.2F);
        PacketDistributor.sendToServer(ClimbActionPayload.turn(newDir, blockX, blockZ,
                wall.bottomSolidY(), wall.topSolidY()));
        return true;
    }

    private static void tickCorner(LocalPlayer player, ClimbData data) {
        double t = Math.min(1.0D, (data.cornerTicks - 1) / (double) CORNER_TICKS);
        double eased = smoothStep(t);
        double x = Mth.lerp(eased, data.cornerStartX, data.cornerTargetX);
        double z = Mth.lerp(eased, data.cornerStartZ, data.cornerTargetZ);
        double y = Mth.lerp(eased, data.cornerStartY, data.cornerTargetY);

        // 向外鼓出一点，绕开墙角
        double midX = (data.cornerStartX + data.cornerTargetX) * 0.5D;
        double midZ = (data.cornerStartZ + data.cornerTargetZ) * 0.5D;
        double outX = midX - data.cornerPivotX;
        double outZ = midZ - data.cornerPivotZ;
        double length = Math.sqrt(outX * outX + outZ * outZ);
        if (length > 1.0E-4D) {
            double bulge = Math.sin(Math.PI * t) * CORNER_BULGE;
            x += outX / length * bulge;
            z += outZ / length * bulge;
        }

        player.setPos(x, y, z);
        player.setDeltaMovement(Vec3.ZERO);
        player.setNoGravity(true);
        player.fallDistance = 0.0F;
        player.yBodyRot = data.wallDir.toYRot();
        data.moving = true;

        if (data.cornerTicks >= CORNER_TICKS + 1) {
            data.cornerTicks = 0;
        } else {
            data.cornerTicks++;
        }
    }

    private static boolean tryStart(LocalPlayer player, ClimbData data) {
        if (!ClimbHelper.canGrab(player)) return false;
        if (!ParagliderCompat.hasStamina(player)) return false;

        ClimbHelper.Wall wall = ClimbHelper.findWall(player.level(), player);
        if (wall == null) return false;

        data.climbing = true;
        data.setWall(wall.dir(), wall.x(), wall.z(), wall.bottomSolidY(), wall.topSolidY());
        data.lateral = ClimbHelper.lateralOf(wall.dir(), player);
        data.ticks = 0;
        data.jumpVelocity = 0.0D;
        data.moving = false;
        data.grabWindow = 0;
        data.atTop = false;
        data.mantleTicks = 0;
        data.cornerTicks = 0;
        data.jumpCooldown = 0;

        // 这里先不改坐标：万一服务端因为坐标慢一刻拒绝这次抓墙，玩家就不会被“吸”到墙面上
        // 又原地掉回去。贴墙的动作留给下一刻的 applyPose。
        player.setNoGravity(true);
        player.setDeltaMovement(Vec3.ZERO);
        player.fallDistance = 0.0F;
        ClimbHelper.playWallSound(player, data, true, 0.35F, 1.6F);
        PacketDistributor.sendToServer(ClimbActionPayload.start(wall.dir(),
                wall.x(), wall.z(), wall.bottomSolidY(), wall.topSolidY()));
        return true;
    }

    /** 贴墙、无重力、身体朝向墙面（头部仍然可以自由转动）。 */
    private static void applyPose(LocalPlayer player, ClimbData data, double y) {
        ClimbHelper.setOnWall(player, data, data.lateral, y);
        player.setDeltaMovement(Vec3.ZERO);
        player.setNoGravity(true);
        player.setSprinting(false);
        player.fallDistance = 0.0F;
        player.yBodyRot = data.wallDir.toYRot();
    }

    /** 该坐标是否放得下玩家（不会卡进方块）。 */
    private static boolean fitsAt(LocalPlayer player, ClimbData data, double lateral, double y) {
        return player.level().noCollision(player,
                player.getDimensions(player.getPose()).makeBoundingBox(
                        ClimbHelper.onWallX(data, lateral), y, ClimbHelper.onWallZ(data, lateral)));
    }

    private static void climbJump(LocalPlayer player, ClimbData data, int lateralDir) {
        if (data.jumpVelocity > 0.0D) return;
        if (data.jumpCooldown > 0) return;
        if (!ParagliderCompat.hasStamina(player)) return;
        data.jumpVelocity = Config.jumpVelocity();
        data.jumpLateralDir = lateralDir;
        data.jumpCooldown = JUMP_COOLDOWN_TICKS;
        PacketDistributor.sendToServer(ClimbActionPayload.jump());
    }

    /** 开始翻上墙顶：先上升越过边缘，再前移到方块上方，整个过程用 8 刻动画表现。 */
    private static void startMantle(LocalPlayer player, ClimbData data) {
        if (!ClimbHelper.canMantle(player.level(), data)) return;

        double targetX = data.wallX + 0.5D;
        double targetY = data.topSurfaceY;
        double targetZ = data.wallZ + 0.5D;
        if (!player.level().noCollision(player,
                player.getDimensions(player.getPose()).makeBoundingBox(targetX, targetY, targetZ))) {
            return;
        }

        data.mantleStartX = player.getX();
        data.mantleStartY = player.getY();
        data.mantleStartZ = player.getZ();
        data.mantleTargetX = targetX;
        data.mantleTargetY = targetY;
        data.mantleTargetZ = targetZ;
        data.mantleTicks = 1;

        data.clearClimbing();
        data.cooldown = Config.grabCooldown();
        data.grabWindow = 0;

        player.setNoGravity(true);
        player.setDeltaMovement(Vec3.ZERO);
        ClimbHelper.playWallSound(player, data, false, 0.6F, 1.1F);
        PacketDistributor.sendToServer(ClimbActionPayload.mantle());
    }

    private static void tickMantle(LocalPlayer player, ClimbData data) {
        double t = Math.min(1.0D, data.mantleTicks / (double) MANTLE_TICKS);
        double x;
        double y;
        double z;

        if (t < MANTLE_RISE) {
            // 第一阶段：贴着墙面向上越过边缘
            double k = t / MANTLE_RISE;
            double eased = smoothStep(k);
            x = data.mantleStartX;
            z = data.mantleStartZ;
            y = Mth.lerp(eased, data.mantleStartY, data.mantleTargetY + 0.25D);
        } else {
            // 第二阶段：越过边缘后向前挪到方块上方，再落下来站好
            double k = (t - MANTLE_RISE) / (1.0D - MANTLE_RISE);
            double eased = smoothStep(k);
            x = Mth.lerp(eased, data.mantleStartX, data.mantleTargetX);
            z = Mth.lerp(eased, data.mantleStartZ, data.mantleTargetZ);
            y = Mth.lerp(k, data.mantleTargetY + 0.25D, data.mantleTargetY);
        }

        player.setPos(x, y, z);
        player.setDeltaMovement(Vec3.ZERO);
        player.setNoGravity(true);
        player.fallDistance = 0.0F;

        if (data.mantleTicks >= MANTLE_TICKS) {
            player.setPos(data.mantleTargetX, data.mantleTargetY, data.mantleTargetZ);
            player.setNoGravity(false);
            player.setDeltaMovement(Vec3.ZERO);
            player.fallDistance = 0.0F;
            data.mantleTicks = 0;
        } else {
            data.mantleTicks++;
        }
    }

    private static double smoothStep(double t) {
        return t * t * (3.0D - 2.0D * t);
    }

    private static void detach(LocalPlayer player, ClimbData data, Vec3 velocity) {
        if (!data.climbing) return;
        data.clearClimbing();
        data.cooldown = Config.grabCooldown();
        data.grabWindow = 0;
        data.cornerTicks = 0;
        player.setNoGravity(false);
        if (velocity != null) player.setDeltaMovement(velocity);
        PacketDistributor.sendToServer(ClimbActionPayload.stop());
    }

    /** 处理服务端发来的攀爬状态（自己的与别的玩家的）。 */
    public static void handleState(ClimbStatePayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) return;

        Entity entity = level.getEntity(payload.entityId());
        if (!(entity instanceof Player player)) return;

        ClimbData data = ClimbAttachments.get(player);
        if (payload.climbing()) {
            Direction dir = Direction.values()[Math.floorMod((int) payload.dir(), Direction.values().length)];
            boolean wasClimbing = data.climbing;
            data.climbing = true;
            data.setWall(dir, payload.wallX(), payload.wallZ(), payload.bottomSolidY(), payload.topSolidY());
            if (!wasClimbing) {
                data.lateral = ClimbHelper.lateralOf(dir, player);
                data.atTop = false;
            }
        } else {
            boolean wasClimbing = data.climbing;
            data.clearClimbing();
            data.cooldown = Config.grabCooldown();
            data.grabWindow = 0;
            if (wasClimbing && !player.isFallFlying()) {
                player.setNoGravity(false);
            }
        }
    }
}
