package com.xuanli.wildclimb.climb;

import com.xuanli.wildclimb.Config;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * 墙面检测与攀爬几何计算。客户端与服务端共用，保证两边算出来的墙面完全一致。
 *
 * <p>攀爬时玩家在墙面上的位置由两部分组成：垂直于墙面的固定坐标（贴墙），以及沿着墙面的
 * 自由坐标（{@link ClimbData#lateral}），后者允许玩家横向攀爬。</p>
 */
public final class ClimbHelper {
    /** 玩家碰撞箱半宽。 */
    public static final double PLAYER_HALF_WIDTH = 0.3D;
    /** 贴墙时留出的极小缝隙，避免和方块碰撞箱互相推挤。 */
    public static final double FACE_GAP = 0.001D;
    /** 探测墙面时探针伸出的距离。 */
    private static final double PROBE_REACH = 0.35D;
    /** 玩家身体表面到墙面的最大间距，超过则抓不到墙。 */
    private static final double MAX_GRAB_GAP = 0.55D;
    /** 服务端校验时的最大间距（服务端坐标慢一刻，放宽一些）。 */
    private static final double MAX_SERVER_GAP = 1.3D;
    /** 服务端校验时，脚部高度上下尝试的偏移（格）。 */
    private static final int[] SERVER_Y_OFFSETS = {0, -1, 1, -2, 2};
    /** 竖直方向扫描墙面的上限。 */
    private static final int MAX_SCAN = 96;

    private ClimbHelper() {}

    /**
     * 一段墙面：所在方块列、朝向，以及这一列实心方块的范围。
     *
     * @param dir          从玩家指向墙面的方向
     * @param x            墙面方块的 X
     * @param z            墙面方块的 Z
     * @param bottomSolidY 这一列最下方实心方块的 Y
     * @param topSolidY    这一列最上方实心方块的 Y
     */
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

    /**
     * 按玩家视线方向寻找可攀爬的墙面。
     */
    public static @Nullable Wall findWall(Level level, Player player) {
        Vec3 look = player.getLookAngle();
        // 抬头/低头太厉害时不做判定，避免仰视天空也触发攀爬
        if (look.x * look.x + look.z * look.z < 0.10D) return null;
        Direction dir = Direction.getNearest(look.x, 0.0D, look.z);
        return findWall(level, player, dir);
    }

    /**
     * 在指定方向上寻找可攀爬的墙面。
     */
    public static @Nullable Wall findWall(Level level, Player player, Direction dir) {
        if (dir.getAxis().isVertical()) return null;

        BlockPos probe = columnInFront(player, dir);
        Wall wall = scanColumn(level, probe.getX(), probe.getZ(), probe.getY(), dir);
        if (wall == null) return null;

        double face = faceCoordinate(wall.x(), wall.z(), dir);
        double playerCoord = dir.getAxis() == Direction.Axis.X ? player.getX() : player.getZ();
        double gap = Math.abs(face - playerCoord) - PLAYER_HALF_WIDTH;
        if (gap > MAX_GRAB_GAP) return null;

        int feetBlockY = Mth.floor(player.getY() + 0.1D);
        // 墙面必须至少高出脚底两格（脚所在方块 + 上方一格）
        if (wall.topSolidY() < feetBlockY + 1) return null;
        if (wall.topSolidY() - wall.bottomSolidY() + 1 < Config.minWallHeight()) return null;

        return wall;
    }

    /**
     * 玩家正前方贴着的那个墙面方块（脚部所在高度）。
     */
    public static BlockPos columnInFront(Player player, Direction dir) {
        double px = player.getX() + dir.getStepX() * (PLAYER_HALF_WIDTH + PROBE_REACH);
        double pz = player.getZ() + dir.getStepZ() * (PLAYER_HALF_WIDTH + PROBE_REACH);
        return BlockPos.containing(px, player.getY() + 0.1D, pz);
    }

    /**
     * 从指定方块开始，向上下扫描出一整段墙面。该方块必须本身是墙。
     *
     * @return 墙面，或该位置不是墙面时返回 {@code null}
     */
    public static @Nullable Wall scanColumn(Level level, int x, int z, int startY, Direction dir) {
        if (!isWall(level, new BlockPos(x, startY, z), dir)) return null;

        int top = startY;
        for (int i = 0; i < MAX_SCAN; i++) {
            if (!isWall(level, new BlockPos(x, top + 1, z), dir)) break;
            top++;
        }
        int bottom = startY;
        for (int i = 0; i < MAX_SCAN; i++) {
            if (!isWall(level, new BlockPos(x, bottom - 1, z), dir)) break;
            bottom--;
        }
        return new Wall(dir, x, z, bottom, top);
    }

    /** 该方块在该方向上是否有可以抓的实心面。 */
    public static boolean isWall(Level level, BlockPos pos, Direction dir) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) return false;
        return state.isFaceSturdy(level, pos, dir.getOpposite());
    }

    /** 墙面的坐标（X 轴方向返回 X，Z 轴方向返回 Z）。 */
    public static double faceCoordinate(int wallX, int wallZ, Direction dir) {
        return switch (dir) {
            case EAST -> wallX;
            case WEST -> wallX + 1.0D;
            case SOUTH -> wallZ;
            case NORTH -> wallZ + 1.0D;
            default -> 0.0D;
        };
    }

    /** 贴墙时玩家中心的 X（只有东西朝向的墙会用到）。 */
    public static double perpendicularX(Direction dir, int wallX) {
        return switch (dir) {
            case EAST -> wallX - PLAYER_HALF_WIDTH - FACE_GAP;
            case WEST -> wallX + 1.0D + PLAYER_HALF_WIDTH + FACE_GAP;
            default -> wallX + 0.5D;
        };
    }

    /** 贴墙时玩家中心的 Z（只有南北朝向的墙会用到）。 */
    public static double perpendicularZ(Direction dir, int wallZ) {
        return switch (dir) {
            case NORTH -> wallZ + 1.0D + PLAYER_HALF_WIDTH + FACE_GAP;
            case SOUTH -> wallZ - PLAYER_HALF_WIDTH - FACE_GAP;
            default -> wallZ + 0.5D;
        };
    }

    /** 玩家沿着墙面方向的坐标（南北墙是 X，东西墙是 Z）。 */
    public static double lateralOf(Direction dir, Player player) {
        return dir.getAxis() == Direction.Axis.X ? player.getZ() : player.getX();
    }

    /** 玩家按“右键”横移时，沿墙坐标的前进方向。 */
    public static double lateralRightSign(Direction dir) {
        return dir.getAxis() == Direction.Axis.X ? dir.getStepX() : -dir.getStepZ();
    }

    /** 给定沿墙坐标时，玩家所在的墙面方块列。 */
    public static int columnX(Direction dir, int wallX, double lateral) {
        return dir.getAxis() == Direction.Axis.X ? wallX : Mth.floor(lateral);
    }

    public static int columnZ(Direction dir, int wallZ, double lateral) {
        return dir.getAxis() == Direction.Axis.X ? Mth.floor(lateral) : wallZ;
    }

    /** 把玩家摆到墙面上的指定位置。 */
    public static void setOnWall(Player player, ClimbData data, double lateral, double y) {
        player.setPos(onWallX(data, lateral), y, onWallZ(data, lateral));
    }

    /** 攀爬时玩家中心的 X。 */
    public static double onWallX(ClimbData data, double lateral) {
        return onWallX(data.wallDir, data.wallX, data.wallZ, lateral);
    }

    /** 攀爬时玩家中心的 Z。 */
    public static double onWallZ(ClimbData data, double lateral) {
        return onWallZ(data.wallDir, data.wallX, data.wallZ, lateral);
    }

    /** 攀爬时玩家中心的 X（指定墙面）。 */
    public static double onWallX(Direction dir, int wallX, int wallZ, double lateral) {
        return dir.getAxis() == Direction.Axis.X ? perpendicularX(dir, wallX) : lateral;
    }

    /** 攀爬时玩家中心的 Z（指定墙面）。 */
    public static double onWallZ(Direction dir, int wallX, int wallZ, double lateral) {
        return dir.getAxis() == Direction.Axis.Z ? perpendicularZ(dir, wallZ) : lateral;
    }

    /** 沿墙面方向移动时，绕着墙角转弯后的新朝向。 */
    public static Direction wrapDirection(Direction dir, boolean movingRight) {
        Direction result = movingRight ? dir.getCounterClockWise() : dir.getClockWise();
        return result.getAxis().isVertical() ? dir : result;
    }

    /** 某一列墙面在横轴上的中心坐标。 */
    public static double lateralCenter(Direction dir, int wallX, int wallZ) {
        return dir.getAxis() == Direction.Axis.X ? wallZ + 0.5D : wallX + 0.5D;
    }

    /**
     * 服务端校验客户端发来的墙面。
     *
     * <p>服务端的玩家坐标比客户端慢一刻，起跳途中抓墙时位置可能还差半格，
     * 所以这里不重新按视线探测，而是直接验证客户端指出的那一列确实是可攀爬的墙面，
     * 并在脚部高度上下各试几格来抵消坐标延迟。</p>
     */
    public static @Nullable Wall verifyWall(Player player, Direction dir, int wallX, int wallZ) {
        if (dir.getAxis().isVertical()) return null;

        double face = faceCoordinate(wallX, wallZ, dir);
        double coord = dir.getAxis() == Direction.Axis.X ? player.getX() : player.getZ();
        double gap = Math.abs(face - coord) - PLAYER_HALF_WIDTH;
        if (gap > MAX_SERVER_GAP) return null;

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

    /** 是否可以开始攀爬（与墙面无关的通用条件）。 */
    public static boolean canGrab(Player player) {
        if (player.isSpectator() || player.isPassenger() || player.isDeadOrDying() || player.isSleeping()) return false;
        if (player.isInWater() || player.isInLava() || player.isFallFlying()) return false;
        if (player.getAbilities().flying) return false;
        ClimbData data = ClimbAttachments.get(player);
        return !data.climbing && data.cooldown <= 0;
    }

    /** 攀爬过程中每刻的合法性检查。 */
    public static boolean canStillClimb(Player player, ClimbData data) {
        if (!data.climbing) return false;
        if (player.isSpectator() || player.isPassenger() || player.isDeadOrDying()) return false;
        if (player.isInWater() || player.isInLava() || player.isFallFlying()) return false;
        if (player.getAbilities().flying) return false;

        Level level = player.level();
        BlockPos column = columnInFront(player, data.wallDir);
        int feetBlockY = Mth.floor(player.getY() + 0.1D);
        return isWall(level, new BlockPos(column.getX(), feetBlockY, column.getZ()), data.wallDir)
                || isWall(level, new BlockPos(column.getX(), feetBlockY + 1, column.getZ()), data.wallDir);
    }

    /** 墙顶是否有足够空间让玩家翻上去。 */
    public static boolean canMantle(Level level, ClimbData data) {
        BlockPos feet = new BlockPos(data.wallX, data.topSolidY + 1, data.wallZ);
        return isPassable(level, feet) && isPassable(level, feet.above());
    }

    private static boolean isPassable(Level level, BlockPos pos) {
        return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
    }

    /**
     * 播放所攀爬方块自己的音效（木板是木头声、沙子是沙子声……）。
     *
     * @param hit {@code true} 用击打音，{@code false} 用脚步声
     */
    public static void playWallSound(Player player, ClimbData data, boolean hit, float volume, float pitch) {
        Level level = player.level();
        int feetY = Mth.floor(player.getY() + 0.1D);
        BlockPos pos = new BlockPos(data.wallX, feetY, data.wallZ);
        SoundType soundType = level.getBlockState(pos).getSoundType();
        SoundEvent event = hit ? soundType.getHitSound() : soundType.getStepSound();
        level.playSound(null, player.blockPosition(), event, SoundSource.PLAYERS, volume, pitch);
    }

    /** 所攀爬方块的脚步声，供攀爬过程中的循环音效使用。 */
    public static SoundEvent wallStepSound(Level level, ClimbData data, int feetY) {
        return level.getBlockState(new BlockPos(data.wallX, feetY, data.wallZ)).getSoundType().getStepSound();
    }
}
