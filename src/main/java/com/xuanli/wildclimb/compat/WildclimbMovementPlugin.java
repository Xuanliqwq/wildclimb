package com.xuanli.wildclimb.compat;

import com.xuanli.wildclimb.Config;
import com.xuanli.wildclimb.Wildclimb;
import com.xuanli.wildclimb.climb.ClimbAttachments;
import com.xuanli.wildclimb.climb.ClimbData;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import tictim.paraglider.api.movement.MovementPlugin;
import tictim.paraglider.api.movement.ParagliderPlayerStates;
import tictim.paraglider.api.plugin.ParagliderPlugin;

/**
 * 把攀爬注册成 Paraglider 的玩家状态，这样耐力消耗、耐力恢复延迟、
 * 耐力耗尽后的缓慢恢复以及耐力轮 UI 全部由 Paraglider 自带逻辑处理。
 *
 * <p>Paraglider 会在所有模组文件里扫描 {@link ParagliderPlugin} 注解并通过无参构造函数实例化，
 * 所以这个类必须保留公开的无参构造函数。</p>
 */
@ParagliderPlugin
public final class WildclimbMovementPlugin implements MovementPlugin {
    /** 正在向上/向下移动的攀爬状态：持续消耗耐力。 */
    public static final ResourceLocation CLIMBING = Wildclimb.id("climbing");
    /** 挂在墙上不动：不消耗耐力，但也不会恢复。 */
    public static final ResourceLocation CLIMBING_HOLD = Wildclimb.id("climbing_hold");

    /** 优先级高于 Paraglider 自带的所有状态（最高为 8）。 */
    private static final double PRIORITY = 200.0D;
    /** 背包滑翔的优先级：低于攀爬，高于 Paraglider 自带的滑翔判定。 */
    private static final double GLIDE_PRIORITY = 150.0D;
    /** 与 idle 相同的耐力恢复速度：跑步和滞空时也照常恢复。 */
    private static final double RECOVERY_DELTA = 20.0D;

    @Override
    public void registerNewStates(PlayerStateRegister register) {
        double delta = Config.climbStaminaPerTick();
        Wildclimb.LOGGER.info("[wildclimb] registering Paraglider states ({} per tick, {} per second)", -delta,
                -delta * 20.0D);
        register.register(CLIMBING, -delta);
        register.register(CLIMBING_HOLD, 0.0D);
    }

    @Override
    public void modifyRegisteredStates(PlayerStateModifier modifier) {
        // Paraglider 默认让跑步（负 delta 的恢复延迟）和滞空（0 delta）时耐力停止恢复，
        // 这里把默认值改成和 idle 一样的恢复速度。已有的配置会被文件里的值覆盖，
        // 所以同时还写了 ParagliderStateConfig 去改它的配置文件。
        if (modifier.exists(ParagliderPlayerStates.RUNNING)) {
            modifier.changeDefaultStaminaDelta(ParagliderPlayerStates.RUNNING, RECOVERY_DELTA);
        }
        if (modifier.exists(ParagliderPlayerStates.MIDAIR)) {
            modifier.changeDefaultStaminaDelta(ParagliderPlayerStates.MIDAIR, RECOVERY_DELTA);
        }
    }

    @Override
    public void registerStateConnections(PlayerStateConnectionRegister register) {
        Wildclimb.LOGGER.info("[wildclimb] registering Paraglider state connections (priority {})", PRIORITY);
        // 从 idle 出发时，只要玩家在攀爬就切到攀爬状态
        register.connect(ParagliderPlayerStates.IDLE, CLIMBING,
                ctx -> isClimbing(ctx.player()) && isMoving(ctx.player()), PRIORITY);
        register.connect(ParagliderPlayerStates.IDLE, CLIMBING_HOLD,
                ctx -> isClimbing(ctx.player()), PRIORITY - 1.0D);

        // 结束攀爬后回到正常的判定流程（idle 会自己继续判断是否滑翔/游泳/奔跑/滞空）
        register.connect(CLIMBING, ParagliderPlayerStates.IDLE,
                ctx -> !isClimbing(ctx.player()), 0.0D);
        register.connect(CLIMBING_HOLD, ParagliderPlayerStates.IDLE,
                ctx -> !isClimbing(ctx.player()), 0.0D);

        // 背包里有滑翔伞时，由本模组直接把状态推到滑翔，不需要把伞拿在手上
        register.connect(ParagliderPlayerStates.IDLE, ParagliderPlayerStates.PARAGLIDING,
                ctx -> ParagliderGlide.isGliding(ctx.player()), GLIDE_PRIORITY);
    }

    private static boolean isClimbing(Player player) {
        ClimbData data = ClimbAttachments.get(player);
        return data.climbing;
    }

    private static boolean isMoving(Player player) {
        ClimbData data = ClimbAttachments.get(player);
        return data.moving || data.jumpVelocity > 0.0D;
    }
}
