package com.xuanli.wildclimb.climb;

import com.xuanli.wildclimb.Config;
import com.xuanli.wildclimb.compat.ParagliderCompat;
import com.xuanli.wildclimb.net.ClimbActionPayload;
import com.xuanli.wildclimb.net.ClimbStatePayload;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/** Handles local climbing input and movement and applies state updates for remote players. */
public final class ClimbClient {
    /** Mantle duration in ticks, matching the 0.5-second animation. */
    private static final int MANTLE_TICKS = ClimbData.MANTLE_TICKS;
    private static final double MANTLE_RISE = ClimbData.MANTLE_RISE;
    private static final int CORNER_TICKS = ClimbData.CORNER_TICKS;
    private static final double LOOK_AWAY_DOT = -0.5D;
    private static final double FACING_DOT = 0.85D;
    private static final int GRACE_TICKS = 8;
    private static final double JUMP_LATERAL_BOOST = 1.5D;
    private static final int JUMP_COOLDOWN_TICKS = ClimbData.JUMP_COOLDOWN_TICKS;
    private static final int CLIMB_SOUND_INTERVAL = 10;

    private ClimbClient() {}

    public static void onPlayerTick(Player player) {
        if (!player.level().isClientSide()) return;
        ClimbData data = ClimbAttachments.get(player);
        if (player instanceof LocalPlayer localPlayer) {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player != localPlayer) return;
            tickLocal(localPlayer, data, minecraft);
        } else {
            tickOther(player, data);
        }
        updateRenderRotation(player, data);
    }

    public static void onPlayerTickPre(Player player) {
        if (!(player instanceof LocalPlayer)) return;
        ClimbData data = ClimbAttachments.get(player);
        if (!data.climbing && data.mantleTicks <= 0) return;
        player.setDeltaMovement(Vec3.ZERO);
        player.setNoGravity(true);
    }

    private static void tickOther(Player player, ClimbData data) {
        if (data.jumpAnimationTicks > 0) data.jumpAnimationTicks--;
        if (data.mantleTicks > 0) data.mantleTicks--;
        if (!data.climbing) return;
        data.ticks++;
        data.atTop = player.getY() >= data.maxY - 0.02D;
        updateMotion(data, new Vec3(player.getX() - player.xOld, player.getY() - player.yOld,
                player.getZ() - player.zOld));
    }

    private static void updateMotion(ClimbData data, Vec3 delta) {
        double lateral = (data.wallDir.getAxis() == Direction.Axis.X ? delta.z : delta.x)
                * ClimbHelper.lateralRightSign(data.wallDir);
        data.moving = delta.lengthSqr() > 0.004D * 0.004D;
        if (Math.abs(delta.y) > 0.004D) data.descending = delta.y < 0.0D;
        double baseSpeed = Config.climbSpeedPerTick();
        float cadence = baseSpeed > 0.0D ? (float) (delta.length() / baseSpeed) : 1.0F;
        data.animationSpeed = Mth.lerp(0.3F, data.animationSpeed, Mth.clamp(cadence, 0.75F, 1.45F));
        data.traverseDirection = Math.abs(lateral) > Math.max(0.004D, Math.abs(delta.y))
                ? (lateral > 0.0D ? 1 : -1) : 0;
    }

    private static void tickLocal(LocalPlayer player, ClimbData data, Minecraft minecraft) {
        if (data.cooldown > 0) data.cooldown--;
        if (data.jumpAnimationTicks > 0) data.jumpAnimationTicks--;
        if (data.jumpCooldown > 0) data.jumpCooldown--;

        boolean jumpDown = minecraft.options.keyJump.isDown();
        boolean jumpPressed = jumpDown && !data.wasJumpDown;
        data.wasJumpDown = jumpDown;

        if (data.mantleTicks > 0) {
            tickMantle(player, data);
            return;
        }
        if (data.cornerTicks > 0) {
            if (minecraft.options.keyShift.isDown() || ParagliderCompat.isDepleted(player)) {
                detach(player, data, null);
                return;
            }
            tickCorner(player, data);
            return;
        }

        boolean forward = minecraft.options.keyUp.isDown();
        boolean backward = minecraft.options.keyDown.isDown();
        boolean left = minecraft.options.keyLeft.isDown();
        boolean right = minecraft.options.keyRight.isDown();
        boolean sneak = minecraft.options.keyShift.isDown();

        if (!data.climbing) {
            if (jumpPressed) {
                if (tryStart(player, data)) return;
                data.grabWindow = Config.airborneGrabWindow();
            } else if (data.grabWindow > 0) {
                data.grabWindow--;
                if (!player.onGround() && tryStart(player, data)) return;
            }
            return;
        }

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
        } else if (data.graceTicks > 0) {
            data.graceTicks--;
        }
        if (ParagliderCompat.isDepleted(player)) {
            detach(player, data, null);
            return;
        }

        data.ticks++;
        data.atTop = player.getY() >= data.maxY - 0.02D;

        if (jumpPressed) {
            double facing = facingDot(player, data);
            if (backward || facing < LOOK_AWAY_DOT) {
                Direction dir = data.wallDir;
                detach(player, data, new Vec3(-dir.getStepX() * 0.18D, 0.42D, -dir.getStepZ() * 0.18D));
                return;
            }
            if (data.atTop) {
                startMantle(player, data);
                return;
            }
            int lateralDir = 0;
            if (left != right) {
                double sign = ClimbHelper.lateralRightSign(data.wallDir);
                lateralDir = (int) (right ? sign : -sign);
            } else if (facing < FACING_DOT) {
                lateralDir = lateralDirectionFromLook(player, data);
            }
            climbJump(player, data, lateralDir);
        }

        Vec3 start = player.position();
        boolean jumping = data.jumpVelocity > 0.0D;
        double speed = Config.climbSpeedPerTick();
        double vertical = ((forward ? 1.0D : 0.0D) - (backward ? 1.0D : 0.0D)) * speed;
        double lateral = (right ? 1.0D : 0.0D) - (left ? 1.0D : 0.0D);
        double diagonal = vertical != 0.0D && lateral != 0.0D ? 1.0D / Math.sqrt(2.0D) : 1.0D;
        data.climbVelocity = Mth.lerp(0.6D, data.climbVelocity, vertical * diagonal);
        data.lateralVelocity = Mth.lerp(0.6D, data.lateralVelocity, lateral * diagonal);
        double y = Mth.clamp(start.y + (jumping ? data.jumpVelocity : data.climbVelocity), data.minY, data.maxY);
        double x = ClimbHelper.onWallX(data, data.lateral);
        double z = ClimbHelper.onWallZ(data, data.lateral);
        if (!moveTransition(player, new Vec3(x, y, z))
                && (y == start.y || !moveTransition(player, new Vec3(x, start.y, z)))) {
            detach(player, data, null);
            return;
        }
        player.setSprinting(false);
        double previousLateral = data.lateral;
        if (jumping) {
            double step = data.jumpLateralDir * speed * JUMP_LATERAL_BOOST
                    * Math.sqrt(data.jumpVelocity / Config.jumpVelocity());
            if (step != 0.0D) moveLaterally(player, data, step);
            data.jumpVelocity = data.cornerTicks == 0 && player.getY() > start.y && player.getY() < data.maxY
                    ? Math.max(0.0D, (data.jumpVelocity - 0.08D) * 0.98D) : 0.0D;
        } else if (Math.abs(data.lateralVelocity) > 0.025D) {
            moveLaterally(player, data, data.lateralVelocity * speed
                    * ClimbHelper.lateralRightSign(data.wallDir));
        }
        if (data.cornerTicks > 0) return;
        if (data.lateral != previousLateral) ClimbHelper.setOnWall(player, data, data.lateral, player.getY());
        data.atTop = player.getY() >= data.maxY - 0.02D;
        updateMotion(data, player.position().subtract(start));
        if (forward && data.atTop && !jumping) {
            startMantle(player, data);
            if (data.mantleTicks > 0) return;
        }

        if (data.moving && data.ticks % CLIMB_SOUND_INTERVAL == 0) {
            SoundEvent stepSound = ClimbHelper.wallStepSound(player.level(), data,
                    Mth.floor(player.getY() + 0.1D));
            player.level().playSound(player, player.blockPosition(), stepSound, SoundSource.PLAYERS, 0.16F, 1.4F);
        }
    }

    private static double facingDot(LocalPlayer player, ClimbData data) {
        Vec3 look = player.getLookAngle();
        double horizontal = Math.sqrt(look.x * look.x + look.z * look.z);
        if (horizontal < 0.2D) return 1.0D;
        Direction dir = data.wallDir;
        return (look.x * dir.getStepX() + look.z * dir.getStepZ()) / horizontal;
    }

    private static int lateralDirectionFromLook(LocalPlayer player, ClimbData data) {
        Vec3 look = player.getLookAngle();
        double alongWall = data.wallDir.getAxis() == Direction.Axis.X ? look.x : look.z;
        return alongWall >= 0.0D ? 1 : -1;
    }

    private static void moveLaterally(LocalPlayer player, ClimbData data, double step) {
        if (tryMoveLateral(player, data, data.lateral + step)
                || tryMoveLateral(player, data, data.lateral + step * 0.5D)) return;
        tryWrapCorner(player, data, step * ClimbHelper.lateralRightSign(data.wallDir) > 0.0D);
    }

    private static boolean tryMoveLateral(LocalPlayer player, ClimbData data, double target) {
        double y = player.getY();
        if (!clearPath(player, player.position(), new Vec3(ClimbHelper.onWallX(data, target), y,
                ClimbHelper.onWallZ(data, target)))) return false;

        int columnX = ClimbHelper.columnX(data.wallDir, data.wallX, target);
        int columnZ = ClimbHelper.columnZ(data.wallDir, data.wallZ, target);
        if (columnX == data.wallX && columnZ == data.wallZ) {
            data.lateral = target;
            return true;
        }

        int feetBlockY = Mth.floor(y + 0.1D);
        ClimbHelper.Wall wall = ClimbHelper.scanColumn(player.level(), columnX, columnZ, feetBlockY, data.wallDir);
        if (wall == null) return false;
        if (y > wall.maxY() + 0.05D || y < wall.minY() - 0.05D) return false;

        data.moveToColumn(columnX, columnZ, wall.bottomSolidY(), wall.topSolidY());
        data.lateral = target;
        return true;
    }

    private static boolean tryWrapCorner(LocalPlayer player, ClimbData data, boolean movingRight) {
        Direction newDir = ClimbHelper.wrapDirection(data.wallDir, movingRight);
        if (newDir == data.wallDir || newDir.getAxis().isVertical()) return false;

        Direction inner = newDir.getOpposite();
        BlockPos feet = BlockPos.containing(player.getX(), player.getY() + 0.1D, player.getZ());
        BlockPos side = feet.relative(inner);
        if (startCorner(player, data, inner, side.getX(), side.getZ(), true)) return true;
        return startCorner(player, data, newDir, data.wallX, data.wallZ, false);
    }

    private static boolean startCorner(LocalPlayer player, ClimbData data, Direction newDir,
                                       int blockX, int blockZ, boolean inner) {
        int feetBlockY = Mth.floor(player.getY() + 0.1D);
        ClimbHelper.Wall wall = ClimbHelper.scanColumn(player.level(), blockX, blockZ, feetBlockY, newDir);
        if (wall == null) return false;

        double blockMin = newDir.getAxis() == Direction.Axis.X ? blockZ : blockX;
        double lateral = inner ? ClimbHelper.lateralOf(newDir, player)
                : blockMin + 0.5D - (data.wallDir.getStepX() + data.wallDir.getStepZ()) * 0.38D;
        double targetX = ClimbHelper.onWallX(newDir, blockX, blockZ, lateral);
        double targetZ = ClimbHelper.onWallZ(newDir, blockX, blockZ, lateral);
        double y = player.getY();
        if (y < wall.minY() || y > wall.maxY() + 0.02D) return false;
        Vec3 start = player.position();
        Vec3 target = new Vec3(targetX, y, targetZ);
        if (inner && (Mth.floor(lateral) != (int) blockMin || start.distanceToSqr(target) > 0.04D)) return false;
        if (!inner && Math.abs(data.lateral - ClimbHelper.faceCoordinate(blockX, blockZ, newDir)) > 0.25D) return false;
        Vec3 bend = newDir.getAxis() == Direction.Axis.X
                ? new Vec3(targetX, y, start.z) : new Vec3(start.x, y, targetZ);
        if (!clearPath(player, start, bend) || !clearPath(player, bend, target)) return false;
        data.cornerBend = bend;
        data.cornerStartYaw = data.renderBodyYaw;
        boolean movingRight = newDir == ClimbHelper.wrapDirection(data.wallDir, true);
        data.traverseDirection = movingRight != inner ? 1 : -1;

        data.cornerStart = start;
        data.cornerTarget = target;
        data.cornerTicks = 1;

        data.cornerWall = wall;
        sendTurn(wall);
        data.jumpLateralDir = 0;

        ClimbHelper.playWallSound(player, data, false, 0.4F, 1.2F);
        return true;
    }

    private static void tickCorner(LocalPlayer player, ClimbData data) {
        double t = (double) data.cornerTicks / CORNER_TICKS;
        double progress = smoothStep(t) * 2.0D;
        double previous = smoothStep((double) (data.cornerTicks - 1) / CORNER_TICKS) * 2.0D;
        Vec3 next = progress < 1.0D ? data.cornerStart.lerp(data.cornerBend, progress)
                : data.cornerBend.lerp(data.cornerTarget, progress - 1.0D);
        if ((previous < 1.0D && progress >= 1.0D && !moveTransition(player, data.cornerBend))
                || !moveTransition(player, next)) {
            detach(player, data, null);
            return;
        }
        data.moving = true;
        if (data.cornerTicks++ >= CORNER_TICKS) {
            ClimbHelper.Wall wall = data.cornerWall;
            data.cornerTicks = 0;
            data.cornerWall = null;
            data.setWall(wall.dir(), wall.x(), wall.z(), wall.bottomSolidY(), wall.topSolidY());
            data.lateral = ClimbHelper.lateralOf(wall.dir(), player);
            data.graceTicks = GRACE_TICKS;
            sendTurn(wall);
        }
    }

    private static void sendTurn(ClimbHelper.Wall wall) {
        PacketDistributor.sendToServer(ClimbActionPayload.turn(wall.dir(), wall.x(), wall.z(),
                wall.bottomSolidY(), wall.topSolidY()));
    }

    private static void updateRenderRotation(Player player, ClimbData data) {
        if (!data.climbing && data.mantleTicks == 0) {
            data.hasClimbRotation = false;
            return;
        }
        if (!data.hasClimbRotation) {
            data.renderBodyYaw = player.yBodyRot;
            data.renderHeadYaw = player.getYHeadRot();
            data.hasClimbRotation = true;
        }
        data.previousRenderBodyYaw = data.renderBodyYaw;
        data.previousRenderHeadYaw = data.renderHeadYaw;
        float bodyTarget = data.wallDir.toYRot();
        if (data.cornerWall != null) {
            double progress = (double) (data.cornerTicks - 1) / CORNER_TICKS;
            bodyTarget = Mth.rotLerp((float) smoothStep(progress), data.cornerStartYaw,
                    data.cornerWall.dir().toYRot());
        }
        data.renderBodyYaw += Mth.clamp(Mth.wrapDegrees(bodyTarget - data.renderBodyYaw) * 0.55F, -16F, 16F);
        // Keep visual head rotation within a natural range without modifying player input.
        float offset = Mth.clamp(Mth.wrapDegrees(player.getYHeadRot() - data.renderBodyYaw), -62F, 62F);
        float headTarget = data.renderBodyYaw + offset;
        data.renderHeadYaw += Mth.clamp(Mth.wrapDegrees(headTarget - data.renderHeadYaw) * 0.5F, -18F, 18F);
    }

    private static boolean tryStart(LocalPlayer player, ClimbData data) {
        if (!ClimbHelper.canGrab(player)) return false;
        if (!ParagliderCompat.hasStamina(player)) return false;

        ClimbHelper.Wall wall = ClimbHelper.findWall(player.level(), player);
        if (wall == null) return false;

        double lateral = ClimbHelper.lateralOf(wall.dir(), player);
        Vec3 anchor = new Vec3(ClimbHelper.onWallX(wall.dir(), wall.x(), wall.z(), lateral),
                Mth.clamp(player.getY(), wall.minY(), wall.maxY()),
                ClimbHelper.onWallZ(wall.dir(), wall.x(), wall.z(), lateral));
        if (!clearPath(player, player.position(), anchor)) return false;
        data.climbing = true;
        data.setWall(wall.dir(), wall.x(), wall.z(), wall.bottomSolidY(), wall.topSolidY());
        data.lateral = lateral;
        data.ticks = 0;
        data.jumpVelocity = 0.0D;
        data.moving = false;
        data.grabWindow = 0;
        data.graceTicks = 2;
        data.atTop = false;
        data.mantleTicks = 0;
        data.cornerTicks = 0;
        data.jumpCooldown = 0;

        // Apply the attachment on the next tick so a rejected grab does not snap the player to the wall.
        player.setNoGravity(true);
        player.setDeltaMovement(Vec3.ZERO);
        player.fallDistance = 0.0F;
        ClimbHelper.playWallSound(player, data, true, 0.35F, 1.6F);
        PacketDistributor.sendToServer(ClimbActionPayload.start(wall.dir(),
                wall.x(), wall.z(), wall.bottomSolidY(), wall.topSolidY()));
        return true;
    }

    private static void climbJump(LocalPlayer player, ClimbData data, int lateralDir) {
        if (data.jumpVelocity > 0.0D) return;
        if (data.jumpCooldown > 0) return;
        if (!ParagliderCompat.hasStamina(player)) return;
        data.jumpVelocity = Config.jumpVelocity();
        data.jumpAnimationTicks = JUMP_COOLDOWN_TICKS;
        data.jumpLateralDir = lateralDir;
        data.jumpCooldown = JUMP_COOLDOWN_TICKS;
        PacketDistributor.sendToServer(ClimbActionPayload.jump());
    }

    /** Checks the entire lift and forward paths before starting a mantle. */
    private static void startMantle(LocalPlayer player, ClimbData data) {
        if (!ClimbHelper.canMantle(player.level(), data)) return;

        Direction dir = data.wallDir;
        double depth = ClimbHelper.PLAYER_HALF_WIDTH * 2 + 0.08D;
        Vec3 start = player.position();
        Vec3 crest = new Vec3(start.x, data.topSurfaceY, start.z);
        Vec3 target = crest.add(dir.getStepX() * depth, 0.0D, dir.getStepZ() * depth);
        if (!clearPath(player, start, crest) || !clearPath(player, crest, target)) return;

        data.mantleStart = start;
        data.mantleCrest = crest;
        data.mantleTarget = target;
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
        double t = (double) data.mantleTicks / MANTLE_TICKS;
        boolean rising = t < MANTLE_RISE;
        Vec3 next = rising
                ? data.mantleStart.lerp(data.mantleCrest, smoothStep(t / MANTLE_RISE))
                : data.mantleCrest.lerp(data.mantleTarget, smoothStep((t - MANTLE_RISE) / (1.0D - MANTLE_RISE)));
        boolean crossedCrest = !rising && (double) (data.mantleTicks - 1) / MANTLE_TICKS < MANTLE_RISE;
        if ((crossedCrest && !moveTransition(player, data.mantleCrest))
                || !moveTransition(player, next) || data.mantleTicks >= MANTLE_TICKS) {
            data.mantleTicks = 0;
            player.setNoGravity(false);
        } else {
            data.mantleTicks++;
        }
    }

    private static boolean clearPath(LocalPlayer player, Vec3 start, Vec3 target) {
        var box = player.getDimensions(player.getPose()).makeBoundingBox(start.x, start.y, start.z);
        return player.level().noCollision(player, box.expandTowards(target.subtract(start)));
    }

    private static boolean moveTransition(LocalPlayer player, Vec3 target) {
        if (!clearPath(player, player.position(), target)) return false;
        if (!target.equals(player.position())) player.setPos(target.x, target.y, target.z);
        player.setDeltaMovement(Vec3.ZERO);
        player.setNoGravity(true);
        player.fallDistance = 0.0F;
        return true;
    }

    private static double smoothStep(double t) {
        return t * t * (3.0D - 2.0D * t);
    }

    private static void detach(LocalPlayer player, ClimbData data, Vec3 velocity) {
        if (!data.climbing) return;
        data.clearClimbing();
        data.cooldown = Config.grabCooldown();
        data.grabWindow = 0;
        player.setNoGravity(false);
        if (velocity != null) player.setDeltaMovement(velocity);
        PacketDistributor.sendToServer(ClimbActionPayload.stop());
    }

    public static void handleState(ClimbStatePayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) return;

        Entity entity = level.getEntity(payload.entityId());
        if (!(entity instanceof Player player)) return;

        ClimbData data = ClimbAttachments.get(player);
        if (payload.animation() == ClimbStatePayload.JUMP) {
            if (!(player instanceof LocalPlayer) && data.climbing) {
                data.jumpAnimationTicks = JUMP_COOLDOWN_TICKS;
            }
            return;
        }
        if (payload.climbing()) {
            if (player instanceof LocalPlayer && (data.cornerTicks > 0 || data.mantleTicks > 0)) return;
            Direction dir = Direction.values()[Math.floorMod((int) payload.dir(), Direction.values().length)];
            if (player instanceof LocalPlayer && data.climbing && data.graceTicks > 0 && data.wallDir != dir) return;
            boolean resetAnchor = !data.climbing || data.wallDir != dir;
            data.climbing = true;
            data.setWall(dir, payload.wallX(), payload.wallZ(), payload.bottomSolidY(), payload.topSolidY());
            if (resetAnchor) {
                data.lateral = ClimbHelper.lateralOf(dir, player);
                data.atTop = false;
            }
        } else {
            boolean wasClimbing = data.climbing;
            data.clearClimbing();
            if (!(player instanceof LocalPlayer) && payload.animation() == ClimbStatePayload.MANTLE) {
                data.mantleTicks = MANTLE_TICKS;
            }
            data.cooldown = Config.grabCooldown();
            data.grabWindow = 0;
            if (wasClimbing && !player.isFallFlying()) {
                player.setNoGravity(false);
            }
        }
    }
}
