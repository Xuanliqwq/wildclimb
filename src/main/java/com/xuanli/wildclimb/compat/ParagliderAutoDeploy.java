package com.xuanli.wildclimb.compat;

import com.xuanli.wildclimb.Config;
import com.xuanli.wildclimb.Wildclimb;
import com.xuanli.wildclimb.client.ParagliderVisual;
import com.xuanli.wildclimb.climb.ClimbAttachments;
import com.xuanli.wildclimb.climb.ClimbData;
import com.xuanli.wildclimb.net.ParagliderGlidePayload;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import tictim.paraglider.api.ParagliderAPI;

/** Handles the jump key edge for inventory glider deployment. */
@EventBusSubscriber(modid = Wildclimb.MODID, value = Dist.CLIENT)
public final class ParagliderAutoDeploy {
    private static boolean wasJumpDown;

    private ParagliderAutoDeploy() {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof LocalPlayer player)) return;

        boolean jumpDown = Minecraft.getInstance().options.keyJump.isDown();
        boolean pressed = jumpDown && !wasJumpDown;
        wasJumpDown = jumpDown;
        if (!pressed) return;

        if (!canToggle(player)) return;
        if (!ParagliderGlide.hasParaglider(player)) return;

        boolean gliding = ParagliderVisual.isParagliding(player);
        if (!gliding && !Config.autoDeployParaglider()) return;

        PacketDistributor.sendToServer(new ParagliderGlidePayload(!gliding));
    }

    private static boolean canToggle(LocalPlayer player) {
        if (player.isSpectator() || player.isPassenger()) return false;
        if (player.isInWater() || player.isInLava()) return false;
        if (player.isFallFlying() || player.getAbilities().flying) return false;
        ClimbData climb = ClimbAttachments.get(player);
        if (climb.climbing || climb.mantleTicks > 0 || climb.cooldown == Config.grabCooldown()) return false;
        return !player.getMainHandItem().is(ParagliderAPI.PARAGLIDERS);
    }
}
