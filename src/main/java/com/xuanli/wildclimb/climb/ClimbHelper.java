package com.xuanli.wildclimb.climb;

import com.xuanli.wildclimb.Config;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** Shared wall detection and attachment geometry. */
public final class ClimbHelper {
    public static final double PLAYER_HALF_WIDTH = 0.3D;
    public static final double FACE_GAP = 0.001D;
    private static final double PROBE_REACH = 0.35D;
    private static final double MAX_GRAB_GAP = 0.55D;
    private static final double MAX_SERVER_GAP = 1.3D;
    private static final int[] SERVER_Y_OFFSETS = {0, -1, 1, -2, 2};
    private static final int MAX_SCAN = 96;

    private ClimbHelper() {}

    /** The vertical extent of a climbable face in one column. Direction points from the player into the wall. */
    public record Wall(Direction dir, int x, int z, int bottomSolidY, int topSolidY) {
        public double topSurfaceY() {
            return topSolidY + 1.0D;
        }

        public double minY() {
            return bottomSolidY;
        }

        public double maxY() {
            return Math.max(bottomSolidY, topSurfaceY() - ClimbData.HANG_OFFSET);
        }
    }

    public static @Nullable Wall findWall(Level level, Player player) {
        Vec3 look = player.getLookAngle();
        Direction dir = look.horizontalDistanceSqr() < 0.10D
                ? player.getDirection() : Direction.getNearest(look.x, 0.0D, look.z);
        return findWall(level, player, dir);
    }

    public static @Nullable Wall findWall(Level level, Player player, Direction dir) {
        if (dir.getAxis().isVertical()) return null;

        BlockPos probe = columnInFront(player, dir);
        Wall wall = scanColumn(level, probe.getX(), probe.getZ(), probe.getY(), dir);
        if (wall == null && !player.onGround()) {
            wall = scanColumn(level, probe.getX(), probe.getZ(), probe.getY() - 1, dir);
            if (wall == null) wall = scanColumn(level, probe.getX(), probe.getZ(), probe.getY() + 1, dir);
        }
        if (wall == null) return null;

        double face = faceCoordinate(wall.x(), wall.z(), dir);
        double playerCoord = dir.getAxis() == Direction.Axis.X ? player.getX() : player.getZ();
        double gap = Math.abs(face - playerCoord) - PLAYER_HALF_WIDTH;
        if (gap > MAX_GRAB_GAP) return null;

        // Allow an airborne grab near the ledge while keeping the snap short.
        double ledgeAllowance = player.onGround() ? 0.0D : 0.75D;
        if (player.getY() > wall.maxY() + ledgeAllowance || player.getY() < wall.minY() - 0.6D) return null;
        if (wall.topSolidY() - wall.bottomSolidY() + 1 < Config.minWallHeight()) return null;

        return wall;
    }

    public static BlockPos columnInFront(Player player, Direction dir) {
        double px = player.getX() + dir.getStepX() * (PLAYER_HALF_WIDTH + PROBE_REACH);
        double pz = player.getZ() + dir.getStepZ() * (PLAYER_HALF_WIDTH + PROBE_REACH);
        return BlockPos.containing(px, player.getY() + 0.1D, pz);
    }

