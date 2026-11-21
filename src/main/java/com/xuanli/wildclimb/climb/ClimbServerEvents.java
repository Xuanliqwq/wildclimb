package com.xuanli.wildclimb.climb;

import com.xuanli.wildclimb.Wildclimb;
import com.xuanli.wildclimb.compat.ParagliderGlide;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

@EventBusSubscriber(modid = Wildclimb.MODID)
public final class ClimbServerEvents {
    private ClimbServerEvents() {}

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ParagliderGlide.tick(player);
            ClimbServer.tick(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ClimbServer.onLogout(player);
        }
    }
}
