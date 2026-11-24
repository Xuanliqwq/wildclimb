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

/** Registers climbing and inventory gliding with the Paraglider movement state machine. */
@ParagliderPlugin
public final class WildclimbMovementPlugin implements MovementPlugin {
    public static final ResourceLocation CLIMBING = Wildclimb.id("climbing");
    public static final ResourceLocation CLIMBING_HOLD = Wildclimb.id("climbing_hold");

    private static final double PRIORITY = 200.0D;
    private static final double GLIDE_PRIORITY = 150.0D;
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
        // Restore stamina recovery in the running and midair movement states.
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
        register.connect(ParagliderPlayerStates.IDLE, CLIMBING,
                ctx -> isClimbing(ctx.player()) && isMoving(ctx.player()), PRIORITY);
        register.connect(ParagliderPlayerStates.IDLE, CLIMBING_HOLD,
                ctx -> isClimbing(ctx.player()), PRIORITY - 1.0D);

        register.connect(CLIMBING, ParagliderPlayerStates.IDLE,
                ctx -> !isClimbing(ctx.player()), 0.0D);
        register.connect(CLIMBING_HOLD, ParagliderPlayerStates.IDLE,
                ctx -> !isClimbing(ctx.player()), 0.0D);

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