    /** Scans the contiguous climbable face above and below the sample block. */
    public static @Nullable Wall scanColumn(Level level, int x, int z, int startY, Direction dir) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, startY, z);
        if (!isWall(level, pos, dir)) return null;

        int top = startY;
        for (int i = 0; i < MAX_SCAN; i++) {
            if (!isWall(level, pos.set(x, top + 1, z), dir)) break;
            top++;
        }
        int bottom = startY;
        for (int i = 0; i < MAX_SCAN; i++) {
            if (!isWall(level, pos.set(x, bottom - 1, z), dir)) break;
            bottom--;
        }
        return new Wall(dir, x, z, bottom, top);
    }

    public static boolean isWall(Level level, BlockPos pos, Direction dir) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) return false;
        return state.isFaceSturdy(level, pos, dir.getOpposite());
    }

    public static double faceCoordinate(int wallX, int wallZ, Direction dir) {
        return switch (dir) {
            case EAST -> wallX;
            case WEST -> wallX + 1.0D;
            case SOUTH -> wallZ;
            case NORTH -> wallZ + 1.0D;
            default -> 0.0D;
        };
    }

    public static double perpendicularX(Direction dir, int wallX) {
        return switch (dir) {
            case EAST -> wallX - PLAYER_HALF_WIDTH - FACE_GAP;
            case WEST -> wallX + 1.0D + PLAYER_HALF_WIDTH + FACE_GAP;
            default -> wallX + 0.5D;
        };
    }

    public static double perpendicularZ(Direction dir, int wallZ) {
        return switch (dir) {
            case NORTH -> wallZ + 1.0D + PLAYER_HALF_WIDTH + FACE_GAP;
            case SOUTH -> wallZ - PLAYER_HALF_WIDTH - FACE_GAP;
            default -> wallZ + 0.5D;
        };
    }

    public static double lateralOf(Direction dir, Player player) {
        return dir.getAxis() == Direction.Axis.X ? player.getZ() : player.getX();
    }

    public static double lateralRightSign(Direction dir) {
        return dir.getAxis() == Direction.Axis.X ? dir.getStepX() : -dir.getStepZ();
    }

    public static int columnX(Direction dir, int wallX, double lateral) {
        return dir.getAxis() == Direction.Axis.X ? wallX : Mth.floor(lateral);
    }

    public static int columnZ(Direction dir, int wallZ, double lateral) {
        return dir.getAxis() == Direction.Axis.X ? Mth.floor(lateral) : wallZ;
    }

    public static void setOnWall(Player player, ClimbData data, double lateral, double y) {
        player.setPos(onWallX(data, lateral), y, onWallZ(data, lateral));
    }

    public static double onWallX(ClimbData data, double lateral) {
        return onWallX(data.wallDir, data.wallX, data.wallZ, lateral);
    }

    public static double onWallZ(ClimbData data, double lateral) {
        return onWallZ(data.wallDir, data.wallX, data.wallZ, lateral);
    }

    public static double onWallX(Direction dir, int wallX, int wallZ, double lateral) {
        return dir.getAxis() == Direction.Axis.X ? perpendicularX(dir, wallX) : lateral;
    }

    public static double onWallZ(Direction dir, int wallX, int wallZ, double lateral) {
        return dir.getAxis() == Direction.Axis.Z ? perpendicularZ(dir, wallZ) : lateral;
    }

    public static Direction wrapDirection(Direction dir, boolean movingRight) {
        Direction result = movingRight ? dir.getCounterClockWise() : dir.getClockWise();
        return result.getAxis().isVertical() ? dir : result;
    }

    public static double lateralCenter(Direction dir, int wallX, int wallZ) {
        return dir.getAxis() == Direction.Axis.X ? wallZ + 0.5D : wallX + 0.5D;
    }

    /** Validates the proposed wall, allowing for movement packet latency. */
    public static @Nullable Wall verifyWall(Player player, Direction dir, int wallX, int wallZ) {
        if (dir.getAxis().isVertical()) return null;

        double face = faceCoordinate(wallX, wallZ, dir);
        double coord = dir.getAxis() == Direction.Axis.X ? player.getX() : player.getZ();
        double signedDistance = (face - coord) * (dir.getStepX() + dir.getStepZ());
        double lateral = lateralOf(dir, player);
        double center = lateralCenter(dir, wallX, wallZ);
        if (signedDistance < -FACE_GAP || signedDistance > MAX_SERVER_GAP + PLAYER_HALF_WIDTH
                || Math.abs(lateral - center) > 1.2D) return null;

        Level level = player.level();
        int feetY = Mth.floor(player.getY() + 0.1D);
        for (int offset : SERVER_Y_OFFSETS) {
            Wall wall = scanColumn(level, wallX, wallZ, feetY + offset, dir);
            if (wall == null) continue;
            if (wall.topSolidY() - wall.bottomSolidY() + 1 < Config.minWallHeight()) continue;
            return wall;
        }
        return null;
    }

    /** Checks a nearby corner even when the player is still on the starting side of the destination plane. */
    public static @Nullable Wall verifyTurn(Player player, ClimbData data, Direction dir, int x, int z) {
        if (dir.getAxis().isVertical()) return null;
        if (dir == data.wallDir) return verifyWall(player, dir, x, z);
        if (dir.getAxis() == data.wallDir.getAxis()) return null;
        if (Math.abs((long) x - data.wallX) > 1 || Math.abs((long) z - data.wallZ) > 1) return null;
        double coord = dir.getAxis() == Direction.Axis.X ? player.getX() : player.getZ();
        if (Math.abs(faceCoordinate(x, z, dir) - coord) > 0.95D
                || Math.abs(lateralOf(dir, player) - lateralCenter(dir, x, z)) > 1.05D) return null;
        int feet = Mth.floor(player.getY() + 0.1D);
        Wall wall = scanColumn(player.level(), x, z, feet, dir);
        if (wall == null || player.getY() < wall.minY() - 0.2D || player.getY() > wall.maxY() + 0.2D) return null;
        return wall;
    }

    public static boolean sameWallPlane(ClimbData data, BlockPos pos) {
        return data.wallDir.getAxis() == Direction.Axis.X ? pos.getX() == data.wallX : pos.getZ() == data.wallZ;
    }

    public static boolean canGrab(Player player) {
        if (player.isSpectator() || player.isPassenger() || player.isDeadOrDying() || player.isSleeping()) return false;
        if (player.isInWater() || player.isInLava() || player.isFallFlying()) return false;
        if (player.getAbilities().flying) return false;
        // Give vanilla climbable blocks priority.
        if (isOnOrFacingClimbable(player)) return false;
        ClimbData data = ClimbAttachments.get(player);
        return !data.climbing && data.cooldown <= 0;
    }

    public static boolean canStillClimb(Player player, ClimbData data) {
        return canStillClimb(player, data, columnInFront(player, data.wallDir));
    }

    static boolean canStillClimb(Player player, ClimbData data, BlockPos column) {
        if (!data.climbing) return false;
        if (player.isSpectator() || player.isPassenger() || player.isDeadOrDying()) return false;
        if (player.isInWater() || player.isInLava() || player.isFallFlying()) return false;
        if (player.getAbilities().flying) return false;
        if (isOnOrFacingClimbable(player)) return false;

        Level level = player.level();
        return sameWallPlane(data, column)
                && (isWall(level, column, data.wallDir) || isWall(level, column.above(), data.wallDir));
    }

    /** Checks both feet and torso height so ladders starting one block above ground still take priority. */
    public static boolean isOnOrFacingClimbable(Player player) {
        if (player.onClimbable()) return true;

        Level level = player.level();
        int feetY = Mth.floor(player.getY() + 0.1D);
        BlockPos feet = new BlockPos(Mth.floor(player.getX()), feetY, Mth.floor(player.getZ()));
        if (isClimbable(level, feet) || isClimbable(level, feet.above())) return true;

        BlockPos front = feet.relative(player.getDirection());
        return isClimbable(level, front) || isClimbable(level, front.above());
    }

    private static boolean isClimbable(Level level, BlockPos pos) {
        return level.getBlockState(pos).is(BlockTags.CLIMBABLE);
    }

    public static boolean canMantle(Level level, ClimbData data) {
        BlockPos feet = new BlockPos(data.wallX, data.topSolidY + 1, data.wallZ);
        return isPassable(level, feet) && isPassable(level, feet.above());
    }

    private static boolean isPassable(Level level, BlockPos pos) {
        return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
    }

    public static void playWallSound(Player player, ClimbData data, boolean hit, float volume, float pitch) {
        Level level = player.level();
        int feetY = Mth.floor(player.getY() + 0.1D);
        BlockPos pos = new BlockPos(data.wallX, feetY, data.wallZ);
        SoundType soundType = level.getBlockState(pos).getSoundType();
        SoundEvent event = hit ? soundType.getHitSound() : soundType.getStepSound();
        level.playSound(null, player.blockPosition(), event, SoundSource.PLAYERS, volume, pitch);
    }

    public static SoundEvent wallStepSound(Level level, ClimbData data, int feetY) {
        return level.getBlockState(new BlockPos(data.wallX, feetY, data.wallZ)).getSoundType().getStepSound();
    }
}
